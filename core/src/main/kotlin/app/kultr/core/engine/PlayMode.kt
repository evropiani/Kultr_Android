package app.kultr.core.engine

/**
 * The one mode button in the system's player banner (the media notification,
 * the lock screen, Samsung's Now Bar): each press moves on to the next of
 * off, shuffle, repeat, repeat one and Karousel. In the app, shuffle and
 * repeat are separate buttons that can be on together; the banner has room
 * for one, so there each mode is on alone.
 */
enum class PlayMode {
    OFF,
    SHUFFLE,
    REPEAT,
    REPEAT_ONE,
    KAROUSEL,
    ;

    /** The mode one press of the button moves to. */
    val next: PlayMode get() = entries[(ordinal + 1) % entries.size]

    /** What the player does in this mode. */
    val shuffle: Boolean get() = this == SHUFFLE
    val repeat: RepeatMode
        get() = when (this) {
            REPEAT -> RepeatMode.ALL
            REPEAT_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    val karousel: Boolean get() = this == KAROUSEL

    companion object {
        /**
         * The mode the banner shows for the player as it is. With several on
         * at once (set in the app), the one that decides most about what plays
         * next: Karousel, then repeating one track, the queue, then shuffle.
         */
        fun of(shuffle: Boolean, repeat: RepeatMode, karousel: Boolean): PlayMode = when {
            karousel -> KAROUSEL
            repeat == RepeatMode.ONE -> REPEAT_ONE
            repeat == RepeatMode.ALL -> REPEAT
            shuffle -> SHUFFLE
            else -> OFF
        }
    }
}
