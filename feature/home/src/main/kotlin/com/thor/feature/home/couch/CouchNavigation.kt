package com.thor.feature.home.couch

import com.thor.feature.home.LauncherViewModel

/**
 * Which region of the dashboard the controller is in.
 *
 * The shelf used to be the only one, because it was the only thing on the
 * screen. The dashboard put four more regions up at once, and a television
 * interface where three quarters of what is visible can only be reached by
 * touching the screen is not a television interface — the whole point of the
 * mode is that the device is across the room.
 *
 * Ordered as they sit on the panel, top to bottom, because [CouchNavigation]
 * moves between them with up and down and reads that order directly.
 */
enum class CouchZone {
    /** The selected game: play, and its context menu. */
    SPOTLIGHT,

    /** The counts beside the spotlight, each a jump into a shelf. */
    LIBRARY,

    /** The rail of cards. The default, and where a launch comes from. */
    SHELF,

    /** Search, filters, random, controllers, downloads, power. */
    DASHBOARD,
}

/**
 * Where the controller goes next.
 *
 * Pure, and separate from the view model, for the same reason `ShortcutGrid` and
 * `LauncherFocus` are: movement rules are where off-by-one errors live, they are
 * invisible when wrong — a cursor that stops one short of a row reads as the row
 * being disabled — and they are the one part of this screen that can be checked
 * without a television.
 *
 * Every move is clamped rather than wrapped. Wrapping puts the two ends of a row
 * one press apart, which on a stick that repeats is how you overshoot past the
 * thing you were aiming for and end up back where you started.
 */
object CouchNavigation {

    /** Play and More info. */
    const val SPOTLIGHT_ACTIONS = 2

    /** The five rows of the library panel. */
    const val LIBRARY_ROWS = 5

    /*
     * The library panel's rows, in the order it draws them.
     *
     * Here rather than beside either the panel or the view model because both
     * read them and the cursor's position *is* the index — a row added to the
     * panel without a number added here is a press that lands on the row above.
     */
    const val LIBRARY_ROW_ALL_GAMES = 0
    const val LIBRARY_ROW_FAVOURITES = 1
    const val LIBRARY_ROW_RECENT = 2
    const val LIBRARY_ROW_INSTALLED = 3

    /*
     * Systems, where collections used to be.
     *
     * Collections counted the folders the user had made by hand, which on most
     * setups is none — a row that reads zero and goes nowhere, in the panel that
     * exists to say what is in the library. The shelf is built one rail per
     * system, so the number of systems is both a real figure and the one this
     * panel can actually take you to.
     */
    const val LIBRARY_ROW_PLATFORMS = 4

    /** Search, Filters, Random, Controllers, Downloads, Power. */
    const val DASHBOARD_ACTIONS = 6

    /** What a move produced. */
    sealed interface Move {
        /** Stay in the dashboard, at this position. */
        data class To(val focus: CouchFocus) : Move

        /** Leave the dashboard upward, into the section bar. */
        data object ExitToNavBar : Move
    }

    /**
     * Moves up.
     *
     * The shelf is the middle of the screen and the two things above it are side
     * by side, so leaving the shelf upward has to pick one: the spotlight, which
     * is what the shelf's own cursor is describing. The library sits to its
     * right and is reached from there, which keeps the vertical path a straight
     * line rather than a choice made blind.
     */
    fun up(focus: CouchFocus, railCount: Int): Move = when (focus.zone) {
        CouchZone.SPOTLIGHT, CouchZone.LIBRARY -> Move.ExitToNavBar

        CouchZone.SHELF ->
            if (focus.rail <= 0) {
                Move.To(focus.copy(zone = CouchZone.SPOTLIGHT, action = 0))
            } else {
                Move.To(focus.copy(rail = focus.rail - 1))
            }

        CouchZone.DASHBOARD -> Move.To(focus.copy(zone = CouchZone.SHELF))
    }

    /** Moves down. The dashboard is the floor; there is nothing under it. */
    fun down(focus: CouchFocus, railCount: Int): Move = when (focus.zone) {
        CouchZone.SPOTLIGHT, CouchZone.LIBRARY ->
            Move.To(focus.copy(zone = CouchZone.SHELF))

        CouchZone.SHELF ->
            if (focus.rail >= railCount - 1) {
                Move.To(focus.copy(zone = CouchZone.DASHBOARD, action = 0))
            } else {
                Move.To(focus.copy(rail = focus.rail + 1))
            }

        CouchZone.DASHBOARD -> Move.To(focus)
    }

