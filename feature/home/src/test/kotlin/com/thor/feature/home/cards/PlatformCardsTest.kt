package com.thor.feature.home.cards

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.GameEntry
import com.thor.core.model.GameMetadata
import com.thor.core.model.Platform
import com.thor.core.model.PlayStats
import org.junit.Test

/**
 * What the card flow shows, and in what order.
 *
 * A flow shows one system at a time, which makes every rule here cost a press to
 * discover and another to leave. A card for an empty system, a card in the wrong
 * place, or an end the user cannot step past are all cheap mistakes on a grid —
 * everything is visible at once there — and expensive in a flow.
 */
class PlatformCardsTest {

    private fun platform(id: String, name: String = id, sortIndex: Int = 0) = Platform(
        id = id,
        name = name,
        shortName = name.take(4),
        manufacturer = "Nobody",
        releaseYear = null,
        accentArgb = 0xFF000000,
        romExtensions = emptySet(),
        sortIndex = sortIndex,
    )

    private fun game(
        id: String,
        platformId: String,
        hidden: Boolean = false,
        playMillis: Long = 0L,
        launches: Int = 0,
        lastPlayed: Long? = null,
    ) = GameEntry(
        id = id,
        title = id,
        sortTitle = id,
        platformId = platformId,
        contentUri = "file:///roms/$id",
        fileName = "$id.rom",
        fileSizeBytes = 1_024L,
        isHidden = hidden,
        stats = PlayStats(
            totalPlayMillis = playMillis,
            launchCount = launches,
            lastPlayedEpochMs = lastPlayed,
        ),
        metadata = GameMetadata.EMPTY,
    )

    private val platforms = mapOf(
        "nes" to platform("nes", "Nintendo", sortIndex = 1),
        "snes" to platform("snes", "Super Nintendo", sortIndex = 2),
    )

    // ---- Which systems get a card -----------------------------------------

    @Test
    fun `a system with games gets a card carrying the count`() {
        val cards = platformCards(
            listOf(game("a", "nes"), game("b", "nes")),
            platforms,
        )

        assertThat(cards.map { it.platform.id }).containsExactly("nes")
        assertThat(cards.single().gameCount).isEqualTo(2)
    }

    /**
     * An empty system has no card at all.
     *
     * On the grid an empty folder sits beside a full one and the difference is
     * visible; in a flow it costs a press to reach and a press to leave, with
     * nothing on screen beforehand to say it was not worth the trip.
     */
    @Test
    fun `a system with no games gets no card`() {
        val cards = platformCards(emptyList(), platforms)

        assertThat(cards).isEmpty()
    }

    @Test
    fun `hidden games count toward nothing`() {
        val cards = platformCards(
            listOf(game("a", "nes"), game("b", "nes", hidden = true)),
            platforms,
        )

        assertThat(cards.single().gameCount).isEqualTo(1)
    }

    /** Hiding every game in a system removes the system, as it does on the grid. */
    @Test
    fun `a system whose games are all hidden disappears`() {
        val cards = platformCards(listOf(game("a", "nes", hidden = true)), platforms)

        assertThat(cards).isEmpty()
    }

    /** A game whose system was removed cannot be drawn, and is not counted. */
    @Test
    fun `a game with no platform is skipped rather than crashing`() {
        val cards = platformCards(listOf(game("a", "dreamcast")), platforms)

        assertThat(cards).isEmpty()
    }

    @Test
    fun `cards follow the platform sort order every other list uses`() {
        val cards = platformCards(
            listOf(game("b", "snes"), game("a", "nes")),
            platforms,
        )

        assertThat(cards.map { it.platform.id }).containsExactly("nes", "snes").inOrder()
    }

    // ---- What a card says --------------------------------------------------

    @Test
    fun `play time is summed across the system`() {
        val cards = platformCards(
            listOf(
                game("a", "nes", playMillis = 3_600_000L, launches = 1),
                game("b", "nes", playMillis = 1_800_000L, launches = 1),
            ),
            platforms,
        )

        assertThat(cards.single().totalPlayMillis).isEqualTo(5_400_000L)
        assertThat(cards.single().hasBeenPlayed).isTrue()
    }

