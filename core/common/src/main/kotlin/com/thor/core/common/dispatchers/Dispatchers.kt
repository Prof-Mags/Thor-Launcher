package com.thor.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for dispatchers, so that every call site declares which thread pool
 * it expects and tests can substitute a deterministic scheduler.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: ThorDispatcher)

enum class ThorDispatcher {
    /** Disk, database and network work. */
    IO,

    /** CPU-bound work: layout solving, image post-processing, sorting. */
    Default,

    /** UI thread. */
    Main,
}

/**
 * An application-lifetime coroutine scope.
 *
 * Used for work that must outlive any one screen — library scans, artwork
 * downloads, play-time accounting — without leaking a view model.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(ThorDispatcher.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(ThorDispatcher.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Dispatcher(ThorDispatcher.Main)
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(
        @Dispatcher(ThorDispatcher.Default) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
