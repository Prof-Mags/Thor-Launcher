package com.thor.data.stream

import com.limelight.nvstream.jni.MoonBridge
import com.thor.core.common.log.ThorLog
import com.thor.core.model.KeyboardKey
import com.thor.core.model.SessionQuality
import kotlin.math.roundToInt

/**
 * The mouse and keyboard the second screen provides while streaming.
 *
 * Stateless about gestures — the panel that draws it works those out — and
 * entirely about turning them into protocol events. Kept here rather than in the
 * UI so the sending is in one place with the rest of the stream input, and so
 * the panel can be a plain composable with nothing to mock.
 *
 * A trackpad rather than only touching the video, because a streamed desktop
 * needs precision: a finger is far wider than a scrollbar, and pointing at a
 * shrunken copy of a 1080p screen cannot hit a window's close button. Relative
 * movement lets a small pad address every pixel.
 */
class StreamPad(private var quality: SessionQuality = SessionQuality()) {

    fun updateSettings(quality: SessionQuality) {
        this.quality = quality
    }

    /** Moves the pointer by a finger's travel, scaled by the speed setting. */
    fun move(dx: Float, dy: Float) {
        val scale = quality.trackpadSpeed
        val moveX = (dx * scale).roundToInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        val moveY = (dy * scale).roundToInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        if (moveX.toInt() == 0 && moveY.toInt() == 0) return

        runCatching { MoonBridge.sendMouseMove(moveX, moveY) }
            .onFailure { ThorLog.w(TAG, "Could not move the pointer", it) }
    }

    /** A press and release of one button. */
    fun click(button: Byte = BUTTON_LEFT) {
        runCatching {
            MoonBridge.sendMouseButton(BUTTON_PRESS, button)
            MoonBridge.sendMouseButton(BUTTON_RELEASE, button)
        }.onFailure { ThorLog.w(TAG, "Could not click", it) }
    }

    /** Holds a button down, for dragging a window or selecting text. */
    fun press(button: Byte = BUTTON_LEFT) {
        runCatching { MoonBridge.sendMouseButton(BUTTON_PRESS, button) }
            .onFailure { ThorLog.w(TAG, "Could not press", it) }
    }

    fun release(button: Byte = BUTTON_LEFT) {
        runCatching { MoonBridge.sendMouseButton(BUTTON_RELEASE, button) }
            .onFailure { ThorLog.w(TAG, "Could not release", it) }
    }

    /** Two fingers dragging, in the direction the setting says content should go. */
    fun scroll(dy: Float) {
        val direction = if (quality.naturalScroll) -1f else 1f
        val amount = (dy * direction * SCROLL_SCALE)
            .roundToInt()
            .coerceIn(SHORT_MIN, SHORT_MAX)
            .toShort()
        if (amount.toInt() == 0) return

        runCatching { MoonBridge.sendMouseHighResScroll(amount) }
            .onFailure { ThorLog.w(TAG, "Could not scroll", it) }
    }

    /**
     * Sends one key from THOR's own keyboard to the PC.
     *
     * Characters go as text rather than as key codes, and that is the important
     * part: a key code describes a position on a US keyboard, so sending one for
     * "£" or "é" types whatever happens to live there on the PC's layout.
     * `sendUtf8Text` says what was meant instead of where it was pressed.
     *
     * The keys that are not characters have no text to send and go as codes.
     *
     * @return the text this key adds to the local buffer, or null when it is not
     *   a character — the panel keeps its own copy so the user can see what they
     *   typed, since nothing comes back from the PC.
     */
    fun sendKey(key: KeyboardKey, shifted: Boolean): String? = when (key) {
        is KeyboardKey.Character -> {
            val char = if (shifted) key.upper else key.lower
            type(char)
            char.toString()
        }

        KeyboardKey.Space -> {
            tap(VK_SPACE)
            " "
        }

        KeyboardKey.Backspace -> {
            tap(VK_BACK)
            null
        }

        KeyboardKey.Enter -> {
            tap(VK_RETURN)
            null
        }

        // Shift and the layer switch are the panel's own business: they change
        // what the next key means and never reach the PC.
        else -> null
    }

    /**
     * Types one character, as a key press rather than as text.
     *
     * This was `sendUtf8Text`, on the reasoning that text says what was meant
     * while a key code says only where it was pressed. That reasoning is right
     * and the result did not work: UTF-8 text rides its own ENet channel — 0x06,
     * separate from the keyboard channel — and a host that does not implement it
     * drops the packet in silence. Nothing typed ever appeared, with no error
     * anywhere, because from the client's side the send succeeded.
     *
     * Key codes go on the keyboard channel every GameStream host has spoken
     * since the beginning, which is what Moonlight uses for ordinary typing.
     *
     * The cost is real and worth stating: a virtual key names a **position on a
     * US keyboard**, so this types what that position produces on the PC's
     * layout. Anything outside the mapping below falls back to text, which is
     * correct where it works and no worse than nothing where it does not.
     */
    fun type(char: Char) {
        val mapped = virtualKeyFor(char)
        if (mapped == null) {
            runCatching { MoonBridge.sendUtf8Text(char.toString()) }
                .onFailure { ThorLog.w(TAG, "Could not send text", it) }
            return
        }

        val (code, needsShift) = mapped
        tap(code, if (needsShift) MODIFIER_SHIFT else 0)
    }

