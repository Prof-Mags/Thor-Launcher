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

    private var activityFocused = false
    private var presentationFocused = false

    /**
     * Whether the user is looking at THOR rather than at something it launched.
     *
     * Decides which half of the pointer drives it: the launcher inside its own
     * windows, the accessibility service everywhere else. Both must never act on
     * the same press — that double-toggles the chord and clicks twice.
     *
     * Taken from THOR's own windows rather than from accessibility events, and the
     * difference matters. An event-derived answer starts out as a guess, is stale
     * for as long as no window has changed, and is simply wrong at the moment the
     * service is switched on. THOR knows which of its windows holds focus without
     * being told, and *both* count: the grid usually lives in a `Presentation`, so
     * the activity having no focus does not mean the launcher is not in front.
     */
    val launcherForeground: Boolean get() = activityFocused || presentationFocused

    fun setActivityFocused(focused: Boolean) {
        activityFocused = focused
    }

    fun setPresentationFocused(focused: Boolean) {
        presentationFocused = focused
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
        displays = panels
        // Re-clamped only while the pointer is up. Publishing a position for a
        // pointer nobody has raised would have anything drawing from this state
        // show a cursor the moment the hardware was reported.
        if (panels.isNotEmpty() && isActive) clampAndPublish()
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
