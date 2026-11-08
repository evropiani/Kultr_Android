package app.kultr.android.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.playback.StationRequest
import app.kultr.android.ui.Commands
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.Routes
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.SettingRow
import app.kultr.android.ui.player.rememberPosition
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.LocalReduceMotion
import app.kultr.core.settings.DEFAULT_HOME_WIDGETS
import app.kultr.core.settings.HomeWidget
import app.kultr.core.settings.homeWidgetRows
import app.kultr.core.util.Format
import app.kultr.core.util.t
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Words a shortcut widget can start from. */
private val SHORTCUTS = listOf(
    "shuffle all", "plan an hour", "sleep in 30 minutes", "mix now", "open stations", "open downloads", "open recap", "open crates",
)

fun widgetIcon(kind: HomeWidget.Kind): ImageVector = when (kind) {
    HomeWidget.Kind.NOW_PLAYING -> Icons.Rounded.PlayCircle
    HomeWidget.Kind.LISTENING -> Icons.Rounded.BarChart
    HomeWidget.Kind.NEXT_MIX -> Icons.AutoMirrored.Rounded.CallMerge
    HomeWidget.Kind.STATION -> Icons.Rounded.Tune
    HomeWidget.Kind.CRATE -> Icons.Rounded.Inventory2
    HomeWidget.Kind.SHORTCUT -> Icons.Rounded.Bolt
}

/**
 * Widgets at the top of Home, like a phone's home screen: small ones side by
 * side, medium and large ones across. Hold one for its menu: its size, Edit
 * widgets (a minus takes one away, the arrows move it), or add another.
 */
@Composable
fun HomeWidgetsGrid() {
    val graph = LocalActions.current.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val widgets = settings.homeWidgets
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    fun change(transform: (List<HomeWidget>) -> List<HomeWidget>) = graph.settings.update { it.copy(homeWidgets = transform(it.homeWidgets)) }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        homeWidgetRows(widgets).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { index ->
                    val widget = widgets[index]
                    WidgetTile(
                        widget,
                        editing = editing,
                        canMoveEarlier = index > 0,
                        canMoveLater = index < widgets.size - 1,
                        onEdit = { editing = true },
                        onAdd = { adding = true },
                        onResize = { size -> change { list -> list.map { if (it.id == widget.id) it.copy(size = size) else it } } },
                        onRemove = { change { list -> list.filterNot { it.id == widget.id } } },
                        onMove = { by -> change { list -> list.toMutableList().apply { add((index + by).coerceIn(0, size - 1), removeAt(index)) } } },
                    )
                }
                if (row.size == 1 && widgets[row[0]].size == HomeWidget.Size.SMALL) Spacer(Modifier.weight(1f))
            }
        }
        if (editing || widgets.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("Add a widget"), icon = Icons.Rounded.Add, onClick = { adding = true })
                Spacer(Modifier.weight(1f))
                if (editing) Pill(t("Done"), accent = true, onClick = { editing = false })
            }
        }
    }
    if (adding) {
        AddWidgetDialog(onAdd = { widget ->
            change { it + widget }
            adding = false
        }, onDismiss = { adding = false })
    }
}

