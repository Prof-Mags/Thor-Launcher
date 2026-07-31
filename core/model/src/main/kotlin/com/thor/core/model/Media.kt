package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A film or a television series.
 *
 * One type for both, rather than a sealed pair, because almost everything the
 * launcher does with them is identical — browse, describe, resolve sources,
 * play, remember where you were. The differences are two nullable fields and a
 * list of seasons, which is a far smaller cost than duplicating the repository,
 * the rows, the detail panel and the player for each.
 */
@Serializable
enum class MediaType { MOVIE, SERIES }

/**
 * A title's identity.
 *
 * TMDb's id plus the type, because those ids are only unique *within* a type —
 * movie 1399 and series 1399 are unrelated. Carrying the pair everywhere removes
 * a whole class of lookup that returns the wrong title and cannot be debugged
 * from the symptom.
 */
@Serializable
data class MediaId(val type: MediaType, val tmdbId: Int) {
    /** Stable string form, for database keys and caches. */
    val key: String get() = "${type.name.lowercase()}:$tmdbId"

    companion object {
        fun parse(key: String): MediaId? {
            val (type, id) = key.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val mediaType = MediaType.entries.firstOrNull { it.name.equals(type, true) } ?: return null
            return MediaId(mediaType, id.toIntOrNull() ?: return null)
        }
    }
}

/**
 * Scores from the services that publish them.
 *
 * All nullable and independently so. A title with a TMDb score and no Rotten
 * Tomatoes rating is the normal case, not a partial failure, and the panel shows
 * whichever it has rather than reserving space for all three.
 */
@Serializable
data class MediaRatings(
    /** 0..10. */
    val tmdb: Float? = null,
    /** 0..10, via the IMDb id where a provider supplies it. */
    val imdb: Float? = null,
    /** 0..100, the Tomatometer. */
    val rottenTomatoes: Int? = null,
) {
    val isEmpty: Boolean get() = tmdb == null && imdb == null && rottenTomatoes == null
}

/** Someone in front of or behind the camera. */
@Serializable
data class CreditedPerson(
    val name: String,
    /** Character for cast, job title for crew. */
    val role: String,
    val profileUrl: String? = null,
)

/** One episode of a series. */
@Serializable
data class Episode(
    val seasonNumber: Int,
    val number: Int,
    val title: String,
    val overview: String = "",
    val stillUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
) {
    /** "S02E07", the form every release name uses. */
    val code: String get() = "S%02dE%02d".format(seasonNumber, number)
}

/**
 * A season, with its episodes.
 *
 * Specials are season 0 by TMDb's convention and are kept rather than filtered:
 * they are part of several series' running order, and hiding them makes the
 * episode list disagree with every other source the user has seen.
 */
@Serializable
data class Season(
    val number: Int,
    val name: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val episodes: List<Episode> = emptyList(),
) {
    val isSpecials: Boolean get() = number == 0
}

/**
 * Everything the launcher knows about one title.
 *
 * Deliberately flat and fully resolved. The detail panel updates as the cursor
 * moves along a row, so it cannot afford to discover halfway through drawing
 * that it needs another request; whatever is on screen was either already
 * fetched or is visibly absent.
 */
@Serializable
data class MediaItem(
    val id: MediaId,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** Clear-background wordmark, when the provider has one. */
    val logoUrl: String? = null,
    val releaseYear: Int? = null,
    /** Films: the film's length. Series: the typical episode length. */
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    /** Certification for the user's region, e.g. "PG-13" or "15". */
    val contentRating: String? = null,
    val ratings: MediaRatings = MediaRatings(),
    val cast: List<CreditedPerson> = emptyList(),
    val crew: List<CreditedPerson> = emptyList(),
    /** YouTube watch URL for the primary trailer, when one is published. */
    val trailerUrl: String? = null,
    /** Empty for films, and for series whose seasons have not been loaded yet. */
    val seasons: List<Season> = emptyList(),
    /** "tt0133093" — what every stream source is keyed by, not TMDb's id. */
    val imdbId: String? = null,
) {
    val isSeries: Boolean get() = id.type == MediaType.SERIES

    /** Seasons a viewer would call seasons, specials last. */
    val orderedSeasons: List<Season>
        get() = seasons.sortedBy { if (it.isSpecials) Int.MAX_VALUE else it.number }

    fun episode(season: Int, number: Int): Episode? =
        seasons.firstOrNull { it.number == season }
            ?.episodes
            ?.firstOrNull { it.number == number }
}

/**
 * One shelf on the browse screen.
 *
 * The rows are data rather than an enum of screens, so "Trending", "Continue
 * watching" and a genre the user pinned are the same thing arranged differently
 * — which is what lets the layout be rearranged without touching the fetchers.
 */
@Serializable
data class MediaRow(
    val id: String,
    val title: String,
    val items: List<MediaItem> = emptyList(),
    /** Landscape stills instead of posters — for continue watching and episodes. */
    val landscape: Boolean = false,
)

/**
 * How far through something the viewer got.
 *
 * Held per *episode* for a series rather than per series, because "continue
 * watching" has to resume the right one and a series-level position cannot say
 * which episode it belonged to.
 */
@Serializable
data class WatchProgress(
    val mediaId: MediaId,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /**
     * Near enough the end to count as watched.
     *
     * Credits run for minutes, and a viewer who stops during them has finished.
     * Resuming four seconds before the end and calling it "continue watching" is
     * the single most irritating thing a media app can do.
     */
    val isFinished: Boolean get() = fraction >= FINISHED_FRACTION

    /** Worth offering to resume: started, and not effectively over. */
    val isResumable: Boolean get() = positionMs > RESUME_FLOOR_MS && !isFinished

    companion object {
        const val FINISHED_FRACTION = 0.92f
        const val RESUME_FLOOR_MS = 30_000L
    }
}
