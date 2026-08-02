package com.thor.feature.settings.tutorial

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.LauncherExtension
import com.thor.feature.settings.SettingsCategory
import org.junit.Test

/**
 * The tour against the rail it describes.
 *
 * Both are derived from [SettingsCategory.navigationEntries], and these are here
 * so they stay derived: a category added with a hand-written tour step would drift
 * the moment the rail changed, and one *missing* a step would be walked past in
 * silence — which is the failure that is hard to notice, because a tour that skips
 * a screen still looks like a working tour.
 */
class TutorialContentTest {

    @Test
    fun `base tour names no extension category when none are enabled`() {
        val named = ThorTutorial.base(emptySet()).mapNotNull { it.settingsCategory }

        assertThat(named).doesNotContain(SettingsCategory.MOVIES)
        assertThat(named).doesNotContain(SettingsCategory.STREAMING)
    }

    @Test
    fun `base tour names an extension category once that extension is enabled`() {
        val named = ThorTutorial.base(setOf(LauncherExtension.MOVIES.id))
            .mapNotNull { it.settingsCategory }

        assertThat(named).contains(SettingsCategory.MOVIES)
        // The other extension is still absent: enabling one must not reveal both.
        assertThat(named).doesNotContain(SettingsCategory.STREAMING)
    }

    /**
     * The tour's categories and the rail's are the same set, whichever
     * extensions are on.
     *
     * Sets rather than lists: the step introducing Settings opens the first
     * category so the reader has the real screen in front of them while it is
     * described, and the step for that category follows — one category, named
     * twice, on purpose.
     */
    @Test
    fun `base tour walks exactly the categories the rail draws`() {
        for (enabled in extensionCombinations()) {
            val expected = SettingsCategory.navigationEntries(enabled).toSet()
            val named = ThorTutorial.base(enabled)
                .mapNotNull { it.settingsCategory }
                .toSet()

            assertThat(named).isEqualTo(expected)
        }
    }

    @Test
    fun `every step carries text on a panel`() {
        val steps = ThorTutorial.base(emptySet()) +
            LauncherExtension.entries.flatMap(ThorTutorial::forExtension)

        for (step in steps) {
            assertThat(step.title).isNotEmpty()
            assertThat(step.body).isNotEmpty()
        }
    }

    /** An extension's own tour only ever points at its own settings. */
    @Test
    fun `extension tours name only their own category`() {
        for (extension in LauncherExtension.entries) {
            val named = ThorTutorial.forExtension(extension)
                .mapNotNull { it.settingsCategory }

            assertThat(named.map { it.extension }.toSet() - setOf(null))
                .isEqualTo(setOf(extension))
        }
    }

    private fun extensionCombinations(): List<Set<String>> {
        val ids = LauncherExtension.entries.map { it.id }
        return listOf(
            emptySet(),
            setOf(ids.first()),
            setOf(ids.last()),
            ids.toSet(),
        )
    }
}
