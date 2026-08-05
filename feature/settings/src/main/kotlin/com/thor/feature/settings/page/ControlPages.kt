package com.thor.feature.settings.page

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.thor.core.model.MouseAction
import com.thor.core.model.MouseButton
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.IntSliderRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.SettingsViewModel

/**
 * The controller pointer.
 *
 * The permission row comes second, right under the switch, because the feature
 * does nothing without it and there is no dialog to ask — the user has to be told
 * plainly and taken to the right screen.
 */
@Composable
internal fun PointerPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    serviceEnabled: Boolean,
    pointerRunning: Boolean,
) {
    val mouse = settings.mouse

    // The one thing that can change while this page is open is the permission,
    // and only by leaving for system settings and coming back.
    LaunchedEffect(Unit) { viewModel.refreshPointerService() }

    SwitchRow(
        title = "Controller pointer",
        subtitle = "Hold Start and Select to raise a cursor. Works inside Loki " +
            "straight away; see below to use it in other apps.",
        checked = mouse.enabled,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateMouse { it.copy(enabled = on) } },
    )
    RowDivider()

    /*
     * The permission is for *other apps only*, and saying so matters.
     *
     * Inside THOR the pointer needs nothing: the launcher sees its own buttons and
     * owns its own windows. Presenting accessibility access as "required" made the
     * whole feature look broken until it was granted, when in fact the half most
     * people want was already working.
     */
    /*
     * Always an action, never just a status line.
     *
     * Accessibility access cannot be requested — Android shows no dialog for it,
     * so nothing will ever prompt and the row has to be the way in. It stays
     * pressable once granted too, because the other reason the pointer does
     * nothing outside THOR is the service being switched off again, and the same
     * screen is where that is fixed.
     */
    ActionRow(
        title = "Use the pointer in other apps",
        subtitle = when {
            pointerRunning ->
                "Working. The pointer can be used in games and apps."

            serviceEnabled ->
                "Granted, but the service is not running yet. Try switching it off " +
                    "and on again in Accessibility."

            else ->
                "Not granted. Android shows no prompt for this — open Accessibility " +
                    "and turn on “Controller pointer”. Until then the pointer works " +
                    "inside Loki only."
        },
        focused = focusedRow == 1,
        trailingLabel = if (pointerRunning) "Accessibility" else "Open",
        onClick = viewModel::openPointerServiceSettings,
    )
    RowDivider()

    IntSliderRow(
        title = "Pointer speed",
        subtitle = "Pixels per second at full stick",
        value = mouse.speed.toInt(),
        range = SPEED_RANGE,
        focused = focusedRow == 3,
        onValueChange = { value ->
            viewModel.updateMouse { it.copy(speed = value.toFloat()) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Cross between screens",
        subtitle = "Moving off the bottom of one panel continues onto the other",
        checked = mouse.spanDisplays,
        focused = focusedRow == 4,
        onCheckedChange = { on -> viewModel.updateMouse { it.copy(spanDisplays = on) } },
    )

    /*
     * One row per button.
     *
     * Every button is listed, including the unbound ones, so the page is a map of
     * the controller rather than a list of the choices already made — otherwise
     * there is no way to discover that a button *could* be bound.
     */
    MouseButton.entries.forEachIndexed { index, button ->
        RowDivider()
        ChoiceRow(
            title = button.label,
            options = MouseAction.entries,
            selected = mouse.actionFor(button),
            label = MouseAction::label,
            focused = focusedRow == POINTER_FIXED_ROWS + index,
            onSelected = { action ->
                viewModel.updateMouse { current ->
                    current.copy(bindings = current.bindings + (button to action))
                }
            },
        )
    }
}

/** Enable, permission, speed and span, before the per-button rows. */
internal const val POINTER_FIXED_ROWS = 4
private val SPEED_RANGE = 400..3_000

@Composable
internal fun NavigationPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val controls = settings.controls

    SwitchRow(
        title = "Wrap at edges",
        subtitle = "Moving past the last column returns to the first",
        checked = controls.wrapNavigation,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateControls { it.copy(wrapNavigation = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Edge turns the page",
        checked = controls.edgeFlipsPage,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateControls { it.copy(edgeFlipsPage = on) } },
    )
    RowDivider()
    SliderRow(
        title = "Stick sensitivity",
        value = controls.stickSensitivity,
        range = 0.5f..2f,
        focused = focusedRow == 2,
        valueLabel = { "${"%.1f".format(it)}x" },
        onValueChange = { value -> viewModel.updateControls { it.copy(stickSensitivity = value) } },
    )
    RowDivider()
    SwitchRow(
        title = "Touch input",
        subtitle = "Off makes the launcher controller-only",
        checked = controls.touchEnabled,
        focused = focusedRow == 3,
        onCheckedChange = { on -> viewModel.updateControls { it.copy(touchEnabled = on) } },
    )
}

@Composable
internal fun FeedbackPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val controls = settings.controls
    val audio = settings.audio

    SwitchRow(
        title = "Haptics",
        checked = controls.hapticsEnabled,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateControls { it.copy(hapticsEnabled = on) } },
    )
    RowDivider()
    SliderRow(
        title = "Haptic intensity",
        value = controls.hapticIntensity,
        range = 0f..1f,
        focused = focusedRow == 1,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { value -> viewModel.updateControls { it.copy(hapticIntensity = value) } },
    )
    RowDivider()
    SwitchRow(
        title = "Sound effects",
        subtitle = "Plays at the system media volume",
        checked = audio.soundEffectsEnabled,
        focused = focusedRow == 2,
        onCheckedChange = { on ->
            viewModel.updateAudio { it.copy(soundEffectsEnabled = on) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Navigation sounds",
        subtitle = "Cursor ticks and page turns, not just launches",
        checked = audio.navigationSounds,
        focused = focusedRow == 3,
        onCheckedChange = { on ->
            // Both flags move together: the settings screen offers one switch,
            // and leaving launch sounds on while navigation sounds are off would
            // be a state the user could not see or explain.
            viewModel.updateAudio { it.copy(navigationSounds = on, launchSounds = on) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Sound effect volume",
        value = audio.uiVolume,
        range = 0f..1f,
        focused = focusedRow == 4,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { volume -> viewModel.updateAudio { it.copy(uiVolume = volume) } },
    )
}
