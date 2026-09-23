package app.kultr.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import app.kultr.android.AppGraph
import app.kultr.android.KultrApp
import app.kultr.android.MainActivity
import app.kultr.android.R
import app.kultr.core.api.Song
import app.kultr.core.engine.EngineHost
import app.kultr.core.engine.EngineStatus
import app.kultr.core.engine.PlaybackEngine
import app.kultr.core.engine.QueueItem
import app.kultr.core.engine.TrackChangeReason
import app.kultr.core.injekt.PlanContext
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.injekt.planTransition
import app.kultr.core.settings.Settings
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class SavedSession(
    val profileId: String,
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
)

/** The queue and position, kept across restarts when "Resume where you left off" is on. */
private class SessionStore(context: Context) {
    private val file = File(context.filesDir, "session.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun save(session: SavedSession) {
        runCatching {
            val tmp = File(file.parentFile, "session.json.tmp")
            tmp.writeText(json.encodeToString(SavedSession.serializer(), session))
            tmp.renameTo(file)
        }
    }

    fun load(): SavedSession? = runCatching {
        if (file.isFile) json.decodeFromString(SavedSession.serializer(), file.readText()) else null
    }.getOrNull()

    fun clear() {
        file.delete()
    }
}

/** Counts how long a track has really been listened to, for scrobbling. */
private class ListeningTracker(private val graph: AppGraph) {
    private var song: Song? = null
    private var listenedMs = 0L
    private var scrobbled = false

    fun start(song: Song) {
        this.song = song
        listenedMs = 0
        scrobbled = false
        graph.scrobbles.nowPlaying(song)
    }

    fun advance(deltaMs: Long, durationMs: Long, positionMs: Long) {
        val current = song ?: return
        if (scrobbled || current.isRadio) return
        listenedMs += deltaMs.coerceIn(0, 1_000)
        val length = if (durationMs > 0) durationMs else (current.duration ?: 0) * 1000L
        val threshold = (length / 2).coerceIn(20_000, 240_000)
        if (listenedMs >= threshold) {
            scrobbled = true
            graph.scrobbles.played(current, (listenedMs / 1000).toInt(), positionMs >= length - 5_000, "")
        }
    }
}

