package com.thor.launcher.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.input.MouseController
import com.thor.core.input.PointerDisplay
import com.thor.core.input.PointerPosition
import com.thor.core.model.MouseAction
import com.thor.core.model.MouseButton
import com.thor.core.model.MouseSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The pointer, everywhere except inside THOR.
 *
 * An accessibility service is the only way an unprivileged app can put a cursor
 * over another app and click with it. Nothing else is offered: injecting input
 * needs `INJECT_EVENTS`, which is signature-only, and every other route is either
 * root or a lie. `dispatchGesture` is the sanctioned door, and this is what walks
 * through it.
 *
 * **What this service cannot do, and why.** Accessibility services are delivered
 * key events and never motion events. The analogue stick is a motion event. So
 * while another app has focus the pointer moves on the D-pad, and the stick only
 * drives it inside THOR — where the launcher reads its own input and hands the
 * deltas over directly. That is an API boundary, not an omission.
 *
 * **It consumes the buttons it uses.** While the pointer is up, a bound button is
 * a click and the app underneath hears nothing of it — otherwise a press would
 * both move the cursor and act in the game, and every click would fire twice. The
 * chord that raises the pointer also lowers it, and it is checked before anything
 * else, so there is always a way back out.
 */
@AndroidEntryPoint
class ThorMouseService : AccessibilityService() {

    @Inject lateinit var mouse: MouseController

    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: PointerOverlay? = null
    private var settings = MouseSettings()

    /** Buttons of the toggle chord currently held. */
    private var startHeld = false
    private var selectHeld = false

    /** Suppresses the chord re-firing while both buttons stay down. */
    private var chordFired = false

    /** Directions currently held, for repeat while the pointer is up. */
    private val heldDirections = mutableMapOf<Int, Job>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = PointerOverlay(this)
        reportDisplays()
        mouse.onPanelsNeeded(::reportDisplays)

        settingsRepository.mouse
            .onEach { updated ->
                settings = updated
                mouse.updateSettings(updated)
            }
            .launchIn(scope)

        // Drawn from the shared state rather than from this service's own idea of
        // where the pointer is, so the cursor is in the same place whether the
        // launcher or this service last moved it.
        mouse.state
            .onEach { state ->
                val position = state.position
                if (state.active && position != null) {
                    overlay?.show(position, settings.cursorSizeDp)
                } else {
                    overlay?.hide()
                }
            }
            .launchIn(scope)

