package com.thor.launcher.mouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.thor.core.common.log.ThorLog
import com.thor.core.input.PointerPosition

/**
 * Draws the pointer, on whichever panel it is currently on.
 *
 * One window per display, created on demand and left in place while the pointer
 * is up. `TYPE_ACCESSIBILITY_OVERLAY` rather than `TYPE_APPLICATION_OVERLAY`,
 * which is the difference between needing one permission and two: an accessibility
 * overlay is granted by the service already being enabled, while an application
 * overlay would need "Draw over other apps" on top of it. Nothing else about the
 * window differs.
 *
 * The windows are untouchable and unfocusable by design. A cursor that swallowed
 * the touch underneath it would make the pointer the only thing on the device that
 * could be pointed at.
 */
class PointerOverlay(
    private val context: Context,
    /**
     * Joystick motion, from whichever panel the cursor is on.
     *
     * Reported from here because this is the only window THOR has that is
     * focused while another app is in front, and a focused window is the only
     * place Android delivers motion events at all.
     */
    private val onMotion: (MotionEvent) -> Boolean = { false },
) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val windows = mutableMapOf<Int, DisplayWindow>()

    private class DisplayWindow(
        val windowManager: WindowManager,
        val view: PointerView,
    )

    /**
     * Shows the pointer at [position], moving it between panels as needed.
     *
     * @param fillArgb the theme's cursor colour, so the pointer looks like part
     *   of the launcher even while standing over somebody else's app
     */
    fun show(position: PointerPosition, sizeDp: Int, fillArgb: Long) {
        // Hidden on every other panel, so the pointer is never in two places.
        windows.filterKeys { it != position.displayId }.keys.forEach(::hideOn)

        val window = windows.getOrPut(position.displayId) {
            create(position.displayId) ?: return
        }
        window.view.setFill(fillArgb.toInt())
        window.view.moveTo(position.x, position.y, sizeDp)
    }

    /** Takes the pointer down everywhere. */
    fun hide() {
        windows.keys.toList().forEach(::hideOn)
    }

    private fun hideOn(displayId: Int) {
        val window = windows.remove(displayId) ?: return
        runCatching { window.windowManager.removeViewImmediate(window.view) }
    }

    private fun create(displayId: Int): DisplayWindow? {
        val display = displayManager?.getDisplay(displayId) ?: return null
        val displayContext = context.createDisplayContext(display)
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
            ?: return null

        /*
         * Two window types, tried in order.
         *
         * An accessibility overlay is granted by this service already being
         * enabled and needs no further permission, so it is always tried first.
         * But whether one may be placed on a *secondary* display varies by ROM,
         * and a refusal there used to lose the cursor entirely the moment it
         * crossed panels — with nothing on screen to say why. An application
         * overlay is the fallback where "Draw over other apps" has been granted.
         */
        val types = intArrayOf(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        )

        for (type in types) {
            val view = PointerView(displayContext, onMotion)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                /*
                 * Focusable on purpose, and this is the crux of the whole feature.
                 *
                 * Accessibility services are delivered key events and never motion
                 * events, and the analogue stick is a motion event. So the cursor
                 * was being driven by the launcher's own input handling, which
                 * only works while the launcher holds focus — the moment a click
                 * gave focus to the app underneath, the stick went somewhere
                 * nothing could see it and the cursor froze until the launcher was
                 * brought back. A focused window is the only place motion events
                 * are delivered, so while the pointer is up this window is it.
                 *
                 * NOT_TOUCHABLE stays: the cursor must never eat the taps it is
                 * aiming. ALT_FOCUSABLE_IM keeps a keyboard from opening merely
                 * because a focusable window appeared.
                 */
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            val added = runCatching { windowManager.addView(view, params) }
                .onFailure { error ->
                    ThorLog.w(TAG, "Overlay type $type refused on display $displayId", error)
                }
                .isSuccess

            if (added) {
                // Focus has to be asked for; being in a focusable window is not
                // enough for a view to be delivered anything.
                view.requestFocus()
                return DisplayWindow(windowManager, view)
            }
        }

        ThorLog.e(TAG, "No pointer overlay could be placed on display $displayId")
        return null
    }

    private companion object {
        const val TAG = "Pointer"
    }
}

/**
 * The cursor itself.
 *
 * Drawn rather than an asset so it can carry its own outline: a flat white arrow
 * disappears over white, and a flat black one disappears over a game. An arrow
 * filled light with a dark stroke reads on both, which is the only thing a
 * pointer over arbitrary content has to do.
 */
@SuppressLint("ViewConstructor")
private class PointerView(
    context: Context,
    private val onMotion: (MotionEvent) -> Boolean,
) : View(context) {

    private var pointerX = 0f
    private var pointerY = 0f
    private var sizePx = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * The stick, everywhere.
     *
     * `onGenericMotionEvent` rather than `onTouchEvent`: a joystick is a generic
     * motion source, and it is delivered only to the focused window — which is
     * why this window asks to be one while the pointer is up.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        onMotion(event) || super.onGenericMotionEvent(event)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(220, 16, 18, 22)
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Takes the theme's cursor colour, and picks its own outline against it.
     *
     * The same rule the in-launcher cursor uses: this arrow sits over content
     * nobody chose — a game, a browser, a store page — so the outline is whichever
     * of black or white the fill is furthest from, rather than a fixed colour that
     * would disappear for half the palettes.
     */
    fun setFill(argb: Int) {
        if (fill.color == argb) return
        fill.color = argb
        stroke.color = if (Color.luminance(argb) > MID_LUMINANCE) {
            Color.argb(220, 16, 18, 22)
        } else {
            Color.argb(220, 255, 255, 255)
        }
        invalidate()
    }

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 0, 0, 0)
    }

    private val path = Path()

    init {
        setWillNotDraw(false)
        // A hardware layer would be re-uploaded on every move; the cursor is a
        // dozen path segments and cheaper drawn straight onto the canvas.
        setLayerType(LAYER_TYPE_NONE, null)
    }

    fun moveTo(x: Float, y: Float, sizeDp: Int) {
        val px = sizeDp * resources.displayMetrics.density
        if (pointerX == x && pointerY == y && sizePx == px) return
        pointerX = x
        pointerY = y
        sizePx = px
        stroke.strokeWidth = px * STROKE_FRACTION
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (sizePx <= 0f) return

        val w = sizePx * 0.62f
        val h = sizePx

        // The same arrow the in-launcher cursor draws; see the note there. The two
        // must agree exactly, because crossing out of THOR hands the drawing from
        // one to the other and a change of shape mid-travel reads as a glitch.
        path.reset()
        path.moveTo(pointerX, pointerY)
        path.lineTo(pointerX, pointerY + h * 0.80f)
        path.lineTo(pointerX + w * 0.24f, pointerY + h * 0.62f)
        path.lineTo(pointerX + w * 0.40f, pointerY + h)
        path.lineTo(pointerX + w * 0.58f, pointerY + h * 0.92f)
        path.lineTo(pointerX + w * 0.42f, pointerY + h * 0.56f)
        path.lineTo(pointerX + w * 0.68f, pointerY + h * 0.54f)
        path.close()

        // A soft drop under the arrow, so it separates from bright content the
        // outline alone would sit flat against.
        canvas.save()
        canvas.translate(sizePx * 0.06f, sizePx * 0.08f)
        canvas.drawPath(path, shadow)
        canvas.restore()

        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    private companion object {
        const val STROKE_FRACTION = 0.075f

        /** Above this, a fill needs a dark outline rather than a pale one. */
        const val MID_LUMINANCE = 0.45f
    }
}
