package com.thor.data.library

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.GameEntry
import com.thor.core.model.GameMetadata
import com.thor.core.model.GridEntry
import com.thor.core.model.PlayStats
import com.thor.core.model.SmartFolderPreset
import com.thor.core.model.SmartQuery
import com.thor.core.model.SortOrder
import org.junit.Test

/**
 * What a smart folder actually holds.
 *
 * These exist because the feature shipped broken in a way no compiler could see:
 * the evaluator was complete and correct and *nothing called it*, while the grid
 * resolved a smart folder from the stored children a smart folder does not have.
 * Every folder created was placed, opened, and empty. A test that asks "does this
 * query return the right games" would have passed the whole time — the one worth
 * having asks whether the thing on screen is that.
 */
class SmartQueryEvaluatorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun game(
        id: String,
        title: String = id,
        platform: String = "snes",
        rating: Int? = null,
        year: Int? = null,
        launches: Int = 0,
        lastPlayed: Long? = null,
        playMillis: Long = 0L,
        favourite: Boolean = false,
    ) = GameEntry(
        id = id,
        title = title,
        sortTitle = title.lowercase(),
        platformId = platform,
        contentUri = "file://$id",
        fileName = "$id.rom",
        fileSizeBytes = 1_000L,
        metadata = GameMetadata.EMPTY.copy(rating = rating, releaseYear = year),
        stats = PlayStats(
            totalPlayMillis = playMillis,
            launchCount = launches,
            lastPlayedEpochMs = lastPlayed,
        ),
        isFavorite = favourite,
    )

    private val library: List<GridEntry> = listOf(
        game("a", "Alpha", rating = 90, year = 1995, launches = 3, lastPlayed = now - day),
        game("b", "Beta", rating = 40, year = 2005, favourite = true),
        game("c", "Gamma", platform = "nes", rating = 85, year = 1988, launches = 1,
            lastPlayed = now - 40 * day, playMillis = 9_000L),
        game("d", "Delta", year = 2020),
    )

    @Test
    fun `an empty query matches the whole library`() {
        val result = SmartQueryEvaluator.evaluate(library, SmartQuery(), now)
        assertThat(result).hasSize(library.size)
    }

    @Test
    fun `unplayed only excludes anything ever launched`() {
        val result = SmartQueryEvaluator.evaluate(library, SmartQuery(unplayedOnly = true), now)
        assertThat(result.map(GridEntry::id)).containsExactly("b", "d")
    }

    @Test
    fun `a recency window excludes a game never played rather than including it`() {
        /*
         * The trap in the filter. A game with no last-played date has no date to
         * fall outside the window, so a naive implementation lets every unplayed
         * game through a "played in the last week" folder — which is precisely
         * backwards.
         */
        val result = SmartQueryEvaluator.evaluate(
            library,
            SmartQuery(playedWithinDays = 7),
            now,
        )
        assertThat(result.map(GridEntry::id)).containsExactly("a")
    }

    @Test
    fun `a rating floor keeps unrated games out`() {
        // An unrated game is not a zero-rated one, but it must not clear a floor
        // either, or "best of" fills up with everything the scrapers missed.
        val result = SmartQueryEvaluator.evaluate(library, SmartQuery(minRating = 80), now)
        assertThat(result.map(GridEntry::id)).containsExactly("a", "c")
    }

    @Test
    fun `a year range selects a generation`() {
        val result = SmartQueryEvaluator.evaluate(
            library,
            SmartQuery(releasedAfterYear = 1990, releasedBeforeYear = 2000),
            now,
        )
        assertThat(result.map(GridEntry::id)).containsExactly("a")
    }

    @Test
    fun `a platform narrows to one system`() {
        val result = SmartQueryEvaluator.evaluate(
            library,
            SmartQuery(platformIds = setOf("nes")),
            now,
        )
        assertThat(result.map(GridEntry::id)).containsExactly("c")
    }

    @Test
    fun `a title match ignores case and punctuation`() {
        val result = SmartQueryEvaluator.evaluate(
            library,
            SmartQuery(titleContains = "ALPHA"),
            now,
        )
        assertThat(result.map(GridEntry::id)).containsExactly("a")
    }

    @Test
    fun `a limit caps the result after sorting, not before`() {
        // Otherwise "top 1 by rating" returns whichever game happened to be first
        // in the library and then sorts that single result, which is always right
        // by accident and never right on purpose.
        val result = SmartQueryEvaluator.evaluate(
            library,
            SmartQuery(sort = SortOrder.RATING, limit = 1),
            now,
        )
        assertThat(result.map(GridEntry::id)).containsExactly("a")
    }

    @Test
    fun `favourites only keeps starred games`() {
        val result = SmartQueryEvaluator.evaluate(library, SmartQuery(favoritesOnly = true), now)
        assertThat(result.map(GridEntry::id)).containsExactly("b")
    }

    @Test
    fun `non-game entries are never matched`() {
        // A smart folder is over games. Folders and widgets appearing inside one
        // would let a folder contain itself.
        val result = SmartQueryEvaluator.evaluate(library, SmartQuery(), now)
        assertThat(result.all { it is GameEntry }).isTrue()
    }

    @Test
    fun `every shipped preset returns something from a normal library`() {
        /*
         * The guard for the reported symptom. A preset that quietly matches
         * nothing is indistinguishable, on the grid, from the evaluator not being
         * called at all — which is the bug this feature shipped with.
         *
         * "Retro" is excluded from the non-empty assertion only because a library
         * with no pre-2000 games is a legitimate one; it still has to evaluate.
         */
        SmartFolderPreset.entries.forEach { preset ->
            val result = SmartQueryEvaluator.evaluate(library, preset.query, now)
            if (preset != SmartFolderPreset.RETRO) {
                assertThat(result).isNotEmpty()
            }
            preset.query.limit?.let { assertThat(result.size).isAtMost(it) }
        }
    }
}
