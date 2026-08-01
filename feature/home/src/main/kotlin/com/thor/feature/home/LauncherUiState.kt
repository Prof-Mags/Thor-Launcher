package com.thor.feature.home

import androidx.compose.runtime.Immutable
import com.thor.core.model.FolderStyle
import com.thor.core.model.GridEntry
import com.thor.core.model.GridPage
import com.thor.core.model.GridPlacement
import com.thor.core.model.GridSpec
import com.thor.core.model.FolderEntry
import com.thor.core.model.Platform

/**
 * What the two screens render.
 *
 * This is deliberately one object rather than a state-per-screen: the top
 * display's detail panel is a pure function of [selection], and the bottom
 * display's grid is a pure function of the rest. Sharing one state holder is
 * what keeps the two panels frame-accurate with each other — there is no
 * message passing between windows to fall behind.
 */
@Immutable
data class LauncherUiState(
    val pages: List<GridPage> = emptyList(),
    val placements: List<GridPlacement> = emptyList(),
    val entriesById: Map<String, GridEntry> = emptyMap(),
    val dockEntryIds: List<String?> = List(5) { null },
    val platformsById: Map<String, Platform> = emptyMap(),
    val spec: GridSpec = GridSpec.DEFAULT,
    val currentPage: Int = 0,
    /** Cell the cursor occupies on [currentPage]; null when nothing is focused. */
    val cursor: CursorPosition = CursorPosition(0, 0),
    /** Entry currently under the cursor, resolved for convenience. */
    val selection: GridEntry? = null,
    val editMode: EditMode = EditMode.None,
    val openFolderId: String? = null,
    val openFolderContents: List<GridEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val scanLabel: String? = null,
    val sideMenuOpen: Boolean = false,
    /** Row highlighted in the Start panel while it holds input. */
    val sideMenuIndex: Int = 0,
    /** Entry whose context menu is open; null when closed. */
    val contextMenuEntry: GridEntry? = null,
    /** Row highlighted inside the context menu. */
    val contextMenuIndex: Int = 0,
    /** Entry being edited; null when the editor is closed. */
    val editingEntry: GridEntry? = null,
    /** True when a second panel is attached and can host a launch. */
    val hasSecondScreen: Boolean = false,
    /** Pages kept composed either side of the current one. */
    val pagePrefetchRadius: Int = 1,
    /** When false, taps on the grid are ignored and only the pad drives it. */
    val touchEnabled: Boolean = true,
    /** How folders render when they have no custom artwork. */
    val folderStyle: FolderStyle = FolderStyle.STACK,
    /** Set when an action failed and the UI should surface it. */
    val transientMessage: String? = null,
) {
    val pageCount: Int get() = maxOf(1, pages.size)

    /** True while the grid is showing a folder's contents instead of a page. */
    val isFolderOpen: Boolean get() = openFolderId != null

    /** The folder the grid is showing, for whatever needs to name it. */
    val openFolder: FolderEntry? get() = openFolderId?.let { entriesById[it] as? FolderEntry }

    /**
     * Pages the grid can turn to.
     *
     * Inside a folder that is the folder's own paging, not the home grid's: a folder
     * holding forty games has to be readable, and it borrows the same cell geometry
     * rather than inventing a second kind of grid.
     */
    val visiblePageCount: Int
        get() = if (isFolderOpen) {
            val cells = spec.cellsPerPage.coerceAtLeast(1)
            maxOf(1, (openFolderContents.size + cells - 1) / cells)
        } else {
            pageCount
        }

    /** Placements on the visible page, indexed by cell for O(1) lookup. */
    fun placementsForPage(pageIndex: Int): Map<Int, GridPlacement> =
        placements.asSequence()
            .filter { it.pageIndex == pageIndex }
            .associateBy { it.row * spec.columns + it.column }

    /**
     * What occupies a cell — the open folder's contents when one is open, and the
     * page's own placements otherwise.
     *
     * A folder's children are laid out in order and packed, because they have no
     * placements of their own: they are a *list* the grid is showing, not an
     * arrangement the user made.
     */
    fun entryAt(pageIndex: Int, row: Int, column: Int): GridEntry? {
        val cell = row * spec.columns + column
        if (isFolderOpen) {
            return openFolderContents.getOrNull(pageIndex * spec.cellsPerPage + cell)
        }
        val placement = placementsForPage(pageIndex)[cell] ?: return null
        return entriesById[placement.entryId]
    }
}

/** Cursor location within the current page. */
@Immutable
data class CursorPosition(val row: Int, val column: Int) {
    fun cellIndex(columns: Int): Int = row * columns + column
}

/** Grid editing state. */
@Immutable
sealed interface EditMode {
    /** Normal browsing. */
    data object None : EditMode

    /** Icons jiggle and can be rearranged, but nothing is held. */
    data object Arranging : EditMode

    /** An icon is held by the cursor and follows it until dropped. */
    data class Holding(
        val entryId: String,
        val originPage: Int,
        val originRow: Int,
        val originColumn: Int,
    ) : EditMode

    val isActive: Boolean get() = this !is None
}

/** One-shot effects the UI performs and then forgets. */
sealed interface LauncherEffect {
    data class ShowMessage(val message: String) : LauncherEffect
    data class LaunchFailed(val reason: String) : LauncherEffect
    data object OpenSettings : LauncherEffect
    data object OpenSearch : LauncherEffect
    /**
     * An app was started.
     *
     * The shell releases its panel-focus lock on this, so the window the app is
     * arriving in wins focus rather than having to take it from a launcher panel the
     * user happened to touch last — which is the touch that started it.
     *
     * @param onSecondaryPanel whether the app was sent to the display the launcher's
     *   *presentation* projects onto. The shell releases its claim only then: an app
     *   arriving on the activity's own display is not competing with the second panel
     *   for focus, and standing that panel down for it left the screen the user was
     *   actually holding visible but unable to answer the controller at all.
     */
    data class Launched(val onSecondaryPanel: Boolean) : LauncherEffect

    /**
     * The app a panel was handed over for never arrived.
     *
     * The exact undo of [Launched], and it has to exist because the two halves of
     * a handover are separable: the launcher gives away the *panel* and the
     * *controller* together, and the watchdog that notices nothing took the panel
     * only ever gave the panel back. What that leaves is the grid returning to
     * the screen, fully drawn and fully animated, having permanently yielded its
     * claim on the pad to an app that does not exist — which is the frozen panel
     * this design keeps re-inventing, arriving this time by way of a launch that
     * failed after `startActivity` had already returned.
     *
     * Home cleared the claim, which is why Home was the only way out.
     */
    data object LaunchAbandoned : LauncherEffect

    data object OpenPowerMenu : LauncherEffect

    /**
     * Asks the shell to open a picker for a platform's artwork.
     *
     * The view model cannot open one itself — a document picker is an activity
     * result, which belongs to the activity — so the choice is made in the shell
     * and handed back through [LauncherViewModel.setPlatformArtwork].
     */
    data class PickPlatformArtwork(
        val platformId: String,
        /** True for the wide backdrop, false for the square icon. */
        val hero: Boolean,
    ) : LauncherEffect
}
