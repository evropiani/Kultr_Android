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
            hours > 0 -> "$hours hr $minutes min"
            minutes > 0 -> "$minutes min"
            else -> "$seconds sec"
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
        return "${NumberFormat.getIntegerInstance().format(n)} ${if (n == 1) singular else plural}"
    }

    fun relative(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null || timestamp <= 0) return "never"
        val minutes = ((now - timestamp) / 60_000.0).roundToLong()
        if (minutes < 1) return "just now"
        if (minutes < 60) return "$minutes min ago"
        val hours = (minutes / 60.0).roundToLong()
        if (hours < 24) return "$hours hr ago"
        val days = (hours / 24.0).roundToLong()
        if (days < 30) return "$days day${if (days == 1L) "" else "s"} ago"
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
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}
