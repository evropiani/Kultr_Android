package app.kultr.core.injekt

import app.kultr.core.api.Song
import app.kultr.core.dsp.BpmSource
import app.kultr.core.dsp.KeyMode
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.settings.Settings
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InjektTest {
    private fun analysis(
        id: String,
        bpm: Double,
        camelot: String = "8A",
        energy: Double = 0.5,
        outroStart: Double = 200.0,
        introEnd: Double = 0.0,
        duration: Double = 240.0,
        confidence: Double = 0.8,
    ) = TrackAnalysis(
        songId = id, analysedAt = 0, bpmSource = BpmSource.DSP, duration = duration, bpm = bpm,
        bpmConfidence = confidence, beatOffset = 0.0, downbeatOffset = 0.0, outroDownbeat = 0.0, key = 9,
        keyName = "Am", mode = KeyMode.MINOR, keyConfidence = 0.5, camelot = camelot, energy = energy,
        brightness = 0.3, peak = 0.9, introEnd = introEnd, outroStart = outroStart,
    )

    private val a = Song(id = "a", title = "A", duration = 240)
    private val b = Song(id = "b", title = "B", duration = 240)
    private val context = PlanContext(durationA = 240.0, currentTime = 150.0)

    @Test
    fun tempoMeetsInTheMiddleGeometrically() {
        val match = matchTempo(120.0, 130.0, 0.5)
        assertTrue(abs(match.meetBpm - 124.9) < 0.1)
        assertTrue(abs(match.outgoingRate * 120 - match.incomingRate * 130) < 1e-9)
    }

    @Test
    fun halfAndDoubleTimeAreConsidered() {
        val match = matchTempo(140.0, 70.0, 0.0)
        assertEquals(140.0, match.targetBpm)
        assertEquals(0.0, match.worstShift, 1e-9)
    }

    @Test
    fun withoutInjektItIsAPlainCrossfade() {
        val s = Settings(injektEnabled = false, crossfadeSeconds = 6.0)
        val plan = planTransition(a, b, context, s, null, null)
        assertEquals(TransitionType.CROSSFADE, plan.type)
        assertEquals(6.0, plan.duration)
        assertEquals(234.0, plan.startAt)
    }

    @Test
    fun crossfadeOffFallsBackToGaplessOrCut() {
        val gapless = planTransition(a, b, context, Settings(injektEnabled = false, crossfadeEnabled = false), null, null)
        assertEquals(TransitionType.GAPLESS, gapless.type)
        val cut = planTransition(a, b, context, Settings(injektEnabled = false, crossfadeEnabled = false, gapless = false), null, null)
        assertEquals(TransitionType.CUT, cut.type)
    }

    @Test
    fun missingAnalysisDegradesToCrossfade() {
        val plan = planTransition(a, b, context, Settings(), analysis("a", 124.0), null)
        assertEquals(TransitionType.CROSSFADE, plan.type)
    }

    @Test
    fun closeTempiAreBeatMatchedOnABar() {
        val s = Settings(injektBars = 8)
        val plan = planTransition(a, b, context, s, analysis("a", 124.0), analysis("b", 126.0, camelot = "9A"))
        assertEquals(TransitionType.BLEND, plan.type)
        assertTrue(plan.bassSwap)
        assertFalse(plan.sweep)
        // Both decks move toward each other.
        assertTrue(plan.outgoingRate > 1.0 && plan.incomingRate < 1.0)
        assertEquals(124.0 * plan.outgoingRate, 126.0 * plan.incomingRate, 1e-6)
        // Start lands on a bar of the outgoing grid (bar = 4 beats at 124 BPM).
        val bar = 60.0 / 124 * 4
        val bars = plan.startAt / bar
        assertEquals(bars, Math.round(bars).toDouble(), 1e-6)
        assertTrue(plan.startAt >= context.currentTime)
        assertTrue(plan.outgoingRamp > 0)
        assertTrue(plan.tempoRelease > 0)
    }

    @Test
    fun farTempiAreNotMatched() {
        val plan = planTransition(a, b, context, Settings(), analysis("a", 100.0), analysis("b", 128.0))
        assertEquals(TransitionType.CROSSFADE, plan.type)
        assertEquals(1.0, plan.incomingRate)
        assertEquals(1.0, plan.outgoingRate)
    }

    @Test
    fun clashingKeysSweepOut() {
        val plan = planTransition(a, b, context, Settings(), analysis("a", 124.0, camelot = "8A"), analysis("b", 124.0, camelot = "2B"))
        assertEquals(TransitionType.SWEEP, plan.type)
        assertTrue(plan.sweep)
    }

    @Test
    fun longIntrosAreSkippedToADownbeat() {
        val plan = planTransition(a, b, context, Settings(), analysis("a", 120.0), analysis("b", 120.0, introEnd = 17.3))
        val bar = 60.0 / 120 * 4
        assertTrue(plan.inStartOffset >= 17.3)
        assertEquals(0.0, plan.inStartOffset % bar, 1e-6)
    }

    @Test
    fun planIsNeverScheduledInThePast() {
        val late = PlanContext(durationA = 240.0, currentTime = 230.0)
        val plan = planTransition(a, b, late, Settings(), analysis("a", 124.0), analysis("b", 124.0))
        assertTrue(plan.startAt >= 230.0)
        assertTrue(plan.startAt <= 239.5)
    }

    @Test
    fun radioIsNeverBlended() {
        val radio = Song(id = "radio:1", title = "FM", kultrStreamUrl = "http://x")
        assertEquals(TransitionType.CUT, planTransition(radio, b, context, Settings(), null, null).type)
    }

    @Test
    fun affinityPrefersSimilarTracks() {
        val seed = analysis("s", 124.0, camelot = "8A", energy = 0.6)
        val close = analysis("c", 125.0, camelot = "9A", energy = 0.6)
        val far = analysis("f", 90.0, camelot = "3B", energy = 0.1)
        assertTrue(affinity(seed, close) > affinity(seed, far))
        val ranked = rankAutoQueue(seed, listOf(Song(id = "f") to far, Song(id = "c") to close), count = 1, random = Random(1))
        assertEquals(1, ranked.size)
    }
}
