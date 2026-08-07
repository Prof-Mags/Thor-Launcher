package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * How many cells something occupies.
 *
 * Everything on the grid but a widget is one cell, so this exists almost
 * entirely for widgets — but it is expressed as a property of a *placement*
 * rather than of a widget, because that is what the occupancy rules need. A
 * cell is taken or free; what is standing on it does not come into it.
 */
@Serializable
data class CellSpan(val columns: Int = 1, val rows: Int = 1) {

    val cellCount: Int get() = columns * rows

    val isSingle: Boolean get() = columns == 1 && rows == 1

    /** Shrunk to something the matrix can actually hold. */
    fun coercedTo(spec: GridSpec): CellSpan = CellSpan(
        columns = columns.coerceIn(1, spec.columns.coerceAtLeast(1)),
        rows = rows.coerceIn(1, spec.rows.coerceAtLeast(1)),
    )

    companion object {
        val SINGLE = CellSpan()
    }
}

/**
 * A page and a cell on it.
 *
 * [GridPlacement] without the entry, for answering "where does this go" before
 * there is anything to put there — which is the question asked while a widget is
 * still being chosen and may yet be abandoned.
 */
data class GridSlot(val pageIndex: Int, val row: Int, val column: Int)

/**
 * A rectangle of cells, anchored at its top-left.
 *
 * What the cursor actually moves between, once widgets exist: everything else on
 * the grid happens to be a 1×1 box, and treating them all the same way is what
 * keeps navigation one rule rather than a special case per entry type.
 */
data class CellBox(val row: Int, val column: Int, val span: CellSpan) {
    val lastRow: Int get() = row + span.rows - 1
    val lastColumn: Int get() = column + span.columns - 1

    fun contains(row: Int, column: Int): Boolean =
        row in this.row..lastRow && column in this.column..lastColumn
}

/**
 * Which cells a placement covers.
 *
 * A placement stores one cell — its top-left — and that was the whole truth
 * while every entry was one cell wide. A widget breaks it: a 2×2 clock stored
 * at (1,1) is *also* standing on (1,2), (2,1) and (2,2), and nothing in the
 * database says so. Every rule that asks "is this cell free" has to expand the
 * footprint first, or icons are placed underneath widgets and disappear.
 *
 * Kept as pure functions over the spec rather than as methods on [GridPlacement]
 * because the answer depends on the grid's current shape, not on the placement:
 * the same widget covers four cells on a 5×3 page and is clipped to two if the
 * user pinches down to a matrix that cannot hold it.
 */
object GridFootprint {

    /**
     * Cell indices covered by [span] anchored at ([row], [column]).
     *
     * Clipped to the matrix rather than refused. A widget can outlive the
     * geometry it was placed under — the user pinches the grid smaller, and a
     * 3-wide widget on a 3-column page anchored at column 1 now runs off the
     * end — and the honest answer for occupancy is the part still on the page.
     * Refusing outright would report those cells as free and let an icon land
     * under the visible half of the widget.
     */
    fun cells(row: Int, column: Int, span: CellSpan, spec: GridSpec): List<Int> {
        if (row < 0 || column < 0 || row >= spec.rows || column >= spec.columns) return emptyList()
        val lastRow = (row + span.rows - 1).coerceAtMost(spec.rows - 1)
        val lastColumn = (column + span.columns - 1).coerceAtMost(spec.columns - 1)
        return buildList {
            for (r in row..lastRow) {
                for (c in column..lastColumn) {
                    add(r * spec.columns + c)
                }
            }
        }
    }

    fun cells(placement: GridPlacement, span: CellSpan, spec: GridSpec): List<Int> =
        cells(placement.row, placement.column, span, spec)

    /** True when the whole of [span] sits inside the matrix. */
    fun fits(row: Int, column: Int, span: CellSpan, spec: GridSpec): Boolean =
        row >= 0 && column >= 0 &&
            row + span.rows <= spec.rows &&
            column + span.columns <= spec.columns

    /**
     * Every occupied cell on one page, keyed by the entry standing on it.
     *
     * Returns the owner rather than a bare set so a caller can tell "occupied by
     * something else" from "occupied by the thing I am moving" — which is the
     * difference between refusing a move and allowing a widget to be nudged one
     * cell across its own footprint.
     */
    fun occupants(
        placements: Collection<GridPlacement>,
        spans: Map<String, CellSpan>,
        pageIndex: Int,
        spec: GridSpec,
    ): Map<Int, String> = buildMap {
        placements.asSequence()
            .filter { it.pageIndex == pageIndex }
            .forEach { placement ->
                val span = spans[placement.entryId] ?: CellSpan.SINGLE
                cells(placement, span, spec).forEach { cell -> put(cell, placement.entryId) }
            }
    }

    /**
     * Whether [span] can be anchored at ([row], [column]) on [pageIndex].
     *
     * [ignoring] is the entry being moved or resized: its own cells are not an
     * obstacle to itself, or growing a widget by one column would be refused by
     * the column it already covers.
     */
    fun isFree(
        row: Int,
        column: Int,
        span: CellSpan,
        placements: Collection<GridPlacement>,
        spans: Map<String, CellSpan>,
        pageIndex: Int,
        spec: GridSpec,
        ignoring: String? = null,
    ): Boolean {
        if (!fits(row, column, span, spec)) return false
        val occupied = occupants(placements, spans, pageIndex, spec)
        return cells(row, column, span, spec).none { cell ->
            val owner = occupied[cell]
            owner != null && owner != ignoring
        }
    }

