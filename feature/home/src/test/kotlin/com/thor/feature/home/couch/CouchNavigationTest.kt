package com.thor.feature.home.couch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Moving around the dashboard with a controller.
 *
 * These rules fail invisibly. A cursor that stops one short of a row reads as
 * the row being disabled, and one that leaves a region a press early reads as
 * the button not working — neither produces an error, and neither is noticeable
 * from across a room except as the interface feeling wrong.
 */
class CouchNavigationTest {

    private fun focus(zone: CouchZone, action: Int = 0, rail: Int = 0, item: Int = 0) =
        CouchFocus(rail = rail, item = item, zone = zone, action = action)

    private fun CouchNavigation.Move.focus(): CouchFocus =
        (this as CouchNavigation.Move.To).focus

    // ---- Vertical path ------------------------------------------------------

    /**
     * The one straight line through the screen, top to bottom:
     * nav bar, spotlight, shelf, dashboard.
     */
    @Test
    fun `down walks from the spotlight to the dashboard`() {
        val spotlight = focus(CouchZone.SPOTLIGHT)

        val shelf = CouchNavigation.down(spotlight, railCount = 1).focus()
        assertThat(shelf.zone).isEqualTo(CouchZone.SHELF)

        val dashboard = CouchNavigation.down(shelf, railCount = 1).focus()
        assertThat(dashboard.zone).isEqualTo(CouchZone.DASHBOARD)
    }

    @Test
    fun `up walks back and then leaves for the section bar`() {
        val dashboard = focus(CouchZone.DASHBOARD)

        val shelf = CouchNavigation.up(dashboard, railCount = 1).focus()
        assertThat(shelf.zone).isEqualTo(CouchZone.SHELF)

        val spotlight = CouchNavigation.up(shelf, railCount = 1).focus()
        assertThat(spotlight.zone).isEqualTo(CouchZone.SPOTLIGHT)

        assertThat(CouchNavigation.up(spotlight, railCount = 1))
            .isInstanceOf(CouchNavigation.Move.ExitToNavBar::class.java)
    }

    /** The shelf's own rails come first; only the last one falls through. */
    @Test
    fun `down inside the shelf changes rail before reaching the dashboard`() {
        val first = focus(CouchZone.SHELF, rail = 0)

        val second = CouchNavigation.down(first, railCount = 3).focus()
        assertThat(second.zone).isEqualTo(CouchZone.SHELF)
        assertThat(second.rail).isEqualTo(1)

        val last = focus(CouchZone.SHELF, rail = 2)
        assertThat(CouchNavigation.down(last, railCount = 3).focus().zone)
            .isEqualTo(CouchZone.DASHBOARD)
    }

    @Test
    fun `the dashboard is the floor`() {
        val dashboard = focus(CouchZone.DASHBOARD, action = 3)

        assertThat(CouchNavigation.down(dashboard, railCount = 2).focus()).isEqualTo(dashboard)
    }

    // ---- Sideways -----------------------------------------------------------

    @Test
    fun `the library sits to the right of the spotlight`() {
        val lastButton = focus(CouchZone.SPOTLIGHT, action = CouchNavigation.SPOTLIGHT_ACTIONS - 1)

        val library = CouchNavigation.horizontal(lastButton, 1, itemCount = 0).focus()
        assertThat(library.zone).isEqualTo(CouchZone.LIBRARY)

        // And back again, onto the button it came from.
        val back = CouchNavigation.horizontal(library, -1, itemCount = 0).focus()
        assertThat(back.zone).isEqualTo(CouchZone.SPOTLIGHT)
        assertThat(back.action).isEqualTo(CouchNavigation.SPOTLIGHT_ACTIONS - 1)
    }

    @Test
    fun `spotlight buttons step before handing over`() {
        val play = focus(CouchZone.SPOTLIGHT, action = 0)

        val more = CouchNavigation.horizontal(play, 1, itemCount = 0).focus()
        assertThat(more.zone).isEqualTo(CouchZone.SPOTLIGHT)
        assertThat(more.action).isEqualTo(1)
    }

    /**
     * Clamped, never wrapped.
     *
     * Wrapping puts the two ends of a row one press apart, and on a stick that
     * repeats that is how you shoot past the tile you were aiming for and end up
     * back at the start.
     */
    @Test
    fun `the dashboard row clamps at both ends`() {
        val first = focus(CouchZone.DASHBOARD, action = 0)
        assertThat(CouchNavigation.horizontal(first, -1, itemCount = 0).focus().action)
            .isEqualTo(0)

        val last = focus(CouchZone.DASHBOARD, action = CouchNavigation.DASHBOARD_ACTIONS - 1)
        assertThat(CouchNavigation.horizontal(last, 1, itemCount = 0).focus().action)
            .isEqualTo(CouchNavigation.DASHBOARD_ACTIONS - 1)
    }

    @Test
    fun `the shelf clamps at both ends of its rail`() {
        val first = focus(CouchZone.SHELF, item = 0)
        assertThat(CouchNavigation.horizontal(first, -1, itemCount = 5).focus().item).isEqualTo(0)

        val last = focus(CouchZone.SHELF, item = 4)
        assertThat(CouchNavigation.horizontal(last, 1, itemCount = 5).focus().item).isEqualTo(4)
    }

