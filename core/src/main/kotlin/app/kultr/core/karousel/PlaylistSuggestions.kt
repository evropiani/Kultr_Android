package app.kultr.core.karousel

import app.kultr.core.api.Song
import kotlin.random.Random

/**
 * Suggested songs under a playlist, as on Spotify: a few songs that would
 * fit, each with a + to add it. Add one and the next suggestions are songs
 * like the one just added. They come from Karousel's picker, so the server's
 * similar songs and artists lead when it has them, and the library carries
 * on when it does not. The same rules as Kultr for iOS and the web.
 */
object PlaylistSuggestions {
    /** How many suggestions show at a time. */
    const val COUNT = 5

    /** Songs from the playlist, or favourites, that suggestions start from. */
    private const val SEEDS = 3

    /**
     * What suggestions are drawn from: the song just added first, so what
     * comes next is like it, then songs from the playlist by other artists,
     * picked at random so that refreshing gives something new.
     */
    fun seeds(playlist: List<Song>, added: Song?, random: Random = Random.Default): List<Song> {
        val seeds = ArrayList<Song>()
        val artists = HashSet<String>()
        fun push(song: Song) {
            val artist = Karousel.artistKey(song.artist)
            if (artist.isEmpty() || !artists.add(artist)) return
            seeds += song
        }
        if (added != null) push(added)
        for (song in playlist.shuffled(random)) {
            if (seeds.size >= SEEDS) break
            if (song.id != added?.id) push(song)
        }
        return seeds
    }

    /** Ids and keys of what not to suggest: the playlist (the same song on another album too), and anything shown already. */
    fun exclude(playlist: List<Song>, leaveOut: List<Song> = emptyList()): Set<String> =
        (playlist + leaveOut).flatMap { listOf(it.id, Karousel.key(it)) }.toSet()

    /**
     * Songs to suggest for [playlist] (the song just added included). An
     * empty playlist starts from what the user plays most and has hearted.
     */
    suspend fun suggest(
        sources: KarouselSources,
        playlist: List<Song>,
        added: Song? = null,
        leaveOut: List<Song> = emptyList(),
        count: Int = COUNT,
        random: Random = Random.Default,
    ): List<Song> {
        var seeds = seeds(playlist, added, random)
        if (seeds.isEmpty()) {
            val favourites = runCatching { sources.favourites(40) }.getOrDefault(emptyList())
            seeds = seeds(favourites.take(40), null, random)
        }
        if (seeds.isEmpty() || count <= 0) return emptyList()
        return Karousel(sources).next(
            Karousel.Input(seeds = seeds, exclude = exclude(playlist, leaveOut), count = count, random = random),
        )
    }
}
