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

    @Test
    fun paleAccentsAreDeepenedForLightModeKeepingTheirHue() {
        val paleYellow = ArtworkColor.rgb(0xff, 0xe0, 0x66)
        val deep = ArtworkColor.readableOnLight(paleYellow)
        assertTrue(ArtworkColor.luminance(deep) <= 0.22, "luminance ${ArtworkColor.luminance(deep)}")
        // Still a yellow: red and green well above blue.
        val r = deep shr 16 and 0xff
        val g = deep shr 8 and 0xff
        val b = deep and 0xff
        assertTrue(r > b + 40 && g > b + 30, "not yellow any more: $r $g $b")

        val darkBlue = ArtworkColor.rgb(0x2a, 0x3a, 0x9a)
        assertEquals(darkBlue, ArtworkColor.readableOnLight(darkBlue))
    }

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(Versions.isNewer("v1.5.0", "1.4.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.2"))
        assertTrue(Versions.isNewer("2.0", "1.99.99"))
        assertTrue(!Versions.isNewer("1.4.0", "1.4.0"))
        assertTrue(!Versions.isNewer("v1.4", "1.4.0"))
        assertTrue(!Versions.isNewer("1.3.9", "1.4.0"))
        assertTrue(Versions.isNewer("1.5.0", "1.5.0-beta"))
        assertTrue(!Versions.isNewer("1.5.0-beta", "1.5.0"))
        assertTrue(!Versions.isNewer("nightly", "1.4.0"))
        assertEquals("1.4.0", Versions.fromTag("v1.4.0"))
    }
}

class RecapTest {
    private val zone = java.time.ZoneOffset.UTC
    private fun at(day: String) = java.time.LocalDateTime.parse(day + "T12:00").toInstant(zone).toEpochMilli()

    @org.junit.Test
    fun aMonthOfListening() {
        val a = app.kultr.core.api.Song(id = "a", title = "A", artist = "X", artistId = "x", album = "Ax", albumId = "ax")
        val b = app.kultr.core.api.Song(id = "b", title = "B", artist = "Y", artistId = "y", album = "By", albumId = "by")
        val history = listOf(
            Play("a", at("2026-02-20"), 100),
            Play("a", at("2026-03-02"), 200),
            Play("a", at("2026-03-02"), 200),
            Play("b", at("2026-03-05"), 300),
            Play("gone", at("2026-03-06"), 50),
        )
        val songs = mapOf("a" to a, "b" to b)
        assertEquals(listOf(java.time.YearMonth.of(2026, 3), java.time.YearMonth.of(2026, 2)), recapMonths(history, zone))
        val recap = monthRecap(history, songs, java.time.YearMonth.of(2026, 3), zone)
        assertEquals(750, recap.seconds)
        assertEquals(4, recap.plays)
        assertEquals(3, recap.tracks)
        assertEquals(2, recap.artists)
        assertEquals(listOf("a", "b"), recap.topSongs.map { it.item.id })
        assertEquals("X", recap.topArtists.first().item.name)
        assertEquals(java.time.LocalDate.of(2026, 3, 2) to 400L, recap.busiestDay)
        // "a" was heard in February already.
        assertEquals(2, recap.firstListens)
        assertEquals(100, recap.previousSeconds)
    }
}

class CommandsTest {
    private val stations = listOf("Night Drive", "Warm Up")
    private val crates = listOf("Digging")

    private fun parse(text: String) = parseCommand(text, stations, crates)

    @org.junit.Test
    fun plainWordsBecomeCommands() {
        assertEquals(KultrCommand.Pause, parse("Pause"))
        assertEquals(KultrCommand.Next, parse("skip this!"))
        assertEquals(KultrCommand.ShuffleAll, parse("surprise me"))
        assertEquals(KultrCommand.SleepAfterTrack, parse("stop after this song"))
        assertEquals(KultrCommand.Sleep(30), parse("sleep in half an hour"))
        assertEquals(KultrCommand.Sleep(45), parse("timer 45"))
        assertEquals(KultrCommand.Sleep(120), parse("sleep timer 2 hours"))
        assertEquals(KultrCommand.Sleep(90), parse("sleep 90min"))
        assertEquals(KultrCommand.Plan(60), parse("plan an hour"))
        assertEquals(KultrCommand.Plan(null), parse("plan a set"))
        assertEquals(KultrCommand.Open(CommandPlace.SETTINGS), parse("open settings"))
        assertEquals(KultrCommand.Open(CommandPlace.RECAP), parse("show my monthly recap"))
        assertEquals(KultrCommand.Open(CommandPlace.LISTENING), parse("go to stats"))
    }

    @org.junit.Test
    fun namesBeatSearches() {
        assertEquals(KultrCommand.Station("Night Drive"), parse("play night drive"))
        assertEquals(KultrCommand.Station("Warm Up"), parse("tune in to warm"))
        assertEquals(KultrCommand.PlayCrate("Digging"), parse("play digging"))
        assertEquals(KultrCommand.Crate("Digging"), parse("open digging"))
        assertEquals(KultrCommand.Play("radiohead"), parse("play some radiohead"))
        assertEquals(KultrCommand.Shuffle("daft punk"), parse("shuffle daft punk"))
        assertNull(parse("radiohead"))
        assertNull(parse("sleep well"))
    }
}
