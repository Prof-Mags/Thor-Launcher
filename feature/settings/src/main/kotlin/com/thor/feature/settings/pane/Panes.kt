package com.thor.feature.settings.pane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.thor.core.input.RawKeyPress
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.ClockStyle
import com.thor.core.model.ColorBlindMode
import com.thor.core.model.CursorAnimation
import com.thor.core.model.CursorStyle
import com.thor.core.model.DockStyle
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DualScreenMode
import com.thor.core.model.FolderStyle
import com.thor.core.model.GridSpec
import com.thor.core.model.IconShape
import com.thor.core.model.Platform
import com.thor.core.model.RomDirectory
import com.thor.core.model.SortOrder
import com.thor.core.model.ThorSettings
import com.thor.data.metadata.ProviderStatus
import com.thor.data.sync.ScrapeState
import com.thor.data.sync.SyncState
import com.thor.feature.settings.PlatformEmulatorOption
import com.thor.feature.settings.SettingsPage
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.ActionRow
import com.thor.feature.settings.component.AddSystemRow
import com.thor.feature.settings.component.ChoiceRow
import com.thor.feature.settings.component.ColorRow
import com.thor.feature.settings.component.DirectoryPickerRow
import com.thor.feature.settings.component.InfoRow
import com.thor.feature.settings.component.IntSliderRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.SliderRow
import com.thor.feature.settings.component.SwitchRow
import com.thor.feature.settings.component.SystemRow
import com.thor.feature.settings.component.TextFieldRow
import androidx.compose.runtime.LaunchedEffect
import com.thor.core.model.CornerStyle
import com.thor.core.model.IconPack
import com.thor.core.model.MouseAction
import com.thor.core.model.MediaSettings
import com.thor.core.model.ProfileRegistry
import com.thor.core.model.MouseButton
import com.thor.feature.settings.IconPackStatus
import com.thor.feature.settings.component.DirectoryPickerRow
import com.thor.feature.settings.component.FilePickerRow
import com.thor.feature.settings.component.ThemePreviewRow
import com.thor.feature.settings.component.WallpaperPickerRow

/**
 * The contents of every settings page.
 *
 * Each page is a flat column of rows bound directly to one slice of
 * [ThorSettings]. There is no per-page state holder, because every control is
 * already a pure function of the persisted value and writes straight back
 * through the view model.
 *
 * `focusedRow` indices must run contiguously from zero within each page —
 * a gap is a controller press that highlights nothing, and the count reported
 * by [rowCountFor] must match the highest index used.
 */
