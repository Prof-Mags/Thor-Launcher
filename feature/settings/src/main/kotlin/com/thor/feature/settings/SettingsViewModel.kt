package com.thor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.datastore.SettingsRepository
import com.thor.core.input.RawKeyPress
import com.thor.core.model.AccessibilitySettings
import com.thor.core.model.ControllerCommand
import com.thor.core.model.AudioSettings
import com.thor.core.model.ControlSettings
import com.thor.core.model.DeveloperSettings
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DockSettings
import com.thor.core.model.GridSpec
import com.thor.core.model.LibrarySettings
import com.thor.core.model.MetadataSettings
import com.thor.core.model.PerformanceSettings
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.Platform
import com.thor.core.model.RomDirectory
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeSpec
import com.thor.core.model.ThorSettings
import com.thor.data.launcher.DefaultLauncherManager
import com.thor.data.launcher.EntryLauncher
import com.thor.data.metadata.MetadataAggregator
import com.thor.data.metadata.ProviderStatus
import com.thor.data.repository.LibraryRepository
import com.thor.data.scanner.EmulatorRegistry
import com.thor.data.sync.LibrarySyncManager
import com.thor.data.sync.MetadataSyncManager
import com.thor.data.sync.ScrapeState
import com.thor.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the settings overlay.
 *
 * Every mutator delegates to a narrow `update…` on [SettingsRepository] rather
 * than writing a whole settings object, so two panes edited in quick succession
 * cannot overwrite one another.
 */
