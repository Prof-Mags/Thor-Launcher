package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.WatchProgressEntity
import com.thor.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

/**
 * Resume points for films and episodes.
 *
 * The "continue watching" shelf is a query rather than a maintained list: what
 * belongs on it is exactly what has a recent position that is neither at the
 * start nor at the end, and deriving that on read means nothing can go stale.
 */
@Dao
interface WatchProgressDao {

    /**
     * What to offer as "continue watching", most recent first.
     *
     * Bounded at both ends deliberately. A title barely started is not something
     * the viewer is partway through, and one at the credits is finished — both
     * would otherwise sit at the top of the shelf forever, which is how a
     * continue-watching row fills up with things nobody wants to continue.
     */
    @Query(
        """
        SELECT * FROM watch_progress
        WHERE duration_millis > 0
          AND position_millis > :minimumPositionMillis
          AND position_millis < duration_millis * :finishedFraction
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun observeInProgress(
        minimumPositionMillis: Long = WatchProgress.RESUME_FLOOR_MS,
        finishedFraction: Float = WatchProgress.FINISHED_FRACTION,
        limit: Int = 20,
    ): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE id = :id")
    suspend fun find(id: String): WatchProgressEntity?

    /** Every recorded position for one title, so a series can resume its episode. */
    @Query("SELECT * FROM watch_progress WHERE media_key = :mediaKey ORDER BY updated_at DESC")
    suspend fun forMedia(mediaKey: String): List<WatchProgressEntity>

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun clear(id: String)

    @Query("DELETE FROM watch_progress WHERE media_key = :mediaKey")
    suspend fun clearMedia(mediaKey: String)
}
