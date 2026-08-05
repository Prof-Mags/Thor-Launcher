package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.thor.core.database.model.PageEntity
import com.thor.core.database.model.PlacementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GridDao {

    @Query("SELECT * FROM pages ORDER BY page_index ASC")
    fun observePages(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages ORDER BY page_index ASC")
    suspend fun getPages(): List<PageEntity>

    @Upsert
    suspend fun upsertPage(page: PageEntity)

    @Upsert
    suspend fun upsertPages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)

    @Query("SELECT * FROM placements WHERE is_dock = 0 AND parent_folder_id IS NULL")
    fun observePlacements(): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements")
    fun observeAllPlacements(): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements WHERE parent_folder_id = :folderId ORDER BY folder_index ASC")
    fun observeFolderContents(folderId: String): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements WHERE is_dock = 1 ORDER BY column_index ASC")
    fun observeDock(): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements")
    suspend fun getAllPlacements(): List<PlacementEntity>

    @Query("SELECT * FROM placements WHERE entry_id = :entryId")
    suspend fun getPlacement(entryId: String): PlacementEntity?

    @Upsert
    suspend fun upsert(placement: PlacementEntity)

    @Upsert
    suspend fun upsertAll(placements: List<PlacementEntity>)

    @Query("DELETE FROM placements WHERE entry_id = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    @Query("DELETE FROM placements WHERE entry_id IN (:entryIds)")
    suspend fun deleteByEntryIds(entryIds: List<String>)

    @Query("DELETE FROM placements WHERE parent_folder_id = :folderId")
    suspend fun deleteFolderContents(folderId: String)

    /**
     * Removes placements pointing at entries that no longer exist anywhere.
     * Run after every scan, since placements carry no foreign key.
     *
     * The widget clause is not decoration. A widget's identity lives in its own
     * table and in no other, so without it every placed widget matched "exists
     * nowhere" and had its cell deleted by the scan that runs at startup — the
     * row survived, the widget did not, and it read as widgets not being saved.
     * Matching against the table rather than excusing the `widget:` prefix
     * outright keeps the useful half: a widget whose row really has gone still
     * has its cell reclaimed.
     */
    @Query(
        """
        DELETE FROM placements
        WHERE entry_id NOT IN (SELECT id FROM apps)
          AND entry_id NOT IN (SELECT id FROM games)
          AND entry_id NOT IN (SELECT id FROM folders)
          AND entry_id NOT IN (SELECT 'widget:' || app_widget_id FROM widgets)
          AND entry_id NOT LIKE 'shortcut:%'
        """,
    )
    suspend fun pruneOrphans(): Int

    @Query(
        """
        SELECT COUNT(*) FROM placements
        WHERE page_index = :pageIndex AND is_dock = 0 AND parent_folder_id IS NULL
        """,
    )
    suspend fun occupancy(pageIndex: Int): Int

    @Query(
        """
        SELECT (row * :columns + column_index) FROM placements
        WHERE page_index = :pageIndex AND is_dock = 0 AND parent_folder_id IS NULL
        """,
    )
    suspend fun occupiedCells(pageIndex: Int, columns: Int): List<Int>

    @Transaction
    suspend fun replaceLayout(pages: List<PageEntity>, placements: List<PlacementEntity>) {
        clearPlacements()
        clearPages()
        upsertPages(pages)
        upsertAll(placements)
    }

    @Query("DELETE FROM placements")
    suspend fun clearPlacements()

    @Query("DELETE FROM pages")
    suspend fun clearPages()
}
