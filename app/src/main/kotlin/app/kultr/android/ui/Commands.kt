package app.kultr.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.kultr.android.playback.StationRequest
import app.kultr.core.api.Song
import app.kultr.core.util.CommandPlace
import app.kultr.core.util.KultrCommand
import app.kultr.core.util.parseCommand
import app.kultr.core.util.t

/** The command bar in Search: words Kultr can do, rather than look for. */
object Commands {
    fun parse(text: String, actions: AppActions): KultrCommand? {
        val settings = actions.graph.settings.current
        val stations = settings.stations + actions.graph.stations.tally.value?.decades.orEmpty()
        // Each station answers to its own name, and a starting one to its name in the phone's language too.
        return parseCommand(text, stations.flatMap { listOf(it.name, it.title) }.distinctBy { it.lowercase() }, settings.crates.map { it.name })
    }

    /** What a command names, to show search results for it under its row. */
    fun subject(command: KultrCommand?): String? = when (command) {
        is KultrCommand.Play -> command.what
        is KultrCommand.Shuffle -> command.what
        null -> null
        else -> ""
    }

    /** What the command will do, for the row above the search results. */
    fun describe(command: KultrCommand): Pair<ImageVector, String> = when (command) {
        is KultrCommand.Play -> Icons.Rounded.PlayArrow to t("Play “%@”", command.what)
        is KultrCommand.Shuffle -> Icons.Rounded.Shuffle to t("Shuffle “%@”", command.what)
        KultrCommand.ShuffleAll -> Icons.Rounded.Shuffle to t("Shuffle your whole library")
        is KultrCommand.Station -> Icons.Rounded.Tune to t("Play the station %@", t(command.name))
        is KultrCommand.Sleep -> Icons.Rounded.Bedtime to t("Sleep timer: %@ min", command.minutes)
        KultrCommand.SleepAfterTrack -> Icons.Rounded.Bedtime to t("Stop after this track")
        KultrCommand.Pause -> Icons.Rounded.Pause to t("Pause")
        KultrCommand.Resume -> Icons.Rounded.PlayArrow to t("Play")
        KultrCommand.Next -> Icons.Rounded.SkipNext to t("Next track")
        KultrCommand.Previous -> Icons.Rounded.SkipPrevious to t("Previous track")
        KultrCommand.MixNow -> Icons.AutoMirrored.Rounded.CallMerge to t("Mix into the next track")
        is KultrCommand.Plan -> Icons.Rounded.ShowChart to (command.minutes?.let { t("Plan a set of %@ min", it) } ?: t("Plan a set"))
        is KultrCommand.Open -> Icons.AutoMirrored.Rounded.OpenInNew to t("Open %@", label(command.place))
        is KultrCommand.Crate -> Icons.Rounded.Inventory2 to t("Open the crate %@", command.name)
        is KultrCommand.PlayCrate -> Icons.Rounded.Inventory2 to t("Play the crate %@", command.name)
    }

    private fun label(place: CommandPlace) = when (place) {
        CommandPlace.HOME -> t("Home")
        CommandPlace.LIBRARY -> t("Library")
        CommandPlace.SETTINGS -> t("Settings")
        CommandPlace.LISTENING -> t("Listening")
        CommandPlace.RECAP -> t("the monthly recap")
        CommandPlace.STATIONS -> t("Stations")
        CommandPlace.CRATES -> t("Crates")
        CommandPlace.DOWNLOADS -> t("Downloads")
        CommandPlace.PLANNER -> t("the energy planner")
        CommandPlace.SEARCH -> t("Search")
    }