    @Test
    fun `unplayed counts the games never launched`() {
        val cards = platformCards(
            listOf(
                game("a", "nes", playMillis = 60_000L, launches = 2),
                game("b", "nes"),
                game("c", "nes"),
            ),
            platforms,
        )

        assertThat(cards.single().unplayedCount).isEqualTo(2)
    }

    /**
     * The preview comes from something actually played.
     *
     * A never-played game has no claim to represent a system, and taking the
     * artwork of whichever entry happened to sort first would make the card
     * change for reasons the user cannot see.
     */
    @Test
    fun `a system with nothing played has no borrowed preview`() {
        val cards = platformCards(listOf(game("a", "nes"), game("b", "nes")), platforms)

        assertThat(cards.single().previewUri).isNull()
    }

    // ---- Stepping ----------------------------------------------------------

    @Test
    fun `stepping right moves one along`() {
        assertThat(stepCard(current = 0, delta = 1, count = 5)).isEqualTo(1)
    }

    /** The far end is one press away in the other direction, not twenty-four. */
    @Test
    fun `stepping right off the end wraps to the first`() {
        assertThat(stepCard(current = 4, delta = 1, count = 5)).isEqualTo(0)
    }

    @Test
    fun `stepping left off the front wraps to the last`() {
        assertThat(stepCard(current = 0, delta = -1, count = 5)).isEqualTo(4)
    }

    /** A library still scanning steps harmlessly in place rather than throwing. */
    @Test
    fun `stepping an empty flow stays at zero`() {
        assertThat(stepCard(current = 0, delta = 1, count = 0)).isEqualTo(0)
        assertThat(stepCard(current = 0, delta = -1, count = 0)).isEqualTo(0)
    }

    @Test
    fun `a single system stays where it is whichever way it is stepped`() {
        assertThat(stepCard(current = 0, delta = 1, count = 1)).isEqualTo(0)
        assertThat(stepCard(current = 0, delta = -1, count = 1)).isEqualTo(0)
    }

    // ---- What the card prints ----------------------------------------------

    @Test
    fun `a system never played says nothing about play time`() {
        assertThat(formatPlayTime(0L)).isNull()
    }

    /** Under an hour reads as minutes: "0h" on forty minutes reads as never. */
    @Test
    fun `under an hour is minutes`() {
        assertThat(formatPlayTime(40 * 60_000L)).isEqualTo("40m played")
    }

    /** A few seconds is still something, and rounds up rather than to nothing. */
    @Test
    fun `a played system never reports zero`() {
        assertThat(formatPlayTime(5_000L)).isEqualTo("1m played")
    }

    @Test
    fun `an hour and over is whole hours`() {
        assertThat(formatPlayTime(90 * 60_000L)).isEqualTo("1h played")
        assertThat(formatPlayTime(12 * 3_600_000L)).isEqualTo("12h played")
    }

    @Test
    fun `the count agrees with itself`() {
        assertThat(formatGameCount(1)).isEqualTo("1 game")
        assertThat(formatGameCount(142)).isEqualTo("142 games")
    }

    // ---- The rest of what a card carries -----------------------------------

    @Test
    fun `favourites are counted across the system`() {
        val cards = platformCards(
            listOf(
                game("a", "nes").copy(isFavorite = true),
                game("b", "nes").copy(isFavorite = true),
                game("c", "nes"),
            ),
            platforms,
        )

        assertThat(cards.single().favouriteCount).isEqualTo(2)
    }

    @Test
    fun `last played is the most recent game in the system`() {
        val cards = platformCards(
            listOf(
                game("a", "nes", launches = 1, lastPlayed = 1_000L),
                game("b", "nes", launches = 1, lastPlayed = 9_000L),
            ),
            platforms,
        )

        assertThat(cards.single().lastPlayedEpochMs).isEqualTo(9_000L)
    }

    @Test
    fun `a system nothing has been played on has no last played`() {
        val cards = platformCards(listOf(game("a", "nes")), platforms)

        assertThat(cards.single().lastPlayedEpochMs).isNull()
    }

