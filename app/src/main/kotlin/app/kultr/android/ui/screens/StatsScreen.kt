package app.kultr.android.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.db.HistoryEntity
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AccentWash
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.SongRow
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import app.kultr.core.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * Listening, from your server's numbers: every device's plays count, and they
 * survive reinstalling. What was played last merges the server's last-played
 * times with this phone's own history; the day-by-day chart is this phone's
 * alone, since the server keeps a count per track rather than a log of every
 * play.
 */
@Composable
fun StatsScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val history by remember { graph.library.history(5000) }.collectAsStateWithLifecycle(emptyList())
    val mostPlayed by remember { graph.library.mostPlayedSongs(20) }.collectAsStateWithLifecycle(emptyList())
    val artists by remember { graph.library.artistPlays(10) }.collectAsStateWithLifecycle(emptyList())
    val total by remember { graph.library.totalPlays() }.collectAsStateWithLifecycle(0L)
    val pending by remember { graph.library.pendingPlays() }.collectAsStateWithLifecycle(0)
    val refreshing by graph.sync.listening.collectAsStateWithLifecycle()
    val version by graph.sync.listeningVersion.collectAsStateWithLifecycle()
    val recent by produceState(emptyList<Song>(), history.firstOrNull()?.id, version, total) {
        value = graph.library.recentlyPlayed(12)
    }
    val days = remember(history) { lastTwoWeeks(history) }
    var confirm by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AccentWash()
        Column(Modifier.fillMaxSize()) {
            BackBar("Listening")
            if (total == 0L && history.isEmpty() && recent.isEmpty()) {
                EmptyState(
                    Icons.Rounded.BarChart,
                    "Nothing played yet",
                    body = "Play something and it shows up here — along with what you play on your server from other devices.",
                )
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = chromePadding())) {
                item(key = "head") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${Format.count(total.toInt(), "play")} on your server",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                        )
                        Text(
                            if (pending > 0) {
                                "${Format.count(pending, "play")} from this phone waiting to be sent."
                            } else {
                                "Counted on your server, so every device sees the same."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pending > 0) colors.warning else colors.ink3,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                if (refreshing) "Refreshing…" else "Refresh",
                                icon = Icons.Rounded.Refresh,
                                onClick = { if (!refreshing) graph.sync.refreshListening(force = true) },
                            )
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "recent-head") { SectionHeader("Played recently", icon = Icons.Rounded.History) }
                    items(recent, key = { "recent-${it.id}" }) { song ->
                        SongRow(song, onClick = { actions.play(recent, recent.indexOfFirst { it.id == song.id }) })
                    }
                }
                if (mostPlayed.isNotEmpty()) {
                    item(key = "most-head") { SectionHeader("Played the most", icon = Icons.Rounded.PlayArrow) }
                    items(mostPlayed, key = { "most-${it.id}" }) { song ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                SongRow(song, onClick = { actions.play(mostPlayed, mostPlayed.indexOfFirst { it.id == song.id }) })
                            }
                            Text("${song.playCount ?: 0}×", color = colors.ink3, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item(key = "artists") {
                        SectionHeader("Top artists")
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val max = artists.first().plays.coerceAtLeast(1)
                            val accent = colors.accent
                            artists.forEach { artist ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            artist.artistId?.let { id -> Modifier.clickable { actions.openArtist(id) } } ?: Modifier,
                                        ),
                                ) {
                                    Row {
                                        Text(artist.name ?: "Unknown artist", color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(Format.count(artist.plays.toInt(), "play"), color = colors.ink3)
                                    }
                                    Canvas(Modifier.fillMaxWidth(artist.plays.toFloat() / max).height(4.dp)) {
                                        drawRect(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "chart") {
                    SectionHeader("The last two weeks on this phone", icon = Icons.Rounded.BarChart)
                    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DayChart(days)
                            Text(
                                "Your server counts plays per track, not each play, so this chart comes from this phone's own history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink3,
                            )
                        }
                    }
                    Row(Modifier.padding(16.dp)) {
                        Pill("Clear this phone's history", icon = Icons.Rounded.Delete, onClick = { confirm = true })
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Clear this phone's history?",
            body = "The chart starts again from nothing. Play counts on your server are not touched, and plays not sent yet are kept until they are.",
            confirm = "Clear",
            onConfirm = { actions.launch { graph.library.clearHistory() } },
            onDismiss = { confirm = false },
        )
    }
}

/** Seconds listened on each of the last fourteen days, oldest first. */
private fun lastTwoWeeks(history: List<HistoryEntity>): List<Pair<LocalDate, Long>> {
    val zone = ZoneId.systemDefault()
    val daySeconds = HashMap<LocalDate, Long>()
    for (entry in history) {
        val day = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalDate()
        daySeconds[day] = (daySeconds[day] ?: 0) + entry.seconds
    }
    val today = LocalDate.now(zone)
    return (13 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (daySeconds[it] ?: 0L) } }
}

@Composable
private fun DayChart(days: List<Pair<LocalDate, Long>>) {
    val colors = Kultr.colors
    val locale = LocalConfiguration.current.locales[0]
    val peak = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (days.size - 1)) / days.size
            days.forEachIndexed { i, (_, seconds) ->
                val h = (seconds.toFloat() / peak * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    Brush.verticalGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.35f)), startY = size.height - h, endY = size.height),
                    topLeft = Offset(i * (barWidth + gap), size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, (day, _) ->
                Text(
                    if (i % 2 == 0) day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) else "",
                    color = colors.ink3,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
