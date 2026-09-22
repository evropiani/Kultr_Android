package app.kultr.android.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.android.KultrApp
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.settings.PlayheadStyle
import app.kultr.core.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sin

/** The playback position, polled while on screen. */
@Composable
fun rememberPosition(intervalMs: Long = 200): State<Long> {
    val connection = KultrApp.graph.player
    return produceState(connection.positionMs()) {
        while (isActive) {
            value = connection.positionMs()
            delay(intervalMs)
        }
    }
}

/**
 * The progress bar. Drag or tap to seek. When a transition has been planned,
 * the stretch where the next track will come in is hatched, so you can see
 * where the blend starts.
 */
@Composable
fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: PlayheadStyle = PlayheadStyle.MINIMAL,
    plan: TransitionPlan? = null,
    showTimes: Boolean = true,
    countDown: Boolean = false,
    onToggleCountDown: () -> Unit = {},
    reduceMotion: Boolean = false,
) {
    val colors = Kultr.colors
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val transition = rememberInfiniteTransition(label = "playhead")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reduceMotion) 1_000_000 else 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) onSeek((offset.x / size.width * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val h = 4.dp.toPx()
                val y = size.height / 2
                val w = size.width
                val radius = CornerRadius(h / 2, h / 2)
                drawRoundRect(colors.ink4, Offset(0f, y - h / 2), Size(w, h), radius)

                // The planned hand-over, hatched.
                if (plan != null && durationMs > 0 && plan.duration > 0.3) {
                    val start = (plan.startAt * 1000 / durationMs).toFloat().coerceIn(0f, 1f)
                    val end = ((plan.startAt + plan.duration * plan.outgoingRate) * 1000 / durationMs).toFloat().coerceIn(start, 1f)
                    var x = start * w
                    val step = 5.dp.toPx()
                    while (x < end * w) {
                        drawLine(colors.accent.copy(alpha = 0.55f), Offset(x, y - h / 2), Offset(minOf(x + step / 2, end * w), y + h / 2), 1.5.dp.toPx())
                        x += step
                    }
                }

                val played = fraction * w
                when (style) {
                    PlayheadStyle.WAVE -> {
                        drawRoundRect(
                            Brush.linearGradient(
                                listOf(colors.accent.copy(alpha = 0.5f), colors.accent, colors.accent.copy(alpha = 0.5f)),
                                start = Offset(phase * 120f - 120f, 0f),
                                end = Offset(phase * 120f, 0f),
                                tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                            ),
                            Offset(0f, y - h / 2),
                            Size(played, h),
                            radius,
                        )
                    }
                    PlayheadStyle.COMET -> {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, colors.accent), startX = max(0f, played - w * 0.35f), endX = played),
                            Offset(0f, y - h / 2),
                            Size(played, h),
                            radius,
                        )
                    }
                    PlayheadStyle.EQUALIZER -> {
                        val bar = 3.dp.toPx()
                        var x = 0f
                        var i = 0
                        while (x < played) {
                            val amp = 0.5f + 0.5f * sin((i * 0.9f + phase * 6.283f).toDouble()).toFloat()
                            val bh = h + amp * 6.dp.toPx()
                            drawRoundRect(colors.accent, Offset(x, y - bh / 2), Size(minOf(bar, played - x), bh), CornerRadius(bar / 2, bar / 2))
                            x += bar * 1.8f
                            i++
                        }
                    }
                    else -> drawRoundRect(colors.accent, Offset(0f, y - h / 2), Size(played, h), radius)
                }

                val thumb = if (dragFraction != null) 9.dp.toPx() else 6.dp.toPx()
                when (style) {
                    PlayheadStyle.GLOW -> {
                        val glow = 10.dp.toPx() + 4.dp.toPx() * sin(phase * 6.283f)
                        drawCircle(colors.accent.copy(alpha = 0.25f), glow, Offset(played, y))
                    }
                    PlayheadStyle.PULSE -> {
                        drawCircle(colors.accent.copy(alpha = (1f - phase) * 0.5f), thumb + phase * 14.dp.toPx(), Offset(played, y))
                    }
                    PlayheadStyle.COMET -> drawCircle(colors.accent.copy(alpha = 0.35f), thumb * 2f, Offset(played, y))
                    else -> Unit
                }
                drawCircle(colors.accent, thumb, Offset(played, y))
                drawCircle(Color.White.copy(alpha = 0.9f), thumb * 0.35f, Offset(played, y))
                if (style == PlayheadStyle.MINIMAL) drawLine(colors.accent, Offset(played, y), Offset(played, y), h, StrokeCap.Round)
            }
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                val shownMs = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
                val left = if (countDown && durationMs > 0) "-" + Format.timeMs(durationMs - shownMs) else Format.timeMs(shownMs)
                Text(
                    left,
                    color = colors.ink3,
                    fontSize = 12.sp,
                    style = Tabular,
                    modifier = Modifier.clickable(onClick = onToggleCountDown),
                )
                Spacer(Modifier.weight(1f))
                Text(if (durationMs > 0) Format.timeMs(durationMs) else "--:--", color = colors.ink3, fontSize = 12.sp, style = Tabular)
            }
        }
    }
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")
