package app.kultr.android.data

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kultr.android.AppGraph
import app.kultr.android.KultrApp
import app.kultr.android.data.db.RoomLibraryStore
import app.kultr.android.data.db.syncState
import app.kultr.core.api.describeError
import app.kultr.core.sync.LibrarySync
import app.kultr.core.sync.ListeningSync
import app.kultr.core.sync.SyncMode
import app.kultr.core.sync.SyncProgress
import app.kultr.core.sync.SyncSummary
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _listening = MutableStateFlow(false)

    /** True while plays are being sent and play counts read back. */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _listeningVersion = MutableStateFlow(0)

    /** Bumped when listening data changed on the way in or out, so views re-read it. */
    val listeningVersion: StateFlow<Int> = _listeningVersion.asStateFlow()

    private var lastListening = 0L

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

    /** Right after signing in to a server whose library is not on this phone yet, build it. */
    fun startFirstSyncIfNeeded() {
        val profileId = graph.auth.active.value?.id ?: return
        graph.scope.launch {
            val state = withContext(Dispatchers.IO) { graph.databaseFor(profileId).syncState() }
            if (state.lastCheck == null && graph.auth.active.value?.id == profileId) start(SyncMode.FULL, quiet = false)
        }
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
            // Plays made offline go up, and anything played elsewhere since comes down.
            refreshListeningNow()
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
     * Send plays still waiting for the server, then read back play counts and
     * last-played times for albums played since (here or on another device).
     * Cheap; skipped if it ran in the last minute unless [force]d.
     */
    fun refreshListening(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_listening.value || (!force && now - lastListening < 60_000)) return
        lastListening = now
        graph.scope.launch { refreshListeningNow() }
    }

    /** [refreshListening], waiting for it. Returns false if the server could not be reached. */
    suspend fun refreshListeningNow(): Boolean {
        val client = graph.auth.client.value ?: return false
        val db = graph.database.value ?: return false
        if (_listening.value) return true
        _listening.value = true
        return try {
            val sent = graph.scrobbles.flush()
            // A library that was never synced has nothing to compare against yet.
            val pulled = if (withContext(Dispatchers.IO) { db.syncState() }.lastCheck == null) {
                0
            } else {
                withContext(Dispatchers.IO) { ListeningSync(client, RoomLibraryStore(db)).pull() }.albumsChanged
            }
            if (sent > 0 || pulled > 0) _listeningVersion.value++
            true
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            false
        } finally {
            _listening.value = false
        }
    }

    /**
     * On startup: if the library has been synced before and the server looks
     * different, pull in the changes. A never-synced library waits for the
     * person to press the button, since the first sync is the expensive one.
     * Listening data is exchanged every time the app comes to the front,
     * which is usually when you come back from another device.
     */
    fun onAppStart() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = refreshListening()
            },
        )
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
            if (check.changed) graph.sync.runNow(SyncMode.CHECK, quiet = true) else graph.sync.refreshListeningNow()
            Result.success()
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
