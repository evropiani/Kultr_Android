package app.kultr.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kultr.android.AppGraph
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Playlist
import app.kultr.core.api.Song
import app.kultr.core.settings.GridSize
import app.kultr.core.util.Format
import kotlinx.coroutines.flow.first

fun GridSize.minCell(): Dp = when (this) {
    GridSize.SMALL -> 104.dp
    GridSize.MEDIUM -> 144.dp
    GridSize.LARGE -> 190.dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val graph = LocalActions.current.graph
    Column(
        modifier
            .combinedClickable(onClick = onClick)
            .dragSource(
                { DragPayload(album.name, album.coverArt ?: album.id) { graph.library.songsOfAlbumNow(album.id) } },
                onLongPress = onLongClick,
            )
            .padding(6.dp),
    ) {
        ArtworkFill(album.coverArt ?: album.id, Modifier.fillMaxWidth().aspectRatio(1f), label = album.name)
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Kultr.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · ")
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalActions.current.graph
    Column(
        modifier
            .combinedClickableCompat(onClick)
            .dragSource({ DragPayload(artist.name, artist.coverArt) { artistSongs(graph, artist) } })
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkFill(
            artist.coverArt,
            Modifier.fillMaxWidth().aspectRatio(1f),
            shape = CircleShape,
            label = artist.name,
            imageUrl = artist.artistImageUrl?.takeIf { artist.coverArt == null && it.startsWith("http") },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Kultr.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        artist.albumCount?.let {
            Text(Format.count(it, "album"), style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalActions.current.graph
    Column(
        modifier
            .combinedClickableCompat(onClick)
            .dragSource({ DragPayload(playlist.name, playlist.coverArt) { playlistSongs(graph, playlist) } })
            .padding(6.dp),
    ) {
        ArtworkFill(playlist.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), label = playlist.name)
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Kultr.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(playlist.songCount?.let { Format.count(it, "track") }, playlist.duration?.let { Format.duration(it.toLong()) })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Kultr.colors.ink3,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

/** An artist's tracks from the mirror, or album by album from the server when not synced. */
private suspend fun artistSongs(graph: AppGraph, artist: Artist): List<Song> {
    val local = graph.library.songsOfArtistNow(artist.id)
    if (local.isNotEmpty()) return local
    val albums = graph.auth.client.value?.getArtist(artist.id)?.album.orEmpty()
    return albums.flatMap { graph.library.songsOfAlbumNow(it.id) }
}

/** A playlist's tracks from the mirror, or from the server when its contents are not synced. */
private suspend fun playlistSongs(graph: AppGraph, playlist: Playlist): List<Song> {
    val local = graph.library.playlist(playlist.id).first()?.songs.orEmpty()
    if (local.isNotEmpty()) return local
    return graph.auth.client.value?.getPlaylist(playlist.id)?.entry.orEmpty()
}

/** A horizontally scrolling row of cards. */
@Composable
fun <T> Shelf(
    items: List<T>,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    card: @Composable (T, Modifier) -> Unit,
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = key) { item -> card(item, Modifier.width(cardWidth)) }
    }
}
