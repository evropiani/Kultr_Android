package app.kultr.core.sync

import app.kultr.core.api.Album
import app.kultr.core.api.AlbumListType
import app.kultr.core.api.Artist
import app.kultr.core.api.Genre
import app.kultr.core.api.Playlist
import app.kultr.core.api.ServerInfo
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.describeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

enum class SyncMode { FULL, CHECK }

enum class SyncPhase { IDLE, CONNECTING, ARTISTS, ALBUMS, SONGS, PLAYLISTS, GENRES, CLEANUP, DONE, ERROR, CANCELLED }

data class SyncProgress(
    val phase: SyncPhase,
    val message: String,
    val current: Int,
    val total: Int,
    /** 0..1 across the whole run, so a single progress bar can show it. */
    val percent: Double,
)

@Serializable
data class LibraryCounts(
    val artists: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val playlists: Int = 0,
    val genres: Int = 0,
)

@Serializable
data class SyncState(
    val lastFullSync: Long? = null,
    val lastCheck: Long? = null,
    val counts: LibraryCounts = LibraryCounts(),
    val serverVersion: String? = null,
    val serverType: String? = null,
    /** Newest album `created` timestamp seen, used for cheap delta checks. */
    val newestAlbumCreated: String? = null,
)

data class SyncSummary(
    val mode: SyncMode,
    val startedAt: Long,
    val finishedAt: Long,
    val counts: LibraryCounts,
    val albumsAdded: Int,
    val albumsUpdated: Int,
    val albumsRemoved: Int,
    val songsRemoved: Int,
    val upToDate: Boolean,
    val errors: List<String>,
)

/** What an album looked like last time, to decide whether its tracks need re-reading. */
data class AlbumStamp(val songCount: Int?, val changed: String?, val duration: Int?)

/**
 * Where the mirrored library lives. On Android this is Room; in tests, maps.
 * Implementations should make each call atomic.
 */
interface LibraryStore {
    suspend fun albumStamps(): Map<String, AlbumStamp>
    suspend fun putArtists(artists: List<Artist>)
    suspend fun putAlbums(albums: List<Album>)

    /**
     * Store the full track list of each album, removing tracks that belong to
     * one of these albums but are no longer on it.
     */
    suspend fun replaceAlbumSongs(songsByAlbum: Map<String, List<Song>>)
    suspend fun replacePlaylists(playlists: List<Playlist>)
    suspend fun replaceGenres(genres: List<Genre>)
    suspend fun deleteAlbumsNotIn(keep: Set<String>): Int
    suspend fun deleteArtistsNotIn(keep: Set<String>): Int

    /** Remove songs whose album is not in [albumIds]. */
    suspend fun deleteSongsOutsideAlbums(albumIds: Set<String>): Int
    suspend fun counts(): LibraryCounts
    suspend fun syncState(): SyncState
    suspend fun setSyncState(state: SyncState)
}

private const val ALBUM_PAGE = 500

private fun Int.pretty(): String = NumberFormat.getIntegerInstance().format(this)

/**
 * Mirror the server's library into a [LibraryStore].
 *
 * [SyncMode.FULL] re-reads every album's track list: slow but exhaustive.
 * [SyncMode.CHECK] re-reads the album index (cheap) and only pulls tracks for
 * albums that are new or whose `changed`/`songCount`/`duration` moved.
 */
