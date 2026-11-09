package app.kultr.core.karousel

import app.kultr.core.api.Song
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Suggested songs under a playlist: the same cases as Kultr for iOS and the web. */
class PlaylistSuggestionsTest {
    private fun song(artist: String, title: String, id: String = "$artist-$title") =
        Song(id = id, title = title, artist = artist, artistId = "ar-$artist")

    /** A server whose station for a song is ten songs "like" it, each by its own artist. */
    private open inner class Server(val favouriteSongs: List<Song> = emptyList()) : KarouselSources {
        val stations = ArrayList<String>()

        override suspend fun station(seed: Song): List<Song> {
            stations += seed.id
            return (1..10).map { song("${seed.title} artist $it", "Like ${seed.title} $it") }
        }

        override suspend fun similarArtists(artist: ArtistRef): List<ArtistRef> = emptyList()
        override suspend fun topSongs(artist: ArtistRef, count: Int): List<Song> = emptyList()
        override suspend fun byArtists(artists: List<ArtistRef>): List<Song> = emptyList()
        override suspend fun inGenres(genres: Set<String>, count: Int): List<Song> = emptyList()
        override suspend fun favourites(count: Int): List<Song> = favouriteSongs.take(count)
    }

    private val playlist = listOf(
        song("Yll Limani", "Faaaj"),
        song("Yll Limani", "Taxi"),
        song("Majk", "Kcen"),
        song("Era Istrefi", "Bonbon"),
    )
    private val added = song("Dhurata Dora", "NRVZ")

    @Test
    fun seedsAreThreeArtistsWithTheSongJustAddedFirst() {
        val seeds = PlaylistSuggestions.seeds(playlist, null, Random(1))
        assertEquals(3, seeds.size)
        assertEquals(3, seeds.map { it.artist }.toSet().size)
        val afterAdd = PlaylistSuggestions.seeds(playlist + added, added, Random(1))
        assertEquals(added.id, afterAdd.first().id)
        assertTrue(afterAdd.drop(1).none { it.id == added.id || it.artist == added.artist })
    }

    @Test
    fun neverSuggestsThePlaylistOrTheSameSongFromAnotherAlbum() = runTest {
        val remaster = song("Majk", "Kcen (Remastered 2024)", id = "other-album")
        val server = object : Server() {
            override suspend fun station(seed: Song): List<Song> = listOf(remaster) + super.station(seed)
        }
        val picks = PlaylistSuggestions.suggest(server, playlist, random = Random(2))
        assertEquals(PlaylistSuggestions.COUNT, picks.size)
        assertTrue(picks.none { pick -> playlist.any { it.id == pick.id } || pick.id == "other-album" }, picks.joinToString { it.title })
    }

    @Test
    fun afterAnAddTheSuggestionsAreLikeIt() = runTest {
        val server = Server()
        val picks = PlaylistSuggestions.suggest(server, playlist + added, added, random = Random(4))
        assertEquals(added.id, server.stations.first())
        assertTrue(picks.count { it.title.startsWith("Like ${added.title}") } >= 3, picks.joinToString { it.title })
    }

    @Test
    fun refreshOffersDifferentSongs() = runTest {
        val first = PlaylistSuggestions.suggest(Server(), playlist, random = Random(3))
        val refreshed = PlaylistSuggestions.suggest(Server(), playlist, leaveOut = first, random = Random(3))
        assertTrue(refreshed.isNotEmpty())
        assertTrue(refreshed.none { pick -> first.any { it.id == pick.id } })
    }

    @Test
    fun anEmptyPlaylistStartsFromFavourites() = runTest {
        val favourites = listOf(song("Capital T", "Bongo"), song("Tayna", "OMG"), song("Lluni", "Ditari"))
        val server = Server(favourites)
        val picks = PlaylistSuggestions.suggest(server, emptyList(), random = Random(5))
        assertTrue(server.stations.isNotEmpty() && favourites.any { it.id == server.stations.first() })
        assertEquals(PlaylistSuggestions.COUNT, picks.size)
        assertTrue(PlaylistSuggestions.suggest(Server(), emptyList(), random = Random(6)).isEmpty())
    }
}
