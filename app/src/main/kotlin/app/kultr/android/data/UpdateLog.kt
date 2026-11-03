package app.kultr.android.data

import android.content.Context
import androidx.core.content.edit
import app.kultr.android.BuildConfig
import app.kultr.core.util.Versions

/** One version's notes, as written for its release page. */
data class VersionNotes(val version: String, val markdown: String)

/**
 * What changed in every version, from the release notes bundled with the app
 * (the same ones the release page shows), and which of them this person has
 * not seen yet.
 */
class UpdateLog(private val context: Context) {
    private val prefs = context.getSharedPreferences("kultr.ui", Context.MODE_PRIVATE)

    /** Every version up to this one, newest first. */
    val all: List<VersionNotes> by lazy {
        val names = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()
        names.mapNotNull { name ->
            val version = name.removeSuffix(".md").removePrefix("v")
            if (Versions.compare(version, BuildConfig.VERSION_NAME) > 0) return@mapNotNull null
            runCatching { context.assets.open("$DIR/$name").use { it.readBytes().decodeToString() } }.getOrNull()?.let { VersionNotes(version, it) }
        }.sortedWith { a, b -> Versions.compare(b.version, a.version) }
    }

    /**
     * The versions to tell about on this start: every one since the version
     * last opened. A fresh install has nothing to catch up on; someone coming
     * from before Kultr kept track ([upgraded]) hears about this version.
     */
    fun unseen(upgraded: Boolean): List<VersionNotes> {
        val last = prefs.getString(KEY, null)
        return when {
            last == null && !upgraded -> emptyList()
            last == null -> all.take(1)
            else -> all.filter { Versions.compare(it.version, last) > 0 }
        }.also { if (last != BuildConfig.VERSION_NAME) prefs.edit { putString(KEY, BuildConfig.VERSION_NAME) } }
    }

    private companion object {
        const val DIR = "release-notes"
        const val KEY = "whatsNew.lastSeen"
    }
}