    /**
     * The block of cells a cursor at ([row], [column]) is standing on.
     *
     * A widget's whole footprint, or the single cell itself when nothing spans
     * it. This is what lets the cursor treat a widget as one thing: stepping
     * from the *edge* of this box rather than from the cell means a 2×2 clock is
     * crossed in one press instead of two, and the cursor never comes to rest on
     * a cell the widget is covering — which looked like the grid still having
     * cells behind it.
     */
    fun boxAt(
        row: Int,
        column: Int,
        placements: Collection<GridPlacement>,
        spans: Map<String, CellSpan>,
        pageIndex: Int,
        spec: GridSpec,
    ): CellBox {
        val single = CellBox(row, column, CellSpan.SINGLE)
        val cell = row * spec.columns + column
        val owner = occupants(placements, spans, pageIndex, spec)[cell] ?: return single
        val span = spans[owner] ?: return single
        val anchor = placements.firstOrNull {
            it.pageIndex == pageIndex && it.entryId == owner
        } ?: return single
        return CellBox(anchor.row, anchor.column, span.coercedTo(spec))
    }

    /**
     * An anchor for [span] whose footprint still covers ([row], [column]).
     *
     * The cell a user presses is a cell they want the widget *on*, not the corner
     * they want it hung from — and treating it as the corner is why adding a
     * widget almost always failed. [fits] refuses any anchor whose span would run
     * off the edge, so on the default 5×3 grid a three-wide widget can only be
     * anchored in columns 0–2 and a 3×2 one only in rows 0–1: six of fifteen
     * cells. Widgets are added by long-pressing an *empty* cell, and empty cells
     * are the ones at the end of the grid, because everything fills from the top
     * left. So the press was nearly always somewhere a large widget could not be
     * hung, and the launcher said there was no room while looking at a page that
     * was largely free.
     *
     * Sliding left and up is the whole fix. The widget lands under the finger
     * that asked for it, which is what "put it here" means when the thing being
     * placed is bigger than the thing being pointed at.
     *
     * Candidates are ordered by how far they move: the exact anchor first, then
     * the smallest displacement, so a widget that *does* fit where it was asked
     * for never slides. Null when no position on this page covers that cell,
     * which the caller answers by looking elsewhere.
     */
    fun anchorCovering(
        row: Int,
        column: Int,
        span: CellSpan,
        placements: Collection<GridPlacement>,
        spans: Map<String, CellSpan>,
        pageIndex: Int,
        spec: GridSpec,
        ignoring: String? = null,
    ): GridSlot? {
        val wanted = span.coercedTo(spec)
        if (row < 0 || column < 0 || row >= spec.rows || column >= spec.columns) return null

        val occupied = occupants(placements, spans, pageIndex, spec)

        // Every anchor whose footprint would still cover the pressed cell, and
        // which sits inside the matrix. Both ends are clamped, so this is empty
        // only when the span cannot be placed on this page at all.
        val firstRow = (row - wanted.rows + 1).coerceAtLeast(0)
        val lastRow = row.coerceAtMost(spec.rows - wanted.rows)
        val firstColumn = (column - wanted.columns + 1).coerceAtLeast(0)
        val lastColumn = column.coerceAtMost(spec.columns - wanted.columns)

        val candidates = buildList {
            for (r in firstRow..lastRow) {
                for (c in firstColumn..lastColumn) {
                    add(GridSlot(pageIndex, r, c))
                }
            }
        }

        return candidates
            // Least movement wins, so a widget only slides as far as it must.
            // Row displacement breaks a tie ahead of column, which keeps the
            // pressed row as the widget's top edge wherever that is possible.
            .sortedWith(
                compareBy(
                    { (row - it.row) + (column - it.column) },
                    { row - it.row },
                    { column - it.column },
                ),
            )
            .firstOrNull { slot ->
                cells(slot.row, slot.column, wanted, spec).none { cell ->
                    val owner = occupied[cell]
                    owner != null && owner != ignoring
                }
            }
    }

    /**
     * The first cell on [pageIndex] where [span] fits, in reading order.
     *
     * Null when the page cannot hold it, which the caller answers by trying the
     * next page rather than by dropping the widget somewhere it overlaps.
     */
    fun firstFreeCell(
        span: CellSpan,
        placements: Collection<GridPlacement>,
        spans: Map<String, CellSpan>,
        pageIndex: Int,
        spec: GridSpec,
        ignoring: String? = null,
    ): Int? {
        val occupied = occupants(placements, spans, pageIndex, spec)
        for (row in 0 until spec.rows) {
            for (column in 0 until spec.columns) {
                if (!fits(row, column, span, spec)) continue
                val clear = cells(row, column, span, spec).none { cell ->
                    val owner = occupied[cell]
                    owner != null && owner != ignoring
                }
                if (clear) return row * spec.columns + column
            }
        }
        return null
    }
}
