package app.kultr.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import app.kultr.core.util.Format

/** An extra entry for a song's menu, for screen-specific actions. */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Multi-select across a list of tracks. Long-press starts it. */
@Stable
class SongSelection {
    val ids: SnapshotStateList<String> = emptyList<String>().toMutableStateList()
    val active: Boolean get() = ids.isNotEmpty()

    fun toggle(id: String) {
        if (!ids.remove(id)) ids.add(id)
    }

    fun clear() = ids.clear()

    fun selectAll(songs: List<Song>) {
        ids.clear()
        ids.addAll(songs.map { it.id }.distinct())
    }

    fun picked(songs: List<Song>): List<Song> {
        val set = ids.toHashSet()
        return songs.filter { it.id in set }.distinctBy { it.id }
    }
}

@Composable
fun rememberSelection(): SongSelection = remember { SongSelection() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    showArtwork: Boolean = true,
    isCurrent: Boolean = false,
    downloaded: Boolean = false,
    selected: Boolean = false,
    selecting: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
    extraActions: List<MenuAction> = emptyList(),
) {
    val colors = Kultr.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) colors.accent else colors.ink3,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        when {
            showArtwork -> Box {
                Artwork(song.artworkId, size = if (compact) 38.dp else 46.dp)
                if (isCurrent) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = colors.accent) }
                }
            }
            number != null -> Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = colors.accent, modifier = Modifier.size(18.dp))
                } else {
                    Text("$number", color = colors.ink3, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) colors.accent else colors.ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(song.artist, if (showArtwork) song.album else null).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = colors.ink3, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (downloaded) {
            Icon(Icons.Rounded.DownloadDone, contentDescription = "Downloaded", tint = colors.ink3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (song.isStarred) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Favourite", tint = colors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        song.duration?.let {
            Text(Format.time(it.toDouble()), color = colors.ink3, fontSize = 12.sp)
        }
        if (!selecting) SongMenuButton(song, downloaded, extraActions) else Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun SongMenuButton(song: Song, downloaded: Boolean, extraActions: List<MenuAction> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${song.title}", tint = Kultr.colors.ink3)
        }
        SongMenu(song, downloaded, open, onDismiss = { open = false }, extraActions = extraActions)
    }
}

@Composable
fun SongMenu(
    song: Song,
    downloaded: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extraActions: List<MenuAction> = emptyList(),
) {
    val actions = LocalActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Play", Icons.Rounded.PlayArrow, onDismiss) { actions.play(listOf(song)) }
        MenuItem("Play next", Icons.Rounded.SkipNext, onDismiss) { actions.playNext(listOf(song)) }
        MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic, onDismiss) { actions.enqueue(listOf(song)) }
        if (!song.isRadio) {
            MenuItem("Add to playlist…", Icons.AutoMirrored.Rounded.PlaylistAdd, onDismiss) { actions.addToPlaylist(listOf(song)) }
            if (song.isStarred) {
                MenuItem("Remove from favourites", Icons.Rounded.FavoriteBorder, onDismiss) { actions.setFavourite(song, false) }
            } else {
                MenuItem("Add to favourites", Icons.Rounded.Favorite, onDismiss) { actions.setFavourite(song, true) }
            }
            MenuItem(if ((song.userRating ?: 0) > 0) "Rating: ${song.userRating} ★" else "Rate…", Icons.Rounded.Star, onDismiss) { actions.rate(song) }
            MenuItem("Start an InjeKt set from here", Icons.Rounded.AutoAwesome, onDismiss) { actions.startInjektSet(song) }
            HorizontalDivider()
            if (song.albumId != null) MenuItem("Go to album", Icons.Rounded.Album, onDismiss) { actions.openAlbum(song.albumId) }
            if (song.artistId != null) MenuItem("Go to artist", Icons.Rounded.Person, onDismiss) { actions.openArtist(song.artistId) }
            if (downloaded) {
                MenuItem("Remove download", Icons.Rounded.Delete, onDismiss) { actions.removeDownloads(listOf(song)) }
            } else {
                MenuItem("Download", Icons.Rounded.Download, onDismiss) { actions.download(listOf(song), song.title) }
            }
        }
        if (extraActions.isNotEmpty()) {
            HorizontalDivider()
            extraActions.forEach { extra -> MenuItem(extra.label, extra.icon, onDismiss, extra.onClick) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onDismiss: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            onDismiss()
            action()
        },
    )
}

/**
 * Track rows for a LazyColumn. Tapping plays the list from that track (or
 * toggles selection while selecting); long-press starts selecting.
 */
fun LazyListScope.songItems(
    songs: List<Song>,
    currentId: String?,
    downloaded: Set<String>,
    selection: SongSelection?,
    onPlay: (Int) -> Unit,
    numbered: Boolean = false,
    showArtwork: Boolean = true,
    compact: Boolean = false,
    keyPrefix: String = "song",
    extraActions: (Int, Song) -> List<MenuAction> = { _, _ -> emptyList() },
) {
    itemsIndexed(songs, key = { index, song -> "$keyPrefix:$index:${song.id}" }) { index, song ->
        val selecting = selection?.active == true
        SongRow(
            song = song,
            onClick = { if (selecting) selection?.toggle(song.id) else onPlay(index) },
            onLongClick = selection?.let { { it.toggle(song.id) } },
            number = if (numbered) song.track ?: (index + 1) else null,
            showArtwork = showArtwork,
            isCurrent = song.id == currentId,
            downloaded = song.id in downloaded,
            selected = selecting && selection?.ids?.contains(song.id) == true,
            selecting = selecting,
            compact = compact,
            extraActions = extraActions(index, song),
        )
    }
}

/** The bar shown while tracks are selected. */
@Composable
fun SelectionBar(selection: SongSelection, songs: List<Song>, modifier: Modifier = Modifier) {
    val actions = LocalActions.current
    val picked = selection.picked(songs)
    var more by remember { mutableStateOf(false) }
    GlassPanel(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), strong = true, padding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { selection.clear() }) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
            Text("${picked.size} selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.play(picked); selection.clear() }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play") }
            IconButton(onClick = { actions.playNext(picked); selection.clear() }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Play next") }
            IconButton(onClick = { actions.enqueue(picked); selection.clear() }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to queue") }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = { more = false; selection.selectAll(songs) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { more = false; actions.addToPlaylist(picked); selection.clear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to favourites") },
                        leadingIcon = { Icon(Icons.Rounded.Favorite, null) },
                        onClick = { more = false; actions.setFavourite(picked, true); selection.clear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from favourites") },
                        leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) },
                        onClick = { more = false; actions.setFavourite(picked, false); selection.clear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { more = false; actions.download(picked); selection.clear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove downloads") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { more = false; actions.removeDownloads(picked); selection.clear() },
                    )
                }
            }
        }
    }
}
