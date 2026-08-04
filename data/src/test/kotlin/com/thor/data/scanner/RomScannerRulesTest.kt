package com.thor.data.scanner

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.BuiltInPlatforms
import com.thor.core.model.Platform
import org.junit.Test

/**
 * The three rules that decide whether a file on disk becomes a game.
 *
 * Each of them fails silently in the same direction — a library with entries in
 * it that are not games — and the user's only clue is a grid that has more cells
 * than they have ROMs. Held as plain functions so the rules can be tested
 * without a device, which is the only part of scanning that can be.
 */
class RomScannerRulesTest {

    private val added: List<Platform> = listOf("snes", "psx", "genesis", "gamecube", "arcade")
        .map { BuiltInPlatforms.BY_ID.getValue(it) }

    // ---- Which system a folder is announcing --------------------------------

    @Test
    fun `a folder named as the platform is recognised`() {
        assertThat(platformHintFor("SNES", added)).isEqualTo("snes")
        assertThat(platformHintFor("snes", added)).isEqualTo("snes")
        assertThat(platformHintFor("PlayStation", added)).isEqualTo("psx")
    }

    /**
     * How No-Intro and Redump name their sets, and therefore how most ROM
     * collections on disk are laid out.
     */
    @Test
    fun `a maker prefix is stripped`() {
        assertThat(platformHintFor("Sony - PlayStation", added)).isEqualTo("psx")
        assertThat(platformHintFor("Nintendo - SNES", added)).isEqualTo("snes")
    }

    /** The vocabulary every other front-end's folders already use. */
    @Test
    fun `the shared front-end names resolve`() {
        assertThat(platformHintFor("ps1", added)).isEqualTo("psx")
        assertThat(platformHintFor("megadrive", added)).isEqualTo("genesis")
        assertThat(platformHintFor("gc", added)).isEqualTo("gamecube")
        assertThat(platformHintFor("mame", added)).isEqualTo("arcade")
    }

    /**
     * The bug this shares with the extension index, fixed there and not here.
     *
     * Removing a system marks it not-added. If a folder named after it still
     * scoped everything beneath it, the next scan imported all of its games
     * again — a system you deleted coming back while the settings insist you
     * never had it.
     */
    @Test
    fun `a folder for a system the user has not added is not a hint`() {
        assertThat(platformHintFor("nds", added)).isNull()
        assertThat(platformHintFor("Nintendo DS", added)).isNull()
        // Nor through the alias table, which knows every platform Loki models.
        assertThat(platformHintFor("n3ds", added)).isNull()
        assertThat(platformHintFor("", added)).isNull()
        assertThat(platformHintFor("SNES", emptyList())).isNull()
    }

    @Test
    fun `an ordinary folder name announces nothing`() {
        listOf("ROMs", "Games", "Downloads", "media", "covers").forEach { name ->
            assertThat(platformHintFor(name, added)).isNull()
        }
    }

    // ---- Whether a file is a game at all ------------------------------------

    /**
     * The headline fault: a folder hint alone was enough to import a file.
     *
     * Everything beside a ROM — its save, its save state, its box art, a `.txt`
     * of notes — became a library entry with a title taken off its filename.
     */
    @Test
    fun `only extensions the platform claims are games`() {
        val snes = BuiltInPlatforms.BY_ID.getValue("snes")

        assertThat(platformAcceptsExtension(snes, "smc")).isTrue()
        assertThat(platformAcceptsExtension(snes, "sfc")).isTrue()

        listOf("srm", "sav", "state", "png", "jpg", "txt", "nfo", "xml", "db")
            .forEach { extension ->
                assertThat(platformAcceptsExtension(snes, extension)).isFalse()
            }
    }

    @Test
    fun `a platform that is not being scanned accepts nothing`() {
        assertThat(platformAcceptsExtension(null, "smc")).isFalse()
    }

    // ---- A disc's tracks are not games --------------------------------------

    @Test
    fun `a multi-track rip contributes only its sheet`() {
        val shadowed = discTracksShadowedBySheet(
            listOf(
                "Final Fantasy VII.cue",
                "Final Fantasy VII (Track 01).bin",
                "Final Fantasy VII (Track 02).bin",
                "Final Fantasy VII (Track 03).bin",
            ),
        )

        assertThat(shadowed).hasSize(3)
        assertThat(shadowed).doesNotContain("Final Fantasy VII.cue")
    }

    @Test
    fun `a single-track rip has its data file suppressed`() {
        val shadowed = discTracksShadowedBySheet(listOf("Metal Gear Solid.cue", "Metal Gear Solid.bin"))

        assertThat(shadowed).containsExactly("Metal Gear Solid.bin")
    }

    @Test
    fun `every kind of sheet counts`() {
        assertThat(discTracksShadowedBySheet(listOf("Shenmue.gdi", "Shenmue.raw")))
            .containsExactly("Shenmue.raw")
        assertThat(discTracksShadowedBySheet(listOf("Panzer.ccd", "Panzer.img")))
            .containsExactly("Panzer.img")
    }

    /**
     * The rule has to stay off cartridge sets.
     *
     * `.bin` is a Mega Drive dump as often as it is a disc track, and the only
     * thing that distinguishes them is whether a sheet sits beside it.
     */
    @Test
    fun `cartridge dumps with no sheet beside them are untouched`() {
        val names = listOf("Sonic.bin", "Streets of Rage 2.bin", "Golden Axe.bin")

        assertThat(discTracksShadowedBySheet(names)).isEmpty()
    }

    @Test
    fun `an unrelated disc in the same folder keeps its own tracks`() {
        val shadowed = discTracksShadowedBySheet(
            listOf("Disc A.cue", "Disc A (Track 01).bin", "Disc B.bin"),
        )

        // Disc B has no sheet naming it, so it is a game in its own right.
        assertThat(shadowed).containsExactly("Disc A (Track 01).bin")
    }

    @Test
    fun `casing does not matter`() {
        assertThat(discTracksShadowedBySheet(listOf("GAME.CUE", "Game.BIN")))
            .containsExactly("Game.BIN")
    }
}
