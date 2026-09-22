package app.kultr.android.data.db

import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Genre
import app.kultr.core.api.Playlist
import app.kultr.core.api.Song
import app.kultr.core.sync.AlbumStamp
import app.kultr.core.sync.LibraryCounts
import app.kultr.core.sync.LibraryStore
import app.kultr.core.sync.SyncState
import kotlinx.serialization.json.Json

private const val SYNC_STATE_KEY = "syncState"

private val stateJson = Json { ignoreUnknownKeys = true }

suspend fun KultrDatabase.syncState(): SyncState =
    meta().get(SYNC_STATE_KEY)?.let { runCatching { stateJson.decodeFromString(SyncState.serializer(), it) }.getOrNull() }
        ?: SyncState()

suspend fun KultrDatabase.setSyncState(state: SyncState) {
    meta().put(MetaEntity(SYNC_STATE_KEY, stateJson.encodeToString(SyncState.serializer(), state)))
}

fun KultrDatabase.syncStateFlow() = meta().observe(SYNC_STATE_KEY)

fun decodeSyncState(text: String?): SyncState =
    text?.let { runCatching { stateJson.decodeFromString(SyncState.serializer(), it) }.getOrNull() } ?: SyncState()

/** The core sync engine's view of the Room mirror. */
class RoomLibraryStore(private val db: KultrDatabase) : LibraryStore {
    private val dao = db.library()

    override suspend fun albumStamps(): Map<String, AlbumStamp> =
        dao.albumStamps().associate { it.id to AlbumStamp(it.songCount, it.changed, it.duration) }

    override suspend fun putArtists(artists: List<Artist>) {
        artists.chunked(SQL_CHUNK).forEach { chunk -> dao.upsertArtists(chunk.map { it.toEntity() }) }
    }

    override suspend fun putAlbums(albums: List<Album>) {
        albums.chunked(SQL_CHUNK).forEach { chunk -> dao.upsertAlbums(chunk.map { it.toEntity() }) }
    }

    override suspend fun replaceAlbumSongs(songsByAlbum: Map<String, List<Song>>) {
        dao.replaceAlbumSongs(songsByAlbum.mapValues { (_, songs) -> songs.map { it.toEntity() } })
    }

    override suspend fun replacePlaylists(playlists: List<Playlist>) {
        dao.replacePlaylists(playlists.mapIndexed { index, playlist -> playlist.toEntity(index) })
    }

    override suspend fun replaceGenres(genres: List<Genre>) {
        dao.replaceGenres(genres.map { it.toEntity() })
    }

    override suspend fun deleteAlbumsNotIn(keep: Set<String>): Int {
        val gone = dao.albumIds().filter { it !in keep }
        gone.chunked(SQL_CHUNK).forEach { dao.deleteAlbums(it) }
        return gone.size
    }

    override suspend fun deleteArtistsNotIn(keep: Set<String>): Int {
        val gone = dao.artistIds().filter { it !in keep }
        gone.chunked(SQL_CHUNK).forEach { dao.deleteArtists(it) }
        return gone.size
    }

    override suspend fun deleteSongsOutsideAlbums(albumIds: Set<String>): Int {
        val gone = dao.songAlbumPairs().filter { it.albumId == null || it.albumId !in albumIds }.map { it.id }
        gone.chunked(SQL_CHUNK).forEach { dao.deleteSongs(it) }
        return gone.size
    }

    override suspend fun counts(): LibraryCounts {
        val c = dao.counts()
        return LibraryCounts(c.artists, c.albums, c.songs, c.playlists, c.genres)
    }

    override suspend fun syncState(): SyncState = db.syncState()

    override suspend fun setSyncState(state: SyncState) = db.setSyncState(state)
}
