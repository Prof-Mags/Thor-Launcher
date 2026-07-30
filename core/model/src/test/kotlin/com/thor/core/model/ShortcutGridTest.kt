package com.thor.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shortcut panel's cursor rules.
 *
 * Worth testing rather than eyeballing because the panel's last row is partly
 * empty — nine tiles over a four-column grid — and that is precisely where a clamp
 * is easy to get wrong.
 */
class ShortcutGridTest {

    @Test
    fun `every action is a tile`() {
        assertEquals(ShortcutAction.entries.size, ShortcutGrid.ACTIONS.size)
    }

    @Test
    fun `horizontal movement crosses row boundaries`() {
        val count = ShortcutGrid.ACTIONS.size

        // Last tile of the first row, moving right.
        assertEquals(
            ShortcutGrid.COLUMNS,
            ShortcutGrid.move(index = ShortcutGrid.COLUMNS - 1, delta = 1, count = count),
        )
        // First tile of the second row, moving left.
        assertEquals(
            ShortcutGrid.COLUMNS - 1,
            ShortcutGrid.move(index = ShortcutGrid.COLUMNS, delta = -1, count = count),
        )
    }

    @Test
    fun `movement stops at both ends rather than wrapping`() {
        val count = 10

        assertEquals(0, ShortcutGrid.move(index = 0, delta = -1, count = count))
        assertEquals(0, ShortcutGrid.move(index = 0, delta = -ShortcutGrid.COLUMNS, count = count))
        assertEquals(9, ShortcutGrid.move(index = 9, delta = 1, count = count))
        assertEquals(9, ShortcutGrid.move(index = 9, delta = ShortcutGrid.COLUMNS, count = count))
    }

    @Test
    fun `down from the last full row lands on the final tile`() {
        // Nine tiles: rows of 4, 4 and 1. Pressing Down from the second row's
        // last tile has no cell beneath it, and should land on the ninth rather
        // than refusing to move.
        val count = 9
        val fromIndex = 7

        assertEquals(
            count - 1,
            ShortcutGrid.move(index = fromIndex, delta = ShortcutGrid.COLUMNS, count = count),
        )
    }

    @Test
    fun `an empty panel reports a focus of zero`() {
        assertEquals(0, ShortcutGrid.move(index = 3, delta = 1, count = 0))
    }
}
