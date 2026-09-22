package app.kultr.core.settings

import app.kultr.core.api.AuthMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Settings backup — moving your setup to another phone, or between Kultr on
 * the web and Kultr on Android. The file format is the web client's.
 *
 * Deliberately *not* included: usernames, passwords or anything else that could
 * sign someone in. A settings file is the kind of thing people paste into an
 * issue or drop in a shared folder without thinking twice, so credentials are
 * kept out of it by construction.
 */
object SettingsFile {
    const val KIND = "kultr.settings"
    const val VERSION = 1

    /** Keys that are about *this* device, so restoring them elsewhere is wrong. */
    private val DEVICE_LOCAL_KEYS = setOf("hasSeenWelcome", "streamCacheMb", "offlineWifiOnly")

    /** Lists whose length is fixed by the app rather than chosen by the person. */
    private val FIXED_LENGTH_KEYS = setOf("eqGains")

    /** Never let anything credential-shaped through, whatever the file claims. */
    private val FORBIDDEN = Regex("pass|secret|token|credential|server|username|auth", RegexOption.IGNORE_CASE)

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    data class ExportedServer(val label: String, val serverUrl: String, val authMode: AuthMode)

    data class ImportResult(
        val settings: Settings,
        val applied: List<String>,
        /** Keys in the file that were rejected, with the reason. */
        val skipped: List<Pair<String, String>>,
        /** Servers found in the file, scrubbed of anything credential-shaped. */
        val servers: List<ExportedServer>,
    )

    class InvalidFileException(message: String) : Exception(message)

    fun export(
        settings: Settings,
        appVersion: String,
        exportedAt: String,
        servers: List<ExportedServer>? = null,
    ): String {
        val all = json.encodeToJsonElement(settings).jsonObject
        val exported = JsonObject(all.filterKeys { it !in DEVICE_LOCAL_KEYS && !FORBIDDEN.containsMatchIn(it) })
        val root = buildJsonObject {
            put("kind", KIND)
            put("version", VERSION)
            put("exportedAt", exportedAt)
            put("app", "Kultr Android $appVersion")
            put("settings", exported)
            if (servers != null) {
                // Rebuilt field by field, so a profile growing a new property
                // later cannot quietly start appearing in exports.
                put(
                    "servers",
                    buildJsonArray {
                        servers.forEach { server ->
                            add(
                                buildJsonObject {
                                    put("label", server.label)
                                    put("serverUrl", server.serverUrl)
                                    put("authMode", if (server.authMode == AuthMode.PLAIN) "plain" else "token")
                                },
                            )
                        }
                    },
                )
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Validate a settings file against [current]. Every key has to exist in
     * Kultr and carry the same *type* as ours, so a hand-edited or hostile
     * file cannot inject anything: unknown keys, type mismatches, values we do
     * not understand and anything credential-shaped are dropped, not trusted.
     */
    fun import(text: String, current: Settings): ImportResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: throw InvalidFileException("That file is not a Kultr settings export.")

        if ((root["kind"] as? JsonPrimitive)?.content != KIND) {
            throw InvalidFileException("That file is not a Kultr settings export.")
        }
        val incoming = root["settings"] as? JsonObject
            ?: throw InvalidFileException("The file has no settings in it.")

        val defaults = json.encodeToJsonElement(Settings()).jsonObject
        var working = json.encodeToJsonElement(current).jsonObject
        var result = current
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, String>>()

        for ((key, value) in incoming) {
            val expected = defaults[key]
            when {
                expected == null -> skipped += key to "not a setting Kultr for Android has"
                FORBIDDEN.containsMatchIn(key) -> skipped += key to "credentials are never imported"
                key in DEVICE_LOCAL_KEYS -> skipped += key to "specific to the device it was exported from"
                !sameShape(expected, value) -> skipped += key to "wrong type"
                else -> {
                    val candidate = if (key in FIXED_LENGTH_KEYS && value is JsonArray && expected is JsonArray) {
                        JsonArray(value.take(expected.size))
                    } else {
                        value
                    }
                    val next = JsonObject(working + (key to candidate))
                    val decoded = runCatching { json.decodeFromJsonElement<Settings>(next) }.getOrNull()
                    if (decoded == null) {
                        skipped += key to "value not understood"
                    } else {
                        working = next
                        result = decoded
                        applied += key
                    }
                }
            }
        }

        val servers = readServers(root["servers"])
        if (applied.isEmpty() && servers.isEmpty()) {
            throw InvalidFileException("Nothing in that file could be applied.")
        }
        return ImportResult(result, applied, skipped, servers)
    }

    private fun kindOf(element: JsonElement): String = when (element) {
        is JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            element.doubleOrNull != null -> "number"
            else -> "string"
        }
    }

    private fun sameShape(expected: JsonElement, value: JsonElement): Boolean {
        val kind = kindOf(expected)
        if (kind != kindOf(value)) return false
        if (kind == "number" && value is JsonPrimitive) {
            val number = value.doubleOrNull ?: return false
            if (!number.isFinite()) return false
        }
        if (expected is JsonArray && value is JsonArray) {
            // An empty default says nothing about its element type, so those are
            // taken to be lists of ids — which is what all of them currently are.
            val elementKind = expected.firstOrNull()?.let(::kindOf) ?: "string"
            return value.all { kindOf(it) == elementKind }
        }
        return true
    }

    private fun readServers(input: JsonElement?): List<ExportedServer> {
        val list = input as? JsonArray ?: return emptyList()
        return list.mapNotNull { entry ->
            val record = entry as? JsonObject ?: return@mapNotNull null
            val url = (record["serverUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val label = (record["label"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            val mode = if ((record["authMode"] as? JsonPrimitive)?.content == "plain") AuthMode.PLAIN else AuthMode.TOKEN
            ExportedServer(label, url, mode)
        }
    }
}
