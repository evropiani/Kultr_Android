package app.kultr.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.CrateItem
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.ArtistCard
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SkeletonGrid
import app.kultr.android.ui.components.SkeletonRows
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.components.minCell
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Song
import app.kultr.core.karousel.Station
import app.kultr.core.settings.Crate
import app.kultr.core.settings.NoteAndTags
import app.kultr.core.settings.tagCounts
import app.kultr.core.util.Format
import java.util.Locale
import java.util.UUID

// ------------------------------------------------------------ notes and tags --

/**
 * "Your notes" on an album or artist page: tags you can tap to see everything
 * with that tag, and a note of your own. Private; kept with your settings.
 */
@Composable
fun NotesSection(key: String, name: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val annotation = settings.annotations[key] ?: NoteAndTags()
    var addingTag by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Your notes", modifier = Modifier.padding(top = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(annotation.tags, key = { it }) { tag ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.sand)
                            .clickable { menu = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Sell, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tag, style = MaterialTheme.typography.labelLarge, color = colors.accent)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Everything tagged “$tag”") }, leadingIcon = { Icon(Icons.Rounded.Sell, null) }, onClick = {
                            menu = false
                            actions.openTag(tag)
                        })
                        DropdownMenuItem(text = { Text("Remove the tag", color = colors.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger) }, onClick = {
                            menu = false
                            graph.crates.setAnnotation(key, annotation.withoutTag(tag))
                        })
                    }
                }
            }
            item(key = "+") {
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, colors.ink4, RoundedCornerShape(50))
                        .clickable { addingTag = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add a tag", tint = colors.ink2, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tag", style = MaterialTheme.typography.labelLarge, color = colors.ink2)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.sand.copy(alpha = colors.sand.alpha * 0.6f))
                .clickable { editingNote = true }
                .padding(12.dp),
        ) {
            if (annotation.note.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EditNote, contentDescription = null, tint = colors.ink3)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a note", style = MaterialTheme.typography.bodyMedium, color = colors.ink3)
                }
            } else {
                Text(annotation.note, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            }
        }
    }

    if (addingTag) {
        val existing = tagCounts(settings.annotations).take(6).map { it.first }.filter { tag -> annotation.tags.none { it.equals(tag, true) } }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingTag = false },
            title = { Text("Tag “$name”") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (existing.isEmpty()) {
                        Text("A word of your own, like “gym” or “rainy day”.", style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(existing) { tag -> Pill(tag, onClick = { text = tag }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    graph.crates.setAnnotation(key, annotation.withTag(text))
                    addingTag = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingTag = false }) { Text("Cancel") } },
        )
    }
    if (editingNote) {
        var text by remember { mutableStateOf(annotation.note) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text(name) },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("Your note") }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    graph.crates.setAnnotation(key, annotation.copy(note = text.trim()))
                    editingNote = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNote = false }) { Text("Cancel") } },
        )
    }
}

/** Library → Tags: every tag you use, with how many albums and artists carry it. */
@Composable
fun TagsTab(query: String) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val settings by actions.graph.settings.settings.collectAsStateWithLifecycle()
    val needle = query.trim().lowercase()
    val tags = tagCounts(settings.annotations).filter { needle.isEmpty() || it.first.lowercase().contains(needle) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(8.dp))) {
        if (tags.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Sell,
                    if (needle.isEmpty()) "No tags yet" else "No tags match “$query”",
                    body = "Open an album or an artist and tap + Tag under Your notes.",
                )
            }
        }
        items(tags, key = { it.first }) { (tag, count) ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.openTag(tag) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Sell, contentDescription = null, tint = colors.accent, modifier = Modifier.width(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(tag, style = MaterialTheme.typography.bodyLarge, color = colors.ink, modifier = Modifier.weight(1f))
                Text("$count", style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.ink4)
            }
        }
    }
}

