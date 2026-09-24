package app.kultr.android.widget

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetLayoutTest {
    private val slack = 0.01f

    private fun assertFits(width: Float, height: Float, spec: WidgetSpec) {
        val innerW = width - 2 * spec.paddingH
        assertTrue(spec.contentWidth <= innerW + slack, "content ${spec.contentWidth} wider than $innerW at $width×$height")
        assertTrue(spec.controlsWidth <= spec.textWidth + slack, "controls wider than the text at $width×$height")
        assertTrue(spec.art <= height - 2 * spec.paddingV + slack, "artwork taller than the widget at $width×$height")
    }

    @Test
    fun everySizeFitsItsWidth() {
        for (showArt in listOf(true, false)) {
            for (fontScale in listOf(0.85f, 1f, 1.3f)) {
                var width = 150f
                while (width <= 900f) {
                    var height = 60f
                    while (height <= 600f) {
                        assertFits(width, height, WidgetLayout.compute(width, height, showArt, fontScale))
                        height += 10f
                    }
                    width += 10f
                }
            }
        }
    }

    @Test
    fun theColumnFitsItsHeightFromOneRowUp() {
        for (width in listOf(250f, 320f, 400f)) {
            for (height in listOf(90f, 110f, 180f, 300f)) {
                val spec = WidgetLayout.compute(width, height, showArt = true)
                assertTrue(spec.columnHeight() <= height - 2 * spec.paddingV + slack, "column too tall at $width×$height")
            }
        }
    }

    @Test
    fun aOneRowWidgetShowsArtworkAsTallAsItsContent() {
        val spec = WidgetLayout.compute(320f, 100f, showArt = true)
        assertTrue(spec.showsArt)
        assertEquals(100f - 2 * spec.paddingV, spec.art, slack)
    }

    @Test
    fun artworkCanBeTurnedOff() {
        val spec = WidgetLayout.compute(320f, 100f, showArt = false)
        assertEquals(0f, spec.art)
        assertEquals(0f, spec.gap)
        // With no artwork, the text spans the widget and the controls sit in its middle.
        assertEquals(320f - 2 * spec.paddingH, spec.textWidth, slack)
    }

    @Test
    fun artworkMakesWayWhenTheWidgetIsTooNarrow() {
        val spec = WidgetLayout.compute(180f, 110f, showArt = true)
        assertEquals(0f, spec.art)
    }

    @Test
    fun biggerWidgetsGetBiggerContentUpToALimit() {
        val small = WidgetLayout.compute(320f, 100f, showArt = true)
        val medium = WidgetLayout.compute(320f, 200f, showArt = true)
        val large = WidgetLayout.compute(420f, 320f, showArt = true)
        val huge = WidgetLayout.compute(900f, 600f, showArt = true)
        assertTrue(medium.titleSize > small.titleSize)
        assertTrue(medium.art > small.art)
        assertTrue(large.playIcon > medium.playIcon)
        assertTrue(huge.titleSize <= 16f * 1.5f + slack)
        assertTrue(huge.art <= 200f)
    }

    @Test
    fun wideWidgetsKeepTheGroupCentredRatherThanStretched() {
        val spec = WidgetLayout.compute(800f, 110f, showArt = true)
        assertTrue(spec.contentWidth < 800f - 2 * spec.paddingH - 100f)
    }
}
