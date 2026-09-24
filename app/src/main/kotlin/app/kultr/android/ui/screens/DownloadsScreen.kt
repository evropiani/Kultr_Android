package app.kultr.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.ActiveDownload
import app.kultr.android.data.DownloadStatus
import app.kultr.android.data.db.DownloadEntity
import app.kultr.android.data.db.DownloadUsage
import app.kultr.android.ui.DownloadsPage
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.Routes
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AccentWash
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SelectionBar
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.glass
import app.kultr.android.ui.components.rememberSelection
import app.kultr.android.ui.components.songItems
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Song
import app.kultr.core.util.Format

/** Everything about offline copies: what is coming down now, and what is already here. */
@Composable
fun DownloadsScreen(initialPage: DownloadsPage) {
    val graph = LocalActions.current.graph
    var page by rememberSaveable { mutableStateOf(initialPage) }
    LaunchedEffect(initialPage) { page = initialPage }
    val status by graph.offline.status.collectAsStateWithLifecycle()
    val usage by graph.offline.usage.collectAsStateWithLifecycle(DownloadUsage(0, 0))

    Box(Modifier.fillMaxSize()) {
        AccentWash()
        Column(Modifier.fillMaxSize()) {
            BackBar("Downloads")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pending = when (val s = status) {
                    is DownloadStatus.Running -> s.queued
                    is DownloadStatus.Waiting -> s.queued
                    DownloadStatus.Idle -> 0
                }
                Pill(
                    DownloadsPage.NOW.label,
                    icon = Icons.Rounded.Download,
                    accent = page == DownloadsPage.NOW,
                    badge = pending.takeIf { it > 0 }?.toString(),
                    onClick = { page = DownloadsPage.NOW },
                )
                Pill(
                    DownloadsPage.OFFLINE.label,
                    icon = Icons.Rounded.DownloadDone,
                    accent = page == DownloadsPage.OFFLINE,
                    badge = usage.count.takeIf { it > 0 }?.toString(),
                    onClick = { page = DownloadsPage.OFFLINE },
                )
            }
            Box(Modifier.weight(1f)) {
                when (page) {
                    DownloadsPage.NOW -> DownloadQueue()
                    DownloadsPage.OFFLINE -> OfflineContent()
                }
            }
        }
    }
}

// --------------------------------------------------------------- queue --

