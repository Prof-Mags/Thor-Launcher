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
}
