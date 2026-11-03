package app.kultr.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.playback.StationRequest
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.LocalReduceMotion
import app.kultr.core.karousel.Bounds
import app.kultr.core.karousel.Station
import app.kultr.core.settings.tagCounts
import app.kultr.core.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Year
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------- icons --

/** The icons a station can have, by the name Kultr for iOS gives each (an SF Symbol), so they match across apps. */
val STATION_ICONS: Map<String, ImageVector> = linkedMapOf(
    "dot.radiowaves.left.and.right" to Icons.Rounded.Radio,
    "sunrise.fill" to Icons.Rounded.WbTwilight,
    "car.fill" to Icons.Rounded.DirectionsCar,
    "flame.fill" to Icons.Rounded.LocalFireDepartment,
    "brain.head.profile" to Icons.Rounded.Psychology,
    "moon.stars.fill" to Icons.Rounded.NightsStay,
    "sparkle" to Icons.Rounded.AutoAwesome,
    "heart.fill" to Icons.Rounded.Favorite,
    "figure.run" to Icons.AutoMirrored.Rounded.DirectionsRun,
    "cup.and.saucer.fill" to Icons.Rounded.LocalCafe,
    "leaf.fill" to Icons.Rounded.Eco,
    "bolt.fill" to Icons.Rounded.Bolt,
    "headphones" to Icons.Rounded.Headphones,
    "guitars.fill" to Icons.Rounded.LibraryMusic,
    "pianokeys" to Icons.Rounded.Piano,
    "music.mic" to Icons.Rounded.Mic,
    "sun.max.fill" to Icons.Rounded.WbSunny,
    "cloud.rain.fill" to Icons.Rounded.Umbrella,
    "snowflake" to Icons.Rounded.AcUnit,
    "party.popper.fill" to Icons.Rounded.Celebration,
)

/** The icon for [name]; decades and crates have their own, anything unknown is a radio. */
fun stationIcon(name: String): ImageVector = when (name) {
    "calendar" -> Icons.Rounded.CalendarMonth
    "shippingbox.fill" -> Icons.Rounded.Inventory2
    "tag.fill" -> Icons.Rounded.Sell
    else -> STATION_ICONS[name] ?: Icons.Rounded.Radio
}

private val CAMELOT_KEYS: List<String> = (1..12).flatMap { listOf("${it}A", "${it}B") }

/** A track count, or what is missing while there is none. */
private fun countLine(count: Int?): String = when {
    count == null -> "Counting tracks…"
    count > 0 -> "${Format.count(count, "track")} fit"
    else -> "No tracks fit yet"
}

// --------------------------------------------------------------- screen --

/**
 * Stations: endless mixes from your own library. Turn the dial to choose one;
 * it ticks at each station. While a station plays, turning the dial mixes
 * into the new one. Below it, your stations to edit, and one per decade.
 */
