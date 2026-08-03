package com.thor.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.datastore.PlaybackStateSerializer
import com.thor.core.datastore.PlaybackStore
import com.thor.core.datastore.ProfileRegistrySerializer
import com.thor.core.datastore.ProfileSettingsStore
import com.thor.core.datastore.ProfileStore
import com.thor.core.datastore.SettingsSerializer
import com.thor.core.model.PlaybackState
import com.thor.core.model.ProfileRegistry
import com.thor.core.model.ThorSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val PLAYBACK_FILE = "thor-playback.json"
    private const val PROFILES_FILE = "thor-profiles.json"

    /**
     * Settings, resolved through whoever is signed in.
     *
     * The file lives inside the active profile's directory, so this is bound to
     * [ProfileSettingsStore] rather than to a fixed path. Everything that
     * injects `DataStore<ThorSettings>` keeps working unchanged and follows the
     * profile automatically.
     */
    @Provides
    @Singleton
    fun providesSettingsDataStore(
        store: ProfileSettingsStore,
    ): DataStore<ThorSettings> = store

    /**
     * The profile registry, at a fixed path.
     *
     * This is the one document that cannot be per-profile: it is what says which
     * profile to load.
     */
    @Provides
    @Singleton
    @ProfileStore
    fun providesProfileDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(ThorDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        serializer: ProfileRegistrySerializer,
    ): DataStore<ProfileRegistry> = DataStoreFactory.create(
        serializer = serializer,
        // Losing the registry must not brick the launcher — it re-seeds a
        // default profile, which then adopts whatever data is on disk.
        corruptionHandler = ReplaceFileCorruptionHandler { ProfileRegistry.EMPTY },
        scope = scope + ioDispatcher,
        produceFile = { context.dataStoreFile(PROFILES_FILE) },
    )

    /**
     * The open play session.
     *
     * A separate file so a settings write cannot lose an in-flight session and a
     * session write cannot churn the much larger settings document — this one is
     * written on every launch and every return.
     */
    @Provides
    @Singleton
    @PlaybackStore
    fun providesPlaybackDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(ThorDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        serializer: PlaybackStateSerializer,
    ): DataStore<PlaybackState> = DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler = ReplaceFileCorruptionHandler { PlaybackState.EMPTY },
        scope = scope + ioDispatcher,
        produceFile = { context.dataStoreFile(PLAYBACK_FILE) },
    )

    private fun Context.dataStoreFile(name: String) =
        java.io.File(applicationContext.filesDir, "datastore/$name")
}
