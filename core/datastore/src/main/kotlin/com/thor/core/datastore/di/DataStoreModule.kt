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
import com.thor.core.datastore.SettingsSerializer
import com.thor.core.model.PlaybackState
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

    private const val SETTINGS_FILE = "thor-settings.json"
    private const val PLAYBACK_FILE = "thor-playback.json"

    @Provides
    @Singleton
    fun providesSettingsDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(ThorDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        serializer: SettingsSerializer,
    ): DataStore<ThorSettings> = DataStoreFactory.create(
        serializer = serializer,
        // On corruption, fall back to defaults instead of throwing on every read.
        corruptionHandler = ReplaceFileCorruptionHandler { ThorSettings.DEFAULT },
        scope = scope + ioDispatcher,
        produceFile = { context.dataStoreFile(SETTINGS_FILE) },
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