/** Everything with one tag: albums and artists, and a way to play them all or make a crate of them. */
@Composable
fun TagScreen(tag: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val found by produceState<Pair<List<Album>, List<Artist>>?>(null, settings.annotations) {
        val wanted = tag.lowercase()
        val keys = settings.annotations.filterValues { a -> a.tags.any { it.lowercase() == wanted } }.keys
        val albums = graph.library.albumsByIds(keys.filter { it.startsWith("album:") }.map { it.removePrefix("album:") })
        val artists = graph.library.artistsByIds(keys.filter { it.startsWith("artist:") }.map { it.removePrefix("artist:") })
        value = albums.sortedBy { Format.sortKey(it.name) } to artists.sortedBy { Format.sortKey(it.name) }
    }
    suspend fun songs(): List<Song> {
        val (albums, artists) = found ?: return emptyList()
        val out = LinkedHashMap<String, Song>()
        albums.forEach { album -> graph.library.songsOfAlbumNow(album.id).forEach { out.putIfAbsent(it.id, it) } }
        artists.forEach { artist -> graph.library.songsOfArtistNow(artist.id).forEach { out.putIfAbsent(it.id, it) } }
        return out.values.toList()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(16.dp))) {
        item {
            BackBar()
            PageTitle(tag)
        }
        val (albums, artists) = found ?: run {
            item { SkeletonGrid(110.dp, count = 4) }
            return@LazyColumn
        }
        if (albums.isEmpty() && artists.isEmpty()) {
            item { EmptyState(Icons.Rounded.Sell, "Nothing tagged “$tag”", body = "The tag was taken off everything that had it.") }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton("Play", Icons.Rounded.PlayArrow, onClick = { actions.launch { actions.play(songs()) } }, modifier = Modifier.weight(1f))
                WideButton("Shuffle", Icons.Rounded.Shuffle, onClick = { actions.launch { actions.shuffle(songs()) } }, modifier = Modifier.weight(1f))
            }
        }
        if (albums.isNotEmpty()) {
            item {
                SectionHeader("Albums")
                Shelf(albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
            }
        }
        if (artists.isNotEmpty()) {
            item {
                SectionHeader("Artists")
                Shelf(artists, key = { it.id }, cardWidth = 130.dp) { artist, modifier -> ArtistCard(artist, { actions.openArtist(artist.id) }, modifier) }
            }
        }
        item {
            Pill(
                "Make a crate of everything tagged “$tag”",
                icon = Icons.Rounded.Inventory2,
                modifier = Modifier.padding(16.dp),
                onClick = {
                    val name = tag.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    val crate = Crate(name = name, rules = Station(name = name, icon = "tag.fill", tags = listOf(tag)))
                    graph.crates.save(crate)
                    actions.openCrate(crate.id)
                },
            )
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}

// ------------------------------------------------------------------ crates --

/** Library → Crates: your crates as cards, and a button for a new one. */
@Composable
fun CratesTab(query: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val needle = query.trim().lowercase()
    val crates = settings.crates.filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf<StationDraft?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(settings.gridSize.minCell()),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = chromePadding(10.dp)),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(6.dp)) {
                Pill("New crate", icon = Icons.Rounded.Add, accent = true, onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Fill it by hand") }, leadingIcon = { Icon(Icons.Rounded.PanTool, null) }, onClick = {
                        menu = false
                        creating = true
                    })
                    DropdownMenuItem(text = { Text("Fill it by rules") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = {
                        menu = false
                        rules = StationDraft(Station(name = "New crate", icon = "shippingbox.fill", minRating = 4, addedDays = 365), isNew = true, noun = "crate")
                    })
                }
            }
        }
        if (crates.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Rounded.Inventory2,
                    if (needle.isEmpty()) "No crates yet" else "No crates match “$query”",
                    body = "A crate holds albums, artists and tracks you choose: open the menu of any of them and tap Add to crate. " +
                        "Or let rules fill it, like “4 stars and up, added this year”.",
                )
            }
        }
        items(crates, key = { it.id }) { CrateCard(it) }
    }
    if (creating) {
        TextInputDialog("New crate", "", "Create", onConfirm = { name ->
            val crate = Crate(name = name.trim())
            graph.crates.save(crate)
            graph.messages.show("Open the menu of an album, artist or track and tap Add to crate to fill “${crate.name}”.")
        }, onDismiss = { creating = false })
    }
    rules?.let { draft ->
        StationEditor(draft, onSave = { picked ->
            val crate = Crate(name = picked.name, icon = picked.icon, rules = picked)
            graph.crates.save(crate)
            rules = null
            actions.openCrate(crate.id)
        }, onCancel = { rules = null })
    }
}

