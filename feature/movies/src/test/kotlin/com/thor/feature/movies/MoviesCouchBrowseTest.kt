package com.thor.feature.movies

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.core.model.WatchProgress
import org.junit.Test

/**
 * The couch catalogue's arithmetic.
 *
 * None of this fails loudly. A shelf that is a few dp too tall clips the caption
 * off every card on it, a card with no width becomes as wide as its longest
 * title, and a fact line that says "1 seasons" is simply read as sloppy — all of
 * them are perfectly good code, and all of them are only visible from a sofa.
 */
class MoviesCouchBrowseTest {

    private fun film(
        year: Int? = 1999,
        runtime: Int? = 136,
        rating: String? = "15",
        genres: List<String> = listOf("Action", "Science Fiction"),
    ) = MediaItem(
        id = MediaId(imdbId = "tt0133093", type = MediaType.MOVIE),
        title = "The Matrix",
        releaseYear = year,
        runtimeMinutes = runtime,
        contentRating = rating,
        genres = genres,
    )

    private fun series(seasons: Int) = MediaItem(
        id = MediaId(imdbId = "tt0903747", type = MediaType.SERIES),
        title = "Breaking Bad",
        releaseYear = 2008,
        runtimeMinutes = 49,
        seasons = buildList {
            add(Season(number = 0, name = "Specials"))
            repeat(seasons) { index -> add(Season(number = index + 1, name = "Season ${index + 1}")) }
        },
    )

    // ---- Regions ------------------------------------------------------------

    @Test
    fun `the billboard and one shelf both fit on a television`() {
        val available = 560.dp

        val billboard = couchBillboardHeight(available)
        val shelf = couchShelfHeight(available)

        // The point of the proportions: the focused shelf has to be on the screen
        // at the same time as the title it is describing, or moving along it
        // describes something the viewer cannot see.
        assertThat((billboard + shelf).value).isLessThan(available.value)
    }

    /**
     * A panel that is not the shape of a television still has to be usable.
     *
     * Couch mode is chosen by the viewer, not detected, so it can be running on
     * the handheld's own short panel while a cable is found.
     */
    @Test
    fun `a short panel keeps a usable billboard and shelf`() {
        val billboard = couchBillboardHeight(180.dp)
        val shelf = couchShelfHeight(180.dp)

        assertThat(billboard.value).isAtLeast(210f)
        assertThat(shelf.value).isAtLeast(150f)
    }

    @Test
    fun `a very tall panel stops the billboard swallowing the shelves`() {
        assertThat(couchBillboardHeight(2_000.dp).value).isAtMost(420f)
        assertThat(couchShelfHeight(2_000.dp).value).isAtMost(290f)
    }

    /**
     * The artwork is whatever the shelf has left after its own furniture.
     *
     * A `LazyRow` clips anything taller than itself, so the caption under a card
     * disappears rather than overflowing - which reads as the captions having
     * been turned off rather than as a layout that does not fit.
     */
    @Test
    fun `a card leaves room for its header and its caption`() {
        val shelf = couchShelfHeight(560.dp)

        val poster = couchPosterHeight(shelf)

        assertThat(poster.value).isLessThan(shelf.value)
        // Header, gap, caption, caption gap and the growth allowance either side.
        assertThat((shelf - poster).value).isAtLeast(60f)
    }

    @Test
    fun `posters stand up and stills lie down`() {
        val poster = couchCardWidth(150.dp, landscape = false)
        val still = couchCardWidth(150.dp, landscape = true)

        assertThat(poster.value).isLessThan(150f)
        assertThat(still.value).isGreaterThan(150f)
    }

    // ---- Words --------------------------------------------------------------

    @Test
    fun `a film says what it is, when it was, and how long it runs`() {
        val line = couchFactLine(film())

        assertThat(line).isEqualTo(
            "Film  /  1999  /  2h 16m  /  15  /  Action, Science Fiction",
        )
    }

    /** A title with nothing scraped still produces a line rather than blanks. */
    @Test
    fun `an unscraped title says only what is known`() {
        val line = couchFactLine(film(year = null, runtime = null, rating = null, genres = emptyList()))

        assertThat(line).isEqualTo("Film")
    }

    /**
     * Specials are not a season anybody counts.
     *
     * They are season 0 by the provider's convention and are kept everywhere else
     * because they are part of several series' running order - but "4 seasons"
     * for a show with three is a number the viewer can check against the box.
     */
    @Test
    fun `a series counts its seasons without the specials`() {
        assertThat(couchFactLine(series(seasons = 3))).contains("3 seasons")
        assertThat(couchFactLine(series(seasons = 1))).contains("1 season")
        assertThat(couchFactLine(series(seasons = 1))).doesNotContain("1 seasons")
    }

    @Test
    fun `runtime is read as hours rather than as a large number of minutes`() {
        assertThat(couchRuntimeLabel(49)).isEqualTo("49m")
        assertThat(couchRuntimeLabel(60)).isEqualTo("1h 0m")
        assertThat(couchRuntimeLabel(136)).isEqualTo("2h 16m")
    }

    @Test
    fun `a part-watched episode says which one and how much is left`() {
        val progress = WatchProgress(
            mediaId = MediaId(imdbId = "tt0903747", type = MediaType.SERIES),
            seasonNumber = 2,
            episodeNumber = 7,
            positionMs = 600_000L,
            durationMs = 2_400_000L,
        )

        assertThat(couchResumeLabel(progress)).isEqualTo("S02E07  /  30 min left")
    }

    /**
     * Never "0 min left".
     *
     * A card sitting on the last few seconds is still a card offering to finish
     * something, and a zero on it reads as the resume point being broken.
     */
    @Test
    fun `a nearly finished film still offers a minute`() {
        val progress = WatchProgress(
            mediaId = MediaId(imdbId = "tt0133093", type = MediaType.MOVIE),
            positionMs = 8_150_000L,
            durationMs = 8_160_000L,
        )

        assertThat(couchResumeLabel(progress)).isEqualTo("1 min left")
    }
}