@Composable
private fun RowScope.WidgetTile(
    widget: HomeWidget,
    editing: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onResize: (HomeWidget.Size) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val colors = Kultr.colors
    val shape = RoundedCornerShape(Kultr.radii.lg)
    var menu by remember { mutableStateOf(false) }
    val wiggle = editing && !LocalReduceMotion.current
    val tilt = if (wiggle) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        transition.animateFloat(-1.2f, 1.2f, infiniteRepeatable(tween(130), RepeatMode.Reverse), label = "tilt").value
    } else {
        0f
    }
    Box(Modifier.weight(1f).height(if (widget.size == HomeWidget.Size.LARGE) 312.dp else 150.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(tilt)
                .clip(shape)
                .background(colors.sand),
        ) {
            WidgetContent(widget, enabled = !editing, onLongPress = { menu = true })
        }
        if (editing) {
            Box(
                Modifier.offset((-8).dp, (-8).dp).size(26.dp).clip(CircleShape).background(colors.elevated).border(1.dp, colors.ink4, CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, contentDescription = t("Remove %@", widget.kind.label), tint = colors.ink, modifier = Modifier.size(16.dp)) }
            Row(Modifier.align(Alignment.BottomCenter).padding(6.dp)) {
                if (canMoveEarlier) SmallRound(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, t("Move earlier")) { onMove(-1) }
                if (canMoveLater) SmallRound(Icons.AutoMirrored.Rounded.KeyboardArrowRight, t("Move later")) { onMove(1) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(t("Edit widgets")) }, leadingIcon = { Icon(Icons.Rounded.Dashboard, null) }, onClick = {
                menu = false
                onEdit()
            })
            HomeWidget.Size.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.label) },
                    leadingIcon = { Icon(if (widget.size == size) Icons.Rounded.Check else Icons.Rounded.OpenInFull, null) },
                    onClick = {
                        menu = false
                        onResize(size)
                    },
                )
            }
            DropdownMenuItem(text = { Text(t("Add a widget…")) }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = {
                menu = false
                onAdd()
            })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(t("Remove widget"), color = colors.danger) },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = colors.danger) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 4.dp).size(32.dp).clip(CircleShape).background(Kultr.colors.elevated).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Kultr.colors.accent) }
}

