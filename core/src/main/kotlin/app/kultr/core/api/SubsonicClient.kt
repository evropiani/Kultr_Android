package app.kultr.core.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Protocol version we speak. 1.16.1 is what Navidrome implements. */
const val API_VERSION = "1.16.1"
const val CLIENT_NAME = "Kultr"

/** JSON settings shared by everything that reads Subsonic responses. */
val SubsonicJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

class SubsonicException(val code: Int, message: String) : Exception(message)

class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Human-readable explanations for the Subsonic error codes Navidrome uses. */
fun describeError(err: Throwable): String = when (err) {
    is SubsonicException -> when (err.code) {
        0 -> err.message?.takeIf { it.isNotBlank() } ?: "The server reported a generic error."
        10 -> "The server is missing a required parameter."
        20 -> "Your server is too old for this client."
        30 -> "This client is too old for your server."
        40 -> "Wrong username or password."
        41 -> "Token authentication is disabled on this server. Switch to “Plain password” in the advanced options (and use HTTPS!)."
        50 -> "Your account is not allowed to do that."
        60 -> "This feature needs a Subsonic Premium subscription (not applicable to Navidrome)."
        70 -> "Not found."
        else -> err.message?.takeIf { it.isNotBlank() } ?: "Server error ${err.code}."
    }
    is NetworkException -> err.message ?: "Network error."
    is java.net.UnknownHostException -> "Could not find that server. Check the address."
    is java.net.ConnectException -> "Could not connect to the server. Is it running, and reachable from this phone?"
    is java.net.SocketTimeoutException -> "The server took too long to answer."
    is javax.net.ssl.SSLException -> "A secure connection could not be made: ${err.message}"
    else -> err.message ?: err.toString()
}

/**
 * Add a scheme when the person typed a bare host, and drop trailing slashes.
 * A bare address gets https — plain http only when asked for explicitly.
 */
fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return trimmed
    return "https://$trimmed"
}

private val SALT_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()

fun randomSalt(length: Int = 12): String {
    val random = java.security.SecureRandom()
    return buildString(length) { repeat(length) { append(SALT_ALPHABET[random.nextInt(SALT_ALPHABET.size)]) } }
}

fun md5Hex(input: String): String {
    val digest = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return hexEncode(digest)
}

fun hexEncode(bytes: ByteArray): String {
    val hex = "0123456789abcdef"
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xff
        out.append(hex[v ushr 4]).append(hex[v and 0x0f])
    }
    return out.toString()
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

