package app.kultr.core.api

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubsonicClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    private fun ok(body: String) = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.53.3","openSubsonic":true$body}}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val endpoint = url.pathSegments.last()
                if (url.queryParameter("u") == "bad") {
                    return MockResponse().setBody(
                        """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong username or password"}}}""",
                    )
                }
                return when (endpoint) {
                    "ping" -> MockResponse().setBody(ok(""))
                    "getArtists" -> MockResponse().setBody(
                        ok(
                            ""","artists":{"ignoredArticles":"The","index":[
                            {"name":"A","artist":[{"id":"ar1","name":"Aphex Twin","albumCount":3}]},
                            {"name":"B","artist":[{"id":"ar2","name":"Boards of Canada","albumCount":2,"starred":"2024-01-01T00:00:00Z"}]}]}""",
                        ),
                    )
                    "getAlbum" -> MockResponse().setBody(
                        ok(
                            ""","album":{"id":"${url.queryParameter("id")}","name":"Selected Ambient Works","artist":"Aphex Twin",
                            "songCount":2,"duration":600,"created":"2023-05-01T10:00:00Z","unknownField":{"x":1},
                            "song":[{"id":"s1","title":"Xtal","track":1,"duration":291,"albumId":"al1","replayGain":{"trackGain":-6.5,"trackPeak":0.98}},
                                    {"id":"s2","title":"Tha","track":2,"duration":544,"albumId":"al1","bpm":128}]}""",
                        ),
                    )
                    "getAlbumList2" -> MockResponse().setBody(ok(""","albumList2":{}"""))
                    "getLyricsBySongId" -> MockResponse().setBody(
                        ok(""","lyricsList":{"structuredLyrics":[{"lang":"eng","synced":true,"offset":100,"line":[{"start":1000,"value":"Hello"},{"start":2500,"value":"World"}]}]}"""),
                    )
                    "createPlaylist" -> MockResponse().setBody(ok(""","playlist":{"id":"p1","name":"${url.queryParameter("name")}","songCount":${url.queryParameterValues("songId").size}}"""))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(user: String = "alice", mode: AuthMode = AuthMode.TOKEN, path: String = "/") =
        SubsonicClient(Credentials(server.url(path).toString(), user, "sesame", mode), http)

    @Test
    fun tokenMatchesTheSubsonicDocumentationExample() {
        assertEquals("26719a1196d2a940705a59634eb18eab", md5Hex("sesame" + "c19b2d"))
    }

    @Test
    fun pingReportsServerInfoAndSendsTokenAuth() = runTest {
        val info = client().ping()
        assertEquals("navidrome", info.type)
        assertEquals("Navidrome 0.53.3", info.description)
        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("alice", url.queryParameter("u"))
        assertEquals("json", url.queryParameter("f"))
        assertEquals("Kultr", url.queryParameter("c"))
        val salt = url.queryParameter("s")!!
        assertEquals(md5Hex("sesame$salt"), url.queryParameter("t"))
        assertNull(url.queryParameter("p"))
    }

    @Test
    fun plainModeSendsHexEncodedPassword() = runTest {
        client(mode = AuthMode.PLAIN).ping()
        val url = server.takeRequest().requestUrl!!
        assertEquals("enc:736573616d65", url.queryParameter("p"))
        assertNull(url.queryParameter("t"))
    }

    @Test
    fun apiErrorsCarryTheirCode() = runTest {
        val err = assertFailsWith<SubsonicException> { client(user = "bad").ping() }
        assertEquals(40, err.code)
        assertEquals("Wrong username or password.", describeError(err))
    }

    @Test
    fun httpErrorsAreExplained() = runTest {
        val err = assertFailsWith<NetworkException> { client().getScanStatus() }
        assertTrue(err.message!!.contains("404"))
    }

    @Test
    fun artistsAreFlattenedAcrossIndexes() = runTest {
        val artists = client().getArtists()
        assertEquals(listOf("Aphex Twin", "Boards of Canada"), artists.map { it.name })
        assertTrue(artists[1].isStarred)
    }

    @Test
    fun albumDetailParsesSongsAndIgnoresUnknownFields() = runTest {
        val album = client().getAlbum("al1")
        assertNotNull(album)
        assertEquals(2, album.song!!.size)
        assertEquals(-6.5, album.song!![0].replayGain!!.trackGain)
        assertEquals(128, album.song!![1].bpm)
    }

    @Test
    fun emptyContainersBecomeEmptyLists() = runTest {
        assertEquals(emptyList(), client().getAlbumList2(AlbumListType.NEWEST))
    }

    @Test
    fun serverUnderASubPathKeepsItsPrefix() = runTest {
        client(path = "/navidrome/").ping()
        assertEquals("/navidrome/rest/ping", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun listParametersRepeatTheKey() = runTest {
        val playlist = client().createPlaylist("Mix", listOf("s1", "s2", "s3"))
        assertEquals(3, playlist!!.songCount)
        assertEquals(listOf("s1", "s2", "s3"), server.takeRequest().requestUrl!!.queryParameterValues("songId"))
    }

    @Test
    fun structuredLyricsParse() = runTest {
        val lyrics = client().getLyricsBySongId("s1")
        assertEquals(1, lyrics.size)
        assertTrue(lyrics[0].synced)
        assertEquals(2500L, lyrics[0].line!![1].start)
    }

    @Test
    fun streamUrlsAreStableAndCarryOptions() {
        val c = client()
        val first = c.streamUrl("s1", maxBitRate = 192, format = "opus")
        val second = c.streamUrl("s1", maxBitRate = 192, format = "opus")
        assertEquals(first, second)
        val url = first.toHttpUrl()
        assertEquals("192", url.queryParameter("maxBitRate"))
        assertEquals("opus", url.queryParameter("format"))
        assertNull(c.streamUrl("s1", maxBitRate = 0).toHttpUrl().queryParameter("maxBitRate"))
        assertNull(c.coverArtUrl(null))
    }

    @Test
    fun serverAddressesAreNormalised() {
        assertEquals("https://music.example.com", normalizeServerUrl(" music.example.com/ "))
        assertEquals("http://10.0.0.2:4533", normalizeServerUrl("http://10.0.0.2:4533//"))
        assertEquals("", normalizeServerUrl("  "))
    }
}
