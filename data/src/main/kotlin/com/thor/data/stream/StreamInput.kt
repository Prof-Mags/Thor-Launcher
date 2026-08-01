package com.thor.data.stream

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.jni.MoonBridge
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns the handheld's controls into GameStream input.
 *
 * Stateful on purpose. The protocol sends the **whole** pad on every change — a
 * bitmask of every button currently held, both triggers and both sticks — rather
 * than one event per press, so something has to remember what is down. Sending a
 * packet built from a single event would release every other button on the pad
 * with each press.
 *
 * Not thread-safe, and does not need to be: input arrives on the window's own
 * thread, one event at a time.
 */
class StreamInput(
    /**
     * Called when the user asks to leave with the button combination.
     *
     * A callback rather than a flag the window polls, because the combination is
     * recognised here — this is the only place that knows the whole pad state.
     */
    private val onQuitCombo: () -> Unit = {},
    /** Called when Start is pressed and the setting says it opens settings. */
    private val onStart: (() -> Unit)? = null,
) {

    private var deadZone = DEFAULT_DEAD_ZONE
    private var startOpensSettings = false

    fun updateSettings(quality: com.thor.core.model.SessionQuality) {
        deadZone = quality.stickDeadZone.coerceIn(0f, MAX_DEAD_ZONE)
        startOpensSettings = quality.startOpensSettings
    }

    private var buttons = 0
    private var leftTrigger: Byte = 0
    private var rightTrigger: Byte = 0
    private var leftStickX: Short = 0
    private var leftStickY: Short = 0
    private var rightStickX: Short = 0
    private var rightStickY: Short = 0

    /**
     * Whether the D-pad is currently held on the hat axes.
     *
     * The hat is reported as an axis, not as buttons, so its directions have to
     * be cleared explicitly when it returns to centre — unlike a real button,
     * there is no key-up to hang the release on.
     */
    private var hatButtons = 0

    /**
     * @return true when the event was part of the game and should not also reach
     *   the launcher. A key THOR does not recognise is left alone, so volume and
     *   power keep working while streaming.
     */
    fun onKey(event: KeyEvent): Boolean {
        val flag = buttonFlag(event.keyCode)

        if (flag == 0) {
            /*
             * Triggers report as both an axis and a key on many pads.
             *
             * Handled on the axis, which has a range, and swallowed here so a
             * pull is not also sent as a button — the host would see the trigger
             * fully pressed the instant it left centre.
             */
            return event.keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_R2
        }

        /*
         * Start can be claimed for the settings panel instead of the game.
         *
         * Off by default, and deliberately: Start is a button most games use, and
         * a stream where it opens a launcher menu instead of pausing is a stream
         * with a button missing. Offered because on a desktop it is useless and
         * a quick way into the settings is worth more.
         */
        if (startOpensSettings &&
            event.keyCode == KeyEvent.KEYCODE_BUTTON_START &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            onStart?.invoke()
            return true
        }

        buttons = when (event.action) {
            KeyEvent.ACTION_DOWN -> buttons or flag
            KeyEvent.ACTION_UP -> buttons and flag.inv()
            else -> return true
        }

        /*
         * The quit combination, checked before the pad is sent on.
         *
         * Start, Select, LB and RB together — Moonlight's own, so anyone
         * arriving from it already knows the gesture. Four buttons at once
         * cannot happen during play, which matters on a handheld where the only
         * alternative is a system Back key that many games legitimately use.
         *
         * The pad is still sent afterwards: the host has to see these released,
         * or the game is left holding all four.
         */
        if (buttons and QUIT_COMBO == QUIT_COMBO) {
            send()
            onQuitCombo()
            return true
        }

        send()
        return true
    }

    /**
     * @return true when the motion belonged to a gamepad.
     *
     * Anything else — a mouse, the touchscreen — is left for the caller, which
     * has its own uses for it.
     */
    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromGamepad) return false

        leftStickX = event.axis(MotionEvent.AXIS_X)
        // Inverted, because Android measures down-positive and the protocol
        // measures up-positive. Getting this wrong is a stream where pushing
        // forward walks backwards.
        leftStickY = (-event.axis(MotionEvent.AXIS_Y)).toShort()
        rightStickX = event.axis(MotionEvent.AXIS_Z)
        rightStickY = (-event.axis(MotionEvent.AXIS_RZ)).toShort()

        leftTrigger = event.trigger(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE)
        rightTrigger = event.trigger(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS)

        /*
         * The D-pad arrives as two axes reading -1, 0 or 1.
         *
         * Rebuilt from scratch each time rather than toggled, so a diagonal
         * released to centre clears both directions at once — which is what the
         * axes report and what a held-then-released toggle would miss.
         */
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val newHat = when {
            hatX < -HAT_THRESHOLD -> ControllerFlags.LEFT
            hatX > HAT_THRESHOLD -> ControllerFlags.RIGHT
            else -> 0
        } or when {
            hatY < -HAT_THRESHOLD -> ControllerFlags.UP
            hatY > HAT_THRESHOLD -> ControllerFlags.DOWN
            else -> 0
        }

        if (newHat != hatButtons) {
            buttons = (buttons and hatButtons.inv()) or newHat
            hatButtons = newHat
        }

        send()
        return true
    }

    /**
     * Releases everything.
     *
     * Sent when the session ends or the window loses focus. Without it the host
     * keeps whatever was held at that moment held forever — a trigger pulled as
     * the user backed out leaves the game accelerating into a wall.
     */
    fun releaseAll() {
        buttons = 0
        hatButtons = 0
        leftTrigger = 0
        rightTrigger = 0
        leftStickX = 0
        leftStickY = 0
        rightStickX = 0
        rightStickY = 0
        send()
    }

    /*
     * Instance methods rather than companion extensions, because the dead zone
     * is a setting.
     *
     * They were on the companion while it was a constant; a companion cannot see
     * an instance field, so making the dead zone configurable moved them here.
     */

    /** One axis, dead-zoned and scaled to the protocol's signed range. */
    private fun MotionEvent.axis(axis: Int): Short {
        val value = getAxisValue(axis)
        if (abs(value) < deadZone) return 0

        /*
         * Rescaled from the edge of the dead zone rather than clipped.
         *
         * Clipping leaves a step at the threshold — the stick does nothing, then
         * jumps to whatever the dead zone was — which feels like a sticky
         * control. Stretching the remaining range back to full keeps fine
         * movement available right where it is wanted.
         */
        val sign = if (value < 0) -1f else 1f
        val scaled = (abs(value) - deadZone) / (1f - deadZone)
        return (sign * scaled.coerceAtMost(1f) * Short.MAX_VALUE).toInt().toShort()
    }

    /**
     * A trigger, from whichever axis this pad reports it on.
     *
     * Controllers disagree: some use `LTRIGGER`/`RTRIGGER`, others reuse the
     * driving axes `BRAKE`/`GAS`, and a few populate both. Taking the larger
     * covers every case without having to identify the device.
     */
    private fun MotionEvent.trigger(primary: Int, fallback: Int): Byte {
        val value = max(getAxisValue(primary), getAxisValue(fallback))
        if (value < deadZone) return 0
        return (value.coerceAtMost(1f) * UByte.MAX_VALUE.toInt()).toInt().toByte()
    }

    private fun send() {
        MoonBridge.sendMultiControllerInput(
            CONTROLLER_NUMBER,
            ACTIVE_MASK,
            buttons,
            leftTrigger,
            rightTrigger,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
        )
    }

    /** The button bits, as the protocol numbers them. */
    private object ControllerFlags {
        const val UP = 0x0001
        const val DOWN = 0x0002
        const val LEFT = 0x0004
        const val RIGHT = 0x0008
        const val PLAY = 0x0010
        const val BACK = 0x0020
        const val LS_CLK = 0x0040
        const val RS_CLK = 0x0080
        const val LB = 0x0100
        const val RB = 0x0200
        const val SPECIAL = 0x0400
        const val A = 0x1000
        const val B = 0x2000
        const val X = 0x4000
        const val Y = 0x8000
    }

    private companion object {
        /**
         * One pad, and it is pad zero.
         *
         * The handheld's controls are built in and there is nowhere to plug a
         * second one; the mask says exactly that pad is present, which is what
         * makes the host show one controller rather than none.
         */
        const val CONTROLLER_NUMBER: Short = 0
        const val ACTIVE_MASK: Short = 0x1

        /** Past halfway is held; the hat only ever reports -1, 0 or 1 anyway. */
        const val HAT_THRESHOLD = 0.5f

        /**
         * Start + Select + LB + RB, held together.
         *
         * Not `const`: Kotlin only folds literals into compile-time constants,
         * and `or` is a function call however constant its operands look.
         */
        val QUIT_COMBO = ControllerFlags.PLAY or ControllerFlags.BACK or
            ControllerFlags.LB or ControllerFlags.RB

        /**
         * Below this, a stick is treated as centred.
         *
         * Analogue sticks rest slightly off zero and drift with wear, and a
         * stream sends a packet on every change — so without a dead zone a pad
         * sitting untouched on a table produces a steady trickle of input and a
         * character that slowly walks away.
         */
        const val DEFAULT_DEAD_ZONE = 0.12f

        /** Past this a stick would be unusable, so the setting is capped. */
        const val MAX_DEAD_ZONE = 0.5f

        val MotionEvent.isFromGamepad: Boolean
            get() = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

        fun buttonFlag(keyCode: Int): Int = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> ControllerFlags.A
            KeyEvent.KEYCODE_BUTTON_B -> ControllerFlags.B
            KeyEvent.KEYCODE_BUTTON_X -> ControllerFlags.X
            KeyEvent.KEYCODE_BUTTON_Y -> ControllerFlags.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerFlags.LB
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerFlags.RB
            KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerFlags.LS_CLK
            KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerFlags.RS_CLK
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> ControllerFlags.PLAY
            KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerFlags.BACK
            KeyEvent.KEYCODE_BUTTON_MODE -> ControllerFlags.SPECIAL

            // The hat usually arrives as an axis, but some pads send these as
            // keys instead, and a pad that sends both is harmless: the bit is
            // already set.
            KeyEvent.KEYCODE_DPAD_UP -> ControllerFlags.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ControllerFlags.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> ControllerFlags.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerFlags.RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER -> ControllerFlags.A

            else -> 0
        }
    }
}
