package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY sort_title ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    /**
     * One-shot read, for the scraper.
     *
     * Collecting the observable query just to take its first emission would set
     * up and tear down a table observer around a pass that then writes to that
     * same table.
     */
    @Query("SELECT * FROM folders ORDER BY sort_title ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM folders")
    suspend fun allIds(): List<String>

    @Query("UPDATE folders SET child_ids = :childIds WHERE id = :id")
    suspend fun setChildren(id: String, childIds: List<String>)
}