/** A platform paired with the emulators actually installed for it. */
data class PlatformEmulatorOption(
    val platform: Platform,
    /** Package name to display name, in registry order. */
    val installed: List<Pair<String, String>>,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val syncManager: LibrarySyncManager,
    private val metadataSyncManager: MetadataSyncManager,
    private val entryLauncher: EntryLauncher,
    private val aggregator: MetadataAggregator,
    private val defaultLauncherManager: DefaultLauncherManager,
) : ViewModel() {

    val settings: StateFlow<ThorSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThorSettings.DEFAULT,
    )

    val scanState: StateFlow<SyncState> = syncManager.state
    val scrapeState: StateFlow<ScrapeState> = metadataSyncManager.state

    /**
     * Platforms with their installable emulators resolved.
     *
     * Only emulators actually present on the device are offered — listing one
     * that is not installed produces a launch failure the user cannot act on
     * from the settings screen.
     */
    val platformOptions: StateFlow<List<PlatformEmulatorOption>> = libraryRepository.addedPlatforms
        .map { platforms ->
            platforms.map { platform ->
                PlatformEmulatorOption(
                    platform = platform,
                    installed = entryLauncher.installedEmulatorsFor(platform.id)
                        .map { packageName ->
                            packageName to (
                                EmulatorRegistry.specFor(packageName)?.displayName ?: packageName
                                )
                        },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Platforms not yet added, offered in the "Add platform" dropdown.
     *
     * Restricted to the systems that are actually supported end to end — each
     * one has known emulators in the registry and recognised file extensions in
     * the scanner. The database still carries every platform so a mixed ROM
     * folder is scanned correctly; this is only what the picker offers.
     */
    val availablePlatforms: StateFlow<List<Platform>> = libraryRepository.platforms
        .map { all ->
            all.filterNot(Platform::isAdded).filter { it.id in OFFERED_PLATFORM_IDS }
                .sortedBy { OFFERED_PLATFORM_IDS.indexOf(it.id) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The platform whose setup dialog is open, if any. */
    private val _pendingPlatform = MutableStateFlow<Platform?>(null)
    val pendingPlatform: StateFlow<Platform?> = _pendingPlatform.asStateFlow()

    /** Emulators installed for the platform currently being added. */
    fun installedEmulatorsFor(platform: Platform): List<Pair<String, String>> =
        entryLauncher.installedEmulatorsFor(platform.id).map { packageName ->
            packageName to (EmulatorRegistry.specFor(packageName)?.displayName ?: packageName)
        }

    fun beginAddPlatform(platform: Platform) {
        _pendingPlatform.value = platform
    }

    fun cancelAddPlatform() {
        _pendingPlatform.value = null
    }

    /**
     * Commits a platform's setup.
     *
     * Adds the platform, records its ROM folder scoped to it, assigns the
     * emulator, and kicks off a scan — the folder is only useful once it has
     * been walked, and making the user find "Scan library" afterwards is the
     * kind of missing last step that makes a feature look broken.
     */
    fun confirmAddPlatform(
        platform: Platform,
        romDirectoryUri: String?,
        romDirectoryName: String,
        emulatorPackage: String?,
        scanSubfolders: Boolean,
    ) {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.addPlatform(platform.id)

            emulatorPackage?.let { packageName ->
                libraryRepository.setPlatformEmulators(platform.id, listOf(packageName))
            }

            if (romDirectoryUri != null) {
                settingsRepository.updateLibrary { current ->
                    if (current.romDirectoryUris.any { it.uri == romDirectoryUri }) {
                        current
                    } else {
                        current.copy(
                            romDirectoryUris = current.romDirectoryUris + RomDirectory(
                                uri = romDirectoryUri,
                                displayName = romDirectoryName,
                                // Scoping the folder to this platform is what
                                // lets the scanner import ambiguous extensions
                                // like .iso or .bin, which it otherwise skips
                                // because they belong to several systems.
                                platformId = platform.id,
                                recursive = scanSubfolders,
                            ),
                        )
                    }
                }
            }

            _pendingPlatform.value = null
            if (romDirectoryUri != null) syncManager.requestFullScan()
        }
    }

    private val _selectedCategory = MutableStateFlow(SettingsCategory.APPEARANCE)
    val selectedCategory: StateFlow<SettingsCategory> = _selectedCategory.asStateFlow()

    private val _focusedRow = MutableStateFlow(0)
    val focusedRow: StateFlow<Int> = _focusedRow.asStateFlow()

    /** The page open within [selectedCategory]; null shows that category's list. */
    private val _openPage = MutableStateFlow<SettingsPage?>(null)
    val openPage: StateFlow<SettingsPage?> = _openPage.asStateFlow()

    fun selectCategory(category: SettingsCategory) {
        _selectedCategory.value = category
        // Changing category always returns to that category's page list; leaving
        // a page from another category open would show unrelated controls under
        // the new heading.
        _openPage.value = null
        _focusedRow.value = 0
    }

    fun openPage(page: SettingsPage) {
        _selectedCategory.value = page.category
        _openPage.value = page
        _focusOnRail.value = false
        _focusedRow.value = 0
    }

    /** Returns from a page to its category's list. */
    fun closePage() {
        _openPage.value = null
        _focusedRow.value = 0
    }

    /** True when Back should close the overlay rather than a page. */
    val isAtTopLevel: Boolean get() = _openPage.value == null

    fun focusRow(index: Int) {
        _focusedRow.value = index.coerceAtLeast(0)
    }

    /**
     * Whether the rail or the detail pane currently has the cursor.
     *
     * Settings is a two-column layout, so a single focus index cannot describe
     * it: Left and Right move *between* the columns, Up and Down move within
     * whichever one is active.
     */
    private val _focusOnRail = MutableStateFlow(true)
    val focusOnRail: StateFlow<Boolean> = _focusOnRail.asStateFlow()

    /**
     * Routes a controller command.
     *
     * @param rowCount how many focusable rows the visible pane has, supplied by
     *   the screen because only it knows what it rendered
     * @return true when the command was consumed
     */
    fun onControllerCommand(command: ControllerCommand, rowCount: Int): Boolean = when (command) {
        ControllerCommand.NAVIGATE_UP -> {
            if (_focusOnRail.value) {
                val entries = SettingsCategory.entries
                val index = entries.indexOf(_selectedCategory.value)
                selectCategory(entries[(index - 1 + entries.size) % entries.size])
            } else {
                _focusedRow.value = (_focusedRow.value - 1).coerceAtLeast(0)
            }
            true
        }

        ControllerCommand.NAVIGATE_DOWN -> {
            if (_focusOnRail.value) {
                val entries = SettingsCategory.entries
                val index = entries.indexOf(_selectedCategory.value)
                selectCategory(entries[(index + 1) % entries.size])
            } else if (rowCount > 0) {
                _focusedRow.value = (_focusedRow.value + 1).coerceAtMost(rowCount - 1)
            }
            true
        }

        // Left steps back out: from a page to its list, from a list to the rail.
        // It never closes the overlay — that is Back's job alone.
        ControllerCommand.NAVIGATE_LEFT -> {
            when {
                // Unless the focused row navigates sideways itself, in which case
                // left means "the previous one of these" — a gallery is browsed, not
                // stepped out of.
                focusedRowTakesHorizontal() -> _horizontalStep.value -= 1
                _openPage.value != null -> closePage()
                !_focusOnRail.value -> {
                    _focusOnRail.value = true
                    _focusedRow.value = 0
                }
            }
            true
        }

        ControllerCommand.NAVIGATE_RIGHT -> {
            when {
                focusedRowTakesHorizontal() -> _horizontalStep.value += 1
                _focusOnRail.value && rowCount > 0 -> {
                    _focusOnRail.value = false
                    _focusedRow.value = 0
                }
            }
            true
        }

        ControllerCommand.CONFIRM -> when {
            // From the rail, step into the category's page list.
            _focusOnRail.value && rowCount > 0 -> {
                _focusOnRail.value = false
                _focusedRow.value = 0
                true
            }

            // From a page list, open the highlighted page.
            _openPage.value == null -> {
                val pages = SettingsPage.forCategory(_selectedCategory.value)
                pages.getOrNull(_focusedRow.value)?.let(::openPage) != null
            }

            // Inside a page, broadcast to the focused row so its own control
            // acts — a toggle flips, a choice advances, a stepper steps.
            else -> {
                _activationTick.value += 1
                true
            }
        }

        else -> false
    }

    /**
     * Increments each time Confirm is pressed inside a page.
     *
     * The row holding the cursor claims it. A counter rather than an event
     * stream so it survives recomposition without per-row subscriptions.
     */
    private val _activationTick = MutableStateFlow(0)
    val activationTick: StateFlow<Int> = _activationTick.asStateFlow()

    /**
     * A running signed count of Left/Right presses aimed at a row that wants them.
     *
     * Broadcast the same way [activationTick] is: the launcher owns the cursor, so a
     * press cannot be delivered to a focused view — it is published, and the row
     * holding the cursor claims it.
     */
    private val _horizontalStep = MutableStateFlow(0)
    val horizontalStep: StateFlow<Int> = _horizontalStep.asStateFlow()

    /**
     * Rows that navigate sideways, reported by the page that draws them.
     *
     * Declared rather than assumed, so Left and Right keep meaning "out" and "in"
     * on every row that is not a gallery.
     */
    private val _horizontalRows = MutableStateFlow<Set<Int>>(emptySet())

    fun setRowTakesHorizontal(index: Int, takes: Boolean) {
        _horizontalRows.update { rows -> if (takes) rows + index else rows - index }
    }

    private fun focusedRowTakesHorizontal(): Boolean =
        !_focusOnRail.value && _focusedRow.value in _horizontalRows.value

    fun resetFocus() {
        _focusOnRail.value = true
        _openPage.value = null
        _focusedRow.value = 0
    }

    /**
     * Selects a theme and its paired wallpaper.
     *
     * A theme ships with the background it was designed against, and applying
     * the two together is what makes picking one look deliberate rather than
     * leaving a palette fighting whatever effect happened to be set. The
     * wallpaper remains independently changeable afterwards, so this is a
     * starting point rather than a lock.
     */
    fun selectTheme(themeId: ThemeId) {
        viewModelScope.launchSafely(TAG) {
            settingsRepository.updatePersonalization {
                it.copy(
                    themeId = themeId,
                    animatedWallpaper = ThemeSpec.of(themeId).defaultWallpaper,
                )
            }
        }
    }

    fun updatePersonalization(transform: (PersonalizationSettings) -> PersonalizationSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updatePersonalization(transform) }
    }

    fun updateGrid(transform: (GridSpec) -> GridSpec) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateGrid(transform) }
    }

    fun updateDock(transform: (DockSettings) -> DockSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateDock(transform) }
    }

    fun updateLibrary(transform: (LibrarySettings) -> LibrarySettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateLibrary(transform) }
    }

    fun updateMetadata(transform: (MetadataSettings) -> MetadataSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateMetadata(transform) }
    }

    fun updateControls(transform: (ControlSettings) -> ControlSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateControls(transform) }
    }

    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateDisplay(transform) }
    }

    fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateAudio(transform) }
    }

    fun updatePerformance(transform: (PerformanceSettings) -> PerformanceSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updatePerformance(transform) }
    }

    fun updateAccessibility(transform: (AccessibilitySettings) -> AccessibilitySettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateAccessibility(transform) }
    }

    fun updateDeveloper(transform: (DeveloperSettings) -> DeveloperSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateDeveloper(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launchSafely(TAG) { settingsRepository.resetToDefaults() }
    }

    fun scanLibrary() {
        syncManager.requestFullScan()
    }

    /** Stores or clears a provider's API key. */
    fun setApiKey(providerId: String, key: String) {
        viewModelScope.launchSafely(TAG) {
            settingsRepository.updateMetadata { current ->
                current.copy(
                    apiKeys = if (key.isBlank()) {
                        current.apiKeys - providerId
                    } else {
                        current.apiKeys + (providerId to key)
                    },
                )
            }
        }
    }


    fun setScreenScraperUser(value: String) =
        updateScreenScraper { it.copy(screenScraperUser = value.trim()) }

    fun setScreenScraperPassword(value: String) =
        updateScreenScraper { it.copy(screenScraperPassword = value) }



    private fun updateScreenScraper(transform: (MetadataSettings) -> MetadataSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateMetadata(transform) }
    }

    fun addPlatform(platformId: String) {
        viewModelScope.launchSafely(TAG) { libraryRepository.addPlatform(platformId) }
    }

    fun removePlatform(platformId: String) {
        viewModelScope.launchSafely(TAG) { libraryRepository.removePlatform(platformId) }
    }

    /**
     * Adds or removes an emulator from a platform's list.
     *
     * Appending rather than replacing keeps the existing default in place when
     * a second emulator is added — promoting the newest choice to default would
     * silently change what every game on that system launches with.
     */
    fun togglePlatformEmulator(platformId: String, packageName: String) {
        viewModelScope.launchSafely(TAG) {
            val current = platformOptions.value
                .firstOrNull { it.platform.id == platformId }
                ?.platform
                ?.emulatorPackages
                .orEmpty()

            val updated = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
            libraryRepository.setPlatformEmulators(platformId, updated)
        }
    }

    fun scrapeMetadata(onlyMissing: Boolean) {
        metadataSyncManager.requestScrape(onlyMissing)
    }

    fun cancelScrape() {
        metadataSyncManager.cancel()
    }

    /**
     * Result of the last connection check, by provider id.
     *
     * A scrape deliberately swallows provider failures so one bad key cannot
     * fail the whole run — which also means a wrong key looks exactly like a
     * game with no artwork. This is the explicit answer to "is it connected".
     */
    private val _providerStatus = MutableStateFlow<Map<String, ProviderStatus>>(emptyMap())
    val providerStatus: StateFlow<Map<String, ProviderStatus>> = _providerStatus.asStateFlow()

    private val _checkingProviders = MutableStateFlow(false)
    val checkingProviders: StateFlow<Boolean> = _checkingProviders.asStateFlow()

    /**
     * True when artwork can be fetched but text cannot.
     *
     * SteamGridDB serves artwork only. With it as the sole configured provider a
     * scrape fills in cover art for the entire library and leaves every
     * developer, publisher, description and genre blank — which looks like a
     * broken scraper rather than a provider that never offered those fields, so
     * the metadata page says so outright.
     */
    private val _artworkOnlyProviders = MutableStateFlow(false)
    val artworkOnlyProviders: StateFlow<Boolean> = _artworkOnlyProviders.asStateFlow()

    /**
     * Whether THOR is the system home app.
     *
     * Re-read rather than observed: the answer only changes as a result of the
     * user visiting the system chooser, at which point the launcher is paused
     * and will be recomposed on return.
     */
    private val _isDefaultLauncher = MutableStateFlow(defaultLauncherManager.isDefault())
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()

    fun refreshDefaultLauncher() {
        _isDefaultLauncher.value = defaultLauncherManager.isDefault()
    }

    /** Opens the system chooser; Android will not let an app set this itself. */
    fun requestDefaultLauncher() {
        defaultLauncherManager.requestDefault()
    }

    /**
     * Whether the button tester is listening.
     *
     * Exposed here rather than owned by the shell so the diagnostics page can
     * switch it on; the shell watches this and puts the input router into capture
     * mode, since the router belongs to the activity.
     */
    private val _keyCaptureEnabled = MutableStateFlow(false)
    val keyCaptureEnabled: StateFlow<Boolean> = _keyCaptureEnabled.asStateFlow()

    /** Presses seen while the tester is listening, most recent first. */
    private val _capturedKeys = MutableStateFlow<List<RawKeyPress>>(emptyList())
    val capturedKeys: StateFlow<List<RawKeyPress>> = _capturedKeys.asStateFlow()

    fun setKeyCapture(enabled: Boolean) {
        _keyCaptureEnabled.value = enabled
        if (!enabled) _capturedKeys.value = emptyList()
    }

    /**
     * Records a press.
     *
     * Repeats of the same code collapse onto the existing entry rather than
     * filling the list, so holding a button does not push the interesting ones off
     * the end.
     */
    fun onKeyCaptured(press: RawKeyPress) {
        _capturedKeys.update { current ->
            (listOf(press) + current.filterNot { it.keyCode == press.keyCode })
                .take(MAX_CAPTURED_KEYS)
        }
    }

    fun checkProviderConnections() {
        if (_checkingProviders.value) return
        viewModelScope.launchSafely(TAG) {
            _checkingProviders.value = true
            _providerStatus.value = aggregator.checkConnections()
            _artworkOnlyProviders.value =
                aggregator.hasUsableProvider() && !aggregator.hasTextualProvider()
            _checkingProviders.value = false
        }
    }

    private companion object {
        /** Log tag for guarded background work. */
        const val TAG = "Settings"

        /** Distinct codes kept by the button tester. */
        const val MAX_CAPTURED_KEYS = 6

        /**
         * Platforms the picker offers, in menu order.
         *
         * Kept deliberately short: each of these has working emulator entries
         * and unambiguous file extensions, so adding one produces a platform
         * that actually scans and launches. The rest of the catalogue is still
         * in the database and still recognised by the scanner.
         */
        val OFFERED_PLATFORM_IDS = listOf("n64", "nds", "3ds", "switch")
    }
}
