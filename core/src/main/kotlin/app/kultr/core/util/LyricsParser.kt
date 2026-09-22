package app.kultr.core.util

import app.kultr.core.api.Lyrics
import app.kultr.core.api.StructuredLyrics

data class LyricLine(
    /** Seconds from the start of the track, or null when the lyrics are not synced. */
    val start: Double?,
    val text: String,
)

data class LyricsDoc(val lines: List<LyricLine>, val synced: Boolean) {
    /** Index of the line being sung at [seconds], or -1. */
    fun activeIndex(seconds: Double): Int {
        if (!synced) return -1
        var found = -1
        for ((i, line) in lines.withIndex()) {
            val start = line.start ?: continue
            if (start <= seconds + 0.15) found = i else break
        }
        return found
    }
}

object LyricsParser {
    private val LRC_TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    /**
     * Prefer OpenSubsonic's structured lyrics (synced when available), and
     * fall back to the classic plain-text endpoint. Plain text that is really
     * an LRC file is parsed as synced lyrics.
     */
    fun choose(structured: List<StructuredLyrics>, plain: Lyrics?): LyricsDoc? {
        val best = structured.firstOrNull { it.synced && !it.line.isNullOrEmpty() } ?: structured.firstOrNull()
        val lines = best?.line
        if (best != null && !lines.isNullOrEmpty()) {
            val offset = (best.offset ?: 0) / 1000.0
            return LyricsDoc(
                lines = lines.map { LyricLine(if (best.synced) it.start?.let { s -> s / 1000.0 + offset } else null, it.value) },
                synced = best.synced,
            )
        }
        val text = plain?.value?.trim().orEmpty()
        if (text.isEmpty()) return null
        return parseLrc(text) ?: LyricsDoc(text.lines().map { LyricLine(null, it) }, synced = false)
    }

    /** Parse "[mm:ss.xx] text" lines; null if the text has no timestamps. */
    fun parseLrc(text: String): LyricsDoc? {
        val out = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val stamps = LRC_TIME.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val body = raw.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toInt()
                val seconds = stamp.groupValues[2].toInt()
                val fraction = stamp.groupValues[3].takeIf { it.isNotEmpty() }?.let { ("0.$it").toDouble() } ?: 0.0
                out += LyricLine(minutes * 60 + seconds + fraction, body)
            }
        }
        if (out.isEmpty()) return null
        return LyricsDoc(out.sortedBy { it.start }, synced = true)
    }
}
