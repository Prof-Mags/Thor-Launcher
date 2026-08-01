package com.thor.launcher.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.display.SecondaryDisplay
import com.thor.core.model.SessionQuality
import com.thor.core.model.ThemeSpec
import com.thor.data.stream.StreamPad
import com.thor.feature.stream.StreamPadPanel
import com.thor.feature.stream.StreamPanelController

/**
 * Puts the trackpad and keyboard on the second screen for as long as a stream is
 * up.
 *
 * Draws nothing where it is placed — it owns a window on another display, which
 * is why it is composed into a zero-sized view inside the streaming activity
 * rather than into a layout.
 *
 * The display is found here rather than injected, because this window has no
 * launcher state to read: the stream activity is its own task and the topology
 * it needs is one number, which the platform already knows.
 */
@Composable
fun StreamPadHost(
    pad: StreamPad,
    quality: SessionQuality,
    controller: StreamPanelController,
) {
    val context = LocalContext.current
    var displayId by remember { mutableStateOf(secondaryDisplayId(context)) }

    /*
     * Followed rather than read once.
     *
     * The panel is exposed and withdrawn by the lid and by the ROM's own
     * behaviour while an app is fullscreen, and a display id captured at launch
     * would leave the second screen blank for the rest of the session with no
     * indication why.
     */
    DisposableEffect(context) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) {
                displayId = secondaryDisplayId(context)
            }

            override fun onDisplayRemoved(id: Int) {
                displayId = secondaryDisplayId(context)
            }

            override fun onDisplayChanged(id: Int) {
                displayId = secondaryDisplayId(context)
            }
        }
        manager?.registerDisplayListener(listener, null)
        onDispose { manager?.unregisterDisplayListener(listener) }
    }

    SecondaryDisplay(
        displayId = displayId,
        enabled = { quality.bottomPanel && displayId != null },
        /*
         * Never takes focus, and this is the important line in the file.
         *
         * Focus here costs the streaming window its own — `setFocusable(true)`
         * makes this panel the key target — and the streaming window is what
         * forwards the controller to the PC. A focused trackpad would therefore
         * take the entire pad away from the game: the picture would keep moving
         * and no button would do anything, which reads as the stream having
         * frozen.
         *
         * Nothing is lost by refusing it. Focus governs *key* delivery; touch is
         * delivered to whichever window is under the finger regardless, so the
         * trackpad and the keyboard work exactly as well without it.
         */
        takesFocus = { false },
    ) {
        /*
         * Its own theme scope, at defaults.
         *
         * This window is outside the launcher's composition and inherits nothing
         * from it. The user's chosen theme is not read here on purpose: doing so
         * would mean this window depending on settings, and a trackpad is not
         * worth making the stream wait on a DataStore read.
         */
        ThorTheme {
            StreamPadPanel(pad = pad, quality = quality, controller = controller)
        }
    }
}

/**
 * The first display that is not the built-in one.
 *
 * `DISPLAY_CATEGORY_PRESENTATION` is the right question to ask: it lists exactly
 * the displays a `Presentation` may be shown on, which is not the same as every
 * display the device reports.
 */
private fun secondaryDisplayId(context: Context): Int? {
    val manager = context.getSystemService(DisplayManager::class.java) ?: return null
    return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        ?.displayId
}
