package com.thor.feature.home

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.COUCH_SHORT_SIDE
import com.thor.core.designsystem.theme.DesignScale
import com.thor.core.designsystem.theme.PANEL_SHORT_SIDE
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.ClockStyle
import com.thor.core.model.CouchWallpaperStyle
import com.thor.core.model.DisplaySettings
import com.thor.core.model.DockSettings
import com.thor.core.model.GameEntry
import com.thor.core.model.GameJournal
import com.thor.core.model.GridEntry
import com.thor.core.model.HomeLayout
import com.thor.core.model.LauncherAction
import com.thor.core.model.LauncherFeatures.DOCK_ENABLED
import com.thor.core.model.LauncherTab
import com.thor.core.model.PanelLayout
import com.thor.core.model.SortOrder
import com.thor.core.model.WidgetEntry
import com.thor.core.ui.component.AnimatedWallpaperBackground
import com.thor.core.ui.component.ModeChangeVeil
import com.thor.core.ui.profile.ShellStatus
import com.thor.core.ui.profile.ShellStatusActions
import com.thor.feature.home.couch.CouchDashboardActions
import com.thor.feature.home.couch.CouchDetailScroll
import com.thor.feature.home.couch.CouchFocus
import com.thor.feature.home.couch.CouchPlatformMenu
import com.thor.feature.home.couch.couchPlatforms
import com.thor.feature.home.couch.CouchPlatformSummary
import com.thor.feature.home.couch.CouchQuickDetails
import com.thor.feature.home.couch.CouchScreen
import com.thor.feature.home.couch.platform
import com.thor.feature.home.cards.PlatformCard
import com.thor.feature.home.cards.PlatformCardScreen
import com.thor.feature.home.companion.CompanionPanel
import com.thor.feature.home.dialog.NoteDialog
import com.thor.feature.home.dialog.NoteDialogState
import com.thor.feature.home.dialog.FolderPickerDialog
import com.thor.feature.home.dialog.FolderPickerState
import com.thor.feature.home.dialog.SortDialog
import com.thor.data.metadata.MetadataCandidate
import com.thor.feature.home.dialog.MatchPickerDialog
import com.thor.feature.home.dialog.MatchPickerState
import com.thor.feature.home.dialog.WidgetChoice
import com.thor.feature.home.dialog.WidgetPickerDialog
import com.thor.feature.home.dialog.WidgetPickerState
import com.thor.feature.home.grid.LauncherGrid
import com.thor.feature.home.grid.PageIndicators
import com.thor.feature.home.menu.CellAction
import com.thor.feature.home.menu.CellMenuState
import com.thor.feature.home.menu.ContextAction
import com.thor.feature.home.menu.EmptyCellMenu
import com.thor.feature.home.menu.EntryContextMenu
import com.thor.feature.home.menu.SideMenu
import com.thor.feature.home.menu.SideMenuAction
import com.thor.feature.home.shell.BottomNavBar
import com.thor.feature.home.shell.dockHeightFor
import com.thor.feature.home.shell.EmptySection
import com.thor.feature.home.shell.FloatingDock
import com.thor.feature.home.shell.icon

/**
 * The bottom display: wallpaper, grid, page indicators, dock and Start panel.
 */
