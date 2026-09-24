package app.kultr.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kultr.android.KultrApp
import app.kultr.android.R
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.theme.DEFAULT_ACCENT
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.KultrTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Opens when the widget is placed (and when it is reconfigured): whether it
 * shows the artwork, and how solid its background is, from 100% (solid) to
 * 0% (invisible).
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without saving must not leave a half-set-up widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val initialOpacity = NowPlayingWidget.opacity(this, widgetId)
        val initialShowArt = NowPlayingWidget.showsArt(this, widgetId)
        val state = NowPlayingWidget.loadState(this)
        val size = NowPlayingWidget.sizes(this, widgetId).first()

        setContent {
            KultrTheme(KultrApp.graph.settings.current, DEFAULT_ACCENT) {
                ConfigScreen(
                    initialOpacity = initialOpacity,
                    initialShowArt = initialShowArt,
                    state = state,
                    size = size,
                    onCancel = { finish() },
                    onSave = { opacity, showArt ->
                        NowPlayingWidget.setOpacity(this, widgetId, opacity)
                        NowPlayingWidget.setShowsArt(this, widgetId, showArt)
                        NowPlayingWidget.refresh(this, intArrayOf(widgetId))
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    initialOpacity: Int,
    initialShowArt: Boolean,
    state: WidgetState,
    size: SizeF,
    onCancel: () -> Unit,
    onSave: (opacity: Int, showArt: Boolean) -> Unit,
) {
    val colors = Kultr.colors
    var opacity by rememberSaveable { mutableFloatStateOf(initialOpacity.toFloat()) }
    var showArt by rememberSaveable { mutableStateOf(initialShowArt) }
    val percent = opacity.roundToInt()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Now playing widget", style = MaterialTheme.typography.headlineSmall, color = colors.ink)
            Text(
                "Everything stays centred, and grows with the widget when you resize it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )

            // A stand-in wallpaper, so the transparency is visible in the preview.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Kultr.radii.lg))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF16222A), Color(0xFF7C8CFF))))
                    .padding(horizontal = 12.dp, vertical = 28.dp),
            ) {
                WidgetPreview(state, size, showArt, percent)
            }

            Row(
                Modifier.fillMaxWidth().clickable { showArt = !showArt },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show artwork", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Text("To the left of the controls", style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                }
                Switch(checked = showArt, onCheckedChange = { showArt = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background", style = MaterialTheme.typography.titleMedium, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(
                        when (percent) {
                            100 -> "100% · solid"
                            0 -> "0% · invisible"
                            else -> "$percent%"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.accent,
                    )
                }
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..100f,
                    steps = 19,
                )
                Row {
                    Text("Invisible", style = MaterialTheme.typography.labelSmall, color = colors.ink3, modifier = Modifier.weight(1f))
                    Text("Solid", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel", color = colors.ink2) }
            Pill("Save", accent = true, onClick = { onSave(percent, showArt) })
        }
    }
}

/**
 * The widget at its size on the home screen (narrowed if it does not fit
 * here), laid out by the same [WidgetLayout] as the real one.
 */
@Composable
private fun WidgetPreview(state: WidgetState, size: SizeF, showArt: Boolean, percent: Int) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = min(size.width, maxWidth.value)
        val height = min(size.height, 280f)
        val spec = remember(width, height, showArt, fontScale) { WidgetLayout.compute(width, height, showArt, fontScale) }
        Box(
            Modifier
                .size(width.dp, height.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101018).copy(alpha = percent / 100f))
                .padding(horizontal = spec.paddingH.dp, vertical = spec.paddingV.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spec.showsArt) {
                    Artwork(
                        coverId = null,
                        imageUrl = state.artUrl,
                        size = spec.art.dp,
                        shape = RoundedCornerShape((spec.art * 0.12f).dp),
                    )
                    Spacer(Modifier.width(spec.gap.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val hasTrack = state.title != null
                    PreviewLine(state.title ?: stringResource(R.string.widget_idle_title), spec.titleSize, spec.textWidth, Color.White, FontWeight.Medium)
                    PreviewLine(
                        if (hasTrack) state.artist.orEmpty() else stringResource(R.string.widget_idle_subtitle),
                        spec.artistSize,
                        spec.textWidth,
                        Color.White.copy(alpha = 0.6f),
                        FontWeight.Normal,
                    )
                    Row(Modifier.padding(top = spec.controlsGap.dp), verticalAlignment = Alignment.CenterVertically) {
                        PreviewButton(R.drawable.ic_widget_previous, spec.skipIcon, spec)
                        PreviewButton(if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play, spec.playIcon, spec)
                        PreviewButton(R.drawable.ic_widget_next, spec.skipIcon, spec)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(text: String, size: Float, width: Float, color: Color, weight: FontWeight) {
    // The widget sizes its text in dp, with the font setting already applied.
    val fontSize = with(LocalDensity.current) { size.dp.toSp() }
    Text(
        text,
        Modifier.width(width.dp),
        color = color,
        fontSize = fontSize,
        fontWeight = weight,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewButton(@DrawableRes icon: Int, size: Float, spec: WidgetSpec) {
    Image(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier
            .padding(horizontal = spec.buttonPadH.dp, vertical = spec.buttonPadV.dp)
            .size(size.dp),
    )
}
