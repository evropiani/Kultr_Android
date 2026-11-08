package app.kultr.android.playback

import app.kultr.android.AppGraph
import app.kultr.android.data.StationLibrary
import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.engine.PlaybackEngine
import app.kultr.core.engine.RepeatMode
import app.kultr.core.karousel.Station
import app.kultr.core.karousel.stationPicks
import app.kultr.core.util.t

/**
 * A station in the playback service: a track that fits, then an endless mix
 * that keeps to the station's rules, picked by how well each track mixes out
 * of the one before. Runs on the service's main thread, like the engine.
 */
class StationPlayer(private val graph: AppGraph, private val engine: PlaybackEngine, private val start: () -> Unit) {
    /** The tracks that fit the station playing, read when it started. */
    @Volatile private var pool: List<Pair<Song, TrackAnalysis?>> = emptyList()

    val station: Station? get() = graph.hub.station.value

    suspend fun handle(request: StationRequest) = when (request) {
        is StationRequest.Play -> play(request.station)
        is StationRequest.Switch -> switch(request.station)
        StationRequest.End -> end()
    }

    private suspend fun play(station: Station) {
        val found = graph.stations.pool(station)
        val first = found.filter { it.second != null }.randomOrNull() ?: found.randomOrNull()
        if (first == null) {
            graph.messages.show(StationLibrary.emptyMessage(station))
            return
        }
        pool = found
        val picks = next(first.first, setOf(first.first.id), 4, first.second)
        if (engine.shuffle) engine.setShuffle(false)
        if (engine.repeat != RepeatMode.OFF) engine.setRepeat(RepeatMode.OFF)
        engine.setQueue(engine.newItems(listOf(first.first) + picks, karousel = true), 0, 0)
        graph.hub.setStation(station)
        start()
        graph.messages.show(t("Station: %@", station.title))
    }

    /** Turn to [station] while one plays: what the old one lined up goes, the new one's picks come next, mixed into right away. */
    private suspend fun switch(station: Station) {
        val current = engine.currentItem
        if (this.station == null || current == null) return play(station)
        val found = graph.stations.pool(station)
        if (found.isEmpty()) {
            graph.messages.show(StationLibrary.emptyMessage(station))
            return
        }
        graph.hub.setStation(station)
        pool = found
        val picks = next(current.song, setOf(current.song.id), 6)
        if (picks.isEmpty() || this.station != station) return
        // Karousel's upcoming songs belong to the old station; what you queued yourself stays.
        for (at in engine.queue.indices.reversed()) {
            if (at > engine.index && engine.queue[at].karousel) engine.removeRange(at, at + 1)
        }
        engine.addItems(engine.index + 1, engine.newItems(picks, karousel = true))
        engine.mixNow()
        graph.messages.show(t("Station: %@", station.title))
    }

    /** End the station; the music plays on to the end of the queue as usual. */
    fun end() {
        if (station == null) return
        graph.hub.setStation(null)
        pool = emptyList()
        if (!graph.settings.current.karousel) engine.onKarouselChanged(false)
    }

    /** The station's next [count] songs, mixing well out of [seed]. */
    suspend fun next(seed: Song?, queued: Set<String>, count: Int, known: TrackAnalysis? = null): List<Song> {
        val seedAnalysis = known ?: seed?.let { graph.analysis.cached(it.id) }
        val recent = graph.library.songIdsPlayedSince(System.currentTimeMillis() - RECENT_MS).toHashSet()
        return stationPicks(pool, seedAnalysis, recent, queued, count)
    }

    private companion object {
        /** Tracks played in the last three hours wait their turn. */
        const val RECENT_MS = 3 * 60 * 60 * 1000L
    }
}
