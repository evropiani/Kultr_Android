package app.kultr.core.util

import app.kultr.core.api.Song
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One play from this phone's history: what, when (epoch ms) and for how long. */
data class Play(val songId: String, val playedAt: Long, val seconds: Int)

/**
 * One month of listening on this phone, for the recap in Listening. Built from
 * the phone's own history, because the server only keeps a play count and the
 * last time per track, not each play.
 */
data class MonthRecap(
    val month: YearMonth,
    val seconds: Long,
    val plays: Int,
    val tracks: Int,
    val artists: Int,
    /** Most played first; ties go to more time listened. */
    val topSongs: List<Entry<Song>>,
    val topArtists: List<Entry<Name>>,
    val topAlbums: List<Entry<Name>>,
    /** The day with the most listening. */
    val busiestDay: Pair<LocalDate, Long>?,
    /** Tracks played for the first time on this phone this month. */
    val firstListens: Int,
    /** Time listened the month before, to compare with. */
    val previousSeconds: Long,
) {
    data class Entry<T>(val item: T, val plays: Int, val seconds: Long)

    /** An artist or an album, as far as the tracks played know it. */
    data class Name(val id: String?, val name: String, val artist: String? = null, val coverArt: String? = null)

    val isEmpty: Boolean get() = plays == 0
}

private fun Play.month(zone: ZoneId) = YearMonth.from(Instant.ofEpochMilli(playedAt).atZone(zone))

/** The months with any listening in [history], newest first. */
fun recapMonths(history: List<Play>, zone: ZoneId = ZoneId.systemDefault()): List<YearMonth> =
    history.map { it.month(zone) }.distinct().sortedDescending()

/**
 * The recap of [month]. [songs] resolves the ids in [history]; a track no
 * longer in the library still counts towards the time and the plays, it just
 * can't be listed.
 */
fun monthRecap(
    history: List<Play>,
    songs: Map<String, Song>,
    month: YearMonth,
    zone: ZoneId = ZoneId.systemDefault(),
    top: Int = 10,
): MonthRecap {
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val previousStart = month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    class Tally(val name: MonthRecap.Name) {
        var plays = 0
        var seconds = 0L
    }
    var seconds = 0L
    var previousSeconds = 0L
    var plays = 0
    val bySong = HashMap<String, Tally>()
    val byArtist = HashMap<String, Tally>()
    val byAlbum = HashMap<String, Tally>()
    val byDay = HashMap<LocalDate, Long>()
    val heardBefore = HashSet<String>()

    fun HashMap<String, Tally>.count(key: String, name: MonthRecap.Name, time: Long) {
        getOrPut(key) { Tally(name) }.apply {
            this.plays++
            this.seconds += time
        }
    }

    for (play in history) {
        if (play.playedAt < start) {
            heardBefore += play.songId
            if (play.playedAt >= previousStart) previousSeconds += play.seconds
        }
        if (play.playedAt !in start until end) continue
        val time = play.seconds.toLong()
        seconds += time
        plays++
        bySong.count(play.songId, MonthRecap.Name(play.songId, play.songId), time)
        val day = Instant.ofEpochMilli(play.playedAt).atZone(zone).toLocalDate()
        byDay[day] = (byDay[day] ?: 0) + time
        val song = songs[play.songId] ?: continue
        (song.artist ?: song.artistId)?.let { artist ->
            byArtist.count(song.artistId ?: artist, MonthRecap.Name(song.artistId, artist), time)
        }
        (song.album ?: song.albumId)?.let { album ->
            byAlbum.count(song.albumId ?: album, MonthRecap.Name(song.albumId, album, song.artist, song.coverArt), time)
        }
    }

    fun <T> ranked(items: List<Triple<T, Tally, String>>): List<MonthRecap.Entry<T>> =
        items.sortedWith(compareByDescending<Triple<T, Tally, String>> { it.second.plays }.thenByDescending { it.second.seconds }.thenBy { it.third })
            .take(top)
            .map { MonthRecap.Entry(it.first, it.second.plays, it.second.seconds) }

    return MonthRecap(
        month = month,
        seconds = seconds,
        plays = plays,
        tracks = bySong.size,
        artists = byArtist.size,
        topSongs = ranked(bySong.mapNotNull { (id, tally) -> songs[id]?.let { Triple(it, tally, id) } }),
        topArtists = ranked(byArtist.map { (key, tally) -> Triple(tally.name, tally, key) }),
        topAlbums = ranked(byAlbum.map { (key, tally) -> Triple(tally.name, tally, key) }),
        busiestDay = byDay.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Long>> { it.value }.thenByDescending { it.key })?.toPair(),
        firstListens = bySong.keys.count { it !in heardBefore },
        previousSeconds = previousSeconds,
    )
}
