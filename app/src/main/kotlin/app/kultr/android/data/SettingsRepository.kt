package app.kultr.android.data

import android.content.Context
import androidx.core.content.edit
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

    init {
        moveToCream()
        addShelf("rediscover", after = "mostPlayedAlbums")
        addShelf("stations", after = null)
    }

    /** A new home shelf, once, so it shows up without a trip to Settings; after [after], or first. Removing it afterwards sticks. */
    private fun addShelf(id: String, after: String?) {
        val key = "home.added.$id"
        if (prefs.getBoolean(key, false)) return
        prefs.edit { putBoolean(key, true) }
        update { s ->
            if (id in s.homeTiles) return@update s
            val at = if (after == null) 0 else s.homeTiles.indexOf(after).let { if (it < 0) s.homeTiles.size else it + 1 }
            s.copy(homeTiles = s.homeTiles.toMutableList().apply { add(at, id) })
        }
    }

    /**
     * 2.0's look, once: the old defaults (periwinkle taken from the artwork,
     * blurred covers behind every page, accent-coloured borders) become the
     * new ones. A colour someone picked themselves is kept, and all of it can
     * be switched back in Settings → Appearance.
     */
    private fun moveToCream() {
        if (prefs.getBoolean(KEY_CREAM, false)) return
        prefs.edit { putBoolean(KEY_CREAM, true) }
        if (!prefs.contains(KEY)) return
        val fresh = Settings()
        update {
            it.copy(
                accent = if (it.accent.equals("#7c8cff", ignoreCase = true)) fresh.accent else it.accent,
                accentMode = fresh.accentMode,
                backdropArtwork = fresh.backdropArtwork,
                surfaceBorder = fresh.surfaceBorder,
            )
        }
    }

    private fun load(): Settings {
        val text = prefs.getString(KEY, null) ?: return Settings()
        return runCatching { json.decodeFromString(Settings.serializer(), text) }.getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        state.update { old ->
            val next = transform(old)
            if (next != old) prefs.edit { putString(KEY, json.encodeToString(Settings.serializer(), next)) }
            next
        }
    }

    fun replace(settings: Settings) = update { settings }

    fun reset() = update { Settings(hasSeenWelcome = it.hasSeenWelcome) }

    private companion object {
        const val KEY = "settings.json"
        const val KEY_CREAM = "look.cream"
    }
}
