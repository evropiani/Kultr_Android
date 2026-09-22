package app.kultr.android.data

import app.kultr.android.data.db.HistoryEntity
import app.kultr.core.api.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Listening history and scrobbling.
 *
 * Every play that reaches half the track (or four minutes) is written to the
 * local history, which feeds the Stats page, and sent to the server. Plays
 * made offline are kept and sent the next time the server is reachable.
 */
class Scrobbles(private val graph: AppGraph) {
    private val flushing = Mutex()

    /** Tell the server what is playing right now. Best-effort. */
    fun nowPlaying(song: Song) {
        if (song.isRadio || !graph.settings.current.scrobble) return
        val client = graph.auth.client.value ?: return
        graph.scope.launch(Dispatchers.IO) {
            runCatching { client.scrobble(song.id, submission = false) }
        }
    }

    /** A play counted: record it, bump the local play count, and submit it. */
    fun played(song: Song, seconds: Int, completed: Boolean, source: String) {
        if (song.isRadio) return
        val db = graph.database.value ?: return
        graph.scope.launch(Dispatchers.IO) {
            val playedAt = System.currentTimeMillis()
            val id = db.history().insert(
                HistoryEntity(
                    songId = song.id,
                    playedAt = playedAt,
                    seconds = seconds,
                    completed = completed,
                    source = source,
                    submitted = !graph.settings.current.scrobble,
                ),
            )
            db.library().bumpPlayCount(song.id, Instant.ofEpochMilli(playedAt).toString())
            if (!graph.settings.current.scrobble) return@launch
            val client = graph.auth.client.value ?: return@launch
            try {
                client.scrobble(song.id, submission = true, timeMs = playedAt)
                db.history().markSubmitted(id)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
                // Offline: flush() sends it later.
            }
        }
    }

    /** Send plays that could not be submitted when they happened. */
    suspend fun flush() {
        if (!graph.settings.current.scrobble) return
        val db = graph.database.value ?: return
        val client = graph.auth.client.value ?: return
        withContext(Dispatchers.IO) {
            flushing.withLock {
                val pending = db.history().unsubmitted(200)
                for (entry in pending) {
                    try {
                        client.scrobble(entry.songId, submission = true, timeMs = entry.playedAt)
                        db.history().markSubmitted(entry.id)
                    } catch (err: CancellationException) {
                        throw err
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }
    }
}
