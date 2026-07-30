package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every built-in platform has something to say about itself.
 *
 * The information panel for a platform folder is built from these, so a platform
 * added later without one does not fail — it renders a heading, a year and then a
 * gap where the paragraph should be. That is the sort of omission nobody notices
 * until a user does.
 */
class PlatformDescriptionTest {

    @Test
    fun `every built-in platform has a description`() {
        val missing = BuiltInPlatforms.ALL
            .filter { it.description.isBlank() }
            .map(Platform::id)

        assertThat(missing).isEmpty()
    }

    /** Long enough to be a description rather than a label. */
    @Test
    fun `descriptions are a sentence or more`() {
        BuiltInPlatforms.ALL.forEach { platform ->
            assertThat(platform.description.length).isGreaterThan(MIN_LENGTH)
        }
    }

    @Test
    fun `descriptions survive the round trip through the id lookup`() {
        BuiltInPlatforms.ALL.forEach { platform ->
            assertThat(BuiltInPlatforms.descriptionFor(platform.id))
                .isEqualTo(platform.description)
        }
    }

    /** A platform the launcher does not know has no description, not a crash. */
    @Test
    fun `an unknown platform has no description`() {
        assertThat(BuiltInPlatforms.descriptionFor("no-such-system")).isEmpty()
    }

    @Test
    fun `the subtitle pairs maker and year, and copes with either missing`() {
        val snes = BuiltInPlatforms.BY_ID.getValue("snes")
        assertThat(snes.subtitle).isEqualTo("Nintendo · 1990")

        // Arcade has no single release year.
        val arcade = BuiltInPlatforms.BY_ID.getValue("arcade")
        assertThat(arcade.subtitle).isEqualTo("Various")

        // Neither: the separator must not be left stranded on its own.
        val bare = Platform(
            id = "probe",
            name = "Probe",
            shortName = "P",
            manufacturer = "",
            releaseYear = null,
            accentArgb = 0L,
            romExtensions = emptySet(),
        )
        assertThat(bare.subtitle).isEmpty()
    }

    private companion object {
        const val MIN_LENGTH = 40
    }
}
