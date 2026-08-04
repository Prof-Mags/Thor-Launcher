package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What is allowed into the square cell.
 *
 * The slot was filled from the first result of a request that *asked* for the
 * 1:1 sizes, and asking is not the same as receiving: anything that made the
 * filter not apply put a two-by-three cover in a square frame, which is the tall
 * artwork that appeared in the grid. The response reports the dimensions, so the
 * shape is checked rather than assumed.
 */
class SteamGridDbSquareTest {

    private fun image(url: String, width: Int?, height: Int?) =
        SteamGridDbProvider.SgdbImage(id = 1, url = url, width = width, height = height)

    @Test
    fun `a 1024 square is kept`() {
        assertThat(squareImages(listOf(image("square", 1024, 1024))))
            .containsExactly("square")
    }

    @Test
    fun `a two-by-three cover is refused`() {
        // 600x900 is the shape that was reaching the cell.
        assertThat(squareImages(listOf(image("cover", 600, 900)))).isEmpty()
    }

    @Test
    fun `a wide banner is refused`() {
        assertThat(squareImages(listOf(image("banner", 920, 430)))).isEmpty()
    }

    @Test
    fun `an off-by-one upload still counts as square`() {
        assertThat(squareImages(listOf(image("almost", 1024, 1023))))
            .containsExactly("almost")
    }

    @Test
    fun `an image with no dimensions is refused rather than gambled on`() {
        assertThat(squareImages(listOf(image("unknown", null, null)))).isEmpty()
    }

    @Test
    fun `a zero dimension cannot divide`() {
        assertThat(squareImages(listOf(image("broken", 512, 0)))).isEmpty()
    }

    @Test
    fun `order is preserved, since the first one is the one used`() {
        val images = listOf(
            image("cover", 600, 900),
            image("first-square", 512, 512),
            image("second-square", 1024, 1024),
        )

        assertThat(squareImages(images))
            .containsExactly("first-square", "second-square").inOrder()
    }

    @Test
    fun `a game with no square art yields nothing rather than something tall`() {
        val images = listOf(image("cover", 600, 900), image("banner", 460, 215))

        // Empty is the honest answer: the cell falls back to box art, which is
        // at least a whole picture rather than a cropped one.
        assertThat(squareImages(images)).isEmpty()
    }
}
