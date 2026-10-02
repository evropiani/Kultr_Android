package app.kultr.android.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.content.edit
import app.kultr.android.AppGraph
import app.kultr.android.data.db.AlbumEntity
import app.kultr.android.data.db.KultrDatabase
import app.kultr.android.data.db.RoomLibraryStore
import app.kultr.android.data.db.toAlbum
import app.kultr.android.data.db.toArtist
import app.kultr.android.data.db.toSong
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Song
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.md5Hex
import app.kultr.core.local.LocalCatalogBuilder
import app.kultr.core.local.LocalTrack
import app.kultr.core.sync.SyncMode
import app.kultr.core.sync.SyncPhase
import app.kultr.core.sync.SyncProgress
import app.kultr.core.sync.SyncSummary
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A folder of music on the phone, chosen in the system's folder picker. */
@Serializable
data class LocalFolder(val uri: String, val name: String)

/**
 * Music on the phone, played without a server.
 *
 * The person chooses one or more folders in the system's folder picker, which
 * grants Kultr lasting access to them and nothing else (no storage
 * permission). A scan walks those folders, reads each new or changed file's
 * tags, and fills the same kind of library database a server sync fills, as a
 * library of its own ("Music on this phone"). Tracks play straight from their
 * files; favourites, ratings, play counts and playlists are kept on the phone.
 */
class LocalLibrary(private val graph: AppGraph) {
    private val context: Context get() = graph.app
    private val prefs = graph.app.getSharedPreferences("kultr.local", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LocalFolder.serializer())

    private val _folders = MutableStateFlow(load())
    val folders: StateFlow<List<LocalFolder>> = _folders.asStateFlow()

    private fun load(): List<LocalFolder> =
        prefs.getString(KEY_FOLDERS, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun save(list: List<LocalFolder>) {
        _folders.value = list
        prefs.edit { putString(KEY_FOLDERS, json.encodeToString(serializer, list)) }
    }

    /** Keep a folder picked in the system's folder picker. Returns it, or null if access was refused. */
    fun addFolder(uri: Uri): LocalFolder? {
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!granted) return null
        _folders.value.firstOrNull { it.uri == uri.toString() }?.let { return it }
        val folder = LocalFolder(uri.toString(), displayName(uri))
        save(_folders.value + folder)
        return folder
    }

