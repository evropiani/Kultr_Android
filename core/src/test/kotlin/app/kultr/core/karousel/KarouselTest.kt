package app.kultr.core.karousel

import app.kultr.core.api.Song
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KarouselTest {
    private fun song(artist: String, title: String, genre: String? = null, id: String = "$artist-$title") =
        Song(id = id, title = title, artist = artist, artistId = "ar-$artist", genre = genre)

    /** A server with Last.fm: similar songs, similar artists and their best-known songs. */
    private class Server(val offline: Boolean = false, val library: List<Song> = emptyList()) : KarouselSources {
        val station = (1..12).map { i -> Song(id = "st-$i", title = "Station song $i", artist = listOf("Phoenix", "Air", "Justice", "Daft Punk")[i % 4]) } +
            Song(id = "st-omt", title = "One More Time (Remastered 2021)", artist = "Daft Punk")

        private fun online() {
            if (offline) throw IOException("offline")
        }

        override suspend fun station(seed: Song): List<Song> {
            online()
            return station
        }

        override suspend fun similarArtists(artist: ArtistRef): List<ArtistRef> {
            online()
            return if (artist.name == "Daft Punk") listOf("Justice", "Cassius", "Air").map { ArtistRef(it, "ar-$it") } else emptyList()
        }

        override suspend fun topSongs(artist: ArtistRef, count: Int): List<Song> {
            online()
            return (1..6).map { Song(id = "top-${artist.name}-$it", title = "${artist.name} hit $it", artist = artist.name) }.take(count)
        }

        override suspend fun byArtists(artists: List<ArtistRef>): List<Song> {
            val names = artists.map { Karousel.artistKey(it.name) }.toSet()
            return library.filter { Karousel.artistKey(it.artist) in names }
        }

        override suspend fun inGenres(genres: Set<String>, count: Int): List<Song> = library.filter { it.genre in genres }.take(count)

        override suspend fun favourites(count: Int): List<Song> = library.take(count)
    }

    private val playing = song("Daft Punk", "One More Time")

    @Test
    fun keepsTheMusicGoingWithMusicLikeWhatIsPlaying() = runTest {
        val next = Karousel(Server()).next(
            Karousel.Input(
                seeds = listOf(playing),
                exclude = setOf("st-1", playing.id, Karousel.key(playing)),
                count = 10,
                random = Random(7),
            ),
        )
        assertEquals(10, next.size)
        assertEquals(next.size, next.map { it.id }.toSet().size, "no song twice")
        // What is queued, and the same song remastered on the station, aren't picked again.
        assertTrue(next.none { it.id == "st-1" })
        assertTrue(next.none { it.title.startsWith("One More Time") })
        // Mostly the station, with songs by similar artists and by the artist playing.
        assertTrue(next.count { it.id.startsWith("st-") } >= 4)
        assertTrue(next.any { it.id.startsWith("top-") && it.artist != "Daft Punk" })
        assertTrue(next.any { it.id.startsWith("top-Daft Punk") })
        // At most two songs by one artist, and never the same artist twice in a row.
        assertTrue(next.groupBy { it.artist }.values.all { it.size <= Karousel.MAX_PER_ARTIST })
        assertTrue(next.zipWithNext().none { (a, b) -> a.artist == b.artist })
    }

    @Test
    fun withNoConnectionItCarriesOnWithTheLibrary() = runTest {
        val library = listOf(
            song("Daft Punk", "Aerodynamic"),
            song("Massive Attack", "Teardrop", genre = "Trip Hop"),
            song("Portishead", "Roads", genre = "Trip Hop"),
            song("Air", "Sexy Boy"),
            song("Massive Attack", "Angel", genre = "Trip Hop"),
            song("Massive Attack", "Unfinished Sympathy", genre = "Trip Hop"),
        )
        val seed = song("Massive Attack", "Angel", genre = "Trip Hop")
        val next = Karousel(Server(offline = true, library = library)).next(
            Karousel.Input(seeds = listOf(seed), exclude = setOf(seed.id), count = 4, random = Random(1)),
        )
        assertEquals(4, next.size)
        // The same artist first (no more than two), then the same genre, then what the user plays.
        assertEquals(setOf("Teardrop", "Unfinished Sympathy"), next.filter { it.artist == "Massive Attack" }.map { it.title }.toSet())
        assertTrue(next.any { it.title == "Roads" })
        assertTrue(next.none { it.title == "Angel" })
    }

    @Test
    fun nothingToGoOnMeansNothingAdded() = runTest {
        val karousel = Karousel(Server(offline = true))
        assertEquals(emptyList(), karousel.next(Karousel.Input(seeds = listOf(playing))))
        assertEquals(emptyList(), karousel.next(Karousel.Input(seeds = emptyList())))
        // Internet radio has nothing to be like.
        assertEquals(emptyList(), karousel.next(Karousel.Input(seeds = listOf(Song(id = "r", title = "FM", kultrStreamUrl = "https://radio")))))
    }

    @Test
    fun songsAreKnownByTheirFirstArtistAndTitle() {
        assertEquals("daft punk", Karousel.artistKey("Daft Punk feat. Pharrell Williams"))
        assertEquals("beyonce", Karousel.artistKey("Beyoncé, JAY-Z"))
        assertEquals(
            Karousel.key(song("Daft Punk", "Get Lucky")),
            Karousel.key(song("Daft Punk ft. Pharrell", "Get Lucky (Radio Edit)")),
        )
        assertEquals(
            Karousel.key(song("Queen", "Bohemian Rhapsody")),
            Karousel.key(song("Queen", "Bohemian Rhapsody - Remastered 2011")),
        )
        assertTrue(Karousel.key(song("Queen", "Bohemian Rhapsody")) != Karousel.key(song("Queen", "Somebody to Love")))
    }

    @Test
    fun spreadKeepsAnArtistFromPlayingTwiceInARow() {
        val spread = Karousel.spread(listOf(song("A", "1"), song("A", "2"), song("B", "1"), song("C", "1")))
        assertTrue(spread.zipWithNext().none { (a, b) -> a.artist == b.artist })
        assertEquals(4, spread.size)
    }
}
