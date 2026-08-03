package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlatformProfileTest {
    @Test
    fun `every built in platform has complete editorial highlights`() {
        BuiltInPlatforms.ALL.forEach { platform ->
            val profile = PlatformProfiles.forPlatform(platform)
            assertThat(profile.category).isNotEmpty()
            assertThat(profile.highlights).hasSize(3)
            profile.highlights.forEach { highlight ->
                assertThat(highlight.title).isNotEmpty()
                assertThat(highlight.description).isNotEmpty()
            }
        }
    }

    @Test
    fun `3ds exposes hardware specific metadata`() {
        val platform = requireNotNull(BuiltInPlatforms.BY_ID["3ds"])
        val profile = PlatformProfiles.forPlatform(platform)

        assertThat(profile.systemGlyph).isEqualTo(PlatformGlyph.DUAL_SCREEN)
        assertThat(profile.highlights.map(PlatformHighlight::title)).containsExactly(
            "Glasses-free 3D",
            "Touch Screen",
            "StreetPass & SpotPass",
        ).inOrder()
    }
}
