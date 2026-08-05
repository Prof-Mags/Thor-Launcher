package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.PlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platforms ORDER BY sort_index ASC")
    fun observeAll(): Flow<List<PlatformEntity>>

    /** Systems the user has added to their setup. */
    @Query("SELECT * FROM platforms WHERE is_added = 1 ORDER BY sort_index ASC")
    fun observeAdded(): Flow<List<PlatformEntity>>

    @Query("UPDATE platforms SET is_added = :added WHERE id = :id")
    suspend fun setAdded(id: String, added: Boolean)

    @Query("UPDATE platforms SET emulator_packages = :packages WHERE id = :id")
    suspend fun setEmulators(id: String, packages: List<String>)

    /**
     * Forgets the pictures a system was wearing, keeping the system itself.
     *
     * Used when a platform is removed. The row is a built-in definition and has
     * to survive so the system can be added back; the artwork is a record of a
     * library that no longer exists, and leaving it means a system added back
     * later silently reappears in the artwork of the one that was deleted.
     */
    @Query(
        """
        UPDATE platforms
        SET artwork_icon_uri = NULL,
            artwork_hero_uri = NULL,
            artwork_logo_uri = NULL,
            artwork_pack_id = NULL
        WHERE id = :id
        """,
    )
    suspend fun clearArtwork(id: String)

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun getById(id: String): PlatformEntity?

    @Query("SELECT * FROM platforms")
    suspend fun getAll(): List<PlatformEntity>

    @Upsert
    suspend fun upsertAll(platforms: List<PlatformEntity>)

    @Upsert
    suspend fun upsert(platform: PlatformEntity)

    @Query("DELETE FROM platforms WHERE id = :id AND is_custom = 1")
    suspend fun deleteCustom(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(platforms: List<PlatformEntity>)
}