@Composable
fun SettingsPageContent(
    page: SettingsPage,
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    platformOptions: List<PlatformEmulatorOption>,
    availablePlatforms: List<Platform>,
    scanState: SyncState,
    scrapeState: ScrapeState,
    providerStatus: Map<String, ProviderStatus>,
    checkingProviders: Boolean,
    artworkOnlyProviders: Boolean,
    /** Whether anything configured can supply a landscape image. */
    noScreenshotProvider: Boolean = false,
    /** Whether the build carries ScreenScraper developer credentials. */
    screenScraperKeyMissing: Boolean = false,
    isDefaultLauncher: Boolean,
    keyCaptureEnabled: Boolean,
    capturedKeys: List<RawKeyPress>,
    iconPacks: List<IconPack>,
    iconPackStatus: IconPackStatus,
    pointerServiceEnabled: Boolean,
    pointerRunning: Boolean,
    /** What Real-Debrid said when last asked, or null if it has not been. */
    debridStatus: String?,
    /** What the last grid clear did, or null if it has not been used. */
    gridClearResult: String?,
    indexerStatus: Map<Int, String>,
    addonStatus: Map<Int, String>,
    /** What the last extension import said, or null if there has not been one. */
    extensionStatus: String?,
    /** Everyone on the device, for the profiles page. */
    profileRegistry: ProfileRegistry = ProfileRegistry.EMPTY,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        when (page) {
            SettingsPage.THEME -> ThemePage(settings, focusedRow, viewModel)
            SettingsPage.WALLPAPER -> WallpaperPage(settings, focusedRow, viewModel)
            SettingsPage.GRID -> GridPage(settings, focusedRow, viewModel)
            SettingsPage.DOCK -> DockPage(settings, focusedRow, viewModel)
            SettingsPage.CURSOR -> CursorPage(settings, focusedRow, viewModel)
            SettingsPage.INTERFACE -> InterfacePage(settings, focusedRow, viewModel)

            SettingsPage.PLATFORMS -> PlatformsPage(
                settings, focusedRow, viewModel, platformOptions, availablePlatforms, scanState,
                scrapeState,
            )
            SettingsPage.ROM_FOLDERS -> RomFoldersPage(settings, focusedRow, viewModel)
            SettingsPage.SCANNING ->
                ScanningPage(settings, focusedRow, viewModel, gridClearResult)
            SettingsPage.ICON_PACKS -> IconPacksPage(
                focusedRow, viewModel, iconPacks, iconPackStatus,
            )
            SettingsPage.METADATA -> MetadataPage(
                settings, focusedRow, viewModel, scrapeState, providerStatus,
                checkingProviders, artworkOnlyProviders, noScreenshotProvider,
                screenScraperKeyMissing,
            )
            SettingsPage.SORTING -> SortingPage(settings, focusedRow, viewModel)

            SettingsPage.MOVIES_CATALOGUE ->
                MoviesCataloguePage(
                    settings, focusedRow, viewModel, debridStatus, indexerStatus,
                    addonStatus,
                )

            SettingsPage.MOVIES_PLAYBACK ->
                MoviesPlaybackPage(settings, focusedRow, viewModel)

            SettingsPage.STREAM_QUALITY -> StreamQualityPage(settings, focusedRow, viewModel)
            SettingsPage.STREAM_CONTROLS -> StreamControlsPage(settings, focusedRow, viewModel)
            SettingsPage.STREAM_HOSTS -> StreamHostsPage(settings, focusedRow, viewModel)

            SettingsPage.NAVIGATION -> NavigationPage(settings, focusedRow, viewModel)
            SettingsPage.POINTER -> PointerPage(
                settings, focusedRow, viewModel, pointerServiceEnabled, pointerRunning,
            )
            SettingsPage.FEEDBACK -> FeedbackPage(settings, focusedRow, viewModel)

            SettingsPage.PROFILES -> ProfilesPage(profileRegistry, focusedRow, viewModel)
            SettingsPage.PROFILE_EDIT -> ProfileEditPage(profileRegistry, focusedRow, viewModel)
            SettingsPage.DUAL_SCREEN -> DualScreenPage(settings, focusedRow, viewModel)
            SettingsPage.PERFORMANCE -> PerformancePage(settings, focusedRow, viewModel)

            SettingsPage.EXTENSIONS -> ExtensionsPage(settings, focusedRow, viewModel, extensionStatus)
            SettingsPage.ACCESSIBILITY -> AccessibilityPage(settings, focusedRow, viewModel)
        }
    }
}

/**
 * Focusable rows per page.
 *
 * Kept beside the pages themselves so the two are edited together; a count that
 * overshoots produces presses that appear to do nothing.
 */
fun rowCountFor(
    page: SettingsPage,
    platformCount: Int,
    iconPackCount: Int = 0,
    /** The whole group, because its page's row count depends on two lists. */
    mediaSettings: MediaSettings = MediaSettings(),
    wallpaperClearRows: Int = 0,
    extraRomFolderCount: Int = 0,
    /** Profile rows depend on how many there are and whether the active one has a picture. */
    profileRegistry: ProfileRegistry = ProfileRegistry.EMPTY,
    activeProfileHasAvatar: Boolean = false,
): Int = when (page) {
    SettingsPage.THEME -> 5
    SettingsPage.WALLPAPER -> 3 + wallpaperClearRows
    SettingsPage.GRID -> 5
    SettingsPage.DOCK -> 6
    SettingsPage.CURSOR -> 3
    SettingsPage.INTERFACE -> 7
    // One card per platform, Add, then Scan when there is something to scan.
    SettingsPage.PLATFORMS -> platformCount + 1 + if (platformCount > 0) 1 else 0
    SettingsPage.ROM_FOLDERS -> extraRomFolderCount + 1
    SettingsPage.SCANNING -> 8
    // Two import rows, then one row per installed pack.
    SettingsPage.ICON_PACKS -> 2 + iconPackCount
    // Scrape, only-missing, trailers, check, one per provider, then four credentials.
    SettingsPage.METADATA -> PROVIDER_FIRST_ROW + PROVIDERS.size + 4
    SettingsPage.SORTING -> 2
    // Two keys and the debrid status line, then one row per indexer, then the
    // add button and the summary.
    SettingsPage.MOVIES_CATALOGUE -> moviesCatalogueRows(mediaSettings)
    SettingsPage.MOVIES_PLAYBACK -> MOVIES_PLAYBACK_ROWS
    SettingsPage.STREAM_QUALITY -> STREAM_QUALITY_ROWS
    SettingsPage.STREAM_CONTROLS -> STREAM_CONTROLS_ROWS
    SettingsPage.STREAM_HOSTS -> STREAM_HOSTS_ROWS
    SettingsPage.NAVIGATION -> 4
    // Enable, permission, speed, span, then one row per bindable button.
    SettingsPage.POINTER -> 4 + MouseButton.entries.size
    SettingsPage.FEEDBACK -> 5
    SettingsPage.PROFILES -> profilesRowCount(profileRegistry)
    SettingsPage.PROFILE_EDIT -> profileEditRowCount(activeProfileHasAvatar)
    SettingsPage.DUAL_SCREEN -> 6
    SettingsPage.PERFORMANCE -> 3
    SettingsPage.EXTENSIONS -> EXTENSIONS_ROWS
    SettingsPage.ACCESSIBILITY -> 5
}

