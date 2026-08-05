package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.GameVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games WHERE is_hidden = 0 ORDER BY sort_title ASC")
    fun observeVisible(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games ORDER BY sort_title ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platform_id = :platformId ORDER BY sort_title ASC")
    fun observeByPlatform(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE content_uri = :uri")
    suspend fun getByUri(uri: String): GameEntity?

    @Query("SELECT * FROM games WHERE duplicate_key = :key")
    suspend fun getByDuplicateKey(key: String): List<GameEntity>

    @Query("SELECT * FROM games WHERE is_hidden = 0 ORDER BY sort_title ASC")
    suspend fun getVisible(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE platform_id = :platformId")
    suspend fun countForPlatform(platformId: String): Int

    @Upsert
    suspend fun upsertAll(games: List<GameEntity>)

    @Upsert
    suspend fun upsert(game: GameEntity)

    @Delete
    suspend fun delete(game: GameEntity)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Every game belonging to one system, for when that system is removed. */
    @Query("DELETE FROM games WHERE platform_id = :platformId")
    suspend fun deleteByPlatform(platformId: String)

    /**
     * The ids of one system's games, before they are deleted.
     *
     * Needed because a game's grid placement is keyed by its id and outlives the
     * row: removing a system without collecting these first strands a placement
     * for every game it had.
     */
    @Query("SELECT id FROM games WHERE platform_id = :platformId")
    suspend fun idsByPlatform(platformId: String): List<String>

    /** The systems actually represented in the library, whatever the settings say. */
    @Query("SELECT DISTINCT platform_id FROM games")
    suspend fun allPlatformIds(): List<String>

    @Query("SELECT id FROM games")
    suspend fun allIds(): List<String>

    @Query("SELECT content_uri FROM games")
    suspend fun allContentUris(): List<String>

    @Query("UPDATE games SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE games SET is_hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("UPDATE games SET is_missing = :missing WHERE id IN (:ids)")
    suspend fun setMissing(ids: List<String>, missing: Boolean)

    @Query("UPDATE games SET emulator_package = :packageName WHERE id = :id")
    suspend fun setEmulator(id: String, packageName: String?)

    /**
     * Reassigns a game to another system.
     *
     * `duplicate_key` is derived from the platform, so it is rewritten in the
     * same statement — leaving it stale would make the de-duplicator treat the
     * moved game as a variant of its old system's titles.
     */
    @Query(
        """
        UPDATE games
        SET platform_id = :platformId, duplicate_key = :duplicateKey
        WHERE id = :id
        """,
    )
    suspend fun setPlatform(id: String, platformId: String, duplicateKey: String)

    @Query(
        """
        UPDATE games
        SET launch_count = launch_count + 1,
            last_played_at = :timestamp,
            first_played_at = COALESCE(first_played_at, :timestamp)
        WHERE id = :id
        """,
    )
    suspend fun recordLaunch(id: String, timestamp: Long)

    @Query("UPDATE games SET total_play_millis = total_play_millis + :millis WHERE id = :id")
    suspend fun addPlayTime(id: String, millis: Long)

    @Query("UPDATE games SET title = :title, sort_title = :sortTitle WHERE id = :id")
    suspend fun rename(id: String, title: String, sortTitle: String)

    /**
     * Replaces the whole metadata blob.
     *
     * Callers read, copy and write back rather than patching fields, so the
     * `lockedFields` set stays consistent with the values it protects.
     */
    @Query("UPDATE games SET metadata = :metadata WHERE id = :id")
    suspend fun setMetadata(id: String, metadata: com.thor.core.model.GameMetadata)

    /**
     * Free-text search across title and metadata.
     *
     * The metadata JSON is matched as raw text, which is enough to find a
     * developer or genre without maintaining a separate FTS index — the library
     * is small enough (tens of thousands of rows at most) that the scan stays
     * inside a frame budget when debounced.
     */
    @Query(
        """
        SELECT * FROM games
        WHERE is_hidden = 0
          AND (sort_title LIKE '%' || :query || '%' OR metadata LIKE '%' || :query || '%')
        ORDER BY
          CASE WHEN sort_title LIKE :query || '%' THEN 0 ELSE 1 END,
          sort_title ASC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<GameEntity>

    @Query("SELECT * FROM game_versions WHERE game_id = :gameId ORDER BY label ASC")
    suspend fun versionsFor(gameId: String): List<GameVersionEntity>

    @Query("SELECT * FROM game_versions WHERE game_id IN (:gameIds)")
    suspend fun versionsFor(gameIds: List<String>): List<GameVersionEntity>

    @Upsert
    suspend fun upsertVersions(versions: List<GameVersionEntity>)

    @Query("DELETE FROM game_versions WHERE game_id = :gameId")
    suspend fun deleteVersionsFor(gameId: String)
}
