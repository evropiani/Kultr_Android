package app.kultr.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** SQLite on older Android allows 999 bound variables; stay well under it. */
const val SQL_CHUNK = 500

data class Counts(
    val artists: Int,
    val albums: Int,
    val songs: Int,
    val playlists: Int,
    val genres: Int,
)

@Dao
abstract class LibraryDao {
    // ------------------------------------------------------------- writes --

    @Upsert
    abstract suspend fun upsertSongs(songs: List<SongEntity>)

    @Upsert
    abstract suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Upsert
    abstract suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Upsert
    abstract suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    abstract suspend fun upsertGenres(genres: List<GenreEntity>)

    @Query("DELETE FROM playlists")
    abstract suspend fun clearPlaylists()

    @Query("DELETE FROM genres")
    abstract suspend fun clearGenres()

    @Transaction
    open suspend fun replacePlaylists(playlists: List<PlaylistEntity>) {
        clearPlaylists()
        upsertPlaylists(playlists)
    }

    @Transaction
    open suspend fun replaceGenres(genres: List<GenreEntity>) {
        clearGenres()
        upsertGenres(genres)
    }

    @Query("SELECT id FROM songs WHERE albumId = :albumId")
    abstract suspend fun songIdsOfAlbum(albumId: String): List<String>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    abstract suspend fun deleteSongs(ids: List<String>)

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    abstract suspend fun deleteAlbums(ids: List<String>)

    @Query("DELETE FROM artists WHERE id IN (:ids)")
    abstract suspend fun deleteArtists(ids: List<String>)

    @Query("SELECT id FROM albums")
    abstract suspend fun albumIds(): List<String>

    @Query("SELECT id FROM artists")
    abstract suspend fun artistIds(): List<String>

    @Query("SELECT id, albumId FROM songs")
    abstract suspend fun songAlbumPairs(): List<SongAlbum>

    @Query("SELECT id, songCount, changed, duration FROM albums")
    abstract suspend fun albumStamps(): List<AlbumStampRow>

    @Transaction
    open suspend fun replaceAlbumSongs(songsByAlbum: Map<String, List<SongEntity>>) {
        for ((albumId, songs) in songsByAlbum) {
            val keep = songs.map { it.id }.toHashSet()
            val stale = songIdsOfAlbum(albumId).filter { it !in keep }
            stale.chunked(SQL_CHUNK).forEach { deleteSongs(it) }
        }
        val all = songsByAlbum.values.flatten()
        all.chunked(SQL_CHUNK).forEach { upsertSongs(it) }
    }

    @Query("UPDATE songs SET starred = :starred WHERE id = :id")
    abstract suspend fun setSongStarred(id: String, starred: String?)

    @Query("UPDATE albums SET starred = :starred WHERE id = :id")
    abstract suspend fun setAlbumStarred(id: String, starred: String?)

    @Query("UPDATE artists SET starred = :starred WHERE id = :id")
    abstract suspend fun setArtistStarred(id: String, starred: String?)

    @Query("UPDATE songs SET userRating = :rating WHERE id = :id")
    abstract suspend fun setSongRating(id: String, rating: Int)

    @Query("UPDATE songs SET playCount = COALESCE(playCount, 0) + 1, played = :played WHERE id = :id")
    abstract suspend fun bumpPlayCount(id: String, played: String)

    @Query("DELETE FROM songs")
    abstract suspend fun clearSongs()

    @Query("DELETE FROM albums")
    abstract suspend fun clearAlbums()

    @Query("DELETE FROM artists")
    abstract suspend fun clearArtists()

    @Transaction
    open suspend fun clearLibrary() {
        clearSongs()
        clearAlbums()
        clearArtists()
        clearPlaylists()
        clearGenres()
    }

    // -------------------------------------------------------------- reads --

