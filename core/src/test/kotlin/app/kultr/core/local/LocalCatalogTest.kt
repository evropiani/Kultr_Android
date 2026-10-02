package app.kultr.core.local

import app.kultr.core.api.Song
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalCatalogTest {
    private fun track(
        name: String,
        folder: String,
        album: String? = null,
        artist: String? = null,
        albumArtist: String? = null,
        number: Int? = null,
        disc: Int? = null,
        genre: String? = null,
    ) = LocalTrack(
        uri = "content://music/$folder/$name",
        fileName = name,
        folderKey = folder,
        folderName = folder.substringAfterLast('/'),
        modified = 1_700_000_000_000,
        size = 1_000,
        title = null,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        track = number,
        disc = disc,
        genre = genre,
        durationMs = 200_400,
    )

    @Test
    fun untaggedFilesTakeTheirNamesFromTheFileAndFolder() {
        val catalog = LocalCatalogBuilder.build(listOf(track("01 Intro.mp3", "Music/Demo Tape")))
        val song = catalog.songs.single()
        assertEquals("01 Intro", song.title)
        assertEquals("Demo Tape", song.album)
        assertEquals(LocalCatalogBuilder.UNKNOWN_ARTIST, catalog.albums.single().artist)
        assertEquals(200, song.duration)
        assertEquals("mp3", song.suffix)
        assertTrue(song.id.startsWith(LocalCatalogBuilder.SONG_PREFIX))
        assertEquals(song.id, LocalCatalogBuilder.songId(song.path!!))
    }

    @Test
    fun albumsWithTheSameNameStayApartUnlessTheirArtistSaysOtherwise() {
        val catalog = LocalCatalogBuilder.build(
            listOf(
                // Two different "Greatest Hits", no album artist, in two folders: two albums.
                track("a.mp3", "Music/A", album = "Greatest Hits", artist = "A"),
                track("b.mp3", "Music/B", album = "Greatest Hits", artist = "B"),
                // A two-disc album in two folders, tagged with its album artist: one album.
                track("1.flac", "Music/Big/CD1", album = "Big", artist = "C", albumArtist = "C", number = 1, disc = 1),
                track("2.flac", "Music/Big/CD2", album = "Big", artist = "C", albumArtist = "C", number = 1, disc = 2),
            ),
        )
        assertEquals(3, catalog.albums.size)
        val big = catalog.albums.single { it.name == "Big" }
        assertEquals(2, big.songCount)
        assertEquals(listOf(1, 2), catalog.songs.filter { it.albumId == big.id }.map { it.discNumber })
        assertEquals(setOf("A", "B", "C"), catalog.artists.map { it.name }.toSet())
    }

    @Test
    fun compilationsWithoutAnAlbumArtistAreByVariousArtists() {
        val catalog = LocalCatalogBuilder.build(
            listOf(
                track("1.mp3", "Music/Mix", album = "Mix", artist = "X", genre = "House"),
                track("2.mp3", "Music/Mix", album = "Mix", artist = "Y", genre = "House"),
            ),
        )
        val album = catalog.albums.single()
        assertEquals(LocalCatalogBuilder.VARIOUS_ARTISTS, album.artist)
        assertEquals(true, album.isCompilation)
        // Each track keeps its own artist name.
        assertEquals(listOf("X", "Y"), catalog.songs.map { it.artist })
        assertEquals("House", album.genre)
        assertEquals(2, catalog.genres.single().songCount)
    }

    @Test
    fun aFolderImageIsTheCoverAndUserDataCarriesOver() {
        val t = track("1.mp3", "Music/Album", album = "Album", artist = "Z")
        val id = LocalCatalogBuilder.songId(t.uri)
        val before = Song(id = id, playCount = 7, played = "2026-09-01T10:00:00Z", starred = "2026-08-01T00:00:00Z", userRating = 4)
        val catalog = LocalCatalogBuilder.build(listOf(t), covers = mapOf("Music/Album" to "content://cover")) { if (it == id) before else null }
        val song = catalog.songs.single()
        assertEquals("content://cover", song.coverArt)
        assertEquals("content://cover", catalog.albums.single().coverArt)
        assertEquals(7L, song.playCount)
        assertEquals(4, song.userRating)
        assertEquals("2026-08-01T00:00:00Z", song.starred)
        assertEquals(7L, catalog.albums.single().playCount)
    }

    @Test
    fun tracksAreInDiscAndTrackOrder() {
        val catalog = LocalCatalogBuilder.build(
            listOf(
                track("b.mp3", "Music/X", album = "X", artist = "X", number = 2),
                track("c.mp3", "Music/X", album = "X", artist = "X", number = null),
                track("a.mp3", "Music/X", album = "X", artist = "X", number = 1),
            ),
        )
        assertEquals(listOf(1, 2, null), catalog.songs.map { it.track })
        assertNull(catalog.albums.single().coverArt)
    }
}
