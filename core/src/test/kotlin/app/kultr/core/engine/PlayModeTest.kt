package app.kultr.core.engine

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The player banner's mode button: the same cases as Kultr for iOS. */
class PlayModeTest {
    @Test
    fun eachPressMovesToTheNextModeAndRoundAgain() {
        val seen = generateSequence(PlayMode.OFF) { it.next }.take(6).toList()
        assertEquals(
            listOf(PlayMode.OFF, PlayMode.SHUFFLE, PlayMode.REPEAT, PlayMode.REPEAT_ONE, PlayMode.KAROUSEL, PlayMode.OFF),
            seen,
        )
    }

    @Test
    fun eachModeIsOnAlone() {
        for (mode in PlayMode.entries) {
            assertEquals(mode, PlayMode.of(mode.shuffle, mode.repeat, mode.karousel), "$mode reads back as itself")
        }
        assertTrue(PlayMode.SHUFFLE.shuffle)
        assertEquals(RepeatMode.OFF, PlayMode.SHUFFLE.repeat)
        assertFalse(PlayMode.KAROUSEL.shuffle)
        assertEquals(RepeatMode.OFF, PlayMode.KAROUSEL.repeat)
        assertEquals(RepeatMode.ONE, PlayMode.REPEAT_ONE.repeat)
    }

    @Test
    fun withSeveralOnTheBannerShowsTheOneThatDecidesMost() {
        assertEquals(PlayMode.KAROUSEL, PlayMode.of(shuffle = true, repeat = RepeatMode.ALL, karousel = true))
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.of(shuffle = true, repeat = RepeatMode.ONE, karousel = false))
        assertEquals(PlayMode.REPEAT, PlayMode.of(shuffle = true, repeat = RepeatMode.ALL, karousel = false))
        assertEquals(PlayMode.SHUFFLE, PlayMode.of(shuffle = true, repeat = RepeatMode.OFF, karousel = false))
        assertEquals(PlayMode.OFF, PlayMode.of(shuffle = false, repeat = RepeatMode.OFF, karousel = false))
    }
}
