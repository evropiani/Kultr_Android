package app.kultr.android.playback

import app.kultr.core.injekt.TransitionPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A sleep timer: stop at a moment, or at the end of the current track. */
data class SleepTimer(val endsAtMillis: Long?, val endOfTrack: Boolean)

data class TransitionInfo(
    /** The plan that will take the current track out, once it has been made. */
    val upcoming: TransitionPlan?,
    /** The transition that is audible right now. */
    val active: TransitionPlan?,
    /** Id of the song the upcoming plan leads out of. */
    val fromSongId: String?,
)

/**
 * What the player knows that Media3 has no words for, shared between the
 * playback service and the UI (same process).
 */
class PlaybackHub {
    private val _transition = MutableStateFlow(TransitionInfo(null, null, null))
    val transition: StateFlow<TransitionInfo> = _transition.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

    fun publishTransition(info: TransitionInfo) {
        if (_transition.value != info) _transition.value = info
    }

    fun setSleepTimer(minutes: Int?) {
        _sleepTimer.value = minutes?.let { SleepTimer(System.currentTimeMillis() + it * 60_000L, false) }
    }

    fun sleepAtEndOfTrack() {
        _sleepTimer.value = SleepTimer(null, true)
    }

    fun clearSleepTimer() {
        _sleepTimer.value = null
    }
}
