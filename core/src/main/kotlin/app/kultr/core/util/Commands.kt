package app.kultr.core.util

/** Places the command bar can open. */
enum class CommandPlace { HOME, LIBRARY, SETTINGS, LISTENING, RECAP, STATIONS, CRATES, DOWNLOADS, PLANNER, SEARCH }

/** Something typed into the command bar that Kultr can do, rather than look for. */
sealed interface KultrCommand {
    data class Play(val what: String) : KultrCommand
    data class Shuffle(val what: String) : KultrCommand
    data object ShuffleAll : KultrCommand
    data class Station(val name: String) : KultrCommand
    data class Sleep(val minutes: Int) : KultrCommand
    data object SleepAfterTrack : KultrCommand
    data object Pause : KultrCommand
    data object Resume : KultrCommand
    data object Next : KultrCommand
    data object Previous : KultrCommand
    data object MixNow : KultrCommand
    data class Plan(val minutes: Int?) : KultrCommand
    data class Open(val place: CommandPlace) : KultrCommand
    data class Crate(val name: String) : KultrCommand
    data class PlayCrate(val name: String) : KultrCommand
}

private val NOT_WORD = Regex("[^\\p{L}\\p{N}' ]")

private val PLACES = mapOf(
    "home" to CommandPlace.HOME, "library" to CommandPlace.LIBRARY, "settings" to CommandPlace.SETTINGS,
    "preferences" to CommandPlace.SETTINGS, "listening" to CommandPlace.LISTENING, "stats" to CommandPlace.LISTENING,
    "statistics" to CommandPlace.LISTENING, "history" to CommandPlace.LISTENING, "recap" to CommandPlace.RECAP,
    "monthly recap" to CommandPlace.RECAP, "month" to CommandPlace.RECAP, "stations" to CommandPlace.STATIONS,
    "dial" to CommandPlace.STATIONS, "crates" to CommandPlace.CRATES, "downloads" to CommandPlace.DOWNLOADS,
    "planner" to CommandPlace.PLANNER, "energy planner" to CommandPlace.PLANNER, "search" to CommandPlace.SEARCH,
)

/**
 * Reads plain words as a command: "play night drive", "shuffle radiohead",
 * "sleep in 30 minutes", "next", "open settings", "plan an hour". [stations]
 * and [crates] are their names, so "play night drive" can start the station
 * rather than search for a song. Null when the words aren't a command. The
 * same words as Kultr for iOS.
 */
fun parseCommand(text: String, stations: List<String> = emptyList(), crates: List<String> = emptyList()): KultrCommand? {
    val words = text.lowercase().replace(NOT_WORD, " ").split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    val line = words.joinToString(" ")
    fun rest(after: Int) = words.drop(after).joinToString(" ")
    fun named(name: String, names: List<String>): String? {
        val wanted = name.trim()
        if (wanted.isEmpty()) return null
        return names.firstOrNull { it.lowercase() == wanted } ?: names.firstOrNull { wanted.length >= 3 && it.lowercase().startsWith(wanted) }
    }

    when (line) {
        "pause", "stop", "stop the music", "be quiet" -> return KultrCommand.Pause
        "play", "resume", "continue", "go on", "unpause" -> return KultrCommand.Resume
        "next", "skip", "next track", "next song", "skip this" -> return KultrCommand.Next
        "previous", "back", "go back", "previous track", "previous song", "last song" -> return KultrCommand.Previous
        "mix", "mix now", "mix it", "blend", "mix into the next one" -> return KultrCommand.MixNow
        "shuffle", "shuffle all", "shuffle everything", "shuffle my library", "surprise me" -> return KultrCommand.ShuffleAll
        "sleep after this", "sleep after this song", "sleep after this track", "stop after this song", "stop after this track" ->
            return KultrCommand.SleepAfterTrack
    }
    val first = words[0]
    // Sleep in 30 minutes, stop in an hour, timer 45.
    if (first in setOf("sleep", "timer", "stop") || line.startsWith("sleep timer")) {
        val after = if (line.startsWith("sleep timer")) words.drop(2) else words.drop(1)
        minutesIn(after)?.let { return KultrCommand.Sleep(it) }
    }
    // Plan a set, plan an hour, plan 90 minutes.
    if (first == "plan" || line.startsWith("build a set") || line.startsWith("make a set")) {
        return KultrCommand.Plan(minutesIn(words.drop(1)))
    }
    // Open settings, show my stations, go to listening.
    if (first in setOf("open", "show", "go")) {
        val target = words.drop(1).toMutableList()
        if (target.firstOrNull() == "to") target.removeAt(0)
        if (target.firstOrNull() in setOf("my", "the")) target.removeAt(0)
        val place = target.joinToString(" ")
        PLACES[place]?.let { return KultrCommand.Open(it) }
        if (target.firstOrNull() == "crate") named(target.drop(1).joinToString(" "), crates)?.let { return KultrCommand.Crate(it) }
        named(place, crates)?.let { return KultrCommand.Crate(it) }
    }
    if (first == "crate") named(rest(1), crates)?.let { return KultrCommand.Crate(it) }
    // Station night drive, tune in to warm up, play the station peak hour.
    if (first == "station") return KultrCommand.Station(named(rest(1), stations) ?: rest(1))
    if (words.size > 2 && first == "tune" && words[1] in setOf("in", "to")) {
        val name = if (words[1] == "in" && words.size > 3 && words[2] == "to") rest(3) else rest(2)
        return KultrCommand.Station(named(name, stations) ?: name)
    }
    if (line.startsWith("play the station ")) return KultrCommand.Station(named(rest(3), stations) ?: rest(3))
    if (first == "shuffle" && words.size > 1) return KultrCommand.Shuffle(rest(1))
    if (first == "play" && words.size > 1) {
        val what = rest(1)
        named(what, stations)?.let { return KultrCommand.Station(it) }
        named(what, crates)?.let { return KultrCommand.PlayCrate(it) }
        return KultrCommand.Play(what.removePrefix("some "))
    }
    return null
}

private val NUMBERS = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20,
    "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90,
)

/** "30", "30 minutes", "half an hour", "an hour", "2 hours", "1h", "90min". */
private fun minutesIn(input: List<String>): Int? {
    val words = if (input.firstOrNull() in setOf("in", "for", "after")) input.drop(1) else input
    val line = words.joinToString(" ")
    when {
        line.startsWith("half an hour") || line.startsWith("half hour") -> return 30
        line.startsWith("an hour and a half") || line.startsWith("one and a half hours") -> return 90
        line.startsWith("an hour") || line.startsWith("one hour") || line == "hour" -> return 60
    }
    val first = words.firstOrNull() ?: return null
    val digits = first.takeWhile { it.isDigit() }
    val inline = first.drop(digits.length)
    val value = digits.toIntOrNull() ?: NUMBERS[first] ?: return null
    val unit = inline.ifEmpty { words.getOrNull(1) ?: "min" }
    val minutes = if (unit.startsWith("h")) value * 60 else value
    return minutes.takeIf { it in 1..24 * 60 }
}
