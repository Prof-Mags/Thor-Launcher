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

    /**
     * The description gets the larger half.
     *
     * The shelf is the only region with a height of its own and the billboard
     * takes what is left, so a shelf that grew past half the panel would quietly
     * squeeze out the thing it is there to be read alongside.
     */
    @Test
    fun `one shelf leaves the description the greater part of the screen`() {
        val available = 560.dp

        val shelf = couchShelfHeight(available)

        assertThat(shelf.value).isLessThan(available.value / 2f)
    }

    /**
     * A panel that is not the shape of a television still has to be usable.
     *
     * Couch mode is chosen by the viewer, not detected, so it can be running on
     * the handheld's own short panel while a cable is found.
     */
    @Test
    fun `a short panel keeps a usable shelf`() {
        assertThat(couchShelfHeight(180.dp).value).isAtLeast(150f)
    }

    @Test
    fun `a very tall panel stops the shelf swallowing the description`() {
        assertThat(couchShelfHeight(2_000.dp).value).isAtMost(330f)
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

    /**
     * Every card stands up, continue watching included.
     *
     * That shelf is landscape everywhere else in the launcher. Here it is one
     * category among several on a screen that shows one at a time, so a row of
     * wide stills would be the shelf that broke the rhythm - and its cards would
     * come out half the height of every other shelf's.
     */
    @Test
    fun `cards are portrait whichever shelf they are on`() {
        val width = couchCardWidth(150.dp)

        assertThat(width.value).isWithin(0.5f).of(100f)
    }

    /**
     * The story is measured, not assumed.
     *
     * A television at the usual interface size leaves the billboard a couple of
     * hundred dp above the shelf, and a fixed line count sized for that pushes
     * the title itself off the top of a smaller one.
     */
    @Test
    fun `the story is cut to the room above the shelf`() {
        val television = 476.dp
        val handheld = 370.dp

        val onTelevision = couchOverviewLines(television - couchShelfHeight(television))
        val onHandheld = couchOverviewLines(handheld - couchShelfHeight(handheld))

        assertThat(onTelevision).isAtLeast(2)
        assertThat(onHandheld).isLessThan(onTelevision)
    }

    /** A billboard with room for the name and the play button shows those two. */
    @Test
    fun `a panel with no room for prose asks for none`() {
        assertThat(couchOverviewLines(120.dp)).isEqualTo(0)
    }

    @Test
    fun `a very tall panel stops short of a wall of text`() {
        assertThat(couchOverviewLines(1_200.dp)).isAtMost(6)
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
