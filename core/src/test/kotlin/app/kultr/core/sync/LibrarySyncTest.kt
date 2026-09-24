package app.kultr.core.sync

import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Credentials
import app.kultr.core.api.Genre
import app.kultr.core.api.Playlist
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryStore : LibraryStore {
    val artists = ConcurrentHashMap<String, Artist>()
    val albums = ConcurrentHashMap<String, Album>()
    val songs = ConcurrentHashMap<String, Song>()
    var playlists = listOf<Playlist>()
    var genres = listOf<Genre>()
    var state = SyncState()

    override suspend fun albumStamps() = albums.mapValues {
        AlbumStamp(it.value.songCount, it.value.changed, it.value.duration, it.value.playCount, it.value.played)
    }
    override suspend fun putArtists(artists: List<Artist>) = artists.forEach { this.artists[it.id] = it }
    override suspend fun putAlbums(albums: List<Album>) = albums.forEach { this.albums[it.id] = it.copy(song = null) }
    override suspend fun replaceAlbumSongs(songsByAlbum: Map<String, List<Song>>) {
        for ((albumId, list) in songsByAlbum) {
            val keep = list.map { it.id }.toSet()
            songs.values.removeIf { it.albumId == albumId && it.id !in keep }
            list.forEach { songs[it.id] = it }
        }
    }
    override suspend fun replacePlaylists(playlists: List<Playlist>) { this.playlists = playlists }
    override suspend fun replaceGenres(genres: List<Genre>) { this.genres = genres }
    override suspend fun deleteAlbumsNotIn(keep: Set<String>): Int {
        val gone = albums.keys.filter { it !in keep }
        gone.forEach { albums.remove(it) }
        return gone.size
    }
    override suspend fun deleteArtistsNotIn(keep: Set<String>): Int {
        val gone = artists.keys.filter { it !in keep }
        gone.forEach { artists.remove(it) }
        return gone.size
    }
    override suspend fun deleteSongsOutsideAlbums(albumIds: Set<String>): Int {
        val gone = songs.values.filter { it.albumId !in albumIds }.map { it.id }
        gone.forEach { songs.remove(it) }
        return gone.size
    }
    override suspend fun counts() = LibraryCounts(artists.size, albums.size, songs.size, playlists.size, genres.size)
    override suspend fun syncState() = state
    override suspend fun setSyncState(state: SyncState) { this.state = state }
}

class LibrarySyncTest {
    private lateinit var server: MockWebServer
    private val albumRequests = mutableListOf<String>()

    /** The fake server's library: album id → (changed stamp, track ids). */
    private var library = mapOf(
        "al1" to ("t1" to listOf("s1", "s2")),
        "al2" to ("t1" to listOf("s3")),
    )

    /** Album id → (play count, last played), as the server counts them. */
    private var plays = mapOf<String, Pair<Int, String>>()

