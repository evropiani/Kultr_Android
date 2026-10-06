package app.kultr.core.karousel

import app.kultr.core.api.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import kotlin.random.Random

/** An artist as the library knows them: the name, and their id where there is one. */
data class ArtistRef(val name: String, val id: String? = null)

/**
 * Where Karousel finds music. Any of these may come up empty (no connection, a
 * server without Last.fm, music on the phone), and Karousel carries on with
 * the rest. Every song offered must be one that can play right now.
 */
interface KarouselSources {
    /** Songs like [seed], by any artist: a station started from it. */
    suspend fun station(seed: Song): List<Song>

    /** Artists like [artist] that are in the library. */
    suspend fun similarArtists(artist: ArtistRef): List<ArtistRef>

    /** [artist]'s best-known songs in the library. */
    suspend fun topSongs(artist: ArtistRef, count: Int): List<Song>

    /** The library's songs by any of [artists]. */
    suspend fun byArtists(artists: List<ArtistRef>): List<Song>

    /** Some of the library's songs in any of [genres]. */
    suspend fun inGenres(genres: Set<String>, count: Int): List<Song>

    /** What the user plays most and has hearted, most played first. */
    suspend fun favourites(count: Int): List<Song>
}

/**
 * Karousel: when the queue runs out, more music like what has been playing,
 * so it never stops. Ported from KultrDL.
 *
 * It draws on a station started from the song now playing (the server's
 * similar songs), on the best-known songs of artists like the ones playing and
 * of those artists themselves, and on the library's songs by any of them.
 * Short of those (with no connection, say, or for music on the phone) it
 * carries on with the library: the same genres first, then what the user
 * plays most.
 */
