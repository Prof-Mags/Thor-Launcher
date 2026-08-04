package com.thor.core.display

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.DualScreenMode
import org.junit.Test

/**
 * Mode resolution decides whether the launcher opens a second window at all.
 * Getting it wrong either loses the info panel entirely or tries to project
 * onto a display that is not there, so every combination is pinned here.
 */
class DisplayTopologyTest {

    private val primary = ThorDisplayInfo(
        displayId = 0,
        name = "Built-in",
        widthPx = 1920,
        heightPx = 1080,
        densityDpi = 420,
        refreshRate = 120f,
        isPrimary = true,
        isPresentationCapable = false,
    )

    private val secondary = primary.copy(
        displayId = 1,
        name = "Secondary",
        widthPx = 1920,
        heightPx = 1080,
        isPrimary = false,
        isPresentationCapable = true,
    )

    private fun topology(mode: DualScreenMode, withSecondary: Boolean) = DisplayTopology(
        primary = primary,
        secondary = if (withSecondary) secondary else null,
        requestedMode = mode,
    )

    // ---- What counts as a second panel --------------------------------------

    @Test
    fun `a public presentation display is the second panel`() {
        assertThat(DisplayTopology.isUsableSecondary(displayId = 1, flags = FLAG_PRESENTATION))
            .isTrue()
    }

    @Test
    fun `the primary display is never the second panel`() {
        assertThat(DisplayTopology.isUsableSecondary(displayId = 0, flags = FLAG_PRESENTATION))
            .isFalse()
    }

    /** A recorder's or a cast target's own surface must not steal the info panel. */
    @Test
    fun `private virtual displays are not second panels`() {
        assertThat(
            DisplayTopology.isUsableSecondary(
                displayId = 2,
                flags = FLAG_PRESENTATION or FLAG_PRIVATE,
            ),
        ).isFalse()
        assertThat(DisplayTopology.isUsableSecondary(displayId = 2, flags = 0)).isFalse()
    }

    /**
     * The wake-from-sleep flash.
     *
     * Sleeping turns both panels off and they come back one at a time. While the
     * rule consulted `Display.state`, the second panel read as *absent* for the
     * frames before it caught up — so `AUTO` resolved to a single-screen layout,
     * the launcher composed itself that way, and rebuilt as dual a moment later.
     *
     * Power state is not part of this question, which is why there is no state
     * parameter to pass: the primary display is never called missing for being
     * off, and the second panel is the same kind of thing.
     */
    @Test
    fun `a second panel that is merely powered off is still a second panel`() {
        // Exactly what the display reports while the device is waking: attached,
        // public, presentation-capable — and not yet lit.
        assertThat(DisplayTopology.isUsableSecondary(displayId = 1, flags = FLAG_PRESENTATION))
            .isTrue()

        // And the mode that would have been resolved from it stays dual.
        assertThat(topology(DualScreenMode.AUTO, withSecondary = true).effectiveMode)
            .isEqualTo(DualScreenMode.DUAL_DISPLAY)
    }

    @Test
    fun `auto uses dual display when a second panel is present`() {
        val resolved = topology(DualScreenMode.AUTO, withSecondary = true)
        assertThat(resolved.effectiveMode).isEqualTo(DualScreenMode.DUAL_DISPLAY)
        assertThat(resolved.needsPresentation).isTrue()
    }

    @Test
    fun `auto falls back to split when there is only one panel`() {
        val resolved = topology(DualScreenMode.AUTO, withSecondary = false)
        assertThat(resolved.effectiveMode).isEqualTo(DualScreenMode.SPLIT_SINGLE)
        assertThat(resolved.needsPresentation).isFalse()
    }

    @Test
    fun `explicit dual display degrades gracefully when the panel is gone`() {
        val resolved = topology(DualScreenMode.DUAL_DISPLAY, withSecondary = false)
        assertThat(resolved.effectiveMode).isEqualTo(DualScreenMode.SPLIT_SINGLE)
        assertThat(resolved.needsPresentation).isFalse()
    }

    @Test
    fun `explicit split is honoured even with a second panel attached`() {
        val resolved = topology(DualScreenMode.SPLIT_SINGLE, withSecondary = true)
        assertThat(resolved.effectiveMode).isEqualTo(DualScreenMode.SPLIT_SINGLE)
        assertThat(resolved.needsPresentation).isFalse()
    }

