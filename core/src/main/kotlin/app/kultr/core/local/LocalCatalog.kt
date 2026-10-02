package app.kultr.core.local

import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Genre
import app.kultr.core.api.Song
import app.kultr.core.api.md5Hex
import java.time.Instant

/**
 * One audio file found in a music folder on the phone, with what its tags say.
 * Everything from the tags is optional: untagged files still get a title from
 * their file name and an album from their folder.
 */
data class LocalTrack(
    /** Where the file is (a content URI on Android). Also what identifies it. */
    val uri: String,
    val fileName: String,
    /** Identifies the folder the file is in, and that folder's name. */
    val folderKey: String,
    val folderName: String,
    /** Last modified, in milliseconds since the epoch. */
    val modified: Long,
    val size: Long,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    /** Bits per second. */
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val mimeType: String? = null,
)

/** A local library in the shapes the rest of Kultr knows from a server. */
data class LocalCatalog(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>,
)

/**
 * Turns the tracks found in the chosen folders into songs, albums, artists
 * and genres, as a Subsonic server would list them, so the library pages,
 * home shelves, search and InjeKt work the same on local music.
 *
 * Albums are grouped the way most players do it: by album name and album
 * artist when the files say who the album is by, otherwise by album name
 * within one folder, so two different "Greatest Hits" in two folders stay
 * apart while a two-disc album in CD1 and CD2 folders stays together.
 */
object LocalCatalogBuilder {
    const val SONG_PREFIX = "local:"
    const val ALBUM_PREFIX = "local-album:"
    const val ARTIST_PREFIX = "local-artist:"
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val VARIOUS_ARTISTS = "Various artists"

    fun songId(uri: String): String = SONG_PREFIX + md5Hex(uri).take(24)

    fun artistId(name: String): String = ARTIST_PREFIX + md5Hex(name.trim().lowercase()).take(16)

    /**
     * The album a track belongs to: by name and album artist when the file
     * names one, otherwise by name within the track's folder.
     */
    fun albumId(albumName: String, albumArtist: String?, folderKey: String): String {
        val key = if (albumArtist != null) {
            "a|${albumName.lowercase()}|${albumArtist.lowercase()}"
        } else {
            "f|${albumName.lowercase()}|$folderKey"
        }
        return ALBUM_PREFIX + md5Hex(key).take(16)
    }

    /**
     * Build the catalogue. [covers] maps a folder key to the image to use as
     * the cover for albums in that folder (cover.jpg and the like). [previous]
     * gives what was known about a song before (play counts, favourites,
     * ratings), which the files themselves cannot say.
     */
    fun build(
        tracks: List<LocalTrack>,
        covers: Map<String, String> = emptyMap(),
        previous: (songId: String) -> Song? = { null },
    ): LocalCatalog {
        data class Grouped(val id: String, val tracks: MutableList<LocalTrack> = mutableListOf())

        val groups = LinkedHashMap<String, Grouped>()
        for (track in tracks.distinctBy { it.uri }) {
            val albumName = track.album?.trim()?.takeIf { it.isNotEmpty() } ?: track.folderName
            val albumArtist = track.albumArtist?.trim()?.takeIf { it.isNotEmpty() }
            val id = albumId(albumName, albumArtist, track.folderKey)
            groups.getOrPut(id) { Grouped(id) }.tracks += track
        }

        val songs = ArrayList<Song>(tracks.size)
        val albums = ArrayList<Album>(groups.size)
        for (group in groups.values) {
            val first = group.tracks.first()
            val albumName = first.album?.trim()?.takeIf { it.isNotEmpty() } ?: first.folderName
            val trackArtists = group.tracks.mapNotNull { it.artist?.trim()?.takeIf { a -> a.isNotEmpty() } }.distinct()
            val albumArtist = first.albumArtist?.trim()?.takeIf { it.isNotEmpty() }
                ?: when (trackArtists.size) {
                    0 -> UNKNOWN_ARTIST
                    1 -> trackArtists.single()
                    else -> VARIOUS_ARTISTS
                }
            val albumId = group.id
            val albumArtistId = artistId(albumArtist)
            val cover = group.tracks.firstNotNullOfOrNull { covers[it.folderKey] }

            val albumSongs = group.tracks.map { track ->
                val id = songId(track.uri)
                val before = previous(id)
                val artist = track.artist?.trim()?.takeIf { it.isNotEmpty() } ?: albumArtist
                Song(
                    id = id,
                    parent = albumId,
                    title = track.title?.trim()?.takeIf { it.isNotEmpty() } ?: track.fileName.substringBeforeLast('.'),
                    album = albumName,
                    artist = artist,
                    albumId = albumId,
                    // Artist pages list album artists, so every track links to its album's.
                    artistId = albumArtistId,
                    track = track.track,
                    discNumber = track.disc,
                    year = track.year,
                    genre = track.genre?.trim()?.takeIf { it.isNotEmpty() },
                    coverArt = cover,
                    size = track.size,
                    contentType = track.mimeType,
                    suffix = track.fileName.substringAfterLast('.', "").lowercase().ifEmpty { null },
                    duration = track.durationMs?.let { ((it + 500) / 1000).toInt() },
                    bitRate = track.bitRate?.let { it / 1000 },
                    samplingRate = track.sampleRate,
                    path = track.uri,
                    created = Instant.ofEpochMilli(track.modified).toString(),
                    playCount = before?.playCount,
                    played = before?.played,
                    starred = before?.starred,
                    userRating = before?.userRating,
                )
            }.sortedWith(compareBy<Song> { it.discNumber ?: 1 }.thenBy { it.track ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
            songs += albumSongs

            val genre = albumSongs.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            albums += Album(
                id = albumId,
                name = albumName,
                artist = albumArtist,
                artistId = albumArtistId,
                coverArt = cover,
                songCount = albumSongs.size,
                duration = albumSongs.sumOf { it.duration ?: 0 },
                playCount = albumSongs.sumOf { it.playCount ?: 0L }.takeIf { it > 0 },
                played = albumSongs.mapNotNull { it.played }.maxOrNull(),
                created = albumSongs.mapNotNull { it.created }.maxOrNull(),
                year = albumSongs.mapNotNull { it.year }.maxOrNull(),
                genre = genre,
                isCompilation = albumArtist == VARIOUS_ARTISTS,
            )
        }

        val artists = albums.groupBy { it.artistId.orEmpty() }.map { (id, own) ->
            Artist(id = id, name = own.first().artist.orEmpty(), coverArt = own.firstNotNullOfOrNull { it.coverArt }, albumCount = own.size)
        }

        val genres = songs.filter { it.genre != null }.groupBy { it.genre.orEmpty() }.map { (name, inGenre) ->
            Genre(value = name, songCount = inGenre.size, albumCount = inGenre.mapNotNull { it.albumId }.distinct().size)
        }.sortedByDescending { it.songCount }

        return LocalCatalog(songs, albums, artists, genres)
    }
}
