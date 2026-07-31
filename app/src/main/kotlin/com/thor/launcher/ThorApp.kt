package com.thor.launcher

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.display.LauncherFocus
import com.thor.core.display.LauncherPanel
import com.thor.core.display.SecondaryDisplay
import com.thor.core.display.ThorDisplayMonitor
import com.thor.core.input.ControllerInputRouter
import com.thor.core.input.MouseController
import com.thor.core.input.PointerDisplay
import com.thor.launcher.mouse.PointerHost
import kotlinx.coroutines.flow.drop
import com.thor.data.capture.RecordingState
import com.thor.core.model.ControllerCommand
import com.thor.core.model.DualScreenMode
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.KeyboardKey
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.feedback.FeedbackCue
import com.thor.core.ui.component.ThorKeyboard
import com.thor.core.ui.component.ThorIntro
import com.thor.core.ui.component.ConsoleMockup
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorTextInputState
import com.thor.core.ui.feedback.rememberThorFeedback
import com.thor.feature.home.BottomScreen
import com.thor.feature.home.LauncherEffect
import com.thor.feature.home.AppDrawerScreen
import com.thor.feature.home.InputSurface
import com.thor.feature.home.LauncherViewModel
import com.thor.feature.home.component.EditEntryDialog
import com.thor.feature.home.component.EmptySection
import com.thor.feature.movies.MoviesBottomPanel
import com.thor.feature.movies.MoviesTopPanel
import com.thor.feature.movies.MoviesViewModel
import com.thor.feature.movies.rememberMoviesSection
import com.thor.feature.movies.handleCommand
import com.thor.feature.movies.perform
import com.thor.core.model.LauncherTab
import com.thor.feature.home.component.SideMenuAction
import com.thor.feature.home.component.ShortcutPanel
import com.thor.feature.home.component.SortDialog
import com.thor.feature.search.SearchScreen
import com.thor.feature.search.SearchViewModel
import com.thor.feature.settings.SettingsCategory
import com.thor.feature.settings.SettingsScreen
import com.thor.feature.topscreen.TopScreen