/**
 * Hosts Kultr's engine and exposes it as a media session: the notification,
 * lock screen, Bluetooth and headset buttons, Android Auto and the app's own
 * UI all talk to it through Media3.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private lateinit var graph: AppGraph
    private lateinit var player: KultrPlayer
    private lateinit var engine: PlaybackEngine
    private lateinit var decks: List<ExoDeck>
    private lateinit var browser: LibraryBrowser
    private lateinit var sessions: SessionStore
    private lateinit var tracker: ListeningTracker
    private var session: MediaLibrarySession? = null

    /** What the session controls: [player] here, or a Cast receiver while one is connected. */
    private lateinit var sessionPlayer: Player
    private var castPlayer: CastPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val eq = EqState()
    private var lastTickAt = 0L
    private var lastSavedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            scheduleTick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        graph = KultrApp.graph
        browser = LibraryBrowser(graph)
        sessions = SessionStore(this)
        tracker = ListeningTracker(graph)

        val sources = DeckSources(this, graph)
        decks = listOf(ExoDeck(this, "A", sources, eq), ExoDeck(this, "B", sources, eq))
        player = KultrPlayer(
            context = this,
            looper = Looper.getMainLooper(),
            toMediaItem = { song -> MediaItems.from(song, graph.auth.client.value) },
            onDuck = { level -> decks.forEach { it.setDuck(level) } },
        )
        engine = PlaybackEngine(decks[0], decks[1], host, scope)
        player.engine = engine
        applyEq(graph.settings.current)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_stat_kultr) },
        )
        sessionPlayer = buildCastPlayer() ?: player
        session = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        observe()
        restore()
    }

    /**
     * Wrap the local player so playback moves to a Chromecast when a Cast
     * session starts, and back when it ends. Null where Cast is unavailable.
     */
    private fun buildCastPlayer(): CastPlayer? = runCatching {
        val remote = RemoteCastPlayer.Builder(this)
            .setMediaItemConverter(KultrMediaItemConverter())
            .build()
        CastPlayer.Builder(this)
            .setLocalPlayer(player)
            .setRemotePlayer(remote)
            .setTransferCallback(KultrTransferCallback { song -> CastSupport.streamFor(song, graph.auth.client.value) })
            .build()
    }.getOrNull().also { castPlayer = it }

    private fun observe() {
        var previous = graph.settings.current
        scope.launch {
            graph.settings.settings.collect { now ->
                if (now == previous) return@collect
                applyEq(now)
                engine.onSettingsChanged(planSignature(now) != planSignature(previous))
                previous = now
            }
        }
        // A different server has a different library; its queue makes no sense here.
        scope.launch {
            graph.auth.active.map { it?.id }.distinctUntilChanged().drop(1).collect {
                engine.setQueue(emptyList(), 0, 0)
                sessions.clear()
            }
        }
        scope.launch {
            graph.hub.sleepTimer.collect { timer ->
                engine.pauseAtEndOfTrack = timer?.endOfTrack == true
                scheduleTick()
            }
        }
    }

    private fun planSignature(s: Settings): List<Any> = listOf(
        s.crossfadeEnabled, s.crossfadeSeconds, s.crossfadeCurve, s.gapless, s.injektEnabled, s.injektBeatMatch,
        s.injektBassSwap, s.injektHarmonic, s.injektMaxTempoShift, s.injektTempoRamp, s.injektTempoBlend,
        s.injektBars, s.injektSkipIntro,
    )

    private fun applyEq(s: Settings) {
        eq.update(s.eqEnabled, s.eqBandGains, s.eqPreamp)
    }

    private fun restore() {
        if (!graph.settings.current.resumeOnStart) return
        val saved = sessions.load() ?: return
        if (saved.profileId != graph.auth.active.value?.id || saved.songs.isEmpty()) return
        engine.setQueue(engine.newItems(saved.songs), saved.index, saved.positionMs)
    }

    private fun saveSession() {
        val profile = graph.auth.active.value ?: return
        val queue = engine.queue
        if (queue.isEmpty()) {
            sessions.clear()
            return
        }
        // Keep the saved queue a sensible size around the current track.
        val from = (engine.index - 100).coerceAtLeast(0)
        val songs = queue.drop(from).take(500).map { it.song }
        sessions.save(SavedSession(profile.id, songs, engine.index - from, engine.positionMs))
    }

    // ---------------------------------------------------------------- tick --

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        var interval = engine.tickIntervalMs()
        if (interval == 0L && graph.hub.sleepTimer.value?.endsAtMillis != null && engine.playWhenReady) interval = 1_000
        if (interval > 0) handler.postDelayed(tick, interval)
    }

    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastTickAt == 0L) 0 else now - lastTickAt
        lastTickAt = now
        engine.tick()
        if (engine.playWhenReady && engine.status == EngineStatus.READY) {
            tracker.advance(delta, engine.durationMs, engine.positionMs)
        }
        graph.hub.sleepTimer.value?.endsAtMillis?.let { ends ->
            if (System.currentTimeMillis() >= ends) {
                graph.hub.clearSleepTimer()
                sessionPlayer.pause()
                graph.messages.show("Sleep timer finished. Good night.")
            }
        }
        if (engine.playWhenReady && now - lastSavedAt > 10_000) {
            lastSavedAt = now
            saveSession()
        }
    }

    // ---------------------------------------------------------------- host --

    private val host = object : EngineHost {
        override fun now(): Long = SystemClock.elapsedRealtime()

        override fun settings(): Settings = graph.settings.current

        override suspend fun plan(current: Song, next: Song, context: PlanContext): TransitionPlan {
            val s = graph.settings.current
            if (!s.injektEnabled) return planTransition(current, next, context, s, null, null)
            val a = withTimeoutOrNull(25_000) { graph.analysis.getOrAnalyse(current) }
            val b = withTimeoutOrNull(25_000) { graph.analysis.getOrAnalyse(next) }
            // Analysis can take a while; plan against where playback is now.
            val fresh = context.copy(currentTime = engine.positionMs / 1000.0)
            return planTransition(current, next, fresh, s, a, b)
        }

        override suspend fun extendQueue(seed: Song, recent: List<Song>): List<Song> =
            withContext(Dispatchers.IO) { AutoQueue(graph).build(seed, recent) }

        override fun onStateChanged() {
            player.refresh()
            graph.hub.publishTransition(
                TransitionInfo(engine.currentPlan, engine.activeTransition, engine.currentItem?.song?.id),
            )
            val timer = graph.hub.sleepTimer.value
            if (timer?.endOfTrack == true && !engine.pauseAtEndOfTrack) graph.hub.clearSleepTimer()
            if (!engine.playWhenReady) saveSession()
            scheduleTick()
        }

        override fun onTrackStarted(item: QueueItem, reason: TrackChangeReason) {
            tracker.start(item.song)
            graph.analysis.analyseAhead(item.song)
            val next = engine.queue.getOrNull(engine.index + 1)
            if (next != null) graph.analysis.analyseAhead(next.song)
            saveSession()
        }

        override fun onError(message: String) {
            graph.messages.error(message)
        }
    }

    // ------------------------------------------------------------- session --

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSession()
        // Keep going while something plays, here or on a Cast receiver.
        if (!sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveSession()
        handler.removeCallbacks(tick)
        session?.release()
        session = null
        // The Cast wrapper releases the local player along with its own.
        castPlayer?.release() ?: player.release()
        decks.forEach { it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            scope.future { LibraryResult.ofItem(this@PlaybackService.browser.root(), params) }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = this@PlaybackService.browser.children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val item = this@PlaybackService.browser.item(mediaId) ?: this@PlaybackService.browser.root()
            LibraryResult.ofItem(item, null)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = this@PlaybackService.browser.search(query).size
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = this@PlaybackService.browser.search(query)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            this@PlaybackService.browser.resolve(mediaItems).toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val resolved = this@PlaybackService.browser.resolve(mediaItems)
            val index = if (resolved.size == mediaItems.size) startIndex else 0
            MediaSession.MediaItemsWithStartPosition(resolved, index, startPositionMs)
        }
    }
}
