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
import androidx.compose.material.icons.rounded.Inventory2
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
import app.kultr.android.data.CrateItem
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.LocalMusicMode
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.InfoLine
import app.kultr.android.ui.components.MenuAction
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SkeletonDetail
import app.kultr.android.ui.components.SkeletonRows
import app.kultr.android.ui.components.SongSelection
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.starredShown
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.ArtistInfo
import app.kultr.core.api.Song
import app.kultr.core.settings.NoteAndTags
import app.kultr.core.util.Format
import app.kultr.core.util.t

/** A back arrow that floats over the header. */
@Composable
fun BackBar(title: String? = null) {
    val actions = LocalActions.current
    Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = t("Back")) }
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Artwork, title, subtitle, Play and Shuffle, and more actions — the top of every detail page. */
@Composable
fun DetailHeader(
    title: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    artworkShape: Shape = RoundedCornerShape(Kultr.radii.lg),
    imageUrl: String? = null,
    subtitle: @Composable () -> Unit = {},
    tags: List<Pair<String, (() -> Unit)?>> = emptyList(),
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
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
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        subtitle()
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            InfoLine(tags)
        }
        Spacer(Modifier.height(14.dp))
        if (onPlay != null || onShuffle != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onPlay?.let { WideButton(t("Play"), Icons.Rounded.PlayArrow, onClick = it, modifier = Modifier.weight(1f)) }
                onShuffle?.let { WideButton(t("Shuffle"), Icons.Rounded.Shuffle, onClick = it, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DownloadPill(songs: List<Song>, label: String) {
    val actions = LocalActions.current
    val downloaded by actions.graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val missing = songs.count { !it.isRadio && it.id !in downloaded }
    if (songs.isEmpty() || LocalMusicMode.current) return
    if (missing == 0) {
        Pill(t("Remove download"), icon = Icons.Rounded.DownloadDone, onClick = { actions.removeDownloads(songs) })
    } else {
        Pill(t("Download"), icon = Icons.Rounded.Download, badge = "$missing", onClick = { actions.download(songs, label) })
    }
}

@Composable
private fun DetailScaffold(selection: SongSelection, songs: List<Song>, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackBar()
            SelectionBar(selection, songs)
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
        Column { BackBar(); SkeletonDetail() }
        return
    }
    val discs = songs.groupBy { it.discNumber ?: 1 }
    DetailScaffold(selection, songs) {
        item(key = "header") {
            DetailHeader(
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
                onPlay = { actions.play(songs) },
                onShuffle = { actions.shuffle(songs) },
                actions = {
                    val favourite = starredShown(shown)
                    Pill(
                        t("Favourite"),
                        icon = if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = { actions.setAlbumFavourite(shown, !favourite) },
                    )
                    Pill(t("Queue"), icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = { actions.enqueue(songs) })
                    Pill(t("Add to playlist"), icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = { actions.addToPlaylist(songs) })
                    Pill(t("Crate"), icon = Icons.Rounded.Inventory2, onClick = { actions.addToCrate(CrateItem.Album(shown.id, shown.name)) })
                    DownloadPill(songs, shown.name)
                },
            )
        }
        if (songs.isEmpty()) {
            item(key = "empty") { SkeletonRows(count = 6, artwork = false) }
        }
        discs.forEach { (disc, discSongs) ->
            if (discs.size > 1) item(key = "disc$disc") { SectionHeader(t("Disc %@", disc)) }
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
        item(key = "yours") { NotesSection(NoteAndTags.albumKey(shown.id), shown.name) }
        notes?.let { text ->
            item(key = "notes") {
                SectionHeader(t("About this album"))
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
        Column { BackBar(); SkeletonDetail() }
        return
    }
    val allAlbums = albums.ifEmpty { remote?.album.orEmpty() }
    val popular = top.ifEmpty { songs.sortedByDescending { it.playCount ?: 0 }.take(10) }.take(10)
    DetailScaffold(selection, popular) {
        item(key = "header") {
            DetailHeader(
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
                onPlay = { actions.launch { actions.play(graph.library.songsOfArtistNow(id)) } },
                onShuffle = { actions.launch { actions.shuffle(graph.library.songsOfArtistNow(id)) } },
                actions = {
                    val favourite = starredShown(shown)
                    Pill(
                        t("Favourite"),
                        icon = if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onClick = { actions.setArtistFavourite(shown, !favourite) },
                    )
                    Pill(t("InjeKt radio"), icon = Icons.Rounded.AutoAwesome, onClick = {
                        actions.launch { songs.randomOrNull()?.let { actions.startInjektSet(it) } }
                    })
                    Pill(t("Crate"), icon = Icons.Rounded.Inventory2, onClick = { actions.addToCrate(CrateItem.Artist(shown.id, shown.name)) })
                    DownloadPill(songs, shown.name)
                },
            )
        }
        if (popular.isNotEmpty()) {
            item(key = "top-head") { SectionHeader(t("Popular")) }
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
                SectionHeader(t("Albums"))
                Shelf(allAlbums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
            }
        }
        item(key = "yours") { NotesSection(NoteAndTags.artistKey(shown.id), shown.name) }
        val similar = info?.similarArtist.orEmpty()
        if (similar.isNotEmpty()) {
            item(key = "similar") {
                SectionHeader(t("Similar artists"))
                Shelf(similar, key = { it.id }, cardWidth = 130.dp) { other, modifier -> ArtistCard(other, { actions.openArtist(other.id) }, modifier) }
            }
        }
        info?.biography?.let { bio ->
            val text = cleanHtml(bio)
            if (text.isNotBlank()) {
                item(key = "bio") {
                    SectionHeader(t("About"))
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
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, t("Playlist not found"), body = t("It may have been deleted on the server."))
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
                onPlay = { actions.play(songs) },
                onShuffle = { actions.shuffle(songs) },
                actions = {
                    Pill(t("Queue"), icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = { actions.enqueue(songs) })
                    DownloadPill(songs, d.playlist.name)
                    if (editable) {
                        Pill(t("Rename"), icon = Icons.Rounded.Edit, onClick = { renaming = true })
                        Pill(t("Delete"), icon = Icons.Rounded.Delete, onClick = { deleting = true })
                    }
                },
            )
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, t("This playlist is empty"), body = t("Add tracks from any track's menu."))
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
                    MenuAction(t("Remove from this playlist"), Icons.Rounded.RemoveCircleOutline) {
                        actions.launch {
                            graph.library.removeFromPlaylist(id, listOf(index))?.let { graph.messages.error(it) }
                        }
                    },
                )
            },
        )
    }
    if (renaming) {
        TextInputDialog(t("Rename playlist"), d.playlist.name, t("Rename"), onConfirm = { name ->
            actions.launch { graph.library.renamePlaylist(id, name)?.let { graph.messages.error(it) } }
        }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            title = t("Delete “%@”?", d.playlist.name),
            body = t("The playlist is deleted on your server. The tracks in it are not affected."),
            confirm = t("Delete"),
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
                title = name,
                coverId = null,
                subtitle = {
                    Text("${Format.count(albums.size, "album")} · ${Format.count(songs.size, "track")}", color = Kultr.colors.ink2)
                },
                onPlay = { actions.play(songs) },
                onShuffle = { actions.shuffle(songs) },
                actions = {
                    Pill(t("InjeKt set"), icon = Icons.Rounded.AutoAwesome, onClick = { songs.randomOrNull()?.let { actions.startInjektSet(it) } })
                    DownloadPill(songs, name)
                },
            )
        }
        if (albums.isNotEmpty()) {
            item(key = "albums") {
                SectionHeader(t("Albums"))
                Shelf(albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "songs-head") { SectionHeader(t("Tracks")) }
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
