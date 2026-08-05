package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.AchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE entry_id = :entryId ORDER BY earned_at DESC, points DESC")
    fun observeFor(entryId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE entry_id = :entryId AND earned_at IS NOT NULL ORDER BY earned_at DESC LIMIT :limit")
    suspend fun recentlyEarned(entryId: String, limit: Int): List<AchievementEntity>

    @Upsert
    suspend fun upsertAll(achievements: List<AchievementEntity>)

    @Query("DELETE FROM achievements WHERE entry_id = :entryId")
    suspend fun clearFor(entryId: String)
}
