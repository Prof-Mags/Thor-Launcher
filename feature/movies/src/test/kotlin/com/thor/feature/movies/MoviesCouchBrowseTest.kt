package com.thor.feature.movies

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.core.model.WatchProgress
import org.junit.Test

/**
 * The couch catalogue's arithmetic.
 *
 * None of this fails loudly. A shelf that is a few dp too tall clips the caption
 * off every card on it, a card with no width becomes as wide as its longest
 * title, and a fact line that says "1 seasons" is simply read as sloppy - all of
 * them are perfectly good code, and all of them are only visible from a sofa.
 */
class MoviesCouchBrowseTest {

    private fun film(
        id: String = "tt0133093",
        year: Int? = 1999,
        runtime: Int? = 136,
        rating: String? = "15",
        genres: List<String> = listOf("Action", "Science Fiction"),
        ratings: MediaRatings = MediaRatings(),
    ) = MediaItem(
        id = MediaId(imdbId = id, type = MediaType.MOVIE),
        title = "The Matrix",
        releaseYear = year,
        runtimeMinutes = runtime,
        contentRating = rating,
        genres = genres,
        ratings = ratings,
    )

    private fun series(seasons: Int) = MediaItem(
        id = MediaId(imdbId = "tt0903747", type = MediaType.SERIES),
        title = "Breaking Bad",
        releaseYear = 2008,
        runtimeMinutes = 49,
        seasons = buildList {
            add(Season(number = 0, name = "Specials"))
            repeat(seasons) { index ->
                add(Season(number = index + 1, name = "Season ${index + 1}"))
            }
        },
    )