        mouse.setServiceConnected(true)
        ThorLog.i(TAG, "Pointer service connected")
    }

    override fun onDestroy() {
        heldDirections.values.forEach(Job::cancel)
        heldDirections.clear()
        overlay?.hide()
        overlay = null
        // Cleared before the scope dies: the controller is a singleton and outlives
        // this service, so a listener left pointing at a destroyed one would keep
        // it reachable and would report panels through a dead window manager.
        mouse.onPanelsNeeded(null)
        mouse.setServiceConnected(false)
        // The pointer cannot be driven outside the launcher without this service,
        // so it is put away rather than left up and unresponsive.
        if (!mouse.launcherForeground) mouse.setActive(false)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Nothing is observed.
     *
     * Whether THOR is in front is asked of the launcher itself rather than
     * inferred from window-state events — see [MouseController.launcherForeground]
     * — so this service reads nothing about any app, including which one is open.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        mouse.setActive(false)
    }

    /**
     * @return true when the event was consumed and must not reach the app below.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        /*
         * Stands down entirely while THOR itself is in front.
         *
         * The launcher runs the pointer unaided — chord, buttons, stick and its
         * own click dispatch — because inside its own windows it needs none of
         * this service's powers. Handling the same press in both places double-
         * toggled the chord and clicked everything twice; worse, this service
         * consuming buttons *before* the launcher saw them is exactly the
         * "normal controls overwrite the mouse" symptom. Outside THOR the
         * launcher cannot see input at all, and this service is the only driver.
         */
        if (mouse.launcherOwnsPointer) return false

        // The chord is checked first and always, so the pointer can be dismissed
        // from any state — including one where something else has gone wrong.
        if (handleChord(event)) return true

        if (!mouse.isActive) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP

        // Direction keys move the cursor. The stick would be better and is not
        // available here; see the class note.
        directionFor(event.keyCode)?.let { (dx, dy) ->
            when {
                down && event.repeatCount == 0 -> startRepeat(event.keyCode, dx, dy)
                up -> stopRepeat(event.keyCode)
            }
            return true
        }

        val button = buttonFor(event.keyCode) ?: return false
        val action = settings.actionFor(button)
        if (action == MouseAction.NONE) return false

        // Acted on release, so a held button does not repeat a click, and so the
        // press and its release are consumed as a pair.
        if (up) perform(action)
        return !settings.passThroughToApp
    }

    /**
     * Declares the panels when the launcher has not.
     *
     * Ordered by their vertical position so the stacked space matches the physical
     * stack — the pointer runs off the bottom of the upper panel onto the top of
     * the lower one, and getting the order wrong would make crossing the seam jump
     * the wrong way. Presentation displays are excluded: a cast target or a screen
     * recorder is somewhere the cursor could wander to and never come back from.
     */
    private fun reportDisplays() {
        val manager = getSystemService(DisplayManager::class.java) ?: return

        val panels = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .toList()
            .let { presentation -> listOfNotNull(manager.getDisplay(Display.DEFAULT_DISPLAY)) + presentation }
            .filter { it.isValid && it.state != Display.STATE_OFF }

        var offset = 0
        mouse.setFallbackDisplays(
            panels.map { display ->
                val metrics = DisplayMetrics().also {
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(it)
                }
                PointerDisplay(
                    displayId = display.displayId,
                    widthPx = metrics.widthPixels,
                    heightPx = metrics.heightPixels,
                    topOffsetPx = offset,
                ).also { offset += metrics.heightPixels }
            },
        )
    }

    /**
     * Start + Select, held together.
     *
     * Fires once per press of the pair rather than on every key event while both
     * are down, which would toggle the pointer several times a second.
     */
    private fun handleChord(event: KeyEvent): Boolean {
        val isStart = event.keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            event.keyCode == KeyEvent.KEYCODE_MENU
        val isSelect = event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE

        if (!isStart && !isSelect) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        if (isStart) startHeld = down
        if (isSelect) selectHeld = down

        if (startHeld && selectHeld && !chordFired) {
            chordFired = true
            if (settings.enabled) {
                mouse.toggle()
                ThorLog.i(TAG, "Pointer ${if (mouse.isActive) "on" else "off"}")
            }
            return true
        }

        if (!startHeld && !selectHeld) chordFired = false

        /*
         * Both halves of the chord are swallowed while the pointer is up.
         *
         * Start and Select are usually bound in the app underneath, and letting
         * one through as the other arrives would pause the game every time the
         * pointer was dismissed.
         */
        return mouse.isActive
    }

    private fun startRepeat(keyCode: Int, dx: Float, dy: Float) {
        stopRepeat(keyCode)
        heldDirections[keyCode] = scope.launch {
            // One step immediately, then a smooth glide — the same shape as the
            // grid's auto-repeat, so held movement feels like the rest of the
            // launcher rather than like a separate input system.
            mouse.moveByStep(dx, dy)
            delay(REPEAT_DELAY_MS)
            while (isActive) {
                mouse.moveByStep(dx, dy)
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopRepeat(keyCode: Int) {
        heldDirections.remove(keyCode)?.cancel()
    }

    private fun perform(action: MouseAction) {
        val position = mouse.state.value.position ?: return
        when (action) {
            MouseAction.PRIMARY_CLICK -> tap(position, TAP_MS)
            MouseAction.SECONDARY_CLICK -> tap(position, LONG_PRESS_MS)
            MouseAction.SCROLL_UP -> swipe(position, -SCROLL_DISTANCE_PX)
            MouseAction.SCROLL_DOWN -> swipe(position, SCROLL_DISTANCE_PX)
            MouseAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            /*
             * Only useful while THOR is on screen, and it says so.
             *
             * THOR's keyboard is a composable in THOR's own window, so out here it
             * would open somewhere nobody can see. Typing into another app needs
             * `ACTION_SET_TEXT` on its focused node, which needs permission to read
             * that app's window content — a thing this service deliberately does
             * not ask for. Until that trade is made, the platform's own keyboard is
             * what appears when the pointer taps a text field in another app, and
             * that is the app's IME doing its job rather than THOR failing at one.
             */
            MouseAction.OPEN_KEYBOARD -> if (mouse.launcherForeground) {
                mouse.requestKeyboard()
            } else {
                ThorLog.i(TAG, "Keyboard request ignored: THOR is not on screen")
            }
            MouseAction.TOGGLE_OFF -> mouse.setActive(false)
            MouseAction.NONE -> Unit
        }
    }

    private fun tap(position: PointerPosition, durationMs: Long) {
        val path = Path().apply { moveTo(position.x, position.y) }
        dispatch(
            GestureDescription.StrokeDescription(path, 0L, durationMs),
            position.displayId,
        )
        mouse.notifyClicked()
    }

    /**
     * A scroll, expressed as the drag it actually is.
     *
     * There is no scroll gesture to dispatch — `dispatchGesture` describes strokes,
     * and a scroll is a stroke. Content that only responds to a fling will not move
     * far, which is the honest limit of doing this from outside the app.
     */
    private fun swipe(position: PointerPosition, distance: Float) {
        val path = Path().apply {
            moveTo(position.x, position.y)
            lineTo(position.x, position.y - distance)
        }
        dispatch(
            GestureDescription.StrokeDescription(path, 0L, SCROLL_MS),
            position.displayId,
        )
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, displayId: Int) {
        val builder = GestureDescription.Builder().addStroke(stroke)

        /*
         * Aimed at the panel the cursor is on.
         *
         * Without this every gesture lands on the default display, so a click on
         * the second panel would press whatever happened to be under the same
         * coordinates on the first — which is worse than not clicking at all.
         * API 30; below that the pointer is confined to the default display.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setDisplayId(displayId)
        }

        runCatching { dispatchGesture(builder.build(), null, null) }
            .onFailure { error -> ThorLog.w(TAG, "Gesture refused on display $displayId", error) }
    }

    private fun directionFor(keyCode: Int): Pair<Float, Float>? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -1f to 0f
        KeyEvent.KEYCODE_DPAD_RIGHT -> 1f to 0f
        KeyEvent.KEYCODE_DPAD_UP -> 0f to -1f
        KeyEvent.KEYCODE_DPAD_DOWN -> 0f to 1f
        else -> null
    }

    private fun buttonFor(keyCode: Int): MouseButton? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> MouseButton.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> MouseButton.B
        KeyEvent.KEYCODE_BUTTON_X -> MouseButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> MouseButton.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> MouseButton.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> MouseButton.R1
        KeyEvent.KEYCODE_BUTTON_L2 -> MouseButton.L2
        KeyEvent.KEYCODE_BUTTON_R2 -> MouseButton.R2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> MouseButton.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> MouseButton.R3
        else -> null
    }

    private companion object {
        const val TAG = "Pointer"
        const val TAP_MS = 40L
        const val LONG_PRESS_MS = 600L
        const val SCROLL_MS = 220L
        const val SCROLL_DISTANCE_PX = 420f
        const val REPEAT_DELAY_MS = 260L
        const val REPEAT_INTERVAL_MS = 16L
    }
}
