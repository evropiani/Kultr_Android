package app.kultr.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import org.json.JSONObject

/**
 * Playing on a Chromecast or speaker group. The receiver fetches audio
 * straight from the server, so it has to be able to reach it (the same
 * network, or a public address).
 */
object CastSupport {
    /** What Cast receivers play natively; anything else is transcoded to MP3. */
    private val native = setOf("mp3", "aac", "m4a", "mp4", "flac", "ogg", "oga", "opus", "wav", "webm")

    /** URL and MIME type for a receiver, or null when there is no way to stream [song]. */
    fun streamFor(song: Song, client: SubsonicClient?): Pair<String, String>? {
        song.kultrStreamUrl?.let { return it to (song.contentType ?: "audio/mpeg") }
        client ?: return null
        val suffix = song.suffix?.lowercase()
        return if (suffix != null && suffix in native) {
            client.streamUrl(song.id) to (song.contentType ?: "audio/$suffix")
        } else {
            client.streamUrl(song.id, 320, "mp3") to "audio/mpeg"
        }
    }
}

/**
 * Converts queue items to Cast and back, carrying Kultr's song JSON along
 * so the app still knows exactly which track is playing on the receiver.
 */
@UnstableApi
class KultrMediaItemConverter : MediaItemConverter {
    private val base = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val item = base.toMediaQueueItem(mediaItem)
        val song = mediaItem.mediaMetadata.extras?.getString(MediaItems.EXTRA_SONG) ?: return item
        val info = item.media ?: return item
        val custom = JSONObject(info.customData?.toString() ?: "{}").put(KEY_SONG, song)
        val builder = MediaInfo.Builder(info.contentId).setCustomData(custom)
        info.contentType?.let { builder.setContentType(it) }
        info.metadata?.let { builder.setMetadata(it) }
        info.contentUrl?.let { builder.setContentUrl(it) }
        if (info.streamType == MediaInfo.STREAM_TYPE_LIVE) builder.setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        return MediaQueueItem.Builder(builder.build()).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val item = base.toMediaItem(mediaQueueItem)
        val song = mediaQueueItem.media?.customData?.optString(KEY_SONG)?.takeIf { it.isNotEmpty() } ?: return item
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(MediaItems.EXTRA_SONG, song) }
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    private companion object {
        const val KEY_SONG = "kultrSong"
    }
}

/**
 * Moves the queue between this phone and a receiver when a Cast session
 * starts or ends.
 *
 * Tracks get a receiver-friendly stream URL on the way out. A very long
 * queue travels as a window around the current track, because Cast limits
 * the size of each message. Shuffle is not carried over: Kultr's queue is
 * already in shuffled order, and the receiver would shuffle it again.
 */
@UnstableApi
class KultrTransferCallback(private val streamFor: (Song) -> Pair<String, String>?) : CastPlayer.TransferCallback {
    override fun transferState(sourcePlayer: Player, targetPlayer: Player) {
        val state = PlayerTransferState.fromPlayer(sourcePlayer)
        val items = state.mediaItems
        val current = state.currentMediaItemIndex.coerceAtLeast(0)
        val from = (current - WINDOW_BEFORE).coerceAtLeast(0)
        val to = (current + WINDOW_AFTER).coerceAtMost(items.size)

        val window = ArrayList<MediaItem>(to - from)
        var newIndex = C.INDEX_UNSET
        var playableBefore = 0
        for (i in from until to) {
            val item = playable(items[i]) ?: continue
            if (i == current) newIndex = window.size
            if (i < current) playableBefore++
            window += item
        }

        val builder = state.buildUpon().setMediaItems(window).setShuffleModeEnabled(false)
        when {
            window.isEmpty() -> builder.setCurrentMediaItemIndex(C.INDEX_UNSET)
            newIndex == C.INDEX_UNSET -> builder
                .setCurrentMediaItemIndex(playableBefore.coerceAtMost(window.size - 1))
                .setCurrentPosition(0)
            else -> builder.setCurrentMediaItemIndex(newIndex)
        }
        builder.build().setToPlayer(targetPlayer)
    }

    private fun playable(item: MediaItem): MediaItem? {
        val local = item.localConfiguration
        if (local != null && local.uri != Uri.EMPTY) return item
        val song = MediaItems.songOf(item) ?: return null
        val (url, mime) = streamFor(song) ?: return null
        return item.buildUpon().setUri(url).setMimeType(mime).build()
    }

    private companion object {
        const val WINDOW_BEFORE = 10
        const val WINDOW_AFTER = 100
    }
}
