package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.ArtworkSet
import org.junit.Test

/**
 * Whether a re-scrape can actually change anything.
 *
 * It could not. Every artwork slot was written on the first pass and kept for
 * good afterwards, so a library scraped by one provider kept that provider's
 * images however many times it was scraped again — which made "re-scrape" a
 * button that reported success and changed nothing.
 *
 * The merge itself is private, so these exercise the rule it applies rather than
 * the function; keeping the rule written down is the point, because both
 * directions of it are load-bearing and neither is obvious.
 */
class ArtworkMergeTest {

    private val stale = "https://old/cover.jpg"
    private val fresh = "https://new/cover.jpg"

    /** The rule `mergeArtwork` applies to every single-image slot. */
    private fun slot(current: String?, fetched: String?, replace: Boolean): String? =
        if (replace) fetched ?: current else current ?: fetched

    @Test
    fun `a top-up leaves an image that is already there`() {
        assertThat(slot(stale, fresh, replace = false)).isEqualTo(stale)
    }

    @Test
    fun `a full re-scrape replaces it`() {
        assertThat(slot(stale, fresh, replace = true)).isEqualTo(fresh)
    }

    @Test
    fun `a slot nobody answered keeps what it had`() {
        // The important half: a provider being down or rate-limited must never
        // blank a library that already has artwork.
        assertThat(slot(stale, null, replace = true)).isEqualTo(stale)
    }

    @Test
    fun `an empty slot takes whatever arrives, either way`() {
        assertThat(slot(null, fresh, replace = true)).isEqualTo(fresh)
        assertThat(slot(null, fresh, replace = false)).isEqualTo(fresh)
    }

    /** The ordering rule for the screenshot list, which has a cap to compete for. */
    private fun screenshots(
        current: List<String>,
        fetched: List<String>,
        replace: Boolean,
    ): List<String> = when {
        replace -> fetched + current
        else -> current + fetched
    }.distinct().take(ArtworkSet.MAX_SCREENSHOTS)

    @Test
    fun `a full set of stale shots cannot shut the new ones out`() {
        // This is the specific failure: three stored images filled the cap, so
        // appending the fetched ones put them past it and nothing changed.
        val current = listOf("old1", "old2", "old3")
        val fetched = listOf("new1", "new2", "new3")

        assertThat(screenshots(current, fetched, replace = true))
            .containsExactly("new1", "new2", "new3").inOrder()
    }

    @Test
    fun `a top-up keeps what is shown and fills the remainder`() {
        val current = listOf("old1")
        val fetched = listOf("new1", "new2", "new3")

        assertThat(screenshots(current, fetched, replace = false))
            .containsExactly("old1", "new1", "new2").inOrder()
    }

    @Test
    fun `a re-scrape that finds nothing keeps the images it had`() {
        val current = listOf("old1", "old2")

        assertThat(screenshots(current, emptyList(), replace = true))
            .containsExactly("old1", "old2").inOrder()
    }

    @Test
    fun `the same image from two providers is only counted once`() {
        val current = listOf("shared")
        val fetched = listOf("shared", "new1")

        assertThat(screenshots(current, fetched, replace = true))
            .containsExactly("shared", "new1").inOrder()
    }
}
