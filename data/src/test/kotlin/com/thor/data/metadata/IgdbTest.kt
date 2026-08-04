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

    // ---- The query body ---------------------------------------------------
    //
    // Assembled inline once, from two pieces that each carried the `where`
    // keyword. IGDB answered `where where platforms = ...` with a 400 and the
    // provider reported nothing, for every game, instantly. These check the
    // shape rather than the content, which is where that class of fault lives.

    @Test
    fun `the query has exactly one where, however many conditions`() {
        val withPlatform = igdbSearchBody("Chrono Trigger", platformId = "19")
        val without = igdbSearchBody("Chrono Trigger", platformId = null)

        assertThat(withPlatform.split("where").size - 1).isEqualTo(1)
        assertThat(without.split("where").size - 1).isEqualTo(1)
    }

    @Test
    fun `a platform id becomes a condition joined to the game type`() {
        assertThat(igdbSearchBody("Chrono Trigger", platformId = "19"))
            .contains("where platforms = (19) & game_type = (0,8,9,10,11);")
    }

    @Test
    fun `no platform id leaves the game type filtering alone`() {
        val body = igdbSearchBody("Chrono Trigger", platformId = null)

        assertThat(body).contains("where game_type = (0,8,9,10,11);")
        assertThat(body).doesNotContain("platforms")
    }

    @Test
    fun `a blank platform id is treated as none rather than an empty list`() {
        // `platforms = ()` is a syntax error, and a platform with no IGDB id
        // recorded is the normal case rather than an exceptional one.
        assertThat(igdbSearchBody("Chrono Trigger", platformId = " "))
            .doesNotContain("platforms")
    }

    @Test
    fun `every clause is terminated, since one missing semicolon fails the request`() {
        val body = igdbSearchBody("Chrono Trigger", platformId = "19")

        assertThat(body).endsWith(";")
        // search, fields, where, limit.
        assertThat(body.count { it == ';' }).isEqualTo(4)
    }

    @Test
    fun `a quoted title cannot break the body it sits in`() {
        val body = igdbSearchBody("""Tom Clancy's "Rainbow Six"""", platformId = "19")

        // Two quotes exactly: the ones this function put there.
        assertThat(body.count { it == '"' }).isEqualTo(2)
    }
}
