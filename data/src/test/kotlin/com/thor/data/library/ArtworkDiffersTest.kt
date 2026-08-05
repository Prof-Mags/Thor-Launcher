package com.thor.data.library

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.ArtworkSet
import org.junit.Test

/**
 * The rule deciding whether a hand edit pins a game's pictures.
 *
 * It matters in both directions. Answering yes too readily pins whatever the
 * scraper happened to find the moment someone opened the editor and pressed
 * Save; answering no when a picture really was chosen by hand lets the next
 * scrape quietly replace it, which is the failure the editor exists to prevent.
 */
class ArtworkDiffersTest {

    @Test
    fun `an untouched set is not a change`() {
        val stored = ArtworkSet(
            boxArt = "box",
            icon = "ico",
            hero = "hero",
            logo = "logo",
            screenshots = listOf("a", "b"),
        )

        // Opening the dialog and saving without touching a picture.
        assertThat(artworkDiffers(stored, stored)).isFalse()
    }

    @Test
    fun `each slot the editor owns counts as a change`() {
        val stored = ArtworkSet(icon = "ico", hero = "hero", logo = "logo")

        assertThat(artworkDiffers(stored.copy(icon = "new"), stored)).isTrue()
        assertThat(artworkDiffers(stored.copy(hero = "new"), stored)).isTrue()
        assertThat(artworkDiffers(stored.copy(logo = "new"), stored)).isTrue()
    }

    @Test
    fun `emptying a slot counts as a change`() {
        // Clearing a wrong backdrop is as deliberate as replacing it, and has to
        // lock too — otherwise the scrape that put it there simply puts it back.
        val stored = ArtworkSet(hero = "hero")

        assertThat(artworkDiffers(stored.copy(hero = null), stored)).isTrue()
    }

    @Test
    fun `screenshots count, added or removed`() {
        val stored = ArtworkSet(screenshots = listOf("a", "b"))

        assertThat(artworkDiffers(ArtworkSet(screenshots = listOf("a", "b", "c")), stored)).isTrue()
        assertThat(artworkDiffers(ArtworkSet(screenshots = listOf("a")), stored)).isTrue()
        // Order is the slideshow's order, so reordering is an edit as well.
        assertThat(artworkDiffers(ArtworkSet(screenshots = listOf("b", "a")), stored)).isTrue()
    }

    @Test
    fun `box art alone is not a change here`() {
        // It travels the custom-icon path instead, which sets its own lock. If it
        // counted here, clearing the cover would lock artwork on the way past and
        // the reset row would stop being offered.
        val stored = ArtworkSet(boxArt = "box")

        assertThat(artworkDiffers(ArtworkSet(boxArt = "other"), stored)).isFalse()
    }

    @Test
    fun `an over-long stored list does not read as edited`() {
        // A library scraped by an earlier build can hold more than the cap. The
        // editor is seeded with the capped list and writes that back, so a naive
        // comparison would call every such save an edit and pin the artwork of
        // any old game whose editor was merely opened.
        val stored = ArtworkSet(screenshots = List(8) { "shot-$it" })
        val edited = ArtworkSet(screenshots = stored.cappedScreenshots)

        assertThat(artworkDiffers(edited, stored)).isFalse()
    }
}
