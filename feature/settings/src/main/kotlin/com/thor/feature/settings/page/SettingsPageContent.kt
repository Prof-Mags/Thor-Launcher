package com.thor.feature.settings.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.input.RawKeyPress
import com.thor.core.model.IconPack
import com.thor.core.model.ControllerCommand
import com.thor.core.model.FolderEntry
import com.thor.core.model.MediaSettings
import com.thor.core.model.MouseButton
import com.thor.core.model.Platform
import com.thor.core.model.ProfileRegistry
import com.thor.core.model.ThorSettings
import com.thor.data.achievements.AchievementSyncState
import com.thor.data.achievements.RetroAchievementsStatus
import com.thor.data.metadata.ProviderStatus
import com.thor.data.sync.ScrapeState
import com.thor.data.sync.SyncState
import com.thor.feature.settings.IconPackStatus
import com.thor.feature.settings.PlatformEmulatorOption
import com.thor.feature.settings.SettingsPage
import com.thor.feature.settings.SettingsViewModel

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
    achievementSync: AchievementSyncState,
    retroAchievementsStatus: RetroAchievementsStatus?,
    checkingRetroAchievements: Boolean,
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
    /** What the last artwork import said, or null if there has not been one. */
    importStatus: String? = null,
    /** Everyone on the device, for the profiles page. */
    profileRegistry: ProfileRegistry = ProfileRegistry.EMPTY,
    /** Which custom theme the editor has open, or null when it is showing the list. */
    editingThemeId: String? = null,
    /** What the last theme action said, or null if there has not been one. */
    themeStatus: String? = null,
    /** Every smart folder, and which one the editor has open. */
    smartFolders: List<FolderEntry> = emptyList(),
    editingSmartFolderId: String? = null,
    smartFolderStatus: String? = null,
    /** Which controller profile is open, and the command waiting for a button. */
    editingProfileId: String? = null,
    awaitingBindingFor: ControllerCommand? = null,
    /** What the last backup or restore said, and whether a restart is pending. */
    backupStatus: String? = null,
    restartRequired: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        when (page) {
            SettingsPage.THEME -> ThemePage(settings, focusedRow, viewModel)
            SettingsPage.SURFACES -> SurfacesPage(settings, focusedRow, viewModel)
            SettingsPage.THEME_EDITOR -> ThemeEditorPage(
                settings,
                focusedRow,
                viewModel,
                editingThemeId,
                themeStatus,
            )
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
                settings, focusedRow, viewModel, iconPacks, iconPackStatus, importStatus,
            )
            SettingsPage.METADATA -> MetadataPage(
                settings, focusedRow, viewModel, scrapeState, providerStatus,
                checkingProviders, artworkOnlyProviders, noScreenshotProvider,
                screenScraperKeyMissing,
            )
            SettingsPage.SORTING -> SortingPage(settings, focusedRow, viewModel)
            SettingsPage.SMART_FOLDERS -> SmartFoldersPage(
                folders = smartFolders,
                // The systems the user has actually added, not every system Loki
                // knows: a folder narrowed to a console with no games on the
                // device would simply be empty.
                platforms = platformOptions.map(PlatformEmulatorOption::platform),
                focusedRow = focusedRow,
                viewModel = viewModel,
                editingId = editingSmartFolderId,
                status = smartFolderStatus,
            )
            SettingsPage.ACHIEVEMENTS -> AchievementsPage(
                settings, focusedRow, viewModel, achievementSync, retroAchievementsStatus,
                checkingRetroAchievements,
            )

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
            SettingsPage.BUTTON_MAPPING -> ButtonMappingPage(
                settings = settings,
                focusedRow = focusedRow,
                viewModel = viewModel,
                editingId = editingProfileId,
                awaiting = awaitingBindingFor,
            )
            SettingsPage.POINTER -> PointerPage(
                settings, focusedRow, viewModel, pointerServiceEnabled, pointerRunning,
            )
            SettingsPage.FEEDBACK -> FeedbackPage(settings, focusedRow, viewModel)

            SettingsPage.PROFILES -> ProfilesPage(profileRegistry, focusedRow, viewModel)
            SettingsPage.PROFILE_EDIT -> ProfileEditPage(profileRegistry, focusedRow, viewModel)
            SettingsPage.DUAL_SCREEN -> DualScreenPage(settings, focusedRow, viewModel)
            SettingsPage.PERFORMANCE -> PerformancePage(settings, focusedRow, viewModel)
            SettingsPage.RECORDING -> RecordingPage(settings, focusedRow, viewModel)

            SettingsPage.BACKUP -> BackupPage(
                activeProfile = profileRegistry.active,
                focusedRow = focusedRow,
                viewModel = viewModel,
                status = backupStatus,
                restartRequired = restartRequired,
            )
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
    /** The editor is a short list until a theme is opened, and long once one is. */
    customThemeCount: Int = 0,
    editingTheme: Boolean = false,
    /** As the theme editor: a short list until a folder is opened, long once one is. */
    smartFolderCount: Int = 0,
    editingSmartFolder: Boolean = false,
    /** As the other two editors: a short list until one is opened. */
    customProfileCount: Int = 0,
    editingProfile: Boolean = false,
): Int = when (page) {
    SettingsPage.THEME -> THEME_ROWS
    SettingsPage.SURFACES -> SURFACES_ROWS
    SettingsPage.THEME_EDITOR -> themeEditorRows(customThemeCount, editingTheme)
    SettingsPage.SMART_FOLDERS -> smartFolderRows(smartFolderCount, editingSmartFolder)
    SettingsPage.BUTTON_MAPPING -> buttonMappingRows(customProfileCount, editingProfile)
    SettingsPage.BACKUP -> BACKUP_ROWS
    SettingsPage.WALLPAPER -> WALLPAPER_FIXED_ROWS + wallpaperClearRows
    SettingsPage.GRID -> 6
    SettingsPage.DOCK -> 6
    SettingsPage.CURSOR -> 3
    SettingsPage.INTERFACE -> INTERFACE_ROWS
    // One card per platform, Add, then Scan when there is something to scan.
    SettingsPage.PLATFORMS -> platformCount + 1 + if (platformCount > 0) 1 else 0
    SettingsPage.ROM_FOLDERS -> extraRomFolderCount + 1
    SettingsPage.SCANNING -> 8
    // The bundled switch, two pack imports and the artwork import, then one row
    // per installed pack.
    SettingsPage.ICON_PACKS -> IMPORT_ROWS + iconPackCount
    // Scrape, only-missing, ask-me, trailers, check, one per provider, then
    // the credentials.
    // Four credential rows plus IGDB's pair.
    SettingsPage.METADATA -> PROVIDER_FIRST_ROW + PROVIDERS.size + 6
    SettingsPage.SORTING -> 2
    SettingsPage.ACHIEVEMENTS -> ACHIEVEMENTS_ROWS
    // Two keys and the debrid status line, then one row per indexer, then the
    // add button and the summary.
    SettingsPage.MOVIES_CATALOGUE -> moviesCatalogueRows(mediaSettings)
    SettingsPage.MOVIES_PLAYBACK -> MOVIES_PLAYBACK_ROWS
    SettingsPage.STREAM_QUALITY -> STREAM_QUALITY_ROWS
    SettingsPage.STREAM_CONTROLS -> STREAM_CONTROLS_ROWS
    SettingsPage.STREAM_HOSTS -> STREAM_HOSTS_ROWS
    SettingsPage.NAVIGATION -> 4
    // Enable, permission, speed, span, then one row per bindable button.
    SettingsPage.POINTER -> POINTER_FIXED_ROWS + MouseButton.entries.size
    SettingsPage.FEEDBACK -> 5
    SettingsPage.PROFILES -> profilesRowCount(profileRegistry)
    SettingsPage.PROFILE_EDIT -> profileEditRowCount(activeProfileHasAvatar)
    SettingsPage.DUAL_SCREEN -> 7
    SettingsPage.PERFORMANCE -> 3
    // One row: what sound goes on a capture. See [RecordingAudio] for why there
    // is no game-audio option to make it two.
    SettingsPage.RECORDING -> 1
    SettingsPage.EXTENSIONS -> EXTENSIONS_ROWS
    SettingsPage.ACCESSIBILITY -> 5
}
