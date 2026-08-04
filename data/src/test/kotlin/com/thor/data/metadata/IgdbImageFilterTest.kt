package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which of IGDB's images the strip is allowed to show.
 *
 * Measured against the real API rather than assumed: `screenshots` come back
 * uniformly 1280 by 720, while `artworks` are whatever was uploaded — 720 by
 * 1280 portrait, 1200 square, 640 by 399. Feeding the second set to a widescreen
 * strip is how a row of mismatched shapes appears, and no framing decision
 * downstream repairs an image taller than it is wide.
 */
class IgdbImageFilterTest {

    private fun image(id: String, width: Int?, height: Int?) =
        IgdbImage(imageId = id, width = width, height = height)

    @Test
    fun `a sixteen by nine capture is kept`() {
        assertThat(landscapeImages(listOf(image("shot", 1280, 720)))).containsExactly("shot")
    }

    @Test
    fun `a portrait key art is dropped`() {
        // A real one: 720x1280, from Super Mario Odyssey's artworks.
        assertThat(landscapeImages(listOf(image("tall", 720, 1280)))).isEmpty()
    }

    @Test
    fun `a square image is dropped`() {
        assertThat(landscapeImages(listOf(image("square", 1200, 1200)))).isEmpty()
    }

    @Test
    fun `a wide image that is merely small is dropped`() {
        // 640x399 is the right shape and the wrong size; upscaling it into the
        // panel looks worse than showing one fewer image.
        assertThat(landscapeImages(listOf(image("small", 640, 399)))).isEmpty()
    }

    @Test
    fun `the band is generous enough for near misses`() {
        // 1260x720 is 1.75 and 1890x1080 is 1.75 — both real artworks, both fine.
        val kept = landscapeImages(
            listOf(image("a", 1260, 720), image("b", 1890, 1080)),
        )

        assertThat(kept).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `an image with no dimensions reported is dropped rather than gambled on`() {
        val images = listOf(
            image("unknown", null, null),
            image("halfknown", 1920, null),
            image("good", 1920, 1080),
        )

        assertThat(landscapeImages(images)).containsExactly("good")
    }

    @Test
    fun `a zero height cannot divide`() {
        assertThat(landscapeImages(listOf(image("broken", 1920, 0)))).isEmpty()
    }

    @Test
    fun `order is preserved, since the first becomes the backdrop`() {
        val images = listOf(
            image("first", 1920, 1080),
            image("portrait", 720, 1280),
            image("second", 1280, 720),
        )

        assertThat(landscapeImages(images)).containsExactly("first", "second").inOrder()
    }
}
