package com.thor.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.thor.core.database.model.AchievementEntity
import com.thor.core.database.model.AppEntity
import com.thor.core.database.model.CollectionEntity
import com.thor.core.database.model.CollectionEntryCrossRef
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.GameVersionEntity
import com.thor.core.database.model.PageEntity
import com.thor.core.database.model.PlacementEntity
import com.thor.core.database.model.PlatformEntity
import com.thor.core.database.model.PlaySessionEntity
import com.thor.core.database.model.WatchProgressEntity
import com.thor.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM apps WHERE is_hidden = 0 ORDER BY sort_title ASC")
    fun observeVisible(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps ORDER BY sort_title ASC")
    fun observeAll(): Flow<List<AppEntity>>

    /**
     * One-shot read.
     *
     * Search runs on every debounced keystroke; collecting the observable query
     * just to take its first emission sets up and tears down a table observer
     * each time, which this avoids.
     */
    @Query("SELECT * FROM apps WHERE is_hidden = 0 ORDER BY sort_title ASC")
    suspend fun getVisible(): List<AppEntity>

    @Query("SELECT * FROM apps WHERE id = :id")
    fun observeById(id: String): Flow<AppEntity?>

    @Query("SELECT * FROM apps WHERE id = :id")
    suspend fun getById(id: String): AppEntity?

    @Query("SELECT * FROM apps WHERE package_name = :packageName AND user_serial = :userSerial")
    suspend fun getByPackage(packageName: String, userSerial: Long = 0L): AppEntity?

    @Query("SELECT * FROM apps WHERE is_emulator = 1 ORDER BY sort_title ASC")
    fun observeEmulators(): Flow<List<AppEntity>>

    @Upsert
    suspend fun upsertAll(apps: List<AppEntity>)

    @Upsert
    suspend fun upsert(app: AppEntity)

    @Query("DELETE FROM apps WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM apps")
    suspend fun allIds(): List<String>

    @Query("UPDATE apps SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE apps SET is_hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query(
        """
        UPDATE apps
        SET launch_count = launch_count + 1,
            last_played_at = :timestamp
        WHERE id = :id
        """,
    )
    suspend fun recordLaunch(id: String, timestamp: Long)

    @Query("UPDATE apps SET total_play_millis = total_play_millis + :millis WHERE id = :id")
    suspend fun addPlayTime(id: String, millis: Long)

    @Query("UPDATE apps SET title = :title, sort_title = :sortTitle WHERE id = :id")
    suspend fun rename(id: String, title: String, sortTitle: String)

    @Query("UPDATE apps SET custom_icon_uri = :uri WHERE id = :id")
    suspend fun setCustomIcon(id: String, uri: String?)
}

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
     */
    @Query(
        """
        DELETE FROM placements
        WHERE entry_id NOT IN (SELECT id FROM apps)
          AND entry_id NOT IN (SELECT id FROM games)
          AND entry_id NOT IN (SELECT id FROM folders)
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
