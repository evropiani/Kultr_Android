package app.kultr.core.util

import app.kultr.core.api.Lyrics
import app.kultr.core.api.ReplayGain
import app.kultr.core.api.Song
import app.kultr.core.api.StructuredLyricLine
import app.kultr.core.api.StructuredLyrics
import app.kultr.core.engine.curveValue
import app.kultr.core.engine.replayGainFor
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.ReplayGainMode
import app.kultr.core.settings.Settings
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilTest {
    @Test
    fun formatsTimes() {
        assertEquals("0:00", Format.time(null))
        assertEquals("3:07", Format.time(187.9))
        assertEquals("1:02:03", Format.time(3723.0))
        assertEquals("1 hr 24 min", Format.duration(5040))
        assertEquals("25 sec", Format.duration(25))
        assertEquals("1.5 KB", Format.bytes(1536))
        assertEquals("beatles", Format.sortKey("The Beatles"))
        assertEquals("BO", Format.initials("Boards of Canada"))
    }

    @Test
    fun structuredSyncedLyricsWin() {
        val doc = LyricsParser.choose(
            listOf(
                StructuredLyrics(synced = false, line = listOf(StructuredLyricLine(value = "plain"))),
                StructuredLyrics(synced = true, offset = 500, line = listOf(StructuredLyricLine(1000, "a"), StructuredLyricLine(3000, "b"))),
            ),
            null,
        )
        assertNotNull(doc)
        assertTrue(doc.synced)
        assertEquals(1.5, doc.lines[0].start)
        assertEquals(-1, doc.activeIndex(1.0))
        assertEquals(0, doc.activeIndex(2.0))
        assertEquals(1, doc.activeIndex(9.0))
    }

    @Test
    fun plainTextThatIsReallyLrcIsSynced() {
        val doc = LyricsParser.choose(emptyList(), Lyrics(value = "[00:01.50]Hello\n[00:03.00][00:05.25]Again"))
        assertNotNull(doc)
        assertTrue(doc.synced)
        assertEquals(listOf(1.5, 3.0, 5.25), doc.lines.map { it.start })
        val plain = LyricsParser.choose(emptyList(), Lyrics(value = "one\ntwo"))!!
        assertEquals(2, plain.lines.size)
        assertNull(LyricsParser.choose(emptyList(), Lyrics(value = "  ")))
    }

    @Test
    fun dominantColourIgnoresGreysAndPicksTheCommonHue() {
        val orange = ArtworkColor.rgb(220, 120, 40)
        val grey = ArtworkColor.rgb(128, 128, 128)
        val pixels = IntArray(100) { if (it < 60) orange else grey }
        val colour = ArtworkColor.dominant(pixels)
        assertNotNull(colour)
        assertTrue((colour shr 16 and 0xff) > (colour and 0xff))
        assertNull(ArtworkColor.dominant(IntArray(10) { grey }))
        assertEquals("#7c8cff", ArtworkColor.toHex(ArtworkColor.parseHex("#7C8CFF")!!))
    }

    @Test
    fun curvesStartAndEndInTheRightPlace() {
        for (curve in CrossfadeCurve.entries) {
            assertEquals(0.0, curveValue(curve, 0.0, rising = true), 1e-9)
            assertEquals(1.0, curveValue(curve, 1.0, rising = true), 1e-9)
            assertEquals(1.0, curveValue(curve, 0.0, rising = false), 1e-9)
            assertEquals(0.0, curveValue(curve, 1.0, rising = false), 1e-9)
        }
    }

    @Test
    fun replayGainNeverClips() {
        val song = Song(id = "x", replayGain = ReplayGain(trackGain = 6.0, trackPeak = 0.9, albumGain = -3.0))
        val track = replayGainFor(song, Settings(replayGainMode = ReplayGainMode.TRACK))
        assertTrue(abs(track - 1 / 0.9f) < 0.001f)
        val album = replayGainFor(song, Settings(replayGainMode = ReplayGainMode.ALBUM))
        assertTrue(abs(album - 0.7079f) < 0.001f)
        assertEquals(1f, replayGainFor(song, Settings(replayGainMode = ReplayGainMode.OFF)))
    }
}