    @Test
    fun `single screen never requests a presentation`() {
        val withPanel = topology(DualScreenMode.SINGLE, withSecondary = true)
        val withoutPanel = topology(DualScreenMode.SINGLE, withSecondary = false)

        assertThat(withPanel.effectiveMode).isEqualTo(DualScreenMode.SINGLE)
        assertThat(withPanel.needsPresentation).isFalse()
        assertThat(withoutPanel.needsPresentation).isFalse()
    }

    /**
     * Couch mode keeps the second window and puts nothing in it.
     *
     * The presentation is what holds the panel dark. Releasing it hands the screen
     * back to the system, which lights it with the wallpaper — the opposite of
     * what the mode is for. So "needs a window there" is true while "shows a
     * surface there" is false, which is why the two stopped being one question.
     */
    @Test
    fun `couch mode keeps a window on the second panel in order to darken it`() {
        val withPanel = topology(DualScreenMode.COUCH, withSecondary = true)
        val withoutPanel = topology(DualScreenMode.COUCH, withSecondary = false)

        assertThat(withPanel.effectiveMode).isEqualTo(DualScreenMode.COUCH)
        assertThat(withPanel.needsPresentation).isTrue()

        // And it is still couch mode with nothing to darken.
        assertThat(withoutPanel.effectiveMode).isEqualTo(DualScreenMode.COUCH)
        assertThat(withoutPanel.needsPresentation).isFalse()
    }

    @Test
    fun `aspect ratio is derived from the panel dimensions`() {
        assertThat(primary.aspectRatio).isWithin(0.001f).of(1920f / 1080f)
    }

    @Test
    fun `a zero height panel does not divide by zero`() {
        val degenerate = primary.copy(heightPx = 0)
        assertThat(degenerate.aspectRatio).isEqualTo(1f)
    }

    // ---- An attached monitor -------------------------------------------------

    /**
     * The device's own second panel is not a monitor.
     *
     * The whole risk in counting displays rather than naming them, and the reason
     * this is tested first: the Thor always reports a second panel, so mistaking it
     * for something the user plugged in would put every device permanently into a
     * mode meant for a docked one.
     */
    @Test
    fun `the built-in second panel is not an external display`() {
        assertThat(DisplayTopology.hasExternalDisplay(listOf(primary, secondary))).isFalse()
        assertThat(DisplayTopology.hasExternalDisplay(listOf(primary))).isFalse()
        assertThat(DisplayTopology.hasExternalDisplay(emptyList())).isFalse()
    }

    @Test
    fun `a third usable display is a monitor`() {
        val monitor = secondary.copy(displayId = 7, name = "HDMI")

        assertThat(DisplayTopology.hasExternalDisplay(listOf(primary, secondary, monitor)))
            .isTrue()
    }

    /**
     * A recording must not look like a monitor.
     *
     * The launcher creates a display of its own while recording. One that counted
     * here would flip the entire interface into couch mode the moment the user
     * pressed record — which is why the filter is on *usable* displays, and why
     * ours are private.
     */
    @Test
    fun `a display of our own is not a monitor`() {
        val ours = secondary.copy(displayId = 9, isPresentationCapable = false)

        assertThat(DisplayTopology.hasExternalDisplay(listOf(primary, secondary, ours)))
            .isFalse()
    }

    /** Plugging a monitor in is what asks for couch mode, and only on Automatic. */
    @Test
    fun `automatic switches to couch mode for a monitor, and nothing else does`() {
        val monitor = secondary.copy(displayId = 7)
        val withMonitor = { mode: DualScreenMode ->
            DisplayTopology(
                primary = primary,
                secondary = monitor,
                requestedMode = mode,
                hasExternalDisplay = true,
            )
        }

        assertThat(withMonitor(DualScreenMode.AUTO).effectiveMode)
            .isEqualTo(DualScreenMode.COUCH)

        // A mode chosen outright is an instruction; an attached screen does not
        // overrule it.
        assertThat(withMonitor(DualScreenMode.DUAL_DISPLAY).effectiveMode)
            .isEqualTo(DualScreenMode.DUAL_DISPLAY)
        assertThat(withMonitor(DualScreenMode.SPLIT_SINGLE).effectiveMode)
            .isEqualTo(DualScreenMode.SPLIT_SINGLE)
        assertThat(withMonitor(DualScreenMode.SINGLE).effectiveMode)
            .isEqualTo(DualScreenMode.SINGLE)
    }

    private companion object {
        /** `Display.FLAG_PRESENTATION`, written out so this stays a JVM test. */
        const val FLAG_PRESENTATION = 1 shl 3

        /** `Display.FLAG_PRIVATE`. */
        const val FLAG_PRIVATE = 1 shl 2
    }
}
