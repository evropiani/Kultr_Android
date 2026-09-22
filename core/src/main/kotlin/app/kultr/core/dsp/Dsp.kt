package app.kultr.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Signal analysis used by InjeKt, ported from Kultr's web client.
 *
 * Everything here works on mono PCM and has no platform dependencies. The goal
 * is not musicological perfection — it is to know, for every track, roughly:
 *
 *   - how fast it is (BPM) and where its beats/downbeats land,
 *   - what key it is in (for harmonic mixing),
 *   - how loud and how bright it is (for level and EQ matching),
 *   - where the intro stops being an intro and the outro starts.
 *
 * That is enough to plan a beat-matched, harmonically sensible transition.
 */

private val PITCH_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/** Camelot wheel position per pitch class, for major and minor keys. */
private val MAJOR_CAMELOT = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
private val MINOR_CAMELOT = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

/** Krumhansl–Schmuckler key profiles. */
private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.6, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

const val FFT_SIZE = 1024
const val HOP_SIZE = 512

/** Iterative radix-2 Cooley–Tukey FFT with precomputed twiddle tables. */
class Fft(val size: Int) {
    private val cosTable: FloatArray
    private val sinTable: FloatArray
    private val rev: IntArray

    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "FFT size must be a power of two" }
        cosTable = FloatArray(size / 2) { i -> cos(-2 * PI * i / size).toFloat() }
        sinTable = FloatArray(size / 2) { i -> sin(-2 * PI * i / size).toFloat() }
        val bits = Integer.numberOfTrailingZeros(size)
        rev = IntArray(size) { i ->
            var r = 0
            for (b in 0 until bits) if (i and (1 shl b) != 0) r = r or (1 shl (bits - 1 - b))
            r
        }
    }

    /** In-place complex FFT. */
    fun transform(re: FloatArray, im: FloatArray) {
        val n = size
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var tmp = re[i]
                re[i] = re[j]
                re[j] = tmp
                tmp = im[i]
                im[i] = im[j]
                im[j] = tmp
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until half) {
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val ar = re[i + j + half]
                    val ai = im[i + j + half]
                    val tr = ar * c - ai * s
                    val ti = ar * s + ai * c
                    re[i + j + half] = re[i + j] - tr
                    im[i + j + half] = im[i + j] - ti
                    re[i + j] += tr
                    im[i + j] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}

private fun hannWindow(size: Int): FloatArray =
    FloatArray(size) { i -> (0.5 - 0.5 * cos(2 * PI * i / (size - 1))).toFloat() }

class SpectralFeatures(
    /** Onset strength per frame (spectral flux, half-wave rectified). */
    val onset: FloatArray,
    /** RMS per frame, linear. */
    val rms: FloatArray,
    /** Spectral centroid per frame, in Hz. */
    val centroid: FloatArray,
    /** Summed chroma vector over the whole signal. */
    val chroma: FloatArray,
    /** Frames per second of the frame-rate features. */
    val fps: Double,
)

private fun log2(x: Double): Double = ln(x) / ln(2.0)

/** One STFT pass that produces every frame-rate feature we need. */
fun spectralFeatures(pcm: FloatArray, sampleRate: Int): SpectralFeatures {
    val fft = Fft(FFT_SIZE)
    val window = hannWindow(FFT_SIZE)
    val frames = max(1, floor((pcm.size - FFT_SIZE).toDouble() / HOP_SIZE).toInt() + 1)
    val onset = FloatArray(frames)
    val rms = FloatArray(frames)
    val centroid = FloatArray(frames)
    val chroma = FloatArray(12)

    val re = FloatArray(FFT_SIZE)
    val im = FloatArray(FFT_SIZE)
    val bins = FFT_SIZE / 2
    val mag = FloatArray(bins)
    val prevMag = FloatArray(bins)
    val binHz = sampleRate.toDouble() / FFT_SIZE

    // Pre-map bins to pitch classes once; bins outside the musical range map to -1.
    val binPitch = IntArray(bins) { b ->
        val hz = b * binHz
        if (hz < 65 || hz > 2200) {
            -1
        } else {
            val midi = 69 + 12 * log2(hz / 440)
            ((midi.roundToInt() % 12) + 12) % 12
        }
    }

    for (f in 0 until frames) {
        val start = f * HOP_SIZE
        var sumSquares = 0.0
        for (i in 0 until FFT_SIZE) {
            val index = start + i
            val sample = if (index < pcm.size) pcm[index] else 0f
            sumSquares += sample * sample
            re[i] = sample * window[i]
            im[i] = 0f
        }
        rms[f] = sqrt(sumSquares / FFT_SIZE).toFloat()

        fft.transform(re, im)

        var flux = 0.0
        var weighted = 0.0
        var total = 0.0
        val sampleChroma = (f and 3) == 0
        for (b in 0 until bins) {
            val m = sqrt(re[b] * re[b] + im[b] * im[b])
            mag[b] = m
            val diff = m - prevMag[b]
            if (diff > 0) flux += diff
            weighted += m * b * binHz
            total += m
            val pc = binPitch[b]
            // Chroma only needs a coarse picture; sample every 4th frame.
            if (pc >= 0 && sampleChroma) chroma[pc] += m * m
        }
        onset[f] = flux.toFloat()
        centroid[f] = if (total > 1e-9) (weighted / total).toFloat() else 0f
        System.arraycopy(mag, 0, prevMag, 0, bins)
    }

    return SpectralFeatures(onset, rms, centroid, chroma, sampleRate.toDouble() / HOP_SIZE)
}

