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

    /**
     * The panel's width in dp, which is the width its layout is decided in.
     *
     * Pixels alone do not describe a screen to a composition — dp does, and dp is
     * pixels over density. Anything re-drawing this panel somewhere else, at some
     * other size, needs this figure to reproduce the layout rather than merely the
     * picture; see the recording frame.
     */
    val widthDp: Float
        get() = if (densityDpi <= 0) widthPx.toFloat() else widthPx * DP_PER_INCH / densityDpi
}

/** Android's baseline: 160dpi is where one dp equals one pixel. */
private const val DP_PER_INCH = 160f

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
    /**
     * Whether a monitor is attached beyond the device's own second panel.
     *
     * Passed in rather than worked out here, because this type holds two displays
     * and an attached monitor is by definition a third — it is not a fact this
     * class has the data to determine. [hasExternalDisplay] answers it from the
     * full list, which is what the caller has.
     */
    val hasExternalDisplay: Boolean = false,
) {
    val hasSecondaryDisplay: Boolean get() = secondary != null

    val effectiveMode: DualScreenMode
        get() = when (requestedMode) {
            // A monitor is how someone says they have docked and sat down; see
            // `resolveMode` in the shell, which this mirrors.
            DualScreenMode.AUTO ->
                if (hasExternalDisplay) {
                    DualScreenMode.COUCH
                } else if (hasSecondaryDisplay) {
                    DualScreenMode.DUAL_DISPLAY
                } else {
                    DualScreenMode.SPLIT_SINGLE
                }
            // A user who explicitly asked for dual display but unplugged the
            // second panel still needs a usable launcher.
            DualScreenMode.DUAL_DISPLAY ->
                if (hasSecondaryDisplay) DualScreenMode.DUAL_DISPLAY else DualScreenMode.SPLIT_SINGLE
            DualScreenMode.SPLIT_SINGLE -> DualScreenMode.SPLIT_SINGLE
            DualScreenMode.SINGLE -> DualScreenMode.SINGLE

            // Honoured whether or not a second panel is attached: couch mode is
            // about where the user is sitting, and a second panel being present
            // is the thing it exists to switch off.
            DualScreenMode.COUCH -> DualScreenMode.COUCH
        }

    /**
     * True when the launcher needs a window on the other display.
     *
     * Couch mode needs one too, and not to draw the launcher in: it holds that
     * panel with something black so the system does not fill it with the
     * wallpaper and the last app's leftovers. "Needs a presentation" and "shows a
     * surface there" stopped being the same question when couch mode arrived.
     */
    val needsPresentation: Boolean
        get() = secondary != null &&
            effectiveMode in setOf(DualScreenMode.DUAL_DISPLAY, DualScreenMode.COUCH)

    companion object {
        /**
         * Displays Android reports but which are not real second screens.
         * Overlay/virtual displays created by screen recorders and casting show
         * up in the same list and must not steal the info panel.
         */
        /**
         * Whether a monitor is plugged in, as distinct from the device's own panels.
         *
         * The Thor has exactly two built-in screens, so a *second* usable
         * presentation display is one the user attached — a television, a monitor,
         * a dock. Counting rather than naming, because the public API has no way to
         * ask whether a display is built in: `Display.getType` is hidden, and the
         * names are whatever the driver or the monitor's EDID happens to say.
         *
         * Private displays are already excluded by [isUsableSecondary], which is
         * what keeps the launcher's own virtual displays out of this — a recording
         * in progress creates one, and a recording must not look like a monitor.
         */
        fun hasExternalDisplay(displays: List<ThorDisplayInfo>): Boolean =
            displays.count { !it.isPrimary && it.isPresentationCapable } >= EXTERNAL_THRESHOLD

        /**
         * The device's own second panel, plus one.
         *
         * Named rather than written as `2` at the comparison, because the number is
         * a fact about this hardware and not an arbitrary threshold: it is "the
         * built-in second screen, and then another one".
         */
        private const val EXTERNAL_THRESHOLD = 2

        fun isUsableSecondary(display: Display): Boolean =
            isUsableSecondary(display.displayId, display.flags)

        /**
         * Whether a display is a second panel the launcher can draw on.
         *
         * Taken apart from [Display] so the rule can be tested — the flags are
         * the whole of it, and a `Display` cannot be constructed off-device.
         *
         * **Power state is deliberately not consulted.** This used to reject a
         * display in `STATE_OFF`, and that is what put the launcher into single
         * screen for a moment on every wake: sleeping turns both panels off, and
         * on waking they come back one at a time. For the frame or two where the
         * primary was on and the second panel had not caught up, the second panel
         * was reported as *absent* — so `AUTO` resolved to a single-screen layout,
         * the launcher composed itself that way, and then rebuilt as dual the
         * instant the panel reported in.
         *
         * The asymmetry gives the mistake away: the primary display is never
         * treated as missing for being off, and the second panel is the same kind
         * of thing. Whether a display exists and whether it is currently lit are
         * two questions, and only the first one belongs here.
         */
        fun isUsableSecondary(displayId: Int, flags: Int): Boolean {
            if (displayId == Display.DEFAULT_DISPLAY) return false
            val isPresentation = flags and Display.FLAG_PRESENTATION != 0
            val isPrivate = flags and Display.FLAG_PRIVATE != 0
            // The AYN Thor's second panel reports as a public presentation
            // display; private virtual displays belong to other apps.
            return isPresentation && !isPrivate
        }
    }
}
