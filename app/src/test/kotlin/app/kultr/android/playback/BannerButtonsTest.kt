package app.kultr.android.playback

import android.app.Application
import android.content.Context
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.PlatformSessionState
import app.kultr.core.engine.PlayMode
import com.google.common.collect.ImmutableList
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The mode and like buttons in the system's player banner: what Android is
 * handed for the media player in the notification shade and on the lock
 * screen, read back from the platform media session the way System UI reads
 * it, with Kultr's own buttons and connect rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BannerButtonsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private var session: MediaSession? = null
    private var notificationController: MediaController? = null

    @After
    fun release() {
        notificationController?.release()
        session?.release()
    }

    /** A player with two songs, playing the first, that can do everything. */
    private class TwoSongs : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("a").setMediaItem(MediaItem.Builder().setMediaId("a").build()).build(),
                    MediaItemData.Builder("b").setMediaItem(MediaItem.Builder().setMediaId("b").build()).build(),
                ),
            )
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    /**
     * A session set up as PlaybackService sets it up, with Media3's
     * notification controller connected: that controller is what the
     * platform session, and so the system's banner, is configured from.
     */
    private fun start(buttons: List<CommandButton>): MediaSession {
        val session = MediaSession.Builder(context, TwoSongs())
            .setId(UUID.randomUUID().toString())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) =
                    BannerButtons.connection(session, controller, buttons)
            })
            .build()
        this.session = session
        val connecting = MediaController.Builder(context, session.token)
            .setConnectionHints(Bundle().apply { putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true) })
            .buildAsync()
        idle()
        notificationController = connecting.get()
        session.setMediaButtonPreferences(buttons)
        idle()
        return session
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** What the platform session, and so System UI, is handed. */
    private fun banner(session: MediaSession): PlaybackState = PlatformSessionState.of(session)!!

    @Test
    fun theBannerGetsTheModeButtonThenTheHeart() {
        val state = banner(start(BannerButtons.of(PlayMode.SHUFFLE, liked = false)))
        // Android 13 and later draw the custom actions after next, in this order.
        assertEquals(listOf(BannerButtons.MODE.customAction, BannerButtons.LIKE.customAction), state.customActions.map { it.action })
        assertEquals(listOf("Shuffle on", "Add to favourites"), state.customActions.map { it.name.toString() })
        assertTrue(state.customActions.all { it.icon != 0 }, "every button has an icon")
    }

    @Test
    fun previousPlayAndNextStayWhereTheyAre() {
        val actions = banner(start(BannerButtons.of(PlayMode.OFF, liked = true))).actions
        for (action in listOf(PlaybackState.ACTION_SKIP_TO_PREVIOUS, PlaybackState.ACTION_PLAY_PAUSE, PlaybackState.ACTION_SKIP_TO_NEXT)) {
            assertNotEquals(0L, actions and action, "action $action is offered")
        }
    }

    @Test
    fun theButtonsFollowTheModeAndTheLike() {
        val session = start(BannerButtons.of(PlayMode.OFF, liked = false))
        val before = banner(session).customActions
        session.setMediaButtonPreferences(BannerButtons.of(PlayMode.KAROUSEL, liked = true))
        idle()
        val after = banner(session).customActions
        assertEquals(listOf("Shuffle off", "Add to favourites"), before.map { it.name.toString() })
        assertEquals(listOf("Karousel on", "Remove from favourites"), after.map { it.name.toString() })
        assertEquals(app.kultr.android.R.drawable.ic_banner_karousel, after[0].icon)
        assertNotEquals(before[1].icon, after[1].icon, "the heart fills")
    }

    @Test
    fun withNothingToLikeOnlyTheModeButtonShows() {
        val state = banner(start(BannerButtons.of(PlayMode.REPEAT_ONE, liked = null)))
        assertEquals(listOf("Repeat this track"), state.customActions.map { it.name.toString() })
    }

    @Test
    fun theNotificationOnAndroid12AndOlderHasAllFiveButtons() {
        val session = start(BannerButtons.of(PlayMode.REPEAT, liked = false))
        val buttons = object : DefaultMediaNotificationProvider(context) {
            fun of(buttons: List<CommandButton>) = getMediaButtons(
                session,
                Player.Commands.Builder().addAllCommands().build(),
                ImmutableList.copyOf(buttons),
                /* showPauseButton= */ true,
            )
        }.of(BannerButtons.of(PlayMode.REPEAT, liked = false))
        assertEquals(
            listOf(null, null, null, BannerButtons.MODE.customAction, BannerButtons.LIKE.customAction),
            buttons.map { it.sessionCommand?.customAction },
        )
    }
}
