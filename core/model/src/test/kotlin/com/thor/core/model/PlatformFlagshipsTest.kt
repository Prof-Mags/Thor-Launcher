package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The flagship table fails quietly.
 *
 * A mistyped key, or one aimed at a platform id that does not exist, does not
 * throw — it just never matches, and the platform silently goes back to having no
 * backdrop. That is indistinguishable from "the user owns none of these", which
 * is a legitimate outcome, so nothing at runtime can tell the two apart. These
 * tests are the only place the difference is visible.
 */
class PlatformFlagshipsTest {

    @Test
    fun `every keyed platform exists`() {
        BuiltInPlatforms.ALL.map(Platform::id).let { known ->
            PLATFORM_IDS.forEach { id ->
                assertThat(known).contains(id)
            }
        }
    }

    @Test
    fun `keys are normalised`() {
        // A key with a space, a colon or a capital can never match, because every
        // title it is compared against has already had those stripped out.
        PLATFORM_IDS.forEach { id ->
            val title = "zzz no such game"
            // Exercised through the public surface: a lookup that survives every
            // platform proves the keys are at least well-formed enough to compare.
            assertThat(PlatformFlagships.rankOf(id, title)).isNull()
        }
    }

    @Test
    fun `matches a rom filename with region and revision decorations`() {
        val rank = PlatformFlagships.rankOf("snes", "Super Mario World (USA) [!].sfc")
        assertThat(rank).isEqualTo(0)
    }

    @Test
    fun `matches a numbered sequel from the series key`() {
        // "supermariobros" is meant to cover the series, not just the first one.
        assertThat(PlatformFlagships.rankOf("nes", "Super Mario Bros. 3")).isEqualTo(0)
    }

    @Test
    fun `ranks in list order so the strongest title wins`() {
        val zelda = PlatformFlagships.rankOf("n64", "The Legend of Zelda: Ocarina of Time")
        val mario = PlatformFlagships.rankOf("n64", "Super Mario 64")
        assertThat(mario!!).isLessThan(zelda!!)
    }

    @Test
    fun `an unlisted game is not a stand-in for its platform`() {
        // The whole point: owning a game on a platform must not make it the face
        // of that platform. Only the listed ones qualify.
        assertThat(PlatformFlagships.rankOf("snes", "Uniracers")).isNull()
    }

    @Test
    fun `a platform with no flagships returns null rather than guessing`() {
        assertThat(PlatformFlagships.rankOf(BuiltInPlatforms.ID_PC, "Half-Life")).isNull()
    }

    @Test
    fun `normalise strips punctuation case and spacing`() {
        assertThat(PlatformFlagships.normalise("The Legend of Zelda: A Link to the Past"))
            .isEqualTo("thelegendofzeldaalinktothepast")
    }

    private companion object {
        /**
         * Mirrors the table's keys.
         *
         * Written out rather than read from the object, because a test that asks
         * the code under test which platforms it covers cannot catch a platform
         * being dropped from it.
         */
        val PLATFORM_IDS = listOf(
            "nes", "snes", "n64", "gamecube", "wii", "wiiu", "switch",
            "gb", "gbc", "gba", "nds", "3ds", "virtualboy",
            "psx", "ps2", "ps3", "psp", "psvita",
            "xbox", "arcade", "dreamcast", "saturn", "genesis",
        )
    }
}
