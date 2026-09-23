package app.kultr.android.data

import app.kultr.android.AppGraph
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kultr.android.KultrApp
import app.kultr.android.MainActivity
import app.kultr.android.R
import app.kultr.android.data.db.DownloadEntity
import app.kultr.android.data.db.DownloadState
import app.kultr.android.data.db.DownloadUsage
import app.kultr.android.data.db.KultrDatabase
import app.kultr.android.data.db.SQL_CHUNK
import app.kultr.android.data.db.toSong
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.describeError
import app.kultr.core.api.md5Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

data class DownloadProgress(
    val done: Int,
    val total: Int,
    val failed: Int,
    val bytes: Long,
    val current: String,
)

/** A file being fetched right now. [total] is an estimate when the server does not say. */
data class ActiveDownload(
    val song: Song,
    val bytes: Long,
    val total: Long,
) {
    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** What the download queue is doing, for the indicator and the Downloads page. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    /** Tracks are queued but the job cannot run yet: no network, or no Wi-Fi when that is required. */
    data class Waiting(val queued: Int, val forWifi: Boolean) : DownloadStatus

    data class Running(val progress: DownloadProgress?, val queued: Int) : DownloadStatus
}

/**
 * Offline copies. Downloads are *incremental*: asking for the same albums
 * again only fetches what is missing, including files deleted behind our back.
 *
 * Requests go into the database as queued rows and a WorkManager job drains
 * them, so a big download survives the app being closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineManager(private val graph: AppGraph) {
    private val work = WorkManager.getInstance(graph.app)

    /** songId → absolute path, for the active server. */
    private val paths: StateFlow<Map<String, String>> = graph.database
        .flatMapLatest { db -> db?.downloads()?.doneFlow() ?: flowOf(emptyList()) }
        .map { rows -> rows.mapNotNull { row -> row.path?.let { row.songId to it } }.toMap() }
        .stateIn(graph.scope, SharingStarted.Eagerly, emptyMap())

    val downloadedIds: StateFlow<Set<String>> = paths
        .map { it.keys }
        .stateIn(graph.scope, SharingStarted.Eagerly, emptySet())

    val usage: Flow<DownloadUsage> = graph.database.flatMapLatest { db ->
        db?.downloads()?.usageFlow() ?: flowOf(DownloadUsage(0, 0))
    }

    val queuedCount: StateFlow<Int> = graph.database
        .flatMapLatest { db -> db?.downloads()?.queuedCountFlow() ?: flowOf(0) }
        .stateIn(graph.scope, SharingStarted.Eagerly, 0)

    /** The next tracks in the queue (the first few hundred; the count says how many in all). */
    val queue: Flow<List<DownloadEntity>> = graph.database.flatMapLatest { db ->
        db?.downloads()?.queuedFlow(300) ?: flowOf(emptyList())
    }

    val failed: Flow<List<DownloadEntity>> = graph.database.flatMapLatest { db ->
        db?.downloads()?.failedFlow() ?: flowOf(emptyList())
    }

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _active = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val active: StateFlow<Map<String, ActiveDownload>> = _active.asStateFlow()

    private val workInfos: Flow<List<WorkInfo>> = work.getWorkInfosForUniqueWorkFlow(WORK_NAME)

    val status: StateFlow<DownloadStatus> = combine(
        workInfos,
        queuedCount,
        _progress,
        graph.settings.settings.map { it.offlineWifiOnly }.distinctUntilChanged(),
    ) { infos, queued, progress, wifiOnly ->
        when {
            infos.any { it.state == WorkInfo.State.RUNNING } -> DownloadStatus.Running(progress, queued)
            queued > 0 -> DownloadStatus.Waiting(queued, forWifi = wifiOnly && graph.network.isMetered)
            else -> DownloadStatus.Idle
        }
    }.stateIn(graph.scope, SharingStarted.Eagerly, DownloadStatus.Idle)

    /** The Wi-Fi rule the enqueued job was created with, while this process knows it. */
    private var enqueuedWifiOnly: Boolean? = null

    init {
        // Changing "Wi-Fi only" must apply to downloads already waiting.
        graph.scope.launch {
            graph.settings.settings.map { it.offlineWifiOnly }.distinctUntilChanged().drop(1).collect {
                if (queuedCount.value > 0) startWorker(force = true)
            }
        }
        // Pick up a queue left behind when the app or the job was stopped.
        graph.scope.launch {
            graph.database.filterNotNull().collect { db ->
                val queued = withContext(Dispatchers.IO) { db.downloads().queuedCount() }
                val infos = workInfos.first()
                if (queued > 0 && infos.none { !it.state.isFinished }) startWorker()
            }
        }
    }

    internal fun report(progress: DownloadProgress?) {
        _progress.value = progress
    }

    internal fun reportActive(songId: String, value: ActiveDownload?) {
        _active.update { current -> if (value == null) current - songId else current + (songId to value) }
    }

    internal fun clearActive() {
        _active.value = emptyMap()
    }

    /** The stored file for a song, if there is one and it still exists. */
    fun fileFor(songId: String): File? = paths.value[songId]?.let(::File)?.takeIf { it.isFile }

    fun directoryFor(profileId: String): File {
        val root = graph.app.getExternalFilesDir("offline") ?: File(graph.app.filesDir, "offline")
        return File(root, md5Hex(profileId).take(16)).apply { mkdirs() }
    }

    /** How many of [songs] are not downloaded yet — used to label buttons. */
    fun missing(songs: List<Song>): Int {
        val have = downloadedIds.value
        return songs.count { !it.isRadio && it.id !in have }
    }

    suspend fun download(songs: List<Song>, label: String? = null) {
        val db = graph.database.value ?: return
        val wanted = songs.filter { !it.isRadio }.distinctBy { it.id }
        val pending = withContext(Dispatchers.IO) {
            val rows = HashMap<String, DownloadEntity>()
            wanted.map { it.id }.chunked(SQL_CHUNK).forEach { chunk -> db.downloads().getMany(chunk).forEach { rows[it.songId] = it } }
            wanted.filter { song ->
                val row = rows[song.id]
                row == null || row.state != DownloadState.DONE || row.path == null || !File(row.path).isFile
            }
        }
        if (pending.isEmpty()) {
            graph.messages.success(if (label != null) "$label is already downloaded." else "Everything is already downloaded.")
            return
        }
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            pending.chunked(SQL_CHUNK).forEach { chunk ->
                db.downloads().upsert(
                    chunk.mapIndexed { i, song ->
                        // Keep the order they were asked for: albums download track by track.
                        DownloadEntity(song.id, DownloadState.QUEUED, null, 0, null, now + i, null, null)
                    },
                )
            }
        }
        startWorker()
        val skipped = wanted.size - pending.size
        graph.messages.show(
            buildString {
                append("Downloading ${pending.size} track${if (pending.size == 1) "" else "s"}")
                if (skipped > 0) append(" · $skipped already here")
                if (graph.settings.current.offlineWifiOnly && graph.network.isMetered) append(" · waiting for Wi-Fi")
            },
        )
    }

    /**
     * Make sure a job is draining the queue under the current Wi-Fi rule.
     *
     * A job that is already running with the same rule is left alone and a
     * follow-up is chained behind it, so nothing queued at the last moment is
     * missed. Anything else — a job still waiting for a network it may never
     * get, or one made under the other rule — is replaced.
     */
    fun startWorker(force: Boolean = false) {
        val wifiOnly = graph.settings.current.offlineWifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(constraints).build()
        graph.scope.launch {
            val running = workInfos.first().any { it.state == WorkInfo.State.RUNNING }
            val policy = if (running && !force && enqueuedWifiOnly == wifiOnly) {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            } else {
                ExistingWorkPolicy.REPLACE
            }
            enqueuedWifiOnly = wifiOnly
            work.enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }

    /** Stop downloading and forget everything still queued. */
    fun cancel() {
        work.cancelUniqueWork(WORK_NAME)
        _progress.value = null
        clearActive()
        graph.scope.launch(Dispatchers.IO) { graph.database.value?.downloads()?.clearQueued() }
    }

    /** Take tracks out of the queue before they are fetched. */
    suspend fun dequeue(songIds: List<String>) {
        val db = graph.database.value ?: return
        withContext(Dispatchers.IO) {
            songIds.chunked(SQL_CHUNK).forEach { chunk ->
                val queued = db.downloads().getMany(chunk).filter { it.state != DownloadState.DONE }.map { it.songId }
                if (queued.isNotEmpty()) db.downloads().delete(queued)
            }
        }
    }

    suspend fun retryFailed(): Int {
        val db = graph.database.value ?: return 0
        val count = withContext(Dispatchers.IO) { db.downloads().requeueFailed(System.currentTimeMillis()) }
        if (count > 0) startWorker()
        return count
    }

    suspend fun clearFailed() {
        val db = graph.database.value ?: return
        withContext(Dispatchers.IO) { db.downloads().clearFailed() }
    }

    suspend fun remove(songIds: List<String>): Int {
        val db = graph.database.value ?: return 0
        return withContext(Dispatchers.IO) {
            var removed = 0
            songIds.chunked(SQL_CHUNK).forEach { chunk ->
                db.downloads().getMany(chunk).forEach { row ->
                    row.path?.let { File(it).delete() }
                    removed++
                }
                db.downloads().delete(chunk)
            }
            removed
        }
    }

    suspend fun removeAll(): Int {
        val db = graph.database.value ?: return 0
        cancel()
        return withContext(Dispatchers.IO) {
            val all = db.downloads().allDone()
            all.forEach { row -> row.path?.let { File(it).delete() } }
            all.map { it.songId }.chunked(SQL_CHUNK).forEach { db.downloads().delete(it) }
            db.downloads().clearFailed()
            all.size
        }
    }

    companion object {
        const val WORK_NAME = "kultr.downloads"
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 7201
        const val EXTRA_OPEN_DOWNLOADS = "kultr.open_downloads"
    }
}

