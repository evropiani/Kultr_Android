package app.kultr.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.db.ArtistPlays
import app.kultr.android.data.db.HistoryEntity
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.Routes
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AlbumCard
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Shelf
import app.kultr.android.ui.components.SkeletonRows
import app.kultr.android.ui.components.SongRow
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Album
import app.kultr.core.api.Song
import app.kultr.core.util.Format
import app.kultr.core.util.MonthRecap
import app.kultr.core.util.Play
import app.kultr.core.util.monthRecap
import app.kultr.core.util.recapMonths
import app.kultr.core.util.t
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// Made on use, so month and day names follow the language Kultr is in.
private val MONTH_TITLE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("LLLL yyyy")
private val MONTH_NAME: DateTimeFormatter get() = DateTimeFormatter.ofPattern("LLLL")
private val DAY_TITLE: DateTimeFormatter get() = DateTimeFormatter.ofPattern(t("EEEE d MMMM"))

private fun List<HistoryEntity>.plays() = map { Play(it.songId, it.playedAt, it.seconds) }

/**
 * A month of listening on this phone: how long, how much, the tracks, albums
 * and artists you played most, and the day you listened the longest. Any
 * month with history can be picked from the calendar button.
 */
@Composable
fun RecapScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val history by remember { graph.library.history(20_000) }.collectAsStateWithLifecycle(null)
    val songs by produceState<Map<String, Song>?>(null, history) {
        val ids = history?.map { it.songId }?.distinct() ?: return@produceState
        value = graph.library.songsByIds(ids).associateBy { it.id }
    }
    val plays = remember(history) { history.orEmpty().plays() }
    val months = remember(plays) { recapMonths(plays) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val month = picked?.let(YearMonth::parse)?.takeIf { it in months } ?: months.firstOrNull()
    val recap = remember(plays, songs, month) { month?.let { m -> songs?.let { monthRecap(plays, it, m) } } }
    var choosing by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(24.dp))) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackBar() }
                if (months.size > 1) {
                    Box {
                        IconButton(onClick = { choosing = true }) { Icon(Icons.Rounded.CalendarMonth, contentDescription = t("Choose a month"), tint = colors.accent) }
                        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                            months.forEach { m ->
                                DropdownMenuItem(text = { Text(m.format(MONTH_TITLE)) }, onClick = {
                                    picked = m.toString()
                                    choosing = false
                                })
                            }
                        }
                    }
                }
            }
            Text(
                month?.format(MONTH_TITLE) ?: t("Monthly recap"),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        when {
            history == null || (month != null && songs == null) -> item { SkeletonRows() }
            recap == null || recap.isEmpty -> item {
                EmptyState(
                    Icons.Rounded.CalendarMonth,
                    t("No months to look back on yet"),
                    body = t("Kultr makes a recap from what you play on this phone. Play a few tracks and this month's shows up here."),
                )
            }
            else -> recapItems(recap)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recapItems(recap: MonthRecap) {
    val top = recap.topSongs.map { it.item }
    item {
        val colors = Kultr.colors
        val thisMonth = recap.month == YearMonth.now()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Format.duration(recap.seconds), style = MaterialTheme.typography.displaySmall, color = colors.ink)
            Text(
                "${Format.count(recap.plays, "play")} · ${Format.count(recap.tracks, "track")} · ${Format.count(recap.artists, "artist")}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            val before = recap.month.minusMonths(1).format(MONTH_NAME)
            Text(
                when {
                    recap.previousSeconds <= 0 -> if (thisMonth) t("So far this month.") else t("Nothing played the month before.")
                    thisMonth -> t("So far this month. %@ was %@.", before, Format.duration(recap.previousSeconds))
                    else -> t("%@ was %@.", before, Format.duration(recap.previousSeconds))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
            )
        }
    }
    if (top.isNotEmpty()) {
        item {
            val actions = LocalActions.current
            WideButton(t("Play this month's top tracks"), Icons.Rounded.PlayArrow, onClick = { actions.play(top) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), accent = true)
            SectionHeader(t("Top tracks"), modifier = Modifier.padding(top = 8.dp))
        }
        itemsIndexed(recap.topSongs, key = { _, entry -> "top:" + entry.item.id }) { index, entry ->
            val actions = LocalActions.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SongRow(entry.item, onClick = { actions.play(top, index) }) }
                Text("${entry.plays}×", style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
    val albums = recap.topAlbums.mapNotNull { entry ->
        entry.item.id?.let { Album(id = it, name = entry.item.name, artist = entry.item.artist, coverArt = entry.item.coverArt) }
    }
    if (albums.isNotEmpty()) {
        item {
            val actions = LocalActions.current
            SectionHeader(t("Top albums"))
            Shelf(albums, key = { it.id }) { album, modifier -> AlbumCard(album, { actions.openAlbum(album.id) }, modifier) }
        }
    }
    if (recap.topArtists.isNotEmpty()) {
        item {
            SectionHeader(t("Top artists"))
            PlayBars(recap.topArtists.map { ArtistPlays(it.item.name, it.item.id, it.plays.toLong()) })
        }
    }
    item {
        val colors = Kultr.colors
        SectionHeader(t("The month in numbers"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            recap.busiestDay?.let { (day, seconds) -> Fact(t("Your longest day"), "${day.format(DAY_TITLE)} · ${Format.duration(seconds)}") }
            Fact(
                t("First listens"),
                if (recap.firstListens == 0) t("Only tracks you had played before") else t("%@ you had never played on this phone", Format.count(recap.firstListens, "track")),
            )
            Fact(t("On average"), t("%@ a play", Format.duration(recap.seconds / recap.plays)))
        }
        Text(
            t("Made from what you played on this phone. Plays on other devices count on your server, but not here."),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink)
    }
}

/** Artists as accent bars, longest for the most plays; each opens its artist. */
@Composable
fun PlayBars(rows: List<ArtistPlays>) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val peak = rows.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Column(Modifier.fillMaxWidth().clickable(enabled = row.artistId != null) { actions.openArtist(row.artistId) }) {
                Row {
                    Text(row.name ?: t("Unknown artist"), color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(Format.count(row.plays.toInt(), "play"), style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth((row.plays.toFloat() / peak).coerceAtLeast(0.02f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent),
                )
            }
        }
    }
}

/** This month so far, opening the full recap. */
@Composable
fun RecapLink(history: List<HistoryEntity>) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val now = remember(history) { monthRecap(history.plays(), emptyMap(), YearMonth.now()) }
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.sand)
            .clickable { actions.navigate(Routes.RECAP) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = colors.accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("Monthly recap"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                if (now.isEmpty) t("Nothing played this month yet") else t("%@ so far: %@, %@", now.month.format(MONTH_TITLE), Format.duration(now.seconds), Format.count(now.plays, "play")),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.ink3)
    }
}
