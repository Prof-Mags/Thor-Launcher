package com.thor.feature.settings.page

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.ClockStyle
import com.thor.core.model.CursorAnimation
import com.thor.core.model.CursorStyle
import com.thor.core.model.DockStyle
import com.thor.core.model.FolderStyle
import com.thor.core.model.InfoPanelStyle
import com.thor.core.model.GridSpec
import com.thor.core.model.HomeLayout
import com.thor.core.model.IconShape
import com.thor.core.model.MotionStyle
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.IntSliderRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.WallpaperPickerRow
import com.thor.feature.settings.SettingsViewModel
import kotlin.math.roundToInt

@Composable
internal fun WallpaperPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization
    val gridClearRow = 2
    val infoPickerRow = 2 + if (personalization.wallpaperUri != null) 1 else 0
    val infoClearRow = infoPickerRow + 1
    // Last, and after however many clear buttons the two pickers have put on
    // screen — a row index that ignored them would land the cursor on a button.
    val dimRow = infoPickerRow + 1 + if (personalization.topScreenWallpaperUri != null) 1 else 0

    ChoiceRow(
        title = "Background effect",
        subtitle = "Animated layer drawn behind both screens",
        options = AnimatedWallpaper.entries,
        selected = personalization.animatedWallpaper,
        focused = focusedRow == 0,
        label = AnimatedWallpaper::label,
        onSelected = { wallpaper ->
            viewModel.updatePersonalization { it.copy(animatedWallpaper = wallpaper) }
        },
    )
    RowDivider()
    WallpaperPickerRow(
        title = "Grid wallpaper",
        subtitle = "Image for the grid screen",
        currentUri = personalization.wallpaperUri,
        focused = focusedRow == 1,
        clearFocused = personalization.wallpaperUri != null && focusedRow == gridClearRow,
        onPicked = { uri -> viewModel.updatePersonalization { it.copy(wallpaperUri = uri) } },
    )
    RowDivider()
    WallpaperPickerRow(
        title = "Info screen wallpaper",
        subtitle = "Shown when nothing is highlighted",
        currentUri = personalization.topScreenWallpaperUri,
        focused = focusedRow == infoPickerRow,
        clearFocused = personalization.topScreenWallpaperUri != null && focusedRow == infoClearRow,
        onPicked = { uri ->
            viewModel.updatePersonalization { it.copy(topScreenWallpaperUri = uri) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Dim",
        subtitle = "Fades the whole background toward the theme's ground, so a busy " +
            "image stops competing with what is drawn over it",
        value = personalization.wallpaperDim,
        range = 0f..1f,
        focused = focusedRow == dimRow,
        valueLabel = { "${(it * 100).roundToInt()}%" },
        onValueChange = { dim ->
            viewModel.updatePersonalization { it.copy(wallpaperDim = dim) }
        },
    )
}

/** Effect, two pickers and the dim, before whatever clear buttons are showing. */
internal const val WALLPAPER_FIXED_ROWS = 4

@Composable
internal fun GridPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val grid = settings.grid
    val cards = settings.display.homeLayout == HomeLayout.PLATFORM_CARDS

    /*
     * First, because it decides whether anything below it is on screen at all.
     *
     * The rows underneath are not hidden when the flow is on, and that is
     * deliberate: they still describe the grid an opened system's games are laid
     * out on, so they are neither dead nor irrelevant — and a page that empties
     * itself when a switch is thrown makes the switch look destructive.
     */
    ChoiceRow(
        title = "Home shows",
        subtitle = settings.display.homeLayout.description,
        options = HomeLayout.entries,
        selected = settings.display.homeLayout,
        focused = focusedRow == 0,
        label = HomeLayout::label,
        onSelected = { layout -> viewModel.updateDisplay { it.copy(homeLayout = layout) } },
    )
    RowDivider()

    // One picker rather than separate column and row sliders. The two together
    // could reach a matrix with another size's spacing, which is the crowded
    // in-between state the presets exist to remove — and pinch already steps
    // through exactly this list, so the two controls now agree.
    ChoiceRow(
        title = "Layout",
        subtitle = if (cards) {
            "Used inside a system, where its games are laid out"
        } else {
            "Also reachable by pinching the grid"
        },
        options = GridSpec.PRESETS,
        selected = grid.preset,
        focused = focusedRow == 1,
        label = { it.label },
        // The dimensions belong here, not in the button: at rest the row reports
        // which preset is on, and "Comfortable  ·  5 × 4" did not fit the pill,
        // so the part that survived the cut was a bare column count.
        optionDescription = { "${it.columns} × ${it.rows}" },
        onSelected = { preset -> viewModel.updateGrid(preset::applyTo) },
    )
    RowDivider()
    SliderRow(
        title = "Icon size",
        subtitle = "Fine-tunes how much of each cell the artwork fills",
        value = grid.iconScale,
        range = GridSpec.MIN_ICON_SCALE..GridSpec.MAX_ICON_SCALE,
        focused = focusedRow == 2,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale -> viewModel.updateGrid { it.copy(iconScale = scale) } },
    )
    RowDivider()
    IntSliderRow(
        title = "Icon spacing",
        subtitle = "Percent of a cell left as gutter",
        value = grid.spacingDp,
        range = 0..48,
        focused = focusedRow == 3,
        suffix = "%",
        onValueChange = { spacing -> viewModel.updateGrid { it.copy(spacingDp = spacing) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Icon shape",
        options = IconShape.entries,
        selected = grid.iconShape,
        focused = focusedRow == 4,
        label = IconShape::label,
        onSelected = { shape -> viewModel.updateGrid { it.copy(iconShape = shape) } },
    )
    RowDivider()
    SwitchRow(
        title = "Show labels",
        checked = grid.showLabels,
        focused = focusedRow == 5,
        onCheckedChange = { on -> viewModel.updateGrid { it.copy(showLabels = on) } },
    )
}

@Composable
internal fun DockPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val dock = settings.dock

    SwitchRow(
        title = "Show dock",
        checked = dock.visible,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateDock { it.copy(visible = on) } },
    )
    RowDivider()
    SliderRow(
        title = "Size",
        value = dock.scale,
        range = 0.7f..1.4f,
        focused = focusedRow == 1,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale -> viewModel.updateDock { it.copy(scale = scale) } },
    )
    RowDivider()
    SliderRow(
        title = "Transparency",
        value = dock.backgroundAlpha,
        range = 0f..1f,
        focused = focusedRow == 2,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { alpha -> viewModel.updateDock { it.copy(backgroundAlpha = alpha) } },
    )
    RowDivider()
    SwitchRow(
        title = "Translucent background",
        subtitle = "Off makes the dock solid, which reads better over artwork",
        checked = dock.blurEnabled,
        focused = focusedRow == 3,
        onCheckedChange = { on -> viewModel.updateDock { it.copy(blurEnabled = on) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Shape",
        subtitle = "Square matches the grid's own cells",
        options = DockStyle.entries,
        selected = dock.style,
        focused = focusedRow == 4,
        label = DockStyle::label,
        onSelected = { style -> viewModel.updateDock { it.copy(style = style) } },
    )
    RowDivider()
    SwitchRow(
        title = "Auto-hide",
        subtitle = "Only show the dock when a slot is selected",
        checked = dock.autoHide,
        focused = focusedRow == 5,
        onCheckedChange = { on -> viewModel.updateDock { it.copy(autoHide = on) } },
    )
}

@Composable
internal fun CursorPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    ChoiceRow(
        title = "Style",
        options = CursorStyle.entries,
        selected = personalization.cursorStyle,
        focused = focusedRow == 0,
        label = CursorStyle::label,
        onSelected = { style -> viewModel.updatePersonalization { it.copy(cursorStyle = style) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Animation",
        options = CursorAnimation.entries,
        selected = personalization.cursorAnimation,
        focused = focusedRow == 1,
        label = CursorAnimation::label,
        onSelected = { animation ->
            viewModel.updatePersonalization { it.copy(cursorAnimation = animation) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Glow",
        value = personalization.highlightGlow,
        range = 0f..1f,
        focused = focusedRow == 2,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { glow ->
            viewModel.updatePersonalization { it.copy(highlightGlow = glow) }
        },
    )
}

/**
 * Type, motion and the furniture: everything that is neither colour nor material.
 *
 * Typeface and motion style are new here and were previously unreachable — a theme
 * declared both, so reading the launcher in a serif meant living with Linen's
 * palette, and wanting stepped, mechanical transitions meant Terminal's green. They
 * sit next to their own scale sliders, because "which face" and "how big" are one
 * question asked twice.
 */
@Composable
internal fun InterfacePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    SliderRow(
        title = "Text size",
        value = personalization.fontScale,
        range = 0.8f..1.5f,
        focused = focusedRow == 0,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale ->
            viewModel.updatePersonalization { it.copy(fontScale = scale) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Motion style",
        subtitle = "The character of every transition: its easing and its overshoot",
        options = MOTION_OPTIONS,
        selected = personalization.motionOverride,
        label = { it?.label ?: THEME_DEFAULT },
        focused = focusedRow == 1,
        onSelected = { motion ->
            viewModel.updatePersonalization { it.copy(motionOverride = motion) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Transition speed",
        subtitle = "Higher is faster",
        value = personalization.transitionSpeed,
        range = 0.5f..2f,
        focused = focusedRow == 2,
        valueLabel = { "${"%.1f".format(it)}x" },
        onValueChange = { speed ->
            viewModel.updatePersonalization { it.copy(transitionSpeed = speed) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Clock",
        options = ClockStyle.entries,
        selected = personalization.clockStyle,
        focused = focusedRow == 3,
        label = ClockStyle::label,
        onSelected = { style -> viewModel.updatePersonalization { it.copy(clockStyle = style) } },
    )
    RowDivider()
    SwitchRow(
        title = "Status bar",
        subtitle = "Clock and battery above the grid",
        checked = personalization.showStatusBar,
        focused = focusedRow == 4,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(showStatusBar = on) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Folder style",
        options = FolderStyle.entries,
        selected = personalization.folderStyle,
        focused = focusedRow == 5,
        label = FolderStyle::label,
        onSelected = { style -> viewModel.updatePersonalization { it.copy(folderStyle = style) } },
    )
    RowDivider()
    SwitchRow(
        title = "Page indicators",
        checked = personalization.showPageIndicators,
        focused = focusedRow == 6,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(showPageIndicators = on) }
        },
    )
    RowDivider()
    /*
     * How the information panel meets the artwork behind it.
     *
     * On this page rather than under Theme because it is a layout decision more
     * than a colour one: it changes where the panel ends, not what shade it is.
     */
    ChoiceRow(
        title = "Info panel edge",
        subtitle = InfoPanelStyle.entries
            .firstOrNull { it == personalization.infoPanelStyle }
            ?.description,
        options = InfoPanelStyle.entries,
        selected = personalization.infoPanelStyle,
        focused = focusedRow == 7,
        label = InfoPanelStyle::label,
        onSelected = { style ->
            viewModel.updatePersonalization { it.copy(infoPanelStyle = style) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Autoplay trailers",
        subtitle = "Play a game's trailer on the info panel while it is highlighted; " +
            "L1 or R1 shows screenshots instead",
        checked = personalization.autoplayTrailers,
        focused = focusedRow == 8,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(autoplayTrailers = on) }
        },
    )
}

/**
 * Typeface, size, motion, speed, clock, status bar, folders, indicators, panel
 * edge, trailers.
 */
internal const val INTERFACE_ROWS = 9

private val MOTION_OPTIONS: List<MotionStyle?> = listOf(null) + MotionStyle.entries