    /** Do it. [openTab] opens one of the tabs by its route. */
    fun run(command: KultrCommand, actions: AppActions, openTab: (String) -> Unit) {
        val graph = actions.graph
        val player = graph.player
        when (command) {
            is KultrCommand.Play, is KultrCommand.Shuffle -> actions.launch {
                val what = subject(command).orEmpty()
                val songs = music(what, actions)
                when {
                    songs.isEmpty() -> graph.messages.show(t("Nothing in your library matches “%@”.", what))
                    command is KultrCommand.Shuffle -> actions.shuffle(songs)
                    else -> actions.play(songs)
                }
            }
            KultrCommand.ShuffleAll -> actions.launch { actions.shuffle(graph.library.randomSongs(200)) }
            is KultrCommand.Station -> {
                val station = (graph.settings.current.stations + graph.stations.tally.value?.decades.orEmpty())
                    .firstOrNull { it.name.equals(command.name, ignoreCase = true) || it.title.equals(command.name, ignoreCase = true) }
                if (station == null) {
                    graph.messages.show(t("There's no station called “%@”. Stations has the list.", command.name))
                } else {
                    player.station(StationRequest.Play(station))
                }
            }
            is KultrCommand.Sleep -> {
                graph.hub.setSleepTimer(command.minutes)
                graph.messages.show(t("Sleep timer: %@ minutes.", command.minutes))
            }
            KultrCommand.SleepAfterTrack -> {
                graph.hub.sleepAtEndOfTrack()
                graph.messages.show(t("Stopping after this track."))
            }
            KultrCommand.Pause -> if (player.state.value.playWhenReady) player.toggle()
            KultrCommand.Resume -> if (!player.state.value.playWhenReady) player.toggle()
            KultrCommand.Next -> player.next()
            KultrCommand.Previous -> player.previous()
            KultrCommand.MixNow -> graph.hub.mixNow()
            is KultrCommand.Plan -> actions.navigate(Routes.planner(command.minutes))
            is KultrCommand.Open -> when (command.place) {
                CommandPlace.HOME -> openTab(Routes.HOME)
                CommandPlace.LIBRARY -> openTab(Routes.LIBRARY)
                CommandPlace.SETTINGS -> openTab(Routes.SETTINGS)
                CommandPlace.SEARCH -> openTab(Routes.SEARCH)
                CommandPlace.LISTENING -> actions.navigate(Routes.STATS)
                CommandPlace.RECAP -> actions.navigate(Routes.RECAP)
                CommandPlace.STATIONS -> actions.navigate(Routes.STATIONS)
                CommandPlace.CRATES -> actions.openLibrary(LibraryTab.CRATES)
                CommandPlace.DOWNLOADS -> actions.openDownloads()
                CommandPlace.PLANNER -> actions.navigate(Routes.planner())
            }
            is KultrCommand.Crate -> crate(command.name, actions)?.let { actions.openCrate(it.id) }
            is KultrCommand.PlayCrate -> {
                val crate = crate(command.name, actions) ?: return
                actions.launch {
                    val songs = graph.crates.songs(crate)
                    if (songs.isEmpty()) graph.messages.show(t("“%@” is empty.", crate.name)) else actions.play(songs)
                }
            }
        }
    }

    private fun crate(name: String, actions: AppActions) = actions.graph.settings.current.crates.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The music [what] names: an artist, then an album, then tracks, as the library search finds them. */
    private suspend fun music(what: String, actions: AppActions): List<Song> {
        val library = actions.graph.library
        val found = library.search(what)
        val artist = found.artists.firstOrNull { it.name.equals(what, ignoreCase = true) }
            ?: found.artists.firstOrNull()?.takeIf { found.albums.isEmpty() && found.songs.isEmpty() }
        if (artist != null) return library.songsOfArtistNow(artist.id)
        val album = found.albums.firstOrNull { it.name.equals(what, ignoreCase = true) } ?: found.albums.firstOrNull()?.takeIf { found.songs.isEmpty() }
        if (album != null) return library.songsOfAlbumNow(album.id)
        found.artists.firstOrNull()?.let { return library.songsOfArtistNow(it.id) }
        return found.songs
    }
}