    private fun progress(
        id: String = "tt0133093",
        season: Int? = null,
        episode: Int? = null,
        positionMs: Long = 600_000L,
        durationMs: Long = 2_400_000L,
    ) = WatchProgress(
        mediaId = MediaId(imdbId = id, type = MediaType.MOVIE),
        seasonNumber = season,
        episodeNumber = episode,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    // ---- Regions ------------------------------------------------------------

    /**
     * The featured card and the first shelf under it are the screen.
     *
     * The shelves scroll, so they do not all have to fit - but the top one has to
     * be at least partly on screen beside the card describing it, or moving along
     * it describes something the viewer cannot see.
     */
    @Test
    fun `the featured card and the first shelf share a television`() {
        val available = 400.dp

        val hero = couchHeroHeight(available)
        val shelf = couchShelfHeight(available)

        assertThat((hero + shelf).value).isAtMost(available.value)
    }

    /**
     * A panel that is not the shape of a television still has to be usable.
     *
     * Couch mode is chosen by the viewer, not detected, so it can be running on
     * the handheld's own short panel while a cable is found.
     */
    @Test
    fun `a short panel keeps a usable card and shelf`() {
        assertThat(couchHeroHeight(180.dp).value).isAtLeast(200f)
        assertThat(couchShelfHeight(180.dp).value).isAtLeast(148f)
    }

    /**
     * The card is never shorter than the things printed inside it.
     *
     * This is the constraint the two clamps exist to hold, and it is the one that
     * was quietly broken: a floor of 180 under furniture measuring 216 meant every
     * panel small enough to hit the floor drew a card its own contents overflowed.
     */
    @Test
    fun `the smallest featured card still holds its furniture`() {
        assertThat(couchHeroHeight(0.dp).value).isAtLeast(180f)
    }

    @Test
    fun `a very tall panel stops either region swallowing the other`() {
        assertThat(couchHeroHeight(2_000.dp).value).isAtMost(340f)
        assertThat(couchShelfHeight(2_000.dp).value).isAtMost(300f)
    }

    /**
     * The artwork is whatever the shelf has left after its own furniture.
     *
     * A `LazyRow` clips anything taller than itself, so the caption under a card
     * disappears rather than overflowing - which reads as the captions having been
     * turned off rather than as a layout that does not fit.
     */
    @Test
    fun `a card leaves room for its header and its caption`() {
        val shelf = couchShelfHeight(400.dp)

        val poster = couchPosterHeight(shelf)

        assertThat(poster.value).isLessThan(shelf.value)
        // Header, gap, two-line caption, caption gap and the growth allowance.
        assertThat((shelf - poster).value).isAtLeast(60f)
    }

    @Test
    fun `posters stand up and continue-watching stills lie down`() {
        val poster = couchCardWidth(150.dp, landscape = false)
        val still = couchCardWidth(150.dp, landscape = true)

        assertThat(poster.value).isLessThan(150f)
        assertThat(still.value).isGreaterThan(150f)
    }

    /**
     * The story is measured, not assumed.
     *
     * A television at the usual interface size leaves the card a couple of hundred
     * dp, and a fixed line count sized for that pushes the title itself out of the
     * card on a smaller one.
     */
    @Test
    fun `the story is cut to the room inside the card`() {
        val onTelevision = couchOverviewLines(couchHeroHeight(400.dp))
        val onHandheld = couchOverviewLines(couchHeroHeight(200.dp))

        assertThat(onTelevision).isAtLeast(1)
        assertThat(onHandheld).isAtMost(onTelevision)
    }

    /** A card with room for the name and the play button shows those two. */
    @Test
    fun `a card with no room for prose asks for none`() {
        assertThat(couchOverviewLines(120.dp)).isEqualTo(0)
    }

    @Test
    fun `a very tall card stops short of a wall of text`() {
        assertThat(couchOverviewLines(1_200.dp)).isAtMost(4)
    }

    // ---- What the rail reports ----------------------------------------------

    /**
     * A title on three shelves is one title.
     *
     * Catalogue shelves overlap heavily - a film is regularly trending and new and
     * in its genre row at once - so summing the rows would report a library
     * several times the size of the one that came back.
     */
    @Test
    fun `the rail counts each title once however many shelves it is on`() {
        val matrix = film(id = "tt0133093")
        val speed = film(id = "tt0111257")
        val rows = listOf(
            MediaRow(id = "top", title = "Popular", items = listOf(matrix, speed)),
            MediaRow(id = "new", title = "New releases", items = listOf(matrix)),
        )

        val stats = couchMediaStats(rows)

        assertThat(stats.titles).isEqualTo(2)
        assertThat(stats.categories).isEqualTo(2)
    }

    /** Only what is worth resuming: a finished film is not still being watched. */
    @Test
    fun `the rail counts what can actually be resumed`() {
        val started = film(id = "tt0133093")
        val finished = film(id = "tt0111257")
        val rows = listOf(
            MediaRow(
                id = "continue",
                title = "Continue watching",
                items = listOf(started, finished),
                landscape = true,
                progress = listOf(
                    progress(id = "tt0133093", positionMs = 600_000L, durationMs = 2_400_000L),
                    progress(id = "tt0111257", positionMs = 2_390_000L, durationMs = 2_400_000L),
                ),
            ),
        )

        assertThat(couchMediaStats(rows).continueWatching).isEqualTo(1)
    }

    @Test
    fun `an empty catalogue counts to nothing rather than failing`() {
        val stats = couchMediaStats(emptyList())

        assertThat(stats.titles).isEqualTo(0)
        assertThat(stats.continueWatching).isEqualTo(0)
        assertThat(stats.categories).isEqualTo(0)
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
        val bare = film(year = null, runtime = null, rating = null, genres = emptyList())

        assertThat(couchFactLine(bare)).isEqualTo("Film")
    }

    /**
     * Specials are not a season anybody counts.
     *
     * They are season 0 by the provider's convention and are kept everywhere else
     * because they are part of several series' running order - but "4 seasons" for
     * a show with three is a number the viewer can check against the box.
     */
    @Test
    fun `a series counts its seasons without the specials`() {
        assertThat(couchFactLine(series(seasons = 3))).contains("3 seasons")
        assertThat(couchFactLine(series(seasons = 1))).contains("1 season")
        assertThat(couchFactLine(series(seasons = 1))).doesNotContain("1 seasons")
    }

    /** Under a card there is room for two facts, not six. */
    @Test
    fun `a card caption is the year and one genre`() {
        assertThat(couchCardSubtitle(film())).isEqualTo("1999  /  Action")
        assertThat(couchCardSubtitle(film(genres = emptyList()))).isEqualTo("1999  /  Film")
    }

    @Test
    fun `runtime is read as hours rather than as a large number of minutes`() {
        assertThat(couchRuntimeLabel(49)).isEqualTo("49m")
        assertThat(couchRuntimeLabel(60)).isEqualTo("1h 0m")
        assertThat(couchRuntimeLabel(136)).isEqualTo("2h 16m")
    }

    @Test
    fun `a part-watched episode says which one and how much is left`() {
        val label = couchResumeLabel(progress(season = 2, episode = 7))

        assertThat(label).isEqualTo("S02E07  /  30 min left")
    }

    /** Over an hour left is an evening's decision, so it is said in hours. */
    @Test
    fun `a film barely started says how many hours are left`() {
        val label = couchResumeLabel(progress(positionMs = 60_000L, durationMs = 5_520_000L))

        assertThat(label).isEqualTo("1h 31m left")
    }

    /**
     * Never "0 min left".
     *
     * A card sitting on the last few seconds is still a card offering to finish
     * something, and a zero on it reads as the resume point being broken.
     */
    @Test
    fun `a nearly finished film still offers a minute`() {
        val label = couchResumeLabel(progress(positionMs = 8_150_000L, durationMs = 8_160_000L))

        assertThat(label).isEqualTo("1 min left")
    }

    /**
     * One score, from whoever is most likely to have one.
     *
     * Two badges reading 8.7 and 82% invite the arithmetic of reconciling them,
     * which is not a thing to be doing while choosing a film.
     */
    @Test
    fun `the score shown is the best one available, with its source`() {
        val all = MediaRatings(tmdb = 8.2f, imdb = 8.7f, rottenTomatoes = 93)
        val onlyTomatoes = MediaRatings(rottenTomatoes = 93)

        assertThat(couchScore(all)).isEqualTo("IMDb" to "8.7")
        assertThat(couchScore(MediaRatings(tmdb = 8.2f))).isEqualTo("TMDb" to "8.2")
        assertThat(couchScore(onlyTomatoes)).isEqualTo("RT" to "93%")
        assertThat(couchScore(MediaRatings())).isNull()
    }

    @Test
    fun `a shelf of one is not a shelf of one titles`() {
        assertThat(couchCountLabel(1)).isEqualTo("1 title")
        assertThat(couchCountLabel(24)).isEqualTo("24 titles")
    }
}
