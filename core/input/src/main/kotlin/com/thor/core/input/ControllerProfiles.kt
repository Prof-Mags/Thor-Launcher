package com.thor.core.input

import android.view.KeyEvent
import com.thor.core.model.ControllerCommand
import com.thor.core.model.ControllerProfile

/**
 * The shipped controller mappings.
 *
 * The default profile follows the layout in THOR's specification and covers
 * both gamepad button codes and the generic D-pad codes, because Android
 * reports the AYN Thor's stick and D-pad through different key codes depending
 * on how the input device is configured.
 */
object ControllerProfiles {

    val DEFAULT: ControllerProfile = ControllerProfile(
        id = ControllerProfile.DEFAULT_ID,
        name = "Loki Default",
        isBuiltIn = true,
        bindings = buildMap {
            // Directional input. Both D-pad and hat/stick synthesised codes.
            put(KeyEvent.KEYCODE_DPAD_UP, ControllerCommand.NAVIGATE_UP)
            put(KeyEvent.KEYCODE_DPAD_DOWN, ControllerCommand.NAVIGATE_DOWN)
            put(KeyEvent.KEYCODE_DPAD_LEFT, ControllerCommand.NAVIGATE_LEFT)
            put(KeyEvent.KEYCODE_DPAD_RIGHT, ControllerCommand.NAVIGATE_RIGHT)

            // A confirms, B goes back — the Nintendo-style physical layout the
            // Thor ships with, which Android reports as BUTTON_A / BUTTON_B.
            put(KeyEvent.KEYCODE_BUTTON_A, ControllerCommand.CONFIRM)
            put(KeyEvent.KEYCODE_DPAD_CENTER, ControllerCommand.CONFIRM)
            put(KeyEvent.KEYCODE_ENTER, ControllerCommand.CONFIRM)
            put(KeyEvent.KEYCODE_BUTTON_B, ControllerCommand.BACK)
            put(KeyEvent.KEYCODE_BACK, ControllerCommand.BACK)
            put(KeyEvent.KEYCODE_ESCAPE, ControllerCommand.BACK)

            // Y opens the context menu — it is the button players reach for
            // when they want "more options", and every action the old X binding
            // exposed is reachable from that menu anyway. X keeps the one-press
            // favourite toggle.
            put(KeyEvent.KEYCODE_BUTTON_Y, ControllerCommand.CONTEXT_MENU)
            put(KeyEvent.KEYCODE_BUTTON_X, ControllerCommand.TOGGLE_FAVORITE)

            // The bumpers cycle the highlighted game's screenshots rather than
            // paging the grid — with the triggers taking over paging, no
            // shoulder button is left idle while a game with multiple images
            // is focused.
            put(KeyEvent.KEYCODE_BUTTON_L1, ControllerCommand.CYCLE_IMAGE_PREVIOUS)
            put(KeyEvent.KEYCODE_BUTTON_R1, ControllerCommand.CYCLE_IMAGE_NEXT)
            put(KeyEvent.KEYCODE_BUTTON_L2, ControllerCommand.PAGE_PREVIOUS)
            put(KeyEvent.KEYCODE_BUTTON_R2, ControllerCommand.PAGE_NEXT)

            /*
             * The shortcut panel, on the stick clicks.
             *
             * Both sticks, and nothing else uses either: a press that opens a panel
             * wants a button the user cannot hit by accident mid-game, and L3/R3 are
             * the only two on this device that are otherwise idle.
             */
            put(KeyEvent.KEYCODE_BUTTON_THUMBL, ControllerCommand.OPEN_SHORTCUTS)
            put(KeyEvent.KEYCODE_BUTTON_THUMBR, ControllerCommand.OPEN_SHORTCUTS)

            /*
             * The Thor's own AYN button, held — kept, but not the primary binding.
             *
             * Its firmware reports no vendor keycode. A short press is consumed above
             * the launcher and never arrives; a long press injects `SHIFT_RIGHT` (60)
             * from a virtual device, which nothing in the system claims. The catch is
             * that the same press *also* powers the bottom panel off, and that is
             * firmware behaviour on a button the vendor owns — no app-level launcher
             * can intercept it or countermand it. So the panel opens, on a screen
             * that has just gone dark. Left bound because it costs nothing and
             * remains useful when the grid is on the panel that stays lit.
             *
             * A paired keyboard's right shift therefore opens the panel too. That is
             * harmless: routing is suspended entirely while a field is collecting
             * text, so shift still capitalises, and outside a field a lone modifier
             * does nothing else here.
             */
            put(KeyEvent.KEYCODE_SHIFT_RIGHT, ControllerCommand.OPEN_SHORTCUTS)

            put(KeyEvent.KEYCODE_BUTTON_START, ControllerCommand.OPEN_SIDE_MENU)
            put(KeyEvent.KEYCODE_MENU, ControllerCommand.OPEN_SIDE_MENU)
            put(KeyEvent.KEYCODE_BUTTON_SELECT, ControllerCommand.OPEN_APP_DRAWER)
            put(KeyEvent.KEYCODE_BUTTON_MODE, ControllerCommand.GO_HOME)
            put(KeyEvent.KEYCODE_HOME, ControllerCommand.GO_HOME)
            put(KeyEvent.KEYCODE_SEARCH, ControllerCommand.SEARCH)

            // Keyboard equivalents, so the launcher is fully operable from a
            // paired keyboard and from the emulator during development.
            put(KeyEvent.KEYCODE_W, ControllerCommand.NAVIGATE_UP)
            put(KeyEvent.KEYCODE_S, ControllerCommand.NAVIGATE_DOWN)
            put(KeyEvent.KEYCODE_A, ControllerCommand.NAVIGATE_LEFT)
            put(KeyEvent.KEYCODE_D, ControllerCommand.NAVIGATE_RIGHT)
            put(KeyEvent.KEYCODE_F, ControllerCommand.TOGGLE_FAVORITE)
            put(KeyEvent.KEYCODE_E, ControllerCommand.CONTEXT_MENU)
            put(KeyEvent.KEYCODE_TAB, ControllerCommand.OPEN_APP_DRAWER)
        },
    )

    /** Swaps confirm and back for users who prefer the PlayStation layout. */
    val SWAPPED_AB: ControllerProfile = DEFAULT.copy(
        id = "swapped-ab",
        name = "Swapped A/B",
        bindings = DEFAULT.bindings.toMutableMap().apply {
            put(KeyEvent.KEYCODE_BUTTON_A, ControllerCommand.BACK)
            put(KeyEvent.KEYCODE_BUTTON_B, ControllerCommand.CONFIRM)
        },
    )

    val BUILT_IN: List<ControllerProfile> = listOf(DEFAULT, SWAPPED_AB)

    fun byId(id: String, custom: List<ControllerProfile>): ControllerProfile =
        custom.firstOrNull { it.id == id }
            ?: BUILT_IN.firstOrNull { it.id == id }
            ?: DEFAULT
}