    @Query(
        "SELECT (SELECT COUNT(*) FROM artists) AS artists, (SELECT COUNT(*) FROM albums) AS albums, " +
            "(SELECT COUNT(*) FROM songs) AS songs, (SELECT COUNT(*) FROM playlists) AS playlists, " +
            "(SELECT COUNT(*) FROM genres) AS genres",
    )
    abstract suspend fun counts(): Counts

    @Query(
        "SELECT (SELECT COUNT(*) FROM artists) AS artists, (SELECT COUNT(*) FROM albums) AS albums, " +
            "(SELECT COUNT(*) FROM songs) AS songs, (SELECT COUNT(*) FROM playlists) AS playlists, " +
            "(SELECT COUNT(*) FROM genres) AS genres",
    )
    abstract fun countsFlow(): Flow<Counts>

    @Query("SELECT * FROM albums ORDER BY sortName")
    abstract fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists ORDER BY sortName")
    abstract fun artists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM songs ORDER BY sortTitle")
    abstract fun songs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    abstract suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM playlists ORDER BY position")
    abstract fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY position")
    abstract suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM genres ORDER BY songCount DESC")
    abstract fun genres(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract fun songFlow(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    abstract suspend fun songsById(ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM albums WHERE id = :id")
    abstract fun album(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id")
    abstract suspend fun albumNow(id: String): AlbumEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    abstract fun artist(id: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun playlist(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY COALESCE(discNumber, 1), COALESCE(track, 0), sortTitle")
    abstract fun songsOfAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY COALESCE(discNumber, 1), COALESCE(track, 0), sortTitle")
    abstract suspend fun songsOfAlbumNow(albumId: String): List<SongEntity>

    @Query(
        "SELECT * FROM songs WHERE artistId = :artistId " +
            "ORDER BY COALESCE(year, 0) DESC, album, COALESCE(discNumber, 1), COALESCE(track, 0)",
    )
    abstract fun songsOfArtist(artistId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistId = :artistId")
    abstract suspend fun songsOfArtistNow(artistId: String): List<SongEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY COALESCE(year, 0) DESC, sortName")
    abstract fun albumsOfArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY artist, album, COALESCE(discNumber, 1), COALESCE(track, 0)")
    abstract fun songsOfGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre")
    abstract suspend fun songsOfGenreNow(genre: String): List<SongEntity>

    @Query("SELECT * FROM albums WHERE genre = :genre ORDER BY sortName")
    abstract fun albumsOfGenre(genre: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM songs WHERE starred IS NOT NULL AND starred != '' ORDER BY starred DESC")
    abstract fun starredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE starred IS NOT NULL AND starred != ''")
    abstract suspend fun starredSongsNow(): List<SongEntity>

    @Query("SELECT * FROM albums WHERE starred IS NOT NULL AND starred != '' ORDER BY starred DESC")
    abstract fun starredAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists WHERE starred IS NOT NULL AND starred != '' ORDER BY starred DESC")
    abstract fun starredArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM albums ORDER BY created DESC LIMIT :limit")
    abstract fun recentlyAdded(limit: Int): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    abstract fun mostPlayedSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM albums WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    abstract fun mostPlayedAlbums(limit: Int): Flow<List<AlbumEntity>>

    @Query(
        "SELECT artists.* FROM artists JOIN (SELECT artistId, SUM(COALESCE(playCount, 0)) AS plays FROM songs " +
            "GROUP BY artistId) p ON p.artistId = artists.id WHERE p.plays > 0 ORDER BY p.plays DESC LIMIT :limit",
    )
    abstract fun mostPlayedArtists(limit: Int): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM songs ORDER BY RANDOM() LIMIT :limit")
    abstract suspend fun randomSongs(limit: Int): List<SongEntity>

    @Query("SELECT * FROM albums ORDER BY RANDOM() LIMIT :limit")
    abstract suspend fun randomAlbums(limit: Int): List<AlbumEntity>

    @Query("SELECT * FROM artists ORDER BY RANDOM() LIMIT :limit")
    abstract suspend fun randomArtists(limit: Int): List<ArtistEntity>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY RANDOM() LIMIT :limit")
    abstract suspend fun randomSongsOfGenre(genre: String, limit: Int): List<SongEntity>

    @Query(
        "SELECT * FROM songs WHERE title LIKE :pattern OR artist LIKE :pattern OR album LIKE :pattern " +
            "ORDER BY CASE WHEN title LIKE :prefix THEN 0 ELSE 1 END, COALESCE(playCount, 0) DESC LIMIT :limit",
    )
    abstract suspend fun searchSongs(pattern: String, prefix: String, limit: Int): List<SongEntity>

    @Query(
        "SELECT * FROM albums WHERE name LIKE :pattern OR artist LIKE :pattern " +
            "ORDER BY CASE WHEN name LIKE :prefix THEN 0 ELSE 1 END, sortName LIMIT :limit",
    )
    abstract suspend fun searchAlbums(pattern: String, prefix: String, limit: Int): List<AlbumEntity>

    @Query(
        "SELECT * FROM artists WHERE name LIKE :pattern " +
            "ORDER BY CASE WHEN name LIKE :prefix THEN 0 ELSE 1 END, sortName LIMIT :limit",
    )
    abstract suspend fun searchArtists(pattern: String, prefix: String, limit: Int): List<ArtistEntity>
}

data class SongAlbum(val id: String, val albumId: String?)

data class AlbumStampRow(val id: String, val songCount: Int?, val changed: String?, val duration: Int?)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis WHERE songId = :songId")
    suspend fun get(songId: String): AnalysisEntity?

    @Query("SELECT * FROM analysis WHERE songId = :songId")
    fun observe(songId: String): Flow<AnalysisEntity?>

    @Query("SELECT * FROM analysis WHERE songId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<AnalysisEntity>

    @Upsert
    suspend fun put(entity: AnalysisEntity)

    @Query("SELECT COUNT(*) FROM analysis WHERE version = :version")
    fun countFlow(version: Int): Flow<Int>

    @Query(
        "SELECT songs.* FROM songs LEFT JOIN analysis ON analysis.songId = songs.id " +
            "WHERE (analysis.songId IS NULL OR analysis.version != :version) AND COALESCE(songs.duration, 0) BETWEEN 1 AND :maxSeconds " +
            "LIMIT :limit",
    )
    suspend fun missing(version: Int, maxSeconds: Int, limit: Int): List<SongEntity>

    @Query(
        "SELECT COUNT(*) FROM songs LEFT JOIN analysis ON analysis.songId = songs.id " +
            "WHERE (analysis.songId IS NULL OR analysis.version != :version) AND COALESCE(songs.duration, 0) BETWEEN 1 AND :maxSeconds",
    )
    fun missingCountFlow(version: Int, maxSeconds: Int): Flow<Int>

    @Query("DELETE FROM analysis")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE submitted = 0 ORDER BY playedAt LIMIT :limit")
    suspend fun unsubmitted(limit: Int): List<HistoryEntity>

    @Query("UPDATE history SET submitted = 1 WHERE id = :id")
    suspend fun markSubmitted(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

data class DownloadUsage(val count: Int, val bytes: Long)

@Dao
interface DownloadDao {
    @Upsert
    suspend fun upsert(entities: List<DownloadEntity>)

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun get(songId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE songId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'done'")
    suspend fun allDone(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state = 'done' ORDER BY savedAt DESC")
    fun doneFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE state = 'queued' ORDER BY requestedAt LIMIT :limit")
    suspend fun queued(limit: Int): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'queued'")
    fun queuedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(size), 0) AS bytes FROM downloads WHERE state = 'done'")
    fun usageFlow(): Flow<DownloadUsage>

    @Query("DELETE FROM downloads WHERE songId IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM downloads WHERE state = 'queued'")
    suspend fun clearQueued()

    @Query("DELETE FROM downloads WHERE state = 'failed'")
    suspend fun clearFailed()
}

@Dao
interface MetaDao {
    @Upsert
    suspend fun put(entity: MetaEntity)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>
}
