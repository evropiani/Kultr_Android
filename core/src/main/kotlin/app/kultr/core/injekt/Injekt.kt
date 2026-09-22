package app.kultr.core.injekt

import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.dsp.camelotDistance
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.Settings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/*
 * InjeKt — Kultr's DJ-style transition engine.
 *
 * Given the track that is playing and the track that comes next, the planner
 * decides *where* to start the blend, *how long* it should last, whether the
 * two tracks can be beat-matched, and which tricks to use (bass swap, filter
 * sweep, intro skip). The playback engine then just executes the plan.
 *
 * Everything degrades gracefully: with no analysis available it produces a
 * plain equal-power crossfade, and with crossfade switched off entirely it
 * produces a gapless hand-off.
 */

enum class TransitionType { GAPLESS, CROSSFADE, BLEND, SWEEP, CUT }

data class TransitionPlan(
    val type: TransitionType,
    /** Length of the overlap in seconds (wall clock). */
    val duration: Double,
    /** Position in the outgoing track at which the overlap begins, seconds. */
    val startAt: Double,
    /** Position in the incoming track to start from, seconds. */
    val inStartOffset: Double,
    /** Playback rate applied to the incoming track for beat-matching. */
    val incomingRate: Double,
    /**
     * Rate the *outgoing* track is eased to before the blend, so both tracks
     * meet at a shared tempo instead of the next one doing all the work.
     */
    val outgoingRate: Double,
    /**
     * Seconds of the outgoing track — measured in its own timeline, ending at
     * [startAt] — over which it drifts from its natural tempo to [outgoingRate].
     */
    val outgoingRamp: Double,
    /** Seconds over which the incoming track eases back to its natural tempo. */
    val tempoRelease: Double,
    val bassSwap: Boolean,
    val sweep: Boolean,
    val curve: CrossfadeCurve,
    /** Short label for the UI, e.g. "InjeKt · 124⇄126 @ 125 BPM · 8 bars". */
    val label: String,
    /** Longer explanation, shown in the InjeKt panel. */
    val reason: String,
)

private const val MIN_TRANSITION = 2.0
private const val MAX_TRANSITION = 24.0

private fun clamp(value: Double, low: Double, high: Double): Double = min(high, max(low, value))

/**
 * Pick whichever downbeat anchor was fitted closest to [time]. Analysis
 * records two: one from the head of the track and one from the tail.
 */
private fun barOrigin(analysis: TrackAnalysis, time: Double): Double {
    val head = analysis.downbeatOffset
    val tail = analysis.outroDownbeat
    return if (abs(time - tail) < abs(time - head)) tail else head
}

/** Round [time] down to the nearest bar boundary of [analysis]'s grid. */
internal fun snapDownToBar(analysis: TrackAnalysis, time: Double): Double {
    val bar = 60 / analysis.bpm * 4
    if (!bar.isFinite() || bar <= 0) return time
    val origin = barOrigin(analysis, time)
    val bars = floor((time - origin) / bar)
    return max(0.0, origin + bars * bar)
}

/** First bar boundary at or after [time]. */
internal fun snapUpToBar(analysis: TrackAnalysis, time: Double): Double {
    val bar = 60 / analysis.bpm * 4
    if (!bar.isFinite() || bar <= 0) return time
    val origin = barOrigin(analysis, time)
    val bars = ceil((time - origin) / bar)
    return max(0.0, origin + bars * bar)
}

data class TempoMatch(
    /** Tempo, in BPM, both decks play at during the overlap. */
    val meetBpm: Double,
    val outgoingRate: Double,
    val incomingRate: Double,
    /** The BPM of the incoming track once half/double time is accounted for. */
    val targetBpm: Double,
    /** The larger of the two decks' tempo shifts, as a fraction. */
    val worstShift: Double,
)

/**
 * Work out a tempo the two tracks can meet at.
 *
 * [blend] is the share of the journey the outgoing track makes: 0 leaves it
 * alone and stretches the incoming track all the way, 1 does the opposite,
 * 0.5 splits the difference. The meeting point is a geometric interpolation
 * because tempo is a ratio. Half and double time are considered too.
 */
