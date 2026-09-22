package app.kultr.core.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The colour the interface takes from whatever is playing.
 *
 * Works on packed ARGB pixels (Android's `Bitmap.getPixels` layout) from a
 * small downscaled copy of the artwork: bucket colours coarsely and pick the
 * most common one that is neither near-black, near-white nor fully
 * desaturated, then lift it into a range that reads as an accent.
 */
object ArtworkColor {
    fun dominant(pixels: IntArray): Int? {
        val counts = HashMap<Int, IntArray>()
        for (argb in pixels) {
            val alpha = argb ushr 24 and 0xff
            if (alpha < 200) continue
            val r = argb shr 16 and 0xff
            val g = argb shr 8 and 0xff
            val b = argb and 0xff
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
            if (luma < 28 || luma > 235) continue
            val saturation = if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
            if (saturation < 0.12) continue
            val key = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
            val bucket = counts.getOrPut(key) { IntArray(4) }
            bucket[0]++
            bucket[1] += r
            bucket[2] += g
            bucket[3] += b
        }
        val best = counts.values.maxByOrNull { it[0] } ?: return null
        val n = best[0]
        return lift(best[1] / n, best[2] / n, best[3] / n)
    }

    /** Nudge a sampled colour into a range that still reads as an accent. */
    fun lift(r: Int, g: Int, b: Int): Int {
        val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
        var scale = 1.0
        if (luma < 90) scale = 90 / max(luma, 1.0)
        if (luma > 200) scale = 200 / luma
        return rgb(
            min(255, (r * scale).roundToInt()),
            min(255, (g * scale).roundToInt()),
            min(255, (b * scale).roundToInt()),
        )
    }

    fun rgb(r: Int, g: Int, b: Int): Int = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    /** "#7c8cff" → ARGB, or null. */
    fun parseHex(hex: String): Int? {
        val match = Regex("^#?([0-9a-fA-F]{6})$").matchEntire(hex.trim()) ?: return null
        return (0xff shl 24) or match.groupValues[1].toInt(16)
    }

    fun toHex(argb: Int): String = "#%06x".format(argb and 0xffffff)

    /** Mix [b] into [a] by [amount] (0..1). */
    fun mix(a: Int, b: Int, amount: Double): Int {
        val t = amount.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = (((a shr shift) and 0xff) * (1 - t) + ((b shr shift) and 0xff) * t).roundToInt()
        return rgb(ch(16), ch(8), ch(0))
    }
}
