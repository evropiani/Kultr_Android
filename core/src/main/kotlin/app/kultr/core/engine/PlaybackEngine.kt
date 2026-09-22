package app.kultr.core.engine

import app.kultr.core.api.Song
import app.kultr.core.injekt.PlanContext
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.injekt.TransitionType
import app.kultr.core.injekt.crossfadePlan
import app.kultr.core.injekt.gaplessPlan
import app.kultr.core.injekt.hardCutPlan
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

enum class RepeatMode { OFF, ALL, ONE }

/** What the outside world sees, mapped one-to-one onto Media3's player states. */
enum class EngineStatus { IDLE, BUFFERING, READY, ENDED }

enum class TrackChangeReason {
    /** Something asked for this track: a new queue, a skip, a tap in the queue. */
    USER,

    /** The previous track finished (or failed) and playback moved on. */
    AUTO,

    /** An overlapping transition (crossfade, InjeKt) brought this track in. */
    TRANSITION,
}

/** Everything the engine needs from the platform. */
interface EngineHost {
    /** Monotonic clock, in milliseconds. */
    fun now(): Long

    fun settings(): Settings

    /** Plan the hand-over from [current] to [next]. May analyse both tracks. */
    suspend fun plan(current: Song, next: Song, context: PlanContext): TransitionPlan

    /** The queue ran out; return tracks to keep going with (or nothing). */
    suspend fun extendQueue(seed: Song, recent: List<Song>): List<Song>

    /** Anything visible changed: queue, index, status, plan. */
    fun onStateChanged()

    fun onTrackStarted(item: QueueItem, reason: TrackChangeReason)

    fun onError(message: String)
}

/**
 * Kultr's two-deck playback engine.
 *
 * Whenever crossfade or InjeKt is active, the next track is loaded on the idle
 * deck and both play at once for the length of the transition, each through
 * its own gain, low-shelf and high-pass, so basslines can be swapped and the
 * outgoing track swept out the way a DJ would. Gapless hand-overs are left to
 * the deck itself, which can join two files sample-accurately.
 *
 * Single-threaded: every call, including [tick], must come from one thread.
 * The platform calls [tick] on a timer — see [tickIntervalMs].
 */
