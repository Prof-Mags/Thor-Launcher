package com.thor.core.ui.icon

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.BuiltInPlatforms
import com.thor.core.model.PlatformArtwork
import org.junit.Test

/**
 * Who wins the platform folder.
 *
 * The first attempt preferred any existing artwork, which showed none of the
 * bundled icons at all: a scraped library has an image on every platform
 * already, so the fallback branch was never reached. Ownership is the rule, not
 * presence, and it is worth holding still.
 */
class PlatformIconsTest {

    @Test
    fun `the bundled icon beats artwork a scraper found`() {
        // No packId: nobody chose this, it was simply what the scraper had.
        val scraped = PlatformArtwork(iconUri = "https://example/nes.png")

        assertThat(PlatformIcons.preferredOver(scraped, "nes")).isNotNull()
    }

    @Test
    fun `an installed pack keeps its place`() {
        val fromPack = PlatformArtwork(iconUri = "file:///packs/nes.png", packId = "some-pack")

        assertThat(PlatformIcons.preferredOver(fromPack, "nes")).isNull()
    }

    @Test
    fun `an image the user picked by hand keeps its place`() {
        val chosen = PlatformArtwork(
            iconUri = "content://pictures/nes.png",
            packId = PlatformArtwork.USER_PACK_ID,
        )

        assertThat(PlatformIcons.preferredOver(chosen, "nes")).isNull()
    }

    @Test
    fun `a platform with no artwork at all takes the bundled icon`() {
        assertThat(PlatformIcons.preferredOver(PlatformArtwork.NONE, "snes")).isNotNull()
        assertThat(PlatformIcons.preferredOver(null, "snes")).isNotNull()
    }

    /**
     * Coverage, stated rather than counted.
     *
     * Written as the exact set of *misses* so both directions are caught: a
     * system quietly losing its icon fails, and so does one gaining an icon
     * without this list being updated to say so.
     *
     * PC and Android are not consoles and have nothing to draw — one is whatever
     * the user points Loki at, the other is the device it runs on.
     */
    @Test
    fun `every console is covered and the two non-consoles are not`() {
        val uncovered = BuiltInPlatforms.ALL
            .map { it.id }
            .filter { PlatformIcons.forPlatform(it) == null }

        assertThat(uncovered).containsExactly("pc", "android")
    }

    @Test
    fun `a system this set does not cover falls through`() {
        // A missing entry is not an error; it is a platform with nothing to draw.
        assertThat(PlatformIcons.preferredOver(PlatformArtwork.NONE, "pc")).isNull()
        assertThat(PlatformIcons.forPlatform("android")).isNull()
    }

    /**
     * The six added after the first pass, which had no artwork at all.
     *
     * Named individually because they are the ones whose source files exist
     * under a different name than the platform id, and a rename is the failure
     * this whole map is written to survive.
     */
    @Test
    fun `the home computers and early consoles resolve`() {
        listOf("amiga", "msx", "zxspectrum", "colecovision", "intellivision", "sg1000")
            .forEach { id ->
                assertThat(PlatformIcons.forPlatform(id)).isNotNull()
            }
    }

    @Test
    fun `ids the artwork was renamed onto all resolve`() {
        // The renames are the part most likely to rot: the file was called one
        // thing and the platform is called another.
        listOf(
            "gamecube", "3ds", "psx", "genesis", "mastersystem",
            "pcengine", "pcenginecd", "ngpc", "lynx", "jaguar", "c64", "dos",
        ).forEach { id ->
            assertThat(PlatformIcons.forPlatform(id)).isNotNull()
        }
    }

    @Test
    fun `an unknown platform is simply absent`() {
        assertThat(PlatformIcons.forPlatform("not-a-platform")).isNull()
        assertThat(PlatformIcons.forPlatform(null)).isNull()
    }
}