    private fun ok(body: String) = """{"subsonic-response":{"status":"ok","version":"1.16.1"$body}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val body = when (url.pathSegments.last()) {
                    "ping" -> ok("")
                    "getArtists" -> ok(""","artists":{"index":[{"name":"A","artist":[{"id":"ar1","name":"Artist"}]}]}""")
                    "getAlbumList2" -> {
                        val offset = url.queryParameter("offset")!!.toInt()
                        // "recent" lists only played albums, most recently played first.
                        val recent = url.queryParameter("type") == "recent"
                        val entries = if (!recent) library.entries.toList() else {
                            library.entries.filter { it.key in plays }.sortedByDescending { plays.getValue(it.key).second }
                        }
                        val albums = if (offset > 0) "" else entries.joinToString(",") { (id, v) ->
                            val played = plays[id]?.let { (count, at) -> ""","playCount":$count,"played":"$at"""" } ?: ""
                            """{"id":"$id","name":"Album $id","artistId":"ar1","songCount":${v.second.size},"changed":"${v.first}","created":"2026-0${id.last()}-01"$played}"""
                        }
                        ok(""","albumList2":{"album":[$albums]}""")
                    }
                    "getAlbum" -> {
                        val id = url.queryParameter("id")!!
                        synchronized(albumRequests) { albumRequests += id }
                        val count = plays[id]?.first ?: 0
                        val songs = library.getValue(id).second.joinToString(",") {
                            """{"id":"$it","title":"$it","albumId":"$id","playCount":$count}"""
                        }
                        ok(""","album":{"id":"$id","name":"Album $id","song":[$songs]}""")
                    }
                    "getPlaylists" -> ok(""","playlists":{"playlist":[{"id":"p1","name":"Mix"}]}""")
                    "getPlaylist" -> ok(""","playlist":{"id":"p1","name":"Mix","entry":[{"id":"s1","title":"s1"}]}""")
                    "getGenres" -> ok(""","genres":{"genre":[{"value":"Techno","songCount":3}]}""")
                    "getScanStatus" -> ok(""","scanStatus":{"scanning":false,"count":3}""")
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun sync(store: LibraryStore) = LibrarySync(
        SubsonicClient(Credentials(server.url("/").toString(), "u", "p"), OkHttpClient()),
        store,
    )

    @Test
    fun fullSyncMirrorsEverything() = runTest {
        val store = MemoryStore()
        val progress = mutableListOf<SyncProgress>()
        val summary = sync(store).run(SyncMode.FULL, onProgress = { progress += it })
        assertEquals(LibraryCounts(artists = 1, albums = 2, songs = 3, playlists = 1, genres = 1), summary.counts)
        assertEquals(2, summary.albumsAdded)
        assertEquals(SyncPhase.DONE, progress.last().phase)
        assertTrue(progress.zipWithNext().all { (x, y) -> y.percent >= x.percent - 1e-9 || y.phase == SyncPhase.DONE })
        assertEquals(listOf("s1"), store.playlists.single().entry!!.map { it.id })
        assertEquals("2026-02-01", store.state.newestAlbumCreated)
    }

    @Test
    fun checkOnlyRereadsChangedAlbums() = runTest {
        val store = MemoryStore()
        sync(store).run(SyncMode.FULL)
        albumRequests.clear()

        library = mapOf(
            "al1" to ("t2" to listOf("s1")), // a track was removed
            "al3" to ("t1" to listOf("s9")), // a new album; al2 is gone
        )
        val summary = sync(store).run(SyncMode.CHECK)
        assertEquals(listOf("al1", "al3"), albumRequests.sorted())
        assertEquals(1, summary.albumsAdded)
        assertEquals(1, summary.albumsUpdated)
        assertEquals(1, summary.albumsRemoved)
        assertEquals(setOf("s1", "s9"), store.songs.keys)
        assertFalse(summary.upToDate)

        albumRequests.clear()
        val again = sync(store).run(SyncMode.CHECK)
        assertTrue(again.upToDate)
        assertTrue(albumRequests.isEmpty())
    }

    @Test
    fun checkRereadsAlbumsPlayedSinceWithoutCallingThemChanged() = runTest {
        val store = MemoryStore()
        sync(store).run(SyncMode.FULL)
        albumRequests.clear()

        plays = mapOf("al2" to (4 to "2026-09-24T10:00:00Z"))
        val summary = sync(store).run(SyncMode.CHECK)
        assertEquals(listOf("al2"), albumRequests)
        assertEquals(1, summary.albumsPlayed)
        assertEquals(0, summary.albumsUpdated)
        assertTrue(summary.upToDate)
        assertEquals(4L, store.songs.getValue("s3").playCount)
    }

    @Test
    fun listeningPullReadsBackOnlyAlbumsPlayedSince() = runTest {
        val store = MemoryStore()
        plays = mapOf("al1" to (2 to "2026-09-20T08:00:00Z"))
        sync(store).run(SyncMode.FULL)
        albumRequests.clear()

        // Played elsewhere: al2 for the first time, al1 not again.
        plays = mapOf("al1" to (2 to "2026-09-20T08:00:00Z"), "al2" to (1 to "2026-09-24T09:00:00Z"))
        val listening = ListeningSync(SubsonicClient(Credentials(server.url("/").toString(), "u", "p"), OkHttpClient()), store)
        val pull = listening.pull()
        assertEquals(1, pull.albumsChanged)
        assertEquals(listOf("al2"), albumRequests)
        assertEquals(1L, store.songs.getValue("s3").playCount)
        assertEquals("2026-09-24T09:00:00Z", store.albums.getValue("al2").played)
        assertEquals("2026-09-24T09:00:00Z", store.state.listeningPulledThrough)

        // Nothing new: nothing re-read.
        albumRequests.clear()
        assertEquals(0, listening.pull().albumsChanged)
        assertTrue(albumRequests.isEmpty())

        // A later library sync keeps the watermark.
        sync(store).run(SyncMode.CHECK)
        assertEquals("2026-09-24T09:00:00Z", store.state.listeningPulledThrough)
    }

    @Test
    fun quickCheckNoticesNewAlbums() = runTest {
        val store = MemoryStore()
        assertTrue(sync(store).quickCheck().changed)
        sync(store).run(SyncMode.FULL)
        // getAlbumList2(newest) returns al1 first here, which is older than what we saw.
        assertFalse(sync(store).quickCheck().changed)
    }
}
