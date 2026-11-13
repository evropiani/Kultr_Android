package app.kultr.android.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import app.kultr.android.R
import app.kultr.core.engine.PlayMode
import app.kultr.core.util.t

/**
 * The two buttons the system's player banner (the media player in the
 * notification shade and on the lock screen, Samsung's Now Bar) shows beside
 * previous, play and next: a mode button that moves through off, shuffle,
 * repeat, repeat one and Karousel, and a heart to like the song or take the
 * like back.
 *
 * Each button asks for its secondary slot first, beside previous or next,
 * which players built on Media3 honour, then for the overflow. The overflow
 * is what reaches the system: Media3 hands Android only buttons placed in
 * back, forward or overflow, so a button that asks for a secondary slot
 * alone never appears in the banner (Kultr 2.2.0 and 2.2.1). In the
 * overflow the two become the session's custom actions, the mode button
 * first. Android 13 and later draw them in that order after next, in the
 * expanded player only; the collapsed one keeps to previous, play and next.
 */
internal object BannerButtons {
    /** The mode button: off, shuffle, repeat, repeat one, Karousel. */
    val MODE = SessionCommand("app.kultr.banner.MODE", Bundle.EMPTY)

    /** The heart. */
    val LIKE = SessionCommand("app.kultr.banner.LIKE", Bundle.EMPTY)

    /**
     * The banner's buttons for [mode]. [liked] is whether the song playing
     * is a favourite, or null when there is nothing to like (a radio stream,
     * nothing playing), in which case only the mode button shows.
     */
    fun of(mode: PlayMode, liked: Boolean?): List<CommandButton> {
        val modeButton = CommandButton.Builder(
            when (mode) {
                PlayMode.OFF -> CommandButton.ICON_SHUFFLE_OFF
                PlayMode.SHUFFLE -> CommandButton.ICON_SHUFFLE_ON
                PlayMode.REPEAT -> CommandButton.ICON_REPEAT_ALL
                PlayMode.REPEAT_ONE -> CommandButton.ICON_REPEAT_ONE
                PlayMode.KAROUSEL -> CommandButton.ICON_UNDEFINED
            },
        )
            .apply { if (mode == PlayMode.KAROUSEL) setCustomIconResId(R.drawable.ic_banner_karousel) }
            .setDisplayName(
                when (mode) {
                    PlayMode.OFF -> t("Shuffle off")
                    PlayMode.SHUFFLE -> t("Shuffle on")
                    PlayMode.REPEAT -> t("Repeat the queue")
                    PlayMode.REPEAT_ONE -> t("Repeat this track")
                    PlayMode.KAROUSEL -> t("Karousel on")
                },
            )
            .setSessionCommand(MODE)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        if (liked == null) return listOf(modeButton)
        val likeButton = CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (liked) t("Remove from favourites") else t("Add to favourites"))
            .setSessionCommand(LIKE)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(modeButton, likeButton)
    }

    /**
     * What a controller that connects gets: the mode and like commands on
     * top of what Media3 grants by default (the full set for a trusted
     * controller, such as Kultr's own player, System UI and Media3's
     * notification controller, which is what the system's banner is built
     * from; the reduced one for anything else), and [buttons].
     *
     * Not built on super.onConnect: that is an empty placeholder (no
     * commands at all) that Media3 swaps for these defaults only when it
     * comes back untouched. Copying its commands, as 2.2.0 did, left every
     * controller, Kultr's own included, unable to play anything.
     */
    fun connection(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        buttons: List<CommandButton>,
    ): MediaSession.ConnectionResult {
        val trusted = controller.isTrusted
        val sessionCommands = if (trusted) {
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
        } else {
            MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS
        }
        val playerCommands = if (trusted) {
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        } else {
            MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands.buildUpon().add(MODE).add(LIKE).build())
            .setAvailablePlayerCommands(playerCommands)
            .setMediaButtonPreferences(buttons)
            .build()
    }
}
