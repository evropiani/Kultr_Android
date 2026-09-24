package app.kultr.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AccentWash
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Eyebrow
import app.kultr.android.ui.components.Loading
import app.kultr.android.ui.components.MenuAction
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SongSelection
import app.kultr.android.ui.components.Tag
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.ArtistInfo
import app.kultr.core.api.Song
import app.kultr.core.util.Format

/** A back arrow that floats over the header. */
@Composable
fun BackBar(title: String? = null) {
    val actions = LocalActions.current
    Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Artwork, eyebrow, title, subtitle and actions — the top of every detail page. */
@Composable
fun DetailHeader(
    eyebrow: String,
    title: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    artworkShape: Shape = RoundedCornerShape(Kultr.radii.lg),
    imageUrl: String? = null,
    subtitle: @Composable () -> Unit = {},
    tags: List<Pair<String, (() -> Unit)?>> = emptyList(),
    actions: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ArtworkFill(
            coverId,
            Modifier.size(220.dp).align(Alignment.CenterHorizontally),
            shape = artworkShape,
            label = title,
            pixels = 600,
            imageUrl = imageUrl,
        )
        Spacer(Modifier.height(18.dp))
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        subtitle()
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { (text, click) -> Tag(text, onClick = click) } }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DownloadPill(songs: List<Song>, label: String) {
    val actions = LocalActions.current
    val downloaded by actions.graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val missing = songs.count { !it.isRadio && it.id !in downloaded }
    if (songs.isEmpty()) return
    if (missing == 0) {
        Pill("Remove download", icon = Icons.Rounded.DownloadDone, onClick = { actions.removeDownloads(songs) })
    } else {
        Pill("Download", icon = Icons.Rounded.Download, badge = "$missing", onClick = { actions.download(songs, label) })
    }
}

@Composable
private fun DetailScaffold(selection: SongSelection, songs: List<Song>, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AccentWash()
        Column(Modifier.fillMaxSize()) {
            BackBar()
            if (selection.active) SelectionBar(selection, songs)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = chromePadding()), content = content)
        }
    }
}

// ---------------------------------------------------------------- album --

