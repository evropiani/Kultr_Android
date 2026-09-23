package app.kultr.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kultr.android.KultrApp
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.theme.DEFAULT_ACCENT
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.KultrTheme
import kotlin.math.roundToInt

/**
 * Opens when the widget is placed (and when it is reconfigured): choose how
 * solid its background is, from 100% (solid) to 0% (invisible).
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
        val initial = NowPlayingWidget.opacity(this, widgetId)
        val state = NowPlayingWidget.loadState(this)

        setContent {
            KultrTheme(KultrApp.graph.settings.current, DEFAULT_ACCENT) {
                ConfigScreen(
                    initialOpacity = initial,
                    state = state,
                    onCancel = { finish() },
                    onSave = { opacity ->
                        NowPlayingWidget.setOpacity(this, widgetId, opacity)
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
private fun ConfigScreen(initialOpacity: Int, state: WidgetState, onCancel: () -> Unit, onSave: (Int) -> Unit) {
    val colors = Kultr.colors
    var opacity by rememberSaveable { mutableFloatStateOf(initialOpacity.toFloat()) }
    val percent = opacity.roundToInt()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Now playing widget", style = MaterialTheme.typography.headlineSmall, color = colors.ink)
        Text(
            "Choose how solid the widget's background is. Artwork, text and buttons always stay visible.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
        )

        // A stand-in wallpaper, so the transparency is visible in the preview.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Kultr.radii.lg))
                .background(Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF16222A), Color(0xFF7C8CFF))))
                .padding(horizontal = 16.dp, vertical = 28.dp),
        ) {
            WidgetPreview(state, percent)
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

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = colors.ink2) }
            Pill("Save", accent = true, onClick = { onSave(percent) })
        }
    }
}

/** Roughly what the home screen widget will look like at [percent] opacity. */
@Composable
private fun WidgetPreview(state: WidgetState, percent: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF101018).copy(alpha = percent / 100f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22222E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = DEFAULT_ACCENT, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.title ?: "Nothing playing",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.artist ?: "Tap to open Kultr",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White)
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White)
            }
        }
    }
}
