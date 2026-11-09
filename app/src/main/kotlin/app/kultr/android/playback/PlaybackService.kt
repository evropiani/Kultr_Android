package app.kultr.android.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.kultr.android.AppGraph
import app.kultr.android.KultrActivity
import app.kultr.android.KultrApp
import app.kultr.android.R
import app.kultr.android.widget.NowPlayingWidget
import app.kultr.android.widget.WidgetActionReceiver
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.engine.EngineHost
import app.kultr.core.engine.EngineStatus
import app.kultr.core.engine.PlayMode
import app.kultr.core.engine.PlaybackEngine
import app.kultr.core.engine.QueueItem
import app.kultr.core.engine.RepeatMode
import app.kultr.core.engine.TrackChangeReason
import app.kultr.core.injekt.PlanContext
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.injekt.planTransition
import app.kultr.core.karousel.calmestFirst
import app.kultr.core.karousel.calmness
import app.kultr.core.settings.Settings
import app.kultr.core.util.t
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    /** Which of [songs] Karousel added. */
    val karousel: List<Int> = emptyList(),
)

private fun SavedSession.mediaItems(client: SubsonicClient?): List<MediaItem> {
    val added = karousel.toHashSet()
    return songs.mapIndexed { i, song -> MediaItems.from(song, client, i in added) }
}

/** The queue and position, kept across restarts when "Resume where you left off" is on. */
private class SessionStore(context: Context) {
    private val file = File(context.filesDir, SESSION_FILE)
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

/**
 * Counts how much of a track has really been heard, for sending the play.
 *
 * Measured by how far the track moved on between ticks, not by how often
 * the tick ran: a late tick (a busy phone, the app in the background) still
 * counts in full. A jump the clock cannot account for is a seek, and is not
 * counted.
 */
private class ListeningTracker(private val graph: AppGraph) {
    private var song: Song? = null
    private var listenedMs = 0L
    private var scrobbled = false

    /** Position at the previous tick; null after a new track or a pause. */
    private var lastPositionMs: Long? = null

    fun start(song: Song) {
        this.song = song
        listenedMs = 0
        scrobbled = false
        lastPositionMs = null
        graph.scrobbles.nowPlaying(song)
    }

    /** Not playing: whatever happens to the position until it plays again is not listening. */
    fun hold() {
        lastPositionMs = null
    }

