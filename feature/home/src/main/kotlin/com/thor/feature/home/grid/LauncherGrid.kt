package com.thor.feature.home.grid

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.thor.core.model.CellSpan
import com.thor.core.model.FolderEntry
import com.thor.core.model.GridFootprint
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.PlatformFolders
import com.thor.core.model.WidgetEntry
import com.thor.feature.home.couch.platform
import com.thor.feature.home.EditMode
import com.thor.feature.home.LauncherUiState

/**
 * The home grid.
 *
 * Resolves stored placements into cells and hands the rest to [GridPager], which
 * is the single layout shared with the app drawer.
 */
@Composable
fun LauncherGrid(
    state: LauncherUiState,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPinch: (Float) -> Unit,
    /** Inflates a placed widget; see [WidgetLayer]. */
    createWidgetView: (Context, Int) -> View? = { _, _ -> null },
    /** Tells a provider the size of the box it was given, in dp. */
    onWidgetMeasured: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> Unit = { _, _, _ -> },
    /** Starts a game pressed inside one of the launcher's own widgets. */
    onWidgetLaunch: (GridEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val heldId = (state.editMode as? EditMode.Holding)?.entryId
    val cellsPerPage = state.spec.cellsPerPage.coerceAtLeast(1)

    /*
     * Lifted out of `state` so the overlay lambda below can be reused.
     *
     * A lambda that reads `state` directly captures a different object on every
     * cursor move, which makes it a new lambda, which recomposes the whole cell
     * matrix — undoing the one optimisation this file exists to protect. These
     * are the parts of the state the overlay actually depends on, and none of
     * them changes when the cursor does.
     */
    val spec = state.spec
    val editing = state.editMode.isActive
    val folderOpen = state.isFolderOpen
    val currentPage = state.currentPage
    val cursorState = rememberUpdatedState(state.cursor)

    /*
     * A controller move changes only the cursor, but the old `entryAt` path made
     * every visible cell rebuild a map of all placements to find its entry.  Index
     * the stable layout once instead, so movement changes the two focused cells
     * rather than repeatedly scanning the entire library.
     */
    val homeEntries = remember(state.placements, state.entriesById, state.spec.columns) {
        val pages = mutableMapOf<Int, MutableMap<Int, GridEntry>>()
        state.placements.forEach { placement ->
            state.entriesById[placement.entryId]?.let { entry ->
                // A widget is not a cell. It is drawn over the matrix by
                // [WidgetLayer], across however many cells it spans, and putting
                // it here as well would leave an icon plate and a label showing
                // through underneath it.
                if (entry is WidgetEntry) return@let
                pages.getOrPut(placement.pageIndex) { mutableMapOf() }[
                    placement.row * state.spec.columns + placement.column
                ] = entry
            }
        }
        pages.mapValues { (_, entries) -> entries.toMap() }
    }

    /** The other half of that: what the overlay draws, keyed by page. */
    val widgetsByPage = remember(state.placements, state.entriesById) {
        PlacedWidgets(
            state.placements
                .mapNotNull { placement ->
                    (state.entriesById[placement.entryId] as? WidgetEntry)?.let { widget ->
                        placement.pageIndex to PlacedWidget(
                            entry = widget,
                            row = placement.row,
                            column = placement.column,
                        )
                    }
                }
                .groupBy({ it.first }, { it.second }),
        )
    }

    /*
     * Which cells are standing underneath a widget, per page.
     *
     * The matrix still lays every cell out — a widget is drawn over it, not in
     * it — so without this the cells beneath one go on drawing their empty
     * plates and, when the cursor is there, a second highlight inside the first.
     */
    val coveredByPage = remember(widgetsByPage, state.spec) {
        widgetsByPage.byPage.mapValues { (_, onPage) ->
            onPage.flatMapTo(mutableSetOf()) { placed ->
                GridFootprint.cells(
                    row = placed.row,
                    column = placed.column,
                    span = CellSpan(placed.entry.spanColumns, placed.entry.spanRows),
                    spec = state.spec,
                )
            }
        }
    }

    /*
     * What the launcher's own widgets draw from.
     *
     * Resolved once per library change, and only when one of them is actually
     * placed: four of the five are different questions asked of the same list of
     * games, and on a library of a few thousand that is a sort nobody should pay
     * for on a grid with no widgets on it.
     */
    val widgetData = remember(state.entriesById, state.platformsById, widgetsByPage) {
        if (widgetsByPage.byPage.values.none { page -> page.any { it.entry.isBuiltIn } }) {
            LauncherWidgetData()
        } else {
            val games = state.entriesById.values.filterIsInstance<GameEntry>()
            LauncherWidgetData(
                recent = games
                    .filter { it.stats.hasBeenPlayed }
                    .sortedByDescending { it.stats.lastPlayedEpochMs ?: 0L }
                    .take(WIDGET_GAME_LIMIT),
                favourites = games
                    .filter(GameEntry::isFavorite)
                    .sortedBy(GameEntry::sortTitle)
                    .take(WIDGET_GAME_LIMIT),
                unplayed = games
                    .filterNot { it.stats.hasBeenPlayed }
                    .sortedBy(GameEntry::sortTitle)
                    .take(WIDGET_GAME_LIMIT),
                mostPlayed = games
                    .filter { it.stats.totalPlayMillis > 0L }
                    .sortedByDescending { it.stats.totalPlayMillis }
                    .take(WIDGET_GAME_LIMIT),
                /*
                 * Stable for as long as the library is.
                 *
                 * Picked by hashing the library's size and the day rather than at
                 * random, so the tile does not change every time the grid
                 * recomposes — which is on every cursor move. A suggestion that
                 * flickers past is not one anybody can act on.
                 */
                surprise = games
                    .filterNot { it.stats.hasBeenPlayed }
                    .ifEmpty { games }
                    .let { pool ->
                        pool.getOrNull(
                            (System.currentTimeMillis() / MILLIS_PER_DAY)
                                .toInt()
                                .mod(pool.size.coerceAtLeast(1)),
                        )
                    },
                gameCount = games.size,
                totalPlayMillis = games.sumOf { it.stats.totalPlayMillis },
                platformsById = state.platformsById,
            )
        }
    }

    // Folder preview artwork is also layout data; resolve it once per library
    // snapshot instead of rebuilding four child lookups for every cell on a move.
    val folderPreviews = remember(state.entriesById) {
        state.entriesById.values.filterIsInstance<FolderEntry>().associate { folder ->
            folder.id to folder.childIds
                .take(FOLDER_PREVIEW_COUNT)
                .map { childId ->
                    (state.entriesById[childId] as? GameEntry)
                        ?.metadata
                        ?.artwork
                        ?.cellImage
                }
        }
    }

    /*
     * An entry displaced by a drop has had its placement removed, so it occupies
     * no cell and would render nowhere — leaving the user holding something
     * invisible while the banner tells them to position it. It is drawn under the
     * cursor instead, which is also where dropping will actually put it.
     */
    val floatingHeld = heldId
        ?.takeIf { id -> state.placements.none { it.entryId == id } }
        ?.let(state.entriesById::get)

    // A cursor move creates a new UI state but does not alter any cell's data.
    // Keep a distinct content token so GridPager can skip its page matrix for
    // that common case while still refreshing immediately for a library, folder
    // or edit-mode change.
    val contentVersion = remember(
        state.placements,
        state.entriesById,
        state.platformsById,
        state.openFolderId,
        state.openFolderContents,
        state.editMode,
        // A held entry follows the cursor. Refresh the cell mapper while it is
        // being moved, without turning ordinary cursor navigation into a full
        // grid recomposition.
        floatingHeld?.let { state.currentPage },
        floatingHeld?.let { state.cursor },
        state.spec.columns,
    ) { Any() }

    /*
     * A folder is a different data source, not merely a different page count.
     * Recreate the pager at that boundary so it cannot retain an old page's cell
     * composition while the header and top screen have already switched folders.
     */
    key(state.openFolderId) {
        GridPager(
            spec = state.spec,
            // The folder's paging while one is open; the home grid's otherwise.
            pageCount = state.visiblePageCount,
            currentPage = state.currentPage,
            cursor = state.cursor,
            touchEnabled = state.touchEnabled,
            jiggling = state.editMode.isActive,
            folderStyle = state.folderStyle,
            prefetchRadius = state.pagePrefetchRadius,
            contentVersion = contentVersion,
            onCellTapped = onCellTapped,
            onCellLongPressed = onCellLongPressed,
            onPageChanged = onPageChanged,
            onPinch = onPinch,
            cellAt = { page, row, column ->
                val cell = row * state.spec.columns + column
                val isCursorCell = page == state.currentPage &&
                    row == state.cursor.row &&
                    column == state.cursor.column

                // A floating held entry wins the cursor's cell; that cell is empty by
                // definition, since dropping onto an occupied one is what displaced it.
                val entry = floatingHeld?.takeIf { isCursorCell }
                    ?: if (state.isFolderOpen) {
                        state.openFolderContents.getOrNull(page * cellsPerPage + cell)
                    } else {
                        homeEntries[page]?.get(cell)
                    }

                GridCellData(
                    entry = entry,
                    // A platform folder resolves its platform too, not only a game.
                    // The cell needs it to know it is a *system* rather than a folder
                    // the user made, which decides whether it may be drawn as a
                    // collage of the games inside it.
                    platform = when (entry) {
                        is GameEntry -> state.platformsById[entry.platformId]
                        is FolderEntry -> PlatformFolders.platformIdOf(entry.id)
                            ?.let { platformId -> state.platformsById[platformId] }

                        else -> null
                    },
                    isHeld = entry != null && entry.id == heldId,
                    folderPreview = (entry as? FolderEntry)
                        ?.let { folder -> folderPreviews[folder.id] }
                        .orEmpty(),
                    covered = !state.isFolderOpen &&
                        coveredByPage[page]?.contains(cell) == true,
                )
            },
            pageOverlay = { page, metrics ->
                // Never inside a folder: a folder shows a list of its children,
                // and the widgets belong to the page behind it.
                val onPage = if (folderOpen) emptyList() else widgetsByPage.on(page)
                if (onPage.isNotEmpty()) {
                    WidgetLayer(
                        widgets = onPage,
                        metrics = metrics,
                        spec = spec,
                        // The cursor arrives as a State for the same reason the
                        // cells read theirs from one: a move would otherwise
                        // rebuild this lambda, and with it every cell on every
                        // composed page, to change one ring.
                        cursor = cursorState,
                        onThisPage = page == currentPage,
                        editing = editing,
                        heldId = heldId,
                        createView = createWidgetView,
                        onMeasured = onWidgetMeasured,
                        widgetData = widgetData,
                        onLaunch = onWidgetLaunch,
                        onTapped = onCellTapped,
                        onLongPressed = onCellLongPressed,
                    )
                }
            },
            modifier = modifier,
        )
    }
}

/** How many children a folder's 2×2 preview can show. */
private const val FOLDER_PREVIEW_COUNT = 4

/**
 * The longest list any of the launcher's own widgets can show.
 *
 * A ceiling on the work rather than on the layout — how many actually fit is
 * measured from the widget's own box, and this only stops a library of thousands
 * being sorted in full for a strip that shows five.
 */
private const val WIDGET_GAME_LIMIT = 8

/** What "held steady for the day" is measured in, for the Surprise me widget. */
private const val MILLIS_PER_DAY = 86_400_000L
