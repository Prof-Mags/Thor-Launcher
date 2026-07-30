package com.thor.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cursor movement over the on-screen keyboard.
 *
 * The rows are ragged — ten across the top, nine in the home row, six along the
 * bottom — so every vertical move has to re-clamp the column. Worth testing rather
 * than eyeballing: an off-by-one here types the wrong letter, which is the kind of
 * bug that is obvious to a user and invisible in a screenshot.
 */
class ThorKeyboardLayoutTest {

    @Test
    fun `both layers share the bottom row`() {
        val letters = ThorKeyboardLayout.rows(KeyboardLayer.LETTERS).last()
        val symbols = ThorKeyboardLayout.rows(KeyboardLayer.SYMBOLS).last()

        assertEquals(letters, symbols)
        assertTrue(letters.contains(KeyboardKey.Enter))
        assertTrue(letters.contains(KeyboardKey.Space))
        assertTrue(letters.contains(KeyboardKey.Cancel))
    }

    @Test
    fun `the letter layer is qwerty with shift and backspace on the last letter row`() {
        val rows = ThorKeyboardLayout.rows(KeyboardLayer.LETTERS)

        assertEquals("qwertyuiop", rows[0].joinToString("") { key -> label(key) })
        assertEquals("asdfghjkl", rows[1].joinToString("") { key -> label(key) })
        assertEquals(KeyboardKey.Shift, rows[2].first())
        assertEquals(KeyboardKey.Backspace, rows[2].last())
        assertEquals("zxcvbnm", rows[2].drop(1).dropLast(1).joinToString("") { key -> label(key) })
    }

    @Test
    fun `the symbol layer leads with the digits`() {
        val rows = ThorKeyboardLayout.rows(KeyboardLayer.SYMBOLS)

        assertEquals("1234567890", rows[0].joinToString("") { key -> label(key) })
    }

    private fun label(key: KeyboardKey): String =
        (key as? KeyboardKey.Character)?.resolve(shifted = false)?.toString().orEmpty()

    @Test
    fun `shift resolves a character's two forms`() {
        val key = KeyboardKey.Character(lower = 'q', upper = 'Q')

        assertEquals('q', key.resolve(shifted = false))
        assertEquals('Q', key.resolve(shifted = true))
    }

    @Test
    fun `horizontal movement clamps inside the row`() {
        val start = ThorKeyboardLayout.move(KeyboardLayer.LETTERS, row = 1, column = 0, NavDirection.LEFT)
        assertEquals(KeyboardCursor(1, 0), start)

        val lastColumn = ThorKeyboardLayout.rows(KeyboardLayer.LETTERS)[1].lastIndex
        val end = ThorKeyboardLayout.move(
            layer = KeyboardLayer.LETTERS,
            row = 1,
            column = lastColumn,
            direction = NavDirection.RIGHT,
        )
        assertEquals(KeyboardCursor(1, lastColumn), end)
    }

    @Test
    fun `vertical movement wraps between the first and last rows`() {
        val rows = ThorKeyboardLayout.rows(KeyboardLayer.LETTERS)

        val up = ThorKeyboardLayout.move(KeyboardLayer.LETTERS, row = 0, column = 0, NavDirection.UP)
        assertEquals(rows.lastIndex, up.row)

        val down = ThorKeyboardLayout.move(
            layer = KeyboardLayer.LETTERS,
            row = rows.lastIndex,
            column = 0,
            direction = NavDirection.DOWN,
        )
        assertEquals(0, down.row)
    }

    @Test
    fun `a vertical move clamps the column to the arriving row`() {
        // Column 9 exists in "qwertyuiop" but not in "asdfghjkl", so moving down
        // from `p` must land on the last key of the shorter row rather than nowhere.
        val shorterRow = ThorKeyboardLayout.rows(KeyboardLayer.LETTERS)[1]
        val moved = ThorKeyboardLayout.move(
            layer = KeyboardLayer.LETTERS,
            row = 0,
            column = 9,
            direction = NavDirection.DOWN,
        )

        assertEquals(1, moved.row)
        assertEquals(shorterRow.lastIndex, moved.column)
        assertNotNull(ThorKeyboardLayout.keyAt(KeyboardLayer.LETTERS, moved.row, moved.column))
    }

    @Test
    fun `an out of bounds cursor is brought back inside`() {
        // State can outlive a layer switch: the symbol row the cursor was on may be
        // shorter than the letter row it came from.
        val moved = ThorKeyboardLayout.move(
            layer = KeyboardLayer.SYMBOLS,
            row = 99,
            column = 99,
            direction = NavDirection.LEFT,
        )

        assertNotNull(ThorKeyboardLayout.keyAt(KeyboardLayer.SYMBOLS, moved.row, moved.column))
    }

    @Test
    fun `keyAt reports nothing outside the layout`() {
        assertNull(ThorKeyboardLayout.keyAt(KeyboardLayer.LETTERS, row = 99, column = 0))
        assertNull(ThorKeyboardLayout.keyAt(KeyboardLayer.LETTERS, row = 0, column = 99))
    }
}
