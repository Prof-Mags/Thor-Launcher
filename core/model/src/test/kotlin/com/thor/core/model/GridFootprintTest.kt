package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The occupancy rules a widget introduced.
 *
 * Worth testing directly rather than through the repository: every one of these
 * is a silent failure. Getting a footprint wrong does not throw — it reports a
 * covered cell as free, and an icon is placed underneath a widget where the user
 * can never see it again. The symptom is "my games disappeared after I added a
 * clock", several steps from the arithmetic that caused it.
 */
class GridFootprintTest {

    private val spec = GridSpec(columns = 5, rows = 3)

    @Test
    fun `a single cell covers only itself`() {
        assertThat(GridFootprint.cells(1, 2, CellSpan.SINGLE, spec)).containsExactly(7)
    }

    @Test
    fun `a two by two covers the square below and right of its anchor`() {
        // Anchored at row 0, column 1 on a 5-wide page: cells 1,2 and 6,7.
        assertThat(GridFootprint.cells(0, 1, CellSpan(2, 2), spec))
            .containsExactly(1, 2, 6, 7)
    }

    @Test
    fun `a footprint running off the page is clipped rather than wrapped`() {
        // Three wide at column 3 of five: columns 3 and 4 exist, and the third
        // must not appear as column 0 of the next row.
        val cells = GridFootprint.cells(0, 3, CellSpan(columns = 3, rows = 1), spec)
        assertThat(cells).containsExactly(3, 4)
    }

    @Test
    fun `an anchor outside the matrix covers nothing`() {
        assertThat(GridFootprint.cells(3, 0, CellSpan.SINGLE, spec)).isEmpty()
        assertThat(GridFootprint.cells(0, 5, CellSpan.SINGLE, spec)).isEmpty()
    }

    @Test
    fun `fits is about the whole span, not the anchor`() {
        assertThat(GridFootprint.fits(0, 3, CellSpan(2, 2), spec)).isTrue()
        // Column 4 plus two wide runs past column 5.
        assertThat(GridFootprint.fits(0, 4, CellSpan(2, 2), spec)).isFalse()
        // Row 2 plus two tall runs past row 3.
        assertThat(GridFootprint.fits(2, 0, CellSpan(2, 2), spec)).isFalse()
    }

    @Test
    fun `every cell a widget stands on names it as the occupant`() {
        val placements = listOf(
            GridPlacement("widget:7", pageIndex = 0, row = 0, column = 0),
            GridPlacement("game:a", pageIndex = 0, row = 2, column = 4),
        )
        val spans = mapOf("widget:7" to CellSpan(2, 2))

        val occupants = GridFootprint.occupants(placements, spans, pageIndex = 0, spec = spec)

        assertThat(occupants.keys).containsExactly(0, 1, 5, 6, 14)
        assertThat(occupants[6]).isEqualTo("widget:7")
        assertThat(occupants[14]).isEqualTo("game:a")
    }

    @Test
    fun `placements on another page are not occupants of this one`() {
        val placements = listOf(GridPlacement("game:a", pageIndex = 1, row = 0, column = 0))
        assertThat(GridFootprint.occupants(placements, emptyMap(), 0, spec)).isEmpty()
    }

    @Test
    fun `a cell under a widget is not free`() {
        val placements = listOf(GridPlacement("widget:7", 0, row = 0, column = 0))
        val spans = mapOf("widget:7" to CellSpan(2, 2))

        // Cell (1,1) is the bottom-right of the widget, and stores nothing.
        val free = GridFootprint.isFree(1, 1, CellSpan.SINGLE, placements, spans, 0, spec)
        assertThat(free).isFalse()
    }

    @Test
    fun `a widget does not block itself`() {
        val placements = listOf(GridPlacement("widget:7", 0, row = 0, column = 0))
        val spans = mapOf("widget:7" to CellSpan(2, 2))

        // Growing it to 3x2 overlaps the cells it already holds, which is not a
        // collision — without `ignoring` every resize would be refused.
        val grown = GridFootprint.isFree(
            row = 0,
            column = 0,
            span = CellSpan(columns = 3, rows = 2),
            placements = placements,
            spans = spans,
            pageIndex = 0,
            spec = spec,
            ignoring = "widget:7",
        )
        assertThat(grown).isTrue()
    }

