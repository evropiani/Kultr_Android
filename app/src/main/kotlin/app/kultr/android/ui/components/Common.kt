package app.kultr.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.android.ui.theme.EyebrowStyle
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.LocalReduceMotion

/** A translucent panel with an accent-tinted edge — Kultr's "glass". */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Kultr.radii.lg),
    strong: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val colors = Kultr.colors
    Box(
        modifier
            .clip(shape)
            .background(if (strong) colors.glassStrong else colors.glass)
            .border(BorderStroke(1.dp, colors.edge), shape)
            .padding(padding),
    ) { content() }
}

/** A capsule button: the accent for the one action that leads, sand with an accent label otherwise. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val colors = Kultr.colors
    val shape = RoundedCornerShape(50)
    val background = if (accent) colors.accent else colors.sand
    val content = if (accent) colors.onAccent else colors.accent
    Row(
        modifier
            .clip(shape)
            .background(background.copy(alpha = if (enabled) background.alpha else background.alpha * 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, for a pill stretched wider than its label.
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(18.dp))
        Text(text, color = content.copy(alpha = if (enabled) 1f else 0.5f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (badge != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (accent) colors.onAccent.copy(alpha = 0.18f) else colors.accentSoft)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) { Text(badge, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A wide button: sand with an accent label, like Apple Music's Play and Shuffle; filled with the accent when it leads. */
@Composable
fun WideButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true) {
    val colors = Kultr.colors
    val content = if (accent) colors.onAccent else colors.accent
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(Kultr.radii.md))
            .background(if (accent) colors.accent else colors.sand)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = content.copy(alpha = if (enabled) 1f else 0.5f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * Year, genre, format and the like as one quiet line ("2019 · Electronic ·
 * FLAC"), not a row of chips. Parts with a click (a genre, a rating) are
 * a shade brighter and can be tapped.
 */
@Composable
fun InfoLine(parts: List<Pair<String, (() -> Unit)?>>, modifier: Modifier = Modifier, center: Boolean = false) {
    if (parts.isEmpty()) return
    val colors = Kultr.colors
    val style = MaterialTheme.typography.labelLarge
    Row(
        modifier,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { index, (text, onClick) ->
            if (index > 0) Text("  ·  ", color = colors.ink4, style = style)
            Text(
                text,
                color = if (onClick != null) colors.ink2 else colors.ink3,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).then(
                    if (onClick != null) Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick) else Modifier,
                ),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Kultr.colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Kultr.colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        action?.invoke(this)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = EyebrowStyle, color = Kultr.colors.ink3, modifier = modifier)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Kultr.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Kultr.colors.accent, modifier = Modifier.size(28.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Kultr.colors.ink, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Kultr.colors.ink2, textAlign = TextAlign.Center)
        }
        action?.invoke()
    }
}

/** Placeholders breathe gently while their page loads; still with Reduce motion. */
@Composable
private fun Modifier.placeholder(): Modifier {
    if (LocalReduceMotion.current) return this
    val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
        1f,
        0.55f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    return this.graphicsLayer { alpha = pulse }
}

private val SKELETON_WIDTHS = listOf(0.62f to 0.38f, 0.48f to 0.3f, 0.7f to 0.44f, 0.55f to 0.26f, 0.66f to 0.4f)

/** A list still loading: the shape of its rows, not a spinner. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 8, artwork: Boolean = true) {
    val fill = Kultr.colors.sand
    Column(modifier.fillMaxWidth().placeholder()) {
        repeat(count) { i ->
            val (title, subtitle) = SKELETON_WIDTHS[i % SKELETON_WIDTHS.size]
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (artwork) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(title).height(12.dp).clip(RoundedCornerShape(50)).background(fill))
                    Box(Modifier.fillMaxWidth(subtitle).height(10.dp).clip(RoundedCornerShape(50)).background(fill))
                }
            }
        }
    }
}

/** A grid of covers still loading. */
@Composable
fun SkeletonGrid(minCell: Dp, modifier: Modifier = Modifier, count: Int = 12, round: Boolean = false) {
    val fill = Kultr.colors.sand
    val shape = if (round) CircleShape else RoundedCornerShape(Kultr.radii.md)
    BoxWithConstraints(modifier.fillMaxWidth().padding(10.dp).placeholder()) {
        val columns = (maxWidth / minCell).toInt().coerceAtLeast(1)
        Column {
            (0 until count).chunked(columns).forEach { row ->
                Row {
                    row.forEach { _ ->
                        Column(Modifier.weight(1f).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(fill))
                            Box(Modifier.fillMaxWidth(0.75f).height(12.dp).clip(RoundedCornerShape(50)).background(fill))
                            Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(50)).background(fill))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** An album, artist or playlist page still loading: the cover, the title, the buttons, then tracks. */
@Composable
fun SkeletonDetail(modifier: Modifier = Modifier) {
    val fill = Kultr.colors.sand
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).placeholder(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp).clip(RoundedCornerShape(Kultr.radii.lg)).background(fill))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(22.dp).clip(RoundedCornerShape(50)).background(fill))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(50)).background(fill))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(Kultr.radii.md)).background(fill)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        SkeletonRows(count = 6, artwork = false)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Kultr.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

/** A settings-style row: label and hint on the left, a control on the right. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
        }
        if (control != null) {
            Spacer(Modifier.width(12.dp))
            control()
        }
    }
}

/** The page colour, behind screens that have no artwork of their own. */
@Composable
fun ArtworkBackdropPlain(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Kultr.colors.background))
}
