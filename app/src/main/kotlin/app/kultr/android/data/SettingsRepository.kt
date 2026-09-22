package app.kultr.android.data

import android.content.Context
import app.kultr.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Every preference, as one JSON document in SharedPreferences. Reads are
 * synchronous (the player needs them on its own thread without suspending),
 * and every change is published through [settings].
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("kultr.settings", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val state = MutableStateFlow(load())
    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    private fun load(): Settings {
        val text = prefs.getString(KEY, null) ?: return Settings()
        return runCatching { json.decodeFromString(Settings.serializer(), text) }.getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        state.update { old ->
            val next = transform(old)
            if (next != old) prefs.edit().putString(KEY, json.encodeToString(Settings.serializer(), next)).apply()
            next
        }
    }

    fun replace(settings: Settings) = update { settings }

    fun reset() = update { Settings(hasSeenWelcome = it.hasSeenWelcome) }

    private companion object {
        const val KEY = "settings.json"
    }
}
