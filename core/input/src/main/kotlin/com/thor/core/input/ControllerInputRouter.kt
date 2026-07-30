package com.thor.core.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.thor.core.model.ControllerCommand
import com.thor.core.model.ControllerProfile
import com.thor.core.model.NavDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A raw key press, reported for diagnostics rather than acted on.
 *
 * Exists to answer "what does this button actually send?" — vendor buttons on
 * handhelds report anything from a standard gamepad code to a manufacturer
 * keycode outside the documented range, and some are swallowed by a system app
 * before any launcher sees them. Guessing is not possible; observing is.
 */
data class RawKeyPress(
    val keyCode: Int,
    /** `KeyEvent`'s own name for the code, or the number when it has none. */
    val keyName: String,
    val deviceName: String?,
    /** Whether the active profile already maps this code to something. */
    val boundTo: ControllerCommand?,
)

/** One dispatched control input. */
data class ControllerEvent(
    val command: ControllerCommand,
    /** True when produced by auto-repeat rather than an initial press. */
    val isRepeat: Boolean = false,
    /** True when a trigger was held, meaning "do this faster/further". */
    val accelerated: Boolean = false,
)

/**
 * Turns raw Android input into [ControllerEvent]s.
 *
 * Three things make controller navigation feel right, and all three live here
 * rather than in the UI:
 *
 *  - **Custom auto-repeat.** Android's key repeat rate is a system setting and
 *    is far too slow for grid navigation, so held directions are re-emitted on
 *    our own schedule from [ControllerProfile].
 *  - **Long-press promotion.** `CONFIRM` is only dispatched on release, because
 *    holding it means "pick up this icon" instead. The press is therefore
 *    deferred, not duplicated.
 *  - **Analog edge detection.** A stick held past the dead zone must produce
 *    one press and then repeats, not a flood of events at the sample rate.
 */
