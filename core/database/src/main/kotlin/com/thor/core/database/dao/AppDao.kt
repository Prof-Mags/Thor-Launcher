package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.AppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM apps WHERE is_hidden = 0 ORDER BY sort_title ASC")
    fun observeVisible(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps ORDER BY sort_title ASC")
    fun observeAll(): Flow<List<AppEntity>>

    /**
     * One-shot read.
     *
     * Search runs on every debounced keystroke; collecting the observable query
     * just to take its first emission sets up and tears down a table observer
     * each time, which this avoids.
     */
    @Query("SELECT * FROM apps WHERE is_hidden = 0 ORDER BY sort_title ASC")
    suspend fun getVisible(): List<AppEntity>

    @Query("SELECT * FROM apps WHERE id = :id")
    fun observeById(id: String): Flow<AppEntity?>

    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun getById(id: String): AppEntity?

    @Query("SELECT * FROM apps WHERE package_name = :packageName AND user_serial = :userSerial")
    suspend fun getByPackage(packageName: String, userSerial: Long = 0L): AppEntity?

    @Query("SELECT * FROM apps WHERE is_emulator = 1 ORDER BY sort_title ASC")
    fun observeEmulators(): Flow<List<AppEntity>>

    @Upsert
    suspend fun upsertAll(apps: List<AppEntity>)

    @Upsert
    suspend fun upsert(app: AppEntity)

    @Query("DELETE FROM apps WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM apps")
    suspend fun allIds(): List<String>

    @Query("UPDATE apps SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE apps SET is_hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query(
        """
        UPDATE apps
        SET launch_count = launch_count + 1,
            last_played_at = :timestamp
        WHERE id = :id
        """,
    )
    suspend fun recordLaunch(id: String, timestamp: Long)

    @Query("UPDATE apps SET total_play_millis = total_play_millis + :millis WHERE id = :id")
    suspend fun addPlayTime(id: String, millis: Long)

    @Query("UPDATE apps SET title = :title, sort_title = :sortTitle WHERE id = :id")
    suspend fun rename(id: String, title: String, sortTitle: String)

    @Query("UPDATE apps SET custom_icon_uri = :uri WHERE id = :id")
    suspend fun setCustomIcon(id: String, uri: String?)
}
