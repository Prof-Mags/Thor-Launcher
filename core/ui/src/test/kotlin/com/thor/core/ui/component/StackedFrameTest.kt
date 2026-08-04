package com.thor.core.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The recording's frame.
 *
 * The failure this guards against has happened twice: the frame and the layout
 * that fills it were computed from different numbers, disagreed, and a recording
 * of two screens showed one and a half. Both now come from the panels, and these
 * hold the arithmetic still.
 */
class StackedFrameTest {

    // The hardware: a 16:9 lid over a narrower, squarer panel.
    private val topWidth = 1920
    private val topAspect = 16f / 9f
    private val bottomWidth = 1080
    private val bottomAspect = 1f

    @Test
    fun `width is the wider panel, so it records at its own resolution`() {
        val frame = stackedFrameSize(topWidth, topAspect, bottomWidth, bottomAspect, ceiling = 4096)

        assertThat(frame.width).isEqualTo(1920)
    }

    @Test
    fun `height is the two panels stacked and nothing more`() {
        val frame = stackedFrameSize(topWidth, topAspect, bottomWidth, bottomAspect, ceiling = 4096)

        // 1920/(16:9) = 1080 for the lid, 1080/1 = 1080 for the base.
        assertThat(frame.height).isEqualTo(2160)
    }

    @Test
    fun `a frame over the ceiling scales whole, keeping its shape`() {
        val big = stackedFrameSize(3840, topAspect, 2160, bottomAspect, ceiling = 1920)
        val unscaled = stackedFrameSize(3840, topAspect, 2160, bottomAspect, ceiling = 9999)

        assertThat(maxOf(big.width, big.height)).isAtMost(1920)
        // Same proportions: scaling one dimension and clamping the other is what
        // cut the bottom panel off the recording before.
        val wanted = unscaled.width.toFloat() / unscaled.height
        assertThat(big.width.toFloat() / big.height).isWithin(0.02f).of(wanted)
    }

    @Test
    fun `both dimensions come back even, which the encoder requires`() {
        val frame = stackedFrameSize(1921, 1.77f, 1081, 0.99f, ceiling = 4096)

        assertThat(frame.width % 2).isEqualTo(0)
        assertThat(frame.height % 2).isEqualTo(0)
    }

    @Test
    fun `an absurd display cannot produce a frame the encoder refuses`() {
        val frame = stackedFrameSize(1, 0.0001f, 1, 9999f, ceiling = 2160)

        assertThat(frame.width).isAtLeast(2)
        assertThat(frame.height).isAtLeast(2)
        assertThat(maxOf(frame.width, frame.height)).isAtMost(2160)
    }

    @Test
    fun `one screen recorded twice is simply a square of it`() {
        // The single-display fallback hands the same panel in for both halves.
        val frame = stackedFrameSize(1080, 1f, 1080, 1f, ceiling = 4096)

        assertThat(frame.width).isEqualTo(1080)
        assertThat(frame.height).isEqualTo(2160)
    }
}
