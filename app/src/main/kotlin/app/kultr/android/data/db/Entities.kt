package app.kultr.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Genre
import app.kultr.core.api.Playlist
import app.kultr.core.api.ReplayGain
import app.kultr.core.api.Song
import app.kultr.core.dsp.BpmSource
import app.kultr.core.dsp.KeyMode
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.util.Format
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * The local mirror of the server's library. One database per server profile,
 * so switching servers never mixes two libraries.
 */

@Entity(
    tableName = "songs",
    indices = [Index("albumId"), Index("artistId"), Index("genre"), Index("starred"), Index("created"), Index("sortTitle")],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sortTitle: String,
    val album: String?,
    val artist: String?,
    val albumId: String?,
    val artistId: String?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val size: Long?,
    val contentType: String?,
    val suffix: String?,
    val duration: Int?,
    val bitRate: Int?,
    val samplingRate: Int?,
    val channelCount: Int?,
    val path: String?,
    val playCount: Long?,
    val played: String?,
    val created: String?,
    val starred: String?,
    val userRating: Int?,
    val bpm: Int?,
    val comment: String?,
    val trackGain: Double?,
    val albumGain: Double?,
    val trackPeak: Double?,
    val albumPeak: Double?,
)

@Entity(
    tableName = "albums",
    indices = [Index("artistId"), Index("starred"), Index("created"), Index("sortName"), Index("genre")],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortName: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int?,
    val duration: Int?,
    val playCount: Long?,
    val created: String?,
    val changed: String?,
    val starred: String?,
    val year: Int?,
    val genre: String?,
    val userRating: Int?,
    val isCompilation: Boolean?,
)

@Entity(tableName = "artists", indices = [Index("sortName"), Index("starred")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortName: String,
    val coverArt: String?,
    val artistImageUrl: String?,
    val albumCount: Int?,
    val starred: String?,
    val userRating: Int?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val isPublic: Boolean?,
    val songCount: Int?,
    val duration: Int?,
    val created: String?,
    val changed: String?,
    val coverArt: String?,
    /** JSON array of song ids, in playlist order. Empty when contents were not synced. */
    val entryIds: String,
    /** Order the server listed the playlists in. */
    val position: Int,
)

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val value: String,
    val songCount: Int?,
    val albumCount: Int?,
)

@Entity(tableName = "analysis")
data class AnalysisEntity(
    @PrimaryKey val songId: String,
    val version: Int,
    val analysedAt: Long,
    val bpmSource: String,
    val duration: Double,
    val bpm: Double,
    val bpmConfidence: Double,
    val beatOffset: Double,
    val downbeatOffset: Double,
    val outroDownbeat: Double,
    val musicalKey: Int,
    val keyName: String,
    val mode: String,
    val keyConfidence: Double,
    val camelot: String,
    val energy: Double,
    val brightness: Double,
    val peak: Double,
    val introEnd: Double,
    val outroStart: Double,
)

@Entity(tableName = "history", indices = [Index("songId"), Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
    /** Seconds actually listened to. */
    val seconds: Int,
    val completed: Boolean,
    val source: String,
    /** Whether the server has been told (scrobbles made offline are sent later). */
    val submitted: Boolean,
)

object DownloadState {
    const val QUEUED = "queued"
    const val DONE = "done"
    const val FAILED = "failed"
}

@Entity(tableName = "downloads", indices = [Index("state")])
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val state: String,
    /** Absolute path of the stored file, once downloaded. */
    val path: String?,
    val size: Long,
    val contentType: String?,
    val requestedAt: Long,
    val savedAt: Long?,
    val error: String?,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

// ------------------------------------------------------------------ mapping --

private val idListSerializer = ListSerializer(String.serializer())
private val mapperJson = Json { ignoreUnknownKeys = true }

fun encodeIds(ids: List<String>): String = mapperJson.encodeToString(idListSerializer, ids)

fun decodeIds(text: String): List<String> =
    if (text.isBlank()) emptyList() else runCatching { mapperJson.decodeFromString(idListSerializer, text) }.getOrDefault(emptyList())

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    sortTitle = Format.sortKey(title),
    album = album,
    artist = artist,
    albumId = albumId,
    artistId = artistId,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    coverArt = coverArt,
    size = size,
    contentType = contentType,
    suffix = suffix,
    duration = duration,
    bitRate = bitRate,
    samplingRate = samplingRate,
    channelCount = channelCount,
    path = path,
    playCount = playCount,
    played = played,
    created = created,
    starred = starred,
    userRating = userRating,
    bpm = bpm,
    comment = comment,
    trackGain = replayGain?.trackGain,
    albumGain = replayGain?.albumGain,
    trackPeak = replayGain?.trackPeak,
    albumPeak = replayGain?.albumPeak,
)

fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    album = album,
    artist = artist,
    albumId = albumId,
    artistId = artistId,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    coverArt = coverArt,
    size = size,
    contentType = contentType,
    suffix = suffix,
    duration = duration,
    bitRate = bitRate,
    samplingRate = samplingRate,
    channelCount = channelCount,
    path = path,
    playCount = playCount,
    played = played,
    created = created,
    starred = starred,
    userRating = userRating,
    bpm = bpm,
    comment = comment,
    replayGain = if (trackGain != null || albumGain != null) ReplayGain(trackGain, albumGain, trackPeak, albumPeak) else null,
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    sortName = Format.sortKey(sortName ?: name),
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    duration = duration,
    playCount = playCount,
    created = created,
    changed = changed,
    starred = starred,
    year = year,
    genre = genre,
    userRating = userRating,
    isCompilation = isCompilation,
)

fun AlbumEntity.toAlbum(): Album = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    duration = duration,
    playCount = playCount,
    created = created,
    changed = changed,
    starred = starred,
    year = year,
    genre = genre,
    userRating = userRating,
    isCompilation = isCompilation,
)

fun Artist.toEntity(): ArtistEntity = ArtistEntity(
    id = id,
    name = name,
    sortName = Format.sortKey(sortName ?: name),
    coverArt = coverArt,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    starred = starred,
    userRating = userRating,
)

fun ArtistEntity.toArtist(): Artist = Artist(
    id = id,
    name = name,
    coverArt = coverArt,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    starred = starred,
    userRating = userRating,
)

fun Playlist.toEntity(position: Int): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    comment = comment,
    owner = owner,
    isPublic = isPublic,
    songCount = songCount,
    duration = duration,
    created = created,
    changed = changed,
    coverArt = coverArt,
    entryIds = encodeIds(entry?.map { it.id }.orEmpty()),
    position = position,
)

fun PlaylistEntity.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    comment = comment,
    owner = owner,
    isPublic = isPublic,
    songCount = songCount,
    duration = duration,
    created = created,
    changed = changed,
    coverArt = coverArt,
)

fun Genre.toEntity(): GenreEntity = GenreEntity(value, songCount, albumCount)

fun GenreEntity.toGenre(): Genre = Genre(value, songCount, albumCount)

fun TrackAnalysis.toEntity(): AnalysisEntity = AnalysisEntity(
    songId = songId,
    version = version,
    analysedAt = analysedAt,
    bpmSource = bpmSource.name,
    duration = duration,
    bpm = bpm,
    bpmConfidence = bpmConfidence,
    beatOffset = beatOffset,
    downbeatOffset = downbeatOffset,
    outroDownbeat = outroDownbeat,
    musicalKey = key,
    keyName = keyName,
    mode = mode.name,
    keyConfidence = keyConfidence,
    camelot = camelot,
    energy = energy,
    brightness = brightness,
    peak = peak,
    introEnd = introEnd,
    outroStart = outroStart,
)

fun AnalysisEntity.toAnalysis(): TrackAnalysis = TrackAnalysis(
    songId = songId,
    version = version,
    analysedAt = analysedAt,
    bpmSource = runCatching { BpmSource.valueOf(bpmSource) }.getOrDefault(BpmSource.DSP),
    duration = duration,
    bpm = bpm,
    bpmConfidence = bpmConfidence,
    beatOffset = beatOffset,
    downbeatOffset = downbeatOffset,
    outroDownbeat = outroDownbeat,
    key = musicalKey,
    keyName = keyName,
    mode = runCatching { KeyMode.valueOf(mode) }.getOrDefault(KeyMode.MAJOR),
    keyConfidence = keyConfidence,
    camelot = camelot,
    energy = energy,
    brightness = brightness,
    peak = peak,
    introEnd = introEnd,
    outroStart = outroStart,
)
