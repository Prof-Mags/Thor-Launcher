package com.thor.feature.home

import androidx.compose.runtime.Immutable
import com.thor.core.model.CellBox
import com.thor.core.model.CellSpan
import com.thor.core.model.FolderEntry
import com.thor.core.model.FolderStyle
import com.thor.core.model.GridEntry
import com.thor.core.model.GridFootprint
import com.thor.core.model.GridPage
import com.thor.core.model.GridPlacement
import com.thor.core.model.GridSpec
import com.thor.core.model.Platform
import com.thor.core.model.WidgetEntry
import com.thor.feature.home.couch.platform
import com.thor.feature.home.shell.icon

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
    /**
     * Optional sections the user has enabled, by extension id.
     *
     * Carried in the launcher's own state because the section bar is drawn from
     * it and the cursor is stepped through it — both on the hot path, and both
     * wrong the moment they disagree with what is actually available.
     */
    val enabledExtensions: Set<String> = emptySet(),
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

    /**
     * How many cells each placed widget covers.
     *
     * Lazy because most states are never asked: this is read when a cell has to
     * be resolved to what stands on it, which happens on a long press rather
     * than on every frame.
     */
    val widgetSpans: Map<String, CellSpan> by lazy {
        entriesById.values
            .filterIsInstance<WidgetEntry>()
            .associate { widget -> widget.id to CellSpan(widget.spanColumns, widget.spanRows) }
    }

    /**
     * The block of cells the cursor is standing on at ([row], [column]).
     *
     * One cell for everything but a widget. A folder is showing a packed list
     * with no placements of its own, so nothing there spans anything and the
     * home grid's placements would be the wrong question to ask.
     */
    fun cursorBox(pageIndex: Int, row: Int, column: Int): CellBox {
        if (isFolderOpen || widgetSpans.isEmpty()) {
            return CellBox(row, column, CellSpan.SINGLE)
        }
        return GridFootprint.boxAt(row, column, placements, widgetSpans, pageIndex, spec)
    }

    /** A cursor position moved onto the anchor of whatever covers it. */
    fun snapCursor(pageIndex: Int, row: Int, column: Int): CursorPosition {
        val box = cursorBox(pageIndex, row, column)
        return CursorPosition(box.row, box.column)
    }

    /**
     * What occupies a cell — the open folder's contents when one is open, and the
     * page's own placements otherwise.
     *
     * A folder's children are laid out in order and packed, because they have no
     * placements of their own: they are a *list* the grid is showing, not an
     * arrangement the user made.
     *
     * Resolved through the footprint, so a widget answers from every cell it
     * stands on rather than only from the one its placement stores.
     */
    fun entryAt(pageIndex: Int, row: Int, column: Int): GridEntry? {
        val cell = row * spec.columns + column
        if (isFolderOpen) {
            return openFolderContents.getOrNull(pageIndex * spec.cellsPerPage + cell)
        }
        val occupant = GridFootprint.occupants(placements, widgetSpans, pageIndex, spec)[cell]
        return occupant?.let(entriesById::get)
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

    /**
     * A widget is being given a new size.
     *
     * Its own mode rather than a flag on [Holding], because the buttons mean
     * something different: while holding, the D-pad moves the cursor and takes
     * the entry with it, and while resizing it grows and shrinks an edge without
     * the cursor going anywhere. One mode doing both would need a modifier
     * button, on a device whose face buttons are already spoken for.
     *
     * [originColumns] and [originRows] are what Back restores, so a resize can be
     * abandoned rather than merely stopped at whatever size it had reached.
     */
    data class Resizing(
        val entryId: String,
        val originColumns: Int,
        val originRows: Int,
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
     * Raise the system's power dialog.
     *
     * An effect rather than a call, because no public intent opens it: the
     * only route is `performGlobalAction` on the accessibility service, which
     * lives in the app module. The shell forwards this to the same place the
     * pointer's own power action goes.
     */
    data object RequestPowerMenu : LauncherEffect

    /**
     * Ask for a recording of the real screen.
     *
     * Only this kind is handed up. It needs a consent dialog and a foreground
     * service to hold the projection, and both live in the app module this one
     * cannot see. Recording the launcher's own panels needs neither and is done in
     * the view model directly.
     */
    data object StartScreenRecording : LauncherEffect

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
    data class PickGameArtwork(
        val gameId: String,
        /** True for the wide backdrop, false for the cover on the cell. */
        val hero: Boolean,
    ) : LauncherEffect

    data class PickPlatformArtwork(
        val platformId: String,
        /** True for the wide backdrop, false for the square icon. */
        val hero: Boolean,
    ) : LauncherEffect

    /**
     * Ask the user to let this launcher bind a widget.
     *
     * `BIND_APPWIDGET` is signature-level, so an ordinary install cannot bind
     * one silently and has to raise the platform's consent dialog. That is an
     * activity result, which belongs to the activity — the view model can only
     * ask for it and wait to be told what happened.
     */
    data object RequestWidgetBind : LauncherEffect

    /**
     * Run the provider's own setup screen before the widget is placed.
     *
     * Also an activity result, and also not optional: a provider that declares a
     * configuration activity and never gets to run it draws an empty box for as
     * long as it stays on the grid.
     */
    data object ConfigureWidget : LauncherEffect
}
