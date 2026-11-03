package app.kultr.core.injekt

import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.dsp.camelotDistance
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** The shape of a set: energy from 0 to 1 at evenly spaced points from its start to its end. */
class EnergyCurve(points: List<Double>) {
    val points: List<Double> = if (points.size >= 2) points.map { it.coerceIn(0.0, 1.0) } else listOf(0.5, 0.5)

    /** Where the line is at [fraction] (0 to 1) of the set. */
    fun valueAt(fraction: Double): Double {
        val f = fraction.coerceIn(0.0, 1.0) * (points.size - 1)
        val i = min(points.size - 2, f.toInt())
        val t = f - i
        return points[i] * (1 - t) + points[i + 1] * t
    }

    /** The same shape with [count] points, for drawing over. */
    fun resampled(count: Int): EnergyCurve {
        val n = max(2, count)
        return EnergyCurve(List(n) { valueAt(it / (n - 1).toDouble()) })
    }

    override fun equals(other: Any?) = other is EnergyCurve && other.points == points
    override fun hashCode() = points.hashCode()

    companion object {
        val WARM_UP_AND_PEAK = EnergyCurve(listOf(0.35, 0.5, 0.65, 0.8, 0.95, 0.9, 0.7, 0.5))
        val STEADY = EnergyCurve(listOf(0.6, 0.6))
        val BUILD = EnergyCurve(listOf(0.3, 0.95))
        val WIND_DOWN = EnergyCurve(listOf(0.7, 0.45, 0.25, 0.15))
    }
}

/** One track of a planned set: when it starts (seconds from the start), and the energy the line asked for there. */
data class PlannedTrack(val song: Song, val energy: Double, val target: Double, val startsAt: Double)

/** How well [b] mixes out of [a]: close tempo (or half or double) and a neighbouring key. */
fun mixScore(a: TrackAnalysis, b: TrackAnalysis): Double {
    val ratio = if (a.bpm > 0 && b.bpm > 0) b.bpm / a.bpm else 1.0
    val tempoDistance = minOf(abs(ratio - 1), abs(ratio - 2) / 2, abs(ratio - 0.5) * 2)
    val tempo = exp(-tempoDistance * 10)
    val key = 1 - min(1.0, camelotDistance(a.camelot, b.camelot) / 6)
    return tempo * 0.6 + key * 0.4
}

/**
 * A set of about [minutes] that follows [curve]: each next track is the one
 * whose energy is closest to where the line is at that moment, that mixes
 * well out of the one before (tempo and key), isn't by the same artist as it
 * and hasn't been used. Mixes overlap by [overlap] seconds.
 */
fun planSet(
    curve: EnergyCurve,
    minutes: Int,
    candidates: List<Pair<Song, TrackAnalysis>>,
    overlap: Double = 8.0,
    random: Random = Random.Default,
): List<PlannedTrack> {
    val total = max(1, minutes) * 60.0
    val used = HashSet<String>()
    val usable = candidates.filter { it.second.duration >= 60 && !it.first.isRadio }
    val out = ArrayList<PlannedTrack>()
    var elapsed = 0.0
    var previous: Pair<Song, TrackAnalysis>? = null
    while (elapsed < total && out.size < 300) {
        val target = curve.valueAt(elapsed / total)
        val before = previous
        val best = usable.filter { it.first.id !in used }.maxByOrNull { (song, analysis) ->
            val fit = 1 - min(1.0, abs(analysis.energy - target) * 3)
            var score = fit * 0.65 + (before?.let { mixScore(it.second, analysis) } ?: 1.0) * 0.35
            val artist = before?.first?.artistId
            if (artist != null && artist == song.artistId) score *= 0.7
            score + random.nextDouble(0.0, 0.04)
        } ?: break
        used += best.first.id
        out += PlannedTrack(best.first, best.second.energy, target, elapsed)
        elapsed += max(30.0, best.second.duration - overlap)
        previous = best
    }
    return out
}
