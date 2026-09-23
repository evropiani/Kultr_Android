package app.kultr.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.kultr.core.settings.EQ_BANDS
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Equaliser settings shared by both decks; [version] bumps on every change. */
class EqState {
    @Volatile var enabled = false
        private set
    @Volatile var gainsDb = FloatArray(EQ_BANDS.size)
        private set
    @Volatile var preampDb = 0f
        private set
    @Volatile var version = 0
        private set

    fun update(enabled: Boolean, gains: List<Double>, preamp: Double) {
        val next = FloatArray(EQ_BANDS.size) { gains.getOrElse(it) { 0.0 }.toFloat() }
        if (enabled == this.enabled && next.contentEquals(gainsDb) && preamp.toFloat() == preampDb) return
        this.enabled = enabled
        gainsDb = next
        preampDb = preamp.toFloat()
        version++
    }
}

/** One RBJ-cookbook biquad, with state for up to eight channels. */
internal class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val x1 = DoubleArray(8)
    private val x2 = DoubleArray(8)
    private val y1 = DoubleArray(8)
    private val y2 = DoubleArray(8)
    var bypass = true

    fun reset() {
        x1.fill(0.0)
        x2.fill(0.0)
        y1.fill(0.0)
        y2.fill(0.0)
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun lowShelf(rate: Int, frequency: Double, gainDb: Double) {
        bypass = kotlin.math.abs(gainDb) < 0.05
        if (bypass) return
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * min(frequency, rate * 0.45) / rate
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * cw + sa),
            2 * a * ((a - 1) - (a + 1) * cw),
            a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa,
            -2 * ((a - 1) + (a + 1) * cw),
            (a + 1) + (a - 1) * cw - sa,
        )
    }

    fun highShelf(rate: Int, frequency: Double, gainDb: Double) {
        bypass = kotlin.math.abs(gainDb) < 0.05
        if (bypass) return
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * min(frequency, rate * 0.45) / rate
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) + (a - 1) * cw + sa),
            -2 * a * ((a - 1) + (a + 1) * cw),
            a * ((a + 1) + (a - 1) * cw - sa),
            (a + 1) - (a - 1) * cw + sa,
            2 * ((a - 1) - (a + 1) * cw),
            (a + 1) - (a - 1) * cw - sa,
        )
    }

    fun peaking(rate: Int, frequency: Double, gainDb: Double, q: Double) {
        bypass = kotlin.math.abs(gainDb) < 0.05 || frequency >= rate * 0.49
        if (bypass) return
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * frequency / rate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
    }

    fun highPass(rate: Int, frequency: Double, q: Double) {
        bypass = frequency <= 21
        if (bypass) return
        val w0 = 2 * PI * min(frequency, rate * 0.45) / rate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    fun process(x: Double, channel: Int): Double {
        val y = b0 * x + b1 * x1[channel] + b2 * x2[channel] - a1 * y1[channel] - a2 * y2[channel]
        x2[channel] = x1[channel]
        x1[channel] = x
        y2[channel] = y1[channel]
        y1[channel] = y
        return y
    }
}

/**
 * Everything a deck does to its audio, in one pass: the bass-swap low shelf,
 * the sweep high-pass, the equaliser, and the fade/ReplayGain level.
 *
 * Parameters are written from the main thread and read on the playback
 * thread; the level ramps across each buffer so fades never click.
 */
@UnstableApi
class DeckProcessor(private val eq: EqState) : BaseAudioProcessor() {
    @Volatile var targetGain = 0f
    @Volatile var bassDb = 0f
    @Volatile var sweepHz = 20f

    private var gain = 0f
    private var channels = 2
    private var rate = 44_100
    private var isFloat = false

    private val bass = Biquad()
    private val sweep = Biquad()
    private val bands = Array(EQ_BANDS.size) { Biquad() }
    private var appliedBass = Float.NaN
    private var appliedSweep = Float.NaN
    private var appliedEq = -1
    private var eqOn = false
    private var preamp = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount > 8) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        channels = max(1, inputAudioFormat.channelCount)
        rate = max(8_000, inputAudioFormat.sampleRate)
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        appliedBass = Float.NaN
        appliedSweep = Float.NaN
        appliedEq = -1
        bass.reset()
        sweep.reset()
        bands.forEach { it.reset() }
        gain = targetGain
    }

    override fun onReset() {
        gain = 0f
    }

    private fun refreshFilters() {
        val b = bassDb
        if (b != appliedBass) {
            bass.lowShelf(rate, 180.0, b.toDouble())
            appliedBass = b
        }
        val s = sweepHz
        if (s != appliedSweep) {
            sweep.highPass(rate, s.toDouble(), 0.7)
            appliedSweep = s
        }
        val version = eq.version
        if (version != appliedEq) {
            appliedEq = version
            eqOn = eq.enabled
            val gains = eq.gainsDb
            bands.forEachIndexed { index, filter ->
                val g = if (eqOn) gains.getOrElse(index) { 0f }.toDouble() else 0.0
                val f = EQ_BANDS[index].toDouble()
                when (index) {
                    0 -> filter.lowShelf(rate, f, g)
                    EQ_BANDS.size - 1 -> filter.highShelf(rate, f, g)
                    else -> filter.peaking(rate, f, g, 1.1)
                }
            }
            preamp = if (eqOn) 10.0.pow(eq.preampDb / 20.0) else 1.0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val start = inputBuffer.position()
        val end = inputBuffer.limit()
        val size = end - start
        if (size <= 0) return
        val output = replaceOutputBuffer(size)
        refreshFilters()

        val bytesPerSample = if (isFloat) 4 else 2
        val frames = size / (bytesPerSample * channels)
        val from = gain
        val to = targetGain
        val step = if (frames > 0) (to - from) / frames else 0f
        var level = from
        val useBass = !bass.bypass
        val useSweep = !sweep.bypass
        val activeBands = bands.filter { !it.bypass }
        val pre = preamp

        for (frame in 0 until frames) {
            level += step
            for (channel in 0 until channels) {
                var x = if (isFloat) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                if (useBass) x = bass.process(x, channel)
                if (useSweep) x = sweep.process(x, channel)
                for (band in activeBands) x = band.process(x, channel)
                x *= pre * level
                if (isFloat) {
                    output.putFloat(x.toFloat())
                } else {
                    val v = (x * 32767.0).coerceIn(-32768.0, 32767.0)
                    output.putShort(v.toInt().toShort())
                }
            }
        }
        // Anything left over (a partial frame) is copied as-is.
        while (inputBuffer.position() < end) output.put(inputBuffer.get())
        gain = to
        output.flip()
    }
}
