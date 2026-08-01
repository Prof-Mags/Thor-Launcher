package com.thor.core.database.di

import android.content.Context
import androidx.room.Room
import com.thor.core.database.ThorDatabase
import com.thor.core.database.ThorMigrations
import com.thor.core.database.dao.AchievementDao
import com.thor.core.database.dao.AppDao
import com.thor.core.database.dao.CollectionDao
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.GridDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.database.dao.PlayHistoryDao
import com.thor.core.database.dao.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesThorDatabase(
        @ApplicationContext context: Context,
    ): ThorDatabase = Room.databaseBuilder(
        context = context,
        klass = ThorDatabase::class.java,
        name = ThorDatabase.NAME,
    )
        // WAL lets the grid keep reading while a library scan writes, which is
        // what stops a scan from stuttering the UI on a large ROM set.
        .addMigrations(*ThorMigrations.ALL)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Provides fun providesAppDao(db: ThorDatabase): AppDao = db.appDao()
    @Provides fun providesGameDao(db: ThorDatabase): GameDao = db.gameDao()
    @Provides fun providesPlatformDao(db: ThorDatabase): PlatformDao = db.platformDao()
    @Provides fun providesFolderDao(db: ThorDatabase): FolderDao = db.folderDao()
    @Provides fun providesGridDao(db: ThorDatabase): GridDao = db.gridDao()
    @Provides fun providesCollectionDao(db: ThorDatabase): CollectionDao = db.collectionDao()
    @Provides fun providesPlayHistoryDao(db: ThorDatabase): PlayHistoryDao = db.playHistoryDao()

    @Provides fun providesWatchProgressDao(db: ThorDatabase): WatchProgressDao =
        db.watchProgressDao()
    @Provides fun providesAchievementDao(db: ThorDatabase): AchievementDao = db.achievementDao()
}
