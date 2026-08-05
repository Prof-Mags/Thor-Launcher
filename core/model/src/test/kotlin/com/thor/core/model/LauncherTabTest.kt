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
            LauncherTab.SHOWS,
        ).inOrder()

        assertThat(LauncherTab.DEFAULT).isEqualTo(LauncherTab.HOME)
        assertThat(LauncherTab.HOME.isHome).isTrue()
        assertThat(LauncherTab.STREAM.isHome).isFalse()
        // Shows sits beside Movies rather than at an end, because the bumpers
        // walk between them and a tab in between would be a stop on the way.
        assertThat(LauncherTab.ORDERED.indexOf(LauncherTab.SHOWS))
            .isEqualTo(LauncherTab.ORDERED.indexOf(LauncherTab.MOVIES) + 1)
    }

    /**
     * Shows is one section wearing two tabs, and only on a television.
     *
     * It shares a view model, a cursor and a player with Movies - it is that
     * section with its media type chosen from the bar rather than from inside
     * itself. The handheld's bar is a strip along the bottom of a small panel and
     * its toggle is already on screen, so there it would be a second door into a
     * room the user is standing in.
     */
    @Test
    fun `shows is a tab only couch mode draws`() {
        assertThat(LauncherTab.visible(ALL)).doesNotContain(LauncherTab.SHOWS)
        assertThat(LauncherTab.visible(ALL, couch = true)).contains(LauncherTab.SHOWS)

        assertThat(LauncherTab.SHOWS.isCouchOnly).isTrue()
        assertThat(LauncherTab.MOVIES.isCouchOnly).isFalse()
    }

    /** Both faces of one section answer to it, so the shell can route a press. */
    @Test
    fun `movies and shows are the same section`() {
        assertThat(LauncherTab.MOVIES.isMoviesSection).isTrue()
        assertThat(LauncherTab.SHOWS.isMoviesSection).isTrue()
        assertThat(LauncherTab.HOME.isMoviesSection).isFalse()

        assertThat(LauncherTab.MOVIES.mediaType).isEqualTo(MediaType.MOVIE)
        assertThat(LauncherTab.SHOWS.mediaType).isEqualTo(MediaType.SERIES)
        assertThat(LauncherTab.HOME.mediaType).isNull()
        assertThat(LauncherTab.forMediaType(MediaType.SERIES)).isEqualTo(LauncherTab.SHOWS)
        assertThat(LauncherTab.forMediaType(MediaType.MOVIE)).isEqualTo(LauncherTab.MOVIES)
    }

    /**
     * Shows needs the Movies extension, because it is the Movies section.
     *
     * Turning the extension off has to take both tabs with it, or the bar keeps a
     * door to a section the user has removed.
     */
    @Test
    fun `withdrawing the movies extension takes shows with it`() {
        val streamOnly = setOf(LauncherExtension.STREAM.id)

        assertThat(LauncherTab.visible(streamOnly, couch = true))
            .containsExactly(LauncherTab.STREAM, LauncherTab.HOME)
            .inOrder()
    }

    /**
     * Leaving couch mode on Shows lands on Movies, not Home.
     *
     * It is the same catalogue reached the other way. Throwing the user back to
     * Home for having been on a tab the handheld does not draw would read as the
     * launcher losing its place.
     */
    @Test
    fun `a tab the shell cannot draw collapses to the nearest one it can`() {
        assertThat(LauncherTab.landing(LauncherTab.SHOWS, ALL)).isEqualTo(LauncherTab.MOVIES)
        assertThat(LauncherTab.landing(LauncherTab.SHOWS, ALL, couch = true))
            .isEqualTo(LauncherTab.SHOWS)
        // With no Movies extension there is nothing of that section left to land on.
        assertThat(LauncherTab.landing(LauncherTab.SHOWS, emptySet())).isEqualTo(LauncherTab.HOME)
        assertThat(LauncherTab.landing(LauncherTab.HOME, ALL)).isEqualTo(LauncherTab.HOME)
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

    /** Walked per shell, because each draws a different set of tabs. */
    @Test
    fun `stepping nowhere is a no-op`() {
        listOf(false, true).forEach { couch ->
            LauncherTab.visible(ALL, couch).forEach { tab ->
                assertThat(LauncherTab.step(tab, 0, ALL, couch)).isEqualTo(tab)
            }
        }
    }

    @Test
    fun `only enabled sections are shown`() {
        assertThat(LauncherTab.visible(emptySet())).containsExactly(LauncherTab.HOME)

        assertThat(LauncherTab.visible(setOf(LauncherExtension.MOVIES.id)))
            .containsExactly(LauncherTab.HOME, LauncherTab.MOVIES)
            .inOrder()

        // Everything but the tab this shell does not have; see the couch test above.
        assertThat(LauncherTab.visible(ALL))
            .containsExactlyElementsIn(LauncherTab.ORDERED - LauncherTab.SHOWS)
        assertThat(LauncherTab.visible(ALL, couch = true))
            .containsExactlyElementsIn(LauncherTab.ORDERED)
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
