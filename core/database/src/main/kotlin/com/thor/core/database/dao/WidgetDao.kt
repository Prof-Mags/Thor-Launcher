package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thor.core.database.model.WidgetEntity
import kotlinx.coroutines.flow.Flow

/** Placed widgets, in the order they were added. */
@Dao
interface WidgetDao {

    @Query("SELECT * FROM widgets ORDER BY added_at ASC")
    fun observeAll(): Flow<List<WidgetEntity>>

    @Query("SELECT * FROM widgets ORDER BY added_at ASC")
    suspend fun all(): List<WidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(widget: WidgetEntity)

    @Query("UPDATE widgets SET span_columns = :columns, span_rows = :rows WHERE app_widget_id = :id")
    suspend fun resize(id: Int, columns: Int, rows: Int)

    @Query("DELETE FROM widgets WHERE app_widget_id = :id")
    suspend fun delete(id: Int)

    /**
     * Ids the launcher believes in, so the host can be told about the rest.
     *
     * `AppWidgetHost` keeps its own list and hands back everything it has ever
     * allocated. Anything it names that is not here is an allocation that
     * outlived its row — a widget removed while the host was not listening, or a
     * crash between allocating an id and storing it — and it goes on leaking
     * until somebody deletes it.
     */
    @Query("SELECT app_widget_id FROM widgets")
    suspend fun allIds(): List<Int>

    /**
     * The smallest id in the table, which is how a built-in gets its key.
     *
     * The launcher's own widgets are not allocated by the platform and so have
     * no id of their own; they take one from below zero, where the platform's
     * allocator never goes. Null when the table is empty.
     */
    @Query("SELECT MIN(app_widget_id) FROM widgets")
    suspend fun lowestId(): Int?
}