class PlaybackEngine(
    deckA: Deck,
    deckB: Deck,
    private val host: EngineHost,
    private val scope: CoroutineScope,
) : DeckListener {

    private val decks = arrayOf(deckA, deckB)
    private var activeSlot = 0
    private val active: Deck get() = decks[activeSlot]
    private val idle: Deck get() = decks[1 - activeSlot]

    /** Fade level (0..1) per deck, before ReplayGain. */
    private val fades = floatArrayOf(1f, 0f)

    var queue: List<QueueItem> = emptyList()
        private set
    private var unshuffled: List<QueueItem>? = null

    /** Index of the current item in [queue], or -1 when the queue is empty. */
    var index: Int = -1
        private set
    var playWhenReady: Boolean = false
        private set
    var repeat: RepeatMode = RepeatMode.OFF
        private set
    var shuffle: Boolean = false
        private set

    /** True once the last item finished and nothing followed. */
    var ended: Boolean = false
        private set

    /** Nothing is loaded (fresh start, or after [stop]). */
    var stopped: Boolean = true
        private set
    var lastError: String? = null
        private set

    /** Pause when the current track ends instead of moving on (sleep timer). */
    var pauseAtEndOfTrack: Boolean = false
        set(value) {
            field = value
            if (value) clearPending()
        }

    private var stoppedPositionMs = 0L
    private var nextUid = 1L

    // ---------------------------------------------------------- transitions --

    private class Pending(val item: QueueItem, val plan: TransitionPlan)

    private class Transition(
        val plan: TransitionPlan,
        val from: Deck,
        val to: Deck,
        val requestedAt: Long,
        val durationMs: Long,
        val fromLevel: Float,
    ) {
        /** Engine clock when the incoming deck actually started; -1 while waiting. */
        var startedAt = -1L
    }

    private class Ramp(val deck: Deck, val from: Double, val to: Double, val start: Long, val duration: Long)

    /** Drift of the outgoing deck toward the meeting tempo, in its own timeline. */
    private class Approach(val deck: Deck, val rate: Double, val fromMs: Long, val toMs: Long)

    private var pending: Pending? = null
    private var preparedFor: Long? = null
    private var planJob: Job? = null
    private var transition: Transition? = null
    private var tempoRelease: Ramp? = null
    private var approach: Approach? = null
    private var errorStreak = 0
    private var extendJob: Job? = null

    /** The plan for the track playing now, with the uid of the track it leads out of. */
    var lastPlan: Pair<Long, TransitionPlan>? = null
        private set

    /** The transition that is audible right now, if any. */
    val activeTransition: TransitionPlan? get() = transition?.plan

    /** The plan that will take the current track out, if one is ready. */
    val currentPlan: TransitionPlan?
        get() = lastPlan?.takeIf { it.first == currentItem?.uid }?.second

    /**
     * A clock that only runs while playback is wanted. Fades and ramps are
     * timed against it, so pausing halfway through a crossfade pauses the
     * crossfade too.
     */
    private var clock = 0L
    private var lastTickAt = -1L

    init {
        deckA.setListener(this)
        deckB.setListener(this)
    }

    // ------------------------------------------------------------ accessors --

    val currentItem: QueueItem? get() = queue.getOrNull(index)

    val status: EngineStatus
        get() = when {
            queue.isEmpty() || stopped -> EngineStatus.IDLE
            ended -> EngineStatus.ENDED
            active.status == DeckStatus.READY || active.status == DeckStatus.ENDED -> EngineStatus.READY
            active.status == DeckStatus.ERROR -> EngineStatus.READY
            else -> EngineStatus.BUFFERING
        }

    val positionMs: Long
        get() = when {
            stopped -> stoppedPositionMs
            ended -> durationMs.coerceAtLeast(0)
            else -> active.positionMs.coerceAtLeast(0)
        }

    val bufferedPositionMs: Long
        get() = if (stopped) stoppedPositionMs else max(active.bufferedPositionMs, positionMs)

    /** Duration of the current item, from the deck when known, else from the server. */
    val durationMs: Long
        get() {
            val fromDeck = if (!stopped && active.item?.uid == currentItem?.uid) active.durationMs else -1
            if (fromDeck > 0) return fromDeck
            return (currentItem?.song?.duration ?: 0) * 1000L
        }

    val isTransitioning: Boolean get() = transition != null

    fun newItems(songs: List<Song>): List<QueueItem> = songs.map { QueueItem(nextUid++, it) }

    // ------------------------------------------------------------- transport --

    /** Replace the queue and load [startIndex] at [startPositionMs]. */
    fun setQueue(items: List<QueueItem>, startIndex: Int, startPositionMs: Long) {
        cancelTransition()
        clearPending()
        lastError = null
        errorStreak = 0
        if (items.isEmpty()) {
            queue = emptyList()
            unshuffled = null
            index = -1
            unload()
            host.onStateChanged()
            return
        }
        var list = items
        var start = startIndex.coerceIn(0, items.size - 1)
        unshuffled = null
        if (shuffle) {
            unshuffled = items
            val (shuffled, at) = shuffledWithCurrentFirst(items, start)
            list = shuffled
            start = at
        }
        queue = list
        index = start
        ended = false
        loadCurrent(max(0, startPositionMs))
        if (playWhenReady) active.play()
        host.onTrackStarted(queue[index], TrackChangeReason.USER)
        host.onStateChanged()
    }

    fun setPlayWhenReady(value: Boolean) {
        if (playWhenReady == value) return
        playWhenReady = value
        if (value) {
            if (queue.isEmpty()) {
                host.onStateChanged()
                return
            }
            if (stopped) loadCurrent(stoppedPositionMs)
            active.play()
            transition?.let { if (it.from.item != null) it.from.play() }
        } else {
            active.pause()
            transition?.from?.pause()
        }
        host.onStateChanged()
    }

    /** Make sure something is loaded, after [stop] or on a fresh start. */
    fun prepare() {
        if (queue.isEmpty() || !stopped) return
        loadCurrent(stoppedPositionMs)
        if (playWhenReady) active.play()
        host.onStateChanged()
    }

    fun stop() {
        stoppedPositionMs = positionMs
        cancelTransition()
        clearPending()
        unload()
        host.onStateChanged()
    }

    /** Seek to [positionMs] in the item at [targetIndex]. */
    fun seekTo(targetIndex: Int, positionMs: Long, manual: Boolean = true) {
        if (queue.isEmpty()) return
        val target = targetIndex.coerceIn(0, queue.size - 1)
        val position = max(0, positionMs)
        if (target == index && !ended) {
            cancelTransition()
            clearPending()
            if (stopped) {
                stoppedPositionMs = position
            } else {
                active.seekTo(position)
            }
            host.onStateChanged()
            return
        }
        skipTo(target, position, manual)
    }

    fun setRepeat(mode: RepeatMode) {
        if (repeat == mode) return
        repeat = mode
        clearPending()
        host.onStateChanged()
    }

    fun setShuffle(enabled: Boolean) {
        if (shuffle == enabled) return
        shuffle = enabled
        if (queue.isNotEmpty()) {
            if (enabled) {
                unshuffled = queue
                val (shuffled, at) = shuffledWithCurrentFirst(queue, index)
                queue = shuffled
                index = at
            } else {
                val restored = unshuffled
                if (restored != null) {
                    val current = currentItem
                    // Anything added while shuffled that the original order does
                    // not know about goes to the end rather than disappearing.
                    val known = restored.map { it.uid }.toHashSet()
                    val alive = queue.map { it.uid }.toHashSet()
                    val merged = restored.filter { it.uid in alive } + queue.filter { it.uid !in known }
                    queue = merged
                    index = current?.let { c -> merged.indexOfFirst { it.uid == c.uid } }?.takeIf { it >= 0 } ?: 0
                }
            }
        }
        unshuffled = if (enabled) unshuffled else null
        clearPending()
        host.onStateChanged()
    }

    // ------------------------------------------------------------ queue edits --

    fun addItems(at: Int, items: List<QueueItem>) {
        if (items.isEmpty()) return
        if (queue.isEmpty()) {
            setQueue(items, 0, 0)
            return
        }
        val position = at.coerceIn(0, queue.size)
        val list = queue.toMutableList()
        list.addAll(position, items)
        queue = list
        if (position <= index) index += items.size
        unshuffled?.let { original ->
            // "Play next" while shuffled should also be next once unshuffled.
            val anchor = if (position == index + 1) currentItem?.uid else null
            val anchorAt = anchor?.let { uid -> original.indexOfFirst { it.uid == uid } } ?: -1
            val copy = original.toMutableList()
            if (anchorAt >= 0) copy.addAll(anchorAt + 1, items) else copy.addAll(items)
            unshuffled = copy
        }
        revalidatePending()
        host.onStateChanged()
    }

    /** Remove [from] until [to] (exclusive). Removing the current item moves on. */
    fun removeRange(from: Int, to: Int) {
        if (queue.isEmpty()) return
        val start = from.coerceIn(0, queue.size)
        val end = to.coerceIn(start, queue.size)
        if (start == end) return
        val removed = queue.subList(start, end).map { it.uid }.toHashSet()
        val list = queue.toMutableList()
        repeat(end - start) { list.removeAt(start) }
        unshuffled = unshuffled?.filter { it.uid !in removed }
        val currentRemoved = index in start until end
        queue = list
        if (list.isEmpty()) {
            cancelTransition()
            clearPending()
            index = -1
            unload()
            host.onStateChanged()
            return
        }
        if (currentRemoved) {
            cancelTransition()
            clearPending()
            index = start.coerceAtMost(list.size - 1)
            if (start >= list.size) {
                // Removed the tail including the current item: nothing follows.
                ended = true
                active.pause()
            } else {
                ended = false
                if (!stopped) loadCurrent(0)
                if (playWhenReady && !stopped) active.play()
                host.onTrackStarted(queue[index], TrackChangeReason.USER)
            }
        } else if (index >= end) {
            index -= end - start
        }
        revalidatePending()
        host.onStateChanged()
    }

    /** Media3 semantics: move [from]..[to] (exclusive) so it starts at [newIndex]. */
    fun moveRange(from: Int, to: Int, newIndex: Int) {
        if (queue.isEmpty()) return
        val start = from.coerceIn(0, queue.size)
        val end = to.coerceIn(start, queue.size)
        if (start == end) return
        val current = currentItem
        val list = queue.toMutableList()
        val moving = list.subList(start, end).toList()
        repeat(end - start) { list.removeAt(start) }
        list.addAll(newIndex.coerceIn(0, list.size), moving)
        queue = list
        index = current?.let { c -> list.indexOfFirst { it.uid == c.uid } } ?: index
        revalidatePending()
        host.onStateChanged()
    }

    // --------------------------------------------------------------- settings --

    /** Re-read gains and invalidate plans after a settings change. */
    fun onSettingsChanged(plansAffected: Boolean) {
        applyLevel(active)
        if (transition == null) applyLevel(idle)
        if (plansAffected) clearPending()
    }

    fun release() {
        planJob?.cancel()
        extendJob?.cancel()
        decks.forEach {
            it.setListener(null)
            it.stop()
        }
    }

    // ------------------------------------------------------------------ tick --

    /** How often the platform should call [tick] right now. 0 means "no need". */
    fun tickIntervalMs(): Long {
        if (transition != null || tempoRelease != null || approach != null) return 16
        if (!playWhenReady || stopped || queue.isEmpty()) return 0
        val p = pending
        if (p != null && p.plan.type != TransitionType.GAPLESS && p.plan.type != TransitionType.CUT) {
            val startMs = (p.plan.startAt * 1000).roundToLong()
            val untilStart = startMs - active.positionMs
            val rampMs = (p.plan.outgoingRamp * 1000).roundToLong()
            if (untilStart < rampMs + 1500) return 16
        }
        return 250
    }

    fun tick() {
        val now = host.now()
        if (lastTickAt >= 0 && playWhenReady) clock += (now - lastTickAt).coerceIn(0, 1000)
        lastTickAt = now

        runTransition()
        runTempoRelease()

        if (stopped || queue.isEmpty() || !playWhenReady || transition != null) return
        val deck = active
        val item = currentItem ?: return
        if (deck.item?.uid != item.uid || deck.status != DeckStatus.READY) return

        val position = deck.positionMs
        runApproach(deck, position)

        val duration = durationMs
        if (duration <= 0) return
        val p = pending
        if (p != null) {
            if (p.plan.type == TransitionType.GAPLESS || p.plan.type == TransitionType.CUT) return
            val startMs = min((p.plan.startAt * 1000).roundToLong(), duration - 50)
            val rampMs = (p.plan.outgoingRamp * 1000).roundToLong()
            if (approach == null && rampMs > 0 && abs(p.plan.outgoingRate - 1) > 0.001 &&
                position >= startMs - rampMs && position < startMs
            ) {
                approach = Approach(deck, p.plan.outgoingRate, position, startMs)
            }
            if (position >= startMs) executeTransition(p)
        } else if (duration - position <= PREPARE_LEAD_MS && preparedFor != item.uid && !pauseAtEndOfTrack) {
            prepareNext()
        }
    }

    private fun runApproach(deck: Deck, position: Long) {
        val a = approach ?: return
        if (a.deck !== deck) {
            approach = null
            return
        }
        val span = a.toMs - a.fromMs
        val t = if (span > 0) (position - a.fromMs).toDouble() / span else 1.0
        if (t >= 1) {
            deck.setRate(a.rate.toFloat())
            approach = null
        } else if (t >= 0) {
            val eased = t * t * (3 - 2 * t)
            deck.setRate((1 + (a.rate - 1) * eased).toFloat())
        }
    }

    private fun runTempoRelease() {
        val r = tempoRelease ?: return
        val t = (clock - r.start).toDouble() / r.duration
        when {
            t >= 1 -> {
                r.deck.setRate(r.to.toFloat())
                tempoRelease = null
            }
            t >= 0 -> {
                val eased = t * t * (3 - 2 * t)
                r.deck.setRate((r.from + (r.to - r.from) * eased).toFloat())
            }
        }
    }

    private fun runTransition() {
        val tr = transition ?: return
        if (tr.startedAt < 0) {
            // Wait for the incoming deck to actually make sound before the fade
            // clock starts, so a slow start never fades in silence.
            if (!tr.to.isPlaying && clock - tr.requestedAt <= INCOMING_START_TIMEOUT_MS) return
            tr.startedAt = clock
        }
        val plan = tr.plan
        val t = if (tr.durationMs > 0) (clock - tr.startedAt).toDouble() / tr.durationMs else 1.0
        if (t >= 1) {
            finishTransition(tr)
            return
        }
        val fromFade = tr.fromLevel * curveValue(plan.curve, t, rising = false)
        val toFade = curveValue(plan.curve, t, rising = true)
        setFade(tr.from, fromFade.toFloat())
        setFade(tr.to, toFade.toFloat())
        if (plan.bassSwap) {
            // Drop the outgoing bass early, bring the incoming bass in late, so
            // the two kick drums never fight.
            tr.from.setBassDb((BASS_CUT_DB * min(1.0, t / 0.55)).toFloat())
            tr.to.setBassDb((BASS_CUT_DB * (1 - max(0.0, (t - 0.35) / 0.65))).toFloat())
        }
        if (plan.sweep) {
            tr.from.setSweepHz((SWEEP_FROM_HZ * (SWEEP_TO_HZ / SWEEP_FROM_HZ).pow(t)).toFloat())
        }
    }

    private fun finishTransition(tr: Transition) {
        transition = null
        tr.from.stop()
        resetDeck(tr.from)
        fades[slotOf(tr.from)] = 0f
        setFade(tr.to, 1f)
        tr.to.setBassDb(0f)
        tr.to.setSweepHz(SWEEP_FROM_HZ.toFloat())
        val plan = tr.plan
        if (plan.tempoRelease > 0 && abs(plan.incomingRate - 1) > 0.001) {
            tempoRelease = Ramp(tr.to, plan.incomingRate, 1.0, clock, (plan.tempoRelease * 1000).roundToLong())
        }
        host.onStateChanged()
    }

    // --------------------------------------------------------------- planning --

    private fun nextIndexAfter(current: Int): Int? = when {
        queue.isEmpty() -> null
        repeat == RepeatMode.ONE -> current
        current + 1 < queue.size -> current + 1
        repeat == RepeatMode.ALL -> 0
        else -> null
    }

    private fun peekNext(): QueueItem? = nextIndexAfter(index)?.let { queue[it] }

    private fun prepareNext() {
        val current = currentItem ?: return
        preparedFor = current.uid
        planJob?.cancel()
        planJob = scope.launch {
            var next = peekNext()
            if (next == null && host.settings().injektAutoQueue) {
                if (extendQueueNow(current)) next = peekNext()
            }
            if (next == null || currentItem?.uid != current.uid) return@launch
            val context = PlanContext(
                durationA = durationMs / 1000.0,
                currentTime = active.positionMs / 1000.0,
            )
            val plan = try {
                host.plan(current.song, next.song, context)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
                fallbackPlan(context.durationA)
            }
            if (currentItem?.uid != current.uid || peekNext()?.uid != next.uid) return@launch
            setPending(next, plan)
        }
    }

    private fun fallbackPlan(durationA: Double): TransitionPlan {
        val s = host.settings()
        return when {
            s.crossfadeEnabled && s.crossfadeSeconds > 0 -> crossfadePlan(durationA, s.crossfadeSeconds, s.crossfadeCurve)
            s.gapless -> gaplessPlan(durationA)
            else -> hardCutPlan(durationA)
        }
    }

    private suspend fun extendQueueNow(seed: QueueItem): Boolean {
        val recent = queue.takeLast(60).map { it.song }
        val songs = try {
            host.extendQueue(seed.song, recent)
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            emptyList()
        }
        if (songs.isEmpty() || queue.isEmpty()) return false
        val items = newItems(songs)
        queue = queue + items
        unshuffled = unshuffled?.plus(items)
        host.onStateChanged()
        return true
    }

    private fun setPending(item: QueueItem, plan: TransitionPlan) {
        pending = Pending(item, plan)
        currentItem?.let { lastPlan = it.uid to plan }
        when (plan.type) {
            TransitionType.GAPLESS -> active.setNext(item)
            TransitionType.CUT -> primeIdle(item, 0)
            else -> primeIdle(item, (plan.inStartOffset * 1000).roundToLong())
        }
        host.onStateChanged()
    }

    private fun primeIdle(item: QueueItem, startMs: Long) {
        if (transition != null) return
        val deck = idle
        resetDeck(deck)
        setFade(deck, 0f)
        deck.load(item, startMs)
    }

    /** Forget the planned hand-over; it is rebuilt when needed. */
    private fun clearPending() {
        planJob?.cancel()
        planJob = null
        val hadPending = pending != null
        pending = null
        preparedFor = null
        approach?.let {
            it.deck.setRate(1f)
            approach = null
        }
        if (!stopped) active.setNext(null)
        if (transition == null && idle.item != null) idle.stop()
        if (hadPending) lastPlan = null
    }

    /** After a queue edit: keep the plan only if it still leads to the right track. */
    private fun revalidatePending() {
        val p = pending
        if (p != null && peekNext()?.uid != p.item.uid) {
            clearPending()
        } else if (p == null && preparedFor != null) {
            preparedFor = null
        }
    }

    // ------------------------------------------------------------ hand-overs --

    private fun executeTransition(p: Pending) {
        val from = active
        val to = idle
        val plan = p.plan
        val nextIndex = nextIndexAfter(index) ?: return
        pending = null
        preparedFor = null
        approach = null

        if (to.item?.uid != p.item.uid) {
            resetDeck(to)
            to.load(p.item, (plan.inStartOffset * 1000).roundToLong())
        }
        if (abs(plan.incomingRate - 1) > 0.001) to.setRate(plan.incomingRate.toFloat())
        if (abs(plan.outgoingRate - 1) > 0.001) from.setRate(plan.outgoingRate.toFloat())
        if (plan.bassSwap) to.setBassDb(BASS_CUT_DB.toFloat())
        setFade(to, 0f)
        to.play()

        activeSlot = slotOf(to)
        index = nextIndex
        ended = false
        transition = Transition(
            plan = plan,
            from = from,
            to = to,
            requestedAt = clock,
            durationMs = max(50, (plan.duration * 1000).roundToLong()),
            fromLevel = fades[slotOf(from)],
        )
        host.onTrackStarted(p.item, TrackChangeReason.TRANSITION)
        host.onStateChanged()
    }

    /** Jump to another item, with a short crossfade if that is switched on. */
    private fun skipTo(target: Int, positionMs: Long, manual: Boolean) {
        cancelTransition()
        clearPending()
        lastError = null
        val item = queue[target]
        val s = host.settings()
        val fadeMs = if (manual && s.crossfadeOnSkip && s.crossfadeEnabled && playWhenReady && !stopped &&
            active.isPlaying && !item.song.isRadio && active.item?.song?.isRadio != true
        ) {
            (min(s.crossfadeSeconds, 2.0) * 1000).roundToLong()
        } else {
            0L
        }
        index = target
        ended = false
        if (fadeMs > 50) {
            val from = active
            val to = idle
            resetDeck(to)
            setFade(to, 0f)
            to.load(item, positionMs)
            to.play()
            activeSlot = slotOf(to)
            transition = Transition(
                plan = crossfadePlan(fadeMs / 1000.0 / 0.4, fadeMs / 1000.0, CrossfadeCurve.EQUAL_POWER)
                    .copy(label = "Skip", reason = "A short fade instead of a hard cut."),
                from = from,
                to = to,
                requestedAt = clock,
                durationMs = fadeMs,
                fromLevel = fades[slotOf(from)],
            )
        } else {
            loadCurrent(positionMs)
            if (playWhenReady) active.play()
        }
        host.onTrackStarted(item, if (manual) TrackChangeReason.USER else TrackChangeReason.AUTO)
        host.onStateChanged()
    }

    /** The current deck finished and there was no overlapping transition. */
    private fun onActiveEnded() {
        val p = pending
        if (pauseAtEndOfTrack) {
            pauseAtEndOfTrack = false
            val next = nextIndexAfter(index)
            clearPending()
            playWhenReady = false
            if (next != null) {
                index = next
                loadCurrent(0)
                host.onTrackStarted(queue[index], TrackChangeReason.AUTO)
            } else {
                ended = true
            }
            host.onStateChanged()
            return
        }
        if (p != null && p.plan.type == TransitionType.CUT && idle.item?.uid == p.item.uid) {
            val nextIndex = nextIndexAfter(index)
            if (nextIndex != null) {
                val from = active
                val to = idle
                pending = null
                preparedFor = null
                activeSlot = slotOf(to)
                index = nextIndex
                setFade(to, 1f)
                from.stop()
                fades[slotOf(from)] = 0f
                if (playWhenReady) to.play()
                host.onTrackStarted(p.item, TrackChangeReason.AUTO)
                host.onStateChanged()
                return
            }
        }
        advanceAfterEnd()
    }

    private fun advanceAfterEnd() {
        clearPending()
        val next = nextIndexAfter(index)
        if (next != null) {
            skipTo(next, 0, manual = false)
            return
        }
        val current = currentItem
        if (current != null && host.settings().injektAutoQueue) {
            extendJob?.cancel()
            extendJob = scope.launch {
                if (extendQueueNow(current) && currentItem?.uid == current.uid) {
                    nextIndexAfter(index)?.let { skipTo(it, 0, manual = false) }
                } else {
                    markEnded()
                }
            }
            return
        }
        markEnded()
    }

    private fun markEnded() {
        ended = true
        host.onStateChanged()
    }

    // ---------------------------------------------------------- deck events --

    override fun onStatusChanged(deck: Deck) {
        if (deck === active) {
            if (deck.status == DeckStatus.READY) errorStreak = 0
            if (deck.status == DeckStatus.ENDED && transition == null && !stopped && !ended &&
                deck.item?.uid == currentItem?.uid
            ) {
                onActiveEnded()
                return
            }
        } else if (deck.status == DeckStatus.ENDED) {
            // The outgoing deck of a transition ran out before its fade did.
            transition?.let { if (it.from === deck) deck.pause() }
        }
        host.onStateChanged()
    }

    override fun onError(deck: Deck, message: String) {
        if (deck !== active) {
            // A primed or outgoing deck failed; the hand-over falls back to a
            // normal load when the time comes.
            val tr = transition
            if (tr != null && tr.from === deck) finishTransition(tr) else clearPending()
            return
        }
        lastError = message
        host.onError(message)
        errorStreak++
        val next = nextIndexAfter(index)
        if (next != null && next != index && errorStreak < MAX_ERROR_STREAK) {
            val failed = currentItem?.uid
            scope.launch {
                delay(ERROR_SKIP_DELAY_MS)
                if (currentItem?.uid == failed) skipTo(next, 0, manual = false)
            }
        } else {
            playWhenReady = false
            active.pause()
        }
        host.onStateChanged()
    }

    override fun onAutoAdvanced(deck: Deck, item: QueueItem) {
        if (deck !== active) return
        val nextIndex = nextIndexAfter(index)
        pending = null
        preparedFor = null
        if (nextIndex != null && queue[nextIndex].uid == item.uid) {
            index = nextIndex
        } else {
            val found = queue.indexOfFirst { it.uid == item.uid }
            if (found >= 0) index = found
        }
        host.onTrackStarted(item, TrackChangeReason.AUTO)
        host.onStateChanged()
    }

    // --------------------------------------------------------------- helpers --

    private fun slotOf(deck: Deck): Int = if (deck === decks[0]) 0 else 1

    private fun setFade(deck: Deck, fade: Float) {
        fades[slotOf(deck)] = fade
        applyLevel(deck)
    }

    private fun applyLevel(deck: Deck) {
        val song = deck.item?.song
        val base = if (song != null) replayGainFor(song, host.settings()) else 1f
        deck.setLevel(base * fades[slotOf(deck)])
    }

    private fun resetDeck(deck: Deck) {
        deck.setRate(1f)
        deck.setBassDb(0f)
        deck.setSweepHz(SWEEP_FROM_HZ.toFloat())
    }

    private fun loadCurrent(positionMs: Long) {
        val item = currentItem ?: return
        idle.stop()
        fades[1 - activeSlot] = 0f
        resetDeck(active)
        active.load(item, positionMs)
        stopped = false
        ended = false
        setFade(active, 1f)
    }

    private fun unload() {
        decks.forEach { it.stop() }
        stopped = true
        ended = false
        approach = null
        tempoRelease = null
    }

    /** Abandon an overlapping transition, keeping the incoming track at full level. */
    private fun cancelTransition() {
        val tr = transition
        if (tr != null) {
            transition = null
            tr.from.stop()
            resetDeck(tr.from)
            fades[slotOf(tr.from)] = 0f
            setFade(tr.to, 1f)
            tr.to.setBassDb(0f)
            tr.to.setSweepHz(SWEEP_FROM_HZ.toFloat())
        }
        tempoRelease?.let {
            it.deck.setRate(1f)
            tempoRelease = null
        }
    }

    private fun shuffledWithCurrentFirst(items: List<QueueItem>, current: Int): Pair<List<QueueItem>, Int> {
        val head = items.getOrNull(current) ?: return items.shuffled() to 0
        val rest = items.filterIndexed { i, _ -> i != current }.shuffled()
        return (listOf(head) + rest) to 0
    }

    companion object {
        /** How early the next transition is planned. */
        const val PREPARE_LEAD_MS = 35_000L
        const val INCOMING_START_TIMEOUT_MS = 5_000L
        const val BASS_CUT_DB = -26.0
        const val SWEEP_FROM_HZ = 20.0
        const val SWEEP_TO_HZ = 2400.0
        private const val MAX_ERROR_STREAK = 4
        private const val ERROR_SKIP_DELAY_MS = 1_500L
    }
}
