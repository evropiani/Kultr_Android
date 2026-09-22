package app.kultr.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.kultr.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .addNetworkInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "Kultr-Android/${BuildConfig.VERSION_NAME}")
                .build(),
        )
    }
    .build()

data class NetworkState(val online: Boolean, val metered: Boolean)

/** Whether we are online, and whether the connection costs money. */
class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    val isMetered: Boolean get() = _state.value.metered
    val isOnline: Boolean get() = _state.value.online

    init {
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _state.value = read()
                }

                override fun onLost(network: Network) {
                    _state.value = read()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _state.value = read()
                }
            })
        }
    }

    private fun read(): NetworkState {
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = runCatching { manager.isActiveNetworkMetered }.getOrDefault(false)
        return NetworkState(online, metered)
    }
}