/** A tile's tap target; a long press anywhere opens the widget's menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tap(enabled: Boolean, onLongPress: () -> Unit, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongPress).padding(12.dp),
    ) { content() }
}

@Composable
private fun TileLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Kultr.colors.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Kultr.colors.accent, maxLines = 1)
    }
}

@Composable
private fun WidgetContent(widget: HomeWidget, enabled: Boolean, onLongPress: () -> Unit) {
    when (widget.kind) {
        HomeWidget.Kind.NOW_PLAYING -> NowPlayingTile(widget.size, enabled, onLongPress)
        HomeWidget.Kind.LISTENING -> ListeningTile(widget.size, enabled, onLongPress)
        HomeWidget.Kind.NEXT_MIX -> NextMixTile(enabled, onLongPress)
        HomeWidget.Kind.STATION -> StationTile(widget.ref, widget.size, enabled, onLongPress)
        HomeWidget.Kind.CRATE -> CrateTile(widget.ref, widget.size, enabled, onLongPress)
        HomeWidget.Kind.SHORTCUT -> ShortcutTile(widget.ref.orEmpty(), enabled, onLongPress)
    }
}

@Composable
private fun NowPlayingTile(size: HomeWidget.Size, enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val player = actions.graph.player
    val colors = Kultr.colors
    val state by player.state.collectAsStateWithLifecycle()
    val song = state.current
    if (song == null) {
        Tap(enabled, onLongPress, onClick = actions.openPlayer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileLabel(Icons.Rounded.GraphicEq, t("Now playing"))
                Text(t("Nothing is playing"), style = MaterialTheme.typography.titleMedium, color = colors.ink)
            }
        }
        return
    }
    val playing = state.playWhenReady && !state.ended
    Box(Modifier.fillMaxSize()) {
        Tap(enabled, onLongPress, onClick = actions.openPlayer) {
            when (size) {
                HomeWidget.Size.SMALL -> Column {
                    Artwork(song.artworkId, size = 64.dp, label = song.title)
                    Spacer(Modifier.weight(1f))
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HomeWidget.Size.MEDIUM -> Row {
                    Artwork(song.artworkId, size = 118.dp, label = song.title)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TileLabel(Icons.Rounded.GraphicEq, t("Now playing"))
                        Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HomeWidget.Size.LARGE -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Artwork(song.artworkId, size = 180.dp, label = song.title)
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            Modifier.align(if (size == HomeWidget.Size.LARGE) Alignment.BottomCenter else Alignment.BottomEnd).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (size != HomeWidget.Size.SMALL) {
                IconButton(enabled = enabled, onClick = player::previous) { Icon(Icons.Rounded.SkipPrevious, contentDescription = t("Previous"), tint = colors.ink) }
            }
            IconButton(enabled = enabled, onClick = player::toggle) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) t("Pause") else t("Play"), tint = colors.ink, modifier = Modifier.size(30.dp))
            }
            IconButton(enabled = enabled, onClick = player::next) { Icon(Icons.Rounded.SkipNext, contentDescription = t("Next"), tint = colors.ink) }
        }
    }
}

@Composable
private fun ListeningTile(size: HomeWidget.Size, enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val history by remember { actions.graph.library.history(5000) }.collectAsStateWithLifecycle(emptyList())
    // This week from Monday, each day of it, and the four weeks before for "usually".
    val (week, usual, days) = remember(history) {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekMs = 7 * 86_400_000L
        var thisWeek = 0L
        var before = 0L
        val perDay = LongArray(7)
        history.forEach { entry ->
            if (entry.playedAt >= start) {
                thisWeek += entry.seconds
                val day = ((entry.playedAt - start) / 86_400_000).toInt()
                if (day in 0..6) perDay[day] += entry.seconds.toLong()
            } else if (entry.playedAt >= start - 4 * weekMs) {
                before += entry.seconds
            }
        }
        Triple(thisWeek, before / 4, perDay.toList())
    }
    Tap(enabled, onLongPress, onClick = { actions.navigate(Routes.STATS) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TileLabel(Icons.Rounded.BarChart, t("This week"))
            Text(
                if (week > 0) Format.duration(week) else t("Nothing yet"),
                style = if (size == HomeWidget.Size.SMALL) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                maxLines = 1,
            )
            Text(if (usual > 0) t("Usually %@", Format.duration(usual)) else t("Your usual shows after a week"), style = MaterialTheme.typography.bodySmall, color = colors.ink3, maxLines = 2)
            if (size != HomeWidget.Size.SMALL) {
                val peak = days.max().coerceAtLeast(1)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(if (size == HomeWidget.Size.LARGE) 150.dp else 44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    days.forEach { seconds ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight((seconds.toFloat() / peak).coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.accent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextMixTile(enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val state by graph.player.state.collectAsStateWithLifecycle()
    val transition by graph.hub.transition.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val next = state.upNext.firstOrNull()?.song
    Tap(enabled, onLongPress, onClick = actions.openPlayer) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TileLabel(Icons.AutoMirrored.Rounded.CallMerge, t("Next mix"))
            if (next == null) {
                Text(t("Nothing up next"), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text(
                    if (settings.karousel) t("Karousel finds more as the track plays.") else t("Queue something, or turn on Karousel."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
                return@Column
            }
            Text(next.title, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(next.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            val position by rememberPosition(1000)
            val plan = transition.upcoming?.takeIf { transition.fromSongId == state.current?.id }
            val rate = plan?.outgoingRate?.takeIf { it > 0 } ?: 1.0
            val fade = if (settings.crossfadeEnabled) settings.crossfadeSeconds else 0.0
            val start = plan?.startAt ?: (state.durationMs / 1000.0 - fade)
            val left = ((start - position / 1000.0) / rate).coerceAtLeast(0.0)
            Text(
                if (state.playWhenReady) "in ${Format.time(left)}" else "paused",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun StationTile(id: String?, size: HomeWidget.Size, enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val tally by graph.stations.tally.collectAsStateWithLifecycle()
    val playingId = graph.hub.station.collectAsStateWithLifecycle().value?.id
    val station = (settings.stations + tally?.decades.orEmpty()).firstOrNull { it.id == id }
    Tap(enabled, onLongPress, onClick = {
        if (station != null) graph.player.station(StationRequest.Play(station)) else actions.navigate(Routes.STATIONS)
    }) {
        Column {
            Icon(
                if (station != null && playingId == station.id) Icons.Rounded.GraphicEq else station?.let { stationIcon(it.icon) } ?: Icons.Rounded.Tune,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(if (size == HomeWidget.Size.SMALL) 28.dp else 40.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(station?.name ?: t("Station gone"), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                station?.let { s -> tally?.counts?.get(s.id)?.let { Format.count(it, "track") } ?: s.summary } ?: t("Tap to choose another"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun CrateTile(id: String?, size: HomeWidget.Size, enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val crate = settings.crates.firstOrNull { it.id == id }
    val covers by produceState(emptyList<String>(), crate, size) {
        if (crate != null && size != HomeWidget.Size.SMALL) value = graph.crates.songs(crate).mapNotNull { it.artworkId }.distinct().take(4)
    }
    Tap(enabled, onLongPress, onClick = {
        crate?.let { c -> actions.launch { graph.crates.songs(c).let { if (it.isEmpty()) actions.openCrate(c.id) else actions.play(it) } } }
    }) {
        Row {
            if (size != HomeWidget.Size.SMALL && covers.isNotEmpty()) {
                Column(Modifier.size(118.dp).clip(RoundedCornerShape(10.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    covers.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { ArtworkFill(it, Modifier.size(58.dp), shape = RoundedCornerShape(0.dp)) }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column {
                TileLabel(Icons.Rounded.Inventory2, t("Crate"))
                Spacer(Modifier.weight(1f))
                Text(crate?.name ?: t("Crate gone"), style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    crate?.let { if (it.byRules) it.rules?.summary.orEmpty() else it.contents } ?: t("It was deleted"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ShortcutTile(words: String, enabled: Boolean, onLongPress: () -> Unit) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val command = remember(words) { Commands.parse(words, actions) }
    val (icon, text) = command?.let(Commands::describe) ?: (Icons.Rounded.Bolt to "“$words”")
    Tap(enabled, onLongPress, onClick = {
        if (command == null) {
            actions.graph.messages.show(t("Kultr doesn't know how to do “%@” any more.", words))
        } else {
            Commands.run(command, actions) { route -> actions.navigate(route) }
        }
    }) {
        Column {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onAccent)
            }
            Spacer(Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------ adding one --

/** A widget's name in a list: its kind, and for a station, crate or shortcut, which one. */
@Composable
fun homeWidgetTitle(widget: HomeWidget): String {
    val actions = LocalActions.current
    val graph = actions.graph
    return when (widget.kind) {
        HomeWidget.Kind.STATION -> t("Station: %@", (graph.settings.current.stations + graph.stations.tally.value?.decades.orEmpty()).firstOrNull { it.id == widget.ref }?.title ?: t("gone"))
        HomeWidget.Kind.CRATE -> t("Crate: %@", graph.settings.current.crates.firstOrNull { it.id == widget.ref }?.name ?: t("gone"))
        HomeWidget.Kind.SHORTCUT -> Commands.parse(widget.ref.orEmpty(), actions)?.let { Commands.describe(it).second } ?: t("Shortcut: “%@”", widget.ref)
        else -> widget.kind.label
    }
}

