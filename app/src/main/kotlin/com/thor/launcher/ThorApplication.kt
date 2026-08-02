package com.thor.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.log.CrashReporter
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.data.sync.PlaytimeTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

    @Inject lateinit var playtimeTracker: PlaytimeTracker

    /**
     * Ends the open play session when the device sleeps.
     *
     * A session is measured against the wall clock, from the launch to the next
     * time the launcher is in front — which is the only bracket an app that
     * cannot observe another app's process has available. On a handheld that
     * bracket is wrong in one very ordinary case: play is not ended by exiting
     * the game, it is ended by pressing the power button with the game still
     * open. The elapsed span then covers the whole time the device sat in a bag,
     * and `PlaytimeTracker` discards anything past twelve hours as implausible —
     * correctly, because it is. The result is that an evening's play is credited
     * as nothing at all, every time, which is a play-time total that never
     * leaves zero however much the device is used.
     *
     * Screen-off is the missing signal, and it is one an unprivileged app can
     * genuinely have: the session closes when the device sleeps, so what is
     * credited is time the screen was actually on.
     *
     * Registered on the application rather than the activity on purpose. The
     * launcher is paused for the entire life of the session being measured — the
     * game is covering it — so a receiver tied to the activity's lifecycle would
     * be unregistered at precisely the moment it is needed.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            applicationScope.launch { playtimeTracker.settle() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Installed first, before anything else can throw, so a crash during
        // startup is still captured.
        CrashReporter.install(filesDir)

        // `ACTION_SCREEN_OFF` is a protected system broadcast and cannot be
        // declared in the manifest, so it is registered here for the life of the
        // process. Marked not-exported because nothing outside the system has any
        // business sending it.
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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
