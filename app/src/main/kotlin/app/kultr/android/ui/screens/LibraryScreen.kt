package app.kultr.android.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.db.DownloadUsage
import app.kultr.android.ui.LibraryTab
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Loading
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.PlaylistCard
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.minCell
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.RadioStation
import app.kultr.core.api.Song
import app.kultr.core.api.asSong
import app.kultr.core.api.describeError
import app.kultr.core.util.Format

private enum class AlbumSort(val label: String) { NAME("Name"), ARTIST("Artist"), YEAR("Year"), ADDED("Recently added"), PLAYS("Most played") }
private enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), ADDED("Recently added"), PLAYS("Most played"), RATING("Rating") }

@Composable
fun LibraryScreen(initialTab: LibraryTab) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            color = Kultr.colors.ink,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        PrimaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 8.dp,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            LibraryTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry; query = "" }, text = { Text(entry.label) })
            }
        }
        if (tab in setOf(LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.SONGS, LibraryTab.PLAYLISTS, LibraryTab.GENRES, LibraryTab.RADIO)) {
            FilterField(query, onChange = { query = it }, placeholder = "Filter ${tab.label.lowercase()}")
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                LibraryTab.ALBUMS -> AlbumsTab(query)
                LibraryTab.ARTISTS -> ArtistsTab(query)
                LibraryTab.SONGS -> SongsTab(query)
                LibraryTab.PLAYLISTS -> PlaylistsTab(query)
                LibraryTab.GENRES -> GenresTab(query)
                LibraryTab.FAVOURITES -> FavouritesTab()
                LibraryTab.DOWNLOADS -> DownloadsTab()
                LibraryTab.RADIO -> RadioTab(query)
            }
        }
    }
}

@Composable
fun FilterField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun <T : Enum<T>> SortMenu(current: T, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(label(current), icon = Icons.AutoMirrored.Rounded.Sort, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onPick(option) })
            }
        }
    }
}

// --------------------------------------------------------------- albums --

@Composable
private fun AlbumsTab(query: String) {
    val actions = LocalActions.current
    val settings by actions.graph.settings.settings.collectAsStateWithLifecycle()
    val albums by remember { actions.graph.library.albums() }.collectAsStateWithLifecycle(null)
    var sort by rememberSaveable { mutableStateOf(AlbumSort.NAME) }
    val list = albums ?: return Loading()
    val shown = remember(list, query, sort) {
        val needle = query.trim().lowercase()
        val filtered = if (needle.isEmpty()) list else list.filter {
            it.name.lowercase().contains(needle) || (it.artist ?: "").lowercase().contains(needle)
        }
        when (sort) {
            AlbumSort.NAME -> filtered.sortedBy { Format.sortKey(it.name) }
            AlbumSort.ARTIST -> filtered.sortedWith(compareBy<Album> { Format.sortKey(it.artist) }.thenBy { it.year ?: 0 })
            AlbumSort.YEAR -> filtered.sortedByDescending { it.year ?: 0 }
            AlbumSort.ADDED -> filtered.sortedByDescending { it.created ?: "" }
            AlbumSort.PLAYS -> filtered.sortedByDescending { it.playCount ?: 0 }
        }
    }
    if (list.isEmpty()) return EmptyLibrary()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(settings.gridSize.minCell()),
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Format.count(shown.size, "album"), color = Kultr.colors.ink3, modifier = Modifier.weight(1f))
                SortMenu(sort, AlbumSort.entries, { it.label }) { sort = it }
            }
        }
        items(shown, key = { it.id }) { album -> AlbumCard(album, onClick = { actions.openAlbum(album.id) }) }
    }
}

@Composable
fun EmptyLibrary() {
    val actions = LocalActions.current
    EmptyState(
        icon = Icons.Rounded.LibraryMusic,
        title = "Nothing here yet",
        body = "Sync your library to browse it on this phone.",
        action = { Pill("Open Sync", accent = true, onClick = { actions.navigate(app.kultr.android.ui.Routes.SYNC) }) },
    )
}

// -------------------------------------------------------------- artists --

