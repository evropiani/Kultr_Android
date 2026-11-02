package app.kultr.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.db.Counts
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.LocalMusicMode
import app.kultr.android.ui.Routes
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.PlaylistCard
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SongRow
import app.kultr.android.ui.components.minCell
import app.kultr.android.ui.player.CastButton
import app.kultr.android.ui.rememberFolderPicker
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Playlist
import app.kultr.core.api.RadioStation
import app.kultr.core.api.Song
import app.kultr.core.api.asSong
import app.kultr.core.settings.HomeTile
import app.kultr.core.settings.TileKind
import app.kultr.core.settings.resolveHomeTiles
import app.kultr.core.sync.SyncMode
import app.kultr.core.sync.SyncState
import app.kultr.core.util.Format
import java.util.Calendar

private fun tileIcon(id: String): ImageVector = when {
    id == "recentlyPlayed" -> Icons.Rounded.History
    id.startsWith("mostPlayed") -> Icons.Rounded.PlayArrow
    id.startsWith("random") -> Icons.Rounded.Shuffle
    id == "recentlyAdded" -> Icons.Rounded.Album
    id == "favouritePlaylists" -> Icons.AutoMirrored.Rounded.QueueMusic
    id.startsWith("favourite") -> Icons.Rounded.Favorite
    else -> Icons.Rounded.Radio
}

@Composable
fun HomeScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val counts by graph.library.counts.collectAsStateWithLifecycle(Counts(0, 0, 0, 0, 0))
    val syncState by graph.library.syncState.collectAsStateWithLifecycle(SyncState())
    val syncing by graph.sync.running.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val local = LocalMusicMode.current
    val tiles = remember(settings.homeTiles) { resolveHomeTiles(settings.homeTiles) }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding())) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.greeting(hour), style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, modifier = Modifier.weight(1f))
                    CastButton()
                    IconButton(onClick = { actions.navigate(Routes.STATS) }) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Listening stats", tint = Kultr.colors.ink2)
                    }
                    IconButton(onClick = { actions.navigate(Routes.SYNC) }) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sync", tint = if (syncing) Kultr.colors.accent else Kultr.colors.ink2)
                    }
                }
                Text(
                    if (syncing) {
                        progress?.message ?: "Syncing…"
                    } else {
                        "${Format.count(counts.songs, "track")} · ${Format.count(counts.albums, "album")} · checked ${Format.relative(syncState.lastCheck)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Kultr.colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (counts.songs > 0) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Shuffle all", icon = Icons.Rounded.Shuffle, onClick = {
                            actions.launch { actions.shuffle(graph.library.randomSongs(200)) }
                        })
                        Pill("Start an InjeKt set", icon = Icons.Rounded.AutoAwesome, accent = true, onClick = { actions.startInjektSet() })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (counts.albums == 0 && local) {
            item(key = "empty") { LocalEmptyState(syncing) }
        } else if (counts.albums == 0) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Album,
                    title = if (syncing) "Syncing your library…" else "Your library is not synced yet",
                    body = "Kultr keeps a copy of your library on this phone, so browsing is instant and works without a connection. " +
                        "It is a one-time job — after that, only changes are fetched.",
                    action = {
                        Pill(
                            if (syncing) "Syncing…" else "Sync my library",
                            icon = Icons.Rounded.Sync,
                            accent = true,
                            enabled = !syncing,
                            onClick = { graph.sync.start(SyncMode.FULL) },
                        )
                    },
                )
            }
        } else {
            tiles.forEach { tile ->
                item(key = "tile:${tile.id}") { HomeShelf(tile) }
            }
            if (tiles.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        icon = Icons.Rounded.Favorite,
                        title = "Nothing on the home page yet",
                        body = "Choose what appears here, and in what order, in Settings → Home page.",
                    )
                }
            }
        }
    }
}

/** What a home shelf holds: one kind of thing each. */
private sealed interface ShelfItems {
    val isEmpty: Boolean

    data class Songs(val songs: List<Song>) : ShelfItems {
        override val isEmpty get() = songs.isEmpty()
    }

    data class Albums(val albums: List<Album>) : ShelfItems {
        override val isEmpty get() = albums.isEmpty()
    }

    data class Artists(val artists: List<Artist>) : ShelfItems {
        override val isEmpty get() = artists.isEmpty()
    }

    data class Playlists(val playlists: List<Playlist>) : ShelfItems {
        override val isEmpty get() = playlists.isEmpty()
    }