// ---------------------------------------------------------------- Appearance

@Composable
private fun ThemePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    // A gallery rather than a dropdown of names: with twenty themes, choosing
    // from a list meant leaving Settings to see each one.
    ThemePreviewRow(
        selected = personalization.themeId,
        focused = focusedRow == 0,
        onSelected = viewModel::selectTheme,
        onTakesHorizontalInput = { takes -> viewModel.setRowTakesHorizontal(0, takes) },
    )
    RowDivider()
    ColorRow(
        title = "Accent colour",
        subtitle = "Overrides the theme's own accent",
        colorsToPick = ACCENT_SWATCHES,
        selected = personalization.accentOverrideArgb?.let(::Color),
        focused = focusedRow == 1,
        onSelected = { color ->
            // Stored as an unsigned 32-bit ARGB value in a Long, matching the
            // representation every other colour in the model uses.
            val argb = color?.toArgb()?.toLong()?.and(0xFFFFFFFFL)
            viewModel.updatePersonalization { it.copy(accentOverrideArgb = argb) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Dynamic colour",
        subtitle = "Derive the palette from the system wallpaper (Android 12+)",
        checked = personalization.useDynamicColor,
        focused = focusedRow == 2,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(useDynamicColor = on) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Autoplay trailers",
        subtitle = "Play a game's trailer on the info panel while it is highlighted; " +
            "L1 or R1 shows screenshots instead",
        checked = personalization.autoplayTrailers,
        focused = focusedRow == 3,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(autoplayTrailers = on) }
        },
    )
    RowDivider()
    // One answer for every corner in the launcher. On the Theme page rather than
    // Interface because it overrides something the theme itself declares, and the
    // two are only comprehensible next to each other.
    ChoiceRow(
        title = "Corner style",
        subtitle = "Shape of panels, cards, dialogs and tabs",
        options = CornerStyle.entries,
        selected = personalization.cornerStyle,
        label = CornerStyle::label,
        focused = focusedRow == 4,
        onSelected = { style ->
            viewModel.updatePersonalization { it.copy(cornerStyle = style) }
        },
    )
}

/**
 * Import, list and remove platform icon packs.
 *
 * Two ways in because packs arrive both ways: extracted into a folder, or still
 * as the archive they were downloaded as. Neither is more correct than the other
 * and guessing wrong means the user cannot find their pack in the picker.
 */
@Composable
private fun IconPacksPage(
    focusedRow: Int,
    viewModel: SettingsViewModel,
    packs: List<IconPack>,
    status: IconPackStatus,
) {
    DirectoryPickerRow(
        title = "Import from folder",
        subtitle = "Pick an extracted pack folder",
        focused = focusedRow == 0,
        onPicked = { uri, _ -> viewModel.installIconPackFromFolder(uri) },
    )
    RowDivider()
    FilePickerRow(
        title = "Import from archive",
        subtitle = "Pick a .zip pack",
        mimeTypes = ZIP_MIME_TYPES,
        focused = focusedRow == 2,
        onPicked = { uri, _ -> viewModel.installIconPackFromZip(uri) },
    )

    // Said out loud rather than left to be inferred from the list: an import can
    // succeed for most platforms and hold artwork for the rest, and a silent
    // partial success reads as a broken pack.
    status.message?.let { message ->
        RowDivider()
        InfoRow("Last import", message)
    }

    if (packs.isEmpty()) {
        RowDivider()
        InfoRow(
            "Installed",
            "None. Loki ships no packs — platform artwork comes from ones you import.",
        )
        return
    }

    packs.forEachIndexed { index, pack ->
        RowDivider()
        ActionRow(
            title = pack.name,
            subtitle = buildString {
                append("${pack.author} · v${pack.version} · ")
                append("${pack.appliedCount} platform")
                if (pack.appliedCount != 1) append("s")
                if (pack.heldCount > 0) append(", ${pack.heldCount} held")
            },
            focused = focusedRow == IMPORT_ROWS + index,
            destructive = true,
            trailingLabel = "Remove",
            onClick = { viewModel.removeIconPack(pack.id) },
        )
    }
}

