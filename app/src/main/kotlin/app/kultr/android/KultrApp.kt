package app.kultr.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.media3.cast.Cast
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import app.kultr.android.data.OfflineManager

class KultrApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        graphInstance = AppGraph(this)
        createChannels()
        // Cast needs Google Play services; without them the cast button simply never appears.
        runCatching { Cast.getSingletonInstance(this).initialize() }
        graph.sync.onAppStart()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(OfflineManager.CHANNEL, getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("artwork"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        private lateinit var instance: KultrApp
        private lateinit var graphInstance: AppGraph

        val graph: AppGraph get() = graphInstance
    }
}
