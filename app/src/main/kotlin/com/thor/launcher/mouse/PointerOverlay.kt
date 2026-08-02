package com.thor.launcher.mouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * The windows are untouchable, but focusable while the pointer is up. That keeps
 * touches with the app beneath the cursor while allowing the focused overlay to
 * receive controller motion events outside THOR.
 */
class PointerOverlay(
    private val context: Context,
    private val onMotion: (MotionEvent) -> Boolean,
    /**
     * The window feeding the stick stream has stopped feeding it.
     *
     * Load-bearing, because a held stick produces no further events: the stream
     * is a burst on deflection and then silence, so "the stick is still down" and
     * "nobody is telling us any more" look identical from here. Whoever is moving
     * the cursor has to be told the moment the feed goes, or it carries on
     * applying the last deflection it was given for as long as the pointer is up.
     */
    private val onFeedLost: () -> Unit = {},
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
    fun show(position: PointerPosition, sizeDp: Int, fillArgb: Long): Boolean {
        // Hidden on every other panel, so the pointer is never in two places.
        windows.filterKeys { it != position.displayId }.keys.forEach(::hideOn)

        val window = windows[position.displayId]
            ?: create(position.displayId)?.also { windows[position.displayId] = it }
            ?: return false
        window.view.setFill(fillArgb.toInt())
        window.view.moveTo(position.x, position.y, sizeDp)
        return true
    }

    /** Takes the pointer down everywhere. */
    fun hide() {
        windows.keys.toList().forEach(::hideOn)
    }

    /**
     * Whether these windows take key focus.
     *
     * Focusable is the normal state and is what delivers the analogue stick — an
     * accessibility service is given keys but not motion. It has to be given up
     * while THOR's own keyboard is showing, because focus carries keys as well,
     * and a focused overlay is a keyboard that receives nothing.
     *
     * Applied to windows already on screen rather than only to new ones, since
     * the keyboard opens and closes many times over a single pointer session.
     */
    fun setFocusable(focusable: Boolean) {
        if (this.focusable == focusable) return
        this.focusable = focusable

        windows.values.forEach { window ->
            val params = window.view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            params.flags = flagsFor(focusable)
            runCatching { window.windowManager.updateViewLayout(window.view, params) }
                .onFailure { error -> ThorLog.w(TAG, "Could not update overlay focus", error) }
            if (focusable) window.view.requestFocus()
        }
    }

    private var focusable = true

    /**
     * The window flags, which differ only in whether focus is taken.
     *
     * `FLAG_NOT_TOUCHABLE` is in both and never negotiable: this window covers the
     * whole panel, and a touchable one would swallow every tap meant for the app
     * underneath the cursor.
     */
    private fun flagsFor(focusable: Boolean): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (focusable) {
            base or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        } else {
            base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
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
            val view = PointerView(displayContext, onMotion, onFeedLost)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                /*
                 * This transparent window takes key focus only while pointer mode
                 * is up. Accessibility services receive controller keys, but not
                 * the analogue-stick motion stream; that stream follows the focused
                 * window. Keeping this view untouchable means it never blocks the
                 * app beneath it, while focus gives the external pointer the same
                 * stick control it has inside THOR.
                 */
                flagsFor(focusable),
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
 * A compact crosshair rather than a desktop arrow. Its centre is the click point,
 * and its separated arms remain visible over both bright screenshots and dark apps.
 */
@SuppressLint("ViewConstructor")
private class PointerView(
    context: Context,
    private val onMotion: (MotionEvent) -> Boolean,
    private val onFeedLost: () -> Unit,
) : View(context) {

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
        strokeCap = Paint.Cap.ROUND
    }

    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Takes the theme's cursor colour, and picks its own outline against it.
     *
     * The same rule the in-launcher cursor uses: this reticle sits over content
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
        accent.color = argb
        invalidate()
    }

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 0, 0, 0)
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
        // A hardware layer would be re-uploaded on every move; the crosshair is
        // four short strokes and cheaper drawn straight onto the canvas.
        setLayerType(LAYER_TYPE_NONE, null)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        onMotion(event) || super.onGenericMotionEvent(event)

    /**
     * Focus is what delivers the stick, so losing it ends the deflection.
     *
     * The motion stream follows the focused window, and this one does not keep
     * focus for the life of the pointer: the cursor crossing to the other panel
     * tears this window down and builds another over there, a dialog or an app
     * can take focus at any moment, and whether a focusable overlay is even
     * permitted on a secondary display varies by ROM.
     *
     * Whenever that happens mid-deflection, the event that would have reported
     * the stick returning to centre is delivered somewhere else — and the last
     * deflection this window *did* report is left standing. The cursor then
     * travels in that direction for as long as the pointer is up, answering
     * nothing, which is the "it drifts to the bottom of the screen and I cannot
     * control it" fault. Ending the deflection on focus loss is the same
     * protection `LauncherActivity` gets from `releaseAll`.
     */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) onFeedLost()
    }

    /** The window going away is the same loss, arriving by the other route. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onFeedLost()
    }

    fun moveTo(x: Float, y: Float, sizeDp: Int) {
        val px = sizeDp * resources.displayMetrics.density
        if (pointerX == x && pointerY == y && sizePx == px) return
        pointerX = x
        pointerY = y
        sizePx = px
        stroke.strokeWidth = px * STROKE_FRACTION
        accent.strokeWidth = px * ACCENT_STROKE_FRACTION
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (sizePx <= 0f) return

        val arm = sizePx * ARM_FRACTION
        val gap = sizePx * GAP_FRACTION
        val shadowWidth = sizePx * SHADOW_STROKE_FRACTION
        shadow.strokeWidth = shadowWidth

        fun line(dx1: Float, dy1: Float, dx2: Float, dy2: Float, paint: Paint) {
            canvas.drawLine(pointerX + dx1, pointerY + dy1, pointerX + dx2, pointerY + dy2, paint)
        }

        // Outline first, then the themed inner stroke, then a solid centre so the
        // exact press point is readable without making the reticle bulky.
        fun drawArms(paint: Paint) {
            line(-arm, 0f, -gap, 0f, paint)
            line(gap, 0f, arm, 0f, paint)
            line(0f, -arm, 0f, -gap, paint)
            line(0f, gap, 0f, arm, paint)
        }

        drawArms(shadow)
        drawArms(stroke)
        drawArms(accent)
        canvas.drawCircle(pointerX, pointerY, sizePx * CENTRE_RADIUS_FRACTION, fill)
    }

    private companion object {
        const val STROKE_FRACTION = 0.09f
        const val SHADOW_STROKE_FRACTION = 0.17f
        const val ACCENT_STROKE_FRACTION = 0.052f
        const val ARM_FRACTION = 0.38f
        const val GAP_FRACTION = 0.12f
        const val CENTRE_RADIUS_FRACTION = 0.105f

        /** Above this, a fill needs a dark outline rather than a pale one. */
        const val MID_LUMINANCE = 0.45f
    }
}
