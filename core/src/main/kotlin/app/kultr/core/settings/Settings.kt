package app.kultr.core.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    @SerialName("dark") DARK,
    @SerialName("light") LIGHT,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class AccentMode {
    @SerialName("artwork") ARTWORK,
    @SerialName("fixed") FIXED,
}

@Serializable
enum class SurfaceBorder {
    @SerialName("neutral") NEUTRAL,
    @SerialName("accent") ACCENT,
}

@Serializable
enum class CornerStyle {
    @SerialName("sharp") SHARP,
    @SerialName("soft") SOFT,
    @SerialName("round") ROUND,
}

@Serializable
enum class PlayheadStyle(val label: String, val note: String) {
    @SerialName("minimal") MINIMAL("Minimal", "A plain accent-coloured bar. Still."),
    @SerialName("glow") GLOW("Glow", "A soft halo that breathes around the playhead."),
    @SerialName("pulse") PULSE("Pulse", "A ring that expands out of the playhead in time."),
    @SerialName("wave") WAVE("Wave", "Diagonal light travelling along the played part."),
    @SerialName("comet") COMET("Comet", "A bright head dragging a shimmering tail."),
    @SerialName("equalizer") EQUALIZER("Equalizer", "Sliding bars, like a level meter."),
}

@Serializable
enum class GridSize {
    @SerialName("small") SMALL,
    @SerialName("medium") MEDIUM,
    @SerialName("large") LARGE,
}

@Serializable
enum class CrossfadeCurve(val label: String) {
    @SerialName("equalPower") EQUAL_POWER("Equal power"),
    @SerialName("linear") LINEAR("Linear"),
    @SerialName("smooth") SMOOTH("Smooth"),
    @SerialName("sharp") SHARP("Sharp"),
}

@Serializable
enum class ReplayGainMode(val label: String) {
    @SerialName("off") OFF("Off"),
    @SerialName("track") TRACK("Per track"),
    @SerialName("album") ALBUM("Per album"),
}

val EQ_BANDS = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

