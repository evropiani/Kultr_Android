package app.kultr.core.karousel

import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.injekt.rankAutoQueue
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/** A range with either end left open. */
@Serializable
data class Bounds(val min: Double? = null, val max: Double? = null) {
    val isOpen: Boolean get() = min == null && max == null

    operator fun contains(value: Double): Boolean =
        (min == null || value >= min) && (max == null || value <= max)
}

/**
 * A station: rules over the library, played as an endless mix. Kultr picks
 * what fits the rules and InjeKt mixes from one track to the next. Rules
 * about tempo, energy and key need InjeKt's analysis, so they only match
 * tracks it has analysed (or whose tags carry a tempo). The same JSON as
 * Kultr for iOS, so stations travel in a settings backup.
 */
@Serializable
data class Station(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Station",
    /** An icon name, as Kultr for iOS spells it (an SF Symbol); Android draws its own for each. */
    val icon: String = "dot.radiowaves.left.and.right",
    /** Beats per minute. */
    val bpm: Bounds? = null,
    /** 0 to 1, as InjeKt measures it; shown as 1 to 10. */
    val energy: Bounds? = null,
    /** Camelot keys, like "8A"; null or empty for any. */
    val keys: List<String>? = null,
    /** Any of these genres; null or empty for any. */
    val genres: List<String>? = null,
    val years: Bounds? = null,
    val unplayed: Boolean = false,
    val favourites: Boolean = false,
    /** At least this many stars. */
    val minRating: Int? = null,
    /** Albums or artists you gave any of these tags. */
    val tags: List<String>? = null,
    /** Added to the library in the last this many days. */
    val addedDays: Int? = null,
) {
    /** Rules that only InjeKt's analysis can answer. */
    val needsAnalysis: Boolean
        get() = energy?.isOpen == false || !keys.isNullOrEmpty()

    /** Rules that need a tempo, from the analysis or the tags. */
    val needsTempo: Boolean get() = needsAnalysis || bpm?.isOpen == false

    /**
     * Whether [song] fits, given what InjeKt knows about it. [tagged] holds
     * the album and artist ids with any of the station's tags; [addedSince]
     * is the day ("2026-01-31") a track must have been added on or after.
     */
    fun matches(song: Song, analysis: TrackAnalysis?, tagged: Set<String> = emptySet(), addedSince: String? = null): Boolean {
        if (song.isRadio) return false
        if (!tags.isNullOrEmpty() && song.albumId !in tagged && song.artistId !in tagged) return false
        // ISO dates sort as text; the day is enough.
        if (addedSince != null && addedDays != null && (song.created?.take(10) ?: return false) < addedSince) return false
        if (bpm != null && !bpm.isOpen) {
            val tempo = analysis?.bpm?.takeIf { it > 0 } ?: song.bpm?.takeIf { it > 0 }?.toDouble()
            if (tempo == null || tempo !in bpm) return false
        }
        if (energy != null && !energy.isOpen && (analysis == null || analysis.energy !in energy)) return false
        if (!keys.isNullOrEmpty() && analysis?.camelot !in keys) return false
        if (!genres.isNullOrEmpty()) {
            val genre = song.genre?.lowercase() ?: return false
            if (genres.none { genre == it.lowercase() || genre.contains(it.lowercase()) }) return false
        }
        if (years != null && !years.isOpen && (song.year?.toDouble()?.let { it in years } != true)) return false
        if (unplayed && (song.playCount ?: 0) > 0) return false
        if (favourites && !song.isStarred) return false
        if (minRating != null && minRating > 0 && (song.userRating ?: 0) < minRating) return false
        return true
    }

    /** One line saying what the rules are, like "96–110 BPM · energy 4–6 · 6A, 8A". */
    val summary: String
        get() {
            val parts = buildList {
                bpm?.takeIf { !it.isOpen }?.let { add(range(it, " BPM") { v -> "${v.roundToInt()}" }) }
                energy?.takeIf { !it.isOpen }?.let { add("energy " + range(it) { v -> "${(v * 10).roundToInt()}" }) }
                keys?.takeIf { it.isNotEmpty() }?.let { add(if (it.size <= 3) it.joinToString(", ") else "${it.size} keys") }
                genres?.takeIf { it.isNotEmpty() }?.let { add(if (it.size <= 2) it.joinToString(", ") else "${it.size} genres") }
                years?.takeIf { !it.isOpen }?.let { add(range(it) { v -> "${v.toInt()}" }) }
                if (unplayed) add("never played")
                if (favourites) add("favourites")
                minRating?.takeIf { it > 0 }?.let { add("$it★ and up") }
                tags?.takeIf { it.isNotEmpty() }?.let { add("tagged " + it.joinToString(", ")) }
                addedDays?.let { add(if (it == 365) "added this year" else "added in the last $it days") }
            }
            return if (parts.isEmpty()) "Your whole library" else parts.joinToString(" · ")
        }

    companion object {
        /** A station for the decade starting [start]. */
        fun decade(start: Int) = Station(id = "decade-$start", name = "${start}s", icon = "calendar", years = Bounds(start.toDouble(), start + 9.0))

        private fun range(bounds: Bounds, unit: String = "", text: (Double) -> String): String = when {
            bounds.min != null && bounds.max != null -> "${text(bounds.min)}–${text(bounds.max)}$unit"
            bounds.min != null -> "${text(bounds.min)}$unit and up"
            bounds.max != null -> "up to ${text(bounds.max)}$unit"
            else -> "any"
        }
    }
}

