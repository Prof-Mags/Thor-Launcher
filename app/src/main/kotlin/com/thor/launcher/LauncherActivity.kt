package com.thor.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.thor.core.datastore.SettingsRepository
import com.thor.data.notification.NotificationRepository
import com.thor.core.display.ThorDisplayMonitor
import com.thor.core.display.hideSystemBars
import com.thor.core.input.ControllerInputRouter
import com.thor.core.input.ControllerProfiles
import com.thor.core.input.MouseController
import com.thor.launcher.capture.RecordingGeometry
import com.thor.data.launcher.HomeRequests
import com.thor.data.launcher.LauncherForeground
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launcher's single activity.
 *
 * It owns three things that cannot live in Compose: the physical input stream,
 * the window flags, and the lifecycle that the secondary-display presentation
 * attaches to. Everything else is delegated to [ThorApp].
 */
@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var displayMonitor: ThorDisplayMonitor

    /**
     * Input is routed through a single router scoped to this activity, so key
     * repeat timers and long-press watches die with the window rather than
     * outliving it.
     */
    private val inputRouter: ControllerInputRouter by lazy {
        ControllerInputRouter(lifecycleScope, mouse)
    }

    /**
     * The controller pointer.
     *
     * Injected here rather than reached through the view model because the router
     * is built from the activity and needs it before any composition exists — and
     * because the same instance is held by the accessibility service, which is how
     * the pointer stays in one place as focus moves between them.
     */
    @Inject lateinit var mouse: MouseController

    /**
     * Home presses, from either display.
     *
     * Because this activity is `singleTask`, pressing Home while it is already running
     * does not recreate it — the intent arrives at [onNewIntent] instead. Without
     * listening for that, Home brought the launcher forward still showing whatever
     * page and overlay it was left on, which is not what Home means anywhere.
     *
     * Presses made on the *second* panel never arrive here at all: Android routes
     * `CATEGORY_HOME` per display, so those go to [SecondaryHomeActivity]. Both report
     * into the same bus, so the launcher hears every press wherever it was made.
     */
    @Inject lateinit var homeRequests: HomeRequests

    /** Reports whether THOR is the activity in front, for the launch watchdog. */
    @Inject lateinit var launcherForeground: LauncherForeground

    /** Re-reads notification access when the launcher comes back to the front. */
    @Inject lateinit var notifications: NotificationRepository

    /**
     * Where a recording gets its shape, filled in by the shell.
     *
     * Held by the activity rather than reached for inside the composable because
     * the *service* reads it, and the service runs when this composition does not.
     */
    @Inject lateinit var recordingGeometry: RecordingGeometry

    /**
     * Re-asserts full screen and releases held keys as foreground status changes.
     *
     * Deliberately *not* reported to the launcher shell as a "the user is back"
     * signal. On a two-screen device this fires whenever the user touches the
     * launcher's own panel while an app keeps the other one — so acting on it
     * evicted the running app and made playing on one screen while browsing on the
     * other impossible. Which panel an app occupies is tracked by the launch that
     * put it there instead; see `LauncherViewModel.secondScreenOccupied`.
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)

        // The only signal Android gives for "this is the thing in front". The
        // launcher uses it to tell a launch that arrived from one that silently
        // did nothing; see [LauncherForeground].
        launcherForeground.setTopResumed(isTopResumedActivity)

        if (isTopResumedActivity) {
            // The system restores the bars whenever something else has been in
            // front, so full screen is re-asserted on the way back rather than
            // only in onCreate.
            hideSystemBars(window)
        } else {
            // A direction held at the moment focus moved away would otherwise
            // leave the auto-repeat timer running against a window that can no
            // longer see the key-up.
            inputRouter.releaseAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars(window)

        // A launcher is the thing the user comes back to; keeping the panel
        // awake while it is foreground matches the handheld's shell behaviour.
        lifecycleScope.launch {
            if (settingsRepository.display.first().keepTopScreenAwake) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // Controller remapping takes effect immediately, without a restart.
        settingsRepository.controls
            .onEach { controls ->
                val profile = ControllerProfiles.byId(
                    controls.activeProfileId,
                    controls.customProfiles,
                )
                // Sensitivity is expressed as a multiplier but applied as an
                // inverse dead zone: a more sensitive stick registers a
                // direction sooner, i.e. at a smaller deflection.
                val deadZone = (profile.stickDeadZone / controls.stickSensitivity)
                    .coerceIn(MIN_DEAD_ZONE, MAX_DEAD_ZONE)
                inputRouter.setProfile(profile.copy(stickDeadZone = deadZone))
            }
            .launchIn(lifecycleScope)

        setContent {
            ThorApp(
                inputRouter = inputRouter,
                displayMonitor = displayMonitor,
                mouse = mouse,
                homeRequests = homeRequests.requests,
                recordingGeometry = recordingGeometry,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only a genuine HOME launch resets the launcher; other intents that
        // reach a singleTask activity should leave the user's place alone.
        val isHome = intent.categories?.contains(Intent.CATEGORY_HOME) == true
        if (isHome) homeRequests.onHomePressed()
    }

    /**
     * Intercepts physical input before Compose sees it.
     *
     * `dispatchKeyEvent` rather than `onKeyDown` because the grid must receive
     * D-pad input even when a text field or a Compose focus target would
     * otherwise consume it — the launcher's own cursor is the focus model, and
     * letting the framework's focus traversal also run would fight it.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        // The same entry point the secondary panel uses, so whichever window holds
        // focus drives one cursor.
        inputRouter.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        inputRouter.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)

    /**
     * Keeps held-key state and the system bars honest as focus moves.
     *
     * Focus moves between the launcher's two windows and any app on either panel,
     * so it says nothing about whether an app is still running — see
     * [onTopResumedActivityChanged] for why nothing here may act on it.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Losing focus while a direction is held would otherwise leave the
        // auto-repeat timer running against a window that can no longer see the
        // key-up event.
        if (!hasFocus) {
            inputRouter.releaseAll()
        } else {
            // Re-hidden on regaining focus: the system restores the bars after a
            // transient swipe or after returning from another app.
            hideSystemBars(window)
        }
    }

    override fun onResume() {
        super.onResume()
        // Whether THOR is on screen, for the pointer. Lifecycle rather than window
        // focus: focus moves to the app on the *other* panel, and to the pointer's
        // own overlay, neither of which means the launcher has gone anywhere.
        mouse.setActivityVisible(true)
        // Notification access is granted in Android's settings, so returning from
        // them is the one moment the answer is known to have possibly changed and
        // nothing has told us.
        notifications.refresh()
    }

    override fun onPause() {
        super.onPause()
        mouse.setActivityVisible(false)
        inputRouter.releaseAll()
    }

    private companion object {
        /** Bounds on the derived dead zone, so no setting makes the stick unusable. */
        const val MIN_DEAD_ZONE = 0.15f
        const val MAX_DEAD_ZONE = 0.85f
    }
}