fun matchTempo(bpmA: Double, bpmB: Double, blend: Double): TempoMatch {
    val share = clamp(blend, 0.0, 1.0)
    var best = TempoMatch(bpmA, 1.0, 1.0, bpmB, Double.POSITIVE_INFINITY)
    if (!(bpmA > 0) || !(bpmB > 0)) return best
    for (targetBpm in doubleArrayOf(bpmB, bpmB * 2, bpmB / 2)) {
        if (targetBpm < 50 || targetBpm > 220) continue
        val meetBpm = bpmA * (targetBpm / bpmA).pow(share)
        val outgoingRate = meetBpm / bpmA
        val incomingRate = meetBpm / targetBpm
        val worstShift = max(abs(outgoingRate - 1), abs(incomingRate - 1))
        if (worstShift < best.worstShift) best = TempoMatch(meetBpm, outgoingRate, incomingRate, targetBpm, worstShift)
    }
    return best
}

fun gaplessPlan(durationA: Double): TransitionPlan = TransitionPlan(
    type = TransitionType.GAPLESS,
    duration = 0.0,
    startAt = max(0.0, durationA),
    inStartOffset = 0.0,
    incomingRate = 1.0,
    outgoingRate = 1.0,
    outgoingRamp = 0.0,
    tempoRelease = 0.0,
    bassSwap = false,
    sweep = false,
    curve = CrossfadeCurve.LINEAR,
    label = "Gapless",
    reason = "Tracks run straight into each other with no silence between them.",
)

/** No overlap at all: the next track begins when this one has finished. */
fun hardCutPlan(durationA: Double): TransitionPlan = TransitionPlan(
    type = TransitionType.CUT,
    duration = 0.0,
    startAt = max(0.0, durationA),
    inStartOffset = 0.0,
    incomingRate = 1.0,
    outgoingRate = 1.0,
    outgoingRamp = 0.0,
    tempoRelease = 0.0,
    bassSwap = false,
    sweep = false,
    curve = CrossfadeCurve.LINEAR,
    label = "No crossfade",
    reason = "Crossfade and gapless are both off, so tracks simply follow one another.",
)

fun crossfadePlan(durationA: Double, seconds: Double, curve: CrossfadeCurve): TransitionPlan {
    val duration = clamp(min(seconds, durationA * 0.4), 0.2, MAX_TRANSITION)
    val shown = String.format(java.util.Locale.ROOT, "%.1f", duration)
    return TransitionPlan(
        type = TransitionType.CROSSFADE,
        duration = duration,
        startAt = max(0.0, durationA - duration),
        inStartOffset = 0.0,
        incomingRate = 1.0,
        outgoingRate = 1.0,
        outgoingRamp = 0.0,
        tempoRelease = 0.0,
        bassSwap = false,
        sweep = false,
        curve = curve,
        label = "Crossfade · ${shown}s",
        reason = "The outgoing track fades out over $shown seconds while the next one fades in.",
    )
}

data class PlanContext(
    /** Real duration of the outgoing track, seconds. */
    val durationA: Double,
    /** Where playback currently is, so a plan is never scheduled in the past. */
    val currentTime: Double,
)

/** Whether planning needs analysis at all, so callers can skip fetching it. */
fun needsAnalysis(settings: Settings): Boolean = settings.injektEnabled

private fun fmt(value: Double, digits: Int = 0): String =
    String.format(java.util.Locale.ROOT, "%.${digits}f", value)

/**
 * Build the transition from [current] to [next]. Pure: the caller supplies
 * analysis for both tracks (or null when there is none).
 */
