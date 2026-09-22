package app.kultr.core.engine

import app.kultr.core.api.Song
import app.kultr.core.injekt.PlanContext
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.injekt.TransitionType
import app.kultr.core.injekt.planTransition
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A deck that plays silence against a fake clock. */
class FakeDeck(override val name: String, private val clock: () -> Long) : Deck {
    override var item: QueueItem? = null
    override var status: DeckStatus = DeckStatus.IDLE
    override var durationMs: Long = -1
    private var base = 0L
    private var since: Long? = null
    private var wantPlay = false
    var rate = 1f
        private set
    var level = 0f
        private set
    var bass = 0f
        private set
    var sweep = 20f
        private set
    var next: QueueItem? = null
        private set
    var failNextLoad = false
    private var listener: DeckListener? = null

    override val positionMs: Long
        get() = base + (since?.let { ((clock() - it) * rate).toLong() } ?: 0L)
    override val bufferedPositionMs: Long get() = positionMs
    override val isPlaying: Boolean get() = status == DeckStatus.READY && wantPlay

    override fun load(item: QueueItem, startMs: Long) {
        this.item = item
        base = startMs
        since = null
        wantPlay = false
        next = null
        durationMs = (item.song.duration ?: 0) * 1000L
        status = DeckStatus.BUFFERING
    }

    override fun play() {
        wantPlay = true
        if (status == DeckStatus.READY && since == null) since = clock()
    }

    override fun pause() {
        freeze()
        wantPlay = false
    }

    private fun freeze() {
        base = positionMs
        since = if (since != null) clock() else null
    }

    override fun seekTo(positionMs: Long) {
        base = positionMs
        if (since != null) since = clock()
        if (status == DeckStatus.ENDED) status = DeckStatus.READY
    }

    override fun stop() {
        item = null
        next = null
        since = null
        wantPlay = false
        base = 0
        status = DeckStatus.IDLE
    }

    override fun setNext(item: QueueItem?) {
        next = item
    }

    override fun setLevel(level: Float) {
        this.level = level
    }

    override fun setRate(rate: Float) {
        freeze()
        this.rate = rate
    }

    override fun setBassDb(db: Float) {
        bass = db
    }

    override fun setSweepHz(hz: Float) {
        sweep = hz
    }

    override fun setListener(listener: DeckListener?) {
        this.listener = listener
    }

