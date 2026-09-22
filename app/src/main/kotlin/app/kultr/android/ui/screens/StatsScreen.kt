package app.kultr.android.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.db.HistoryEntity
import app.kultr.android.ui.LocalActions
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
import java.util.Locale

private data class StatsSummary(
    val plays: Int,
    val seconds: Long,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<String, Long>>,
    val days: List<Pair<LocalDate, Long>>,
)

/**
 * Listening stats from Kultr's own history on this phone, not the server's
 * counters — so they reflect what you actually played here.
 */
@Composable
fun StatsScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val history by remember { graph.library.history(5000) }.collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    val summary by produceState<StatsSummary?>(null, history) { value = summarise(history, graph.library.songsByIds(history.map { it.songId }.distinct())) }

    Box(Modifier.fillMaxSize()) {
        AccentWash()
        Column(Modifier.fillMaxSize()) {
            BackBar("Listening")
            val s = summary
            if (history.isEmpty()) {
                EmptyState(Icons.Rounded.BarChart, "No listening history yet", body = "Play something and Kultr starts keeping track. History stays on this phone.")
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "head") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${Format.count(s?.plays ?: history.size, "play")} · ${Format.duration(s?.seconds ?: 0)} of music",
                            style = MaterialTheme.typography.titleLarge,
                            color = Kultr.colors.ink,
                        )
                        Pill("Clear history", icon = Icons.Rounded.Delete, onClick = { confirm = true })
                    }
                }
                if (s != null) {
                    item(key = "chart") {
                        SectionHeader("The last two weeks", icon = Icons.Rounded.BarChart)
                        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { DayChart(s.days) }
                    }
                    if (s.topArtists.isNotEmpty()) {
                        item(key = "artists") {
                            SectionHeader("Top artists")
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val max = s.topArtists.first().second.coerceAtLeast(1)
                                val accent = Kultr.colors.accent
                                s.topArtists.forEach { (name, seconds) ->
                                    Row {
                                        Text(name, color = Kultr.colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(Format.duration(seconds), color = Kultr.colors.ink3)
                                    }
                                    Canvas(Modifier.fillMaxWidth(seconds.toFloat() / max).height(4.dp)) {
                                        drawRect(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
                                    }
                                }
                            }
                        }
                    }
                    if (s.topSongs.isNotEmpty()) {
                        item(key = "songs-head") { SectionHeader("Most played here") }
                        items(s.topSongs, key = { it.first.id }) { (song, count) ->
                            Row {
                                Box(Modifier.weight(1f)) {
                                    SongRow(song, onClick = { actions.play(s.topSongs.map { it.first }, s.topSongs.indexOfFirst { it.first.id == song.id }) })
                                }
                                Text("$count×", color = Kultr.colors.ink3, modifier = Modifier.padding(top = 20.dp, end = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Clear listening history?",
            body = "Your stats on this phone start again from nothing. Play counts on the server are not touched.",
            confirm = "Clear",
            onConfirm = { actions.launch { graph.library.clearHistory() } },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun DayChart(days: List<Pair<LocalDate, Long>>) {
    val colors = Kultr.colors
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
                    if (i % 2 == 0) day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) else "",
                    color = colors.ink3,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun summarise(history: List<HistoryEntity>, songs: List<Song>): StatsSummary {
    val byId = songs.associateBy { it.id }
    val zone = ZoneId.systemDefault()
    val plays = HashMap<String, Int>()
    val artistSeconds = HashMap<String, Long>()
    val daySeconds = HashMap<LocalDate, Long>()
    var total = 0L
    for (entry in history) {
        total += entry.seconds
        plays[entry.songId] = (plays[entry.songId] ?: 0) + 1
        byId[entry.songId]?.artist?.let { artistSeconds[it] = (artistSeconds[it] ?: 0) + entry.seconds }
        val day = Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalDate()
        daySeconds[day] = (daySeconds[day] ?: 0) + entry.seconds
    }
    val today = LocalDate.now(zone)
    val days = (13 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (daySeconds[it] ?: 0L) } }
    return StatsSummary(
        plays = history.size,
        seconds = total,
        topSongs = plays.entries.sortedByDescending { it.value }.mapNotNull { (id, count) -> byId[id]?.let { it to count } }.take(20),
        topArtists = artistSeconds.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        days = days,
    )
}