val EQ_PRESETS: Map<String, List<Double>> = linkedMapOf(
    "Flat" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    "Bass Boost" to listOf(6.0, 5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    "Bass Reduce" to listOf(-6.0, -5.0, -4.0, -2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    "Treble Boost" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 4.0, 5.0, 6.0),
    "Vocal" to listOf(-2.0, -1.0, 0.0, 2.0, 4.0, 4.0, 3.0, 1.0, 0.0, -1.0),
    "Acoustic" to listOf(4.0, 3.0, 2.0, 0.0, 1.0, 1.0, 2.0, 3.0, 3.0, 2.0),
    "Electronic" to listOf(5.0, 4.0, 1.0, 0.0, -2.0, 1.0, 0.0, 2.0, 4.0, 5.0),
    "Late Night" to listOf(-4.0, -3.0, -1.0, 1.0, 2.0, 2.0, 1.0, 0.0, -1.0, -2.0),
    "Loudness" to listOf(6.0, 4.0, 0.0, -2.0, -3.0, -1.0, 1.0, 3.0, 5.0, 6.0),
)

/**
 * Every preference Kultr has. Key names match the web client's, so a settings
 * file exported from one can be imported into the other; anything only one
 * side understands is skipped on import rather than guessed at.
 */
@Serializable
data class Settings(
    // ---- appearance
    val theme: ThemeMode = ThemeMode.DARK,
    val accentMode: AccentMode = AccentMode.ARTWORK,
    val accent: String = "#7c8cff",
    /** How strongly `accent` is mixed into the colour taken from the artwork, 0–100. */
    val accentBlend: Int = 0,
    val surfaceBorder: SurfaceBorder = SurfaceBorder.ACCENT,
    val borderOpacity: Int = 45,
    val surfaceOpacity: Int = 100,
    val corners: CornerStyle = CornerStyle.SOFT,
    val playhead: PlayheadStyle = PlayheadStyle.MINIMAL,
    /** Whether the player's left-hand time counts up or down. */
    val timeRemaining: Boolean = false,
    val reduceMotion: Boolean = false,
    val backdropArtwork: Boolean = true,
    val gridSize: GridSize = GridSize.MEDIUM,
    val compactRows: Boolean = false,

    // ---- playback
    val crossfadeEnabled: Boolean = true,
    val crossfadeSeconds: Double = 6.0,
    val crossfadeCurve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER,
    val crossfadeOnSkip: Boolean = true,
    val gapless: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.TRACK,
    val replayGainPreamp: Double = 0.0,
    /** kbps cap on Wi-Fi; 0 streams the original file. */
    val preferredBitrate: Int = 0,
    /** kbps cap on mobile data; -1 means "same as Wi-Fi". */
    val preferredBitrateMobile: Int = -1,
    val preferredFormat: String = "",
    val scrobble: Boolean = true,
    val resumeOnStart: Boolean = true,

    // ---- equaliser
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqGains: List<Double> = List(10) { 0.0 },
    val eqPreamp: Double = 0.0,

    // ---- injekt
    val injektEnabled: Boolean = true,
    val injektBeatMatch: Boolean = true,
    val injektBassSwap: Boolean = true,
    val injektHarmonic: Boolean = true,
    val injektMaxTempoShift: Double = 8.0,
    /** Ease the *current* track toward the next one's tempo before the blend. */
    val injektTempoRamp: Boolean = true,
    /** Share of the tempo gap the current track closes, 0–100. */
    val injektTempoBlend: Double = 50.0,
    val injektBars: Int = 8,
    val injektSkipIntro: Boolean = true,
    val injektAutoQueue: Boolean = true,
    val injektAnalyseAhead: Boolean = true,
    /** Only analyse over Wi-Fi, so InjeKt never spends mobile data. */
    val injektAnalyseOnWifiOnly: Boolean = false,

    // ---- offline
    val offlineConcurrency: Int = 3,
    /** kbps for downloads; 0 keeps the original file. */
    val offlineBitrate: Int = 0,
    val offlineWifiOnly: Boolean = true,
    /** Play a downloaded file instead of streaming when one exists. */
    val offlineFirst: Boolean = true,
    /** Size of the cache for streamed audio, in MB. */
    val streamCacheMb: Int = 1024,

    // ---- library
    val autoSyncOnStart: Boolean = true,
    val autoSyncMinutes: Int = 60,
    val syncPlaylistContents: Boolean = true,

    // ---- misc
    val showLyrics: Boolean = true,
    /** Which shelves the home page shows, in order. Ids come from [HOME_TILES]. */
    val homeTiles: List<String> = listOf("mostPlayedSongs", "mostPlayedAlbums", "randomSongs", "mostPlayedArtists"),
    /** Radio stations you have hearted. Navidrome has no concept of this. */
    val favouriteRadios: List<String> = emptyList(),
    val hasSeenWelcome: Boolean = false,
) {
    /** EQ gains, always exactly one per band. */
    val eqBandGains: List<Double>
        get() = List(EQ_BANDS.size) { eqGains.getOrElse(it) { 0.0 } }

    fun withEqPreset(name: String): Settings =
        copy(eqPreset = name, eqGains = EQ_PRESETS[name] ?: EQ_PRESETS.getValue("Flat"))

    fun bitrateFor(metered: Boolean): Int =
        if (metered && preferredBitrateMobile >= 0) preferredBitrateMobile else preferredBitrate
}

enum class TileKind { SONGS, ALBUMS, ARTISTS, PLAYLISTS, RADIOS }

/** A shelf the home page can show. */
data class HomeTile(
    val id: String,
    /** Heading on the home page. */
    val title: String,
    /** One line in Settings, explaining what fills it. */
    val note: String,
    val kind: TileKind,
)

val HOME_TILES: List<HomeTile> = listOf(
    HomeTile("recentlyPlayed", "Jump back in", "Tracks you played most recently.", TileKind.SONGS),
    HomeTile("mostPlayedSongs", "Played the most", "Your most-played tracks, by the play count on the server.", TileKind.SONGS),
    HomeTile("mostPlayedAlbums", "Albums you keep coming back to", "Albums with the highest play counts.", TileKind.ALBUMS),
    HomeTile("mostPlayedArtists", "Artists you play most", "Worked out by adding up the play counts of each artist’s tracks.", TileKind.ARTISTS),
    HomeTile("mostPlayedPlaylists", "Playlists on repeat", "Ranked by the play counts of the tracks inside them.", TileKind.PLAYLISTS),
    HomeTile("randomSongs", "Something else", "A different handful of tracks every time you open the page.", TileKind.SONGS),
    HomeTile("randomAlbums", "Albums at random", "A different handful of albums every time.", TileKind.ALBUMS),
    HomeTile("randomArtists", "Artists at random", "A different handful of artists every time.", TileKind.ARTISTS),
    HomeTile("recentlyAdded", "Recently added", "The newest albums in your library.", TileKind.ALBUMS),
    HomeTile("favouriteSongs", "Favourites", "Tracks you have hearted.", TileKind.SONGS),
    HomeTile("favouriteAlbums", "Favourite albums", "Albums you have hearted.", TileKind.ALBUMS),
    HomeTile("favouriteArtists", "Favourite artists", "Artists you have hearted.", TileKind.ARTISTS),
    HomeTile("favouritePlaylists", "Favourite playlists", "Playlists you own, newest first.", TileKind.PLAYLISTS),
    HomeTile("favouriteRadios", "Favourite stations", "Internet radio you have hearted on the Radio page.", TileKind.RADIOS),
    HomeTile("radios", "Internet radio", "Every station configured on your server.", TileKind.RADIOS),
)

private val TILES_BY_ID = HOME_TILES.associateBy { it.id }

fun homeTile(id: String): HomeTile? = TILES_BY_ID[id]

/** The enabled tiles, in order, ignoring any id that no longer exists. */
fun resolveHomeTiles(ids: List<String>): List<HomeTile> {
    val seen = HashSet<String>()
    return ids.mapNotNull { id -> TILES_BY_ID[id]?.takeIf { seen.add(id) } }
}

/** Everything not currently switched on, in catalogue order. */
fun availableHomeTiles(ids: List<String>): List<HomeTile> {
    val on = ids.toSet()
    return HOME_TILES.filter { it.id !in on }
}
