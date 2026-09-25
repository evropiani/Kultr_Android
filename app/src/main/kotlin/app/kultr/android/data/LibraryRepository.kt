package app.kultr.android.data

import app.kultr.android.AppGraph
import app.kultr.android.data.db.ArtistPlays
import app.kultr.android.data.db.Counts
import app.kultr.android.data.db.HistoryEntity
import app.kultr.android.data.db.KultrDatabase
import app.kultr.android.data.db.SQL_CHUNK
import app.kultr.android.data.db.decodeIds
import app.kultr.android.data.db.decodeSyncState
import app.kultr.android.data.db.encodeIds
import app.kultr.android.data.db.syncStateFlow
import app.kultr.android.data.db.toAlbum
import app.kultr.android.data.db.toArtist
import app.kultr.android.data.db.toEntity
import app.kultr.android.data.db.toGenre
import app.kultr.android.data.db.toPlaylist
import app.kultr.android.data.db.toSong
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.ArtistInfo
import app.kultr.core.api.Genre
import app.kultr.core.api.Playlist
import app.kultr.core.api.RadioStation
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.describeError
import app.kultr.core.sync.SyncState
import app.kultr.core.util.LyricsDoc
import app.kultr.core.util.LyricsParser
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>, val entryIds: List<String>)

data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
}

class NotConnectedException : Exception("You are not connected to a server.")

