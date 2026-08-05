package com.thor.feature.home.menu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Moving the cursor around a grid filled column by column.
 *
 * The menu was a single column, where up and down were simply minus one and plus
 * one. It is two columns now, filled downwards — the first column top to bottom,
 * then the second — so up and down move inside a column and left and right cross
 * between them.
 *
 * The property that matters throughout is that the cursor lands on a tile that
 * exists. Eleven actions leave the second column one short, so the cell beside
 * the last tile of the first column is not drawn; an index pointing at it draws
 * no highlight, and the menu looks closed while still holding the controller.
 */
class ContextMenuNavigationTest {

    /** Eleven actions in two columns: six down the first, five down the second. */
    private val ragged = 11

    @Test
    fun `the grid is as many rows as the taller column`() {
        assertThat(contextMenuRows(11)).isEqualTo(6)
        assertThat(contextMenuRows(12)).isEqualTo(6)
        assertThat(contextMenuRows(1)).isEqualTo(1)
    }

    /**
     * The arrangement the fill order exists for.
     *
     * Launch, then launch on the top screen, then on the bottom, are written next
     * to each other in the action list. Filling by columns is what keeps them in
     * one column, one under the next, instead of scattering them diagonally.
     */
    @Test
    fun `consecutive actions sit under one another`() {
        val rows = contextMenuRows(ragged)

        // Indices 1 and 2 are the two screen targets; same column, adjacent rows.
        assertThat(1 / rows).isEqualTo(2 / rows)
        assertThat(2 % rows - 1 % rows).isEqualTo(1)
    }

    @Test
    fun `down moves one tile within the column`() {
        assertThat(stepContextMenuRow(index = 0, direction = 1, count = ragged)).isEqualTo(1)
        assertThat(stepContextMenuRow(index = 6, direction = 1, count = ragged)).isEqualTo(7)
    }

    @Test
    fun `up moves one tile within the column`() {
        assertThat(stepContextMenuRow(index = 3, direction = -1, count = ragged)).isEqualTo(2)
        assertThat(stepContextMenuRow(index = 7, direction = -1, count = ragged)).isEqualTo(6)
    }

    @Test
    fun `down off the bottom returns to the top of the same column`() {
        // 5 ends the first column, 10 ends the shorter second one.
        assertThat(stepContextMenuRow(index = 5, direction = 1, count = ragged)).isEqualTo(0)
        assertThat(stepContextMenuRow(index = 10, direction = 1, count = ragged)).isEqualTo(6)
    }

    @Test
    fun `up off the top wraps to the bottom of the same column`() {
        assertThat(stepContextMenuRow(index = 0, direction = -1, count = ragged)).isEqualTo(5)
        assertThat(stepContextMenuRow(index = 6, direction = -1, count = ragged)).isEqualTo(10)
    }

    @Test
    fun `right crosses to the same row of the next column`() {
        assertThat(stepContextMenuColumn(index = 0, direction = 1, count = ragged)).isEqualTo(6)
        assertThat(stepContextMenuColumn(index = 6, direction = 1, count = ragged)).isEqualTo(0)
    }

    /**
     * The cell the ragged column does not have.
     *
     * Index 5 is the last tile of the first column; the tile beside it would be
     * 11, which is past the end. Stepping sideways from there has to stay put
     * rather than land on nothing.
     */
    @Test
    fun `a missing cell is never stepped onto`() {
        assertThat(stepContextMenuColumn(index = 5, direction = 1, count = ragged)).isEqualTo(5)
    }

    @Test
    fun `every move from every tile lands on a tile that exists`() {
        for (count in 1..24) {
            for (index in 0 until count) {
                for (direction in listOf(-1, 1)) {
                    assertThat(stepContextMenuRow(index, direction, count)).isIn(0 until count)
                    assertThat(stepContextMenuColumn(index, direction, count)).isIn(0 until count)
                }
            }
        }
    }

    @Test
    fun `up and down keep the cursor in its column`() {
        for (count in 1..24) {
            val rows = contextMenuRows(count)
            for (index in 0 until count) {
                for (direction in listOf(-1, 1)) {
                    assertThat(stepContextMenuRow(index, direction, count) / rows)
                        .isEqualTo(index / rows)
                }
            }
        }
    }

    @Test
    fun `left and right keep the cursor in its row`() {
        for (count in 1..24) {
            val rows = contextMenuRows(count)
            for (index in 0 until count) {
                for (direction in listOf(-1, 1)) {
                    assertThat(stepContextMenuColumn(index, direction, count) % rows)
                        .isEqualTo(index % rows)
                }
            }
        }
    }

    @Test
    fun `an empty menu does not produce an index`() {
        assertThat(stepContextMenuRow(index = 0, direction = 1, count = 0)).isEqualTo(0)
        assertThat(stepContextMenuColumn(index = 0, direction = 1, count = 0)).isEqualTo(0)
    }
}
