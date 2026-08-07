package com.thor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.thor.core.database.model.AchievementEntity
import com.thor.core.database.model.AppEntity
import com.thor.core.database.model.CollectionEntity
import com.thor.core.database.model.CollectionEntryCrossRef
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.GameNoteEntity
import com.thor.core.database.model.GameVersionEntity
import com.thor.core.database.model.PageEntity
import com.thor.core.database.model.PlacementEntity
import com.thor.core.database.model.PlatformEntity
import com.thor.core.database.model.PlaySessionEntity
import com.thor.core.database.model.WatchProgressEntity
import com.thor.core.database.model.WidgetEntity

/**
 * The launcher's local library.
 *
 * Schemas are exported to `core/database/schemas` (see the `thor.android.room`
 * convention plugin) so that migrations can be validated against real prior
 * versions rather than being hand-checked.
 */
@Database(
    entities = [
        AppEntity::class,
        GameEntity::class,
        GameVersionEntity::class,
        PlatformEntity::class,
        FolderEntity::class,
        PageEntity::class,
        PlacementEntity::class,
        CollectionEntity::class,
        CollectionEntryCrossRef::class,
        PlaySessionEntity::class,
        GameNoteEntity::class,
        AchievementEntity::class,
        WatchProgressEntity::class,
        WidgetEntity::class,
    ],
    version = ThorDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(ThorTypeConverters::class)
abstract class ThorDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun gameDao(): GameDao
    abstract fun platformDao(): PlatformDao
    abstract fun folderDao(): FolderDao
    abstract fun gridDao(): GridDao
    abstract fun collectionDao(): CollectionDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun achievementDao(): AchievementDao
    abstract fun gameNoteDao(): GameNoteDao

    abstract fun watchProgressDao(): WatchProgressDao

    abstract fun widgetDao(): WidgetDao

    companion object {
        const val VERSION = 8
        const val NAME = "thor-library.db"
    }
}
