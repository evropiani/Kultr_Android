package app.kultr.core.util

import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Formatting helpers shared across the UI. */
object Format {
    /** "3:07", or "1:02:03" past an hour. */
    fun time(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds < 0) return "0:00"
        val total = floor(seconds).toLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    fun timeMs(ms: Long?): String = time(ms?.let { it / 1000.0 })

    /** "1 hr 24 min" style duration, for albums and playlists. */
    fun duration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return "—"
        val hours = seconds / 3600
        val minutes = ((seconds % 3600) / 60.0).roundToInt()
        return when {
            hours > 0 -> t("%@ hr %@ min", hours, minutes)
            minutes > 0 -> t("%@ min", minutes)
            else -> t("%@ sec", seconds)
        }
    }

    fun bytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        val exponent = minOf(units.size - 1, floor(ln(bytes.toDouble()) / ln(1024.0)).toInt())
        val value = bytes / 1024.0.pow(exponent)
        val digits = if (value >= 10 || exponent == 0) 0 else 1
        return "%.${digits}f %s".format(value, units[exponent])
    }

    fun count(value: Int?, singular: String, plural: String = singular + "s"): String {
        val n = value ?: 0
        // "%@ tracks" is looked up as a whole, so a language can put its own word around the number.
        return t("%@ " + if (n == 1) singular else plural, NumberFormat.getIntegerInstance().format(n))
    }

    fun relative(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null || timestamp <= 0) return t("never")
        val minutes = ((now - timestamp) / 60_000.0).roundToLong()
        if (minutes < 1) return t("just now")
        if (minutes < 60) return t("%@ min ago", minutes)
        val hours = (minutes / 60.0).roundToLong()
        if (hours < 24) return t("%@ hr ago", hours)
        val days = (hours / 24.0).roundToLong()
        if (days < 30) return t(if (days == 1L) "%@ day ago" else "%@ days ago", days)
        return java.text.DateFormat.getDateInstance().format(java.util.Date(timestamp))
    }

    private val ARTICLES = Regex("^(the|a|an|der|die|das|le|la|les|el|los)\\s+", RegexOption.IGNORE_CASE)

    /** "The Beatles" sorts under B, like every other music app. */
    fun sortKey(name: String?): String = name?.replace(ARTICLES, "")?.lowercase() ?: ""

    fun initials(name: String?): String {
        if (name.isNullOrBlank()) return "?"
        return name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { "?" }
    }

    fun greeting(hour: Int): String = when {
        hour < 5 -> t("Still up?")
        hour < 12 -> t("Good morning")
        hour < 18 -> t("Good afternoon")
        else -> t("Good evening")
    }
}
