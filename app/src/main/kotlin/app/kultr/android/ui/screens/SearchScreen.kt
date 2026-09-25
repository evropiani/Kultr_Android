package app.kultr.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.SearchResults
import app.kultr.android.data.db.Counts
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Loading
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.describeError
import kotlinx.coroutines.delay

/**
 * Search runs against the local mirror, so it is instant and works offline.
 * Until the library has been synced — or when asked — it asks the server.
 * The field itself is in the floating bar ([GlassNavigationBar][app.kultr.android.ui.GlassNavigationBar]),
 * grown out of the search button; this page shows what it finds.
 */
@Composable
fun SearchScreen(query: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val counts by graph.library.counts.collectAsStateWithLifecycle(Counts(0, 0, 0, 0, 0))
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    var serverSearch by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf(SearchResults()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selection = rememberSelection()
    val useServer = serverSearch || counts.songs == 0

    LaunchedEffect(query, useServer) {
        val q = query.trim()
        if (q.length < 2) {
            results = SearchResults()
            error = null
            return@LaunchedEffect
        }
        delay(if (useServer) 350 else 120)
        loading = true
        error = null
        results = try {
            if (useServer) graph.library.searchServer(q) else graph.library.search(q)
        } catch (err: Exception) {
            if (err is kotlinx.coroutines.CancellationException) throw err
            error = describeError(err)
            SearchResults()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            color = Kultr.colors.ink,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (counts.songs == 0) "Searching your server (the library is not synced yet)" else "Search on the server instead",
                color = Kultr.colors.ink3,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (counts.songs > 0) Switch(checked = serverSearch, onCheckedChange = { serverSearch = it })
        }
        SelectionBar(selection, results.songs)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = chromePadding())) {
            when {
                query.trim().length < 2 -> item {
                    EmptyState(Icons.Rounded.Search, "Find anything", body = "Type at least two letters.")
                }
                loading && results.isEmpty -> item { Loading() }
                error != null -> item { EmptyState(Icons.Rounded.CloudQueue, "Search failed", body = error) }
                results.isEmpty -> item { EmptyState(Icons.Rounded.Search, "Nothing matches “${query.trim()}”") }
                else -> {
                    if (results.artists.isNotEmpty()) {
                        item(key = "artists") {
                            SectionHeader("Artists")
                            Shelf(results.artists, key = { it.id }, cardWidth = 120.dp) { artist, modifier ->
                                ArtistCard(artist, { actions.openArtist(artist.id) }, modifier)
                            }
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item(key = "albums") {
                            SectionHeader("Albums")
                            Shelf(results.albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
                        }
                    }
                    if (results.songs.isNotEmpty()) {
                        item(key = "songs") { SectionHeader("Tracks") }
                        songItems(
                            results.songs,
                            currentId = player.current?.id,
                            downloaded = downloaded,
                            selection = selection,
                            onPlay = { index -> actions.play(results.songs, index) },
                        )
                    }
                }
            }
        }
    }
}
