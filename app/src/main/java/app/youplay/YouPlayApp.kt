package app.youplay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class YouPlayApp : Application(), ImageLoaderFactory {
    @javax.inject.Inject lateinit var caches: app.youplay.data.cache.CacheManager
    @javax.inject.Inject lateinit var settingsStore: app.youplay.data.local.SettingsDataStore
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onCreate() {
        // The crash screen runs in its own ":crash" process: keep that process free of the rest of the app.
        if (app.youplay.crash.CrashHandler.isCrashProcess()) return
        app.youplay.crash.CrashHandler.install(this)
        super.onCreate()
        caches.start()
        app.youplay.data.notify.NewVideoNotifier.ensureChannel(this)
        // Keep the background new-video check in step with the Settings toggle.
        appScope.launch {
            settingsStore.settings.map { it.notifyNewVideos }.distinctUntilChanged().collect {
                app.youplay.data.notify.NewVideoWorker.schedule(this@YouPlayApp, it)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .crossfade(true)
            .respectCacheHeaders(false) // cache thumbnails regardless of server cache headers
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
