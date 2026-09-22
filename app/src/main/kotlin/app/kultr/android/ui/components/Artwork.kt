package app.kultr.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.KultrApp
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.util.Format
import coil3.compose.AsyncImage

/** Artwork URL for a cover id at a size bucket, or null when signed out. */
@Composable
fun rememberArtworkUrl(coverId: String?, size: Int): String? {
    val client by KultrApp.graph.auth.client.collectAsStateWithLifecycle()
    // Snap to a few sizes so the same cover is not fetched at every pixel size.
    val bucket = when {
        size <= 96 -> 96
        size <= 200 -> 200
        size <= 400 -> 400
        else -> 800
    }
    return remember(client, coverId, bucket) { client?.coverArtUrl(coverId, bucket) }
}

/**
 * Cover art with a graceful placeholder: a gradient in the accent with the
 * initials of whatever it is, so an album without artwork still looks placed.
 */
@Composable
fun Artwork(
    coverId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(Kultr.radii.sm),
    label: String? = null,
    icon: ImageVector = Icons.Rounded.MusicNote,
    imageUrl: String? = null,
) {
    val pixels = (size.value * 2.5f).toInt()
    val url = imageUrl ?: rememberArtworkUrl(coverId, pixels)
    val colors = Kultr.colors
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.accent.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.15f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                Format.initials(label),
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.3f).coerceIn(10f, 48f).sp,
            )
        } else {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(size * 0.4f))
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Artwork that fills its parent's width (square), for cards and headers. */
@Composable
fun ArtworkFill(
    coverId: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Kultr.radii.md),
    label: String? = null,
    pixels: Int = 400,
    imageUrl: String? = null,
) {
    val url = imageUrl ?: rememberArtworkUrl(coverId, pixels)
    val colors = Kultr.colors
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.12f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(Format.initials(label), color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 28.sp)
        }
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
