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
class PointerOverlay(private val context: Context) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val windows = mutableMapOf<Int, DisplayWindow>()

    private class DisplayWindow(
        val windowManager: WindowManager,
        val view: PointerView,
    )

    /** Shows the pointer at [position], moving it between panels as needed. */
    fun show(position: PointerPosition, sizeDp: Int) {
        // Hidden on every other panel, so the pointer is never in two places.
        windows.filterKeys { it != position.displayId }.keys.forEach(::hideOn)

        val window = windows.getOrPut(position.displayId) {
            create(position.displayId) ?: return
        }
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
            val view = PointerView(displayContext)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                /*
                 * Not focusable, not touchable, and laid out over everything.
                 * Without NOT_TOUCHABLE the cursor would eat the taps it is
                 * supposed to be aiming; without NOT_FOCUSABLE it would take key
                 * focus from the app underneath and stop the buttons reaching it.
                 */
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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

            if (added) return DisplayWindow(windowManager, view)
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
private class PointerView(context: Context) : View(context) {

    private var pointerX = 0f
    private var pointerY = 0f
    private var sizePx = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(220, 16, 18, 22)
        strokeJoin = Paint.Join.ROUND
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

        path.reset()
        path.moveTo(pointerX, pointerY)
        path.lineTo(pointerX, pointerY + h)
        path.lineTo(pointerX + w * 0.30f, pointerY + h * 0.74f)
        path.lineTo(pointerX + w * 0.52f, pointerY + h * 1.02f)
        path.lineTo(pointerX + w * 0.75f, pointerY + h * 0.90f)
        path.lineTo(pointerX + w * 0.54f, pointerY + h * 0.62f)
        path.lineTo(pointerX + w, pointerY + h * 0.58f)
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
    }
}
