package app.kultr.core.dsp

import app.kultr.core.api.Song
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspTest {
    private val rate = 22050

    /** Short decaying noise bursts on every beat, louder on the downbeat. */
    private fun clickTrack(bpm: Double, seconds: Double, introSilence: Double = 0.0): FloatArray {
        val pcm = FloatArray((seconds * rate).toInt())
        val random = Random(42)
        val beat = 60.0 / bpm
        var t = introSilence
        var n = 0
        while (t < seconds) {
            val start = (t * rate).toInt()
            val gain = if (n % 4 == 0) 1.0 else 0.55
            for (i in 0 until (0.06 * rate).toInt()) {
                val index = start + i
                if (index >= pcm.size) break
                val env = exp(-i / (0.012 * rate))
                pcm[index] += ((random.nextDouble() * 2 - 1) * env * gain).toFloat()
            }
            t += beat
            n++
        }
        return pcm
    }

    private fun tones(frequencies: List<Pair<Double, Double>>, seconds: Double): FloatArray {
        val pcm = FloatArray((seconds * rate).toInt())
        for (i in pcm.indices) {
            var v = 0.0
            for ((hz, amp) in frequencies) v += amp * sin(2 * PI * hz * i / rate)
            pcm[i] = (v * 0.2).toFloat()
        }
        return pcm
    }

    @Test
    fun fftFindsASineInTheRightBin() {
        val size = 64
        val fft = Fft(size)
        val re = FloatArray(size) { sin(2 * PI * 5 * it / size).toFloat() }
        val im = FloatArray(size)
        fft.transform(re, im)
        val mags = (0 until size / 2).map { kotlin.math.sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(5, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun detectsTempoOfAClickTrack() {
        for (bpm in listOf(120.0, 128.0, 95.0, 174.0)) {
            val pcm = clickTrack(bpm, 60.0)
            val result = analysePcm(pcm, rate)
            assertTrue(abs(result.bpm - bpm) < 1.0, "expected $bpm, got ${result.bpm}")
            assertTrue(result.bpmConfidence > 0.2, "confidence ${result.bpmConfidence} at $bpm")
        }
    }

    @Test
    fun findsTheFirstBeatAndTheIntro() {
        val pcm = clickTrack(120.0, 60.0, introSilence = 8.0)
        val result = analysePcm(pcm, rate)
        // Beats every 0.5s starting at 8s, so the grid is phase-aligned to 0.
        val phase = result.beatOffset % 0.5
        assertTrue(phase < 0.05 || phase > 0.45, "beat phase $phase")
        assertTrue(result.introEnd in 6.5..9.5, "intro end ${result.introEnd}")
    }

    @Test
    fun detectsMajorAndMinorKeys() {
        // C major: tonic triad heavy, plus the rest of the scale.
        val cMajor = tones(
            listOf(261.63 to 1.0, 329.63 to 0.8, 392.0 to 0.9, 293.66 to 0.3, 349.23 to 0.3, 440.0 to 0.3, 493.88 to 0.3),
            20.0,
        )
        val key = detectKey(spectralFeatures(cMajor, rate).chroma)
        assertEquals("C", key.name)
        assertEquals("8B", key.camelot)

        // A minor.
        val aMinor = tones(
            listOf(220.0 to 1.0, 261.63 to 0.8, 329.63 to 0.9, 246.94 to 0.3, 293.66 to 0.3, 349.23 to 0.3, 392.0 to 0.3),
            20.0,
        )
        val minor = detectKey(spectralFeatures(aMinor, rate).chroma)
        assertEquals(KeyMode.MINOR, minor.mode)
        assertEquals("8A", minor.camelot)
    }

    @Test
    fun camelotDistances() {
        assertEquals(0.0, camelotDistance("8A", "8A"))
        assertEquals(0.5, camelotDistance("8A", "8B"))
        assertEquals(1.0, camelotDistance("8A", "9A"))
        assertEquals(1.0, camelotDistance("12B", "1B"))
        assertEquals(2.0, camelotDistance("8A", "9B"))
        assertEquals(6.0, camelotDistance("8A", "nonsense"))
    }

    @Test
    fun tagBpmWinsWhenTheDspIsUnsure() {
        val pcm = analysePcm(clickTrack(120.0, 30.0), rate).copy(bpmConfidence = 0.1)
        val song = Song(id = "x", bpm = 126, duration = 30)
        val analysis = TrackAnalysis.from(song, pcm, now = 1)
        assertEquals(126.0, analysis.bpm)
        assertEquals(BpmSource.TAG, analysis.bpmSource)
        val confident = TrackAnalysis.from(song, pcm.copy(bpmConfidence = 0.9), now = 1)
        assertEquals(BpmSource.DSP, confident.bpmSource)
    }

    @Test
    fun monoAccumulatorDownmixesAndDecimates() {
        val acc = MonoAccumulator(inputRate = 44100, channels = 2)
        assertEquals(22050, acc.sampleRate)
        // Left +1.0, right 0.0 → mono 0.5, two frames averaged into one sample.
        val frames = ShortArray(8) { if (it % 2 == 0) Short.MAX_VALUE else 0 }
        acc.addPcm16(frames)
        val out = acc.toArray()
        assertEquals(2, out.size)
        assertTrue(abs(out[0] - 0.5f) < 0.001f)

        val floats = MonoAccumulator(inputRate = 48000, channels = 1)
        assertEquals(24000, floats.sampleRate)
        floats.addFloat(FloatArray(10) { 0.25f })
        assertEquals(5, floats.length)
    }
}