fun planTransition(
    current: Song,
    next: Song,
    context: PlanContext,
    settings: Settings,
    analysisA: TrackAnalysis?,
    analysisB: TrackAnalysis?,
): TransitionPlan {
    val s = settings
    val durationA = context.durationA.takeIf { it > 0 } ?: current.duration?.toDouble() ?: 0.0

    // Streams without a length (radio) cannot be planned; let them end.
    if (durationA <= 0 || current.isRadio || next.isRadio) return hardCutPlan(durationA)

    if (!s.injektEnabled) {
        if (!s.crossfadeEnabled || s.crossfadeSeconds <= 0) {
            return if (s.gapless) gaplessPlan(durationA) else hardCutPlan(durationA)
        }
        return crossfadePlan(durationA, s.crossfadeSeconds, s.crossfadeCurve)
    }

    if (analysisA == null || analysisB == null || analysisA.bpm <= 0 || analysisB.bpm <= 0) {
        return crossfadePlan(durationA, if (s.crossfadeEnabled) s.crossfadeSeconds else 4.0, s.crossfadeCurve)
    }

    val bpmA = analysisA.bpm
    val bpmB = analysisB.bpm

    // How much of the tempo gap the *outgoing* track closes. Meeting in the
    // middle halves the artefact on both sides.
    val blend = if (s.injektTempoRamp) clamp(s.injektTempoBlend / 100, 0.0, 1.0) else 0.0
    val maxShift = s.injektMaxTempoShift / 100

    val match = matchTempo(bpmA, bpmB, blend)
    val confident = analysisA.bpmConfidence >= 0.2 && analysisB.bpmConfidence >= 0.2
    val beatMatch = s.injektBeatMatch && confident && match.worstShift <= maxShift

    val keyDistance = camelotDistance(analysisA.camelot, analysisB.camelot)
    val harmonicClash = s.injektHarmonic && keyDistance > 2
    val energyDelta = abs(analysisA.energy - analysisB.energy)

    // A calm blend gets long bars; a jarring pair gets a short, decisive one.
    var bars = s.injektBars
    if (energyDelta > 0.35) bars = min(bars, 4)
    if (harmonicClash) bars = min(bars, 4)
    if (!beatMatch) bars = min(bars, 4)

    val barSeconds = 60 / bpmA * 4
    var duration = clamp(bars * barSeconds, MIN_TRANSITION, MAX_TRANSITION)
    duration = min(duration, durationA * 0.35)

    // Prefer the musical outro; never start before "now" and never overrun.
    val latestStart = durationA - duration
    var startAt = min(analysisA.outroStart, latestStart)
    startAt = max(startAt, context.currentTime + 1)

    // The blend has to begin on a bar of the outgoing track, otherwise the two
    // beat grids meet at an arbitrary offset and "beat-matched" means nothing.
    if (beatMatch) {
        val aligned = snapDownToBar(analysisA, startAt)
        startAt = if (aligned >= context.currentTime + 0.5) aligned else aligned + barSeconds
    }

    startAt = clamp(startAt, 0.0, max(0.0, durationA - 0.5))
    // Shorten rather than overrun if alignment pushed the start point late.
    duration = min(duration, max(0.5, durationA - startAt))

    // Where the next track comes in: skip a long intro, land on a downbeat.
    var inStartOffset = 0.0
    if (s.injektSkipIntro && analysisB.introEnd > 2.5) inStartOffset = min(analysisB.introEnd, 45.0)
    if (beatMatch) inStartOffset = snapUpToBar(analysisB, inStartOffset)
    val nextDuration = next.duration?.toDouble()?.takeIf { it > 0 } ?: analysisB.duration
    inStartOffset = clamp(inStartOffset, 0.0, max(0.0, nextDuration - 30))

    val type = when {
        harmonicClash -> TransitionType.SWEEP
        beatMatch -> TransitionType.BLEND
        else -> TransitionType.CROSSFADE
    }
    val incomingRate = if (beatMatch) clamp(match.incomingRate, 0.75, 1.35) else 1.0
    val outgoingRate = if (beatMatch) clamp(match.outgoingRate, 0.75, 1.35) else 1.0
    val meetBpm = bpmA * outgoingRate

    // The outgoing track drifts into the meeting tempo *before* the blend,
    // over eight of its own bars, so by the time the next track appears the
    // two are already locked.
    var outgoingRamp = 0.0
    if (beatMatch && abs(outgoingRate - 1) > 0.0005) {
        outgoingRamp = clamp(barSeconds * 8, 6.0, 24.0)
        outgoingRamp = min(outgoingRamp, max(0.0, startAt - context.currentTime - 0.25))
    }

    val details = mutableListOf<String>()
    if (beatMatch) {
        val outDir = if (outgoingRate >= 1) "up" else "down"
        val inDir = if (incomingRate >= 1) "up" else "down"
        details += "Beat-matched at ${fmt(meetBpm)} BPM — this track $outDir ${fmt(abs(outgoingRate - 1) * 100, 1)}%, " +
            "the next $inDir ${fmt(abs(incomingRate - 1) * 100, 1)}%"
        if (outgoingRamp > 0) details += "current track eased into tempo over ${fmt(outgoingRamp)}s"
    } else {
        details += "${fmt(bpmA)} BPM into ${fmt(bpmB)} BPM, tempos too far apart to match"
    }
    details += "keys ${analysisA.camelot} → ${analysisB.camelot}"
    if (harmonicClash) details += "keys clash, so the outgoing track is filtered out instead of blended"
    if (inStartOffset > 1) details += "intro skipped to ${fmt(inStartOffset, 1)}s"

    val label = if (beatMatch) {
        "InjeKt · ${fmt(bpmA)}⇄${fmt(bpmB)} @ ${fmt(meetBpm)} BPM · $bars bars"
    } else {
        "InjeKt · ${fmt(duration, 1)}s ${if (harmonicClash) "sweep" else "blend"}"
    }

    return TransitionPlan(
        type = type,
        // `duration` was measured in the outgoing track's timeline; the fades
        // run on the clock, and during the overlap that track plays at
        // `outgoingRate`, so the same music takes proportionally longer or less.
        duration = duration / outgoingRate,
        startAt = startAt,
        inStartOffset = inStartOffset,
        incomingRate = incomingRate,
        outgoingRate = outgoingRate,
        outgoingRamp = outgoingRamp,
        tempoRelease = if (beatMatch) clamp(60 / bpmB * 4 * 8, 4.0, 30.0) else 0.0,
        bassSwap = s.injektBassSwap && (type == TransitionType.BLEND || type == TransitionType.SWEEP),
        sweep = type == TransitionType.SWEEP,
        curve = if (type == TransitionType.SWEEP) CrossfadeCurve.SHARP else CrossfadeCurve.EQUAL_POWER,
        label = label,
        reason = details.joinToString(" · "),
    )
}

