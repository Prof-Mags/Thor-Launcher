package com.thor.core.display

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thor.core.common.log.ThorLog
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Mirrors [content] onto a secondary display for as long as this composable is
 * in the composition and [enabled] is true.
 *
 * Nothing is rendered in the caller's own layout — this is a side effect that
 * owns a second window. It is written as a composable rather than as an
 * activity-level API so the top screen's content can be an ordinary composable
 * that recomposes from the same state as the bottom screen.
 */
@Composable
fun SecondaryDisplay(
    displayId: Int?,
    /**
     * Whether this window should be up, read on every change rather than passed.
     *
     * A lambda, and this is not a style choice. The caller composes in the
     * activity's window, and Compose *pauses that composition's frame clock* the
     * moment an app covers the activity's own display — so a plain `Boolean`
     * parameter freezes at whatever it was when the app launched and can never be
     * updated again until the launcher comes back. Read through a lambda instead
     * and [snapshotFlow] sees each change directly, on a coroutine that keeps
     * running while the activity is stopped.
     */
    enabled: () -> Boolean,
    /**
     * Lets this panel take focus, making it both typable and the key target.
     *
     * Off by default: focus here costs the activity its own, so it is only worth
     * taking when this panel is the surface the user is actually working on —
     * because a text field needs it, or because an app is occupying the other
     * display and this is the only launcher surface left.
     *
     * A lambda for the same reason as [enabled], and this is the one that mattered
     * most: while the activity was stopped behind an app, touching this panel
     * could not make it focusable again, so the launcher stayed visible and deaf
     * to the controller until Home brought the activity back.
     */
    takesFocus: () -> Boolean = { false },
    /** Routes this window's key events; see [ThorPresentation.keyDispatcher]. */
    keyDispatcher: ((KeyEvent) -> Boolean)? = null,
    motionDispatcher: ((MotionEvent) -> Boolean)? = null,
    /**
     * Reports this window gaining or losing key focus.
     *
     * The caller's only way to hear about a touch it cannot see. When an app covers
     * the other display, the user tapping it produces no event anywhere in the
     * launcher — the app consumes the touch — and the sole trace of it is this
     * window losing focus.
     */
    onFocusChanged: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findComponentActivity()

    // Keeps the presentation's composition pointed at the latest content lambda
    // without tearing the window down and rebuilding it on every recomposition.
    val currentContent by rememberUpdatedState(content)

    // Read at call time rather than captured, so a window built once still reports
    // to whatever the current composition is watching.
    val currentFocusListener by rememberUpdatedState(onFocusChanged)
    val currentKeyDispatcher by rememberUpdatedState(keyDispatcher)
    val currentMotionDispatcher by rememberUpdatedState(motionDispatcher)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTakesFocus by rememberUpdatedState(takesFocus)

    if (activity == null || displayId == null) {
        return
    }

    /*
     * The window, held where two effects with different lifetimes can both reach it.
     *
     * Presence and focus are driven by a coroutine, because that is the only thing
     * still running while the activity is stopped. Disposal cannot be: Compose cancels
     * effect coroutines during apply but resumes them afterwards, so a `finally` runs
     * a frame late — long enough, when the two panels are swapped over, to leave the
     * outgoing window sitting on the display the incoming one has just been put on.
     */
    val slot = remember(activity, displayId) { PresentationSlot() }

    DisposableEffect(slot) {
        onDispose { slot.hide() }
    }

    /*
     * One effect drives this window for as long as the display does.
     *
     * Keyed on the slot alone — deliberately not on [enabled] or [takesFocus] —
     * because a `LaunchedEffect` only restarts when a key changes, and a key can only
     * change by recomposing. This composable is called from the activity's
     * composition, which Compose stops driving the instant an app covers the
     * activity's display. Everything that has to keep working while that is true
     * therefore has to be driven from *inside* this coroutine, which goes on running,
     * rather than from a parameter, which does not.
     */
    LaunchedEffect(slot) {
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(displayId)

        if (display == null) {
            ThorLog.w("Display", "Secondary display $displayId is no longer available")
            return@LaunchedEffect
        }

        fun show() {
            if (slot.presentation != null) return
            // Shown from STARTED rather than RESUMED: the panel should be populated
            // as soon as it can be, and waiting for resume left it blank for a frame
            // on every return to the launcher.
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

            slot.presentation = ThorPresentation(
                activity = activity,
                display = display,
                content = {
                    /*
                     * These two owners are normally found by walking up from
                     * `LocalContext`, and inside a Presentation that walk fails:
                     * a presentation's context comes from
                     * `createDisplayContext`, which does not wrap the activity.
                     * Any composable calling `rememberLauncherForActivityResult`
                     * on this panel therefore threw the moment it entered the
                     * composition — which is what crashed the launcher as soon
                     * as the entry editor's icon picker was created.
                     */
                    CompositionLocalProvider(
                        LocalActivityResultRegistryOwner provides activity,
                        LocalOnBackPressedDispatcherOwner provides activity,
                    ) {
                        currentContent()
                    }
                },
            ).also { created ->
                /*
                 * Trampolines rather than the callbacks themselves.
                 *
                 * This window outlives many recompositions, and the router it
                 * reports to is re-supplied by each of them. Storing the lambda
                 * that happened to be current when the window was built left a
                 * re-created presentation dispatching into a stale closure.
                 */
                created.keyDispatcher = { event ->
                    currentKeyDispatcher?.invoke(event) == true
                }
                created.motionDispatcher = { event ->
                    currentMotionDispatcher?.invoke(event) == true
                }
                created.focusListener = { hasFocus -> currentFocusListener?.invoke(hasFocus) }
            }
            runCatching { slot.presentation?.show() }.onFailure { error ->
                // The display can disappear between resolution and show();
                // losing this panel must not take the launcher with it.
                ThorLog.e("Display", "Failed to show presentation on $displayId", error)
                slot.presentation = null
            }
        }

        /*
         * Deliberately *not* dismissed when the activity pauses.
         *
         * Pausing means something took the foreground, but not necessarily on
         * this display — and launching an app on the main panel pauses the
         * activity every time. Dismissing on pause therefore tore this window
         * down on any launch at all, uncovering whatever the system keeps behind
         * it on this display, which is its own default launcher. That is the
         * stock home screen appearing on the other panel.
         *
         * A launch that genuinely covers *this* display is handled by [enabled]
         * instead: the caller knows where it sent the app, and says so. Resume is
         * still observed as a repair path, because the system dismisses the
         * presentation itself if the display is detached and reattached, and
         * because [show] declines while the activity is below STARTED.
         */
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentEnabled()) show()
        }
        activity.lifecycle.addObserver(observer)

        try {
            /*
             * Presence and focus, applied from snapshot reads.
             *
             * [snapshotFlow] observes the state the two lambdas touch and emits
             * when it changes, with no recomposition anywhere in the path. That is
             * the whole point: while an app covers the activity's display this is
             * the only thing still able to act on a touch landing on this panel.
             */
            snapshotFlow { currentEnabled() to currentTakesFocus() }
                .distinctUntilChanged()
                .collect { (shouldShow, shouldTakeFocus) ->
                    if (shouldShow) {
                        show()
                        slot.presentation?.setFocusable(shouldTakeFocus)
                    } else {
                        slot.hide()
                    }
                }
        } finally {
            // The window itself is disposed of by the `DisposableEffect` above, which
            // runs during apply rather than a frame later.
            activity.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * A one-slot home for the second window.
 *
 * Exists so that the coroutine driving the window and the disposal that has to be
 * synchronous with leaving the composition can be two different effects over one
 * window, rather than one effect that has to be both.
 */
private class PresentationSlot {

    var presentation: ThorPresentation? = null

    fun hide() {
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
    }
}

/** Walks the context wrapper chain to the hosting activity. */
fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
