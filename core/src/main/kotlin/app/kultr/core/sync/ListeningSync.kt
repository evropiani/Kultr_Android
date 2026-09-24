package app.kultr.core.sync

import app.kultr.core.api.Album
import app.kultr.core.api.AlbumListType
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Listening data, read back from the server.
 *
 * Navidrome keeps what matters across devices: each track's play count and
 * when it was last played, fed by scrobbles from every client. Albums come
 * back with their own play count and last-played time, so an album whose
 * numbers moved since the mirror last saw it was played somewhere (here or
 * on another device), and re-reading its tracks brings their counts and
 * times up to date. The server lists albums most recently played first, so
 * a pull stops at the first page that reaches plays it has already seen.
 */
class ListeningSync(
    private val client: SubsonicClient,
    private val store: LibraryStore,
) {
    data class Pull(val albumsChanged: Int, val songsRefreshed: Int)

    suspend fun pull(): Pull {
        val watermark = store.syncState().listeningPulledThrough.orEmpty()
        val stamps = store.albumStamps()
        var newest = watermark
        val changed = mutableListOf<Album>()

        for (page in 0 until MAX_PAGES) {
            val albums = client.getAlbumList2(AlbumListType.RECENT, size = PAGE, offset = page * PAGE)
            for (album in albums) {
                val played = album.played
                if (played != null && played > newest) newest = played
                // Albums the mirror does not have yet are the library sync's job.
                val local = stamps[album.id] ?: continue
                if (local.playsDiffer(album)) changed += album
            }
            val oldest = albums.lastOrNull()?.played
            if (albums.size < PAGE || (watermark.isNotEmpty() && oldest != null && oldest <= watermark)) break
        }

        val queue = ArrayDeque(changed.take(MAX_ALBUMS))
        val lock = Mutex()
        var refreshed = 0
        coroutineScope {
            repeat(WORKERS) {
                launch {
                    while (true) {
                        val album = lock.withLock { queue.removeFirstOrNull() } ?: break
                        try {
                            val songs: List<Song> = client.getAlbum(album.id)?.song.orEmpty()
                            store.replaceAlbumSongs(mapOf(album.id to songs))
                            store.putAlbums(listOf(album))
                            lock.withLock { refreshed += songs.size }
                        } catch (err: CancellationException) {
                            throw err
                        } catch (_: Exception) {
                            // Left for next time: its stamp still differs.
                        }
                    }
                }
            }
        }

        if (newest != watermark) {
            store.setSyncState(store.syncState().copy(listeningPulledThrough = newest))
        }
        return Pull(changed.size, refreshed)
    }

    private companion object {
        const val PAGE = 100
        const val MAX_PAGES = 5
        const val MAX_ALBUMS = 120
        const val WORKERS = 3
    }
}
