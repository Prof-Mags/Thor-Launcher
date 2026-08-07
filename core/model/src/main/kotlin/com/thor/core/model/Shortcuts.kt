package com.thor.core.model

/**
 * One tile in the shortcut panel.
 *
 * The panel is the surface the AYN Thor's own button raises, so it holds the
 * things a player wants *without leaving what they are doing*: the launcher
 * surfaces that are otherwise two or three presses away, and the system panels
 * that would otherwise mean quitting to Android settings. Anything that needs a
 * privileged permission is deliberately absent — a tile that silently does
 * nothing is worse than no tile.
 */
enum class ShortcutAction(val label: String, val description: String) {

    APPS("Apps", "Open the app drawer"),
    SEARCH("Search", "Search the whole library"),
    // The constant keeps its name: it is an identifier, not a label, and renaming
    // it would rewrite every stored shortcut placement that refers to it.
    THOR_SETTINGS("Loki", "Open Loki's settings"),
    SWAP_SCREENS("Swap screens", "Move the grid to the other panel"),
    COUCH_MODE("Couch mode", "Toggle the controller-first single-screen layout"),
    SCAN_LIBRARY("Rescan", "Look for newly added games"),
    RECORD("Record", "Capture both panels to a video"),

    /**
     * The other kind of recording, and a separate action rather than a mode.
     *
     * These capture different things and cannot be one button with a setting behind
     * it: [RECORD] re-draws both panels into the console mock-up and stops producing
     * frames the moment the launcher is not on screen, while this mirrors the real
     * display and keeps going into a game. Which one someone wants is a decision
     * they make each time, at the moment they press it.
     */
    RECORD_SCREEN("Record screen", "Capture the top screen, including games"),

    /**
     * One frame rather than a video, and filed against the game rather than dumped.
     *
     * Separate from both recordings because it answers a different question. A
     * recording is something you set going and stop; this is a moment you want
     * kept, and the reason it is worth keeping is almost always *which game it
     * was* — so it is stored against the game and shown on that game's panel,
     * rather than landing in a folder of a hundred undated frames.
     *
     * Needs the pointer service, which is the only route to capturing a display
     * the launcher does not own; see `ScreenshotBridge`.
     */
    SCREENSHOT("Screenshot", "Save a frame to the game it came from"),

    WIFI("Wi-Fi", "Open the Wi-Fi panel"),
    BLUETOOTH("Bluetooth", "Open Bluetooth settings"),
    VOLUME("Volume", "Open the volume panel"),
    SYSTEM_SETTINGS("Android", "Open Android settings"),
}

/**
 * Layout and cursor arithmetic for the shortcut panel.
 *
 * Kept out of the view model and out of the composable so the movement rules are
 * testable on their own — the panel is a grid whose last row is usually partly
 * empty, which is exactly where off-by-one cursor bugs live.
 */
object ShortcutGrid {

    /** Tiles per row. Twelve actions fill three tidy rows at this width. */
    const val COLUMNS = 4

    /** The tiles, in the order they are laid out. */
    val ACTIONS: List<ShortcutAction> = ShortcutAction.entries.toList()

    /**
     * Moves the focus by [delta] tiles, clamped to the tiles that exist.
     *
     * Horizontal steps are ±1 and vertical steps are ±[COLUMNS], so the panel
     * navigates as one continuous strip: pressing Right at the end of a row moves
     * to the start of the next rather than stopping dead, and pressing Down from
     * the last full row lands on the final tile instead of nothing. Clamping
     * rather than wrapping keeps the two ends of the list from being one press
     * apart, which makes it easy to overshoot back to the top.
     */
    fun move(index: Int, delta: Int, count: Int): Int {
        if (count <= 0) return 0
        return (index + delta).coerceIn(0, count - 1)
    }
}
