package app.kultr.android.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import app.kultr.android.MainActivity
import app.kultr.android.playback.PlaybackService

/**
 * The widget's buttons.
 *
 * While Kultr's playback service is running, commands go straight to its
 * session player — the same one the notification, lock screen and Android
 * Auto use. When it is not running, play/pause starts it with a media button
 * event, and the session resumes the last queue; next and previous have
 * nothing to act on then, so they open the app instead.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val service = PlaybackService.current
        if (service != null) {
            service.onWidgetAction(action)
            return
        }
        when {
            action == ACTION_PLAY_PAUSE && PlaybackService.canResume(context) -> {
                val play = Intent(context, PlaybackService::class.java)
                    .setAction(Intent.ACTION_MEDIA_BUTTON)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                // A tap on a widget lets an app start a foreground service from the background.
                runCatching { ContextCompat.startForegroundService(context, play) }
                    .onFailure { openApp(context) }
            }
            else -> openApp(context)
        }
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "app.kultr.android.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "app.kultr.android.widget.NEXT"
        const val ACTION_PREVIOUS = "app.kultr.android.widget.PREVIOUS"

        fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