/** Which full-screen overlay, if any, is showing on the info surface. */
private enum class Overlay { NONE, SETTINGS, SEARCH }

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
    val settings by settingsViewModel.settings.collectAsState()

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

    /**
     * Bumped whenever another window is observed taking the second panel.
     *
     * The launch watchdog compares this across a launch: unchanged means nothing
     * ever came, however cleanly the launch call returned.
     */
    var panelTakenTicks by remember { mutableIntStateOf(0) }

    /** Bumped by each launch onto the second panel, to arm the watchdog. */
    var launchTicks by remember { mutableIntStateOf(0) }

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
                    touchedSurface = surface
                    focusYieldedToApp = false
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
    val recording by viewModel.recording.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val navCursor by viewModel.navCursor.collectAsState()

    /*
     * The Movies section.
     *
     * Its view model and state are hoisted here because the section spans both
     * panels, and neither of them can own it — on this device either window can
     * be the composition still running while the other is stopped.
     */
    val moviesViewModel: MoviesViewModel = hiltViewModel()
    val moviesSection = rememberMoviesSection(moviesViewModel)
    val moviesState by moviesViewModel.uiState.collectAsState()
    val moviesDetail by moviesViewModel.detail.collectAsState()
    val moviesSources by moviesViewModel.sources.collectAsState()
    val moviesPlayback by moviesViewModel.playback.collectAsState()

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
    val mode = resolveMode(settings.display.mode, secondary != null)

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
    val gridInActivityWindowNow: () -> Boolean = { settings.display.swapScreens }
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
            overlay != Overlay.NONE || state.editingEntry != null -> overlaySurfaceNow()
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
        )
    }

    /*
     * Takes the panel back when a launch turned out to be a no-op.
     *
     * The launcher stands down the moment a launch is *reported*, because waiting
     * would leave the arriving app deaf for as long as it took to appear. But
     * `startMainActivity` returning without throwing does not mean the app came
     * to the foreground — a ROM can queue it, refuse it silently, or bring it up
     * behind whatever is showing. When that happened the launcher had already
     * given the panel away: nothing on screen, nothing with focus, and a
     * controller that did nothing. It read as the launcher freezing while the app
     * ran in the background.
     *
     * So the yield is provisional. If no window is observed taking the panel
     * within the grace period, nothing came and the launcher takes it back.
     * Evidence-based rather than timed alone: an app that *did* arrive bumps
     * [panelTakenTicks] on its way in, and is never disturbed.
     */
    LaunchedEffect(launchTicks) {
        if (launchTicks == 0) return@LaunchedEffect
        val takenBefore = panelTakenTicks
        delay(LAUNCH_TAKEOVER_GRACE_MS)

        if (panelTakenTicks == takenBefore && focusYieldedToApp) {
            focusYieldedToApp = false
            viewModel.releaseSecondScreen()
        }
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

        if (!hasFocus && presentationHoldsFocusNow()) {
            focusYieldedToApp = true
            // Evidence that some other window really has the panel, which is what
            // the launch watchdog below waits for.
            panelTakenTicks++
        }
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
                if (selectedTabNow() == LauncherTab.MOVIES && !overlayIsOpenNow()) {
                    if (moviesSection.handleCommand(event.command)) {
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
                val target =
                    if (activeSurfaceNow() == InputSurface.BOTTOM) Overlay.NONE else overlay

                when (target) {
                    Overlay.NONE -> {
                        viewModel.onCommand(event.command, event.accelerated)
                        feedback.play(event.command.toCue())
                    }

                    Overlay.SETTINGS -> {
                        if (event.command == ControllerCommand.BACK) {
                            // Back unwinds one level at a time: an open page
                            // first, then the overlay. Closing outright from a
                            // page would lose the user's place in the rail.
                            if (settingsViewModel.isAtTopLevel) {
                                overlay = Overlay.NONE
                                settingsViewModel.resetFocus()
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
        val introMotion = ThorTheme.materials.animationsEnabled

        if (introVisible) {
            LaunchedEffect(Unit) {
                feedback.play(FeedbackCue.BOOT)
                introProgress.animateTo(
                    targetValue = INTRO_LAND,
                    animationSpec = tween(
                        durationMillis = if (introMotion) INTRO_RISE_MS else INTRO_REDUCED_MS,
                        easing = FastOutSlowInEasing,
                    ),
                )
                introProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (introMotion) INTRO_SETTLE_MS else INTRO_REDUCED_MS,
                        easing = LinearOutSlowInEasing,
                    ),
                )
                // Arrived: the same cue Home plays, because this is the same event.
                feedback.play(FeedbackCue.HOME)
                viewModel.finishIntro()
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
                    onSkip = viewModel::finishIntro,
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

        // Every keystroke goes straight to the focused field, so it fills in live on
        // whichever panel it is drawn on while the keyboard stays on this one.
        LaunchedEffect(keyboard.text, keyboard.visible) {
            if (keyboard.visible) textInput.setText(keyboard.text)
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
                            // Arms the watchdog: the yield is provisional until
                            // something is seen to take the panel.
                            launchTicks++
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
                    LauncherEffect.OpenPowerMenu ->
                        if (mouse.serviceConnected.value) {
                            mouse.requestPowerMenu()
                        } else {
                            transientMessage =
                                "The power menu needs THOR's accessibility service. " +
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
                    Overlay.SETTINGS -> SettingsScreen(
                        onDismiss = {
                            overlay = Overlay.NONE
                            settingsViewModel.resetFocus()
                        },
                        onRowCountChanged = { settingsRowCount = it },
                        viewModel = settingsViewModel,
                    )

                    Overlay.SEARCH -> SearchScreen(
                        onEntrySelected = { entry ->
                            overlay = Overlay.NONE
                            viewModel.launchEntry(entry)
                        },
                        onDismiss = { overlay = Overlay.NONE },
                        viewModel = searchViewModel,
                    )

                    Overlay.NONE -> Unit
                }
            }
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
                    MoviesTopPanel(
                        mode = moviesSection.mode,
                        state = moviesState,
                        playback = moviesPlayback,
                        onStatus = moviesSection::onStatus,
                        onCommands = moviesSection::onCommands,
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
                    folderChildren = state.openFolderContents,
                    clockStyle = settings.personalization.clockStyle,
                    showStatusBar = settings.personalization.showStatusBar,
                    // Trailer playback is an explicit user preference. Performance
                    // mode reduces interface effects, but must not silently replace
                    // a successfully fetched trailer with screenshots.
                    videoPreviewsEnabled = settings.personalization.autoplayTrailers &&
                        trailerDismissedFor != state.selection?.id,
                    selectedScreenshot = selectedScreenshot,
                    onScreenshotSelected = viewModel::setScreenshot,
                    // Only when this panel is holding the controller *itself*. An
                    // overlay drawn over it has its own focus to show, and two focus
                    // treatments on one panel would contradict each other.
                    focused = activeSurface == InputSurface.TOP && !overlayIsOpen,
                )
                infoOverlays()

                // Only where this surface has a panel of its own. In the split and
                // single-screen modes both surfaces share one window, and two intros
                // stacked in it would read as a mirror rather than as one launcher
                // starting.
                if (mode == DualScreenMode.DUAL_DISPLAY) introOverlay()
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
                onTabSelected = viewModel::selectTab,
                /*
                 * The other half of the Movies section: describe, choose, or
                 * control, matching whatever its top panel is showing. Supplied
                 * from here rather than from the home module, so a feature module
                 * never has to depend on an unrelated one.
                 */
                sectionContent = { tab ->
                    if (tab == LauncherTab.MOVIES) {
                        MoviesBottomPanel(
                            mode = moviesSection.mode,
                            detail = moviesDetail,
                            sources = moviesSources,
                            playback = moviesPlayback,
                            status = moviesSection.status,
                            focusedSource = moviesSection.focusedSource,
                            focusedAction = moviesSection.focusedAction,
                            hasNextEpisode = moviesViewModel.nextEpisode() != null,
                            onPlayerAction = moviesSection::perform,
                            onSeek = moviesSection::seekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        EmptySection(tab = tab, modifier = Modifier.fillMaxSize())
                    }
                },
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

            // Above everything on this panel, including the keyboard: at cold start
            // nothing else is open, and if anything were, the intro is what the user
            // is looking at.
            introOverlay()
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
         * The shape of a recording, taken from the panels themselves.
         *
         * Width follows the wider panel, height is both panels stacked plus room for
         * the console body around them — so the video is the device's own proportions
         * rather than a guess, whatever hardware this is running on.
         */
        val primaryPanel = displays.firstOrNull { it.isPrimary }
        LaunchedEffect(primaryPanel, secondary) {
            val top = primaryPanel ?: return@LaunchedEffect
            val bottom = secondary ?: top
            val width = maxOf(top.widthPx, bottom.widthPx)
            val scaled = { panel: com.thor.core.display.ThorDisplayInfo ->
                (width / panel.aspectRatio).toInt()
            }
            viewModel.setCaptureGeometry(
                width = width,
                height = ((scaled(top) + scaled(bottom)) * CAPTURE_BODY_ALLOWANCE).toInt(),
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
                viewModel.openKeyboard(label = "Type", initial = "")
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

                /*
                 * The recording's own display.
                 *
                 * A third window, on a display the launcher created for itself, whose
                 * output is the video encoder's input surface. It renders the same two
                 * surfaces the panels do — from the same state, so it cannot drift —
                 * inside a console body. Nothing is captured from the screens; they are
                 * simply drawn again somewhere that happens to be a file.
                 */
                (recording as? RecordingState.Active)?.let { active ->
                    SecondaryDisplay(
                        displayId = active.displayId,
                        enabled = { true },
                        takesFocus = { false },
                    ) {
                        secondWindow {
                        ConsoleMockup(
                            topAspect = displays.firstOrNull { it.isPrimary }?.aspectRatio
                                ?: DEFAULT_PANEL_ASPECT,
                            bottomAspect = secondary?.aspectRatio ?: DEFAULT_PANEL_ASPECT,
                            topPanel = topContent,
                            bottomPanel = { bottomContent(Modifier.fillMaxSize()) },
                        )
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

            DualScreenMode.SPLIT_SINGLE -> {
                val topWeight = settings.display.splitRatio.coerceIn(0.2f, 0.8f)
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
                }
            }
        }

        }
        }
    }
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

/** Resolves the user's preference against the hardware actually present. */
private fun resolveMode(requested: DualScreenMode, hasSecondary: Boolean): DualScreenMode =
    when (requested) {
        DualScreenMode.AUTO,
        DualScreenMode.DUAL_DISPLAY,
        -> if (hasSecondary) DualScreenMode.DUAL_DISPLAY else DualScreenMode.SPLIT_SINGLE

        DualScreenMode.SPLIT_SINGLE -> DualScreenMode.SPLIT_SINGLE
        DualScreenMode.SINGLE -> DualScreenMode.SINGLE
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

/**
 * The intro's two segments, in milliseconds.
 *
 * The first ends where the chime in `ui_boot` lands, so the mark and the sound
 * arrive together; the second is the reveal. Short on purpose — this is a launcher,
 * and the second time you see an intro is the first time it is too long.
 */
private const val INTRO_RISE_MS = 900
private const val INTRO_SETTLE_MS = 700

/** Where the first segment stops: the beat the mark lands on. */
private const val INTRO_LAND = 0.62f

/** Under reduced motion the whole thing is a brief fade instead. */
private const val INTRO_REDUCED_MS = 220

/** The shape a panel is assumed to be before the displays have reported in. */
private const val DEFAULT_PANEL_ASPECT = 16f / 10f

/** Recording red, fixed rather than themed: it means one thing everywhere. */
private val RECORDING_DOT = Color(0xFFE5484D)

/** Extra height for the console body drawn around the two panels. */
private const val CAPTURE_BODY_ALLOWANCE = 1.22f

/** How long a transient message stays up, and how far it sits above the dock. */
/**
 * How long a launch has to actually put something on the panel.
 *
 * Generous on purpose. A cold app on a handheld can take seconds to draw its
 * first frame, and taking the panel back from one that was merely slow would be
 * worse than the fault this guards against.
 */
private const val LAUNCH_TAKEOVER_GRACE_MS = 6_000L

private const val MESSAGE_DURATION_MS = 3_000L
private const val MESSAGE_BOTTOM_INSET = 96
