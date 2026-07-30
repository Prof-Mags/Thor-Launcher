package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Match confidence gates whether a scraper result is allowed to contribute
 * metadata at all, so its ranking behaviour matters more than its absolute
 * values: the right game must always out-score the wrong one.
 */
class TitleMatcherTest {

    @Test
    fun `identical titles score perfectly`() {
        assertThat(TitleMatcher.confidence("Chrono Trigger", "Chrono Trigger")).isEqualTo(1f)
    }

    @Test
    fun `case and punctuation are ignored`() {
        val score = TitleMatcher.confidence("Ratchet & Clank", "ratchet and clank")
        assertThat(score).isGreaterThan(0.6f)
    }

    @Test
    fun `subtitle differences still match strongly`() {
        val score = TitleMatcher.confidence("Pokemon Red", "Pokemon Red Version")
        assertThat(score).isGreaterThan(0.5f)
    }

    @Test
    fun `a different game scores low`() {
        val score = TitleMatcher.confidence("Chrono Trigger", "Chrono Cross")
        assertThat(score).isLessThan(0.6f)
    }

    @Test
    fun `an unrelated title scores near zero`() {
        val score = TitleMatcher.confidence("Metroid Prime", "Animal Crossing")
        assertThat(score).isLessThan(0.2f)
    }

    @Test
    fun `the correct sequel outranks the wrong one`() {
        val right = TitleMatcher.confidence("Final Fantasy VII", "Final Fantasy VII")
        val wrong = TitleMatcher.confidence("Final Fantasy VII", "Final Fantasy VIII")
        assertThat(right).isGreaterThan(wrong)
    }

    @Test
    fun `a short query does not match a much longer title as well as an exact one`() {
        val exact = TitleMatcher.confidence("Zelda", "Zelda")
        val longer = TitleMatcher.confidence(
            "Zelda",
            "Zelda II The Adventure of Link Special Collector Edition",
        )
        assertThat(exact).isGreaterThan(longer)
    }

    @Test
    fun `empty input never produces a usable score`() {
        assertThat(TitleMatcher.confidence("", "Chrono Trigger")).isEqualTo(0f)
        assertThat(TitleMatcher.confidence("Chrono Trigger", "")).isEqualTo(0f)
    }

    @Test
    fun `scores always stay within range`() {
        val pairs = listOf(
            "Super Mario World" to "Super Mario World 2 Yoshi's Island",
            "Halo" to "Halo Combat Evolved Anniversary Edition",
            "Doom" to "DOOM",
            "F-Zero" to "F Zero X",
        )
        pairs.forEach { (query, candidate) ->
            val score = TitleMatcher.confidence(query, candidate)
            assertThat(score).isAtLeast(0f)
            assertThat(score).isAtMost(1f)
        }
    }
}
