package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.ColorBlindMode
import com.thor.core.model.CouchWallpaperStyle
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DualScreenMode
import com.thor.core.model.RecordingAudio
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.SettingsViewModel

@Composable
internal fun DualScreenPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val display = settings.display

    ChoiceRow(
        title = "Screen mode",
        subtitle = "Automatic uses the second panel when one is attached. " +
            "Couch mode puts everything on the top screen and turns the bottom " +
            "one off, for a docked device you are sitting away from.",
        options = DualScreenMode.entries,
        selected = display.mode,
        focused = focusedRow == 0,
        label = DualScreenMode::label,
        onSelected = { mode -> viewModel.updateDisplay { it.copy(mode = mode) } },
    )
    RowDivider()
    SwitchRow(
        title = "Couch mode on a monitor",
        subtitle = if (display.mode == DualScreenMode.AUTO) {
            "Switches to Couch mode on its own when a monitor is plugged in"
        } else {
            "Only applies on Automatic — Screen mode is set to " +
                "${display.mode.label.lowercase()}"
        },
        checked = display.couchOnExternalDisplay,
        focused = focusedRow == 1,
        onCheckedChange = { on ->
            viewModel.updateDisplay { it.copy(couchOnExternalDisplay = on) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Couch UI size",
        subtitle = "Make the complete Couch Mode interface smaller or larger",
        value = display.couchUiScale,
        range = DisplaySettings.MIN_COUCH_UI_SCALE..DisplaySettings.MAX_COUCH_UI_SCALE,
        focused = focusedRow == 2,
        valueLabel = { "%.0f%%".format(it * 100f) },
        onValueChange = { scale ->
            viewModel.updateDisplay { it.copy(couchUiScale = scale) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Couch background",
        subtitle = "What Couch mode draws behind its dashboard. All of these are " +
            "drawn rather than loaded, tinted by the highlighted system, and slow " +
            "enough to sit behind something you are reading. Match launcher hands " +
            "it back to the wallpaper the rest of the launcher uses.",
        options = CouchWallpaperStyle.entries,
        selected = display.couchWallpaper,
        focused = focusedRow == 3,
        label = CouchWallpaperStyle::label,
        onSelected = { style -> viewModel.updateDisplay { it.copy(couchWallpaper = style) } },
    )
    RowDivider()
    SwitchRow(
        title = "Swap screens",
        subtitle = if (display.mode == DualScreenMode.COUCH) {
            "Not used in couch mode — only one screen is in play"
        } else {
            "Put the grid on the main panel instead"
        },
        checked = display.swapScreens,
        focused = focusedRow == 4,
        onCheckedChange = { on -> viewModel.updateDisplay { it.copy(swapScreens = on) } },
    )
    RowDivider()
    SliderRow(
        title = "Split ratio",
        subtitle = "How much of the screen the info panel takes, when one screen " +
            "is showing both",
        value = display.splitRatio,
        range = 0.25f..0.75f,
        focused = focusedRow == 5,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { ratio -> viewModel.updateDisplay { it.copy(splitRatio = ratio) } },
    )
    RowDivider()
    SwitchRow(
        title = "Keep screen awake",
        checked = display.keepTopScreenAwake,
        focused = focusedRow == 6,
        onCheckedChange = { on -> viewModel.updateDisplay { it.copy(keepTopScreenAwake = on) } },
    )
}

@Composable
internal fun RecordingPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    ChoiceRow(
        title = "Sound",
        /*
         * Says what the microphone will actually pick up.
         *
         * On a handheld the speakers are a hand's width from the microphone, so
         * "Microphone" records the game — and the room, and you. Somebody
         * choosing it expecting clean game audio and getting a recording of their
         * living room has been misled by one word, and the fix is the sentence
         * underneath it. See [RecordingAudio] for why clean game audio is not on
         * this list at all.
         */
        subtitle = settings.recording.audio.description,
        options = RecordingAudio.entries,
        selected = settings.recording.audio,
        focused = focusedRow == 0,
        label = RecordingAudio::label,
        onSelected = { choice -> viewModel.updateRecording { it.copy(audio = choice) } },
    )
}

@Composable
internal fun PerformancePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val performance = settings.performance

    SwitchRow(
        title = "Performance mode",
        subtitle = "Disables blur and animated wallpaper in one switch",
        checked = performance.performanceMode,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updatePerformance { it.copy(performanceMode = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Animations",
        checked = performance.animationsEnabled,
        focused = focusedRow == 1,
        onCheckedChange = { on ->
            viewModel.updatePerformance { it.copy(animationsEnabled = on) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Background blur",
        checked = performance.blurEnabled,
        focused = focusedRow == 2,
        onCheckedChange = { on -> viewModel.updatePerformance { it.copy(blurEnabled = on) } },
    )
}

@Composable
internal fun AccessibilityPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val accessibility = settings.accessibility

    SwitchRow(
        title = "High contrast",
        // Says what it does now that Contrast is a dial on the Theme page: this is
        // the top of that dial, reachable from here without hunting for it, and it
        // wins while it is on — so the two controls cannot appear to disagree.
        subtitle = "Forces the palette to maximum contrast, overriding the Contrast " +
            "setting on the Theme page",
        checked = accessibility.highContrast,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateAccessibility { it.copy(highContrast = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Large text",
        checked = accessibility.largeText,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateAccessibility { it.copy(largeText = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Reduce motion",
        subtitle = "Removes transitions and idle animation",
        checked = accessibility.reduceMotion,
        focused = focusedRow == 2,
        onCheckedChange = { on -> viewModel.updateAccessibility { it.copy(reduceMotion = on) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Colour vision",
        options = ColorBlindMode.entries,
        selected = accessibility.colorBlindMode,
        focused = focusedRow == 3,
        label = ColorBlindMode::label,
        onSelected = { mode -> viewModel.updateAccessibility { it.copy(colorBlindMode = mode) } },
    )
    RowDivider()
    SliderRow(
        title = "Touch target size",
        value = accessibility.touchTargetScale,
        range = 1f..1.6f,
        focused = focusedRow == 4,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale ->
            viewModel.updateAccessibility { it.copy(touchTargetScale = scale) }
        },
    )
}
