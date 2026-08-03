package com.thor.launcher

import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.thor.core.common.log.ThorLog
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.display.DisplayTopology
import com.thor.core.display.LauncherFocus
import com.thor.core.display.LauncherPanel
import com.thor.core.display.SecondaryDisplay
import com.thor.core.display.ThorDisplayMonitor
import com.thor.core.input.ControllerInputRouter
import com.thor.core.input.MouseController
import com.thor.core.input.PointerDisplay
import com.thor.launcher.capture.ProjectedScreen
import com.thor.launcher.capture.ProjectionConsentActivity
import com.thor.launcher.capture.RecordingGeometry
import com.thor.launcher.mouse.PointerHost
import kotlinx.coroutines.flow.drop
import com.thor.data.capture.RecordingState
import com.thor.core.model.ControllerCommand
import com.thor.core.model.DualScreenMode
import com.thor.core.model.FolderEntry
import com.thor.core.model.LauncherExtension
import com.thor.core.model.GameEntry
import com.thor.core.model.KeyboardKey
import com.thor.core.model.PlatformFolders
import com.thor.core.model.ThorSettings
import com.thor.core.ui.feedback.FeedbackCue
import com.thor.core.ui.component.ThorKeyboard
import com.thor.core.ui.component.ThorIntro
import com.thor.core.ui.component.ConsoleMockup
import com.thor.core.ui.component.recordingFrameSize
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorTextInputState
import com.thor.core.ui.feedback.rememberThorFeedback
import com.thor.feature.home.BottomScreen
import com.thor.launcher.stream.StreamSessionActivity
import com.thor.feature.home.LauncherEffect
import com.thor.feature.home.AppDrawerScreen
import com.thor.feature.home.InputSurface
import com.thor.feature.home.LauncherViewModel
import com.thor.feature.home.component.EditEntryDialog
import com.thor.feature.home.component.EmptySection
import com.thor.feature.movies.MoviesBottomPanel
import com.thor.feature.movies.MoviesCouchScreen
import com.thor.feature.movies.MoviesMode
import com.thor.feature.movies.MoviesTopPanel
import com.thor.feature.movies.MoviesViewModel
import com.thor.feature.stream.StreamBottomPanel
import com.thor.feature.stream.StreamCouchScreen
import com.thor.feature.stream.StreamTopPanel
import com.thor.feature.stream.StreamEffect
import com.thor.feature.stream.StreamViewModel
import com.thor.feature.movies.rememberMoviesSection
import com.thor.feature.movies.handleCommand
import com.thor.feature.movies.perform
import com.thor.feature.movies.pickSource
import com.thor.feature.movies.pickSeason
import com.thor.feature.movies.pickEpisode
import com.thor.feature.movies.pickTitle
import com.thor.core.model.LauncherTab
import com.thor.feature.home.component.SideMenuAction
import com.thor.feature.home.component.ShortcutPanel
import com.thor.feature.home.component.SortDialog
import com.thor.feature.search.SearchScreen
import com.thor.feature.search.SearchViewModel
import com.thor.feature.settings.SettingsCategory
import com.thor.feature.settings.SettingsScreen
import com.thor.feature.settings.tutorial.PermissionsScreen
import com.thor.feature.settings.tutorial.ThorTutorial
import com.thor.feature.settings.tutorial.TutorialPanel
import com.thor.feature.settings.tutorial.TutorialScreen
import com.thor.feature.settings.tutorial.TutorialStep
import com.thor.feature.settings.tutorial.rememberPermissionItems
import com.thor.feature.topscreen.ShellStatus
import com.thor.feature.topscreen.ShellStatusActions
import com.thor.feature.topscreen.TopScreen

/** Which full-screen overlay, if any, is showing on the info surface. */
/**
 * What the information panel is hosting.
 *
 * The walkthrough is deliberately not one of these. It runs *across* both panels
 * and drives this state itself — a step about a settings category opens
 * [Overlay.SETTINGS] on the information panel while the step's own card stays on
 * the grid panel beside the reader. A tour that was an overlay could not do that,
 * because it would be the thing occupying the surface it needs to show.
 */
private enum class Overlay { NONE, SETTINGS, SEARCH, PERMISSIONS }

/**
 * The shell's surfaces named as the focus rule names them.
 *
 * [InputSurface] is the launcher's own vocabulary — top and bottom, as the user sees
 * them — while [LauncherPanel] is the role a surface plays, which is what decides
 * which window has to hold focus for it. They are the same two things; only one of
 * them can live in `:core:display`, where the rule is testable.
 */
private fun InputSurface.toPanel(): LauncherPanel = when (this) {
    InputSurface.BOTTOM -> LauncherPanel.GRID
    InputSurface.TOP -> LauncherPanel.INFO
}

/**
 * The launcher shell.
 *
 * Resolves how the two surfaces map onto the hardware, wires physical input
 * into the view model, and hosts the overlays. The top surface is composed once
 * and then either placed in this window or handed to a [SecondaryDisplay]
 * presentation — the composable itself is identical either way, which is what
 * makes the split-screen fallback a genuine substitute rather than a second
 * implementation.
 */
