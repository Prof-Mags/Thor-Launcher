package com.thor.feature.stream

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import org.junit.Test

/**
 * The television layout's arithmetic, and the words on its cards.
 *
 * The cursor is a single index into a flat list of PCs that is drawn as a grid,
 * so every direction on the pad is arithmetic on that index — and arithmetic
 * that is wrong by one column is a cursor that skips a machine or refuses to
 * reach the last one. None of that throws; it is only visible with a controller
 * in hand, across a room, which is the hardest place in this launcher to notice
 * anything.
 */
class StreamCouchScreenTest {

    // ---- Moving through the wall of PCs --------------------------------------

    @Test
    fun `down moves a full row`() {
        assertThat(streamGridTarget(cursor = 0, count = 6, columns = 3, rows = 1)).isEqualTo(3)
        assertThat(streamGridTarget(cursor = 2, count = 6, columns = 3, rows = 1)).isEqualTo(5)
    }

    @Test
    fun `up moves a full row`() {
        assertThat(streamGridTarget(cursor = 4, count = 6, columns = 3, rows = -1)).isEqualTo(1)
    }

    /**
     * Up from the top row leaves the section, which is what makes the navigation
     * bar reachable: the shell only sees the presses this screen declines.
     */
    @Test
    fun `up from the first row is declined`() {
        assertThat(streamGridTarget(cursor = 1, count = 6, columns = 3, rows = -1)).isNull()
    }

    /**
     * Down from the last row is declined too, and the caller turns that into a
     * move onto the selected PC's buttons.
     */
    @Test
    fun `down from the last row is declined`() {
        assertThat(streamGridTarget(cursor = 4, count = 6, columns = 3, rows = 1)).isNull()
        assertThat(streamGridTarget(cursor = 0, count = 2, columns = 3, rows = 1)).isNull()
    }

    /**
     * A ragged final row is still reachable from every column above it.
     *
     * Four PCs put one card on the second row. Pressing down from the third
     * column has no cell directly beneath it, and refusing there would make the
     * fourth machine reachable from one column only — which from a sofa reads as
     * the cursor being stuck rather than as the grid being ragged.
     */
    @Test
    fun `down onto a short row lands on its last card`() {
        assertThat(streamGridTarget(cursor = 2, count = 4, columns = 3, rows = 1)).isEqualTo(3)
        assertThat(streamGridTarget(cursor = 1, count = 5, columns = 3, rows = 1)).isEqualTo(4)
    }

    /**
     * The tile that adds a PC is walked into, not shortcut to.
     *
     * It is the cell after the last machine, so the wall is one longer than the
     * host list and every direction counts it. That is the whole reason it is a
     * tile: a face button would have had to be taken from something, and the one
     * going spare was Y — which is how an unpaired PC is paired.
     */
    @Test
    fun `the tile that adds a PC sits after the last machine`() {
        // Three machines fill the first row, so the tile starts a second one.
        val cells = 3 + 1

        assertThat(streamGridTarget(cursor = 0, count = cells, columns = 3, rows = 1)).isEqualTo(3)
        assertThat(streamGridTarget(cursor = 2, count = cells, columns = 3, rows = 1)).isEqualTo(3)
        // And down from the tile itself leaves the wall, for the button row.
        assertThat(streamGridTarget(cursor = 3, count = cells, columns = 3, rows = 1)).isNull()
    }

    @Test
    fun `an empty wall goes nowhere`() {
        assertThat(streamGridTarget(cursor = 0, count = 0, columns = 3, rows = 1)).isNull()
        assertThat(streamGridTarget(cursor = 0, count = 0, columns = 3, rows = -1)).isNull()
    }

    // ---- How big a card is ---------------------------------------------------

    /** Two rows on screen is the point of the size; see [couchHostCardHeight]. */
    @Test
    fun `two rows of cards fit the grid they were sized for`() {
        val gridHeight = 420.dp
        val card = couchHostCardHeight(gridHeight)

        assertThat(card.value * 2).isAtMost(gridHeight.value)
    }

    @Test
    fun `a card stays recognisable on a short panel`() {
        assertThat(couchHostCardHeight(120.dp).value).isAtLeast(130f)
    }

