package com.thor.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.common.profile.ProfileMigrator
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.model.ThorSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the profile registry's store from the per-profile ones. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileStore

/**
 * Settings for whoever is signed in, behind the interface everything already
 * injects.
 *
 * `DataStore` has exactly two members, which is what makes this practical: the
 * whole launcher keeps injecting `DataStore<ThorSettings>` and never learns that
 * profiles exist, while reads follow the active profile and writes land in that
 * profile's file. Doing it the other way — teaching every repository and view
 * model to ask which profile is active — would have put the same lookup in
 * thirty places and made forgetting it a silent bug that writes one person's
 * preference into another's file.
 *
 * Stores are cached per profile rather than rebuilt on each switch. DataStore
 * does not permit two instances over one file, and switching back and forth
 * between two profiles would otherwise do exactly that.
 */
@Singleton
class ProfileSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serializer: SettingsSerializer,
    private val profiles: ProfileRegistryRepository,
    private val migrator: ProfileMigrator,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : DataStore<ThorSettings> {

    private val stores = mutableMapOf<String, DataStore<ThorSettings>>()

    private val active: StateFlow<DataStore<ThorSettings>?> = profiles.activeProfileId
        .filter(String::isNotEmpty)
        .distinctUntilChanged()
        .map(::storeFor)
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val data: Flow<ThorSettings> = active
        .filterNotNull()
        .flatMapLatest { it.data }

    override suspend fun updateData(
        transform: suspend (t: ThorSettings) -> ThorSettings,
    ): ThorSettings = awaitStore().updateData(transform)

    /**
     * The store for the active profile, waiting for one to exist if needed.
     *
     * A write can be issued before the registry's first emission — a settings
     * screen restored on cold start, say. Seeding rather than failing means that
     * write lands in the profile that boot was going to pick anyway.
     */
    private suspend fun awaitStore(): DataStore<ThorSettings> {
        active.value?.let { return it }
        val id = profiles.ensureSeeded().active?.id
            ?: error("Profile registry seeded without a profile")
        return storeFor(id)
    }

    @Synchronized
    private fun storeFor(profileId: String): DataStore<ThorSettings> =
        stores.getOrPut(profileId) {
            // Before the file is opened, not after: DataStore reads on first
            // collect, and a legacy settings file moved in afterwards would be
            // ignored until the process restarted — by which time defaults have
            // been written over the top of it.
            migrator.adoptLegacyData(profileId)
            DataStoreFactory.create(
                serializer = serializer,
                corruptionHandler = ReplaceFileCorruptionHandler { ThorSettings.DEFAULT },
                scope = scope + ioDispatcher,
                produceFile = { ProfileFiles.settings(context, profileId) },
            )
        }

    /** Blocks until a profile exists. Used by the migration on first run. */
    suspend fun awaitReady(): ThorSettings = data.first()
}
