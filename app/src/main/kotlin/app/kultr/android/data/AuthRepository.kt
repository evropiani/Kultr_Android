package app.kultr.android.data

import android.content.Context
import app.kultr.core.api.AuthMode
import app.kultr.core.api.Credentials
import app.kultr.core.api.ServerInfo
import app.kultr.core.api.SubsonicClient
import app.kultr.core.api.describeError
import app.kultr.core.api.normalizeServerUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Serializable
data class ServerProfile(
    val id: String,
    val label: String,
    val serverUrl: String,
    val username: String,
    val authMode: AuthMode = AuthMode.TOKEN,
    /**
     * A server you keep but are not using right now. Disabled servers stay in
     * the list with their credentials intact; they just cannot be connected to.
     */
    val enabled: Boolean = true,
    /** The password, sealed with [SecretBox]. Empty for imported profiles. */
    val secret: String = "",
) {
    val hasCredentials: Boolean get() = username.isNotBlank() && secret.isNotEmpty()
}

sealed interface Connection {
    data object Idle : Connection
    data object Connecting : Connection
    data class Online(val info: ServerInfo) : Connection

    /** Signed in, but the server cannot be reached right now. Offline copies still play. */
    data class Offline(val message: String) : Connection
}

fun hostLabel(url: String): String = url.toHttpUrlOrNull()?.let { u ->
    if (u.port == 80 || u.port == 443) u.host else "${u.host}:${u.port}"
} ?: url

private fun profileId(url: String, username: String): String = "${url.trimEnd('/')}#$username".lowercase()

/**
 * Saved servers, which one is active, and the API client for it.
 */
class AuthRepository(
    context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("kultr.auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(ServerProfile.serializer())

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    private val _active = MutableStateFlow<ServerProfile?>(null)
    val active: StateFlow<ServerProfile?> = _active.asStateFlow()

    private val _client = MutableStateFlow<SubsonicClient?>(null)
    val client: StateFlow<SubsonicClient?> = _client.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.Idle)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private var pingJob: Job? = null

    init {
        val activeId = prefs.getString(KEY_ACTIVE, null)
        val profile = _profiles.value.firstOrNull { it.id == activeId }
        if (profile != null && profile.enabled) activate(profile, ping = true)
    }

    private fun loadProfiles(): List<ServerProfile> {
        val text = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, text) }.getOrDefault(emptyList())
    }

    private fun saveProfiles(list: List<ServerProfile>) {
        _profiles.value = list
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(listSerializer, list)).apply()
    }

    private fun saveActive(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    private fun buildClient(profile: ServerProfile): SubsonicClient? {
        val password = SecretBox.decrypt(profile.secret) ?: return null
        if (profile.username.isBlank()) return null
        return SubsonicClient(Credentials(profile.serverUrl, profile.username, password, profile.authMode), http)
    }

    /**
     * Make [profile] the active one. The client exists straight away, so the
     * library and downloads work even if the server does not answer.
     */
    private fun activate(profile: ServerProfile, ping: Boolean) {
        val client = buildClient(profile)
        _active.value = profile
        _client.value = client
        saveActive(profile.id)
        if (client == null) {
            _connection.value = Connection.Offline("Sign in again: the saved password could not be read.")
            return
        }
        if (ping) refreshConnection()
    }

    /** Ask the server whether it is there; updates [connection]. */
    fun refreshConnection() {
        val client = _client.value ?: return
        pingJob?.cancel()
        pingJob = scope.launch {
            _connection.value = Connection.Connecting
            _connection.value = try {
                Connection.Online(client.ping())
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                Connection.Offline(describeError(err))
            }
        }
    }

    data class LoginInput(
        val serverUrl: String,
        val username: String,
        val password: String,
        val label: String = "",
        val authMode: AuthMode = AuthMode.TOKEN,
    )

    /** Check the credentials against the server and, if they work, sign in. Returns an error message on failure. */
    suspend fun login(input: LoginInput): String? {
        val url = normalizeServerUrl(input.serverUrl)
        if (url.isEmpty()) return "Enter your server’s address."
        if (url.toHttpUrlOrNull() == null) return "“${input.serverUrl}” is not a valid address."
        if (input.username.isBlank()) return "Enter your username."
        val creds = Credentials(url, input.username.trim(), input.password, input.authMode)
        val client = SubsonicClient(creds, http)
        _connection.value = Connection.Connecting
        return try {
            val info = client.ping()
            val id = profileId(client.baseUrl, creds.username)
            val existing = _profiles.value.firstOrNull { it.id == id }
            val profile = ServerProfile(
                id = id,
                label = input.label.trim().ifEmpty { existing?.label ?: hostLabel(client.baseUrl) },
                serverUrl = client.baseUrl,
                username = creds.username,
                authMode = creds.authMode,
                enabled = true,
                secret = SecretBox.encrypt(creds.password),
            )
            // Replace the profile, and any credential-less placeholder imported for this address.
            saveProfiles(
                _profiles.value.filter { it.id != id && !(it.serverUrl == profile.serverUrl && !it.hasCredentials) } + profile,
            )
            _active.value = profile
            _client.value = client
            saveActive(id)
            _connection.value = Connection.Online(info)
            null
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            _connection.value = if (_client.value != null) Connection.Offline(describeError(err)) else Connection.Idle
            describeError(err)
        }
    }

    fun switchTo(id: String): Boolean {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return false
        if (!profile.enabled || !profile.hasCredentials) return false
        activate(profile, ping = true)
        return true
    }

    fun setEnabled(id: String, enabled: Boolean) {
        saveProfiles(_profiles.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
        if (!enabled && _active.value?.id == id) signOut()
    }

    fun rename(id: String, label: String) {
        saveProfiles(_profiles.value.map { if (it.id == id) it.copy(label = label.trim().ifEmpty { it.label }) else it })
        _active.value?.let { a -> if (a.id == id) _active.value = _profiles.value.first { it.id == id } }
    }

    /** Forget a server entirely. Returns the removed profile so its data can be wiped. */
    fun remove(id: String): ServerProfile? {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return null
        saveProfiles(_profiles.value.filter { it.id != id })
        if (_active.value?.id == id) signOut()
        return profile
    }

    /** Leave the current server; its profile and library stay for next time. */
    fun signOut() {
        pingJob?.cancel()
        _active.value = null
        _client.value = null
        _connection.value = Connection.Idle
        saveActive(null)
    }

    /** Servers from a settings file. They arrive without credentials. */
    fun importServers(servers: List<Triple<String, String, AuthMode>>): Int {
        var added = 0
        val list = _profiles.value.toMutableList()
        for ((label, rawUrl, mode) in servers) {
            val url = normalizeServerUrl(rawUrl)
            if (url.isEmpty() || list.any { it.serverUrl.equals(url, ignoreCase = true) }) continue
            list += ServerProfile(
                id = profileId(url, ""),
                label = label.ifBlank { hostLabel(url) },
                serverUrl = url,
                username = "",
                authMode = mode,
            )
            added++
        }
        if (added > 0) saveProfiles(list)
        return added
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE = "active"
    }
}
