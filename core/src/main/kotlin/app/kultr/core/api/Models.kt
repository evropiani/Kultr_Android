package app.kultr.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subsonic API shapes, narrowed to what Navidrome actually returns and what
 * Kultr actually uses. Everything optional is genuinely optional — Navidrome
 * omits empty fields rather than sending nulls.
 */

@Serializable
data class ReplayGain(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
)

@Serializable
data class Song(
    val id: String,
    val parent: String? = null,
    val title: String = "",
    val album: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val transcodedContentType: String? = null,
    val transcodedSuffix: String? = null,
    /** Seconds. */
    val duration: Int? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val channelCount: Int? = null,
    val path: String? = null,
    val playCount: Long? = null,
    val played: String? = null,
    val created: String? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val averageRating: Double? = null,
    val bpm: Int? = null,
    val comment: String? = null,
    val musicBrainzId: String? = null,
    val isVideo: Boolean? = null,
    val type: String? = null,
    val replayGain: ReplayGain? = null,
    /**
     * Kultr-only: play this exact URL instead of building a Subsonic stream
     * URL. Used for internet radio stations, which are not library tracks.
     */
    val kultrStreamUrl: String? = null,
) {
    val isStarred: Boolean get() = !starred.isNullOrEmpty()
    val isRadio: Boolean get() = kultrStreamUrl != null

    /** The artwork id to ask the server for: the song's own, else its album's. */
    val artworkId: String? get() = coverArt ?: albumId
}

@Serializable
data class Album(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val playCount: Long? = null,
    /** When any of its tracks was last played (OpenSubsonic; Navidrome sends it). */
    val played: String? = null,
    val created: String? = null,
    val changed: String? = null,
    val starred: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val userRating: Int? = null,
    val sortName: String? = null,
    val isCompilation: Boolean? = null,
    /** Present on getAlbum, absent on getAlbumList2. */
    val song: List<Song>? = null,
) {
    val isStarred: Boolean get() = !starred.isNullOrEmpty()
}

@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val userRating: Int? = null,
    val sortName: String? = null,
    val musicBrainzId: String? = null,
    val album: List<Album>? = null,
) {
    val isStarred: Boolean get() = !starred.isNullOrEmpty()
}

@Serializable
data class ArtistIndex(
    val name: String = "",
    val artist: List<Artist> = emptyList(),
)

@Serializable
data class ArtistInfo(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<Artist>? = null,
)

@Serializable
data class AlbumInfo(
    val notes: String? = null,
    val lastFmUrl: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    @SerialName("public") val isPublic: Boolean? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val entry: List<Song>? = null,
)

@Serializable
data class Genre(
    val value: String = "",
    val songCount: Int? = null,
    val albumCount: Int? = null,
)

@Serializable
data class ScanStatus(
    val scanning: Boolean = false,
    val count: Long? = null,
    val folderCount: Long? = null,
    val lastScan: String? = null,
)

@Serializable
data class SubsonicUser(
    val username: String = "",
    val email: String? = null,
    val scrobblingEnabled: Boolean? = null,
    val adminRole: Boolean? = null,
    val streamRole: Boolean? = null,
    val downloadRole: Boolean? = null,
    val playlistRole: Boolean? = null,
    val shareRole: Boolean? = null,
)

@Serializable
data class RadioStation(
    val id: String,
    val name: String = "",
    val streamUrl: String = "",
    val homePageUrl: String? = null,
)

@Serializable
data class Lyrics(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null,
)

@Serializable
data class StructuredLyricLine(
    /** Milliseconds from the start of the track, when synced. */
    val start: Long? = null,
    val value: String = "",
)

@Serializable
data class StructuredLyrics(
    val lang: String? = null,
    val synced: Boolean = false,
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    /** Milliseconds to add to every line's start. */
    val offset: Long? = null,
    val line: List<StructuredLyricLine>? = null,
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Starred(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class ServerInfo(
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
) {
    /** "Navidrome 0.53.3", or whatever best describes the server. */
    val description: String
        get() {
            val name = type?.replaceFirstChar { it.uppercase() } ?: "Subsonic"
            val ver = serverVersion ?: version
            return if (ver != null) "$name $ver" else name
        }
}

@Serializable
enum class AuthMode {
    /** md5(password + salt) on every request. The default and the safe choice. */
    @SerialName("token")
    TOKEN,

    /** Hex-encoded password. Only for reverse proxies that break token auth. */
    @SerialName("plain")
    PLAIN,
}

@Serializable
data class Credentials(
    /** Base URL of the server, no trailing slash. */
    val serverUrl: String,
    val username: String,
    val password: String,
    val authMode: AuthMode = AuthMode.TOKEN,
)

/** Radio stations are played through the same queue as library tracks. */
fun RadioStation.asSong(): Song = Song(
    id = "radio:$id",
    title = name,
    artist = "Internet radio",
    album = homePageUrl,
    kultrStreamUrl = streamUrl,
)
