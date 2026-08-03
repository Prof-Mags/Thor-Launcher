package com.thor.core.datastore.di

import com.thor.core.common.profile.ActiveProfileId
import com.thor.core.datastore.ProfileRegistryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * Publishes who is signed in, for anything that has to follow them.
 *
 * Exposed as a bare `Flow<String>` under a qualifier so that the library
 * database can track the active profile without depending on how profiles are
 * stored — the database needs an id, not a registry.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {

    @Provides
    @Singleton
    @ActiveProfileId
    fun providesActiveProfileId(
        profiles: ProfileRegistryRepository,
    ): Flow<String> = profiles.activeProfileId
}