    /** Advance the simulation: finish loading, reach the end, move on gaplessly. */
    fun update() {
        if (status == DeckStatus.BUFFERING) {
            if (failNextLoad) {
                failNextLoad = false
                status = DeckStatus.ERROR
                listener?.onError(this, "boom")
                return
            }
            status = DeckStatus.READY
            if (wantPlay) since = clock()
            listener?.onStatusChanged(this)
        }
        if (status == DeckStatus.READY && since != null && durationMs > 0 && positionMs >= durationMs) {
            val following = next
            if (following != null) {
                val overflow = positionMs - durationMs
                item = following
                next = null
                durationMs = (following.song.duration ?: 0) * 1000L
                base = overflow
                since = clock()
                listener?.onAutoAdvanced(this, following)
            } else {
                base = durationMs
                since = null
                status = DeckStatus.ENDED
                listener?.onStatusChanged(this)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineTest {
    private var now = 0L
    private val a = FakeDeck("A") { now }
    private val b = FakeDeck("B") { now }
    private var settings = Settings(injektEnabled = false, crossfadeSeconds = 6.0)
    private val scope = TestScope(StandardTestDispatcher())
    private val started = mutableListOf<Pair<String, TrackChangeReason>>()
    private val errors = mutableListOf<String>()
    private var extension: List<Song> = emptyList()
    private var planOverride: ((Song, Song, PlanContext) -> TransitionPlan)? = null

    private val host = object : EngineHost {
        override fun now() = now
        override fun settings() = settings
        override suspend fun plan(current: Song, next: Song, context: PlanContext): TransitionPlan =
            planOverride?.invoke(current, next, context) ?: planTransition(current, next, context, settings, null, null)

        override suspend fun extendQueue(seed: Song, recent: List<Song>): List<Song> = extension.also { extension = emptyList() }
        override fun onStateChanged() {}
        override fun onTrackStarted(item: QueueItem, reason: TrackChangeReason) {
            started += item.song.id to reason
        }

        override fun onError(message: String) {
            errors += message
        }
    }

    private val engine = PlaybackEngine(a, b, host, scope)

    private fun songs(vararg ids: String, seconds: Int = 60) = ids.map { Song(id = it, title = it, duration = seconds) }

    private fun advance(ms: Long, step: Long = 10) {
        var left = ms
        while (left > 0) {
            val dt = minOf(step, left)
            now += dt
            a.update()
            b.update()
            engine.tick()
            scope.testScheduler.advanceTimeBy(dt)
            scope.testScheduler.runCurrent()
            left -= dt
        }
    }

    private fun start(vararg ids: String, seconds: Int = 60) {
        engine.setQueue(engine.newItems(songs(*ids, seconds = seconds)), 0, 0)
        engine.setPlayWhenReady(true)
        advance(20)
    }

    private val activeDeck: FakeDeck get() = if (a.isPlaying && a.item?.uid == engine.currentItem?.uid) a else b

    @Test
    fun playsTheFirstTrack() {
        start("1", "2")
        assertEquals(EngineStatus.READY, engine.status)
        assertTrue(a.isPlaying)
        advance(5_000)
        assertTrue(abs(engine.positionMs - 5_000) < 50)
        assertEquals("1" to TrackChangeReason.USER, started.first())
    }

    @Test
    fun crossfadesIntoTheNextTrack() {
        start("1", "2")
        advance(30_000)
        // Planned 35s before the end, primed on the idle deck at level zero.
        assertEquals("2", b.item?.song?.id)
        assertFalse(b.isPlaying)
        assertEquals(TransitionType.CROSSFADE, engine.currentPlan?.type)

        advance(24_500) // 54.5s: the six-second crossfade has begun
        assertEquals(1, engine.index)
        assertTrue(engine.isTransitioning)
        assertTrue(a.isPlaying && b.isPlaying)
        assertEquals("2" to TrackChangeReason.TRANSITION, started.last())

        advance(2_500) // halfway: equal power keeps the sum of squares at one
        assertTrue(a.level in 0.3f..0.95f, "outgoing ${a.level}")
        assertTrue(b.level in 0.3f..0.95f, "incoming ${b.level}")
        assertTrue(abs(a.level * a.level + b.level * b.level - 1f) < 0.05f)

        advance(4_000)
        assertFalse(engine.isTransitioning)
        assertNull(a.item)
        assertEquals(1f, b.level)
        // The incoming track has been playing since 54s, and it is now 61s.
        assertTrue(abs(engine.positionMs - 7_000) < 100, "position ${engine.positionMs}")
    }

    @Test
    fun gaplessLetsTheDeckJoinTheTracks() {
        settings = settings.copy(crossfadeEnabled = false, gapless = true)
        start("1", "2", "3")
        advance(30_000)
        assertEquals("2", a.next?.song?.id)
        assertNull(b.item)
        advance(30_100)
        assertEquals(1, engine.index)
        assertEquals("2" to TrackChangeReason.AUTO, started.last())
        assertTrue(a.isPlaying)
    }

    @Test
    fun hardCutStartsThePrimedDeckWhenTheTrackEnds() {
        settings = settings.copy(crossfadeEnabled = false, gapless = false)
        start("1", "2")
        advance(30_000)
        assertEquals("2", b.item?.song?.id)
        advance(30_100)
        assertEquals(1, engine.index)
        assertTrue(b.isPlaying)
        assertNull(a.item)
    }

    @Test
    fun manualSkipFades() {
        start("1", "2", "3")
        advance(10_000)
        engine.seekTo(2, 0)
        assertEquals(2, engine.index)
        assertTrue(engine.isTransitioning)
        advance(2_200)
        assertFalse(engine.isTransitioning)
        assertEquals("3", activeDeck.item?.song?.id)
        assertEquals(1, listOf(a, b).count { it.item != null })
    }

    @Test
    fun aNewQueueWhilePlayingKeepsPlaying() {
        start("1", "2")
        advance(3_000)
        engine.setQueue(engine.newItems(songs("x", "y")), 1, 0)
        advance(50)
        assertEquals("y", engine.currentItem?.song?.id)
        assertTrue(activeDeck.isPlaying)
    }

    @Test
    fun skipWithoutFadeWhenSwitchedOff() {
        settings = settings.copy(crossfadeOnSkip = false)
        start("1", "2")
        engine.seekTo(1, 0)
        assertFalse(engine.isTransitioning)
        advance(20)
        assertEquals("2", a.item?.song?.id)
        assertTrue(a.isPlaying)
    }

    @Test
    fun seekingWithinATrackClearsThePlan() {
        start("1", "2")
        advance(30_000)
        assertNotNull(engine.currentPlan)
        engine.seekTo(0, 5_000)
        assertNull(b.item)
        assertNull(engine.currentPlan)
        advance(500)
        assertTrue(engine.positionMs in 5_000..6_000)
    }

    @Test
    fun endOfQueueEnds() {
        settings = settings.copy(injektAutoQueue = false)
        start("1", seconds = 20)
        advance(21_000)
        assertEquals(EngineStatus.ENDED, engine.status)
        assertEquals(0, engine.index)
    }

    @Test
    fun autoQueueKeepsTheMusicGoing() {
        settings = settings.copy(injektAutoQueue = true)
        extension = songs("x", "y")
        start("1")
        advance(30_000)
        assertEquals(3, engine.queue.size)
        advance(31_000)
        assertEquals(1, engine.index)
        assertEquals("x", engine.currentItem?.song?.id)
    }

    @Test
    fun removingTheCurrentTrackMovesOn() {
        start("1", "2", "3")
        engine.removeRange(0, 1)
        assertEquals(0, engine.index)
        assertEquals("2", engine.currentItem?.song?.id)
        advance(20)
        assertTrue(activeDeck.isPlaying)
    }

    @Test
    fun queueEditsKeepTheCurrentTrack() {
        start("1", "2", "3")
        engine.seekTo(1, 0)
        advance(3_000)
        engine.addItems(0, engine.newItems(songs("0")))
        assertEquals(2, engine.index)
        assertEquals("2", engine.currentItem?.song?.id)
        engine.moveRange(2, 3, 0)
        assertEquals(0, engine.index)
        assertEquals(listOf("2", "0", "1", "3"), engine.queue.map { it.song.id })
    }

    @Test
    fun shuffleKeepsTheCurrentTrackFirstAndUnshuffleRestores() {
        start("1", "2", "3", "4", "5", "6")
        engine.seekTo(2, 0)
        advance(3_000)
        engine.setShuffle(true)
        assertEquals(0, engine.index)
        assertEquals("3", engine.currentItem?.song?.id)
        assertEquals(setOf("1", "2", "3", "4", "5", "6"), engine.queue.map { it.song.id }.toSet())
        engine.setShuffle(false)
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), engine.queue.map { it.song.id })
        assertEquals(2, engine.index)
    }

    @Test
    fun repeatOneReplaysTheSameTrack() {
        settings = settings.copy(crossfadeEnabled = false, gapless = true)
        start("1", "2")
        engine.setRepeat(RepeatMode.ONE)
        advance(61_000)
        assertEquals(0, engine.index)
        assertEquals("1" to TrackChangeReason.AUTO, started.last())
    }

    @Test
    fun pauseAtEndOfTrackStopsOnTheNextOne() {
        start("1", "2")
        engine.pauseAtEndOfTrack = true
        advance(61_000)
        assertFalse(engine.playWhenReady)
        assertEquals(1, engine.index)
        assertFalse(a.isPlaying || b.isPlaying)
    }

    @Test
    fun pausingFreezesACrossfade() {
        start("1", "2")
        advance(55_500)
        assertTrue(engine.isTransitioning)
        val levelBefore = b.level
        engine.setPlayWhenReady(false)
        advance(10_000)
        assertTrue(engine.isTransitioning)
        assertEquals(levelBefore, b.level, 0.02f)
        engine.setPlayWhenReady(true)
        advance(7_000)
        assertFalse(engine.isTransitioning)
    }

    @Test
    fun injektPlansDriveRatesAndTheBassSwap() {
        settings = settings.copy(injektEnabled = true)
        planOverride = { _, _, _ ->
            TransitionPlan(
                type = TransitionType.BLEND, duration = 8.0, startAt = 48.0, inStartOffset = 4.0,
                incomingRate = 0.98, outgoingRate = 1.02, outgoingRamp = 6.0, tempoRelease = 4.0,
                bassSwap = true, sweep = false, curve = CrossfadeCurve.EQUAL_POWER, label = "InjeKt", reason = "",
            )
        }
        start("1", "2")
        advance(30_000)
        assertEquals("2", b.item?.song?.id)
        assertTrue(abs(b.positionMs - 4_000) < 10)
        advance(15_000) // 45s: inside the six-second tempo approach
        assertTrue(a.rate > 1f && a.rate < 1.02f, "approach rate ${a.rate}")
        advance(3_100) // 48.1s: blend begins
        assertTrue(engine.isTransitioning)
        assertEquals(1.02f, a.rate, 0.001f)
        assertEquals(0.98f, b.rate, 0.001f)
        advance(4_000) // halfway through the overlap
        assertTrue(a.bass < -10f, "outgoing bass ${a.bass}")
        assertTrue(b.bass < 0f && b.bass > -26f, "incoming bass ${b.bass}")
        advance(4_200)
        assertFalse(engine.isTransitioning)
        assertEquals(0f, b.bass)
        advance(4_100) // tempo released back to normal
        assertEquals(1f, b.rate, 0.001f)
    }

    @Test
    fun aFailedTrackIsSkipped() {
        a.failNextLoad = true
        engine.setQueue(engine.newItems(songs("bad", "good")), 0, 0)
        engine.setPlayWhenReady(true)
        advance(2_000)
        assertEquals(listOf("boom"), errors)
        assertEquals("good", engine.currentItem?.song?.id)
        assertTrue(activeDeck.isPlaying)
    }

    @Test
    fun stopAndPrepareResumeWhereWeWere() {
        start("1", "2")
        advance(12_000)
        engine.stop()
        assertEquals(EngineStatus.IDLE, engine.status)
        assertTrue(abs(engine.positionMs - 12_000) < 50)
        engine.prepare()
        advance(20)
        assertTrue(abs(engine.positionMs - 12_000) < 100)
    }
}
