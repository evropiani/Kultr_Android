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
    "start" to CommandPlace.HOME, "startseite" to CommandPlace.HOME, "mediathek" to CommandPlace.LIBRARY,
    "bibliothek" to CommandPlace.LIBRARY, "einstellungen" to CommandPlace.SETTINGS, "hörstatistik" to CommandPlace.LISTENING,
    "statistik" to CommandPlace.LISTENING, "verlauf" to CommandPlace.LISTENING, "rückblick" to CommandPlace.RECAP,
    "monatsrückblick" to CommandPlace.RECAP, "stationen" to CommandPlace.STATIONS, "regler" to CommandPlace.STATIONS,
    "kisten" to CommandPlace.CRATES, "planer" to CommandPlace.PLANNER, "energieplaner" to CommandPlace.PLANNER,
    "suche" to CommandPlace.SEARCH,
)

/**
 * Reads plain words as a command: "play night drive", "shuffle radiohead",
 * "sleep in 30 minutes", "next", "open settings", "plan an hour". [stations]
 * and [crates] are their names, so "play night drive" can start the station
 * rather than search for a song. Null when the words aren't a command. The
 * same words as Kultr for iOS, in English and in German ("spiele Nachtfahrt",
 * "schlaf in 30 Minuten", "öffne Einstellungen").
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
        "pause", "stop", "stop the music", "be quiet",
        "stopp", "stopp die musik", "anhalten", "ruhe", "pausieren", "pausiere",
        -> return KultrCommand.Pause
        "play", "resume", "continue", "go on", "unpause",
        "fortsetzen", "weiterspielen", "spiel weiter", "spiele weiter", "abspielen", "wiedergeben",
        -> return KultrCommand.Resume
        "next", "skip", "next track", "next song", "skip this",
        "weiter", "nächster", "nächstes", "nächster titel", "nächstes lied", "nächster song", "überspringen", "skippen",
        -> return KultrCommand.Next
        "previous", "back", "go back", "previous track", "previous song", "last song",
        "zurück", "vorheriger", "vorheriger titel", "voriger titel", "vorheriges lied", "letzter titel", "letztes lied",
        -> return KultrCommand.Previous
        "mix", "mix now", "mix it", "blend", "mix into the next one",
        "mischen", "jetzt mischen", "mixen", "jetzt mixen", "überblenden", "in den nächsten mischen",
        -> return KultrCommand.MixNow
        "shuffle", "shuffle all", "shuffle everything", "shuffle my library", "surprise me",
        "zufällig", "zufall", "alles zufällig", "zufallswiedergabe", "überrasch mich", "überrasche mich",
        -> return KultrCommand.ShuffleAll
        "sleep after this", "sleep after this song", "sleep after this track", "stop after this song", "stop after this track",
        "nach diesem titel stoppen", "nach diesem lied stoppen", "stopp nach diesem titel", "stopp nach diesem lied",
        "schlafen nach diesem titel", "schlafen nach diesem lied",
        -> return KultrCommand.SleepAfterTrack
    }
    val first = words[0]
    // Sleep in 30 minutes, stop in an hour, timer 45; schlaf in 30 Minuten.
    if (first in SLEEP || line.startsWith("sleep timer")) {
        val after = if (line.startsWith("sleep timer")) words.drop(2) else words.drop(1)
        minutesIn(after)?.let { return KultrCommand.Sleep(it) }
    }
    // Plan a set, plan an hour, plan 90 minutes; plane eine Stunde, Set planen.
    if (first in setOf("plan", "plane", "planen") || PLAN_LINES.any { line.startsWith(it) }) {
        return KultrCommand.Plan(minutesIn(words.drop(1)))
    }
    // Open settings, show my stations, go to listening; öffne Einstellungen, zeig meine Kisten.
    if (first in OPEN) {
        val target = words.drop(1).toMutableList()
        if (target.firstOrNull() in setOf("to", "zu", "zur", "zum", "in", "ins")) target.removeAt(0)
        if (target.firstOrNull() in setOf("my", "the", "mein", "meine", "meinen", "die", "den", "das", "der")) target.removeAt(0)
        if (target.lastOrNull() in setOf("an", "auf")) target.removeAt(target.size - 1)
        val place = target.joinToString(" ")
        PLACES[place]?.let { return KultrCommand.Open(it) }
        if (target.firstOrNull() in CRATE) named(target.drop(1).joinToString(" "), crates)?.let { return KultrCommand.Crate(it) }
        named(place, crates)?.let { return KultrCommand.Crate(it) }
    }
    if (first in CRATE) named(rest(1), crates)?.let { return KultrCommand.Crate(it) }
    // Station night drive, tune in to warm up, play the station peak hour; spiele die Station Nachtfahrt.
    if (first == "station") return KultrCommand.Station(named(rest(1), stations) ?: rest(1))
    if (words.size > 2 && first == "tune" && words[1] in setOf("in", "to")) {
        val name = if (words[1] == "in" && words.size > 3 && words[2] == "to") rest(3) else rest(2)
        return KultrCommand.Station(named(name, stations) ?: name)
    }
    if (STATION_LINES.any { line.startsWith(it) }) return KultrCommand.Station(named(rest(3), stations) ?: rest(3))
    if (first in setOf("shuffle", "zufällig") && words.size > 1) return KultrCommand.Shuffle(rest(1))
    if (first in setOf("play", "spiel", "spiele") && words.size > 1) {
        var what = rest(1)
        // "spiele Radiohead ab"
        if (first != "play" && what.endsWith(" ab")) what = what.dropLast(3)
        named(what, stations)?.let { return KultrCommand.Station(it) }
        named(what, crates)?.let { return KultrCommand.PlayCrate(it) }
        FILLERS.firstOrNull { what.startsWith(it) && what.length > it.length }?.let { return KultrCommand.Play(what.removePrefix(it)) }
        return KultrCommand.Play(what)
    }
    return null
}

private val SLEEP = setOf("sleep", "timer", "stop", "schlaf", "schlafe", "schlafen", "schlaftimer", "stopp", "stoppe")
private val OPEN = setOf("open", "show", "go", "öffne", "öffnen", "zeig", "zeige", "geh", "gehe")
private val CRATE = setOf("crate", "kiste")
private val PLAN_LINES = listOf("build a set", "make a set", "set planen", "ein set planen", "bau ein set", "baue ein set")
private val STATION_LINES = listOf("play the station ", "spiel die station ", "spiele die station ")
private val FILLERS = listOf("some ", "etwas von ", "was von ", "etwas ", "was ")

private val NUMBERS = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20,
    "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    "eins" to 1, "zwei" to 2, "drei" to 3, "fünf" to 5, "zehn" to 10, "fünfzehn" to 15, "zwanzig" to 20,
    "dreißig" to 30, "vierzig" to 40, "fünfzig" to 50, "sechzig" to 60, "neunzig" to 90,
)

/** "30", "30 minutes", "half an hour", "an hour", "2 hours", "1h", "90min"; "eine Stunde", "30 Minuten". */
private fun minutesIn(input: List<String>): Int? {
    val words = if (input.firstOrNull() in setOf("in", "for", "after", "nach", "für")) input.drop(1) else input
    val line = words.joinToString(" ")
    when {
        listOf("half an hour", "half hour", "einer halben stunde", "eine halbe stunde", "halbe stunde").any { line.startsWith(it) } -> return 30
        listOf("an hour and a half", "one and a half hours", "anderthalb stunden", "eineinhalb stunden").any { line.startsWith(it) } -> return 90
        listOf("an hour", "one hour", "einer stunde", "eine stunde").any { line.startsWith(it) } || line == "hour" || line == "stunde" -> return 60
    }
    val first = words.firstOrNull() ?: return null
    val digits = first.takeWhile { it.isDigit() }
    val inline = first.drop(digits.length)
    val value = digits.toIntOrNull() ?: NUMBERS[first] ?: return null
    val unit = inline.ifEmpty { words.getOrNull(1) ?: "min" }
    val minutes = if (unit.startsWith("h") || unit.startsWith("stund") || unit.startsWith("std")) value * 60 else value
    return minutes.takeIf { it in 1..24 * 60 }
}
