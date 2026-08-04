package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Slug resolution, written against the pack format this support was built for.
 *
 * The slugs below are the real folder names from a Cocoon pack, not invented
 * ones. Getting a mapping wrong is silent: the artwork imports, the pack reports
 * success, and one platform quietly wears nothing or — worse — the wrong system's
 * icon, which nobody would think to look for.
 */
class IconPackSlugsTest {

    /** Every platform folder in the reference pack. */
    private val referencePackSlugs = listOf(
        "dreamcast", "fbneo", "fds", "gamegear", "gb", "gba", "gbc", "gc",
        "genesis", "genesismsu", "mame", "n3ds", "n64", "nds", "nes", "ngp",
        "ngpc", "ps2", "psp", "psvita", "psx", "saturn", "snes", "snesmsu1",
        "steam", "switch", "wii", "wiiu", "windows",
    )

    @Test
    fun `slugs matching a THOR id map to themselves`() {
        listOf("nes", "snes", "n64", "psx", "ps2", "switch", "wii", "gba")
            .forEach { slug ->
                assertThat(IconPackSlugs.platformIdFor(slug)).isEqualTo(slug)
            }
    }

    @Test
    fun `differently named machines are recognised`() {
        assertThat(IconPackSlugs.platformIdFor("gc")).isEqualTo("gamecube")
        assertThat(IconPackSlugs.platformIdFor("n3ds")).isEqualTo("3ds")
        assertThat(IconPackSlugs.platformIdFor("ps1")).isEqualTo("psx")
    }

    /** Two arcade emulators are not two arcade platforms. */
    @Test
    fun `emulators collapse onto the platform they emulate`() {
        assertThat(IconPackSlugs.platformIdFor("mame")).isEqualTo("arcade")
        assertThat(IconPackSlugs.platformIdFor("fbneo")).isEqualTo("arcade")
    }

    @Test
    fun `console variants collapse onto the parent console`() {
        assertThat(IconPackSlugs.platformIdFor("genesismsu")).isEqualTo("genesis")
        assertThat(IconPackSlugs.platformIdFor("snesmsu1")).isEqualTo("snes")
        assertThat(IconPackSlugs.platformIdFor("fds")).isEqualTo("nes")
    }

    @Test
    fun `desktop storefronts map to PC`() {
        assertThat(IconPackSlugs.platformIdFor("steam")).isEqualTo("pc")
        assertThat(IconPackSlugs.platformIdFor("windows")).isEqualTo("pc")
    }

    /**
     * The three that used to be unmatched, and are not any more.
     *
     * This test previously asserted the opposite — that THOR modelled no Game
     * Gear and no Neo Geo Pocket, so a pack shipping artwork for them had
     * nowhere to put it. Both are built-in platforms now, and the mono Pocket
     * aliases onto the colour model because they share emulators and artwork.
     */
    @Test
    fun `handhelds the reference pack ships now resolve`() {
        assertThat(IconPackSlugs.platformIdFor("gamegear")).isEqualTo("gamegear")
        assertThat(IconPackSlugs.platformIdFor("ngpc")).isEqualTo("ngpc")
        assertThat(IconPackSlugs.platformIdFor("ngp")).isEqualTo("ngpc")
    }

    @Test
    fun `the whole reference pack resolves`() {
        val resolved = referencePackSlugs.count { IconPackSlugs.platformIdFor(it) != null }
        assertThat(resolved).isEqualTo(referencePackSlugs.size)
    }

    /** Whatever resolves must resolve to a platform that actually exists. */
    @Test
    fun `no slug maps to a platform THOR does not have`() {
        referencePackSlugs.forEach { slug ->
            val id = IconPackSlugs.platformIdFor(slug) ?: return@forEach
            assertThat(BuiltInPlatforms.BY_ID).containsKey(id)
        }
    }

    @Test
    fun `casing and padding are ignored`() {
        assertThat(IconPackSlugs.platformIdFor("  NES  ")).isEqualTo("nes")
        assertThat(IconPackSlugs.platformIdFor("GC")).isEqualTo("gamecube")
        assertThat(IconPackSlugs.platformIdFor("")).isNull()
    }

    /**
     * Storage keeps the pack's own name, never the alias target — see
     * [IconPackSlugs.storageKeyFor] for why that direction matters.
     */
    @Test
    fun `storage keys preserve the slug the pack used`() {
        assertThat(IconPackSlugs.storageKeyFor("gc")).isEqualTo("gc")
        assertThat(IconPackSlugs.storageKeyFor("GameGear")).isEqualTo("gamegear")
        assertThat(IconPackSlugs.storageKeyFor("ngp")).isEqualTo("ngp")
    }

    /**
     * The folder id has to round-trip.
     *
     * Three separate places now derive a platform's folder from its id and one
     * reverses it. A mismatch is silent: artwork applies to a folder that does not
     * exist, or a folder resolves to no platform and quietly loses its hero.
     */
    @Test
    fun `platform folder ids round-trip`() {
        BuiltInPlatforms.ALL.forEach { platform ->
            val folderId = PlatformFolders.idFor(platform.id)
            assertThat(PlatformFolders.platformIdOf(folderId)).isEqualTo(platform.id)
        }
    }

    @Test
    fun `other folder ids are not mistaken for platform folders`() {
        assertThat(PlatformFolders.platformIdOf("folder:8f3a-1234")).isNull()
        assertThat(PlatformFolders.platformIdOf("game:nes:mario")).isNull()
        assertThat(PlatformFolders.platformIdOf("folder:platform:")).isNull()
        assertThat(PlatformFolders.platformIdOf("")).isNull()
    }

    /** Held artwork has to be findable once the platform exists. */
    @Test
    fun `a pack finds its artwork through an alias`() {
        val pack = IconPack(
            id = "test",
            name = "Test",
            author = "Nobody",
            version = "1.0",
            artworkBySlug = mapOf(
                "gc" to PlatformArtwork(iconUri = "file:///gc.png"),
                "gamegear" to PlatformArtwork(iconUri = "file:///gg.png"),
            ),
        )

        assertThat(pack.artworkFor("gamecube")?.iconUri).isEqualTo("file:///gc.png")
        // Held: no THOR platform yet, but the artwork is present and keyed.
        assertThat(pack.artworkFor("gamegear")?.iconUri).isEqualTo("file:///gg.png")
        assertThat(pack.artworkFor("dreamcast")).isNull()
    }
}
