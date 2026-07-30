package com.thor.core.model

/**
 * One key on THOR's own on-screen keyboard.
 *
 * The launcher ships a keyboard because it cannot use the platform's. An IME is
 * drawn by the system on the display that owns the focused window, and the
 * launcher's interactive surface is a `Presentation` on the second panel — so the
 * keyboard either appeared on the wrong screen or, more often, not at all. A
 * keyboard that is simply part of the launcher's own composition has no such
 * problem: it renders wherever the grid renders, in the current theme, and it is
 * driven by the same controller router as everything else.
 */
sealed interface KeyboardKey {

    /** A single character, in its unshifted and shifted forms. */
    data class Character(val lower: Char, val upper: Char) : KeyboardKey {
        fun resolve(shifted: Boolean): Char = if (shifted) upper else lower
    }

    data object Space : KeyboardKey
    data object Backspace : KeyboardKey

    /** Shift, latching for one character. */
    data object Shift : KeyboardKey

    /** Switches between the letter and symbol layers. */
    data object Layer : KeyboardKey

    /** Accepts what has been typed and hands input on. */
    data object Enter : KeyboardKey

    /** Closes the keyboard. */
    data object Cancel : KeyboardKey

    /**
     * Opens the clipboard sheet: paste what has been copied, or copy this field.
     *
     * A sheet rather than a bare paste key. The system exposes one primary clip,
     * but a launcher that has been open a while has seen several go past, and the
     * one wanted is often not the last — a paste key that can only ever insert the
     * most recent clip is the one case where a second press does not help.
     */
    data object Clipboard : KeyboardKey
}

/** Which set of keys is on show. */
enum class KeyboardLayer { LETTERS, SYMBOLS }

/**
 * The key layout and the cursor arithmetic over it.
 *
 * Kept in the domain module, away from the composable, because the rows are ragged
 * — ten digits, nine letters, six function keys — and a cursor over a ragged grid
 * is exactly the kind of thing that is easy to get wrong and easy to test.
 */
object ThorKeyboardLayout {

    private fun row(characters: String): List<KeyboardKey> = characters.map { char ->
        KeyboardKey.Character(lower = char, upper = char.uppercaseChar())
    }

    /**
     * The bottom row, shared by both layers.
     *
     * Laid out as a phone keyboard's is — layer switch, comma, space, full stop,
     * enter — because that is the arrangement every user's thumbs already know.
     * Close is the one addition: a phone keyboard is dismissed by the system's back
     * gesture, and this one has to offer its own way out for a finger.
     */
    private val FUNCTIONS: List<KeyboardKey> = listOf(
        KeyboardKey.Layer,
        KeyboardKey.Clipboard,
        KeyboardKey.Character(lower = ',', upper = ','),
        KeyboardKey.Space,
        KeyboardKey.Character(lower = '.', upper = '.'),
        KeyboardKey.Enter,
        KeyboardKey.Cancel,
    )

    /*
     * The familiar three-row QWERTY, with shift and backspace flanking the bottom
     * letter row. Deliberately no digit row: the layer switch carries the numbers,
     * exactly where a phone keyboard keeps them, and a fourth row of letters-sized
     * keys costs a third of the panel.
     */
    private val LETTER_ROWS: List<List<KeyboardKey>> = listOf(
        row("qwertyuiop"),
        row("asdfghjkl"),
        listOf(KeyboardKey.Shift) + row("zxcvbnm") + KeyboardKey.Backspace,
        FUNCTIONS,
    )

    /*
     * The symbol layer, in the same shape: digits on top, then the punctuation that
     * actually appears in game titles and search terms.
     */
    private val SYMBOL_ROWS: List<List<KeyboardKey>> = listOf(
        row("1234567890"),
        row("@#$%&-+()/"),
        row("*\"':;!?") + KeyboardKey.Backspace,
        FUNCTIONS,
    )

    fun rows(layer: KeyboardLayer): List<List<KeyboardKey>> = when (layer) {
        KeyboardLayer.LETTERS -> LETTER_ROWS
        KeyboardLayer.SYMBOLS -> SYMBOL_ROWS
    }

    /** The key under the cursor, or null if the position is somehow out of bounds. */
    fun keyAt(layer: KeyboardLayer, row: Int, column: Int): KeyboardKey? =
        rows(layer).getOrNull(row)?.getOrNull(column)

    /**
     * Moves the cursor one step.
     *
     * Horizontal movement clamps inside the row and vertical movement wraps, which
     * is the combination that suits a keyboard: running off the end of a row should
     * not silently jump to another letter, but the function row needs to be one
     * press away from the top row rather than four.
     *
     * The column is preserved across a vertical move and clamped to the arriving
     * row, so moving down from `p` lands on `l` rather than on nothing.
     */
    fun move(
        layer: KeyboardLayer,
        row: Int,
        column: Int,
        direction: NavDirection,
    ): KeyboardCursor {
        val rows = rows(layer)
        if (rows.isEmpty()) return KeyboardCursor(0, 0)

        val safeRow = row.coerceIn(0, rows.lastIndex)
        val safeColumn = column.coerceIn(0, rows[safeRow].lastIndex.coerceAtLeast(0))

        return when (direction) {
            NavDirection.LEFT -> KeyboardCursor(
                row = safeRow,
                column = (safeColumn - 1).coerceAtLeast(0),
            )

            NavDirection.RIGHT -> KeyboardCursor(
                row = safeRow,
                column = (safeColumn + 1).coerceAtMost(rows[safeRow].lastIndex),
            )

            NavDirection.UP -> {
                val target = if (safeRow == 0) rows.lastIndex else safeRow - 1
                KeyboardCursor(target, safeColumn.coerceAtMost(rows[target].lastIndex))
            }

            NavDirection.DOWN -> {
                val target = if (safeRow == rows.lastIndex) 0 else safeRow + 1
                KeyboardCursor(target, safeColumn.coerceAtMost(rows[target].lastIndex))
            }
        }
    }
}

/** A position on the keyboard. */
data class KeyboardCursor(val row: Int, val column: Int)
