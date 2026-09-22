package app.kultr.android.playback

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(val index: Int, val song: Song, val key: String)

data class PlayerUiState(
    val connected: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val index: Int = -1,
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0,
) {
    val hasMedia: Boolean get() = current != null
    val upNext: List<QueueEntry> get() = if (index < 0) queue else queue.drop(index + 1)
}

/**
 * The UI's side of playback: a MediaController connected to [PlaybackService],
 * turned into a [StateFlow] the screens can collect, plus the handful of
 * commands they need. Commands issued before the connection is up are queued.
 */
class PlayerConnection(private val graph: AppGraph) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    fun connect() {
        if (future != null) return
        val context = graph.app
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            {
                val c = runCatching { f.get() }.getOrNull()
                if (c == null) {
                    future = null
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                publish(c)
                while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = PlayerUiState()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
        } else {
            pending.addLast(action)
            connect()
        }
    }

    private fun publish(player: Player) {
        val timeline = player.currentTimeline
        val window = Timeline.Window()
        val queue = ArrayList<QueueEntry>(timeline.windowCount)
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            val song = MediaItems.songOf(window.mediaItem) ?: continue
            queue += QueueEntry(i, song, "$i:${song.id}")
        }
        val index = if (timeline.isEmpty) -1 else player.currentMediaItemIndex
        val current = player.currentMediaItem?.let { MediaItems.songOf(it) }
        _state.value = PlayerUiState(
            connected = true,
            queue = queue,
            index = index,
            current = current,
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            ended = player.playbackState == Player.STATE_ENDED,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: ((current?.duration ?: 0) * 1000L),
        )
    }

    /** Current position; read it on a timer, it is not part of [state]. */
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    private fun items(songs: List<Song>): List<MediaItem> {
        val client = graph.auth.client.value
        return songs.map { MediaItems.from(it, client) }
    }

    // ------------------------------------------------------------ commands --

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.shuffleModeEnabled != shuffle) c.shuffleModeEnabled = shuffle
            c.setMediaItems(items(songs), startIndex.coerceIn(0, songs.size - 1), 0)
            c.prepare()
            c.play()
        }
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items(songs))
                c.prepare()
                c.play()
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, items(songs))
            }
        }
        graph.messages.show(if (songs.size == 1) "“${songs[0].title}” plays next" else "${songs.size} tracks play next")
    }

    fun enqueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items(songs))
                c.prepare()
                c.play()
            } else {
                c.addMediaItems(items(songs))
            }
        }
        graph.messages.show(if (songs.size == 1) "Added “${songs[0].title}” to the queue" else "Added ${songs.size} tracks to the queue")
    }

    fun toggle() = withController { c ->
        when {
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition(0)
                c.play()
            }
            c.playWhenReady -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun jumpTo(index: Int) = withController { c ->
        c.seekTo(index, 0)
        c.play()
    }

    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }

    fun remove(index: Int) = withController { it.removeMediaItem(index) }

    /** Drop everything after the current track. */
    fun clearUpcoming() = withController { c ->
        val start = c.currentMediaItemIndex + 1
        if (start < c.mediaItemCount) c.removeMediaItems(start, c.mediaItemCount)
    }

    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = withController { c ->
        c.pause()
        c.clearMediaItems()
    }
}