    fun advance(wallMs: Long, durationMs: Long, positionMs: Long) {
        val current = song ?: return
        val last = lastPositionMs
        lastPositionMs = positionMs
        if (scrobbled || current.isRadio || last == null) return
        val moved = positionMs - last
        // Tempo-matched playback runs a few percent fast; allow for timer jitter too.
        if (moved > 0 && moved <= wallMs * 1.15 + 500) listenedMs += moved
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
    private lateinit var stations: StationPlayer
    private lateinit var decks: List<ExoDeck>
    private lateinit var browser: LibraryBrowser
    private lateinit var sessions: SessionStore
    private lateinit var tracker: ListeningTracker
    private val karousel by lazy { KarouselMusic(graph) }

    /** The sleep timer is in its last 15 minutes: Karousel plays its calmest picks first. */
    @Volatile private var windingDown = false

    /** The songs hearted in the library, for the like button in the player banner; null until read. */
    private var starredIds: Set<String>? = null

    /** A like pressed in the banner, shown at once while the library catches up. */
    private var likePressed: Pair<String, Boolean>? = null
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
            toMediaItem = { song, karousel -> MediaItems.from(song, graph.auth.client.value, karousel) },
            onDuck = { level -> decks.forEach { it.setDuck(level) } },
            onQueueReplaced = { graph.hub.setStation(null) },
        )
        engine = PlaybackEngine(decks[0], decks[1], host, scope)
        player.engine = engine
        stations = StationPlayer(graph, engine) { player.play() }
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
                    Intent(this, KultrActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(KultrActivity.EXTRA_OPEN_PLAYER, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        observe()
        restore()

        // The home screen widget follows whatever the session plays, here or on a Cast receiver.
        sessionPlayer.addListener(widgetListener)
        sessionPlayer.addListener(bannerListener)
        refreshBanner()
        NowPlayingWidget.publish(this, NowPlayingWidget.stateOf(sessionPlayer))
        current = this
    }

    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                NowPlayingWidget.publish(this@PlaybackService, NowPlayingWidget.stateOf(player))
            }
        }
    }

    // ------------------------------------------------------- player banner --
    //
    // The system's player banner (the media notification, the lock screen,
    // Samsung's Now Bar) gets two buttons beside the usual three: on the left
    // a mode button that moves through off, shuffle, repeat, repeat one and
    // Karousel; on the right a heart to like the song or take the like back.

    private val bannerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                refreshBanner()
            }
        }
    }

    private fun currentSong(): Song? = sessionPlayer.currentMediaItem?.let(MediaItems::songOf)

    private fun playMode(karousel: Boolean = graph.settings.current.karousel): PlayMode = PlayMode.of(
        shuffle = sessionPlayer.shuffleModeEnabled,
        repeat = when (sessionPlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
        karousel = karousel,
    )

    private fun isLiked(song: Song): Boolean =
        likePressed?.takeIf { it.first == song.id }?.second ?: starredIds?.let { song.id in it } ?: song.isStarred

    /** The banner's buttons for the player as it is, or as [mode] once a press has set it. */
    private fun bannerButtons(mode: PlayMode = playMode()): List<CommandButton> {
        val modeButton = CommandButton.Builder(
            when (mode) {
                PlayMode.OFF -> CommandButton.ICON_SHUFFLE_OFF
                PlayMode.SHUFFLE -> CommandButton.ICON_SHUFFLE_ON
                PlayMode.REPEAT -> CommandButton.ICON_REPEAT_ALL
                PlayMode.REPEAT_ONE -> CommandButton.ICON_REPEAT_ONE
                PlayMode.KAROUSEL -> CommandButton.ICON_UNDEFINED
            },
        )
            .apply { if (mode == PlayMode.KAROUSEL) setCustomIconResId(R.drawable.ic_banner_karousel) }
            .setDisplayName(
                when (mode) {
                    PlayMode.OFF -> t("Shuffle off")
                    PlayMode.SHUFFLE -> t("Shuffle on")
                    PlayMode.REPEAT -> t("Repeat the queue")
                    PlayMode.REPEAT_ONE -> t("Repeat this track")
                    PlayMode.KAROUSEL -> t("Karousel on")
                },
            )
            .setSessionCommand(MODE_COMMAND)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build()
        val song = currentSong()?.takeIf { !it.isRadio } ?: return listOf(modeButton)
        val liked = isLiked(song)
        val likeButton = CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) t("Remove from favourites") else t("Add to favourites"))
            .setSessionCommand(LIKE_COMMAND)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build()
        return listOf(modeButton, likeButton)
    }

    private fun refreshBanner(mode: PlayMode = playMode()) {
        session?.setMediaButtonPreferences(bannerButtons(mode))
    }

    /** The mode button: on to the next mode, each one on alone. */
    private fun nextMode() {
        val next = playMode().next
        val player = sessionPlayer
        if (graph.settings.current.karousel != next.karousel) graph.settings.update { it.copy(karousel = next.karousel) }
        if (player.shuffleModeEnabled != next.shuffle) player.shuffleModeEnabled = next.shuffle
        val repeat = when (next.repeat) {
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        if (player.repeatMode != repeat) player.repeatMode = repeat
        // The setting is saved in a moment; show the new mode now.
        refreshBanner(next)
    }

    /** The heart: like the song playing, or take the like back. */
    private fun toggleLike() {
        val song = currentSong()?.takeIf { !it.isRadio } ?: return
        val liked = !isLiked(song)
        likePressed = song.id to liked
        refreshBanner()
        scope.launch {
            val error = graph.library.setStarred(song, liked)
            if (error != null) {
                likePressed = null
                refreshBanner()
                graph.messages.error(error)
            }
        }
    }

    /** A button on the home screen widget, sent while this service is running. */
    fun onWidgetAction(action: String) {
        val target = sessionPlayer
        when (action) {
            WidgetActionReceiver.ACTION_PLAY_PAUSE -> when {
                target.playWhenReady && target.playbackState != Player.STATE_ENDED -> target.pause()
                target.mediaItemCount == 0 -> resumeFromSaved(target)
                else -> {
                    if (target.playbackState == Player.STATE_IDLE) target.prepare()
                    if (target.playbackState == Player.STATE_ENDED) target.seekToDefaultPosition(0)
                    target.play()
                }
            }
            WidgetActionReceiver.ACTION_NEXT -> target.seekToNext()
            WidgetActionReceiver.ACTION_PREVIOUS -> target.seekToPrevious()
        }
    }

    private fun resumeFromSaved(target: Player) {
        val saved = savedForProfile() ?: return
        val client = graph.auth.client.value
        target.setMediaItems(saved.mediaItems(client), saved.index, saved.positionMs)
        target.prepare()
        target.play()
    }

    /** The saved queue, if it belongs to the server that is signed in now. */
    private fun savedForProfile(): SavedSession? =
        sessions.load()?.takeIf { it.profileId == graph.auth.active.value?.id && it.songs.isNotEmpty() }

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
                if (now.karousel != previous.karousel) {
                    // Switching Karousel off ends a station too.
                    if (!now.karousel) graph.hub.setStation(null)
                    engine.onKarouselChanged(now.karousel)
                    refreshBanner(playMode(now.karousel))
                }
                previous = now
            }
        }
        scope.launch {
            graph.library.starredSongs().map { songs -> songs.mapTo(HashSet()) { it.id } }.distinctUntilChanged().collect { ids ->
                starredIds = ids
                // The library has caught up with a like pressed in the banner.
                likePressed?.let { (id, liked) -> if ((id in ids) == liked) likePressed = null }
                refreshBanner()
            }
        }
        // A different server has a different library; its queue makes no sense here.
        scope.launch {
            graph.auth.active.map { it?.id }.distinctUntilChanged().drop(1).collect {
                graph.hub.setStation(null)
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
        // Wind down: in a timed sleep timer's last 15 minutes, Karousel's calmest picks come first.
        scope.launch {
            graph.hub.sleepTimer.collectLatest { timer ->
                windingDown = false
                val ends = timer?.endsAtMillis ?: return@collectLatest
                delay(ends - WIND_DOWN_MS - System.currentTimeMillis())
                if (!graph.settings.current.sleepWindDown) return@collectLatest
                windingDown = true
                val known = graph.analysis.cachedMany(engine.queue.filter { it.karousel }.map { it.song.id })
                engine.reorderKarousel { calmness(known[it.id]) }
            }
        }
        scope.launch { graph.hub.mixRequests.collect { engine.mixNow() } }
        scope.launch { graph.hub.stationRequests.collect { stations.handle(it) } }
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
        val added = saved.karousel.toHashSet()
        engine.setQueue(saved.songs.mapIndexed { i, song -> engine.newItem(song, i in added) }, saved.index, saved.positionMs)
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
        val kept = queue.drop(from).take(500)
        sessions.save(
            SavedSession(
                profile.id,
                kept.map { it.song },
                engine.index - from,
                engine.positionMs,
                kept.indices.filter { kept[it].karousel },
            ),
        )
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
        } else {
            tracker.hold()
        }
        graph.hub.sleepTimer.value?.endsAtMillis?.let { ends ->
            if (System.currentTimeMillis() >= ends) {
                graph.hub.clearSleepTimer()
                sessionPlayer.pause()
                graph.messages.show(t("Sleep timer finished. Good night."))
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

        override fun settings(): Settings {
            val s = graph.settings.current
            // A station is an endless mix, whatever Karousel is set to.
            return if (!s.karousel && stations.station != null) s.copy(karousel = true) else s
        }

        override suspend fun plan(current: Song, next: Song, context: PlanContext): TransitionPlan {
            val s = graph.settings.current
            if (!s.injektEnabled) return planTransition(current, next, context, s, null, null)
            val a = withTimeoutOrNull(25_000) { graph.analysis.getOrAnalyse(current) }
            val b = withTimeoutOrNull(25_000) { graph.analysis.getOrAnalyse(next) }
            // Analysis can take a while; plan against where playback is now.
            val fresh = context.copy(currentTime = engine.positionMs / 1000.0)
            return planTransition(current, next, fresh, s, a, b)
        }

        override suspend fun extendQueue(seeds: List<Song>, queued: List<Song>): List<Song> =
            withContext(Dispatchers.IO) {
                val picks = if (stations.station != null) {
                    stations.next(seeds.firstOrNull(), queued.mapTo(HashSet()) { it.id }, 8)
                } else {
                    karousel.next(seeds, queued)
                }
                if (!windingDown || picks.size < 2) return@withContext picks
                val known = graph.analysis.cachedMany(picks.map { it.id })
                calmestFirst(picks) { known[it.id] }
            }

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
            // This track and the next are analysed for the plan made now; look
            // one further, so skipping ahead lands on a transition that is ready too.
            for (ahead in 1..2) engine.queue.getOrNull(engine.index + ahead)?.let { graph.analysis.analyseAhead(it.song) }
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
        current = null
        sessionPlayer.removeListener(widgetListener)
        sessionPlayer.removeListener(bannerListener)
        NowPlayingWidget.publish(this, NowPlayingWidget.stateOf(sessionPlayer).copy(playing = false))
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
        /** Every controller, the system's player banner among them, gets the mode and like buttons. */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            if (!base.isAccepted) return base
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(base.availableSessionCommands.buildUpon().add(MODE_COMMAND).add(LIKE_COMMAND).build())
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .setMediaButtonPreferences(bannerButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                MODE_COMMAND.customAction -> nextMode()
                LIKE_COMMAND.customAction -> toggleLike()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

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

        /**
         * Play pressed with nothing loaded — a headset or Bluetooth button,
         * KWGT, the widget or System UI's resume card after the app was
         * closed: carry on with the saved queue where it stopped. For System
         * UI's card alone ([isForPlayback] false), only the current track.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val saved = savedForProfile() ?: throw UnsupportedOperationException(t("Nothing to resume."))
            val client = graph.auth.client.value
            if (isForPlayback) {
                MediaSession.MediaItemsWithStartPosition(saved.mediaItems(client), saved.index, saved.positionMs)
            } else {
                val song = saved.songs.getOrElse(saved.index) { saved.songs.first() }
                MediaSession.MediaItemsWithStartPosition(listOf(MediaItems.from(song, client)), 0, saved.positionMs)
            }
        }
    }

    companion object {
        /** The running service, for the home screen widget's buttons. */
        @Volatile
        var current: PlaybackService? = null
            private set

        /** Whether there is a saved queue that play could pick up again. */
        fun canResume(context: Context): Boolean = File(context.filesDir, SESSION_FILE).isFile

        /** How long before a sleep timer ends that Karousel winds down. */
        private const val WIND_DOWN_MS = 15 * 60 * 1000L

        /** The player banner's mode button: off, shuffle, repeat, repeat one, Karousel. */
        private val MODE_COMMAND = SessionCommand("app.kultr.banner.MODE", Bundle.EMPTY)

        /** The player banner's heart. */
        private val LIKE_COMMAND = SessionCommand("app.kultr.banner.LIKE", Bundle.EMPTY)
    }
}

private const val SESSION_FILE = "session.json"
