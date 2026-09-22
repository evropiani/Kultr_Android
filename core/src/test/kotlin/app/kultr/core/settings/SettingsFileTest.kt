package app.kultr.core.settings

import app.kultr.core.api.AuthMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsFileTest {
    @Test
    fun exportRoundTrips() {
        val mine = Settings(theme = ThemeMode.LIGHT, crossfadeSeconds = 9.5, homeTiles = listOf("radios", "recentlyAdded"), eqGains = List(10) { it.toDouble() })
        val text = SettingsFile.export(mine, "1.0", "2026-01-01T00:00:00Z")
        assertTrue(text.contains("\"kind\": \"kultr.settings\""))
        assertFalse(text.contains("hasSeenWelcome"))
        val result = SettingsFile.import(text, Settings())
        assertEquals(mine.theme, result.settings.theme)
        assertEquals(9.5, result.settings.crossfadeSeconds)
        assertEquals(mine.homeTiles, result.settings.homeTiles)
        assertEquals(mine.eqGains, result.settings.eqGains)
        assertTrue(result.servers.isEmpty())
    }

    @Test
    fun serversAreExportedWithoutCredentials() {
        val text = SettingsFile.export(
            Settings(),
            "1.0",
            "now",
            listOf(SettingsFile.ExportedServer("Home", "https://music.example.com", AuthMode.PLAIN)),
        )
        val result = SettingsFile.import(text, Settings())
        assertEquals(1, result.servers.size)
        assertEquals(AuthMode.PLAIN, result.servers[0].authMode)
        assertFalse(text.contains("password"))
    }

    @Test
    fun aWebClientExportImportsWhatItCan() {
        val web = """
            {"kind":"kultr.settings","version":1,"exportedAt":"2026-01-01","app":"Kultr 1.4.0",
             "settings":{"theme":"system","glass":"frosted","crossfadeSeconds":4,"injektBars":16,
                         "eqGains":[1,2,3,4,5,6,7,8,9,10,11],"volume":0.5,"accent":42,
                         "corners":"hexagonal","password":"hunter2","homeTiles":["radios"]},
             "servers":[{"label":"Home","serverUrl":"https://nd.example.com","authMode":"token","password":"x"}]}
        """.trimIndent()
        val result = SettingsFile.import(web, Settings())
        assertEquals(ThemeMode.SYSTEM, result.settings.theme)
        assertEquals(4.0, result.settings.crossfadeSeconds)
        assertEquals(16, result.settings.injektBars)
        assertEquals(10, result.settings.eqGains.size)
        assertEquals(listOf("radios"), result.settings.homeTiles)
        val skipped = result.skipped.toMap()
        assertTrue("glass" in skipped)
        assertTrue("volume" in skipped)
        assertEquals("wrong type", skipped["accent"])
        assertEquals("value not understood", skipped["corners"])
        assertTrue("password" in skipped)
        assertEquals("https://nd.example.com", result.servers.single().serverUrl)
    }

    @Test
    fun garbageIsRejected() {
        assertFailsWith<SettingsFile.InvalidFileException> { SettingsFile.import("not json", Settings()) }
        assertFailsWith<SettingsFile.InvalidFileException> { SettingsFile.import("""{"kind":"other"}""", Settings()) }
        assertFailsWith<SettingsFile.InvalidFileException> {
            SettingsFile.import("""{"kind":"kultr.settings","settings":{"nope":1}}""", Settings())
        }
    }

    @Test
    fun homeTilesResolveInOrderWithoutDuplicates() {
        val tiles = resolveHomeTiles(listOf("radios", "bogus", "radios", "recentlyAdded"))
        assertEquals(listOf("radios", "recentlyAdded"), tiles.map { it.id })
        assertEquals(HOME_TILES.size - 2, availableHomeTiles(listOf("radios", "recentlyAdded")).size)
    }

    @Test
    fun eqPresetsFillEveryBand() {
        val s = Settings().withEqPreset("Bass Boost")
        assertEquals(EQ_BANDS.size, s.eqBandGains.size)
        assertEquals(6.0, s.eqBandGains[0])
        assertEquals(10, Settings(eqGains = listOf(1.0)).eqBandGains.size)
    }
}
