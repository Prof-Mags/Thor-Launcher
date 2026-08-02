package com.thor.core.input

import com.thor.core.model.MouseAction
import com.thor.core.model.MouseSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sign

/** One panel the pointer can be on. */
data class PointerDisplay(
    val displayId: Int,
    val widthPx: Int,
    val heightPx: Int,
    /** Where this panel sits in the stacked space; see [MouseController]. */
    val topOffsetPx: Int,
)

/** Where the pointer is, in the coordinates of the panel it is on. */
data class PointerPosition(
    val displayId: Int,
    val x: Float,
    val y: Float,
)

/** Something to do at a point: a click, a scroll, a request for the keyboard. */
data class PointerAction(val action: MouseAction, val position: PointerPosition)

/** What the pointer is doing. */
data class MouseState(
    val active: Boolean = false,
    val position: PointerPosition? = null,
    /** Bumped on every click so a renderer can flash the cursor. */
    val clickTick: Int = 0,
)

/**
 * The pointer's position and mode, shared by everything that draws or moves it.
 *
 * A singleton because there is exactly one pointer and three things need it at
 * once: the launcher, which drives it from the analogue stick while it has focus;
 * the accessibility service, which drives it from the D-pad while it does not; and
 * whichever window is drawing the cursor. State in any one of those would leave
 * the pointer jumping as focus moved between them.
 *
 * **The two panels are one surface.** Positions are held in a stacked space —
 * panel heights laid end to end, top panel first — so moving down past the bottom
 * of one continues onto the top of the next with no special case at the seam. The
 * alternative, a position per display plus a hand-off, is the same arithmetic
 * written twice and wrong once.
 */
@Singleton
class MouseController @Inject constructor() {

    private val _state = MutableStateFlow(MouseState())
    val state: StateFlow<MouseState> = _state.asStateFlow()

    private val _keyboardRequests = MutableStateFlow(0)

    /** Bumped when a bound button asks for the on-screen keyboard. */
    val keyboardRequests: StateFlow<Int> = _keyboardRequests.asStateFlow()

    private val _typedText = MutableStateFlow("")

    /**
     * What Loki's keyboard currently holds, for typing into another app.
     *
     * The keyboard itself never moves. It is drawn in Loki's own window on the
     * panel the user is holding, which on this device is still on screen while
     * an app has the other one — so the thing being filled in and the thing
     * doing the filling are simply on different screens, which is the same
     * arrangement the launcher's own fields already use.
     *
     * Only the *text* has to cross, and it crosses as the whole buffer rather
     * than as keystrokes: the field is set to this value, so backspace, shift
     * and the symbol layer all work without any of them being described here.
     */
    val typedText: StateFlow<String> = _typedText.asStateFlow()

    fun setTypedText(text: String) {
        _typedText.value = text
    }

    /**
     * The text the field already held when the keyboard was raised.
     *
     * Read from the focused field and handed to the keyboard as its starting
     * buffer, so filling in a URL bar that already has a URL in it edits that
     * URL rather than silently replacing it with whatever is typed next.
     */
    @Volatile
    var keyboardSeed: String = ""

    private val _typing = MutableStateFlow(false)

    /**
     * Whether THOR's own keyboard is up and waiting for keys.
     *
     * The pointer has to stand aside for it, and nothing else in this class does
     * that job. While the keyboard is showing, the buttons mean letters: the
     * cursor keeps its stick, but A is a key press rather than a click and the
     * D-pad walks the layout rather than nudging the pointer.
     *
     * Three things read this, because the keys are intercepted in three places
     * before they could ever reach the keyboard — the accessibility service
     * filters them first, the router swallows them next, and the focusable cursor
     * overlay holds the window focus that would carry them. All three defer while
     * this is true.
     */
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    /** True while THOR's keyboard is showing; see [typing]. */
    val launcherTyping: Boolean get() = _typing.value

    fun setLauncherTyping(value: Boolean) {
        _typing.value = value
    }

    private val _backRequests = MutableStateFlow(0)

    /**
     * Bumped when the pointer's Back is pressed while THOR is the thing on screen.
     *
     * Back means two different things depending on what is in front, and the
     * pointer has to ask for the right one. Over another app it is the system's
     * Back, which the service performs directly. Over THOR it is the *launcher's*
     * Back — close the folder, the panel, the overlay — and the system's Back is
     * no use for that at all: `LauncherActivity` is a home activity, so a global
     * Back aimed at it is a press with nothing to go back to.
     *
     * That is what made the button appear dead inside the launcher. The service
     * owns the pointer's buttons whenever it is connected, so the launcher had
     * already swallowed the press and the service then spent it on a global Back
     * that did nothing. This is the way back across that boundary, and it is the
     * same shape as [keyboardRequests], which exists for the same reason.
     */
    val backRequests: StateFlow<Int> = _backRequests.asStateFlow()