    data class Radios(val stations: List<RadioStation>) : ShelfItems {
        override val isEmpty get() = stations.isEmpty()
    }
}

/** Up to [limit] of what [tile] shows, kept up to date: on Home a handful, on its own page all of it. */
@Composable
private fun rememberShelf(tile: HomeTile, limit: Int): ShelfItems {
    val graph = LocalActions.current.graph
    val library = graph.library
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val playing by graph.player.state.collectAsStateWithLifecycle()
    val counts by library.counts.collectAsStateWithLifecycle(Counts(0, 0, 0, 0, 0))
    val lastCheck = library.syncState.collectAsStateWithLifecycle(SyncState()).value.lastCheck
    // Bumped when plays went up to the server or came down from other devices.
    val listening by graph.sync.listeningVersion.collectAsStateWithLifecycle()
    return when (tile.kind) {
        TileKind.SONGS -> {
            val songs by when (tile.id) {
                "mostPlayedSongs" -> remember(limit) { library.mostPlayedSongs(limit) }.collectAsStateWithLifecycle(emptyList<Song>())
                "favouriteSongs" -> remember { library.starredSongs() }.collectAsStateWithLifecycle(emptyList())
                "recentlyPlayed" -> produceState(emptyList<Song>(), playing.current?.id, lastCheck, listening) { value = library.recentlyPlayed(limit) }
                else -> produceState(emptyList<Song>(), counts.songs, lastCheck) { value = library.randomSongs(limit) }
            }
            ShelfItems.Songs(songs.take(limit))
        }
        TileKind.ALBUMS -> {
            val albums by when (tile.id) {
                "mostPlayedAlbums" -> remember(limit) { library.mostPlayedAlbums(limit) }.collectAsStateWithLifecycle(emptyList())
                "recentlyAdded" -> remember(limit) { library.recentlyAdded(limit) }.collectAsStateWithLifecycle(emptyList())
                "favouriteAlbums" -> remember { library.starredAlbums() }.collectAsStateWithLifecycle(emptyList())
                else -> produceState(emptyList<Album>(), counts.albums, lastCheck) { value = library.randomAlbums(limit) }
            }
            ShelfItems.Albums(albums.take(limit))
        }
        TileKind.ARTISTS -> {
            val artists by when (tile.id) {
                "mostPlayedArtists" -> remember(limit) { library.mostPlayedArtists(limit) }.collectAsStateWithLifecycle(emptyList())
                "favouriteArtists" -> remember { library.starredArtists() }.collectAsStateWithLifecycle(emptyList())
                else -> produceState(emptyList<Artist>(), counts.artists, lastCheck) { value = library.randomArtists(limit) }
            }
            ShelfItems.Artists(artists.take(limit))
        }
        TileKind.PLAYLISTS -> {
            val username = graph.auth.active.collectAsStateWithLifecycle().value?.username
            val playlists by when (tile.id) {
                "mostPlayedPlaylists" -> remember { library.playlistsByPlays() }.collectAsStateWithLifecycle(emptyList())
                else -> remember { library.playlists() }.collectAsStateWithLifecycle(emptyList())
            }
            val mine = if (tile.id == "favouritePlaylists") {
                playlists.filter { it.owner == null || it.owner == username }.sortedByDescending { it.changed ?: it.created }
            } else {
                playlists
            }
            ShelfItems.Playlists(mine.take(limit))
        }
        TileKind.RADIOS -> {
            val stations by produceState(emptyList<RadioStation>(), lastCheck) {
                value = runCatching { library.radioStations() }.getOrDefault(emptyList())
            }
            ShelfItems.Radios((if (tile.id == "favouriteRadios") stations.filter { it.id in settings.favouriteRadios } else stations).take(limit))
        }
    }
}

@Composable
private fun HomeShelf(tile: HomeTile) {
    val actions = LocalActions.current
    val items = rememberShelf(tile, SHELF_SIZE)
    if (items.isEmpty) return
    ShelfHeader(tile) { actions.navigate(Routes.shelf(tile.id)) }
    when (items) {
        is ShelfItems.Songs -> SongList(items.songs.take(10))
        is ShelfItems.Albums -> Shelf(items.albums, key = { it.id }) { album, modifier ->
            AlbumCard(album, onClick = { actions.openAlbum(album.id) }, modifier = modifier)
        }
        is ShelfItems.Artists -> Shelf(items.artists, key = { it.id }, cardWidth = 130.dp) { artist, modifier ->
            ArtistCard(artist, onClick = { actions.openArtist(artist.id) }, modifier = modifier)
        }
        is ShelfItems.Playlists -> Shelf(items.playlists, key = { it.id }) { playlist, modifier ->
            PlaylistCard(playlist, onClick = { actions.openPlaylist(playlist.id) }, modifier = modifier)
        }
        is ShelfItems.Radios -> Shelf(items.stations, key = { it.id }, cardWidth = 130.dp) { station, modifier ->
            StationCard(station, modifier) { actions.play(listOf(station.asSong())) }
        }
    }
}