/**
 * Zip, spelled several ways.
 *
 * Providers disagree on what a `.zip` is: the Downloads provider usually reports
 * `application/zip`, some file managers report `application/x-zip-compressed`, and
 * anything that has lost the association reports `application/octet-stream`.
 * Filtering on the first alone hides the file the user came to pick.
 */
private val ZIP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)

/** Rows above the list of installed packs. */
private const val IMPORT_ROWS = 2

/**
 * The controller pointer.
 *
 * The permission row comes second, right under the switch, because the feature
 * does nothing without it and there is no dialog to ask — the user has to be told
 * plainly and taken to the right screen.
 */
@Composable
private fun PointerPage(
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
private const val POINTER_FIXED_ROWS = 4
private val SPEED_RANGE = 400..3_000

@Composable
private fun WallpaperPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization
    val gridClearRow = 2
    val infoPickerRow = 2 + if (personalization.wallpaperUri != null) 1 else 0
    val infoClearRow = infoPickerRow + 1

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
}

@Composable
private fun GridPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val grid = settings.grid

    // One picker rather than separate column and row sliders. The two together
    // could reach a matrix with another size's spacing, which is the crowded
    // in-between state the presets exist to remove — and pinch already steps
    // through exactly this list, so the two controls now agree.
    ChoiceRow(
        title = "Layout",
        subtitle = "Also reachable by pinching the grid",
        options = GridSpec.PRESETS,
        selected = grid.preset,
        focused = focusedRow == 0,
        label = { "${it.label}  ·  ${it.columns} × ${it.rows}" },
        onSelected = { preset -> viewModel.updateGrid(preset::applyTo) },
    )
    RowDivider()
    SliderRow(
        title = "Icon size",
        subtitle = "Fine-tunes how much of each cell the artwork fills",
        value = grid.iconScale,
        range = GridSpec.MIN_ICON_SCALE..GridSpec.MAX_ICON_SCALE,
        focused = focusedRow == 1,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale -> viewModel.updateGrid { it.copy(iconScale = scale) } },
    )
    RowDivider()
    IntSliderRow(
        title = "Icon spacing",
        subtitle = "Percent of a cell left as gutter",
        value = grid.spacingDp,
        range = 0..48,
        focused = focusedRow == 2,
        suffix = "%",
        onValueChange = { spacing -> viewModel.updateGrid { it.copy(spacingDp = spacing) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Icon shape",
        options = IconShape.entries,
        selected = grid.iconShape,
        focused = focusedRow == 3,
        label = IconShape::label,
        onSelected = { shape -> viewModel.updateGrid { it.copy(iconShape = shape) } },
    )
    RowDivider()
    SwitchRow(
        title = "Show labels",
        checked = grid.showLabels,
        focused = focusedRow == 4,
        onCheckedChange = { on -> viewModel.updateGrid { it.copy(showLabels = on) } },
    )
}

@Composable
private fun DockPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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
private fun CursorPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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

@Composable
private fun InterfacePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    SwitchRow(
        title = "Glass effects",
        subtitle = "Translucent panels with a blurred backdrop",
        checked = personalization.glassEffects,
        focused = focusedRow == 0,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(glassEffects = on) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Text size",
        value = personalization.fontScale,
        range = 0.8f..1.5f,
        focused = focusedRow == 1,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { scale ->
            viewModel.updatePersonalization { it.copy(fontScale = scale) }
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
}

// ------------------------------------------------------------------- Library

@Composable
private fun PlatformsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    platformOptions: List<PlatformEmulatorOption>,
    availablePlatforms: List<Platform>,
    scanState: SyncState,
    scrapeState: ScrapeState,
) {
    platformOptions.forEachIndexed { index, option ->
        val scrapeProgress = (scrapeState as? ScrapeState.Running)
            ?.takeIf { it.platformId == option.platform.id }
            ?.let { "${it.done}/${it.total}" }
        if (index > 0) RowDivider()
        SystemRow(
            platform = option.platform,
            installedEmulators = option.installed,
            romFolder = settings.library.romDirectoryUris
                .firstOrNull { it.platformId == option.platform.id }
                ?.displayName,
            focused = focusedRow == index,
            scrapeProgress = scrapeProgress,
            onToggleEmulator = { packageName ->
                viewModel.togglePlatformEmulator(option.platform.id, packageName)
            },
            onScrape = { viewModel.scrapePlatform(option.platform.id) },
            onRemove = { viewModel.removePlatform(option.platform.id) },
        )
    }

    if (platformOptions.isNotEmpty()) RowDivider()

    AddSystemRow(
        available = availablePlatforms,
        focused = focusedRow == platformOptions.size,
        onAdd = viewModel::beginAddPlatform,
    )

    if (platformOptions.isNotEmpty()) {
        RowDivider()
        ActionRow(
            title = "Scan library now",
            subtitle = when (scanState) {
                is SyncState.Scanning -> "Scanning ${scanState.label} — ${scanState.found} found"
                is SyncState.Completed ->
                    "Found ${scanState.gamesFound} games and ${scanState.appsFound} apps"

                is SyncState.Failed -> scanState.message
                SyncState.Idle -> "Re-read every configured folder"
            },
            focused = focusedRow == platformOptions.size + 1,
            trailingLabel = if (scanState is SyncState.Scanning) "Running" else "Scan",
            onClick = viewModel::scanLibrary,
        )
    }
}

@Composable
private fun RomFoldersPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val library = settings.library
    val extras = library.romDirectoryUris.filter { it.platformId == null }

    if (extras.isEmpty()) {
        InfoRow(
            title = "No extra folders",
            value = "Platforms bring their own",
        )
        RowDivider()
    }

    extras.forEachIndexed { index, directory ->
        ActionRow(
            title = directory.displayName,
            subtitle = "Mixed folder — platform detected per file",
            focused = focusedRow == index,
            trailingLabel = "Remove",
            onClick = {
                viewModel.updateLibrary { current ->
                    current.copy(romDirectoryUris = current.romDirectoryUris - directory)
                }
            },
        )
        RowDivider()
    }

    DirectoryPickerRow(
        title = "Add folder",
        subtitle = "For collections spanning several systems",
        focused = focusedRow == extras.size,
        onPicked = { uri, name ->
            viewModel.updateLibrary { current ->
                // Re-adding a folder must not create a duplicate that would then
                // be scanned twice.
                if (current.romDirectoryUris.any { it.uri == uri }) {
                    current
                } else {
                    current.copy(
                        romDirectoryUris = current.romDirectoryUris +
                            RomDirectory(uri = uri, displayName = name),
                    )
                }
            }
        },
    )
}

