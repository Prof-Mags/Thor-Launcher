package com.thor.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.log.CrashReporter
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Also owns the Coil image loader, because artwork caching limits are a user
 * setting: the loader is built once from the persisted values rather than
 * being reconstructed per screen.
 */
@HiltAndroidApp
class ThorApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var okHttpClient: OkHttpClient

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        // Installed first, before anything else can throw, so a crash during
        // startup is still captured.
        CrashReporter.install(filesDir)

        // Verbose logging follows the developer setting, so a shipped build is
        // quiet until the user explicitly asks for diagnostics.
        settingsRepository.developer
            .onEach { ThorLog.enabled = it.verboseLogging }
            .launchIn(applicationScope)

        ThorLog.i("App", "THOR launcher started")
    }

    /**
     * Coil loader shared by every screen.
     *
     * Cache sizes are fixed rather than configurable. They used to come from
     * settings, which meant blocking the main thread on a DataStore read during
     * application startup to build the loader — a real cost for a value nobody
     * needs to tune.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(MEMORY_CACHE_MB * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_MB.toLong() * 1024 * 1024)
                    .build()
            }
            // Off globally: the launcher renders into two windows on two
            // displays, and a hardware bitmap is only valid in the context that
            // uploaded it. The memory saving is not worth artwork that silently
            // fails to draw on the second panel.
            .allowHardware(false)
            .crossfade(true)
            .build()

    private companion object {
        const val MEMORY_CACHE_MB = 96
        const val DISK_CACHE_MB = 512
    }
}
