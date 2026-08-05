package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.thor.core.database.model.PlaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    @Insert
    suspend fun insert(session: PlaySessionEntity)

    @Query("SELECT * FROM play_sessions WHERE entry_id = :entryId ORDER BY started_at DESC LIMIT :limit")
    suspend fun recentFor(entryId: String, limit: Int = 20): List<PlaySessionEntity>

    @Query("SELECT COALESCE(SUM(duration_millis), 0) FROM play_sessions WHERE entry_id = :entryId")
    suspend fun totalMillisFor(entryId: String): Long

    @Query("SELECT entry_id FROM play_sessions GROUP BY entry_id ORDER BY MAX(started_at) DESC LIMIT :limit")
    fun observeRecentlyPlayedIds(limit: Int): Flow<List<String>>

    @Query("DELETE FROM play_sessions WHERE entry_id = :entryId")
    suspend fun clearFor(entryId: String)
}