@Composable
private fun ScanningPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    gridClearResult: String?,
) {
    val library = settings.library

    SwitchRow(
        title = "Look inside archives",
        subtitle = "Scan .zip and .7z containers",
        checked = library.scanArchives,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(scanArchives = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Detect duplicates",
        checked = library.detectDuplicates,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(detectDuplicates = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Group versions",
        subtitle = "Collapse regional variants and revisions into one entry",
        checked = library.groupVersions,
        focused = focusedRow == 2,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(groupVersions = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Show apps on the grid",
        checked = library.showAppsOnGrid,
        focused = focusedRow == 3,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(showAppsOnGrid = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Hide system apps",
        checked = library.hideSystemApps,
        focused = focusedRow == 4,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(hideSystemApps = on) } },
    )
    RowDivider()
    // The way back. Hiding survives rescans, so without this an entry hidden by
    // mistake has no cell to long-press and no list that mentions it.
    SwitchRow(
        title = "Show hidden entries",
        subtitle = "Reveal hidden games and apps, dimmed, so they can be restored",
        checked = library.showHiddenEntries,
        focused = focusedRow == 5,
        onCheckedChange = { on ->
            viewModel.updateLibrary { it.copy(showHiddenEntries = on) }
        },
    )
    RowDivider()
    ActionRow(
        title = "Scan library now",
        subtitle = "Apply these settings to the whole library",
        focused = focusedRow == 6,
        trailingLabel = "Scan",
        onClick = viewModel::scanLibrary,
    )
    RowDivider()
    /*
     * Clearing the grid, not the library.
     *
     * Marked destructive because it undoes arranging that may have taken a
     * while, but it is recoverable in a way deleting is not: the games stay
     * scanned, stay searchable and stay inside their platform folders, so a
     * rescan files them back. Says how many it took, because everything it does
     * happens on a screen the user is not currently looking at.
     */
    ActionRow(
        title = "Remove all games from the grid",
        subtitle = gridClearResult
            ?: "Clears every game's cell. The games stay in your library and in " +
            "their platform folders — only the grid is emptied.",
        focused = focusedRow == 7,
        trailingLabel = "Remove",
        destructive = true,
        onClick = viewModel::clearGamesFromGrid,
    )
}

@Composable
private fun MetadataPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    scrapeState: ScrapeState,
    providerStatus: Map<String, ProviderStatus>,
    checking: Boolean,
    artworkOnly: Boolean,
    noScreenshots: Boolean,
    screenScraperKeyMissing: Boolean,
) {
    val metadata = settings.metadata

    ActionRow(
        title = "Download metadata",
        subtitle = when (scrapeState) {
            is ScrapeState.Running ->
                "${scrapeState.done} of ${scrapeState.total} — ${scrapeState.currentTitle}"

            is ScrapeState.Completed ->
                "Updated ${scrapeState.updated}, skipped ${scrapeState.skipped}"

            is ScrapeState.Failed -> scrapeState.message
            ScrapeState.NotConfigured -> "Add credentials below before scraping"
            ScrapeState.Idle -> "Fetch artwork and details for your games"
        },
        focused = focusedRow == 0,
        trailingLabel = if (scrapeState is ScrapeState.Running) "Cancel" else "Start",
        onClick = {
            if (scrapeState is ScrapeState.Running) {
                viewModel.cancelScrape()
            } else {
                viewModel.scrapeMetadata(onlyMissing = metadata.scrapeOnlyMissing)
            }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Only fill in missing data",
        subtitle = "Leave already-scraped entries alone",
        checked = metadata.scrapeOnlyMissing,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateMetadata { it.copy(scrapeOnlyMissing = on) } },
    )
    RowDivider()
    /*
     * Trailers, for libraries scraped before THOR could fetch them.
     *
     * Its own action rather than a full re-scrape: "only missing" means *never
     * scraped*, so every existing game is skipped and no trailer ever arrives —
     * but re-scraping everything is hundreds of rate-limited calls to fill one
     * field most of them will not have. This asks only about games without one.
     */
    ActionRow(
        title = "Fetch missing trailers",
        subtitle = "Look up trailers for games that have none, without re-scraping " +
            "everything else",
        focused = focusedRow == 2,
        trailingLabel = "Fetch",
        onClick = viewModel::refreshTrailers,
    )

    RowDivider()
    ActionRow(
        title = "Check connections",
        subtitle = "Verify each provider's credentials actually work",
        focused = focusedRow == 3,
        trailingLabel = if (checking) "Checking…" else "Check",
        onClick = viewModel::checkProviderConnections,
    )

    // Artwork arriving while every text field stays blank looks like a broken
    // scraper. It is usually just SteamGridDB being the only configured provider,
    // and SteamGridDB serves artwork only — so say which providers supply text.
    if (artworkOnly) {
        RowDivider()
        InfoRow(
            "No description source",
            "Enable Wikidata for key-free Wikipedia descriptions, or add a RAWG key " +
                "for RAWG descriptions and credits.",
        )
    }

    // The same shape of fault one layer along. SteamGridDB fills every cover, so
    // the scrape plainly worked — but it holds nothing landscape, so the panel
    // has no image to show and nothing anywhere says why.
    if (noScreenshots) {
        RowDivider()
        InfoRow(
            "No screenshot source",
            "SteamGridDB has covers, banners and logos but no widescreen images. " +
                "ScreenScraper supplies them for retro systems and RAWG for modern " +
                "ones 2014 without one of those the game panel has nothing to show.",
        )
    }

    PROVIDERS.forEachIndexed { index, provider ->
        RowDivider()
        SwitchRow(
            title = provider.second,
            subtitle = when {
                provider.first !in IMPLEMENTED_PROVIDERS -> "Not yet implemented"
                else -> providerStatus[provider.first].describe()
            },
            checked = provider.first in metadata.enabledProviders,
            focused = focusedRow == PROVIDER_FIRST_ROW + index,
            onCheckedChange = { on ->
                viewModel.updateMetadata { current ->
                    current.copy(
                        enabledProviders = if (on) {
                            current.enabledProviders + provider.first
                        } else {
                            current.enabledProviders - provider.first
                        },
                    )
                }
            },
        )
    }

    RowDivider()
    TextFieldRow(
        title = "SteamGridDB key",
        subtitle = "Artwork. From steamgriddb.com/profile/preferences/api",
        value = metadata.apiKeys[PROVIDER_STEAMGRIDDB].orEmpty(),
        placeholder = "API key",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size,
        onValueChange = { viewModel.setApiKey(PROVIDER_STEAMGRIDDB, it) },
    )
    RowDivider()
    TextFieldRow(
        title = "RAWG key",
        subtitle = "Descriptions and credits. From rawg.io/apidocs",
        value = metadata.apiKeys[PROVIDER_RAWG].orEmpty(),
        placeholder = "API key",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 1,
        onValueChange = { viewModel.setApiKey(PROVIDER_RAWG, it) },
    )
    RowDivider()
    TextFieldRow(
        title = "ScreenScraper account",
        // Says outright when the account cannot do anything on its own. These
        // fields look like the switch that turns the provider on and are not:
        // the developer pair is compiled into the build.
        subtitle = if (screenScraperKeyMissing) {
            "This build has no ScreenScraper developer key, so the provider is off " +
                "and an account cannot turn it on"
        } else {
            "Optional — raises the daily quota and image quality"
        },
        value = metadata.screenScraperUser,
        placeholder = "Username",
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 2,
        onValueChange = viewModel::setScreenScraperUser,
    )
    RowDivider()
    TextFieldRow(
        title = "ScreenScraper password",
        value = metadata.screenScraperPassword,
        placeholder = "Password",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 3,
        onValueChange = viewModel::setScreenScraperPassword,
    )
}

/**
 * Where the provider switches start on the Metadata page.
 *
 * Named, and the credential rows below are counted from it, because the fixed
 * rows above have been renumbered by hand twice now — and every time, the rows
 * after them silently stopped matching the cursor. An index expressed as
 * arithmetic cannot drift from the list it is indexing.
 */
private const val PROVIDER_FIRST_ROW = 4

@Composable
private fun SortingPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val library = settings.library

    ChoiceRow(
        title = "Default sort",
        options = SortOrder.entries,
        selected = library.defaultSort,
        focused = focusedRow == 0,
        label = SortOrder::label,
        onSelected = { order -> viewModel.updateLibrary { it.copy(defaultSort = order) } },
    )
    RowDivider()
    SwitchRow(
        title = "Reverse order",
        checked = library.sortDescending,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(sortDescending = on) } },
    )
}