@Composable
private fun ArtistsTab(query: String) {
    val actions = LocalActions.current
    val settings by actions.graph.settings.settings.collectAsStateWithLifecycle()
    val artists by remember { actions.graph.library.artists() }.collectAsStateWithLifecycle(null)
    val list = artists ?: return Loading()
    if (list.isEmpty()) return EmptyLibrary()
    val shown = remember(list, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) list else list.filter { it.name.lowercase().contains(needle) }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(settings.gridSize.minCell()),
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(Format.count(shown.size, "artist"), color = Kultr.colors.ink3, modifier = Modifier.padding(6.dp))
        }
        items(shown, key = { it.id }) { artist -> ArtistCard(artist, onClick = { actions.openArtist(artist.id) }) }
    }
}

// ---------------------------------------------------------------- songs --

@Composable
private fun SongsTab(query: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val songs by remember { graph.library.songs() }.collectAsStateWithLifecycle(null)
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(SongSort.TITLE) }
    val selection = rememberSelection()
    val list = songs ?: return Loading()
    if (list.isEmpty()) return EmptyLibrary()
    val shown = remember(list, query, sort) {
        val needle = query.trim().lowercase()
        val filtered = if (needle.isEmpty()) list else list.filter {
            it.title.lowercase().contains(needle) || (it.artist ?: "").lowercase().contains(needle) ||
                (it.album ?: "").lowercase().contains(needle)
        }
        when (sort) {
            SongSort.TITLE -> filtered
            SongSort.ARTIST -> filtered.sortedWith(compareBy<Song> { Format.sortKey(it.artist) }.thenBy { it.album }.thenBy { it.track ?: 0 })
            SongSort.ALBUM -> filtered.sortedWith(compareBy<Song> { Format.sortKey(it.album) }.thenBy { it.discNumber ?: 1 }.thenBy { it.track ?: 0 })
            SongSort.ADDED -> filtered.sortedByDescending { it.created ?: "" }
            SongSort.PLAYS -> filtered.sortedByDescending { it.playCount ?: 0 }
            SongSort.RATING -> filtered.sortedByDescending { it.userRating ?: 0 }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (selection.active) SelectionBar(selection, shown)
        LazyColumn(Modifier.weight(1f)) {
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, onClick = { actions.play(shown.take(1000)) })
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, onClick = { actions.shuffle(shown.shuffled().take(1000)) })
                    SortMenu(sort, SongSort.entries, { it.label }) { sort = it }
                    Text(Format.count(shown.size, "track"), color = Kultr.colors.ink3)
                }
            }
            songItems(
                shown,
                currentId = player.current?.id,
                downloaded = downloaded,
                selection = selection,
                onPlay = { index -> playWindow(actions, shown, index) },
                compact = settings.compactRows,
            )
        }
    }
}

/** Play from [index] of a (possibly huge) list without sending the whole library to the player. */
fun playWindow(actions: app.kultr.android.ui.AppActions, songs: List<Song>, index: Int) {
    val from = (index - 200).coerceAtLeast(0)
    val window = songs.subList(from, (index + 800).coerceAtMost(songs.size))
    actions.play(window, index - from)
}

// ------------------------------------------------------------ playlists --

@Composable
private fun PlaylistsTab(query: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val playlists by remember { graph.library.playlists() }.collectAsStateWithLifecycle(null)
    var creating by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val list = playlists ?: return Loading()
    val shown = remember(list, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) list else list.filter { it.name.lowercase().contains(needle) }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(settings.gridSize.minCell()),
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("New playlist", icon = Icons.Rounded.Add, accent = true, onClick = { creating = true })
                Pill(if (refreshing) "Refreshing…" else "Refresh", icon = Icons.Rounded.Refresh, enabled = !refreshing, onClick = {
                    refreshing = true
                    actions.launch {
                        graph.library.refreshPlaylists()?.let { graph.messages.error(it) }
                        refreshing = false
                    }
                })
            }
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.LibraryMusic, "No playlists", body = "Create one, or add tracks to a new playlist from any track's menu.")
            }
        }
        items(shown, key = { it.id }) { playlist -> PlaylistCard(playlist, onClick = { actions.openPlaylist(playlist.id) }) }
    }
    if (creating) {
        TextInputDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { name -> actions.launch { graph.library.createPlaylist(name, emptyList())?.let { graph.messages.error(it) } } },
            onDismiss = { creating = false },
        )
    }
}

// --------------------------------------------------------------- genres --

