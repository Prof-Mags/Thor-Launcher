package com.thor.data.repository

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GridDao
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.PageEntity
import com.thor.core.database.model.PlacementEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.FolderIcons
import com.thor.core.model.GridPage
import com.thor.core.model.GridPlacement
import com.thor.core.model.PlatformFolders
import com.thor.core.model.GridSpec
import com.thor.core.model.SmartQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the arrangement of the bottom-screen grid.
 *
 * Placement is explicit and sparse: an entry occupies exactly the cell the user
 * put it in, and empty cells stay empty. That is a deliberate departure from
 * the usual "flow items into a list" launcher grid, and it is what makes the
 * layout feel like the 3DS HOME Menu rather than like an app drawer.
 */
@Singleton
class GridLayoutRepository @Inject constructor(
    private val gridDao: GridDao,
    private val folderDao: FolderDao,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    val pages: Flow<List<GridPage>> = gridDao.observePages()
        .map { entities ->
            entities.map { GridPage(it.id, it.pageIndex, it.title, it.wallpaperUri) }
        }
        .distinctUntilChanged()

    /** Placements on pages, excluding dock slots and folder contents. */
    val placements: Flow<List<GridPlacement>> = gridDao.observePlacements()
        .map { list -> list.map(PlacementEntity::toDomain) }
        .distinctUntilChanged()

    /** Dock slot assignments, ordered by slot index. */
    val dockPlacements: Flow<List<GridPlacement>> = gridDao.observeDock()
        .map { list -> list.map(PlacementEntity::toDomain) }
        .distinctUntilChanged()

    fun folderContents(folderId: String): Flow<List<String>> =
        gridDao.observeFolderContents(folderId).map { list -> list.map(PlacementEntity::entryId) }

    /**
     * Places any entries that have no cell yet.
     *
     * Called after every scan. New entries fill the first free cell in
     * reading order, creating pages as needed, so a library that grew by 300
     * games does not disturb anything the user has already arranged.
     */
    suspend fun placeUnplacedEntries(entryIds: Collection<String>) =
        withContext(defaultDispatcher) {
            val spec = settings.grid.first()
            val existing = gridDao.getAllPlacements()
            val placed = existing.mapTo(mutableSetOf(), PlacementEntity::entryId)
            val unplaced = entryIds.filterNot { it in placed }
            if (unplaced.isEmpty()) return@withContext

            ensurePageExists(0)

            // Occupancy is tracked in memory while assigning, so a batch of 300
            // new entries costs one read rather than one query per entry.
            val occupied = existing
                .filterNot { it.isDock || it.parentFolderId != null }
                .groupBy(PlacementEntity::pageIndex) { it.cellIndex(spec.columns) }
                .mapValues { it.value.toMutableSet() }
                .toMutableMap()

            val additions = mutableListOf<PlacementEntity>()
            var page = 0
            var cell = 0

            for (entryId in unplaced.sorted()) {
                // Advance to the next free cell, spilling onto new pages.
                while (true) {
                    val pageCells = occupied.getOrPut(page) { mutableSetOf() }
                    if (cell >= spec.cellsPerPage) {
                        page++
                        cell = 0
                        continue
                    }
                    if (cell in pageCells) {
                        cell++
                        continue
                    }
                    pageCells += cell
                    break
                }

                additions += GridPlacement
                    .fromCellIndex(entryId, page, cell, spec.columns)
                    .toEntity()
                cell++
            }

            val highestPage = additions.maxOfOrNull(PlacementEntity::pageIndex) ?: 0
            (0..highestPage).forEach { ensurePageExists(it) }
            gridDao.upsertAll(additions)
        }

    /**
     * Moves an entry to a specific cell.
     *
     * Returns the id of whatever already occupied that cell, whose placement is
     * removed — it is now off the grid and in the caller's hands.
     *
     * This used to swap the two, sending the occupant back to the cell the
     * dragged icon came from. That is a position the user never chose, and on a
     * hand-arranged grid it means one deliberate move silently relocates a second
     * icon somewhere across the page. Handing the occupant back instead lets the
     * caller keep it held so the user places it themselves.
     *
     * @return the displaced entry's id, or null if the cell was empty
     */
    suspend fun moveEntry(
        entryId: String,
        pageIndex: Int,
        row: Int,
        column: Int,
    ): String? = withContext(defaultDispatcher) {
        val spec = settings.grid.first()
        if (row !in 0 until spec.rows || column !in 0 until spec.columns) return@withContext null

        val source = gridDao.getPlacement(entryId) ?: return@withContext null
        val occupant = gridDao.getAllPlacements().firstOrNull {
            !it.isDock && it.parentFolderId == null &&
                it.pageIndex == pageIndex && it.row == row && it.column == column
        }

        ensurePageExists(pageIndex)

        gridDao.upsert(
            source.copy(pageIndex = pageIndex, row = row, column = column, parentFolderId = null),
        )

        val displaced = occupant?.takeIf { it.entryId != entryId } ?: return@withContext null
        // Removed rather than parked somewhere: the caller picks it straight back
        // up, and leaving it on the grid meanwhile would put two icons in one
        // cell for as long as the user took to decide.
        gridDao.deleteByEntryId(displaced.entryId)
        displaced.entryId
    }

    /**
     * Places an entry that currently has no placement into a specific cell.
     *
     * Used to land an entry that was displaced by a move, and to add one from the
     * app drawer. Refuses an occupied cell rather than starting another
     * displacement chain, which could otherwise run indefinitely.
     *
     * @return true when the entry was placed
     */
    suspend fun placeEntryAt(
        entryId: String,
        pageIndex: Int,
        row: Int,
        column: Int,
    ): Boolean = withContext(defaultDispatcher) {
        val spec = settings.grid.first()
        if (row !in 0 until spec.rows || column !in 0 until spec.columns) return@withContext false

        // The entry's own placement does not count as an occupant, so returning
        // an icon to the cell it was picked up from — which it still nominally
        // holds until it is dropped — succeeds rather than being refused.
        val occupied = gridDao.getAllPlacements().any {
            !it.isDock && it.parentFolderId == null && it.entryId != entryId &&
                it.pageIndex == pageIndex && it.row == row && it.column == column
        }
        if (occupied) return@withContext false

        ensurePageExists(pageIndex)
        gridDao.upsert(
            PlacementEntity(
                entryId = entryId,
                pageIndex = pageIndex,
                row = row,
                column = column,
            ),
        )
        true
    }

    /**
     * Takes an entry off the grid, leaving the entry itself alone.
     *
     * Only the placement goes; the app or game stays in the library. That is
     * what makes "remove from grid" distinct from hiding or uninstalling.
     */
    suspend fun removePlacement(entryId: String) = withContext(defaultDispatcher) {
        gridDao.deleteByEntryId(entryId)
    }

    /** Assigns an entry to a dock slot, evicting whatever held it. */
    suspend fun setDockSlot(entryId: String, slot: Int) = withContext(defaultDispatcher) {
        val current = gridDao.observeDock().first()
        current.firstOrNull { it.column == slot }?.let { gridDao.deleteByEntryId(it.entryId) }
        gridDao.upsert(
            PlacementEntity(
                entryId = entryId,
                pageIndex = 0,
                row = 0,
                column = slot,
                isDock = true,
            ),
        )
    }

    /**
     * Creates a folder containing two entries.
     *
     * This is the drop-one-icon-onto-another gesture. The folder takes the
     * target's cell, and both entries move inside it.
     */
    suspend fun createFolderFrom(
        draggedId: String,
        targetId: String,
        title: String = "Folder",
    ): String? = withContext(defaultDispatcher) {
        val target = gridDao.getPlacement(targetId) ?: return@withContext null
        val folderId = "folder:${UUID.randomUUID()}"

        folderDao.upsert(
            FolderEntity(
                id = folderId,
                title = title,
                sortTitle = TitleNormalizer.sortKey(title),
                childIds = listOf(targetId, draggedId),
                iconKey = FolderIcons.DEFAULT,
            ),
        )

        gridDao.upsertAll(
            listOf(
                PlacementEntity(
                    entryId = folderId,
                    pageIndex = target.pageIndex,
                    row = target.row,
                    column = target.column,
                ),
                PlacementEntity(
                    entryId = targetId,
                    pageIndex = 0, row = 0, column = 0,
                    parentFolderId = folderId,
                    folderIndex = 0,
                ),
                PlacementEntity(
                    entryId = draggedId,
                    pageIndex = 0, row = 0, column = 0,
                    parentFolderId = folderId,
                    folderIndex = 1,
                ),
            ),
        )
        folderId
    }

    /**
     * Creates an empty folder with no contents.
     *
     * Unlike [createFolderFrom] this does not consume another entry's cell —
     * the caller places it. Note that [dissolveIfEmpty] would collapse a folder
     * reaching zero children, so an empty folder created here only persists
     * until something is removed from it; that is intentional, since an empty
     * folder the user never fills is clutter.
     */
    suspend fun createEmptyFolder(title: String): String = withContext(defaultDispatcher) {
        val folderId = "folder:${UUID.randomUUID()}"
        folderDao.upsert(
            FolderEntity(
                id = folderId,
                title = title,
                sortTitle = TitleNormalizer.sortKey(title),
                iconKey = FolderIcons.DEFAULT,
            ),
        )
        folderId
    }

    /**
     * Files games into one folder per platform, and puts the folders on the grid.
     *
     * Adding a platform can bring in hundreds of games at once, and dropping those
     * straight onto the grid buries everything that was already there under pages of
     * one system. A folder per platform is what the user would have built by hand,
     * so the launcher builds it: the grid gains one cell per platform rather than one
     * per ROM.
     *
     * Two properties make this safe to run after *every* scan rather than only when a
     * platform is added:
     *
     *  - Only entries with no placement are touched, and a folder's children have
     *    placements of their own — so a game the user dragged out onto the grid, or
     *    moved to a different folder, is left exactly where they put it.
     *  - The folder's id is derived from the platform, so it is found again next time
     *    however the user has since renamed or re-themed it.
     *
     * @param gamesByPlatform platform id to the ids of its games
     * @param titleFor names a folder being created for the first time
     */
    suspend fun fileGamesIntoPlatformFolders(
        gamesByPlatform: Map<String, List<String>>,
        titleFor: (String) -> String,
        /**
         * The platform's icon-pack artwork, if any.
         *
         * Folders are born wearing it. Without this the ordering defeats the
         * feature entirely: adding a platform applies the pack's artwork to the
         * *platform*, but its folder does not exist yet — the scan creates it
         * moments later, blank — so the cell the user actually looks at stayed
         * undressed until the pack was removed and imported again.
         */
        artworkFor: (String) -> String? = { null },
    ) = withContext(defaultDispatcher) {
        if (gamesByPlatform.isEmpty()) return@withContext

        val placed = gridDao.getAllPlacements().mapTo(mutableSetOf(), PlacementEntity::entryId)
        val folderIds = mutableListOf<String>()

        for ((platformId, gameIds) in gamesByPlatform) {
            val fresh = gameIds.filterNot { it in placed }.sorted()
            val folderId = platformFolderId(platformId)
            val existing = folderDao.getById(folderId)

            // Nothing new and no folder yet means an empty platform: no folder is
            // created for it, because an empty folder on the grid is just clutter of
            // a different shape.
            if (fresh.isEmpty() && existing == null) continue

            val artwork = artworkFor(platformId)

            val folder = when {
                existing == null -> {
                    val title = titleFor(platformId)
                    FolderEntity(
                        id = folderId,
                        title = title,
                        sortTitle = TitleNormalizer.sortKey(title),
                        iconKey = FolderIcons.DEFAULT,
                        artworkUri = artwork,
                    ).also { folderDao.upsert(it) }
                }

                /*
                 * An existing folder is filled in only when it has none.
                 *
                 * This covers the other ordering — folder first, pack installed
                 * afterwards — without overwriting artwork on every scan, which
                 * would silently undo a cover the user picked for that folder by
                 * hand. Replacing artwork is what installing a pack does, and that
                 * path is explicit.
                 */
                existing.artworkUri == null && artwork != null ->
                    existing.copy(artworkUri = artwork).also { folderDao.upsert(it) }

                else -> existing
            }

            folderIds += folderId
            if (fresh.isEmpty()) continue

            // Written as one batch rather than through `addToFolder` per game: a
            // freshly added platform is hundreds of rows, and doing that one query at
            // a time is the difference between a scan that finishes and one that
            // appears to hang.
            val children = folder.childIds
            val additions = fresh.filterNot { it in children }
            if (additions.isEmpty()) continue

            gridDao.upsertAll(
                additions.mapIndexed { offset, entryId ->
                    PlacementEntity(
                        entryId = entryId,
                        pageIndex = 0,
                        row = 0,
                        column = 0,
                        parentFolderId = folderId,
                        folderIndex = children.size + offset,
                    )
                },
            )
            folderDao.setChildren(folderId, children + additions)
        }

        // The folders themselves are what the grid shows.
        placeUnplacedEntries(folderIds)
    }

    /**
     * A folder id derived from its platform.
     *
     * Deterministic on purpose: the folder has to be found again on the next scan,
     * and searching by title would lose it the moment the user renamed it.
     */
    private fun platformFolderId(platformId: String): String =
        PlatformFolders.idFor(platformId)

    /** Adds an entry to an existing folder. */
    suspend fun addToFolder(entryId: String, folderId: String) = withContext(defaultDispatcher) {
        val folder = folderDao.getById(folderId) ?: return@withContext
        if (folder.smartQuery != null) return@withContext

        val contents = gridDao.observeFolderContents(folderId).first()
        if (contents.any { it.entryId == entryId }) return@withContext

        gridDao.upsert(
            PlacementEntity(
                entryId = entryId,
                pageIndex = 0, row = 0, column = 0,
                parentFolderId = folderId,
                folderIndex = contents.size,
            ),
        )
        folderDao.setChildren(folderId, folder.childIds + entryId)
    }

    /**
     * Removes an entry from its folder and returns it to the grid.
     *
     * The entry lands in the first free cell of the folder's own page, so it
     * reappears near where the user was looking.
     */
    suspend fun removeFromFolder(entryId: String, folderId: String) =
        withContext(defaultDispatcher) {
            val folder = folderDao.getById(folderId) ?: return@withContext
            val folderPlacement = gridDao.getPlacement(folderId)
            val spec = settings.grid.first()
            val page = folderPlacement?.pageIndex ?: 0

            val occupied = gridDao.occupiedCells(page, spec.columns).toSet()
            val freeCell = (0 until spec.cellsPerPage).firstOrNull { it !in occupied }

            if (freeCell != null) {
                gridDao.upsert(
                    GridPlacement.fromCellIndex(entryId, page, freeCell, spec.columns).toEntity(),
                )
            } else {
                // The page is full; spill onto a fresh page rather than refusing.
                val newPage = (gridDao.getPages().maxOfOrNull(PageEntity::pageIndex) ?: 0) + 1
                ensurePageExists(newPage)
                gridDao.upsert(
                    GridPlacement.fromCellIndex(entryId, newPage, 0, spec.columns).toEntity(),
                )
            }

            folderDao.setChildren(folderId, folder.childIds - entryId)
            dissolveIfEmpty(folderId)
        }

    /**
     * Deletes a folder, returning its contents to the grid.
     *
     * A folder is only a container; deleting one must never delete games.
     */
    suspend fun deleteFolder(folderId: String) = withContext(defaultDispatcher) {
        val contents = gridDao.observeFolderContents(folderId).first()
        contents.forEach { removeFromFolder(it.entryId, folderId) }
        gridDao.deleteFolderContents(folderId)
        gridDao.deleteByEntryId(folderId)
        folderDao.deleteById(folderId)
    }

    /** Collapses a folder that has one or zero children left. */
    private suspend fun dissolveIfEmpty(folderId: String) {
        val remaining = gridDao.observeFolderContents(folderId).first()
        if (remaining.size > 1) return
        remaining.forEach { removeFromFolder(it.entryId, folderId) }
        gridDao.deleteByEntryId(folderId)
        folderDao.deleteById(folderId)
    }

    suspend fun renameFolder(folderId: String, title: String) = withContext(defaultDispatcher) {
        val folder = folderDao.getById(folderId) ?: return@withContext
        folderDao.upsert(
            folder.copy(title = title, sortTitle = TitleNormalizer.sortKey(title)),
        )
    }

    suspend fun recolorFolder(folderId: String, accentArgb: Long?) =
        withContext(defaultDispatcher) {
            val folder = folderDao.getById(folderId) ?: return@withContext
            folderDao.upsert(folder.copy(accentArgb = accentArgb))
        }

    suspend fun setFolderArtwork(folderId: String, artworkUri: String?) =
        withContext(defaultDispatcher) {
            val folder = folderDao.getById(folderId) ?: return@withContext
            folderDao.upsert(folder.copy(artworkUri = artworkUri))
        }

    /** Creates a smart folder backed by [query]. */
    suspend fun createSmartFolder(title: String, query: SmartQuery): String =
        withContext(defaultDispatcher) {
            val folderId = "folder:${UUID.randomUUID()}"
            folderDao.upsert(
                FolderEntity(
                    id = folderId,
                    title = title,
                    sortTitle = TitleNormalizer.sortKey(title),
                    smartQuery = query,
                    iconKey = "star",
                ),
            )
            placeUnplacedEntries(listOf(folderId))
            folderId
        }

    suspend fun addPage(): Int = withContext(defaultDispatcher) {
        val next = (gridDao.getPages().maxOfOrNull(PageEntity::pageIndex) ?: -1) + 1
        ensurePageExists(next)
        next
    }

    /**
     * Removes a page and closes the gap.
     *
     * Entries on the removed page move to the end rather than being deleted.
     */
    suspend fun removePage(pageIndex: Int) = withContext(defaultDispatcher) {
        val pages = gridDao.getPages()
        if (pages.size <= 1) return@withContext
        val page = pages.firstOrNull { it.pageIndex == pageIndex } ?: return@withContext

        val orphans = gridDao.getAllPlacements()
            .filter { !it.isDock && it.parentFolderId == null && it.pageIndex == pageIndex }

        gridDao.deleteByEntryIds(orphans.map(PlacementEntity::entryId))
        gridDao.deletePage(page.id)

        // Re-index the pages after the removed one.
        val reindexed = pages
            .filter { it.pageIndex > pageIndex }
            .map { it.copy(pageIndex = it.pageIndex - 1) }
        gridDao.upsertPages(reindexed)

        val shifted = gridDao.getAllPlacements()
            .filter { !it.isDock && it.parentFolderId == null && it.pageIndex > pageIndex }
            .map { it.copy(pageIndex = it.pageIndex - 1) }
        gridDao.upsertAll(shifted)

        placeUnplacedEntries(orphans.map(PlacementEntity::entryId))
    }

    suspend fun renamePage(pageIndex: Int, title: String) = withContext(defaultDispatcher) {
        val page = gridDao.getPages().firstOrNull { it.pageIndex == pageIndex } ?: return@withContext
        gridDao.upsertPage(page.copy(title = title))
    }

    /** Drops placements for entries that no longer exist. */
    suspend fun pruneOrphans(): Int = withContext(defaultDispatcher) {
        gridDao.pruneOrphans()
    }

    /**
     * Lays entries out in the given order, filling pages in reading order.
     *
     * Backs the Start panel's Sort. Only page placements are rewritten — dock
     * slots and folder contents keep their positions, because sorting the grid
     * should not empty the dock or shuffle what is inside a folder.
     */
    suspend fun applyOrder(orderedEntryIds: List<String>, spec: GridSpec) =
        withContext(defaultDispatcher) {
            val placements = orderedEntryIds.mapIndexed { index, entryId ->
                val page = index / spec.cellsPerPage
                val cell = index % spec.cellsPerPage
                PlacementEntity(
                    entryId = entryId,
                    pageIndex = page,
                    row = cell / spec.columns,
                    column = cell % spec.columns,
                )
            }

            val pageCount = (placements.maxOfOrNull(PlacementEntity::pageIndex) ?: 0) + 1
            (0 until pageCount).forEach { ensurePageExists(it) }
            gridDao.upsertAll(placements)
        }

    /**
     * Re-flows only when the current layout no longer fits [spec].
     *
     * Shrinking the grid strands every placement whose row or column is now out
     * of range: the cell index it maps to lies beyond the matrix, so the entry
     * silently stops rendering and looks deleted. Growing the grid is safe —
     * existing coordinates stay valid — so the layout is left alone in that
     * direction and the user's arrangement survives.
     *
     * @return true when a reflow was performed
     */
    suspend fun reflowIfNeeded(spec: GridSpec): Boolean = withContext(defaultDispatcher) {
        val stranded = gridDao.getAllPlacements().any { placement ->
            !placement.isDock &&
                placement.parentFolderId == null &&
                (placement.row >= spec.rows || placement.column >= spec.columns)
        }
        if (stranded) reflow(spec)
        stranded
    }

    /**
     * Re-flows the whole grid.
     *
     * Used when the user changes grid dimensions such that existing cells no
     * longer fit, and by "reset layout". Order is preserved; positions are not.
     */
    suspend fun reflow(spec: GridSpec) = withContext(defaultDispatcher) {
        val all = gridDao.getAllPlacements()
            .filter { !it.isDock && it.parentFolderId == null }
            .sortedWith(
                compareBy<PlacementEntity> { it.pageIndex }
                    .thenBy { it.row }
                    .thenBy { it.column },
            )

        val reflowed = all.mapIndexed { index, placement ->
            val page = index / spec.cellsPerPage
            val cell = index % spec.cellsPerPage
            placement.copy(
                pageIndex = page,
                row = cell / spec.columns,
                column = cell % spec.columns,
            )
        }

        val pageCount = (reflowed.maxOfOrNull(PlacementEntity::pageIndex) ?: 0) + 1
        (0 until pageCount).forEach { ensurePageExists(it) }
        gridDao.upsertAll(reflowed)
    }

    private suspend fun ensurePageExists(pageIndex: Int) {
        val pages = gridDao.getPages()
        if (pages.any { it.pageIndex == pageIndex }) return
        gridDao.upsertPage(
            PageEntity(id = "page:$pageIndex", pageIndex = pageIndex),
        )
    }

    private fun PlacementEntity.cellIndex(columns: Int): Int = row * columns + column
}
