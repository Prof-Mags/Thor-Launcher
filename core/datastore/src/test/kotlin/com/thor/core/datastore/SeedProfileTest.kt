package com.thor.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which directory an empty registry adopts.
 *
 * This is the recovery path for a launcher that minted a new profile id on
 * every start and never wrote it down, leaving each session's settings and
 * library under an id the next session had no idea about. Getting the choice
 * wrong here means adopting one of the empty directories it left behind and
 * presenting the user with a reset launcher a second time.
 */
class SeedProfileTest {

    private val settingsWeight = 1L shl 40

    @Test
    fun `a fresh install takes the fixed default`() {
        assertThat(chooseSeedProfileId(emptyList(), "default")).isEqualTo("default")
    }

    @Test
    fun `directories holding nothing are ignored`() {
        val candidates = listOf("empty-a" to 0L, "empty-b" to 0L)

        assertThat(chooseSeedProfileId(candidates, "default")).isEqualTo("default")
    }

    @Test
    fun `a profile with settings beats a newer one with only a database`() {
        // The launcher left these behind newest-first: the empty directories are
        // the *recent* ones, and the real profile is the oldest.
        val candidates = listOf(
            "newest-empty" to 98_304L,
            "middle-empty" to 98_304L,
            "the-real-one" to settingsWeight + 40_000_000L,
        )

        assertThat(chooseSeedProfileId(candidates, "default")).isEqualTo("the-real-one")
    }

    @Test
    fun `with no settings anywhere, the largest library wins`() {
        val candidates = listOf(
            "scanned-nothing" to 98_304L,
            "scanned-a-library" to 42_000_000L,
        )

        assertThat(chooseSeedProfileId(candidates, "default")).isEqualTo("scanned-a-library")
    }

    @Test
    fun `equal candidates take the most recent, which is listed first`() {
        val candidates = listOf("newer" to 500L, "older" to 500L)

        assertThat(chooseSeedProfileId(candidates, "default")).isEqualTo("newer")
    }

    @Test
    fun `a single real profile is adopted rather than left beside a new default`() {
        val candidates = listOf("3f2a9c-uuid" to settingsWeight)

        assertThat(chooseSeedProfileId(candidates, "default")).isEqualTo("3f2a9c-uuid")
    }
}
