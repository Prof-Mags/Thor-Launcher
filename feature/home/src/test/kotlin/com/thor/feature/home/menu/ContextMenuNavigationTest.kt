package com.thor.feature.home.menu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Moving the cursor around a grid whose last row is usually incomplete.
 *
 * The menu was a single column, where up and down were simply minus one and plus
 * one. It is two columns now, so down has to move a whole row — and the arithmetic
 * that does it has to cope with a count that is not a multiple of the width,
 * which is the normal case: a game offers eleven actions.
 *
 * The property that matters throughout is that the cursor lands on a tile that
 * exists. An index past the end draws no highlight, so the menu looks closed
 * while still holding the controller.
 */
class ContextMenuNavigationTest {

    /** Eleven actions in two columns: five full rows and one lone tile. */
    private val ragged = 11

    @Test
    fun `down moves a whole row`() {
        assertThat(stepContextMenuRow(index = 0, direction = 1, count = ragged)).isEqualTo(2)
        assertThat(stepContextMenuRow(index = 5, direction = 1, count = ragged)).isEqualTo(7)
    }

    @Test
    fun `up moves a whole row`() {
        assertThat(stepContextMenuRow(index = 6, direction = -1, count = ragged)).isEqualTo(4)
        assertThat(stepContextMenuRow(index = 3, direction = -1, count = ragged)).isEqualTo(1)
    }

    @Test
    fun `down off the bottom returns to the top of the same column`() {
        // 9 is the last tile in the right-hand column; 10 is the lone one on the left.
        assertThat(stepContextMenuRow(index = 9, direction = 1, count = ragged)).isEqualTo(1)
        assertThat(stepContextMenuRow(index = 10, direction = 1, count = ragged)).isEqualTo(0)
    }

    /**
     * The case the ragged row breaks.
     *
     * Going up from the top of the right-hand column cannot land on index 11 —
     * that tile is not drawn — so it lands on 9, the lowest one that column has.
     */
    @Test
    fun `up off the top lands on the lowest tile that column actually has`() {
        assertThat(stepContextMenuRow(index = 1, direction = -1, count = ragged)).isEqualTo(9)
        assertThat(stepContextMenuRow(index = 0, direction = -1, count = ragged)).isEqualTo(10)
    }

    @Test
    fun `every move from every tile lands on a tile that exists`() {
        for (count in 1..24) {
            for (index in 0 until count) {
                for (direction in listOf(-1, 1)) {
                    val landed = stepContextMenuRow(index, direction, count)

                    assertThat(landed).isAtLeast(0)
                    assertThat(landed).isLessThan(count)
                }
            }
        }
    }

    @Test
    fun `a column keeps its column when stepping within the grid`() {
        for (count in 2..24) {
            for (index in 0 until count) {
                for (direction in listOf(-1, 1)) {
                    assertThat(stepContextMenuRow(index, direction, count) % CONTEXT_MENU_COLUMNS)
                        .isEqualTo(index % CONTEXT_MENU_COLUMNS)
                }
            }
        }
    }

    @Test
    fun `an empty menu does not produce an index`() {
        assertThat(stepContextMenuRow(index = 0, direction = 1, count = 0)).isEqualTo(0)
    }
}
