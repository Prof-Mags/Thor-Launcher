package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * RAWG's derived image paths.
 *
 * The crop is the one that matters: 600 by 400 is three-to-two, so a picture
 * requested for a widescreen frame arrives with its top and bottom already
 * removed. Nothing downstream can recover that, which is why it is undone at the
 * point the URL is read rather than compensated for later.
 */
class RawgImageUrlTest {

    private val original =
        "https://media.rawg.io/media/games/4be/4be6a6ad0364751a96229c56bf69be59.jpg"

    @Test
    fun `a crop resolves to the image it was cut from`() {
        val cropped =
            "https://media.rawg.io/media/crop/600/400/games/4be/4be6a6ad0364751a96229c56bf69be59.jpg"

        assertThat(fullSizeRawgImage(cropped)).isEqualTo(original)
    }

    @Test
    fun `a resize resolves to the image it was scaled from`() {
        val resized =
            "https://media.rawg.io/media/resize/420/-/games/4be/4be6a6ad0364751a96229c56bf69be59.jpg"

        assertThat(fullSizeRawgImage(resized)).isEqualTo(original)
    }

    @Test
    fun `an original is left exactly as it is`() {
        assertThat(fullSizeRawgImage(original)).isEqualTo(original)
    }

    @Test
    fun `screenshot paths normalise the same way`() {
        val cropped = "https://media.rawg.io/media/crop/600/400/screenshots/abc/def.jpg"

        assertThat(fullSizeRawgImage(cropped))
            .isEqualTo("https://media.rawg.io/media/screenshots/abc/def.jpg")
    }

    @Test
    fun `a url from somewhere else is not rewritten`() {
        // Deliberately narrow: this undoes one CDN's known derivations, and a
        // rule loose enough to touch other hosts would corrupt them.
        val other = "https://cdn.example.com/media/crop/600/400/art.jpg"

        assertThat(fullSizeRawgImage("https://example.com/art.jpg"))
            .isEqualTo("https://example.com/art.jpg")
        // Matched on path rather than host, so this one is rewritten too — worth
        // recording as the deliberate limit of the rule rather than a surprise.
        assertThat(fullSizeRawgImage(other)).isEqualTo("https://cdn.example.com/media/art.jpg")
    }
}