// ------------------------------------------------------------- auto queue --

/**
 * How well [candidate] follows [from], 0..1. Used to keep the flow going when
 * the queue runs out, so the next track is one that will actually mix.
 */
fun affinity(from: TrackAnalysis, candidate: TrackAnalysis): Double {
    val tempoRatio = if (from.bpm > 0 && candidate.bpm > 0) candidate.bpm / from.bpm else 1.0
    val tempoDistance = minOf(
        abs(tempoRatio - 1),
        abs(tempoRatio - 2) / 2,
        abs(tempoRatio - 0.5) * 2,
    )
    val tempoScore = exp(-tempoDistance * 10)
    val keyScore = 1 - min(1.0, camelotDistance(from.camelot, candidate.camelot) / 6)
    val energyScore = 1 - min(1.0, abs(from.energy - candidate.energy) * 1.6)
    val brightnessScore = 1 - min(1.0, abs(from.brightness - candidate.brightness) * 1.4)
    return tempoScore * 0.4 + keyScore * 0.25 + energyScore * 0.25 + brightnessScore * 0.1
}

/**
 * Re-rank auto-queue candidates by how well they mix out of the seed.
 * Unanalysed tracks get a middling score so they still get a turn. The best
 * few are lightly shuffled so the result does not become repetitive.
 */
fun rankAutoQueue(
    seed: TrackAnalysis?,
    candidates: List<Pair<Song, TrackAnalysis?>>,
    count: Int,
    random: Random = Random.Default,
): List<Song> {
    if (candidates.isEmpty()) return emptyList()
    if (seed == null) return candidates.map { it.first }.shuffled(random).take(count)
    val scored = candidates.map { (song, analysis) ->
        song to (if (analysis != null) affinity(seed, analysis) else 0.45 + random.nextDouble() * 0.1)
    }.sortedByDescending { it.second }
    return scored.take(max(count, count * 3)).map { it.first }.shuffled(random).take(count)
}