    fun removeFolder(folder: LocalFolder) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        save(_folders.value.filter { it.uri != folder.uri })
    }

    /** Forget every folder, for when the local library itself is removed. */
    fun forgetAll() {
        _folders.value.forEach { removeFolder(it) }
        artDirectory().deleteRecursively()
    }

    private fun displayName(tree: Uri): String = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "Music"

    /** Covers taken out of the files themselves, saved as images. */
    private fun artDirectory(): File = File(context.filesDir, "local-art").apply { mkdirs() }

    /** Scan the folders into [db]. [SyncMode.FULL] re-reads every file's tags; otherwise only new and changed ones. */
    suspend fun scan(db: KultrDatabase, mode: SyncMode, onProgress: (SyncProgress) -> Unit): SyncSummary =
        withContext(Dispatchers.IO) { Scanner(db, mode, onProgress).run() }

    private class Found(val track: LocalTrack, val directory: String)

    private inner class Scanner(
        private val db: KultrDatabase,
        private val mode: SyncMode,
        private val onProgress: (SyncProgress) -> Unit,
    ) {
        private val resolver = context.contentResolver
        private val errors = mutableListOf<String>()

        /** Cover image per folder, best name first (cover.jpg before folder.jpg and so on). */
        private val covers = HashMap<String, Pair<Int, String>>()

        suspend fun run(): SyncSummary {
            val startedAt = System.currentTimeMillis()
            val store = RoomLibraryStore(db)
            val dao = db.library()
            onProgress(SyncProgress(SyncPhase.CONNECTING, "Looking through your folders…", 0, 1, 0.0))

            // ---------------------------------------------------------- walk --
            val files = ArrayList<Found>()
            var unreadable = 0
            for (folder in _folders.value) {
                currentCoroutineContext().ensureActive()
                val tree = Uri.parse(folder.uri)
                val ok = runCatching { walk(tree, DocumentsContract.getTreeDocumentId(tree), folder.name, files) }.isSuccess
                if (!ok) {
                    unreadable++
                    errors += "Kultr can no longer read “${folder.name}”. Add it again in Settings."
                }
                onProgress(SyncProgress(SyncPhase.ARTISTS, "Found ${files.size} tracks…", 0, 1, 0.05))
            }

            // ---------------------------------------------------------- tags --
            val before = dao.allSongs().associateBy { it.path }
            val previousAlbums = dao.allAlbumsNow().associateBy { it.id }
            val previousArtists = dao.allArtistsNow().associateBy { it.id }
            val tracks = arrayOfNulls<LocalTrack>(files.size)
            val toRead = ArrayList<Int>()
            files.forEachIndexed { index, found ->
                val known = before[found.track.uri]
                val unchanged = mode != SyncMode.FULL && known != null &&
                    known.size == found.track.size && known.created == Instant.ofEpochMilli(found.track.modified).toString()
                if (unchanged && known != null) {
                    tracks[index] = found.track.copy(
                        title = known.title,
                        artist = known.artist,
                        albumArtist = null,
                        album = known.album,
                        track = known.track,
                        disc = known.discNumber,
                        year = known.year,
                        genre = known.genre,
                        durationMs = known.duration?.let { it * 1000L },
                        bitRate = known.bitRate?.let { it * 1000 },
                        sampleRate = known.samplingRate,
                        mimeType = known.contentType,
                    ).let { restoreAlbumArtist(it, known.albumId, previousAlbums) }
                } else {
                    toRead += index
                }
            }
            val done = AtomicInteger(0)
            coroutineScope {
                val cursor = AtomicInteger(0)
                repeat(4) {
                    launch {
                        while (true) {
                            val i = cursor.getAndIncrement()
                            if (i >= toRead.size) break
                            currentCoroutineContext().ensureActive()
                            val index = toRead[i]
                            tracks[index] = readTags(files[index].track)
                            val n = done.incrementAndGet()
                            if (n % 10 == 0 || n == toRead.size) {
                                onProgress(
                                    SyncProgress(
                                        SyncPhase.SONGS,
                                        "Reading tags… $n of ${toRead.size}",
                                        n,
                                        toRead.size,
                                        0.1 + 0.75 * n / max(1, toRead.size),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------- catalogue --
            val folderCovers = covers.mapValues { it.value.second }
            val known = before.values.associateBy { it.id }
            val catalog = LocalCatalogBuilder.build(tracks.filterNotNull(), folderCovers) { id -> known[id]?.toSong() }

            // Albums without a cover image in their folder use the picture in their first track, if any.
            onProgress(SyncProgress(SyncPhase.ALBUMS, "Finding covers…", 0, 1, 0.88))
            val embedded = HashMap<String, String>()
            for (album in catalog.albums) {
                if (album.coverArt != null) continue
                currentCoroutineContext().ensureActive()
                val first = catalog.songs.firstOrNull { it.albumId == album.id }?.path ?: continue
                embeddedCover(album.id, Uri.parse(first))?.let { embedded[album.id] = it }
            }
            fun cover(albumId: String?, own: String?) = own ?: albumId?.let(embedded::get)

            // Favourites and ratings of albums and artists belong to the person, not the files.
            val albums: List<Album> = catalog.albums.map { album ->
                val old = previousAlbums[album.id]?.toAlbum()
                album.copy(coverArt = cover(album.id, album.coverArt), starred = old?.starred, userRating = old?.userRating)
            }
            val coverOf = albums.associate { it.id to it.coverArt }
            val songs: List<Song> = catalog.songs.map { it.copy(coverArt = coverOf[it.albumId] ?: it.coverArt) }
            val artists: List<Artist> = catalog.artists.map { artist ->
                val old = previousArtists[artist.id]?.toArtist()
                artist.copy(
                    coverArt = albums.firstOrNull { it.artistId == artist.id && it.coverArt != null }?.coverArt,
                    starred = old?.starred,
                    userRating = old?.userRating,
                )
            }

            // --------------------------------------------------------- store --
            onProgress(SyncProgress(SyncPhase.CLEANUP, "Saving your library…", 1, 1, 0.95))
            store.putArtists(artists)
            store.putAlbums(albums)
            store.replaceAlbumSongs(songs.groupBy { it.albumId.orEmpty() })
            store.replaceGenres(catalog.genres)
            var albumsRemoved = 0
            var songsRemoved = 0
            // A folder that could not be read (a card taken out) must not empty the library.
            if (unreadable == 0) {
                val albumIds = albums.map { it.id }.toHashSet()
                albumsRemoved = store.deleteAlbumsNotIn(albumIds)
                store.deleteArtistsNotIn(artists.map { it.id }.toHashSet())
                songsRemoved = store.deleteSongsOutsideAlbums(albumIds)
            }

            val counts = store.counts()
            val state = store.syncState()
            store.setSyncState(
                state.copy(
                    lastFullSync = if (mode == SyncMode.FULL) startedAt else state.lastFullSync ?: startedAt,
                    lastCheck = startedAt,
                    counts = counts,
                    serverType = "local",
                    serverVersion = null,
                    newestAlbumCreated = albums.mapNotNull { it.created }.maxOrNull(),
                ),
            )
            onProgress(SyncProgress(SyncPhase.DONE, "Library up to date", 1, 1, 1.0))

            val added = albums.count { it.id !in previousAlbums }
            val changedAlbums = toRead.mapNotNull { tracks[it] }.map { LocalCatalogBuilder.songId(it.uri) }
                .mapNotNull { id -> songs.firstOrNull { it.id == id }?.albumId }.toSet()
            val updated = changedAlbums.count { it in previousAlbums }
            return SyncSummary(
                mode = mode,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                counts = counts,
                albumsAdded = added,
                albumsUpdated = updated,
                albumsRemoved = albumsRemoved,
                songsRemoved = songsRemoved,
                upToDate = added == 0 && updated == 0 && albumsRemoved == 0 && songsRemoved == 0,
                errors = errors,
            )
        }

        /**
         * The album artist is not kept per song, but grouping needs it. An
         * unchanged song's album was grouped by album artist exactly when its
         * id is the one that grouping gives, and then the album's artist is it.
         */
        private fun restoreAlbumArtist(track: LocalTrack, albumId: String?, albums: Map<String, AlbumEntity>): LocalTrack {
            val artist = albumId?.let(albums::get)?.artist ?: return track
            val albumName = track.album?.trim()?.takeIf { it.isNotEmpty() } ?: track.folderName
            return if (LocalCatalogBuilder.albumId(albumName, artist, track.folderKey) == albumId) track.copy(albumArtist = artist) else track
        }

        private fun walk(tree: Uri, documentId: String, folderName: String, out: MutableList<Found>) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val projection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_SIZE,
            )
            val folderKey = "$tree|$documentId"
            val subfolders = ArrayList<Pair<String, String>>()
            val cursor = resolver.query(children, projection, null, null, null) ?: throw IllegalStateException("unreadable")
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) subfolders += id to name
                        continue
                    }
                    val lower = name.lowercase()
                    val extension = lower.substringAfterLast('.', "")
                    if (extension in IMAGE_EXTENSIONS) {
                        val rank = COVER_NAMES.indexOf(lower.substringBeforeLast('.'))
                        if (rank >= 0 && (covers[folderKey]?.first ?: Int.MAX_VALUE) > rank) {
                            covers[folderKey] = rank to DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                        }
                        continue
                    }
                    val audio = mime?.startsWith("audio/") == true || extension in AUDIO_EXTENSIONS
                    if (!audio || name.startsWith(".")) continue
                    out += Found(
                        LocalTrack(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            fileName = name,
                            folderKey = folderKey,
                            folderName = folderName,
                            modified = if (c.isNull(3)) 0L else c.getLong(3),
                            size = if (c.isNull(4)) 0L else c.getLong(4),
                            mimeType = mime?.takeIf { it.startsWith("audio/") },
                        ),
                        folderKey,
                    )
                }
            }
            for ((id, name) in subfolders) runCatching { walk(tree, id, name, out) }
        }

        private fun readTags(file: LocalTrack): LocalTrack {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, Uri.parse(file.uri))
                fun tag(key: Int): String? = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
                file.copy(
                    title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    track = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                    disc = tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                    year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()
                        ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.toIntOrNull(),
                    genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(::cleanGenre),
                    durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                    bitRate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                    sampleRate = if (Build.VERSION.SDK_INT >= 31) tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null,
                    mimeType = tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: file.mimeType,
                )
            } catch (_: Exception) {
                // Unreadable tags: the file still plays, named after itself.
                file
            } finally {
                runCatching { retriever.release() }
            }
        }

        /** The picture inside a track, saved small as the album's cover; null if it has none. */
        private fun embeddedCover(albumId: String, track: Uri): String? {
            val name = md5Hex(albumId).take(16)
            val file = File(artDirectory(), "$name.jpg")
            val none = File(artDirectory(), "$name.none")
            if (mode != SyncMode.FULL) {
                if (file.isFile) return Uri.fromFile(file).toString()
                if (none.isFile) return null
            }
            val retriever = MediaMetadataRetriever()
            val bytes = try {
                retriever.setDataSource(context, track)
                retriever.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
            if (bytes == null) {
                none.createNewFile()
                return null
            }
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > COVER_PIXELS * 2) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@runCatching null
                val scale = COVER_PIXELS.toFloat() / max(bitmap.width, bitmap.height)
                val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                file.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                none.delete()
                Uri.fromFile(file).toString()
            }.getOrNull()
        }

        /** "(17)" and "Rock (17)" from old ID3 tags become just the name, or nothing. */
        private fun cleanGenre(raw: String): String? =
            raw.replace(Regex("\\(\\d+\\)"), "").trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        /** The profile id of the library on the phone (a server profile's id is its address and user). */
        const val PROFILE_ID = "local"
        private const val KEY_FOLDERS = "folders"
        private const val COVER_PIXELS = 600

        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav", "wave", "aif", "aiff", "wma", "alac", "mka", "webm", "3gp", "amr")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "artwork", "albumartsmall")

        /** A track from a library on the phone (rather than from a server). */
        fun isLocal(song: Song): Boolean = song.id.startsWith(LocalCatalogBuilder.SONG_PREFIX)

        /** Where a local track's file is, or null for a server track. */
        fun uriOf(song: Song): Uri? = if (isLocal(song)) song.path?.let(Uri::parse) else null

        /** Covers in a local library are images already: a folder's cover.jpg, or one taken out of a track. */
        fun isLocalArt(coverId: String?): Boolean =
            coverId != null && (coverId.startsWith("content://") || coverId.startsWith("file:"))

        /** What to load for a cover: the image itself in a local library, the server's cover art otherwise. */
        fun artwork(coverId: String?, size: Int, client: SubsonicClient?): String? = when {
            coverId.isNullOrEmpty() -> null
            isLocalArt(coverId) -> coverId
            else -> client?.coverArtUrl(coverId, size)
        }
    }
}