/** Which widget, how big, and for a station, crate or shortcut, which one. */
@Composable
fun AddWidgetDialog(onAdd: (HomeWidget) -> Unit, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val tally by graph.stations.tally.collectAsStateWithLifecycle()
    val stations = settings.stations + tally?.decades.orEmpty()
    var kind by remember { mutableStateOf(HomeWidget.Kind.NOW_PLAYING) }
    var size by remember { mutableStateOf(HomeWidget.Size.SMALL) }
    var ref by remember { mutableStateOf("") }
    fun defaultRef(of: HomeWidget.Kind) = when (of) {
        HomeWidget.Kind.STATION -> stations.firstOrNull()?.id.orEmpty()
        HomeWidget.Kind.CRATE -> settings.crates.firstOrNull()?.id.orEmpty()
        HomeWidget.Kind.SHORTCUT -> SHORTCUTS.first()
        else -> ""
    }
    val canAdd = when (kind) {
        HomeWidget.Kind.STATION, HomeWidget.Kind.CRATE -> ref.isNotEmpty()
        HomeWidget.Kind.SHORTCUT -> Commands.parse(ref, actions) != null
        else -> true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Add a widget")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(HomeWidget.Kind.entries) { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            kind = option
                            ref = defaultRef(option)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(widgetIcon(option), contentDescription = null, tint = colors.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = colors.ink)
                            Text(option.note, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                        }
                        if (kind == option) Icon(Icons.Rounded.Check, contentDescription = t("Chosen"), tint = colors.accent)
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Segmented(HomeWidget.Size.entries.map { it to it.label }, size, { size = it })
                    Spacer(Modifier.height(8.dp))
                }
                when (kind) {
                    HomeWidget.Kind.STATION -> items(stations, key = { "s:" + it.id }) { station ->
                        ChoiceRow(station.title, ref == station.id) { ref = station.id }
                    }
                    HomeWidget.Kind.CRATE -> {
                        if (settings.crates.isEmpty()) item { Text(t("No crates yet: make one in Library → Crates."), color = colors.ink3) }
                        items(settings.crates, key = { "c:" + it.id }) { crate -> ChoiceRow(crate.name, ref == crate.id) { ref = crate.id } }
                    }
                    HomeWidget.Kind.SHORTCUT -> {
                        items(SHORTCUTS, key = { "w:$it" }) { words ->
                            ChoiceRow(Commands.parse(words, actions)?.let { Commands.describe(it).second } ?: words, ref == words) { ref = words }
                        }
                        item {
                            OutlinedTextField(ref, { ref = it }, label = { Text(t("Or your own words, as in Search")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text(
                                Commands.parse(ref, actions)?.let { t("Does: %@", Commands.describe(it).second) } ?: t("Kultr doesn't understand those words yet."),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink3,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canAdd, onClick = {
                val needsRef = kind in setOf(HomeWidget.Kind.STATION, HomeWidget.Kind.CRATE, HomeWidget.Kind.SHORTCUT)
                onAdd(HomeWidget(kind = kind, size = size, ref = if (needsRef) ref else null))
            }) { Text(t("Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun ChoiceRow(text: String, chosen: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Kultr.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (chosen) Icon(Icons.Rounded.Check, contentDescription = t("Chosen"), tint = Kultr.colors.accent)
    }
}

/** Settings → Home page: the widgets, their sizes and order, and the switch for all of them. */
@Composable
fun HomeWidgetSettings() {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    fun change(transform: (List<HomeWidget>) -> List<HomeWidget>) = graph.settings.update { it.copy(homeWidgets = transform(it.homeWidgets)) }
    SettingRow(
        t("Widgets on Home"),
        hint = t("At the top of Home: hold one there for its size, to move it or take it off."),
        onClick = { graph.settings.update { it.copy(showHomeWidgets = !it.showHomeWidgets) } },
    ) { Switch(settings.showHomeWidgets, { on -> graph.settings.update { it.copy(showHomeWidgets = on) } }) }
    if (!settings.showHomeWidgets) return
    settings.homeWidgets.forEachIndexed { index, widget ->
        var sizeMenu by remember(widget.id) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(widgetIcon(widget.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(homeWidgetTitle(widget), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { sizeMenu = true }) { Text(widget.size.label) }
                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                    HomeWidget.Size.entries.forEach { size ->
                        DropdownMenuItem(text = { Text(size.label) }, onClick = {
                            sizeMenu = false
                            change { list -> list.map { if (it.id == widget.id) it.copy(size = size) else it } }
                        })
                    }
                }
            }
            IconButton(enabled = index > 0, onClick = { change { it.toMutableList().apply { add(index - 1, removeAt(index)) } } }) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = t("Move up"))
            }
            IconButton(onClick = { change { list -> list.filterNot { it.id == widget.id } } }) {
                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = t("Remove"), tint = colors.danger)
            }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Add a widget"), icon = Icons.Rounded.Add, onClick = { adding = true })
        if (settings.homeWidgets != DEFAULT_HOME_WIDGETS) Pill(t("Back to the starting widgets"), onClick = { change { DEFAULT_HOME_WIDGETS } })
    }
    if (adding) {
        AddWidgetDialog(onAdd = { widget ->
            change { it + widget }
            adding = false
        }, onDismiss = { adding = false })
    }
}
