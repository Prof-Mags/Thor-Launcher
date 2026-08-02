package com.thor.core.display

/**
 * Which of the launcher's two surfaces the user is working on.
 *
 * A *panel* is a role, not a screen: the grid and the info surface swap windows
 * with the display settings, and either can end up in either window.
 */
enum class LauncherPanel { GRID, INFO }

/** Which of the launcher's two windows a panel is drawn in. */
enum class LauncherWindow { ACTIVITY, PRESENTATION }

/**
 * Who holds the controller.
 *
 * Pulled out of the shell composable and made pure, because this is the rule the
 * dual-screen design has broken and had to re-derive several times, and every time
 * it broke it did so silently — a launcher that is drawn correctly and answers no
 * button looks identical to one that has crashed, and neither logs anything.
 *
 * The rule itself is two lines: the window holding the active panel takes focus,
 * unless something else has taken it and the user has not asked for it back.
 * Everything below is that, plus the cases each of the previous attempts got wrong.
 */
object LauncherFocus {

    /** The window a panel is drawn in, given where the display settings put the grid. */
    fun windowHolding(panel: LauncherPanel, gridInActivityWindow: Boolean): LauncherWindow =
        when (panel) {
            LauncherPanel.GRID ->
                if (gridInActivityWindow) LauncherWindow.ACTIVITY else LauncherWindow.PRESENTATION

            LauncherPanel.INFO ->
                if (gridInActivityWindow) LauncherWindow.PRESENTATION else LauncherWindow.ACTIVITY
        }

    /**
     * Whether the presentation should hold the device's key focus.
     *
     * @param activePanel the surface the user is working on: an open overlay's, or
     *   the one last touched.
     * @param gridInActivityWindow the `swapScreens` display setting.
     * @param overlayOpen whether the launcher has an overlay up. An overlay outranks
     *   a yielded claim, because an overlay only exists because the user just asked
     *   for one — the keyboard in particular is unusable if the window it is drawn in
     *   cannot take a key.
     * @param focusYieldedToApp whether another window took focus while the launcher
     *   was asking for it. Evidence that the user reached for something the launcher
     *   cannot see; cleared by any touch on a launcher surface.
     */
    fun presentationTakesFocus(
        activePanel: LauncherPanel,
        gridInActivityWindow: Boolean,
        overlayOpen: Boolean,
        focusYieldedToApp: Boolean,
        bothPanelsInActivityWindow: Boolean = false,
    ): Boolean {
        // Couch mode draws both surfaces in one window and leaves the presentation
        // dark. There is nothing there to drive, so it must never take a key —
        // [windowHolding] cannot say so on its own, because one boolean cannot
        // express "both", and it would hand focus to the blank panel the moment
        // the info surface became the active one.
        if (bothPanelsInActivityWindow) return false

        val active = windowHolding(activePanel, gridInActivityWindow)
        return active == LauncherWindow.PRESENTATION && (overlayOpen || !focusYieldedToApp)
    }

    /**
     * Whether a launch is a reason for the presentation to stand down.
     *
     * Only a launch onto the display the presentation projects onto. This is the
     * distinction that was missing, and its absence is the whole of the frozen-panel
     * bug: an app opening on the *activity's* display is not competing for the second
     * panel, but the launcher stood that panel down for it anyway — so the screen the
     * user was holding stayed lit, kept animating, and answered nothing, with Home the
     * only way back.
     */
    fun launchYieldsPresentationFocus(launchedOnSecondaryPanel: Boolean): Boolean =
        launchedOnSecondaryPanel
}
