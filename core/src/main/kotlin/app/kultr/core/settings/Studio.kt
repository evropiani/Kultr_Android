package app.kultr.core.settings

import app.kultr.core.util.t
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

// ----------------------------------------------------------- Home widgets --

/** A widget on Home: what it shows, and how big it is. The same JSON as Kultr for iOS. */
@Serializable
data class HomeWidget(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind = Kind.NOW_PLAYING,
    val size: Size = Size.SMALL,
    /** The station's or crate's id, or the shortcut's words ("sleep in 30 minutes"). */
    val ref: String? = null,
) {
    @Serializable
    enum class Kind(private val english: String, private val englishNote: String) {
        @SerialName("nowPlaying") NOW_PLAYING("Now playing", "The track, with play and skip. Tap to open the player."),
        @SerialName("listening") LISTENING("Listening this week", "Hours this week, against your usual."),
        @SerialName("nextMix") NEXT_MIX("Next mix", "What InjeKt plays next, and when."),
        @SerialName("station") STATION("A station", "One tap starts it."),
        @SerialName("crate") CRATE("A crate", "Its covers; tap to play it."),
        @SerialName("shortcut") SHORTCUT("A shortcut", "One tap for anything you'd type in Search."),
        ;

        val label: String get() = t(english)
        val note: String get() = t(englishNote)
    }

    @Serializable
    enum class Size(private val english: String) {
        @SerialName("small") SMALL("Small"),
        @SerialName("medium") MEDIUM("Medium"),
        @SerialName("large") LARGE("Large"),
        ;

        val label: String get() = t(english)
    }
}

val DEFAULT_HOME_WIDGETS: List<HomeWidget> = listOf(
    HomeWidget("now", HomeWidget.Kind.NOW_PLAYING, HomeWidget.Size.MEDIUM),
    HomeWidget("listening", HomeWidget.Kind.LISTENING, HomeWidget.Size.SMALL),
    HomeWidget("next", HomeWidget.Kind.NEXT_MIX, HomeWidget.Size.SMALL),
)

/**
 * Widgets in rows of a two-column grid: small ones side by side, medium and
 * large ones across the whole width. Returns rows of widget indices.
 */
