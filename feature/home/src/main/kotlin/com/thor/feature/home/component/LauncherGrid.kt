package com.thor.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
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

    /*
     * An entry displaced by a drop has had its placement removed, so it occupies
     * no cell and would render nowhere — leaving the user holding something
     * invisible while the banner tells them to position it. It is drawn under the
     * cursor instead, which is also where dropping will actually put it.
     */
    val floatingHeld = heldId
        ?.takeIf { id -> state.placements.none { it.entryId == id } }
        ?.let(state.entriesById::get)

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
        onCellTapped = onCellTapped,
        onCellLongPressed = onCellLongPressed,
        onPageChanged = onPageChanged,
        onPinch = onPinch,
        cellAt = { page, row, column ->
            val isCursorCell = page == state.currentPage &&
                row == state.cursor.row &&
                column == state.cursor.column

            // A floating held entry wins the cursor's cell; that cell is empty by
            // definition, since dropping onto an occupied one is what displaced it.
            val entry = floatingHeld?.takeIf { isCursorCell }
                ?: state.entryAt(page, row, column)

            GridCellData(
                entry = entry,
                platform = (entry as? GameEntry)
                    ?.let { game -> state.platformsById[game.platformId] },
                isHeld = entry != null && entry.id == heldId,
                folderPreview = (entry as? FolderEntry)
                    ?.childIds
                    ?.take(FOLDER_PREVIEW_COUNT)
                    ?.map { childId ->
                        (state.entriesById[childId] as? GameEntry)
                            ?.let { child -> child.metadata.artwork.cellImage }
                    }
                    .orEmpty(),
            )
        },
        modifier = modifier,
    )
}

/** How many children a folder's 2×2 preview can show. */
private const val FOLDER_PREVIEW_COUNT = 4
