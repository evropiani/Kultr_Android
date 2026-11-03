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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.kultr.android.KultrApp
import app.kultr.android.ui.components.glass
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.injekt.TransitionPlan
import app.kultr.core.settings.PlayheadStyle
import app.kultr.core.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
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
 * where the blend starts. While dragging, a glass lens above the finger shows
 * the time large, with the beats around it once InjeKt knows them.
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
    /** The track's analysis, for the beats in the lens. */
    analysis: TrackAnalysis? = null,
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
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
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
            val dragging = dragFraction
            if (dragging != null && durationMs > 0) {
                val lensWidth = 148.dp
                val x = (maxWidth * dragging - lensWidth / 2).coerceIn(0.dp, (maxWidth - lensWidth).coerceAtLeast(0.dp))
                ScrubLens((dragging * durationMs).toLong(), analysis, plan, Modifier.offset(x = x, y = (-84).dp).width(lensWidth))
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

/**
 * The glass lens over the scrubber while it is dragged: the time, large, and
 * eight seconds of the track around it with InjeKt's beats marked, the first
 * beat of each bar taller and in the accent, and the planned mix shaded.
 */
@Composable
private fun ScrubLens(ms: Long, analysis: TrackAnalysis?, plan: TransitionPlan?, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    Column(
        modifier.glass(RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(Format.timeMs(ms), style = MaterialTheme.typography.titleLarge.merge(Tabular), fontWeight = FontWeight.Bold, color = colors.ink)
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val now = ms / 1000.0
            val start = now - LENS_SPAN / 2
            fun x(seconds: Double) = ((seconds - start) / LENS_SPAN * size.width).toFloat()
            val mid = size.height / 2
            if (plan != null && plan.duration > 0.3) {
                val from = x(plan.startAt).coerceAtLeast(0f)
                val to = x(plan.startAt + plan.duration * plan.outgoingRate).coerceAtMost(size.width)
                if (to > from) drawRect(colors.accent.copy(alpha = 0.18f), Offset(from, 0f), Size(to - from, size.height))
            }
            drawRect(colors.ink3.copy(alpha = 0.5f), Offset(0f, mid - 0.5f), Size(size.width, 1f))
            val bpm = analysis?.bpm?.takeIf { it > 0 }
            if (bpm != null) {
                val beat = 60.0 / bpm
                val offset = analysis.downbeatOffset.takeIf { it > 0 } ?: analysis.beatOffset
                var k = ceil((start - offset) / beat).toInt()
                while (true) {
                    val time = offset + k * beat
                    if (time > start + LENS_SPAN) break
                    if (time >= 0) {
                        val downbeat = Math.floorMod(k, 4) == 0
                        val h = if (downbeat) size.height else size.height * 0.45f
                        val w = if (downbeat) 2.dp.toPx() else 1.5.dp.toPx()
                        drawRoundRect(if (downbeat) colors.accent else colors.ink3, Offset(x(time) - w / 2, mid - h / 2), Size(w, h), CornerRadius(w / 2, w / 2))
                    }
                    k++
                }
            } else {
                // No beats known yet: plain second marks.
                var second = ceil(start)
                while (second <= start + LENS_SPAN) {
                    if (second >= 0) drawRect(colors.ink3, Offset(x(second) - 0.5f, mid - 4.dp.toPx()), Size(1f, 8.dp.toPx()))
                    second += 1
                }
            }
            // Where your finger is.
            val w = 3.dp.toPx()
            drawRoundRect(colors.ink, Offset(size.width / 2 - w / 2, 0f), Size(w, size.height), CornerRadius(w / 2, w / 2))
        }
    }
}

private const val LENS_SPAN = 8.0
