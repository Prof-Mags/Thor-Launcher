package com.thor.feature.topscreen

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.unit.Constraints
import org.junit.Test

/**
 * The search that keeps a synopsis inside the panel.
 *
 * Measurement itself belongs to Compose; what is worth pinning here is that the
 * search returns the *largest* fitting size rather than merely a fitting one,
 * and that it terminates — a loop that walked past its floor would spin on a
 * description no size can accommodate.
 */
class GameDescriptionFitTest {

    /** Height falls as the text shrinks: 400px at full size, proportionally less below. */
    private fun proportional(fullHeight: Int): (Float) -> Int =
        { scale -> (fullHeight * scale).toInt() }

    @Test
    fun `text that already fits is left at full size`() {
        assertThat(fittedTextScale(available = 400, measureHeight = proportional(300)))
            .isEqualTo(1f)
    }

    @Test
    fun `a small overflow is absorbed by stepping down`() {
        val scale = fittedTextScale(available = 490, measureHeight = proportional(500))

        assertThat(scale).isLessThan(1f)
        assertThat(proportional(500)(scale)).isAtMost(490)
    }

    /**
     * The floor has to leave enough range to be worth having.
     *
     * It was 0.95, which meant the fitter could absorb a five per cent overflow and
     * nothing more — so "shrink until it fits" was in practice "ellipsise", and the
     * mechanism that exists to stop a synopsis being cut mid-clause was doing
     * nothing at all for any description long enough to need it. A twelve per cent
     * overflow is an ordinary length of paragraph and has to survive.
     */
    @Test
    fun `an overflow of about a tenth is still absorbed`() {
        val scale = fittedTextScale(available = 440, measureHeight = proportional(500))

        assertThat(scale).isGreaterThan(MIN_DESCRIPTION_SCALE)
        assertThat(proportional(500)(scale)).isAtMost(440)
    }

    @Test
    fun `the largest fitting size is chosen, not merely a fitting one`() {
        val scale = fittedTextScale(available = 490, measureHeight = proportional(500))

        // One step larger would overflow, or the search stopped too early.
        assertThat(proportional(500)(scale + 0.03f)).isGreaterThan(490)
    }

    @Test
    fun `an overflow past the floor ellipsises rather than shrinking to fit`() {
        // The floor is deliberately shallow: text small enough to fit anything
        // trades one unreadable outcome for another. Named rather than written out,
        // so moving it does not silently change what this claims.
        val scale = fittedTextScale(available = 300, measureHeight = proportional(500))

        assertThat(scale).isEqualTo(MIN_DESCRIPTION_SCALE)
        assertThat(proportional(500)(scale)).isGreaterThan(300)
    }

    @Test
    fun `a description no size can fit stops at the floor rather than looping`() {
        val scale = fittedTextScale(available = 10, measureHeight = proportional(5_000))

        assertThat(scale).isEqualTo(MIN_DESCRIPTION_SCALE)
    }

    @Test
    fun `an unbounded panel does not shrink anything`() {
        val scale = fittedTextScale(
            available = Constraints.Infinity,
            measureHeight = proportional(5_000),
        )

        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun `a panel with no room left is not measured against zero`() {
        assertThat(fittedTextScale(available = 0, measureHeight = proportional(300)))
            .isEqualTo(1f)
    }
}
