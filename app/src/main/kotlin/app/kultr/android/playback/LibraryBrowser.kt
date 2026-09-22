package app.kultr.android.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import kotlinx.coroutines.flow.first

/**
 * The browse tree shown by Android Auto and other media browsers: a handful
 * of shelves, each opening onto albums, playlists or tracks.
 */
class LibraryBrowser(private val graph: AppGraph) {
    companion object {
        const val ROOT = "root"
        private const val RECENT = "shelf:recent"
        private const val FAVOURITES = "shelf:favourites"
        private const val MOST_PLAYED = "shelf:most-played"
        private const val PLAYLISTS = "shelf:playlists"
        private const val MIX = "mix:random"
        private const val ALBUM = "album:"
        private const val PLAYLIST = "playlist:"
    }

    fun root(): MediaItem = folder(ROOT, "Kultr", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    private fun folder(id: String, title: String, type: Int, artwork: Uri? = null, playable: Boolean = false): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(!playable || id.startsWith(ALBUM) || id.startsWith(PLAYLIST))
                    .setIsPlayable(playable)
                    .setMediaType(type)
                    .setArtworkUri(artwork)
                    .build(),
            )
            .build()

    private fun client() = graph.auth.client.value

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(MIX, "Shuffle my library", MediaMetadata.MEDIA_TYPE_PLAYLIST, playable = true),
            folder(RECENT, "Recently added", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(FAVOURITES, "Favourites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(MOST_PLAYED, "Played the most", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        )
        parentId == RECENT -> graph.library.recentlyAdded(40).first().map { album ->
            folder(
                ALBUM + album.id,
                album.name,
                MediaMetadata.MEDIA_TYPE_ALBUM,
                client()?.coverArtUrl(album.coverArt ?: album.id, 300)?.let(Uri::parse),
                playable = true,
            )
        }
        parentId == PLAYLISTS -> graph.library.playlists().first().map { playlist ->
            folder(
                PLAYLIST + playlist.id,
                playlist.name,
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
                client()?.coverArtUrl(playlist.coverArt, 300)?.let(Uri::parse),
                playable = true,
            )
        }
        parentId == FAVOURITES -> graph.library.starredSongs().first().take(200).map(::track)
        parentId == MOST_PLAYED -> graph.library.mostPlayedSongs(100).first().map(::track)
        parentId.startsWith(ALBUM) || parentId.startsWith(PLAYLIST) || parentId == MIX -> songsFor(parentId).map(::track)
        else -> emptyList()
    }

    private fun track(song: Song): MediaItem = MediaItems.from(song, client())

    /** Tracks behind a playable container, or null if [id] is not one. */
    suspend fun songsFor(id: String): List<Song> = when {
        id == MIX -> graph.library.randomSongs(100)
        id.startsWith(ALBUM) -> graph.library.songsOfAlbumNow(id.removePrefix(ALBUM))
        id.startsWith(PLAYLIST) -> graph.library.playlist(id.removePrefix(PLAYLIST)).first()?.songs.orEmpty()
        else -> emptyList()
    }

    fun isContainer(id: String): Boolean = id == MIX || id.startsWith(ALBUM) || id.startsWith(PLAYLIST)

    /** Look a single item up by id, for controllers that only send ids. */
    suspend fun item(id: String): MediaItem? {
        if (id == ROOT) return root()
        if (id.startsWith("shelf:") || isContainer(id)) return children(ROOT).firstOrNull { it.mediaId == id }
        return graph.library.songsByIds(listOf(id)).firstOrNull()?.let(::track)
    }

    /** Resolve whatever a controller sent into playable, song-carrying items. */
    suspend fun resolve(items: List<MediaItem>): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        for (item in items) {
            val song = MediaItems.songOf(item)
            when {
                song != null -> out += item
                isContainer(item.mediaId) -> songsFor(item.mediaId).mapTo(out, ::track)
                item.mediaId.isNotEmpty() -> {
                    val found = graph.library.songsByIds(listOf(item.mediaId)).firstOrNull()
                        ?: runCatching { client()?.getSong(item.mediaId) }.getOrNull()
                    if (found != null) out += track(found)
                }
                item.requestMetadata.searchQuery != null -> {
                    val query = item.requestMetadata.searchQuery.orEmpty()
                    graph.library.search(query).songs.take(50).mapTo(out, ::track)
                }
            }
        }
        return out
    }

    suspend fun search(query: String): List<MediaItem> = graph.library.search(query).songs.take(50).map(::track)
}
