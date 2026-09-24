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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import app.kultr.android.ui.LibraryTab
import app.kultr.android.ui.LocalActions
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
import app.kultr.android.ui.player.CastButton
import app.kultr.android.ui.theme.Kultr
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

        if (counts.albums == 0) {
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
                item(key = "tile:${tile.id}") { HomeShelf(tile, counts, syncState.lastCheck) }
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

@Composable
private fun HomeShelf(tile: HomeTile, counts: Counts, lastCheck: Long?) {
    val actions = LocalActions.current
    val graph = actions.graph
    val library = graph.library
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val playing by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    // Bumped when plays went up to the server or came down from other devices.
    val listening by graph.sync.listeningVersion.collectAsStateWithLifecycle()
    val seeAllTab = when (tile.id) {
        "mostPlayedAlbums", "recentlyAdded", "randomAlbums" -> LibraryTab.ALBUMS
        "mostPlayedArtists", "randomArtists" -> LibraryTab.ARTISTS
        "favouriteSongs", "favouriteAlbums", "favouriteArtists" -> LibraryTab.FAVOURITES
        "mostPlayedPlaylists", "favouritePlaylists" -> LibraryTab.PLAYLISTS
        "radios", "favouriteRadios" -> LibraryTab.RADIO
        "mostPlayedSongs" -> LibraryTab.SONGS
        else -> null
    }
    val seeAll: (() -> Unit)? = seeAllTab?.let { tab -> { actions.openLibrary(tab) } }

    when (tile.kind) {
        TileKind.SONGS -> {
            val songs by when (tile.id) {
                "mostPlayedSongs" -> remember { library.mostPlayedSongs(10) }.collectAsStateWithLifecycle(emptyList<Song>())
                "favouriteSongs" -> remember { library.starredSongs() }.collectAsStateWithLifecycle(emptyList())
                "recentlyPlayed" -> produceState(emptyList<Song>(), playing.current?.id, lastCheck, listening) { value = library.recentlyPlayed(10) }
                else -> produceState(emptyList<Song>(), counts.songs, lastCheck) { value = library.randomSongs(10) }
            }
            val shown = songs.take(10)
            if (shown.isEmpty()) return
            ShelfHeader(tile, seeAll)
            Column {
                shown.forEachIndexed { index, song ->
                    SongRow(
                        song = song,
                        onClick = { actions.play(shown, index) },
                        isCurrent = song.id == playing.current?.id,
                        downloaded = song.id in downloaded,
                        compact = settings.compactRows,
                    )
                }
            }
        }
        TileKind.ALBUMS -> {
            val albums by when (tile.id) {
                "mostPlayedAlbums" -> remember { library.mostPlayedAlbums(16) }.collectAsStateWithLifecycle(emptyList())
                "recentlyAdded" -> remember { library.recentlyAdded(16) }.collectAsStateWithLifecycle(emptyList())
                "favouriteAlbums" -> remember { library.starredAlbums() }.collectAsStateWithLifecycle(emptyList())
                else -> produceState(emptyList<app.kultr.core.api.Album>(), counts.albums, lastCheck) { value = library.randomAlbums(16) }
            }
            if (albums.isEmpty()) return
            ShelfHeader(tile, seeAll)
            Shelf(albums.take(16), key = { it.id }) { album, modifier ->
                AlbumCard(album, onClick = { actions.openAlbum(album.id) }, modifier = modifier)
            }
        }
        TileKind.ARTISTS -> {
            val artists by when (tile.id) {
                "mostPlayedArtists" -> remember { library.mostPlayedArtists(16) }.collectAsStateWithLifecycle(emptyList())
                "favouriteArtists" -> remember { library.starredArtists() }.collectAsStateWithLifecycle(emptyList())
                else -> produceState(emptyList<app.kultr.core.api.Artist>(), counts.artists, lastCheck) { value = library.randomArtists(16) }
            }
            if (artists.isEmpty()) return
            ShelfHeader(tile, seeAll)
            Shelf(artists.take(16), key = { it.id }, cardWidth = 130.dp) { artist, modifier ->
                ArtistCard(artist, onClick = { actions.openArtist(artist.id) }, modifier = modifier)
            }
        }
        TileKind.PLAYLISTS -> {
            val active by graph.auth.active.collectAsStateWithLifecycle()
            val username = active?.username
            val playlists by when (tile.id) {
                "mostPlayedPlaylists" -> remember { library.playlistsByPlays() }.collectAsStateWithLifecycle(emptyList())
                else -> remember { library.playlists() }.collectAsStateWithLifecycle(emptyList())
            }
            val shown = if (tile.id == "favouritePlaylists") {
                playlists.filter { it.owner == null || it.owner == username }.sortedByDescending { it.changed ?: it.created }
            } else {
                playlists
            }
            if (shown.isEmpty()) return
            ShelfHeader(tile, seeAll)
            Shelf(shown.take(16), key = { it.id }) { playlist, modifier ->
                PlaylistCard(playlist, onClick = { actions.openPlaylist(playlist.id) }, modifier = modifier)
            }
        }
        TileKind.RADIOS -> {
            val stations by produceState(emptyList<RadioStation>(), lastCheck) {
                value = runCatching { library.radioStations() }.getOrDefault(emptyList())
            }
            val shown = if (tile.id == "favouriteRadios") stations.filter { it.id in settings.favouriteRadios } else stations
            if (shown.isEmpty()) return
            ShelfHeader(tile, seeAll)
            Shelf(shown, key = { it.id }, cardWidth = 130.dp) { station, modifier ->
                StationCard(station, modifier) { actions.play(listOf(station.asSong())) }
            }
        }
    }
}

@Composable
private fun ShelfHeader(tile: HomeTile, seeAll: (() -> Unit)?) {
    SectionHeader(
        tile.title,
        icon = tileIcon(tile.id),
        modifier = Modifier.padding(top = 12.dp),
        action = seeAll?.let { open -> { Pill("See all", onClick = open) } },
    )
}

@Composable
fun StationCard(station: RadioStation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(6.dp)) {
        ArtworkFill(null, Modifier.fillMaxWidth().aspectRatio(1f), label = station.name)
        Spacer(Modifier.height(8.dp))
        Text(station.name, style = MaterialTheme.typography.bodyMedium, color = Kultr.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Radio", style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
    }
}
