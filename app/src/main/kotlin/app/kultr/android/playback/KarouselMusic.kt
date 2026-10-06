package app.kultr.android.playback

import android.util.Log
import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import app.kultr.core.injekt.affinity
import app.kultr.core.karousel.ArtistRef
import app.kultr.core.karousel.Karousel
import app.kultr.core.karousel.KarouselSources

/**
 * Karousel's music, from the library in use. A server offers its similar songs
 * and similar artists and their best-known songs (when it has Last.fm set up);
 * the library itself carries on from there, and is all there is for the music
 * on the phone. Offline, only downloaded songs are offered, since nothing else
 * would play.
 */
class KarouselMusic(private val graph: AppGraph) : KarouselSources {
    private val library get() = graph.library

    /** Whether the server can be asked: the library is a server's, and it can be reached. */
    private val online: Boolean
        get() = !graph.isLocal && graph.auth.client.value != null && graph.network.isOnline

    private fun playable(song: Song): Boolean =
        !song.isRadio && (graph.isLocal || online || song.id in graph.offline.downloadedIds.value)

    /** Songs like [seeds] (the track playing first) to follow [queued], leaving out what played in the last few hours. */
    suspend fun next(seeds: List<Song>, queued: List<Song>, count: Int = 10): List<Song> {
        val exclude = HashSet<String>()
        val recent = library.songsByIds(library.songIdsPlayedSince(System.currentTimeMillis() - RECENT_MS))
        for (song in queued + recent) {
            exclude += song.id
            exclude += Karousel.key(song)
        }
        return Karousel(this, log = { Log.i(TAG, "Karousel: $it") }).next(Karousel.Input(seeds, exclude, count))
    }

    override suspend fun station(seed: Song): List<Song> {
        if (!online) return emptyList()
        return mixingWell(seed, library.similarSongs(seed.id, 50).filter(::playable))
    }

    /**
     * With InjeKt on, the songs that mix best out of [seed] come first, as far
     * as analyses already made can tell; the rest keep the server's order.
     */
    private suspend fun mixingWell(seed: Song, songs: List<Song>): List<Song> {
        if (!graph.settings.current.injektEnabled || songs.isEmpty()) return songs
        val analyses = graph.analysis.cachedMany(songs.map { it.id } + seed.id)
        val from = analyses[seed.id] ?: return songs
        return songs.sortedByDescending { song -> analyses[song.id]?.let { affinity(from, it) } ?: UNKNOWN_AFFINITY }
    }

    override suspend fun similarArtists(artist: ArtistRef): List<ArtistRef> {
        val id = artist.id ?: return emptyList()
        if (!online) return emptyList()
        return library.artistInfo(id)?.similarArtist.orEmpty().map { ArtistRef(it.name, it.id) }
    }

    override suspend fun topSongs(artist: ArtistRef, count: Int): List<Song> {
        if (!online) return emptyList()
        return library.topSongs(artist.name).filter(::playable).take(count)
    }

    override suspend fun byArtists(artists: List<ArtistRef>): List<Song> =
        library.songsByArtists(artists.mapNotNull { it.id }, artists.map { it.name }).filter(::playable)

    override suspend fun inGenres(genres: Set<String>, count: Int): List<Song> {
        val each = (count / genres.size.coerceAtLeast(1)).coerceAtLeast(10)
        return genres.take(3).flatMap { library.randomSongs(each, it) }.filter(::playable)
    }

    override suspend fun favourites(count: Int): List<Song> {
        val played = library.mostPlayedSongsNow(count)
        val hearted = library.starredSongsNow().shuffled().take(count / 2)
        val downloaded = if (!online && !graph.isLocal) {
            library.songsByIds(graph.offline.downloadedIds.value.shuffled().take(count))
        } else {
            emptyList()
        }
        // Anything at all from the library last, for a library with no plays or hearts yet.
        val any = library.randomSongs(count)
        return (played + hearted + downloaded + any).distinctBy { it.id }.filter(::playable)
    }

    private companion object {
        const val TAG = "Kultr"

        /** Songs played this recently are not offered again. */
        const val RECENT_MS = 3 * 60 * 60 * 1000L

        /** Where a song not analysed yet sits among analysed ones: in the middle. */
        const val UNKNOWN_AFFINITY = 0.5
    }
}
