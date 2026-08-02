package com.thor.core.display

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The focus rule, and specifically the cases it has been got wrong in.
 *
 * Each of these corresponds to a way the launcher has actually failed on the
 * hardware, so they are written as the symptom rather than as the mechanism.
 */
class LauncherFocusTest {

    // ---- Where a panel lives ------------------------------------------------

    @Test
    fun `grid is in the presentation by default`() {
        assertThat(LauncherFocus.windowHolding(LauncherPanel.GRID, gridInActivityWindow = false))
            .isEqualTo(LauncherWindow.PRESENTATION)
        assertThat(LauncherFocus.windowHolding(LauncherPanel.INFO, gridInActivityWindow = false))
            .isEqualTo(LauncherWindow.ACTIVITY)
    }

    @Test
    fun `swapping screens exchanges the two windows`() {
        assertThat(LauncherFocus.windowHolding(LauncherPanel.GRID, gridInActivityWindow = true))
            .isEqualTo(LauncherWindow.ACTIVITY)
        assertThat(LauncherFocus.windowHolding(LauncherPanel.INFO, gridInActivityWindow = true))
            .isEqualTo(LauncherWindow.PRESENTATION)
    }

    // ---- The frozen panel ---------------------------------------------------

    /**
     * The bug this rule exists for: an app on the *other* display must not cost the
     * panel the user is holding its controller.
     */
    @Test
    fun `launching onto the activity's panel does not stand the presentation down`() {
        val yields = LauncherFocus.launchYieldsPresentationFocus(launchedOnSecondaryPanel = false)
        assertThat(yields).isFalse()

        // Which is to say: the grid, on the second panel, keeps focus throughout.
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.GRID,
                gridInActivityWindow = false,
                overlayOpen = false,
                focusYieldedToApp = yields,
            ),
        ).isTrue()
    }

    @Test
    fun `launching onto the presentation's own panel does stand it down`() {
        val yields = LauncherFocus.launchYieldsPresentationFocus(launchedOnSecondaryPanel = true)
        assertThat(yields).isTrue()

        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.GRID,
                gridInActivityWindow = false,
                overlayOpen = false,
                focusYieldedToApp = yields,
            ),
        ).isFalse()
    }

    /** Touching a launcher surface clears the yield, which is how the pad comes back. */
    @Test
    fun `a touch after yielding takes focus straight back`() {
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.GRID,
                gridInActivityWindow = false,
                overlayOpen = false,
                focusYieldedToApp = false,
            ),
        ).isTrue()
    }

    // ---- Following the active panel ----------------------------------------

    @Test
    fun `focus follows the active panel across the swap setting`() {
        // Info panel active, grid in the activity window: the info panel is the
        // presentation, so the presentation takes focus.
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.INFO,
                gridInActivityWindow = true,
                overlayOpen = false,
                focusYieldedToApp = false,
            ),
        ).isTrue()

        // Same panel active, screens the usual way round: now it is the activity's.
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.INFO,
                gridInActivityWindow = false,
                overlayOpen = false,
                focusYieldedToApp = false,
            ),
        ).isFalse()
    }

    // ---- Overlays -----------------------------------------------------------

    /**
     * An overlay is always the user's most recent request, so it outranks a yielded
     * claim. Without this the keyboard could be raised on a panel that was refusing
     * key input, which is a keyboard that cannot be typed on.
     */
    @Test
    fun `an overlay claims focus even after the launcher has yielded it`() {
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.GRID,
                gridInActivityWindow = false,
                overlayOpen = true,
                focusYieldedToApp = true,
            ),
        ).isTrue()
    }

    @Test
    fun `an overlay on the other window does not pull focus to the presentation`() {
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.INFO,
                gridInActivityWindow = false,
                overlayOpen = true,
                focusYieldedToApp = false,
            ),
        ).isFalse()
    }

    // ---- Couch mode ---------------------------------------------------------

    /**
     * The darkened panel must never take a key, in any combination.
     *
     * Couch mode draws both surfaces in the activity window and holds the other
     * panel black. `gridInActivityWindow` cannot describe that on its own — it
     * says which window holds the grid, and here the answer for *both* panels is
     * the same window — so without the override, the info surface becoming active
     * pointed focus at the panel showing nothing, and the controller stopped
     * driving the screen the user was looking at.
     */
    @Test
    fun `couch mode never gives focus to the darkened panel`() {
        for (panel in LauncherPanel.entries) {
            for (overlay in listOf(false, true)) {
                for (yielded in listOf(false, true)) {
                    assertThat(
                        LauncherFocus.presentationTakesFocus(
                            activePanel = panel,
                            gridInActivityWindow = true,
                            overlayOpen = overlay,
                            focusYieldedToApp = yielded,
                            bothPanelsInActivityWindow = true,
                        ),
                    ).isFalse()
                }
            }
        }
    }

    /** And it is off by default, so every two-panel case is unchanged. */
    @Test
    fun `the two-panel rule is untouched when the panels are in two windows`() {
        assertThat(
            LauncherFocus.presentationTakesFocus(
                activePanel = LauncherPanel.GRID,
                gridInActivityWindow = false,
                overlayOpen = false,
                focusYieldedToApp = false,
            ),
        ).isTrue()
    }
}
