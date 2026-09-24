package app.kultr.android.widget

import kotlin.math.max
import kotlin.math.min

/**
 * The widget's measurements at one size, in dp.
 *
 * The widget's content is a single centred group: the artwork (when shown) on
 * the left, and beside it the title, the artist and the controls, stacked
 * and centred on one another.
 */
data class WidgetSpec(
    /** Space between the widget's edge and its content. */
    val paddingH: Float,
    val paddingV: Float,
    /** Edge of the square artwork; 0 when it is hidden or does not fit. */
    val art: Float,
    /** Between the artwork and the text. */
    val gap: Float,
    /** Width of the title and artist lines, so the group does not shift from one track to the next. */
    val textWidth: Float,
    val titleSize: Float,
    val artistSize: Float,
    /** Between the artist and the controls. */
    val controlsGap: Float,
    val playIcon: Float,
    val skipIcon: Float,
    /** Padding around each button's icon: it spaces the buttons out and makes them easier to hit. */
    val buttonPadH: Float,
    val buttonPadV: Float,
) {
    val showsArt: Boolean get() = art > 0f

    /** Width of the whole group: artwork, gap and text. */
    val contentWidth: Float get() = art + gap + textWidth

    /** Width of the row of controls. */
    val controlsWidth: Float get() = playIcon + 2 * skipIcon + 6 * buttonPadH

    /** Height of the text and the controls under it. */
    fun columnHeight(fontLine: Float = LINE): Float =
        (titleSize + artistSize) * fontLine + controlsGap + playIcon + 2 * buttonPadV

    companion object {
        /** One line of text is about this much taller than its font size (Roboto, with font padding). */
        const val LINE = 1.34f
    }
}

/**
 * Works out [WidgetSpec]s. Remote views cannot scale a layout by themselves,
 * so the widget is measured here for each size the launcher reports, and
 * everything grows with it: resizing to 4×2 or 5×3 gives bigger artwork,
 * text and buttons rather than the same small row lost in empty space.
 */
object WidgetLayout {
    private const val MIN_SCALE = 0.7f
    private const val MAX_SCALE = 1.5f

    // Sizes at scale 1.
    private const val TITLE = 16f
    private const val ARTIST = 13.5f
    private const val CONTROLS_GAP = 6f
    private const val PLAY = 34f
    private const val SKIP = 27f
    private const val BUTTON_PAD_V = 4f
    private const val BUTTON_PAD_MIN = 8f
    private const val BUTTON_PAD_MAX = 24f

    /** Artwork smaller than this is not worth showing. */
    private const val MIN_ART = 40f
    private const val MAX_ART = 200f

    /** The artwork takes at most this share of the width, so the text keeps room. */
    private const val ART_SHARE = 0.38f

    /** Lines wider than this (at scale 1) read as a banner; the group is centred instead. */
    private const val MAX_TEXT = 340f

    /** The controls spread across about this share of the text width. */
    private const val ROW_SHARE = 0.72f

    /**
     * The layout for a widget [width] × [height] dp. [fontScale] is the
     * system font size setting; text follows it, within reason, and the rest
     * of the layout makes room.
     */
    fun compute(width: Float, height: Float, showArt: Boolean, fontScale: Float = 1f): WidgetSpec {
        val font = fontScale.coerceIn(0.85f, 1.3f)
        val paddingH = (width * 0.05f).coerceIn(12f, 20f)
        val paddingV = (height * 0.08f).coerceIn(6f, 16f)
        val innerW = max(width - 2 * paddingH, 1f)
        val innerH = max(height - 2 * paddingV, 1f)

        // How tall the text and controls are, and how narrow the controls can get, at scale 1.
        val unitHeight = (TITLE + ARTIST) * WidgetSpec.LINE * font + CONTROLS_GAP + PLAY + 2 * BUTTON_PAD_V
        val unitRow = PLAY + 2 * SKIP + 6 * BUTTON_PAD_MIN

        fun gapFor(art: Float) = if (art > 0f) (innerW * 0.045f).coerceIn(10f, 18f) else 0f
        fun scaleFor(art: Float) =
            min(min(innerH / unitHeight, (innerW - art - gapFor(art)) / unitRow), MAX_SCALE)

        var art = if (showArt) min(min(innerH, innerW * ART_SHARE), MAX_ART) else 0f
        // The artwork is the first thing to go when the text and controls would get too small.
        if (art < MIN_ART || scaleFor(art) < MIN_SCALE) art = 0f
        val gap = gapFor(art)
        val scale = max(scaleFor(art), MIN_SCALE)

        val text = min(innerW - art - gap, MAX_TEXT * scale)
        val play = PLAY * scale
        val skip = SKIP * scale
        val padH = ((ROW_SHARE * text - play - 2 * skip) / 6)
            .coerceIn(BUTTON_PAD_MIN * scale, BUTTON_PAD_MAX * scale)
        return WidgetSpec(
            paddingH = paddingH,
            paddingV = paddingV,
            art = art,
            gap = gap,
            textWidth = text,
            titleSize = TITLE * scale * font,
            artistSize = ARTIST * scale * font,
            controlsGap = CONTROLS_GAP * scale,
            playIcon = play,
            skipIcon = skip,
            buttonPadH = padH,
            buttonPadV = BUTTON_PAD_V * scale,
        )
    }
}