    fun requestBack() {
        _backRequests.update { it + 1 }
    }

    /**
     * Actions the pointer wants performed, wherever it currently is.
     *
     * Split from the deciding because the two halves live in different places and
     * neither can do the other's job. Whichever component saw the button decides
     * what it meant; whichever component can reach the thing under the cursor
     * carries it out — the launcher into its own windows, the accessibility
     * service into everybody else's.
     *
     * Both consumers are always listening, and the position on the request says
     * which one it is for.
     */
    private val _actions = MutableSharedFlow<PointerAction>(extraBufferCapacity = 8)
    val actions: SharedFlow<PointerAction> = _actions.asSharedFlow()

    /** The bindings currently in force, for whoever is reading the buttons. */
    val bindings: MouseSettings get() = settings

    @Volatile
    private var activityVisible = false

    @Volatile
    private var presentationVisible = false

    /**
     * Whether any of THOR's surfaces is on screen.
     *
     * Deliberately *not* derived from window focus. Focus was the wrong question
     * asked three different ways: it moves when the user touches the other panel,
     * it moves again when the pointer's own overlay comes up, and a window torn
     * down while focused never reports losing it — so the answer was routinely
     * stale in the one direction that mattered. Being on screen is a lifecycle
     * fact and a composition fact, and both of those are reported reliably.
     *
     * Used only to decide whether opening THOR's own keyboard would put it
     * somewhere the user can see. Nothing about who drives the pointer depends on
     * it any more; see [launcherOwnsPointer].
     */
    val launcherForeground: Boolean get() = activityVisible || presentationVisible

    /**
     * Whether the launcher, rather than the service, drives the pointer.
     *
     * One owner, decided by one fact: whether the service is running. Not by
     * where the cursor is and not by who holds focus.
     *
     * Splitting it per panel was a mistake that took three attempts to see. Every
     * rule involving focus depends on a signal that arrives late, or not at all
     * when a window is torn down without ever reporting the loss — and the moment
     * it was wrong the pointer went inert, which is unfixable from the front
     * because the very buttons that would dismiss it are the ones being dropped.
     * The symptom was precise: press A on something outside THOR and the cursor
     * froze until the launcher was focused again, because the click moved focus
     * to the app and the launcher then stopped receiving the stick that was
     * driving the cursor.
     *
     * The service can do everything the launcher can — `dispatchGesture` lands on
     * THOR's own windows exactly as it lands on anybody else's — so when it is
     * running there is no reason to hand anything back. When it is not running
     * the launcher does the whole job unaided, inside its own windows.
     */
    val launcherOwnsPointer: Boolean get() = !_serviceConnected.value

