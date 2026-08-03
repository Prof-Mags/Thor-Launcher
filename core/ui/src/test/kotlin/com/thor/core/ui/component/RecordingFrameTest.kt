package com.thor.core.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The recording frame's shape.
 *
 * [recordingFrameSize] and the console that fills it have to agree, and when they
 * did not the failure was silent: the encoder produced a perfectly valid file in
 * which the base's panel was cut in half. These pin the properties that make that
 * impossible rather than the exact pixel figures, so the console can be restyled
 * without rewriting the test — only changed *proportions* should fail here.
 */
class RecordingFrameTest {

    /** The AYN Thor: two 1920x1080 panels. */
    private val panelAspect = 1920f / 1080f
    private val panelWidth = 1920

    private fun frame(
        top: Float = panelAspect,
        bottom: Float = panelAspect,
    ) = recordingFrameSize(panelWidth, top, bottom)

    @Test
    fun `the frame holds both screens and the chrome around them`() {
        val f = frame()

        val screens = f.width * topScreenWidth() / panelAspect +
            f.width * bottomScreenWidth() / panelAspect

        assertThat(f.height.toFloat()).isGreaterThan(screens)
        // ...and is not so tall that the device is lost in empty body.
        assertThat(f.height.toFloat()).isLessThan(screens * GENEROUS_CEILING)
    }

    /**
     * The lid's screen is the bigger one, which is what makes it read as a handheld
     * rather than as two equal rectangles in a frame.
     */
    @Test
    fun `the base's screen is meaningfully smaller than the lid's`() {
        assertThat(bottomScreenWidth()).isLessThan(topScreenWidth())

        // Not merely smaller — visibly so.
        assertThat(bottomScreenWidth() / topScreenWidth()).isLessThan(NOTICEABLY_SMALLER)
    }

    /**
     * The frame arrives already inside the encoder's range.
     *
     * Not a formality: the recorder scales an oversized frame down in proportion, so
     * overshooting costs every screen its sharpness for nothing. Worth knowing at
     * build time rather than discovering in a soft video.
     */
    @Test
    fun `a 1080p handheld records without needing to be scaled down`() {
        val f = frame()

        assertThat(maxOf(f.width, f.height)).isAtMost(ENCODER_CEILING)
    }

    /** The console is wider than its screens, so the frame is wider than a panel. */
    @Test
    fun `the frame is sized up so the lid's screen keeps as many pixels as it can`() {
        val f = frame()

        assertThat(f.width).isGreaterThan(panelWidth)
        assertThat(f.width.toFloat()).isLessThan(panelWidth * WIDTH_CEILING)
    }

    /*
     * Both of these compare the frame's *shape*, not its height.
     *
     * Once the frame is limited by the encoder's ceiling its height pins to exactly
     * that, whatever the panels are — so two very different consoles come out the
     * same number of pixels tall and an assertion on height proves nothing. The
     * proportions are what the panels actually decide.
     */
    private fun shapeOf(frame: RecordingFrame): Float = frame.height.toFloat() / frame.width

    @Test
    fun `a squarer panel makes a taller frame than a wide one`() {
        assertThat(shapeOf(frame(top = 1f, bottom = 1f)))
            .isGreaterThan(shapeOf(frame(top = 2f, bottom = 2f)))
    }

    @Test
    fun `both panels are accounted for, not just the lid's`() {
        // A squarer base panel is taller, so the frame has to grow for it.
        assertThat(shapeOf(frame(bottom = 1f))).isGreaterThan(shapeOf(frame()))
    }

    /** A display reporting nonsense must not produce an unencodable frame. */
    @Test
    fun `an absurd aspect is clamped rather than propagated`() {
        val f = recordingFrameSize(panelWidth, 0.0001f, 9999f)

        assertThat(f.width).isGreaterThan(0)
        assertThat(f.height).isGreaterThan(0)
        assertThat(maxOf(f.width, f.height)).isAtMost(ENCODER_CEILING)
    }

    @Test
    fun `a tiny panel still produces a usable frame`() {
        val f = recordingFrameSize(1, panelAspect, panelAspect)

        assertThat(f.width).isAtLeast(1)
        assertThat(f.height).isAtLeast(1)
    }

    private companion object {
        /** Chrome should be a frame, not a poster. */
        const val GENEROUS_CEILING = 1.45f

        /** The base's screen against the lid's; anything above this reads as equal. */
        const val NOTICEABLY_SMALLER = 0.8f

        /** The console is wider than its screens, but it is not a poster frame. */
        const val WIDTH_CEILING = 1.5f
    }
}
