package app.kultr.android.data

import app.kultr.android.AppGraph
import app.kultr.android.data.db.HistoryEntity
import app.kultr.android.data.db.KultrDatabase
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.SubsonicException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Listening history, and sending plays to the server.
 *
 * Every play that reaches half the track (or four minutes) is written to the
 * local history as waiting (`submitted = false`) and sent to the server with
 * the time it actually happened. If the server cannot be reached it stays
 * waiting and goes out later, still with its original time, and exactly
 * once: a play being sent is never picked up by a flush at the same moment.
 * Navidrome counts the plays, so every device (and this one, after a fresh
 * sync) sees the same "recently" and "most played".
 */
class Scrobbles(private val graph: AppGraph) {
    private val flushing = Mutex()

    /** History entries being sent right now. */
    private val inFlight: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Tell the server what is playing right now. Best-effort. */
    fun nowPlaying(song: Song) {
        if (song.isRadio || !graph.settings.current.scrobble) return
        val client = graph.auth.client.value ?: return
        graph.scope.launch(Dispatchers.IO) {
            runCatching { client.scrobble(song.id, submission = false) }
        }
    }

    /** A play counted: record it, bump the local play count, and send it. Internet radio is not a library track and is not sent. */
    fun played(song: Song, seconds: Int, completed: Boolean, source: String) {
        if (song.isRadio) return
        val db = graph.database.value ?: return
        graph.scope.launch(Dispatchers.IO) {
            val playedAt = System.currentTimeMillis()
            val sending = graph.settings.current.scrobble
            // Recorded and claimed together, so a flush running now cannot send it too.
            val id = flushing.withLock {
                val entry = HistoryEntity(
                    songId = song.id,
                    playedAt = playedAt,
                    seconds = seconds,
                    completed = completed,
                    source = source,
                    submitted = !sending,
                )
                db.history().insert(entry).also { if (sending) inFlight += it }
            }
            db.library().bumpPlayCount(song.id, Instant.ofEpochMilli(playedAt).toString())
            if (!sending) return@launch
            val client = graph.auth.client.value
            if (client == null) {
                inFlight -= id
                return@launch
            }
            try {
                send(client, db, id, song.id, playedAt)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
                // Offline: flush() sends it later, with the time it was played.
            } finally {
                inFlight -= id
            }
        }
    }

    /** Send plays that could not be sent when they happened. Returns how many went. */
    suspend fun flush(): Int {
        if (!graph.settings.current.scrobble) return 0
        val db = graph.database.value ?: return 0
        val client = graph.auth.client.value ?: return 0
        return withContext(Dispatchers.IO) {
            flushing.withLock {
                var sent = 0
                for (entry in db.history().unsubmitted(200)) {
                    if (entry.id in inFlight) continue
                    try {
                        send(client, db, entry.id, entry.songId, entry.playedAt)
                        sent++
                    } catch (err: CancellationException) {
                        throw err
                    } catch (_: Exception) {
                        // Still unreachable: keep the rest queued and try again later.
                        break
                    }
                }
                sent
            }
        }
    }

    /**
     * Scrobble one play and mark it sent. A server that answers but cannot
     * find the track (it was deleted) will never accept it, so that play is
     * let go instead of blocking the queue forever; anything else throws and
     * the play stays waiting.
     */
    private suspend fun send(client: SubsonicClient, db: KultrDatabase, id: Long, songId: String, playedAt: Long) {
        try {
            client.scrobble(songId, submission = true, timeMs = playedAt)
        } catch (err: SubsonicException) {
            if (err.code != NOT_FOUND) throw err
        }
        db.history().markSubmitted(id)
    }

    private companion object {
        /** Subsonic's "the requested data was not found". */
        const val NOT_FOUND = 70
    }
}
