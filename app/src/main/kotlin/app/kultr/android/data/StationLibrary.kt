package app.kultr.android.data

import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.karousel.STATION_PRESETS
import app.kultr.core.karousel.Station
import app.kultr.core.karousel.addedSinceDay
import app.kultr.core.karousel.decadeStations
import app.kultr.core.settings.taggedIds
import app.kultr.core.util.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Decades from the library, and how many tracks fit each station, by id. */
data class StationTally(val decades: List<Station> = emptyList(), val counts: Map<String, Int> = emptyMap())

/**
 * Stations: yours (from Settings), plus one per decade the library has enough
 * music from, and the tracks that fit each. Matching reads the whole library
 * and every analysis, so it runs off the main thread and only again when the
 * library, the analysis or the stations change.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StationLibrary(private val graph: AppGraph) {
    /** Null while counting for the first time. */
    val tally: StateFlow<StationTally?> = combine(
        graph.library.counts,
        graph.analysis.analysedCount,
        graph.sync.listeningVersion,
        graph.settings.settings.map { it.stations to it.annotations }.distinctUntilChanged(),
    ) { _, _, _, mine -> mine }
        .debounce(400)
        .mapLatest { (stations, annotations) ->
            val songs = graph.library.allSongs()
            val analyses = graph.analysis.cachedAll()
            val decades = decadeStations(songs.mapNotNull { it.year })
            val counts = (stations + decades).associate { station ->
                val tagged = taggedIds(annotations, station.tags.orEmpty())
                val since = addedSinceDay(station.addedDays)
                station.id to songs.count { station.matches(it, analyses[it.id], tagged, since) }
            }
            StationTally(decades, counts)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(graph.scope, SharingStarted.WhileSubscribed(10_000), null)

    /** Every station, yours first, in dial order. */
    fun all(tally: StationTally?): List<Station> = graph.settings.current.stations + tally?.decades.orEmpty()

    /** How many tracks fit [station] right now, for the editor. */
    suspend fun count(station: Station): Int = pool(station, playableOnly = false).size

    /** The tracks that fit [station], each with InjeKt's analysis if there is one; offline, only downloaded ones. */
    suspend fun pool(station: Station, playableOnly: Boolean = true): List<Pair<Song, TrackAnalysis?>> {
        val annotations = graph.settings.current.annotations
        val tagged = taggedIds(annotations, station.tags.orEmpty())
        val since = addedSinceDay(station.addedDays)
        val analyses = graph.analysis.cachedAll()
        val pool = graph.library.allSongs().mapNotNull { song ->
            val analysis = analyses[song.id]
            if (station.matches(song, analysis, tagged, since)) song to analysis else null
        }
        if (!playableOnly || graph.isLocal || graph.network.isOnline) return pool
        val downloaded = graph.offline.downloadedIds.value
        return pool.filter { it.first.id in downloaded }
    }

    // ------------------------------------------------------------- editing --

    fun save(station: Station) = graph.settings.update { s ->
        val at = s.stations.indexOfFirst { it.id == station.id }
        s.copy(stations = if (at >= 0) s.stations.toMutableList().also { it[at] = station } else s.stations + station)
    }

    fun delete(station: Station) = graph.settings.update { s -> s.copy(stations = s.stations.filterNot { it.id == station.id }) }

    fun resetToPresets() = graph.settings.update { it.copy(stations = STATION_PRESETS) }

    companion object {
        fun emptyMessage(station: Station): String =
            if (station.needsAnalysis) {
                t("Nothing fits “%@” yet. It needs InjeKt's analysis: open Sync and tap Analyse missing.", station.title)
            } else {
                t("Nothing in your library fits “%@”.", station.title)
            }
    }
}
