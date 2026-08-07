package com.thor.core.database.di

import android.content.Context
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.profile.ActiveProfileId
import com.thor.core.common.profile.ProfileMigrator
import com.thor.core.database.ActiveDatabase
import com.thor.core.database.ThorDatabase
import com.thor.core.database.dao.AchievementDao
import com.thor.core.database.dao.AppDao
import com.thor.core.database.dao.CollectionDao
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.GameNoteDao
import com.thor.core.database.dao.GridDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.database.dao.PlayHistoryDao
import com.thor.core.database.dao.WatchProgressDao
import com.thor.core.database.dao.WidgetDao
import com.thor.core.database.profileScopedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The library, per profile.
     *
     * There is deliberately no `ThorDatabase` binding any more: injecting one
     * would hand out whichever profile's database happened to be open when the
     * graph was built, and go on holding it after a switch.
     */
    @Provides
    @Singleton
    fun providesActiveDatabase(
        @ApplicationContext context: Context,
        migrator: ProfileMigrator,
        @ActiveProfileId profileIds: Flow<String>,
        @ApplicationScope scope: CoroutineScope,
    ): ActiveDatabase = ActiveDatabase(context, migrator, profileIds, scope)

    @Provides
    @Singleton
    fun providesAppDao(active: ActiveDatabase): AppDao =
        profileScopedDao(AppDao::class.java, active.current, active::require, ThorDatabase::appDao)

    @Provides
    @Singleton
    fun providesGameDao(active: ActiveDatabase): GameDao =
        profileScopedDao(GameDao::class.java, active.current, active::require, ThorDatabase::gameDao)

    @Provides
    @Singleton
    fun providesPlatformDao(active: ActiveDatabase): PlatformDao =
        profileScopedDao(PlatformDao::class.java, active.current, active::require, ThorDatabase::platformDao)

    @Provides
    @Singleton
    fun providesFolderDao(active: ActiveDatabase): FolderDao =
        profileScopedDao(FolderDao::class.java, active.current, active::require, ThorDatabase::folderDao)

    @Provides
    @Singleton
    fun providesGridDao(active: ActiveDatabase): GridDao =
        profileScopedDao(GridDao::class.java, active.current, active::require, ThorDatabase::gridDao)

    @Provides
    @Singleton
    fun providesCollectionDao(active: ActiveDatabase): CollectionDao =
        profileScopedDao(CollectionDao::class.java, active.current, active::require, ThorDatabase::collectionDao)

    @Provides
    @Singleton
    fun providesPlayHistoryDao(active: ActiveDatabase): PlayHistoryDao =
        profileScopedDao(PlayHistoryDao::class.java, active.current, active::require, ThorDatabase::playHistoryDao)

    @Provides
    @Singleton
    fun providesGameNoteDao(active: ActiveDatabase): GameNoteDao =
        profileScopedDao(GameNoteDao::class.java, active.current, active::require, ThorDatabase::gameNoteDao)

    @Provides
    @Singleton
    fun providesWatchProgressDao(active: ActiveDatabase): WatchProgressDao =
        profileScopedDao(WatchProgressDao::class.java, active.current, active::require, ThorDatabase::watchProgressDao)

    @Provides
    @Singleton
    fun providesAchievementDao(active: ActiveDatabase): AchievementDao =
        profileScopedDao(AchievementDao::class.java, active.current, active::require, ThorDatabase::achievementDao)

    /**
     * Profile-scoped like the rest, which is the right answer for widgets too.
     *
     * A widget id is allocated by one host and remembered by one database, so a
     * profile switch has to change both together. Sharing this table across
     * profiles would leave each of them drawing the other's widgets, and the
     * first removal from either would release ids the other still points at.
     */
    @Provides
    @Singleton
    fun providesWidgetDao(active: ActiveDatabase): WidgetDao =
        profileScopedDao(WidgetDao::class.java, active.current, active::require, ThorDatabase::widgetDao)
}
