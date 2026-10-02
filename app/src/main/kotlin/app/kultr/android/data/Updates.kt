package app.kultr.android.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.kultr.android.AppGraph
import app.kultr.android.BuildConfig
import app.kultr.core.api.hexEncode
import app.kultr.core.util.Versions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A published release of Kultr for Android. */
@Serializable
data class AppRelease(
    val version: String,
    val title: String,
    /** The release notes, in Markdown. */
    val notes: String,
    val publishedAt: String? = null,
    val pageUrl: String,
    val apkUrl: String? = null,
    val sha256Url: String? = null,
)

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data class UpToDate(val latest: String) : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Downloading(val fraction: Float?) : UpdateDownload
    data object Installing : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}

/**
 * Finds out whether a newer Kultr has been released, and installs it.
 *
 * Releases are the GitHub releases of this repository. Kultr looks at most
 * twice a day by itself (and whenever asked in Settings → About); a newer one
 * shows as a banner until it is dismissed. Installing downloads the release's
 * APK, checks it against the checksum published with it, and hands it to
 * Android's installer, which only accepts it if it is signed with the same
 * key as the app already installed.
 */
class Updates(private val graph: AppGraph) {
    private val prefs = graph.app.getSharedPreferences("kultr.updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    private val _available = MutableStateFlow(loadAvailable())

    /** The newest release, if it is newer than this app. Kept between launches. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))

    /** The version whose banner was dismissed. */
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    private fun loadAvailable(): AppRelease? {
        val release = prefs.getString(KEY_AVAILABLE, null)
            ?.let { runCatching { json.decodeFromString(AppRelease.serializer(), it) }.getOrNull() }
            ?: return null
        // Installed since: it is not news any more.
        return release.takeIf { Versions.isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    /** Look each time Kultr comes to the front, at most twice a day. */
    fun onAppStart() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = checkInBackground()
            },
        )
    }

    /** Look by itself, if it has not looked in the last twelve hours. */
    fun checkInBackground() {
        val last = prefs.getLong(KEY_CHECKED_AT, 0)
        if (System.currentTimeMillis() - last < BACKGROUND_INTERVAL_MS) return
        graph.scope.launch { checkNow() }
    }

    /** Ask GitHub for the latest release now. */
    suspend fun checkNow(): UpdateCheck {
        _check.value = UpdateCheck.Checking
        val result = try {
            val latest = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit { putLong(KEY_CHECKED_AT, System.currentTimeMillis()) }
            if (Versions.isNewer(latest.version, BuildConfig.VERSION_NAME)) {
                _available.value = latest
                prefs.edit { putString(KEY_AVAILABLE, json.encodeToString(AppRelease.serializer(), latest)) }
                UpdateCheck.Available(latest)
            } else {
                _available.value = null
                prefs.edit { remove(KEY_AVAILABLE) }
                UpdateCheck.UpToDate(latest.version)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            UpdateCheck.Failed(err.message ?: "Could not reach GitHub.")
        }
        _check.value = result
        return result
    }

    fun dismiss(release: AppRelease) {
        _dismissed.value = release.version
        prefs.edit { putString(KEY_DISMISSED, release.version) }
    }

    private fun fetchLatest(): AppRelease {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        graph.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}.")
            val body = json.decodeFromString(GitHubRelease.serializer(), response.body.string())
            val apk = body.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val sha = body.assets.firstOrNull { it.name.endsWith(".apk.sha256", ignoreCase = true) }
            return AppRelease(
                version = Versions.fromTag(body.tagName),
                title = body.name?.takeIf { it.isNotBlank() } ?: "Kultr ${Versions.fromTag(body.tagName)}",
                notes = body.body.orEmpty(),
                publishedAt = body.publishedAt,
                pageUrl = body.htmlUrl,
                apkUrl = apk?.browserDownloadUrl,
                sha256Url = sha?.browserDownloadUrl,
            )
        }
    }

    /**
     * Download [release] and open Android's installer on it. Android first asks
     * the person to allow Kultr to install apps, once; then this is needed again.
     */
    fun install(release: AppRelease) {
        if (_download.value is UpdateDownload.Downloading) return
        val context = graph.app
        val url = release.apkUrl
        if (url == null) {
            openPage(release)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            graph.messages.show("Allow Kultr to install updates, then tap Update again.", long = true)
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }.onFailure { openPage(release) }
            return
        }
        graph.scope.launch {
            _download.value = UpdateDownload.Downloading(null)
            try {
                val file = withContext(Dispatchers.IO) { fetchApk(release, url) }
                _download.value = UpdateDownload.Installing
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                _download.value = UpdateDownload.Idle
            } catch (err: CancellationException) {
                throw err
            } catch (err: ActivityNotFoundException) {
                _download.value = UpdateDownload.Idle
                openPage(release)
            } catch (err: Exception) {
                _download.value = UpdateDownload.Failed(err.message ?: "The download failed.")
            }
        }
    }

    fun openPage(release: AppRelease) {
        runCatching {
            graph.app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun fetchApk(release: AppRelease, url: String): File {
        val directory = File(graph.app.cacheDir, "updates").apply { mkdirs() }
        // Only the newest download is worth keeping.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "Kultr-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        graph.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The download failed (${response.code}).")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            var done = 0L
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        _download.value = UpdateDownload.Downloading(total?.let { done.toFloat() / it })
                    }
                }
            }
        }
        release.sha256Url?.let { shaUrl ->
            val expected = runCatching {
                graph.http.newCall(Request.Builder().url(shaUrl).build()).execute().use { it.body.string() }
            }.getOrNull()?.trim()?.substringBefore(' ')?.lowercase()
            if (expected != null && expected.length == 64 && expected != hexEncode(digest.digest())) {
                file.delete()
                throw IOException("The download did not match its checksum. Try again.")
            }
        }
        return file
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/evropiani/Kultr_Android/releases/latest"
        const val APK_TYPE = "application/vnd.android.package-archive"
        const val BACKGROUND_INTERVAL_MS = 12 * 60 * 60 * 1000L
        const val KEY_CHECKED_AT = "checkedAt"
        const val KEY_AVAILABLE = "available"
        const val KEY_DISMISSED = "dismissed"
    }
}
