package com.thor.data.stream

import android.view.MotionEvent
import android.view.View
import com.limelight.nvstream.jni.MoonBridge
import com.thor.core.common.log.ThorLog
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drives the PC's pointer from the handheld's touchscreen.
 *
 * Absolute rather than relative: a finger lands somewhere on a picture of the
 * remote screen, and the obvious meaning is "there" — not "move the pointer a
 * little in that direction". The touched point maps straight onto the same point
 * of the host's desktop, so pointing at a button puts the cursor on that button.
 *
 * Two gestures on top of that, because a desktop needs them and a touchscreen has
 * no buttons: a tap is a left click, and two fingers moving together scroll.
 * Everything else — a long press, a three-finger anything — is deliberately not
 * claimed, so it cannot be triggered by accident while aiming.
 */
class StreamTouch {

    /**
     * Off unless the setting says otherwise.
     *
     * The second screen carries a trackpad, which is the better tool for a
     * desktop — and a stray palm on the video while holding the handheld would
     * otherwise fling the cursor across the PC's screen.
     */
    private var enabled = false
    private var tapToClick = true
    private var naturalScroll = true

    fun updateSettings(quality: com.thor.core.model.SessionQuality) {
        enabled = quality.touchVideoAsPointer
        tapToClick = quality.tapToClick
        naturalScroll = quality.naturalScroll
    }

    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L

    /** Set once a gesture has been ruled out as a tap, so it cannot become one. */
    private var moved = false

    /** Where the last two-finger scroll was, so movement can be measured from it. */
    private var lastScrollY = 0f
    private var scrolling = false

    /**
     * @return true when the event was consumed as pointer input.
     *
     * Events from anything other than a finger are declined, so a connected
     * mouse keeps its own handling.
     */
    fun onTouch(view: View, event: MotionEvent): Boolean {
        if (!enabled || view.width <= 0 || view.height <= 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTimeMs = event.eventTime
                moved = false
                scrolling = false

                /*
                 * The pointer moves on touch-down, before any click.
                 *
                 * A click is delivered wherever the host's cursor currently is,
                 * so sending the position first is what makes a tap land where
                 * the finger is rather than wherever the pointer was left.
                 */
                sendPosition(view, event.x, event.y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger turns the gesture into a scroll, and it can
                // never go back to being a tap.
                if (event.pointerCount == 2) {
                    scrolling = true
                    moved = true
                    lastScrollY = midpointY(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (scrolling && event.pointerCount >= 2) {
                    val y = midpointY(event)
                    val delta = y - lastScrollY
                    if (abs(delta) >= SCROLL_STEP_PX) {
                        /*
                         * Inverted, so the content follows the fingers.
                         *
                         * Dragging down moves the page down, as it does
                         * everywhere else on a touchscreen — which is the
                         * opposite of what a scroll wheel reports.
                         */
                        sendScroll(-delta)
                        lastScrollY = y
                    }
                    return true
                }

                if (abs(event.x - downX) > TAP_SLOP_PX || abs(event.y - downY) > TAP_SLOP_PX) {
                    moved = true
                }
                sendPosition(view, event.x, event.y)
            }

            MotionEvent.ACTION_UP -> {
                /*
                 * A tap is a short press that did not travel.
                 *
                 * Both conditions matter: without the distance test, dragging
                 * the pointer somewhere and lifting would click there, and
                 * without the time test a slow, careful placement would too.
                 */
                val quick = event.eventTime - downTimeMs <= TAP_TIME_MS
                if (!moved && quick && tapToClick) click()
                scrolling = false
            }

            MotionEvent.ACTION_CANCEL -> scrolling = false

            else -> return false
        }
        return true
    }

    /**
     * Where the finger is, as a fraction of the view, in the host's own terms.
     *
     * The reference size is the view rather than the stream: the protocol scales
     * the coordinates itself, so handing it the size the user actually touched
     * keeps the mapping right whatever resolution the host chose to send —
     * including when it sends something other than what was asked for.
     */
    private fun sendPosition(view: View, x: Float, y: Float) {
        runCatching {
            MoonBridge.sendMousePosition(
                x.coerceIn(0f, view.width.toFloat()).roundToInt().toShort(),
                y.coerceIn(0f, view.height.toFloat()).roundToInt().toShort(),
                view.width.toShort(),
                view.height.toShort(),
            )
        }.onFailure { ThorLog.w(TAG, "Could not send a pointer position", it) }
    }

    private fun click() {
        runCatching {
            MoonBridge.sendMouseButton(BUTTON_PRESS, BUTTON_LEFT)
            MoonBridge.sendMouseButton(BUTTON_RELEASE, BUTTON_LEFT)
        }.onFailure { ThorLog.w(TAG, "Could not send a click", it) }
    }

    private fun sendScroll(pixels: Float) {
        runCatching {
            /*
             * Scaled into the protocol's high-resolution units, where one notch
             * of a wheel is 120. A finger travelling a few pixels should not be
             * a full notch, or scrolling would leap a line at a time.
             */
            val amount = (pixels / SCROLL_STEP_PX * SCROLL_UNITS_PER_STEP)
                .coerceIn(-SCROLL_CLAMP, SCROLL_CLAMP)
            MoonBridge.sendMouseHighResScroll(amount.roundToInt().toShort())
        }.onFailure { ThorLog.w(TAG, "Could not send a scroll", it) }
    }

    private fun midpointY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    private companion object {
        const val TAG = "Stream"

        /** Mouse button events, as `MouseButtonPacket` numbers them. */
        const val BUTTON_PRESS: Byte = 0x07
        const val BUTTON_RELEASE: Byte = 0x08
        const val BUTTON_LEFT: Byte = 0x01

        /** Far enough to be aiming rather than tapping. */
        const val TAP_SLOP_PX = 16f

        /** Longer than this and the finger was placing the pointer, not clicking. */
        const val TAP_TIME_MS = 250L

        /** How far two fingers travel before it counts as one step of scroll. */
        const val SCROLL_STEP_PX = 12f

        /** One wheel notch, in the units the protocol counts them in. */
        const val SCROLL_UNITS_PER_STEP = 40f

        /** Stops a fast flick sending a scroll the host reads as enormous. */
        const val SCROLL_CLAMP = 600f
    }
}
