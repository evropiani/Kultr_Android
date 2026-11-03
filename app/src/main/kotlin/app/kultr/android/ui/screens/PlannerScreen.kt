package app.kultr.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.SongRow
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.injekt.EnergyCurve
import app.kultr.core.injekt.PlannedTrack
import app.kultr.core.injekt.planSet
import app.kultr.core.karousel.Station
import app.kultr.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val POINTS = 25
private val LENGTHS = listOf(30, 60, 90, 120)

/**
 * The energy planner: draw how the next stretch should feel, low to high,
 * and Kultr builds a set from InjeKt's analysis that follows the line, with
 * tempos and keys that mix into each other. Play it, or keep it as a playlist.
 */
@Composable
fun PlannerScreen(initialMinutes: Int? = null) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val scope = rememberCoroutineScope()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val tally by graph.stations.tally.collectAsStateWithLifecycle()
    val analysed by graph.analysis.analysedCount.collectAsStateWithLifecycle(0)
    val player by graph.player.state.collectAsStateWithLifecycle()
    var curve by remember { mutableStateOf(EnergyCurve.WARM_UP_AND_PEAK.resampled(POINTS)) }
    var minutes by remember { mutableIntStateOf(initialMinutes?.let { m -> LENGTHS.minBy { abs(it - m) } } ?: 60) }
    var source by remember { mutableStateOf<Station?>(null) }
    var plan by remember { mutableStateOf(emptyList<PlannedTrack>()) }
    var building by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun build() = scope.launch {
        building = true
        val pool = graph.stations.pool(source ?: Station(name = "All")).mapNotNull { (song, analysis) -> analysis?.let { song to it } }
        if (pool.size < 5) {
            graph.messages.show("Not enough analysed tracks for a set yet. InjeKt has to analyse a few more first.")
        } else {
            val result = withContext(Dispatchers.Default) { planSet(curve, minutes, pool) }
            plan = result
            if (result.isEmpty()) graph.messages.show("Nothing fits that shape yet.")
        }
        building = false
    }

    val songs = plan.map { it.song }
    val length = plan.lastOrNull()?.let { it.startsAt + (it.song.duration ?: 0) }?.toLong() ?: 0L
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(24.dp))) {
        item {
            BackBar()
            Text("Energy planner", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
            Text(
                "Draw the shape of the set with your finger: low is calm, high is peak.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            EnergyCanvas(curve, minutes, plan, onChange = { curve = it }, modifier = Modifier.padding(16.dp).fillMaxWidth().height(220.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Warm up and peak" to EnergyCurve.WARM_UP_AND_PEAK,
                    "Steady" to EnergyCurve.STEADY,
                    "Build" to EnergyCurve.BUILD,
                    "Wind down" to EnergyCurve.WIND_DOWN,
                ).forEach { (label, shape) ->
                    Pill(label, onClick = {
                        curve = shape.resampled(POINTS)
                        plan = emptyList()
                    })
                }
            }
            Segmented(
                LENGTHS.map { it to if (it < 60) "$it min" else if (it == 60) "1 hour" else if (it % 60 == 0) "${it / 60} hours" else "$it min" },
                minutes,
                { minutes = it },
                Modifier.padding(16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("From", style = MaterialTheme.typography.bodyLarge, color = colors.ink, modifier = Modifier.weight(1f))
                Box {
                    Pill(source?.name ?: "Your whole library", icon = Icons.Rounded.UnfoldMore, onClick = { choosing = true })
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        DropdownMenuItem(text = { Text("Your whole library") }, onClick = {
                            source = null
                            choosing = false
                        })
                        (settings.stations + tally?.decades.orEmpty()).forEach { station ->
                            DropdownMenuItem(text = { Text(station.name) }, onClick = {
                                source = station
                                choosing = false
                            })
                        }
                    }
                }
            }
            WideButton(
                when {
                    building -> "Building…"
                    plan.isEmpty() -> "Build the set"
                    else -> "Build it again"
                },
                Icons.Rounded.ShowChart,
                onClick = { build() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                accent = plan.isEmpty(),
                enabled = !building,
            )
            if (analysed < 30) AnalysisNote(Modifier.padding(horizontal = 16.dp))
        }
        if (plan.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WideButton("Play the set", Icons.Rounded.PlayArrow, onClick = {
                        actions.play(songs)
                        graph.messages.show("Playing your set: ${Format.count(songs.size, "track")}")
                    }, modifier = Modifier.weight(1f), accent = true)
                    WideButton("Save", Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = { saving = true }, modifier = Modifier.weight(1f))
                }
                SectionHeader("${Format.count(plan.size, "track")} · ${Format.duration(length)}", modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(plan, key = { _, track -> track.song.id }) { index, track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.time(track.startsAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(start = 12.dp).width(40.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        SongRow(track.song, onClick = { actions.play(songs, index) }, isCurrent = track.song.id == player.current?.id, compact = true)
                    }
                    EnergyBadge(track.energy)
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
    if (saving) {
        TextInputDialog("Save the set", "Set, ${Format.duration(length)}", "Save", onConfirm = { name ->
            actions.launch {
                val error = graph.library.createPlaylist(name.trim(), songs)
                if (error != null) graph.messages.error(error) else graph.messages.show("Saved “${name.trim()}”")
            }
        }, onDismiss = { saving = false })
    }
}

/** The drawing: a grid, the line you drew, filled beneath, and the planned tracks as dots. */
@Composable
private fun EnergyCanvas(curve: EnergyCurve, minutes: Int, plan: List<PlannedTrack>, onChange: (EnergyCurve) -> Unit, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val current by rememberUpdatedState(curve)
    val change by rememberUpdatedState(onChange)
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sand.copy(alpha = colors.sand.alpha * 0.5f))
            .semantics {
                contentDescription = "Energy over the set"
                stateDescription = "Starts at ${(curve.points.first() * 10).roundToInt()}, peaks at ${(curve.points.max() * 10).roundToInt()}, " +
                    "ends at ${(curve.points.last() * 10).roundToInt()}, out of 10"
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    /** Sets the point under [at]; gives back its index, so the next one can fill the gap. */
                    fun draw(at: Offset, last: Int?): Int {
                        val points = current.points.toMutableList()
                        val count = points.size
                        val index = ((at.x / size.width.coerceAtLeast(1)) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        val value = 1 - (at.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f).toDouble()
                        // Fill the gap from the last point drawn, so a fast stroke leaves no holes.
                        if (last != null && last != index) {
                            val start = points[last]
                            val step = if (index > last) 1 else -1
                            var i = last + step
                            while (i != index) {
                                points[i] = start + (value - start) * (i - last).toDouble() / (index - last)
                                i += step
                            }
                        }
                        points[index] = value
                        change(EnergyCurve(points))
                        return index
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var last = draw(down.position, null)
                        while (true) {
                            val pointer = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!pointer.pressed) break
                            last = draw(pointer.position, last)
                            pointer.consume()
                        }
                    }
                },
        ) {
            val grid = colors.ink4.copy(alpha = colors.ink4.alpha * 0.6f)
            for (step in 1 until 5) {
                val y = size.height * step / 5
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (step in 1 until 4) {
                val x = size.width * step / 4
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            val count = curve.points.size
            fun point(i: Int) = Offset(size.width * i / (count - 1), size.height * (1 - curve.points[i].toFloat()))
            val line = Path().apply {
                moveTo(point(0).x, point(0).y)
                for (i in 1 until count) lineTo(point(i).x, point(i).y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, colors.accent.copy(alpha = 0.14f))
            drawPath(line, colors.accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // What the set actually does.
            val total = minutes * 60.0
            plan.filter { it.startsAt <= total }.forEach { track ->
                drawCircle(colors.ink, radius = 4.5.dp.toPx(), center = Offset((size.width * track.startsAt / total).toFloat(), size.height * (1 - track.energy.toFloat())))
            }
        }
        Text("Peak", style = MaterialTheme.typography.labelSmall, color = colors.ink3, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        Text("Calm", style = MaterialTheme.typography.labelSmall, color = colors.ink3, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Text(
            if (minutes >= 60) "${minutes / 60} h" + (if (minutes % 60 == 0) "" else " ${minutes % 60} min") else "$minutes min",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

/** A track's energy, 1 to 10, as a small accent number. */
@Composable
private fun EnergyBadge(energy: Double) {
    val colors = Kultr.colors
    val value = (energy * 10).roundToInt().coerceAtLeast(1)
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(colors.accentSoft).semantics { contentDescription = "Energy $value of 10" },
        contentAlignment = Alignment.Center,
    ) { Text("$value", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.accent) }
}
