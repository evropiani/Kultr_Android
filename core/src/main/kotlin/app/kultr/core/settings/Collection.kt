package app.kultr.core.settings

import app.kultr.core.karousel.Station
import app.kultr.core.util.Format
import app.kultr.core.util.t
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Your own note and tags on an album or an artist. Private: kept with your
 * settings (and in a settings backup), never sent to the server.
 */
@Serializable
data class NoteAndTags(val note: String = "", val tags: List<String> = emptyList()) {
    val isEmpty: Boolean get() = note.isBlank() && tags.isEmpty()

    /** With [tag] added, unless it is there already in any case. */
    fun withTag(tag: String): NoteAndTags {
        val clean = tag.trim()
        if (clean.isEmpty() || tags.any { it.equals(clean, ignoreCase = true) }) return this
        return copy(tags = tags + clean)
    }

    fun withoutTag(tag: String): NoteAndTags = copy(tags = tags.filterNot { it.equals(tag, ignoreCase = true) })

    companion object {
        fun albumKey(id: String) = "album:$id"
        fun artistKey(id: String) = "artist:$id"
    }
}

/** Every tag in use and how often, most used first, then by name. */
fun tagCounts(annotations: Map<String, NoteAndTags>): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Pair<String, Int>>()
    for (annotation in annotations.values) {
        for (tag in annotation.tags) {
            val key = tag.lowercase()
            val (name, count) = counts[key] ?: (tag to 0)
            counts[key] = name to count + 1
        }
    }
    return counts.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
}

/** The album and artist ids carrying any of [tags]. */
fun taggedIds(annotations: Map<String, NoteAndTags>, tags: List<String>): Set<String> {
    val wanted = tags.mapTo(HashSet()) { it.lowercase() }
    if (wanted.isEmpty()) return emptySet()
    return annotations
        .filterValues { annotation -> annotation.tags.any { it.lowercase() in wanted } }
        .keys.mapNotNullTo(HashSet()) { key -> key.substringAfter(':', "").takeIf { it.isNotEmpty() } }
}

/**
 * A crate: a folder of albums, artists and tracks, filled by hand, or by rules
 * like "4 stars and up, added this year". It can be kept in step with a
 * playlist on the server.
 */
@Serializable
data class Crate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Crate",
    val icon: String = "shippingbox.fill",
    val albums: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val songs: List<String> = emptyList(),
    /** Filled by these rules instead of by hand; null for a crate filled by hand. */
    val rules: Station? = null,
    /** Kept in step with a playlist of the same name on the server. */
    val syncPlaylist: Boolean = false,
    /** That playlist, once made. */
    val playlistId: String? = null,
) {
    val byRules: Boolean get() = rules != null
    val isEmpty: Boolean get() = albums.isEmpty() && artists.isEmpty() && songs.isEmpty()

    /** "3 albums · 12 tracks", for a crate filled by hand. */
    val contents: String
        get() = listOfNotNull(
            albums.size.takeIf { it > 0 }?.let { Format.count(it, "album") },
            artists.size.takeIf { it > 0 }?.let { Format.count(it, "artist") },
            songs.size.takeIf { it > 0 }?.let { Format.count(it, "track") },
        ).joinToString(" · ").ifEmpty { t("Empty") }

    /** In if it was out, out if it was in. */
    fun toggleAlbum(id: String) = copy(albums = albums.toggle(id))
    fun toggleArtist(id: String) = copy(artists = artists.toggle(id))
    fun toggleSong(id: String) = copy(songs = songs.toggle(id))

    private fun List<String>.toggle(id: String) = if (id in this) this - id else this + id
}