/** Subtract a moving average and half-wave rectify, which sharpens onsets. */
fun normalizeOnset(onset: FloatArray, fps: Double): FloatArray {
    val out = FloatArray(onset.size)
    val half = max(1, (fps * 0.15).roundToInt())
    val span = half * 2 + 1
    var sum = 0.0
    for (i in onset.indices) {
        sum += onset[i]
        if (i >= span) sum -= onset[i - span]
        val count = min(i + 1, span)
        val mean = sum / count
        out[i] = max(0.0, onset[i] - mean).toFloat()
    }
    var peak = 0f
    for (value in out) if (value > peak) peak = value
    if (peak > 0) for (i in out.indices) out[i] /= peak
    return out
}

/** Autocorrelation at an integer lag, normalised by the overlap length. */
private fun acfAt(signal: FloatArray, lag: Int): Double {
    val limit = signal.size - lag
    if (limit <= 0) return 0.0
    var sum = 0.0
    for (i in 0 until limit) sum += signal[i] * signal[i + lag]
    return sum / limit
}

data class TempoResult(
    val bpm: Double,
    val confidence: Double,
    /** Seconds from the start of the signal to the first beat. */
    val beatOffset: Double,
    /** Seconds from the start of the signal to the first downbeat (4/4 assumed). */
    val downbeatOffset: Double,
    /**
     * A downbeat anchored near the END of the track.
     *
     * Even a 0.3% tempo error puts a grid fitted at 0:00 a whole beat out by
     * 4:00, and the mix-out point is exactly where being out matters. So the
     * phase is fitted twice and InjeKt uses whichever anchor is closer to the
     * point it is snapping.
     */
    val outroDownbeat: Double,
)

private class CombFit(val magnitude: Double, val offset: Double)

/**
 * Correlate the onset envelope against a unit pulse train of the given period
 * over [from, to). The magnitude says how well that period fits; the argument
 * gives the phase, i.e. where the pulses actually land.
 */
private fun combFit(env: FloatArray, period: Double, from: Int, to: Int): CombFit {
    var re = 0.0
    var im = 0.0
    val step = 2 * PI / period
    for (n in from until to) {
        val angle = step * (n - from)
        re += env[n] * cos(angle)
        im += env[n] * sin(angle)
    }
    val phase = atan2(im, re)
    var offset = phase / (2 * PI) * period
    offset = ((offset % period) + period) % period
    return CombFit(sqrt(re * re + im * im), from + offset)
}

