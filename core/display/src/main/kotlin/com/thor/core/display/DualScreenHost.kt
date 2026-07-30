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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thor.core.common.log.ThorLog

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
    enabled: Boolean,
    /**
     * Lets this panel take focus, making it both typable and the key target.
     *
     * Off by default: focus here costs the activity its own, so it is only worth
     * taking when this panel is the surface the user is actually working on —
     * because a text field needs it, or because an app is occupying the other
     * display and this is the only launcher surface left.
     */
    takesFocus: Boolean = false,
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

    // Held outside the effect so the focus flag can be re-applied without
    // recreating the window, which would tear down the very dialog that asked
    // for focus in the first place.
    val presentationHolder = remember { mutableStateOf<ThorPresentation?>(null) }

    if (activity == null || displayId == null || !enabled) {
        return
    }

    LaunchedEffect(takesFocus, presentationHolder.value) {
        presentationHolder.value?.setFocusable(takesFocus)
    }

    DisposableEffect(activity, displayId) {
        val displayManager = activity.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(displayId)

        if (display == null) {
            ThorLog.w("Display", "Secondary display $displayId is no longer available")
            return@DisposableEffect onDispose { }
        }

        var presentation: ThorPresentation? = null

        fun show() {
            if (presentation != null) return
            presentation = ThorPresentation(
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
                created.keyDispatcher = keyDispatcher
                created.motionDispatcher = motionDispatcher
                created.focusListener = { hasFocus -> currentFocusListener?.invoke(hasFocus) }
            }
            runCatching { presentation?.show() }.onFailure { error ->
                // The display can disappear between resolution and show();
                // losing this panel must not take the launcher with it.
                ThorLog.e("Display", "Failed to show presentation on $displayId", error)
                presentation = null
            }
            presentationHolder.value = presentation
        }

        fun hide() {
            presentation?.let { runCatching { it.dismiss() } }
            presentation = null
            presentationHolder.value = null
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
         * instead: the caller knows where it sent the activity, and this
         * composable leaves the composition when it should stand down. Resume is
         * still observed as a repair path, because the system dismisses the
         * presentation itself if the display is detached and reattached.
         */
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) show()
        }

        activity.lifecycle.addObserver(observer)
        // Shown from STARTED rather than RESUMED: the panel should be populated
        // as soon as it can be, and waiting for resume left it blank for a frame
        // on every return to the launcher.
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) show()

        onDispose {
            activity.lifecycle.removeObserver(observer)
            hide()
        }
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
