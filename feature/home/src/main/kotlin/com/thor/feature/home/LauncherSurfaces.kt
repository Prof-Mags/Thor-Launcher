package com.thor.feature.home

import androidx.compose.runtime.Immutable
import com.thor.core.model.AppEntry
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.ShortcutAction
import com.thor.core.model.SortOrder

/**
 * State for the sort picker.
 *
 * Kept out of [LauncherUiState] and collected separately: the main state object
 * already combines a dozen sources, and folding two more short-lived overlays
 * into it would mean the whole grid recomposes every time a dialog's highlight
 * moves.
 */
@Immutable
data class SortPickerState(
    val visible: Boolean = false,
    val focusedIndex: Int = 0,
    val order: SortOrder = SortOrder.TITLE,
    val descending: Boolean = false,
) {
    /** The orders the picker offers, excluding the hand-arranged default. */
    val orders: List<SortOrder> = SortOrder.entries.filterNot { it == SortOrder.MANUAL }
}

/**
 * State for the app drawer.
 *
 * The drawer reuses the grid's geometry, so it carries its own page and cursor
 * rather than borrowing the home grid's — otherwise closing the drawer would
 * leave the home cursor wherever the drawer left it.
 */
@Immutable
data class AppDrawerState(
    val visible: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val page: Int = 0,
    val cursor: CursorPosition = CursorPosition(0, 0),
)

/**
 * State for the shortcut panel raised by the AYN button.
 *
 * The tiles are resolved once, on opening, rather than recomputed as state
 * changes: the panel's own actions alter the launcher underneath it, and a list
 * that reshuffled while the cursor sat in it would move the tile out from under
 * the press.
 */
@Immutable
data class ShortcutPanelState(
    val visible: Boolean = false,
    val actions: List<ShortcutAction> = emptyList(),
    val focusedIndex: Int = 0,
)

/**
 * State for THOR's own on-screen keyboard.
 *
 * All of it lives here rather than inside the composable so that the controller and
 * a finger drive one keyboard: a shift latched by the Y button has to show on the
 * key a thumb is about to press, and a cursor moved by the D-pad has to be the same
 * cursor a tap sets.
 */
@Immutable
data class KeyboardState(
    val visible: Boolean = false,
    val text: String = "",
    /** What the text is for, shown above the field. */
    val label: String = "",
    val layer: KeyboardLayer = KeyboardLayer.LETTERS,
    /** Latched for a single character, as a phone keyboard's shift is. */
    val shifted: Boolean = false,
    val cursorRow: Int = 1,
    val cursorColumn: Int = 0,
    /**
     * The clipboard sheet, raised over the keys by the clipboard key.
     *
     * Part of the keyboard's own state rather than a surface of its own: it is
     * drawn inside the keyboard, dismissed with the same Back, and while it is up
     * the keys must not also be typing.
     */
    val clipboardOpen: Boolean = false,
    /** Clips offered by the sheet, most recent first. */
    val clips: List<String> = emptyList(),
    val clipIndex: Int = 0,
)

/**
 * Which physical surface the controller is driving.
 *
 * The launcher owns two windows, and a press has to go to exactly one of them.
 * Tapping a surface claims input for it, which is what lets the user reach the
 * grid while the settings overlay is still open on the other panel.
 */
enum class InputSurface {
    /** The information panel and the overlays it hosts. */
    TOP,

    /** The grid, dock and Start panel. */
    BOTTOM,
}
