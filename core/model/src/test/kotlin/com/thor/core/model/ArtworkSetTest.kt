package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The screenshot cap is enforced on read as well as on write.
 *
 * Capping only at ingestion would leave every library scraped by an earlier
 * build with over-long lists already persisted, and the slideshow and the
 * editor would still have to cope with them.
 */
class ArtworkSetTest {

    @Test
    fun `capped screenshots trims an over-long list`() {
        val artwork = ArtworkSet(screenshots = List(12) { "shot-$it" })

        assertThat(artwork.cappedScreenshots).hasSize(ArtworkSet.MAX_SCREENSHOTS)
        // The first few, not an arbitrary slice: providers return them in
        // relevance order. Derived from the cap so retuning it does not require
        // editing the expectation.
        val expected = List(ArtworkSet.MAX_SCREENSHOTS) { "shot-$it" }
        assertThat(artwork.cappedScreenshots).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `capped screenshots leaves a short list alone`() {
        val artwork = ArtworkSet(screenshots = listOf("a", "b"))
        assertThat(artwork.cappedScreenshots).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `capped screenshots on an empty set is empty`() {
        assertThat(ArtworkSet.EMPTY.cappedScreenshots).isEmpty()
    }

    @Test
    fun `cell image prefers the square icon over cover art`() {
        assertThat(ArtworkSet(boxArt = "box", icon = "ico", hero = "hero").cellImage)
            .isEqualTo("ico")
        assertThat(ArtworkSet(boxArt = "box", hero = "hero").cellImage).isEqualTo("box")
    }

    @Test
    fun `cell image never returns wide art`() {
        // The whole point: a 16:9 hero or screenshot in a 1:1 cell is either
        // cropped to an unrecognisable strip or letterboxed into a stripe, and the
        // letterboxing is what made the grid look full of 16:9 images. With no
        // square source the cell falls back to the initials plate instead.
        assertThat(ArtworkSet(hero = "hero").cellImage).isNull()
        assertThat(ArtworkSet(screenshots = listOf("shot")).cellImage).isNull()
        assertThat(ArtworkSet(hero = "hero", screenshots = listOf("shot")).cellImage).isNull()
    }

    @Test
    fun `background image prefers the wide hero`() {
        val artwork = ArtworkSet(boxArt = "box", hero = "hero", screenshots = listOf("shot"))
        assertThat(artwork.backgroundImage).isEqualTo("hero")
    }

    @Test
    fun `is empty only when nothing at all is present`() {
        assertThat(ArtworkSet.EMPTY.isEmpty).isTrue()
        assertThat(ArtworkSet(screenshots = listOf("shot")).isEmpty).isFalse()
        assertThat(ArtworkSet(videoUri = "clip").isEmpty).isFalse()
    }
}
