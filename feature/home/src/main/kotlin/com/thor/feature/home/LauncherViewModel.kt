package com.thor.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ControllerCommand
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.GridSpec
import com.thor.core.model.KeyboardKey
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.ThorKeyboardLayout
import com.thor.core.model.AppEntry
import com.thor.core.model.LauncherAction
import com.thor.core.model.NavDirection
import com.thor.core.model.Platform
import com.thor.core.model.ShortcutAction
import com.thor.core.model.ShortcutGrid
import com.thor.core.model.ControlSettings
import com.thor.core.model.LauncherTab
import com.thor.core.model.SortOrder
import com.thor.data.capture.RecordingState
import com.thor.data.capture.ScreenRecorder
import com.thor.data.clipboard.ThorClipboard
import com.thor.data.launcher.EntryLauncher
import com.thor.data.launcher.LaunchTarget
import com.thor.data.launcher.SystemPanel
import com.thor.feature.home.component.ContextAction
import com.thor.feature.home.component.EmulatorOption
import com.thor.feature.home.component.EntryEdits
import com.thor.feature.home.component.FolderPickerState
import com.thor.feature.home.component.SideMenuAction
import com.thor.feature.home.component.contextActionsFor
import com.thor.data.launcher.LaunchFailure
import com.thor.data.launcher.LaunchResult
import com.thor.data.repository.GridLayoutRepository
import com.thor.data.repository.LibraryRepository
import com.thor.data.sync.LibrarySyncManager
import com.thor.data.sync.PlaytimeTracker
import com.thor.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

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
     * Takes the second panel back when a launch turned out to be a no-op.
     *
     * `startMainActivity` returning without throwing does not mean the app came
     * to the foreground: a ROM can queue it, refuse it silently, or bring it up
     * behind whatever is showing. The launcher had already handed the panel over
     * by then, so nothing was on screen, nothing had focus, and the controller
     * did nothing — the launcher looked frozen while the app ran in the
     * background, recoverable only by touching the panel or pressing Home.
     *
     * Called by the shell when it observes that nothing ever took the panel.
     */
    fun releaseSecondScreen() {
        if (!_secondScreenOccupied.value) return
        ThorLog.i(TAG, "Nothing took the second panel; taking it back")
        _secondScreenOccupied.value = false
        settlePlaytime()
    }

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
     * Null is the grid holding the cursor, which is only possible on Home — the
     * other sections have no cursor target of their own yet, so the bar keeps it.
     */
    private val _navCursor = MutableStateFlow<LauncherTab?>(null)
    val navCursor: StateFlow<LauncherTab?> = _navCursor.asStateFlow()

    /** True while the controller is on the nav bar rather than in the content. */
    private val isNavBarFocused: Boolean get() = _navCursor.value != null

    /**
     * Moves the cursor down out of the grid and onto the bar.
     *
     * The bar is reached by walking into it, not by a dedicated button: pressing
     * Down past the bottom row is what every ten-foot interface does, and it is
     * the one gesture that needs no discovering. It lands on the *selected* tab
     * rather than the first, so arriving somewhere and pressing Confirm is a
     * no-op instead of a section change nobody asked for.
     */
    fun enterNavBar() {
        _navCursor.value = _selectedTab.value
    }

    /**
     * Returns the cursor to the content above.
     *
     * Refused on a section that has no content to hold a cursor — leaving the bar
     * there would strand input on a surface with nothing focusable on it, which is
     * the same dead end the dock used to be.
     */
    fun leaveNavBar() {
        if (_selectedTab.value.isHome) _navCursor.value = null
    }

    /** Selects a section, from a tap or from Confirm on the bar. */
    fun selectTab(tab: LauncherTab) {
        _selectedTab.value = tab
        // The cursor follows the selection, and stays on the bar: the sections
        // other than Home have nothing else to focus, and on Home the user is one
        // press of Up away from the grid.
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
    private fun onNavBarCommand(command: ControllerCommand) {
        val focused = _navCursor.value ?: return
        when (command) {
            ControllerCommand.NAVIGATE_LEFT ->
                _navCursor.value = LauncherTab.step(focused, -1)

            ControllerCommand.NAVIGATE_RIGHT ->
                _navCursor.value = LauncherTab.step(focused, 1)

            // Up is the way back into the content, and Back does the same thing:
            // a bar at the bottom of the screen has nothing below it, so both of
            // the "leave here" buttons should agree.
            ControllerCommand.NAVIGATE_UP, ControllerCommand.BACK -> leaveNavBar()

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
                if (controlSettings.value.wrapNavigation && _selectedTab.value.isHome) {
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
    fun toggleRecording() {
        if (screenRecorder.isRecording) {
            val saved = screenRecorder.stop()
            emit(
                LauncherEffect.ShowMessage(
                    saved?.let { "Saved $it to Movies/THOR" } ?: "Nothing was recorded",
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
     * Held in the view model precisely because of its lifetime: it survives
     * configuration changes and dies with the process, which is exactly "once per
     * cold start". A `remember` in the composition would replay it on every rotation
     * or display change, and a flag in settings would replay it never.
     *
     * A launcher is returned to dozens of times a day — pressing Home must not play
     * an intro, and it does not, because the process is still alive.
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

    /**
     * Opens or closes the panel the AYN button raises.
     *
     * A toggle rather than two calls because the button that opens it is the
     * obvious thing to press to make it go away, and it is the only control the
     * user is guaranteed to still have while a game holds the other panel.
     */
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

    /** Runs a tile. The panel always closes first, so nothing opens behind it. */
    fun onShortcut(action: ShortcutAction) {
        closeShortcutPanel()
        when (action) {
            ShortcutAction.APPS -> openAppDrawer()
            ShortcutAction.SEARCH -> emit(LauncherEffect.OpenSearch)
            ShortcutAction.THOR_SETTINGS -> emit(LauncherEffect.OpenSettings)
            ShortcutAction.SCAN_LIBRARY -> scanLibrary()
            ShortcutAction.RECORD -> toggleRecording()

            ShortcutAction.SWAP_SCREENS -> viewModelScope.launchSafely(TAG) {
                settingsRepository.updateDisplay { it.copy(swapScreens = !it.swapScreens) }
            }

            ShortcutAction.WIFI -> openSystemPanel(SystemPanel.WIFI)
            ShortcutAction.BLUETOOTH -> openSystemPanel(SystemPanel.BLUETOOTH)
            ShortcutAction.VOLUME -> openSystemPanel(SystemPanel.VOLUME)
            ShortcutAction.SYSTEM_SETTINGS -> openSystemPanel(SystemPanel.ALL_SETTINGS)
        }
    }

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
    }

    fun openAppDrawer() {
        _appDrawer.update { it.copy(visible = true, page = 0, cursor = CursorPosition(0, 0)) }
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
        ) { grid, performance, controls, personalization ->
            GridConfig(
                spec = grid,
                // One page either side is enough to make a swipe look instant
                // without composing pages nobody is heading toward; performance
                // mode drops it to zero to save the work entirely.
                pagePrefetchRadius = if (performance.performanceMode) 0 else 1,
                touchEnabled = controls.touchEnabled,
                folderStyle = personalization.folderStyle,
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
        val selection = resolveSelection(layout, spec, interaction, derived.openFolderContents)
        LauncherUiState(
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
    fun onCommand(command: ControllerCommand, accelerated: Boolean) {
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

        // Any button skips the intro, and does nothing else — a press meant to
        // dismiss it should not also launch whatever the cursor happens to be on.
        if (_introVisible.value) {
            finishIntro()
            return
        }

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
            onSideMenuCommand(command)
            return
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
            onNavBarCommand(command)
            return
        }

        // Sections other than Home have no grid to drive, so everything below
        // this point would act on a surface the user cannot see.
        if (!_selectedTab.value.isHome) {
            when (command) {
                ControllerCommand.NAVIGATE_DOWN, ControllerCommand.BACK -> enterNavBar()
                ControllerCommand.GO_HOME -> goHome()
                ControllerCommand.OPEN_SHORTCUTS -> toggleShortcutPanel()
                else -> Unit
            }
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
        val spec = uiState.value.spec
        val controls = controlSettings.value
        val position = cursor.value
        val page = currentPage.value

        var row = position.row
        var column = position.column

        when (direction) {
            NavDirection.UP -> row--
            NavDirection.DOWN -> row++
            NavDirection.LEFT -> column--
            NavDirection.RIGHT -> column++
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

        cursor.value = CursorPosition(row, column)

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
        cursor.value = CursorPosition(row, column)
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
     * Where an ordinary launch sends an entry.
     *
     * The screen the user is touching, not whichever one the activity happens to
     * live on. `DEFAULT` means "wherever the system would put it", which is the
     * activity's display — so pressing A on the grid opened the game on the *other*
     * panel, left the grid on screen in front of the user, and handed input to the
     * app behind it. Nothing had covered the grid's own display, so the presentation
     * was never stood down either: the launcher looked frozen because it was still
     * being drawn while no longer receiving input.
     */
    private val defaultLaunchTarget: LaunchTarget
        get() = if (gridOnSecondaryDisplay) {
            LaunchTarget.SECOND_SCREEN
        } else {
            LaunchTarget.MAIN_SCREEN
        }

    fun launchEntry(entry: GridEntry) {
        // A folder opens in place; it has nothing to launch.
        if (entry is FolderEntry) {
            openFolder(entry.id)
            return
        }
        launchEntryOn(entry, defaultLaunchTarget)
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

        is LaunchFailure.NoHandler -> "Nothing on this device can open that"
        is LaunchFailure.Unknown -> failure.cause.message ?: "Could not launch"
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
        _secondScreenOccupied.value = false

        // Whatever was launched is being left behind, so its session is over.
        settlePlaytime()

        // Home means the Home section with the cursor back in the grid. Returning
        // to the launcher still on Movies would be the same surprise as returning
        // to it on page four of the grid.
        _selectedTab.value = LauncherTab.DEFAULT
        _navCursor.value = null

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

    fun toggleFavorite() {
        val entry = uiState.value.selection ?: return
        viewModelScope.launchSafely(TAG) {
            libraryRepository.setFavorite(entry.id, !entry.isFavorite)
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
            val displaced = if (hasPlacement) {
                gridRepository.moveEntry(holding.entryId, page, row, column)
            } else {
                gridRepository.placeEntryAt(holding.entryId, page, row, column)
                null
            }

            editMode.value = if (displaced != null) {
                emit(LauncherEffect.ShowMessage("Now place the icon you moved"))
                EditMode.Holding(
                    entryId = displaced,
                    // Its origin is the cell it just lost, which is where Back
                    // should return it to.
                    originPage = page,
                    originRow = row,
                    originColumn = column,
                )
            } else {
                EditMode.Arranging
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
        )
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
            ControllerCommand.NAVIGATE_UP ->
                contextMenuIndex.value = (contextMenuIndex.value - 1 + actions.size) % actions.size

            ControllerCommand.NAVIGATE_DOWN ->
                contextMenuIndex.value = (contextMenuIndex.value + 1) % actions.size

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
                launchEntryOn(entry, LaunchTarget.MAIN_SCREEN)

            ContextAction.LAUNCH_SECOND_SCREEN ->
                launchEntryOn(entry, LaunchTarget.SECOND_SCREEN)

            ContextAction.ADD_TO_GRID -> addToGrid(entry)

            ContextAction.REMOVE_FROM_GRID -> removeFromGrid(entry)

            ContextAction.MOVE_TO_FOLDER -> openFolderPicker(entry)

            ContextAction.REMOVE_FROM_FOLDER -> removeFromFolder(entry)

            ContextAction.EDIT -> openEditor(entry)

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

    /** Launches an entry on a specific panel. */
    fun launchEntryOn(entry: GridEntry, target: LaunchTarget) {
        /*
         * A controller Confirm can coincide with a touch callback in one frame.
         * Two start requests make one task steal focus from the other and look
         * exactly like the app opened behind a frozen grid.
         */
        if (launchJob?.isActive == true) return

        launchJob = viewModelScope.launchSafely(TAG) {
            try {
            /*
             * The panel is handed over *before* the app is started, not after.
             *
             * A `Presentation` sits above application windows on its display. Starting
             * the app first meant it arrived underneath a window that was still there
             * — drawn behind the grid, with the window manager deciding focus between
             * the two — and only then did the launcher take its window away. Standing
             * down first gives the app an empty display to arrive on, which is the
             * only ordering that cannot produce a grid sitting on top of a running
             * app. The flag is put back if the launch turns out to fail.
             */
            if (target == LaunchTarget.SECOND_SCREEN) {
                _secondScreenOccupied.value = true
                if (!awaitSecondaryPresentationDismissal()) {
                    _secondScreenOccupied.value = false
                    emit(LauncherEffect.LaunchFailed("Could not prepare the second screen"))
                    closeContextMenu()
                    return@launchSafely
                }
            }

            val result = when (entry) {
                is AppEntry -> entryLauncher.launchApp(entry, target)

                is GameEntry -> {
                    val platform = uiState.value.platformsById[entry.platformId]
                    entryLauncher.launchGame(
                        game = entry,
                        platformDefaultEmulator = platform?.defaultEmulatorPackage,
                        target = target,
                    )
                }

                is FolderEntry -> {
                    _secondScreenOccupied.value = false
                    openFolder(entry.id)
                    closeContextMenu()
                    return@launchSafely
                }

                // Shortcuts carry a launcher action rather than a component, so
                // there is nothing for a display target to apply to.
                else -> {
                    _secondScreenOccupied.value = false
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
            val arrivedOnSecondPanel = target == LaunchTarget.SECOND_SCREEN &&
                (result as? LaunchResult.Success)?.onRequestedTarget == true
            if (target == LaunchTarget.SECOND_SCREEN && !arrivedOnSecondPanel) {
                _secondScreenOccupied.value = false
            }
            if (result is LaunchResult.Success) {
                // Reports where the app *landed*, not where it was aimed, so the
                // shell yields focus for the panel actually being taken.
                emit(LauncherEffect.Launched(onSecondaryPanel = arrivedOnSecondPanel))
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
    private fun onSideMenuCommand(command: ControllerCommand) {
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

    // ------------------------------------------------------------- internal

    private fun resolveSelection(
        layout: LayoutSnapshot,
        spec: GridSpec,
        interaction: InteractionSnapshot,
        /** Already resolved by the caller's memo; see [LayoutMemo]. */
        openFolderContents: List<GridEntry>,
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

        val cell = interaction.cursor.cellIndex(spec.columns)
        val placement = layout.placements.firstOrNull {
            it.pageIndex == interaction.page && it.row * spec.columns + it.column == cell
        } ?: return null
        return layout.entries[placement.entryId]
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
        const val DOCK_SLOTS = 5

        /** A guard only; normal handoff proceeds as soon as dismissal is acknowledged. */
        const val PRESENTATION_HANDOVER_TIMEOUT_MS = 1_000L

        /** Fallback capture size, used only until the shell reports the panels. */
        const val DEFAULT_CAPTURE_WIDTH = 1080
        const val DEFAULT_CAPTURE_HEIGHT = 1600
        const val DEFAULT_CAPTURE_DENSITY = 320
    }
}
