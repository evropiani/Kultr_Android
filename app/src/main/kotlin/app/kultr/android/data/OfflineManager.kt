package app.kultr.android.data

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kultr.android.KultrApp
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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

/**
 * Offline copies. Downloads are *incremental*: asking for the same albums
 * again only fetches what is missing, including files deleted behind our back.
 *
 * Requests go into the database as queued rows and a WorkManager job drains
 * them, so a big download survives the app being closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineManager(private val graph: AppGraph) {
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

    val queuedCount: Flow<Int> = graph.database.flatMapLatest { db -> db?.downloads()?.queuedCountFlow() ?: flowOf(0) }

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    internal fun report(progress: DownloadProgress?) {
        _progress.value = progress
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
                    chunk.map {
                        DownloadEntity(it.id, DownloadState.QUEUED, null, 0, null, now, null, null)
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

    fun startWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (graph.settings.current.offlineWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(graph.app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(graph.app).cancelUniqueWork(WORK_NAME)
        _progress.value = null
        graph.scope.launch(Dispatchers.IO) { graph.database.value?.downloads()?.clearQueued() }
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
        var total = 0
        runCatching { setForeground(foregroundInfo("Preparing downloads…", 0, 0)) }

        try {
            while (true) {
                val batch = db.downloads().queued(32)
                if (batch.isEmpty()) break
                total = maxOf(total, done + failed + batch.size)
                coroutineScope {
                    batch.map { row ->
                        async {
                            limit.withPermit {
                                val result = fetch(db, client, row, directory)
                                synchronized(this@DownloadWorker) {
                                    if (result >= 0) {
                                        done++
                                        bytes += result
                                    } else {
                                        failed++
                                    }
                                }
                                val progress = DownloadProgress(done, total, failed, bytes, row.songId)
                                graph.offline.report(progress)
                                runCatching { setForeground(foregroundInfo("Downloading for offline", done + failed, total)) }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            graph.offline.report(null)
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
            val response = withContext(Dispatchers.IO) {
                graph.http.newCall(Request.Builder().url(url).build()).execute()
            }
            val size = response.use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val contentType = res.header("Content-Type")
                if (contentType != null && contentType.contains("json")) throw IOException("The server refused the download.")
                withContext(Dispatchers.IO) {
                    partial.outputStream().use { out -> res.body.byteStream().use { it.copyTo(out, 64 * 1024) } }
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
        }
    }

    private fun foregroundInfo(title: String, done: Int, total: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, OfflineManager.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kultr)
            .setContentTitle(title)
            .setContentText(if (total > 0) "$done of $total" else null)
            .setProgress(total, done, total == 0)
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