// ------------------------------------------------------------------ Controls

@Composable
private fun NavigationPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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
private fun FeedbackPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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

// ------------------------------------------------------------------- Display

@Composable
private fun DualScreenPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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
    SwitchRow(
        title = "Swap screens",
        subtitle = if (display.mode == DualScreenMode.COUCH) {
            "Not used in couch mode — only one screen is in play"
        } else {
            "Put the grid on the main panel instead"
        },
        checked = display.swapScreens,
        focused = focusedRow == 3,
        onCheckedChange = { on -> viewModel.updateDisplay { it.copy(swapScreens = on) } },
    )
    RowDivider()
    SliderRow(
        title = "Split ratio",
        subtitle = "How much of the screen the info panel takes, when one screen " +
            "is showing both",
        value = display.splitRatio,
        range = 0.25f..0.75f,
        focused = focusedRow == 4,
        valueLabel = { "${(it * 100).toInt()}%" },
        onValueChange = { ratio -> viewModel.updateDisplay { it.copy(splitRatio = ratio) } },
    )
    RowDivider()
    SwitchRow(
        title = "Keep screen awake",
        checked = display.keepTopScreenAwake,
        focused = focusedRow == 5,
        onCheckedChange = { on -> viewModel.updateDisplay { it.copy(keepTopScreenAwake = on) } },
    )
}

