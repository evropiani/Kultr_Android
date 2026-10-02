package app.kultr.core.util

/**
 * Release versions like "1.4.0" or a tag like "v1.4.0", compared number by
 * number ("1.10.0" is newer than "1.9.2"). Anything after a dash ("1.5.0-beta")
 * makes it come just before the plain version.
 */
object Versions {
    private class Parsed(val numbers: List<Int>, val preRelease: Boolean)

    private fun parse(version: String): Parsed? {
        val text = version.trim().removePrefix("v").removePrefix("V")
        val core = text.substringBefore('-').substringBefore('+')
        val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
        if (numbers.isEmpty()) return null
        return Parsed(numbers, text.contains('-'))
    }

    /** Negative if [a] is older than [b], positive if newer, 0 if the same (or either is not a version). */
    fun compare(a: String, b: String): Int {
        val x = parse(a) ?: return 0
        val y = parse(b) ?: return 0
        for (i in 0 until maxOf(x.numbers.size, y.numbers.size)) {
            val difference = x.numbers.getOrElse(i) { 0 } - y.numbers.getOrElse(i) { 0 }
            if (difference != 0) return difference
        }
        return when {
            x.preRelease == y.preRelease -> 0
            x.preRelease -> -1
            else -> 1
        }
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /** "v1.4.0" → "1.4.0". */
    fun fromTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")
}
