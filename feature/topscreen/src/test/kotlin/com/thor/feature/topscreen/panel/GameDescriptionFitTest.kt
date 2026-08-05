package com.thor.feature.topscreen.panel

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

    // ---- Shortening, once shrinking has run out ------------------------------

    /** 10px per character at full size, scaling with the text. */
    private fun perCharacter(): (Float, String) -> Int =
        { scale, body -> (body.length * 10 * scale).toInt() }

    private val threeSentences =
        "Bowser takes the princess. Mario gives chase. The castle is always empty."

    @Test
    fun `a synopsis that fits is left whole and unshrunk`() {
        val fitted = fitDescription(threeSentences, available = 10_000, measureHeight = perCharacter())

        assertThat(fitted.text).isEqualTo(threeSentences)
        assertThat(fitted.scale).isEqualTo(1f)
    }

    @Test
    fun `shrinking is tried before anything is dropped`() {
        // Fits at a smaller size, so the whole text survives.
        val available = (threeSentences.length * 10 * 0.9f).toInt()

        val fitted = fitDescription(threeSentences, available, perCharacter())

        assertThat(fitted.text).isEqualTo(threeSentences)
        assertThat(fitted.scale).isLessThan(1f)
    }

    /**
     * The case that put "…kidnapped by Bows" on the panel.
     *
     * Too long to fit even at the floor, so sentences come off — and what is
     * left has to end where a sentence ended.
     */
    @Test
    fun `text past the floor loses whole sentences rather than being cut`() {
        val available = (30 * 10 * MIN_DESCRIPTION_SCALE).toInt()

        val fitted = fitDescription(threeSentences, available, perCharacter())

        assertThat(fitted.text).isEqualTo("Bowser takes the princess.")
        assertThat(fitted.scale).isEqualTo(MIN_DESCRIPTION_SCALE)
        assertThat(threeSentences).contains(fitted.text)
    }

    @Test
    fun `the most sentences that fit are kept, not merely some`() {
        val available = (60 * 10 * MIN_DESCRIPTION_SCALE).toInt()

        val fitted = fitDescription(threeSentences, available, perCharacter())

        assertThat(fitted.text).isEqualTo("Bowser takes the princess. Mario gives chase.")
    }

    @Test
    fun `a single sentence too long for the panel is still shown`() {
        val one = "One enormous sentence with nowhere to break it at all."

        val fitted = fitDescription(one, available = 10, measureHeight = perCharacter())

        assertThat(fitted.text).isEqualTo(one)
        assertThat(fitted.scale).isEqualTo(MIN_DESCRIPTION_SCALE)
    }

    @Test
    fun `an unbounded panel keeps the whole synopsis`() {
        val fitted = fitDescription(
            threeSentences,
            available = Constraints.Infinity,
            measureHeight = perCharacter(),
        )

        assertThat(fitted.text).isEqualTo(threeSentences)
        assertThat(fitted.scale).isEqualTo(1f)
    }
}
