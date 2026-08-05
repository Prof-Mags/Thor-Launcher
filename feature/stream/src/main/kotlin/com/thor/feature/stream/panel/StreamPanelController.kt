package com.thor.feature.stream.panel
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thor.core.model.KeyboardKey
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.NavDirection
import com.thor.core.ui.feedback.FeedbackCue
import com.thor.core.model.ThorKeyboardLayout
import com.thor.data.stream.StreamPad
import kotlin.math.abs

/** What the bottom panel is showing. */
enum class PanelMode { PAD, SETTINGS }

/** Past halfway is a push; the hat only ever reports -1, 0 or 1 anyway. */
private const val THRESHOLD = 0.5f

/** More than a field this size shows; the tail is the part being read. */
private const val ECHO_LIMIT = 120

/**
 * The state the second screen and the streaming window share.
 *
 * One object because the two are in different windows on different displays and
 * have to agree about three things: which of them owns the controller, what the
 * panel is showing, and where the keyboard cursor is. Held by the activity and
 * read by the panel's composition.
 *
 * Compose state rather than flows: every field is read during composition and
 * written from the input thread, which is exactly what `mutableStateOf` handles.
 */
class StreamPanelController(
    private val pad: StreamPad,
    /**
     * Fires the launcher's haptic and sound for one cue.
     *
     * Passed in rather than built here because feedback obeys the user's
     * settings, and this class has none — it is created by the streaming
     * activity, which does.
     */
    private val onFeedback: (FeedbackCue) -> Unit = {},
) {

    var mode by mutableStateOf(PanelMode.PAD)
        private set

    /**
     * Whether the controller drives this panel instead of the game.
     *
     * The whole pad follows focus — there is no splitting a button between two
     * windows — so this is a deliberate handover, made by touching the panel and
     * undone by B. While it is true the game receives nothing, which is correct:
     * the user is typing.
     */
    var keyboardFocused by mutableStateOf(false)
        private set

    var layer by mutableStateOf(KeyboardLayer.LETTERS)
        private set

    var shifted by mutableStateOf(false)
        private set

    /** The direction last acted on, so a held push is one step rather than many. */
    private var lastDirection: NavDirection? = null

    var cursorRow by mutableStateOf(0)
        private set

    var cursorColumn by mutableStateOf(0)
        private set

    /** A local echo of what has been typed; nothing comes back from the PC. */
    var typed by mutableStateOf("")
        private set

    fun takeKeyboard() {
        keyboardFocused = true
    }

    fun releaseKeyboard() {
        keyboardFocused = false
    }

    fun toggleSettings() {
        mode = if (mode == PanelMode.SETTINGS) PanelMode.PAD else PanelMode.SETTINGS
    }

    fun showPad() {
        mode = PanelMode.PAD
    }

    /**
     * Applies one key of the on-screen keyboard, whether pressed or clicked.
     *
     * The same path for both so a finger and the controller cannot diverge —
     * they are two ways of choosing a key, not two keyboards.
     */
    fun press(key: KeyboardKey) {
        /*
         * Fired here rather than at each call site, which is the point of
         * routing touch and the controller through one method: a key pressed by
         * finger and the same key chosen with A produce identical feedback, and
         * neither can be forgotten when the other is changed.
         *
         * The cue matches what the launcher's own keyboard uses for the same
         * key, so typing feels the same wherever it happens.
         */
        onFeedback(key.toCue())

        when (key) {
            KeyboardKey.Shift -> shifted = !shifted

            KeyboardKey.Layer -> {
                layer = if (layer == KeyboardLayer.LETTERS) {
                    KeyboardLayer.SYMBOLS
                } else {
                    KeyboardLayer.LETTERS
                }
                // The layers are different shapes, so a cursor left where it was
                // can be past the end of its new row.
                clampCursor()
            }

            KeyboardKey.Enter -> {
                pad.sendKey(key, shifted)
                typed = ""
            }

            KeyboardKey.Backspace -> {
                pad.sendKey(key, shifted)
                typed = typed.dropLast(1)
            }

            // Cancel is the keyboard's own way out, and here that means handing
            // the controller back rather than dismissing anything.
            KeyboardKey.Cancel -> releaseKeyboard()

            else -> {
                /*
                 * Trimmed to what the field can show.
                 *
                 * This is a local echo that only Enter clears, so a session spent
                 * typing into a PC without ever pressing it grew the string
                 * without limit — and every keystroke re-laid-out the whole of
                 * it, so the keyboard got slower the longer it was used. Only the
                 * tail is visible in any case.
                 */
                typed = (typed + pad.sendKey(key, shifted).orEmpty()).takeLast(ECHO_LIMIT)
                // A latched shift applies to one key, as on every phone keyboard.
                shifted = false
            }
        }
    }

    /**
     * Drives the keyboard from the controller.
     *
     * @return true when the key was used here. Anything else is declined so the
     *   window's own handling — leaving the stream, the quit combination — still
     *   works while the panel holds focus.
     */
    fun handleKey(event: KeyEvent): Boolean {
        if (!keyboardFocused || event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> move(NavDirection.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(NavDirection.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(NavDirection.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(NavDirection.RIGHT)

            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> {
                ThorKeyboardLayout.keyAt(layer, cursorRow, cursorColumn)?.let(::press)
                true
            }

            // B hands the controller back to the game, which is the only way out
            // that does not need a finger.
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                releaseKeyboard()
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                press(KeyboardKey.Backspace)
                true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                press(KeyboardKey.Space)
                true
            }

            else -> false
        }
    }

    /**
     * Drives the keyboard from the D-pad and the left stick.
     *
     * Needed as well as [handleKey], and this is the whole reason the controller
     * appeared not to work here: on a gamepad the D-pad is **not** a key. It
     * arrives as the `HAT_X`/`HAT_Y` axes of a motion event, so a router that
     * only looked at key codes saw nothing at all while the user pressed
     * directions — THOR's own [com.thor.core.input.ControllerInputRouter] reads
     * the same axes for exactly this reason.
     *
     * @return true when the motion was used here, which also keeps it from
     *   reaching the game — correct, since the pad is being used to type.
     */
    fun handleMotion(event: MotionEvent): Boolean {
        if (!keyboardFocused) return false

        /*
         * The stick counts as well as the hat.
         *
         * Whichever the user reaches for should work, and taking the larger
         * deflection of the two means a device that reports only one of them is
         * handled without asking which it is.
         */
        val x = pick(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_X))
        val y = pick(event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.getAxisValue(MotionEvent.AXIS_Y))

        val direction = when {
            x <= -THRESHOLD -> NavDirection.LEFT
            x >= THRESHOLD -> NavDirection.RIGHT
            y <= -THRESHOLD -> NavDirection.UP
            y >= THRESHOLD -> NavDirection.DOWN
            else -> null
        }

        /*
         * One step per push, not one per event.
         *
         * A held direction reports continuously — dozens of events a second —
         * and moving on each would send the cursor across the keyboard instantly.
         * Acting only when the direction *changes* makes a push worth exactly one
         * key, and returning to centre is what re-arms it.
         */
        if (direction != lastDirection) {
            lastDirection = direction
            direction?.let(::move)
        }
        return true
    }

    private fun move(direction: NavDirection): Boolean {
        val next = ThorKeyboardLayout.move(layer, cursorRow, cursorColumn, direction)

        /*
         * Only when the cursor actually moved.
         *
         * Movement clamps at the ends of a row, so holding a direction against
         * the edge would otherwise buzz on every event while nothing changed —
         * which reads as the keyboard being stuck rather than as a limit.
         */
        if (next.row != cursorRow || next.column != cursorColumn) {
            cursorRow = next.row
            cursorColumn = next.column
            onFeedback(FeedbackCue.NAVIGATE)
        }
        return true
    }

    /**
     * The feedback a key produces, matching the launcher's own keyboard.
     *
     * Backspace and Cancel undo, so they take the back cue; Enter completes
     * something, so it takes success; the layer switch is a page turn. Everything
     * else is an ordinary confirm.
     */
    private fun KeyboardKey.toCue(): FeedbackCue = when (this) {
        KeyboardKey.Backspace, KeyboardKey.Cancel -> FeedbackCue.BACK
        KeyboardKey.Enter -> FeedbackCue.SUCCESS
        KeyboardKey.Layer -> FeedbackCue.PAGE
        else -> FeedbackCue.CONFIRM
    }

    /** Whichever axis is pushed further, so hat or stick both work. */
    private fun pick(hat: Float, stick: Float): Float =
        if (abs(hat) >= abs(stick)) hat else stick

    private fun clampCursor() {
        val rows = ThorKeyboardLayout.rows(layer)
        cursorRow = cursorRow.coerceIn(0, rows.lastIndex)
        cursorColumn = cursorColumn.coerceIn(0, rows[cursorRow].lastIndex)
    }
}
