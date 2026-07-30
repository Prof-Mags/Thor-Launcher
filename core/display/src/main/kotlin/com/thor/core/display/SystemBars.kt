package com.thor.core.display

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Puts a window into full screen, hiding the status and navigation bars.
 *
 * Both of THOR's windows need this, not just the activity's: the presentation on
 * the second panel has its own window and its own insets controller, so hiding
 * the bars on the activity left the notification strip showing on the other
 * screen.
 *
 * Bars remain reachable by an edge swipe — [WindowInsetsControllerCompat
 * .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE] — rather than being locked away. A
 * launcher that made the notification shade unreachable would be hiding the
 * device's own controls from its owner.
 */
fun hideSystemBars(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
