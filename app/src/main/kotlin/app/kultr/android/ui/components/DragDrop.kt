package app.kultr.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import kotlin.math.roundToInt

/**
 * Something being dragged: what to call it, a cover to show under the
 * finger, and how to get its tracks. [resolve] is only called on drop, so
 * dragging an album does not load its track list until it is needed.
 */
class DragPayload(
    val label: String,
    val coverId: String?,
    val resolve: suspend () -> List<Song>,
)

/**
 * Where a drag can end, ordered the way the web client orders them:
 * queueing is the common case, deleting is furthest from the finger's path.
 */
enum class DropAction(val label: String, val hint: String, val icon: ImageVector) {
    PLAY_NEXT("Play next", "Jump the queue", Icons.Rounded.PlayArrow),
    QUEUE("Add to queue", "Play after everything else", Icons.AutoMirrored.Rounded.QueueMusic),
    FAVOURITE("Favourite", "Star on your server", Icons.Rounded.Favorite),
    DOWNLOAD("Sync offline", "Download for offline play", Icons.Rounded.Download),
    REMOVE("Delete downloads", "Remove the local copies", Icons.Rounded.Delete),
}

/** The drag in progress, shared by every drag source and the drop zone. */
@Stable
class DragDropState(private val onDrop: (DragPayload, DropAction) -> Unit) {
    var payload by mutableStateOf<DragPayload?>(null)
        private set

    /** Finger position in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    var hovered by mutableStateOf<DropAction?>(null)
        private set

    private val targets = HashMap<DropAction, Rect>()

    internal fun start(payload: DragPayload, at: Offset) {
        this.payload = payload
        pointer = at
        hovered = null
    }

    internal fun move(delta: Offset) {
        if (payload == null) return
        pointer += delta
        hovered = targets.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    /** Finish the drag; true when it was dropped on a target. */
    internal fun finish(): Boolean {
        val dragged = payload
        val target = hovered
        cancel()
        if (dragged != null && target != null) {
            onDrop(dragged, target)
            return true
        }
        return false
    }

    internal fun cancel() {
        payload = null
        hovered = null
    }

    internal fun register(action: DropAction, bounds: Rect) {
        targets[action] = bounds
    }
}

val LocalDragDrop = staticCompositionLocalOf<DragDropState?> { null }

/**
 * Long-press and drag this element onto the drop zone. A long press that is
 * released without moving calls [onLongPress] instead, so long-press to
 * select keeps working. Put this *after* any clickable in the chain so a
 * release after the long press does not also count as a tap.
 */
fun Modifier.dragSource(
    payload: () -> DragPayload?,
    onLongPress: (() -> Unit)? = null,
): Modifier = composed {
    val state = LocalDragDrop.current ?: return@composed this
    val haptics = LocalHapticFeedback.current
    val latestPayload by rememberUpdatedState(payload)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(state) {
            var travelled = 0f
            var started = false
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    travelled = 0f
                    val dragged = latestPayload()
                    started = dragged != null
                    if (dragged != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val root = coordinates[0]?.takeIf { it.isAttached }?.localToRoot(offset) ?: offset
                        state.start(dragged, root)
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    travelled += amount.getDistance()
                    state.move(amount)
                },
                onDragEnd = {
                    val dropped = started && state.finish()
                    if (!dropped && travelled < viewConfiguration.touchSlop) latestLongPress?.invoke()
                    started = false
                },
                onDragCancel = {
                    state.cancel()
                    started = false
                },
            )
        }
}

/**
 * The drop targets along the bottom of the screen and the card that follows
 * the finger. Only composed while something is being dragged.
 */
@Composable
fun DropZoneOverlay(state: DragDropState, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val payload = state.payload
    Box(modifier.fillMaxSize()) {
        // Dim everything behind, so the targets and their labels stand out.
        AnimatedVisibility(visible = payload != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (colors.dark) 0.6f else 0.4f)))
        }
        AnimatedVisibility(
            visible = payload != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.2f to colors.background.copy(alpha = 0.96f),
                            1f to colors.background,
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 40.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Drop “${state.payload?.label.orEmpty()}” on…",
                    color = colors.ink,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Three on top, two below: big enough to hit with a thumb.
                val rows = listOf(DropAction.entries.take(3), DropAction.entries.drop(3))
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        row.forEach { action ->
                            DropTarget(action, state, Modifier.weight(1f).widthIn(max = 160.dp))
                        }
                    }
                }
            }
        }

        if (payload != null) {
            val position = state.pointer
            Row(
                Modifier
                    .offset { IntOffset((position.x - 24.dp.toPx()).roundToInt(), (position.y - 72.dp.toPx()).roundToInt()) }
                    .widthIn(max = 240.dp)
                    .shadow(12.dp, RoundedCornerShape(Kultr.radii.md))
                    .clip(RoundedCornerShape(Kultr.radii.md))
                    .background(colors.elevated)
                    .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)), RoundedCornerShape(Kultr.radii.md))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(payload.coverId, size = 36.dp, label = payload.label)
                Spacer(Modifier.width(8.dp))
                Text(
                    payload.label,
                    color = colors.ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DropTarget(action: DropAction, state: DragDropState, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val over = state.hovered == action
    val danger = action == DropAction.REMOVE
    val scale by animateFloatAsState(if (over) 1.06f else 1f, label = "drop-target")
    val tint = when {
        over && danger -> colors.danger
        over -> colors.accent
        else -> colors.ink
    }
    val shape = RoundedCornerShape(Kultr.radii.lg)
    Column(
        modifier
            .onGloballyPositioned { state.register(action, it.boundsInRoot()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (over) -6.dp.toPx() else 0f
            }
            .clip(shape)
            .background(colors.elevated)
            .background(
                when {
                    over && danger -> colors.danger.copy(alpha = 0.16f)
                    over -> colors.accentSoft
                    else -> Color.Transparent
                },
            )
            .border(BorderStroke(1.dp, if (over) tint.copy(alpha = 0.8f) else colors.edge), shape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(action.label, color = colors.ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        Text(action.hint, color = colors.ink2, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}