@Composable
fun StationsScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val tally by graph.stations.tally.collectAsStateWithLifecycle()
    val playing by graph.hub.station.collectAsStateWithLifecycle()
    val stations = settings.stations + tally?.decades.orEmpty()
    var selected by rememberSaveable { mutableIntStateOf(stations.indexOfFirst { it.id == playing?.id }.coerceAtLeast(0)) }
    val current = stations.getOrNull(selected) ?: stations.firstOrNull()
    var editing by remember { mutableStateOf<StationDraft?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // The dial's position the last time it settled, so a station playing only switches on a real turn.
    var settledId by remember { mutableStateOf(current?.id) }
    fun count(station: Station) = tally?.counts?.get(station.id)
    fun select(station: Station) {
        stations.indexOfFirst { it.id == station.id }.takeIf { it >= 0 }?.let { selected = it }
    }

    // Turning the dial while a station plays mixes into the new one, once it has settled.
    LaunchedEffect(current?.id) {
        if (current == null || current.id == settledId) return@LaunchedEffect
        delay(900)
        settledId = current.id
        val now = graph.hub.station.value
        if (now != null && now.id != current.id && (count(current) ?: 1) > 0) graph.player.station(StationRequest.Switch(current))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(16.dp))) {
        item {
            Row(Modifier.statusBarsPadding().padding(4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = colors.accent) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Back to the starting stations") },
                            leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                            onClick = {
                                menu = false
                                resetting = true
                            },
                        )
                    }
                }
            }
            Text("Stations", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
        if (stations.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StationDial(stations, selected, onSelect = { selected = it }, modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 32.dp))
                }
            }
        }
        if (current != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.summary, style = MaterialTheme.typography.bodyMedium, color = colors.ink2, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(countLine(count(current)), style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                }
                Box(Modifier.padding(horizontal = 16.dp)) {
                    when {
                        playing?.id == current.id -> WideButton(
                            "End ${current.name}",
                            Icons.Rounded.Stop,
                            onClick = {
                                graph.player.station(StationRequest.End)
                                graph.messages.show("Station ended")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> WideButton(
                            if (playing == null) "Play ${current.name}" else "Switch to ${current.name}",
                            if (playing == null) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.CallMerge,
                            onClick = {
                                settledId = current.id
                                graph.player.station(if (playing == null) StationRequest.Play(current) else StationRequest.Switch(current))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            accent = true,
                            enabled = (count(current) ?: 1) > 0,
                        )
                    }
                }
                if (current.needsTempo) AnalysisNote(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            }
        }
        item { SectionHeader("Your stations", modifier = Modifier.padding(top = 16.dp)) }
        items(settings.stations, key = { "mine:" + it.id }) { station ->
            StationRow(
                station = station,
                count = count(station),
                selected = current?.id == station.id,
                playing = playing?.id == station.id,
                onSelect = { select(station) },
                onEdit = { editing = StationDraft(station, isNew = false) },
                onCopy = { editing = StationDraft(station.copyToEdit(), isNew = true) },
                onDelete = { graph.stations.delete(station) },
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Pill("New station", icon = Icons.Rounded.Add, accent = true, onClick = {
                    editing = StationDraft(Station(name = "New station", bpm = Bounds(100.0, 120.0)), isNew = true)
                })
            }
        }
        val decades = tally?.decades.orEmpty()
        if (decades.isNotEmpty()) {
            item { SectionHeader("Decades") }
            items(decades, key = { "decade:" + it.id }) { station ->
                StationRow(
                    station = station,
                    count = count(station),
                    selected = current?.id == station.id,
                    playing = playing?.id == station.id,
                    onSelect = { select(station) },
                    onEdit = null,
                    onCopy = { editing = StationDraft(station.copyToEdit(), isNew = true) },
                    onDelete = null,
                )
            }
            item {
                Text(
                    "A station for every decade with at least 20 tracks, from the years in your tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    editing?.let { draft ->
        StationEditor(
            draft,
            onSave = { saved ->
                graph.stations.save(saved)
                editing = null
                settledId = saved.id
                (graph.settings.current.stations + tally?.decades.orEmpty()).indexOfFirst { it.id == saved.id }.takeIf { it >= 0 }?.let { selected = it }
            },
            onCancel = { editing = null },
        )
    }
    if (resetting) {
        ConfirmDialog(
            title = "Back to the starting stations?",
            body = "Your own stations and changes go; Warm Up, Night Drive, Peak Hour, Deep Focus, Wind Down, Unplayed gems and Favourites come back.",
            confirm = "Reset",
            onConfirm = { graph.stations.resetToPresets() },
            onDismiss = { resetting = false },
        )
    }
}

private fun Station.copyToEdit() = copy(id = UUID.randomUUID().toString(), name = "$name (copy)")

/** Some stations need InjeKt's analysis; say how far along it is, with a way to finish it. */
@Composable
fun AnalysisNote(modifier: Modifier = Modifier) {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val analysed by graph.analysis.analysedCount.collectAsStateWithLifecycle(0)
    val missing by graph.analysis.missingCount.collectAsStateWithLifecycle(0)
    val progress by graph.analysis.bulkProgress.collectAsStateWithLifecycle()
    if (missing == 0) return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.sand).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Tempo, energy and key come from InjeKt's analysis, which has covered ${Format.count(analysed, "track")} so far, " +
                    "with ${Format.count(missing, "track")} to go. This station grows as more are analysed.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
            val bulk = progress
            if (bulk != null) {
                Text("Analysing ${bulk.done + 1} of ${bulk.total}…", style = MaterialTheme.typography.bodySmall, color = colors.ink3)
            } else {
                Pill("Analyse the rest", icon = Icons.Rounded.AutoAwesome, onClick = { graph.analysis.startBulk() })
            }
        }
    }
}

// ----------------------------------------------------------------- dial --

/**
 * A ring with a station at each stop. Drag around it to turn; it ticks as
 * each station passes the pointer at the top and settles on the nearest one
 * when you let go.
 */
@Composable
private fun StationDial(stations: List<Station>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val step = 2 * PI / stations.size.coerceAtLeast(1)
    // Radians; station i sits under the pointer at -i × step.
    val angle = remember { Animatable((-selected * step).toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    fun nearest(value: Float): Int {
        val n = stations.size
        val raw = (-value / step).roundToInt()
        return ((raw % n) + n) % n
    }

    /** The angle that puts [index] under the pointer, the shortest way round from here. */
    fun target(index: Int): Float {
        val base = -index * step
        val turns = ((angle.value - base) / (2 * PI)).roundToInt()
        return (base + turns * 2 * PI).toFloat()
    }

    // Chosen from the list, or the stations changed: turn there.
    LaunchedEffect(selected, stations.size) {
        if (dragging) return@LaunchedEffect
        if (reduceMotion) angle.snapTo(target(selected)) else angle.animateTo(target(selected), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Station dial"
                stateDescription = stations.getOrNull(selected)?.name.orEmpty()
            },
    ) {
        val side = maxWidth
        val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.sand)
                .border(1.dp, colors.edge, CircleShape)
                .pointerInput(stations.size) {
                    var last: Float? = null
                    var turned = 0f
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            turned = angle.value
                        },
                        onDragEnd = {
                            dragging = false
                            last = null
                            scope.launch { angle.animateTo(target(nearest(angle.value)), spring(dampingRatio = 0.8f)) }
                        },
                        onDragCancel = {
                            dragging = false
                            last = null
                        },
                    ) { change, _ ->
                        val middle = Offset(size.width / 2f, size.height / 2f)
                        val touch = atan2(change.position.y - middle.y, change.position.x - middle.x)
                        last?.let { previous ->
                            var delta = touch - previous
                            if (delta > PI) delta -= (2 * PI).toFloat()
                            if (delta < -PI) delta += (2 * PI).toFloat()
                            turned += delta
                            val to = turned
                            scope.launch { angle.snapTo(to) }
                        }
                        last = touch
                        val index = nearest(turned)
                        if (index != currentSelected) {
                            select(index)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                },
        )
        // The hole in the middle.
        Box(Modifier.size(side * 0.62f).align(Alignment.Center).clip(CircleShape).background(colors.background))
        val iconRadius = constraints.maxWidth * 0.405f
        stations.forEachIndexed { index, station ->
            val a = angle.value + index * step - PI / 2
            val on = index == selected
            val iconSize = side * (if (on) 0.08f else 0.065f)
            Icon(
                stationIcon(station.icon),
                contentDescription = null,
                tint = if (on) colors.accent else colors.ink3,
                modifier = Modifier
                    .offset {
                        val half = iconSize.roundToPx() / 2
                        IntOffset((center.x + cos(a) * iconRadius).roundToInt() - half, (center.y + sin(a) * iconRadius).roundToInt() - half)
                    }
                    .size(iconSize),
            )
        }
        // The pointer at the top.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = side * 0.01f)
                .size(4.dp, side * 0.06f)
                .clip(RoundedCornerShape(50))
                .background(colors.accent),
        )
        stations.getOrNull(selected)?.let { station ->
            Column(Modifier.align(Alignment.Center).width(side * 0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(stationIcon(station.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(side * 0.09f))
                Spacer(Modifier.height(6.dp))
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ----------------------------------------------------------------- rows --

@Composable
private fun StationRow(
    station: Station,
    count: Int?,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = Kultr.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) colors.accentSoft else colors.sand),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.GraphicEq else stationIcon(station.icon), contentDescription = null, tint = colors.accent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(station.summary, count?.let { Format.count(it, "track") }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More for ${station.name}", tint = colors.accent) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onEdit != null) {
                    DropdownMenuItem(text = { Text("Edit…") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) }, onClick = {
                        menu = false
                        onEdit()
                    })
                }
                DropdownMenuItem(
                    text = { Text(if (onEdit == null) "Make a copy to edit…" else "Duplicate…") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                    onClick = {
                        menu = false
                        onCopy()
                    },
                )
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** A station on the Home shelf: tap to play it. */
@Composable
fun MixStationCard(station: Station, count: Int?, modifier: Modifier = Modifier) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    val playing = actions.graph.hub.station.collectAsStateWithLifecycle().value?.id == station.id
    Column(
        modifier
            .padding(4.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(Kultr.radii.md))
            .background(if (playing) colors.accentSoft else colors.sand)
            .clickable { actions.graph.player.station(StationRequest.Play(station)) }
            .padding(12.dp),
    ) {
        Icon(if (playing) Icons.Rounded.GraphicEq else stationIcon(station.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.weight(1f))
        Text(station.name, style = MaterialTheme.typography.titleMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                count == null -> station.summary
                count > 0 -> Format.count(count, "track")
                station.needsTempo -> "Needs InjeKt analysis"
                else -> "No tracks yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --------------------------------------------------------------- editor --

/** A station (or a crate's rules) being edited. */
data class StationDraft(val station: Station, val isNew: Boolean, val noun: String = "station")

/** Every rule of a station (or a crate), with how many tracks fit as you change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationEditor(draft: StationDraft, onSave: (Station) -> Unit, onCancel: () -> Unit) {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    var station by remember(draft) { mutableStateOf(draft.station) }
    val count by produceState<Int?>(null, station) {
        value = null
        delay(300)
        value = graph.stations.count(station)
    }
    val genres by remember { graph.library.genres() }.collectAsStateWithLifecycle(emptyList())
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Text(
                    if (draft.isNew) "New ${draft.noun}" else "Edit ${draft.noun}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = station.name.isNotBlank(), onClick = { onSave(station.copy(name = station.name.trim())) }) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(station.name, { station = station.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(STATION_ICONS.keys.toList()) { name ->
                        val on = station.icon == name
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (on) colors.accent else colors.sand).clickable { station = station.copy(icon = name) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(stationIcon(name), contentDescription = name, tint = if (on) colors.onAccent else colors.accent) }
                    }
                }

                EditorSection("Tempo", "From InjeKt's analysis, or the tempo in the track's tags.") {
                    BoundsEditor("tempo", station.bpm, 50.0..200.0, 2.0, Bounds(100.0, 120.0), { "${it.roundToInt()} BPM" }) { station = station.copy(bpm = it) }
                }
                EditorSection("Energy", "1 is a quiet acoustic track, 10 a loud club track. Needs InjeKt's analysis.") {
                    // 0 to 1 inside, 1 to 10 on screen.
                    val shown = station.energy?.let { Bounds(it.min?.times(10)?.roundToInt()?.toDouble(), it.max?.times(10)?.roundToInt()?.toDouble()) }
                    BoundsEditor("energy", shown, 1.0..10.0, 1.0, Bounds(3.0, 7.0), { "${it.roundToInt()}" }) { picked ->
                        station = station.copy(energy = picked?.let { Bounds(it.min?.div(10), it.max?.div(10)) })
                    }
                }
                EditorSection("Key", "Camelot keys: neighbours on the wheel mix well. Needs InjeKt's analysis.") {
                    val keys = station.keys.orEmpty()
                    SwitchLine("Any key", keys.isEmpty()) { station = station.copy(keys = if (it) null else listOf("8A")) }
                    if (keys.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 6) {
                            CAMELOT_KEYS.forEach { key ->
                                val on = key in keys
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (on) colors.accent else colors.sand)
                                        .clickable { station = station.copy(keys = (if (on) keys - key else keys + key).ifEmpty { null }) },
                                    contentAlignment = Alignment.Center,
                                ) { Text(key, style = MaterialTheme.typography.labelMedium, color = if (on) colors.onAccent else colors.ink2) }
                            }
                        }
                    }
                }
                EditorSection("Genres and tags", "Tags are the ones you give albums and artists on their pages.") {
                    ChoiceLine("Genres", station.genres.orEmpty()) { choosing = "genres" }
                    ChoiceLine("Tags", station.tags.orEmpty()) { choosing = "tags" }
                    Spacer(Modifier.height(8.dp))
                    Text("Added", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    Spacer(Modifier.height(6.dp))
                    Segmented(
                        listOf(0 to "Any time", 30 to "30 days", 90 to "90 days", 365 to "This year"),
                        station.addedDays ?: 0,
                        { station = station.copy(addedDays = it.takeIf { days -> days > 0 }) },
                        Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
                EditorSection("Years", null) {
                    BoundsEditor("years", station.years, 1950.0..Year.now().value.toDouble(), 1.0, Bounds(2000.0, 2009.0), { "${it.toInt()}" }) {
                        station = station.copy(years = it)
                    }
                }
                EditorSection("Plays and ratings", null) {
                    SwitchLine("Only tracks never played", station.unplayed) { station = station.copy(unplayed = it) }
                    SwitchLine("Only favourites", station.favourites) { station = station.copy(favourites = it) }
                    Spacer(Modifier.height(8.dp))
                    Text("Rating", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                    Spacer(Modifier.height(6.dp))
                    Segmented(
                        listOf(0 to "Any") + (1..5).map { it to "$it★+" },
                        station.minRating ?: 0,
                        { station = station.copy(minRating = it.takeIf { stars -> stars > 0 }) },
                        Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    when (val n = count) {
                        null -> "Counting…"
                        0 -> "No tracks fit these rules yet."
                        else -> "${Format.count(n, "track")} fit these rules."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    when (choosing) {
        "genres" -> ChoicePicker("Genres", "No genres in your library yet.", genres.map { it.value }.sorted(), station.genres.orEmpty(), onDone = {
            station = station.copy(genres = it.ifEmpty { null })
            choosing = null
        })
        "tags" -> ChoicePicker(
            "Tags",
            "No tags yet. Give an album or an artist a tag on its page.",
            tagCounts(settings.annotations).map { it.first },
            station.tags.orEmpty(),
            onDone = {
                station = station.copy(tags = it.ifEmpty { null })
                choosing = null
            },
        )
    }
}

@Composable
private fun EditorSection(title: String, footer: String?, content: @Composable () -> Unit) {
    val colors = Kultr.colors
    Spacer(Modifier.height(20.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.ink3)
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Kultr.radii.md)).background(colors.elevated).padding(horizontal = 14.dp, vertical = 8.dp)) {
        content()
    }
    if (footer != null) {
        Spacer(Modifier.height(6.dp))
        Text(footer, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceLine(label: String, chosen: List<String>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink)
        Spacer(Modifier.width(16.dp))
        Text(
            chosen.ifEmpty { listOf("Any") }.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = Kultr.colors.ink3,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Any", or a from and a to. */
@Composable
private fun BoundsEditor(
    noun: String,
    bounds: Bounds?,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    initial: Bounds,
    text: (Double) -> String,
    onChange: (Bounds?) -> Unit,
) {
    val open = bounds?.isOpen ?: true
    SwitchLine("Any $noun", open) { onChange(if (it) null else initial) }
    if (open) return
    val low = bounds?.min ?: range.start
    val high = bounds?.max ?: range.endInclusive
    Stepper("From ${text(low)}", onMinus = { onChange(Bounds((low - step).coerceIn(range), high)) }, onPlus = {
        val next = (low + step).coerceIn(range)
        onChange(Bounds(next, maxOf(next, high)))
    })
    Stepper("To ${text(high)}", onMinus = {
        val next = (high - step).coerceIn(range)
        onChange(Bounds(minOf(next, low), next))
    }, onPlus = { onChange(Bounds(low, (high + step).coerceIn(range))) })
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = Kultr.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.ink, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(50)).background(colors.sand)) {
            IconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, contentDescription = "Less", tint = colors.accent) }
            IconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, contentDescription = "More", tint = colors.accent) }
        }
    }
}

/** Pick any number from a list, with a filter field: genres, tags. */
@Composable
private fun ChoicePicker(title: String, empty: String, all: List<String>, initial: List<String>, onDone: (List<String>) -> Unit) {
    val colors = Kultr.colors
    var chosen by remember { mutableStateOf(initial) }
    var filter by remember { mutableStateOf("") }
    val shown = if (filter.isBlank()) all else all.filter { it.contains(filter.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = { onDone(chosen) },
        title = { Text(title) },
        text = {
            Column {
                if (all.isEmpty()) {
                    Text(empty, color = colors.ink3)
                } else {
                    OutlinedTextField(filter, { filter = it }, placeholder = { Text("Filter ${title.lowercase()}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it }) { name ->
                            val on = name in chosen
                            Row(
                                Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - name else chosen + name }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, color = colors.ink, modifier = Modifier.weight(1f))
                                if (on) Icon(Icons.Rounded.Check, contentDescription = "Chosen", tint = colors.accent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text("Done") } },
        dismissButton = { if (chosen.isNotEmpty()) TextButton(onClick = { chosen = emptyList() }) { Text("Clear") } },
    )
}