/** The first day a track must have been added on to fit [addedDays], as "2026-01-31". */
fun addedSinceDay(addedDays: Int?, nowMs: Long = System.currentTimeMillis()): String? {
    if (addedDays == null) return null
    return Instant.ofEpochMilli(nowMs - addedDays * 86_400_000L).atOffset(ZoneOffset.UTC).toLocalDate().toString()
}

/** The stations Kultr starts with; each can be changed or removed. */
val STATION_PRESETS: List<Station> = listOf(
    Station("warm-up", "Warm Up", "sunrise.fill", bpm = Bounds(112.0, 124.0), energy = Bounds(0.5, 0.85)),
    Station("night-drive", "Night Drive", "car.fill", bpm = Bounds(88.0, 112.0), energy = Bounds(0.35, 0.7)),
    Station("peak-hour", "Peak Hour", "flame.fill", bpm = Bounds(120.0, 135.0), energy = Bounds(0.8, null)),
    Station("deep-focus", "Deep Focus", "brain.head.profile", bpm = Bounds(60.0, 100.0), energy = Bounds(null, 0.5)),
    Station("wind-down", "Wind Down", "moon.stars.fill", bpm = Bounds(50.0, 95.0), energy = Bounds(null, 0.35)),
    Station("unplayed", "Unplayed gems", "sparkle", unplayed = true),
    Station("favourites", "Favourites", "heart.fill", favourites = true),
)

/** Decades with at least [minimum] tracks in [years], oldest first. */
fun decadeStations(years: List<Int>, minimum: Int = 20): List<Station> =
    years.filter { it in 1900..2100 }
        .groupingBy { it / 10 * 10 }
        .eachCount()
        .filterValues { it >= minimum }
        .keys.sorted()
        .map(Station::decade)

/**
 * The next songs for a station: what fits [pool], leaving out [exclude] and
 * what is [queued], ranked by how well each mixes out of [seed]. When
 * everything has been played, it starts over rather than stopping.
 */
fun stationPicks(
    pool: List<Pair<Song, TrackAnalysis?>>,
    seed: TrackAnalysis?,
    exclude: Set<String>,
    queued: Set<String>,
    count: Int,
    random: Random = Random.Default,
): List<Song> {
    val fresh = pool.filter { it.first.id !in exclude && it.first.id !in queued }
        .ifEmpty { pool.filter { it.first.id !in queued } }
    return rankAutoQueue(seed, fresh, count, random)
}
