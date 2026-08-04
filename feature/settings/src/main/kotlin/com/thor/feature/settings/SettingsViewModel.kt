package com.thor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.datastore.SettingsRepository
import com.thor.core.input.MouseController
import com.thor.core.input.RawKeyPress
import com.thor.core.model.AccessibilitySettings
import com.thor.core.model.ControllerCommand
import com.thor.core.model.AudioSettings
import com.thor.core.model.ControlSettings
import com.thor.core.model.DeveloperSettings
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DockSettings
import com.thor.core.model.GridSpec
import com.thor.core.model.LauncherProfile
import com.thor.core.model.LibrarySettings
import com.thor.core.model.ProfileRegistry
import com.thor.core.model.MetadataSettings
import com.thor.core.model.PerformanceSettings
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.Platform
import com.thor.core.model.RomDirectory
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeSpec
import com.thor.core.model.ThorSettings
import android.net.Uri
import android.content.Context
import androidx.core.net.toUri
import com.thor.core.model.ExtensionManifest
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.model.LauncherExtension
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import com.thor.core.model.IconPack
import com.thor.data.iconpack.IconPackImport
import com.thor.data.iconpack.IconPackRepository
import com.thor.core.model.MediaSettings
import com.thor.core.model.StreamSettings
import com.thor.core.model.MouseSettings
import com.thor.core.model.StremioAddon
import com.thor.core.model.StremioAddons
import com.thor.core.model.TorznabIndexer
import com.thor.data.launcher.DefaultLauncherManager
import com.thor.data.launcher.PointerServiceManager
import com.thor.data.launcher.EntryLauncher
import com.thor.data.media.DebridStatus
import com.thor.data.media.AddonCheck
import com.thor.data.media.MediaRepository
import com.thor.data.metadata.MetadataAggregator
import com.thor.data.metadata.ProviderStatus
import com.thor.data.repository.GridLayoutRepository
import com.thor.data.importer.CocoonImportResult
import com.thor.data.importer.CocoonImporter
import com.thor.data.profile.ProfileRepository
import com.thor.data.repository.LibraryRepository
import com.thor.data.scanner.EmulatorRegistry
import com.thor.data.sync.LibrarySyncManager
import com.thor.data.sync.MetadataSyncManager
import com.thor.data.sync.ScrapeState
import com.thor.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
    /** Placements, for the action that clears the grid without touching the library. */
    private val gridRepository: GridLayoutRepository,
    private val syncManager: LibrarySyncManager,
    private val metadataSyncManager: MetadataSyncManager,
    private val entryLauncher: EntryLauncher,
    private val aggregator: MetadataAggregator,
    private val defaultLauncherManager: DefaultLauncherManager,
    private val iconPackRepository: IconPackRepository,
    private val pointerService: PointerServiceManager,
    private val mediaRepository: MediaRepository,
    private val profileRepository: ProfileRepository,
    private val cocoonImporter: CocoonImporter,
    mouse: MouseController,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ---- Profiles ----------------------------------------------------------

    /**
     * Everyone on the device, and who is signed in.
     *
     * Read here rather than through the launcher shell because the page that
     * edits them lives in settings, and because switching profile replaces the
     * settings document this whole view model is built on — the registry is the
     * one thing on this screen that outlives the switch.
     */
    val profiles: StateFlow<ProfileRegistry> = profileRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileRegistry.EMPTY)

    /** Absolute path of a profile's picture, or null for the drawn initial. */
    fun avatarPathFor(profile: LauncherProfile): String? = profileRepository.avatarPath(profile)

    fun switchProfile(id: String) {
        viewModelScope.launch { profileRepository.switchTo(id) }
    }

    /**
     * Adds a profile without switching to it.
     *
     * Deliberate: the row that creates one sits on a settings page belonging to
     * the *current* profile, and switching underneath would replace the page
     * being read. The new profile is one press away in the list above.
     */
    fun createProfile() {
        viewModelScope.launch {
            val taken = profileRepository.profiles.first().profiles.size
            profileRepository.create(
                name = "Player ${taken + 1}",
                accentArgb = LauncherProfile.DEFAULT_ACCENT,
            )
        }
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch { profileRepository.rename(id, name) }
    }

    fun setProfileAccent(id: String, accentArgb: Long) {
        viewModelScope.launch { profileRepository.setAccent(id, accentArgb) }
    }

    fun setProfileAvatar(id: String, uri: String) {
        viewModelScope.launch { profileRepository.setAvatar(id, Uri.parse(uri)) }
    }

    fun clearProfileAvatar(id: String) {
        viewModelScope.launch { profileRepository.clearAvatar(id) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { profileRepository.delete(id) }
    }

    // ---- Extensions --------------------------------------------------------

    /**
     * Which optional parts are enabled, for navigation.
     *
     * The rail and the page lists are built from this, so an extension that has
     * not been imported has no category to land on and no page to open — the
     * cursor cannot reach a section that is not there.
     */
    private val enabledExtensions: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.enabledExtensions }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet(),
        )

    private val _importStatus = MutableStateFlow<String?>(null)

    /** What the last artwork import did, or null if there has not been one. */
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    /**
     * Copies artwork out of another launcher's media folder.
     *
     * Worth having because that data is already matched: somebody decided which
     * game each picture belongs to, correctly, once. Every scraper here has to
     * guess the same thing on every run.
     */
    fun importArtworkFolder(uri: String) {
        viewModelScope.launchSafely(TAG) {
            _importStatus.value = "Importing2026"
            _importStatus.value = when (val result = cocoonImporter.import(uri.toUri())) {
                is CocoonImportResult.Success ->
                    "Imported ${result.images} images for ${result.games} games"
                CocoonImportResult.NothingFound ->
                    "Nothing in that folder matched a game in this library"
                is CocoonImportResult.Failed -> result.reason
            }
        }
    }

    private val _extensionStatus = MutableStateFlow<String?>(null)

    /** What the last import attempt did, or null if there has not been one. */
    val extensionStatus: StateFlow<String?> = _extensionStatus.asStateFlow()

    /**
     * Reads an extension manifest and enables what it names.
     *
     * Every failure is reported in the row rather than logged and swallowed. An
     * import is a deliberate act with a file the user went and fetched, so "it
     * did nothing" is the one outcome that leaves them with nowhere to go —
     * a file that is the wrong kind, names an unknown extension, or cannot be
     * read all say which.
     */
    fun importExtension(uri: String) {
        viewModelScope.launchSafely(TAG) {
            /*
             * Off the main thread, as every other importer here already is.
             *
             * `viewModelScope` is `Main.immediate`, and `openInputStream` on a
             * document URI is a synchronous binder call into whichever provider
             * owns it. A file on local storage returns immediately and hid this;
             * one on Drive, an SMB share or a sleeping SD card fetches the
             * document first, and the launcher is frozen for as long as that
             * takes. `IconPackImporter` reads exactly the same way and has always
             * wrapped it.
             */
            val text = withContext(ioDispatcher) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri.toUri())
                        ?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }

            if (text.isNullOrBlank()) {
                _extensionStatus.value = "That file could not be read."
                return@launchSafely
            }

            val manifest = runCatching {
                EXTENSION_JSON.decodeFromString(ExtensionManifest.serializer(), text)
            }.getOrNull()

            val extension = manifest?.resolved
            if (extension == null) {
                _extensionStatus.value =
                    "That is not a Loki extension file, or it names one this " +
                    "version does not have."
                return@launchSafely
            }

            settingsRepository.setExtensionEnabled(extension.id, enabled = true)
            _extensionStatus.value = "${extension.displayName} added."
        }
    }

    /**
     * Turns an extension off again, hiding its section and settings.
     *
     * Clears the status rather than writing to it. [extensionStatus] is the
     * subtitle of the *import* row, so a removal message landed there read as
     * "Import an extension / Movies & TV removed." — an outcome reported by a
     * control that had nothing to do with it, and one that then sat there for the
     * life of the screen. The row for the extension itself already says what
     * happened by flipping to "Not added", which is where the user was looking.
     */
    fun removeExtension(extension: LauncherExtension) {
        viewModelScope.launchSafely(TAG) {
            settingsRepository.setExtensionEnabled(extension.id, enabled = false)
            _extensionStatus.value = null
        }
    }

    // ---- Icon packs --------------------------------------------------------

    val iconPacks: StateFlow<List<IconPack>> = iconPackRepository.installed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * What the last import did, shown until the next one starts.
     *
     * An import copies tens of megabytes and can take a few seconds, so it needs
     * a running state — and it can partly succeed, covering most platforms while
     * holding artwork for ones THOR does not model. "Done" is not enough to say.
     */
    private val _iconPackStatus = MutableStateFlow<IconPackStatus>(IconPackStatus.Idle)
    val iconPackStatus: StateFlow<IconPackStatus> = _iconPackStatus.asStateFlow()

    fun installIconPackFromFolder(uri: String) = installIconPack {
        iconPackRepository.installFromFolder(uri.toUri())
    }

    fun installIconPackFromZip(uri: String) = installIconPack {
        iconPackRepository.installFromZip(uri.toUri())
    }

    private fun installIconPack(block: suspend () -> IconPackImport) {
        if (_iconPackStatus.value is IconPackStatus.Working) return
        _iconPackStatus.value = IconPackStatus.Working
        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _iconPackStatus.value = IconPackStatus.Failed(
                    error.message ?: "The pack could not be imported",
                )
            },
        ) {
            _iconPackStatus.value = when (val result = block()) {
                is IconPackImport.Success -> IconPackStatus.Installed(
                    name = result.pack.name,
                    applied = result.applied.size,
                    held = result.held.size,
                )

                is IconPackImport.Failed -> IconPackStatus.Failed(result.reason)
            }
        }
    }

    // ---- Pointer -----------------------------------------------------------

    private val _pointerServiceEnabled = MutableStateFlow(pointerService.isEnabled())
    val pointerServiceEnabled: StateFlow<Boolean> = _pointerServiceEnabled.asStateFlow()

    /**
     * Whether the service is actually running, as opposed to merely permitted.
     *
     * These are two different things and the difference is invisible from the
     * settings screen otherwise: the accessibility toggle can read as on while
     * the service is not bound — after an update, a force stop, or a ROM that
     * drops it on reboot. The switch reports the grant; this reports the service
     * reporting itself alive, which is the one that predicts whether the pointer
     * will work outside THOR.
     */
    val pointerRunning: StateFlow<Boolean> = mouse.serviceConnected

    /**
     * Re-reads whether the service is on.
     *
     * Called when the page is opened, because the only way it changes is the user
     * leaving for system settings and coming back — there is nothing to observe
     * while THOR is in front.
     */
    fun refreshPointerService() {
        _pointerServiceEnabled.value = pointerService.isEnabled()
    }

    fun openPointerServiceSettings() {
        pointerService.openSettings()
    }

    fun updateMouse(transform: (MouseSettings) -> MouseSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateMouse(transform) }
    }

    fun updateMedia(transform: (MediaSettings) -> MediaSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateMedia(transform) }
    }

    /**
     * Streaming settings, including the hosts themselves.
     *
     * The host list lives in the same group, so this deliberately takes the whole
     * value rather than exposing quality alone — a caller that replaced the
     * group wholesale would drop every paired PC.
     */
    fun updateStream(transform: (StreamSettings) -> StreamSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateStream(transform) }
    }

    private val _debridStatus = MutableStateFlow<String?>(null)

    /**
     * What Real-Debrid said when last asked.
     *
     * Null until asked. A token that is present but expired, revoked or mistyped
     * is indistinguishable from a working one by inspection, and the symptom it
     * produces — sources listed, nothing ever opening — points nowhere near this
     * screen.
     */
    val debridStatus: StateFlow<String?> = _debridStatus.asStateFlow()

    fun checkDebrid() {
        _debridStatus.value = "Checking…"
        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error -> _debridStatus.value = error.message ?: "Check failed" },
        ) {
            _debridStatus.value = when (val status = mediaRepository.debridStatus()) {
                is DebridStatus.Connected -> buildString {
                    append("Connected as ${status.username}")
                    status.daysRemaining?.let { append(" · $it days left") }
                }

                is DebridStatus.NotConfigured -> "No token set"
                is DebridStatus.InvalidToken -> "That token was rejected"
                is DebridStatus.Error -> "Could not reach Real-Debrid: ${status.reason}"
            }
        }
    }

    /** Appends a blank addon for the user to paste an install URL into. */
    fun addAddon() = updateMedia { it.copy(addons = it.addons + StremioAddon()) }

    fun removeAddon(index: Int) = updateMedia { media ->
        media.copy(addons = media.addons.filterIndexed { i, _ -> i != index })
    }

    /**
     * Stores a pasted URL, normalised, and asks the addon what it is called.
     *
     * The name is what turns a list of URLs into a list of installed addons, and
     * getting one back is the only confirmation that the endpoint answers at all
     * — a URL with a typo looks exactly like a working one until the first search
     * comes back empty.
     */
    fun setAddonUrl(index: Int, url: String) {
        updateMedia { media ->
            media.copy(
                addons = media.addons.mapIndexed { i, addon ->
                    // The name belonged to the previous URL; clearing it stops a
                    // stale one vouching for an endpoint nobody has checked.
                    if (i == index) addon.copy(url = url, name = "") else addon
                },
            )
        }
    }

    /** What each addon said when last checked, keyed by position. */
    private val _addonStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    val addonStatus: StateFlow<Map<Int, String>> = _addonStatus.asStateFlow()

    /**
     * Asks an addon whether it will actually serve streams.
     *
     * The verdict comes from the *stream* endpoint rather than the manifest, and
     * the two disagree more often than they ought to: an addon behind a CDN can
     * serve streams perfectly while its manifest returns a gateway error, and the
     * old check read only the manifest — so a working addon reported nothing at
     * all and the row kept saying "Check this addon", which is exactly what it
     * says before it has ever been pressed.
     */
    fun checkAddon(index: Int) {
        val addon = settings.value.media.addons.getOrNull(index) ?: return
        _addonStatus.update { it + (index to "Checking…") }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _addonStatus.update { it + (index to (error.message ?: "Check failed")) }
            },
        ) {
            val normalised = StremioAddons.normalise(addon.url)
            val result = mediaRepository.checkAddon(normalised)

            _addonStatus.update { it + (index to (result.note ?: "Serving streams")) }

            updateMedia { media ->
                media.copy(
                    addons = media.addons.mapIndexed { i, existing ->
                        if (i == index) {
                            // The stored name is only ever replaced by a better
                            // one: a manifest that failed this time must not
                            // erase a name a previous check established.
                            existing.copy(
                                url = normalised,
                                name = result.name ?: existing.name,
                            )
                        } else {
                            existing
                        }
                    },
                )
            }
        }
    }

    /**
     * What each indexer said when last tested, keyed by its position.
     *
     * Held here rather than written into the settings document: it is the result
     * of a question asked just now, not a preference, and persisting it would
     * leave yesterday's "Answering" on screen beside an indexer that has since
     * gone down.
     */
    private val _indexerStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    val indexerStatus: StateFlow<Map<Int, String>> = _indexerStatus.asStateFlow()

    /**
     * Asks one indexer whether it actually answers.
     *
     * The row could otherwise only report whether its fields were filled in, and
     * said "Ready" for a mistyped host, a revoked key, or a Jackett that is not
     * running. All three surface much later as a film with no sources, on a
     * screen with nothing to point at.
     */
    fun checkIndexer(index: Int) {
        val indexer = settings.value.media.indexers.getOrNull(index) ?: return
        _indexerStatus.update { it + (index to "Checking…") }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _indexerStatus.update { it + (index to (error.message ?: "Check failed")) }
            },
        ) {
            _indexerStatus.update { it + (index to mediaRepository.testIndexer(indexer)) }
        }
    }

    /** Appends a blank indexer for the user to fill in. */
    fun addIndexer() = updateMedia { it.copy(indexers = it.indexers + TorznabIndexer()) }

    fun removeIndexer(index: Int) = updateMedia { media ->
        media.copy(indexers = media.indexers.filterIndexed { i, _ -> i != index })
    }

    fun updateIndexer(index: Int, transform: (TorznabIndexer) -> TorznabIndexer) =
        updateMedia { media ->
            media.copy(
                indexers = media.indexers.mapIndexed { i, indexer ->
                    if (i == index) transform(indexer) else indexer
                },
            )
        }

    fun removeIconPack(packId: String) {
        viewModelScope.launchSafely(TAG) {
            iconPackRepository.remove(packId)
            _iconPackStatus.value = IconPackStatus.Idle
        }
    }

    /**
     * Null is the only pre-load state. Keeping the loaded flag and its settings in
     * one value means startup audio can never observe "loaded" alongside defaults.
     */
    val loadedSettings: StateFlow<ThorSettings?> = settingsRepository.settings
        .map<ThorSettings, ThorSettings?> { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Existing settings consumers still receive a non-null default immediately. */
    val settings: StateFlow<ThorSettings> = loadedSettings
        .map { it ?: ThorSettings.DEFAULT }
        .stateIn(
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
    /**
     * Every platform the user has not added yet.
     *
     * This used to be filtered against a hardcoded list of four ids — N64, DS,
     * 3DS and Switch. Once those four were added the list was empty, and an
     * empty list makes the "Add platform" row a deliberate no-op: it is not
     * disabled, it simply does nothing when pressed, which reads as a broken
     * button rather than as "there is nothing left to add".
     *
     * There is no reason for a whitelist. Every built-in platform already exists
     * in the database so the scanner can recognise its file types, and adding one
     * is exactly the act of saying "I own this system" — so all of them are
     * offered, in catalogue order.
     */
    val availablePlatforms: StateFlow<List<Platform>> = libraryRepository.platforms
        .map { all ->
            all.filterNot(Platform::isAdded).sortedBy(Platform::sortIndex)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The platform whose setup dialog is open, if any. */
    private val _pendingPlatform = MutableStateFlow<Platform?>(null)
    val pendingPlatform: StateFlow<Platform?> = _pendingPlatform.asStateFlow()
    val isAddingPlatform: Boolean get() = _pendingPlatform.value != null

    /** Emulators installed for the platform currently being added. */
    fun installedEmulatorsFor(platform: Platform): List<Pair<String, String>> =
        entryLauncher.installedEmulatorsFor(platform.id).map { packageName ->
            packageName to (EmulatorRegistry.specFor(packageName)?.displayName ?: packageName)
        }

    fun beginAddPlatform(platform: Platform) {
        _pendingPlatform.value = platform
        _horizontalRows.value = emptySet()
        _focusOnRail.value = false
        _focusedRow.value = 0
    }

    fun cancelAddPlatform() {
        _pendingPlatform.value = null
        _horizontalRows.value = emptySet()
        _focusedRow.value = 0
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
            // Same as [addPlatform]: a pack may have been holding this system's
            // artwork since before THOR modelled it.
            iconPackRepository.applyToNewPlatforms()
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
        _horizontalRows.value = emptySet()
        _focusedRow.value = 0
    }

    fun openPage(page: SettingsPage) {
        _selectedCategory.value = page.category
        _openPage.value = page
        _horizontalRows.value = emptySet()
        _focusOnRail.value = false
        _focusedRow.value = 0
    }

    /** Returns from a page to its category's list. */
    fun closePage() {
        _openPage.value = null
        _horizontalRows.value = emptySet()
        _focusedRow.value = 0
    }

    /** True when Back should close the overlay rather than a page. */
    val isAtTopLevel: Boolean get() = _openPage.value == null

    fun focusRow(index: Int) {
        _focusedRow.value = index.coerceAtLeast(0)
    }

    fun clampFocusedRow(rowCount: Int) {
        _focusedRow.value = _focusedRow.value.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
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
                val entries = SettingsCategory.navigationEntries(enabledExtensions.value)
                val index = entries.indexOf(_selectedCategory.value)
                selectCategory(entries[(index - 1 + entries.size) % entries.size])
            } else {
                _focusedRow.value = (_focusedRow.value - 1).coerceAtLeast(0)
            }
            true
        }

        ControllerCommand.NAVIGATE_DOWN -> {
            if (_focusOnRail.value) {
                val entries = SettingsCategory.navigationEntries(enabledExtensions.value)
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
                isAddingPlatform -> Unit
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
                isAddingPlatform -> Unit
                _focusOnRail.value && rowCount > 0 -> {
                    _focusOnRail.value = false
                    _focusedRow.value = 0
                }
            }
            true
        }

        ControllerCommand.CONFIRM -> {
            val pages = SettingsPage.forCategory(
                _selectedCategory.value,
                enabledExtensions.value,
            )
            when {
                // From the rail, step into the category's rows.
                _focusOnRail.value && rowCount > 0 -> {
                    _focusOnRail.value = false
                    _focusedRow.value = 0
                    true
                }

                /*
                 * From a page list, open the highlighted page.
                 *
                 * `pages.isNotEmpty()` is what makes About work, and its absence
                 * is why every control on that screen ignored the controller.
                 * About is a *pane*, not a list of pages, so `_openPage` is
                 * always null there — this branch was therefore always taken, it
                 * looked in an empty page list, found nothing, and returned
                 * false. The activation tick below was never reached, so the
                 * counter each row watches never moved and Confirm did nothing.
                 */
                _openPage.value == null && pages.isNotEmpty() ->
                    pages.getOrNull(_focusedRow.value)?.let(::openPage) != null

                // Inside a page, or on a pane with rows of its own: broadcast to
                // the focused row so its control acts — a toggle flips, an
                // action fires, a choice advances.
                else -> {
                    _activationTick.value += 1
                    true
                }
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
        _horizontalRows.value = emptySet()
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

    /**
     * Records that the walkthrough has been read.
     *
     * Written whichever way it was left — finished, backed out of, or closed —
     * because all three mean the same thing: the user has seen it and does not
     * need it again unasked.
     */
    fun completeTutorial() {
        viewModelScope.launchSafely(TAG) { settingsRepository.setTutorialCompleted(true) }
    }

    /**
     * Clears the record so the walkthrough shows again.
     *
     * The shell watches the same flag that raises it on a first run, so putting
     * it back is the whole of "replay" — there is no second path to keep in step
     * with the first.
     */
    fun replayTutorial() {
        viewModelScope.launchSafely(TAG) { settingsRepository.setTutorialCompleted(false) }
    }

    /** Records that [extension]'s own short walkthrough has been played. */
    fun completeExtensionTour(extension: LauncherExtension) {
        viewModelScope.launchSafely(TAG) {
            settingsRepository.setExtensionTourSeen(extension.id)
        }
    }

    /** Records that the first-run permission list has been shown. */
    fun dismissPermissionsPrompt() {
        viewModelScope.launchSafely(TAG) { settingsRepository.setPermissionsPromptSeen(true) }
    }

    fun scanLibrary() {
        syncManager.requestFullScan()
    }

    /**
     * What the last "clear the grid" did, so the row can say so.
     *
     * Null until it has been used. An action whose entire visible effect is on
     * another screen needs to report on itself, or pressing it looks identical
     * to pressing nothing.
     */
    private val _gridClearResult = MutableStateFlow<String?>(null)
    val gridClearResult: StateFlow<String?> = _gridClearResult.asStateFlow()

    /**
     * Takes every game off the grid, leaving the library alone.
     *
     * The games stay scanned, stay searchable and stay inside their platform
     * folders — only their cells go. That is what makes this recoverable: a
     * rescan files them back, and nothing has been deleted in the meantime.
     */
    fun clearGamesFromGrid() {
        viewModelScope.launchSafely(
            tag = TAG,
            onError = { _gridClearResult.value = "Could not clear the grid" },
        ) {
            val cleared = gridRepository.clearGamePlacements()
            _gridClearResult.value = when (cleared) {
                0 -> "No games were on the grid"
                1 -> "Removed 1 game from the grid"
                else -> "Removed $cleared games from the grid"
            }
        }
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

    // Trimmed on the way in: both are copied out of a web console, and a pasted
    // trailing space turns a correct credential into a rejected one.
    fun setIgdbClientId(value: String) =
        updateScreenScraper { it.copy(igdbClientId = value.trim()) }

    fun setIgdbClientSecret(value: String) =
        updateScreenScraper { it.copy(igdbClientSecret = value.trim()) }



    private fun updateScreenScraper(transform: (MetadataSettings) -> MetadataSettings) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateMetadata(transform) }
    }

    fun addPlatform(platformId: String) {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.addPlatform(platformId)
            // An installed pack may already hold artwork for this system, imported
            // when THOR had no platform to put it on. This is the moment it does.
            iconPackRepository.applyToNewPlatforms()
        }
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

    /**
     * Scrapes one system's games, leaving the rest of the library alone.
     *
     * The useful unit of a scrape. A full pass is hundreds of rate-limited calls
     * across every console you own, so fixing the artwork on one of them meant
     * paying for all of them — and after adding a system, everything else has
     * already been scraped and only the new one needs anything.
     *
     * Not "only missing": asking for a specific system is asking for it to be
     * done again, which is the whole reason to single one out.
     */
    fun scrapePlatform(platformId: String) {
        metadataSyncManager.requestScrape(onlyMissing = false, platformId = platformId)
    }

    /** Fetches trailers for games that have none, without re-scraping the rest. */
    fun refreshTrailers() {
        metadataSyncManager.requestTrailerRefresh()
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
     * True when scraping can run but no enabled source can return descriptions.
     *
     * Facts-only sources must not hide this warning. A generic "text provider"
     * check previously counted sources that never wrote the description field.
     */
    private val _artworkOnlyProviders = MutableStateFlow(false)
    val artworkOnlyProviders: StateFlow<Boolean> = _artworkOnlyProviders.asStateFlow()

    /**
     * Whether anything configured can supply a landscape image of a game.
     *
     * Its own signal because it is invisible otherwise: SteamGridDB fills every
     * cover on the grid, so the scrape plainly worked, while the information
     * panel has nothing to show and no way to say why.
     */
    private val _noScreenshotProvider = MutableStateFlow(false)
    val noScreenshotProvider: StateFlow<Boolean> = _noScreenshotProvider.asStateFlow()

    /**
     * Whether this build carries ScreenScraper developer credentials.
     *
     * Worth its own signal because the account fields sit right there and look
     * like the thing that turns the provider on. They are not: the developer
     * pair is compiled in, the account only raises the quota, and a user who
     * fills in both fields and sees nothing change has no way to know that.
     */
    private val _screenScraperKeyMissing = MutableStateFlow(false)
    val screenScraperKeyMissing: StateFlow<Boolean> = _screenScraperKeyMissing.asStateFlow()

    init {
        /*
         * Recomputed whenever the credentials change, not only when the user
         * presses Check. These warnings answer "why is nothing arriving", and
         * requiring a button press to find out was asking the user to suspect the
         * settings they were already looking at.
         */
        settingsRepository.metadata
            .onEach {
                _artworkOnlyProviders.value =
                    aggregator.hasUsableProvider() && !aggregator.hasDescriptionProvider()
                _noScreenshotProvider.value =
                    aggregator.hasUsableProvider() && !aggregator.hasScreenshotProvider()
                _screenScraperKeyMissing.value = !aggregator.isProviderConfigured("screenscraper")
            }
            .launchIn(viewModelScope)
    }

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
                aggregator.hasUsableProvider() && !aggregator.hasDescriptionProvider()
            _noScreenshotProvider.value =
                aggregator.hasUsableProvider() && !aggregator.hasScreenshotProvider()
            _checkingProviders.value = false
        }
    }

    private companion object {
        /**
         * Lenient on purpose: these files are written by hand.
         *
         * An extension file is a few lines somebody may well have typed
         * themselves, so an unexpected field or a trailing comma should not be
         * the difference between a working import and a message saying the file
         * is not a Loki extension.
         */
        val EXTENSION_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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
    }
}
