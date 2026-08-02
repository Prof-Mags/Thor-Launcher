package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A logical control, independent of which physical button produces it.
 *
 * The input layer translates raw key codes into these, so every screen reacts
 * to `Confirm` rather than to `KEYCODE_BUTTON_A`, and remapping is a matter of
 * changing one [ControllerProfile].
 */
@Serializable
enum class ControllerCommand(val label: String, val description: String) {
    NAVIGATE_UP("Up", "Move the cursor up"),
    NAVIGATE_DOWN("Down", "Move the cursor down"),
    NAVIGATE_LEFT("Left", "Move the cursor left"),
    NAVIGATE_RIGHT("Right", "Move the cursor right"),
    CONFIRM("Confirm", "Launch the selection"),
    BACK("Back", "Go back / close"),
    CONTEXT_MENU("Context", "Open context actions for the selection"),
    TOGGLE_FAVORITE("Favorite", "Add or remove from favourites"),
    PAGE_PREVIOUS("Previous page", "Move one page left"),
    PAGE_NEXT("Next page", "Move one page right"),
    CYCLE_IMAGE_PREVIOUS("Previous image", "Show the game's previous screenshot"),
    CYCLE_IMAGE_NEXT("Next image", "Show the game's next screenshot"),
    OPEN_SIDE_MENU("Side menu", "Open the Start panel"),
    OPEN_APP_DRAWER("App drawer", "Open the app drawer"),

    OPEN_SHORTCUTS("Shortcuts", "Open the quick shortcut panel"),
    GO_HOME("Home", "Return to the launcher home"),
    PICK_UP("Pick up", "Grab the selected icon to move it"),
    CANCEL_EDIT("Cancel edit", "Leave edit mode"),
    SEARCH("Search", "Open global search"),
}

/**
 * A user-editable mapping from Android key codes to [ControllerCommand]s.
 *
 * A single command may be reachable from several key codes (D-pad and left
 * stick both produce `NAVIGATE_*`), so the mapping is many-to-one.
 */
@Serializable
data class ControllerProfile(
    val id: String,
    val name: String,
    /** Android `KeyEvent` key code -> command. */
    val bindings: Map<Int, ControllerCommand>,
    /** Long-press duration that promotes CONFIRM to PICK_UP, in ms. */
    val longPressMillis: Long = 400L,
    /** Delay before D-pad auto-repeat starts, in ms. */
    val repeatDelayMillis: Long = 380L,
    /** Interval between auto-repeats once started, in ms. */
    val repeatIntervalMillis: Long = 95L,
    /** Analog stick deflection needed to register a direction. */
    val stickDeadZone: Float = 0.5f,
    val isBuiltIn: Boolean = false,
) {
    fun commandFor(keyCode: Int): ControllerCommand? = bindings[keyCode]

    companion object {
        const val DEFAULT_ID = "default"
    }
}

/**
 * Motion-axis sources the input layer samples for directional navigation.
 * Values match `MotionEvent.AXIS_*` and are kept here so `:core:model` stays
 * free of Android imports.
 */
object MotionAxes {
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_Z = 11
    const val AXIS_RZ = 14
    const val AXIS_HAT_X = 15
    const val AXIS_HAT_Y = 16
    const val AXIS_LTRIGGER = 17
    const val AXIS_RTRIGGER = 18
    const val AXIS_BRAKE = 23
    const val AXIS_GAS = 22
}

/** A direction on the 2D grid. */
enum class NavDirection { UP, DOWN, LEFT, RIGHT }