/** A shelf's title, which opens all of it: "Played the most ›". */
@Composable
private fun ShelfHeader(tile: HomeTile, onOpen: () -> Unit) {
    SectionHeader(
        tile.title,
        icon = tileIcon(tile.id),
        modifier = Modifier.padding(top = 12.dp).clickable(onClick = onOpen),
        action = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "See all", tint = Kultr.colors.ink3) },
    )
}

/** Songs, each playing the list from itself. */
@Composable
private fun SongList(songs: List<Song>) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val playing by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    Column {
        songs.forEachIndexed { index, song ->
            SongRow(
                song = song,
                onClick = { actions.play(songs, index) },
                isCurrent = song.id == playing.current?.id,
                downloaded = song.id in downloaded,
                compact = settings.compactRows,
            )
        }
    }
}

/** A home shelf on a page of its own, all of it. */
@Composable
fun ShelfScreen(id: String) {
    val actions = LocalActions.current
    val settings by actions.graph.settings.settings.collectAsStateWithLifecycle()
    val tile = resolveHomeTiles(listOf(id)).firstOrNull() ?: return
    val items = rememberShelf(tile, SHELF_PAGE_SIZE)
    Column(Modifier.fillMaxSize()) {
        BackBar()
        Text(tile.title, style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        if (items is ShelfItems.Songs) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = chromePadding())) { SongList(items.songs) }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(settings.gridSize.minCell()),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = chromePadding(10.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (items) {
                is ShelfItems.Albums -> items(items.albums, key = { it.id }) { AlbumCard(it, onClick = { actions.openAlbum(it.id) }) }
                is ShelfItems.Artists -> items(items.artists, key = { it.id }) { ArtistCard(it, onClick = { actions.openArtist(it.id) }) }
                is ShelfItems.Playlists -> items(items.playlists, key = { it.id }) { PlaylistCard(it, onClick = { actions.openPlaylist(it.id) }) }
                is ShelfItems.Radios -> items(items.stations, key = { it.id }) { StationCard(it) { actions.play(listOf(it.asSong())) } }
                is ShelfItems.Songs -> Unit
            }
        }
    }
}

private const val SHELF_SIZE = 16
private const val SHELF_PAGE_SIZE = 300

@Composable
fun StationCard(station: RadioStation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(6.dp)) {
        ArtworkFill(null, Modifier.fillMaxWidth().aspectRatio(1f), label = station.name)
        Spacer(Modifier.height(8.dp))
        Text(station.name, style = MaterialTheme.typography.bodyMedium, color = Kultr.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Radio", style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
    }
}

/** The phone's own library, still empty: choose a folder, or read the chosen ones. */
@Composable
private fun LocalEmptyState(scanning: Boolean) {
    val graph = LocalActions.current.graph
    val folders by graph.local.folders.collectAsStateWithLifecycle()
    val pick = rememberFolderPicker { graph.sync.start(SyncMode.FULL) }
    EmptyState(
        icon = Icons.Rounded.Album,
        title = when {
            scanning -> "Reading your music…"
            folders.isEmpty() -> "Where is your music?"
            else -> "No music found yet"
        },
        body = if (folders.isEmpty()) {
            "Choose the folders your music is in. Kultr reads them and builds a library of your albums, artists and covers."
        } else {
            "Kultr looked through ${folders.joinToString { it.name }} and found nothing it can play yet. Add another folder, or scan again after copying music in."
        },
        action = {
            when {
                folders.isEmpty() -> Pill("Choose a folder", icon = Icons.Rounded.Album, accent = true, onClick = pick)
                else -> Pill(
                    if (scanning) "Reading…" else "Scan again",
                    icon = Icons.Rounded.Sync,
                    accent = true,
                    enabled = !scanning,
                    onClick = { graph.sync.start(SyncMode.FULL) },
                )
            }
        },
    )
}