/**
 * Everything the screens read from the local mirror, and every change the
 * person makes to their library (favourites, ratings, playlists), which goes
 * to the server first and is then reflected locally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(private val graph: AppGraph) {
    private val starring = Mutex()

    private fun <T> fromDb(empty: T, block: (KultrDatabase) -> Flow<T>): Flow<T> =
        graph.database.flatMapLatest { db -> if (db == null) flowOf(empty) else block(db) }

    private fun db(): KultrDatabase? = graph.database.value

    private fun client(): SubsonicClient = graph.auth.client.value ?: throw NotConnectedException()

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---------------------------------------------------------------- reads --

    val counts: Flow<Counts> = fromDb(Counts(0, 0, 0, 0, 0)) { it.library().countsFlow() }

    val syncState: Flow<SyncState> = fromDb(SyncState()) { db -> db.syncStateFlow().map { decodeSyncState(it) } }

    fun albums(): Flow<List<Album>> = fromDb(emptyList()) { db -> db.library().albums().map { list -> list.map { it.toAlbum() } } }

    fun artists(): Flow<List<Artist>> = fromDb(emptyList()) { db -> db.library().artists().map { list -> list.map { it.toArtist() } } }

    fun songs(): Flow<List<Song>> = fromDb(emptyList()) { db -> db.library().songs().map { list -> list.map { it.toSong() } } }

    fun playlists(): Flow<List<Playlist>> =
        fromDb(emptyList()) { db -> db.library().playlists().map { list -> list.map { it.toPlaylist() } } }

    fun genres(): Flow<List<Genre>> = fromDb(emptyList()) { db -> db.library().genres().map { list -> list.map { it.toGenre() } } }

    fun song(id: String): Flow<Song?> = fromDb(null) { db -> db.library().songFlow(id).map { it?.toSong() } }

    fun album(id: String): Flow<Album?> = fromDb(null) { db -> db.library().album(id).map { it?.toAlbum() } }

    fun songsOfAlbum(id: String): Flow<List<Song>> =
        fromDb(emptyList()) { db -> db.library().songsOfAlbum(id).map { list -> list.map { it.toSong() } } }

    fun artist(id: String): Flow<Artist?> = fromDb(null) { db -> db.library().artist(id).map { it?.toArtist() } }

    fun albumsOfArtist(id: String): Flow<List<Album>> =
        fromDb(emptyList()) { db -> db.library().albumsOfArtist(id).map { list -> list.map { it.toAlbum() } } }

    fun songsOfArtist(id: String): Flow<List<Song>> =
        fromDb(emptyList()) { db -> db.library().songsOfArtist(id).map { list -> list.map { it.toSong() } } }

    fun songsOfGenre(genre: String): Flow<List<Song>> =
        fromDb(emptyList()) { db -> db.library().songsOfGenre(genre).map { list -> list.map { it.toSong() } } }

    fun albumsOfGenre(genre: String): Flow<List<Album>> =
        fromDb(emptyList()) { db -> db.library().albumsOfGenre(genre).map { list -> list.map { it.toAlbum() } } }

    fun starredSongs(): Flow<List<Song>> =
        fromDb(emptyList()) { db -> db.library().starredSongs().map { list -> list.map { it.toSong() } } }

    fun starredAlbums(): Flow<List<Album>> =
        fromDb(emptyList()) { db -> db.library().starredAlbums().map { list -> list.map { it.toAlbum() } } }

    fun starredArtists(): Flow<List<Artist>> =
        fromDb(emptyList()) { db -> db.library().starredArtists().map { list -> list.map { it.toArtist() } } }

    fun recentlyAdded(limit: Int): Flow<List<Album>> =
        fromDb(emptyList()) { db -> db.library().recentlyAdded(limit).map { list -> list.map { it.toAlbum() } } }

    fun mostPlayedSongs(limit: Int): Flow<List<Song>> =
        fromDb(emptyList()) { db -> db.library().mostPlayedSongs(limit).map { list -> list.map { it.toSong() } } }

    fun mostPlayedAlbums(limit: Int): Flow<List<Album>> =
        fromDb(emptyList()) { db -> db.library().mostPlayedAlbums(limit).map { list -> list.map { it.toAlbum() } } }

    fun mostPlayedArtists(limit: Int): Flow<List<Artist>> =
        fromDb(emptyList()) { db -> db.library().mostPlayedArtists(limit).map { list -> list.map { it.toArtist() } } }

    fun history(limit: Int): Flow<List<HistoryEntity>> = fromDb(emptyList()) { it.history().recentFlow(limit) }

    fun artistPlays(limit: Int): Flow<List<ArtistPlays>> = fromDb(emptyList()) { it.library().artistPlays(limit) }

    /** All plays the server has counted, across the library. */
    fun totalPlays(): Flow<Long> = fromDb(0L) { it.library().totalPlays() }

    /** Plays on this phone still waiting to reach the server. */
    fun pendingPlays(): Flow<Int> = fromDb(0) { it.history().pendingCount() }

    /** Playlists with their tracks resolved from the mirror, in playlist order. */
    fun playlist(id: String): Flow<PlaylistDetail?> = fromDb(null) { db ->
        db.library().playlist(id).map { entity ->
            if (entity == null) return@map null
            val ids = decodeIds(entity.entryIds)
            PlaylistDetail(entity.toPlaylist(), songsInOrder(db, ids), ids)
        }
    }

    /** Playlists ranked by how much their tracks get played. */
    fun playlistsByPlays(): Flow<List<Playlist>> = fromDb(emptyList()) { db ->
        combine(db.library().playlists(), db.library().mostPlayedSongs(5000)) { playlists, played ->
            val plays = played.associate { it.id to (it.playCount ?: 0L) }
            playlists.map { it to decodeIds(it.entryIds).sumOf { id -> plays[id] ?: 0L } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first.toPlaylist() }
        }
    }

    private suspend fun songsInOrder(db: KultrDatabase, ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = HashMap<String, Song>()
        ids.distinct().chunked(SQL_CHUNK).forEach { chunk ->
            db.library().songsById(chunk).forEach { found[it.id] = it.toSong() }
        }
        return ids.mapNotNull { found[it] }
    }

    suspend fun songsByIds(ids: List<String>): List<Song> {
        val db = db() ?: return emptyList()
        return io { songsInOrder(db, ids) }
    }

    suspend fun songsOfAlbumNow(albumId: String): List<Song> {
        val local = db()?.let { db -> io { db.library().songsOfAlbumNow(albumId).map { it.toSong() } } }.orEmpty()
        if (local.isNotEmpty()) return local
        return runCatching { client().getAlbum(albumId)?.song.orEmpty() }.getOrDefault(emptyList())
    }

    suspend fun songsOfArtistNow(artistId: String): List<Song> {
        val db = db() ?: return emptyList()
        return io { db.library().songsOfArtistNow(artistId).map { it.toSong() } }
            .sortedWith(compareByDescending<Song> { it.year ?: 0 }.thenBy { it.album }.thenBy { it.discNumber ?: 1 }.thenBy { it.track ?: 0 })
    }

    suspend fun songsOfGenreNow(genre: String): List<Song> {
        val db = db() ?: return emptyList()
        return io { db.library().songsOfGenreNow(genre).map { it.toSong() } }
    }

    suspend fun starredSongsNow(): List<Song> {
        val db = db() ?: return emptyList()
        return io { db.library().starredSongsNow().map { it.toSong() } }
    }

    suspend fun allSongs(): List<Song> {
        val db = db() ?: return emptyList()
        return io { db.library().allSongs().map { it.toSong() } }
    }

    suspend fun randomSongs(count: Int, genre: String? = null): List<Song> {
        val db = db()
        val local = if (db != null) {
            io {
                if (genre != null) db.library().randomSongsOfGenre(genre, count) else db.library().randomSongs(count)
            }.map { it.toSong() }
        } else {
            emptyList()
        }
        if (local.isNotEmpty()) return local
        return runCatching { client().getRandomSongs(count, genre) }.getOrDefault(emptyList())
    }

    suspend fun randomAlbums(count: Int): List<Album> =
        db()?.let { db -> io { db.library().randomAlbums(count).map { it.toAlbum() } } }.orEmpty()

    suspend fun randomArtists(count: Int): List<Artist> =
        db()?.let { db -> io { db.library().randomArtists(count).map { it.toArtist() } } }.orEmpty()

    /**
     * Tracks played most recently, newest first, one entry each. Two sources,
     * merged: each track's last-played time as the server keeps it (plays from
     * every device, and it survives a fresh install), and this phone's own
     * history (exact, and all a server without last-played times offers).
     * Whichever is later wins for each track.
     */
    suspend fun recentlyPlayed(limit: Int): List<Song> {
        val db = db() ?: return emptyList()
        val ids = io {
            val latest = HashMap<String, Long>()
            for (song in db.library().recentlyPlayedSongs(limit * 2)) {
                val at = song.played?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                if (at != null) latest[song.id] = at
            }
            for (entry in db.history().recent(limit * 4)) {
                if ((latest[entry.songId] ?: 0L) < entry.playedAt) latest[entry.songId] = entry.playedAt
            }
            latest.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        }
        return songsByIds(ids)
    }

    suspend fun search(query: String, limit: Int = 60): SearchResults {
        val db = db() ?: return SearchResults()
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        val pattern = "%$q%"
        val prefix = "$q%"
        return io {
            SearchResults(
                artists = db.library().searchArtists(pattern, prefix, 20).map { it.toArtist() },
                albums = db.library().searchAlbums(pattern, prefix, 30).map { it.toAlbum() },
                songs = db.library().searchSongs(pattern, prefix, limit).map { it.toSong() },
            )
        }
    }

    /** The server's own search, for libraries that have not been synced (yet). */
    suspend fun searchServer(query: String): SearchResults {
        val result = client().search3(query, artistCount = 20, albumCount = 30, songCount = 60)
        return SearchResults(result.artist, result.album, result.song)
    }

    // ------------------------------------------------------- server extras --

    suspend fun artistInfo(id: String): ArtistInfo? = runCatching { client().getArtistInfo2(id) }.getOrNull()

    suspend fun topSongs(artistName: String): List<Song> =
        runCatching { client().getTopSongs(artistName, 20) }.getOrDefault(emptyList())

    suspend fun similarSongs(songId: String, count: Int = 50): List<Song> =
        runCatching { client().getSimilarSongs2(songId, count) }.getOrDefault(emptyList())

    suspend fun albumNotes(id: String): String? =
        runCatching { client().getAlbumInfo2(id)?.notes }.getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun radioStations(): List<RadioStation> = client().getInternetRadioStations()

    suspend fun lyrics(song: Song): LyricsDoc? {
        val c = client()
        val structured = try {
            c.getLyricsBySongId(song.id)
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            // Older servers do not implement the OpenSubsonic extension.
            emptyList()
        }
        val plain = if (structured.any { !it.line.isNullOrEmpty() }) {
            null
        } else {
            runCatching { c.getLyrics(song.artist, song.title) }.getOrNull()
        }
        return LyricsParser.choose(structured, plain)
    }

    // ------------------------------------------------------------- actions --

    private fun nowIso(): String = Instant.now().toString()

    /** Favourite or unfavourite a song. Returns an error message, or null. */
    // Favourite changes go to the server one at a time, in the order they were
    // made, so a quick tap and its undo arrive as star-then-unstar.
    suspend fun setStarred(song: Song, starred: Boolean): String? = starring.withLock {
        guard {
            if (starred) client().star(id = song.id) else client().unstar(id = song.id)
            db()?.library()?.setSongStarred(song.id, if (starred) nowIso() else null)
        }
    }

    suspend fun setStarred(songs: List<Song>, starred: Boolean): String? = starring.withLock {
        guard {
            songs.chunked(100).forEach { chunk ->
                chunk.forEach { if (starred) client().star(id = it.id) else client().unstar(id = it.id) }
                val stamp = if (starred) nowIso() else null
                chunk.forEach { db()?.library()?.setSongStarred(it.id, stamp) }
            }
        }
    }

    suspend fun setAlbumStarred(album: Album, starred: Boolean): String? = starring.withLock {
        guard {
            if (starred) client().star(albumId = album.id) else client().unstar(albumId = album.id)
            db()?.library()?.setAlbumStarred(album.id, if (starred) nowIso() else null)
        }
    }

    suspend fun setArtistStarred(artist: Artist, starred: Boolean): String? = starring.withLock {
        guard {
            if (starred) client().star(artistId = artist.id) else client().unstar(artistId = artist.id)
            db()?.library()?.setArtistStarred(artist.id, if (starred) nowIso() else null)
        }
    }

    suspend fun setRating(song: Song, rating: Int): String? = guard {
        client().setRating(song.id, rating)
        db()?.library()?.setSongRating(song.id, rating)
    }

    suspend fun createPlaylist(name: String, songs: List<Song>): String? = guard {
        client().createPlaylist(name.trim(), songs.map { it.id })
        refreshPlaylistsNow()
    }

    suspend fun addToPlaylist(playlistId: String, songs: List<Song>): String? = guard {
        songs.chunked(200).forEach { chunk -> client().updatePlaylist(playlistId, songIdToAdd = chunk.map { it.id }) }
        refreshPlaylistNow(playlistId)
    }

    suspend fun removeFromPlaylist(playlistId: String, indices: List<Int>): String? = guard {
        client().updatePlaylist(playlistId, songIndexToRemove = indices.sortedDescending())
        refreshPlaylistNow(playlistId)
    }

    suspend fun renamePlaylist(playlistId: String, name: String, comment: String? = null): String? = guard {
        client().updatePlaylist(playlistId, name = name.trim(), comment = comment)
        refreshPlaylistNow(playlistId)
    }

    suspend fun deletePlaylist(playlistId: String): String? = guard {
        client().deletePlaylist(playlistId)
        refreshPlaylistsNow()
    }

    /** Re-read the playlist list (and every playlist's tracks) from the server. */
    suspend fun refreshPlaylists(): String? = guard { refreshPlaylistsNow() }

    suspend fun refreshPlaylist(playlistId: String): String? = guard { refreshPlaylistNow(playlistId) }

    private suspend fun refreshPlaylistsNow() {
        val db = db() ?: return
        val c = client()
        val playlists = c.getPlaylists().map { summary ->
            runCatching { c.getPlaylist(summary.id) }.getOrNull() ?: summary
        }
        playlists.forEach { p -> p.entry?.let { entries -> db.library().upsertSongs(entries.map { it.toEntity() }) } }
        db.library().replacePlaylists(playlists.mapIndexed { index, playlist -> playlist.toEntity(index) })
    }

    private suspend fun refreshPlaylistNow(playlistId: String) {
        val db = db() ?: return
        val fresh = client().getPlaylist(playlistId) ?: return
        val position = db.library().allPlaylists().indexOfFirst { it.id == playlistId }.takeIf { it >= 0 } ?: Int.MAX_VALUE
        fresh.entry?.let { entries -> entries.chunked(SQL_CHUNK).forEach { chunk -> db.library().upsertSongs(chunk.map { it.toEntity() }) } }
        db.library().upsertPlaylists(listOf(fresh.toEntity(position).copy(entryIds = encodeIds(fresh.entry?.map { it.id }.orEmpty()))))
    }

    suspend fun clearHistory() {
        db()?.let { db -> io { db.history().clear() } }
    }

    private suspend fun guard(block: suspend () -> Unit): String? = try {
        io { block() }
        null
    } catch (err: CancellationException) {
        throw err
    } catch (err: Exception) {
        describeError(err)
    }
}
