package com.thor.core.display

import android.view.Display
import com.thor.core.model.DualScreenMode

/** A display THOR can render onto. */
data class ThorDisplayInfo(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRate: Float,
    val isPrimary: Boolean,
    /** Presentation displays are the ones Android permits a second window on. */
    val isPresentationCapable: Boolean,
) {
    val aspectRatio: Float get() = if (heightPx == 0) 1f else widthPx.toFloat() / heightPx
}

/**
 * What the launcher currently has to work with.
 *
 * [effectiveMode] is the resolved answer to "how do I lay out two surfaces on
 * this hardware right now", already accounting for the user's preference and
 * for whether a second panel is actually attached. Every UI decision reads this
 * rather than re-deriving it, so plugging in an external display or changing
 * the setting takes one code path.
 */
data class DisplayTopology(
    val primary: ThorDisplayInfo,
    val secondary: ThorDisplayInfo?,
    val requestedMode: DualScreenMode,
) {
    val hasSecondaryDisplay: Boolean get() = secondary != null

    val effectiveMode: DualScreenMode
        get() = when (requestedMode) {
            DualScreenMode.AUTO ->
                if (hasSecondaryDisplay) DualScreenMode.DUAL_DISPLAY else DualScreenMode.SPLIT_SINGLE
            // A user who explicitly asked for dual display but unplugged the
            // second panel still needs a usable launcher.
            DualScreenMode.DUAL_DISPLAY ->
                if (hasSecondaryDisplay) DualScreenMode.DUAL_DISPLAY else DualScreenMode.SPLIT_SINGLE
            DualScreenMode.SPLIT_SINGLE -> DualScreenMode.SPLIT_SINGLE
            DualScreenMode.SINGLE -> DualScreenMode.SINGLE
        }

    /** True when the info surface needs its own window on another display. */
    val needsPresentation: Boolean
        get() = effectiveMode == DualScreenMode.DUAL_DISPLAY && secondary != null

    companion object {
        /**
         * Displays Android reports but which are not real second screens.
         * Overlay/virtual displays created by screen recorders and casting show
         * up in the same list and must not steal the info panel.
         */
        fun isUsableSecondary(display: Display): Boolean {
            if (display.displayId == Display.DEFAULT_DISPLAY) return false
            if (display.state == Display.STATE_OFF) return false
            val flags = display.flags
            val isPresentation = flags and Display.FLAG_PRESENTATION != 0
            val isPrivate = flags and Display.FLAG_PRIVATE != 0
            // The AYN Thor's second panel reports as a public presentation
            // display; private virtual displays belong to other apps.
            return isPresentation && !isPrivate
        }
    }
}
