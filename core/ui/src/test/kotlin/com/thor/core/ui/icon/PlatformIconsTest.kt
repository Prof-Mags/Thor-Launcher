package com.thor.core.ui.icon

import com.google.common.truth.Truth.assertThat
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

    @Test
    fun `a system this set does not cover falls through`() {
        // Amiga, MSX, ZX Spectrum, ColecoVision, Intellivision and SG-1000 have
        // no artwork here, and a missing entry is not an error.
        assertThat(PlatformIcons.preferredOver(PlatformArtwork.NONE, "amiga")).isNull()
        assertThat(PlatformIcons.forPlatform("msx")).isNull()
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
