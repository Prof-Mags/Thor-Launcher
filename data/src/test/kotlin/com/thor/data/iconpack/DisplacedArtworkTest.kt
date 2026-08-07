package com.thor.data.iconpack

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.IconPack
import com.thor.core.model.PlatformArtwork
import org.junit.Test

/**
 * What happens to a hand-picked image when a pack covers it.
 *
 * Installing a pack overwrites everything it covers, including the systems the
 * user dressed themselves — skipping those made a pack look as though it had not
 * installed. The cost of that rule is that the one image the user cannot get back
 * by reinstalling anything is now the one most at risk, so it is kept aside and
 * handed back when the pack that covered it goes. These two functions are that
 * promise, and it is worth holding still.
 */
class DisplacedArtworkTest {

    private fun pack(id: String, vararg covers: String) = IconPack(
        id = id,
        name = id,
        author = "Nobody",
        version = "1.0",
        artworkBySlug = covers.associateWith { slug ->
            PlatformArtwork(iconUri = "file:///packs/$id/$slug.png", packId = id)
        },
    )

    private fun chosen(uri: String) =
        PlatformArtwork(iconUri = uri, packId = PlatformArtwork.USER_PACK_ID)

    // ---- The stash ---------------------------------------------------------

    @Test
    fun `the first displacement creates the stash under the reserved id`() {
        val stash = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        assertThat(stash.id).isEqualTo(PlatformArtwork.USER_PACK_ID)
        assertThat(stash.artworkBySlug.keys).containsExactly("nes")
    }

    @Test
    fun `a later displacement of another platform is added alongside`() {
        val first = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        val second = stashWith(first, mapOf("snes" to chosen("content://mine/snes.png")))

        assertThat(second.artworkBySlug.keys).containsExactly("nes", "snes")
    }

    /**
     * Re-picking by hand and being covered again keeps the *newer* image.
     *
     * Holding the older one would hand back something the user had already
     * replaced, which is indistinguishable from the launcher losing their change.
     */
    @Test
    fun `displacing the same platform twice keeps the newer image`() {
        val first = stashWith(null, mapOf("nes" to chosen("content://mine/old.png")))

        val second = stashWith(first, mapOf("nes" to chosen("content://mine/new.png")))

        assertThat(second.artworkBySlug["nes"]?.iconUri).isEqualTo("content://mine/new.png")
    }

    @Test
    fun `the stash is not offered as an installed pack`() {
        val packs = listOf(pack("cocoon"), stashWith(null, mapOf("nes" to chosen("u"))))

        assertThat(realPacks(packs).map { it.id }).containsExactly("cocoon")
        assertThat(userStash(packs)).isNotNull()
    }

    @Test
    fun `with nothing ever displaced there is no stash`() {
        assertThat(userStash(listOf(pack("cocoon")))).isNull()
    }

    // ---- Giving it back ----------------------------------------------------

    /** Two packs installed: removing the newer reveals the older, not the user's. */
    @Test
    fun `another pack outranks the hand-picked image`() {
        val stash = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        val fallback = fallbackFor("nes", listOf(pack("aurora", "nes")), stash)

        assertThat(fallback?.first).isEqualTo("aurora")
    }

    @Test
    fun `the newest remaining pack wins over an older one`() {
        // Installed oldest-first, so the last entry is the most recent.
        val remaining = listOf(pack("aurora", "nes"), pack("cocoon", "nes"))

        val fallback = fallbackFor("nes", remaining, stash = null)

        assertThat(fallback?.first).isEqualTo("cocoon")
    }

    @Test
    fun `with no pack left the hand-picked image comes back`() {
        val stash = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        val fallback = fallbackFor("nes", remaining = emptyList(), stash = stash)

        assertThat(fallback?.first).isEqualTo(PlatformArtwork.USER_PACK_ID)
        assertThat(fallback?.second?.iconUri).isEqualTo("content://mine/nes.png")
    }

    /**
     * A pack that covers other systems does not keep this one dressed.
     *
     * The fallback is per platform, not per pack — a pack still installed but with
     * no artwork for this system is no reason to withhold the user's own image.
     */
    @Test
    fun `a pack covering other platforms does not block the handback`() {
        val stash = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        val fallback = fallbackFor("nes", listOf(pack("aurora", "snes", "gba")), stash)

        assertThat(fallback?.first).isEqualTo(PlatformArtwork.USER_PACK_ID)
    }

    @Test
    fun `nothing to put back falls through to the artwork Loki ships`() {
        assertThat(fallbackFor("nes", remaining = emptyList(), stash = null)).isNull()
    }

    @Test
    fun `a platform the user never dressed is not invented`() {
        val stash = stashWith(null, mapOf("nes" to chosen("content://mine/nes.png")))

        assertThat(fallbackFor("snes", remaining = emptyList(), stash = stash)).isNull()
    }
}
