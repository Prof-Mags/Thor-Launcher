package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.CollectionEntity
import com.thor.core.database.model.CollectionEntryCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY sort_index ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        SELECT entry_id FROM collection_entries
        WHERE collection_id = :collectionId
        ORDER BY position ASC
        """,
    )
    fun observeEntryIds(collectionId: String): Flow<List<String>>

    @Upsert
    suspend fun upsertCrossRefs(refs: List<CollectionEntryCrossRef>)

    @Query("DELETE FROM collection_entries WHERE collection_id = :collectionId AND entry_id = :entryId")
    suspend fun removeEntry(collectionId: String, entryId: String)

    @Query("DELETE FROM collection_entries WHERE collection_id = :collectionId")
    suspend fun clearCollection(collectionId: String)
}
