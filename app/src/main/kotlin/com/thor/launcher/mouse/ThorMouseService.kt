package com.thor.launcher.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.thor.core.common.capture.ScreenshotBridge
import com.thor.core.common.log.ThorLog
import com.thor.data.stream.StreamPresence
import com.thor.core.datastore.SettingsRepository
import com.thor.core.display.DisplayTopology
import com.thor.core.input.MouseController
import com.thor.core.input.PointerDisplay
import com.thor.core.input.PointerPosition
import com.thor.core.model.MouseAction
import com.thor.core.model.MouseButton
import com.thor.core.model.MouseSettings
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * The pointer, everywhere except inside THOR.
 *
 * An accessibility service is the only way an unprivileged app can put a cursor
 * over another app and click with it. Nothing else is offered: injecting input
 * needs `INJECT_EVENTS`, which is signature-only, and every other route is either
 * root or a lie. `dispatchGesture` is the sanctioned door, and this is what walks
 * through it.
 *
 * **External stick input.** Accessibility services receive controller keys but not
 * generic motion. While the cursor is up, its transparent, focusable overlay owns
 * the stick stream without intercepting touches, so the analogue stick works over
 * other apps as well as it does inside THOR.
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

    @Inject lateinit var screenshots: ScreenshotBridge

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: PointerOverlay? = null
    private var settings = MouseSettings()

    /** The theme's cursor colour; the default theme's until settings arrive. */
    private var cursorArgb: Long = PersonalizationSettings().resolveTheme().cursorArgb

    /** Buttons of the toggle chord currently held. */
    private var startHeld = false
    private var selectHeld = false

    /** Suppresses the chord re-firing while both buttons stay down. */
    private var chordFired = false

    /** R1, for the second chord; see [handleOverlayChord]. */
    private var shoulderHeld = false
    private var overlayChordFired = false

    /** Loki's panel over a running game, when it is up. */
    private var gameOverlay: GameOverlay? = null

    /** Directions currently held, for repeat while the pointer is up. */
    private val heldDirections = mutableMapOf<Int, Job>()

    /** Latest analogue-stick deflection, sampled by [startStickLoop]. */
    private var stickX = 0f
    private var stickY = 0f
    private var stickLoop: Job? = null

    /** Right-stick deflection, driving the scroll; see [startScrollLoop]. */
    private var scrollStick = 0f
    private var scrollLoop: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        requestControllerKeyFiltering()
        overlay = PointerOverlay(this, ::onStickMoved, ::stopAllStickInput)
        // Only where the platform has the call. Below API 30 the launcher simply
        // reports that a screenshot cannot be taken, which is the truth and is
        // what every surface offering the action already handles.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenshots.bind(::captureDisplay)
        }
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
        /*
         * The theme's cursor colour, so the pointer looks like Loki's even while it
         * is standing over somebody else's app. Resolved from the whole of the
         * user's appearance settings rather than from the theme id alone: the
         * cursor follows a hue shift, a colour-intensity change and a picked accent
         * as well, and out here there is no Compose theme to read it from.
         *
         * The system's light/dark setting comes from this service's own
         * configuration, which is what [ThemeMode.SYSTEM] needs and the only place
         * to get it without a composition.
         */
        settingsRepository.personalization
            .onEach { cursorArgb = it.resolveTheme(systemDark = systemInDarkMode()).cursorArgb }
            .launchIn(scope)

        mouse.state
            .onEach { state ->
                val position = state.position
                if (state.active && position != null) {
                    val shown = overlay?.show(
                        position = position,
                        sizeDp = settings.cursorSizeDp.coerceAtLeast(MIN_CURSOR_SIZE_DP),
                        fillArgb = cursorArgb,
                    ) == true
                    mouse.setServiceCursorDisplayId(position.displayId.takeIf { shown })
                } else {
                    cancelMovement()
                    overlay?.hide()
                    mouse.setServiceCursorDisplayId(null)
                }
            }
            .launchIn(scope)

        // The launcher's power-menu action. Raised here because
        // `performGlobalAction` is an accessibility API and no public intent
        // opens this dialog; `drop(1)` because collecting a counter replays it.
        mouse.powerMenuRequests
            .drop(1)
            .onEach { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) }
            .launchIn(scope)

        /*
         * The cursor hands back key focus while THOR is typing.
         *
         * This window is focusable on purpose — focus is what delivers the stick,
         * which an accessibility service is not given. But focus also delivers
         * *keys*, so while it holds it the launcher's own keyboard cannot receive
         * one, and letting the service pass keys through would only send them to
         * this window instead. Standing down for the keyboard's lifetime is what
         * puts them back on the launcher.
         *
         * The stick stops with the focus, which is correct rather than a cost:
         * the cursor holds its place, and `onFeedLost` is what stops it drifting
         * off on the last deflection it happened to be given.
         */
        mouse.typing
            .onEach { typing -> overlay?.setFocusable(!typing) }
            .launchIn(scope)

        /*
         * Every edit on Loki's keyboard, written into the field the user tapped.
         *
         * `drop(1)` because collecting a state flow replays its current value,
         * and replaying the seed the moment the keyboard opens would rewrite the
         * field with what it already contains — harmless in a URL bar, not
         * harmless in one that reacts to being edited.
         */
        mouse.typedText
            .drop(1)
            .onEach(::typeIntoFocusedField)
            .launchIn(scope)

        mouse.setServiceConnected(true)
        ThorLog.i(TAG, "Pointer service connected")
    }

    /**
     * Some vendor builds ignore the XML flag until it is also requested from the
     * live service. Without this, [onKeyEvent] never runs once another app owns
     * the foreground, so Start + Select cannot raise or dismiss the pointer.
     */
    private fun requestControllerKeyFiltering() {
        val info = serviceInfo ?: return
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        ThorLog.i(TAG, "External controller key filter requested")
    }

    override fun onDestroy() {
        cancelMovement()
        overlay?.hide()
        overlay = null
        // Same reasoning as the mouse listener below: the bridge is a singleton and
        // outlives this service, so a capture lambda left bound would hold a
        // destroyed service and would report the ability to take a screenshot that
        // nothing can now take.
        screenshots.unbind()
        // A window added by a service the system is tearing down would otherwise
        // be left on screen with nothing able to remove it.
        gameOverlay?.hide()
        gameOverlay = null
        mouse.setServiceCursorDisplayId(null)
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
     * A PNG of one display, taken through the accessibility permission.
     *
     * The only route that works while a game is in front — see [ScreenshotBridge]
     * for why `MediaProjection` is not usable here.
     *
     * The frame arrives as a hardware buffer, which is a handle to memory the GPU
     * owns rather than pixels. It has to be wrapped, copied into a software bitmap
     * and closed, in that order: compressing straight from the hardware bitmap
     * throws on some drivers, and leaving the buffer open leaks a graphics
     * allocation per screenshot.
     *
     * Suspends until the frame lands rather than returning a callback, so the
     * caller can write the file and report the result as one operation.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureDisplay(displayId: Int): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                displayId,
                Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bytes = result.hardwareBuffer.use { buffer ->
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            // A hardware bitmap has no pixels to read; the copy is
                            // what makes it compressible, and is why this is not
                            // simply `hardware.compress`.
                            val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            hardware?.recycle()
                            software?.let { bitmap ->
                                ByteArrayOutputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                                    bitmap.recycle()
                                    out.toByteArray()
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    }

                    override fun onFailure(errorCode: Int) {
                        // Ordinary rather than exceptional: the system refuses a
                        // capture over a secure window, which is a correct refusal
                        // and something the user may simply be looking at.
                        ThorLog.w(TAG, "Screenshot refused on display $displayId (code $errorCode)")
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
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
        cancelMovement()
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
        /*
         * This service owns the pointer outright while it is running.
         *
         * It used to stand down whenever THOR looked like it was in front, which
         * needed a reliable answer to "is it?" and never had one — and being
         * wrong meant the chord itself was dropped, so there was no way back.
         * Nothing is handed back now; the launcher defers whenever this service
         * is connected, and `dispatchGesture` reaches THOR's own windows just as
         * well as anyone else's.
         */
        /*
         * Hands the controller over entirely while a stream is on screen.
         *
         * The handheld is a screen for another machine at that point, and every
         * button belongs to whatever is running on it. This service seeing keys
         * first means it can take them, and both things it does with them are
         * wrong here: the Start+Select chord swallows the two buttons the
         * stream's own quit combination needs, and a pointer that comes up
         * claims the D-pad and face buttons to move a cursor — taking the
         * controller away from the game with no indication of why.
         *
         * Checked before `settings.enabled` so it holds regardless of how the
         * pointer is configured.
         */
        if (StreamPresence.streaming) return false

        /*
         * The panel over the game, before anything else and before the pointer's
         * own switch.
         *
         * It used to sit *after* `settings.enabled`, which is the setting for the
         * pointer — so on any device where the pointer was turned off the chord
         * was dropped before it was ever tested and nothing happened. They are
         * unrelated features that happen to share a service, and gating one on the
         * other's switch was simply a mistake.
         *
         * Dismissable from any state for the same reason the pointer's chord is,
         * which is why it is first.
         */
        if (handleOverlayChord(event)) return true
        if (gameOverlay?.isShowing == true) return true

        if (!settings.enabled) return false

        // The chord is checked first and always, so the pointer can be dismissed
        // from any state — including one where something else has gone wrong.
        if (handleChord(event)) return true

        /*
         * Every key belongs to THOR's keyboard while it is up.
         *
         * This service filters keys before any window sees them, so a bound
         * button was taken for the pointer and the keyboard never heard it — the
         * field stayed empty however much was typed. Passing them through is only
         * half of it: the cursor overlay is focusable, so the keys would land
         * there instead of on the launcher. The overlay gives up focus for the
         * same span; see the collector in `onServiceConnected`.
         */
        if (mouse.launcherTyping) return false

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

        val panels = listOfNotNull(
            manager.getDisplay(Display.DEFAULT_DISPLAY)
                ?.takeIf { it.isValid && it.state != Display.STATE_OFF },
            manager.displays.firstOrNull { display ->
                DisplayTopology.isUsableSecondary(display)
            },
        )

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
    /**
     * Start + R1, held together: Loki over the top of whatever is running.
     *
     * A second chord rather than a mode on the first, because the two do opposite
     * things — the pointer hands the controller to the app underneath, and this
     * takes it away — and because Start + Select was already spent.
     *
     * R1 rather than a hold of Start. A long press cannot be recognised until
     * after the down has already been delivered, so recognising one would mean
     * swallowing every Start and re-injecting the short ones: a pause button that
     * works most of the time. A chord is decided on the frame it completes.
     *
     * Both halves are swallowed while the panel is up, for the same reason the
     * pointer's are: R1 is bound in almost every game, and letting it through as
     * the panel closed would fire it in the game every time.
     */
    private fun handleOverlayChord(event: KeyEvent): Boolean {
        val isStart = event.keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            event.keyCode == KeyEvent.KEYCODE_MENU
        val isShoulder = event.keyCode == KeyEvent.KEYCODE_BUTTON_R1

        if (!isStart && !isShoulder) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        if (isStart) startHeld = down
        if (isShoulder) shoulderHeld = down

        if (startHeld && shoulderHeld && !overlayChordFired) {
            overlayChordFired = true
            toggleGameOverlay()
            return true
        }

        if (!startHeld && !shoulderHeld) overlayChordFired = false

        // Only R1 is swallowed while the panel is up. Start is left alone here so
        // the pointer's own chord below still sees it.
        return isShoulder && gameOverlay?.isShowing == true
    }

    /**
     * Raises or lowers the panel over the running app.
     *
     * Named from the launcher's own record of what it handed a panel to, rather
     * than by asking which app is in front — this service does not read that, and
     * the whole reason it can be trusted with these permissions is that it does
     * not start now.
     */
    private fun toggleGameOverlay() {
        val overlay = gameOverlay ?: GameOverlay(
            context = this,
            onAction = ::onGameOverlayAction,
            onDismiss = {},
        ).also { gameOverlay = it }

        if (overlay.isShowing) {
            overlay.hide()
            return
        }
        overlay.show(
            displayId = Display.DEFAULT_DISPLAY,
            title = screenshots.nowPlaying.value.ifBlank { "Loki" },
            accentArgb = cursorArgb,
        )
    }

    private fun onGameOverlayAction(action: GameOverlayAction) {
        when (action) {
            GameOverlayAction.SCREENSHOT -> scope.launch {
                /*
                 * A beat after the panel comes down.
                 *
                 * The overlay hides itself before running an action, but hiding a
                 * window and the compositor no longer drawing it are not the same
                 * frame — without this the screenshot caught Loki's own panel
                 * sitting over the game it was supposed to be a picture of.
                 */
                delay(OVERLAY_SETTLE_MS)
                // Straight through the same bridge the launcher's own tile uses,
                // so a shot taken from here is filed against the same game and in
                // the same place as one taken from the panel.
                screenshots.captureAndFile()
            }

            // Its own chord already does this; the tile is how somebody finds out
            // that it can be done at all.
            GameOverlayAction.POINTER -> if (settings.enabled) mouse.toggle()

            GameOverlayAction.BRIGHTNESS_DOWN -> stepBrightness(-BRIGHTNESS_STEP)
            GameOverlayAction.BRIGHTNESS_UP -> stepBrightness(BRIGHTNESS_STEP)

            /*
             * The system's own panel rather than a reimplementation of it.
             *
             * Wi-Fi, Bluetooth, volume and aeroplane mode all live there already,
             * and this is the only route to it from over a fullscreen game — the
             * notification shade is exactly what such a game is covering.
             */
            GameOverlayAction.QUICK_SETTINGS ->
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

            GameOverlayAction.GO_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GameOverlayAction.CLOSE -> Unit
        }
    }

    /**
     * Nudges the screen brightness.
     *
     * Written to the system setting rather than to a window attribute, because a
     * window attribute only dims the window it is set on — and the window this
     * service could set it on is a transparent overlay that has just been taken
     * down. The setting is the real brightness and is what the user means.
     *
     * Needs `WRITE_SETTINGS`, which is a permission the user grants in Android's
     * own screen; without it this fails and says so once rather than each press.
     */
    private fun stepBrightness(delta: Int) {
        val resolver = contentResolver
        val current = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return

        val next = (current + delta).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
        }.onFailure {
            ThorLog.w(TAG, "Brightness needs the Modify system settings permission")
        }
    }

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
            while (isActive && mouse.isActive) {
                mouse.moveByStep(dx, dy)
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopRepeat(keyCode: Int) {
        heldDirections.remove(keyCode)?.cancel()
    }

    /**
     * Reads analogue-stick movement from the focusable, untouchable cursor overlay.
     *
     * Android only emits an axis event when the stick changes, not continuously
     * while held, so the value is sampled by [startStickLoop]. A generous dead zone
     * and clearing the loop whenever pointer mode turns off prevent the old "cursor
     * walks itself to the bottom" failure.
     */
    private fun onStickMoved(event: MotionEvent): Boolean {
        if (!mouse.isActive) return false
        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0) return false

        if (event.action == MotionEvent.ACTION_CANCEL) {
            stopStickLoop()
            return true
        }

        // Only a move carries an axis reading worth acting on. Without this, any
        // other generic event on this window — a hover, a scroll — is read for
        // axes it does not have, and a deflection can be set from an event that
        // was never about the stick at all.
        if (event.action != MotionEvent.ACTION_MOVE) return false

        // The hat is a D-pad reported as axes. Prefer it only when it is actually
        // engaged so a controller that reports both does not make two directions
        // fight each other.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = if (abs(hatX) >= HAT_THRESHOLD) hatX else event.getAxisValue(MotionEvent.AXIS_X)
        val y = if (abs(hatY) >= HAT_THRESHOLD) hatY else event.getAxisValue(MotionEvent.AXIS_Y)

        stickX = x.takeIf { abs(it) >= STICK_DEAD_ZONE } ?: 0f
        stickY = y.takeIf { abs(it) >= STICK_DEAD_ZONE } ?: 0f
        if (stickX == 0f && stickY == 0f) stopStickLoop() else startStickLoop()

        val scroll = rightStickY(event)
        scrollStick = scroll.takeIf { abs(it) >= SCROLL_DEAD_ZONE } ?: 0f
        if (scrollStick == 0f) stopScrollLoop() else startScrollLoop()
        return true
    }

    /**
     * The right stick's vertical axis, if this pad actually has one.
     *
     * Which axis carries it is not settled across controllers: `AXIS_RZ` is the
     * usual answer and `AXIS_RY` the other one. Both are also names that *trigger*
     * axes go by on some pads, which matters here more than it looks — read
     * blindly, a pulled trigger would read as a stick buried downward and scroll
     * the page for as long as it was held.
     *
     * The range tells them apart without guessing at the device. A stick swings
     * both ways and reports a minimum below zero; a trigger only presses and
     * reports a minimum of zero. So an axis is only believed to be a stick when it
     * says it can go negative.
     */
    private fun rightStickY(event: MotionEvent): Float {
        val device = event.device ?: return 0f
        for (axis in SCROLL_AXES) {
            val range = device.getMotionRange(axis, event.source) ?: continue
            if (range.min >= 0f) continue
            val value = event.getAxisValue(axis)
            if (abs(value) >= SCROLL_DEAD_ZONE) return value
        }
        return 0f
    }

    /**
     * Scrolls under the cursor for as long as the right stick is held.
     *
     * A swipe rather than a scroll event, because `dispatchGesture` is the only
     * thing this service can do to another app's window and a drag is what a
     * scrollable view is built to answer. Paced to the length of the gesture it
     * dispatches: overlapping strokes are rejected, so issuing them faster than
     * they finish scrolls no quicker and drops most of them on the floor.
     *
     * Distance follows deflection, which is what makes it a stick rather than a
     * button — a nudge moves a line, burying it moves a page.
     */
    private fun startScrollLoop() {
        if (scrollLoop?.isActive == true) return
        scrollLoop = scope.launch {
            while (isActive && mouse.isActive && scrollStick != 0f) {
                val position = mouse.state.value.position
                if (position == null) {
                    delay(SCROLL_REPEAT_MS)
                    continue
                }
                // Pushing the stick down scrolls down, which is the direction the
                // content moves rather than the direction the finger does.
                swipe(position, SCROLL_DISTANCE_PX * scrollStick)
                delay(SCROLL_REPEAT_MS)
            }
        }
    }

    private fun stopScrollLoop() {
        scrollLoop?.cancel()
        scrollLoop = null
        scrollStick = 0f
    }

    private fun startStickLoop() {
        if (stickLoop?.isActive == true) return
        stickLoop = scope.launch {
            var previousFrame = SystemClock.uptimeMillis()
            while (isActive && mouse.isActive && (stickX != 0f || stickY != 0f)) {
                delay(STICK_FRAME_MS)
                val now = SystemClock.uptimeMillis()
                val elapsedSeconds = ((now - previousFrame).coerceIn(1L, MAX_STICK_FRAME_MS)) / 1_000f
                previousFrame = now
                mouse.moveByStick(stickX, stickY, elapsedSeconds)
            }
        }
    }

    private fun stopStickLoop() {
        stickLoop?.cancel()
        stickLoop = null
        stickX = 0f
        stickY = 0f
    }

    /** Stops stale movement before it can move a newly raised pointer. */
    private fun cancelMovement() {
        heldDirections.values.forEach(Job::cancel)
        heldDirections.clear()
        stopAllStickInput()
    }

    /**
     * Ends both stick loops together.
     *
     * They are driven by one stream and are lost together: whatever stops the
     * pad's deflections reaching this service — the overlay losing focus, the
     * window going away, the pointer being put down — leaves *both* sticks
     * holding whatever they last reported. One of them running on is a cursor
     * that walks itself off the screen; the other is a page that scrolls forever.
     */
    private fun stopAllStickInput() {
        stopStickLoop()
        stopScrollLoop()
    }

    private fun perform(action: MouseAction) {
        val position = mouse.state.value.position ?: return
        when (action) {
            MouseAction.PRIMARY_CLICK -> tap(position, TAP_MS)
            MouseAction.SECONDARY_CLICK -> tap(position, LONG_PRESS_MS)
            MouseAction.SCROLL_UP -> swipe(position, -SCROLL_DISTANCE_PX)
            MouseAction.SCROLL_DOWN -> swipe(position, SCROLL_DISTANCE_PX)
            /*
             * Whichever Back the thing on screen actually has.
             *
             * The system's Back over another app, the launcher's own over THOR.
             * They are not interchangeable: `LauncherActivity` is a home activity,
             * so `GLOBAL_ACTION_BACK` aimed at it is a press with nowhere to go —
             * and because this service owns the pointer's buttons whenever it is
             * connected, the launcher had already swallowed B by the time that
             * no-op ran. B did nothing at all inside THOR as a result, while
             * working perfectly everywhere else.
             *
             * Same test as the keyboard below, for the same reason: what a bound
             * button should do depends on where it would land.
             */
            MouseAction.BACK -> if (mouse.launcherForeground) {
                mouse.requestBack()
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
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
            /*
             * Raised whatever is in front, because it no longer only fills in
             * Loki's own fields.
             *
             * The keyboard is drawn on Loki's panel — which on a two-screen
             * handheld is still on screen while an app holds the other one — and
             * what is typed is written into whichever field the cursor last
             * tapped, in whatever app owns it. Seeded from that field so editing
             * a URL that is already there edits it rather than replacing it.
             */
            MouseAction.OPEN_KEYBOARD -> {
                mouse.keyboardSeed = focusedFieldText().orEmpty()
                mouse.setTypedText(mouse.keyboardSeed)
                mouse.requestKeyboard()
            }
            MouseAction.TOGGLE_OFF -> mouse.setActive(false)
            MouseAction.NONE -> Unit
        }
    }

    /**
     * The text of the field the user has selected, in whatever app owns it.
     *
     * `findFocus(FOCUS_INPUT)` asks the system which node holds *input* focus,
     * which is the field a keyboard would type into — as opposed to accessibility
     * focus, which is where a screen reader is pointing and is not the same
     * place. Null when nothing is being edited, which is the ordinary case and
     * not a failure.
     */
    private fun focusedFieldText(): String? = runCatching {
        // Not recycled: node pooling was removed from the platform and `recycle`
        // is deprecated for it, so these are ordinary objects the collector takes.
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.takeIf { it.isEditable }
            ?.text
            ?.toString()
    }.getOrElse { error ->
        ThorLog.w(TAG, "Could not read the focused field: ${error.message}")
        null
    }

    /**
     * Writes [text] into the field the user has selected.
     *
     * The whole buffer every time rather than the character just pressed, which
     * is what makes backspace, shift and the symbol layer work without this
     * knowing any of them exist — the keyboard owns the editing and this owns
     * only the delivery.
     *
     * Does nothing when nothing is focused, which is what happens while the
     * keyboard is filling in one of Loki's own fields: that text is delivered by
     * the launcher's own text focus, and the two paths never both fire because
     * a field inside Loki does not hold Android's input focus.
     */
    private fun typeIntoFocusedField(text: String) {
        runCatching {
            val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
            if (!node.isEditable) return
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text,
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.onFailure { error ->
            // A field that refuses the action is a field that cannot be filled in
            // this way — a canvas-drawn one, or a password field that will not say
            // what it holds. Reported rather than retried.
            ThorLog.w(TAG, "Could not type into the focused field: ${error.message}")
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

    /** Whether Android is currently in its own dark mode, for [ThemeMode.SYSTEM]. */
    private fun systemInDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val TAG = "Pointer"

        /** Ignored by the PNG encoder, which is lossless; required by the call. */
        const val PNG_QUALITY = 100

        /** Long enough for a removed overlay to stop being composited. */
        const val OVERLAY_SETTLE_MS = 120L

        /** About a tenth of the range, which is one perceptible notch. */
        const val BRIGHTNESS_STEP = 25
        const val MIN_BRIGHTNESS = 1
        const val MAX_BRIGHTNESS = 255
        const val TAP_MS = 40L
        const val LONG_PRESS_MS = 600L
        const val SCROLL_MS = 220L
        const val SCROLL_DISTANCE_PX = 420f
        const val REPEAT_DELAY_MS = 260L
        const val REPEAT_INTERVAL_MS = 16L

        const val STICK_FRAME_MS = 16L
        const val MAX_STICK_FRAME_MS = 64L
        const val STICK_DEAD_ZONE = 0.22f
        const val HAT_THRESHOLD = 0.5f

        /**
         * Where the right stick's vertical axis might be, best first.
         *
         * Checked against the device's reported range rather than taken on faith
         * — see `rightStickY`, which is what stops a trigger sharing one of these
         * names from being read as a stick held downward.
         */
        val SCROLL_AXES = intArrayOf(MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)

        /**
         * Larger than the cursor's own dead zone, deliberately.
         *
         * A right stick that is only resting near centre should not creep the
         * page, and scrolling has no equivalent of the fine cursor nudge that
         * makes a tight dead zone worth having on the left one.
         */
        const val SCROLL_DEAD_ZONE = 0.3f

        /** Paced to the gesture: overlapping strokes are refused, not queued. */
        const val SCROLL_REPEAT_MS = SCROLL_MS

        /** A compact reticle stays legible without covering the target. */
        const val MIN_CURSOR_SIZE_DP = 28
    }
}
