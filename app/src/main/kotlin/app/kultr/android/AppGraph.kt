package app.kultr.android

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.kultr.android.data.AnalysisManager
import app.kultr.android.data.AuthRepository
import app.kultr.android.data.LibraryRepository
import app.kultr.android.data.NetworkMonitor
import app.kultr.android.data.OfflineManager
import app.kultr.android.data.Scrobbles
import app.kultr.android.data.SettingsRepository
import app.kultr.android.data.SyncManager
import app.kultr.android.data.UiMessages
import app.kultr.android.data.buildHttpClient
import app.kultr.android.data.db.KultrDatabase
import app.kultr.android.playback.PlaybackHub
import app.kultr.android.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * The app's object graph. Built once in [KultrApp] and reached through
 * [KultrApp.graph] from the service, workers and UI.
 */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http = buildHttpClient()
    val network = NetworkMonitor(app)
    val settings = SettingsRepository(app)
    val messages = UiMessages()
    val auth = AuthRepository(app, http, scope)

    private val databases = HashMap<String, KultrDatabase>()

    /** The open library for the active server. Opened on demand, kept for the process. */
    @Synchronized
    fun databaseFor(profileId: String): KultrDatabase =
        databases.getOrPut(profileId) { KultrDatabase.open(app, profileId) }

    val database: StateFlow<KultrDatabase?> = auth.active
        .map { it?.id }
        .distinctUntilChanged()
        .map { id -> id?.let(::databaseFor) }
        .stateIn(scope, SharingStarted.Eagerly, auth.active.value?.id?.let(::databaseFor))

    /** Close and delete everything stored for a server that has been forgotten. */
    @Synchronized
    fun deleteDataFor(profileId: String) {
        databases.remove(profileId)?.close()
        app.deleteDatabase(KultrDatabase.fileName(profileId))
        offline.directoryFor(profileId).deleteRecursively()
    }

    val library = LibraryRepository(this)
    val scrobbles = Scrobbles(this)
    val sync = SyncManager(this)
    val offline = OfflineManager(this)
    val analysis = AnalysisManager(this)
    val hub = PlaybackHub()
    val player = PlayerConnection(this)

    /** Streamed audio is cached, so replays and seeks do not refetch. */
    val mediaCache: SimpleCache by lazy {
        val bytes = settings.current.streamCacheMb.coerceIn(64, 16_384).toLong() * 1024 * 1024
        SimpleCache(File(app.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(bytes), StandaloneDatabaseProvider(app))
    }

    fun clearMediaCache() {
        val cache = mediaCache
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
    }
}
