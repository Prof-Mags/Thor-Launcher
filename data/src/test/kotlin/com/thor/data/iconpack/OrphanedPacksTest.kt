package com.thor.data.iconpack

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.IconPack
import com.thor.core.model.PlatformArtwork
import org.junit.Test

/**
 * Which pack records describe artwork that is not there.
 *
 * This decides whether platforms get undressed, so both mistakes are costly and
 * neither is visible: too eager and a pack the user installed is stripped off
 * every system it covered; too shy and a platform stays owned by something that
 * cannot draw, which shows no artwork at all — not the pack's, and not the set
 * Loki ships, because ownership suppresses that too. A system simply goes blank
 * and nothing in the interface says why.
 */
class OrphanedPacksTest {

    private fun pack(id: String) = IconPack(
        id = id,
        name = id,
        author = "Nobody",
        version = "1.0",
    )

    @Test
    fun `a pack with its files is left alone`() {
        val packs = listOf(pack("cocoon"), pack("aurora"))

        val orphans = orphanedPacks(packs) { true }

        assertThat(orphans).isEmpty()
    }

    @Test
    fun `a record with no files on disk is an orphan`() {
        val packs = listOf(pack("cocoon"))

        val orphans = orphanedPacks(packs) { false }

        assertThat(orphans.map { it.id }).containsExactly("cocoon")
    }

    /** The mixed case is the real one: one pack survives a restore, one does not. */
    @Test
    fun `only the packs whose files are missing are dropped`() {
        val packs = listOf(pack("cocoon"), pack("aurora"), pack("retro"))

        val orphans = orphanedPacks(packs) { it == "aurora" }

        assertThat(orphans.map { it.id }).containsExactly("cocoon", "retro").inOrder()
    }

    /**
     * The reserved id for a hand-picked image is never an orphan.
     *
     * It has no directory — nothing ever copies files for it — so without the
     * explicit exclusion it would read as missing and be removed, taking every
     * image the user browsed to with it.
     */
    @Test
    fun `hand-picked artwork is never treated as a missing pack`() {
        val packs = listOf(pack(PlatformArtwork.USER_PACK_ID))

        val orphans = orphanedPacks(packs) { false }

        assertThat(orphans).isEmpty()
    }

    @Test
    fun `nothing installed is nothing to repair`() {
        assertThat(orphanedPacks(emptyList()) { false }).isEmpty()
    }
}
