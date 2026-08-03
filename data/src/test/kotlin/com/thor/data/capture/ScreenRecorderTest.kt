package com.thor.data.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that decides a recording's shape.
 *
 * Worth a test of its own because getting it wrong is invisible: the encoder still
 * produces a valid file, nothing logs, and the fault only shows up as a video whose
 * proportions are not the device's — which is exactly how the recorder shipped
 * showing one and a half of its two screens.
 */
class ScreenRecorderTest {

    /** How the AYN Thor's two panels actually stack. */
    private val panelWidth = 1920
    private val stackedHeight = 2160

    @Test
    fun `two stacked 1080p panels are recorded at their own resolution`() {
        assertThat(captureScale(panelWidth, stackedHeight)).isEqualTo(1f)
    }

    /**
     * The failure this replaced.
     *
     * The old code coerced each dimension separately, so a frame taller than the
     * ceiling kept its full width and lost its height — a different shape from the
     * one the launcher had measured for, which Compose fills by overflowing rather
     * than by shrinking. The bottom panel was simply cut off.
     */
    @Test
    fun `an oversized frame keeps its proportions instead of losing its height`() {
        val width = 1920
        val height = 2634 // what the retired 22% console-body allowance produced

        val scale = captureScale(width, height)
        val scaledWidth = width * scale
        val scaledHeight = height * scale

        assertThat(scaledHeight).isAtMost(CAPTURE_MAX_DIMENSION.toFloat())
        assertThat(aspectOf(scaledWidth, scaledHeight))
            .isWithin(TOLERANCE)
            .of(aspectOf(width.toFloat(), height.toFloat()))
    }

    @Test
    fun `a frame under the floor is grown, still in proportion`() {
        val width = 120
        val height = 200

        val scale = captureScale(width, height)

        assertThat(minOf(width * scale, height * scale))
            .isAtLeast(CAPTURE_MIN_DIMENSION.toFloat())
        assertThat(aspectOf(width * scale, height * scale))
            .isWithin(TOLERANCE)
            .of(aspectOf(width.toFloat(), height.toFloat()))
    }

    /** Whichever side is long, the ceiling applies to it. */
    @Test
    fun `a very wide frame is bounded by its width`() {
        val scale = captureScale(width = 5000, height = 1000)

        assertThat(5000 * scale).isAtMost(CAPTURE_MAX_DIMENSION.toFloat())
    }

    @Test
    fun `a degenerate size is left alone rather than dividing by zero`() {
        assertThat(captureScale(0, 0)).isEqualTo(1f)
        assertThat(captureScale(-10, 100)).isEqualTo(1f)
    }

    private fun aspectOf(width: Float, height: Float): Float = width / height

    private companion object {
        /**
         * Generous, because the caller rounds to even pixels afterwards and a
         * pixel either way is not a change of shape.
         */
        const val TOLERANCE = 0.01f
    }
}
