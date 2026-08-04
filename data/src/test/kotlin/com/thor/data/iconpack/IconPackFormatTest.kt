package com.thor.data.iconpack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Reading a pack that names its images instead of foldering them.
 *
 * The failure this guards is silent in the worst way: a flat pack whose names are
 * misread imports cleanly, reports success, and dresses nothing — because every
 * image was filed under a slug no platform has. The user sees a pack that
 * installed and did nothing, with no reason given.
 */
class IconPackFormatTest {

    @Test
    fun `a style suffix names both the system and the style`() {
        val icon = parseFlatArtworkName("snes-console.png")

        assertThat(icon).isEqualTo(FlatArtwork.Icon("snes", IconStyle.CONSOLE))
    }

    @Test
    fun `every offered style is recognised`() {
        IconStyle.entries.forEach { style ->
            assertThat(parseFlatArtworkName("snes-${style.suffix}.png"))
                .isEqualTo(FlatArtwork.Icon("snes", style))
        }
    }

    @Test
    fun `an overlay belongs to the system rather than to a style`() {
        assertThat(parseFlatArtworkName("snes-overlay.png"))
            .isEqualTo(FlatArtwork.Overlay("snes"))
        assertThat(parseFlatArtworkName("snes-frame.png"))
            .isEqualTo(FlatArtwork.Overlay("snes"))
    }

    /**
     * The last hyphen, not the first.
     *
     * Splitting at the first would file every multi-word system under a fragment
     * of its own name — `atari2600` survives that, `neo-geo` does not — and the
     * result is artwork keyed to a slug that resolves to no platform at all.
     */
    @Test
    fun `a hyphenated system keeps its whole name`() {
        assertThat(parseFlatArtworkName("neo-geo-console.png"))
            .isEqualTo(FlatArtwork.Icon("neo-geo", IconStyle.CONSOLE))
        assertThat(parseFlatArtworkName("pc-engine-cd-overlay.png"))
            .isEqualTo(FlatArtwork.Overlay("pc-engine-cd"))
    }

    @Test
    fun `casing and image format do not matter`() {
        assertThat(parseFlatArtworkName("SNES-Console.PNG"))
            .isEqualTo(FlatArtwork.Icon("snes", IconStyle.CONSOLE))
        listOf("webp", "jpg", "jpeg").forEach { extension ->
            assertThat(parseFlatArtworkName("snes-console.$extension")).isNotNull()
        }
    }

    /**
     * The nested layout has to fall through untouched.
     *
     * Both readings run over the same files, and this one is tried first because
     * it is the more specific claim. If it answered for `icon.png` as well, a
     * conventional pack would be filed under the *filename* rather than under its
     * platform directory, and every pack that already works would stop working.
     */
    @Test
    fun `conventionally named images are not read as flat ones`() {
        listOf("icon.png", "hero.png", "logo.png", "overlay.png", "banner.jpg")
            .forEach { name ->
                assertThat(parseFlatArtworkName(name)).isNull()
            }
    }

    @Test
    fun `anything that is not a system and a known style is left alone`() {
        listOf(
            "metadata.json",
            "snes-console.txt",
            "snes-poster.png",
            "readme-notes.png",
            "-console.png",
            "snes-.png",
        ).forEach { name ->
            assertThat(parseFlatArtworkName(name)).isNull()
        }
    }

    @Test
    fun `overlay is artwork in the nested layout too`() {
        assertThat(ArtworkKind.of("overlay.png")).isEqualTo(ArtworkKind.OVERLAY)
        assertThat(ArtworkKind.of("frame.webp")).isEqualTo(ArtworkKind.OVERLAY)
        assertThat(ArtworkKind.of("icon.png")).isEqualTo(ArtworkKind.ICON)
    }

    @Test
    fun `non-images are not artwork at all`() {
        assertThat(ArtworkKind.isImage("metadata.json")).isFalse()
        // Icon sets ship these beside the PNGs; Android cannot decode one.
        assertThat(ArtworkKind.isImage("snes-console.ico")).isFalse()
        assertThat(ArtworkKind.isImage("snes-console.png")).isTrue()
    }

    /**
     * Styles are declared most-preferred first.
     *
     * The importer reverses this list so the packs install least-preferred first
     * and the winner is the one left on screen. Written down because the ordering
     * is load-bearing and reads like nothing: reorder the enum and a user
     * importing a set gets the pixel-art icons.
     */
    @Test
    fun `the plain console render is the style a set leads with`() {
        assertThat(IconStyle.entries.first()).isEqualTo(IconStyle.CONSOLE)
    }

    /** Each style has to produce a distinct pack id and a distinct name. */
    @Test
    fun `styles are distinguishable`() {
        assertThat(IconStyle.entries.map { it.suffix }.toSet())
            .hasSize(IconStyle.entries.size)
        assertThat(IconStyle.entries.map { it.displayName }.toSet())
            .hasSize(IconStyle.entries.size)
    }

    /**
     * A pack with no `metadata.json` is named after what the user picked.
     *
     * Which is most of them, and all of a set exported straight out of an art
     * tool. The alternative is the sanitised id, and with one source producing a
     * pack per style that unreadable name is then repeated down the list.
     */
    @Test
    fun `an unnamed pack takes the name of the folder it came from`() {
        // What `Uri.lastPathSegment` returns for a document tree: the whole
        // path, decoded, in one segment.
        assertThat(displayNameFrom("primary:Documents/Icon Forge")).isEqualTo("Icon Forge")
        assertThat(displayNameFrom("primary:Download/Cocoon.zip")).isEqualTo("Cocoon")
        assertThat(displayNameFrom("")).isEmpty()
    }

    /** A dot in a folder name is not an extension. */
    @Test
    fun `only a zip suffix is stripped`() {
        assertThat(displayNameFrom("primary:icons/v1.2 icons")).isEqualTo("v1.2 icons")
    }
}