class Karousel(
    private val sources: KarouselSources,
    private val log: (String) -> Unit = {},
) {
    data class Input(
        /** What has been playing, the song now playing first. */
        val seeds: List<Song>,
        /** Song ids and [key]s not to play: what is queued and what played lately. */
        val exclude: Set<String> = emptySet(),
        val count: Int = 10,
        val random: Random = Random.Default,
    )

    private class Around(val same: List<Song>, val similar: List<Song>, val similarArtists: List<ArtistRef>)

    suspend fun next(input: Input): List<Song> = coroutineScope {
        val seeds = input.seeds.filter { !it.isRadio && !it.artist.isNullOrBlank() }
        if (seeds.isEmpty() || input.count <= 0) return@coroutineScope emptyList()
        val random = input.random
        val artists = seeds.map { ArtistRef(primary(it.artist), it.artistId) }.distinctBy { artistKey(it.name) }.take(3)

        val station = async {
            for (seed in seeds.take(2)) {
                val found = safe("songs like ${seed.title}", emptyList()) { sources.station(seed) }
                if (found.isNotEmpty()) return@async found
            }
            emptyList()
        }
        val around = artists.mapIndexed { i, artist -> async { around(artist, if (i == 0) 4 else 2, random) } }

        val fromStation = station.await()
        val found = around.awaitAll()
        val near = (artists + found.flatMap { it.similarArtists }).distinctBy { artistKey(it.name) }
        val nearKeys = near.map { artistKey(it.name) }.toSet()
        val seedGenres = seeds.mapNotNull { it.genre?.trim()?.takeIf(String::isNotEmpty) }.toSet()
        val theirs = async { safe("songs by ${near.size} artists", emptyList()) { sources.byArtists(near) } }
        val genres = async {
            if (seedGenres.isEmpty()) emptyList() else safe("songs in ${seedGenres.joinToString()}", emptyList()) { sources.inGenres(seedGenres, 200) }
        }
        val favourites = async { safe("favourites", emptyList()) { sources.favourites(200) } }

        // In order of preference: the station, artists like these, these artists, the library's songs by any of them.
        val pools = listOf(
            fromStation,
            roundRobin(found.map { it.similar }),
            roundRobin(found.map { it.same }),
            theirs.await().filter { artistKey(it.artist) in nearKeys }.shuffled(random),
        ).map { ArrayDeque(it) }
        val backups = listOf(
            genres.await().filter { artistKey(it.artist) !in nearKeys }.shuffled(random),
            favourites.await().take(200).shuffled(random),
        ).map { ArrayDeque(it) }

        val picked = ArrayList<Song>()
        val taken = HashSet<String>(input.exclude)
        val perArtist = HashMap<String, Int>()
        fun take(pool: ArrayDeque<Song>): Boolean {
            while (pool.isNotEmpty()) {
                val song = pool.removeFirst()
                val songKey = key(song)
                val artist = artistKey(song.artist)
                if (song.isRadio || song.id in taken || songKey in taken || (perArtist[artist] ?: 0) >= MAX_PER_ARTIST) continue
                taken += song.id
                taken += songKey
                perArtist[artist] = (perArtist[artist] ?: 0) + 1
                picked += song
                return true
            }
            return false
        }
        // Mostly the station, with artists like these, these artists and the library's own mixed in.
        var turn = 0
        while (picked.size < input.count) {
            val first = MIX[turn++ % MIX.size]
            if (take(pools[first])) continue
            if (pools.none { take(it) }) break
        }
        while (picked.size < input.count) {
            if (backups.none { take(it) }) break
        }

        val sources = listOf(fromStation, found.flatMap { it.similar }, found.flatMap { it.same }).map { s -> s.map { it.id }.toSet() }
        val counts = sources.map { s -> picked.count { it.id in s } }
        log(
            "${picked.size} songs like ${artists.joinToString { it.name }}: ${counts[0]} from the station, " +
                "${counts[1]} by similar artists, ${counts[2]} by the same artists, ${picked.size - counts.sum()} more from the library",
        )
        spread(picked)
    }

    /** The artist's best-known songs, and songs by artists like them. */
    private suspend fun around(artist: ArtistRef, similarArtists: Int, random: Random): Around = coroutineScope {
        val top = async { safe("top songs of ${artist.name}", emptyList()) { sources.topSongs(artist, 6) } }
        val related = safe("artists like ${artist.name}", emptyList()) { sources.similarArtists(artist) }
            .filter { artistKey(it.name) != artistKey(artist.name) }
            .take(8)
        val chosen = related.shuffled(random).take(similarArtists)
        val theirs = chosen.map { r ->
            async { safe("top songs of ${r.name}", emptyList()) { sources.topSongs(r, 5) }.shuffled(random).take(3) }
        }
        Around(top.await().shuffled(random).take(3), roundRobin(theirs.awaitAll()), related)
    }

    private suspend fun <T> safe(what: String, empty: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("$what failed: ${e.message}")
        empty
    }

    companion object {
        /** No more than this many songs by one artist in a batch. */
        const val MAX_PER_ARTIST = 2

        /** Which pool each pick comes from first: 0 the station, 1 similar artists, 2 the same artists, 3 the library's own. */
        private val MIX = intArrayOf(0, 1, 0, 2, 0, 1, 3, 0, 1, 2)

        private val FEATURING = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with|vs\\.?|x)\\s+|\\s*[,;/&+]\\s*")
        private val TITLE_NOISE = Regex(
            "(?i)\\s*[(\\[][^)\\]]*(?:feat|remaster|version|edit|mono|stereo|explicit|clean|bonus|deluxe)[^)\\]]*[)\\]]" +
                "|\\s+-\\s+[^-]*(?:remaster|version|edit|mono|stereo)[^-]*$",
        )
        private val MARKS = Regex("\\p{M}+")
        private val NOT_WORDS = Regex("[^\\p{L}\\p{N}]+")

        /** The first-named artist of a credit like "A feat. B" or "A, B & C". */
        fun primary(artist: String?): String {
            val whole = artist.orEmpty().trim()
            return FEATURING.split(whole).firstOrNull { it.isNotBlank() }?.trim() ?: whole
        }

        /** What artists are compared by: the first-named artist, lower case, without accents or punctuation. */
        fun artistKey(artist: String?): String = fold(primary(artist))

        /** The same song wherever it appears: on another album, remastered, or credited differently. */
        fun key(song: Song): String = "song:" + artistKey(song.artist) + "|" + fold(TITLE_NOISE.replace(song.title, ""))

        private fun fold(text: String): String =
            MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().replace(NOT_WORDS, " ").trim()

        /** Reorders so the same artist doesn't play twice in a row, where another can go between. */
        fun spread(songs: List<Song>): List<Song> {
            val left = songs.toMutableList()
            val out = ArrayList<Song>(songs.size)
            var last: String? = null
            while (left.isNotEmpty()) {
                val i = left.indexOfFirst { artistKey(it.artist) != last }.takeIf { it >= 0 } ?: 0
                val next = left.removeAt(i)
                out += next
                last = artistKey(next.artist)
            }
            return out
        }

        private fun <T> roundRobin(lists: List<List<T>>): List<T> {
            val out = ArrayList<T>()
            val longest = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until longest) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
            return out
        }
    }
}