class LibrarySync(
    private val client: SubsonicClient,
    private val store: LibraryStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        mode: SyncMode,
        includePlaylistContents: Boolean = true,
        concurrency: Int = 6,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncSummary {
        val workers = concurrency.coerceIn(1, 12)
        val startedAt = clock()
        val errors = mutableListOf<String>()
        var base = 0.0
        fun emit(phase: SyncPhase, message: String, current: Int, total: Int, weight: Double) {
            val fraction = if (total > 0) min(1.0, current.toDouble() / total) else 0.0
            onProgress(SyncProgress(phase, message, current, total, min(1.0, base + fraction * weight)))
        }

        try {
            emit(SyncPhase.CONNECTING, "Contacting your server…", 0, 1, 0.0)
            val info: ServerInfo = client.ping()

            // ------------------------------------------------------ artists --
            emit(SyncPhase.ARTISTS, "Reading artists…", 0, 1, W_ARTISTS)
            val artists = client.getArtists()
            store.putArtists(artists)
            emit(SyncPhase.ARTISTS, "${artists.size.pretty()} artists", 1, 1, W_ARTISTS)
            base += W_ARTISTS

            // ------------------------------------------------------- albums --
            emit(SyncPhase.ALBUMS, "Reading albums…", 0, 1, W_ALBUMS)
            val albums = mutableListOf<Album>()
            var offset = 0
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val page = client.getAlbumList2(AlbumListType.ALPHABETICAL_BY_NAME, size = ALBUM_PAGE, offset = offset)
                albums += page
                emit(SyncPhase.ALBUMS, "Reading albums… ${albums.size.pretty()}", albums.size, albums.size + ALBUM_PAGE, W_ALBUMS)
                if (page.size < ALBUM_PAGE) break
                offset += ALBUM_PAGE
                // A misbehaving server that always returns a full page must not spin forever.
                if (offset > 400_000) break
            }
            val previous = store.albumStamps()
            store.putAlbums(albums)
            base += W_ALBUMS

            // -------------------------------------------------------- songs --
            val stale = albums.filter { album ->
                if (mode == SyncMode.FULL) return@filter true
                val before = previous[album.id] ?: return@filter true
                before.songCount != album.songCount || before.changed != album.changed || before.duration != album.duration
            }
            val albumsAdded = albums.count { it.id !in previous }
            val albumsUpdated = max(0, stale.size - albumsAdded)

            emit(
                SyncPhase.SONGS,
                if (stale.isNotEmpty()) "Reading tracks…" else "Tracks already up to date",
                0,
                max(1, stale.size),
                W_SONGS,
            )
            val lock = Mutex()
            val buffer = LinkedHashMap<String, List<Song>>()
            var buffered = 0
            var processed = 0
            var songsSeen = 0
            suspend fun flush() {
                val batch = lock.withLock {
                    val copy = LinkedHashMap(buffer)
                    buffer.clear()
                    buffered = 0
                    copy
                }
                if (batch.isNotEmpty()) store.replaceAlbumSongs(batch)
            }
            coroutineScope {
                val cursor = java.util.concurrent.atomic.AtomicInteger(0)
                repeat(min(workers, max(1, stale.size))) {
                    launch {
                        while (true) {
                            val i = cursor.getAndIncrement()
                            if (i >= stale.size) break
                            val album = stale[i]
                            try {
                                val detail = client.getAlbum(album.id)
                                val songs = detail?.song.orEmpty()
                                val needsFlush = lock.withLock {
                                    buffer[album.id] = songs
                                    buffered += songs.size
                                    songsSeen += songs.size
                                    buffered >= 400
                                }
                                if (needsFlush) flush()
                            } catch (err: CancellationException) {
                                throw err
                            } catch (err: Exception) {
                                lock.withLock { errors += "${album.name}: ${describeError(err)}" }
                            } finally {
                                val done = lock.withLock { ++processed }
                                if (done % 5 == 0 || done == stale.size) {
                                    emit(
                                        SyncPhase.SONGS,
                                        "Reading tracks… ${songsSeen.pretty()} from ${done.pretty()}/${stale.size.pretty()} albums",
                                        done,
                                        max(1, stale.size),
                                        W_SONGS,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            flush()
            base += W_SONGS

            // ---------------------------------------------------- playlists --
            emit(SyncPhase.PLAYLISTS, "Reading playlists…", 0, 1, W_PLAYLISTS)
            var playlists = client.getPlaylists()
            if (includePlaylistContents && playlists.isNotEmpty()) {
                val detailed = playlists.toMutableList()
                playlists.forEachIndexed { i, playlist ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    try {
                        client.getPlaylist(playlist.id)?.let { detailed[i] = it }
                    } catch (err: CancellationException) {
                        throw err
                    } catch (err: Exception) {
                        errors += "Playlist ${playlist.name}: ${describeError(err)}"
                    }
                    emit(SyncPhase.PLAYLISTS, "Reading playlists… ${i + 1}/${playlists.size}", i + 1, playlists.size, W_PLAYLISTS)
                }
                playlists = detailed
            }
            store.replacePlaylists(playlists)
            base += W_PLAYLISTS

            // ------------------------------------------------------- genres --
            emit(SyncPhase.GENRES, "Reading genres…", 0, 1, W_GENRES)
            store.replaceGenres(client.getGenres())
            base += W_GENRES

            // ------------------------------------------------------ cleanup --
            emit(SyncPhase.CLEANUP, "Tidying up…", 1, 1, 0.0)
            val albumIds = albums.map { it.id }.toHashSet()
            val albumsRemoved = store.deleteAlbumsNotIn(albumIds)
            store.deleteArtistsNotIn(artists.map { it.id }.toHashSet())
            val songsRemoved = store.deleteSongsOutsideAlbums(albumIds)

            val counts = store.counts()
            val newest = albums.mapNotNull { it.created }.maxOrNull()
            val before = store.syncState()
            store.setSyncState(
                SyncState(
                    lastFullSync = if (mode == SyncMode.FULL) startedAt else before.lastFullSync,
                    lastCheck = startedAt,
                    counts = counts,
                    serverVersion = info.serverVersion ?: info.version,
                    serverType = info.type,
                    newestAlbumCreated = newest,
                ),
            )
            onProgress(SyncProgress(SyncPhase.DONE, "Library up to date", 1, 1, 1.0))
            return SyncSummary(
                mode = mode,
                startedAt = startedAt,
                finishedAt = clock(),
                counts = counts,
                albumsAdded = albumsAdded,
                albumsUpdated = albumsUpdated,
                albumsRemoved = albumsRemoved,
                songsRemoved = songsRemoved,
                upToDate = mode == SyncMode.CHECK && albumsAdded == 0 && albumsUpdated == 0 && albumsRemoved == 0,
                errors = errors,
            )
        } catch (err: CancellationException) {
            onProgress(SyncProgress(SyncPhase.CANCELLED, "Sync cancelled", 0, 1, 0.0))
            throw err
        } catch (err: Exception) {
            onProgress(SyncProgress(SyncPhase.ERROR, describeError(err), 0, 1, 0.0))
            throw err
        }
    }

    data class QuickCheck(val changed: Boolean, val reason: String)

    /**
     * Cheap "does the server have anything new?" probe — two requests, no
     * writes. Used for the periodic background check.
     */
    suspend fun quickCheck(): QuickCheck {
        val state = store.syncState()
        val newest = client.getAlbumList2(AlbumListType.NEWEST, size = 1).firstOrNull()?.created
        if (newest != null && state.newestAlbumCreated != null && newest > state.newestAlbumCreated) {
            return QuickCheck(true, "New albums were added to your server.")
        }
        if (newest != null && state.newestAlbumCreated == null) {
            return QuickCheck(true, "The library on this phone has never been synced.")
        }
        try {
            val scan = client.getScanStatus()
            val count = scan.count
            if (count != null && state.counts.songs > 0 && count != state.counts.songs.toLong()) {
                return QuickCheck(true, "Server reports ${count.toInt().pretty()} tracks, this phone has ${state.counts.songs.pretty()}.")
            }
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            // getScanStatus needs admin rights on some setups; ignore.
        }
        return QuickCheck(false, "Everything matches.")
    }

    private companion object {
        const val W_ARTISTS = 0.05
        const val W_ALBUMS = 0.15
        const val W_SONGS = 0.65
        const val W_PLAYLISTS = 0.10
        const val W_GENRES = 0.05
    }
}
