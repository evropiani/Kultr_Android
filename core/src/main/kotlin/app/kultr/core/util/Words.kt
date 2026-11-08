package app.kultr.core.util

/**
 * Kultr's own text in the phone's language. The code writes English; the app
 * sets [lookup] once at start to its translations (tests keep English). In a
 * line with values, %@ marks each one, or %1$@, %2$@ when a language puts
 * them in another order. The same keys as Kultr for iOS.
 */
object Words {
    /** The translation of [key], picking a plural form by [count] where it has one; null keeps the English. */
    @Volatile
    var lookup: (key: String, count: Long?) -> String? = { _, _ -> null }

    fun t(key: String, vararg values: Any?): String {
        val texts = values.map { it?.toString().orEmpty() }
        val count = texts.firstNotNullOfOrNull { it.filter { c -> c != ',' && c != '.' && c != ' ' && c != ' ' }.toLongOrNull() }
        var text = lookup(key, count) ?: key
        texts.forEachIndexed { index, value ->
            val numbered = "%${index + 1}\$@"
            text = if (numbered in text) text.replace(numbered, value) else text.replaceFirst("%@", value)
        }
        return text
    }
}

/** [key] in the phone's language, with [values] in its %@ places. */
fun t(key: String, vararg values: Any?): String = Words.t(key, *values)
