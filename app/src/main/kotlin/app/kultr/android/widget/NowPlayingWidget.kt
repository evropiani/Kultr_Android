package app.kultr.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.os.BundleCompat
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
import kotlin.math.max
import kotlin.math.roundToInt

/** What the widget shows. Kept on disk so it survives the app being closed. */
data class WidgetState(
    val title: String? = null,
    val artist: String? = null,
    val artUrl: String? = null,
    val playing: Boolean = false,
)

/**
 * The home screen widget: artwork, title, artist, and previous / play-pause /
 * next, centred and scaled to the widget's size. Each widget has its own
 * background opacity and its own choice of showing the artwork, set when it
 * is placed.
 *
 * The playback service pushes changes here ([publish]); the launcher asks for
 * a redraw through [onUpdate] and, after a resize, [onAppWidgetOptionsChanged].
 * Both draw the last state saved to disk.
 */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        redraw(context, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resized: lay it out again for its new size.
        redraw(context, intArrayOf(id))
    }

    private fun redraw(context: Context, ids: IntArray) {
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
        prefs(context).edit {
            ids.forEach {
                remove(opacityKey(it))
                remove(artKey(it))
            }
        }
    }

    private class CachedArt(val url: String, val pixels: Int, val bitmap: Bitmap)

    companion object {
        /** 100 is a solid background, 0 an invisible one. */
        const val DEFAULT_OPACITY = 85

        private const val PREFS = "kultr.widget"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ART = "art"
        private const val KEY_PLAYING = "playing"

        /** Artwork is loaded at the size it is shown, up to this. */
        private const val MAX_ART_PIXELS = 400

        /** Used until the launcher says how big the widget is: about 4×1. */
        private val DEFAULT_SIZE = SizeF(300f, 100f)
        private const val MAX_SIZES = 8

        /** The last artwork drawn, so a play/pause change does not reload it. */
        @Volatile private var artCache: CachedArt? = null

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun opacityKey(id: Int) = "opacity_$id"
        private fun artKey(id: Int) = "show_art_$id"

        fun opacity(context: Context, widgetId: Int): Int =
            prefs(context).getInt(opacityKey(widgetId), DEFAULT_OPACITY).coerceIn(0, 100)

        fun setOpacity(context: Context, widgetId: Int, percent: Int) {
            prefs(context).edit { putInt(opacityKey(widgetId), percent.coerceIn(0, 100)) }
        }

        fun showsArt(context: Context, widgetId: Int): Boolean = prefs(context).getBoolean(artKey(widgetId), true)

        fun setShowsArt(context: Context, widgetId: Int, show: Boolean) {
            prefs(context).edit { putBoolean(artKey(widgetId), show) }
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

        /**
         * The sizes the widget is shown at, in dp. Android 12 and later report
         * each one; older launchers give a range, whose narrow-and-tall end is
         * the portrait size and wide-and-short end the landscape one.
         */
        fun sizes(context: Context, widgetId: Int): List<SizeF> {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            if (Build.VERSION.SDK_INT >= 31) {
                val reported = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    ?.filter { it.width > 0f && it.height > 0f }
                    ?.distinct()
                if (!reported.isNullOrEmpty()) return reported.take(MAX_SIZES)
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (minWidth <= 0 || minHeight <= 0) return listOf(DEFAULT_SIZE)
            val portrait = SizeF(minWidth.toFloat(), max(maxHeight, minHeight).toFloat())
            val landscape = SizeF(max(maxWidth, minWidth).toFloat(), minHeight.toFloat())
            if (Build.VERSION.SDK_INT >= 31) return listOf(portrait, landscape).distinct()
            val inLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            return listOf(if (inLandscape) landscape else portrait)
        }

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

        /** Redraw specific widgets from the saved state, e.g. after their settings changed. */
        fun refresh(context: Context, ids: IntArray = widgetIds(context)) {
            if (ids.isEmpty()) return
            KultrApp.graph.scope.launch { render(context, ids, loadState(context)) }
        }

        private suspend fun render(context: Context, ids: IntArray, state: WidgetState) {
            val manager = AppWidgetManager.getInstance(context)
            val fontScale = context.resources.configuration.fontScale
            val layouts = ids.associateWith { id ->
                val showArt = showsArt(context, id)
                sizes(context, id).associateWith { WidgetLayout.compute(it.width, it.height, showArt, fontScale) }
            }
            // One bitmap, big enough for the largest artwork, shared by every widget and size.
            val largestArt = layouts.values.maxOfOrNull { specs -> specs.values.maxOf { it.art } } ?: 0f
            val density = context.resources.displayMetrics.density
            val art = if (largestArt > 0f) {
                state.artUrl?.let { artwork(context, it, (largestArt * density).roundToInt().coerceIn(96, MAX_ART_PIXELS)) }
            } else {
                null
            }
            for ((id, specs) in layouts) {
                val views = if (Build.VERSION.SDK_INT >= 31) {
                    // The launcher picks the layout for the size the widget is at right now.
                    RemoteViews(specs.mapValues { (_, spec) -> views(context, id, state, art, spec) })
                } else {
                    views(context, id, state, art, specs.values.first())
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun views(context: Context, widgetId: Int, state: WidgetState, art: Bitmap?, spec: WidgetSpec): RemoteViews {
            val density = context.resources.displayMetrics.density
            fun px(dp: Float) = (dp * density).roundToInt()

            return RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                // Only the background view fades, so artwork, text and buttons stay fully visible.
                setInt(R.id.widget_background, "setImageAlpha", opacity(context, widgetId) * 255 / 100)
                setViewPadding(R.id.widget_content, px(spec.paddingH), px(spec.paddingV), px(spec.paddingH), px(spec.paddingV))

                // Sizes are rounded down where they add up across the widget, so the
                // group is never a pixel wider than the space it is centred in.
                if (spec.showsArt) {
                    val edge = (spec.art * density).toInt()
                    setViewVisibility(R.id.widget_art, View.VISIBLE)
                    setInt(R.id.widget_art, "setMaxWidth", edge)
                    setInt(R.id.widget_art, "setMaxHeight", edge)
                    if (art != null) {
                        setImageViewBitmap(R.id.widget_art, art)
                    } else {
                        setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
                    }
                } else {
                    setViewVisibility(R.id.widget_art, View.GONE)
                }
                val gap = px(spec.gap)
                val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                setViewPadding(R.id.widget_column, if (rtl) 0 else gap, 0, if (rtl) gap else 0, 0)

                val hasTrack = state.title != null
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_idle_title))
                setTextViewText(
                    R.id.widget_artist,
                    if (hasTrack) state.artist.orEmpty() else context.getString(R.string.widget_idle_subtitle),
                )
                // A fixed width, so the group does not shift from one title to the next.
                val textWidth = (spec.textWidth * density).toInt()
                for ((id, size) in listOf(R.id.widget_title to spec.titleSize, R.id.widget_artist to spec.artistSize)) {
                    setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, size)
                    setInt(id, "setWidth", textWidth)
                }

                setViewPadding(R.id.widget_controls, 0, px(spec.controlsGap), 0, 0)
                sizeButton(R.id.widget_previous, spec.skipIcon, spec, density)
                sizeButton(R.id.widget_play, spec.playIcon, spec, density)
                sizeButton(R.id.widget_next, spec.skipIcon, spec, density)
                setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                setContentDescription(
                    R.id.widget_play,
                    context.getString(if (state.playing) R.string.widget_pause else R.string.widget_play),
                )

                setOnClickPendingIntent(R.id.widget_play, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_previous, WidgetActionReceiver.intent(context, WidgetActionReceiver.ACTION_PREVIOUS))
                val open = openApp(context)
                setOnClickPendingIntent(R.id.widget_background, open)
                setOnClickPendingIntent(R.id.widget_art, open)
                setOnClickPendingIntent(R.id.widget_info, open)
            }
        }

        /**
         * The buttons are wrap_content with adjusted view bounds, and their icons
         * are drawn larger than any widget needs, so the max size sets the size:
         * the icon at [icon] dp, plus padding that spaces the row out.
         */
        private fun RemoteViews.sizeButton(id: Int, icon: Float, spec: WidgetSpec, density: Float) {
            val padH = (spec.buttonPadH * density).toInt()
            val padV = (spec.buttonPadV * density).toInt()
            val edge = (icon * density).toInt()
            setViewPadding(id, padH, padV, padH, padV)
            setInt(id, "setMaxWidth", edge + 2 * padH)
            setInt(id, "setMaxHeight", edge + 2 * padV)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Artwork for [url], [pixels] square with rounded corners, from Coil's caches when it can. */
        private suspend fun artwork(context: Context, url: String, pixels: Int): Bitmap? {
            artCache?.let { if (it.url == url && it.pixels >= pixels) return it.bitmap }
            val bitmap = withTimeoutOrNull(10_000) {
                val request = ImageRequest.Builder(context).data(url).size(pixels).allowHardware(false).build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            } ?: return null
            val rounded = withContext(Dispatchers.Default) { rounded(bitmap, pixels) }
            artCache = CachedArt(url, pixels, rounded)
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
            // A low density makes the bitmap's natural size large, so the artwork view,
            // which is sized by its max width and height, never stops short of them.
            output.density = DisplayMetrics.DENSITY_DEFAULT
            return output
        }
    }
}