@Composable
private fun PerformancePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
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

// -------------------------------------------------------------------- System

@Composable
private fun AccessibilityPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val accessibility = settings.accessibility

    SwitchRow(
        title = "High contrast",
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

// --------------------------------------------------------------------- About

/**
 * What this launcher is, and the handful of controls for looking after it.
 *
 * Diagnostics used to be a page of its own under System, and the split never
 * earned itself: "what version is this" and "why is that button doing nothing"
 * are the same visit, and a category holding one page is a category the user
 * walks through rather than reads.
 *
 * The facts are stated first and are not focusable — there is nothing to press
 * on a version number — so the controller's first stop is the default-launcher
 * row and the indices below count only the rows that can be reached.
 */
@Composable
fun AboutPane(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    isDefaultLauncher: Boolean,
    keyCaptureEnabled: Boolean,
    capturedKeys: List<RawKeyPress>,
    /** Systems the user has added, which is not a thing settings knows. */
    platformCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfoRow("Active theme", settings.personalization.themeId.displayName)
        RowDivider()
        InfoRow("Grid", "${settings.grid.columns} × ${settings.grid.rows}")
        RowDivider()
        /*
         * The systems added, counted from the platforms themselves.
         *
         * This read `romDirectoryUris.size`, which is a count of ROM *folders* —
         * a different thing wearing the same label, and one that is legitimately
         * zero for anyone who granted folders per platform rather than as extras.
         * So it reported "0 platforms" to people with a full library.
         */
        InfoRow("Platforms configured", platformCount.toString())
        RowDivider()
        InfoRow("ROM folders", settings.library.romDirectoryUris.size.toString())
        RowDivider()
        InfoRow("Settings schema", settings.schemaVersion.toString())
        RowDivider()
        InfoRow("Built for", "AYN Thor dual screen handheld")
        RowDivider()

        ActionRow(
            title = "Set as default launcher",
            subtitle = if (isDefaultLauncher) {
                "Loki is your home app"
            } else {
                "Opens Android's home app chooser"
            },
            focused = focusedRow == 0,
            trailingLabel = if (isDefaultLauncher) "Active" else "Choose",
            onClick = viewModel::requestDefaultLauncher,
        )
        RowDivider()
        ActionRow(
            title = "Replay the walkthrough",
            subtitle = "The guided tour of both panels, the grid, the controls " +
                "and every setting. Shown once when Loki is first set up.",
            focused = focusedRow == 1,
            trailingLabel = "Replay",
            onClick = viewModel::replayTutorial,
        )
        RowDivider()
        SwitchRow(
            title = "Verbose logging",
            subtitle = "Writes detailed output to logcat",
            checked = settings.developer.verboseLogging,
            focused = focusedRow == 2,
            onCheckedChange = { on -> viewModel.updateDeveloper { it.copy(verboseLogging = on) } },
        )
        RowDivider()
        SwitchRow(
            title = "Button tester",
            subtitle = "Reports what each button sends, without acting on it. " +
                "Use Back to leave.",
            checked = keyCaptureEnabled,
            focused = focusedRow == 3,
            onCheckedChange = viewModel::setKeyCapture,
        )

        if (keyCaptureEnabled) {
            if (capturedKeys.isEmpty()) {
                InfoRow(
                    "Listening",
                    "Press any button. A button that never appears here is being " +
                        "handled by the system before the launcher sees it, and cannot " +
                        "be remapped by an app.",
                )
            } else {
                capturedKeys.forEach { press ->
                    InfoRow(
                        press.keyName,
                        buildString {
                            append("code ${press.keyCode}")
                            press.deviceName?.let { append(" · $it") }
                            append(" · ")
                            append(press.boundTo?.let { "bound to ${it.label}" } ?: "unbound")
                        },
                    )
                }
            }
        }

        RowDivider()
        ActionRow(
            title = "Reset all settings",
            subtitle = "Restores every option to its default. Library data is untouched.",
            focused = focusedRow == 4,
            destructive = true,
            trailingLabel = "RESET",
            onClick = viewModel::resetToDefaults,
        )
    }
}

