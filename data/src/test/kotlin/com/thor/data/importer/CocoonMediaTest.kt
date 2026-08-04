package com.thor.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Reading Cocoon's filenames.
 *
 * Every case here is a real name taken from an actual `downloaded_media` folder
 * rather than an invented one, because the format's difficulty is entirely in
 * what it accumulates: a title id, a version, a format tag, a DLC marker, a
 * download size and a duplicate counter, in varying combinations, on the same
 * line as the name.
 */
class CocoonMediaTest {

    @Test
    fun `a title id and version come off`() {
        assertThat(cocoonTitleOf("Batman Arkham Asylum [0100E870163CA000][v0].png"))
            .isEqualTo("Batman Arkham Asylum")
    }

    @Test
    fun `a duplicate counter comes off`() {
        assertThat(cocoonTitleOf("Batman Arkham Asylum [0100E870163CA800][v65536] (2).png"))
            .isEqualTo("Batman Arkham Asylum")
    }

    @Test
    fun `a download size comes off`() {
        assertThat(cocoonTitleOf("Batman Arkham Knight [0100ACD0163D0800][v327680] (26.88 GB).jpg"))
            .isEqualTo("Batman Arkham Knight")
    }

    @Test
    fun `a size and a counter together both come off`() {
        // The awkward one: two parenthesised groups, only the last a counter.
        assertThat(
            cocoonTitleOf(
                "The Legend of Zelda Breath of the Wild [01007EF00011E000][v0] (13.48 GB) (3).jpg",
            ),
        ).isEqualTo("The Legend of Zelda Breath of the Wild")
    }

    @Test
    fun `a format tag alone comes off`() {
        assertThat(cocoonTitleOf("The Legend of Zelda Tears of the Kingdom [NSP].jpg"))
            .isEqualTo("The Legend of Zelda Tears of the Kingdom")
        assertThat(cocoonTitleOf("Mario Kart 8 Deluxe [NSZ].png"))
            .isEqualTo("Mario Kart 8 Deluxe")
    }

    @Test
    fun `an apostrophe in the name survives`() {
        assertThat(cocoonTitleOf("Yoshi's Crafted World [01006000040c2000][v0].png"))
            .isEqualTo("Yoshi's Crafted World")
    }

    @Test
    fun `a name with no markers at all is left alone`() {
        assertThat(cocoonTitleOf("Folder.jpg")).isEqualTo("Folder")
    }

    @Test
    fun `a region in parentheses is not mistaken for a counter`() {
        // The reason the counter rule is digits-only: a library that names its
        // dumps this way would otherwise have the region stripped off.
        assertThat(cocoonTitleOf("Super Mario World (Europe).png"))
            .isEqualTo("Super Mario World (Europe)")
        assertThat(cocoonTitleOf("Final Fantasy VII (Disc 1).png"))
            .isEqualTo("Final Fantasy VII (Disc 1)")
    }

    @Test
    fun `add-on content is recognised so it cannot take the game's slots`() {
        val dlc = "Mario Kart 8 Deluxe [DLC Booster Course Pass] [0100152000023001][v65536].png"

        assertThat(isCocoonDlc(dlc)).isTrue()
        // And note it would otherwise pass as the base game, which is the trap.
        assertThat(cocoonTitleOf(dlc)).isEqualTo("Mario Kart 8 Deluxe")
    }

    @Test
    fun `the base game is not mistaken for add-on content`() {
        assertThat(isCocoonDlc("Mario Kart 8 Deluxe [0100152000022000][v0] (6.77 GB).png"))
            .isFalse()
    }

    @Test
    fun `folder names map onto slots`() {
        assertThat(CocoonSlot.of("icon")).isEqualTo(CocoonSlot.ICON)
        assertThat(CocoonSlot.of("screenshot_gameplay")).isEqualTo(CocoonSlot.SCREENSHOT_GAMEPLAY)
        assertThat(CocoonSlot.of("Hero")).isEqualTo(CocoonSlot.HERO)
        assertThat(CocoonSlot.of("something_else")).isNull()
    }

    @Test
    fun `the same picture saved several times is imported once`() {
        // The real case: a base game, its update and Android's duplicate copies.
        // Three of these are 272233 bytes to the byte in an actual folder; the
        // fourth is a genuinely different capture.
        val images = listOf(
            image("v0", CocoonSlot.SCREENSHOT_GAMEPLAY, 272233),
            image("copy-1", CocoonSlot.SCREENSHOT_GAMEPLAY, 272233),
            image("copy-2", CocoonSlot.SCREENSHOT_GAMEPLAY, 272233),
            image("update", CocoonSlot.SCREENSHOT_GAMEPLAY, 100766),
        )

        assertThat(selectCocoonArtwork(images).screenshots)
            .containsExactly("v0", "update").inOrder()
    }

    @Test
    fun `a duplicate is dropped even across the two screenshot classes`() {
        val images = listOf(
            image("play", CocoonSlot.SCREENSHOT_GAMEPLAY, 5000),
            image("title-same", CocoonSlot.SCREENSHOT_TITLE, 5000),
            image("title-other", CocoonSlot.SCREENSHOT_TITLE, 9000),
        )

        assertThat(selectCocoonArtwork(images).screenshots)
            .containsExactly("play", "title-other").inOrder()
    }

    @Test
    fun `images whose size is unknown are all kept`() {
        // Zero means the source would not say. Treating that as a value would
        // collapse every such file into one and lose real pictures.
        val images = listOf(
            image("a", CocoonSlot.SCREENSHOT_GAMEPLAY, 0),
            image("b", CocoonSlot.SCREENSHOT_GAMEPLAY, 0),
            image("c", CocoonSlot.SCREENSHOT_GAMEPLAY, 0),
        )

        assertThat(selectCocoonArtwork(images).screenshots).hasSize(3)
    }

    @Test
    fun `one image per slot is kept, and gameplay leads the screenshots`() {
        // Cocoon holds a base game, its update and Android's duplicates, all of
        // which reduce to the same title and the same picture.
        val images = listOf(
            image("icon-a", CocoonSlot.ICON),
            image("icon-b", CocoonSlot.ICON),
            image("title-a", CocoonSlot.SCREENSHOT_TITLE),
            image("play-a", CocoonSlot.SCREENSHOT_GAMEPLAY),
            image("play-b", CocoonSlot.SCREENSHOT_GAMEPLAY),
            image("hero-a", CocoonSlot.HERO),
        )

        val artwork = selectCocoonArtwork(images)

        assertThat(artwork.icon).isEqualTo("icon-a")
        assertThat(artwork.hero).isEqualTo("hero-a")
        assertThat(artwork.screenshots).containsExactly("play-a", "play-b", "title-a").inOrder()
    }

    @Test
    fun `no more than three screenshots are taken`() {
        val images = (1..8).map { image("play-$it", CocoonSlot.SCREENSHOT_GAMEPLAY) }

        // Anything beyond three is a file copied that nothing will ever open.
        assertThat(selectCocoonArtwork(images).screenshots).hasSize(3)
    }

    @Test
    fun `a game with nothing usable reports empty rather than blank slots`() {
        assertThat(selectCocoonArtwork(emptyList()).isEmpty).isTrue()
    }

    private fun image(source: String, slot: CocoonSlot, size: Long = 0L) =
        CocoonImage(title = "Game", slot = slot, source = source, sizeBytes = size)
}
