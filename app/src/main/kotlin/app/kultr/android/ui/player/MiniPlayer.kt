package app.kultr.android.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.playback.PlayerUiState
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.theme.Kultr

/** The strip above the navigation bar: what is playing, play/pause, next. */
@Composable
fun MiniPlayer(state: PlayerUiState, modifier: Modifier = Modifier) {
    val song = state.current ?: return
    val actions = LocalActions.current
    val colors = Kultr.colors
    // Favourite state comes from the library, so it updates when toggled anywhere.
    val live by remember(song.id) { actions.graph.library.song(song.id) }.collectAsStateWithLifecycle(null)
    val starred = live?.isStarred ?: song.isStarred
    val position by rememberPosition(500)
    val shape = RoundedCornerShape(Kultr.radii.lg)

    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(colors.elevated.copy(alpha = 0.94f))
            .background(colors.accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, colors.edge), shape)
            .clickable { actions.openPlayer() },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.artworkId, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!song.isRadio) {
                IconButton(onClick = { actions.setFavourite(live ?: song, !starred) }) {
                    Icon(
                        if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (starred) "Remove from favourites" else "Add to favourites",
                        tint = if (starred) colors.accent else colors.ink2,
                    )
                }
            }
            IconButton(onClick = { actions.graph.player.toggle() }) {
                Icon(
                    if (state.playWhenReady && !state.ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.playWhenReady) "Pause" else "Play",
                    tint = colors.ink,
                )
            }
            IconButton(onClick = { actions.graph.player.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = colors.ink)
            }
        }
        val fraction = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.ink4)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(colors.accent))
        }
    }
}