    @Test
    fun `an empty rail does not move to a negative item`() {
        val empty = focus(CouchZone.SHELF, item = 0)

        assertThat(CouchNavigation.horizontal(empty, 1, itemCount = 0).focus().item).isEqualTo(0)
    }

    // ---- The library column -------------------------------------------------

    @Test
    fun `the library walks its rows before leaving either end`() {
        val top = focus(CouchZone.LIBRARY, action = 0)

        val second = CouchNavigation.verticalInLibrary(top, 1).focus()
        assertThat(second.zone).isEqualTo(CouchZone.LIBRARY)
        assertThat(second.action).isEqualTo(1)

        assertThat(CouchNavigation.verticalInLibrary(top, -1))
            .isInstanceOf(CouchNavigation.Move.ExitToNavBar::class.java)

        val bottom = focus(CouchZone.LIBRARY, action = CouchNavigation.LIBRARY_ROWS - 1)
        assertThat(CouchNavigation.verticalInLibrary(bottom, 1).focus().zone)
            .isEqualTo(CouchZone.SHELF)
    }

    // ---- The shelf cursor is not lost ---------------------------------------

    /**
     * The spotlight describes whatever the shelf is on, so a trip to a button
     * and back must land on the same card — otherwise pressing Play means
     * "launch something else".
     */
    @Test
    fun `leaving the shelf keeps its place`() {
        val onShelf = focus(CouchZone.SHELF, rail = 2, item = 7)

        val spotlight = CouchNavigation
            .up(focus(CouchZone.SHELF, rail = 0, item = 7), railCount = 3)
            .focus()
        assertThat(spotlight.zone).isEqualTo(CouchZone.SPOTLIGHT)
        assertThat(spotlight.item).isEqualTo(7)

        val dashboard = CouchNavigation.down(onShelf, railCount = 3).focus()
        assertThat(dashboard.rail).isEqualTo(2)
        assertThat(dashboard.item).isEqualTo(7)
    }

    // ---- Which shelf a library row points at --------------------------------

    private fun rails(vararg ids: String) = ids.map { CouchRail(it, it, emptyList()) }

    @Test
    fun `each library row lands on the shelf it is counting`() {
        val deck = rails("continue", "favourites", "platform:snes", "collections")

        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_RECENT)).isEqualTo(0)
        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_FAVOURITES)).isEqualTo(1)
        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_COLLECTIONS)).isEqualTo(3)
    }

    /** All games has no rail of its own; the library *is* the platform rails. */
    @Test
    fun `all games lands on the first system`() {
        val deck = rails("continue", "favourites", "platform:snes", "platform:md")

        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_ALL_GAMES))
            .isEqualTo(2)
    }

    /**
     * A row whose shelf has not been built yet reports nothing.
     *
     * Favourites and Collections only exist once something is in them, so this
     * is the ordinary state of a fresh install rather than an edge case. Both
     * callers have to handle it — the panel dims the row, a press leaves the
     * panel — and neither can if this invents an index.
     */
    @Test
    fun `a row with no shelf behind it reports none`() {
        val deck = rails("platform:snes")

        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_FAVOURITES)).isNull()
        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_RECENT)).isNull()
        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_COLLECTIONS)).isNull()
        assertThat(couchLibraryRailIndex(emptyList(), CouchNavigation.LIBRARY_ROW_ALL_GAMES))
            .isNull()
    }

    /** Apps are the app drawer, not a shelf — even when an Apps rail exists. */
    @Test
    fun `installed is not a shelf`() {
        val deck = rails("continue", "apps", "platform:snes")

        assertThat(couchLibraryRailIndex(deck, CouchNavigation.LIBRARY_ROW_INSTALLED)).isNull()
    }

    /** Every row the panel draws is accounted for, so none can press nothing. */
    @Test
    fun `the row indices cover the panel`() {
        val rows = listOf(
            CouchNavigation.LIBRARY_ROW_ALL_GAMES,
            CouchNavigation.LIBRARY_ROW_FAVOURITES,
            CouchNavigation.LIBRARY_ROW_RECENT,
            CouchNavigation.LIBRARY_ROW_INSTALLED,
            CouchNavigation.LIBRARY_ROW_COLLECTIONS,
        )

        assertThat(rows).containsExactlyElementsIn(0 until CouchNavigation.LIBRARY_ROWS)
        assertThat(rows).hasSize(CouchNavigation.LIBRARY_ROWS)
    }

    @Test
    fun `every zone reports how many positions it has`() {
        assertThat(CouchNavigation.actionCount(CouchZone.SPOTLIGHT))
            .isEqualTo(CouchNavigation.SPOTLIGHT_ACTIONS)
        assertThat(CouchNavigation.actionCount(CouchZone.LIBRARY))
            .isEqualTo(CouchNavigation.LIBRARY_ROWS)
        assertThat(CouchNavigation.actionCount(CouchZone.DASHBOARD))
            .isEqualTo(CouchNavigation.DASHBOARD_ACTIONS)
    }
}
