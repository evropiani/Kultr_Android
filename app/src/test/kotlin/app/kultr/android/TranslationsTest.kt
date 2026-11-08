package app.kultr.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every English line the code hands to t(), and every word Format.count
 * counts, has its German in assets/l10n/de.json, with the same number of
 * %@ in it. A new line without German fails here, with the line.
 */
class TranslationsTest {
    private val specifier = Regex("%(\\d+\\$)?(lld|llu|ld|lu|lf|d|u|f)")
    private fun normal(text: String) = text.replace(specifier) { "%${it.groupValues[1]}@" }
    private fun placeholders(text: String) = Regex("%(\\d+\\$)?@").findAll(normal(text)).count()

    private val german: Map<String, List<String>> by lazy {
        val root = Json.parseToJsonElement(File("src/main/assets/l10n/de.json").readText()) as JsonObject
        root.entries.filterNot { it.key.startsWith("//") }.associate { (key, value) ->
            normal(key) to when (value) {
                is JsonObject -> value.values.map { it.toString().trim('"') }
                else -> listOf(value.toString().trim('"'))
            }
        }
    }

    private val literal = "\"((?:[^\"\\\\$]|\\\\.)*)\""
    private val call = Regex("(?<![\\w.])t\\(\\s*$literal")
    private val pick = Regex("(?<![\\w.])t\\(if \\([^)]*\\) $literal else $literal")
    private val counted = Regex("Format\\.count\\([^,]+,\\s*$literal(?:,\\s*$literal)?\\)")

    private fun keys(): Set<String> {
        val sources = listOf(File("src/main/kotlin"), File("../core/src/main/kotlin"))
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
        val keys = HashSet<String>()
        for (file in sources) {
            val text = file.readText()
            call.findAll(text).forEach { keys += it.groupValues[1] }
            pick.findAll(text).forEach { keys += it.groupValues[1]; keys += it.groupValues[2] }
            counted.findAll(text).forEach {
                val singular = it.groupValues[1]
                keys += "%@ $singular"
                keys += "%@ " + it.groupValues[2].ifEmpty { singular + "s" }
            }
        }
        // Pieces joined in code ("%@ " + word, "New " + noun) are not lines of their own.
        return keys.filterNot { it.endsWith(" ") }.map { it.replace("\\\"", "\"").replace("\\n", "\n") }.toSet()
    }

    @Test
    fun everyLineHasGerman() {
        val keys = keys()
        assertTrue(keys.size > 500, "Found only ${keys.size} lines; is the test reading the sources?")
        val missing = keys.filter { normal(it) !in german }.sorted()
        assertTrue(missing.isEmpty(), "No German in assets/l10n/de.json for:\n" + missing.joinToString("\n"))
    }

    @Test
    fun theGermanKeepsThePlaceholders() {
        val wrong = german.filter { (key, values) -> values.any { placeholders(it) != placeholders(key) } }.keys
        assertTrue(wrong.isEmpty(), "These need as many %@ in the German as in the English:\n" + wrong.joinToString("\n"))
    }
}
