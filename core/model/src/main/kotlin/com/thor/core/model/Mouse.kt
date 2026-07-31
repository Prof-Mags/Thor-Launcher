package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * What a controller button does while the pointer is up.
 *
 * Deliberately small. A pointer needs to move, press, and get out of the way;
 * everything past that is a second input system to learn, and the thing being
 * pointed at already has its own.
 */
@Serializable
enum class MouseAction(val label: String) {
    NONE("Nothing"),
    PRIMARY_CLICK("Click"),
    SECONDARY_CLICK("Long press"),
    SCROLL_UP("Scroll up"),
    SCROLL_DOWN("Scroll down"),
    OPEN_KEYBOARD("On-screen keyboard"),
    BACK("Back"),
    TOGGLE_OFF("Turn pointer off"),
}

/**
 * The buttons the pointer can be bound to.
 *
 * A closed set rather than raw keycodes, because these are the buttons every
 * controller this launcher targets actually has — and a binding screen offering
 * `KEYCODE_BUTTON_16` is a binding screen nobody can use.
 */
@Serializable
enum class MouseButton(val label: String) {
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    L1("L1"),
    R1("R1"),
    L2("L2"),
    R2("R2"),
    L3("Left stick click"),
    R3("Right stick click"),
}

/**
 * Pointer mode: a virtual cursor driven by the controller.
 *
 * Exists because a handheld running Android is constantly one tap away from
 * something no gamepad can reach — a login form, a store page, an emulator's own
 * settings — and until now the answer was to find a touchscreen.
 *
 * **What it can do depends on where it is**, and that split is a platform
 * boundary rather than a decision:
 *
 *  - *Inside THOR* the launcher sees every input, so the stick drives the cursor
 *    and any button can be bound.
 *  - *Over another app* the accessibility service filters controller keys and a
 *    transparent, focusable accessibility overlay receives the stick stream. It
 *    can therefore move and click without covering or touching the app beneath.
 */
@Serializable
data class MouseSettings(
    /**
     * Whether Start + Select raises the pointer at all.
     *
     * Off by default. The chord is otherwise free, and a pointer that appears
     * unbidden mid-game is worse than one that has to be switched on.
     */
    val enabled: Boolean = false,

    /**
     * Pointer travel in pixels per second at full stick deflection.
     *
     * Tuned rather than raw: a cursor mapped linearly to a stick is either too
     * slow to cross a panel or too fast to hit anything.
     */
    val speed: Float = 1_400f,

    /**
     * How much faster a fully-deflected stick is than a barely-moved one.
     *
     * 1 is linear. Above that, small deflections become finer and large ones
     * coarser, which is the whole reason a stick can be a usable pointer at all.
     */
    val acceleration: Float = 2.2f,

    /** Cursor size in dp. */
    val cursorSizeDp: Int = 28,

    /**
     * Whether the pointer crosses between the two panels.
     *
     * On: moving past the bottom of one panel continues onto the top of the
     * other, so the two screens are one surface to point at. Off keeps the
     * pointer on the panel it started on, which some people will prefer while a
     * game is running on the other.
     */
    val spanDisplays: Boolean = true,

    /**
     * Whether the running app keeps receiving the buttons the pointer uses.
     *
     * Off, and it should stay off: a bound button that both clicks the cursor and
     * reaches the game fires twice. Exposed because "my game reacted to a click"
     * is a symptom worth being able to explain.
     */
    val passThroughToApp: Boolean = false,

    /** Button assignments. */
    val bindings: Map<MouseButton, MouseAction> = DEFAULT_BINDINGS,
) {
    /** The action bound to [button], or [MouseAction.NONE]. */
    fun actionFor(button: MouseButton): MouseAction =
        bindings[button] ?: MouseAction.NONE

    /** Buttons the pointer consumes, which is every one bound to something. */
    val claimedButtons: Set<MouseButton>
        get() = bindings.filterValues { it != MouseAction.NONE }.keys

    companion object {
        /**
         * The default layout.
         *
         * A is click and B is back because that is what they are everywhere else
         * on this device; the shoulders scroll because a pointer over a long page
         * needs to and the stick is already spoken for.
         */
        val DEFAULT_BINDINGS: Map<MouseButton, MouseAction> = mapOf(
            MouseButton.A to MouseAction.PRIMARY_CLICK,
            MouseButton.B to MouseAction.BACK,
            MouseButton.X to MouseAction.SECONDARY_CLICK,
            MouseButton.Y to MouseAction.OPEN_KEYBOARD,
            MouseButton.R2 to MouseAction.PRIMARY_CLICK,
            MouseButton.L2 to MouseAction.SECONDARY_CLICK,
            MouseButton.R1 to MouseAction.SCROLL_DOWN,
            MouseButton.L1 to MouseAction.SCROLL_UP,
            MouseButton.L3 to MouseAction.OPEN_KEYBOARD,
            MouseButton.R3 to MouseAction.NONE,
        )
    }
}
