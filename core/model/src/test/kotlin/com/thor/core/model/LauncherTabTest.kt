package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tab order and stepping.
 *
 * Small, but the bar is the launcher's top-level navigation and every one of
 * these failing looks like "the bar is janky" rather than like a bug.
 */
class LauncherTabTest {

    @Test
    fun `home is the middle tab and the default`() {
        assertThat(LauncherTab.ORDERED).containsExactly(
            LauncherTab.STREAM,
            LauncherTab.HOME,
            LauncherTab.MOVIES,
        ).inOrder()

        assertThat(LauncherTab.DEFAULT).isEqualTo(LauncherTab.HOME)
        assertThat(LauncherTab.HOME.isHome).isTrue()
        assertThat(LauncherTab.STREAM.isHome).isFalse()
    }

    @Test
    fun `stepping moves one tab at a time`() {
        assertThat(LauncherTab.step(LauncherTab.HOME, 1)).isEqualTo(LauncherTab.MOVIES)
        assertThat(LauncherTab.step(LauncherTab.HOME, -1)).isEqualTo(LauncherTab.STREAM)
    }

    /**
     * Clamped, not wrapped. Pressing Left on the first tab landing on the last
     * reads as the cursor jumping rather than as running out of bar.
     */
    @Test
    fun `stepping past either end stays put`() {
        assertThat(LauncherTab.step(LauncherTab.STREAM, -1)).isEqualTo(LauncherTab.STREAM)
        assertThat(LauncherTab.step(LauncherTab.STREAM, -9)).isEqualTo(LauncherTab.STREAM)
        assertThat(LauncherTab.step(LauncherTab.MOVIES, 1)).isEqualTo(LauncherTab.MOVIES)
        assertThat(LauncherTab.step(LauncherTab.MOVIES, 9)).isEqualTo(LauncherTab.MOVIES)
    }

    /** A held direction produces repeats, so a multi-step jump has to land right. */
    @Test
    fun `a repeated press crosses the bar in one step`() {
        assertThat(LauncherTab.step(LauncherTab.STREAM, 2)).isEqualTo(LauncherTab.MOVIES)
        assertThat(LauncherTab.step(LauncherTab.MOVIES, -2)).isEqualTo(LauncherTab.STREAM)
    }

    @Test
    fun `stepping nowhere is a no-op`() {
        LauncherTab.ORDERED.forEach { tab ->
            assertThat(LauncherTab.step(tab, 0)).isEqualTo(tab)
        }
    }

    @Test
    fun `only enabled sections are shown`() {
        assertThat(LauncherTab.visible(emptySet())).containsExactly(LauncherTab.HOME)

        assertThat(LauncherTab.visible(setOf(LauncherExtension.MOVIES.id)))
            .containsExactly(LauncherTab.HOME, LauncherTab.MOVIES)
            .inOrder()

        assertThat(LauncherTab.visible(ALL)).containsExactlyElementsIn(LauncherTab.ORDERED)
    }

    /**
     * The cursor walks what is drawn, not what exists.
     *
     * With Stream disabled the bar is Home and Movies, so Left from Home has
     * nowhere to go — landing on Stream would put the cursor on a tab that is
     * not on screen, and pressing A there would open a section the user has not
     * added.
     */
    @Test
    fun `stepping skips sections that are not enabled`() {
        val moviesOnly = setOf(LauncherExtension.MOVIES.id)

        assertThat(LauncherTab.step(LauncherTab.HOME, -1, moviesOnly))
            .isEqualTo(LauncherTab.HOME)
        assertThat(LauncherTab.step(LauncherTab.HOME, 1, moviesOnly))
            .isEqualTo(LauncherTab.MOVIES)
        assertThat(LauncherTab.step(LauncherTab.HOME, 1, emptySet()))
            .isEqualTo(LauncherTab.HOME)
    }

    private companion object {
        val ALL: Set<String> =
            LauncherExtension.entries.mapTo(mutableSetOf(), LauncherExtension::id)
    }
}
