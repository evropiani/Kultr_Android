package app.kultr.android.data

import app.kultr.android.AppGraph
import app.kultr.core.api.Song
import app.kultr.core.settings.Crate
import app.kultr.core.settings.NoteAndTags
import app.kultr.core.util.Format
import app.kultr.core.util.t
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Something that can go into a crate. */
sealed interface CrateItem {
    val title: String

    data class Album(val id: String, override val title: String) : CrateItem

    data class Artist(val id: String, override val title: String) : CrateItem

    data class Songs(val songs: List<Song>) : CrateItem {
        override val title: String get() = songs.singleOrNull()?.title ?: "${songs.size} tracks"
    }
}

/**
 * Crates and notes: stored with the settings (so a settings backup keeps
 * them), resolved to tracks from the library, and, for a crate that asks for
 * it, kept in step with a playlist on the server.
 */
class CrateLibrary(private val graph: AppGraph) {
    private val syncing = HashMap<String, Job>()

    fun crate(id: String): Crate? = graph.settings.current.crates.firstOrNull { it.id == id }

    // -------------------------------------------------------------- crates --

    fun save(crate: Crate) {
        graph.settings.update { s ->
            val at = s.crates.indexOfFirst { it.id == crate.id }
            s.copy(crates = if (at >= 0) s.crates.toMutableList().also { it[at] = crate } else s.crates + crate)
        }
        if (crate.syncPlaylist) scheduleSync(crate.id)
    }

    fun delete(crate: Crate) = graph.settings.update { s -> s.copy(crates = s.crates.filterNot { it.id == crate.id }) }

    /** Whether [item] is in [crate]; songs count as in when all of them are. */
    fun contains(crate: Crate, item: CrateItem): Boolean = when (item) {
        is CrateItem.Album -> item.id in crate.albums
        is CrateItem.Artist -> item.id in crate.artists
        is CrateItem.Songs -> item.songs.isNotEmpty() && item.songs.all { it.id in crate.songs }
    }

    /** Puts [item] in the crate, or takes it out if it is in. */
    fun toggle(item: CrateItem, crateId: String) {
        val crate = crate(crateId) ?: return
        val wasIn = contains(crate, item)
        val changed = when (item) {
            is CrateItem.Album -> crate.toggleAlbum(item.id)
            is CrateItem.Artist -> crate.toggleArtist(item.id)
            is CrateItem.Songs -> item.songs.filter { (it.id in crate.songs) == wasIn }.fold(crate) { c, song -> c.toggleSong(song.id) }
        }
        save(changed)
        graph.messages.show(if (wasIn) t("Taken out of “%@”", crate.name) else t("Added to “%@”", crate.name))
    }

    /** Every track in [crate]: its albums, its artists' tracks and its own tracks, or what fits its rules. */
    suspend fun songs(crate: Crate): List<Song> {
        val library = graph.library
        crate.rules?.let { rules ->
            return graph.stations.pool(rules, playableOnly = false).map { it.first }.sortedWith(
                compareBy<Song> { Format.sortKey(it.artist) }.thenBy { it.album.orEmpty() }.thenBy { it.discNumber ?: 1 }.thenBy { it.track ?: 0 },
            )
        }
        val out = LinkedHashMap<String, Song>()
        fun add(songs: List<Song>) = songs.forEach { out.putIfAbsent(it.id, it) }
        crate.albums.forEach { add(library.songsOfAlbumNow(it)) }
        crate.artists.forEach { add(library.songsOfArtistNow(it)) }
        add(library.songsByIds(crate.songs))
        return out.values.toList()
    }

    // ----------------------------------------------------------- playlists --

    /** Bring the crate's playlist in step soon; several changes in a row make one sync. */
    private fun scheduleSync(crateId: String) {
        syncing.remove(crateId)?.cancel()
        syncing[crateId] = graph.scope.launch {
            delay(1_500)
            syncNow(crateId)
            syncing.remove(crateId)
        }
    }

    /** Makes the crate's playlist hold exactly its tracks, creating it the first time. Gives back an error, if any. */
    suspend fun syncNow(crateId: String): String? {
        val crate = crate(crateId)?.takeIf { it.syncPlaylist } ?: return null
        val (id, error) = graph.library.fillPlaylist(crate.playlistId, crate.name, songs(crate))
        if (id != null && id != crate.playlistId) {
            graph.settings.update { s -> s.copy(crates = s.crates.map { if (it.id == crateId) it.copy(playlistId = id) else it }) }
        }
        if (error != null) graph.messages.error(t("“%@” could not be synced as a playlist: %@", crate.name, error))
        return error
    }

    // --------------------------------------------------------------- notes --

    fun annotation(key: String): NoteAndTags = graph.settings.current.annotations[key] ?: NoteAndTags()

    fun setAnnotation(key: String, annotation: NoteAndTags) = graph.settings.update { s ->
        s.copy(annotations = if (annotation.isEmpty) s.annotations - key else s.annotations + (key to annotation))
    }
}
