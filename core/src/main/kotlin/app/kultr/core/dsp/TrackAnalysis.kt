package app.kultr.core.dsp

import app.kultr.core.api.Song
import kotlin.math.max

/**
 * Bump this whenever the DSP changes in a way that invalidates cached numbers.
 * Cached entries with an older version are recomputed on demand.
 */
const val ANALYSIS_VERSION = 4

/** Bitrate requested for analysis streams — small, fast and plenty accurate. */
const val ANALYSIS_BITRATE = 96

/** Target rate for analysis; 22.05 kHz mono is plenty for tempo and key. */
const val ANALYSIS_TARGET_RATE = 22050

enum class BpmSource { DSP, TAG }

/** Everything InjeKt knows about one track. */
data class TrackAnalysis(
    val songId: String,
    val version: Int = ANALYSIS_VERSION,
    val analysedAt: Long,
    val bpmSource: BpmSource,
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
) {
    val isCurrent: Boolean get() = version == ANALYSIS_VERSION

    companion object {
        /**
         * Turn a raw DSP pass into a stored analysis. Navidrome exposes a BPM
         * tag; it is trusted when our own estimate is shaky.
         */
        fun from(song: Song, pcm: PcmAnalysis, now: Long = System.currentTimeMillis()): TrackAnalysis {
            val tagBpm = song.bpm?.takeIf { it in 40..220 }?.toDouble()
            val useTag = tagBpm != null && pcm.bpmConfidence < 0.45
            return TrackAnalysis(
                songId = song.id,
                analysedAt = now,
                bpmSource = if (useTag) BpmSource.TAG else BpmSource.DSP,
                // Prefer the server's duration; decoded duration can drift on VBR files.
                duration = song.duration?.toDouble()?.takeIf { it > 0 } ?: pcm.duration,
                bpm = if (useTag) tagBpm!! else pcm.bpm,
                bpmConfidence = if (useTag) 0.8 else pcm.bpmConfidence,
                beatOffset = pcm.beatOffset,
                downbeatOffset = pcm.downbeatOffset,
                outroDownbeat = pcm.outroDownbeat,
                key = pcm.key,
                keyName = pcm.keyName,
                mode = pcm.mode,
                keyConfidence = pcm.keyConfidence,
                camelot = pcm.camelot,
                energy = pcm.energy,
                brightness = pcm.brightness,
                peak = pcm.peak,
                introEnd = pcm.introEnd,
                outroStart = pcm.outroStart,
            )
        }
    }
}

/**
 * Collects decoded interleaved PCM and folds it into mono at roughly
 * [ANALYSIS_TARGET_RATE], by averaging channels and then blocks of samples.
 *
 * Averaging is a crude low-pass, which is exactly enough: tempo and key
 * detection look at onsets below a few kHz.
 */
class MonoAccumulator(inputRate: Int, private val channels: Int, targetRate: Int = ANALYSIS_TARGET_RATE) {
    private val factor = max(1, Math.round(inputRate.toDouble() / targetRate).toInt())
    val sampleRate: Int = inputRate / factor

    private var buffer = FloatArray(1 shl 16)
    private var size = 0
    private var acc = 0.0
    private var accCount = 0

    val length: Int get() = size

    /** Add interleaved 16-bit frames. */
    fun addPcm16(samples: ShortArray, count: Int = samples.size) {
        var i = 0
        while (i + channels <= count) {
            var sum = 0
            for (c in 0 until channels) sum += samples[i + c]
            push(sum / (channels * 32768.0))
            i += channels
        }
    }

    /** Add interleaved float frames. */
    fun addFloat(samples: FloatArray, count: Int = samples.size) {
        var i = 0
        while (i + channels <= count) {
            var sum = 0.0
            for (c in 0 until channels) sum += samples[i + c]
            push(sum / channels)
            i += channels
        }
    }

    private fun push(value: Double) {
        acc += value
        accCount++
        if (accCount == factor) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = (acc / factor).toFloat()
            acc = 0.0
            accCount = 0
        }
    }

    fun toArray(): FloatArray = buffer.copyOf(size)
}
