package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Accepting whatever form the user pasted.
 *
 * Addon links are shared in several shapes and people paste all of them. Getting
 * this wrong produces a source list that is simply always empty, with no error
 * anywhere — the request goes to a URL that answers nothing, and the panel says
 * "no sources found" exactly as it would for a title nobody has uploaded.
 */
class StremioAddonsTest {

    @Test
    fun `a manifest URL becomes its base`() {
        assertThat(StremioAddons.normalise("https://example.com/manifest.json"))
            .isEqualTo("https://example.com")
    }

    /** What an install button produces. It is ordinary HTTPS underneath. */
    @Test
    fun `the stremio scheme becomes https`() {
        assertThat(StremioAddons.normalise("stremio://example.com/manifest.json"))
            .isEqualTo("https://example.com")
    }

    /**
     * A configured addon carries its options in the path, and they are part of
     * the endpoint — stripping them would silently reset every choice the user
     * made while configuring it.
     */
    @Test
    fun `configuration in the path is preserved`() {
        val url = "https://torrentio.strem.fun/providers=yts,eztv|qualityfilter=480p/manifest.json"

        assertThat(StremioAddons.normalise(url))
            .isEqualTo("https://torrentio.strem.fun/providers=yts,eztv|qualityfilter=480p")
    }

    @Test
    fun `a bare host gains a scheme`() {
        assertThat(StremioAddons.normalise("example.com")).isEqualTo("https://example.com")
    }

    @Test
    fun `plain http is left alone`() {
        assertThat(StremioAddons.normalise("http://192.168.1.10:7000/manifest.json"))
            .isEqualTo("http://192.168.1.10:7000")
    }

    @Test
    fun `trailing slashes and surrounding space are trimmed`() {
        assertThat(StremioAddons.normalise("  https://example.com/  "))
            .isEqualTo("https://example.com")
    }

    @Test
    fun `an already normalised URL is unchanged`() {
        val url = "https://example.com/config"
        assertThat(StremioAddons.normalise(url)).isEqualTo(url)
    }

    @Test
    fun `empty stays empty rather than becoming a scheme`() {
        assertThat(StremioAddons.normalise("")).isEmpty()
        assertThat(StremioAddons.normalise("   ")).isEmpty()
    }

    @Test
    fun `the manifest sits beside the base, whatever was pasted`() {
        val fromManifest = StremioAddons.manifestUrl("https://example.com/manifest.json")
        val fromBase = StremioAddons.manifestUrl("https://example.com")

        assertThat(fromManifest).isEqualTo("https://example.com/manifest.json")
        assertThat(fromBase).isEqualTo(fromManifest)
    }

    @Test
    fun `an addon labels itself by host until its manifest answers`() {
        val pasted = StremioAddon(url = "https://torrentio.strem.fun/providers=yts")
        assertThat(pasted.label).isEqualTo("torrentio.strem.fun")

        assertThat(pasted.copy(name = "Torrentio").label).isEqualTo("Torrentio")
    }
}
