package com.thor.core.common.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Where a full stop ends a sentence and where it does not.
 *
 * The whole value of splitting on sentences rather than on characters is that
 * what comes back reads as finished. A split in the wrong place gives up that
 * value while keeping all of the cost, and it is invisible until somebody reads
 * a game's synopsis on the panel and finds it stopping after "Dr".
 */
class SentencesTest {

    @Test
    fun `plain prose splits on its terminators`() {
        val text = "Mario runs right. Bowser takes the princess. You give chase!"

        assertThat(text.splitSentences()).containsExactly(
            "Mario runs right.",
            "Bowser takes the princess.",
            "You give chase!",
        ).inOrder()
    }

    @Test
    fun `an abbreviation is not the end of a sentence`() {
        val text = "Developed by Dr. Light at Capcom Inc. The sequel followed in 1988."

        assertThat(text.splitSentences()).containsExactly(
            "Developed by Dr. Light at Capcom Inc.",
            "The sequel followed in 1988.",
        ).inOrder()
    }

    /**
     * The other half of the same rule.
     *
     * "Inc." is not on the never-break list, so this is decided by what follows
     * it: a lower-case word means the sentence is still going.
     */
    @Test
    fun `a company suffix mid-sentence does not split it`() {
        val text = "Capcom Inc. was founded in 1979. It began with arcade cabinets."

        assertThat(text.splitSentences()).containsExactly(
            "Capcom Inc. was founded in 1979.",
            "It began with arcade cabinets.",
        ).inOrder()
    }

    @Test
    fun `initials do not split a name`() {
        val text = "Adapted from J. R. R. Tolkien's novel. It sold poorly."

        assertThat(text.splitSentences()).containsExactly(
            "Adapted from J. R. R. Tolkien's novel.",
            "It sold poorly.",
        ).inOrder()
    }

    /** "Street Fighter II: The World Warrior" and friends. */
    @Test
    fun `a versus subtitle stays in one piece`() {
        val text = "Capcom vs. SNK is a crossover. Two rosters meet."

        assertThat(text.splitSentences()).hasSize(2)
    }

    @Test
    fun `a stop followed by lower case is not a break`() {
        val text = "Runs at 59.94 hz on the original hardware. Later ports differ."

        assertThat(text.splitSentences()).containsExactly(
            "Runs at 59.94 hz on the original hardware.",
            "Later ports differ.",
        ).inOrder()
    }

    @Test
    fun `text with no terminator is a single sentence`() {
        assertThat("A puzzle game with no full stop".splitSentences())
            .containsExactly("A puzzle game with no full stop")
    }

    @Test
    fun `blank text has no sentences`() {
        assertThat("   ".splitSentences()).isEmpty()
    }

    @Test
    fun `text already inside the limit is returned whole`() {
        val text = "Short and complete."

        assertThat(text.truncateToSentences(100)).isEqualTo(text)
    }

    @Test
    fun `truncation stops on a sentence boundary`() {
        val text = "One two three four. Five six seven eight. Nine ten eleven twelve."

        val short = text.truncateToSentences(45)

        assertThat(short).isEqualTo("One two three four. Five six seven eight.")
        assertThat(short).doesNotContain("Nine")
    }

    /**
     * The limit is a target, not a guarantee.
     *
     * Returning nothing for a synopsis whose first sentence runs long would be
     * worse than returning it: every caller is prepared for a description that
     * is too big to show and none of them are prepared for one that vanished.
     */
    @Test
    fun `an opening sentence longer than the limit is still kept`() {
        val text = "A very long opening sentence that runs well past the limit given. Then more."

        val short = text.truncateToSentences(20)

        assertThat(short).isEqualTo("A very long opening sentence that runs well past the limit given.")
    }

    @Test
    fun `truncation never cuts mid-word`() {
        val text = "Alpha beta gamma delta. Epsilon zeta eta theta. Iota kappa lambda."

        val short = text.truncateToSentences(50)

        assertThat(text).contains(short)
        assertThat(short.last()).isEqualTo('.')
    }
}