    /**
     * Moves left or right by [delta].
     *
     * In the library the rows are stacked, so sideways means leaving it: left
     * goes back to the spotlight it sits beside. Everywhere else sideways is
     * movement along a row.
     */
    fun horizontal(focus: CouchFocus, delta: Int, itemCount: Int): Move = when (focus.zone) {
        CouchZone.SPOTLIGHT ->
            if (delta > 0 && focus.action >= SPOTLIGHT_ACTIONS - 1) {
                // Past the last button is the panel next to it.
                Move.To(focus.copy(zone = CouchZone.LIBRARY, action = 0))
            } else {
                Move.To(focus.copy(action = clamp(focus.action + delta, SPOTLIGHT_ACTIONS)))
            }

        CouchZone.LIBRARY ->
            if (delta < 0) {
                Move.To(focus.copy(zone = CouchZone.SPOTLIGHT, action = SPOTLIGHT_ACTIONS - 1))
            } else {
                Move.To(focus)
            }

        CouchZone.SHELF ->
            Move.To(focus.copy(item = clamp(focus.item + delta, itemCount)))

        CouchZone.DASHBOARD ->
            Move.To(focus.copy(action = clamp(focus.action + delta, DASHBOARD_ACTIONS)))
    }

    /**
     * Up and down inside the library panel, which is a column rather than a row.
     *
     * Called instead of [up] and [down] when the cursor is in it, so the same
     * two buttons walk the rows rather than leaving immediately.
     */
    fun verticalInLibrary(focus: CouchFocus, delta: Int): Move {
        val next = focus.action + delta
        return when {
            next < 0 -> Move.ExitToNavBar
            next >= LIBRARY_ROWS -> Move.To(focus.copy(zone = CouchZone.SHELF))
            else -> Move.To(focus.copy(action = next))
        }
    }

    /** How many positions the current zone has, for clamping a restored focus. */
    fun actionCount(zone: CouchZone): Int = when (zone) {
        CouchZone.SPOTLIGHT -> SPOTLIGHT_ACTIONS
        CouchZone.LIBRARY -> LIBRARY_ROWS
        CouchZone.DASHBOARD -> DASHBOARD_ACTIONS
        // The shelf's position is its item, not its action.
        CouchZone.SHELF -> 1
    }

    private fun clamp(value: Int, count: Int): Int =
        if (count <= 0) 0 else value.coerceIn(0, count - 1)
}

/**
 * The shelf a library row points at, or null when it has none.
 *
 * Favourites and Recently played are only built once something is in them, so
 * those rows routinely point at a rail that does not exist — this returns null
 * rather than a sentinel index so both callers have to say what they do about
 * it. The panel dims the row; a press on it returns to the shelf.
 *
 * Every row lands on a shelf, Installed included. It used to open the app
 * drawer, which is where apps live on the handheld; couch mode does not raise
 * that drawer at all — see `LauncherViewModel.openAppDrawer` — and the shelf has
 * carried an Apps rail all along.
 */
internal fun couchLibraryRailIndex(rails: List<CouchRail>, row: Int): Int? {
    val id = when (row) {
        CouchNavigation.LIBRARY_ROW_FAVOURITES -> COUCH_RAIL_FAVOURITES
        CouchNavigation.LIBRARY_ROW_RECENT -> COUCH_RAIL_CONTINUE
        CouchNavigation.LIBRARY_ROW_INSTALLED -> COUCH_RAIL_APPS
        // Both All games and Platforms land on the first per-system rail: the
        // whole game library *is* those rails, one after another, so walking
        // down from the first is walking every system in turn.
        else -> return rails.indexOfFirst { it.id.startsWith(COUCH_RAIL_PLATFORM_PREFIX) }
            .takeIf { it >= 0 }
    }
    return rails.indexOfFirst { it.id == id }.takeIf { it >= 0 }
}

/*
 * Rail identifiers, which several files match on by hand.
 *
 * Named here because the side rail, the library panel, the view model and the
 * deck all look rails up by these strings, and a shelf renamed in one of them is
 * a jump that silently stops working in the others.
 */
internal const val COUCH_RAIL_CONTINUE = "continue"
internal const val COUCH_RAIL_FAVOURITES = "favourites"
internal const val COUCH_RAIL_APPS = "apps"
internal const val COUCH_RAIL_COLLECTIONS = "collections"
internal const val COUCH_RAIL_PLATFORM_PREFIX = "platform:"
