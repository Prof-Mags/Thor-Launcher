package com.thor.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.AppEntry
import com.thor.core.model.ControllerCommand
import com.thor.core.model.ControlSettings
import com.thor.core.model.DualScreenMode
import com.thor.core.model.FolderEntry
import com.thor.core.common.capture.ScreenshotBridge
import com.thor.core.model.GameEntry
import com.thor.core.model.GameJournal
import com.thor.core.model.HomeLayout
import com.thor.data.journal.GameJournalRepository
import com.thor.core.model.GameMetadata
import com.thor.core.model.GridEntry
import com.thor.core.model.CellSpan
import com.thor.core.model.GridFootprint
import com.thor.core.model.GridSpec
import com.thor.core.model.LauncherWidget
import com.thor.core.model.WidgetEntry
import com.thor.core.model.KeyboardKey
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.LauncherAction
import com.thor.core.model.LauncherTab
import com.thor.core.model.NavDirection
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFolders
import com.thor.feature.home.cards.PlatformCard
import com.thor.feature.home.companion.CompanionAction
import com.thor.feature.home.companion.COMPANION_ACTIONS
import com.thor.feature.home.dialog.NoteDialogState
import com.thor.feature.home.cards.platformCards
import com.thor.feature.home.cards.stepCard
import com.thor.core.model.PreferredPanel
import com.thor.core.model.ShortcutAction
import com.thor.core.model.ShortcutGrid
import com.thor.core.model.SortOrder
import com.thor.core.model.ThorKeyboardLayout
import com.thor.data.capture.RecordingState
import com.thor.data.capture.ScreenRecorder
import com.thor.data.clipboard.ThorClipboard
import com.thor.data.launcher.EntryLauncher
import com.thor.data.launcher.LaunchFailure
import com.thor.data.launcher.LaunchResult
import com.thor.data.launcher.LaunchTarget
import com.thor.data.launcher.SystemPanel
import com.thor.data.library.GridLayoutRepository
import com.thor.data.library.LibraryRepository
import com.thor.data.library.MoveResult
import com.thor.data.library.SmartQueryEvaluator
import com.thor.data.achievements.AchievementRepository
import com.thor.data.metadata.MetadataCandidate
import com.thor.data.widget.WidgetOption
import com.thor.data.widget.WidgetRepository
import com.thor.data.sync.LibrarySyncManager
import com.thor.data.sync.PlaytimeTracker
import com.thor.data.sync.SyncState
import com.thor.feature.home.couch.buildCouchRails
import com.thor.feature.home.couch.COUCH_RAIL_APPS
import com.thor.feature.home.couch.CouchDetailScroll
import com.thor.feature.home.couch.CouchFocus
import com.thor.feature.home.couch.couchLibraryRailIndex
import com.thor.feature.home.couch.CouchNavigation
import com.thor.feature.home.couch.couchPlatforms
import com.thor.feature.home.couch.CouchRail
import com.thor.feature.home.couch.CouchZone
import com.thor.feature.home.couch.platform
import com.thor.feature.home.dialog.EmulatorOption
import com.thor.feature.home.dialog.EntryEdits
import com.thor.feature.home.dialog.FolderPickerState
import com.thor.feature.home.dialog.MatchPickerState
import com.thor.feature.home.dialog.WidgetChoice
import com.thor.feature.home.dialog.WidgetPickerState
import com.thor.feature.home.menu.CELL_ACTIONS
import com.thor.feature.home.menu.CellAction
import com.thor.feature.home.menu.CellMenuState
import com.thor.feature.home.menu.CONTEXT_MENU_COLUMNS
import com.thor.feature.home.menu.ContextAction
import com.thor.feature.home.menu.stepContextMenuColumn
import com.thor.feature.home.menu.stepContextMenuRow
import com.thor.feature.home.menu.contextActionsFor
import com.thor.feature.home.menu.SideMenuAction
import com.thor.feature.home.shell.icon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the launcher home experience across both displays.
 *
 * All navigation is expressed as cursor movement over a sparse grid, because
 * that is the model both touch and controller input have to agree on: a tap
 * moves the cursor and confirms, a D-pad press only moves it. Keeping one
 * cursor concept means the top screen never has to ask which input device
 * caused a selection change.
 */
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val gridRepository: GridLayoutRepository,
    private val settingsRepository: SettingsRepository,
    private val entryLauncher: EntryLauncher,
    private val syncManager: LibrarySyncManager,
    private val playtimeTracker: PlaytimeTracker,
    private val screenRecorder: ScreenRecorder,
    private val clipboard: ThorClipboard,
    private val widgetRepository: WidgetRepository,
    private val achievementRepository: AchievementRepository,
    private val journalRepository: GameJournalRepository,
    private val screenshots: ScreenshotBridge,
) : ViewModel() {

    private val cursor = MutableStateFlow(CursorPosition(0, 0))
    private val currentPage = MutableStateFlow(0)
    private val editMode = MutableStateFlow<EditMode>(EditMode.None)
    private val openFolderId = MutableStateFlow<String?>(null)
    private val sideMenuOpen = MutableStateFlow(false)
    private val sideMenuIndex = MutableStateFlow(0)
    /**
     * Whether a second panel is attached.
     *
     * Cached rather than queried inside the state transform: that ran a binder
     * call into DisplayManager on *every* emission — every cursor move, every
     * scan progress tick — to answer a question that only changes when hardware
     * is plugged or unplugged. Refreshed when the context menu opens, which is
     * the only thing that consumes it.
     */
    private val hasSecondScreen = MutableStateFlow(false)

    private val contextMenuEntryId = MutableStateFlow<String?>(null)
    private val contextMenuIndex = MutableStateFlow(0)
    private val editingEntryId = MutableStateFlow<String?>(null)

    /**
     * Which screenshot the top screen is showing for the currently focused game.
     *
     * Keyed by entry id rather than reset on every selection change, so a stale
     * index from a previously viewed game is simply ignored — [screenshotIndex]
     * falls back to 0 the moment the id stops matching — instead of needing an
     * explicit reset call threaded through cursor movement.
     */
    private val screenshotCursor = MutableStateFlow(ScreenshotCursor())

    /**
     * Whether something was launched onto the secondary panel.
     *
     * The presentation on that panel has to be dismissed to let the launched app
     * be seen, but *only* then. Hiding it whenever the launcher merely loses
     * focus — a launch onto the primary panel, the notification shade, a
     * permission dialog — uncovers whatever the system has behind it on that
     * display, which is its own default launcher: the stray home screen that
     * appeared on one panel.
     *
     * Must be declared above the `init` block that clears it via
     * [settlePlaytime]; property initialisers run in declaration order, so a
     * later declaration is still null when `init` runs.
     */
    private val _secondScreenOccupied = MutableStateFlow(false)
    val secondScreenOccupied: StateFlow<Boolean> = _secondScreenOccupied.asStateFlow()

    /**
     * The entry the launcher last handed a panel to, while it still has it.
     *
     * [_secondScreenOccupied] says *that* a panel is taken; this says by what, and
     * the difference is the whole of the companion panel and of filing a
     * screenshot against the right game. Set beside the flag rather than derived
     * from the foreground app, which would mean reading which app is open — a
     * thing the accessibility service deliberately does not do.
     *
     * Cleared wherever the flag is, so the two cannot disagree. A stale id would
     * be worse than none: it would attribute a screenshot to whatever was played
     * before.
     */
    private val _runningEntryId = MutableStateFlow<String?>(null)
    val runningEntryId: StateFlow<String?> = _runningEntryId.asStateFlow()

    /** When the current hand-over happened, for the companion panel's timer. */
    private val _runningSinceEpochMs = MutableStateFlow<Long?>(null)
    val runningSinceEpochMs: StateFlow<Long?> = _runningSinceEpochMs.asStateFlow()

    /**
     * Records that a panel has been handed to an entry, or taken back.
     *
     * One function rather than three assignments at each of the four sites that
     * change the flag, because the invariant that matters — an id and a start time
     * exist exactly when the panel is occupied — is the kind that decays when it
     * is spelled out repeatedly.
     */
    private fun setPanelOccupant(entryId: String?) {
        _secondScreenOccupied.value = entryId != null
    }

    /**
     * Records that a game has been started, or has been let go of.
     *
     * Separate from [setPanelOccupant], and the first version of this conflated
     * the two — which is why the companion panel never appeared. They are
     * different facts. "The second panel is occupied" is about *which surface the
     * launcher still owns*, and is false for the ordinary case: a game opens on
     * the top screen and the launcher keeps the bottom one. "A game is running" is
     * true either way, and is the only one the companion panel cares about.
     *
     * Tied to the launcher's own belief rather than to the game's actual life,
     * because nothing can see the latter — see the known limits in the README.
     * Home is what says otherwise, which is the same gesture that takes a panel
     * back, so there is one way out rather than two.
     */
    private fun setRunningEntry(entryId: String?, nowMs: Long = System.currentTimeMillis()) {
        _runningEntryId.value = entryId
        _runningSinceEpochMs.value = entryId?.let { nowMs }
        _companionAction.value = 0
        /*
         * Told to the pointer service, which draws Loki's panel over the game and
         * has to be able to name what is running.
         *
         * Published rather than discovered: the alternative is that service asking
         * the system which app is in front, and the reason it can be trusted with
         * the permissions it holds is that it reads nothing about any app. The
         * launcher already knows.
         */
        screenshots.setNowPlaying(entryId?.let { uiState.value.entriesById[it]?.title })
    }

    /** Which tile the companion panel's cursor is on. */
    private val _companionAction = MutableStateFlow(0)
    val companionAction: StateFlow<Int> = _companionAction.asStateFlow()

    /** Whether the secondary panel's Presentation is still attached to its display. */
    private val secondaryPresentationVisible = MutableStateFlow(false)

    /** Exactly one external launch may own a display handoff at a time. */
    private var launchJob: Job? = null

    /**
     * The folder picker raised by "Move to folder…".
     *
     * Up here with the rest of the state for the same reason as
     * [_secondScreenOccupied]: overlay state is reachable from input routing and
     * from `init`, and a declaration below an `init` block is null while it runs.
     */
    private val _folderPicker = MutableStateFlow(FolderPickerState())
    val folderPicker: StateFlow<FolderPickerState> = _folderPicker.asStateFlow()

    /**
     * Navigation preferences, held rather than fetched.
     *
     * [move] used to read these straight from DataStore — two suspending reads,
     * inside a launched coroutine, on *every* press of a direction. At the
     * auto-repeat rate that is a queue of coroutines each awaiting two flow
     * emissions before the cursor is allowed to move, and because they are
     * separate coroutines nothing guarantees they finish in the order the presses
     * arrived. Held keys therefore moved the cursor late, unevenly, and
     * occasionally in the wrong order.
     *
     * Cursor movement is a synchronous, main-thread operation over snapshot state
     * and should read like one. `Eagerly` because the first press must not be the
     * one that pays for the subscription.
     */
    private val controlSettings: StateFlow<ControlSettings> = settingsRepository.controls
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControlSettings())

    /**
     * Whether the launcher is being driven from across a room.
     *
     * Read from the setting rather than passed in with each command, because it
     * has to be known in places a command never reaches — a long press, a tap on
     * a panel's own button. The shell honours this mode exactly as it is chosen,
     * with or without a second panel, so the setting and what is on screen cannot
     * disagree.
     */
    private val couchMode: StateFlow<Boolean> = settingsRepository.display
        .map { it.mode == DualScreenMode.COUCH }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * What Home draws on this panel; see [HomeLayout].
     *
     * Read here for the same reason [couchMode] is: it decides what a press
     * *means* before it decides what is drawn, and a layout passed in with each
     * command could disagree with the one on screen for exactly one frame — which
     * is the frame in which Confirm launches the wrong thing.
     */
    private val homeLayout: StateFlow<HomeLayout> = settingsRepository.display
        .map { it.homeLayout }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.GRID)

    /**
     * Which system the card flow is showing.
     *
     * Held here rather than in [LauncherUiState] because it survives things the
     * grid's cursor does not: opening a system, launching from inside it and
     * coming back should return to the card you left, and that is a fact about
     * this cursor rather than about the grid's.
     */
    private val _platformCardIndex = MutableStateFlow(0)
    val platformCardIndex: StateFlow<Int> = _platformCardIndex.asStateFlow()

    /**
     * Which way the last step went, for the card that slides in.
     *
     * Kept rather than derived by comparing the old and new index, because
     * [stepCard] wraps: stepping right off the end lands on a *lower* index, and
     * an animation inferred from that comparison would slide the opposite way to
     * the button that was pressed.
     */
    private val _platformCardDirection = MutableStateFlow(1)
    val platformCardDirection: StateFlow<Int> = _platformCardDirection.asStateFlow()

    // ---- Sections ----------------------------------------------------------

    /** Which top-level section the launcher is showing. */
    private val _selectedTab = MutableStateFlow(LauncherTab.DEFAULT)
    val selectedTab: StateFlow<LauncherTab> = _selectedTab.asStateFlow()

    /**
     * The tab the controller cursor is sitting on, or null when it is in the
     * content above the bar.
     *
     * Separate from [selectedTab] for the reason every cursor-driven list in this
     * launcher keeps the two apart: you move across the bar to look before you
     * press, and a bar where moving *is* selecting would tear the whole section
     * down and rebuild it on every press of Right.
     *
     * Null means section content holds the cursor: the handheld grid, Couch
     * Mode's rails, the Movies catalogue, or the Stream host dashboard.
     */
    private val _navCursor = MutableStateFlow<LauncherTab?>(null)
    val navCursor: StateFlow<LauncherTab?> = _navCursor.asStateFlow()

    /** Independent focus for Couch Mode's content rails; never mapped to grid cells. */
    private val _couchFocus = MutableStateFlow(CouchFocus())
    val couchFocus: StateFlow<CouchFocus> = _couchFocus.asStateFlow()

    /** Entry shown in Couch Mode's on-demand Y-button details overlay. */
    private val _couchQuickDetailsEntryId = MutableStateFlow<String?>(null)
    val couchQuickDetailsEntryId: StateFlow<String?> =
        _couchQuickDetailsEntryId.asStateFlow()

    /** Controller cursor for Play, Favourite, More, and Close in the Y panel. */
    private val _couchQuickDetailsActionIndex = MutableStateFlow(0)
    val couchQuickDetailsActionIndex: StateFlow<Int> =
        _couchQuickDetailsActionIndex.asStateFlow()

    /**
     * The stick's requests to move that panel's reading column.
     *
     * A direction and a count rather than a position: how far the description
     * runs is known to the composable measuring it and to nothing here.
     */
    private val _couchQuickDetailsScroll = MutableStateFlow(CouchDetailScroll())
    val couchQuickDetailsScroll: StateFlow<CouchDetailScroll> =
        _couchQuickDetailsScroll.asStateFlow()

    /** Last highlighted item in every Couch rail, including each platform rail. */
    private val couchItemByRailId = mutableMapOf<String, Int>()

    /** Platform shown by Couch Mode's LT/RT library strip. */
    private val _couchPlatformIndex = MutableStateFlow(0)
    val couchPlatformIndex: StateFlow<Int> = _couchPlatformIndex.asStateFlow()

    /** Settings is a real final item in Couch Mode's top navigation. */
    private val _couchSettingsFocused = MutableStateFlow(false)
    val couchSettingsFocused: StateFlow<Boolean> = _couchSettingsFocused.asStateFlow()

    // Rail construction sorts the library. Cache by the immutable collection
    // instances carried through LauncherUiState so a held D-pad never re-sorts
    // hundreds or thousands of games on every repeat event.
    private var couchEntriesRef: Map<String, GridEntry>? = null
    private var couchFolderRef: List<GridEntry>? = null
    private var couchFolderId: String? = null
    private var couchPlatformsRef: Map<String, Platform>? = null
    private var couchSelectedPlatformId: String? = null
    private var cachedCouchRails: List<CouchRail> = emptyList()
    private var couchPlatformEntriesRef: Map<String, GridEntry>? = null
    private var couchPlatformDefinitionsRef: Map<String, Platform>? = null
    private var cachedCouchPlatforms: List<Platform> = emptyList()

    private fun availableCouchPlatforms(state: LauncherUiState): List<Platform> {
        if (
            couchPlatformEntriesRef === state.entriesById &&
            couchPlatformDefinitionsRef === state.platformsById
        ) {
            return cachedCouchPlatforms
        }
        couchPlatformEntriesRef = state.entriesById
        couchPlatformDefinitionsRef = state.platformsById
        return state.couchPlatforms().also { cachedCouchPlatforms = it }
    }

    private fun couchRails(state: LauncherUiState): List<CouchRail> {
        val platforms = availableCouchPlatforms(state)
        val safePlatformIndex = _couchPlatformIndex.value.coerceIn(
            0,
            (platforms.size - 1).coerceAtLeast(0),
        )
        if (platforms.isNotEmpty() && safePlatformIndex != _couchPlatformIndex.value) {
            _couchPlatformIndex.value = safePlatformIndex
        }
        val selectedPlatformId = platforms.getOrNull(safePlatformIndex)?.id
        if (
            couchEntriesRef === state.entriesById &&
            couchFolderRef === state.openFolderContents &&
            couchFolderId == state.openFolderId &&
            couchPlatformsRef === state.platformsById &&
            couchSelectedPlatformId == selectedPlatformId
        ) {
            return cachedCouchRails
        }
        couchEntriesRef = state.entriesById
        couchFolderRef = state.openFolderContents
        couchFolderId = state.openFolderId
        couchPlatformsRef = state.platformsById
        couchSelectedPlatformId = selectedPlatformId
        return buildCouchRails(state, selectedPlatformId).also { cachedCouchRails = it }
    }

    /** True while the controller is on the nav bar rather than in the content. */
    private val isNavBarFocused: Boolean get() = _navCursor.value != null

    /**
     * Moves the cursor out of content and onto the section bar.
     *
     * The bar is reached by walking into it, not by a dedicated button: pressing
     * Down past the bottom row is what every ten-foot interface does, and it is
     * the one gesture that needs no discovering. It lands on the *selected* tab
     * rather than the first, so arriving somewhere and pressing Confirm is a
     * no-op instead of a section change nobody asked for.
     */
    fun enterNavBar() {
        _couchSettingsFocused.value = false
        _navCursor.value = _selectedTab.value
    }

    /**
     * Returns to Home when the section being shown has been withdrawn.
     *
     * Removing an extension takes its section off the bar, but the launcher was
     * still *on* that section: the tab kept rendering, the info panel kept
     * hosting it, and with only Home left the bar is not drawn at all — so the
     * cursor sat on a tab that nothing painted, over a section that no longer
     * existed, with no visible way back. Watched rather than handled at the point
     * of removal because the same thing is true of a settings import, a restore
     * and a reset, and only one of those goes through the Extensions page.
     */
    private fun observeWithdrawnSections() {
        viewModelScope.launch {
            /*
             * The display mode as well as the extensions, because Shows is a tab
             * only couch mode draws. Leaving couch mode with it selected has to
             * put the shell somewhere the handheld bar can actually show.
             */
            combine(
                settingsRepository.settings.map { it.enabledExtensions },
                settingsRepository.display.map { it.mode == DualScreenMode.COUCH },
            ) { enabled, couch -> enabled to couch }
                .distinctUntilChanged()
                .collect { (enabled, couch) ->
                    val sections = LauncherTab.visible(enabled, couch)
                    if (_selectedTab.value !in sections) {
                        _selectedTab.value = LauncherTab.landing(_selectedTab.value, enabled, couch)
                    }
                    // Off the bar too, if it is pointing at a tab that is gone —
                    // or at any tab at all once the bar itself stops being drawn.
                    if (sections.size < 2 || _navCursor.value !in sections) {
                        _navCursor.value = null
                        _couchSettingsFocused.value = false
                    }
                }
        }
    }

    /**
     * Returns the cursor to the content above.
     *
     * Movies and Stream own controller cursors as well, so every section can
     * receive focus after the bar has selected it.
     */
    fun leaveNavBar() {
        _navCursor.value = null
        _couchSettingsFocused.value = false
    }

    /**
     * Points the shell at a section without moving the bar's cursor.
     *
     * For a section that changes which tab it *is*: couch mode draws Films and
     * Shows as two tabs over one catalogue, so switching media type from inside
     * the section has to move the highlight in the bar to match. [selectTab] would
     * also park the cursor on the bar, which means something else entirely — that
     * is what a press *on* the bar does, not what a press inside a section does.
     */
    fun showSection(tab: LauncherTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
    }

    /** Selects a section, from a tap or from Confirm on the bar. */
    fun selectTab(tab: LauncherTab) {
        _selectedTab.value = tab
        _couchSettingsFocused.value = false
        closeCouchQuickDetails()
        // The cursor follows the selection and stays on the bar until the user
        // deliberately enters that section's content.
        _navCursor.value = tab
        // A section change closes what was raised over the previous one, so
        // switching to Movies and back does not restore a menu nobody left open.
        if (!tab.isHome) {
            sideMenuOpen.value = false
            contextMenuEntryId.value = null
            closeAppDrawer()
        }
    }

    /**
     * Controller input while the bar holds the cursor.
     *
     * Confirm is what commits a section, so Left and Right only move the cursor —
     * see [navCursor].
     */
    /**
     * The extensions currently enabled, for walking the section bar.
     *
     * Read from the live state rather than collected into a field of its own:
     * the bar is stepped by a press, so the value is only ever wanted at the
     * moment of one, and a stale copy would let the cursor stop on a section
     * that is no longer there.
     */
    private fun enabledExtensionIds(): Set<String> = uiState.value.enabledExtensions

    private fun onNavBarCommand(command: ControllerCommand, couchMode: Boolean) {
        val focused = _navCursor.value ?: return
        val tabs = LauncherTab.visible(enabledExtensionIds(), couchMode)
        if (couchMode && _couchSettingsFocused.value) {
            when (command) {
                ControllerCommand.NAVIGATE_LEFT -> {
                    _couchSettingsFocused.value = false
                    _navCursor.value = tabs.lastOrNull() ?: LauncherTab.DEFAULT
                }

                ControllerCommand.NAVIGATE_RIGHT -> {
                    _couchSettingsFocused.value = false
                    _navCursor.value = tabs.firstOrNull() ?: LauncherTab.DEFAULT
                }

                ControllerCommand.CONFIRM -> emit(LauncherEffect.OpenSettings)
                ControllerCommand.NAVIGATE_DOWN,
                ControllerCommand.BACK,
                -> leaveNavBar()

                ControllerCommand.GO_HOME -> goHome()
                ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
                else -> Unit
            }
            return
        }
        when (command) {
            ControllerCommand.NAVIGATE_LEFT -> if (couchMode && focused == tabs.firstOrNull()) {
                _couchSettingsFocused.value = true
            } else {
                _navCursor.value = LauncherTab.step(focused, -1, enabledExtensionIds(), couchMode)
            }

            ControllerCommand.NAVIGATE_RIGHT -> if (couchMode && focused == tabs.lastOrNull()) {
                _couchSettingsFocused.value = true
            } else {
                _navCursor.value = LauncherTab.step(focused, 1, enabledExtensionIds(), couchMode)
            }

            // The handheld bar sits below content, while Couch Mode's bar sits
            // above it. Back leaves either orientation without making the user
            // remember which direction applies.
            ControllerCommand.NAVIGATE_UP -> if (!couchMode) leaveNavBar()

            ControllerCommand.BACK -> leaveNavBar()

            ControllerCommand.CONFIRM -> selectTab(focused)

            ControllerCommand.GO_HOME -> goHome()

            /*
             * Down continues the vertical cycle when the user has wrap on.
             *
             * The bar is the bottom of the panel, so it is the bottom of the
             * cycle: Down from here returns to the top row of the grid, exactly as
             * Down from the last row used to before the bar existed. Without this
             * the bar either broke wrap or — when entry was gated on wrap being
             * off, as it first was — became completely unreachable by controller
             * for anyone who had wrap switched on.
             *
             * With wrap off there is nothing below, and the press is swallowed: a
             * bar that holds the cursor and still scrolls the grid behind it is
             * worse than one that does nothing.
             */
            ControllerCommand.NAVIGATE_DOWN ->
                if (couchMode) {
                    leaveNavBar()
                } else if (controlSettings.value.wrapNavigation && _selectedTab.value.isHome) {
                    _navCursor.value = null
                    cursor.value = CursorPosition(0, cursor.value.column)
                }

            ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()

            else -> Unit
        }
    }

    /** Whether the grid cursor is on the last row, where Down leaves the grid. */
    private fun isOnBottomRow(): Boolean =
        _selectedTab.value.isHome && cursor.value.row >= uiState.value.spec.rows - 1

    private val effects = Channel<LauncherEffect>(Channel.BUFFERED)
    val effectFlow: Flow<LauncherEffect> = effects.receiveAsFlow()

    // ---- Sort picker -------------------------------------------------------

    private val _sortPicker = MutableStateFlow(SortPickerState())
    val sortPicker: StateFlow<SortPickerState> = _sortPicker.asStateFlow()

    fun openSortPicker() {
        viewModelScope.launchSafely(TAG) {
            val library = settingsRepository.library.first()
            _sortPicker.value = SortPickerState(
                visible = true,
                focusedIndex = 0,
                order = library.defaultSort.takeIf { it != SortOrder.MANUAL } ?: SortOrder.TITLE,
                descending = library.sortDescending,
            )
        }
    }

    fun closeSortPicker() {
        _sortPicker.update { it.copy(visible = false) }
    }

    fun toggleSortDirection() {
        _sortPicker.update { it.copy(descending = !it.descending) }
    }

    /**
     * Rewrites the grid in the chosen order.
     *
     * Sorting is applied to the layout rather than stored as a browsing
     * preference, because the grid is hand-arranged — a stored "sort order" that
     * changed nothing on screen is exactly the sort of setting that looks broken.
     */
    fun sortGrid(order: SortOrder) {
        viewModelScope.launchSafely(TAG) {
            val descending = _sortPicker.value.descending
            val state = uiState.value
            val spec = settingsRepository.grid.first()

            // Only entries currently on a page are reordered; dock slots and
            // folder contents keep their positions.
            val ordered = state.placements
                .mapNotNull { state.entriesById[it.entryId] }
                .sortedWith(libraryRepository.comparatorFor(order, descending))
                .map { it.id }

            gridRepository.applyOrder(ordered, spec)
            settingsRepository.updateLibrary {
                it.copy(defaultSort = order, sortDescending = descending)
            }
            _sortPicker.update { it.copy(visible = false, order = order) }
        }
    }

    // ---- Recording ---------------------------------------------------------

    /** What the recorder is doing, and the display it is rendering onto. */
    val recording: StateFlow<RecordingState> = screenRecorder.state

    /**
     * The video's shape, reported by the shell.
     *
     * The launcher's own panels decide it: a recording of a two-screen device should
     * be the size those two screens actually are, not a number picked here.
     */
    private var captureWidth = DEFAULT_CAPTURE_WIDTH
    private var captureHeight = DEFAULT_CAPTURE_HEIGHT
    private var captureDensity = DEFAULT_CAPTURE_DENSITY

    fun setCaptureGeometry(width: Int, height: Int, densityDpi: Int) {
        captureWidth = width
        captureHeight = height
        captureDensity = densityDpi
    }

    /**
     * Starts or ends a recording.
     *
     * Records the launcher rather than the screen — see [ScreenRecorder] for why that
     * is the only thing a two-screen launcher can record at all.
     */
    /**
     * Starts or ends a recording of the launcher's own two panels.
     *
     * Done here rather than handed to the service, and that is deliberate: this kind
     * of recording is fed by the launcher *composing*, and the launcher stops
     * composing the moment it is not on screen. It cannot outlive this view model,
     * so wrapping it in a service that can buys nothing and — on Android 14 and
     * later — costs something real, because a foreground service typed for media
     * projection is refused outright when there is no projection to justify it.
     *
     * Recording something the launcher did not draw is [startScreenRecording].
     */
    fun toggleRecording() {
        if (screenRecorder.isRecording) {
            val saved = screenRecorder.stop()
            emit(
                LauncherEffect.ShowMessage(
                    saved?.let { "Saved $it to Movies/Loki" } ?: "Nothing was recorded",
                ),
            )
            return
        }

        val started = screenRecorder.start(
            width = captureWidth,
            height = captureHeight,
            densityDpi = captureDensity,
        )
        if (started is RecordingState.Failed) {
            emit(LauncherEffect.ShowMessage(started.reason))
        }
    }

    /**
     * Asks for a recording of the real screen, which the shell has to arrange.
     *
     * A projection is granted by a system dialog to an activity result and then held
     * by a foreground service, and both of those live in the app module this one
     * cannot see. Stopping is the same toggle either way — the recorder does not
     * care which kind it is running.
     */
    fun startScreenRecording() {
        if (screenRecorder.isRecording) {
            toggleRecording()
            return
        }
        emit(LauncherEffect.StartScreenRecording)
    }

    /**
     * Ends a recording that outlived the launcher.
     *
     * [ScreenRecorder] is application-scoped — it has to be, because it owns a
     * `VirtualDisplay` and a hardware encoder that must not be torn down by a
     * configuration change — and nothing else ever stopped it. So a recording left
     * running when this view model went away kept the encoder open, kept writing, and
     * kept a `VirtualDisplay` alive that the launcher had stopped rendering onto. The
     * file was worse than the leak: `MediaStore` entries are created `IS_PENDING`, and
     * one whose writer dies without clearing that flag is invisible to galleries and
     * cannot be removed by the user.
     *
     * Stopping here publishes what was captured up to this point instead.
     */
    override fun onCleared() {
        super.onCleared()
        if (screenRecorder.isRecording) {
            ThorLog.w(TAG, "Launcher went away mid-recording; closing the file")
            screenRecorder.stop()
        }
    }

    // ---- Cold-start intro --------------------------------------------------

    /**
     * Whether the start-up sequence is still running.
     *
     * Scoped to the process, and that is the whole specification: it survives a
     * rotation or a display change, and dies when the process does. So the intro
     * plays when the device is turned on, when the launcher is force stopped and
     * reopened, and when Android kills it to make room for a game — and does not
     * play for a Home press against a launcher that is still running.
     *
     * It was briefly stored instead, which made it play exactly once per install.
     * That is a different thing and not the one wanted: the sequence is the
     * launcher starting up, so it belongs to a start-up rather than to a
     * first run.
     */
    private val _introVisible = MutableStateFlow(true)
    val introVisible: StateFlow<Boolean> = _introVisible.asStateFlow()

    fun finishIntro() {
        _introVisible.value = false
    }

    // ---- On-screen keyboard ------------------------------------------------

    private val _keyboard = MutableStateFlow(KeyboardState())
    val keyboard: StateFlow<KeyboardState> = _keyboard.asStateFlow()

    /**
     * Raises the keyboard over the grid panel, filling in [label] with [initial].
     *
     * The keyboard is an input method, not a search box: it types into whichever
     * field has claimed the launcher's text focus, on either panel. The shell keeps
     * this buffer and that field in step — see `ThorTextInputState`.
     */
    fun openKeyboard(label: String = "Text", initial: String = "") {
        // Surfaces that were on the way here would otherwise sit behind it, and the
        // app drawer in particular is a grid — leaving it open under a keyboard makes
        // it look as though the keyboard opened the drawer.
        sideMenuOpen.value = false
        contextMenuEntryId.value = null
        closeShortcutPanel()
        closeAppDrawer()

        _keyboard.value = KeyboardState(visible = true, label = label, text = initial)
    }

    fun closeKeyboard() {
        _keyboard.update { it.copy(visible = false) }
    }

    /**
     * Applies a key.
     *
     * The same entry point for a tap and for a button press, so the two can never
     * diverge. Editing is append-and-backspace rather than a full caret model: this
     * is a search box on a handheld, and a caret the user would have to drive with a
     * stick is more machinery than the job needs.
     */
    fun onKeyboardKey(key: KeyboardKey) {
        when (key) {
            is KeyboardKey.Character -> _keyboard.update { state ->
                state.copy(
                    text = state.text + key.resolve(state.shifted),
                    // Latched, not held: shift applies to one character and releases,
                    // which is the only behaviour that works when shift is a button
                    // press rather than something a finger can hold down.
                    shifted = false,
                )
            }

            KeyboardKey.Space -> _keyboard.update { it.copy(text = it.text + ' ') }

            KeyboardKey.Backspace -> _keyboard.update { state ->
                state.copy(text = state.text.dropLast(1))
            }

            KeyboardKey.Shift -> _keyboard.update { it.copy(shifted = !it.shifted) }

            KeyboardKey.Clipboard -> toggleClipboardSheet()

            KeyboardKey.Layer -> _keyboard.update { state ->
                val next = when (state.layer) {
                    KeyboardLayer.LETTERS -> KeyboardLayer.SYMBOLS
                    KeyboardLayer.SYMBOLS -> KeyboardLayer.LETTERS
                }
                // The cursor is re-clamped through the layout, because the arriving
                // layer's rows are not the same lengths as the one being left.
                val cursor = ThorKeyboardLayout.move(
                    layer = next,
                    row = state.cursorRow,
                    column = state.cursorColumn,
                    // A no-op direction: LEFT from column 0 stays put, and every
                    // other position is clamped on the way through.
                    direction = NavDirection.LEFT,
                )
                state.copy(layer = next, cursorRow = cursor.row, cursorColumn = cursor.column)
            }

            // Done and close are the same thing here: every edit has already been
            // applied to the field as it was typed, so there is nothing left to
            // commit — what differs is only where the controller goes next, which is
            // the shell's business rather than this one's.
            KeyboardKey.Enter, KeyboardKey.Cancel -> closeKeyboard()
        }
    }

    /**
     * Controller input while the keyboard is up.
     *
     * Back deletes when there is something to delete and closes when there is not,
     * so the button never traps the user on a surface they cannot leave and never
     * throws away a query in one press.
     */
    /**
     * Opens or closes the clipboard sheet.
     *
     * The clip is read on the way in rather than watched. Android only lets the
     * focused app read the clipboard, and THOR is focused exactly now — pressing
     * the key is the moment it is both allowed and worth doing.
     */
    fun toggleClipboardSheet() {
        val open = !_keyboard.value.clipboardOpen
        if (open) clipboard.refresh()
        _keyboard.update { state ->
            state.copy(
                clipboardOpen = open,
                clips = if (open) clipboard.history.value else emptyList(),
                clipIndex = 0,
            )
        }
    }

    /** Inserts a clip at the caret and closes the sheet. */
    fun pasteClip(text: String) {
        _keyboard.update { state ->
            state.copy(
                text = state.text + text,
                clipboardOpen = false,
                clips = emptyList(),
            )
        }
    }

    /** Copies what is in the field onto the system clipboard. */
    fun copyFieldText() {
        val text = _keyboard.value.text
        if (text.isBlank()) return
        clipboard.copy(text)
        _keyboard.update { it.copy(clipboardOpen = false, clips = emptyList()) }
        emit(LauncherEffect.ShowMessage("Copied"))
    }

    /**
     * Controller input while the clipboard sheet is up.
     *
     * A list, so up and down move and Confirm pastes. Handled before the keys,
     * because while the sheet is open the keyboard underneath must not also be
     * typing what the user is scrolling past.
     */
    private fun onClipboardCommand(command: ControllerCommand) {
        val state = _keyboard.value
        // One past the clips is the "copy this field" row, which is why the sheet
        // is navigable even with nothing on the clipboard.
        val lastIndex = state.clips.size
        when (command) {
            ControllerCommand.NAVIGATE_UP -> _keyboard.update {
                it.copy(clipIndex = (it.clipIndex - 1).coerceAtLeast(0))
            }

            ControllerCommand.NAVIGATE_DOWN -> _keyboard.update {
                it.copy(clipIndex = (it.clipIndex + 1).coerceAtMost(lastIndex))
            }

            ControllerCommand.CONFIRM -> {
                val clip = state.clips.getOrNull(state.clipIndex)
                if (clip != null) pasteClip(clip) else copyFieldText()
            }

            ControllerCommand.BACK -> _keyboard.update {
                it.copy(clipboardOpen = false, clips = emptyList())
            }

            else -> Unit
        }
    }

    private fun onKeyboardCommand(command: ControllerCommand) {
        val state = _keyboard.value
        if (state.clipboardOpen) {
            onClipboardCommand(command)
            return
        }
        when (command) {
            ControllerCommand.NAVIGATE_UP -> moveKeyboardCursor(NavDirection.UP)
            ControllerCommand.NAVIGATE_DOWN -> moveKeyboardCursor(NavDirection.DOWN)
            ControllerCommand.NAVIGATE_LEFT -> moveKeyboardCursor(NavDirection.LEFT)
            ControllerCommand.NAVIGATE_RIGHT -> moveKeyboardCursor(NavDirection.RIGHT)

            ControllerCommand.CONFIRM -> ThorKeyboardLayout
                .keyAt(state.layer, state.cursorRow, state.cursorColumn)
                ?.let(::onKeyboardKey)

            ControllerCommand.BACK -> if (state.text.isEmpty()) {
                onKeyboardKey(KeyboardKey.Cancel)
            } else {
                onKeyboardKey(KeyboardKey.Backspace)
            }

            // The face buttons double as the keys a typist reaches for most.
            ControllerCommand.TOGGLE_FAVORITE -> onKeyboardKey(KeyboardKey.Space)
            ControllerCommand.CONTEXT_MENU -> onKeyboardKey(KeyboardKey.Shift)

            ControllerCommand.PAGE_PREVIOUS,
            ControllerCommand.PAGE_NEXT,
            -> onKeyboardKey(KeyboardKey.Layer)

            ControllerCommand.OPEN_SIDE_MENU -> onKeyboardKey(KeyboardKey.Enter)

            ControllerCommand.GO_HOME -> {
                closeKeyboard()
                goHome()
            }

            else -> Unit
        }
    }

    private fun moveKeyboardCursor(direction: NavDirection) {
        _keyboard.update { state ->
            val cursor = ThorKeyboardLayout.move(
                layer = state.layer,
                row = state.cursorRow,
                column = state.cursorColumn,
                direction = direction,
            )
            state.copy(cursorRow = cursor.row, cursorColumn = cursor.column)
        }
    }

    // ---- Shortcut panel ----------------------------------------------------

    private val _shortcutPanel = MutableStateFlow(ShortcutPanelState())
    val shortcutPanel: StateFlow<ShortcutPanelState> = _shortcutPanel.asStateFlow()

    fun toggleShortcutPanel() {
        if (_shortcutPanel.value.visible) {
            closeShortcutPanel()
            return
        }
        // Raised over the grid, so the transient menus that would otherwise be
        // stacked underneath it are dismissed rather than left to reappear.
        sideMenuOpen.value = false
        contextMenuEntryId.value = null
        _shortcutPanel.value = ShortcutPanelState(
            visible = true,
            actions = ShortcutGrid.ACTIONS,
        )
    }

    fun closeShortcutPanel() {
        _shortcutPanel.update { it.copy(visible = false) }
    }

    // ---- The journal: what the user recorded, not what was scraped -----------

    /** Everything written and captured about one entry. */
    fun journalFor(entryId: String): Flow<GameJournal> = journalRepository.observe(entryId)

    /** Whether a screenshot can be taken at all; false without the pointer service. */
    val canScreenshot: StateFlow<Boolean> = screenshots.available

    /** The note editor, and what it is editing. */
    private val _noteDialog = MutableStateFlow(NoteDialogState())
    val noteDialog: StateFlow<NoteDialogState> = _noteDialog.asStateFlow()

    /**
     * Raises the note editor over whatever asked for it.
     *
     * The existing note is fetched before the dialog opens rather than observed
     * inside it, so the field is populated on its first frame. Opening empty and
     * filling in a moment later would race the user's first keystroke and could
     * silently discard it.
     */
    fun openNoteEditor(entry: GridEntry) {
        closeContextMenu()
        viewModelScope.launchSafely(TAG) {
            val existing = journalRepository.noteFor(entry.id)
            _noteDialog.value = NoteDialogState(
                entryId = entry.id,
                title = entry.title,
                body = existing?.body.orEmpty(),
            )
        }
    }

    fun dismissNoteDialog() { _noteDialog.value = NoteDialogState() }

    /** Saves what was typed and closes; blank deletes, see the repository. */
    fun saveNote(body: String) {
        val entryId = _noteDialog.value.entryId ?: return
        _noteDialog.value = NoteDialogState()
        setNote(entryId, body)
    }

    fun setNote(entryId: String, body: String) {
        viewModelScope.launchSafely(TAG) {
            journalRepository.setNote(entryId, body, System.currentTimeMillis())
        }
    }

    /**
     * Takes a frame and files it against whatever it is a picture of.
     *
     * The running game first, because that is what is on the screen being
     * photographed — filing it against whatever the cursor happens to rest on
     * would attribute a screenshot of one game to another. The selection is the
     * fallback for a capture taken from the launcher itself, where the cursor is
     * the only thing that says what the user means.
     *
     * Nothing at all to attribute it to means no capture rather than an orphan
     * file: a screenshot whose game is unknown cannot be found again on any
     * surface this feature has, and would be a file the user never sees.
     */
    fun captureScreenshot() {
        val target = _runningEntryId.value ?: uiState.value.selection?.id
        if (target == null) {
            emit(LauncherEffect.ShowMessage("Nothing to attach a screenshot to"))
            return
        }

        viewModelScope.launchSafely(TAG) {
            val png = screenshots.capture(CAPTURED_DISPLAY_ID)
            if (png == null) {
                // Two causes and the user can act on both: the service is off, or
                // the system refused the frame. Said plainly rather than silently.
                val reason = if (canScreenshot.value) {
                    "That screen cannot be captured"
                } else {
                    "Turn on Loki's pointer service to take screenshots"
                }
                emit(LauncherEffect.ShowMessage(reason))
                return@launchSafely
            }

            val saved = journalRepository.addScreenshot(target, png, System.currentTimeMillis())
            val title = uiState.value.entriesById[target]?.title
            emit(
                LauncherEffect.ShowMessage(
                    if (saved != null) {
                        "Screenshot saved to ${title ?: "this game"}"
                    } else {
                        "Could not save the screenshot"
                    },
                ),
            )
        }
    }

    /** Runs a tile. The panel always closes first, so nothing opens behind it. */
    fun onShortcut(action: ShortcutAction) {
        closeShortcutPanel()
        when (action) {
            ShortcutAction.APPS -> openAppDrawer()
            ShortcutAction.SEARCH -> emit(LauncherEffect.OpenSearch)
            ShortcutAction.THOR_SETTINGS -> emit(LauncherEffect.OpenSettings)
            ShortcutAction.SCAN_LIBRARY -> scanLibrary()
            ShortcutAction.RECORD -> toggleRecording()
            ShortcutAction.RECORD_SCREEN -> startScreenRecording()
            ShortcutAction.SCREENSHOT -> captureScreenshot()

            ShortcutAction.COUCH_MODE -> viewModelScope.launchSafely(TAG) {
                settingsRepository.updateDisplay { display ->
                    display.copy(
                        mode = if (display.mode == DualScreenMode.COUCH) {
                            DualScreenMode.AUTO
                        } else {
                            DualScreenMode.COUCH
                        },
                    )
                }
            }

            ShortcutAction.SWAP_SCREENS -> viewModelScope.launchSafely(TAG) {
                settingsRepository.updateDisplay { it.copy(swapScreens = !it.swapScreens) }
            }

            ShortcutAction.WIFI -> openSystemPanel(SystemPanel.WIFI)
            ShortcutAction.BLUETOOTH -> openSystemPanel(SystemPanel.BLUETOOTH)
            ShortcutAction.VOLUME -> openSystemPanel(SystemPanel.VOLUME)
            ShortcutAction.SYSTEM_SETTINGS -> openSystemPanel(SystemPanel.ALL_SETTINGS)
        }
    }

    /**
     * The system's downloads list.
     *
     * Public because couch mode's dashboard offers it directly rather than
     * through a shortcut tile: adding a thirteenth [ShortcutAction] would put a
     * lone tile on a fourth row of a panel that currently fills three.
     */
    fun openDownloads() = openSystemPanel(SystemPanel.DOWNLOADS)

    private fun openSystemPanel(panel: SystemPanel) {
        val result = entryLauncher.openSystemPanel(panel)
        if (result is LaunchResult.Failed) {
            emit(LauncherEffect.LaunchFailed(describe(result.reason)))
        }
    }

    /**
     * Controller input while the panel is up.
     *
     * The panel is a strip of tiles rather than a list, so vertical movement is a
     * whole row at a time — see [ShortcutGrid.move].
     */
    private fun onShortcutPanelCommand(command: ControllerCommand) {
        val state = _shortcutPanel.value
        when (command) {
            ControllerCommand.NAVIGATE_LEFT -> moveShortcutFocus(-1)
            ControllerCommand.NAVIGATE_RIGHT -> moveShortcutFocus(1)
            ControllerCommand.NAVIGATE_UP -> moveShortcutFocus(-ShortcutGrid.COLUMNS)
            ControllerCommand.NAVIGATE_DOWN -> moveShortcutFocus(ShortcutGrid.COLUMNS)

            ControllerCommand.CONFIRM ->
                state.actions.getOrNull(state.focusedIndex)?.let(::onShortcut)

            ControllerCommand.BACK -> closeShortcutPanel()

            // Home is not a navigation step inside a panel; it means "put the
            // launcher back to its start", which includes dropping this.
            ControllerCommand.GO_HOME -> {
                closeShortcutPanel()
                goHome()
            }

            else -> Unit
        }
    }

    private fun moveShortcutFocus(delta: Int) {
        _shortcutPanel.update { state ->
            state.copy(
                focusedIndex = ShortcutGrid.move(
                    index = state.focusedIndex,
                    delta = delta,
                    count = state.actions.size,
                ),
            )
        }
    }

    // ---- App drawer --------------------------------------------------------

    private val _appDrawer = MutableStateFlow(AppDrawerState())
    val appDrawer: StateFlow<AppDrawerState> = _appDrawer.asStateFlow()

    init {
        // The drawer lists every installed application, alphabetically, so it is
        // fed straight from the library rather than from grid placements.
        libraryRepository.apps
            .onEach { apps ->
                _appDrawer.update { it.copy(apps = apps.sortedBy { app -> app.sortTitle }) }
            }
            .launchIn(viewModelScope)

        observeWithdrawnSections()
    }

    fun openAppDrawer() {
        /*
         * Couch mode has no drawer.
         *
         * It is the dual-screen one: a paged grid of small icons, sized for a
         * panel held in two hands, drawn over a television at whatever size the
         * handheld's grid happens to be set to. The shelf already carries an Apps
         * rail with the same list on it — every installed application, the system
         * ones included — at a size chosen for the room, so from a sofa that is
         * where apps are, and every route that used to open the drawer goes
         * there instead.
         */
        if (couchMode.value) {
            focusCouchRail(COUCH_RAIL_APPS)
            return
        }
        _appDrawer.update { it.copy(visible = true, page = 0, cursor = CursorPosition(0, 0)) }
    }

    /** Puts the couch cursor on a named shelf, if the library built one. */
    private fun focusCouchRail(id: String): Boolean {
        val rails = couchRails(uiState.value)
        val index = rails.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return false
        setCouchFocus(rails, index, couchItemByRailId[rails[index].id] ?: 0)
        return true
    }

    fun closeAppDrawer() {
        _appDrawer.update { it.copy(visible = false) }
    }

    fun setDrawerCursor(row: Int, column: Int) {
        _appDrawer.update { it.copy(cursor = CursorPosition(row, column)) }
    }

    fun setDrawerPage(page: Int) {
        _appDrawer.update { it.copy(page = page.coerceAtLeast(0)) }
    }

    /** Launches whatever the drawer's cursor is on. */
    fun confirmDrawerSelection() {
        val drawer = _appDrawer.value
        val spec = uiState.value.spec
        val index = drawer.page * spec.cellsPerPage + drawer.cursor.cellIndex(spec.columns)
        drawer.apps.getOrNull(index)?.let { app ->
            closeAppDrawer()
            launchEntry(app)
        }
    }

    /**
     * The combined state.
     *
     * `WhileSubscribed` with a stop timeout keeps the flow alive across the
     * brief unsubscribe that happens when the activity is recreated, so a
     * rotation or a display change does not re-run the whole library query.
     */
    /** Confined to the transform below, which the flow runs on one thread. */
    private val layoutMemo = LayoutMemo()

    val uiState: StateFlow<LauncherUiState> = combine(
        combine(
            gridRepository.pages,
            gridRepository.placements,
            libraryRepository.entriesById,
            gridRepository.dockPlacements,
            libraryRepository.platforms,
        ) { pages, placements, entries, dock, platforms ->
            LayoutSnapshot(pages, placements, entries, dock, platforms)
        },
        combine(
            settingsRepository.grid,
            settingsRepository.performance,
            settingsRepository.controls,
            settingsRepository.personalization,
            settingsRepository.settings.map { it.enabledExtensions }.distinctUntilChanged(),
        ) { grid, performance, controls, personalization, extensions ->
            GridConfig(
                spec = grid,
                // One page either side is enough to make a swipe look instant
                // without composing pages nobody is heading toward; performance
                // mode drops it to zero to save the work entirely.
                pagePrefetchRadius = if (performance.performanceMode) 0 else 1,
                touchEnabled = controls.touchEnabled,
                folderStyle = personalization.folderStyle,
                enabledExtensions = extensions,
            )
        },
        combine(cursor, currentPage, editMode, openFolderId, sideMenuOpen) { c, page, edit, folder, menu ->
            InteractionSnapshot(c, page, edit, folder, menu)
        },
        combine(
            contextMenuEntryId,
            contextMenuIndex,
            editingEntryId,
            sideMenuIndex,
            hasSecondScreen,
        ) { contextId, index, editId, menuIndex, secondScreen ->
            OverlaySnapshot(contextId, index, editId, menuIndex, secondScreen)
        },
        syncManager.state,
    ) { layout, gridConfig, interaction, overlays, sync ->
        val spec = gridConfig.spec
        val derived = layoutMemo.of(layout, interaction.openFolderId)
        val selection =
            resolveSelection(
                layout,
                spec,
                interaction,
                derived.openFolderContents,
                derived.widgetSpans,
            )
        LauncherUiState(
            enabledExtensions = gridConfig.enabledExtensions,
            pages = layout.pages,
            placements = layout.placements,
            entriesById = layout.entries,
            dockEntryIds = derived.dockEntryIds,
            platformsById = derived.platformsById,
            spec = spec,
            currentPage = interaction.page,
            cursor = interaction.cursor,
            selection = selection,
            editMode = interaction.editMode,
            openFolderId = interaction.openFolderId,
            openFolderContents = derived.openFolderContents,
            isLoading = false,
            isScanning = sync is SyncState.Scanning,
            scanLabel = (sync as? SyncState.Scanning)?.label,
            sideMenuOpen = interaction.sideMenuOpen,
            sideMenuIndex = overlays.sideMenuIndex,
            contextMenuEntry = overlays.contextEntryId?.let(layout.entries::get),
            contextMenuIndex = overlays.contextIndex,
            editingEntry = overlays.editingEntryId?.let(layout.entries::get),
            hasSecondScreen = overlays.hasSecondScreen,
            pagePrefetchRadius = gridConfig.pagePrefetchRadius,
            touchEnabled = gridConfig.touchEnabled,
            folderStyle = gridConfig.folderStyle,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LauncherUiState(),
    )

    /**
     * Whether to explain edit mode, which is only ever true once.
     *
     * Its own flow rather than a field on [LauncherUiState]: the two combines
     * that build that state are both at the five-flow limit, and widening one of
     * them to carry a boolean that is false for the rest of the install's life
     * would be paid for on every cursor move.
     */
    val editModeTutorial: StateFlow<Boolean> =
        combine(editMode, settingsRepository.settings) { mode, settings ->
            mode.isActive && !settings.editModeTutorialSeen
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false,
            )

    /** Records that the edit-mode gestures have been explained. */
    fun dismissEditModeTutorial() {
        viewModelScope.launchSafely(TAG) { settingsRepository.setEditModeTutorialSeen(true) }
    }

    /**
     * Ids whose achievements have been fetched this run.
     *
     * A set rather than a timestamp per game: the point is one request per game
     * per session, and progress only changes when the game is played — which
     * ends this process on a handheld anyway, because the launcher does not
     * survive an emulator taking the foreground.
     */
    private val refreshedAchievements = mutableSetOf<String>()

    /**
     * Fetches a game's achievements when the cursor settles on it.
     *
     * Debounced rather than fired per selection: crossing a page of games at
     * auto-repeat is a dozen selections in a second, and each one that reached
     * the network would be a request for a card the user never stopped on.
     *
     * The bulk sync stores counts for the whole library; this fills in the
     * achievements themselves, which are a request per game and so are fetched
     * for the one being looked at. A game with no stored match does nothing —
     * the repository returns without a request.
     */
    private fun observeAchievementRefreshes() {
        uiState
            .map { (it.selection as? GameEntry)?.id }
            .distinctUntilChanged()
            .debounce(ACHIEVEMENT_REFRESH_DELAY_MS)
            .onEach { entryId ->
                if (entryId == null || !refreshedAchievements.add(entryId)) return@onEach
                runCatching { achievementRepository.refresh(entryId) }
                    .onFailure { ThorLog.w(TAG, "Could not refresh achievements", it) }
            }
            .launchIn(viewModelScope)
    }

    /*
     * Started here rather than in the init block near the top, and the position
     * is the whole of it.
     *
     * Kotlin runs property initialisers and init blocks in declaration order, so
     * an init block above [uiState] runs while that property is still null — and
     * this reads it synchronously to build its flow. The result was a null
     * dereference inside the view model's own constructor, which on a launcher
     * means the home screen cannot be created at all: it crashed on start, every
     * time, before drawing anything.
     *
     * Anything else that observes [uiState] at construction belongs below it for
     * the same reason.
     */
    init {
        /*
         * The overlay's Screenshot tile, wired to the same code the launcher's own
         * tile runs.
         *
         * The service can produce a PNG but knows neither which entry it belongs to
         * nor how to write into the active profile, so it asks for the whole
         * operation rather than for a frame. One path means one set of rules about
         * attribution, and a shot taken from over a game lands exactly where one
         * taken from the panel does.
         */
        screenshots.onCaptureRequested { captureScreenshot() }

        /*
         * The recorder is told what sound to capture, rather than reading it.
         *
         * [ScreenRecorder] is deliberately free of settings — it is handed a size,
         * a density and a surface and nothing else — so the choice is pushed into
         * it from here. It only takes effect at the next `start`, because
         * `MediaRecorder` is configured before `prepare()` and cannot be changed
         * after; a setting changed mid-recording waits for the next one.
         */
        settingsRepository.recording
            .onEach { screenRecorder.audio = it.audio }
            .launchIn(viewModelScope)

        observeAchievementRefreshes()
    }

    /** Screenshot index for [LauncherUiState.selection], 0 for any other entry. */
    val screenshotIndex: StateFlow<Int> = combine(
        uiState,
        screenshotCursor,
    ) { state, raw ->
        if (raw.entryId == state.selection?.id) raw.index else 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = 0,
    )

    /**
     * The game whose trailer the user has dismissed with the bumpers.
     *
     * Keyed by entry rather than a bare flag, so moving to another game brings its
     * trailer back — dismissing one is a decision about that game, not a mode.
     * Cleared implicitly by the id no longer matching, which is the same trick
     * [screenshotCursor] uses and for the same reason: no reset call to thread
     * through cursor movement.
     */
    private val _trailerDismissedFor = MutableStateFlow<String?>(null)
    val trailerDismissedFor: StateFlow<String?> = _trailerDismissedFor.asStateFlow()

    /**
     * Restores autoplay after the cursor leaves a game whose trailer was dismissed.
     *
     * The dismissal is deliberately local to one visit. Keeping its id after the
     * cursor moved away meant returning to that same game showed screenshots forever
     * even though autoplay was enabled and a trailer had been scraped successfully.
     */
    fun restoreTrailerForNewSelection(entryId: String?) {
        if (_trailerDismissedFor.value != null && _trailerDismissedFor.value != entryId) {
            _trailerDismissedFor.value = null
        }
    }

    /**
     * Steps the highlighted game's screenshot forward or back, wrapping around.
     *
     * The first press while a trailer is playing puts the stills up instead of
     * moving through them. That is what makes the bumpers a way *out* of the
     * trailer rather than a control the trailer hides — pressing one and watching
     * an invisible index advance behind a video would read as the button doing
     * nothing.
     */
    fun cycleScreenshot(delta: Int) {
        val game = uiState.value.selection as? GameEntry ?: return

        if (_trailerDismissedFor.value != game.id &&
            !game.metadata.artwork.videoUri.isNullOrBlank()
        ) {
            _trailerDismissedFor.value = game.id
            return
        }

        val count = game.metadata.artwork.cappedScreenshots.size
        if (count <= 1) return
        val current = screenshotIndex.value
        screenshotCursor.value = ScreenshotCursor(game.id, (current + delta).mod(count))
    }

    /** Jumps straight to a screenshot, e.g. from a tap on the strip. */
    fun setScreenshot(index: Int) {
        val game = uiState.value.selection as? GameEntry ?: return
        val count = game.metadata.artwork.cappedScreenshots.size
        if (index !in 0 until count) return
        screenshotCursor.value = ScreenshotCursor(game.id, index)
    }

    private data class ScreenshotCursor(val entryId: String? = null, val index: Int = 0)

    private data class LayoutSnapshot(
        val pages: List<com.thor.core.model.GridPage>,
        val placements: List<com.thor.core.model.GridPlacement>,
        val entries: Map<String, GridEntry>,
        val dock: List<com.thor.core.model.GridPlacement>,
        val platforms: List<com.thor.core.model.Platform>,
    )

    /** Settings the grid reads, gathered so the transform takes one argument. */
    private data class GridConfig(
        val spec: GridSpec,
        val pagePrefetchRadius: Int,
        val touchEnabled: Boolean,
        val folderStyle: com.thor.core.model.FolderStyle,
        /** Optional sections enabled, so the bar knows what to draw. */
        val enabledExtensions: Set<String>,
    )

    private data class InteractionSnapshot(
        val cursor: CursorPosition,
        val page: Int,
        val editMode: EditMode,
        val openFolderId: String?,
        val sideMenuOpen: Boolean,
    )

    private data class OverlaySnapshot(
        val contextEntryId: String?,
        val contextIndex: Int,
        val editingEntryId: String?,
        val sideMenuIndex: Int,
        val hasSecondScreen: Boolean,
    )

    init {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.ensurePlatformsSeeded()
        }

        // Settled at startup as well as on regaining focus: if the launcher was
        // killed while a game ran — which is the normal case on a handheld — this
        // is the first chance to credit that session.
        settlePlaytime()

        // Without this the launcher opens to an empty grid and an empty app
        // drawer on a fresh install, because nothing else triggers a scan.
        syncManager.requestAppRefresh()

        // Shrinking the grid strands placements outside the new bounds, and
        // leaves the cursor pointing at a cell that no longer exists.
        viewModelScope.launchSafely(TAG) {
            settingsRepository.grid
                .distinctUntilChangedBy { it.columns to it.rows }
                .collect { spec ->
                    gridRepository.reflowIfNeeded(spec)
                    cursor.update { current ->
                        CursorPosition(
                            row = current.row.coerceIn(0, spec.rows - 1),
                            column = current.column.coerceIn(0, spec.columns - 1),
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------- input

    /** Routes a controller command. */
    fun onCommand(
        command: ControllerCommand,
        accelerated: Boolean,
        couchMode: Boolean = false,
    ) {
        // Overlays are modal and claim input in the order they stack, so the
        // grid never moves underneath an open panel. Without this the cursor
        // drifts while a menu is up and the selection has silently changed by
        // the time it closes.
        /*
         * The keyboard is the innermost surface there is, so it is tested first.
         *
         * It used to be tested *after* the entry editor, which meant the editor —
         * whose fields raise this very keyboard — swallowed every key before the
         * keyboard could see one. Typing into the editor was dead on arrival.
         */
        if (_keyboard.value.visible) {
            onKeyboardCommand(command)
            return
        }

        if (editingEntryId.value != null) {
            // The editor is a form of fields; Back closes it, and its fields raise
            // the keyboard, which is handled above.
            if (command == ControllerCommand.BACK) closeEditor()
            return
        }

        // Swallowed while the intro runs, and no longer ends it: the sequence
        // plays to its own end now. Still swallowed, because a live grid sits
        // underneath and a press falling through would launch whatever the
        // cursor happens to be on.
        if (_introVisible.value) return

        /*
         * The shortcut button is global, not a binding some other surface can
         * swallow. Handled before the panel-routing below so it toggles from
         * wherever the user is — a button whose whole purpose is "get me options
         * without leaving what I am doing" cannot be conditional on nothing else
         * being open.
         */
        if (command == ControllerCommand.OPEN_SHORTCUTS) {
            toggleShortcutPanel()
            return
        }
        // Outermost surface: raised over everything else, so it takes input first.
        if (_shortcutPanel.value.visible) {
            onShortcutPanelCommand(command)
            return
        }

        // Order is innermost-surface-first. The context menu must be tested
        // before the drawer, not after: a menu raised *over* the drawer is the
        // thing the user is looking at, and checking the drawer first meant the
        // drawer swallowed the presses and the menu could not be navigated.
        // The folder picker is raised *from* the context menu, so it is tested
        // first — the menu closes as the picker opens, but ordering it after the
        // menu would still be wrong the moment both are ever open together.
        if (_folderPicker.value.visible) {
            onFolderPickerCommand(command)
            return
        }
        /*
         * The picker is raised *from* the cell menu, so it is tested first — the
         * menu closes as the picker opens, but ordering it the other way round
         * would still be wrong the moment both are ever open together.
         */
        if (_matchPicker.value.visible) {
            onMatchPickerCommand(command)
            return
        }
        if (_widgetPicker.value.visible) {
            onWidgetPickerCommand(command)
            return
        }
        if (_cellMenu.value.visible) {
            onCellMenuCommand(command)
            return
        }
        if (contextMenuEntryId.value != null) {
            onContextMenuCommand(command)
            return
        }
        if (_sortPicker.value.visible) {
            onSortPickerCommand(command)
            return
        }
        if (_appDrawer.value.visible) {
            onAppDrawerCommand(command)
            return
        }
        if (sideMenuOpen.value) {
            onSideMenuCommand(command, couchMode)
            return
        }
        if (couchMode && _couchQuickDetailsEntryId.value != null) {
            onCouchQuickDetailsCommand(command)
            return
        }

        // In Couch Mode the bumpers always step the shared top-level destinations,
        // regardless of which section currently owns the content cursor.
        if (couchMode) {
            when (command) {
                ControllerCommand.CYCLE_IMAGE_PREVIOUS -> {
                    cycleCouchDestination(-1)
                    return
                }

                ControllerCommand.CYCLE_IMAGE_NEXT -> {
                    cycleCouchDestination(1)
                    return
                }

                else -> Unit
            }
        }

        /*
         * The nav bar, last of the surfaces that can hold the cursor.
         *
         * Below every overlay on purpose: an overlay is raised *over* the bar and
         * has to take input from it, exactly as it does from the grid. Above the
         * grid's own handling, because while the cursor is on the bar the grid is
         * not what the buttons are for.
         */
        if (isNavBarFocused) {
            onNavBarCommand(command, couchMode)
            return
        }

        // Sections other than Home have no grid to drive, so everything below
        // this point would act on a surface the user cannot see.
        if (!_selectedTab.value.isHome) {
            when (command) {
                ControllerCommand.NAVIGATE_UP, ControllerCommand.BACK -> enterNavBar()
                ControllerCommand.GO_HOME -> goHome()
                ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
                else -> Unit
            }
            return
        }

        if (couchMode) {
            onCouchHomeCommand(command)
            return
        }

        /*
         * The companion panel owns this screen while a game is running.
         *
         * Ahead of the card flow and the grid, matching the draw order — and
         * without this the D-pad drove the grid *underneath* an panel that was
         * covering it, which is the other half of "the companion panel does not
         * work": it was on screen and none of the buttons reached it.
         */
        if (_runningEntryId.value != null && !uiState.value.isFolderOpen) {
            onCompanionCommand(command)
            return
        }

        /*
         * The card flow replaces the top level of Home and nothing under it.
         *
         * Gated on no folder being open, which is what makes opening a system
         * hand straight back to the grid: the folder's contents are laid out and
         * driven exactly as they always were, and Back closes the folder and
         * returns here. Without the guard the flow would keep the D-pad while the
         * user was looking at a page of games.
         */
        if (homeLayout.value == HomeLayout.PLATFORM_CARDS && !uiState.value.isFolderOpen) {
            onPlatformCardCommand(command)
            return
        }

        /*
         * Resizing takes the D-pad from the cursor.
         *
         * Tested here rather than as a case in the grid's own `when` because it
         * changes what four of its entries mean: while a widget is being sized,
         * Left is "one cell narrower" and not "move the cursor left", and there
         * is no cursor movement available at all until it is finished.
         */
        if (editMode.value is EditMode.Resizing) {
            onResizeCommand(command)
            return
        }

        when (command) {
            ControllerCommand.NAVIGATE_UP -> move(NavDirection.UP)
            ControllerCommand.NAVIGATE_DOWN -> moveDownOrEnterNavBar()
            ControllerCommand.NAVIGATE_LEFT -> move(NavDirection.LEFT)
            ControllerCommand.NAVIGATE_RIGHT -> move(NavDirection.RIGHT)
            ControllerCommand.CONFIRM -> confirm()
            ControllerCommand.BACK -> back()
            ControllerCommand.CONTEXT_MENU -> openContextMenu()
            ControllerCommand.TOGGLE_FAVORITE -> toggleFavorite()
            ControllerCommand.PAGE_PREVIOUS -> changePage(-1)
            ControllerCommand.PAGE_NEXT -> changePage(1)
            ControllerCommand.CYCLE_IMAGE_PREVIOUS -> cycleScreenshot(-1)
            ControllerCommand.CYCLE_IMAGE_NEXT -> cycleScreenshot(1)
            ControllerCommand.OPEN_SIDE_MENU -> toggleSideMenu()
            // The grid-style drawer on this panel, which is the one the Start
            // panel's Apps row opens. It used to raise the info panel's search
            // overlay instead — a surface with its own input handling that only
            // runs while that panel holds input, so pressing Select opened
            // something on the other screen that the pad could not then dismiss.
            ControllerCommand.OPEN_APP_DRAWER -> openAppDrawer()
            // Already handled above, where it applies from every surface.
            ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
            ControllerCommand.GO_HOME -> goHome()
            ControllerCommand.PICK_UP -> pickUp()
            ControllerCommand.CANCEL_EDIT -> cancelEdit()
            ControllerCommand.SEARCH -> emit(LauncherEffect.OpenSearch)
        }
    }

    /**
     * The systems the card flow is showing, as of this press.
     *
     * Computed on demand rather than held as a flow, because the inputs it folds
     * — every game and every platform — change on a scrape, a launch or a hide,
     * while the *cursor* changes on every press. Derived as a flow off
     * [uiState] it would be rebuilt each time the cursor moved a cell, which is
     * the hot path; folded once per press it is work proportional to the library
     * exactly when the library is the thing being asked about.
     */
    private fun currentPlatformCards(): List<PlatformCard> {
        val state = uiState.value
        return platformCards(
            games = state.entriesById.values.filterIsInstance<GameEntry>(),
            platformsById = state.platformsById,
        )
    }

    /**
     * Home, as a flow of systems; see [com.thor.core.model.HomeLayout].
     *
     * Left and Right step, and so do the shoulder buttons — the same two things
     * turn the page on the grid, and a user who has learned one should not have
     * to learn the other. Down reaches the section bar, as it does everywhere
     * else on this panel.
     */
    private fun onPlatformCardCommand(command: ControllerCommand) {
        val cards = currentPlatformCards()

        when (command) {
            ControllerCommand.NAVIGATE_LEFT, ControllerCommand.PAGE_PREVIOUS ->
                stepPlatformCard(-1, cards.size)

            ControllerCommand.NAVIGATE_RIGHT, ControllerCommand.PAGE_NEXT ->
                stepPlatformCard(1, cards.size)

            // The bar is below this panel, so Down is what reaches it. Back does
            // too, because there is nothing else for Back to undo here — the flow
            // is the top level, and a button that does nothing reads as a freeze.
            ControllerCommand.NAVIGATE_DOWN, ControllerCommand.BACK -> enterNavBarIfShown()

            ControllerCommand.CONFIRM -> openPlatformCard(cards)
            ControllerCommand.OPEN_SIDE_MENU -> toggleSideMenu()
            ControllerCommand.OPEN_APP_DRAWER -> openAppDrawer()
            ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
            ControllerCommand.GO_HOME -> goHome()
            ControllerCommand.SEARCH -> emit(LauncherEffect.OpenSearch)

            /*
             * Everything else belongs to a cell, and there are no cells here.
             *
             * Favourite, the context menu, edit mode and screenshot cycling all
             * act on one entry; a card is a whole system. Silently ignored rather
             * than mapped onto something approximate — a Favourite button that
             * quietly favourited a system's most recent game would be worse than
             * one that does nothing.
             */
            else -> Unit
        }
    }

    /**
     * The panel beside a running game.
     *
     * Left and Right walk the tiles, A presses, and Home or Back puts the grid
     * back — the same gesture that takes a panel back, because they are the same
     * decision: the launcher's only signal that a game is finished with is the
     * user saying so.
     */
    private fun onCompanionCommand(command: ControllerCommand) {
        val count = COMPANION_ACTIONS.size
        when (command) {
            ControllerCommand.NAVIGATE_LEFT ->
                _companionAction.value = (_companionAction.value - 1).coerceAtLeast(0)

            ControllerCommand.NAVIGATE_RIGHT ->
                _companionAction.value = (_companionAction.value + 1).coerceAtMost(count - 1)

            ControllerCommand.CONFIRM -> when (COMPANION_ACTIONS[_companionAction.value]) {
                CompanionAction.SCREENSHOT -> captureScreenshot()
                // The screen recording rather than the launcher one: the point of
                // this tile is the game on the other panel, which is exactly what
                // the launcher-only recording cannot see.
                CompanionAction.RECORD -> startScreenRecording()
                CompanionAction.HOME -> goHome()
            }

            // Both, because either reads as "I have finished with that game".
            ControllerCommand.BACK, ControllerCommand.GO_HOME -> goHome()

            ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
            ControllerCommand.SEARCH -> emit(LauncherEffect.OpenSearch)
            ControllerCommand.OPEN_SIDE_MENU -> toggleSideMenu()

            /*
             * Everything else acts on a cell, and there is no cell here.
             *
             * Ignored rather than passed through to the grid underneath, which is
             * what made a press on this panel favourite whatever the hidden cursor
             * happened to be resting on.
             */
            else -> Unit
        }
    }

    /** Steps the flow, wrapping; see [stepCard] for why it wraps. */
    private fun stepPlatformCard(delta: Int, count: Int) {
        if (count <= 1) return
        _platformCardDirection.value = delta
        _platformCardIndex.value = stepCard(_platformCardIndex.value, delta, count)
    }

    /**
     * Opens the system under the cursor, as its folder on the grid.
     *
     * The same folder the grid shows, opened the same way, so everything inside
     * it — sorting, the context menu, launching, the information panel — is what
     * it has always been. A system whose folder does not exist is skipped rather
     * than reported: [platformCards] only builds cards for systems with games,
     * and a scan creates the folder, so the two disagree only in the moment
     * between the two writes.
     */
    private fun openPlatformCard(cards: List<PlatformCard>) {
        val card = cards.getOrNull(_platformCardIndex.value.coerceIn(0, cards.lastIndex)) ?: return
        openFolder(PlatformFolders.idFor(card.platform.id))
    }

    /**
     * Reaches the section bar, but only when it is drawn.
     *
     * A bar with one section on it is not rendered — see `BottomScreen` — so
     * parking the cursor there would put it somewhere invisible with no way back.
     * The grid has the same hazard and predates this; the flow does not inherit
     * it because the flow has fewer places for a lost cursor to be noticed.
     */
    private fun enterNavBarIfShown() {
        val state = uiState.value
        if (LauncherTab.visible(state.enabledExtensions, couchMode.value).size > 1) {
            enterNavBar()
        }
    }

    /** Routes Home commands through Couch Mode's rails instead of hidden grid cells. */
    private fun onCouchHomeCommand(command: ControllerCommand) {
        val state = uiState.value
        if (!state.isFolderOpen) {
            when (command) {
                ControllerCommand.PAGE_PREVIOUS -> {
                    stepCouchPlatform(-1)
                    return
                }

                ControllerCommand.PAGE_NEXT -> {
                    stepCouchPlatform(1)
                    return
                }

                else -> Unit
            }
        }
        val rails = couchRails(state)
        if (rails.isEmpty()) {
            when (command) {
                ControllerCommand.NAVIGATE_UP, ControllerCommand.BACK -> enterNavBar()
                ControllerCommand.OPEN_SIDE_MENU -> toggleCouchPlatformMenu()
                ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
                // As above: Tab reaches the system list, not the drawer.
                ControllerCommand.OPEN_APP_DRAWER -> toggleCouchPlatformMenu()
                ControllerCommand.SEARCH -> emit(LauncherEffect.OpenSearch)
                ControllerCommand.GO_HOME -> goHome()
                else -> Unit
            }
            return
        }

        val currentRail = _couchFocus.value.rail.coerceIn(0, rails.lastIndex)
        val currentItems = rails[currentRail].entries
        val currentItem = _couchFocus.value.item.coerceIn(
            0,
            (currentItems.size - 1).coerceAtLeast(0),
        )
        val focusedEntry = currentItems.getOrNull(currentItem)

        fun moveRail(delta: Int) {
            val nextRail = (currentRail + delta).coerceIn(0, rails.lastIndex)
            if (nextRail == currentRail && delta < 0) {
                enterNavBar()
            } else {
                val remembered = couchItemByRailId[rails[nextRail].id] ?: 0
                setCouchFocus(rails, nextRail, remembered)
            }
        }

        /*
         * The dashboard is four regions, not one shelf.
         *
         * Movement between them is [CouchNavigation], which is pure and tested;
         * this only has to say what a press *does* once the cursor has arrived.
         * Directions taken while the cursor is off the shelf must not fall
         * through to the rail cursor below, which is what `moveRail` still
         * drives for the shelf itself.
         */
        val focus = _couchFocus.value
        val zone = focus.zone

        fun applyMove(move: CouchNavigation.Move) = when (move) {
            is CouchNavigation.Move.ExitToNavBar -> enterNavBar()
            is CouchNavigation.Move.To -> {
                val next = move.focus
                /*
                 * Every landing on the shelf goes through the rail cursor.
                 *
                 * Returning from another zone used to assign the focus straight,
                 * which skipped both things `setCouchFocus` does: clamping the
                 * item to the rail it is actually on, and recording it as that
                 * rail's remembered place. A shelf that shrank while the cursor
                 * was away — a scrape finishing, a favourite cleared — left the
                 * item past the end of it.
                 */
                if (next.zone == CouchZone.SHELF) {
                    if (zone == CouchZone.SHELF && next.rail != focus.rail) {
                        // Rail to rail: pick up where that rail was left.
                        moveRail(next.rail - focus.rail)
                    } else {
                        setCouchFocus(rails, next.rail, next.item)
                    }
                } else {
                    _couchFocus.value = next
                }
            }
        }

        if (zone != CouchZone.SHELF) {
            when (command) {
                ControllerCommand.NAVIGATE_UP -> {
                    applyMove(
                        if (zone == CouchZone.LIBRARY) {
                            CouchNavigation.verticalInLibrary(focus, -1)
                        } else {
                            CouchNavigation.up(focus, rails.size)
                        },
                    )
                    return
                }

                ControllerCommand.NAVIGATE_DOWN -> {
                    applyMove(
                        if (zone == CouchZone.LIBRARY) {
                            CouchNavigation.verticalInLibrary(focus, 1)
                        } else {
                            CouchNavigation.down(focus, rails.size)
                        },
                    )
                    return
                }

                ControllerCommand.NAVIGATE_LEFT -> {
                    applyMove(CouchNavigation.horizontal(focus, -1, currentItems.size))
                    return
                }

                ControllerCommand.NAVIGATE_RIGHT -> {
                    applyMove(CouchNavigation.horizontal(focus, 1, currentItems.size))
                    return
                }

                ControllerCommand.CONFIRM -> {
                    onCouchZoneConfirmed(focus, focusedEntry, rails)
                    return
                }

                // Back returns to the shelf rather than out of the mode: the
                // cursor came from there and that is where it belongs.
                ControllerCommand.BACK -> {
                    setCouchFocus(rails, focus.rail, focus.item)
                    return
                }

                else -> Unit
            }
        }

        when (command) {
            ControllerCommand.NAVIGATE_LEFT ->
                setCouchFocus(rails, currentRail, currentItem - 1)

            ControllerCommand.NAVIGATE_RIGHT ->
                setCouchFocus(rails, currentRail, currentItem + 1)

            ControllerCommand.NAVIGATE_UP ->
                applyMove(CouchNavigation.up(_couchFocus.value, rails.size))

            ControllerCommand.NAVIGATE_DOWN ->
                applyMove(CouchNavigation.down(_couchFocus.value, rails.size))

            ControllerCommand.PAGE_PREVIOUS,
            ControllerCommand.PAGE_NEXT,
            -> Unit
            ControllerCommand.CONFIRM -> focusedEntry?.let(::launchEntry)
            ControllerCommand.CONTEXT_MENU -> focusedEntry?.let(::openCouchQuickDetails)
            ControllerCommand.TOGGLE_FAVORITE -> focusedEntry?.let(::toggleFavorite)
            ControllerCommand.BACK -> if (state.isFolderOpen) closeFolder() else enterNavBar()
            ControllerCommand.OPEN_SIDE_MENU -> toggleCouchPlatformMenu()
            /*
             * Tab opens the platform menu here, not the app drawer.
             *
             * On a keyboard Tab is bound to the drawer, which is the right
             * answer on the handheld grid — the drawer is what a keyboard user
             * reaches for. Couch mode has no grid to add anything to, and its
             * apps are a rail like any other; what a keyboard user wants from
             * this screen is the system list, which otherwise has no key at all.
             */
            ControllerCommand.OPEN_APP_DRAWER -> toggleCouchPlatformMenu()
            ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
            ControllerCommand.GO_HOME -> goHome()
            ControllerCommand.SEARCH -> emit(LauncherEffect.OpenSearch)
            ControllerCommand.PICK_UP,
            ControllerCommand.CANCEL_EDIT,
            ControllerCommand.CYCLE_IMAGE_PREVIOUS,
            ControllerCommand.CYCLE_IMAGE_NEXT,
            -> Unit
        }
    }

    /**
     * What Confirm does outside the shelf.
     *
     * Each region's actions are listed in the order they are drawn, because the
     * cursor's position *is* the index — see [CouchNavigation]. Kept beside the
     * command handler rather than in the composable so that a controller press
     * and a touch reach the same code.
     */
    private fun onCouchZoneConfirmed(
        focus: CouchFocus,
        focusedEntry: GridEntry?,
        rails: List<CouchRail>,
    ) {
        when (focus.zone) {
            CouchZone.SPOTLIGHT -> when (focus.action) {
                0 -> focusedEntry?.let(::launchEntry)
                else -> focusedEntry?.let(::openCouchQuickDetails)
            }

            /*
             * The same jumps the panel's rows make when tapped, in the order
             * they are drawn: all games, favourites, recently played, installed,
             * platforms. Every one of them is a shelf — Installed used to open
             * the app drawer, which couch mode no longer raises.
             */
            CouchZone.LIBRARY -> {
                val index = couchLibraryRailIndex(rails, focus.action)
                /*
                 * A row whose rail does not exist still leaves the panel.
                 *
                 * Favourites and Recently played are only built once something is
                 * in them, so those rows can point at nothing — and a press that
                 * did nothing at all was indistinguishable from a controller that
                 * had stopped responding. Returning to the shelf at least answers
                 * the press. The row is drawn dimmed as well, so the answer is
                 * visible before it is pressed.
                 */
                setCouchFocus(
                    rails = rails,
                    rail = index ?: focus.rail,
                    item = index?.let { couchItemByRailId[rails[it].id] ?: 0 } ?: focus.item,
                )
            }

            CouchZone.DASHBOARD -> when (focus.action) {
                0 -> emit(LauncherEffect.OpenSearch)
                1 -> openSortPicker()
                2 -> uiState.value.entriesById.values
                    .filterIsInstance<GameEntry>()
                    .filterNot(GridEntry::isHidden)
                    .randomOrNull()
                    ?.let(::launchEntry)

                3 -> onShortcut(ShortcutAction.BLUETOOTH)
                4 -> openDownloads()
                else -> requestCouchPowerMenu()
            }

            CouchZone.SHELF -> focusedEntry?.let(::launchEntry)
        }
    }

    /** Touch/pointer focus follows the same rail cursor the controller drives. */
    fun focusCouchEntry(rail: Int, item: Int) {
        val rails = couchRails(uiState.value)
        setCouchFocus(rails, rail, item)
    }

    /**
     * The power dialog, which only the accessibility service can raise.
     *
     * Emitted rather than called: the service lives in the app module and the
     * view model does not know about it, which is the same boundary every other
     * system action here crosses.
     */
    private fun requestCouchPowerMenu() = emit(LauncherEffect.RequestPowerMenu)

    private fun setCouchFocus(rails: List<CouchRail>, rail: Int, item: Int) {
        if (rails.isEmpty()) {
            _couchFocus.value = CouchFocus()
            return
        }
        val safeRail = rail.coerceIn(0, rails.lastIndex)
        val selectedRail = rails[safeRail]
        val safeItem = item.coerceIn(0, (selectedRail.entries.size - 1).coerceAtLeast(0))
        couchItemByRailId[selectedRail.id] = safeItem
        _couchFocus.value = CouchFocus(safeRail, safeItem)
    }

    /** Selects a platform from Couch Mode's strip and reveals its game rail. */
    fun selectCouchPlatform(index: Int) {
        val state = uiState.value
        if (state.isFolderOpen) return
        val platforms = availableCouchPlatforms(state)
        if (platforms.isEmpty()) return
        val safeIndex = index.coerceIn(0, platforms.lastIndex)
        _couchPlatformIndex.value = safeIndex
        val platformId = platforms[safeIndex].id
        val rails = couchRails(state)
        val platformRail = rails.indexOfFirst { it.id == "platform:$platformId" }
        if (platformRail >= 0) {
            val rememberedItem = couchItemByRailId[rails[platformRail].id] ?: 0
            setCouchFocus(rails, platformRail, rememberedItem)
        }
    }

    private fun openCouchQuickDetails(entry: GridEntry) {
        _couchQuickDetailsActionIndex.value = 0
        _couchQuickDetailsScroll.value = CouchDetailScroll()
        _couchQuickDetailsEntryId.value = entry.id
    }

    fun closeCouchQuickDetails() {
        _couchQuickDetailsEntryId.value = null
        _couchQuickDetailsActionIndex.value = 0
        _couchQuickDetailsScroll.value = CouchDetailScroll()
    }

    private fun onCouchQuickDetailsCommand(command: ControllerCommand) {
        val entry = _couchQuickDetailsEntryId.value
            ?.let(uiState.value.entriesById::get)
        if (entry == null) {
            closeCouchQuickDetails()
            return
        }
        when (command) {
            /*
             * Left and right walk the buttons; up and down read the page.
             *
             * All four used to do the same thing — step the same list of four
             * actions — which spent the one axis the panel actually needed. It is
             * a full screen with a description under it now, and a stick that can
             * only cycle four buttons cannot reach the words it is sitting on.
             * The buttons are a row, so left and right are what the eye expects
             * of them; the reading is a column, so up and down are what it
             * expects of that.
             */
            ControllerCommand.NAVIGATE_LEFT -> _couchQuickDetailsActionIndex.value =
                (_couchQuickDetailsActionIndex.value - 1).mod(COUCH_DETAIL_ACTION_COUNT)

            ControllerCommand.NAVIGATE_RIGHT -> _couchQuickDetailsActionIndex.value =
                (_couchQuickDetailsActionIndex.value + 1).mod(COUCH_DETAIL_ACTION_COUNT)

            // The shoulders scroll as well, because that is what they do
            // everywhere else a page is longer than the screen.
            ControllerCommand.NAVIGATE_UP,
            ControllerCommand.PAGE_PREVIOUS,
            -> scrollCouchQuickDetails(-1)

            ControllerCommand.NAVIGATE_DOWN,
            ControllerCommand.PAGE_NEXT,
            -> scrollCouchQuickDetails(1)

            ControllerCommand.CONFIRM -> when (_couchQuickDetailsActionIndex.value) {
                COUCH_DETAIL_PLAY -> {
                    closeCouchQuickDetails()
                    launchEntry(entry)
                }

                COUCH_DETAIL_FAVOURITE -> toggleFavorite(entry)
            }

            ControllerCommand.TOGGLE_FAVORITE -> toggleFavorite(entry)

            // A second Y closes the page the first one opened. It used to reach
            // the legacy action menu, which couch mode no longer raises at all.
            ControllerCommand.CONTEXT_MENU -> closeCouchQuickDetails()

            ControllerCommand.OPEN_SIDE_MENU -> {
                closeCouchQuickDetails()
                toggleCouchPlatformMenu()
            }

            ControllerCommand.OPEN_APP_DRAWER -> {
                closeCouchQuickDetails()
                openAppDrawer()
            }

            ControllerCommand.SEARCH -> {
                closeCouchQuickDetails()
                emit(LauncherEffect.OpenSearch)
            }

            ControllerCommand.GO_HOME -> goHome()
            ControllerCommand.BACK -> closeCouchQuickDetails()
            else -> Unit
        }
    }

    /**
     * Puts the Y panel's cursor on an action, for the pointer.
     *
     * Guarded on the panel being open: a hover reported as it fades out would
     * otherwise leave a position behind for the next game's panel to start on.
     */
    fun focusCouchQuickDetailsAction(index: Int) {
        if (_couchQuickDetailsEntryId.value == null) return
        _couchQuickDetailsActionIndex.value = index.coerceIn(0, COUCH_DETAIL_ACTION_COUNT - 1)
    }

    /**
     * Asks the Y panel's reading column to move one step.
     *
     * The tick is what carries the press. Two downs in a row are the same
     * direction, so without something that changes the second one would arrive
     * as a value the panel had already seen and acted on.
     */
    private fun scrollCouchQuickDetails(direction: Int) {
        _couchQuickDetailsScroll.update { current ->
            CouchDetailScroll(tick = current.tick + 1, direction = direction)
        }
    }

    /** L2/R2 wrap through installed platforms without opening generated folders. */
    private fun stepCouchPlatform(delta: Int) {
        val platforms = availableCouchPlatforms(uiState.value)
        if (platforms.isEmpty()) return
        val current = _couchPlatformIndex.value.coerceIn(0, platforms.lastIndex)
        selectCouchPlatform((current + delta).mod(platforms.size))
    }

    /** Opens Settings from the dedicated Couch Mode nav destination. */
    fun openCouchSettings() {
        closeCouchQuickDetails()
        _navCursor.value = _selectedTab.value
        _couchSettingsFocused.value = true
        emit(LauncherEffect.OpenSettings)
    }

    /**
     * Steps the one Couch navigation sequence: Stream, Home, Movies, Settings.
     *
     * @return true when the new destination is Settings, which lets the host keep
     * its Settings overlay state in sync while the screen itself remains in the
     * normal couch content shell.
     */
    fun cycleCouchDestination(delta: Int, fromSettings: Boolean = false): Boolean {
        // Always the couch list: this sequence exists only in couch mode.
        val tabs = LauncherTab.visible(enabledExtensionIds(), couch = true)
        if (tabs.isEmpty()) return false
        val settingsIndex = tabs.size
        val currentIndex = if (fromSettings) {
            settingsIndex
        } else {
            tabs.indexOf(_selectedTab.value).takeIf { it >= 0 }
                ?: tabs.indexOf(LauncherTab.DEFAULT).coerceAtLeast(0)
        }
        val nextIndex = (currentIndex + delta).mod(tabs.size + 1)
        if (nextIndex == settingsIndex) {
            openCouchSettings()
            return true
        }

        selectTab(tabs[nextIndex])
        leaveNavBar()
        return false
    }

    /** Down from the grid: one row, or out of the grid and onto the bar. */
    private fun moveDownOrEnterNavBar() {
        if (isOnBottomRow()) enterNavBar() else move(NavDirection.DOWN)
    }

    /**
     * Moves the cursor.
     *
     * Movement past a horizontal edge turns the page when the user has that
     * enabled, which is what makes a long library navigable without ever
     * reaching for the shoulder buttons.
     *
     * Synchronous, and that is the point: everything it touches is snapshot state
     * already in memory, so a press should move the cursor in the same frame it
     * arrives. See [controlSettings] for what it used to do instead.
     */
    fun move(direction: NavDirection) {
        val state = uiState.value
        val spec = state.spec
        val controls = controlSettings.value
        val position = cursor.value
        val page = currentPage.value

        /*
         * Stepped from the edge of whatever the cursor is standing on, not from
         * the cell it is in.
         *
         * For everything but a widget those are the same cell. A widget covers
         * several, and stepping from the cell meant the cursor walked *through*
         * it — two presses to cross a 2-wide clock, resting in the middle of it
         * on the way, which read as the grid still having its own cells behind
         * the widget. Leaving from the far edge makes a widget one stop.
         */
        val box = state.cursorBox(page, position.row, position.column)

        var row = box.row
        var column = box.column

        when (direction) {
            NavDirection.UP -> row = box.row - 1
            NavDirection.DOWN -> row = box.lastRow + 1
            NavDirection.LEFT -> column = box.column - 1
            NavDirection.RIGHT -> column = box.lastColumn + 1
        }

        // Vertical wrap is opt-in; vertical edges never change page,
        // because a page turn triggered by pressing Up reads as a glitch.
        if (row < 0) row = if (controls.wrapNavigation) spec.rows - 1 else 0
        if (row >= spec.rows) row = if (controls.wrapNavigation) 0 else spec.rows - 1

        when {
            column < 0 -> when {
                controls.edgeFlipsPage && page > 0 -> {
                    currentPage.value = page - 1
                    column = spec.columns - 1
                }

                controls.wrapNavigation -> column = spec.columns - 1
                else -> column = 0
            }

            column >= spec.columns -> {
                val lastPage = maxOf(0, uiState.value.visiblePageCount - 1)
                when {
                    controls.edgeFlipsPage && page < lastPage -> {
                        currentPage.value = page + 1
                        column = 0
                    }

                    controls.wrapNavigation -> column = 0
                    else -> column = spec.columns - 1
                }
            }
        }

        // Landing on a widget parks the cursor on its anchor, so the highlight
        // is the whole widget rather than one cell somewhere inside it.
        cursor.value = state.snapCursor(page, row, column)

        /*
         * A held icon is deliberately *not* committed on every cursor step.
         * It used to be, which meant dragging across the grid rewrote a
         * placement per cell travelled — harmless while a move merely swapped
         * two icons, but now that landing on an occupied cell displaces its
         * occupant, it would strip the placement of everything the cursor
         * passed over on the way. The move is applied once, on drop.
         */
    }

    /** Places the cursor directly, used by touch input. */
    fun setCursor(row: Int, column: Int) {
        // Snapped for the same reason a move is: a tap anywhere on a widget is a
        // tap on the widget, not on the cell under that part of it.
        val state = uiState.value
        cursor.value = state.snapCursor(currentPage.value, row, column)
    }

    fun setPage(pageIndex: Int) {
        currentPage.value = pageIndex.coerceAtLeast(0)
    }

    private fun changePage(delta: Int) {
        // The open folder's own paging while one is open.
        val lastPage = maxOf(0, uiState.value.visiblePageCount - 1)
        currentPage.value = (currentPage.value + delta).coerceIn(0, lastPage)
    }

    // --------------------------------------------------------------- action

    /**
     * Acts on whatever the cursor is over.
     *
     * In arrange mode Confirm is grab-and-drop rather than launch — otherwise
     * edit mode could be entered but nothing could actually be moved, which is
     * exactly how it behaved before: icons wobbled and Confirm still launched.
     */
    fun confirm() {
        val state = uiState.value

        when (state.editMode) {
            // Commits the move. This used to only clear the held state, which
            // worked by accident while cursor movement was writing the placement
            // on every step; once that was removed, nothing applied the move at
            // all and icons could no longer be rearranged.
            is EditMode.Holding -> {
                drop()
                return
            }

            EditMode.Arranging -> {
                pickUp()
                return
            }

            // Confirm settles the size, as it settles a move. Reached only from
            // touch: the controller's presses are taken by [onResizeCommand]
            // before they arrive here.
            is EditMode.Resizing -> {
                finishWidgetResize()
                return
            }

            EditMode.None -> Unit
        }

        val entry = state.selection ?: return
        launchEntry(entry)
    }

    /**
     * Whether the interactive grid is projected onto the secondary panel.
     *
     * Set by the shell, which is the only place that knows how the two surfaces
     * map onto the hardware.
     */
    private var gridOnSecondaryDisplay = false

    fun setGridOnSecondaryDisplay(value: Boolean) {
        gridOnSecondaryDisplay = value
    }

    /**
     * Tells the launcher which display its second panel is on.
     *
     * Reported by the shell rather than looked up, so that an app sent to "the second
     * screen" lands on the panel the launcher actually projects onto — not on the
     * first non-default display the system happens to list, which on a device with
     * any extra display at all is not the same thing.
     */
    fun setSecondaryDisplayId(displayId: Int?) {
        entryLauncher.secondPanelDisplayId = displayId
        if (displayId == null) secondaryPresentationVisible.value = false
    }

    /** Acknowledgement from [SecondaryDisplay] after the panel is shown or dismissed. */
    fun setSecondaryPresentationVisible(visible: Boolean) {
        secondaryPresentationVisible.value = visible
    }

    /**
     * Where an ordinary launch sends an entry: **the main panel, always**.
     *
     * This used to follow the grid — press A on a grid that lives on the second
     * panel and the app was sent to that panel, taking the launcher's own screen
     * with it. That is the arrangement this hardware is least able to deliver and
     * the least useful when it works: the second panel accepts an activity only
     * through a narrow route, and succeeding means the screen the user is holding
     * stops being a launcher.
     *
     * Sending it to the main panel is both the reliable answer and the better
     * one. It is the display every app can be placed on, and it leaves the grid
     * where the user is looking — game above, launcher below, both live. That is
     * what two screens are for, and it is what the other frontends on this device
     * settle on.
     *
     * The other panel is still available, from an entry's context menu, and is
     * treated as an explicit request: see [launchEntryOn].
     */
    private val defaultLaunchTarget: LaunchTarget
        get() = LaunchTarget.MAIN_SCREEN

    fun launchEntry(entry: GridEntry) {
        // A folder opens in place; it has nothing to launch.
        if (entry is FolderEntry) {
            openFolder(entry.id)
            return
        }
        /*
         * The entry's own preference, where it has one.
         *
         * Set from its context menu and stored per id, so a game that wants the
         * top screen gets it from the grid, from search, from a widget and from
         * couch mode without any of them knowing about the setting. Sending
         * something to the other panel *once* is still the context menu's
         * `LAUNCH_*` actions; this is the answer for "always".
         */
        launchEntryOn(entry, preferredTargetFor(entry.id) ?: defaultLaunchTarget)
    }

    /**
     * Per-entry panel preferences, held hot so a launch does not have to wait.
     *
     * A launch is the one path where a suspend read would be felt: the button has
     * already been pressed, and reading the settings document first puts a frame
     * or two between the press and the app appearing.
     */
    private val launchPanels: StateFlow<Map<String, PreferredPanel>> =
        settingsRepository.library
            .map { it.launchPanels }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The stored panel for an entry, mapped onto a launch target. */
    private fun preferredTargetFor(entryId: String): LaunchTarget? =
        when (launchPanels.value[entryId]) {
            PreferredPanel.TOP -> LaunchTarget.MAIN_SCREEN
            PreferredPanel.BOTTOM -> LaunchTarget.SECOND_SCREEN
            PreferredPanel.DEFAULT, null -> null
        }

    /**
     * Remembers which panel an entry should open on from now on.
     *
     * [PreferredPanel.DEFAULT] removes the key rather than storing it, so the map
     * holds only entries that actually have an opinion and never grows a row per
     * game in the library.
     */
    fun setPreferredPanel(entryId: String, panel: PreferredPanel) {
        viewModelScope.launchSafely(TAG) {
            settingsRepository.updateLibrary { library ->
                library.copy(
                    launchPanels = if (panel == PreferredPanel.DEFAULT) {
                        library.launchPanels - entryId
                    } else {
                        library.launchPanels + (entryId to panel)
                    },
                )
            }
            emit(LauncherEffect.ShowMessage(panel.confirmation))
        }
    }

    private suspend fun handleResult(result: LaunchResult, entryId: String) {
        when (result) {
            // Opens a play session as well as bumping the launch count. The
            // session is closed by whatever brings the launcher back, which is
            // the only moment the elapsed time is knowable.
            is LaunchResult.Success -> playtimeTracker.onLaunched(entryId)
            is LaunchResult.Failed -> emit(
                LauncherEffect.LaunchFailed(describe(result.reason)),
            )
        }
    }

    /**
     * Closes any play session left open by a launch.
     *
     * Called when this window is resumed — meaning whatever was covering it on its
     * own display has gone, which is when the session's duration becomes knowable —
     * and once at startup for the case where the launcher was killed while the game
     * ran.
     *
     * Deliberately does *not* take the second panel back. It used to, which meant
     * any signal that the launcher was in front also evicted whatever was running
     * on the other screen: on a two-screen device the user is *expected* to bring
     * the launcher forward while still playing.
     */
    fun settlePlaytime() {
        viewModelScope.launchSafely(TAG) { playtimeTracker.settle() }
    }

    private fun describe(failure: LaunchFailure): String = when (failure) {
        is LaunchFailure.EmulatorMissing ->
            "No emulator is configured for this platform"

        is LaunchFailure.EmulatorNotInstalled ->
            "The configured emulator (${failure.packageName}) is not installed"

        LaunchFailure.RomUnavailable ->
            "ROM unavailable — regrant its folder or rescan the library"

        is LaunchFailure.UnsupportedEmulatorLaunch -> failure.message

        is LaunchFailure.NoHandler -> "Nothing on this device can open that"

        /*
         * A sentence someone can act on, and then the platform's own first line.
         *
         * This printed `cause.message` verbatim once, which for a refused display
         * placement is a paragraph of process records and uids shown to someone
         * who pressed A on an icon; it was replaced with a fixed sentence, and
         * that went too far the other way. "Android would not let THOR open that
         * app on this screen" was shown for every game on the grid for a reason
         * that had nothing to do with screens — a URI permission the launcher was
         * passing on without holding — and the sentence was general enough to be
         * true of that, so nothing on the device contradicted it. A wrong
         * diagnosis that reads as authoritative is worse than a raw one.
         *
         * So: the sentence, and then the platform's first line, trimmed to
         * something that fits on a panel. The full throwable is still logged by
         * `EntryLauncher`, which is where the rest of it belongs.
         */
        is LaunchFailure.Unknown -> when (val cause = failure.cause) {
            is SecurityException ->
                "Android refused to open that" + cause.detail()

            else -> "That app would not open" + cause.detail()
        }
    }

    /**
     * The first line of a throwable's message, short enough to show.
     *
     * One line, because these are usually many and only the first names what was
     * refused; the rest is the stack the log already has.
     */
    private fun Throwable.detail(): String {
        val first = message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        if (first.isBlank()) return ""
        val clipped = if (first.length > DETAIL_LIMIT) {
            first.take(DETAIL_LIMIT).trimEnd() + "…"
        } else {
            first
        }
        return " — $clipped"
    }

    fun back() {
        when {
            editingEntryId.value != null -> editingEntryId.value = null
            contextMenuEntryId.value != null -> closeContextMenu()
            sideMenuOpen.value -> sideMenuOpen.value = false
            // Back leaves an open folder before anything else on the grid, and puts
            // the cursor back on the folder it came from.
            openFolderId.value != null -> closeFolder()
            editMode.value.isActive -> cancelEdit()
            currentPage.value != 0 -> currentPage.value = 0
        }
    }

    /** Resets the launcher to its home state: page one, no overlays, cursor home. */
    fun goHome() {
        /*
         * Reclaims the secondary panel — and it is the *only* thing that does.
         *
         * After a second-screen launch the presentation is dismissed so the app can
         * be seen, and re-showing it puts the launcher back over whatever is on that
         * display. Home is the one unambiguous "give me THOR back on both screens":
         * every other candidate signal — window focus, top-resumed status — is also
         * produced by simply touching the launcher's other panel, which is what the
         * user does *while* an app is running, and taking the panel back then is
         * exactly what made playing on one screen and browsing on the other
         * impossible.
         */
        setPanelOccupant(null)
        // The companion panel goes with it. Home is the one signal the launcher
        // has that a game is no longer being played — nothing can observe the
        // game itself ending — so it is also what puts the grid back.
        setRunningEntry(null)

        // Whatever was launched is being left behind, so its session is over.
        settlePlaytime()

        // Home means the Home section with the cursor back in the grid. Returning
        // to the launcher still on Movies would be the same surprise as returning
        // to it on page four of the grid.
        _selectedTab.value = LauncherTab.DEFAULT
        _navCursor.value = null
        _couchSettingsFocused.value = false
        _couchFocus.value = CouchFocus()
        closeCouchQuickDetails()

        sideMenuOpen.value = false
        closeFolder()
        editMode.value = EditMode.None
        currentPage.value = 0
        cursor.value = CursorPosition(0, 0)
        // Every transient surface closes too; leaving the drawer or a dialog up
        // after Home would be the same bug in a different place.
        contextMenuEntryId.value = null
        editingEntryId.value = null
        closeAppDrawer()
        closeSortPicker()
    }

    fun toggleFavorite(entry: GridEntry? = uiState.value.selection) {
        val target = entry ?: return
        viewModelScope.launchSafely(TAG) {
            libraryRepository.setFavorite(target.id, !target.isFavorite)
        }
    }

    // ----------------------------------------------------------------- edit

    /** Picks up the entry under the cursor so it follows subsequent movement. */
    fun pickUp() {
        val state = uiState.value
        // Rearranging is a property of the *page*: a folder's children have no
        // placements to move, so picking one up would drop it onto the home grid
        // behind the folder.
        if (state.isFolderOpen) return
        val entry = state.selection ?: return
        editMode.value = EditMode.Holding(
            entryId = entry.id,
            originPage = state.currentPage,
            originRow = state.cursor.row,
            originColumn = state.cursor.column,
        )
    }

    /**
     * Drops a held icon where the cursor is.
     *
     * If that cell was occupied, the occupant is picked up rather than being
     * shuffled off to wherever the dropped icon came from — so the user places it
     * deliberately instead of hunting for where it went. Chains naturally: each
     * drop onto something occupied hands you the next icon.
     */
    fun drop() {
        val holding = editMode.value as? EditMode.Holding ?: return
        val state = uiState.value
        val page = state.currentPage
        val row = state.cursor.row
        val column = state.cursor.column

        viewModelScope.launchSafely(TAG) {
            // The held entry may have no placement, if it was itself displaced by
            // the previous drop.
            val hasPlacement = state.placements.any { it.entryId == holding.entryId }
            val result = if (hasPlacement) {
                gridRepository.moveEntry(holding.entryId, page, row, column)
            } else if (gridRepository.placeEntryAt(holding.entryId, page, row, column)) {
                MoveResult.Moved
            } else {
                MoveResult.Blocked
            }

            editMode.value = when (result) {
                is MoveResult.Displaced -> {
                    emit(LauncherEffect.ShowMessage("Now place the icon you moved"))
                    EditMode.Holding(
                        entryId = result.entryId,
                        // Its origin is the cell it just lost, which is where Back
                        // should return it to.
                        originPage = page,
                        originRow = row,
                        originColumn = column,
                    )
                }

                /*
                 * Still held, and said so.
                 *
                 * A refused drop used to end the hold, so the entry stayed where
                 * it was and the user was left believing they had moved it. Keeping
                 * the hold means the next press lands it somewhere that works.
                 */
                MoveResult.Blocked -> {
                    emit(LauncherEffect.ShowMessage("That does not fit here"))
                    holding
                }

                MoveResult.Moved -> EditMode.Arranging
            }
        }
    }

    /**
     * Returns a held icon to where it was picked up.
     *
     * A displaced icon's origin cell is occupied by whatever displaced it, so it
     * cannot simply go back — moving it there would displace that one in turn and
     * the cancel would never settle. It lands in the first free cell instead,
     * which is at least somewhere the user can find it.
     */
    fun cancelEdit() {
        val mode = editMode.value
        if (mode is EditMode.Holding) {
            viewModelScope.launchSafely(TAG) {
                val restored = gridRepository.placeEntryAt(
                    entryId = mode.entryId,
                    pageIndex = mode.originPage,
                    row = mode.originRow,
                    column = mode.originColumn,
                )
                if (!restored) {
                    gridRepository.placeUnplacedEntries(listOf(mode.entryId))
                }
                editMode.value = EditMode.None
            }
        } else {
            editMode.value = EditMode.None
        }
    }

    fun enterArrangeMode() {
        // The grid being rearranged is the page, so an open folder stands aside.
        closeFolder()
        editMode.value = EditMode.Arranging
    }

    // --------------------------------------------------------- folder picker

    /** Raises the folder picker for the entry the context menu was opened on. */
    fun openFolderPicker(entry: GridEntry? = uiState.value.contextMenuEntry) {
        val target = entry ?: return
        val folders = uiState.value.entriesById.values
            .filterIsInstance<FolderEntry>()
            // Smart folders compute their own contents, so filing into one would
            // be silently undone on the next evaluation.
            .filterNot(FolderEntry::isSmart)
            .sortedBy(FolderEntry::sortTitle)

        closeContextMenu()
        _folderPicker.value = FolderPickerState(
            visible = true,
            entryId = target.id,
            entryTitle = target.title,
            folders = folders,
            focusedIndex = 0,
        )
    }

    fun closeFolderPicker() {
        _folderPicker.update { it.copy(visible = false) }
    }

    /** Files the picked entry into an existing folder. */
    fun fileIntoFolder(folderId: String) {
        val entryId = _folderPicker.value.entryId ?: return
        viewModelScope.launchSafely(TAG) {
            gridRepository.addToFolder(entryId, folderId)
            closeFolderPicker()
        }
    }

    /** Creates a folder and files the picked entry straight into it. */
    fun fileIntoNewFolder() {
        val entryId = _folderPicker.value.entryId ?: return
        viewModelScope.launchSafely(TAG) {
            val folderId = gridRepository.createEmptyFolder("Folder")
            gridRepository.addToFolder(entryId, folderId)
            closeFolderPicker()
        }
    }

    /** Takes the selected entry back out onto the grid. */
    fun removeFromFolder(entry: GridEntry) {
        val folderId = folderContaining(entry.id) ?: return
        viewModelScope.launchSafely(TAG) {
            gridRepository.removeFromFolder(entry.id, folderId)
            closeContextMenu()
        }
    }

    /** The folder holding [entryId], if any. */
    fun folderContaining(entryId: String): String? = uiState.value.entriesById.values
        .filterIsInstance<FolderEntry>()
        .firstOrNull { entryId in it.childIds }
        ?.id

    /** Navigation while the folder picker holds input. */
    private fun onFolderPickerCommand(command: ControllerCommand) {
        val picker = _folderPicker.value
        val count = picker.rowCount
        when (command) {
            ControllerCommand.NAVIGATE_UP ->
                _folderPicker.update { it.copy(focusedIndex = (it.focusedIndex - 1 + count) % count) }

            ControllerCommand.NAVIGATE_DOWN ->
                _folderPicker.update { it.copy(focusedIndex = (it.focusedIndex + 1) % count) }

            ControllerCommand.CONFIRM -> if (picker.isNewFolderRow) {
                fileIntoNewFolder()
            } else {
                picker.folders.getOrNull(picker.focusedIndex)?.let { fileIntoFolder(it.id) }
            }

            ControllerCommand.BACK, ControllerCommand.CONTEXT_MENU -> closeFolderPicker()
            else -> Unit
        }
    }

    /** Drops [draggedId] onto [targetId], creating or joining a folder. */
    fun dropOnto(draggedId: String, targetId: String) {
        if (draggedId == targetId) return
        viewModelScope.launchSafely(TAG) {
            val target = uiState.value.entriesById[targetId]
            if (target is FolderEntry) {
                gridRepository.addToFolder(draggedId, targetId)
            } else {
                gridRepository.createFolderFrom(draggedId, targetId)
            }
            editMode.value = EditMode.Arranging
        }
    }

    // --------------------------------------------------------- context menu

    /** Opens the context menu for whatever the cursor is on. */
    fun openContextMenu(entry: GridEntry? = uiState.value.selection) {
        val target = entry ?: return
        /*
         * Couch mode has no long-press menu.
         *
         * It is a handheld surface — a column of small rows aimed at a thumb, at
         * a size chosen for a device held at arm's length — and on a television
         * it was the one thing on screen not built for the distance.
         *
         * Guarded here rather than at each caller because the routes into it are
         * scattered: a long press on a shelf card, the spotlight's own More info
         * button, the app drawer. All of them mean "tell me about this and let me
         * act on it", which from a sofa is the page Y raises.
         */
        if (couchMode.value) {
            openCouchQuickDetails(target)
            return
        }
        // Refreshed here so the "launch on second screen" row reflects the
        // hardware as it is right now, not as it was at startup.
        hasSecondScreen.value = entryLauncher.hasSecondaryDisplay()
        contextMenuIndex.value = 0
        contextMenuEntryId.value = target.id
    }

    fun closeContextMenu() {
        contextMenuEntryId.value = null
        contextMenuIndex.value = 0
    }

    fun focusContextRow(index: Int) {
        contextMenuIndex.value = index.coerceAtLeast(0)
    }

    /**
     * The rows the menu is currently showing.
     *
     * Both the UI and controller navigation read this one list, so the
     * highlighted index can never point at a row the menu did not render.
     */
    fun currentContextActions(): List<ContextAction> {
        val state = uiState.value
        val entry = state.contextMenuEntry ?: return emptyList()
        return contextActionsFor(
            entry = entry,
            hasSecondScreen = state.hasSecondScreen,
            fromDrawer = _appDrawer.value.visible,
            onGrid = state.placements.any { it.entryId == entry.id },
            foldersExist = state.entriesById.values.any { it is FolderEntry && !it.isSmart },
            inFolder = folderContaining(entry.id) != null,
            // Two kinds of "chosen by hand", asked the way each records it: a
            // platform folder marks its artwork with the user pack id, a game
            // locks its artwork field. Both mean the same thing to the menu:
            // there is something here worth offering to undo.
            hasCustomArtwork = when (entry) {
                is GameEntry ->
                    GameMetadata.FIELD_ARTWORK in entry.metadata.lockedFields
                else -> PlatformFolders.platformIdOf(entry.id)
                    ?.let { state.platformsById[it]?.artwork?.isUserChosen }
                    ?: false
            },
        )
    }

    /** Stores artwork the user picked for one game, and locks it against scrapes. */
    fun setGameArtwork(gameId: String, coverUri: String?, heroUri: String?) {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.setGameArtwork(gameId, coverUri, heroUri)
        }
    }

    /** Stores artwork the user picked for a platform, and dresses its folder. */
    fun setPlatformArtwork(platformId: String, iconUri: String?, heroUri: String?) {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.setPlatformArtwork(platformId, iconUri, heroUri)
        }
    }

    /** Opens the context menu for an entry chosen in the app drawer. */
    fun openDrawerContextMenu(row: Int, column: Int) {
        val spec = uiState.value.spec
        val drawer = _appDrawer.value
        val index = drawer.page * spec.cellsPerPage + row * spec.columns + column
        drawer.apps.getOrNull(index)?.let { app ->
            setDrawerCursor(row, column)
            openContextMenu(app)
        }
    }

    /** Places an entry on the grid, in the first free cell. */
    fun addToGrid(entry: GridEntry) {
        viewModelScope.launchSafely(TAG) {
            gridRepository.placeUnplacedEntries(listOf(entry.id))
            closeContextMenu()
        }
    }

    /**
     * Takes an entry off the grid without touching the entry itself.
     *
     * Only the placement is removed — the app stays installed and stays in the
     * drawer, which is the difference between this and Uninstall.
     */
    fun removeFromGrid(entry: GridEntry) {
        viewModelScope.launchSafely(TAG) {
            gridRepository.removePlacement(entry.id)
            closeContextMenu()
        }
    }

    /** Navigation while the context menu holds input. */
    private fun onContextMenuCommand(command: ControllerCommand) {
        val actions = currentContextActions()
        if (actions.isEmpty()) {
            closeContextMenu()
            return
        }

        when (command) {
            // The menu is a grid filled column by column, so up and down move
            // within a column and left and right cross between them. Treating
            // all four as +/-1 meant Down walked sideways — the cursor crossed
            // to the other column instead of dropping to the tile visibly
            // beneath it.
            ControllerCommand.NAVIGATE_LEFT ->
                contextMenuIndex.value =
                    stepContextMenuColumn(contextMenuIndex.value, -1, actions.size)

            ControllerCommand.NAVIGATE_RIGHT ->
                contextMenuIndex.value =
                    stepContextMenuColumn(contextMenuIndex.value, 1, actions.size)

            ControllerCommand.NAVIGATE_UP ->
                contextMenuIndex.value = stepContextMenuRow(contextMenuIndex.value, -1, actions.size)

            ControllerCommand.NAVIGATE_DOWN ->
                contextMenuIndex.value = stepContextMenuRow(contextMenuIndex.value, 1, actions.size)

            ControllerCommand.CONFIRM ->
                actions.getOrNull(contextMenuIndex.value)?.let(::performContextAction)

            ControllerCommand.BACK,
            ControllerCommand.CONTEXT_MENU,
            -> closeContextMenu()

            else -> Unit
        }
    }

    /** Navigation while the sort picker holds input. */
    private fun onSortPickerCommand(command: ControllerCommand) {
        val picker = _sortPicker.value
        val count = picker.orders.size
        when (command) {
            ControllerCommand.NAVIGATE_UP ->
                _sortPicker.update { it.copy(focusedIndex = (it.focusedIndex - 1 + count) % count) }

            ControllerCommand.NAVIGATE_DOWN ->
                _sortPicker.update { it.copy(focusedIndex = (it.focusedIndex + 1) % count) }

            // Left and right flip the direction, which is the only other axis
            // the picker has.
            ControllerCommand.NAVIGATE_LEFT,
            ControllerCommand.NAVIGATE_RIGHT,
            -> toggleSortDirection()

            ControllerCommand.CONFIRM ->
                picker.orders.getOrNull(picker.focusedIndex)?.let(::sortGrid)

            ControllerCommand.BACK -> closeSortPicker()
            else -> Unit
        }
    }

    /** Navigation while the app drawer holds input. */
    private fun onAppDrawerCommand(command: ControllerCommand) {
        val spec = uiState.value.spec
        val drawer = _appDrawer.value
        // The same helper the drawer's own layout uses, so controller paging can
        // never disagree with how many pages were actually drawn.
        val pageCount = pageCountFor(drawer.apps.size, spec.cellsPerPage)

        when (command) {
            ControllerCommand.NAVIGATE_UP -> _appDrawer.update {
                it.copy(cursor = it.cursor.copy(row = (it.cursor.row - 1).coerceAtLeast(0)))
            }

            ControllerCommand.NAVIGATE_DOWN -> _appDrawer.update {
                it.copy(
                    cursor = it.cursor.copy(
                        row = (it.cursor.row + 1).coerceAtMost(spec.rows - 1),
                    ),
                )
            }

            // Moving past a horizontal edge turns the page, matching the grid.
            ControllerCommand.NAVIGATE_LEFT -> _appDrawer.update {
                if (it.cursor.column > 0) {
                    it.copy(cursor = it.cursor.copy(column = it.cursor.column - 1))
                } else if (it.page > 0) {
                    it.copy(page = it.page - 1, cursor = it.cursor.copy(column = spec.columns - 1))
                } else {
                    it
                }
            }

            ControllerCommand.NAVIGATE_RIGHT -> _appDrawer.update {
                if (it.cursor.column < spec.columns - 1) {
                    it.copy(cursor = it.cursor.copy(column = it.cursor.column + 1))
                } else if (it.page < pageCount - 1) {
                    it.copy(page = it.page + 1, cursor = it.cursor.copy(column = 0))
                } else {
                    it
                }
            }

            ControllerCommand.PAGE_PREVIOUS -> setDrawerPage((drawer.page - 1).coerceAtLeast(0))
            ControllerCommand.PAGE_NEXT ->
                setDrawerPage((drawer.page + 1).coerceAtMost(pageCount - 1))

            ControllerCommand.CONFIRM -> confirmDrawerSelection()

            // Y raises the same menu as a long press, so the drawer's actions —
            // "add to grid" in particular — are reachable without touch.
            ControllerCommand.CONTEXT_MENU ->
                openDrawerContextMenu(drawer.cursor.row, drawer.cursor.column)

            ControllerCommand.TOGGLE_FAVORITE -> {
                val index = drawer.page * spec.cellsPerPage +
                    drawer.cursor.cellIndex(spec.columns)
                drawer.apps.getOrNull(index)?.let { app ->
                    viewModelScope.launchSafely(TAG) {
                        libraryRepository.setFavorite(app.id, !app.isFavorite)
                    }
                }
            }

            ControllerCommand.BACK,
            ControllerCommand.OPEN_APP_DRAWER,
            -> closeAppDrawer()

            else -> Unit
        }
    }

    /** Performs a context-menu action against the entry the menu was opened on. */
    fun performContextAction(action: ContextAction) {
        val entry = uiState.value.contextMenuEntry ?: return
        when (action) {
            ContextAction.LAUNCH -> {
                closeContextMenu()
                launchEntry(entry)
            }

            ContextAction.LAUNCH_MAIN_SCREEN ->
                launchEntryOn(entry, LaunchTarget.MAIN_SCREEN, explicit = true)

            ContextAction.LAUNCH_SECOND_SCREEN ->
                launchEntryOn(entry, LaunchTarget.SECOND_SCREEN, explicit = true)

            // Cycles rather than opening a picker; the message says where it landed.
            ContextAction.ALWAYS_ON_PANEL -> setPreferredPanel(
                entryId = entry.id,
                panel = (launchPanels.value[entry.id] ?: PreferredPanel.DEFAULT).next,
            )

            ContextAction.ADD_TO_GRID -> addToGrid(entry)

            ContextAction.REMOVE_FROM_GRID -> removeFromGrid(entry)

            ContextAction.CHOOSE_MATCH -> openMatchPicker(entry as? GameEntry)

            ContextAction.RESIZE_WIDGET -> beginWidgetResize(entry as? WidgetEntry)

            ContextAction.REMOVE_WIDGET -> (entry as? WidgetEntry)?.let(::removeWidget)

            ContextAction.MOVE_TO_FOLDER -> openFolderPicker(entry)

            ContextAction.REMOVE_FROM_FOLDER -> removeFromFolder(entry)

            ContextAction.EDIT -> openEditor(entry)
            ContextAction.NOTE -> openNoteEditor(entry)

            ContextAction.APP_INFO -> openAppInfo(entry)

            ContextAction.TOGGLE_FAVORITE -> {
                viewModelScope.launchSafely(TAG) {
                    libraryRepository.setFavorite(entry.id, !entry.isFavorite)
                    closeContextMenu()
                }
            }

            ContextAction.HIDE -> setEntryHidden(entry, hidden = true)

            ContextAction.UNHIDE -> setEntryHidden(entry, hidden = false)

            ContextAction.DELETE -> deleteEntry(entry)

            ContextAction.UNINSTALL -> uninstall(entry)

            ContextAction.SET_GAME_COVER, ContextAction.SET_GAME_BACKDROP -> {
                closeContextMenu()
                emit(
                    LauncherEffect.PickGameArtwork(
                        gameId = entry.id,
                        hero = action == ContextAction.SET_GAME_BACKDROP,
                    ),
                )
            }

            ContextAction.CLEAR_GAME_ARTWORK -> {
                closeContextMenu()
                viewModelScope.launchSafely(TAG) {
                    libraryRepository.clearGameArtwork(entry.id)
                    // Said out loud because the cell goes blank until something
                    // refills it, which on its own reads as having broken the game.
                    emit(LauncherEffect.ShowMessage("Artwork reset. Rescrape to refill it"))
                }
            }

            ContextAction.SET_PLATFORM_ICON, ContextAction.SET_PLATFORM_HERO -> {
                val platformId = PlatformFolders.platformIdOf(entry.id)
                closeContextMenu()
                if (platformId != null) {
                    emit(
                        LauncherEffect.PickPlatformArtwork(
                            platformId = platformId,
                            hero = action == ContextAction.SET_PLATFORM_HERO,
                        ),
                    )
                }
            }

            ContextAction.CLEAR_PLATFORM_ARTWORK -> {
                val platformId = PlatformFolders.platformIdOf(entry.id)
                closeContextMenu()
                if (platformId != null) {
                    viewModelScope.launchSafely(TAG) {
                        libraryRepository.clearPlatformArtwork(platformId)
                        emit(LauncherEffect.ShowMessage("Artwork reset"))
                    }
                }
            }

            ContextAction.DELETE_FOLDER -> {
                closeContextMenu()
                deleteFolder(entry.id)
            }
        }
    }

    /**
     * Whether something was launched onto the secondary panel.
     *
     * The presentation on that panel has to be dismissed to let the launched app
     * be seen, but *only* then. Hiding it whenever the launcher merely loses
     * focus — a launch onto the primary panel, the notification shade, a
     * permission dialog — uncovers whatever the system has behind it on that
     * display, which is its own default launcher. That is the stray home screen
     * appearing on one panel.
     *
     * Cleared when the launcher is back in front, since at that point the
     * secondary panel is ours again.
     *
     * Declared with the other state holders at the top of the class rather than
     * here beside its only writer: `init` calls [settlePlaytime], which clears
     * this, and a property declared below an `init` block does not exist yet when
     * that block runs — which threw the moment the view model was constructed.
     */

    /**
     * Launches an entry on a specific panel.
     *
     * @param explicit whether the panel was chosen by the user rather than
     *   derived from where the grid happens to be. Only an explicit choice is
     *   worth reporting when it cannot be honoured: on hardware whose second
     *   panel does not accept activity starts — which is most of it, the panel
     *   has to be a public display and usually is not — the derived target is
     *   refused on *every* launch, and announcing that every time is a notice
     *   about the device's wiring shown to someone who pressed A on a game.
     */
    fun launchEntryOn(entry: GridEntry, target: LaunchTarget, explicit: Boolean = false) {
        /*
         * A controller Confirm can coincide with a touch callback in one frame.
         * Two start requests make one task steal focus from the other and look
         * exactly like the app opened behind a frozen grid.
         *
         * Said out loud when it happens, because the failure it produces is
         * indistinguishable from a dead button: a launch that never finished
         * leaves this job alive, and every press afterwards returns here in
         * silence. "Nothing happens when I press A" has no other symptom, and
         * with nothing logged there was no way to tell it from a press that was
         * never received.
         */
        if (launchJob?.isActive == true) {
            ThorLog.w(TAG, "Ignoring launch of ${entry.id}: one is already in flight")
            viewModelScope.launchSafely(TAG) {
                emit(LauncherEffect.ShowMessage("Still opening the last one…"))
            }
            return
        }

        launchJob = viewModelScope.launchSafely(TAG) {
            try {
            /*
             * The app is started *before* the panel is handed over, and this
             * ordering is a permission requirement rather than a preference.
             *
             * Android only lets an app place an activity on a secondary display
             * when that display is public, or when the app already has a window
             * on it. THOR's presentation is that window. Standing it down first —
             * which is what this did, to give the app an empty display to arrive
             * on — removed the only claim THOR had to the panel, and the launch
             * that followed was refused outright: "Android would not let THOR
             * open that app on this screen", every time, on a panel the launcher
             * had been drawing on a moment earlier.
             *
             * So the window stays up until the start call has been accepted, and
             * comes down immediately after. The app does arrive underneath it for
             * the moment in between, which is the fault the old ordering existed
             * to avoid — but a frame of the grid over an app that is starting is
             * a far smaller thing than an app that cannot start at all.
             *
             * See https://source.android.com/docs/core/display/multi_display/activity-launch
             */
            /*
             * Only one thing can rule the second panel out in advance, and it is
             * not a permission check.
             *
             * This asked `isActivityStartAllowedOnDisplay` first and downgraded
             * the target on a "no" — but that question is about a *pinned* start,
             * and it was being asked while the presentation was still up and
             * before the panel's own activity had been given a chance to exist.
             * So an explicit "open on the bottom screen" could be turned into
             * "opened on the top screen" before anything had been attempted, on
             * the strength of an answer about a route this launch was not going
             * to take. That is the intermittency: the check's answer depended on
             * timing the user had no way to see.
             *
             * The launch is simply attempted now. If the second panel genuinely
             * will not take it, `startFirstThatOpens` drops the display pin and
             * opens it on the main panel — a real refusal rather than a predicted
             * one — and the caller is told where it actually landed.
             *
             * A missing second panel is the exception, because that is a fact
             * rather than a prediction.
             */
            val effectiveTarget = if (
                target == LaunchTarget.SECOND_SCREEN && !entryLauncher.hasSecondaryDisplay()
            ) {
                ThorLog.i(TAG, "No second panel attached; using the main one")
                if (explicit) {
                    emit(LauncherEffect.ShowMessage("No second screen — opened on this one"))
                }
                LaunchTarget.MAIN_SCREEN
            } else {
                target
            }

            /*
             * The panel comes down *before* the launch when it safely can, and
             * that is the difference between an app arriving on the second screen
             * and arriving behind it.
             *
             * A `Presentation` sits above application windows on its display, so
             * starting an app while it is still up puts the app underneath a
             * window that has not gone yet — which is exactly "it opened in the
             * background". This code knew that and could not act on it: standing
             * the panel down first removed THOR's only claim to the display, and
             * the *pinned* launch that followed was refused outright.
             *
             * The direct route has a different claim. It starts from
             * `SecondaryHomeActivity`, which is an activity actually on that
             * display and stays there whether or not the presentation is up — so
             * for that route the panel can come down first, with nothing lost.
             * `awaitSecondaryPresentationDismissal` then waits for the teardown to
             * actually happen rather than assuming a fixed delay was enough; it
             * has existed unused since it was written.
             *
             * The pinned route keeps the old ordering, because for it the
             * presentation *is* the claim.
             */
            val handOverFirst = effectiveTarget == LaunchTarget.SECOND_SCREEN
            if (handOverFirst) {
                setPanelOccupant(entry.id)
                if (!awaitSecondaryPresentationDismissal()) {
                    ThorLog.w(TAG, "Second panel did not stand down; launching over it")
                }

                /*
                 * Then wait for the activity the launch will be started from.
                 *
                 * Standing the presentation down is what uncovers
                 * `SecondaryHomeActivity` — and what prompts the system to
                 * recreate it if it had been reclaimed, which it is free to do at
                 * any time. Checking once and giving up made the second screen
                 * work or not depending on whether that had happened to occur,
                 * which is the whole of "sometimes it opens on the bottom screen
                 * and sometimes it does not".
                 *
                 * A timeout rather than a wait without end: if nothing appears,
                 * the launch still goes ahead by the pinned route and falls back
                 * to the near panel rather than not happening at all.
                 */
                if (!entryLauncher.awaitSecondPanelHost(SECOND_PANEL_HOST_TIMEOUT_MS)) {
                    ThorLog.w(TAG, "No activity on the second panel to launch from")
                }
            }

            val result = when (entry) {
                is AppEntry -> entryLauncher.launchApp(entry, effectiveTarget)

                is GameEntry -> {
                    val platform = uiState.value.platformsById[entry.platformId]
                    entryLauncher.launchGame(
                        game = entry,
                        platformDefaultEmulator = platform?.defaultEmulatorPackage,
                        target = effectiveTarget,
                    )
                }

                is FolderEntry -> {
                    setPanelOccupant(null)
                    openFolder(entry.id)
                    closeContextMenu()
                    return@launchSafely
                }

                // Shortcuts carry a launcher action rather than a component, so
                // there is nothing for a display target to apply to.
                else -> {
                    setPanelOccupant(null)
                    closeContextMenu()
                    return@launchSafely
                }
            }

            /*
             * The panel comes back unless something is actually on it.
             *
             * Two ways that happens, and both used to be one: a launch that failed
             * outright, and a launch that succeeded somewhere else. The second is
             * new — an app the system refuses to place on the second panel now
             * opens on the default display rather than not opening at all, and if
             * the launcher went on believing the panel was occupied it would sit
             * blank, showing the secondary home behind a presentation that had
             * stood down for an app that never came.
             */
            val arrivedOnSecondPanel = effectiveTarget == LaunchTarget.SECOND_SCREEN &&
                (result as? LaunchResult.Success)?.onRequestedTarget == true

            // Stood down only now, and only for a launch that was actually
            // accepted onto that panel — see the note above the start call.
            setPanelOccupant(entry.id.takeIf { arrivedOnSecondPanel })
            if (result is LaunchResult.Success) {
                /*
                 * A game is now running, wherever it landed.
                 *
                 * Not gated on `arrivedOnSecondPanel`, which is the mistake that
                 * made the companion panel never appear: the ordinary case is a
                 * game opening on the top screen while the launcher keeps the
                 * bottom one, and that is *not* the second panel being occupied.
                 * Games only, because an app is not something you play and has no
                 * session worth counting.
                 */
                if (entry is GameEntry) setRunningEntry(entry.id)

                // Reports where the app *landed*, not where it was aimed, so the
                // shell yields focus for the panel actually being taken.
                emit(LauncherEffect.Launched(onSecondaryPanel = arrivedOnSecondPanel))

                /*
                 * Said only when the user asked for the panel by name.
                 *
                 * An explicit "open on the bottom screen" that quietly opened on
                 * the top one is the launcher ignoring an instruction, and the
                 * two screens look similar enough from a glance that it is worth
                 * saying which happened. The automatic case says nothing —
                 * everything goes to the main panel by default now, so there is
                 * nothing to report.
                 */
                if (explicit && effectiveTarget == LaunchTarget.SECOND_SCREEN &&
                    !arrivedOnSecondPanel
                ) {
                    emit(
                        LauncherEffect.ShowMessage(
                            "That screen would not take it — opened on the other one",
                        ),
                    )
                }
            }
            handleResult(result, entry.id)
            closeContextMenu()
            } finally {
                launchJob = null
            }
        }
    }

    /**
     * Waits for the actual Presentation teardown before launching onto its display.
     * The former fixed delay was enough on some ROMs and too short on others,
     * leaving a valid app window alive underneath the grid.
     */
    private suspend fun awaitSecondaryPresentationDismissal(): Boolean {
        if (!secondaryPresentationVisible.value) return true
        return withTimeoutOrNull(PRESENTATION_HANDOVER_TIMEOUT_MS) {
            secondaryPresentationVisible.filter { visible -> !visible }.first()
        } != null
    }

    /** Opens the system application details page. */
    fun openAppInfo(entry: GridEntry) {
        val app = entry as? AppEntry ?: return
        entryLauncher.openAppInfo(app)
        closeContextMenu()
    }

    /** Asks the system to uninstall an app. */
    fun uninstall(entry: GridEntry) {
        val app = entry as? AppEntry ?: return
        entryLauncher.requestUninstall(app.packageName)
        closeContextMenu()
    }

    fun setEntryHidden(entry: GridEntry, hidden: Boolean) {
        viewModelScope.launchSafely(TAG) {
            libraryRepository.setHidden(entry.id, hidden)
            closeContextMenu()
        }
    }

    /**
     * Removes an entry from the library.
     *
     * Its placement goes too. A placement carries no foreign key — that is what
     * lets one point at an app, a game, a folder or a shortcut — so nothing would
     * clean it up on its own, and the cell would stay occupied by an entry that no
     * longer exists.
     */
    fun deleteEntry(entry: GridEntry) {
        viewModelScope.launchSafely(TAG) {
            closeContextMenu()
            gridRepository.removePlacement(entry.id)
            libraryRepository.deleteEntry(entry.id)
            /*
             * The note and the screenshots go with it, and only here.
             *
             * This is the one route that means "I do not want this game": a rescan
             * that cannot find a ROM must never reach it, because a moved file is
             * exactly the case where the note about where you got to is the last
             * record left. That is also why `game_notes` has no foreign key onto
             * `games` — the cascade would have made the rescan do this silently.
             */
            journalRepository.forget(entry.id)
            emit(LauncherEffect.ShowMessage("Removed ${entry.title} from the library"))
        }
    }

    // --------------------------------------------------------------- editing

    fun openEditor(entry: GridEntry) {
        closeContextMenu()
        editingEntryId.value = entry.id
    }

    fun closeEditor() {
        editingEntryId.value = null
    }

    /**
     * Persists the values from the edit dialog.
     *
     * Guarded, because this writes to five tables' worth of state from user
     * input: a constraint violation or a bad URI here used to propagate out of
     * the coroutine and force-close the launcher, losing the whole shell over a
     * failed rename.
     */
    fun applyEdits(entryId: String, edits: EntryEdits) {
        viewModelScope.launchSafely(
            tag = TAG,
            onError = { emit(LauncherEffect.ShowMessage("Could not save changes")) },
        ) {
            libraryRepository.rename(entryId, edits.title)

            // Metadata before artwork: `setCustomIcon` reads the row back and
            // folds the icon into the artwork set, so writing the dialog's
            // metadata snapshot afterwards would overwrite the icon that had
            // just been saved with the stale value the dialog started from.
            edits.metadata?.let { libraryRepository.updateGameMetadata(entryId, it) }
            libraryRepository.setCustomIcon(entryId, edits.customIconUri)

            edits.platformId?.let { libraryRepository.setGamePlatform(entryId, it) }
            // A null package is ambiguous on its own — it means both "unchanged"
            // and "cleared" — so the dialog reports the clear explicitly.
            if (edits.clearEmulator || edits.emulatorPackage != null) {
                libraryRepository.setGameEmulator(entryId, edits.emulatorPackage)
            }
            editingEntryId.value = null
        }
    }

    /**
     * Systems the user has added, for the editor's platform picker.
     *
     * Only added systems: offering to reassign a game to a console the user has
     * not set up would leave it with no emulator and no way to launch.
     */
    val addedPlatforms: StateFlow<List<Platform>> = libraryRepository.addedPlatforms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * Installed emulators offered by the editor's executable picker.
     *
     * Resolved against the selected game's platform, so the list only contains
     * things that can actually run it.
     */
    fun emulatorOptionsFor(entry: GridEntry?): List<EmulatorOption> {
        val game = entry as? GameEntry ?: return emptyList()
        val installed = entryLauncher.installedEmulatorsFor(game.platformId)
        val apps = uiState.value.entriesById.values.filterIsInstance<AppEntry>()
        return installed.map { packageName ->
            EmulatorOption(
                packageName = packageName,
                // The installed app's own name where we have it; the package
                // name is a poor label but better than an empty row.
                label = apps.firstOrNull { it.packageName == packageName }?.title ?: packageName,
            )
        }
    }

    // --------------------------------------------------------------- folder

    /**
     * Where the cursor was before a folder was opened.
     *
     * A folder takes the grid over, so entering one has to start at its first entry
     * and leaving one has to put the user back on the folder they opened — not on
     * whichever cell of the home grid happens to share the coordinates they left the
     * folder at.
     */
    private var folderReturn: Pair<Int, CursorPosition>? = null

    fun openFolder(folderId: String) {
        if (openFolderId.value == folderId) return
        folderReturn = currentPage.value to cursor.value
        openFolderId.value = folderId
        currentPage.value = 0
        cursor.value = CursorPosition(0, 0)
    }

    fun closeFolder() {
        if (openFolderId.value == null) return
        openFolderId.value = null
        folderReturn?.let { (page, position) ->
            currentPage.value = page
            cursor.value = position
        }
        folderReturn = null
    }

    /**
     * Creates an empty folder and drops it on the grid.
     *
     * Backs the Start panel's "New". The folder is placed like any other new
     * entry — first free cell in reading order — and immediately enters
     * arrange mode so it can be moved without a second trip through the menu.
     */
    fun createFolder(title: String = "New folder") {
        viewModelScope.launchSafely(TAG) {
            val folderId = gridRepository.createEmptyFolder(title)
            gridRepository.placeUnplacedEntries(listOf(folderId))
            editMode.value = EditMode.Arranging
        }
    }

    fun renameFolder(folderId: String, title: String) {
        viewModelScope.launchSafely(TAG) { gridRepository.renameFolder(folderId, title) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launchSafely(TAG) {
            gridRepository.deleteFolder(folderId)
            closeFolder()
        }
    }

    // ----------------------------------------------------------------- menu

    fun toggleSideMenu() {
        sideMenuIndex.value = 0
        sideMenuOpen.value = !sideMenuOpen.value
    }

    /** Opens Couch Home's platform drawer with its cursor on the active platform. */
    private fun toggleCouchPlatformMenu() {
        sideMenuIndex.value = _couchPlatformIndex.value
        sideMenuOpen.value = !sideMenuOpen.value
    }

    fun closeSideMenu() {
        sideMenuOpen.value = false
    }

    fun focusSideMenuRow(index: Int) {
        sideMenuIndex.value = index.coerceIn(0, SideMenuAction.entries.lastIndex)
    }

    /**
     * Navigation while the Start panel holds input.
     *
     * Selecting a row emits it through [sideMenuSelections] rather than acting
     * here, because two of the four actions open a settings category that only
     * the host composable knows how to route to.
     */
    private fun onSideMenuCommand(command: ControllerCommand, couchMode: Boolean) {
        if (couchMode && _selectedTab.value.isHome) {
            val platforms = availableCouchPlatforms(uiState.value)
            if (platforms.isEmpty()) {
                if (command == ControllerCommand.BACK ||
                    command == ControllerCommand.OPEN_SIDE_MENU
                ) {
                    closeSideMenu()
                }
                return
            }

            sideMenuIndex.value = sideMenuIndex.value.coerceIn(0, platforms.lastIndex)
            when (command) {
                ControllerCommand.NAVIGATE_UP,
                ControllerCommand.PAGE_PREVIOUS,
                -> sideMenuIndex.value =
                    (sideMenuIndex.value - 1 + platforms.size) % platforms.size

                ControllerCommand.NAVIGATE_DOWN,
                ControllerCommand.PAGE_NEXT,
                -> sideMenuIndex.value = (sideMenuIndex.value + 1) % platforms.size

                ControllerCommand.CONFIRM -> {
                    selectCouchPlatform(sideMenuIndex.value)
                    closeSideMenu()
                }

                ControllerCommand.BACK,
                ControllerCommand.OPEN_SIDE_MENU,
                -> closeSideMenu()

                else -> Unit
            }
            return
        }

        val actions = SideMenuAction.entries
        when (command) {
            ControllerCommand.NAVIGATE_UP ->
                sideMenuIndex.value = (sideMenuIndex.value - 1 + actions.size) % actions.size

            ControllerCommand.NAVIGATE_DOWN ->
                sideMenuIndex.value = (sideMenuIndex.value + 1) % actions.size

            ControllerCommand.CONFIRM ->
                actions.getOrNull(sideMenuIndex.value)?.let(::selectSideMenuAction)

            ControllerCommand.BACK,
            ControllerCommand.OPEN_SIDE_MENU,
            -> closeSideMenu()

            else -> Unit
        }
    }

    private val _sideMenuSelections = Channel<SideMenuAction>(Channel.BUFFERED)

    /** Emitted when a Start panel row is chosen, by pad or by touch. */
    val sideMenuSelections: Flow<SideMenuAction> = _sideMenuSelections.receiveAsFlow()

    fun selectSideMenuAction(action: SideMenuAction) {
        _sideMenuSelections.trySend(action)
    }

    /** Performs a dock or side-menu action. */
    fun performAction(action: LauncherAction) {
        when (action) {
            LauncherAction.OpenSettings -> emit(LauncherEffect.OpenSettings)
            LauncherAction.OpenSearch -> emit(LauncherEffect.OpenSearch)

            /*
             * The dock keyboard slot: up, or away.
             *
             * A keyboard needs a field to type into, and the launcher's only always-
             * available one is search — so raising it from the dock opens search with
             * it. That screen now shows nothing until something is typed, which is what
             * made this unwelcome before: an empty query matches the whole library, so
             * asking for a keyboard threw a list of every app onto the other panel.
             */
            LauncherAction.ToggleKeyboard -> if (_keyboard.value.visible) {
                closeKeyboard()
            } else {
                emit(LauncherEffect.OpenSearch)
            }
            LauncherAction.OpenAppDrawer -> openAppDrawer()
            LauncherAction.OpenSideMenu -> toggleSideMenu()
            LauncherAction.GoHome -> goHome()
            LauncherAction.EditGrid -> enterArrangeMode()
            LauncherAction.ScanLibrary -> scanLibrary()
            LauncherAction.OpenPowerMenu -> emit(LauncherEffect.OpenPowerMenu)
            is LauncherAction.OpenFolder -> openFolder(action.folderId)
            is LauncherAction.LaunchEntry -> {
                uiState.value.entriesById[action.entryId]?.let(::launchEntry)
            }

            else -> ThorLog.d(TAG) { "Action $action is handled by the host activity" }
        }
        sideMenuOpen.value = false
    }

    fun scanLibrary() {
        syncManager.requestFullScan()
    }

    fun addPage() {
        viewModelScope.launchSafely(TAG) { gridRepository.addPage() }
    }

    fun updateGridSpec(transform: (GridSpec) -> GridSpec) {
        viewModelScope.launchSafely(TAG) { settingsRepository.updateGrid(transform) }
    }

    // ---------------------------------------------------------- match picker

    private val _matchPicker = MutableStateFlow(MatchPickerState())
    val matchPicker: StateFlow<MatchPickerState> = _matchPicker.asStateFlow()

    /**
     * Asks every provider about a game and shows what they say.
     *
     * Opened with the list still null so the card appears at once and fills in,
     * rather than the press doing nothing for as long as the slowest provider
     * takes. See [MatchPickerState.candidates].
     */
    fun openMatchPicker(entry: GameEntry? = uiState.value.selection as? GameEntry) {
        val game = entry ?: return
        closeContextMenu()
        _matchPicker.value = MatchPickerState(
            visible = true,
            entryId = game.id,
            entryTitle = game.title,
            candidates = null,
        )

        viewModelScope.launchSafely(TAG) {
            val found = libraryRepository.matchCandidatesFor(game.id)
            // Only if it is still the same card on screen: this is several
            // network calls, and the user is free to dismiss it or move on.
            if (_matchPicker.value.entryId == game.id) {
                _matchPicker.update { it.copy(candidates = found, focusedIndex = 0) }
            }
        }
    }

    fun closeMatchPicker() {
        _matchPicker.value = MatchPickerState()
    }

    fun focusMatchRow(index: Int) {
        _matchPicker.update { picker ->
            picker.copy(focusedIndex = index.coerceIn(0, (picker.rowCount - 1).coerceAtLeast(0)))
        }
    }

    /** Applies the chosen match and locks it against the next scrape. */
    fun pickMatch(candidate: MetadataCandidate) {
        val entryId = _matchPicker.value.entryId ?: return
        val title = _matchPicker.value.entryTitle
        closeMatchPicker()
        viewModelScope.launchSafely(TAG) {
            libraryRepository.applyChosenMatch(entryId, candidate)
            emit(LauncherEffect.ShowMessage("$title is now ${candidate.matchedTitle}"))
        }
    }

    private fun onMatchPickerCommand(command: ControllerCommand) {
        val picker = _matchPicker.value
        val count = picker.rowCount
        when (command) {
            ControllerCommand.NAVIGATE_UP -> if (count > 0) {
                focusMatchRow((picker.focusedIndex - 1 + count) % count)
            }

            ControllerCommand.NAVIGATE_DOWN -> if (count > 0) {
                focusMatchRow((picker.focusedIndex + 1) % count)
            }

            ControllerCommand.CONFIRM ->
                picker.candidates?.getOrNull(picker.focusedIndex)?.let(::pickMatch)

            ControllerCommand.BACK, ControllerCommand.CONTEXT_MENU -> closeMatchPicker()
            else -> Unit
        }
    }

    // --------------------------------------------------------------- widgets

    private val _cellMenu = MutableStateFlow(CellMenuState())
    val cellMenu: StateFlow<CellMenuState> = _cellMenu.asStateFlow()

    private val _widgetPicker = MutableStateFlow(WidgetPickerState())
    val widgetPicker: StateFlow<WidgetPickerState> = _widgetPicker.asStateFlow()

    /**
     * A widget between being chosen and being placed.
     *
     * Held outside the picker's state because it outlives it: the picker closes
     * the moment something is chosen, and what follows is a consent dialog and
     * possibly the provider's own setup screen, each of which leaves the launcher
     * and comes back. The id in here is allocated and unstored for that whole
     * stretch, so every path out of it has to either finish or discard.
     */
    private var pendingWidget: PendingWidget? = null

    private data class PendingWidget(
        val appWidgetId: Int,
        val option: WidgetOption,
        val page: Int,
        val row: Int,
        val column: Int,
    )

    /**
     * A long press on the grid, which now has two answers.
     *
     * On something, the entry's menu, as before. On nothing, the cell's own menu
     * — which is new, and is the whole reason this is a method rather than the
     * shell calling [openContextMenu] directly: the shell cannot tell an empty
     * cell from a full one without knowing about widget footprints.
     */
    fun onCellLongPressed(row: Int, column: Int) {
        setCursor(row, column)
        val state = uiState.value

        // The cursor flow has moved but `uiState` has not caught up — it is a
        // combine, so it settles a frame later — which is why the cell is
        // resolved from the coordinates rather than read off `selection`.
        val entry = state.entryAt(state.currentPage, row, column)
        when {
            entry != null -> openContextMenu(entry)
            // A folder shows a list rather than an arrangement, so there is no
            // "this cell" to act on, and couch mode has no long press at all.
            state.isFolderOpen || couchMode.value -> Unit
            else -> openCellMenu(row, column)
        }
    }

    fun openCellMenu(row: Int, column: Int) {
        _cellMenu.value = CellMenuState(
            visible = true,
            page = uiState.value.currentPage,
            row = row,
            column = column,
            focusedIndex = 0,
        )
    }

    fun closeCellMenu() {
        _cellMenu.update { it.copy(visible = false, focusedIndex = 0) }
    }

    fun focusCellMenuRow(index: Int) {
        _cellMenu.update { it.copy(focusedIndex = index.coerceIn(CELL_ACTIONS.indices)) }
    }

    fun performCellAction(action: CellAction) {
        val cell = _cellMenu.value
        closeCellMenu()
        when (action) {
            CellAction.ADD_WIDGET -> openWidgetPicker()
            CellAction.ADD_APP -> openAppDrawer()
            CellAction.NEW_FOLDER -> viewModelScope.launchSafely(TAG) {
                val folderId = gridRepository.createEmptyFolder("New folder")
                // Into the cell the user pressed. The folder is created placed
                // somewhere already, so this moves it rather than adding it.
                gridRepository.moveEntry(folderId, cell.page, cell.row, cell.column)
            }

            CellAction.ARRANGE -> enterArrangeMode()
            CellAction.ADD_PAGE -> addPage()
        }
    }

    private fun onCellMenuCommand(command: ControllerCommand) {
        val count = CELL_ACTIONS.size
        val index = _cellMenu.value.focusedIndex
        when (command) {
            ControllerCommand.NAVIGATE_LEFT ->
                focusCellMenuRow(stepContextMenuColumn(index, -1, count))

            ControllerCommand.NAVIGATE_RIGHT ->
                focusCellMenuRow(stepContextMenuColumn(index, 1, count))

            ControllerCommand.NAVIGATE_UP -> focusCellMenuRow(stepContextMenuRow(index, -1, count))
            ControllerCommand.NAVIGATE_DOWN -> focusCellMenuRow(stepContextMenuRow(index, 1, count))
            ControllerCommand.CONFIRM ->
                CELL_ACTIONS.getOrNull(index)?.let(::performCellAction)

            ControllerCommand.BACK, ControllerCommand.CONTEXT_MENU -> closeCellMenu()
            else -> Unit
        }
    }

    // ------------------------------------------------------ the widget picker

    fun openWidgetPicker() {
        // The launcher's own are known without asking anything, so the picker
        // opens with them already listed rather than on a spinner.
        _widgetPicker.value = WidgetPickerState(visible = true, appOptions = null)
        viewModelScope.launchSafely(TAG) {
            val options = widgetRepository.options()
            // Only if the picker is still the thing on screen: listing providers
            // is package-manager work across a binder, and the user is free to
            // dismiss it while that runs.
            if (_widgetPicker.value.visible) {
                _widgetPicker.update { it.copy(appOptions = options) }
            }
        }
    }

    fun closeWidgetPicker() {
        _widgetPicker.value = WidgetPickerState()
    }

    fun focusWidgetPickerRow(index: Int) {
        _widgetPicker.update { picker ->
            picker.copy(focusedIndex = index.coerceIn(0, (picker.rowCount - 1).coerceAtLeast(0)))
        }
    }

    /**
     * Takes an id for the chosen provider and starts down whichever path it needs.
     *
     * Three of them, and the widget is not stored until the end of any: bind
     * silently then configure, ask to bind and then configure, or place directly.
     * See [PendingWidget] for why the middle of that is the dangerous part.
     */
    fun chooseWidget(choice: WidgetChoice) {
        when (choice) {
            is WidgetChoice.BuiltIn -> placeBuiltInWidget(choice.widget)
            is WidgetChoice.App -> beginAppWidget(choice.option)
        }
    }

    /**
     * One of the launcher's own, which is placed in a single step.
     *
     * None of the app-widget ceremony applies — there is no id to allocate from
     * the platform, no consent to ask for and no setup screen to run — so this
     * is a write and a placement rather than a state machine spanning two trips
     * out of the launcher.
     */
    private fun placeBuiltInWidget(widget: LauncherWidget) {
        val cell = _cellMenu.value
        closeWidgetPicker()

        viewModelScope.launchSafely(TAG) {
            val entryId = widgetRepository.placeBuiltIn(
                widget = widget,
                span = widget.span,
                nowEpochMs = System.currentTimeMillis(),
            )
            placeWidgetEntry(entryId, widget.span, cell.page, cell.row, cell.column)
        }
    }

    private fun beginAppWidget(option: WidgetOption) {
        val cell = _cellMenu.value
        val component = ComponentName.unflattenFromString(option.component) ?: return
        closeWidgetPicker()

        viewModelScope.launchSafely(TAG) {
            val request = widgetRepository.beginPlacement(component)
            pendingWidget = PendingWidget(
                appWidgetId = request.appWidgetId,
                option = option,
                page = cell.page,
                row = cell.row,
                column = cell.column,
            )
            if (request.bound) continueWidgetPlacement() else emit(LauncherEffect.RequestWidgetBind)
        }
    }

    /**
     * Gives a stored widget its cell.
     *
     * The cell the menu was raised on, covered rather than anchored — a widget
     * three cells wide chosen from the last column slides left so it still sits
     * under the cell that was pressed. That is what the press meant, and treating
     * it as the widget's top-left corner instead is what made adding one report
     * no room on a page that was mostly empty: the cells a user long-presses to
     * add a widget are the free ones at the end of the grid, and those are exactly
     * the cells a large widget can never be hung from. See
     * [com.thor.core.model.GridFootprint.anchorCovering].
     *
     * The first free space on any page remains the fallback, and it is now a real
     * one — reached only when the page has no room for the widget anywhere, rather
     * than on nearly every attempt.
     */
    private suspend fun placeWidgetEntry(
        entryId: String,
        span: CellSpan,
        page: Int,
        row: Int,
        column: Int,
    ) {
        if (gridRepository.placeEntryCovering(entryId, page, row, column)) return
        val slot = gridRepository.firstFreeCellFor(span)
        gridRepository.placeEntryAt(entryId, slot.pageIndex, slot.row, slot.column)
        emit(LauncherEffect.ShowMessage("No room on this page, so it went to the first space"))
    }

    /** The consent dialog for the widget waiting to be placed. */
    fun widgetBindIntent(): Intent? = pendingWidget?.let { pending ->
        ComponentName.unflattenFromString(pending.option.component)?.let { component ->
            widgetRepository.bindIntent(pending.appWidgetId, component)
        }
    }

    fun widgetConfigureIntent(): Intent? =
        pendingWidget?.let { widgetRepository.configureIntent(it.appWidgetId) }

    fun onWidgetBindResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launchSafely(TAG) { continueWidgetPlacement() }
        } else {
            discardPendingWidget("Loki was not allowed to add that widget")
        }
    }

    fun onWidgetConfigured(completed: Boolean) {
        if (completed) {
            viewModelScope.launchSafely(TAG) { finishWidgetPlacement() }
        } else {
            // Cancelled at the provider's own screen, which is a decision not to
            // add it — placing it anyway would leave a widget nobody set up.
            discardPendingWidget(null)
        }
    }

    /** Runs the provider's setup screen if it has one, and places it if not. */
    private suspend fun continueWidgetPlacement() {
        val pending = pendingWidget ?: return
        if (widgetRepository.configureIntent(pending.appWidgetId) != null) {
            emit(LauncherEffect.ConfigureWidget)
        } else {
            finishWidgetPlacement()
        }
    }

    /** Stores the app widget the user finished setting up, and places it. */
    private suspend fun finishWidgetPlacement() {
        val pending = pendingWidget ?: return
        pendingWidget = null

        val component = ComponentName.unflattenFromString(pending.option.component) ?: return
        val span = CellSpan(pending.option.spanColumns, pending.option.spanRows)
        val entryId = widgetRepository.place(
            appWidgetId = pending.appWidgetId,
            provider = component,
            label = pending.option.label,
            span = span,
            nowEpochMs = System.currentTimeMillis(),
        )

        placeWidgetEntry(entryId, span, pending.page, pending.row, pending.column)
    }

    private fun discardPendingWidget(message: String?) {
        val pending = pendingWidget ?: return
        pendingWidget = null
        widgetRepository.discard(pending.appWidgetId)
        message?.let { emit(LauncherEffect.ShowMessage(it)) }
    }

    private fun onWidgetPickerCommand(command: ControllerCommand) {
        val picker = _widgetPicker.value
        val count = picker.rowCount
        when (command) {
            ControllerCommand.NAVIGATE_UP -> if (count > 0) {
                focusWidgetPickerRow((picker.focusedIndex - 1 + count) % count)
            }

            ControllerCommand.NAVIGATE_DOWN -> if (count > 0) {
                focusWidgetPickerRow((picker.focusedIndex + 1) % count)
            }

            ControllerCommand.CONFIRM ->
                picker.choiceAt(picker.focusedIndex)?.let(::chooseWidget)

            ControllerCommand.BACK, ControllerCommand.CONTEXT_MENU -> closeWidgetPicker()
            else -> Unit
        }
    }

    // -------------------------------------------------------- placed widgets

    /**
     * Inflates a placed widget for whichever panel is drawing it.
     *
     * The context is the caller's rather than the application's: the grid is
     * drawn inside a `Presentation` on the second display, and a view built
     * against the wrong context resolves its size and density against the wrong
     * screen.
     */
    fun createWidgetView(context: Context, appWidgetId: Int): View? =
        widgetRepository.createView(context, appWidgetId)

    fun onWidgetMeasured(appWidgetId: Int, widthDp: Int, heightDp: Int) =
        widgetRepository.notifySize(appWidgetId, widthDp, heightDp)

    /**
     * Starts and stops the host with the launcher's visibility.
     *
     * Not with its process: a host that goes on listening while a game is on the
     * panel pays for every clock tick and weather refresh nobody can see.
     */
    fun startWidgetHost() {
        widgetRepository.startListening()
        // Every abandoned picker and refused consent leaves an id allocated and
        // invisible; this is the only thing that ever finds them again.
        viewModelScope.launchSafely(TAG) { widgetRepository.reconcile() }
    }

    fun stopWidgetHost() = widgetRepository.stopListening()

    /** Forgets a widget: its cell, its row and its id, in that order. */
    fun removeWidget(entry: WidgetEntry) {
        viewModelScope.launchSafely(TAG) {
            gridRepository.removePlacement(entry.id)
            widgetRepository.remove(entry.appWidgetId)
            closeContextMenu()
            editMode.value = EditMode.None
        }
    }

    // ------------------------------------------------------------- resizing

    /**
     * Starts resizing a widget, which is a mode of its own.
     *
     * See [EditMode.Resizing]: the D-pad grows and shrinks an edge here rather
     * than moving the cursor, so the two cannot be the same mode.
     */
    fun beginWidgetResize(entry: WidgetEntry? = uiState.value.selection as? WidgetEntry) {
        val widget = entry ?: return
        closeContextMenu()
        closeFolder()
        editMode.value = EditMode.Resizing(
            entryId = widget.id,
            originColumns = widget.spanColumns,
            originRows = widget.spanRows,
        )
    }

    /**
     * Grows or shrinks the widget being resized by one cell in one direction.
     *
     * Refused rather than clamped when the new size would run off the page or
     * cover something else — the widget stays exactly as it was, and nothing is
     * silently displaced to make room for it.
     */
    fun stepWidgetResize(columns: Int, rows: Int) {
        val resizing = editMode.value as? EditMode.Resizing ?: return
        val state = uiState.value
        val widget = state.entriesById[resizing.entryId] as? WidgetEntry ?: return
        val placement = state.placements.firstOrNull { it.entryId == widget.id } ?: return

        val wanted = CellSpan(
            columns = (widget.spanColumns + columns)
                .coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN),
            rows = (widget.spanRows + rows).coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN),
        )
        if (wanted.columns == widget.spanColumns && wanted.rows == widget.spanRows) return

        val fits = GridFootprint.isFree(
            row = placement.row,
            column = placement.column,
            span = wanted,
            placements = state.placements,
            spans = state.widgetSpans,
            pageIndex = placement.pageIndex,
            spec = state.spec,
            ignoring = widget.id,
        )
        if (!fits) return

        viewModelScope.launchSafely(TAG) {
            widgetRepository.resize(widget.appWidgetId, wanted)
        }
    }

    /** Leaves resize mode, keeping the size the widget has reached. */
    fun finishWidgetResize() {
        if (editMode.value !is EditMode.Resizing) return
        editMode.value = EditMode.Arranging
    }

    /** Leaves resize mode and puts the widget back to the size it started at. */
    fun cancelWidgetResize() {
        val resizing = editMode.value as? EditMode.Resizing ?: return
        val widget = uiState.value.entriesById[resizing.entryId] as? WidgetEntry
        editMode.value = EditMode.Arranging
        if (widget == null) return
        if (widget.spanColumns == resizing.originColumns &&
            widget.spanRows == resizing.originRows
        ) {
            return
        }
        viewModelScope.launchSafely(TAG) {
            widgetRepository.resize(
                widget.appWidgetId,
                CellSpan(resizing.originColumns, resizing.originRows),
            )
        }
    }

    private fun onResizeCommand(command: ControllerCommand) {
        when (command) {
            ControllerCommand.NAVIGATE_LEFT -> stepWidgetResize(columns = -1, rows = 0)
            ControllerCommand.NAVIGATE_RIGHT -> stepWidgetResize(columns = 1, rows = 0)
            ControllerCommand.NAVIGATE_UP -> stepWidgetResize(columns = 0, rows = -1)
            ControllerCommand.NAVIGATE_DOWN -> stepWidgetResize(columns = 0, rows = 1)
            ControllerCommand.CONFIRM, ControllerCommand.PICK_UP -> finishWidgetResize()
            ControllerCommand.BACK, ControllerCommand.CANCEL_EDIT -> cancelWidgetResize()
            else -> Unit
        }
    }

    // ------------------------------------------------------------- internal

    private fun resolveSelection(
        layout: LayoutSnapshot,
        spec: GridSpec,
        interaction: InteractionSnapshot,
        /** Already resolved by the caller's memo; see [LayoutMemo]. */
        openFolderContents: List<GridEntry>,
        /** Also from the memo, for the same reason. */
        widgetSpans: Map<String, CellSpan>,
    ): GridEntry? {
        /*
         * Inside an open folder the cursor indexes the folder's contents rather than
         * the page grid — including the page, because a folder with more children
         * than a page holds turns pages of its own. Without the page term the cursor
         * selected the first page's entry no matter which page was on screen.
         */
        if (interaction.openFolderId != null) {
            val index = interaction.page * spec.cellsPerPage +
                interaction.cursor.cellIndex(spec.columns)
            return openFolderContents.getOrNull(index)
        }

        /*
         * Through the footprint, not by matching the stored cell.
         *
         * A widget's placement names its top-left cell and it stands on up to
         * sixteen. Matching the stored cell selected it from that one corner and
         * reported nothing from the rest of it, so the cursor crossed a 2x2 clock
         * and the top screen went blank for three of the four cells it covered.
         */
        val cell = interaction.cursor.cellIndex(spec.columns)
        val occupant = GridFootprint.occupants(
            placements = layout.placements,
            spans = widgetSpans,
            pageIndex = interaction.page,
            spec = spec,
        )[cell] ?: return null
        return layout.entries[occupant]
    }

    /**
     * The parts of the state that depend on the library rather than the cursor.
     *
     * [uiState] combines the layout with the interaction, so its transform runs
     * on *every cursor move* — and at auto-repeat rate that is many times a
     * second. Rebuilding the platform map, the dock slots and the open folder's
     * contents each time is work whose inputs have not changed: nothing about
     * moving a cursor alters which platforms exist. On a folder holding a few
     * hundred games that was a list rebuilt per keypress, on the main thread's
     * critical path, for a value identical to the one already held.
     *
     * Memoised on the identity of the snapshot the values came from, so the work
     * happens once per library change instead of once per press. Returning the
     * *same* instances also lets Compose skip: an unchanged `platformsById` no
     * longer looks like a new map to every reader of it.
     */
    private class DerivedLayout(
        val platformsById: Map<String, com.thor.core.model.Platform>,
        val dockEntryIds: List<String?>,
        val openFolderContents: List<GridEntry>,
        /**
         * How many cells each placed widget covers.
         *
         * Derived here rather than read on demand because the cursor needs it on
         * every move — a widget is selectable from any cell it stands on, not
         * only from the one its placement names — and that is the hottest path
         * in the launcher.
         */
        val widgetSpans: Map<String, CellSpan>,
    )

    private class LayoutMemo {
        private var layout: LayoutSnapshot? = null
        private var folderId: String? = null
        private var cached: DerivedLayout? = null

        fun of(layout: LayoutSnapshot, folderId: String?): DerivedLayout {
            val hit = cached
            // Reference equality on purpose: a new snapshot means the library
            // genuinely changed, and comparing these lists by value would cost
            // more than the work being avoided.
            if (hit != null && this.layout === layout && this.folderId == folderId) return hit

            val derived = DerivedLayout(
                platformsById = layout.platforms.associateBy { it.id },
                dockEntryIds = resolveDock(layout),
                openFolderContents = resolveFolderContents(layout, folderId),
                widgetSpans = layout.entries.values
                    .filterIsInstance<WidgetEntry>()
                    .associate { widget ->
                        widget.id to CellSpan(widget.spanColumns, widget.spanRows)
                    },
            )
            this.layout = layout
            this.folderId = folderId
            cached = derived
            return derived
        }

        private fun resolveFolderContents(
            layout: LayoutSnapshot,
            folderId: String?,
        ): List<GridEntry> {
            val folder = folderId?.let { layout.entries[it] } as? FolderEntry ?: return emptyList()
            /*
             * A smart folder has no children to look up — it *is* its query, and
             * storing what it matched would be the one thing it exists not to do.
             * Resolving from `childIds` regardless is why a smart folder could be
             * created, placed and opened and was always empty.
             *
             * Evaluated against the whole entry map rather than against what is
             * placed, because a game filed inside a platform folder is still a game
             * the query should see. Doing it here rather than in a suspending call
             * also means the contents follow the library for free: favourite a game
             * and it appears in a Favourites folder with nothing told to refresh.
             */
            folder.smartQuery?.let { query ->
                return SmartQueryEvaluator.evaluate(
                    entries = layout.entries.values,
                    query = query,
                    now = System.currentTimeMillis(),
                )
            }
            return folder.childIds.mapNotNull { layout.entries[it] }
        }

        private fun resolveDock(layout: LayoutSnapshot): List<String?> {
            val slots = arrayOfNulls<String>(DOCK_SLOTS)
            layout.dock.forEach { placement ->
                placement.column.takeIf { it in 0 until DOCK_SLOTS }?.let { slot ->
                    slots[slot] = placement.entryId
                }
            }
            return slots.toList()
        }
    }

    private fun emit(effect: LauncherEffect) {
        effects.trySend(effect)
    }

    private companion object {
        const val TAG = "Launcher"
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The display a screenshot is taken of.
         *
         * The default one, which on this device is the top screen — where a game
         * launched to the second panel actually runs. Not the panel the launcher
         * is drawing on, which would photograph the grid rather than the game.
         */
        const val CAPTURED_DISPLAY_ID = android.view.Display.DEFAULT_DISPLAY

        /**
         * How long the cursor has to rest before a game's achievements are
         * fetched. Long enough to cross a page without asking about every cell.
         */
        const val ACHIEVEMENT_REFRESH_DELAY_MS = 700L
        const val DOCK_SLOTS = 5
        /*
         * Two, and both of them act on the game.
         *
         * There was a Close on the end of this row and a More beside it. B
         * closes the page, as B closes everything in the launcher, so Close was
         * a stop on the cursor's way round that did what the user had already
         * been told to press. More opened the long-press menu, which couch mode
         * no longer raises — see [openContextMenu].
         */
        const val COUCH_DETAIL_PLAY = 0
        const val COUCH_DETAIL_FAVOURITE = 1
        const val COUCH_DETAIL_ACTION_COUNT = 2

        /** As much of a platform message as fits on a panel beside a sentence. */
        const val DETAIL_LIMIT = 120

        /**
         * A guard only; normal handoff proceeds the moment dismissal is
         * acknowledged, which is usually within a frame or two.
         *
         * It was one second, which is not a guard but a race. Tearing down a
         * window involves the window manager and the display, and on a loaded
         * handheld — mid-scrape, or with a game still winding down on the other
         * panel — a second is reachable. Every time it was, the launch was
         * abandoned outright and the app simply did not open, with no pattern the
         * user could see. Nothing waits this long in practice; it only has to be
         * longer than the worst case rather than the common one.
         */
        const val PRESENTATION_HANDOVER_TIMEOUT_MS = 4_000L

        /**
         * How long to wait for the second panel's activity once the panel has
         * been handed over.
         *
         * Short, because it is either already there or the system is recreating
         * it as that display's home the moment the presentation goes. Anything
         * longer would be a pause the user feels between pressing the row and the
         * app appearing.
         */
        const val SECOND_PANEL_HOST_TIMEOUT_MS = 1_500L

        /** Fallback capture size, used only until the shell reports the panels. */
        const val DEFAULT_CAPTURE_WIDTH = 1080
        const val DEFAULT_CAPTURE_HEIGHT = 1600
        const val DEFAULT_CAPTURE_DENSITY = 320
    }
}

// The library panel's row indices moved to CouchNavigation, which is where the
// panel that defines their order already keeps its own counts.