fun detectTempo(onsetNorm: FloatArray, fps: Double): TempoResult {
    val minBpm = 62.0
    val maxBpm = 190.0

    val minLag = max(2, floor(60 / maxBpm * fps).toInt())
    val maxLag = min(onsetNorm.size / 2, ceil(60 / minBpm * fps).toInt())
    if (maxLag <= minLag + 1) {
        return TempoResult(120.0, 0.0, 0.0, 0.0, 0.0)
    }

    // Autocorrelate once on the integer lag grid; everything else reads from it.
    val acf = DoubleArray(maxLag + 2)
    for (lag in minLag..maxLag + 1) acf[lag] = acfAt(onsetNorm, lag)

    var bestLag = minLag
    var bestScore = Double.NEGATIVE_INFINITY
    var scoreSum = 0.0
    var scoreCount = 0

    for (lag in minLag..maxLag) {
        var score = acf[lag]
        val double = lag * 2
        if (double <= maxLag) score += 0.55 * acf[double]
        val half = (lag / 2.0).roundToInt()
        if (half >= minLag) score += 0.25 * acf[half]
        // Prior: real dance/pop tempi cluster around 120, which suppresses the
        // classic half/double-time confusion without hard-coding a range.
        val bpm = 60 * fps / lag
        score *= exp(-0.5 * (log2(bpm / 122) / 0.5).pow(2))
        scoreSum += score
        scoreCount++
        if (score > bestScore) {
            bestScore = score
            bestLag = lag
        }
    }

    // One integer frame is ~1.5 BPM wide at 174 BPM, far too coarse to
    // beat-match with. The autocorrelation peak only has to pick the right
    // octave; the precise period comes from a matched pulse train, whose
    // response sharpens with the length of the track.
    val length = onsetNorm.size
    var period = bestLag.toDouble()
    var bestMagnitude = -1.0
    val low = bestLag * 0.96
    val high = bestLag * 1.04
    val step = max(0.0005, bestLag * 0.0002)
    var candidate = low
    while (candidate <= high) {
        val magnitude = combFit(onsetNorm, candidate, 0, length).magnitude
        if (magnitude > bestMagnitude) {
            bestMagnitude = magnitude
            period = candidate
        }
        candidate += step
    }

    val bestBpm = 60 * fps / period
    val meanScore = if (scoreCount > 0) scoreSum / scoreCount else 0.0
    val confidence = if (meanScore > 1e-12) min(1.0, (bestScore / meanScore - 1) / 4) else 0.0

    val beatOffset = combFit(onsetNorm, period, 0, length).offset

    // Which of the four beats in a bar carries the most weight.
    var bestBar = 0
    var bestBarScore = -1.0
    for (b in 0 until 4) {
        var sum = 0.0
        var pos = beatOffset + b * period
        while (pos < length) {
            val index = pos.roundToInt()
            if (index in 0 until length) sum += onsetNorm[index]
            pos += period * 4
        }
        if (sum > bestBarScore) {
            bestBarScore = sum
            bestBar = b
        }
    }

    // Re-fit the phase over the tail so the mix-out grid is anchored where the
    // transition actually happens rather than four minutes earlier.
    val tailFrom = max(0, length - (fps * 75).roundToInt())
    val tail = combFit(onsetNorm, period, tailFrom, length)
    val bar = period * 4
    // Keep the same beat-in-bar as the global fit.
    val barPhase = (beatOffset + bestBar * period) % bar
    var outroDownbeat = tail.offset + ((barPhase - (tail.offset % bar) + bar) % bar)
    if (outroDownbeat >= length) outroDownbeat -= bar

    return TempoResult(
        bpm = (bestBpm * 100).roundToInt() / 100.0,
        confidence = (max(0.0, confidence) * 100).roundToInt() / 100.0,
        beatOffset = beatOffset / fps,
        downbeatOffset = (beatOffset + bestBar * period) / fps,
        outroDownbeat = max(0.0, outroDownbeat) / fps,
    )
}

enum class KeyMode { MAJOR, MINOR }

data class KeyResult(
    val key: Int,
    val mode: KeyMode,
    val confidence: Double,
    val name: String,
    val camelot: String,
)

private fun pearson(a: DoubleArray, b: DoubleArray): Double {
    val n = a.size
    val meanA = a.sum() / n
    val meanB = b.sum() / n
    var num = 0.0
    var denA = 0.0
    var denB = 0.0
    for (i in 0 until n) {
        val da = a[i] - meanA
        val db = b[i] - meanB
        num += da * db
        denA += da * da
        denB += db * db
    }
    val den = sqrt(denA * denB)
    return if (den > 1e-12) num / den else 0.0
}

fun detectKey(chroma: FloatArray): KeyResult {
    val rotated = DoubleArray(12)
    var best = KeyResult(0, KeyMode.MAJOR, 0.0, "C", "${MAJOR_CAMELOT[0]}B")
    var bestScore = -2.0
    var second = -2.0

    for (root in 0 until 12) {
        for (mode in KeyMode.entries) {
            val profile = if (mode == KeyMode.MAJOR) MAJOR_PROFILE else MINOR_PROFILE
            for (i in 0 until 12) rotated[i] = chroma[(root + i) % 12].toDouble()
            val score = pearson(rotated, profile)
            if (score > bestScore) {
                second = bestScore
                bestScore = score
                val camelot = if (mode == KeyMode.MAJOR) "${MAJOR_CAMELOT[root]}B" else "${MINOR_CAMELOT[root]}A"
                best = KeyResult(
                    key = root,
                    mode = mode,
                    confidence = 0.0,
                    name = PITCH_NAMES[root] + if (mode == KeyMode.MINOR) "m" else "",
                    camelot = camelot,
                )
            } else if (score > second) {
                second = score
            }
        }
    }

    val confidence = ((bestScore - second) * 500).roundToInt() / 100.0
    return best.copy(confidence = confidence.coerceIn(0.0, 1.0))
}

data class StructureResult(
    /** Seconds at which the intro stops being quiet/sparse. */
    val introEnd: Double,
    /** Seconds at which the track starts winding down. */
    val outroStart: Double,
    /** Mean loudness over the body of the track, 0..1. */
    val energy: Double,
    /** 0..1 perceptual-ish brightness. */
    val brightness: Double,
    val peak: Double,
)

