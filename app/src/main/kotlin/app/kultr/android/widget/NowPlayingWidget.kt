package app.kultr.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.media3.common.Player
import app.kultr.android.KultrApp
import app.kultr.android.MainActivity
import app.kultr.android.R
import app.kultr.android.playback.MediaItems
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the widget shows. Kept on disk so it survives the app being closed. */
data class WidgetState(
    val title: String? = null,
    val artist: String? = null,
    val artUrl: String? = null,
    val playing: Boolean = false,
)

/**
 * The home screen widget: artwork, title, artist, and previous / play-pause /
 * next. Each widget has its own background opacity, chosen when it is placed.
 *
 * The playback service pushes changes here ([publish]); the launcher asks for
 * a redraw through [onUpdate], which draws the last state saved to disk.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        KultrApp.graph.scope.launch {
            try {
                render(context, ids, loadState(context))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        prefs(context).edit { ids.forEach { remove(opacityKey(it)) } }
    }

    companion object {
        /** 100 is a solid background, 0 an invisible one. */
        const val DEFAULT_OPACITY = 85

        private const val PREFS = "kultr.widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ART = "art"
        private const val KEY_PLAYING = "playing"
        private const val ART_PIXELS = 256

        /** The last artwork drawn, so a play/pause change does not reload it. */
        @Volatile private var artCache: Pair<String, Bitmap>? = null

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun opacityKey(id: Int) = "opacity_$id"

        fun opacity(context: Context, widgetId: Int): Int =
            prefs(context).getInt(opacityKey(widgetId), DEFAULT_OPACITY).coerceIn(0, 100)

        fun setOpacity(context: Context, widgetId: Int, percent: Int) {
            prefs(context).edit { putInt(opacityKey(widgetId), percent.coerceIn(0, 100)) }
        }

        fun loadState(context: Context): WidgetState {
            val p = prefs(context)
            return WidgetState(
                title = p.getString(KEY_TITLE, null),
                artist = p.getString(KEY_ARTIST, null),
                artUrl = p.getString(KEY_ART, null),
                playing = p.getBoolean(KEY_PLAYING, false),
            )
        }

        private fun saveState(context: Context, state: WidgetState) {
            prefs(context).edit {
                putString(KEY_TITLE, state.title)
                putString(KEY_ARTIST, state.artist)
                putString(KEY_ART, state.artUrl)
                putBoolean(KEY_PLAYING, state.playing)
            }
        }

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))

        /** What [player] is doing, as the widget should show it. */
        fun stateOf(player: Player): WidgetState {
            val item = player.currentMediaItem ?: return WidgetState()
            val song = MediaItems.songOf(item)
            val metadata = item.mediaMetadata
            return WidgetState(
                title = song?.title ?: metadata.title?.toString(),
                artist = song?.artist ?: metadata.artist?.toString(),
                artUrl = metadata.artworkUri?.toString(),
                playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            )
        }

        /** Called by the playback service whenever the track or play state changes. */
        fun publish(context: Context, state: WidgetState) {
            if (state == loadState(context)) return
            saveState(context, state)
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            KultrApp.graph.scope.launch { render(context, ids, state) }
        }

        /** Redraw specific widgets from the saved state, e.g. after their opacity changed. */
        fun refresh(context: Context, ids: IntArray = widgetIds(context)) {
            if (ids.isEmpty()) return
            KultrApp.graph.scope.launch { render(context, ids, loadState(context)) }
        }

        private suspend fun render(context: Context, ids: IntArray, state: WidgetState) {
            val art = state.artUrl?.let { artwork(context, it) }
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids) manager.updateAppWidget(id, views(context, id, state, art))
        }

        private fun views(context: Context, widgetId: Int, state: WidgetState, art: Bitmap?): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                // Only the background view fades, so artwork, text and buttons stay fully visible.
                setInt(R.id.widget_background, "setImageAlpha", opacity(context, widgetId) * 255 / 100)

                val hasTrack = state.title != null
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_idle_title))
                setTextViewText(
                    R.id.widget_artist,
                    if (hasTrack) state.artist.orEmpty() else context.getString(R.string.widget_idle_subtitle),
                )
                if (art != null) {
                    setImageViewBitmap(R.id.widget_art, art)
                } else {
                    setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
                }
                setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(
                    R.id.widget_play,
                    context.getString(if (state.playing) R.string.widget_pause else R.string.widget_play),
                )

                setOnClickPendingIntent(R.id.widget_play, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_previous, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_PREVIOUS))
                val open = openApp(context)
                setOnClickPendingIntent(R.id.widget_art, open)
                setOnClickPendingIntent(R.id.widget_info, open)
            }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Artwork for [url], small and with rounded corners, from Coil's caches when it can. */
        private suspend fun artwork(context: Context, url: String): Bitmap? {
            artCache?.let { (cachedUrl, bitmap) -> if (cachedUrl == url) return bitmap }
            val bitmap = withTimeoutOrNull(10_000) {
                val request = ImageRequest.Builder(context).data(url).size(ART_PIXELS).allowHardware(false).build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            } ?: return null
            val rounded = withContext(Dispatchers.Default) { rounded(bitmap, ART_PIXELS) }
            artCache = url to rounded
            return rounded
        }

        /** Remote views cannot clip, so the corners are rounded into the bitmap itself. */
        private fun rounded(source: Bitmap, size: Int): Bitmap {
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val matrix = android.graphics.Matrix()
                matrix.setScale(scale, scale)
                matrix.postTranslate((size - source.width * scale) / 2f, (size - source.height * scale) / 2f)
                setLocalMatrix(matrix)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            val radius = size * 0.12f
            Canvas(output).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
            return output
        }
    }
}