@Composable
fun BottomScreen(
    state: LauncherUiState,
    /** Whether this is the first time edit mode has been entered; see [EditModeTutorial]. */
    showEditTutorial: Boolean = false,
    onDismissEditTutorial: () -> Unit = {},
    dockSettings: DockSettings,
    wallpaper: AnimatedWallpaper,
    wallpaperUri: String?,
    showPageIndicators: Boolean,
    /** What Home draws on this panel: the grid, or a flow of systems. */
    homeLayout: HomeLayout = HomeLayout.GRID,
    /**
     * The systems the flow steps through.
     *
     * Passed in rather than folded here, because the information panel on the
     * other screen resolves the highlighted system out of the same list — folded
     * twice, the two could disagree for a frame, and that is the frame in which
     * the top screen describes a system other than the one on the bottom.
     */
    platformCardList: List<PlatformCard> = emptyList(),
    /** Which system the card flow is showing, and which way it last stepped. */
    platformCardIndex: Int = 0,
    platformCardDirection: Int = 1,
    onPlatformCardOpened: (PlatformCard) -> Unit = {},
    /**
     * The entry currently holding the other panel, if any.
     *
     * Non-null is what turns this panel into the companion; see [CompanionPanel].
     * Passed as the entry rather than as a flag plus an id, because everything the
     * panel draws comes off it and a flag would mean looking it up again here.
     */
    companionEntry: GridEntry? = null,
    companionJournal: GameJournal = GameJournal.EMPTY,
    companionSinceEpochMs: Long? = null,
    canScreenshot: Boolean = false,
    /** Which companion tile the controller cursor is on. */
    companionAction: Int = 0,
    onScreenshot: () -> Unit = {},
    /** Whether a screen recording is running, so the tile can offer to stop it. */
    companionRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    onTakePanelBack: () -> Unit = {},
    /** The note editor, raised from the companion panel and from the context menu. */
    noteDialog: NoteDialogState = NoteDialogState(),
    onNoteSaved: (String) -> Unit = {},
    onNoteDismissed: () -> Unit = {},
    currentSort: SortOrder,
    focusedDockSlot: Int?,
    focusedMenuAction: SideMenuAction?,
    onCellTapped: (Int, Int) -> Unit,
    onCellLongPressed: (Int, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPinch: (Float) -> Unit,
    onDockSlotSelected: (Int) -> Unit,
    onDockAction: (LauncherAction) -> Unit,
    onMenuAction: (SideMenuAction) -> Unit,
    onMenuDismissed: () -> Unit,
    onContextAction: (ContextAction) -> Unit,
    onContextMenuDismissed: () -> Unit,
    appDrawer: AppDrawerState,
    onDrawerCellTapped: (Int, Int) -> Unit,
    onDrawerCellLongPressed: (Int, Int) -> Unit,
    onDrawerPageChanged: (Int) -> Unit,
    sortPicker: SortPickerState,
    onSortPicked: (SortOrder) -> Unit,
    onSortDirectionToggled: () -> Unit,
    onSortDismissed: () -> Unit,
    folderPicker: FolderPickerState,
    onFolderPicked: (String) -> Unit,
    onFolderCreated: () -> Unit,
    onFolderPickerDismissed: () -> Unit,
    /** The menu a long press on an *empty* cell raises. */
    cellMenu: CellMenuState = CellMenuState(),
    onCellAction: (CellAction) -> Unit = {},
    onCellMenuDismissed: () -> Unit = {},
    widgetPicker: WidgetPickerState = WidgetPickerState(),
    /** The "choose the right game" card; see [MatchPickerDialog]. */
    matchPicker: MatchPickerState = MatchPickerState(),
    onMatchPicked: (MetadataCandidate) -> Unit = {},
    onMatchPickerDismissed: () -> Unit = {},
    onWidgetPicked: (WidgetChoice) -> Unit = {},
    onWidgetPickerDismissed: () -> Unit = {},
    /** Inflates a placed widget; see [com.thor.feature.home.grid.WidgetLayer]. */
    createWidgetView: (Context, Int) -> View? = { _, _ -> null },
    onWidgetMeasured: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> Unit = { _, _, _ -> },
    /** Starts a game pressed inside one of the launcher's own widgets. */
    onWidgetLaunch: (GridEntry) -> Unit = {},
    /** One cell wider, narrower, taller or shorter; see [WidgetResizeControls]. */
    onWidgetResizeStep: (columns: Int, rows: Int) -> Unit = { _, _ -> },
    onWidgetResizeDone: () -> Unit = {},
    /** Whether any non-smart folder exists, so filing can be offered. */
    foldersExist: Boolean,
    /** Whether the context-menu entry currently sits inside a folder. */
    entryInFolder: Boolean,
    /** The section the launcher is showing. */
    selectedTab: LauncherTab,
    /** The tab the controller cursor is on, or null when it is in the content. */
    navCursor: LauncherTab?,
    onTabSelected: (LauncherTab) -> Unit,
    /** Focus and actions for Couch Mode's rail library. */
    couchFocus: CouchFocus = CouchFocus(),
    couchPlatformIndex: Int = 0,
    couchQuickDetailsEntryId: String? = null,
    couchQuickDetailsActionIndex: Int = 0,
    couchQuickDetailsScroll: CouchDetailScroll = CouchDetailScroll(),
    couchSettingsFocused: Boolean = false,
    couchSettingsSelected: Boolean = false,
    couchClockStyle: ClockStyle = ClockStyle.DIGITAL_24,
    showCouchStatusBar: Boolean = true,
    couchUiScale: Float = 1f,
    /** What couch mode draws behind its dashboard. */
    couchWallpaper: CouchWallpaperStyle = CouchWallpaperStyle.RIDGES,
    onCouchEntryFocused: (rail: Int, item: Int) -> Unit = { _, _ -> },
    onCouchEntrySelected: (GridEntry) -> Unit = {},
    onCouchEntryLongPressed: (GridEntry) -> Unit = {},
    onCouchPlatformSelected: (Int) -> Unit = {},
    onCouchDetailsPlay: (GridEntry) -> Unit = {},
    onCouchDetailsFavorite: (GridEntry) -> Unit = {},
    onCouchDetailsDismissed: () -> Unit = {},
    onCouchDetailsActionFocused: (Int) -> Unit = {},
    onCouchSettingsSelected: () -> Unit = {},
    couchFullscreenSection: Boolean = false,
    /** Settings content hosted beneath Couch Mode's single shared top bar. */
    couchSettingsContent: (@Composable () -> Unit)? = null,
    /**
     * Content for a section other than Home.
     *
     * Null leaves the section saying that nothing is connected to it, which is
     * still the right answer for a tab nothing has been built for yet.
     */
    sectionContent: (@Composable (LauncherTab) -> Unit)? = null,
    /**
     * Draw the couch layout instead of the handheld one.
     *
     * A parameter rather than a separate screen because everything this composable
     * hosts *around* the grid — the drawer, the menus, the dialogs, the banners —
     * is wanted in both, and a second screen would have had to grow its own copy
     * of all of it. Only the grid, the wallpaper and the section bar differ.
     */
    couchMode: Boolean = false,
    /** Profile and notifications for couch mode's corner. */
    status: ShellStatus? = null,
    statusActions: ShellStatusActions = ShellStatusActions(),
    couchDashboardActions: CouchDashboardActions = CouchDashboardActions(),
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens

    /*
     * The sections the bar has to offer.
     *
     * Home alone until an extension is enabled, and a bar with one tab on it is a
     * strip of screen spent saying where you already are — so it is not drawn.
     * Resolved here rather than beside the bar because the grid's height depends
     * on the same answer; see [bottomClearance].
     */
    val tabs = LauncherTab.visible(state.enabledExtensions, couchMode)
    val navBarVisible = tabs.size > 1

    /*
     * Clearance for whatever owns the bottom edge.
     *
     * Taken from the bar itself rather than written as a number here, so the two
     * cannot drift apart and leave the bottom row of icons half-covered — the
     * same reason it was taken from the dock before. Only one of them is ever
     * shown; see [DOCK_ENABLED].
     *
     * Nothing is reserved when the bar is not there. It used to be reserved
     * unconditionally, which on a stock install — no extensions, so no bar — left
     * a bar's worth of empty panel below the grid and pushed every icon upward:
     * the launcher making room for furniture it had decided not to draw.
     */
    val bottomClearance = when {
        DOCK_ENABLED && dockSettings.visible ->
            dockHeightFor(dockSettings) + dimens.spacingSmall

        DOCK_ENABLED -> 0.dp
        // Couch mode has no bottom bar at all — its sections run along the top —
        // so nothing is owed to the bottom edge and the drawer runs right to it.
        navBarVisible && !couchMode -> PanelLayout.NAV_BAR_HEIGHT.dp
        else -> 0.dp
    }
    // Named for what the drawer actually wants: room at the bottom, whichever
    // bar is putting it there.
    val dockClearance = bottomClearance

    // Drives the Adaptive wallpaper: the highlighted game's system colours the
    // background, so it shifts as the cursor crosses platforms. Null for apps,
    // folders and empty cells, which falls back to the theme's own accent.
    val adaptiveTint = (state.selection as? GameEntry)
        ?.let { game -> state.platformsById[game.platformId] }
        ?.let { platform -> Color(platform.accentArgb) }
    val couchPlatformSummaries = remember(couchMode, state.entriesById) {
        if (!couchMode) {
            emptyMap()
        } else {
            state.entriesById.values.asSequence()
                .filterIsInstance<GameEntry>()
                .filterNot(GridEntry::isHidden)
                .groupBy(GameEntry::platformId)
                .mapValues { (_, games) ->
                    val preview = games
                        .maxByOrNull { it.stats.lastPlayedEpochMs ?: Long.MIN_VALUE }
                        ?.metadata
                        ?.artwork
                        ?.let { it.boxArt ?: it.backgroundImage }
                    CouchPlatformSummary(gameCount = games.size, previewUri = preview)
                }
        }
    }

    /*
     * One canvas for the whole panel, chosen by which mode is drawing.
     *
     * Wrapped here rather than inside each branch because the overlays below —
     * the menus, the dialogs, the banners — are shared between the two and have
     * to be the same size as whatever they are drawn over. It also means couch
     * mode's own scaling is this and nothing else: it used to multiply the
     * panel's density by a constant, which is correct on one screen and wrong on
     * every other because it never asked how large the screen was.
     */
    DesignScale(
        referenceShortSide = if (couchMode) COUCH_SHORT_SIDE else PANEL_SHORT_SIDE,
        userScale = if (couchMode) DisplaySettings.couchDensityScale(couchUiScale) else 1f,
        modifier = modifier,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        /*
         * Couch mode replaces the furniture, not the screen.
         *
         * Everything below this — the app drawer, the side menu, the context
         * menu, the sort and folder dialogs, the banners — is hosted here and is
         * wanted in couch mode too. Swapping the whole screen out for a separate
         * one meant losing all of it, so what is swapped is only the part couch
         * mode actually redraws: the wallpaper, the grid and the section bar.
         *
         * [CouchScreen] brings its own backdrop, so the animated wallpaper is not
         * drawn behind it — two backgrounds fighting is exactly the busy screen
         * this mode exists to avoid.
         */
        if (couchMode) {
            CouchScreen(
                state = state,
                focus = couchFocus,
                tabs = tabs,
                selectedTab = selectedTab,
                navCursor = navCursor,
                settingsFocused = couchSettingsFocused,
                settingsSelected = couchSettingsSelected,
                platformIndex = couchPlatformIndex,
                clockStyle = couchClockStyle,
                showStatusBar = showCouchStatusBar,
                uiScale = couchUiScale,
                wallpaper = couchWallpaper,
                // Only read by the "Match launcher" style, which hands the whole
                // background back to the launcher's own wallpaper and picture.
                themeWallpaper = wallpaper,
                wallpaperImageUri = wallpaperUri,
                onTabSelected = onTabSelected,
                onSettingsSelected = onCouchSettingsSelected,
                onPlatformSelected = onCouchPlatformSelected,
                onEntryFocused = onCouchEntryFocused,
                onEntrySelected = onCouchEntrySelected,
                onEntryLongPressed = onCouchEntryLongPressed,
                onEntryFavorite = onCouchDetailsFavorite,
                fullscreenSection = couchFullscreenSection,
                sectionContent = sectionContent,
                settingsContent = couchSettingsContent,
                status = status,
                statusActions = statusActions,
                dashboardActions = couchDashboardActions,
                modifier = Modifier.fillMaxSize(),
            )
        } else {

        AnimatedWallpaperBackground(
            wallpaper = wallpaper,
            imageUri = wallpaperUri,
            accentTint = adaptiveTint,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            /*
             * In the layout, not over it.
             *
             * Floating this above the grid put it straight through the top row of
             * cells — the grid takes all the height it is given, so anything laid over
             * it lands on icons. As a row of its own the grid simply gets the height
             * that is left, and nothing overlaps.
             */
            if (selectedTab.isHome) {
                /*
                 * No banner over an open folder.
                 *
                 * It named the folder and counted what was in it, over a grid of
                 * that folder's contents — so it was captioning something already
                 * on screen. What it cost was height: it sat *in* the column, so
                 * the grid below it got a shorter box and laid the same matrix
                 * out at a smaller cell size. Opening a folder visibly shrank
                 * every icon, which is the one thing a folder must not do.
                 *
                 * Back closes a folder, from the B button and from the system
                 * gesture alike — see `ControllerProfiles`, which maps both.
                 */
                /*
                 * The card flow stands in for the grid at the top level only.
                 *
                 * Gated on no folder being open, which is what makes opening a
                 * system hand back to the grid: the folder's contents are laid
                 * out by [LauncherGrid] exactly as they always were. The same
                 * condition gates the input side; see
                 * `LauncherViewModel.onPlatformCardCommand`, and the two must
                 * agree or the buttons drive a surface that is not on screen.
                 */
                val showCards = homeLayout == HomeLayout.PLATFORM_CARDS && !state.isFolderOpen

                if (companionEntry != null) {
                    /*
                     * A game has the other panel, so this one belongs to that game.
                     *
                     * Ahead of both the grid and the card flow, because while
                     * something is being played neither of them is what this screen
                     * is for — a menu for choosing something already chosen. This is
                     * the whole reason the device has two screens; see
                     * [CompanionPanel].
                     */
                    CompanionPanel(
                        entry = companionEntry,
                        platform = (companionEntry as? GameEntry)
                            ?.let { state.platformsById[it.platformId] },
                        journal = companionJournal,
                        sinceEpochMs = companionSinceEpochMs,
                        canScreenshot = canScreenshot,
                        recording = companionRecording,
                        focusedAction = companionAction,
                        onScreenshot = onScreenshot,
                        onToggleRecording = onToggleRecording,
                        onHome = onTakePanelBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else if (showCards) {
                    PlatformCardScreen(
                        cards = platformCardList,
                        focusedIndex = platformCardIndex,
                        stepDirection = platformCardDirection,
                        onOpen = { card -> onPlatformCardOpened(card) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    LauncherGrid(
                        state = state,
                        onCellTapped = onCellTapped,
                        onCellLongPressed = onCellLongPressed,
                        onPageChanged = onPageChanged,
                        onPinch = onPinch,
                        createWidgetView = createWidgetView,
                        onWidgetMeasured = onWidgetMeasured,
                        onWidgetLaunch = onWidgetLaunch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                // Never under the flow: its own position line says where it is,
                // and page dots beneath that would be a second, disagreeing one.
                if (showPageIndicators && !showCards) {
                    PageIndicators(
                        // The folder's own pages while one is open, so the dots
                        // match what the grid is actually showing.
                        pageCount = state.visiblePageCount,
                        currentPage = state.currentPage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimens.spacingTiny),
                    )
                }
            } else {
                /*
                 * A section other than Home.
                 *
                 * Filled by the shell where something is connected, and by
                 * [EmptySection] where nothing is yet. The slot exists so this
                 * module never has to know what a section contains: Movies lives
                 * in its own feature module, and having the home screen depend on
                 * it would tie two unrelated features together for one call.
                 *
                 * Either way it takes the grid's place rather than covering it,
                 * so the wallpaper, the bar and every overlay behave exactly as
                 * they do on Home.
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (sectionContent != null) {
                        sectionContent(selectedTab)
                    } else {
                        EmptySection(tab = selectedTab, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Spacer(modifier = Modifier.height(dockClearance))
        }

        } // end of the handheld layout; couch mode drew its own above

        // Over both layouts, so it covers the frame on which one replaces the
        // other. Last in the box for the same reason.
        ModeChangeVeil(key = couchMode, modifier = Modifier.fillMaxSize())

        if (state.isScanning) {
            ScanBanner(
                label = state.scanLabel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(dimens.spacingSmall),
            )
        }

        // Edit mode has no other affordance: the icons wobble, but nothing says
        // what the buttons now do, and grab-and-drop is not guessable.
        if (state.editMode.isActive) {
            EditModeBanner(
                mode = state.editMode,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(dimens.spacingSmall),
            )
        }

        /*
         * The size, in words, while it is being changed.
         *
         * A widget being resized changes shape under the user's hands, and on a
         * grid whose cells are already small the difference between two and three
         * columns is not obvious from the outline alone. Saying the number is
         * also the only thing that makes the ceiling explicable when the widget
         * stops growing.
         */
        (state.editMode as? EditMode.Resizing)?.let { resizing ->
            val widget = state.entriesById[resizing.entryId] as? WidgetEntry
            if (widget != null) {
                WidgetResizeControls(
                    columns = widget.spanColumns,
                    rows = widget.spanRows,
                    onStep = onWidgetResizeStep,
                    onDone = onWidgetResizeDone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomClearance + dimens.spacing),
                )
            }
        }

        // The first time only, and over the banner rather than instead of it:
        // the banner is the reminder, this is the explanation.
        if (showEditTutorial) {
            EditModeTutorial(
                onDismiss = onDismissEditTutorial,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Above the grid but below the menus, so a long press in the drawer can
        // still raise a context menu over it.
        //
        // Never in couch mode, where the shelf's Apps rail is the app list — see
        // `LauncherViewModel.openAppDrawer`, which sends every route there. This
        // guard is for a drawer left open when the mode changes under it.
        if (appDrawer.visible && !couchMode) {
            AppDrawerScreen(
                apps = appDrawer.apps,
                spec = state.spec,
                currentPage = appDrawer.page,
                cursor = appDrawer.cursor,
                touchEnabled = state.touchEnabled,
                wallpaper = wallpaper,
                wallpaperUri = wallpaperUri,
                dockClearance = dockClearance,
                onCellTapped = onDrawerCellTapped,
                onCellLongPressed = onDrawerCellLongPressed,
                onPageChanged = onDrawerPageChanged,
                onPinch = onPinch,
                // The same setting the home grid obeys. The drawer used to draw
                // its dots unconditionally, so with indicators turned off it
                // reserved a strip the grid behind it did not — and the same
                // matrix came out a few pixels smaller in the drawer.
                showPageIndicators = showPageIndicators,
            )
        }

        /*
         * The dock, kept but not shown.
         *
         * Superseded by the nav bar, which owns the bottom edge now — but left
         * whole rather than deleted: the five assignable slots, their placements
         * in the database and their settings page all still work, and the shape of
         * the launcher is not settled enough to throw that away. One constant
         * brings it back.
         *
         * Drawn after the drawer, as it always was, so it stays visible over it —
         * the dock is how the drawer is opened and closed again, and hiding it
         * there made the launcher feel like it had switched to a different app.
         */
        if (DOCK_ENABLED && !couchMode) {
            FloatingDock(
                settings = dockSettings,
                focusedSlot = focusedDockSlot,
                iconShape = state.spec.iconShape,
                onSlotSelected = onDockSlotSelected,
                onSlotActivated = onDockAction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dimens.spacingSmall),
            )
        }

        // Flush to the bottom edge, unlike the dock it replaces: a nav bar that
        // floats above the edge reads as a dialog, not as the frame of the app.
        // Drawn only when there is something to switch between; see [navBarVisible].
        if (navBarVisible && !couchMode) {
            BottomNavBar(
                selectedTab = selectedTab,
                focusedTab = navCursor,
                onTabSelected = onTabSelected,
                tabs = tabs,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        val couchDetailsEntry = couchQuickDetailsEntryId?.let(state.entriesById::get)
        CouchQuickDetails(
            visible = couchMode && selectedTab.isHome && couchDetailsEntry != null,
            entry = couchDetailsEntry,
            platform = (couchDetailsEntry as? GameEntry)
                ?.let { state.platformsById[it.platformId] },
            focusedAction = couchQuickDetailsActionIndex,
            onPlay = { couchDetailsEntry?.let(onCouchDetailsPlay) },
            onToggleFavorite = { couchDetailsEntry?.let(onCouchDetailsFavorite) },
            onDismiss = onCouchDetailsDismissed,
            onActionFocused = onCouchDetailsActionFocused,
            // Hosted here rather than by [CouchScreen], so the scale it composes
            // everything else through has to be handed over explicitly.
            uiScale = couchUiScale,
            scroll = couchQuickDetailsScroll,
        )

        SortDialog(
            visible = sortPicker.visible,
            currentOrder = sortPicker.order,
            descending = sortPicker.descending,
            focusedIndex = sortPicker.focusedIndex,
            onPick = onSortPicked,
            onToggleDirection = onSortDirectionToggled,
            onDismiss = onSortDismissed,
        )

        if (couchMode && selectedTab.isHome) {
            CouchPlatformMenu(
                visible = state.sideMenuOpen,
                platforms = state.couchPlatforms(),
                summaries = couchPlatformSummaries,
                focusedIndex = state.sideMenuIndex,
                onPlatformSelected = { index ->
                    onCouchPlatformSelected(index)
                    onMenuDismissed()
                },
                onDismiss = onMenuDismissed,
            )
        } else {
            SideMenu(
                visible = state.sideMenuOpen,
                focusedAction = focusedMenuAction,
                onAction = onMenuAction,
                onDismiss = onMenuDismissed,
            )
        }

        // Over everything, including the companion panel that usually raises it.
        NoteDialog(
            state = noteDialog,
            onSave = onNoteSaved,
            onDismiss = onNoteDismissed,
        )

        FolderPickerDialog(
            state = folderPicker,
            onPick = onFolderPicked,
            onCreateFolder = onFolderCreated,
            onDismiss = onFolderPickerDismissed,
        )

        EmptyCellMenu(
            visible = cellMenu.visible && !couchMode,
            page = cellMenu.page,
            row = cellMenu.row,
            column = cellMenu.column,
            focusedIndex = cellMenu.focusedIndex,
            onAction = onCellAction,
            onDismiss = onCellMenuDismissed,
        )

        MatchPickerDialog(
            state = matchPicker,
            onPick = onMatchPicked,
            onDismiss = onMatchPickerDismissed,
        )

        WidgetPickerDialog(
            state = widgetPicker,
            onPick = onWidgetPicked,
            onDismiss = onWidgetPickerDismissed,
        )

        EntryContextMenu(
            /*
             * Never raised from a sofa.
             *
             * Nothing in couch mode opens it any more — see
             * `LauncherViewModel.openContextMenu`, which sends every one of those
             * routes to the page Y raises instead. This guard is for the one case
             * that cannot: a menu already open when the mode changes under it,
             * which would otherwise be left on the television.
             */
            entry = state.contextMenuEntry.takeUnless { couchMode },
            hasSecondScreen = state.hasSecondScreen,
            focusedIndex = state.contextMenuIndex,
            fromDrawer = appDrawer.visible,
            foldersExist = foldersExist,
            inFolder = entryInFolder,
            onGrid = state.contextMenuEntry?.let { entry ->
                state.placements.any { it.entryId == entry.id }
            } ?: true,
            onAction = onContextAction,
            onDismiss = onContextMenuDismissed,
        )
    }
    }
}

/**
 * Explains the arranging gestures, once, the first time edit mode is entered.
 *
 * The banner above it is a reminder and reads as one — a single line of button
 * names, useful to somebody who already knows what they are for. None of these
 * gestures are discoverable from it: a pinch resizes the whole grid rather than
 * the cell under the fingers, dragging a cell onto another makes a folder, and
 * the way out is a button rather than anything on screen. Each of those is
 * obvious afterwards and impossible to guess before, which is the definition of
 * something that has to be said once.
 *
 * Shown at the moment it becomes answerable rather than during the first-run
 * walkthrough, which happens before there is a library to arrange.
 */
@Composable
private fun EditModeTutorial(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Anywhere at all, because the one thing every reader wants is for
            // it to go away and the whole card is the target.
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            modifier = modifier
                .fillMaxWidth(TUTORIAL_WIDTH_FRACTION)
                .widthIn(max = TUTORIAL_MAX_WIDTH.dp),
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacingLarge),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                Text(
                    text = "Arranging the grid",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                EDIT_MODE_GESTURES.forEach { (gesture, effect) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = gesture,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.cursor,
                            modifier = Modifier.width(TUTORIAL_GESTURE_WIDTH.dp),
                        )
                        Text(
                            text = effect,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = "Press anywhere to start",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = dimens.spacingSmall),
                )
            }
        }
    }
}

/**
 * What edit mode can do, in the order somebody discovers they want it.
 *
 * Moving first because it is why edit mode was entered at all; resizing second
 * because it is the one nobody finds on their own; leaving last because a card
 * that does not say how to get out of a mode is how a mode becomes a trap.
 */
private val EDIT_MODE_GESTURES = listOf(
    "A" to "Pick a cell up, then D-pad to move it and A again to drop it.",
    "Pinch" to "Two fingers on the grid resizes every cell — more per page, or fewer and larger.",
    "Drag" to "Drop one game onto another to make a folder from the pair.",
    "Y" to "Opens the same menu a long press does, on the cell under the cursor.",
    "B" to "Finishes arranging and puts the grid back to normal.",
)

private const val TUTORIAL_WIDTH_FRACTION = 0.86f
private const val TUTORIAL_MAX_WIDTH = 420
private const val TUTORIAL_GESTURE_WIDTH = 52

/**
 * The size, and the buttons that change it.
 *
 * The D-pad already does this — see `LauncherViewModel.onResizeCommand` — and on
 * a controller that is the better gesture. These exist because the panel this is
 * drawn on is a touchscreen, and without them a finger could start a resize from
 * the long-press menu and then have no way to finish one. The number between
 * each pair is the point of the panel as much as the buttons are: at this cell
 * size two columns and three are not obviously different, and it is the only
 * thing that explains a widget refusing to grow when it has hit the ceiling.
 */
@Composable
private fun WidgetResizeControls(
    columns: Int,
    rows: Int,
    onStep: (columns: Int, rows: Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    GlassSurface(shape = ThorTheme.shapes.large, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            modifier = Modifier.padding(
                horizontal = dimens.spacingSmall,
                vertical = dimens.spacingTiny,
            ),
        ) {
            ResizeAxis(
                label = "wide",
                value = columns,
                onDecrease = { onStep(-1, 0) },
                onIncrease = { onStep(1, 0) },
            )
            ResizeAxis(
                label = "tall",
                value = rows,
                onDecrease = { onStep(0, -1) },
                onIncrease = { onStep(0, 1) },
            )
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelMedium,
                color = colors.cursor,
                modifier = Modifier
                    .clip(ThorTheme.shapes.small)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** One axis of the resize panel: minus, the count, plus. */
@Composable
private fun ResizeAxis(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = ThorTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        ResizeStep(glyph = "−", onClick = onDecrease)
        Text(
            text = "$value $label",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ResizeStep(glyph = "+", onClick = onIncrease)
    }
}

@Composable
private fun ResizeStep(glyph: String, onClick: () -> Unit) {
    val colors = ThorTheme.colors
    Text(
        text = glyph,
        style = MaterialTheme.typography.titleMedium,
        color = colors.cursor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .width(RESIZE_STEP_SIZE.dp)
            .padding(vertical = 3.dp),
    )
}

/** Wide enough for a finger, which is what these are for. */
private const val RESIZE_STEP_SIZE = 34

/** Tells the user edit mode is active and what the buttons do. */
@Composable
private fun EditModeBanner(mode: EditMode, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    GlassSurface(modifier = modifier) {
        Text(
            text = when (mode) {
                is EditMode.Holding -> "Moving — D-pad to position, A to drop, B to cancel"
                is EditMode.Resizing -> "Resizing — D-pad to change the size, A when done, B to undo"
                else -> "Edit mode — A to pick up, pinch to resize, B to finish"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.cursor,
            modifier = Modifier.padding(
                horizontal = dimens.spacing,
                vertical = dimens.spacingSmall,
            ),
        )
    }
}

@Composable
private fun ScanBanner(label: String?, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    GlassSurface(modifier = modifier) {
        Text(
            text = label?.let { "Scanning $it…" } ?: "Scanning library…",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(
                horizontal = dimens.spacing,
                vertical = dimens.spacingSmall,
            ),
        )
    }
}