fun detectStructure(
    rms: FloatArray,
    centroid: FloatArray,
    fps: Double,
    sampleRate: Int,
    duration: Double,
): StructureResult {
    // Smooth the RMS envelope over ~1s so single hits do not look like sections.
    val window = max(1, fps.roundToInt())
    val smooth = FloatArray(rms.size)
    var running = 0.0
    for (i in rms.indices) {
        running += rms[i]
        if (i >= window) running -= rms[i - window]
        smooth[i] = (running / min(i + 1, window)).toFloat()
    }

    val sorted = smooth.copyOf().also { it.sort() }
    val p90 = sorted.getOrElse(floor(sorted.size * 0.9).toInt()) { 0f }.toDouble()
    val median = sorted.getOrElse(floor(sorted.size * 0.5).toInt()) { 0f }.toDouble()
    val enterThreshold = p90 * 0.42
    val leaveThreshold = p90 * 0.3
    val sustain = (fps * 1.5).roundToInt()

    var introEnd = 0.0
    for (i in 0 until smooth.size - sustain) {
        if (smooth[i] < enterThreshold) continue
        var held = true
        for (j in i until i + sustain) {
            if (smooth[j] < leaveThreshold) {
                held = false
                break
            }
        }
        if (held) {
            introEnd = i / fps
            break
        }
    }

    var outroStart = duration
    var i = smooth.size - 1
    while (i >= sustain) {
        if (smooth[i] >= enterThreshold) {
            outroStart = min(duration, (i + 1) / fps)
            break
        }
        i--
    }

    var peak = 0.0
    for (value in rms) if (value > peak) peak = value.toDouble()

    var centroidSum = 0.0
    var centroidCount = 0
    for (k in centroid.indices) {
        if (rms[k] > median * 0.5) {
            centroidSum += centroid[k]
            centroidCount++
        }
    }
    val meanCentroid = if (centroidCount > 0) centroidSum / centroidCount else 0.0

    return StructureResult(
        introEnd = min(introEnd, duration * 0.3),
        outroStart = max(outroStart, duration * 0.5),
        energy = min(1.0, median * 4),
        brightness = min(1.0, meanCentroid / (sampleRate / 4.0)),
        peak = peak,
    )
}

data class PcmAnalysis(
    val duration: Double,
    val bpm: Double,
    val bpmConfidence: Double,
    val beatOffset: Double,
    val downbeatOffset: Double,
    val outroDownbeat: Double,
    val key: Int,
    val keyName: String,
    val mode: KeyMode,
    val keyConfidence: Double,
    val camelot: String,
    val energy: Double,
    val brightness: Double,
    val peak: Double,
    val introEnd: Double,
    val outroStart: Double,
)

/** Full analysis pass over decoded mono PCM. */
fun analysePcm(pcm: FloatArray, sampleRate: Int): PcmAnalysis {
    val duration = pcm.size.toDouble() / sampleRate
    val features = spectralFeatures(pcm, sampleRate)
    val onsetNorm = normalizeOnset(features.onset, features.fps)
    val tempo = detectTempo(onsetNorm, features.fps)
    val key = detectKey(features.chroma)
    val structure = detectStructure(features.rms, features.centroid, features.fps, sampleRate, duration)
    return PcmAnalysis(
        duration = duration,
        bpm = tempo.bpm,
        bpmConfidence = tempo.confidence,
        beatOffset = tempo.beatOffset,
        downbeatOffset = tempo.downbeatOffset,
        outroDownbeat = tempo.outroDownbeat,
        key = key.key,
        keyName = key.name,
        mode = key.mode,
        keyConfidence = key.confidence,
        camelot = key.camelot,
        energy = structure.energy,
        brightness = structure.brightness,
        peak = structure.peak,
        introEnd = structure.introEnd,
        outroStart = structure.outroStart,
    )
}

private val CAMELOT = Regex("^(\\d{1,2})([AB])$")

/** Distance on the Camelot wheel: 0 = same key, 1 = neighbour, up to 6. */
fun camelotDistance(a: String, b: String): Double {
    val left = CAMELOT.matchEntire(a) ?: return 6.0
    val right = CAMELOT.matchEntire(b) ?: return 6.0
    val ln = left.groupValues[1].toInt()
    val rn = right.groupValues[1].toInt()
    val ring = min(abs(ln - rn), 12 - abs(ln - rn)).toDouble()
    val relative = if (left.groupValues[2] == right.groupValues[2]) 0 else 1
    return ring + relative * (if (ring == 0.0) 0.5 else 1.0)
}