@Composable
private fun GenresTab(query: String) {
    val actions = LocalActions.current
    val genres by remember { actions.graph.library.genres() }.collectAsStateWithLifecycle(null)
    val list = genres ?: return Loading()
    if (list.isEmpty()) return EmptyLibrary()
    val shown = remember(list, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) list else list.filter { it.value.lowercase().contains(needle) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.value }) { genre ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.openGenre(genre.value) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(null, size = 44.dp, label = genre.value)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(genre.value, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(genre.songCount?.let { Format.count(it, "track") }, genre.albumCount?.let { Format.count(it, "album") }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Kultr.colors.ink3,
                    )
                }
                IconButton(onClick = {
                    actions.launch { actions.shuffle(actions.graph.library.songsOfGenreNow(genre.value)) }
                }) { Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle ${genre.value}", tint = Kultr.colors.ink2) }
            }
        }
    }
}

// ----------------------------------------------------------- favourites --

@Composable
private fun FavouritesTab() {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val songs by remember { graph.library.starredSongs() }.collectAsStateWithLifecycle(emptyList())
    val albums by remember { graph.library.starredAlbums() }.collectAsStateWithLifecycle(emptyList())
    val artists by remember { graph.library.starredArtists() }.collectAsStateWithLifecycle(emptyList())
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
        EmptyState(Icons.Rounded.FavoriteBorder, "No favourites yet", body = "Heart a track, album or artist and it shows up here.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (selection.active) SelectionBar(selection, songs)
        LazyColumn(Modifier.weight(1f)) {
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, enabled = songs.isNotEmpty(), onClick = { actions.play(songs) })
                    Pill("Shuffle", icon = Icons.Rounded.Shuffle, enabled = songs.isNotEmpty(), onClick = { actions.shuffle(songs) })
                    val missing = graph.offline.missing(songs)
                    Pill(
                        if (missing == 0) "Downloaded" else "Download",
                        icon = Icons.Rounded.Download,
                        badge = if (missing > 0) "$missing" else null,
                        enabled = missing > 0,
                        onClick = { actions.download(songs, "Your favourites") },
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item(key = "albums") {
                    SectionHeader("Albums")
                    Shelf(albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
                }
            }
            if (artists.isNotEmpty()) {
                item(key = "artists") {
                    SectionHeader("Artists")
                    Shelf(artists, key = { it.id }, cardWidth = 130.dp) { artist, modifier -> ArtistCard(artist, { actions.openArtist(artist.id) }, modifier) }
                }
            }
            if (songs.isNotEmpty()) {
                item(key = "songs-head") { SectionHeader("Tracks", icon = Icons.Rounded.Favorite) }
                songItems(
                    songs,
                    currentId = player.current?.id,
                    downloaded = downloaded,
                    selection = selection,
                    onPlay = { index -> actions.play(songs, index) },
                    compact = settings.compactRows,
                )
            }
        }
    }
}

// ------------------------------------------------------------ downloads --

@Composable
private fun DownloadsTab() = OfflineContent()

// ---------------------------------------------------------------- radio --

@Composable
private fun RadioTab(query: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val stations by produceState<List<RadioStation>?>(null) {
        value = try {
            graph.library.radioStations()
        } catch (err: Exception) {
            error = describeError(err)
            emptyList()
        }
    }
    val list = stations ?: return Loading()
    val shown = remember(list, query, settings.favouriteRadios) {
        val needle = query.trim().lowercase()
        (if (needle.isEmpty()) list else list.filter { it.name.lowercase().contains(needle) })
            .sortedByDescending { it.id in settings.favouriteRadios }
    }
    if (list.isEmpty()) {
        EmptyState(
            Icons.Rounded.Radio,
            "No radio stations",
            body = error ?: "Add internet radio stations in Navidrome and they appear here.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.id }) { station ->
            val favourite = station.id in settings.favouriteRadios
            Row(
                Modifier.fillMaxWidth().clickable { actions.play(listOf(station.asSong())) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(null, size = 44.dp, label = station.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(station.name, color = Kultr.colors.ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(station.homePageUrl ?: station.streamUrl, color = Kultr.colors.ink3, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    graph.settings.update { s ->
                        s.copy(favouriteRadios = if (favourite) s.favouriteRadios - station.id else s.favouriteRadios + station.id)
                    }
                }) {
                    Icon(
                        if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favourite) "Unfavourite" else "Favourite",
                        tint = if (favourite) Kultr.colors.accent else Kultr.colors.ink3,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