    @Test
    fun `the first free cell skips a gap too small for the span`() {
        // A one-cell hole at (0,0) and everything else on row 0 taken: a 2x1
        // widget cannot use the hole and must drop to row 1.
        val placements = (1 until spec.columns).map { column ->
            GridPlacement("game:$column", pageIndex = 0, row = 0, column = column)
        }

        val single = GridFootprint.firstFreeCell(CellSpan.SINGLE, placements, emptyMap(), 0, spec)
        assertThat(single).isEqualTo(0)

        val pair = GridFootprint.firstFreeCell(CellSpan(2, 1), placements, emptyMap(), 0, spec)
        assertThat(pair).isEqualTo(spec.columns)
    }

    @Test
    fun `a full page has no free cell for anything`() {
        val placements = (0 until spec.cellsPerPage).map { cell ->
            GridPlacement.fromCellIndex("game:$cell", 0, cell, spec.columns)
        }
        assertThat(GridFootprint.firstFreeCell(CellSpan.SINGLE, placements, emptyMap(), 0, spec))
            .isNull()
    }

    @Test
    fun `every cell of a widget reports the widget's whole box`() {
        val placements = listOf(GridPlacement("widget:7", 0, row = 0, column = 1))
        val spans = mapOf("widget:7" to CellSpan(2, 2))

        // The anchor, and the far corner of the same widget, agree.
        val fromAnchor = GridFootprint.boxAt(0, 1, placements, spans, 0, spec)
        val fromCorner = GridFootprint.boxAt(1, 2, placements, spans, 0, spec)

        assertThat(fromAnchor).isEqualTo(fromCorner)
        assertThat(fromAnchor.row).isEqualTo(0)
        assertThat(fromAnchor.column).isEqualTo(1)
        assertThat(fromAnchor.lastRow).isEqualTo(1)
        assertThat(fromAnchor.lastColumn).isEqualTo(2)
    }

    @Test
    fun `a cell with nothing on it is its own box`() {
        val box = GridFootprint.boxAt(2, 3, emptyList(), emptyMap(), 0, spec)
        assertThat(box).isEqualTo(CellBox(2, 3, CellSpan.SINGLE))
        assertThat(box.lastRow).isEqualTo(2)
        assertThat(box.lastColumn).isEqualTo(3)
    }

    @Test
    fun `stepping off a widget's edge clears it in one move`() {
        // The rule the cursor uses: leave from lastColumn + 1, not column + 1.
        val placements = listOf(GridPlacement("widget:7", 0, row = 0, column = 0))
        val spans = mapOf("widget:7" to CellSpan(columns = 3, rows = 1))

        val box = GridFootprint.boxAt(0, 0, placements, spans, 0, spec)

        assertThat(box.lastColumn + 1).isEqualTo(3)
        // And nothing inside it is a place the cursor could come to rest.
        (0..2).forEach { column -> assertThat(box.contains(0, column)).isTrue() }
        assertThat(box.contains(0, 3)).isFalse()
    }

    // ---- Covering an asked-for cell ----------------------------------------

    @Test
    fun `a widget asked for at the last column slides left instead of being refused`() {
        /*
         * The bug this is here for. On the default 5x3 grid a three-wide widget
         * can only be *anchored* in columns 0 to 2, so pressing column 4 — which
         * is where the empty cells are, because everything fills from the top
         * left — reported no room on an entirely empty page.
         */
        val slot = GridFootprint.anchorCovering(
            row = 2,
            column = 4,
            span = CellSpan(columns = 3, rows = 1),
            placements = emptyList(),
            spans = emptyMap(),
            pageIndex = 0,
            spec = spec,
        )

        assertThat(slot).isNotNull()
        assertThat(slot!!.column).isEqualTo(2)
        assertThat(slot.row).isEqualTo(2)
        // And having slid, it still covers the cell that was actually pressed.
        assertThat(
            GridFootprint.cells(slot.row, slot.column, CellSpan(3, 1), spec),
        ).contains(2 * spec.columns + 4)
    }

    @Test
    fun `a widget that fits where it was asked for does not move`() {
        // Sliding is a fallback, not a policy: a press that already works has to
        // put the widget exactly there, or every placement drifts left.
        val slot = GridFootprint.anchorCovering(
            row = 0,
            column = 1,
            span = CellSpan(columns = 3, rows = 1),
            placements = emptyList(),
            spans = emptyMap(),
            pageIndex = 0,
            spec = spec,
        )

        assertThat(slot?.row).isEqualTo(0)
        assertThat(slot?.column).isEqualTo(1)
    }