@Composable
fun ThorApp(
    inputRouter: ControllerInputRouter,
    displayMonitor: ThorDisplayMonitor,
    /** The controller pointer, shared with the accessibility service. */
    mouse: MouseController,
    /** Fires when the system delivers a HOME intent to the running launcher. */
    homeRequests: Flow<Unit>,
    /**
     * Where a recording gets its shape.
     *
     * Written from here because this is the only place that knows both panels'
     * real sizes, and read by a service that runs when this composition does not —
     * which is the entire reason it is a held value rather than a parameter.
     */
    recordingGeometry: RecordingGeometry,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    /*
     * Collected without the lifecycle, deliberately, everywhere in this shell.
     *
     * `collectAsStateWithLifecycle` stops at `STOPPED`, and this activity is stopped
     * whenever an app covers *its* display — while the launcher is still fully on
     * screen on the other one. Gating on it meant the second panel kept its window
     * and its touch handling but stopped receiving any state at all: a live-looking,
     * completely frozen launcher. A launcher's state is wanted for as long as either
     * of its windows exists, which is exactly as long as this composition does.
     */
    val state by viewModel.uiState.collectAsState()
    val selectedScreenshot by viewModel.screenshotIndex.collectAsState()
    val settingsViewModel: com.thor.feature.settings.SettingsViewModel = hiltViewModel()
    val loadedSettings by settingsViewModel.loadedSettings.collectAsState()
    val settings = loadedSettings ?: ThorSettings.DEFAULT
    val settingsLoaded = loadedSettings != null

    // Hoisted so controller input can drive the results list; the search screen
    // would otherwise own a separate instance that input could not reach.
    val searchViewModel: SearchViewModel = hiltViewModel()

    val displays by displayMonitor.displays.collectAsState(
        initial = displayMonitor.snapshot(),
    )

    var overlay by remember { mutableStateOf(Overlay.NONE) }

    // Reported upward by the settings screen so controller navigation can be
    // clamped to the rows the current pane actually rendered.
    var settingsRowCount by remember { mutableIntStateOf(0) }

    /**
     * The walkthrough that is running, or nothing.
     *
     * Held by the shell rather than by the screen because the tour drives the
     * launcher: it opens settings categories, moves between panels and holds
     * every button while it does. The screen is only the card.
     *
     * [tutorialExtension] says which tour it is — `null` for the main one, or the
     * extension whose short tour is playing — so finishing writes the right flag.
     */
    var tutorialSteps by remember { mutableStateOf(emptyList<TutorialStep>()) }
    var tutorialIndex by remember { mutableIntStateOf(0) }
    var tutorialExtension by remember { mutableStateOf<LauncherExtension?>(null) }

    /*
     * A function, not a value, because the input collector reads it.
     *
     * That collector is a `LaunchedEffect` keyed on the router alone, so the
     * lambda Compose keeps is the one built on the *first* composition. A plain
     * `val tutorialRunning = tutorialSteps.isNotEmpty()` is captured by value at
     * that moment — which is `false`, before any tour exists — and stays false
     * forever, so every press during the walkthrough fell through to the routing
     * below and launched whatever the cursor was sitting on.
     *
     * Read through the state delegate instead. `remember` hands back the same
     * `MutableState` on every composition, so a read inside the lambda is a read
     * of the current value rather than of the one it closed over. The rest of
     * this file reads live for the same reason; see `activeSurfaceNow` and the
     * note on the collector itself.
     */
    val tutorialRunningNow: () -> Boolean = { tutorialSteps.isNotEmpty() }
    val tutorialStep = tutorialSteps.getOrNull(tutorialIndex)

    /*
     * The surface the user last touched, which is the *lock*: it holds until the
     * other surface is touched, so the controller drives the screen the user last put
     * a thumb on whatever is happening on the other one.
     */
    var touchedSurface by remember { mutableStateOf(InputSurface.BOTTOM) }

    /*
     * Set when an app is launched, cleared by any touch or overlay.
     *
     * The launcher gives up its claim on window focus so the app arriving on a
     * display can take it — the tap that started something is not a request to hold
     * that panel. Without this the second window competed for focus with the app it
     * had just launched, and the app ran with a controller that did nothing.
     */
    var focusYieldedToApp by remember { mutableStateOf(false) }

    /*
     * Ordering between the two things that claim the controller.
     *
     * An overlay takes input when it opens and a touch takes it where it lands,
     * and until now the overlay simply won for as long as it was open. That is
     * right while the user is using it and wrong the moment they reach past it:
     * tapping the grid on the other panel set the touched surface, the overlay
     * branch ignored it, and the controller stayed where it was with nothing on
     * screen explaining why. It looked random because it depended on whether an
     * overlay happened to be open at all.
     *
     * One counter, stamped by both. The most recent deliberate act wins, which is
     * the rule a person would state.
     */
    var inputTick by remember { mutableIntStateOf(0) }
    var touchedAtTick by remember { mutableIntStateOf(0) }
    var overlayClaimedAtTick by remember { mutableIntStateOf(0) }

    /** The last thing the launcher had to say, shown briefly and then dropped. */
    var transientMessage by remember { mutableStateOf<String?>(null) }

    /*
     * A touch anywhere on a surface claims the controller for it.
     *
     * Registered in the initial pass, so it sees the event before any cell or button
     * consumes it — the claim is made by *touching the panel*, not by hitting
     * something on it. It also cancels the focus a launch gave away, because reaching
     * for a panel is unambiguously a request to drive it.
     */
    fun Modifier.claimsInputFor(surface: InputSurface): Modifier =
        this.pointerInput(surface) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)

                    /*
                     * Written only when the answer changes, and that is a
                     * performance fix rather than a tidy-up.
                     *
                     * This assigned all four of these on *every* pointer event.
                     * A finger resting on the panel produces a stream of moves,
                     * and each assignment writes snapshot state read at the top
                     * of this composable — so a single drag recomposed the whole
                     * shell dozens of times a second, every one of them to
                     * conclude that the same surface was still being touched.
                     * That is most of what "laggy, and the controls do not
                     * respond straight away" is: the frames were being spent
                     * re-deriving a decision that had not moved.
                     *
                     * The tick still advances when an overlay currently outranks
                     * this surface, because reaching past an overlay to touch a
                     * panel has to keep working — that comparison is the whole
                     * reason the tick exists.
                     */
                    if (touchedSurface != surface || overlayClaimedAtTick >= touchedAtTick) {
                        touchedSurface = surface
                        inputTick++
                        touchedAtTick = inputTick
                    }
                    if (focusYieldedToApp) focusYieldedToApp = false
                }
            }
        }

    val sortPicker by viewModel.sortPicker.collectAsState()
    val appDrawer by viewModel.appDrawer.collectAsState()
    val addedPlatforms by viewModel.addedPlatforms.collectAsState()
    val secondScreenOccupied by viewModel.secondScreenOccupied.collectAsState()
    val folderPicker by viewModel.folderPicker.collectAsState()
    val shortcutPanel by viewModel.shortcutPanel.collectAsState()
    val keyboard by viewModel.keyboard.collectAsState()
    val introVisible by viewModel.introVisible.collectAsState()

    /*
     * Permission state, for the first-run list.
     *
     * Collected here as well as inside the settings screen because the list is
     * drawn by the shell, on the grid panel. Re-asked whenever the launcher is
     * resumed, since granting any of these happens in an Android screen the
     * launcher is not on — coming back is the only moment the answer can have
     * changed.
     */
    val isDefaultLauncher by settingsViewModel.isDefaultLauncher.collectAsState()
    val pointerServiceEnabled by settingsViewModel.pointerServiceEnabled.collectAsState()

    LifecycleResumeEffect(settingsViewModel) {
        settingsViewModel.refreshDefaultLauncher()
        settingsViewModel.refreshPointerService()
        onPauseOrDispose { }
    }
    val recording by viewModel.recording.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val navCursor by viewModel.navCursor.collectAsState()
    val couchFocus by viewModel.couchFocus.collectAsState()
    val couchPlatformIndex by viewModel.couchPlatformIndex.collectAsState()
    val couchQuickDetailsEntryId by viewModel.couchQuickDetailsEntryId.collectAsState()
    val couchQuickDetailsActionIndex by viewModel.couchQuickDetailsActionIndex.collectAsState()
    val couchSettingsFocused by viewModel.couchSettingsFocused.collectAsState()
    val context = LocalContext.current

    /*
     * ---- The notification permission, asked for only when it is needed ----------
     *
     * Loki posts exactly one notification and only while a screen recording runs, so
     * asking at first launch would be asking for something the user may never use.
     * It is requested at the moment they start one instead, which is also the moment
     * the reason for it is obvious.
     *
     * Below Android 13 there is nothing to ask: the permission did not exist and is
     * granted by installing.
     */
    val notificationsAllowed: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        /*
         * Granted or not, the recording the user asked for goes ahead.
         *
         * Refusing costs them the notification, not the feature — the recording
         * still runs and still saves, it just has to be stopped from the launcher.
         * Refusing to record at all because they declined a control surface would be
         * punishing them for an answer they were entitled to give.
         */
        ProjectionConsentActivity.request(context)
    }

    val askForNotifications: () -> Unit = {
        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /*
     * The Movies section.
     *
     * Its view model and state are hoisted here because the section spans both
     * panels, and neither of them can own it — on this device either window can
     * be the composition still running while the other is stopped.
     */
    val moviesViewModel: MoviesViewModel = hiltViewModel()
    val streamViewModel: StreamViewModel = hiltViewModel()

    /*
     * The profile cluster's state.
     *
     * Held here rather than inside the panel because the shade has to close when
     * the launcher leaves the foreground — a shade left open behind a game is
     * the first thing seen on returning, over a panel about something else.
     */
    val profileStatusViewModel: ProfileStatusViewModel = hiltViewModel()
    val activeProfile by profileStatusViewModel.profile.collectAsState()
    val profileAvatarPath by profileStatusViewModel.avatarPath.collectAsState()
    val notificationAccess by profileStatusViewModel.access.collectAsState()
    val shadeOpen by profileStatusViewModel.shadeOpen.collectAsState()
    val shellStatus = ShellStatus(
        profile = activeProfile,
        avatarPath = profileAvatarPath,
        notifications = notificationAccess,
        shadeOpen = shadeOpen,
    )
    val shellStatusActions = remember(profileStatusViewModel) {
        ShellStatusActions(
            onToggleShade = profileStatusViewModel::toggleShade,
            onGrantAccess = profileStatusViewModel::requestNotificationAccess,
            onOpenAppInfo = profileStatusViewModel::openAppInfo,
            onNotificationOpened = profileStatusViewModel::openNotification,
            onNotificationDismissed = profileStatusViewModel::dismissNotification,
            onDismissAll = profileStatusViewModel::dismissAllNotifications,
        )
    }
    val moviesSection = rememberMoviesSection(moviesViewModel)
    val moviesState by moviesViewModel.uiState.collectAsState()
    val moviesDetail by moviesViewModel.detail.collectAsState()
    val moviesSources by moviesViewModel.sources.collectAsState()
    val moviesPlayback by moviesViewModel.playback.collectAsState()
    val moviesSettings by moviesViewModel.settings.collectAsState()

    // Read live rather than captured: the collector below outlives any one value.
    val selectedTabNow: () -> LauncherTab = { viewModel.selectedTab.value }
    val trailerDismissedFor by viewModel.trailerDismissedFor.collectAsState()

    // A bumper temporarily shows stills for the current game. Once the cursor
    // leaves it, returning should start its trailer again rather than preserving
    // that one-off choice indefinitely.
    LaunchedEffect(state.selection?.id) {
        viewModel.restoreTrailerForNewSelection(state.selection?.id)
    }

    /*
     * The launcher's own text focus, provided to both windows.
     *
     * Remembered here rather than injected: it must die with the composition, so a
     * field's callback can never outlive the screen that registered it.
     */
    val textInput = remember { ThorTextInputState() }

    // ---- Where the two surfaces live -------------------------------------------
    // Resolved before anything that routes input, because which window a surface is
    // in decides which window has to hold focus for it.
    val secondary = displays.firstOrNull { !it.isPrimary && it.isPresentationCapable }
    val mode = resolveMode(
        requested = settings.display.mode,
        hasSecondary = secondary != null,
        // Only when the user has left the automatic switch on; see the setting.
        hasExternal = settings.display.couchOnExternalDisplay &&
            DisplayTopology.hasExternalDisplay(displays),
    )
    val couchModeNow = rememberUpdatedState(mode == DualScreenMode.COUCH)

    /*
     * ---- Focus -----------------------------------------------------------------
     *
     * One rule, derived rather than tracked, because tracking it in several places is
     * what made it unreliable: the *active surface* is whatever the user is working
     * on, and the window holding that surface is the one that gets key focus and the
     * controller. Everything else follows from those two lines.
     *
     * Every one of them is a **lambda, evaluated on each read**, and that is the
     * single most important thing in this file. Compose pauses a composition's frame
     * clock when its window's lifecycle drops below STARTED, and this composition
     * belongs to the activity — which is stopped for as long as an app covers the
     * activity's *own* display, while the launcher is still fully on screen on the
     * other one. A value computed into a `val` here therefore freezes at whatever it
     * was at the moment of the launch and can never be recomputed, because nothing
     * will recompose to recompute it. That is precisely what made the visible panel
     * stop answering the controller: the user touched it, the touch was received, the
     * state was written — and the derived focus decision on the far side of it never
     * ran again. Read through the snapshot holders instead and every consumer,
     * recomposing or not, sees the current answer.
     */
    /**
     * Couch mode: one window holds both surfaces, and the other panel stays dark.
     *
     * Named here, beside the focus rule, because that is the only place the
     * difference actually matters — [LauncherFocus.windowHolding] answers with one
     * window per panel and cannot say "both", so without this the presentation
     * would be handed the controller the moment the info surface became active,
     * and the controller would be driving a panel showing nothing.
     */
    val singleWindowNow: () -> Boolean = { mode == DualScreenMode.COUCH }

    val gridInActivityWindowNow: () -> Boolean = {
        // In couch mode the grid is in this window along with everything else,
        // whatever `swapScreens` says about a pairing that is not in use.
        singleWindowNow() || settings.display.swapScreens
    }
    val appOnSecondaryPanelNow: () -> Boolean = { secondScreenOccupied }

    /**
     * Whether the window *not* holding the grid is one the user can see.
     *
     * When it is not, the info panel is not drawn at all and the surfaces it hosts
     * move over the grid instead — see `infoOverlays`.
     */
    val infoWindowFreeNow: () -> Boolean = {
        !gridInActivityWindowNow() || !appOnSecondaryPanelNow()
    }

    /**
     * Where the launcher's overlays are actually drawn.
     *
     * Not a constant, which is what it used to be. Settings, search and the editor
     * live on the info surface while that surface has a panel of its own; when an app
     * has taken that panel they are raised over the grid instead. Aiming the
     * controller at the info surface regardless meant opening settings from a grid
     * that had been displaced sent every button to a window the user could not see.
     */
    val overlaySurfaceNow: () -> InputSurface = {
        if (infoWindowFreeNow()) InputSurface.TOP else InputSurface.BOTTOM
    }

    val activeSurfaceNow: () -> InputSurface = {
        when {
            // An overlay claims input the moment it appears, and gives it straight
            // back when it goes — no stored "previous focus" to get out of step,
            // because the fallback *is* the last touched surface.
            //
            // The keyboard is drawn into the grid's surface by construction, so it
            // claims that one wherever the grid happens to be.
            keyboard.visible -> InputSurface.BOTTOM

            // An overlay holds the controller only until the user reaches past it
            // and touches a panel. After that the touch is the more recent
            // instruction and the overlay is something they have left behind.
            (overlay != Overlay.NONE || state.editingEntry != null) &&
                overlayClaimedAtTick >= touchedAtTick -> overlaySurfaceNow()

            else -> touchedSurface
        }
    }

    val overlayIsOpenNow: () -> Boolean = {
        keyboard.visible || overlay != Overlay.NONE || state.editingEntry != null
    }

    /*
     * Whether the second window should hold the device's key focus.
     *
     * The rule itself lives in [LauncherFocus], where it is a pure function with
     * tests against the specific ways this has failed on the hardware. It is the part
     * of the dual-screen design that has had to be re-derived most often, and each
     * time it broke it did so without a crash or a log — so it is worth having
     * somewhere a test can reach.
     */
    val presentationHoldsFocusNow: () -> Boolean = {
        LauncherFocus.presentationTakesFocus(
            activePanel = activeSurfaceNow().toPanel(),
            gridInActivityWindow = gridInActivityWindowNow(),
            overlayOpen = overlayIsOpenNow(),
            focusYieldedToApp = focusYieldedToApp,
            bothPanelsInActivityWindow = singleWindowNow(),
        )
    }

    // Stamped when an overlay appears, so a later touch outranks it.
    val anOverlayIsOpen = overlay != Overlay.NONE || state.editingEntry != null
    LaunchedEffect(anOverlayIsOpen) {
        if (anOverlayIsOpen) {
            inputTick++
            overlayClaimedAtTick = inputTick
        }
    }

    /*
     * Artwork the user picked for a platform folder.
     *
     * Opened here because a document picker is an activity result and belongs to
     * the activity; the view model asks for one and is handed the answer back.
     * Read permission is taken persistently — without it the URI works until the
     * next reboot and then silently resolves to nothing, which looks like the
     * artwork having been forgotten.
     */
    var pendingArtwork by remember { mutableStateOf<LauncherEffect.PickPlatformArtwork?>(null) }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val target = pendingArtwork
        pendingArtwork = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { ThorLog.w("Launcher", "Artwork URI is not persistable: $uri", it) }

        viewModel.setPlatformArtwork(
            platformId = target.platformId,
            iconUri = uri.toString().takeIf { !target.hero },
            heroUri = uri.toString().takeIf { target.hero },
        )
    }

    // Snapshots of the same derivations, for the things this composition draws.
    val activeSurface = activeSurfaceNow()
    val overlayIsOpen = overlayIsOpenNow()
    val infoWindowFree = infoWindowFreeNow()

    /*
     * Yielding focus to something the launcher cannot see.
     *
     * [claimsInputFor] hears every touch that lands on a launcher surface, and none
     * of the ones that do not. When an app is running on a panel, the user touching
     * it produces nothing anywhere in this composition — the app takes the event —
     * so the launcher went on holding the device's focus and the app the user had
     * just reached for received no buttons at all.
     *
     * Losing focus while we were asking for it is that missing signal, and it is
     * evidence rather than inference: some other window has been given focus, and on
     * this device that means the user put a thumb on it. The guard keeps the
     * launcher's own hand-overs out of it — when this window is unfocusable because
     * the *other* launcher panel is active, the loss is one we asked for.
     *
     * Reads the live derivation rather than a captured value: this is called from a
     * window callback, at a moment when the composition that would have refreshed a
     * captured one may have been paused for as long as the app has been running.
     */
    fun onPresentationFocusChanged(hasFocus: Boolean) {
        /*
         * The pointer's own overlay takes focus while the cursor is up, because a
         * focused window is the only place Android delivers the stick. That is a
         * focus loss this panel must not read as "the user reached for an app" —
         * doing so handed the panel away the instant mouse mode was switched on.
         */
        if (mouse.isActive) return

        if (!hasFocus && presentationHoldsFocusNow()) focusYieldedToApp = true
    }

    ThorTheme(
        personalization = settings.personalization,
        accessibility = settings.accessibility,
        performance = settings.performance,
    ) {
        CompositionLocalProvider(LocalThorTextInput provides textInput) {
        val feedback = rememberThorFeedback(
            controls = settings.controls,
            audio = settings.audio,
        )

        /*
         * ---- Text entry ------------------------------------------------------
         * Stops the router consuming keys while a *platform* field is collecting
         * them.
         *
         * The default profile binds W/A/S/D, E, F and Tab so the launcher is
         * operable from a paired keyboard, and `dispatchKeyEvent` sees those before
         * the focused field does — so typing "was" into a text field moved the grid
         * cursor and entered nothing. Nothing at this level can tell a game-pad
         * press from a keyboard press, so the surfaces that own text fields declare
         * when they are active.
         *
         * Nothing in the launcher is one any more: search, settings and the entry
         * editor all use `ThorInputField`, which is filled in by THOR's own keyboard —
         * and that keyboard is driven by this very router, so suspending routing
         * while a field is active would leave it unable to receive a single key.
         *
         * The check survives for the system's own dialogs, which can still put a real
         * IME over the launcher.
         */
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val textInputActive = imeVisible

        LaunchedEffect(textInputActive) {
            inputRouter.setTextInputActive(textInputActive)
        }

        /*
         * First run, in order: permissions, then the walkthrough.
         *
         * The permission list comes first because it is the part that has to
         * happen at a keyboard-and-settings sort of moment, and the walkthrough
         * reads better once the launcher is actually set up. Each is remembered
         * separately — replaying the tour later is not a request to be asked
         * about accessibility again.
         *
         * Both wait for the intro, which is the outermost surface and swallows
         * everything while it runs; opening underneath it would spend the first
         * steps unseen.
         *
         * The main tour is keyed on the stored flag rather than raised once at
         * startup, so the About screen's "Replay" row is the whole of replaying
         * it: clearing the flag brings this back, with no second path to keep in
         * step with the first.
         *
         * An extension's own short tour comes after, and only when the main one
         * is done — someone adding Movies during their first ten minutes should
         * not be handed two walkthroughs at once.
         */
        val unseenExtension = LauncherExtension.entries.firstOrNull { extension ->
            extension.id in settings.enabledExtensions &&
                extension.id !in settings.seenExtensionTours
        }

        LaunchedEffect(
            settings.permissionsPromptSeen,
            settings.tutorialCompleted,
            unseenExtension,
            settingsLoaded,
            introVisible,
        ) {
            if (!settingsLoaded || introVisible) return@LaunchedEffect

            /*
             * Put the launcher back to its resting state before either tour.
             *
             * A tour is read against the home screen, and it is reached from deep
             * inside Settings — so without this it would start over an open
             * settings page, describing the grid while the grid is nowhere in
             * sight. Everything else Home does is wanted here too: menus closed,
             * folders closed, back to the first page and the Home section.
             */
            fun start(extension: LauncherExtension?, steps: List<TutorialStep>) {
                viewModel.goHome()
                settingsViewModel.resetFocus()
                touchedSurface = InputSurface.BOTTOM
                overlay = Overlay.NONE
                tutorialExtension = extension
                tutorialIndex = 0
                tutorialSteps = steps
            }

            when {
                !settings.permissionsPromptSeen -> overlay = Overlay.PERMISSIONS

                !settings.tutorialCompleted ->
                    start(null, ThorTutorial.base(settings.enabledExtensions))

                unseenExtension != null ->
                    start(unseenExtension, ThorTutorial.forExtension(unseenExtension))
            }
        }

        /*
         * The tour driving the launcher, one step at a time.
         *
         * This is what makes it a walkthrough rather than a document: a step
         * naming a settings category opens that category on the information
         * panel, so the reader is looking at the real screen while the card
         * beside them describes it. A step naming none closes settings again.
         *
         * Driven from the step rather than from the handler that advances it, so
         * stepping backwards puts the panel back exactly as it was on the way
         * through — the two directions cannot disagree because only one of them
         * is written down.
         */
        LaunchedEffect(tutorialStep) {
            val category = tutorialStep?.settingsCategory
            when {
                !tutorialRunningNow() -> Unit
                category != null -> {
                    settingsViewModel.selectCategory(category)
                    settingsViewModel.resetFocus()
                    overlay = Overlay.SETTINGS
                }

                overlay == Overlay.SETTINGS -> overlay = Overlay.NONE
            }
        }

        /*
         * One way forward, for the pad and for the button on the card.
         *
         * Both ends of the tour are reachable by touch as well as by controller —
         * the walkthrough runs before the user necessarily knows which button is
         * Confirm — so the two paths have to agree about what "next" means and
         * about which flag gets written when there is no next.
         */
        val advanceTutorial: () -> Unit = advance@{
            if (tutorialIndex < tutorialSteps.lastIndex) {
                tutorialIndex++
                feedback.play(FeedbackCue.NAVIGATE)
                return@advance
            }

            val finished = tutorialExtension
            tutorialSteps = emptyList()
            tutorialExtension = null
            // Leaves the launcher as it found it: a tour that ended on a settings
            // step must not leave that screen open behind the card it removed.
            overlay = Overlay.NONE
            if (finished == null) {
                settingsViewModel.completeTutorial()
            } else {
                settingsViewModel.completeExtensionTour(finished)
            }
            feedback.play(FeedbackCue.SUCCESS)
        }

        /*
         * Tells the pointer to stand aside while THOR's keyboard is up.
         *
         * Distinct from [textInputActive] above, which is about the *platform's*
         * IME and suspends routing entirely. This is the opposite case: THOR's own
         * keyboard is driven by this router, so it needs the keys rather than
         * needing them held back — and while the pointer is up, three separate
         * things were taking them first. See `MouseController.typing`.
         */
        LaunchedEffect(keyboard.visible) {
            mouse.setLauncherTyping(keyboard.visible)
        }

        /*
         * ---- Button tester ---------------------------------------------------
         * The router belongs to the activity, so the settings screen asks for
         * capture through its own state and the shell applies it here.
         */
        val keyCaptureEnabled by settingsViewModel.keyCaptureEnabled
            .collectAsState()

        LaunchedEffect(keyCaptureEnabled) {
            inputRouter.setCaptureMode(keyCaptureEnabled)
        }
        LaunchedEffect(inputRouter) {
            inputRouter.rawKeys.collect(settingsViewModel::onKeyCaptured)
        }

        // Leaving settings must not strand the launcher in capture mode, where
        // every button would be swallowed by a screen that is no longer open.
        LaunchedEffect(overlay) {
            if (overlay != Overlay.SETTINGS) settingsViewModel.setKeyCapture(false)
        }

        // ---- Physical input -------------------------------------------------
        // Input goes to whichever surface the user last touched. Without this,
        // opening settings on the info panel took the controller with it and the
        // grid became unreachable until the overlay was closed.
        /*
         * Keyed on the router alone.
         *
         * `overlay` and `inputSurface` used to be keys, which restarted this collector
         * every time either changed — and a restart re-subscribes to a hot flow, so
         * anything emitted in the gap is gone. Typing made both change, which is the
         * worst possible time to drop an event. They are read live instead: these are
         * snapshot-backed states, so a read inside the coroutine sees the current
         * value without the coroutine having to be torn down to learn it.
         */
        LaunchedEffect(inputRouter) {
            inputRouter.events.collect { event ->
                /*
                 * The intro is the outermost surface, and it runs to its own end.
                 *
                 * Every button is swallowed here rather than acted on: the
                 * sequence used to be dismissible by any press, and no longer is,
                 * but the swallowing is the half that still matters. A live grid
                 * sits underneath, so a press that fell through would move a
                 * cursor nobody can see or launch whatever it landed on.
                 */
                if (viewModel.introVisible.value) return@collect

                /*
                 * The permission list, modal for the same reason the walkthrough
                 * is: it sits over a live grid, and any press that fell through
                 * would launch whatever the cursor happened to be on.
                 *
                 * Confirm is the only command it takes. Its rows are granted by
                 * touch, because each opens an Android settings screen and the
                 * pad has nothing useful to do with a list of doors.
                 */
                if (overlay == Overlay.PERMISSIONS) {
                    if (event.command == ControllerCommand.CONFIRM) {
                        overlay = Overlay.NONE
                        settingsViewModel.dismissPermissionsPrompt()
                        feedback.play(FeedbackCue.CONFIRM)
                    } else {
                        feedback.play(FeedbackCue.REJECT)
                    }
                    return@collect
                }

                /*
                 * The walkthrough, which is modal in the same way the intro is.
                 *
                 * Handled here rather than in the overlay routing below, and that
                 * placement is the fix for it closing after one step. That routing
                 * collapses to `Overlay.NONE` whenever the *grid* is the active
                 * surface — an overlay is expected to have claimed the surface as
                 * it appeared, and this one never did — so Confirm fell through to
                 * the launcher and launched whatever the cursor was sitting on.
                 * The walkthrough did not close; a game opened on top of it.
                 *
                 * It has to stay outermost now for a second reason as well: the
                 * tour opens Settings on the information panel to show a category
                 * off, and a press reaching the settings routing below would drive
                 * that screen instead of the tour that put it there.
                 *
                 * Being outermost also makes "cannot be skipped" true rather than
                 * merely intended: there is no surface, section or panel that can
                 * take a press before this does, so no button leaves early.
                 */
                if (tutorialRunningNow()) {
                    when (event.command) {
                        ControllerCommand.CONFIRM,
                        ControllerCommand.NAVIGATE_RIGHT,
                        -> advanceTutorial()

                        /*
                         * Back steps once and stops at the first.
                         *
                         * It does not leave, because nothing leaves: the only way
                         * out is the last step. A walkthrough with an exit on the
                         * first page is one most people never see past it.
                         */
                        ControllerCommand.BACK,
                        ControllerCommand.NAVIGATE_LEFT,
                        -> {
                            if (tutorialIndex > 0) {
                                tutorialIndex--
                                feedback.play(FeedbackCue.BACK)
                            } else {
                                feedback.play(FeedbackCue.REJECT)
                            }
                        }

                        // Everything else is swallowed rather than passed on. Home,
                        // the shortcut panel and the section bar all sit underneath.
                        else -> feedback.play(FeedbackCue.REJECT)
                    }
                    return@collect
                }

                /*
                 * The keyboard, when it is up, is every button.
                 *
                 * It lives on the grid surface and claims input for it, so the pad
                 * cannot end up typing on one panel while driving an overlay on the
                 * other. Checked before the shortcut button because a stick click
                 * mid-word should be a key press, not a panel over the keyboard.
                 *
                 * Read from the flow rather than from the composition value this
                 * coroutine closed over. A `LaunchedEffect` keeps the state it was
                 * started with until one of its keys changes, so a captured read is
                 * stale for exactly as long as the keyboard has been open without
                 * anything else changing — and a stale `false` here sent every
                 * keystroke to the grid instead, which is precisely what it did.
                 */
                if (viewModel.keyboard.value.visible) {
                    viewModel.onCommand(event.command, event.accelerated)
                    feedback.play(event.command.toKeyboardCue())
                    return@collect
                }

                /*
                 * The Movies section drives itself while it is open.
                 *
                 * Offered the press before the grid, because the section occupies
                 * both panels and its own cursor is the only one on screen — but
                 * only offered it: whatever the section declines still reaches the
                 * shell, so Home, the nav bar and every overlay keep working. A
                 * section that swallowed everything would be a trap with a film
                 * playing in it.
                 */
                if (
                    selectedTabNow() == LauncherTab.MOVIES &&
                    viewModel.navCursor.value == null &&
                    !overlayIsOpenNow()
                ) {
                    if (moviesSection.handleCommand(event.command)) {
                        feedback.play(event.command.toCue())
                        return@collect
                    }
                }

                // Offered the same way, and declines the same way: Up and Down
                // walk the list of PCs, everything else falls through to the
                // shell so the nav bar and Home keep working.
                if (
                    selectedTabNow() == LauncherTab.STREAM &&
                    viewModel.navCursor.value == null &&
                    !overlayIsOpenNow()
                ) {
                    if (streamViewModel.handleCommand(event.command)) {
                        feedback.play(event.command.toCue())
                        return@collect
                    }
                }

                /*
                 * The shortcut button reaches the grid surface from anywhere: its
                 * panel is drawn there, and opening it makes it the active surface,
                 * so the press that opens it also aims the controller at it.
                 */
                if (event.command == ControllerCommand.OPEN_SHORTCUTS) {
                    touchedSurface = InputSurface.BOTTOM
                    viewModel.onCommand(event.command, event.accelerated)
                    feedback.play(FeedbackCue.MENU_OPEN)
                    return@collect
                }

                /*
                 * The info panel driving itself.
                 *
                 * It is the active surface with nothing over it, which only happens by
                 * being touched. Before this the panel could take focus and then had
                 * nothing to do with it — every press fell through to the grid, so
                 * touching the top screen looked like it had done nothing at all. The
                 * panel is a view of one entry, so the controller acts on that entry:
                 * its shots, launching it, and handing the pad back.
                 */
                if (activeSurfaceNow() == InputSurface.TOP && !overlayIsOpenNow()) {
                    when (event.command) {
                        ControllerCommand.NAVIGATE_LEFT ->
                            viewModel.onCommand(ControllerCommand.CYCLE_IMAGE_PREVIOUS, false)

                        ControllerCommand.NAVIGATE_RIGHT ->
                            viewModel.onCommand(ControllerCommand.CYCLE_IMAGE_NEXT, false)

                        // Back is the way out, and the grid is where it goes: this
                        // panel has no history of its own to unwind.
                        ControllerCommand.BACK -> touchedSurface = InputSurface.BOTTOM

                        // Up and down have nothing to move here. Swallowed rather than
                        // passed on, because a panel that holds the controller and
                        // still scrolls the grid behind it is worse than one that
                        // simply does nothing.
                        ControllerCommand.NAVIGATE_UP, ControllerCommand.NAVIGATE_DOWN -> Unit

                        // Everything else — confirm, home, the menus — means the same
                        // here as anywhere, and acts on the entry this panel is
                        // already showing.
                        else -> viewModel.onCommand(event.command, event.accelerated)
                    }
                    feedback.play(
                        when (event.command) {
                            ControllerCommand.BACK -> FeedbackCue.BACK
                            ControllerCommand.NAVIGATE_UP,
                            ControllerCommand.NAVIGATE_DOWN,
                            -> FeedbackCue.REJECT

                            else -> event.command.toCue()
                        },
                    )
                    return@collect
                }

                // The grid handles everything unless the info surface is the active
                // one, which it is exactly while it is showing something.
                val target = if (couchModeNow.value) {
                    // Couch Mode composes every overlay into its one visible
                    // window, so the active surface must not erase its routing.
                    overlay
                } else if (activeSurfaceNow() == InputSurface.BOTTOM) {
                    Overlay.NONE
                } else {
                    overlay
                }

                when (target) {
                    Overlay.NONE -> {
                        viewModel.onCommand(
                            command = event.command,
                            accelerated = event.accelerated,
                            couchMode = couchModeNow.value,
                        )
                        feedback.play(event.command.toCue())
                    }

                    /*
                     * Never reached, and named rather than folded into an `else`.
                     *
                     * The permission list takes its own presses at the top of this
                     * collector and returns, as the walkthrough does — precisely
                     * because this routing is what broke the walkthrough: `target`
                     * collapses to `NONE` whenever the grid is the active surface,
                     * which sent Confirm to the launcher and launched whatever was
                     * under the cursor. An `else` here would let a future overlay
                     * inherit that bug in silence; this way the compiler asks.
                     */
                    Overlay.PERMISSIONS -> Unit

                    Overlay.SETTINGS -> {
                        if (couchModeNow.value && (
                                event.command == ControllerCommand.CYCLE_IMAGE_PREVIOUS ||
                                    event.command == ControllerCommand.CYCLE_IMAGE_NEXT
                                )
                        ) {
                            // Settings is the final destination in the same bumper
                            // sequence as Stream, Home, and Movies.
                            if (settingsViewModel.isAddingPlatform) {
                                settingsViewModel.cancelAddPlatform()
                            }
                            val delta = if (
                                event.command == ControllerCommand.CYCLE_IMAGE_PREVIOUS
                            ) -1 else 1
                            val settingsStillSelected =
                                viewModel.cycleCouchDestination(delta, fromSettings = true)
                            if (!settingsStillSelected) {
                                overlay = Overlay.NONE
                                settingsViewModel.resetFocus()
                            }
                            feedback.play(FeedbackCue.PAGE)
                        } else if (event.command == ControllerCommand.BACK) {
                            // Back unwinds one level at a time: an open page
                            // first, then the overlay. Closing outright from a
                            // page would lose the user's place in the rail.
                            if (settingsViewModel.isAddingPlatform) {
                                settingsViewModel.cancelAddPlatform()
                            } else if (settingsViewModel.isAtTopLevel) {
                                overlay = Overlay.NONE
                                settingsViewModel.resetFocus()
                                if (couchModeNow.value) viewModel.leaveNavBar()
                            } else {
                                settingsViewModel.closePage()
                            }
                            feedback.play(FeedbackCue.BACK)
                        } else {
                            // The pane knows how many rows it drew; the view
                            // model needs that to clamp downward navigation.
                            val consumed = settingsViewModel.onControllerCommand(
                                command = event.command,
                                rowCount = settingsRowCount,
                            )
                            feedback.play(
                                when {
                                    // Confirm inside a page is reported as
                                    // unconsumed so the row's own control acts on
                                    // it — that is still a confirmation.
                                    event.command == ControllerCommand.CONFIRM ->
                                        FeedbackCue.CONFIRM

                                    consumed -> FeedbackCue.NAVIGATE
                                    else -> FeedbackCue.REJECT
                                },
                            )
                        }
                    }


                    Overlay.SEARCH -> {
                        // Without this the app drawer opens from a dock slot
                        // and can only be closed again — the results list is
                        // unreachable from a pad.
                        when (event.command) {
                            ControllerCommand.BACK -> {
                                overlay = Overlay.NONE
                                feedback.play(FeedbackCue.BACK)
                            }

                            ControllerCommand.NAVIGATE_UP -> {
                                searchViewModel.moveFocus(-1)
                                feedback.play(FeedbackCue.NAVIGATE)
                            }

                            ControllerCommand.NAVIGATE_DOWN -> {
                                searchViewModel.moveFocus(1)
                                feedback.play(FeedbackCue.NAVIGATE)
                            }

                            ControllerCommand.CONFIRM -> {
                                searchViewModel.focusedEntry()?.let { entry ->
                                    overlay = Overlay.NONE
                                    viewModel.launchEntry(entry)
                                    feedback.play(FeedbackCue.LAUNCH)
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }

        /*
         * ---- Cold-start intro ------------------------------------------------
         *
         * One animation, handed to both panels, so the two windows run the sequence
         * as a single thing rather than as two that happen to look alike.
         *
         * Staged rather than one long tween: the chime in the sample lands where the
         * mark does, and the reveal is given its own segment so the launcher appears
         * on a beat instead of whenever the curve happens to finish.
         */
        val introProgress = remember { Animatable(0f) }
        val introMotion = ThorTheme.materials.animationsEnabled &&
            !settings.performance.performanceMode

        if (introVisible && settingsLoaded) {
            LaunchedEffect(Unit) {
                var openingStream: Int? = null
                val openingSound = launch {
                    openingStream = feedback.playWhenReady(
                        if (introMotion) FeedbackCue.BOOT else FeedbackCue.SUCCESS,
                    )
                }
                try {
                    if (introMotion) {
                        introProgress.animateTo(
                            targetValue = INTRO_LOAD_START,
                            animationSpec = tween(
                                durationMillis = INTRO_MARK_MS,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        val loadingFeedback = launch {
                            delay(INTRO_LOAD_FIRST_CUE_MS.toLong())
                            feedback.play(FeedbackCue.SCROLL)
                            delay(
                                (INTRO_LOAD_SECOND_CUE_MS - INTRO_LOAD_FIRST_CUE_MS).toLong(),
                            )
                            feedback.play(FeedbackCue.SCROLL)
                        }
                        introProgress.animateTo(
                            targetValue = INTRO_LOADED,
                            animationSpec = tween(
                                durationMillis = INTRO_LOAD_MS,
                                easing = LinearEasing,
                            ),
                        )
                        loadingFeedback.cancel()
                        feedback.play(FeedbackCue.SUCCESS)
                        delay(INTRO_READY_HOLD_MS.toLong())
                        introProgress.animateTo(
                            targetValue = INTRO_REVEAL_START,
                            animationSpec = tween(
                                durationMillis = INTRO_READY_SETTLE_MS,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        feedback.play(FeedbackCue.HOME)
                        introProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = INTRO_REVEAL_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                        )
                    } else {
                        introProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = INTRO_REDUCED_MS,
                                easing = LinearOutSlowInEasing,
                            ),
                        )
                    }
                    viewModel.finishIntro()
                } finally {
                    openingSound.cancel()
                    // ui_boot is 1.5 seconds long, and must not be left playing
                    // over the launcher once the overlay has gone.
                    feedback.stopSound(openingStream)
                }
            }
        }

        /**
         * The intro, ready to be laid over whichever surfaces are on screen.
         *
         * A lambda rather than a single overlay because the two panels are separate
         * windows — nothing can cover both — so each draws its own, from the one
         * progress value.
         */
        val introOverlay: @Composable () -> Unit = {
            if (introVisible) {
                ThorIntro(
                    progress = introProgress.value,
                    motion = introMotion,
                )
            }
        }

        /*
         * ---- Typed text ------------------------------------------------------
         * Every keystroke goes straight to the search view model, so the results on
         * the info panel build as the user types on the grid panel. Forwarded here
         * rather than from the launcher state holder, which has no business knowing
         * that a search screen exists.
         */
        /*
         * A field claimed text focus, so the keyboard comes up filled in with what is
         * already there. Releasing focus takes it away again.
         *
         * This is the whole connection between the two: the keyboard knows nothing
         * about search, or settings, or the entry editor — it edits a buffer, and
         * whatever holds focus receives it.
         */
        LaunchedEffect(textInput.focusedId) {
            val id = textInput.focusedId
            if (id == null) {
                viewModel.closeKeyboard()
            } else {
                viewModel.openKeyboard(label = textInput.label, initial = textInput.initialText)
            }
        }

        /*
         * Every keystroke goes straight to the field being filled in, so it fills
         * in live on whichever panel it is drawn on while the keyboard stays on
         * this one.
         *
         * Which field that is depends on whether one of Loki's own claimed the
         * keyboard. If it did, the text goes there. If nothing did, the keyboard
         * was raised by the pointer over another app — so the text goes outward
         * instead, to whatever field the cursor last tapped in that app.
         *
         * They cannot both fire: a field inside Loki is not one Android holds
         * input focus on, and a field in another app cannot claim Loki's text
         * focus. `focusedId` is the one question that separates them.
         */
        LaunchedEffect(keyboard.text, keyboard.visible) {
            if (!keyboard.visible) return@LaunchedEffect
            if (textInput.focusedId != null) {
                textInput.setText(keyboard.text)
            } else {
                mouse.setTypedText(keyboard.text)
            }
        }

        /*
         * Closing the keyboard releases the field with it — a caret left blinking on a
         * field nothing is typing into is a lie — and hands the controller to the
         * results, which are on the other panel. Without that, Back after typing went
         * to the grid behind the overlay instead of closing it.
         */
        LaunchedEffect(keyboard.visible) {
            if (keyboard.visible) return@LaunchedEffect
            textInput.release()
        }

        // A message is a notice, not a state: it goes away on its own so the grid is
        // never left wearing one.
        LaunchedEffect(transientMessage) {
            if (transientMessage == null) return@LaunchedEffect
            delay(MESSAGE_DURATION_MS)
            transientMessage = null
        }

        // ---- Home button -----------------------------------------------------
        // Home has to reset *both* surfaces: the grid back to page one and every
        // overlay closed, on the info panel too. Returning to the launcher still
        // showing an open settings page is not what Home means anywhere else.
        LaunchedEffect(homeRequests) {
            homeRequests.collect {
                overlay = Overlay.NONE
                settingsViewModel.resetFocus()
                touchedSurface = InputSurface.BOTTOM
                focusYieldedToApp = false
                viewModel.goHome()
                feedback.play(FeedbackCue.HOME)
            }
        }

        /*
         * ---- An app on this window's own panel has exited --------------------
         *
         * Resume is the closest thing to a dependable signal. An app launched onto
         * this window's display covers it, so being resumed again means that app is
         * gone — time to credit the play session and put the panel back to the grid.
         *
         * Window focus and top-resumed status were both tried here and are both
         * wrong: they are handed over by *touching* the launcher's other panel,
         * which is precisely what the user does while continuing to play, and acting
         * on them evicted the running app the moment the second screen was touched.
         *
         * Resume alone is not enough either, because on this device a launch onto
         * the *other* display also pauses this activity while leaving it visible —
         * so touching it resumes us with the app still running. Skipping the whole
         * thing while a panel is known to be occupied is what keeps a play session
         * open for its real duration; Home is what ends it in that case.
         */
        LifecycleResumeEffect(secondScreenOccupied) {
            if (!secondScreenOccupied) {
                viewModel.settlePlaytime()
            }
            onPauseOrDispose { }
        }

        // ---- Start panel selections -----------------------------------------
        LaunchedEffect(viewModel) {
            viewModel.sideMenuSelections.collect { action ->
                when (action) {
                    SideMenuAction.APPS -> viewModel.openAppDrawer()

                    // Sorts the grid in place rather than opening settings —
                    // "Sort" that took you to a settings page was doing the one
                    // thing it should not.
                    SideMenuAction.SORT -> viewModel.openSortPicker()

                    SideMenuAction.NEW -> viewModel.createFolder()

                    SideMenuAction.GRID -> viewModel.enterArrangeMode()

                    SideMenuAction.SETTINGS -> {
                        settingsViewModel.selectCategory(SettingsCategory.APPEARANCE)
                        overlay = Overlay.SETTINGS
                    }
                }
                // Each row opens a different kind of surface, so each gets the
                // cue for what it actually opened rather than a generic confirm.
                feedback.play(
                    when (action) {
                        SideMenuAction.APPS -> FeedbackCue.DRAWER_OPEN
                        SideMenuAction.SETTINGS -> FeedbackCue.SETTINGS_OPEN
                        SideMenuAction.NEW -> FeedbackCue.SUCCESS
                        SideMenuAction.SORT, SideMenuAction.GRID -> FeedbackCue.MENU_OPEN
                    },
                )
                viewModel.closeSideMenu()
            }
        }

        /*
         * The stream window opens once the host has agreed to a session.
         *
         * Started from here rather than from the section, because starting an
         * activity needs a context and a section is a composable that draws into
         * two windows. The session itself is already held in the process — this
         * intent carries nothing but the instruction to show it.
         */
        LaunchedEffect(streamViewModel) {
            streamViewModel.effects.collect { effect ->
                when (effect) {
                    StreamEffect.OpenSession -> context.startActivity(
                        Intent(context, StreamSessionActivity::class.java),
                    )
                }
            }
        }

        // ---- One-shot effects ------------------------------------------------
        LaunchedEffect(viewModel) {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    // Opening either of these makes the info surface the active one,
                    // which aims the controller at it — no handover to arrange.
                    LauncherEffect.OpenSettings -> {
                        overlay = Overlay.SETTINGS
                        feedback.play(FeedbackCue.SETTINGS_OPEN)
                    }

                    LauncherEffect.OpenSearch -> {
                        overlay = Overlay.SEARCH
                        feedback.play(FeedbackCue.DRAWER_OPEN)
                    }

                    /*
                     * A screen recording, which needs two things asked for first.
                     *
                     * The notification permission, because on Android 13 and later
                     * the recording's only control while the user is inside a game
                     * is its notification, and a denied permission does not fail —
                     * it silently posts nothing. A recording that cannot be stopped
                     * is worse than one that was never offered.
                     *
                     * Then the projection itself, which only a system dialog can
                     * grant and only to an activity result.
                     */
                    LauncherEffect.StartScreenRecording -> {
                        feedback.play(FeedbackCue.CONFIRM)
                        if (notificationsAllowed()) {
                            ProjectionConsentActivity.request(context)
                        } else {
                            askForNotifications()
                        }
                    }

                    /*
                     * The lock is released so the app can take focus on the display it
                     * is arriving on. The tap that started it was a launch, not a
                     * request to hold that panel — and a launcher window still holding
                     * focus is exactly what leaves an app running with a controller
                     * that does nothing.
                     *
                     * Only for a launch onto the panel the *presentation* projects
                     * onto, though. Standing down for an app arriving on the other
                     * display gave away the controller for a window that was not
                     * competing for it, which left the panel still on screen — the one
                     * the user was holding — visible, alive and completely deaf, with
                     * nothing but Home able to get it back.
                     */
                    is LauncherEffect.Launched ->
                        if (LauncherFocus.launchYieldsPresentationFocus(effect.onSecondaryPanel)) {
                            focusYieldedToApp = true
                        }

                    /*
                     * The claim comes back with the panel.
                     *
                     * The view model has established that nothing ever took the
                     * display it stood the presentation down for. Restoring the
                     * window without restoring this leaves the grid on screen and
                     * deaf — see [LauncherEffect.LaunchAbandoned].
                     */
                    LauncherEffect.LaunchAbandoned -> {
                        focusYieldedToApp = false
                        touchedSurface = InputSurface.BOTTOM
                    }

                    /*
                     * The dock has offered this since the beginning and it did
                     * nothing: there is no public intent for the power dialog, so
                     * the shell had no way to carry out what the user picked. The
                     * pointer service can — `performGlobalAction` is an
                     * accessibility API — so the request goes there, and says so
                     * plainly when that service is not running rather than being
                     * silently dropped a second time.
                     */
                    is LauncherEffect.PickPlatformArtwork -> {
                        pendingArtwork = effect
                        artworkPicker.launch(arrayOf("image/*"))
                    }

                    LauncherEffect.OpenPowerMenu ->
                        if (mouse.serviceConnected.value) {
                            mouse.requestPowerMenu()
                        } else {
                            transientMessage =
                                "The power menu needs Loki's accessibility service. " +
                                "Settings → Controls → Pointer."
                            feedback.play(FeedbackCue.ERROR)
                        }

                    // Said out loud, not just sounded. A recording that reports where
                    // it saved the file only in a log has not reported it.
                    is LauncherEffect.LaunchFailed -> {
                        transientMessage = effect.reason
                        feedback.play(FeedbackCue.ERROR)
                    }

                    is LauncherEffect.ShowMessage -> {
                        transientMessage = effect.message
                        feedback.play(FeedbackCue.SUCCESS)
                    }
                }
            }
        }

        /*
         * The system the selection belongs to.
         *
         * Folders resolve too, not just games: a platform's folder is the cell that
         * *is* that system on the grid, so highlighting it should back the info
         * panel with that system's hero rather than with the generic wallpaper —
         * which is exactly what an icon pack is for.
         */
        val selectedPlatform = when (val selection = state.selection) {
            is GameEntry -> state.platformsById[selection.platformId]
            is FolderEntry -> PlatformFolders.platformIdOf(selection.id)
                ?.let { state.platformsById[it] }

            else -> null
        }

        /*
         * The surfaces the info panel *hosts*, kept separate from the panel itself.
         *
         * They have to be able to follow the grid onto the other panel: while an app
         * occupies the info panel's own display, a settings screen composed into that
         * window is behind the app, so opening settings appeared to do nothing at all
         * while still routing the controller to it. Splitting them out means they are
         * composed exactly once, in whichever window the user can actually see.
         */
        val infoOverlays: @Composable () -> Unit = {
            /*
             * The entry editor belongs to this surface, not to the grid's.
             *
             * It is the launcher's only free-text form, and an IME will not
             * reliably render on a secondary display — a `Presentation` there is
             * also unfocusable by default, so its fields could not even take
             * focus. The info surface is normally the activity's own window on the
             * default display, which is where the keyboard lives. Opening on the
             * other panel from where the long press happened is a smaller cost
             * than a form that cannot be typed into.
             */
            EditEntryDialog(
                entry = state.editingEntry,
                onConfirm = { edits ->
                    state.editingEntry?.let { entry ->
                        viewModel.applyEdits(entryId = entry.id, edits = edits)
                    }
                },
                onDismiss = viewModel::closeEditor,
                // Only systems the user has added: reassigning a game to a
                // console they do not own would leave it unlaunchable.
                platforms = addedPlatforms,
                emulatorOptions = viewModel.emulatorOptionsFor(state.editingEntry),
            )

            AnimatedVisibility(
                visible = overlay != Overlay.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                when (overlay) {
                    Overlay.SETTINGS -> if (mode != DualScreenMode.COUCH) {
                        SettingsScreen(
                            onRowCountChanged = { settingsRowCount = it },
                            viewModel = settingsViewModel,
                        )
                    }

                    Overlay.SEARCH -> SearchScreen(
                        onEntrySelected = { entry ->
                            overlay = Overlay.NONE
                            viewModel.launchEntry(entry)
                        },
                        onDismiss = { overlay = Overlay.NONE },
                        viewModel = searchViewModel,
                    )

                    // Drawn on the grid panel instead; see `bottomContent`. This
                    // one is answered by touching its rows, so it belongs on the
                    // panel the user is holding rather than on the one they read.
                    Overlay.PERMISSIONS -> Unit

                    Overlay.NONE -> Unit
                }
            }

            /*
             * The walkthrough's share of this panel, over whatever it opened.
             *
             * Outside the `AnimatedVisibility` above because it is not one of the
             * overlays — it is the thing that opens them. A step about a settings
             * category has Settings underneath it here and its card on the other
             * screen; a step about this panel itself has its card here.
             */
            TutorialScreen(
                steps = tutorialSteps,
                index = tutorialIndex,
                panel = TutorialPanel.INFO,
                onBack = { if (tutorialIndex > 0) tutorialIndex-- },
                onNext = advanceTutorial,
            )
        }

        /** The info panel: game artwork and details, plus whatever it is hosting. */
        val topContent: @Composable () -> Unit = {
            // Touching this surface claims the controller for it, wherever it is drawn
            // — its own panel in dual mode, half the window in split.
            Box(modifier = Modifier.fillMaxSize().claimsInputFor(InputSurface.TOP)) {
                /*
                 * Movies owns this panel outright while its tab is open.
                 *
                 * Not drawn over the game panel but instead of it: the section is
                 * a library and a player, and showing either behind the other
                 * would put two unrelated pictures on one screen.
                 */
                if (selectedTab == LauncherTab.MOVIES) {
                    /*
                     * Collected here, inside the panel, rather than once at the
                     * top of the shell.
                     *
                     * A value collected in the activity's composition and handed
                     * to the other window is one the other window stops seeing
                     * the moment the activity's composition pauses — which it
                     * does whenever an app covers that display. Each panel
                     * collects the player's status in its own composition, so
                     * each keeps up with it for as long as its own window exists.
                     */
                    val moviesStatus by moviesViewModel.playerStatus.collectAsState()

                    MoviesTopPanel(
                        mode = moviesSection.mode,
                        state = moviesState,
                        playback = moviesPlayback,
                        player = moviesViewModel.player,
                        status = moviesStatus,
                        onTypeSelected = moviesViewModel::switchType,
                        onItemSelected = moviesSection::pickTitle,
                    )
                    infoOverlays()
                    if (mode == DualScreenMode.DUAL_DISPLAY) introOverlay()
                    return@Box
                }

                /*
                 * Stream owns this panel outright while its tab is open, for the
                 * same reason Movies does: the section is a list of machines, not
                 * an overlay on a game's detail view, and showing both would put
                 * two unrelated subjects on one screen.
                 */
                if (selectedTab == LauncherTab.STREAM) {
                    // In this panel's own composition; see the note above.
                    val streamState by streamViewModel.uiState.collectAsState()

                    StreamTopPanel(
                        state = streamState,
                        onHostSelected = streamViewModel::selectHost,
                        modifier = Modifier.fillMaxSize(),
                    )
                    infoOverlays()
                    if (mode == DualScreenMode.DUAL_DISPLAY) introOverlay()
                    return@Box
                }

                TopScreen(
                    selection = state.selection,
                    platform = selectedPlatform,
                    wallpaper = settings.personalization.animatedWallpaper,
                    wallpaperUri = settings.personalization.topScreenWallpaperUri
                        ?: settings.personalization.wallpaperUri,
                    /*
                     * The *highlighted* folder's contents, not the open one's.
                     *
                     * `openFolderContents` is populated only while a folder has
                     * been entered, and the platform panel is shown when a
                     * folder is merely rested on — so it was always empty there
                     * and every system reported nothing in it. "0 games" was
                     * literally true of the list it was counting.
                     */
                    folderChildren = state.openFolderContents.ifEmpty {
                        (state.selection as? FolderEntry)
                            ?.childIds
                            ?.mapNotNull(state.entriesById::get)
                            .orEmpty()
                    },
                    clockStyle = settings.personalization.clockStyle,
                    showStatusBar = settings.personalization.showStatusBar,
                    // Trailer playback is an explicit user preference. Performance
                    // mode reduces interface effects, but must not silently replace
                    // a successfully fetched trailer with screenshots.
                    videoPreviewsEnabled = settings.personalization.autoplayTrailers &&
                        trailerDismissedFor != state.selection?.id,
                    selectedScreenshot = selectedScreenshot,
                    onScreenshotSelected = viewModel::setScreenshot,
                    onEntrySelected = viewModel::launchEntry,
                    // Only when this panel is holding the controller *itself*. An
                    // overlay drawn over it has its own focus to show, and two focus
                    // treatments on one panel would contradict each other.
                    focused = activeSurface == InputSurface.TOP && !overlayIsOpen,
                    status = shellStatus,
                    statusActions = shellStatusActions,
                )
                infoOverlays()

                // Only where this surface has a panel of its own. In the split and
                // single-screen modes both surfaces share one window, and two intros
                // stacked in it would read as a mirror rather than as one launcher
                // starting.
                if (mode == DualScreenMode.DUAL_DISPLAY) introOverlay()
            }
        }

        /*
         * The non-Home sections, hoisted so both screens host the same ones.
         *
         * Couch mode draws its own layout and still has to be able to show
         * Movies and Stream; without this the lambda would be written twice
         * and the two copies would start to differ on the first edit.
         *
         * Supplied from here rather than from the home module, so a feature
         * module never has to depend on an unrelated one.
         */
        val sectionHost: @Composable (LauncherTab) -> Unit = { tab ->
                    if (tab == LauncherTab.MOVIES) {
                        // In this panel's own composition; see the note beside the
                        // matching collection in the info panel above.
                        val moviesStatus by moviesViewModel.playerStatus.collectAsState()

                        if (mode == DualScreenMode.COUCH) {
                            MoviesCouchScreen(
                                mode = moviesSection.mode,
                                state = moviesState,
                                detail = moviesDetail,
                                sources = moviesSources,
                                playback = moviesPlayback,
                                player = moviesViewModel.player,
                                status = moviesStatus,
                                focusedSource = moviesSection.focusedSource,
                                focusedAction = moviesSection.focusedAction,
                                hasNextEpisode = moviesViewModel.nextEpisode() != null,
                                skipSeconds = moviesSettings.skipSeconds,
                                onTypeSelected = moviesViewModel::switchType,
                                onItemSelected = moviesSection::pickTitle,
                                onPlayerAction = moviesSection::perform,
                                onSeek = moviesSection::seekTo,
                                onSourcePicked = moviesSection::pickSource,
                                onSeasonSelected = moviesSection::pickSeason,
                                onEpisodeSelected = moviesSection::pickEpisode,
                                query = moviesState.query,
                                onQueryChanged = moviesViewModel::onQueryChanged,
                                searchRequested = moviesSection.searchRequested,
                                onSearchFocused = moviesSection::onSearchFocused,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            MoviesBottomPanel(
                                mode = moviesSection.mode,
                                detail = moviesDetail,
                                sources = moviesSources,
                                playback = moviesPlayback,
                                status = moviesStatus,
                                focusedSource = moviesSection.focusedSource,
                                focusedAction = moviesSection.focusedAction,
                                hasNextEpisode = moviesViewModel.nextEpisode() != null,
                                skipSeconds = moviesSettings.skipSeconds,
                                onPlayerAction = moviesSection::perform,
                                onSeek = moviesSection::seekTo,
                                onSourcePicked = moviesSection::pickSource,
                                onSeasonSelected = moviesSection::pickSeason,
                                onEpisodeSelected = moviesSection::pickEpisode,
                                query = moviesState.query,
                                onQueryChanged = moviesViewModel::onQueryChanged,
                                searchRequested = moviesSection.searchRequested,
                                onSearchFocused = moviesSection::onSearchFocused,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (tab == LauncherTab.STREAM) {
                        val streamState by streamViewModel.uiState.collectAsState()
                        val clientName by streamViewModel.clientName.collectAsState()

                        if (mode == DualScreenMode.COUCH) {
                            StreamCouchScreen(
                                state = streamState,
                                clientName = clientName,
                                onHostSelected = streamViewModel::selectHost,
                                onAddressChanged = streamViewModel::onAddressChanged,
                                onAddHost = streamViewModel::addTypedHost,
                                onRefreshHost = streamViewModel::refresh,
                                onStartStream = streamViewModel::shareScreen,
                                onPairHost = streamViewModel::pair,
                                onCancelPairing = streamViewModel::cancelPairing,
                                onStopStream = streamViewModel::stopHostSession,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            StreamBottomPanel(
                                state = streamState,
                                clientName = clientName,
                                onAddressChanged = streamViewModel::onAddressChanged,
                                onAddHost = streamViewModel::addTypedHost,
                                onRefreshHost = streamViewModel::refresh,
                                onStartStream = streamViewModel::shareScreen,
                                onPairHost = streamViewModel::pair,
                                onCancelPairing = streamViewModel::cancelPairing,
                                onStopStream = streamViewModel::stopHostSession,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        EmptySection(tab = tab, modifier = Modifier.fillMaxSize())
                    }
        }
        val bottomContent: @Composable (Modifier) -> Unit = { bottomModifier ->
            Box(
                modifier = bottomModifier
                    // Any touch anywhere on this panel hands the controller back to it,
                    // and takes back the focus a launch gave away. Registered in the
                    // initial pass so it sees the event before the grid consumes it.
                    .claimsInputFor(InputSurface.BOTTOM),
            ) {
            BottomScreen(
                state = state,
                dockSettings = settings.dock,
                wallpaper = settings.personalization.animatedWallpaper,
                wallpaperUri = settings.personalization.wallpaperUri,
                showPageIndicators = settings.personalization.showPageIndicators,
                currentSort = settings.library.defaultSort,
                focusedDockSlot = null,
                focusedMenuAction = SideMenuAction.entries.getOrNull(state.sideMenuIndex),
                onCellTapped = { row, column ->
                    feedback.play(FeedbackCue.NAVIGATE)
                    viewModel.setCursor(row, column)
                    viewModel.confirm()
                },
                onCellLongPressed = { row, column ->
                    // Long press is the touch equivalent of Y: it opens the
                    // context menu, which is where "move" now lives alongside
                    // everything else. Picking an icon straight up on long
                    // press made the other actions unreachable by touch.
                    feedback.play(FeedbackCue.PICK_UP)
                    viewModel.setCursor(row, column)
                    viewModel.openContextMenu()
                },
                onPageChanged = viewModel::setPage,
                onPinch = { zoom -> viewModel.updateGridSpec { it.pinched(zoom) } },
                onDockSlotSelected = { },
                onDockAction = viewModel::performAction,
                // Touch and controller both funnel through the view model so a
                // row does the same thing however it was chosen.
                onMenuAction = viewModel::selectSideMenuAction,
                onMenuDismissed = viewModel::closeSideMenu,
                onContextAction = viewModel::performContextAction,
                onContextMenuDismissed = viewModel::closeContextMenu,
                appDrawer = appDrawer,
                onDrawerCellTapped = { row, column ->
                    viewModel.setDrawerCursor(row, column)
                    viewModel.confirmDrawerSelection()
                },
                onDrawerCellLongPressed = { row, column ->
                    feedback.play(FeedbackCue.PICK_UP)
                    viewModel.openDrawerContextMenu(row, column)
                },
                onDrawerPageChanged = viewModel::setDrawerPage,
                sortPicker = sortPicker,
                onSortPicked = viewModel::sortGrid,
                onSortDirectionToggled = viewModel::toggleSortDirection,
                onSortDismissed = viewModel::closeSortPicker,
                folderPicker = folderPicker,
                onFolderPicked = viewModel::fileIntoFolder,
                onFolderCreated = viewModel::fileIntoNewFolder,
                onFolderPickerDismissed = viewModel::closeFolderPicker,
                foldersExist = state.entriesById.values.any { it is FolderEntry && !it.isSmart },
                entryInFolder = state.contextMenuEntry
                    ?.let { viewModel.folderContaining(it.id) != null } == true,
                onFolderClosed = viewModel::closeFolder,
                selectedTab = selectedTab,
                navCursor = navCursor,
                onTabSelected = { tab ->
                    if (mode == DualScreenMode.COUCH && overlay == Overlay.SETTINGS) {
                        overlay = Overlay.NONE
                        settingsViewModel.resetFocus()
                    }
                    viewModel.selectTab(tab)
                    if (mode == DualScreenMode.COUCH) viewModel.leaveNavBar()
                },
                couchFocus = couchFocus,
                couchPlatformIndex = couchPlatformIndex,
                couchQuickDetailsEntryId = couchQuickDetailsEntryId,
                couchQuickDetailsActionIndex = couchQuickDetailsActionIndex,
                couchSettingsFocused = couchSettingsFocused,
                couchSettingsSelected = mode == DualScreenMode.COUCH &&
                    overlay == Overlay.SETTINGS,
                couchClockStyle = settings.personalization.clockStyle,
                showCouchStatusBar = settings.personalization.showStatusBar,
                couchUiScale = settings.display.couchUiScale,
                onCouchEntryFocused = viewModel::focusCouchEntry,
                onCouchEntrySelected = viewModel::launchEntry,
                onCouchEntryLongPressed = viewModel::openContextMenu,
                onCouchPlatformSelected = viewModel::selectCouchPlatform,
                onCouchDetailsPlay = { entry ->
                    viewModel.closeCouchQuickDetails()
                    viewModel.launchEntry(entry)
                },
                onCouchDetailsFavorite = viewModel::toggleFavorite,
                onCouchDetailsMore = { entry ->
                    viewModel.closeCouchQuickDetails()
                    viewModel.openContextMenu(entry)
                },
                onCouchDetailsDismissed = viewModel::closeCouchQuickDetails,
                onCouchSettingsSelected = viewModel::openCouchSettings,
                couchFullscreenSection = selectedTab == LauncherTab.MOVIES &&
                    moviesSection.mode == MoviesMode.PLAYING,
                couchSettingsContent = {
                    SettingsScreen(
                        onRowCountChanged = { settingsRowCount = it },
                        viewModel = settingsViewModel,
                        couchMode = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                // The other half of the Movies section: describe, choose, or
                // control, matching whatever its top panel is showing.
                sectionContent = sectionHost,
                couchMode = mode == DualScreenMode.COUCH,
                modifier = Modifier.fillMaxSize(),
            )

            /*
             * The keyboard, over everything else on this panel.
             *
             * On this surface rather than the info panel deliberately: this is the
             * screen the user is holding the controller for, and it is the one the
             * platform IME could never appear on — which is the whole reason the
             * launcher has a keyboard of its own. Typing here, results on the other
             * panel, is what two screens are for.
             */
            if (keyboard.visible) {
                ThorKeyboard(
                    text = keyboard.text,
                    label = keyboard.label,
                    layer = keyboard.layer,
                    shifted = keyboard.shifted,
                    cursorRow = keyboard.cursorRow,
                    cursorColumn = keyboard.cursorColumn,
                    onKey = { key ->
                        viewModel.onKeyboardKey(key)
                        // Touch gets the same cue the pad does, so a tapped key feels
                        // like a pressed one.
                        feedback.play(key.toCue())
                    },
                    onDismiss = viewModel::closeKeyboard,
                    // Null while closed: the sheet's presence *is* its visibility,
                    // so there is no second flag for the two to disagree about.
                    clips = keyboard.clips.takeIf { keyboard.clipboardOpen },
                    clipIndex = keyboard.clipIndex,
                    onPasteClip = { clip ->
                        viewModel.pasteClip(clip)
                        feedback.play(FeedbackCue.CONFIRM)
                    },
                    onCopyText = {
                        viewModel.copyFieldText()
                        feedback.play(FeedbackCue.SUCCESS)
                    },
                )
            }

            // Last, so it draws over everything else on this panel: the AYN button
            // has to reach the launcher while a game is running, which is the
            // situation the panel exists for.
            ShortcutPanel(
                visible = shortcutPanel.visible,
                actions = shortcutPanel.actions,
                focusedIndex = shortcutPanel.focusedIndex,
                onAction = viewModel::onShortcut,
                onDismiss = viewModel::closeShortcutPanel,
            )

            /*
             * The walkthrough's share of this panel.
             *
             * The grid stays visible underneath and is dimmed around whatever the
             * current step is pointing at, rather than covered. An earlier version
             * put a solid panel over it and drew every step on the far screen,
             * which meant the steps about the grid — most of them — were read with
             * the grid hidden. Explaining a thing while hiding it is the fault
             * this whole arrangement exists to fix.
             *
             * Ordering matters here as it does below: this is a `Box`, and a `Box`
             * draws its children in the order they are declared.
             */
            TutorialScreen(
                steps = tutorialSteps,
                index = tutorialIndex,
                panel = TutorialPanel.GRID,
                onBack = { if (tutorialIndex > 0) tutorialIndex-- },
                onNext = advanceTutorial,
            )

            /*
             * The permission list, on the panel being held.
             *
             * Every other overlay is drawn on the information panel, which is the
             * launcher's reading surface. This one is the exception: its rows are
             * pressed rather than read, each opening an Android settings screen,
             * so it belongs under the thumbs.
             *
             * Placed here, near the end, for the reason just given. It was first,
             * which put the entire grid on top of it — the list was composed, was
             * never visible, and could not be dismissed, so the flag saying it had
             * been seen never got written. That also kept the walkthrough from ever
             * showing: the first-run check offers permissions before the
             * walkthrough, so an offer that could not be answered blocked
             * everything behind it.
             */
            if (overlay == Overlay.PERMISSIONS) {
                PermissionsScreen(
                    items = rememberPermissionItems(
                        isDefaultLauncher = isDefaultLauncher,
                        pointerServiceEnabled = pointerServiceEnabled,
                        onSetDefaultLauncher = settingsViewModel::requestDefaultLauncher,
                        onOpenPointerSettings = settingsViewModel::openPointerServiceSettings,
                    ),
                    onDone = {
                        overlay = Overlay.NONE
                        settingsViewModel.dismissPermissionsPrompt()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Above everything on this panel, including the keyboard: at cold start
            // nothing else is open, and if anything were, the intro is what the user
            // is looking at.
            if (mode == DualScreenMode.DUAL_DISPLAY) introOverlay()
            }
        }

        /*
         * ---- Which panel shows what ------------------------------------------
         *
         * The launcher tracks one thing only: whether an app it started has taken the
         * panel the *presentation* projects onto. That flag is set by the launch that
         * caused it, never inferred afterwards from focus.
         *
         * A launch onto the activity's own display needs no flag at all — the app
         * simply covers that window, the way an app covers any activity.
         *
         * `swapScreens` exists for hardware that reports its two panels the other way
         * round, so a wrong guess is correctable from Settings rather than a rebuild.
         * All three read through the live derivations declared at the top of this
         * composable; see the note there for why none of them may be a captured value.
         */
        val appOnSecondaryPanel = appOnSecondaryPanelNow()
        val gridInActivityWindow = gridInActivityWindowNow()

        /*
         * Where an ordinary launch sends an app: the grid's *home* panel, the one
         * the display settings assign it to — not wherever the grid happens to have
         * been displaced to by an app already running.
         *
         * Deliberately not derived from [gridInActivityWindow]. Doing that aimed the
         * next launch at whichever panel the launcher had just moved onto, so a
         * second app opened on top of the launcher instead of alongside it, and the
         * panel that was already occupied kept a dead app on it. One panel is the
         * app panel and the other is the launcher's; that assignment has to be fixed
         * for the pairing to make any sense.
         */
        val gridOnSecondary =
            mode == DualScreenMode.DUAL_DISPLAY && !settings.display.swapScreens
        LaunchedEffect(gridOnSecondary) {
            viewModel.setGridOnSecondaryDisplay(gridOnSecondary)
        }

        /*
         * And *which* display that is.
         *
         * The launcher used to send "second screen" launches to the first non-default
         * display the system listed, while projecting its own panel onto whichever
         * display it had picked here. On a device reporting any extra display — a
         * recorder, a cast target, a vendor overlay — those are two different screens,
         * and the app went to the one nobody was looking at while this panel stood its
         * grid down for it.
         */
        LaunchedEffect(secondary?.displayId) {
            viewModel.setSecondaryDisplayId(secondary?.displayId)
        }

        /*
         * The shape of a recording: the two panels stacked, inside a device body.
         *
         * Width follows the wider panel; the height comes from
         * [recordingFrameSize], which derives both from the same constants the body
         * lays out with. That shared derivation is the point. The height used to be
         * a flat 22% allowance written here while the body used its own separate
         * fractions over there, and the two disagreed — the frame was taller than
         * the layout needed and each panel was drawn narrower than the frame, so
         * neither composition was ever handed the box its real screen has. Both
         * laid out for a smaller screen and showed correspondingly less.
         */
        val primaryPanel = displays.firstOrNull { it.isPrimary }
        LaunchedEffect(primaryPanel, secondary) {
            val top = primaryPanel ?: return@LaunchedEffect
            val bottom = secondary ?: top

            // Wider than either panel, because the console is wider than its screens:
            // the frame is sized so the lid's screen gets its panel's own pixels,
            // and brought back inside the encoder's range if that overshoots.
            val frame = recordingFrameSize(
                panelWidthPx = maxOf(top.widthPx, bottom.widthPx),
                topAspect = top.aspectRatio,
                bottomAspect = bottom.aspectRatio,
            )

            viewModel.setCaptureGeometry(
                width = frame.width,
                height = frame.height,
                densityDpi = top.densityDpi,
            )
            // And to the service, which starts recordings this composition will not
            // be alive to answer for.
            recordingGeometry.setLauncherFrame(
                width = frame.width,
                height = frame.height,
                densityDpi = top.densityDpi,
            )
        }

        /*
         * ---- The pointer's world ---------------------------------------------
         *
         * The two panels, stacked top first, so the cursor runs off the bottom of
         * one and onto the top of the other with no special case at the seam.
         * Reported from here rather than read from `DisplayManager` for the same
         * reason the launch target is: any extra display the system lists — a
         * recorder, a cast target — would otherwise become somewhere the pointer
         * could wander to and not come back from.
         */
        LaunchedEffect(primaryPanel, secondary) {
            val top = primaryPanel ?: return@LaunchedEffect
            val panels = listOfNotNull(top, secondary)
            var offset = 0
            mouse.setDisplays(
                panels.map { panel ->
                    PointerDisplay(
                        displayId = panel.displayId,
                        widthPx = panel.widthPx,
                        heightPx = panel.heightPx,
                        topOffsetPx = offset,
                    ).also { offset += panel.heightPx }
                },
            )
        }

        /*
         * A bound button asked for the on-screen keyboard.
         *
         * The pointer cannot raise a platform IME — an accessibility service has
         * no field to attach one to — but THOR's own keyboard is a composable on
         * the grid surface that types into whatever holds text focus. So the
         * request comes back here and opens that instead.
         *
         * `drop(1)` because the request is a counter and collecting it replays the
         * current value; without it the keyboard would open the moment the pointer
         * service connected.
         */
        LaunchedEffect(mouse) {
            mouse.keyboardRequests.drop(1).collect {
                // Seeded with whatever the tapped field already held, so editing a
                // URL that is already there edits it rather than replacing it.
                viewModel.openKeyboard(label = "Type", initial = mouse.keyboardSeed)
            }
        }

        /*
         * The pointer's Back, when THOR is what it would be going back from.
         *
         * Dispatched as the launcher's own Back command rather than as the
         * system's, so it closes the folder, panel or overlay that is actually
         * open — see `MouseController.backRequests`. `drop(1)` for the same reason
         * as the keyboard above: collecting a counter replays its current value.
         */
        LaunchedEffect(mouse) {
            mouse.backRequests.drop(1).collect {
                inputRouter.emitCommand(ControllerCommand.BACK)
            }
        }

        /*
         * The grid has no panel at all while an app holds the one it lives on, so
         * anything raised over the grid is closed rather than left waiting: a
         * shortcut panel opened from the info surface's window would otherwise be
         * invisible now and appear unbidden the next time Home restored the grid.
         */
        val gridHasNoPanel = mode == DualScreenMode.DUAL_DISPLAY &&
            appOnSecondaryPanel && !gridInActivityWindow
        LaunchedEffect(gridHasNoPanel) {
            if (gridHasNoPanel) viewModel.closeShortcutPanel()
        }

        /*
         * Everything a window on the other display needs in order to compose alone.
         *
         * That window runs its own recomposer — which is what keeps it alive while
         * this activity is stopped — so nothing crosses the boundary implicitly. The
         * theme and the launcher's text focus are provided again on the far side; the
         * *state* still crosses, because these lambdas close over the same snapshot
         * objects, and snapshots do not care which composition reads them.
         */
        val secondWindow: @Composable (@Composable () -> Unit) -> Unit = { inner ->
            ThorTheme(
                personalization = settings.personalization,
                accessibility = settings.accessibility,
                performance = settings.performance,
            ) {
                CompositionLocalProvider(LocalThorTextInput provides textInput) {
                    // On screen for exactly as long as this composition exists,
                    // which is a fact this window can report without being told
                    // and without a focus callback that may never arrive.
                    DisposableEffect(mouse) {
                        mouse.setPresentationVisible(true)
                        onDispose { mouse.setPresentationVisible(false) }
                    }

                    PointerHost(
                        mouse = mouse,
                        displayId = secondary?.displayId,
                        onHoverFeedback = { feedback.play(FeedbackCue.NAVIGATE) },
                        content = inner,
                    )
                }
            }
        }

        // Hosts the pointer over this window, whichever surfaces it holds, and
        // publishes the cursor's position to everything inside so elements can
        // light up under it. The presentation hosts its own; see `secondWindow`.
        PointerHost(
            mouse = mouse,
            displayId = primaryPanel?.displayId,
            onHoverFeedback = { feedback.play(FeedbackCue.NAVIGATE) },
        ) {
        /*
         * The recording's own display, whatever the screen mode is.
         *
         * A third window, on a display the launcher created for itself, whose output
         * is the video encoder's input surface. It renders the same two surfaces the
         * panels do — from the same state, so it cannot drift — one above the other
         * at full size. Nothing is captured from the screens; they are simply drawn
         * again somewhere that happens to be a file.
         *
         * Outside the `when` below, which is where it used to live — inside the
         * dual-display arm, so starting a recording in any other mode produced a
         * display nothing was ever composed onto and a file with nothing in it.
         * What the panels are *doing* is the same in every mode; only where they are
         * drawn differs, and this draws them somewhere else regardless.
         */
        (recording as? RecordingState.Active)?.let { active ->
            SecondaryDisplay(
                displayId = active.displayId,
                enabled = { true },
                takesFocus = { false },
            ) {
                secondWindow {
                    // Falls back to the top panel rather than to a constant: with one
                    // screen the recording is that screen twice over, which is at
                    // least the right proportions and the right layout.
                    val recordedBottom = secondary ?: primaryPanel

                    ConsoleMockup(
                        topAspect = primaryPanel?.aspectRatio ?: DEFAULT_PANEL_ASPECT,
                        bottomAspect = recordedBottom?.aspectRatio ?: DEFAULT_PANEL_ASPECT,
                        // The real screens' dp widths, which is what lets each panel
                        // be laid out as itself and merely drawn smaller. See the
                        // density note in [StackedPanels].
                        topWidthDp = primaryPanel?.widthDp ?: DEFAULT_PANEL_WIDTH_DP,
                        bottomWidthDp = recordedBottom?.widthDp ?: DEFAULT_PANEL_WIDTH_DP,
                        /*
                         * The lid shows whichever screen is being recorded.
                         *
                         * A launcher recording puts the launcher's own top panel
                         * there. A screen recording puts a live mirror of the real
                         * display there instead — so a game is recorded inside the
                         * drawn device, with the launcher's own panel below it,
                         * rather than as a bare rectangle of one screen.
                         */
                        topPanel = active.mirrored?.let { projection ->
                            {
                                ProjectedScreen(
                                    projection = projection,
                                    width = primaryPanel?.widthPx ?: DEFAULT_MIRROR_WIDTH,
                                    height = primaryPanel?.heightPx ?: DEFAULT_MIRROR_HEIGHT,
                                    densityDpi = primaryPanel?.densityDpi
                                        ?: DisplayMetrics.DENSITY_DEFAULT,
                                )
                            }
                        } ?: topContent,
                        bottomPanel = { bottomContent(Modifier.fillMaxSize()) },
                    )
                }
            }
        }

        when (mode) {
            DualScreenMode.DUAL_DISPLAY -> {
                val gridWindowContent: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .claimsInputFor(InputSurface.BOTTOM),
                    ) {
                        bottomContent(Modifier.fillMaxSize())
                        // Raised over the grid when the info panel has no panel of
                        // its own: composing settings into a window an app is
                        // covering made opening it look like nothing happened.
                        if (!infoWindowFree) infoOverlays()

                        // Both deliberately *outside* the surfaces the mock-up renders:
                        // a recording should contain neither its own indicator nor the
                        // message saying where it was saved.
                        if (recording is RecordingState.Active) {
                            RecordingBadge(modifier = Modifier.align(Alignment.TopEnd))
                        }

                        transientMessage?.let { message ->
                            TransientMessage(
                                text = message,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }

                // Nothing at all when an app has that panel — the presentation is
                // dismissed, and in the mirror case this window is behind the app.
                val infoWindowContent: @Composable () -> Unit = {
                    if (infoWindowFree) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .claimsInputFor(InputSurface.TOP),
                        ) {
                            topContent()
                        }
                    }
                }

                if (gridInActivityWindow) {
                    gridWindowContent()
                    SecondaryDisplay(
                        displayId = secondary?.displayId,
                        /*
                         * The presentation stands down exactly while something
                         * occupies the panel it projects onto, and at no other time.
                         * Keying it on focus or top-resumed status was tried and is
                         * wrong in both directions: the system can hand this activity
                         * top-resumed status straight after a launch onto the other
                         * display, which put the grid back over a running app with
                         * input still going to the app underneath, and losing focus
                         * for any other reason tore the window down and uncovered the
                         * system's own launcher on that panel.
                         */
                        enabled = { !appOnSecondaryPanelNow() },
                        takesFocus = presentationHoldsFocusNow,
                        keyDispatcher = inputRouter::dispatchKeyEvent,
                        motionDispatcher = inputRouter::onGenericMotionEvent,
                        onFocusChanged = ::onPresentationFocusChanged,
                        onVisibilityChanged = viewModel::setSecondaryPresentationVisible,
                        content = { secondWindow { infoWindowContent() } },
                    )
                } else {
                    infoWindowContent()
                    SecondaryDisplay(
                        displayId = secondary?.displayId,
                        enabled = { !appOnSecondaryPanelNow() },
                        /*
                         * Focus follows the active surface.
                         *
                         * This is what lets the controller drive this panel while an
                         * app holds the other one: touching it makes its surface the
                         * active one, and the window holding the active surface is the
                         * window that takes focus. A launch onto *this* window's panel
                         * yields the claim, so it never competes for focus with an app
                         * starting on its own display — and the next touch takes it
                         * straight back, which now happens whether or not the activity
                         * behind the other panel is in a state to recompose.
                         */
                        takesFocus = presentationHoldsFocusNow,
                        keyDispatcher = inputRouter::dispatchKeyEvent,
                        motionDispatcher = inputRouter::onGenericMotionEvent,
                        onFocusChanged = ::onPresentationFocusChanged,
                        onVisibilityChanged = viewModel::setSecondaryPresentationVisible,
                        content = { secondWindow { gridWindowContent() } },
                    )
                }
            }

            /*
             * Couch mode, which is its own screen rather than a rearrangement.
             *
             * Everything else here lays the same two panels out differently.
             * This one draws something else entirely — upright box art, section
             * bar along the top, the focused game's own artwork behind it, and a
             * caption instead of an information panel — because the difference
             * that matters is reading distance, and the handheld layout is
             * illegible from a sofa however it is arranged.
             *
             * The grid underneath is untouched: same pages, same placements, same
             * cursor, so the shell's input, editing and launching all work here
             * without knowing this screen exists.
             */
            DualScreenMode.COUCH -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    /*
                     * The same grid surface, drawing its couch layout.
                     *
                     * Everything the grid panel hosts comes with it — the app
                     * drawer, the side menu, the context menu, the keyboard, the
                     * shortcut panel, the walkthrough — because `couchMode`
                     * changes only what that surface *draws*, not what it holds.
                     */
                    bottomContent(Modifier.fillMaxSize())

                    // Settings, search and the entry editor: the information
                    // panel's surfaces, raised over the one screen there is,
                    // because it has no window of its own in this mode.
                    infoOverlays()

                    if (recording is RecordingState.Active) {
                        RecordingBadge(modifier = Modifier.align(Alignment.TopEnd))
                    }
                    transientMessage?.let { message ->
                        TransientMessage(
                            text = message,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                /*
                 * The panel nobody is looking at, held dark.
                 *
                 * A presentation showing black rather than no presentation at
                 * all: dismissing it hands the panel back to whatever the system
                 * would otherwise put there, which on this device is the
                 * wallpaper and the previous app's leftovers. Holding it with
                 * something black is how the panel stays off.
                 *
                 * It never takes focus — see `singleWindowNow` — so the
                 * controller keeps driving the screen the user can see.
                 */
                if (secondary != null) {
                    SecondaryDisplay(
                        displayId = secondary.displayId,
                        enabled = { !appOnSecondaryPanelNow() },
                        takesFocus = { false },
                        keyDispatcher = inputRouter::dispatchKeyEvent,
                        motionDispatcher = inputRouter::onGenericMotionEvent,
                        onFocusChanged = ::onPresentationFocusChanged,
                        onVisibilityChanged = viewModel::setSecondaryPresentationVisible,
                        content = { DarkPanel() },
                    )
                }
            }

            DualScreenMode.SPLIT_SINGLE -> {
                val topWeight = settings.display.splitRatio.coerceIn(0.2f, 0.8f)
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val first: @Composable () -> Unit = {
                            Box(modifier = Modifier.fillMaxWidth().weight(topWeight)) {
                                topContent()
                            }
                        }
                        val second: @Composable () -> Unit = {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f - topWeight)) {
                                bottomContent(Modifier.fillMaxSize())
                            }
                        }
                        if (settings.display.swapScreens) {
                            second()
                            first()
                        } else {
                            first()
                            second()
                        }
                    }
                    // One overlay for the shared window, covering both halves.
                    introOverlay()
                }
            }

            else -> {
                // Single-screen: the grid owns the window, and the surfaces the
                // info panel would host are raised over it. Without the overlays
                // here, settings and search had nowhere to be composed at all in
                // this mode — the row highlighted, the controller went to it, and
                // nothing appeared.
                Box(modifier = Modifier.fillMaxSize()) {
                    bottomContent(Modifier.fillMaxSize())
                    infoOverlays()
                    introOverlay()
                }
            }
        }

        }
        }
    }
}

/**
 * The second panel while couch mode has it switched off.
 *
 * Black rather than the theme's background: the point is a panel that has stopped
 * drawing attention to itself, and every theme's background is a colour chosen to
 * be looked at. On the OLED panel this device ships, black is also the pixels
 * being off rather than lit dark.
 *
 * Deliberately holds the display instead of releasing it. A dismissed presentation
 * gives the panel back to the system, which fills it with the wallpaper and
 * whatever was last there — brighter than the launcher, and not something the
 * launcher can then turn off.
 */
@Composable
private fun DarkPanel() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
}

/**
 * Something the launcher had to say, on its way out.
 *
 * Clears itself, because there is nothing to dismiss it with: a message that needed
 * a button press would be one more thing between the user and the grid.
 */
@Composable
private fun TransientMessage(text: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurface,
        modifier = modifier
            .padding(bottom = MESSAGE_BOTTOM_INSET.dp)
            .clip(ThorTheme.shapes.pill)
            .background(colors.scrim)
            .padding(horizontal = dimens.spacing, vertical = dimens.spacingSmall),
    )
}

/**
 * A quiet marker that a recording is running.
 *
 * Drawn on the live panel only, never on the surfaces the mock-up renders — a
 * recording that shows its own recording badge is a screenshot of the wrong thing.
 */
@Composable
private fun RecordingBadge(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors

    // A slow blink, because a solid dot on a static panel reads as a dead pixel.
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recAlpha",
    )

    Row(
        modifier = modifier
            .padding(10.dp)
            .clip(ThorTheme.shapes.pill)
            .background(colors.scrim)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RECORDING_DOT.copy(alpha = alpha)),
        )
        Text(
            text = "REC",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
        )
    }
}

/**
 * Resolves the user's preference against the hardware actually present.
 *
 * @param hasExternal whether a monitor is attached, which [DualScreenMode.AUTO]
 *   treats as a request for couch mode: plugging one in is how someone says they
 *   have docked the device and sat down, and the handheld layout is not readable
 *   from there. Only AUTO — a mode chosen outright is an instruction, and an
 *   attached screen is not a reason to overrule it.
 */
private fun resolveMode(
    requested: DualScreenMode,
    hasSecondary: Boolean,
    hasExternal: Boolean = false,
): DualScreenMode =
    when (requested) {
        DualScreenMode.AUTO -> when {
            hasExternal -> DualScreenMode.COUCH
            hasSecondary -> DualScreenMode.DUAL_DISPLAY
            else -> DualScreenMode.SPLIT_SINGLE
        }

        DualScreenMode.DUAL_DISPLAY
        -> if (hasSecondary) DualScreenMode.DUAL_DISPLAY else DualScreenMode.SPLIT_SINGLE

        DualScreenMode.SPLIT_SINGLE -> DualScreenMode.SPLIT_SINGLE
        DualScreenMode.SINGLE -> DualScreenMode.SINGLE

        /*
         * Asked for, so honoured, with or without a second panel.
         *
         * Couch mode is a statement about where the user is rather than about
         * what the hardware has: they are across the room from a docked device.
         * Falling back to a two-panel layout because a second panel exists would
         * be answering a question nobody asked — the second panel existing is
         * precisely the thing being turned off.
         */
        DualScreenMode.COUCH -> DualScreenMode.COUCH
    }

/**
 * Feedback for a command that the keyboard is interpreting.
 *
 * The generic mapping would call the space and shift buttons "favourite" and
 * "context menu" and play those cues, which is the wrong sound for a key press.
 */
private fun ControllerCommand.toKeyboardCue(): FeedbackCue = when (this) {
    ControllerCommand.NAVIGATE_UP,
    ControllerCommand.NAVIGATE_DOWN,
    ControllerCommand.NAVIGATE_LEFT,
    ControllerCommand.NAVIGATE_RIGHT,
    -> FeedbackCue.NAVIGATE

    ControllerCommand.BACK -> FeedbackCue.BACK
    ControllerCommand.PAGE_PREVIOUS, ControllerCommand.PAGE_NEXT -> FeedbackCue.PAGE
    ControllerCommand.OPEN_SIDE_MENU -> FeedbackCue.SUCCESS
    else -> FeedbackCue.CONFIRM
}

/** Feedback for a key pressed by touch. */
private fun KeyboardKey.toCue(): FeedbackCue = when (this) {
    KeyboardKey.Backspace, KeyboardKey.Cancel -> FeedbackCue.BACK
    KeyboardKey.Enter -> FeedbackCue.SUCCESS
    KeyboardKey.Layer -> FeedbackCue.PAGE
    else -> FeedbackCue.CONFIRM
}

/** Maps a control command onto the feedback it should produce. */
private fun ControllerCommand.toCue(): FeedbackCue = when (this) {
    ControllerCommand.NAVIGATE_UP,
    ControllerCommand.NAVIGATE_DOWN,
    ControllerCommand.NAVIGATE_LEFT,
    ControllerCommand.NAVIGATE_RIGHT,
    -> FeedbackCue.NAVIGATE

    ControllerCommand.CONFIRM -> FeedbackCue.CONFIRM
    ControllerCommand.BACK, ControllerCommand.CANCEL_EDIT -> FeedbackCue.BACK
    ControllerCommand.PICK_UP -> FeedbackCue.PICK_UP
    ControllerCommand.PAGE_NEXT,
    ControllerCommand.PAGE_PREVIOUS,
    ControllerCommand.CYCLE_IMAGE_NEXT,
    ControllerCommand.CYCLE_IMAGE_PREVIOUS,
    -> FeedbackCue.PAGE

    ControllerCommand.OPEN_SHORTCUTS,
    ControllerCommand.OPEN_SIDE_MENU,
    ControllerCommand.CONTEXT_MENU,
    -> FeedbackCue.MENU_OPEN

    ControllerCommand.OPEN_APP_DRAWER -> FeedbackCue.DRAWER_OPEN

    else -> FeedbackCue.NAVIGATE
}

/** Mark arrival, uninterrupted slow loader, ready hold, and final reveal. */
private const val INTRO_MARK_MS = 700
private const val INTRO_LOAD_MS = 3_200
private const val INTRO_LOAD_FIRST_CUE_MS = 1_100
private const val INTRO_LOAD_SECOND_CUE_MS = 2_200
private const val INTRO_READY_HOLD_MS = 300
private const val INTRO_READY_SETTLE_MS = 200
private const val INTRO_REVEAL_MS = 600

/** Shared timeline positions consumed by the stateless intro on both panels. */
private const val INTRO_LOAD_START = 0.20f
private const val INTRO_LOADED = 0.90f
private const val INTRO_REVEAL_START = 0.94f

/** Reduced motion uses a short fade and the short success cue. */
private const val INTRO_REDUCED_MS = 300

/** The shape a panel is assumed to be before the displays have reported in. */
private const val DEFAULT_PANEL_ASPECT = 16f / 10f

/** A plausible handheld panel, for the moment before any display has reported. */
private const val DEFAULT_PANEL_WIDTH_DP = 640f

/** A plausible screen, for the moment before any display has reported. */
private const val DEFAULT_MIRROR_WIDTH = 1920
private const val DEFAULT_MIRROR_HEIGHT = 1080

/** Recording red, fixed rather than themed: it means one thing everywhere. */
private val RECORDING_DOT = Color(0xFFE5484D)

/** How long a transient message stays up, and how far it sits above the dock. */
private const val MESSAGE_DURATION_MS = 3_000L
private const val MESSAGE_BOTTOM_INSET = 96
