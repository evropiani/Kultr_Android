package app.kultr.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import kotlinx.serialization.json.Json

/**
 * How songs travel between the app, the session and other controllers
 * (notification, Android Auto, Bluetooth): as MediaItems that carry the whole
 * song as JSON, so nothing has to be looked up again on the other side.
 */
object MediaItems {
    const val EXTRA_SONG = "kultr.song"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun artworkUri(song: Song, client: SubsonicClient?, size: Int = 512): Uri? =
        client?.coverArtUrl(song.artworkId, size)?.let(Uri::parse)

    fun from(song: Song, client: SubsonicClient?): MediaItem {
        val extras = Bundle().apply { putString(EXTRA_SONG, json.encodeToString(Song.serializer(), song)) }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setAlbumArtist(song.artist)
            .setTrackNumber(song.track)
            .setDiscNumber(song.discNumber)
            .setReleaseYear(song.year)
            .setGenre(song.genre)
            .setArtworkUri(if (song.isRadio) null else artworkUri(song, client))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (song.isRadio) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun songOf(item: MediaItem): Song? {
        val text = item.mediaMetadata.extras?.getString(EXTRA_SONG)
            ?: item.requestMetadata.extras?.getString(EXTRA_SONG)
            ?: return null
        return runCatching { json.decodeFromString(Song.serializer(), text) }.getOrNull()
    }

    fun encode(song: Song): String = json.encodeToString(Song.serializer(), song)

    fun decode(text: String): Song? = runCatching { json.decodeFromString(Song.serializer(), text) }.getOrNull()
}
