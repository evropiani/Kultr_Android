package app.kultr.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.SearchResults
import app.kultr.android.data.db.Counts
import app.kultr.android.ui.Commands
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.LocalMusicMode
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SkeletonRows
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.describeError
import app.kultr.core.util.KultrCommand
import app.kultr.core.util.t
import kotlinx.coroutines.delay

/**
 * Search runs against the local mirror, so it is instant and works offline.
 * Until the library has been synced — or when asked — it asks the server.
 * The field itself is in the floating bar ([GlassNavigationBar][app.kultr.android.ui.GlassNavigationBar]),
 * grown out of the search button; this page shows what it finds.
 */
@Composable
fun SearchScreen(query: String, onRun: (KultrCommand) -> Unit) {
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
    val local = LocalMusicMode.current
    // The phone's own music is all in the library; only a server has more to ask.
    val useServer = !local && (serverSearch || counts.songs == 0)
    val trimmed = query.trim()
    // A command searches for what it names ("shuffle love" → "love"), or for nothing.
    val command = remember(trimmed) { Commands.parse(trimmed, actions) }
    val lookFor = Commands.subject(command) ?: trimmed

    LaunchedEffect(lookFor, useServer) {
        val q = lookFor
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
            t("Search"),
            style = MaterialTheme.typography.headlineMedium,
            color = Kultr.colors.ink,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        if (!local) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (counts.songs == 0) t("Searching your server (the library is not synced yet)") else t("Search on the server instead"),
                    color = Kultr.colors.ink3,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (counts.songs > 0) Switch(checked = serverSearch, onCheckedChange = { serverSearch = it })
            }
        }
        SelectionBar(selection, results.songs)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = chromePadding())) {
            // The command bar: words Kultr can do, above what it finds.
            command?.let { item(key = "command") { CommandRow(it) { onRun(it) } } }
            when {
                trimmed.length < 2 -> item {
                    EmptyState(
                        Icons.Rounded.Search,
                        t("Find anything, or say what to do"),
                        body = t("Type at least two letters. Or try “play Night Drive”, “shuffle” and an artist, “sleep in 30 minutes”, “next” or “plan an hour”."),
                    )
                }
                lookFor.length < 2 -> Unit
                loading && results.isEmpty -> item { SkeletonRows() }
                error != null -> item { EmptyState(Icons.Rounded.CloudQueue, t("Search failed"), body = error) }
                results.isEmpty && command != null -> Unit
                results.isEmpty -> item { EmptyState(Icons.Rounded.Search, t("Nothing matches “%@”", trimmed), body = t("Check the spelling, or try fewer words.")) }
                else -> {
                    if (results.artists.isNotEmpty()) {
                        item(key = "artists") {
                            SectionHeader(t("Artists"))
                            Shelf(results.artists, key = { it.id }, cardWidth = 120.dp) { artist, modifier ->
                                ArtistCard(artist, { actions.openArtist(artist.id) }, modifier)
                            }
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item(key = "albums") {
                            SectionHeader(t("Albums"))
                            Shelf(results.albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
                        }
                    }
                    if (results.songs.isNotEmpty()) {
                        item(key = "songs") { SectionHeader(t("Tracks")) }
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

/** A command the words make: tap it, or press Search on the keyboard, and Kultr does it. */
@Composable
private fun CommandRow(command: KultrCommand, onRun: () -> Unit) {
    val colors = Kultr.colors
    val (icon, text) = Commands.describe(command)
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.sand)
            .clickable(onClick = onRun)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.onAccent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 2)
            Text(t("Tap, or press Search on the keyboard"), style = MaterialTheme.typography.bodySmall, color = colors.ink3)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = null, tint = colors.ink3)
    }
}
