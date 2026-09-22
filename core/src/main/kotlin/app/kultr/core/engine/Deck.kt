package app.kultr.core.engine

import app.kultr.core.api.Song
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.ReplayGainMode
import app.kultr.core.settings.Settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** One entry in the play queue. [uid] tells apart two copies of the same song. */
data class QueueItem(val uid: Long, val song: Song)

enum class DeckStatus { IDLE, BUFFERING, READY, ENDED, ERROR }

/**
 * One of the two players the engine mixes between. On Android this is an
 * ExoPlayer with Kultr's audio processor in its sink; in tests, a fake.
 *
 * Every method is called on the engine's thread.
 */
interface Deck {
    val name: String

    /** What is loaded right now, or null. */
    val item: QueueItem?
    val status: DeckStatus
    val positionMs: Long

    /** Length of the loaded item, or a value <= 0 when not known yet. */
    val durationMs: Long
    val bufferedPositionMs: Long

    /** True when audio is actually coming out. */
    val isPlaying: Boolean

    /** Load [item] paused at [startMs]. Replaces anything loaded before. */
    fun load(item: QueueItem, startMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** Unload everything and go idle. */
    fun stop()

    /**
     * Queue [item] to follow the loaded one without a gap — the deck moves on
     * by itself and reports it through [DeckListener.onAutoAdvanced]. Null
     * removes a previously queued follower.
     */
    fun setNext(item: QueueItem?)

    /** Final linear gain, fade × ReplayGain; the deck smooths the change. */
    fun setLevel(level: Float)
    fun setRate(rate: Float)

    /** Low-shelf (≈180 Hz) gain in dB — 0 is flat. Used for the bass swap. */
    fun setBassDb(db: Float)

    /** High-pass corner in Hz — 20 is effectively off. Used for the sweep. */
    fun setSweepHz(hz: Float)

    fun setListener(listener: DeckListener?)
}

interface DeckListener {
    fun onStatusChanged(deck: Deck)
    fun onError(deck: Deck, message: String)

    /** The deck moved on to the item given to [Deck.setNext] by itself. */
    fun onAutoAdvanced(deck: Deck, item: QueueItem)
}

/** Shape of a fade at `t` in 0..1, for a rising or falling fade. */
fun curveValue(curve: CrossfadeCurve, t: Double, rising: Boolean): Double {
    val x = t.coerceIn(0.0, 1.0)
    return when (curve) {
        CrossfadeCurve.LINEAR -> if (rising) x else 1 - x
        CrossfadeCurve.SMOOTH -> {
            val s = x * x * (3 - 2 * x)
            if (rising) s else 1 - s
        }
        // Fast out, slow in — keeps a busy mix from turning to mud.
        CrossfadeCurve.SHARP -> if (rising) x.pow(0.6) else 1 - x.pow(1.8)
        CrossfadeCurve.EQUAL_POWER -> if (rising) sin(x * PI / 2) else cos(x * PI / 2)
    }
}

/** Linear gain for a song, honouring the ReplayGain settings. */
fun replayGainFor(song: Song, settings: Settings): Float {
    val mode = settings.replayGainMode
    if (mode == ReplayGainMode.OFF) return 1f
    val rg = song.replayGain ?: return 1f
    val gainDb = (if (mode == ReplayGainMode.ALBUM) rg.albumGain ?: rg.trackGain else rg.trackGain) ?: return 1f
    val peak = (if (mode == ReplayGainMode.ALBUM) rg.albumPeak ?: rg.trackPeak else rg.trackPeak) ?: 1.0
    var scale = 10.0.pow((gainDb + settings.replayGainPreamp) / 20)
    // Never push a track into clipping.
    if (peak > 0 && scale * peak > 1) scale = 1 / peak
    return scale.coerceIn(0.05, 4.0).toFloat()
}
