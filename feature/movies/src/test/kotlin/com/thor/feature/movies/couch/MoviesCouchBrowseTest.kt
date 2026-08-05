package com.thor.feature.movies.couch

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
     * Two categories on screen at once, not one.
     *
     * A catalogue that shows one shelf at a time can only be surveyed by walking
     * it, which is the one thing a screen read from across a room should not
     * require. This is the constraint the whole layout is arranged around.
     */
    @Test
    fun `the featured card and two shelves share a television`() {
        val available = 480.dp

        val hero = couchHeroHeight(available)
        val shelf = couchShelfHeight(available, hero)

        assertThat((hero + shelf * 2).value).isAtMost(available.value)
    }

    /**
     * A panel that is not the shape of a television still has to be usable.
     *
     * Couch mode is chosen by the viewer, not detected, so it can be running on
     * the handheld's own short panel while a cable is found. Two shelves stop
     * being the promise there - a card nobody can make out is worth less than a
     * shelf nobody can see - so the clamps take over and the list scrolls.
     */
    @Test
    fun `a short panel keeps a usable card and shelf`() {
        val hero = couchHeroHeight(180.dp)

        assertThat(hero.value).isAtLeast(172f)
        assertThat(couchShelfHeight(180.dp, hero).value).isAtLeast(118f)
    }

    /**
     * The card is never shorter than the things printed inside it.
     *
     * This is the constraint the hero clamps exist to hold, and it is the one that
     * was quietly broken once already: a floor of 180 under furniture measuring
     * 216 meant every panel small enough to hit the floor drew a card its own
     * contents overflowed.
     */
    @Test
    fun `the smallest featured card still holds its furniture`() {
        assertThat(couchHeroHeight(0.dp).value).isAtLeast(166f)
    }

    /**
     * The card's ceiling is the posters' floor.
     *
     * Everything the shelves get is what the featured card leaves, so a card
     * allowed to take all the room it could would be paid for in artwork nobody
     * can make out from a sofa.
     */
    @Test
    fun `a very tall panel stops either region swallowing the other`() {
        val hero = couchHeroHeight(2_000.dp)

        assertThat(hero.value).isAtMost(224f)
        assertThat(couchShelfHeight(2_000.dp, hero).value).isAtMost(262f)
    }

    /**
     * The featured card is the only ceiling that should bind on a television.
     *
     * The shelf clamp is a guard for panels that are the wrong shape, and while
     * it sat below what the arithmetic asked for, lowering the featured card
     * bought the shelves nothing at all - the height came off one clamp and
     * stopped at the next. This is the panel the launcher is actually looked at
     * on, and on it the division is what decides.
     */
    @Test
    fun `a television shelf is decided by the featured card and not by a clamp`() {
        val available = 730.dp
        val hero = couchHeroHeight(available)

        val shelf = couchShelfHeight(available, hero)

        assertThat(shelf.value).isWithin(1f).of((available - hero).value / 2f)
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
        val shelf = couchShelfHeight(480.dp, couchHeroHeight(480.dp))

        val poster = couchPosterHeight(shelf)

        assertThat(poster.value).isLessThan(shelf.value)
        // Header, gap, two-line caption, caption gap and the growth allowance.
        assertThat((shelf - poster).value).isAtLeast(55f)
    }

    @Test
    fun `posters stand up and continue-watching stills lie down`() {
        val poster = couchCardWidth(150.dp, landscape = false)
        val still = couchCardWidth(150.dp, landscape = true)

        assertThat(poster.value).isLessThan(150f)
        assertThat(still.value).isGreaterThan(150f)
    }

    /**
     * Four resume cards, whatever the panel turns out to be.
     *
     * How many fitted used to depend on the interface scale, because that is what
     * decides how many dp wide the screen is - the same shelf held four at one
     * setting and under three at another. Working the height back from the width
     * pins it.
     */
    @Test
    fun `four resume cards fit a shelf without scrolling`() {
        val rowWidth = 700.dp

        val height = couchStillHeight(posterHeight = 130.dp, rowWidth = rowWidth)
        val width = couchCardWidth(height, landscape = true)

        // The insets and the gaps come out of the function's own budget, so four
        // fitting inside the raw width is the loose form of the same claim.
        assertThat(width.value * 4).isAtMost(rowWidth.value)
    }

    @Test
    fun `a narrower shelf gets smaller resume cards rather than fewer`() {
        val narrow = couchStillHeight(posterHeight = 130.dp, rowWidth = 420.dp)
        val wide = couchStillHeight(posterHeight = 130.dp, rowWidth = 900.dp)

        assertThat(narrow.value).isLessThan(wide.value)
        // Shrunk, not shrunk away: it is still the shelf you resume from.
        assertThat(narrow.value).isAtLeast(62f)
    }

    /**
     * Four fit, and a fifth does not.
     *
     * The whole of what should decide how big a resume card is on a television is
     * that four of them fill the shelf. While the ceiling below sat well under
     * that, it was quietly deciding instead - and a shelf with room for six cards
     * drawing four small ones is a row of thumbnails with a gap after it.
     */
    @Test
    fun `a television shelf is filled by exactly four resume cards`() {
        val rowWidth = 1_243.dp

        val still = couchStillHeight(posterHeight = 184.dp, rowWidth = rowWidth)
        val width = couchCardWidth(still, landscape = true)

        assertThat(width.value * 4).isAtMost(rowWidth.value)
        assertThat(width.value * 5).isGreaterThan(rowWidth.value)
    }

    /**
     * A still stands shorter than the posters beside it.
     *
     * It is two and a half times as wide as a poster of the same height, so one
     * drawn to the posters' own height makes the shelf with the fewest titles on
     * it the loudest thing on the screen. A guard against a shelf far wider than
     * a television's, rather than the number that decides the size.
     */
    @Test
    fun `a very wide shelf stops the resume cards outgrowing the posters`() {
        val poster = 130.dp

        val still = couchStillHeight(poster, rowWidth = 4_000.dp)

        assertThat(still.value).isLessThan(poster.value)
        // Shorter, not shrunken away: it is still the artwork you resume from.
        assertThat(still.value).isAtLeast(poster.value * 0.6f)
    }

    /**
     * And the shelf under it is shorter too.
     *
     * Every shelf used to be given the poster height whatever it held, which left
     * the slack under a shorter card reading as one enormous gap between that
     * category and the next.
     */
    @Test
    fun `the resume shelf is shorter than a shelf of posters`() {
        val poster = 130.dp
        val still = couchStillHeight(poster, rowWidth = 1_200.dp)

        assertThat(couchShelfHeightFor(still).value)
            .isLessThan(couchShelfHeightFor(poster).value)
    }

    /**
     * A series needs more of the foot of the page than a film.
     *
     * A film's panel is a ranked list of one line each. A series has to fit a
     * season control and a strip of episodes above that same list, and squeezing
     * both into a film's height leaves one episode visible at a time.
     */
    @Test
    fun `the panel at the foot of a title page makes room for episodes`() {
        val television = 476.dp

        val film = couchActionPanelHeight(television, series = false)
        val series = couchActionPanelHeight(television, series = true)

        assertThat(series.value).isGreaterThan(film.value)
        // Whatever it takes, the artwork above it keeps the greater share.
        assertThat(series.value).isLessThan(television.value / 2f)
    }

    @Test
    fun `a short panel still leaves the sources somewhere to be`() {
        assertThat(couchActionPanelHeight(200.dp, series = false).value).isAtLeast(150f)
        assertThat(couchActionPanelHeight(200.dp, series = true).value).isAtLeast(180f)
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
        val onTelevision = couchOverviewLines(couchHeroHeight(480.dp))
        val onHandheld = couchOverviewLines(couchHeroHeight(200.dp))

        assertThat(onTelevision).isAtLeast(1)
        assertThat(onHandheld).isAtMost(onTelevision)
    }

    /** A card with room for the name and the play button shows those two. */
    @Test
    fun `a card with no room for prose asks for none`() {
        assertThat(couchOverviewLines(120.dp)).isEqualTo(0)
    }

    /** The whole synopsis is one press away; a hero strip is for deciding to look. */
    @Test
    fun `a very tall card stops short of a wall of text`() {
        assertThat(couchOverviewLines(1_200.dp)).isAtMost(3)
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
