package app.kultr.android.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.kultr.android.AppGraph
import app.kultr.android.data.MessageKind
import app.kultr.core.api.Album
import app.kultr.core.api.Artist
import app.kultr.core.api.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    GENRES("Genres"),
    FAVOURITES("Favourites"),
    DOWNLOADS("Downloads"),
    RADIO("Radio"),
}

enum class DownloadsPage(val label: String) { NOW("Downloading"), OFFLINE("On this phone") }

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library?tab={tab}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val GENRE = "genre/{name}"
    const val SYNC = "sync"
    const val STATS = "stats"
    const val DOWNLOADS = "downloads?page={page}"

    fun library(tab: LibraryTab? = null) = if (tab == null) "library" else "library?tab=${tab.name}"
    fun album(id: String) = "album/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    fun genre(name: String) = "genre/${Uri.encode(name)}"
    fun downloads(page: DownloadsPage? = null) = if (page == null) "downloads" else "downloads?page=${page.name}"
}

/** Dialogs that any screen can ask for; the root composable shows them. */
@Stable
class Dialogs {
    var addToPlaylist by mutableStateOf<List<Song>?>(null)
    var rate by mutableStateOf<Song?>(null)
    var sleepTimer by mutableStateOf(false)
}

/**
 * Everything a screen can ask the app to do: navigate, play, change the
 * library. Handed down through [LocalActions] so screens stay small.
 */
@Stable
class AppActions(
    val graph: AppGraph,
    private val scope: CoroutineScope,
    val navigate: (String) -> Unit,
    val back: () -> Unit,
    val openPlayer: () -> Unit,
    val dialogs: Dialogs,
) {
    private val player get() = graph.player
    private val messages get() = graph.messages

    fun openAlbum(id: String?) {
        if (!id.isNullOrEmpty()) navigate(Routes.album(id))
    }

    fun openArtist(id: String?) {
        if (!id.isNullOrEmpty()) navigate(Routes.artist(id))
    }

    fun openPlaylist(id: String) = navigate(Routes.playlist(id))
    fun openGenre(name: String) = navigate(Routes.genre(name))
    fun openLibrary(tab: LibraryTab) = navigate(Routes.library(tab))
    fun openDownloads(page: DownloadsPage = DownloadsPage.NOW) = navigate(Routes.downloads(page))

    fun play(songs: List<Song>, startIndex: Int = 0) = player.play(songs, startIndex, shuffle = false)

    fun shuffle(songs: List<Song>) {
        if (songs.isEmpty()) return
        player.play(songs.shuffled(), 0, shuffle = true)
    }

    fun playNext(songs: List<Song>) = player.playNext(songs)
    fun enqueue(songs: List<Song>) = player.enqueue(songs)

    fun addToPlaylist(songs: List<Song>) {
        if (songs.isNotEmpty()) dialogs.addToPlaylist = songs
    }

    fun rate(song: Song) {
        dialogs.rate = song
    }

    private fun report(error: String?, success: String? = null) {
        if (error != null) messages.error(error) else if (success != null) messages.show(success, MessageKind.SUCCESS)
    }

    fun setFavourite(song: Song, starred: Boolean) = scope.launch {
        report(graph.library.setStarred(song, starred), if (starred) "Added to favourites" else null)
    }

    fun setFavourite(songs: List<Song>, starred: Boolean) = scope.launch {
        report(
            graph.library.setStarred(songs, starred),
            if (starred) "Added ${songs.size} to favourites" else "Removed ${songs.size} from favourites",
        )
    }

    fun setAlbumFavourite(album: Album, starred: Boolean) = scope.launch {
        report(graph.library.setAlbumStarred(album, starred), if (starred) "Added “${album.name}” to favourites" else null)
    }

    fun setArtistFavourite(artist: Artist, starred: Boolean) = scope.launch {
        report(graph.library.setArtistStarred(artist, starred), if (starred) "Added “${artist.name}” to favourites" else null)
    }

    fun setRating(song: Song, rating: Int) = scope.launch {
        report(graph.library.setRating(song, rating), if (rating > 0) "Rated $rating ★" else "Rating cleared")
    }

    fun download(songs: List<Song>, label: String? = null) = scope.launch { graph.offline.download(songs, label) }

    fun removeDownloads(songs: List<Song>) = scope.launch {
        val removed = graph.offline.remove(songs.map { it.id })
        messages.show(if (removed > 0) "Removed $removed download${if (removed == 1) "" else "s"}." else "Nothing to remove.")
    }

    /** Start a flowing set from [seed] (or a random track), continued by InjeKt. */
    fun startInjektSet(seed: Song? = null) = scope.launch {
        val start = seed ?: graph.library.randomSongs(1).firstOrNull()
        if (start == null) {
            messages.show("Sync your library first.")
            return@launch
        }
        val rest = app.kultr.android.playback.AutoQueue(graph).build(start, emptyList(), count = 24)
        player.play(listOf(start) + rest, 0)
        openPlayer()
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
}

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }
