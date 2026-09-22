package app.kultr.android.playback

import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import app.kultr.core.injekt.rankAutoQueue

/**
 * Picks tracks to keep the music going when the queue runs out.
 *
 * Candidates come from the server's similarity endpoint first (it knows about
 * Last.fm-style relationships), then the same artist, then the same genre, and
 * finally anything in the library. Whatever we get is re-ranked by how well it
 * mixes, using cached analysis only — never analysing dozens of tracks just to
 * sort them.
 */
class AutoQueue(private val graph: AppGraph) {
    suspend fun build(seed: Song, recent: List<Song>, count: Int = 10): List<Song> {
        val exclude = recent.map { it.id }.toHashSet() + seed.id
        val pool = LinkedHashMap<String, Song>()
        fun add(songs: List<Song>) {
            for (song in songs) if (song.id !in exclude && !song.isRadio) pool.putIfAbsent(song.id, song)
        }
        val library = graph.library
        if (!seed.isRadio) add(library.similarSongs(seed.id, 60))
        val artistId = seed.artistId
        if (pool.size < count * 3 && artistId != null) add(library.songsOfArtistNow(artistId).shuffled().take(80))
        if (pool.size < count * 3 && seed.genre != null) add(library.randomSongs(200, seed.genre))
        if (pool.size < count * 3) add(library.randomSongs(200))
        if (pool.isEmpty()) return emptyList()

        val analyses = graph.analysis.cachedMany(pool.keys.toList() + seed.id)
        return rankAutoQueue(analyses[seed.id], pool.values.map { it to analyses[it.id] }, count)
    }
}