    /** Reports whether the launcher activity is resumed. */
    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
    }

    /** Reports whether the second panel's presentation is composed. */
    fun setPresentationVisible(visible: Boolean) {
        presentationVisible = visible
    }

    private val _serviceConnected = MutableStateFlow(false)

    /**
     * Whether the accessibility service is actually running.
     *
     * Distinct from the permission being granted in system settings: the two can
     * disagree while the system is starting the service, and "enabled but not
     * connected" is exactly the state worth being able to see when the pointer
     * does nothing outside the launcher.
     */
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) _serviceCursorDisplayId.value = null
    }

    /*
     * The accessibility service can be alive while the system refuses its cursor
     * overlay on a particular display.  Keep that fact separate from connection
     * state: a THOR window must continue drawing its local cursor in that case.
     */
    private val _serviceCursorDisplayId = MutableStateFlow<Int?>(null)
    val serviceCursorDisplayId: StateFlow<Int?> = _serviceCursorDisplayId.asStateFlow()

    fun setServiceCursorDisplayId(displayId: Int?) {
        _serviceCursorDisplayId.value = displayId
    }

    /**
     * Asks for [action] at the pointer's current position.
     *
     * Silently dropped when the pointer is down or has nowhere to be: an action
     * with no position is one that would land at the origin of whichever display
     * happened to be first.
     */
    fun requestAction(action: MouseAction) {
        if (!isActive) return
        val position = _state.value.position ?: return
        _actions.tryEmit(PointerAction(action, position))
    }

    @Volatile
    private var settings: MouseSettings = MouseSettings()

    /** Panels, in stacked order. Empty until the shell reports the hardware. */
    @Volatile
    private var displays: List<PointerDisplay> = emptyList()

    /** Position in the stacked space, independent of which panel it lands on. */
    private var stackedX = 0f
    private var stackedY = 0f

    /**
     * The panel the pointer is on.
     *
     * Held rather than derived every time, because with spanning off the pointer
     * has to be penned to the panel it was *already* on — and deriving it from a
     * position that has just moved past the seam gives the panel it was about to
     * escape to, which is the one place it must not be allowed to go.
     */
    private var currentDisplayId: Int? = null

    val isActive: Boolean get() = _state.value.active

    /** Whether any panel has been declared, so a caller knows to supply one. */
    val hasDisplays: Boolean get() = displays.isNotEmpty()

    fun updateSettings(settings: MouseSettings) {
        this.settings = settings
        if (!settings.enabled && isActive) setActive(false)
    }

    /**
     * Declares the panels, top first.
     *
     * Taken from the shell rather than from `DisplayManager` here for the same
     * reason the launch target is: any extra display the system reports — a
     * recorder, a cast target — would otherwise become somewhere the pointer could
     * wander off to.
     */
    fun setDisplays(panels: List<PointerDisplay>) {
        if (panels.isEmpty()) return
        displays = panels
        fromLauncher = true
        // Re-clamped only while the pointer is up. Publishing a position for a
        // pointer nobody has raised would have anything drawing from this state
        // show a cursor the moment the hardware was reported.
        if (isActive) placeOnPanels()
    }

    /** True once the launcher has declared the panels, so the service defers. */
    @Volatile
    private var fromLauncher = false

    @Volatile
    private var noPanelsListener: (() -> Unit)? = null

    /**
     * Registers a last-resort source of panels.
     *
     * Called when the pointer is raised with none declared, which is the state the
     * accessibility service starts in whenever it connects without the launcher
     * having run. Without it the first chord of a session produced a pointer that
     * existed but had nowhere to be.
     */
    fun onPanelsNeeded(listener: (() -> Unit)?) {
        noPanelsListener = listener
    }

    /**
     * Declares panels on behalf of a launcher that is not running.
     *
     * The pointer needs to know the size and stacking of the panels before it can
     * put a cursor anywhere; with no panels, raising it produced a pointer with no
     * position, which draws nothing and clicks nothing. That was survivable while
     * the only caller was the launcher's own composition — and fatal the moment
     * the point of the feature became *working outside the launcher*, because the
     * accessibility service can be connected with THOR never having composed at
     * all. It silently explained every "the chord does nothing out here" report.
     *
     * Yields to [setDisplays] whenever the launcher has spoken, because the
     * launcher's list is filtered to the two real panels while this one is
     * whatever the system happens to be listing.
     */
    fun setFallbackDisplays(panels: List<PointerDisplay>) {
        if (fromLauncher || panels.isEmpty()) return
        displays = panels
        if (isActive) placeOnPanels()
    }

    /**
     * Settles the cursor after the panels change under it.
     *
     * A pointer raised before any panel was known has no panel and sits at the
     * origin, so when the panels finally arrive it would appear jammed in the
     * top-left corner rather than where [setActive] means to put it. Re-centred in
     * that case only; a pointer that already had a panel keeps its place, because
     * a display being re-reported is not a reason to move the user's cursor.
     */
    private fun placeOnPanels() {
        if (currentDisplayId == null) centreOnFirstPanel() else clampAndPublish()
    }

    private fun centreOnFirstPanel() {
        val first = displays.firstOrNull()
        stackedX = (first?.widthPx ?: 0) / 2f
        stackedY = (first?.heightPx ?: 0) / 2f
        currentDisplayId = first?.displayId
        clampAndPublish()
    }

    /**
     * Turns the pointer on or off.
     *
     * Starting it in the middle of the first panel rather than where it was last
     * left: the pointer is raised to reach something, and the middle is the
     * shortest average distance to anywhere.
     */
    fun setActive(active: Boolean) {
        if (active && !settings.enabled) return
        if (active == _state.value.active) return

        if (active) {
            val first = displays.firstOrNull()
            stackedX = (first?.widthPx ?: 0) / 2f
            stackedY = (first?.heightPx ?: 0) / 2f
            currentDisplayId = first?.displayId
        } else {
            currentDisplayId = null
        }
        _state.update { it.copy(active = active, position = if (active) resolve() else null) }

        // Raised before anything reported the hardware: the pointer has no panel
        // and therefore no position, so it would draw nothing and click nothing.
        // Whoever can answer is asked, and `placeOnPanels` finishes the job.
        if (active && displays.isEmpty()) noPanelsListener?.invoke()
    }

    fun toggle() = setActive(!isActive)

    /**
     * Moves the pointer by a stick deflection, over [deltaSeconds].
     *
     * @param x -1..1, @param y -1..1
     */
    fun moveByStick(x: Float, y: Float, deltaSeconds: Float) {
        if (!isActive) return
        val settings = this.settings
        move(
            dx = curve(x) * settings.speed * deltaSeconds,
            dy = curve(y) * settings.speed * deltaSeconds,
        )
    }

    /** Moves the pointer by a fixed step, for the D-pad. */
    fun moveByStep(dx: Float, dy: Float) {
        if (!isActive) return
        val step = settings.speed * STEP_SECONDS
        move(dx * step, dy * step)
    }

    /** Asks whoever is listening to raise the on-screen keyboard. */
    private val _powerMenuRequests = MutableStateFlow(0)

    /**
     * Bumped when the launcher wants the system power dialog.
     *
     * Routed through here because the accessibility service is the only part of
     * THOR that can raise it — `performGlobalAction` is an accessibility API and
     * there is no public intent for the power menu. The dock has offered this
     * action since the beginning and it did nothing at all, because the shell had
     * no way to carry out what the user had picked.
     */
    val powerMenuRequests: StateFlow<Int> = _powerMenuRequests.asStateFlow()

    fun requestPowerMenu() {
        _powerMenuRequests.update { it + 1 }
    }

    fun requestKeyboard() {
        _keyboardRequests.update { it + 1 }
    }

    /** Reports a click, so the cursor can acknowledge it. */
    fun notifyClicked() {
        _state.update { it.copy(clickTick = it.clickTick + 1) }
    }

    private fun move(dx: Float, dy: Float) {
        stackedX += dx
        stackedY += dy
        clampAndPublish()
    }

    /**
     * Applies acceleration to a stick deflection.
     *
     * Raising the magnitude to a power keeps the sign and the endpoints — nudging
     * the stick still barely moves, burying it still moves at [MouseSettings.speed]
     * — while everything between becomes finer. A linear stick is either too slow
     * to cross the panel or too coarse to hit a button.
     */
    private fun curve(value: Float): Float {
        val magnitude = abs(value).coerceIn(0f, 1f)
        if (magnitude == 0f) return 0f
        var shaped = magnitude
        repeat(settings.acceleration.toInt().coerceIn(1, 4) - 1) { shaped *= magnitude }
        return shaped * sign(value)
    }

    private fun clampAndPublish() {
        val panels = displays
        if (panels.isEmpty() || !isActive) return

        val width = panels.maxOf { it.widthPx }
        stackedX = stackedX.coerceIn(0f, (width - 1).toFloat())

        if (settings.spanDisplays) {
            val totalHeight = panels.sumOf { it.heightPx }
            stackedY = stackedY.coerceIn(0f, (totalHeight - 1).toFloat())
            // Whichever panel it has arrived on is now the one it is on.
            currentDisplayId = panelAt(stackedY)?.displayId ?: currentDisplayId
        } else {
            // Penned to the panel it was on *before* this move, which is why that
            // is remembered rather than recomputed from the new position.
            val panel = panels.firstOrNull { it.displayId == currentDisplayId }
                ?: panels.first().also { currentDisplayId = it.displayId }
            stackedY = stackedY.coerceIn(
                panel.topOffsetPx.toFloat(),
                (panel.topOffsetPx + panel.heightPx - 1).toFloat(),
            )
        }

        _state.update { it.copy(position = resolve()) }
    }

    /** Turns the stacked position into a panel and a point on it. */
    private fun resolve(): PointerPosition? {
        val panel = panelAt(stackedY) ?: displays.firstOrNull() ?: return null
        return PointerPosition(
            displayId = panel.displayId,
            // Clamped per panel as well, since panels may differ in width.
            x = stackedX.coerceIn(0f, (panel.widthPx - 1).toFloat()),
            y = (stackedY - panel.topOffsetPx).coerceIn(0f, (panel.heightPx - 1).toFloat()),
        )
    }

    private fun panelAt(y: Float): PointerDisplay? = displays.firstOrNull { panel ->
        y >= panel.topOffsetPx && y < panel.topOffsetPx + panel.heightPx
    } ?: displays.lastOrNull()

    private companion object {
        /** One D-pad press moves as far as the stick would in this long. */
        const val STEP_SECONDS = 0.045f
    }
}