@Composable
fun AlbumScreen(id: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val album by remember(id) { graph.library.album(id) }.collectAsStateWithLifecycle(null)
    val localSongs by remember(id) { graph.library.songsOfAlbum(id) }.collectAsStateWithLifecycle(null)
    // Albums the mirror does not have (not synced yet) come straight from the server.
    val remote by produceState<Album?>(null, id, localSongs?.isEmpty()) {
        if (localSongs?.isEmpty() == true) value = runCatching { graph.auth.client.value?.getAlbum(id) }.getOrNull()
    }
    val songs = localSongs?.takeIf { it.isNotEmpty() } ?: remote?.song.orEmpty()
    val shown = album ?: remote
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val notes by produceState<String?>(null, id) { value = graph.library.albumNotes(id) }
    val selection = rememberSelection()

    if (shown == null) {
        Column { BackBar(); Loading() }
        return
    }
    val discs = songs.groupBy { it.discNumber ?: 1 }
    DetailScaffold(selection, songs) {
        item(key = "header") {
            DetailHeader(
                eyebrow = if (shown.isCompilation == true) "Compilation" else "Album",
                title = shown.name,
                coverId = shown.coverArt ?: shown.id,
                subtitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shown.artist.orEmpty(),
                            color = Kultr.colors.ink,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable(enabled = shown.artistId != null) { actions.openArtist(shown.artistId) },
                        )
                        Text(
                            listOfNotNull(
                                shown.year?.toString(),
                                Format.count(songs.size, "track"),
                                Format.duration((shown.duration ?: songs.sumOf { it.duration ?: 0 }).toLong()),
                            ).joinToString(" · ", prefix = " · "),
                            color = Kultr.colors.ink2,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                tags = listOfNotNull(shown.genre?.let { g -> g to { actions.openGenre(g) } }),
                actions = {
                    Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, onClick = { actions.play(songs) })
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, onClick = { actions.shuffle(songs) })
                    Pill(
                        if (shown.isStarred) "Favourite" else "Favourite",
                        icon = if (shown.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = { actions.setAlbumFavourite(shown, !shown.isStarred) },
                    )
                    Pill("Queue", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = { actions.enqueue(songs) })
                    Pill("Add to playlist", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = { actions.addToPlaylist(songs) })
                    DownloadPill(songs, shown.name)
                },
            )
        }
        if (songs.isEmpty()) {
            item(key = "empty") { Loading() }
        }
        discs.forEach { (disc, discSongs) ->
            if (discs.size > 1) item(key = "disc$disc") { SectionHeader("Disc $disc") }
            songItems(
                discSongs,
                currentId = player.current?.id,
                downloaded = downloaded,
                selection = selection,
                onPlay = { index -> actions.play(songs, songs.indexOf(discSongs[index]).coerceAtLeast(0)) },
                numbered = true,
                showArtwork = false,
                compact = settings.compactRows,
                keyPrefix = "disc$disc",
            )
        }
        notes?.let { text ->
            item(key = "notes") {
                SectionHeader("About this album")
                Text(
                    cleanHtml(text),
                    color = Kultr.colors.ink2,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/** Last.fm biographies arrive with a trailing link; keep the words. */
fun cleanHtml(text: String): String =
    text.replace(Regex("<a [^>]*>.*?</a>\\.?"), "").replace(Regex("<[^>]+>"), "").trim()

// --------------------------------------------------------------- artist --

@Composable
fun ArtistScreen(id: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val artist by remember(id) { graph.library.artist(id) }.collectAsStateWithLifecycle(null)
    val albums by remember(id) { graph.library.albumsOfArtist(id) }.collectAsStateWithLifecycle(emptyList())
    val songs by remember(id) { graph.library.songsOfArtist(id) }.collectAsStateWithLifecycle(emptyList())
    val remote by produceState<Artist?>(null, id) { value = runCatching { graph.auth.client.value?.getArtist(id) }.getOrNull() }
    val info by produceState<ArtistInfo?>(null, id) { value = graph.library.artistInfo(id) }
    val shown = artist ?: remote
    val top by produceState(emptyList<Song>(), shown?.name) {
        shown?.name?.let { value = graph.library.topSongs(it) }
    }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    var bioOpen by remember { mutableStateOf(false) }

    if (shown == null) {
        Column { BackBar(); Loading() }
        return
    }
    val allAlbums = albums.ifEmpty { remote?.album.orEmpty() }
    val popular = top.ifEmpty { songs.sortedByDescending { it.playCount ?: 0 }.take(10) }.take(10)
    DetailScaffold(selection, popular) {
        item(key = "header") {
            DetailHeader(
                eyebrow = "Artist",
                title = shown.name,
                coverId = shown.coverArt,
                artworkShape = CircleShape,
                imageUrl = info?.largeImageUrl?.takeIf { shown.coverArt == null && it.startsWith("http") },
                subtitle = {
                    Text(
                        listOfNotNull(
                            Format.count(allAlbums.size, "album"),
                            if (songs.isNotEmpty()) Format.count(songs.size, "track") else null,
                        ).joinToString(" · "),
                        color = Kultr.colors.ink2,
                    )
                },
                actions = {
                    Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, onClick = {
                        actions.launch { actions.play(graph.library.songsOfArtistNow(id)) }
                    })
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, onClick = {
                        actions.launch { actions.shuffle(graph.library.songsOfArtistNow(id)) }
                    })
                    Pill(
                        "Favourite",
                        icon = if (shown.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = { actions.setArtistFavourite(shown, !shown.isStarred) },
                    )
                    Pill("InjeKt radio", icon = Icons.Rounded.AutoAwesome, onClick = {
                        actions.launch { songs.randomOrNull()?.let { actions.startInjektSet(it) } }
                    })
                    DownloadPill(songs, shown.name)
                },
            )
        }
        if (popular.isNotEmpty()) {
            item(key = "top-head") { SectionHeader("Popular") }
            songItems(
                popular,
                currentId = player.current?.id,
                downloaded = downloaded,
                selection = selection,
                onPlay = { index -> actions.play(popular, index) },
                keyPrefix = "top",
            )
        }
        if (allAlbums.isNotEmpty()) {
            item(key = "albums") {
                SectionHeader("Albums")
                Shelf(allAlbums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
            }
        }
        val similar = info?.similarArtist.orEmpty()
        if (similar.isNotEmpty()) {
            item(key = "similar") {
                SectionHeader("Similar artists")
                Shelf(similar, key = { it.id }, cardWidth = 130.dp) { other, modifier -> ArtistCard(other, { actions.openArtist(other.id) }, modifier) }
            }
        }
        info?.biography?.let { bio ->
            val text = cleanHtml(bio)
            if (text.isNotBlank()) {
                item(key = "bio") {
                    SectionHeader("About")
                    Text(
                        text,
                        color = Kultr.colors.ink2,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (bioOpen) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp).clickable { bioOpen = !bioOpen },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- playlist --

@Composable
fun PlaylistScreen(id: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val detail by remember(id) { graph.library.playlist(id) }.collectAsStateWithLifecycle(null)
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var refreshedOnce by remember(id) { mutableStateOf(false) }

    val d = detail
    // Contents may not have been synced; fetch them once if the list looks short.
    if (d != null && !refreshedOnce && d.songs.size < (d.playlist.songCount ?: 0)) {
        refreshedOnce = true
        actions.launch { graph.library.refreshPlaylist(id) }
    }
    if (d == null) {
        Column {
            BackBar()
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Playlist not found", body = "It may have been deleted on the server.")
        }
        return
    }
    val songs = d.songs
    val active by graph.auth.active.collectAsStateWithLifecycle()
    val username = active?.username
    val editable = d.playlist.owner == null || d.playlist.owner == username
    DetailScaffold(selection, songs) {
        item(key = "header") {
            DetailHeader(
                eyebrow = if (d.playlist.isPublic == true) "Public playlist" else "Playlist",
                title = d.playlist.name,
                coverId = d.playlist.coverArt,
                subtitle = {
                    Text(
                        listOfNotNull(
                            d.playlist.owner?.let { "by $it" },
                            Format.count(songs.size, "track"),
                            Format.duration(songs.sumOf { it.duration ?: 0 }.toLong()),
                        ).joinToString(" · "),
                        color = Kultr.colors.ink2,
                    )
                    d.playlist.comment?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Kultr.colors.ink3, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, onClick = { actions.play(songs) })
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, onClick = { actions.shuffle(songs) })
                    Pill("Queue", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = { actions.enqueue(songs) })
                    DownloadPill(songs, d.playlist.name)
                    if (editable) {
                        Pill("Rename", icon = Icons.Rounded.Edit, onClick = { renaming = true })
                        Pill("Delete", icon = Icons.Rounded.Delete, onClick = { deleting = true })
                    }
                },
            )
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "This playlist is empty", body = "Add tracks from any track's menu.")
            }
        }
        songItems(
            songs,
            currentId = player.current?.id,
            downloaded = downloaded,
            selection = selection,
            onPlay = { index -> actions.play(songs, index) },
            compact = settings.compactRows,
            extraActions = { index, _ ->
                if (!editable) emptyList() else listOf(
                    MenuAction("Remove from this playlist", Icons.Rounded.RemoveCircleOutline) {
                        actions.launch {
                            graph.library.removeFromPlaylist(id, listOf(index))?.let { graph.messages.error(it) }
                        }
                    },
                )
            },
        )
    }
    if (renaming) {
        TextInputDialog("Rename playlist", d.playlist.name, "Rename", onConfirm = { name ->
            actions.launch { graph.library.renamePlaylist(id, name)?.let { graph.messages.error(it) } }
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete “${d.playlist.name}”?",
            body = "The playlist is deleted on your server. The tracks in it are not affected.",
            confirm = "Delete",
            onConfirm = {
                actions.launch {
                    val error = graph.library.deletePlaylist(id)
                    if (error != null) graph.messages.error(error) else actions.back()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

// ---------------------------------------------------------------- genre --

@Composable
fun GenreScreen(name: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val songs by remember(name) { graph.library.songsOfGenre(name) }.collectAsStateWithLifecycle(emptyList())
    val albums by remember(name) { graph.library.albumsOfGenre(name) }.collectAsStateWithLifecycle(emptyList())
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    DetailScaffold(selection, songs) {
        item(key = "header") {
            DetailHeader(
                eyebrow = "Genre",
                title = name,
                coverId = null,
                subtitle = {
                    Text("${Format.count(albums.size, "album")} · ${Format.count(songs.size, "track")}", color = Kultr.colors.ink2)
                },
                actions = {
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, accent = true, onClick = { actions.shuffle(songs) })
                    Pill("InjeKt set", icon = Icons.Rounded.AutoAwesome, onClick = { songs.randomOrNull()?.let { actions.startInjektSet(it) } })
                    DownloadPill(songs, name)
                },
            )
        }
        if (albums.isNotEmpty()) {
            item(key = "albums") {
                SectionHeader("Albums")
                Shelf(albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "songs-head") { SectionHeader("Tracks") }
            songItems(
                songs,
                currentId = player.current?.id,
                downloaded = downloaded,
                selection = selection,
                onPlay = { index -> playWindow(actions, songs, index) },
                compact = settings.compactRows,
            )
        }
    }
}
