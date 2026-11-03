package app.kultr.core.karousel

import app.kultr.core.api.Song
import app.kultr.core.dsp.BpmSource
import app.kultr.core.dsp.KeyMode
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.settings.Crate
import app.kultr.core.settings.NoteAndTags
import app.kultr.core.settings.Settings
import app.kultr.core.settings.tagCounts
import app.kultr.core.settings.taggedIds
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StationsTest {
    private fun song(id: String, year: Int? = null, genre: String? = null, bpm: Int? = null, plays: Long? = null, starred: Boolean = false) =
        Song(id = id, title = id, year = year, genre = genre, bpm = bpm, playCount = plays, starred = if (starred) "2024-01-01T00:00:00Z" else null, albumId = "al-$id", artistId = "ar-$id")

    @Test
    fun rulesMatchTempoGenreYearAndPlays() {
        val station = Station(name = "Test", bpm = Bounds(100.0, 120.0), genres = listOf("house"), years = Bounds(2000.0, null), unplayed = true)
        assertTrue(station.matches(song("a", 2005, "Deep House", 110), null))
        assertFalse(station.matches(song("b", 2005, "Deep House", 130), null), "too fast")
        assertFalse(station.matches(song("c", 1999, "House", 110), null), "too old")
        assertFalse(station.matches(song("d", 2005, "Jazz", 110), null), "wrong genre")
        assertFalse(station.matches(song("e", 2005, "House", 110, plays = 3), null), "played already")
        assertFalse(station.matches(song("f", 2005, "House"), null), "no tempo known")
    }

    @Test
    fun energyAndKeyNeedTheAnalysis() {
        val station = Station(name = "Calm", energy = Bounds(null, 0.4), keys = listOf("8A"))
        assertTrue(station.needsAnalysis)
        val calm = analysis(energy = 0.3, camelot = "8A")
        assertTrue(station.matches(song("a"), calm))
        assertFalse(station.matches(song("a"), null))
        assertFalse(station.matches(song("a"), calm.copy(camelot = "9A")))
    }

    @Test
    fun tagsAndAddedDays() {
        val notes = mapOf(NoteAndTags.albumKey("al-a") to NoteAndTags(tags = listOf("Summer")))
        val station = Station(name = "Tagged", tags = listOf("summer"), addedDays = 30)
        val tagged = taggedIds(notes, station.tags.orEmpty())
        assertEquals(setOf("al-a"), tagged)
        val since = addedSinceDay(30, nowMs = 1_767_225_600_000) // 2026-01-01
        assertEquals("2025-12-02", since)
        assertTrue(station.matches(song("a").copy(created = "2025-12-20T10:00:00Z"), null, tagged, since))
        assertFalse(station.matches(song("a").copy(created = "2025-11-20T10:00:00Z"), null, tagged, since))
        assertFalse(station.matches(song("b").copy(created = "2025-12-20T10:00:00Z"), null, tagged, since))
    }

    @Test
    fun summariesReadLikeIos() {
        assertEquals("112–124 BPM · energy 5–9", STATION_PRESETS[0].summary)
        assertEquals("never played", STATION_PRESETS.first { it.id == "unplayed" }.summary)
        assertEquals("Your whole library", Station(name = "All").summary)
        assertEquals("energy up to 5 · 3★ and up", Station(name = "x", energy = Bounds(null, 0.5), minRating = 3).summary)
    }

    @Test
    fun decadesNeedTwentyTracks() {
        val years = List(25) { 1994 } + List(5) { 2003 }
        assertEquals(listOf("decade-1990"), decadeStations(years).map { it.id })
    }

    @Test
    fun picksStartOverOnceEverythingHasPlayed() {
        val pool = listOf(song("a") to null, song("b") to null)
        val picks = stationPicks(pool, null, exclude = setOf("a", "b"), queued = setOf("a"), count = 4, random = Random(1))
        assertEquals(listOf("b"), picks.map { it.id })
    }

    @Test
    fun stationsAndCratesSurviveJson() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val settings = Settings(crates = listOf(Crate(name = "Digging", albums = listOf("1"), rules = Station(name = "r", minRating = 4))))
        val back = json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), settings))
        assertEquals(settings.stations, back.stations)
        assertEquals(settings.crates, back.crates)
        // iOS leaves out what is empty; missing fields fall back to "any".
        val ios = json.decodeFromString(Station.serializer(), """{"id":"x","name":"Mine","icon":"bolt.fill","bpm":{"min":90}}""")
        assertEquals(Bounds(90.0, null), ios.bpm)
        assertFalse(ios.unplayed)
    }

    @Test
    fun tagCountsMergeCase() {
        val notes = mapOf("album:1" to NoteAndTags(tags = listOf("Summer")), "artist:2" to NoteAndTags(tags = listOf("summer", "Live")))
        assertEquals(listOf("Summer" to 2, "Live" to 1), tagCounts(notes))
        assertEquals("1 album · 2 tracks", Crate(name = "c", albums = listOf("a"), songs = listOf("x", "y")).contents)
    }

    private fun analysis(energy: Double, camelot: String) = TrackAnalysis(
        songId = "a", analysedAt = 0, bpmSource = BpmSource.DSP, duration = 240.0, bpm = 120.0,
        bpmConfidence = 0.8, beatOffset = 0.0, downbeatOffset = 0.0, outroDownbeat = 0.0, key = 9,
        keyName = "Am", mode = KeyMode.MINOR, keyConfidence = 0.5, camelot = camelot, energy = energy,
        brightness = 0.3, peak = 0.9, introEnd = 0.0, outroStart = 200.0,
    )
}
