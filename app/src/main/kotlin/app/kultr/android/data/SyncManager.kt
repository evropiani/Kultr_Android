package app.kultr.android.data

import app.kultr.android.AppGraph
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kultr.android.KultrApp
import app.kultr.android.data.db.RoomLibraryStore
import app.kultr.android.data.db.syncState
import app.kultr.core.api.describeError
import app.kultr.core.sync.LibrarySync
import app.kultr.core.sync.SyncMode
import app.kultr.core.sync.SyncProgress
import app.kultr.core.sync.SyncSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Runs library syncs and remembers how the last one went, for the Sync page. */
class SyncManager(private val graph: AppGraph) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _progress = MutableStateFlow<SyncProgress?>(null)
    val progress: StateFlow<SyncProgress?> = _progress.asStateFlow()

    private val _summary = MutableStateFlow<SyncSummary?>(null)
    val summary: StateFlow<SyncSummary?> = _summary.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null

    /** Start a sync unless one is already running. */
    fun start(mode: SyncMode, quiet: Boolean = false) {
        if (_running.value) {
            if (!quiet) graph.messages.show("A sync is already running.")
            return
        }
        job = graph.scope.launch { runNow(mode, quiet) }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Run a sync and wait for it. Returns null on success, or an error message. */
    suspend fun runNow(mode: SyncMode, quiet: Boolean = false): String? {
        val client = graph.auth.client.value ?: return "Not signed in."
        val db = graph.database.value ?: return "Not signed in."
        if (_running.value) return null
        _running.value = true
        _error.value = null
        return try {
            val summary = withContext(Dispatchers.IO) {
                LibrarySync(client, RoomLibraryStore(db)).run(
                    mode = mode,
                    includePlaylistContents = graph.settings.current.syncPlaylistContents,
                    onProgress = { _progress.value = it },
                )
            }
            _summary.value = summary
            if (!quiet) {
                graph.messages.success(
                    when {
                        summary.upToDate -> "Everything is up to date."
                        summary.mode == SyncMode.FULL -> "Library synced: ${summary.counts.songs} tracks in ${summary.counts.albums} albums."
                        else -> "Updated: ${summary.albumsAdded} new, ${summary.albumsUpdated} changed, ${summary.albumsRemoved} removed."
                    },
                )
            }
            graph.scrobbles.flush()
            null
        } catch (err: CancellationException) {
            if (!quiet) graph.messages.show("Sync cancelled.")
            null
        } catch (err: Exception) {
            val message = describeError(err)
            _error.value = message
            if (!quiet) graph.messages.error("Sync failed: $message")
            message
        } finally {
            _running.value = false
        }
    }

    /**
     * On startup: if the library has been synced before and the server looks
     * different, pull in the changes. A never-synced library waits for the
     * person to press the button, since the first sync is the expensive one.
     */
    fun onAppStart() {
        if (!graph.settings.current.autoSyncOnStart) return
        graph.scope.launch {
            val client = graph.auth.client.value ?: return@launch
            val db = graph.database.value ?: return@launch
            val state = withContext(Dispatchers.IO) { db.syncState() }
            if (state.lastCheck == null) return@launch
            val check = runCatching {
                withContext(Dispatchers.IO) { LibrarySync(client, RoomLibraryStore(db)).quickCheck() }
            }.getOrNull() ?: return@launch
            if (check.changed) runNow(SyncMode.CHECK, quiet = true)
            graph.scrobbles.flush()
        }
        schedulePeriodic(graph.app)
    }

    fun schedulePeriodic(context: Context) {
        val minutes = graph.settings.current.autoSyncMinutes
        val work = WorkManager.getInstance(context)
        if (minutes <= 0) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val PERIODIC = "kultr.sync.periodic"
    }
}

/** Background "anything new on the server?" check. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = KultrApp.graph
        val client = graph.auth.client.value ?: return Result.success()
        val db = graph.database.value ?: return Result.success()
        if (db.syncState().lastCheck == null) return Result.success()
        return try {
            val check = LibrarySync(client, RoomLibraryStore(db)).quickCheck()
            if (check.changed) graph.sync.runNow(SyncMode.CHECK, quiet = true)
            graph.scrobbles.flush()
            Result.success()
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