    @Test
    fun `a card stops growing on a tall one`() {
        assertThat(couchHostCardHeight(1_400.dp)).isEqualTo(couchHostCardHeight(2_000.dp))
    }

    // ---- The rail and the help page ------------------------------------------

    /**
     * The rail's cursor walks the enum, and the rail draws its rows by hand.
     *
     * Nothing connects the two but this order, so a destination inserted in one
     * place and not the other sends Down past "Add a PC" to whichever page the
     * enum happens to list next — a coupling with no compiler behind it.
     */
    @Test
    fun `the rail's destinations are in the order they are drawn`() {
        assertThat(StreamCouchPage.entries)
            .containsExactly(
                StreamCouchPage.COMPUTERS,
                StreamCouchPage.ADD_HOST,
                StreamCouchPage.HELP,
            )
            .inOrder()
    }

    /**
     * Titles are the list keys on the help page, and a duplicate key is a crash
     * rather than a page that merely looks wrong.
     */
    @Test
    fun `every help section has its own title and something to say`() {
        val titles = STREAM_HELP_SECTIONS.map(StreamHelpSection::title)

        assertThat(titles).isNotEmpty()
        assertThat(titles).containsNoDuplicates()
        assertThat(STREAM_HELP_SECTIONS.filter { it.body.isBlank() }).isEmpty()
    }

    // ---- The controls above the wall -----------------------------------------

    /**
     * The row the cursor walks is the row that is drawn.
     *
     * Both come from this one list, because they used to come from two: the
     * header was written out by hand and the cursor counted something else, and
     * a control the pad can land on but the screen does not draw is a press that
     * does nothing with nothing lit.
     */
    @Test
    fun `the header offers help, and refresh once there is something to refresh`() {
        val empty = StreamUiState()
        val populated = StreamUiState(hosts = listOf(StreamHost(address = "192.168.1.20")))

        assertThat(empty.headerActions).containsExactly(StreamHeaderAction.HELP)
        assertThat(populated.headerActions)
            .containsExactly(StreamHeaderAction.HELP, StreamHeaderAction.REFRESH)
            .inOrder()
    }

    /**
     * The cursor is kept, and clamped, rather than reset.
     *
     * Refresh leaves the row when the last PC is removed. A cursor still holding
     * its index would be pointing past the end of a row with one control on it,
     * and Confirm would find nothing to run while the header sat there lit.
     */
    @Test
    fun `a header cursor left past the end lands on what is still there`() {
        val state = StreamUiState(headerCursor = 1)

        assertThat(state.focusedHeaderAction).isEqualTo(StreamHeaderAction.HELP)
    }

    @Test
    fun `the header cursor picks out the control it is on`() {
        val state = StreamUiState(
            hosts = listOf(StreamHost(address = "192.168.1.20")),
            headerCursor = 1,
        )

        assertThat(state.focusedHeaderAction).isEqualTo(StreamHeaderAction.REFRESH)
    }

    // ---- What a card says ----------------------------------------------------

    @Test
    fun `a paired host that is idle simply reads as online`() {
        val status = HostStatus.Online(name = "LIVING-ROOM-PC", paired = true)

        assertThat(status.couchLabel()).isEqualTo("Online")
    }

    /**
     * The two states that look identical on the network and are not the same
     * thing at all: answering, and answering to *this* device.
     */
    @Test
    fun `an unpaired host says what is missing`() {
        val status = HostStatus.Online(name = "WORK-PC", paired = false)

        assertThat(status.couchLabel()).isEqualTo("Pair needed")
    }

    @Test
    fun `a host mid-session says so rather than looking free`() {
        val status = HostStatus.Online(name = "LAPTOP", paired = true, currentGame = "Desktop")

        assertThat(status.couchLabel()).isEqualTo("In session")
    }

    @Test
    fun `a host that has never answered is not called offline`() {
        assertThat(HostStatus.Unknown.couchLabel()).isEqualTo("Waiting")
        assertThat(HostStatus.Checking.couchLabel()).isEqualTo("Checking")
        assertThat(HostStatus.Offline("No route to host").couchLabel()).isEqualTo("Offline")
    }
}
