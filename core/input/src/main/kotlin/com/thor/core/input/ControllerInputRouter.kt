package com.thor.core.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.thor.core.model.ControllerCommand
import com.thor.core.model.ControllerProfile
import com.thor.core.model.MouseAction
import com.thor.core.model.MouseButton
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
    /**
     * The pointer, when one is available.
     *
     * The router's only job here is the analogue stick. Everything else the
     * pointer does — the chord that raises it, the buttons that click — is handled
     * by the accessibility service, whose `onKeyEvent` fires for THOR exactly as
     * it does for any other app, and *before* the app sees it. Duplicating that
     * here would mean two things racing to interpret the same press.
     *
     * The stick is the exception, and the reason this hook exists: accessibility
     * services are never delivered motion events, so while THOR has focus it is
     * the only component that can see the stick at all.
     */
    private val mouse: MouseController? = null,
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
     * The stick's latest deflection, sampled by the pointer's own frame loop.
     *
     * Held rather than acted on, because Android only delivers a motion event when
     * an axis *changes*. Moving the cursor on arrival therefore moved it in bursts
     * — fast while the stick was being pushed, then nothing at all while it was
     * held steady, which is the stutter. See [startPointerLoop].
     */
    private var pointerStickX = 0f
    private var pointerStickY = 0f
    private var pointerLoop: Job? = null

    /** Halves of the pointer chord currently held, and whether it has fired. */
    private var pointerStartHeld = false
    private var pointerSelectHeld = false
    private var pointerChordFired = false

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
        // The chord comes first and always, so the pointer can be raised and
        // lowered from any state — including one where something has gone wrong.
        if (handlePointerChord(keyCode, down = true)) return true

        /*
         * The pointer takes everything else while it is up.
         *
         * Its bound buttons become actions and the rest are swallowed, because a
         * press that both clicked the cursor and moved the grid underneath it
         * would do two things at once and look like it had done neither.
         *
         * Handled here rather than left to the accessibility service, and that is
         * the important part: the service is not required for any of this. THOR
         * sees its own input and owns both its windows, so inside the launcher the
         * pointer needs no permission at all. The service exists only to carry the
         * pointer *out* of the launcher.
         */
        /*
         * THOR's keyboard outranks the pointer for keys.
         *
         * This check has to come *before* the pointer, and that ordering is the
         * whole of it: the branch below swallows every key while the pointer is
         * up, so with the keyboard showing there was nothing left to type with.
         * The keyboard is driven by the commands this router emits, so consuming
         * them for the cursor left it drawn, focused and completely inert — keys
         * went to the pointer, the field stayed empty, and the only way to enter
         * text was to put the pointer away first.
         *
         * The stick is untouched, so the cursor still moves while typing. Only
         * the buttons change hands, which is what makes A a key rather than a
         * click for as long as there is a key under it.
         */
        val pointer = mouse
        if (pointer != null && pointer.isActive && !pointer.launcherTyping) {
            // Acted on only when the launcher owns the pointer. Swallowed either
            // way: a press that both clicked the cursor and moved the grid
            // underneath it would do two things and look like it had done none.
            if (pointer.launcherOwnsPointer) {
                val button = pointerButtonFor(keyCode)
                if (button != null && event.repeatCount == 0) {
                    performPointerAction(pointer, pointer.bindings.actionFor(button))
                }
            }
            return true
        }

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
        if (handlePointerChord(keyCode, down = false)) return true
        // Matched to the press, or a release would still fire the command whose
        // press the pointer swallowed — and matched to the typing exception
        // above it too, so a key the keyboard received is released to it as well.
        if (mouse?.isActive == true && mouse?.launcherTyping != true) return true
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

        /*
         * A mouse wheel walks the shelf.
         *
         * Ahead of the joystick guard because a wheel is a pointer source, not a
         * joystick, and was being dropped by it — the launcher took keyboard and
         * pad input on a screen a mouse could reach but not drive.
         *
         * Left and right rather than up and down, which reads backwards until you
         * see what the wheel is over: couch mode shows one rail at a time and the
         * games in it run across, so a wheel notch moves along that rail. Up and
         * down are how you change rails, and they are already on the pad and the
         * arrow keys.
         */
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL) +
                event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (abs(scroll) < SCROLL_THRESHOLD) return false
            emitCommand(
                if (scroll > 0f) {
                    ControllerCommand.NAVIGATE_LEFT
                } else {
                    ControllerCommand.NAVIGATE_RIGHT
                },
            )
            return true
        }

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

        /*
         * The pointer takes the stick whole while it is up.
         *
         * The event only records the deflection; the movement is done by a clock.
         * Motion events arrive when an axis changes and not otherwise, so a stick
         * pushed and then held produces a burst and then silence — a cursor moved
         * on arrival lurches and stalls with it. Sampling a held value on a fixed
         * frame gives the smooth travel, and makes speed a property of the setting
         * rather than of the device's sample rate.
         */
        val pointer = mouse
        if (pointer != null && pointer.isActive) {
            val x = if (abs(hatX) > 0.5f) hatX else stickX
            val y = if (abs(hatY) > 0.5f) hatY else stickY
            pointerStickX = if (abs(x) < deadZone) 0f else x
            pointerStickY = if (abs(y) < deadZone) 0f else y
            startPointerLoop()
            return true
        }

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

    /**
     * Drives the cursor from the held stick, on a fixed frame.
     *
     * Runs only while the pointer is up and the stick is off centre, and stops
     * itself as soon as either stops being true — a stick returning to centre
     * always produces the motion event that reports it, so nothing has to poll for
     * the end. Elapsed time is measured rather than assumed so a frame the system
     * delays does not lose the distance it was owed, and capped so one long stall
     * cannot fling the cursor across the panel.
     */
    private fun startPointerLoop() {
        val pointer = mouse ?: return
        if (pointerLoop?.isActive == true) return

        pointerLoop = scope.launch {
            var last = SystemClock.uptimeMillis()
            while (isActive) {
                delay(FRAME_MS)

                val x = pointerStickX
                val y = pointerStickY
                if (!pointer.isActive || (x == 0f && y == 0f)) return@launch

                val now = SystemClock.uptimeMillis()
                val elapsed = ((now - last).coerceIn(1L, MAX_FRAME_MS)) / 1000f
                last = now
                pointer.moveByStick(x, y, elapsed)
            }
        }
    }

    private fun stopPointerLoop() {
        pointerLoop?.cancel()
        pointerLoop = null
        pointerStickX = 0f
        pointerStickY = 0f
    }

    /**
     * Start + Select, held together, raises and lowers the pointer.
     *
     * Fires once per press of the pair rather than repeatedly while both are
     * down, which would toggle several times a second. Returns true for either
     * half while the pointer is up, so the launcher does not also open the Start
     * panel on the way out.
     */
    private fun handlePointerChord(keyCode: Int, down: Boolean): Boolean {
        val pointer = mouse ?: return false

        /*
         * The service owns the chord whenever it is running.
         *
         * It sees key events before any app does, so when it is connected this
         * code is unreachable for the chord anyway — but declining explicitly is
         * what makes "exactly one of them acts" true by construction rather than
         * by dispatch order. Two handlers toggling the same chord cancel out, and
         * a pointer that will not switch off is worse than one that will not
         * switch on.
         */
        if (pointer.serviceConnected.value) return false

        val isStart = keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU
        val isSelect = keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE
        if (!isStart && !isSelect) return false

        if (isStart) pointerStartHeld = down
        if (isSelect) pointerSelectHeld = down

        if (pointerStartHeld && pointerSelectHeld && !pointerChordFired) {
            pointerChordFired = true
            pointer.toggle()
            return true
        }
        if (!pointerStartHeld && !pointerSelectHeld) pointerChordFired = false

        return pointer.isActive
    }

    /**
     * Carries out a pointer action inside the launcher.
     *
     * Three of them are not about the place the cursor is sitting, and sending
     * them out on a flow that carries a position was the reason they did nothing:
     * the only consumer of that flow is the layer drawing the cursor, which can
     * dispatch a touch and not much else — so a request for the keyboard arrived
     * somewhere with no keyboard to open and was quietly dropped. They are done
     * here instead, where the launcher's own machinery is already to hand.
     *
     * The rest do depend on where the cursor is, and still go out to the window
     * that can reach whatever is under it.
     */
    private fun performPointerAction(pointer: MouseController, action: MouseAction) {
        when (action) {
            MouseAction.NONE -> Unit
            MouseAction.OPEN_KEYBOARD -> pointer.requestKeyboard()
            MouseAction.TOGGLE_OFF -> pointer.setActive(false)
            // The launcher's own Back, not the system's: inside THOR this should
            // close the open panel, which is what every other Back press does.
            MouseAction.BACK -> emit(ControllerEvent(ControllerCommand.BACK))
            else -> pointer.requestAction(action)
        }
    }

    /** The controller button a keycode is, for the pointer's bindings. */
    private fun pointerButtonFor(keyCode: Int): MouseButton? = when (keyCode) {
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

    /** Releases every held key. Call when the launcher loses window focus. */
    fun releaseAll() {
        heldDirections.values.forEach(Job::cancel)
        heldDirections.clear()
        cancelLongPressWatch()
        longPressFired = false
        stickDirection = null
        triggersHeld = false
        // A stick held at the moment focus moved away is never seen to return to
        // centre, so the cursor would keep travelling against a window that can no
        // longer see the stick at all.
        stopPointerLoop()
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

    /**
     * Injects a command that did not come from this window's input stream.
     *
     * For input the launcher has to act on but never sees: the pointer's buttons
     * are read by the accessibility service while it is connected, so a press
     * that means something to the launcher rather than to the system — Back, for
     * one — arrives from the service instead of from a key event.
     *
     * Deliberately the same [events] stream rather than a second channel. Every
     * surface that already knows what to do with a command keeps working without
     * being told there is now more than one way for one to arrive, and the
     * feedback cue, the focus rules and the overlay handling all come for free.
     */
    fun emitCommand(command: ControllerCommand) {
        emit(ControllerEvent(command))
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

        /**
         * How far a wheel has to turn to count as one step.
         *
         * A trackpad reports fractional scroll continuously, and without a floor
         * a resting finger walks the shelf on its own.
         */
        const val SCROLL_THRESHOLD = 0.4f

        /**
         * Ceiling on one pointer frame.
         *
         * The first motion event after the pointer is raised is measured against a
         * stale timestamp, which without a cap would move the cursor the width of
         * the panel in a single step.
         */
        const val MAX_FRAME_MS = 64L

        /**
         * The pointer's frame interval.
         *
         * Faster than the display refreshes on purpose: the cursor's position is
         * then always at most one of these stale when a frame is drawn, rather
         * than beating against the refresh and losing one move in every few.
         */
        const val FRAME_MS = 8L
    }
}