@Composable
private fun DownloadQueue() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val status by graph.offline.status.collectAsStateWithLifecycle()
    val active by graph.offline.active.collectAsStateWithLifecycle()
    val queuedCount by graph.offline.queuedCount.collectAsStateWithLifecycle()
    val queue by graph.offline.queue.collectAsStateWithLifecycle(emptyList())
    val failed by graph.offline.failed.collectAsStateWithLifecycle(emptyList())
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    // Tracks currently being fetched are shown above, not again in the queue.
    val waiting = remember(queue, active) { queue.filter { it.songId !in active } }
    val queueSongs = rememberSongs(waiting)
    val failedSongs = rememberSongs(failed)
    var confirmStop by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding())) {
        item(key = "status") {
            GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val s = status) {
                        is DownloadStatus.Running -> {
                            val p = s.progress
                            Text(
                                if (p != null && p.total > 0) "Downloading ${p.done + p.failed + 1} of ${p.total}" else "Downloading…",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            if (p != null && p.total > 0) {
                                LinearProgressIndicator(
                                    progress = { (p.done + p.failed).toFloat() / p.total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    buildString {
                                        append("${p.done} done · ${Format.bytes(p.bytes)}")
                                        if (p.failed > 0) append(" · ${p.failed} failed")
                                        if (s.queued > 0) append(" · ${s.queued} to go")
                                    },
                                    color = colors.ink3,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("Stop", icon = Icons.Rounded.Stop, onClick = { confirmStop = true })
                            }
                        }
                        is DownloadStatus.Waiting -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (s.forWifi) Icons.Rounded.Wifi else Icons.Rounded.CloudOff, contentDescription = null, tint = colors.warning)
                                Text(
                                    if (s.forWifi) "${Format.count(s.queued, "track")} waiting for Wi-Fi" else "${Format.count(s.queued, "track")} waiting for a connection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                )
                            }
                            Text(
                                if (s.forWifi) {
                                    "“Download on Wi-Fi only” is on, and this phone is on mobile data. Downloads start by themselves once you are on Wi-Fi."
                                } else {
                                    "Downloads start by themselves once the phone is online."
                                },
                                color = colors.ink2,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (s.forWifi || settings.offlineWifiOnly) {
                                    Pill("Use mobile data too", icon = Icons.Rounded.SignalCellularAlt, accent = true, onClick = {
                                        graph.settings.update { it.copy(offlineWifiOnly = false) }
                                    })
                                } else {
                                    Pill("Try now", icon = Icons.Rounded.Refresh, accent = true, onClick = { graph.offline.startWorker(force = true) })
                                }
                                Pill("Clear queue", icon = Icons.Rounded.Close, onClick = { confirmStop = true })
                            }
                        }
                        DownloadStatus.Idle -> {
                            Text("Nothing is downloading", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                            Text(
                                "Use Sync offline on an album, artist or playlist — or drag anything onto “Sync offline” — to keep it on this phone.",
                                color = colors.ink2,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("Download favourites", icon = Icons.Rounded.Download, onClick = {
                                    actions.launch { graph.offline.download(graph.library.starredSongsNow(), "Your favourites") }
                                })
                                Pill("Download everything", icon = Icons.Rounded.Download, onClick = {
                                    actions.launch { graph.offline.download(graph.library.allSongs(), "Your library") }
                                })
                            }
                        }
                    }
                    Text(
                        if (settings.offlineWifiOnly) "Downloads use Wi-Fi only. Change it in Settings → Offline." else "Downloads use Wi-Fi and mobile data.",
                        color = colors.ink3,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (active.isNotEmpty()) {
            item(key = "now-head") { SectionHeader("Now", icon = Icons.Rounded.Download) }
            items(active.values.toList(), key = { "active:${it.song.id}" }) { item -> ActiveRow(item) }
        }

        if (waiting.isNotEmpty()) {
            item(key = "queue-head") {
                SectionHeader("Up next") {
                    Text(
                        Format.count((queuedCount - active.size).coerceAtLeast(waiting.size), "track"),
                        color = colors.ink3,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(waiting, key = { "queued:${it.songId}" }) { row ->
                QueuedRow(queueSongs[row.songId], row.songId, error = null) {
                    actions.launch { graph.offline.dequeue(listOf(row.songId)) }
                }
            }
            val hidden = queuedCount - active.size - waiting.size
            if (hidden > 0) {
                item(key = "queue-more") {
                    Text(
                        "…and ${Format.count(hidden, "more track")}",
                        color = colors.ink3,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (failed.isNotEmpty()) {
            item(key = "failed-head") {
                SectionHeader("Failed", icon = Icons.Rounded.ErrorOutline) {
                    Pill("Retry all", icon = Icons.Rounded.Refresh, onClick = {
                        actions.launch {
                            val count = graph.offline.retryFailed()
                            graph.messages.show("Trying ${Format.count(count, "track")} again.")
                        }
                    })
                    Spacer(Modifier.width(6.dp))
                    Pill("Clear", onClick = { actions.launch { graph.offline.clearFailed() } })
                }
            }
            items(failed, key = { "failed:${it.songId}" }) { row ->
                QueuedRow(failedSongs[row.songId], row.songId, error = row.error ?: "Failed") {
                    actions.launch { graph.offline.dequeue(listOf(row.songId)) }
                }
            }
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            title = "Stop downloading?",
            body = "Everything still waiting is taken out of the queue. Tracks already downloaded stay on this phone.",
            confirm = "Stop",
            onConfirm = { graph.offline.cancel() },
            onDismiss = { confirmStop = false },
        )
    }
}

/** Songs for download rows, looked up in the library by id. */
@Composable
private fun rememberSongs(rows: List<DownloadEntity>): Map<String, Song> {
    val graph = LocalActions.current.graph
    val ids = remember(rows) { rows.map { it.songId } }
    val songs by produceState(emptyMap<String, Song>(), ids) {
        value = graph.library.songsByIds(ids).associateBy { it.id }
    }
    return songs
}

@Composable
private fun ActiveRow(item: ActiveDownload) {
    val colors = Kultr.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.song.artworkId, size = 44.dp, label = item.song.album)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.song.title, color = colors.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { item.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                if (item.total > 0) "${Format.bytes(item.bytes)} of ${Format.bytes(item.total)}" else Format.bytes(item.bytes),
                color = colors.ink3,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun QueuedRow(song: Song?, songId: String, error: String?, onRemove: () -> Unit) {
    val colors = Kultr.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song?.artworkId, size = 40.dp, label = song?.album)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song?.title ?: songId, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                error ?: listOfNotNull(song?.artist, song?.album).joinToString(" · "),
                color = if (error != null) colors.danger else colors.ink3,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove from the queue", tint = colors.ink3)
        }
    }
}

// ------------------------------------------------------------- offline --

/** What is on this phone: albums with downloaded tracks, then every track. */
@Composable
fun OfflineContent() {
    val actions = LocalActions.current
    val graph = actions.graph
    val usage by graph.offline.usage.collectAsStateWithLifecycle(DownloadUsage(0, 0))
    val ids by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val player by graph.player.state.collectAsStateWithLifecycle()
    val songs by produceState(emptyList<Song>(), ids) {
        value = graph.library.songsByIds(ids.toList())
            .sortedWith(compareBy<Song>({ it.artist.orEmpty().lowercase() }, { it.album.orEmpty().lowercase() }, { it.discNumber ?: 0 }, { it.track ?: 0 }))
    }
    val albums = remember(songs) {
        songs.filter { it.albumId != null }
            .groupBy { it.albumId!! }
            .map { (id, tracks) ->
                val first = tracks.first()
                Album(id = id, name = first.album.orEmpty(), artist = first.artist, artistId = first.artistId, coverArt = first.coverArt, songCount = tracks.size)
            }
            .sortedBy { it.name.lowercase() }
    }
    val selection = rememberSelection()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SelectionBar(selection, songs)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = chromePadding())) {
            item(key = "summary") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${Format.count(usage.count, "track")} on this phone · ${Format.bytes(usage.bytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Kultr.colors.ink,
                    )
                    DownloadIndicator(onOpen = { actions.navigate(Routes.downloads(DownloadsPage.NOW)) }, inline = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Play", icon = Icons.Rounded.PlayArrow, accent = true, enabled = songs.isNotEmpty(), onClick = { actions.play(songs) })
                        Pill("Shuffle", icon = Icons.Rounded.Shuffle, enabled = songs.isNotEmpty(), onClick = { actions.shuffle(songs) })
                        Pill("Remove all", icon = Icons.Rounded.Delete, enabled = usage.count > 0, onClick = { confirmClear = true })
                    }
                }
            }
            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.CloudDone,
                        "Nothing on this phone yet",
                        body = "Use Sync offline on any album, artist, playlist or track — or drag it onto “Sync offline” — to keep it here for when you have no connection.",
                    )
                }
            } else {
                if (albums.isNotEmpty()) {
                    item(key = "albums") {
                        SectionHeader("Albums") {
                            Text(Format.count(albums.size, "album"), color = Kultr.colors.ink3, style = MaterialTheme.typography.bodySmall)
                        }
                        Shelf(albums, key = { it.id }) { album, modifier ->
                            AlbumCard(album, { actions.openAlbum(album.id) }, modifier)
                        }
                    }
                }
                item(key = "tracks") { SectionHeader("Tracks") }
                songItems(
                    songs,
                    currentId = player.current?.id,
                    downloaded = ids,
                    selection = selection,
                    onPlay = { index -> actions.play(songs, index) },
                    keyPrefix = "offline",
                )
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Remove every download?",
            body = "The files are deleted from this phone. Your library and playlists are not touched.",
            confirm = "Remove all",
            onConfirm = { actions.launch { graph.messages.show("Removed ${graph.offline.removeAll()} downloads.") } },
            onDismiss = { confirmClear = false },
        )
    }
}

// ----------------------------------------------------------- indicator --

/**
 * A strip that shows while anything is downloading or waiting to. Tapping it
 * opens the Downloads page. [inline] drops the outer margins for use inside
 * a page.
 */
@Composable
fun DownloadIndicator(onOpen: () -> Unit, modifier: Modifier = Modifier, inline: Boolean = false) {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val status by graph.offline.status.collectAsStateWithLifecycle()
    val active by graph.offline.active.collectAsStateWithLifecycle()
    val visible = status != DownloadStatus.Idle
    AnimatedVisibility(visible, modifier = modifier, enter = expandVertically(), exit = shrinkVertically()) {
        val shape = if (inline) RoundedCornerShape(Kultr.radii.md) else RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (inline) {
                        Modifier
                            .clip(shape)
                            .background(colors.elevated.copy(alpha = 0.94f))
                            .background(colors.accent.copy(alpha = 0.08f))
                    } else {
                        // Floating over the page with the mini player and tab bar.
                        Modifier.glass(shape).clip(shape)
                    },
                )
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (val s = status) {
                    is DownloadStatus.Waiting -> (if (s.forWifi) Icons.Rounded.Wifi else Icons.Rounded.CloudOff) to colors.warning
                    else -> Icons.Rounded.Download to colors.accent
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    when (val s = status) {
                        is DownloadStatus.Running -> {
                            val p = s.progress
                            val current = active.values.firstOrNull()?.song?.title
                            when {
                                p != null && p.total > 0 && current != null -> "${p.done + p.failed + 1}/${p.total} · $current"
                                p != null && p.total > 0 -> "Downloading ${p.done + p.failed}/${p.total}"
                                else -> "Downloading…"
                            }
                        }
                        is DownloadStatus.Waiting -> if (s.forWifi) "${Format.count(s.queued, "download")} waiting for Wi-Fi" else "${Format.count(s.queued, "download")} waiting for a connection"
                        DownloadStatus.Idle -> ""
                    },
                    color = colors.ink,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open downloads", tint = colors.ink3, modifier = Modifier.size(18.dp))
            }
            val s = status
            if (s is DownloadStatus.Running) {
                val p = s.progress
                if (p != null && p.total > 0) {
                    val within = active.values.map { it.fraction }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
                    LinearProgressIndicator(
                        progress = { ((p.done + p.failed + within * active.size.coerceAtMost(1)) / p.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
    }
}
