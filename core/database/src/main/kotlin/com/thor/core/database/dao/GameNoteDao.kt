package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.thor.core.database.model.GameNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameNoteDao {

    @Upsert
    suspend fun upsert(note: GameNoteEntity)

    @Query("SELECT * FROM game_notes WHERE entry_id = :entryId")
    suspend fun getById(entryId: String): GameNoteEntity?

    /**
     * Observed rather than fetched, because a note is written on one surface and
     * read on two others — the information panel and the companion panel — and
     * anything holding a snapshot would show yesterday's text beside today's.
     */
    @Query("SELECT * FROM game_notes WHERE entry_id = :entryId")
    fun observe(entryId: String): Flow<GameNoteEntity?>

    @Query("DELETE FROM game_notes WHERE entry_id = :entryId")
    suspend fun delete(entryId: String)
}
