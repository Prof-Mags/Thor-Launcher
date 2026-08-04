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
    fun `text that overflows is stepped down until it fits`() {
        // 500px into 460px needs 0.92 or less; the first steps still overflow.
        val scale = fittedTextScale(available = 460, measureHeight = proportional(500))

        assertThat(scale).isLessThan(1f)
        assertThat(proportional(500)(scale)).isAtMost(460)
    }

    @Test
    fun `the largest fitting size is chosen, not merely a fitting one`() {
        val scale = fittedTextScale(available = 460, measureHeight = proportional(500))

        // One step larger would overflow, or the search stopped too early.
        assertThat(proportional(500)(scale + 0.03f)).isGreaterThan(460)
    }

    @Test
    fun `a description no size can fit stops at the floor rather than looping`() {
        val scale = fittedTextScale(available = 10, measureHeight = proportional(5_000))

        assertThat(scale).isEqualTo(0.88f)
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