class ControllerInputRouter(
    private val scope: CoroutineScope,
) {

    private val _events = MutableSharedFlow<ControllerEvent>(
        replay = 0,
        // Input must never be dropped, but must also never suspend the UI
        // thread; a generous buffer covers a burst of held-key repeats.
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<ControllerEvent> = _events.asSharedFlow()

    private val _profile = MutableStateFlow(ControllerProfiles.DEFAULT)
    val profile: StateFlow<ControllerProfile> = _profile.asStateFlow()

    /** Directions currently held, whether by D-pad or by stick. */
    private val heldDirections = mutableMapOf<ControllerCommand, Job>()

    private var longPressJob: Job? = null
    private var longPressFired = false

    /** Last direction reported by the analog stick, for edge detection. */
    private var stickDirection: NavDirection? = null

    /** True while either trigger is past its threshold. */
    private var triggersHeld = false

    /**
     * Suspends routing while the user is typing.
     *
     * The default profile binds letter keys — W/A/S/D for directions, E for the
     * context menu, F for favourite, Tab for the drawer — so that the launcher is
     * fully operable from a paired keyboard. Those bindings are consumed *before*
     * the focused view sees them, which meant typing "was" into the search box or
     * an API-key field moved the grid cursor and entered nothing. There is no way
     * to tell a game-pad press from a keyboard press at this level, so the screen
     * that owns the text field says when it is collecting input instead.
     */
    @Volatile
    private var textInputActive = false

    fun setProfile(profile: ControllerProfile) {
        _profile.value = profile
    }

    /**
     * Declares whether a text field is collecting input.
     *
     * Held directions are released on the way in, so a direction still down when
     * a field takes focus cannot leave its auto-repeat timer running against a
     * grid the user is no longer looking at.
     */
    fun setTextInputActive(active: Boolean) {
        if (textInputActive == active) return
        textInputActive = active
        if (active) releaseAll()
    }

    /**
     * Every key press seen while capture mode is on, whether bound or not.
     *
     * The only way to find out what a vendor button sends. A code that never
     * appears here is being consumed above the launcher — by a system app or by
     * the framework — and no app-level launcher can reach it.
     */
    private val _rawKeys = MutableSharedFlow<RawKeyPress>(extraBufferCapacity = 16)
    val rawKeys: SharedFlow<RawKeyPress> = _rawKeys.asSharedFlow()

    @Volatile
    private var captureMode = false

    /**
     * Reports presses instead of acting on them.
     *
     * Needed because a button already bound to something would otherwise navigate
     * away from the screen asking about it — a guide button mapped to Home would
     * leave settings before its code could be read.
     */
    fun setCaptureMode(active: Boolean) {
        if (captureMode == active) return
        captureMode = active
        releaseAll()
    }

    /**
     * Escape codes, which stay live during capture.
     *
     * Without an exception there would be no way off the screen: capture consumes
     * everything, and the launcher's own navigation runs through this router.
     */
    private fun isEscape(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE

    /**
     * Routes a key event, whichever window received it.
     *
     * Both of THOR's windows funnel through here. Only the focused window gets key
     * events, and on a dual-screen device the focused one is whichever panel the
     * user last touched — so the launcher's own surfaces have to be
     * interchangeable as input sources or half the device goes dead whenever an
     * app is running on the other panel.
     *
     * @return true when the event was consumed and must not propagate
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
        KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
        else -> false
    }

    /**
     * @return true when the event was consumed and must not propagate.
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Everything falls through to the focused field while typing, including
        // Back — which the text surface uses to dismiss itself.
        if (textInputActive) return false

        val profile = _profile.value

        // Reported and swallowed, so a bound button cannot navigate away from the
        // screen that is asking what it sends.
        if (captureMode && !isEscape(keyCode)) {
            if (event.repeatCount == 0) {
                _rawKeys.tryEmit(
                    RawKeyPress(
                        keyCode = keyCode,
                        keyName = KeyEvent.keyCodeToString(keyCode),
                        deviceName = event.device?.name,
                        boundTo = profile.commandFor(keyCode),
                    ),
                )
            }
            return true
        }

        val command = profile.commandFor(keyCode) ?: return false

        // Android delivers its own repeats for held keys; ours are better
        // timed, so system repeats are swallowed.
        if (event.repeatCount > 0) return true

        return when (command) {
            ControllerCommand.CONFIRM -> {
                startLongPressWatch(profile)
                true
            }

            ControllerCommand.NAVIGATE_UP,
            ControllerCommand.NAVIGATE_DOWN,
            ControllerCommand.NAVIGATE_LEFT,
            ControllerCommand.NAVIGATE_RIGHT,
            -> {
                startAutoRepeat(command, profile)
                true
            }

            else -> {
                emit(ControllerEvent(command, accelerated = triggersHeld))
                true
            }
        }
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (textInputActive) return false
        // Swallowed to match the press, or a release would still fire a command.
        if (captureMode && !isEscape(keyCode)) return true

        val profile = _profile.value
        val command = profile.commandFor(keyCode) ?: return false

        return when (command) {
            ControllerCommand.CONFIRM -> {
                cancelLongPressWatch()
                // A long press already dispatched PICK_UP; releasing must not
                // then also launch the entry.
                if (!longPressFired) emit(ControllerEvent(ControllerCommand.CONFIRM))
                longPressFired = false
                true
            }

            ControllerCommand.NAVIGATE_UP,
            ControllerCommand.NAVIGATE_DOWN,
            ControllerCommand.NAVIGATE_LEFT,
            ControllerCommand.NAVIGATE_RIGHT,
            -> {
                stopAutoRepeat(command)
                true
            }

            else -> true
        }
    }

    /**
     * Handles analog sticks, hat switches and triggers.
     *
     * @return true when the event was consumed.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // The stick would otherwise still move the cursor behind an open field.
        if (textInputActive) return false
        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        val profile = _profile.value
        val deadZone = profile.stickDeadZone

        // Hat switch is reported as an axis but behaves like a D-pad; take it
        // in preference to the stick when both are deflected.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

        val x = if (abs(hatX) > 0.5f) hatX else stickX
        val y = if (abs(hatY) > 0.5f) hatY else stickY

        triggersHeld = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > TRIGGER_THRESHOLD ||
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > TRIGGER_THRESHOLD ||
            event.getAxisValue(MotionEvent.AXIS_BRAKE) > TRIGGER_THRESHOLD ||
            event.getAxisValue(MotionEvent.AXIS_GAS) > TRIGGER_THRESHOLD

        // The dominant axis wins, so a diagonal push produces one clean
        // direction instead of two competing ones.
        val direction = when {
            abs(x) < deadZone && abs(y) < deadZone -> null
            abs(x) >= abs(y) -> if (x > 0) NavDirection.RIGHT else NavDirection.LEFT
            else -> if (y > 0) NavDirection.DOWN else NavDirection.UP
        }

        if (direction == stickDirection) return true
        stickDirection?.let { stopAutoRepeat(it.toCommand()) }
        stickDirection = direction
        direction?.let { startAutoRepeat(it.toCommand(), profile) }
        return true
    }

    /** Releases every held key. Call when the launcher loses window focus. */
    fun releaseAll() {
        heldDirections.values.forEach(Job::cancel)
        heldDirections.clear()
        cancelLongPressWatch()
        longPressFired = false
        stickDirection = null
        triggersHeld = false
    }

    private fun startAutoRepeat(command: ControllerCommand, profile: ControllerProfile) {
        heldDirections.remove(command)?.cancel()
        heldDirections[command] = scope.launch {
            emit(ControllerEvent(command, accelerated = triggersHeld))
            delay(profile.repeatDelayMillis)
            while (isActive) {
                emit(ControllerEvent(command, isRepeat = true, accelerated = triggersHeld))
                delay(profile.repeatIntervalMillis)
            }
        }
    }

    private fun stopAutoRepeat(command: ControllerCommand) {
        heldDirections.remove(command)?.cancel()
    }

    private fun startLongPressWatch(profile: ControllerProfile) {
        longPressFired = false
        longPressJob?.cancel()
        longPressJob = scope.launch {
            delay(profile.longPressMillis)
            longPressFired = true
            emit(ControllerEvent(ControllerCommand.PICK_UP))
        }
    }

    private fun cancelLongPressWatch() {
        longPressJob?.cancel()
        longPressJob = null
    }

    private fun emit(event: ControllerEvent) {
        // tryEmit cannot suspend, so input dispatch never blocks the UI thread.
        _events.tryEmit(event)
    }

    private fun NavDirection.toCommand(): ControllerCommand = when (this) {
        NavDirection.UP -> ControllerCommand.NAVIGATE_UP
        NavDirection.DOWN -> ControllerCommand.NAVIGATE_DOWN
        NavDirection.LEFT -> ControllerCommand.NAVIGATE_LEFT
        NavDirection.RIGHT -> ControllerCommand.NAVIGATE_RIGHT
    }

    private companion object {
        const val TRIGGER_THRESHOLD = 0.6f
    }
}
