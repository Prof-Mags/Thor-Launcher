package com.thor.data.library

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GridDao
import com.thor.core.database.dao.WidgetDao
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.PageEntity
import com.thor.core.database.model.PlacementEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CellSpan
import com.thor.core.model.FolderEntry
import com.thor.core.model.FolderIcons
import com.thor.core.model.GridFootprint
import com.thor.core.model.GridPage
import com.thor.core.model.GridPlacement
import com.thor.core.model.GridSlot
import com.thor.core.model.PlatformFolders
import com.thor.core.model.GridSpec
import com.thor.core.model.SmartQuery
import com.thor.core.model.WidgetEntry
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
 * What happened to a move.
 *
 * Three outcomes rather than a nullable displaced id, because "nothing was
 * displaced" and "the move did not happen" are different things and used to
 * arrive as the same `null` — which is why a widget dropped somewhere it did
 * not fit put the cursor down and looked as though it had worked.
 */
sealed interface MoveResult {
    /** The entry is in its new cell and nothing else moved. */
    data object Moved : MoveResult

    /** The entry is in its new cell; [entryId] was turned out and is now unplaced. */
    data class Displaced(val entryId: String) : MoveResult

    /** Nothing changed: off the page, or too much in the way. */
    data object Blocked : MoveResult
}

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
    /**
     * Widget sizes, because a widget occupies cells its placement does not name.
     *
     * The DAO rather than `WidgetRepository`: that one already needs this class
     * to give a new widget a cell, and repositories that need each other in both
     * directions do not construct.
     */
    private val widgetDao: WidgetDao,
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
            //
            // Expanded through the footprint rather than read straight off the
            // placements: a widget stores one cell and stands on up to sixteen,
            // and filling this from the stored cell alone is how a scan puts
            // three hundred games underneath the clock.
            val spans = spans()
            val occupied = existing
                .filterNot { it.isDock || it.parentFolderId != null }
                .map(PlacementEntity::toDomain)
                .groupBy(GridPlacement::pageIndex)
                .mapValues { (pageIndex, onPage) ->
                    GridFootprint.occupants(onPage, spans, pageIndex, spec).keys.toMutableSet()
                }
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
     * A move onto an occupied cell hands the occupant back rather than swapping
     * the two. Swapping sends the occupant to the cell the dragged icon came
     * from, which is a position the user never chose — on a hand-arranged grid
     * one deliberate move silently relocates a second icon across the page.
     * Handing it back lets the caller keep it held so the user places it.
     *
     * Displacement is only ever offered between single cells. A widget covers
     * several, so a move involving one can uncover several occupants at once,
     * and "here, now place these four" is not something a held cursor can
     * express — those moves are refused instead. See [MoveResult.Blocked].
     */
    suspend fun moveEntry(
        entryId: String,
        pageIndex: Int,
        row: Int,
        column: Int,
    ): MoveResult = withContext(defaultDispatcher) {
        val spec = settings.grid.first()
        val spans = spans()
        val span = spans[entryId] ?: CellSpan.SINGLE
        val source = gridDao.getPlacement(entryId) ?: return@withContext MoveResult.Blocked
        val onPage = pagePlacements(pageIndex)

        /*
         * A clear landing first, and separately from the displacement chain.
         *
         * These were one condition, and the `span.isSingle` in it was applied
         * whether or not anything was actually in the way — so a widget dropped
         * onto entirely empty cells was refused for being a widget. Since every
         * widget has a span greater than one by definition, that meant *no widget
         * could ever be moved anywhere*, and the launcher said "that does not fit
         * here" while pointing at an empty half of the page.
         *
         * The span only ever mattered to the *displacement* case below, where a
         * multi-cell entry has no sensible hand-back. It has no bearing on landing
         * somewhere nothing is standing.
         *
         * Covering rather than anchoring, for the same reason placement does; see
         * [GridFootprint.anchorCovering]. Without it a three-wide widget still
         * could not be dropped anywhere in the last two columns.
         */
        GridFootprint.anchorCovering(
            row = row,
            column = column,
            span = span,
            placements = onPage,
            spans = spans,
            pageIndex = pageIndex,
            spec = spec,
            ignoring = entryId,
        )?.let { clear ->
            ensurePageExists(pageIndex)
            gridDao.upsert(
                source.copy(
                    pageIndex = pageIndex,
                    row = clear.row,
                    column = clear.column,
                    parentFolderId = null,
                ),
            )
            return@withContext MoveResult.Moved
        }

        // Nothing is clear, so the only move left is onto something — which the
        // held cursor can carry on from, but only when both sides are one cell.
        // A widget in the way, or several entries under one drop, has no
        // hand-back and is left alone.
        if (!span.isSingle) return@withContext MoveResult.Blocked
        if (!GridFootprint.fits(row, column, span, spec)) return@withContext MoveResult.Blocked

        val occupants = GridFootprint.occupants(onPage, spans, pageIndex, spec)
        val blockers = GridFootprint.cells(row, column, span, spec)
            .mapNotNull(occupants::get)
            .filterNot { it == entryId }
            .distinct()

        val displaceable = blockers.size == 1 &&
            blockers.all { (spans[it] ?: CellSpan.SINGLE).isSingle }
        if (!displaceable) return@withContext MoveResult.Blocked

        ensurePageExists(pageIndex)
        gridDao.upsert(
            source.copy(pageIndex = pageIndex, row = row, column = column, parentFolderId = null),
        )

        // Exactly one, guaranteed by [displaceable] — the clear-landing branch
        // above already returned for the case where there was nothing here.
        val displaced = blockers.first()
        // Removed rather than parked somewhere: the caller picks it straight back
        // up, and leaving it on the grid meanwhile would put two icons in one
        // cell for as long as the user took to decide.
        gridDao.deleteByEntryId(displaced)
        MoveResult.Displaced(displaced)
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
        val spans = spans()

        // The entry's own placement does not count as an occupant, so returning
        // an icon to the cell it was picked up from — which it still nominally
        // holds until it is dropped — succeeds rather than being refused.
        val free = GridFootprint.isFree(
            row = row,
            column = column,
            span = spans[entryId] ?: CellSpan.SINGLE,
            placements = pagePlacements(pageIndex),
            spans = spans,
            pageIndex = pageIndex,
            spec = spec,
            ignoring = entryId,
        )
        if (!free) return@withContext false

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
     * Places an entry so that its footprint covers ([row], [column]).
     *
     * The placement call for anything that can be bigger than one cell. A widget
     * chosen from a cell menu should land on the cell the menu was raised from,
     * and [placeEntryAt] can only hang it from that cell's top-left corner — which
     * a widget wider or taller than the space remaining can never do. See
     * [GridFootprint.anchorCovering] for how often that is, and why it made adding
     * a widget look broken.
     *
     * @return false only when no position on this page covers that cell, which
     *   means the page genuinely has no room rather than the corner being wrong.
     */
    suspend fun placeEntryCovering(
        entryId: String,
        pageIndex: Int,
        row: Int,
        column: Int,
    ): Boolean = withContext(defaultDispatcher) {
        val spec = settings.grid.first()
        val spans = spans()

        val slot = GridFootprint.anchorCovering(
            row = row,
            column = column,
            span = spans[entryId] ?: CellSpan.SINGLE,
            placements = pagePlacements(pageIndex),
            spans = spans,
            pageIndex = pageIndex,
            spec = spec,
            // As in [placeEntryAt]: an entry is never an obstacle to itself.
            ignoring = entryId,
        ) ?: return@withContext false

        ensurePageExists(pageIndex)
        gridDao.upsert(
            PlacementEntity(
                entryId = entryId,
                pageIndex = pageIndex,
                row = slot.row,
                column = slot.column,
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

    /**
     * Clears every game off the grid in one go, leaving apps and folders alone.
     *
     * The library itself is untouched — the games stay scanned, stay searchable
     * and stay inside their platform folders. Only the cells go, which is the
     * difference between clearing the grid and losing the library, and the reason
     * this is a placement operation rather than a delete.
     *
     * Folders are deliberately spared. A platform folder holds its games by id
     * rather than by placement, so removing the folder's own cell would strand
     * every game inside it with no way back to the grid.
     *
     * @return how many cells were cleared, so the caller can say so rather than
     *   leaving the user to guess whether anything happened.
     */
    suspend fun clearGamePlacements(): Int = withContext(defaultDispatcher) {
        val games = gridDao.observeAllPlacements().first().filter { placement ->
            // `game:<platform>:<title>` — the id form scanned games are given.
            !placement.isDock && placement.entryId.startsWith(GAME_ID_PREFIX)
        }

        games.forEach { placement -> gridDao.deleteByEntryId(placement.entryId) }

        // Clearing hundreds of cells is the case that leaves the most pages
        // behind, so it is the last thing this does rather than something the
        // next scan gets around to.
        pruneEmptyPages()
        games.size
    }

    /**
     * Drops empty pages off the end of the grid.
     *
     * Pages are created on demand as entries are placed, and nothing ever took
     * them away again — so a library that once held four hundred games left
     * behind the dozen pages it had spread across, every one of them blank, and
     * the only way past them was to keep pressing. Deleting the games is
     * supposed to leave the grid empty, not leave a grid of emptiness.
     *
     * **Only from the end, and never page one.** A blank page in the middle is
     * as likely to be deliberate — a gap someone left between two groups of
     * icons — and removing it would renumber everything after it, which moves
     * arrangements the user made on purpose. Trailing blanks are the ones that
     * are unambiguously nothing.
     *
     * A page carrying a title or a wallpaper is kept whatever else is true of
     * it: those are things the user set, and an empty page they named is a page
     * they meant.
     *
     * @return how many were removed.
     */
    suspend fun pruneEmptyPages(): Int = withContext(defaultDispatcher) {
        val pages = gridDao.getPages().sortedBy(PageEntity::pageIndex)
        if (pages.size <= 1) return@withContext 0

        // Folder contents and dock slots do not hold a page open: neither is
        // drawn on one.
        val occupied = gridDao.getAllPlacements()
            .filterNot { it.isDock || it.parentFolderId != null }
            .mapTo(mutableSetOf(), PlacementEntity::pageIndex)

        val removable = pages
            .asReversed()
            .takeWhile { page ->
                page.pageIndex > 0 &&
                    page.pageIndex !in occupied &&
                    page.title.isBlank() &&
                    page.wallpaperUri.isNullOrBlank()
            }

        removable.forEach { gridDao.deletePage(it.id) }
        removable.size
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

            /*
             * No games at all means an empty platform: no folder is created for
             * it, because an empty folder on the grid is clutter of a different
             * shape.
             *
             * The test is "has this system any games", not "has it any *unplaced*
             * games". Those differ exactly when a system has games that are all
             * already placed and yet has no folder — which should be impossible
             * and was reachable: removing a system stranded its games' placements,
             * so re-adding it found every rescanned game already placed, nothing
             * fresh, and built no folder. The stranding is fixed at its source,
             * and this makes a library that already suffered it heal on the next
             * scan rather than needing the system removed and added again.
             */
            if (gameIds.isEmpty() && existing == null) continue

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

    /**
     * Every smart folder, as the editor lists them.
     *
     * Observed rather than fetched: editing a query rewrites the folder, and the
     * page showing it has to follow that without being told twice.
     */
    val smartFolders: Flow<List<FolderEntry>> =
        folderDao.observeAll().map { folders ->
            folders.map(FolderEntity::toDomain).filter(FolderEntry::isSmart)
        }

    /**
     * Replaces a smart folder's query.
     *
     * The contents are not stored, so there is nothing to recompute here — the
     * folder is its query, and changing it changes what is inside on the next
     * read. That is the whole appeal of a smart folder and the reason this is a
     * single column write rather than a re-filing pass.
     */
    suspend fun updateSmartQuery(folderId: String, query: SmartQuery) =
        withContext(defaultDispatcher) {
            val folder = folderDao.getById(folderId) ?: return@withContext
            folderDao.upsert(folder.copy(smartQuery = query))
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
            val placements = packInOrder(orderedEntryIds, spec, spans())
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
        val ordered = gridDao.getAllPlacements()
            .filter { !it.isDock && it.parentFolderId == null }
            .sortedWith(
                compareBy<PlacementEntity> { it.pageIndex }
                    .thenBy { it.row }
                    .thenBy { it.column },
            )
            .map(PlacementEntity::entryId)

        val reflowed = packInOrder(ordered, spec, spans())
        val pageCount = (reflowed.maxOfOrNull(PlacementEntity::pageIndex) ?: 0) + 1
        (0 until pageCount).forEach { ensurePageExists(it) }
        gridDao.upsertAll(reflowed)
    }

    /**
     * Lays a list of entries out in reading order, honouring their footprints.
     *
     * Not `index / cellsPerPage`, which is what this was while everything took
     * one cell. A widget takes several, so filling by index puts the next few
     * icons inside it; each entry is given the first cell where the *whole* of it
     * fits instead, and a widget too wide for what is left of a row moves down
     * rather than being sliced.
     *
     * Order is preserved in the sense that matters — earlier entries are placed
     * first — but a wide widget can be passed over by a later single-cell icon
     * that fits in the gap it could not. The alternative is leaving holes, which
     * on a sorted grid reads as entries having gone missing.
     */
    private fun packInOrder(
        orderedEntryIds: List<String>,
        spec: GridSpec,
        spans: Map<String, CellSpan>,
    ): List<PlacementEntity> {
        val placed = mutableListOf<GridPlacement>()
        val byPage = mutableMapOf<Int, MutableList<GridPlacement>>()

        orderedEntryIds.forEach { entryId ->
            val span = (spans[entryId] ?: CellSpan.SINGLE).coercedTo(spec)
            var pageIndex = 0
            while (true) {
                val onPage = byPage.getOrPut(pageIndex) { mutableListOf() }
                val cell = GridFootprint.firstFreeCell(span, onPage, spans, pageIndex, spec)
                if (cell != null) {
                    val placement =
                        GridPlacement.fromCellIndex(entryId, pageIndex, cell, spec.columns)
                    onPage += placement
                    placed += placement
                    break
                }
                pageIndex++
            }
        }
        return placed.map { it.toEntity() }
    }

    /**
     * Where something of [span] can go, adding a page if no existing one has room.
     *
     * Used to land a widget the user has just chosen. Unlike an icon it cannot
     * simply take the next free cell — a 2×2 needs four adjacent ones, and on a
     * full page there may be four free cells and nowhere to put it.
     */
    suspend fun firstFreeCellFor(
        span: CellSpan,
        ignoring: String? = null,
    ): GridSlot = withContext(defaultDispatcher) {
        val spec = settings.grid.first()
        val wanted = span.coercedTo(spec)
        val spans = spans()
        val pageCount = (gridDao.getPages().maxOfOrNull(PageEntity::pageIndex) ?: 0) + 1

        for (pageIndex in 0 until pageCount) {
            val cell = GridFootprint.firstFreeCell(
                span = wanted,
                placements = pagePlacements(pageIndex),
                spans = spans,
                pageIndex = pageIndex,
                spec = spec,
                ignoring = ignoring,
            ) ?: continue
            return@withContext GridSlot(
                pageIndex = pageIndex,
                row = cell / spec.columns,
                column = cell % spec.columns,
            )
        }

        // Nothing had room, so it goes at the top of a page of its own.
        ensurePageExists(pageCount)
        GridSlot(pageIndex = pageCount, row = 0, column = 0)
    }

    /** Page placements as domain objects; dock slots and folder contents excluded. */
    private suspend fun pagePlacements(pageIndex: Int): List<GridPlacement> =
        gridDao.getAllPlacements()
            .filter { !it.isDock && it.parentFolderId == null && it.pageIndex == pageIndex }
            .map(PlacementEntity::toDomain)

    /**
     * How many cells each widget takes, keyed by grid entry id.
     *
     * Read fresh each time rather than cached: these change when the user resizes
     * one, and a stale map is a layout that places icons under a widget that has
     * just grown.
     */
    private suspend fun spans(): Map<String, CellSpan> =
        widgetDao.all().associate { widget ->
            WidgetEntry.idFor(widget.appWidgetId) to
                CellSpan(columns = widget.spanColumns, rows = widget.spanRows)
        }

    private suspend fun ensurePageExists(pageIndex: Int) {
        val pages = gridDao.getPages()
        if (pages.any { it.pageIndex == pageIndex }) return
        gridDao.upsertPage(
            PageEntity(id = "page:$pageIndex", pageIndex = pageIndex),
        )
    }

    private fun PlacementEntity.cellIndex(columns: Int): Int = row * columns + column

    private companion object {
        /**
         * How a scanned game's id begins: `game:<platform>:<normalised-title>`.
         *
         * Matched on the id rather than by joining the games table, because a
         * placement deliberately carries no foreign key — it can point at an app,
         * a game, a folder or a shortcut — and the id is the only thing that says
         * which without a second query per cell.
         */
        const val GAME_ID_PREFIX = "game:"
    }
}
