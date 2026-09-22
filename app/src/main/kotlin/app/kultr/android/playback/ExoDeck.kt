package app.kultr.android.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import app.kultr.android.AppGraph
import app.kultr.core.engine.Deck
import app.kultr.core.engine.DeckListener
import app.kultr.core.engine.DeckStatus
import app.kultr.core.engine.QueueItem
import java.io.File
import kotlin.math.abs

/**
 * Where a queue item's audio comes from: the offline copy when there is one,
 * the (cached) Subsonic stream otherwise, or the station URL for radio.
 */
@UnstableApi
class DeckSources(context: Context, private val graph: AppGraph) {
    private val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
    private val http = OkHttpDataSource.Factory(graph.http)
    private val plain = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
    private val cached = DefaultMediaSourceFactory(
        CacheDataSource.Factory()
            .setCache(graph.mediaCache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS),
        extractors,
    )

    fun sourceFor(item: QueueItem): MediaSource {
        val song = item.song
        val builder = MediaItem.Builder().setMediaId("q:${item.uid}").setTag(item)
        song.kultrStreamUrl?.let { url ->
            return plain.createMediaSource(builder.setUri(url).build())
        }
        val settings = graph.settings.current
        if (settings.offlineFirst) {
            graph.offline.fileFor(song.id)?.let { file ->
                return plain.createMediaSource(builder.setUri(Uri.fromFile(file)).build())
            }
        }
        val client = graph.auth.client.value
            ?: throw IllegalStateException("Not signed in")
        val bitrate = settings.bitrateFor(graph.network.isMetered)
        val format = settings.preferredFormat.takeIf { bitrate > 0 && it.isNotBlank() }
        val url = client.streamUrl(song.id, bitrate.takeIf { it > 0 }, format)
        val cacheKey = "song:${graph.auth.active.value?.id}:${song.id}:$bitrate:${format.orEmpty()}"
        return cached.createMediaSource(builder.setUri(url).setCustomCacheKey(cacheKey).build())
    }

    fun hasLocalCopy(item: QueueItem): Boolean = graph.offline.fileFor(item.song.id)?.let(File::isFile) == true
}

/** One of the engine's two players: an ExoPlayer with [DeckProcessor] in its audio sink. */
@UnstableApi
class ExoDeck(
    context: Context,
    override val name: String,
    private val sources: DeckSources,
    eq: EqState,
) : Deck, Player.Listener {
    private val processor = DeckProcessor(eq)
    val player: ExoPlayer

    private var current: QueueItem? = null
    private var following: QueueItem? = null
    private var listener: DeckListener? = null
    private var level = 0f
    private var duck = 1f
    private var appliedRate = 1f

    init {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(processor))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        player = ExoPlayer.Builder(context, renderers)
            // Focus is handled once, by the engine's player, not per deck.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(this)
    }

    override val item: QueueItem? get() = current

    override val status: DeckStatus
        get() = when {
            player.playerError != null -> DeckStatus.ERROR
            current == null -> DeckStatus.IDLE
            else -> when (player.playbackState) {
                Player.STATE_BUFFERING -> DeckStatus.BUFFERING
                Player.STATE_READY -> DeckStatus.READY
                Player.STATE_ENDED -> DeckStatus.ENDED
                else -> DeckStatus.BUFFERING
            }
        }

    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.let { if (it == C.TIME_UNSET) -1 else it }
    override val bufferedPositionMs: Long get() = player.bufferedPosition
    override val isPlaying: Boolean get() = player.isPlaying

    override fun load(item: QueueItem, startMs: Long) {
        current = item
        following = null
        player.playWhenReady = false
        try {
            player.setMediaSource(sources.sourceFor(item), startMs)
            player.prepare()
        } catch (err: IllegalStateException) {
            listener?.onError(this, "Sign in to a server to play this.")
        }
    }

    override fun play() {
        if (player.playbackState == Player.STATE_IDLE && current != null) player.prepare()
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun stop() {
        current = null
        following = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    override fun setNext(item: QueueItem?) {
        if (player.mediaItemCount > 1) player.removeMediaItems(1, player.mediaItemCount)
        following = null
        if (item != null && current != null) {
            try {
                player.addMediaSource(sources.sourceFor(item))
                following = item
            } catch (_: IllegalStateException) {
                // Not signed in any more; the engine falls back to a normal load.
            }
        }
    }

    override fun setLevel(level: Float) {
        this.level = level
        processor.targetGain = level * duck
    }

    /** Lower everything while another app briefly needs the speaker. */
    fun setDuck(duck: Float) {
        this.duck = duck
        processor.targetGain = level * duck
    }

    override fun setRate(rate: Float) {
        // Sonic resamples on every change; small steps are inaudible anyway.
        val snap = rate == 1f && appliedRate != 1f
        if (abs(rate - appliedRate) >= 0.0015f || snap) {
            player.playbackParameters = PlaybackParameters(rate)
            appliedRate = rate
        }
    }

    override fun setBassDb(db: Float) {
        processor.bassDb = db
    }

    override fun setSweepHz(hz: Float) {
        processor.sweepHz = hz
    }

    override fun setListener(listener: DeckListener?) {
        this.listener = listener
    }

    fun release() {
        listener = null
        player.removeListener(this)
        player.release()
    }

    // ------------------------------------------------------ Player.Listener --

    override fun onPlaybackStateChanged(playbackState: Int) {
        listener?.onStatusChanged(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        listener?.onStatusChanged(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (current == null) return
        listener?.onError(this, describe(error))
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
        val next = following ?: return
        current = next
        following = null
        // Drop the finished file so the current one is always first.
        val index = player.currentMediaItemIndex
        if (index > 0) player.removeMediaItems(0, index)
        listener?.onAutoAdvanced(this, next)
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The server refused to stream “${current?.song?.title}”."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "The connection to your server dropped while streaming."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The offline copy of “${current?.song?.title}” is missing."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "This phone cannot play “${current?.song?.title}”. Try a transcode format in Settings → Audio."
        else -> "Playback failed: ${error.errorCodeName}."
    }
}
