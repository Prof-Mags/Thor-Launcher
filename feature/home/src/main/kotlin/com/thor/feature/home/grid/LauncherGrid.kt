package com.thor.feature.home.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.PlatformFolders
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
    modifier: Modifier = Modifier,
) {
    val heldId = (state.editMode as? EditMode.Holding)?.entryId
    val cellsPerPage = state.spec.cellsPerPage.coerceAtLeast(1)

    /*
     * A controller move changes only the cursor, but the old `entryAt` path made
     * every visible cell rebuild a map of all placements to find its entry.  Index
     * the stable layout once instead, so movement changes the two focused cells
     * rather than repeatedly scanning the entire library.
     */
    val homeEntries = remember(state.placements, state.entriesById, state.spec.columns) {
        val pages = mutableMapOf<Int, MutableMap<Int, com.thor.core.model.GridEntry>>()
        state.placements.forEach { placement ->
            state.entriesById[placement.entryId]?.let { entry ->
                pages.getOrPut(placement.pageIndex) { mutableMapOf() }[
                    placement.row * state.spec.columns + placement.column
                ] = entry
            }
        }
        pages.mapValues { (_, entries) -> entries.toMap() }
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
                )
            },
            modifier = modifier,
        )
    }
}

/** How many children a folder's 2×2 preview can show. */
private const val FOLDER_PREVIEW_COUNT = 4