/** Drains the queue of requested downloads. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val graph = KultrApp.graph

    override suspend fun doWork(): Result {
        val db = graph.database.value ?: return Result.success()
        val client = graph.auth.client.value ?: return Result.retry()
        val profile = graph.auth.active.value ?: return Result.success()
        val directory = graph.offline.directoryFor(profile.id)
        val settings = graph.settings.current
        val limit = Semaphore(settings.offlineConcurrency.coerceIn(1, 8))

        var done = 0
        var failed = 0
        var bytes = 0L
        runCatching { setForeground(foregroundInfo("Preparing downloads…", 0, 0)) }

        try {
            while (true) {
                val batch = db.downloads().queued(32)
                if (batch.isEmpty()) break
                // Everything finished so far plus everything still queued, so
                // the total follows additions made while this runs.
                var total = done + failed + db.downloads().queuedCount()
                graph.offline.report(DownloadProgress(done, total, failed, bytes, ""))
                coroutineScope {
                    batch.map { row ->
                        async {
                            limit.withPermit {
                                val result = fetch(db, client, row, directory)
                                val progress = synchronized(this@DownloadWorker) {
                                    if (result >= 0) {
                                        done++
                                        bytes += result
                                    } else {
                                        failed++
                                    }
                                    total = maxOf(total, done + failed)
                                    DownloadProgress(done, total, failed, bytes, row.songId)
                                }
                                graph.offline.report(progress)
                                runCatching { setForeground(foregroundInfo("Downloading for offline", done + failed, total)) }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            graph.offline.report(null)
            graph.offline.clearActive()
        }
        if (done + failed > 0) {
            graph.messages.show(
                if (failed == 0) "Downloaded $done track${if (done == 1) "" else "s"}." else "Downloaded $done, $failed failed.",
                if (failed == 0) MessageKind.SUCCESS else MessageKind.WARNING,
            )
        }
        return Result.success()
    }

    /** Download one track; returns its size, or -1 on failure. */
    private suspend fun fetch(db: KultrDatabase, client: SubsonicClient, row: DownloadEntity, directory: File): Long {
        val song = db.library().song(row.songId)?.toSong()
            ?: runCatching { client.getSong(row.songId) }.getOrNull()
        return try {
            if (song == null) throw IOException("This track is no longer on the server.")
            val settings = graph.settings.current
            val transcode = settings.offlineBitrate > 0
            val url = if (transcode) {
                client.streamUrl(song.id, settings.offlineBitrate, settings.preferredFormat.ifBlank { "mp3" })
            } else {
                client.downloadUrl(song.id)
            }
            val extension = when {
                transcode -> settings.preferredFormat.ifBlank { "mp3" }
                else -> song.suffix?.takeIf { it.isNotBlank() } ?: "audio"
            }
            val safeId = song.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(directory, "$safeId.$extension")
            val partial = File(directory, "$safeId.part")
            graph.offline.reportActive(song.id, ActiveDownload(song, 0, estimateSize(song, if (transcode) settings.offlineBitrate else null)))
            val response = withContext(Dispatchers.IO) {
                graph.http.newCall(Request.Builder().url(url).build()).execute()
            }
            val size = response.use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val contentType = res.header("Content-Type")
                if (contentType != null && contentType.contains("json")) throw IOException("The server refused the download.")
                val length = res.body.contentLength()
                val expected = if (length > 0) length else estimateSize(song, if (transcode) settings.offlineBitrate else null)
                withContext(Dispatchers.IO) {
                    partial.outputStream().use { out ->
                        res.body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            var reported = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (copied - reported >= 256 * 1024) {
                                    reported = copied
                                    graph.offline.reportActive(song.id, ActiveDownload(song, copied, maxOf(expected, copied)))
                                    ensureActive()
                                }
                            }
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!partial.renameTo(target)) throw IOException("Could not save the file.")
                }
                target.length()
            }
            db.downloads().upsert(
                listOf(
                    row.copy(
                        state = DownloadState.DONE,
                        path = target.absolutePath,
                        size = size,
                        contentType = song.contentType,
                        savedAt = System.currentTimeMillis(),
                        error = null,
                    ),
                ),
            )
            size
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            db.downloads().upsert(listOf(row.copy(state = DownloadState.FAILED, error = describeError(err))))
            -1
        } finally {
            graph.offline.reportActive(row.songId, null)
        }
    }

    /** A size guess for a progress bar when the server sends no length (transcoding). */
    private fun estimateSize(song: Song, transcodeKbps: Int?): Long {
        song.size?.takeIf { transcodeKbps == null && it > 0 }?.let { return it }
        val seconds = song.duration ?: return 0
        val kbps = transcodeKbps ?: song.bitRate ?: 320
        return seconds.toLong() * kbps * 1000 / 8
    }

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val open = PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OfflineManager.EXTRA_OPEN_DOWNLOADS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(applicationContext, OfflineManager.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kultr)
            .setContentTitle(title)
            .setContentText(if (total > 0) "$done of $total" else null)
            .setProgress(total, done, total == 0)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(OfflineManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(OfflineManager.NOTIFICATION_ID, notification)
        }
    }
}