    @Test
    fun `a tall widget asked for on the last row slides up`() {
        // Spotlight is 3x2 on a 3-row grid, so row 2 can never anchor it either.
        val slot = GridFootprint.anchorCovering(
            row = 2,
            column = 4,
            span = CellSpan(columns = 3, rows = 2),
            placements = emptyList(),
            spans = emptyMap(),
            pageIndex = 0,
            spec = spec,
        )

        assertThat(slot?.row).isEqualTo(1)
        assertThat(slot?.column).isEqualTo(2)
    }

    @Test
    fun `it slides past something already standing there`() {
        // The nearest covering anchor is taken, so an occupied one is stepped
        // over rather than the whole press being refused.
        val placements = listOf(GridPlacement("game:a", pageIndex = 0, row = 0, column = 0))

        val slot = GridFootprint.anchorCovering(
            row = 0,
            column = 2,
            span = CellSpan(columns = 3, rows = 1),
            placements = placements,
            spans = emptyMap(),
            pageIndex = 0,
            spec = spec,
        )

        // Columns 0 and 1 as anchors would both cover the occupied cell 0, so the
        // only covering anchor left is column 2.
        assertThat(slot?.column).isEqualTo(2)
    }

    @Test
    fun `a page with no room for it anywhere reports none`() {
        // The honest answer, and the one the caller turns into "it went to the
        // first space" — which should now be rare rather than routine.
        val placements = (0 until spec.columns * spec.rows).map { cell ->
            GridPlacement.fromCellIndex("game:$cell", 0, cell, spec.columns)
        }

        val slot = GridFootprint.anchorCovering(
            row = 1,
            column = 1,
            span = CellSpan(columns = 3, rows = 1),
            placements = placements,
            spans = emptyMap(),
            pageIndex = 0,
            spec = spec,
        )

        assertThat(slot).isNull()
    }

    @Test
    fun `a widget is not an obstacle to itself when it is moved`() {
        // Nudging a placed widget one cell across its own footprint has to work,
        // or a widget can be placed and then never moved again.
        val placements = listOf(GridPlacement("widget:7", pageIndex = 0, row = 0, column = 0))
        val spans = mapOf("widget:7" to CellSpan(columns = 3, rows = 1))

        val slot = GridFootprint.anchorCovering(
            row = 0,
            column = 1,
            span = CellSpan(columns = 3, rows = 1),
            placements = placements,
            spans = spans,
            pageIndex = 0,
            spec = spec,
            ignoring = "widget:7",
        )

        assertThat(slot?.column).isEqualTo(1)
    }

    @Test
    fun `a widget wider than the grid is shrunk rather than refused`() {
        // The span is coerced first, so a 4-wide widget on a 3-wide grid lands at
        // column 0 instead of finding no legal anchor at all.
        val tiny = GridSpec(columns = 3, rows = 2)

        val slot = GridFootprint.anchorCovering(
            row = 1,
            column = 2,
            span = CellSpan(columns = 4, rows = 1),
            placements = emptyList(),
            spans = emptyMap(),
            pageIndex = 0,
            spec = tiny,
        )

        assertThat(slot?.row).isEqualTo(1)
        assertThat(slot?.column).isEqualTo(0)
    }

    @Test
    fun `every cell on an empty page can take every built-in widget`() {
        /*
         * The regression guard for the reported symptom, stated as the property
         * the user actually cares about: on an empty grid, pressing *anywhere*
         * gets you the widget. Before the slide, six of these fifteen worked for
         * a 3x2 and nine for a 3x1.
         */
        LauncherWidget.entries.forEach { widget ->
            for (row in 0 until spec.rows) {
                for (column in 0 until spec.columns) {
                    val slot = GridFootprint.anchorCovering(
                        row = row,
                        column = column,
                        span = widget.span,
                        placements = emptyList(),
                        spans = emptyMap(),
                        pageIndex = 0,
                        spec = spec,
                    )
                    assertThat(slot).isNotNull()
                    // And it lands on the cell that was pressed, every time.
                    assertThat(
                        GridFootprint.cells(slot!!.row, slot.column, widget.span, spec),
                    ).contains(row * spec.columns + column)
                }
            }
        }
    }

    @Test
    fun `a span is shrunk to what the matrix can hold`() {
        val tiny = GridSpec(columns = 3, rows = 2)
        assertThat(CellSpan(columns = 4, rows = 4).coercedTo(tiny))
            .isEqualTo(CellSpan(columns = 3, rows = 2))
        // And never to nothing, whatever it was asked for.
        assertThat(CellSpan(columns = 0, rows = 0).coercedTo(tiny)).isEqualTo(CellSpan.SINGLE)
    }
}