/** A crate as a card: up to four covers from what is in it. */
@Composable
private fun CrateCard(crate: Crate) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val covers by produceState(emptyList<String>(), crate) {
        value = if (crate.byRules) {
            graph.crates.songs(crate).mapNotNull { it.artworkId }.distinct().take(4)
        } else {
            (crate.albums + graph.library.songsByIds(crate.songs.take(8)).mapNotNull { it.artworkId }).distinct().take(4)
        }
    }
    Column(Modifier.clickable { actions.openCrate(crate.id) }.padding(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Kultr.radii.md)).background(colors.sand),
            contentAlignment = Alignment.Center,
        ) {
            if (covers.isEmpty()) {
                Icon(stationIcon(crate.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { cover -> ArtworkFill(cover, Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(0.dp)) }
                            if (row.size == 1 && covers.size > 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(crate.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (crate.byRules) crate.rules?.summary.orEmpty() else crate.contents,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One crate: play it, see what is in it, change its rules, keep it as a playlist. */
@Composable
fun CrateScreen(id: String) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val counts by graph.library.counts.collectAsStateWithLifecycle(null)
    val selection = rememberSelection()
    val crate = settings.crates.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf<StationDraft?>(null) }
    val contents by produceState<CrateContents?>(null, crate, counts) {
        val c = crate ?: return@produceState
        val songs = graph.crates.songs(c)
        value = if (c.byRules) {
            CrateContents(songs)
        } else {
            CrateContents(songs, graph.library.albumsByIds(c.albums), graph.library.artistsByIds(c.artists), graph.library.songsByIds(c.songs))
        }
    }
    if (crate == null) {
        Column {
            BackBar()
            EmptyState(Icons.Rounded.Inventory2, "This crate is gone", body = "It was deleted.")
        }
        return
    }
    val songs = contents?.songs.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(16.dp))) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackBar() }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = colors.accent) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = {
                            menu = false
                            renaming = true
                        })
                        crate.rules?.let { r ->
                            DropdownMenuItem(text = { Text("Change the rules…") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = {
                                menu = false
                                rules = StationDraft(r.copy(name = crate.name, icon = crate.icon), isNew = false, noun = "crate")
                            })
                            DropdownMenuItem(text = { Text("Make it a station") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = {
                                menu = false
                                graph.stations.save(r.copy(id = UUID.randomUUID().toString(), name = crate.name, icon = crate.icon))
                                graph.messages.show("“${crate.name}” is a station too now")
                            })
                        }
                        if (crate.syncPlaylist) {
                            DropdownMenuItem(text = { Text("Update the playlist now") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = {
                                menu = false
                                actions.launch { if (graph.crates.syncNow(crate.id) == null) graph.messages.show("Playlist up to date") }
                            })
                        }
                        DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, onClick = {
                            menu = false
                            actions.addToPlaylist(songs)
                        })
                        DropdownMenuItem(text = { Text("Delete the crate", color = colors.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger) }, onClick = {
                            menu = false
                            deleting = true
                        })
                    }
                }
            }
            PageTitle(crate.name)
            Text(
                if (crate.byRules) crate.rules?.summary.orEmpty() else crate.contents,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                if (contents == null) "Counting…" else Format.count(songs.size, "track"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton("Play", Icons.Rounded.PlayArrow, onClick = { actions.play(songs) }, modifier = Modifier.weight(1f), enabled = songs.isNotEmpty())
                WideButton("Shuffle", Icons.Rounded.Shuffle, onClick = { actions.shuffle(songs) }, modifier = Modifier.weight(1f), enabled = songs.isNotEmpty())
            }
            Row(
                Modifier.fillMaxWidth().clickable { setSync(graph, crate, !crate.syncPlaylist) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep as a playlist", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    Text(
                        if (graph.isLocal) {
                            "A playlist on this phone with the same tracks, updated when the crate changes."
                        } else {
                            "A playlist on your server with the same tracks, so other apps see it too. Updated when the crate changes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = crate.syncPlaylist, onCheckedChange = { setSync(graph, crate, it) })
            }
        }
        val shown = contents
        when {
            shown == null -> item { SkeletonRows() }
            crate.byRules -> {
                item { SectionHeader("Tracks") }
                songItems(songs, player.current?.id, downloaded, selection, onPlay = { actions.play(songs, it) }, compact = settings.compactRows)
            }
            else -> {
                if (crate.isEmpty) {
                    item {
                        EmptyState(
                            Icons.Rounded.Inventory2,
                            "Nothing in this crate yet",
                            body = "Open the menu of an album, an artist or a track anywhere in Kultr and tap Add to crate.",
                        )
                    }
                }
                if (shown.albums.isNotEmpty()) {
                    item {
                        SectionHeader("Albums")
                        Shelf(shown.albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
                    }
                }
                if (shown.artists.isNotEmpty()) {
                    item {
                        SectionHeader("Artists")
                        Shelf(shown.artists, key = { it.id }, cardWidth = 130.dp) { artist, modifier -> ArtistCard(artist, { actions.openArtist(artist.id) }, modifier) }
                    }
                }
                if (shown.handPicked.isNotEmpty()) {
                    item { SectionHeader("Tracks") }
                    songItems(
                        shown.handPicked,
                        player.current?.id,
                        downloaded,
                        selection,
                        onPlay = { actions.play(shown.handPicked, it) },
                        compact = settings.compactRows,
                        keyPrefix = "crate",
                    )
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog("Rename the crate", crate.name, "Rename", onConfirm = { graph.crates.save(crate.copy(name = it.trim())) }, onDismiss = { renaming = false })
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete “${crate.name}”?",
            body = if (crate.playlistId == null) "The music stays in your library." else "The music stays in your library, and so does its playlist.",
            confirm = "Delete",
            onConfirm = {
                graph.crates.delete(crate)
                actions.back()
            },
            onDismiss = { deleting = false },
        )
    }
    rules?.let { draft ->
        StationEditor(draft, onSave = { picked ->
            graph.crates.save(crate.copy(rules = picked, name = picked.name, icon = picked.icon))
            rules = null
        }, onCancel = { rules = null })
    }
}

private data class CrateContents(
    val songs: List<Song>,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val handPicked: List<Song> = emptyList(),
)

private fun setSync(graph: app.kultr.android.AppGraph, crate: Crate, on: Boolean) {
    graph.crates.save(crate.copy(syncPlaylist = on))
    if (on) graph.messages.show("“${crate.name}” will be kept as a playlist")
}

/** "Add to crate": every crate filled by hand, ticked where the item is in it already, and a new one. */
@Composable
fun AddToCrateDialog(item: CrateItem, onDismiss: () -> Unit) {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val crates = settings.crates.filter { !it.byRules }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add “${item.title}” to a crate", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("New crate…", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.accent)
                    }
                }
                items(crates, key = { it.id }) { crate ->
                    val inside = graph.crates.contains(crate, item)
                    Row(
                        Modifier.fillMaxWidth().clickable { graph.crates.toggle(item, crate.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(stationIcon(crate.icon), contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(crate.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                            Text(crate.contents, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                        }
                        Icon(
                            if (inside) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (inside) "In the crate" else null,
                            tint = if (inside) colors.accent else colors.ink4,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    if (creating) {
        TextInputDialog("New crate", "", "Create", onConfirm = { name ->
            val crate = Crate(name = name.trim())
            graph.crates.save(crate)
            graph.crates.toggle(item, crate.id)
        }, onDismiss = { creating = false })
    }
}