/** Focusable rows on [AboutPane]; the information rows above them are not. */
const val ABOUT_ROWS = 5

private const val PROVIDER_STEAMGRIDDB = "steamgriddb"
private const val PROVIDER_RAWG = "rawg"

private val ACCENT_SWATCHES = listOf(
    Color(0xFF4F8CFF), Color(0xFF8B5CF6), Color(0xFF00E5FF), Color(0xFF39FF14),
    Color(0xFFFF2E88), Color(0xFFF57C00), Color(0xFFE53935), Color(0xFF00C3E3),
)

private val PROVIDERS = listOf(
    PROVIDER_STEAMGRIDDB to "SteamGridDB",
    "wikidata" to "Wikidata",
    PROVIDER_RAWG to "RAWG",
    "screenscraper" to "ScreenScraper",
)

/** Providers with a working client; the rest are listed but inert. */
private val IMPLEMENTED_PROVIDERS = setOf(
    PROVIDER_STEAMGRIDDB,
    "wikidata",
    PROVIDER_RAWG,
    "screenscraper",
)

/** Human-readable form of a provider probe result. */
private fun ProviderStatus?.describe(): String = when (this) {
    null, ProviderStatus.Unknown -> "Not checked"
    ProviderStatus.NotConfigured -> "No credentials"
    ProviderStatus.Connected -> "Connected"
    ProviderStatus.InvalidCredentials -> "Rejected — check the key"
    is ProviderStatus.Unreachable -> "Unreachable — $detail"
    is ProviderStatus.Error -> detail
}