    /** Tab, Escape and the arrows, which a desktop needs and the layout has not got. */
    fun tap(virtualKey: Short, modifiers: Byte = 0) {
        runCatching {
            MoonBridge.sendKeyboardInput(virtualKey, KEY_DOWN, modifiers, 0)
            MoonBridge.sendKeyboardInput(virtualKey, KEY_UP, modifiers, 0)
        }.onFailure { ThorLog.w(TAG, "Could not send a key", it) }
    }

    companion object {
        private const val TAG = "Stream"

        /** As `MouseButtonPacket` numbers them. */
        const val BUTTON_PRESS: Byte = 0x07
        const val BUTTON_RELEASE: Byte = 0x08
        const val BUTTON_LEFT: Byte = 0x01
        const val BUTTON_MIDDLE: Byte = 0x02
        const val BUTTON_RIGHT: Byte = 0x03

        /** As `KeyboardPacket` numbers them. */
        const val KEY_DOWN: Byte = 0x03
        const val KEY_UP: Byte = 0x04
        const val MODIFIER_SHIFT: Byte = 0x01
        const val MODIFIER_CTRL: Byte = 0x02
        const val MODIFIER_ALT: Byte = 0x04

        const val VK_SPACE: Short = 0x20

        /**
         * The US layout, because that is what a virtual key code describes.
         *
         * Only the characters THOR's own keyboard can produce are here; the
         * shifted symbols are the ones a US keyboard puts above the digits and on
         * the punctuation keys. `true` means the host must be told Shift is held,
         * which is how a keyboard produces the upper legend of any key.
         */
        private val SHIFTED_SYMBOLS: Map<Char, Short> = mapOf(
            '!' to 0x31, '@' to 0x32, '#' to 0x33, '$' to 0x34, '%' to 0x35,
            '^' to 0x36, '&' to 0x37, '*' to 0x38, '(' to 0x39, ')' to 0x30,
            '_' to 0xBD, '+' to 0xBB, '{' to 0xDB, '}' to 0xDD, '|' to 0xDC,
            ':' to 0xBA, '"' to 0xDE, '<' to 0xBC, '>' to 0xBE, '?' to 0xBF,
            '~' to 0xC0,
        ).mapValues { it.value.toShort() }

        private val PLAIN_SYMBOLS: Map<Char, Short> = mapOf(
            '-' to 0xBD, '=' to 0xBB, '[' to 0xDB, ']' to 0xDD, '\\' to 0xDC,
            ';' to 0xBA, '\'' to 0xDE, ',' to 0xBC, '.' to 0xBE, '/' to 0xBF,
            '`' to 0xC0,
        ).mapValues { it.value.toShort() }

        /** The key that produces [char], and whether Shift is part of it. */
        fun virtualKeyFor(char: Char): Pair<Short, Boolean>? = when {
            char in 'a'..'z' -> ('A'.code + (char - 'a')).toShort() to false
            char in 'A'..'Z' -> char.code.toShort() to true
            char in '0'..'9' -> char.code.toShort() to false
            char == ' ' -> VK_SPACE to false
            PLAIN_SYMBOLS.containsKey(char) -> PLAIN_SYMBOLS.getValue(char) to false
            SHIFTED_SYMBOLS.containsKey(char) -> SHIFTED_SYMBOLS.getValue(char) to true
            else -> null
        }

        /*
         * Windows virtual key codes, which is what the protocol carries whatever
         * the host runs — Sunshine on Linux and macOS translates them.
         */
        const val VK_BACK: Short = 0x08
        const val VK_TAB: Short = 0x09
        const val VK_RETURN: Short = 0x0D
        const val VK_ESCAPE: Short = 0x1B
        const val VK_LEFT: Short = 0x25
        const val VK_UP: Short = 0x26
        const val VK_RIGHT: Short = 0x27
        const val VK_DOWN: Short = 0x28
        const val VK_DELETE: Short = 0x2E

        /** A finger's travel is far shorter than a wheel's notch; 120 is one notch. */
        private const val SCROLL_SCALE = 4f

        private const val SHORT_MIN = Short.MIN_VALUE.toInt()
        private const val SHORT_MAX = Short.MAX_VALUE.toInt()
    }
}
