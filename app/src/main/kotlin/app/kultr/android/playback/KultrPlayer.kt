package app.kultr.android.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import app.kultr.core.api.Song
import app.kultr.core.engine.EngineStatus
import app.kultr.core.engine.PlaybackEngine
import app.kultr.core.engine.QueueItem
import app.kultr.core.engine.RepeatMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player the media session sees. It presents Kultr's two-deck engine as
 * one ordinary Media3 player, so the notification, lock screen, Bluetooth
 * buttons and Android Auto all work without knowing there are two decks
 * behind it.
 */
@UnstableApi
class KultrPlayer(
    private val context: Context,
    looper: Looper,
    private val toMediaItem: (Song) -> MediaItem,
    private val onDuck: (Float) -> Unit,
) : SimpleBasePlayer(looper) {
    lateinit var engine: PlaybackEngine

    private val itemData = HashMap<Long, Pair<Long, MediaItemData>>()
    private val mediaItems = HashMap<Long, MediaItem>()

    fun refresh() = invalidateState()

    // ------------------------------------------------------------- state --

    override fun getState(): State {
        val e = engine
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(e.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(
                when (e.repeat) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                },
            )
            .setShuffleModeEnabled(e.shuffle)
            .setAudioAttributes(ATTRIBUTES)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setMaxSeekToPreviousPositionMs(3_000)

        val queue = e.queue
        if (queue.isEmpty()) {
            itemData.clear()
            mediaItems.clear()
            return builder.setPlaybackState(Player.STATE_IDLE).setPlaylist(emptyList()).build()
        }

        val index = e.index.coerceIn(0, queue.size - 1)
        val live = queue.map { it.uid }.toHashSet()
        itemData.keys.retainAll(live)
        mediaItems.keys.retainAll(live)

        val playlist = queue.mapIndexed { i, item ->
            val durationUs = if (i == index) {
                e.durationMs.takeIf { it > 0 }?.times(1000) ?: C.TIME_UNSET
            } else {
                item.song.duration?.takeIf { it > 0 }?.let { it * 1_000_000L } ?: C.TIME_UNSET
            }
            val cached = itemData[item.uid]
            if (cached != null && cached.first == durationUs) {
                cached.second
            } else {
                val data = MediaItemData.Builder(item.uid)
                    .setMediaItem(mediaItemFor(item))
                    .setDurationUs(durationUs)
                    .setIsSeekable(!item.song.isRadio)
                    .setIsDynamic(item.song.isRadio)
                    .build()
                itemData[item.uid] = durationUs to data
                data
            }
        }

        return builder
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(index)
            .setPlaybackState(
                when (e.status) {
                    EngineStatus.IDLE -> Player.STATE_IDLE
                    EngineStatus.BUFFERING -> Player.STATE_BUFFERING
                    EngineStatus.READY -> Player.STATE_READY
                    EngineStatus.ENDED -> Player.STATE_ENDED
                },
            )
            .setIsLoading(e.status == EngineStatus.BUFFERING)
            .setContentPositionMs(PositionSupplier { engine.positionMs })
            .setContentBufferedPositionMs(PositionSupplier { engine.bufferedPositionMs })
            .build()
    }

    private fun mediaItemFor(item: QueueItem): MediaItem =
        mediaItems.getOrPut(item.uid) { toMediaItem(item.song) }

    private fun songsOf(items: List<MediaItem>): List<Song> = items.mapNotNull { MediaItems.songOf(it) }

    // ----------------------------------------------------------- commands --

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            if (requestFocus()) {
                engine.setPlayWhenReady(true)
                registerNoisy()
            }
        } else {
            resumeOnFocusGain = false
            engine.setPlayWhenReady(false)
            unregisterNoisy()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        engine.prepare()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.stop()
        abandonFocus()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        abandonFocus()
        unregisterNoisy()
        engine.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        engine.setRepeat(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        engine.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val items = engine.newItems(songsOf(mediaItems))
        val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        val position = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        engine.setQueue(items, index, position)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        engine.addItems(index, engine.newItems(songsOf(mediaItems)))
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        engine.moveRange(fromIndex, toIndex, newIndex)
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        engine.removeRange(fromIndex, toIndex)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val manual = seekCommand == Player.COMMAND_SEEK_TO_NEXT ||
            seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
            seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
            seekCommand == Player.COMMAND_SEEK_TO_MEDIA_ITEM
        val index = if (mediaItemIndex == C.INDEX_UNSET) engine.index else mediaItemIndex
        engine.seekTo(index, if (positionMs == C.TIME_UNSET) 0 else positionMs, manual)
        return Futures.immediateVoidFuture()
    }

    // --------------------------------------------------------- audio focus --

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                onDuck(1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    engine.setPlayWhenReady(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                resumeOnFocusGain = false
                engine.setPlayWhenReady(false)
                unregisterNoisy()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (engine.playWhenReady) {
                    resumeOnFocusGain = true
                    engine.setPlayWhenReady(false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(0.25f)
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
            .also { focusRequest = it }
        hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        hasFocus = false
    }

    // ------------------------------------------------ headphones unplugged --

    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                engine.setPlayWhenReady(false)
                unregisterNoisy()
            }
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    private companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