fun homeWidgetRows(widgets: List<HomeWidget>): List<List<Int>> {
    val rows = ArrayList<List<Int>>()
    var pending: Int? = null
    widgets.forEachIndexed { i, widget ->
        val first = pending
        if (widget.size == HomeWidget.Size.SMALL) {
            if (first == null) {
                pending = i
            } else {
                rows += listOf(first, i)
                pending = null
            }
        } else {
            if (first != null) rows += listOf(first)
            pending = null
            rows += listOf(i)
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

// ---------------------------------------------------------- Kultr Studio --

/** The player's parts, in the order they can be stacked. */
@Serializable
enum class PlayerBlock(private val english: String) {
    @SerialName("artwork") ARTWORK("Cover"),
    @SerialName("title") TITLE("Title and artist"),
    @SerialName("facts") FACTS("Info line"),
    @SerialName("progress") PROGRESS("Progress"),
    @SerialName("transport") TRANSPORT("Buttons"),
    ;

    val label: String get() = t(english)
}

/**
 * A text template with conditions, for the player's info line:
 * "{year}{?genre: · {genre}}" shows the genre, and its dot, only when the
 * track has one. Unknown names come out empty.
 */
fun renderTemplate(template: String, values: Map<String, String>): String {
    val out = StringBuilder()
    var i = 0
    while (i < template.length) {
        val ch = template[i]
        if (ch != '{') {
            out.append(ch)
            i++
            continue
        }
        // Find the matching close brace, allowing nested ones.
        var depth = 0
        var j = i
        while (j < template.length) {
            if (template[j] == '{') depth++
            if (template[j] == '}' && --depth == 0) break
            j++
        }
        if (j >= template.length) {
            out.append(template, i, template.length)
            break
        }
        val inner = template.substring(i + 1, j)
        val colon = inner.indexOf(':')
        if (inner.startsWith("?") && colon > 0) {
            val key = inner.substring(1, colon).trim()
            if (!values[key].isNullOrEmpty()) out.append(renderTemplate(inner.substring(colon + 1), values))
        } else {
            out.append(values[inner.trim()].orEmpty())
        }
        i = j + 1
    }
    return out.toString()
}

/** The names a template can use, with an example of each. */
val TEMPLATE_FIELDS: List<Pair<String, String>> = listOf(
    "year" to "2019", "genre" to "Electronic", "format" to "FLAC", "bitrate" to "1024", "bpm" to "124",
    "key" to "8A", "energy" to "7", "rating" to "4", "plays" to "12", "album" to "Night Ferries",
)

/**
 * A look for the whole app, as Kultr Studio edits and shares it: the colours,
 * the shape, the glass and how the player is put together. Plain JSON (the
 * same as Kultr for iOS), so it travels as a small file or a link.
 */
@Serializable
data class StudioStyle(
    val name: String = "Shared style",
    val theme: ThemeMode = DEFAULTS.theme,
    val nightMode: Boolean = DEFAULTS.nightMode,
    val accentMode: AccentMode = DEFAULTS.accentMode,
    val accent: String = DEFAULTS.accent,
    val corners: CornerStyle = DEFAULTS.corners,
    val gridSize: GridSize = DEFAULTS.gridSize,
    val compactRows: Boolean = DEFAULTS.compactRows,
    val playhead: PlayheadStyle = DEFAULTS.playhead,
    val backdropArtwork: Boolean = DEFAULTS.backdropArtwork,
    val clearGlass: Boolean = DEFAULTS.clearGlass,
    val playerBlocks: List<PlayerBlock> = DEFAULTS.playerBlocks,
    /** Empty for Kultr's own info line, with the genre and rating to tap. */
    val factsTemplate: String = DEFAULTS.factsTemplate,
) {
    /** [settings] with this look; everything that isn't the look stays. */
    fun appliedTo(settings: Settings): Settings = settings.copy(
        theme = theme,
        nightMode = nightMode,
        accentMode = accentMode,
        accent = if (accent.startsWith("#") && accent.length == 7) accent else settings.accent,
        corners = corners,
        gridSize = gridSize,
        compactRows = compactRows,
        playhead = playhead,
        backdropArtwork = backdropArtwork,
        clearGlass = clearGlass,
        playerBlocks = playerBlocks.ifEmpty { PlayerBlock.entries },
        factsTemplate = factsTemplate,
    )

    /** Whether [settings] look like this already. */
    fun matches(settings: Settings): Boolean = copy(name = "") == of(settings)

    /** The style as a link, like kultr://style/eyJuYW1l… */
    val link: String get() = LINK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(serializer(), this).toByteArray())

    companion object {
        private val DEFAULTS = Settings()
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
        const val LINK_PREFIX = "kultr://style/"

        fun of(settings: Settings, name: String = "") = StudioStyle(
            name = name,
            theme = settings.theme,
            nightMode = settings.nightMode,
            accentMode = settings.accentMode,
            accent = settings.accent,
            corners = settings.corners,
            gridSize = settings.gridSize,
            compactRows = settings.compactRows,
            playhead = settings.playhead,
            backdropArtwork = settings.backdropArtwork,
            clearGlass = settings.clearGlass,
            playerBlocks = settings.playerBlocks,
            factsTemplate = settings.factsTemplate,
        )

        /** A style from a link, or from a style file's text. */
        fun read(text: String): StudioStyle? {
            val trimmed = text.trim()
            val body = if (trimmed.startsWith("{")) {
                trimmed
            } else {
                val at = trimmed.indexOf("style/").takeIf { it >= 0 } ?: return null
                runCatching { String(Base64.getUrlDecoder().decode(trimmed.substring(at + 6).trim())) }.getOrNull() ?: return null
            }
            return runCatching { json.decodeFromString(serializer(), body) }.getOrNull()?.let {
                if (it.playerBlocks.isEmpty()) it.copy(playerBlocks = PlayerBlock.entries) else it
            }
        }

        fun json(style: StudioStyle): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(serializer(), style)
    }
}

/** Styles Kultr comes with. */
val STUDIO_PRESETS: List<StudioStyle> = StudioStyle.of(Settings(), "Cream").let { base ->
    listOf(
        base,
        base.copy(name = "Night", theme = ThemeMode.DARK, nightMode = true),
        base.copy(
            name = "Ember",
            theme = ThemeMode.DARK,
            accentMode = AccentMode.ARTWORK,
            backdropArtwork = true,
            playhead = PlayheadStyle.GLOW,
            corners = CornerStyle.ROUND,
        ),
        base.copy(name = "Paper", theme = ThemeMode.LIGHT, corners = CornerStyle.SHARP, compactRows = true, gridSize = GridSize.SMALL, clearGlass = true),
        base.copy(
            name = "Console",
            theme = ThemeMode.DARK,
            corners = CornerStyle.SHARP,
            compactRows = true,
            playhead = PlayheadStyle.EQUALIZER,
            factsTemplate = "{?bpm:{bpm} BPM}{?key: · {key}}{?energy: · energy {energy}}{?format: · {format}}",
            playerBlocks = listOf(PlayerBlock.TITLE, PlayerBlock.PROGRESS, PlayerBlock.TRANSPORT, PlayerBlock.FACTS, PlayerBlock.ARTWORK),
        ),
    )
}