    /**
     * The strip is capped, so a large system does not decode its whole library.
     *
     * The cap is also what keeps the row from wrapping off the card, which on a
     * flow showing one system at a time would push the counts under the fold.
     */
    @Test
    fun `the cover strip is capped`() {
        val many = (1..20).map { index ->
            game("g$index", "nes").withBoxArt("file:///art/$index.png")
        }

        val cards = platformCards(many, platforms)

        assertThat(cards.single().recentArtwork).hasSize(COVER_STRIP_COUNT)
    }

    /** A system nothing has been launched on still shows what is in it. */
    @Test
    fun `covers appear even with nothing played`() {
        val cards = platformCards(
            listOf(game("a", "nes").withBoxArt("file:///art/a.png")),
            platforms,
        )

        assertThat(cards.single().recentArtwork).containsExactly("file:///art/a.png")
    }

    /** The same image twice would read as the card repeating itself. */
    @Test
    fun `duplicate cover art is not repeated in the strip`() {
        val cards = platformCards(
            listOf(
                game("a", "nes").withBoxArt("file:///art/same.png"),
                game("b", "nes").withBoxArt("file:///art/same.png"),
            ),
            platforms,
        )

        assertThat(cards.single().recentArtwork).containsExactly("file:///art/same.png")
    }

    // ---- The identity line -------------------------------------------------

    @Test
    fun `identity reads maker then year then short name`() {
        val line = formatPlatformIdentity("Nintendo", 1996, "N64", "Nintendo 64")

        assertThat(line).isEqualTo("Nintendo  ·  1996  ·  N64")
    }

    /** "N64 · N64" reads as a bug, so the short name is dropped when it repeats. */
    @Test
    fun `a short name identical to the full name is dropped`() {
        val line = formatPlatformIdentity("Sega", 1998, "Dreamcast", "Dreamcast")

        assertThat(line).isEqualTo("Sega  ·  1998")
    }

    /** A user-added system may have none of this, and gets no empty separators. */
    @Test
    fun `missing parts leave no gaps`() {
        assertThat(formatPlatformIdentity("", null, "PC", "PC")).isEmpty()
        assertThat(formatPlatformIdentity("", 1996, "N64", "Nintendo 64"))
            .isEqualTo("1996  ·  N64")
    }

    // ---- Last played, in words ---------------------------------------------

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `a system never played says nothing`() {
        assertThat(formatLastPlayed(null, now)).isNull()
    }

    @Test
    fun `within the hour is recent, and within the day is today`() {
        assertThat(formatLastPlayed(now - 60_000L, now)).isEqualTo("Played recently")
        assertThat(formatLastPlayed(now - 5 * 3_600_000L, now)).isEqualTo("Played today")
    }

    @Test
    fun `days read as days, and one of them reads as yesterday`() {
        assertThat(formatLastPlayed(now - day, now)).isEqualTo("Played yesterday")
        assertThat(formatLastPlayed(now - 3 * day, now)).isEqualTo("Played 3 days ago")
    }

    @Test
    fun `weeks months and years each get their own words`() {
        assertThat(formatLastPlayed(now - 8 * day, now)).isEqualTo("Played last week")
        assertThat(formatLastPlayed(now - 21 * day, now)).isEqualTo("Played 3 weeks ago")
        assertThat(formatLastPlayed(now - 40 * day, now)).isEqualTo("Played last month")
        assertThat(formatLastPlayed(now - 100 * day, now)).isEqualTo("Played 3 months ago")
        assertThat(formatLastPlayed(now - 400 * day, now)).isEqualTo("Played last year")
    }

    /**
     * A clock that has gone backwards reads as "recently", not as a negative age.
     *
     * Reachable without anything being broken: a timezone change, a manual clock
     * set, or a backup restored onto a device whose clock is behind.
     */
    @Test
    fun `a timestamp in the future does not produce nonsense`() {
        assertThat(formatLastPlayed(now + 10 * day, now)).isEqualTo("Played recently")
    }
}

/** Cover art, without restating the whole metadata tree at each call site. */
private fun GameEntry.withBoxArt(uri: String): GameEntry =
    copy(metadata = metadata.copy(artwork = metadata.artwork.copy(boxArt = uri)))
