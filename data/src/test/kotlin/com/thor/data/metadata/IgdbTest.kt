package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IGDB's URL building and query escaping.
 *
 * The escaping is the part worth holding still: the search term is a quoted
 * string inside a body that is otherwise code, so a stray quote does not spoil
 * one field — it ends the string early, turns the rest of the title into syntax
 * and fails the whole request.
 */
class IgdbTest {

    @Test
    fun `an image url carries its size as a path segment`() {
        assertThat(igdbImage("co1r7f", "t_1080p"))
            .isEqualTo("https://images.igdb.com/igdb/image/upload/t_1080p/co1r7f.jpg")
    }

    @Test
    fun `a quote in a title cannot end the query string early`() {
        // "Marvel's Spider-Man" is fine; a double quote is what breaks the body.
        assertThat("""Tom Clancy's "Rainbow Six"""".escapedForApicalypse())
            .isEqualTo("Tom Clancy's Rainbow Six")
    }

    @Test
    fun `a backslash cannot escape its way out of the string`() {
        assertThat("""Half-Life\""".escapedForApicalypse()).isEqualTo("Half-Life")
    }

    @Test
    fun `an ordinary title passes through untouched`() {
        assertThat("Chrono Trigger".escapedForApicalypse()).isEqualTo("Chrono Trigger")
    }

    @Test
    fun `release dates arrive as unix seconds and resolve to a year`() {
        // 1995-08-11, Chrono Trigger's North American release.
        assertThat(yearOfEpochSeconds(808099200L)).isEqualTo(1995)
    }

    @Test
    fun `a release date before the epoch still resolves`() {
        // Not hypothetical: IGDB carries arcade titles from the seventies.
        assertThat(yearOfEpochSeconds(-31_536_000L)).isEqualTo(1969)
    }
}
