package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LauncherProfileTest {

    @Test
    fun `a name already taken gains a counter`() {
        assertThat(uniqueProfileName("Joe", listOf("Joe"))).isEqualTo("Joe 2")
        assertThat(uniqueProfileName("Joe", listOf("Joe", "Joe 2"))).isEqualTo("Joe 3")
    }

    @Test
    fun `taken names are matched regardless of case`() {
        assertThat(uniqueProfileName("joe", listOf("JOE"))).isEqualTo("joe 2")
    }

    @Test
    fun `a free name is left alone`() {
        assertThat(uniqueProfileName("Guest", listOf("Joe"))).isEqualTo("Guest")
    }

    @Test
    fun `a blank name would render as an invisible profile, so it falls back`() {
        assertThat(sanitizeProfileName("   ")).isEqualTo("Player")
        assertThat(sanitizeProfileName("", fallback = "Joe")).isEqualTo("Joe")
    }

    @Test
    fun `names are trimmed and capped`() {
        assertThat(sanitizeProfileName("  Joe  ")).isEqualTo("Joe")
        assertThat(sanitizeProfileName("x".repeat(60)))
            .hasLength(LauncherProfile.MAX_NAME_LENGTH)
    }

    @Test
    fun `deleting the active profile hands over to the most recently used`() {
        val registry = ProfileRegistry(
            profiles = listOf(
                profile("a", lastUsed = 10L),
                profile("b", lastUsed = 30L),
                profile("c", lastUsed = 20L),
            ),
            activeProfileId = "a",
        )

        val next = registry.withProfileRemoved("a")

        assertThat(next.profiles.map(LauncherProfile::id)).containsExactly("b", "c").inOrder()
        assertThat(next.activeProfileId).isEqualTo("b")
    }

    @Test
    fun `deleting an inactive profile does not change who is signed in`() {
        val registry = ProfileRegistry(
            profiles = listOf(profile("a"), profile("b")),
            activeProfileId = "a",
        )

        assertThat(registry.withProfileRemoved("b").activeProfileId).isEqualTo("a")
    }

    @Test
    fun `the last profile cannot be deleted`() {
        val registry = ProfileRegistry(listOf(profile("a")), activeProfileId = "a")

        assertThat(registry.withProfileRemoved("a")).isEqualTo(registry)
    }

    @Test
    fun `deleting an unknown profile is a no-op`() {
        val registry = ProfileRegistry(
            profiles = listOf(profile("a"), profile("b")),
            activeProfileId = "a",
        )

        assertThat(registry.withProfileRemoved("zzz")).isEqualTo(registry)
    }

    @Test
    fun `an active id naming nothing still resolves to a profile`() {
        val registry = ProfileRegistry(listOf(profile("a")), activeProfileId = "gone")

        assertThat(registry.active?.id).isEqualTo("a")
    }

    @Test
    fun `ids become directory names, so path characters are rejected`() {
        assertThat(LauncherProfile.isValidId("3f2a-9c")).isTrue()
        assertThat(LauncherProfile.isValidId("../escape")).isFalse()
        assertThat(LauncherProfile.isValidId("with/slash")).isFalse()
        assertThat(LauncherProfile.isValidId("")).isFalse()
    }

    @Test
    fun `the fallback avatar letter survives a nameless profile`() {
        assertThat(profile("a").copy(name = "joe").initial).isEqualTo("J")
        assertThat(profile("a").copy(name = "  ").initial).isEqualTo("?")
    }

    private fun profile(id: String, lastUsed: Long = 0L) = LauncherProfile(
        id = id,
        name = "Profile $id",
        lastUsedEpochMs = lastUsed,
    )
}
