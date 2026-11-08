package app.kultr.android.data

import android.content.Context
import app.kultr.core.util.Words
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Kultr in the phone's language (or the one chosen for Kultr in Android's
 * settings): German from assets/l10n/de.json, English otherwise. The file is
 * the one Kultr for iOS translates from, keyed by the English: a value is the
 * German, or {"one": …, "other": …} where it changes with a number.
 */
object Translations {
    /** The languages Kultr speaks besides English. */
    val LANGUAGES = setOf("de")

    private val SPECIFIER = Regex("%(\\d+\\$)?(lld|llu|ld|lu|lf|d|u|f)")

    private class Entry(val one: String?, val other: String)

    fun install(context: Context) {
        val app = context.applicationContext
        val tables = HashMap<String, Map<String, Entry>>()
        Words.lookup = lookup@{ key, count ->
            val language = Locale.getDefault().language.takeIf { it in LANGUAGES } ?: return@lookup null
            val table = synchronized(tables) { tables.getOrPut(language) { load(app, language) } }
            val entry = table[key] ?: return@lookup null
            if (count == 1L && entry.one != null) entry.one else entry.other
        }
    }

    /** The table for [language]; Swift's %lld and %lf read as %@, the one placeholder Kultr for Android writes. */
    private fun load(context: Context, language: String): Map<String, Entry> {
        val text = runCatching { context.assets.open("l10n/$language.json").use { it.readBytes().decodeToString() } }.getOrNull() ?: return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return emptyMap()
        fun normal(s: String) = s.replace(SPECIFIER) { "%${it.groupValues[1]}@" }
        return root.entries.mapNotNull { (key, value) ->
            if (key.startsWith("//")) return@mapNotNull null
            val entry = when (value) {
                is JsonPrimitive -> Entry(null, normal(value.content))
                is JsonObject -> {
                    val other = (value["other"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    Entry((value["one"] as? JsonPrimitive)?.content?.let(::normal), normal(other))
                }
                else -> return@mapNotNull null
            }
            normal(key) to entry
        }.toMap()
    }

    /** The language Kultr is showing, in that language: "Deutsch", "English". */
    fun currentName(): String {
        val locale = Locale.getDefault().takeIf { it.language in LANGUAGES } ?: Locale.ENGLISH
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