enum class AlbumListType(val wire: String) {
    RANDOM("random"),
    NEWEST("newest"),
    HIGHEST("highest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    ALPHABETICAL_BY_ARTIST("alphabeticalByArtist"),
    STARRED("starred"),
    BY_YEAR("byYear"),
    BY_GENRE("byGenre"),
}

/**
 * A Subsonic API client. Every call is a suspend function that throws
 * [SubsonicException] for API errors and an [IOException] for transport ones.
 */
class SubsonicClient(
    credentials: Credentials,
    private val http: OkHttpClient,
    private val json: Json = SubsonicJson,
    /**
     * Salt for URLs other components hold on to. Pass a value derived from the
     * profile to keep artwork and stream URLs identical across app restarts,
     * so disk caches keep hitting. A captured URL is replayable whatever the
     * salt, so a fixed one exposes nothing new.
     */
    private val stableSalt: String? = null,
) {
    val credentials: Credentials = credentials.copy(serverUrl = normalizeServerUrl(credentials.serverUrl))

    val baseUrl: String get() = credentials.serverUrl

    private val base: HttpUrl? = baseUrl.toHttpUrlOrNull()

    /**
     * One salt for the lifetime of this client, used for URLs something else
     * holds on to (the player, the image loader). A URL that changed on every
     * call would defeat every cache between us and the server.
     */
    private val stableAuth: Pair<String, String> by lazy {
        val salt = stableSalt?.takeIf { it.length >= 6 } ?: randomSalt()
        salt to md5Hex(credentials.password + salt)
    }

    private fun authParams(stable: Boolean): List<Pair<String, String>> {
        val common = listOf(
            "u" to credentials.username,
            "v" to API_VERSION,
            "c" to CLIENT_NAME,
            "f" to "json",
        )
        if (credentials.authMode == AuthMode.PLAIN) {
            return common + ("p" to "enc:" + hexEncode(credentials.password.toByteArray(Charsets.UTF_8)))
        }
        val (salt, token) = if (stable) {
            stableAuth
        } else {
            val salt = randomSalt()
            salt to md5Hex(credentials.password + salt)
        }
        return common + listOf("s" to salt, "t" to token)
    }

    /**
     * Build a fully-qualified, authenticated URL for any endpoint. Null values,
     * empty strings and empty lists are left out; lists repeat the key.
     */
    fun buildUrl(endpoint: String, params: List<Pair<String, Any?>> = emptyList(), stable: Boolean = false): String {
        val root = base ?: throw NetworkException("“$baseUrl” is not a valid server address.")
        val builder = root.newBuilder().addPathSegments("rest/$endpoint")
        for ((key, value) in authParams(stable)) builder.addQueryParameter(key, value)
        for ((key, value) in params) {
            when (value) {
                null -> Unit
                is Collection<*> -> value.forEach { item ->
                    val text = item?.toString()
                    if (!text.isNullOrEmpty()) builder.addQueryParameter(key, text)
                }
                else -> {
                    val text = value.toString()
                    if (text.isNotEmpty()) builder.addQueryParameter(key, text)
                }
            }
        }
        return builder.build().toString()
    }

    private suspend fun request(
        endpoint: String,
        params: List<Pair<String, Any?>> = emptyList(),
        timeoutMs: Long = 30_000,
    ): JsonObject {
        val url = buildUrl(endpoint, params)
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val client = http.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        val response = try {
            client.newCall(request).await()
        } catch (err: IOException) {
            if (err is NetworkException) throw err
            val reason = describeError(err)
            throw NetworkException(
                if (err is java.io.InterruptedIOException && err !is java.net.SocketTimeoutException) {
                    "The server did not answer within ${timeoutMs / 1000}s."
                } else {
                    reason
                },
                err,
            )
        }
        response.use { res ->
            if (!res.isSuccessful) {
                throw NetworkException("Server responded with HTTP ${res.code} ${res.message}.".trim())
            }
            val text = res.body.string()
            val root = try {
                json.parseToJsonElement(text).jsonObject
            } catch (err: Exception) {
                throw NetworkException(
                    "The server sent something that is not a Subsonic JSON response. Is the address pointing at Navidrome?",
                    err,
                )
            }
            val body = root["subsonic-response"] as? JsonObject
                ?: throw NetworkException("Malformed response: no “subsonic-response” envelope.")
            val status = (body["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (status == "failed") {
                val error = body["error"] as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.intOrNull ?: 0
                val message = (error?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Unknown error"
                throw SubsonicException(code, message)
            }
            return body
        }
    }

    private inline fun <reified T> JsonObject.decode(key: String): T? {
        val element: JsonElement = this[key] ?: return null
        return json.decodeFromJsonElement<T>(element)
    }

    private inline fun <reified T> JsonObject.decodeList(outer: String, inner: String): List<T> {
        val container = this[outer] as? JsonObject ?: return emptyList()
        val element = container[inner] ?: return emptyList()
        return json.decodeFromJsonElement<List<T>>(element)
    }

    // ------------------------------------------------------------ system --

    suspend fun ping(): ServerInfo {
        val body = request("ping", timeoutMs = 15_000)
        return json.decodeFromJsonElement<ServerInfo>(body)
    }

    suspend fun getUser(username: String = credentials.username): SubsonicUser? =
        request("getUser", listOf("username" to username)).decode("user")

    suspend fun getScanStatus(): ScanStatus =
        request("getScanStatus").decode("scanStatus") ?: ScanStatus()

    suspend fun startScan(fullScan: Boolean = false): ScanStatus =
        request("startScan", listOf("fullScan" to fullScan)).decode("scanStatus") ?: ScanStatus(scanning = true)

    // ---------------------------------------------------------- browsing --

    suspend fun getArtists(): List<Artist> {
        val body = request("getArtists", timeoutMs = 60_000)
        val indexes = body.decodeList<ArtistIndex>("artists", "index")
        return indexes.flatMap { it.artist }
    }

    suspend fun getArtist(id: String): Artist? = request("getArtist", listOf("id" to id)).decode("artist")

    suspend fun getArtistInfo2(id: String, count: Int = 20): ArtistInfo? =
        request(
            "getArtistInfo2",
            listOf("id" to id, "count" to count, "includeNotPresent" to false),
        ).decode("artistInfo2")

    suspend fun getAlbum(id: String): Album? = request("getAlbum", listOf("id" to id)).decode("album")

    suspend fun getAlbumInfo2(id: String): AlbumInfo? =
        request("getAlbumInfo2", listOf("id" to id)).decode("albumInfo")

    suspend fun getSong(id: String): Song? = request("getSong", listOf("id" to id)).decode("song")

    suspend fun getAlbumList2(
        type: AlbumListType,
        size: Int = 100,
        offset: Int = 0,
        fromYear: Int? = null,
        toYear: Int? = null,
        genre: String? = null,
    ): List<Album> = request(
        "getAlbumList2",
        listOf(
            "type" to type.wire,
            "size" to size,
            "offset" to offset,
            "fromYear" to fromYear,
            "toYear" to toYear,
            "genre" to genre,
        ),
        timeoutMs = 60_000,
    ).decodeList("albumList2", "album")

    suspend fun getGenres(): List<Genre> = request("getGenres").decodeList("genres", "genre")

    suspend fun getRandomSongs(
        size: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Song> = request(
        "getRandomSongs",
        listOf("size" to size, "genre" to genre, "fromYear" to fromYear, "toYear" to toYear),
    ).decodeList("randomSongs", "song")

    suspend fun getSongsByGenre(genre: String, count: Int = 200, offset: Int = 0): List<Song> =
        request("getSongsByGenre", listOf("genre" to genre, "count" to count, "offset" to offset))
            .decodeList("songsByGenre", "song")

    suspend fun getStarred2(): Starred =
        request("getStarred2", timeoutMs = 60_000).decode("starred2") ?: Starred()

    suspend fun search3(
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 40,
        artistOffset: Int = 0,
        albumOffset: Int = 0,
        songOffset: Int = 0,
    ): SearchResult3 = request(
        "search3",
        listOf(
            "query" to query,
            "artistCount" to artistCount,
            "artistOffset" to artistOffset,
            "albumCount" to albumCount,
            "albumOffset" to albumOffset,
            "songCount" to songCount,
            "songOffset" to songOffset,
        ),
        timeoutMs = 60_000,
    ).decode("searchResult3") ?: SearchResult3()

    suspend fun getSimilarSongs2(id: String, count: Int = 50): List<Song> =
        request("getSimilarSongs2", listOf("id" to id, "count" to count)).decodeList("similarSongs2", "song")

    suspend fun getTopSongs(artist: String, count: Int = 50): List<Song> =
        request("getTopSongs", listOf("artist" to artist, "count" to count)).decodeList("topSongs", "song")

    // --------------------------------------------------------- playlists --

    suspend fun getPlaylists(): List<Playlist> = request("getPlaylists").decodeList("playlists", "playlist")

    suspend fun getPlaylist(id: String): Playlist? =
        request("getPlaylist", listOf("id" to id), timeoutMs = 60_000).decode("playlist")

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Playlist? =
        request("createPlaylist", listOf("name" to name, "songId" to songIds)).decode("playlist")

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdToAdd: List<String> = emptyList(),
        songIndexToRemove: List<Int> = emptyList(),
    ) {
        request(
            "updatePlaylist",
            listOf(
                "playlistId" to playlistId,
                "name" to name,
                "comment" to comment,
                "public" to isPublic,
                "songIdToAdd" to songIdToAdd,
                "songIndexToRemove" to songIndexToRemove,
            ),
        )
    }

    suspend fun deletePlaylist(id: String) {
        request("deletePlaylist", listOf("id" to id))
    }

    // ------------------------------------------------------- annotations --

    suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null) {
        request("star", listOf("id" to id, "albumId" to albumId, "artistId" to artistId))
    }

    suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null) {
        request("unstar", listOf("id" to id, "albumId" to albumId, "artistId" to artistId))
    }

    suspend fun setRating(id: String, rating: Int) {
        request("setRating", listOf("id" to id, "rating" to rating.coerceIn(0, 5)))
    }

    suspend fun scrobble(id: String, submission: Boolean, timeMs: Long = System.currentTimeMillis()) {
        request("scrobble", listOf("id" to id, "submission" to submission, "time" to timeMs))
    }

    // ------------------------------------------------------------- media --

    /**
     * Streaming URL for the player. Stable for the lifetime of this client.
     *
     * No `estimateContentLength`: when the server transcodes, it would announce
     * a size worked out from the bitrate and cut the connection there, and the
     * real output is often larger, so tracks stopped a moment before their end
     * and seeking failed. Without it the stream is read to its real end, and
     * the duration comes from the track's metadata.
     */
    fun streamUrl(id: String, maxBitRate: Int? = null, format: String? = null): String =
        buildUrl(
            "stream",
            listOf(
                "id" to id,
                "maxBitRate" to maxBitRate?.takeIf { it > 0 },
                "format" to format?.takeIf { it.isNotBlank() },
            ),
            stable = true,
        )

    /** Original-file download URL (never transcoded). */
    fun downloadUrl(id: String): String = buildUrl("download", listOf("id" to id), stable = true)

    /** Artwork URL. Stable, so the image cache keeps working. */
    fun coverArtUrl(coverArtId: String?, size: Int? = null): String? {
        if (coverArtId.isNullOrEmpty()) return null
        return buildUrl("getCoverArt", listOf("id" to coverArtId, "size" to size), stable = true)
    }

    suspend fun getLyrics(artist: String?, title: String?): Lyrics? =
        request("getLyrics", listOf("artist" to artist, "title" to title)).decode("lyrics")

    /** OpenSubsonic extension; Navidrome supports it and it can return synced lyrics. */
    suspend fun getLyricsBySongId(id: String): List<StructuredLyrics> =
        request("getLyricsBySongId", listOf("id" to id)).decodeList("lyricsList", "structuredLyrics")

    suspend fun getInternetRadioStations(): List<RadioStation> =
        request("getInternetRadioStations").decodeList("internetRadioStations", "internetRadioStation")

    suspend fun getNowPlaying(): List<Song> = request("getNowPlaying").decodeList("nowPlaying", "entry")
}
