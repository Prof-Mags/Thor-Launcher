package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CreditedPerson
import com.thor.core.model.Episode
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.data.network.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDb — the catalogue behind the Movies section.
 *
 * One provider rather than the aggregated set the game library uses, and for a
 * different reason than convenience: film and television metadata has to be
 * internally consistent. A synopsis from one service, a season list from
 * another and episode stills from a third produce a detail panel where the
 * episode count disagrees with the episode list, which is worse than a panel
 * missing a field.
 *
 * IMDb ids are requested alongside, because they are what stream sources are
 * keyed by — TMDb's own ids mean nothing to a source provider.
 */
@Singleton
class TmdbClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) {

    suspend fun isConfigured(): Boolean = apiKey() != null

    private suspend fun apiKey(): String? =
        settings.media.first().tmdbApiKey.takeIf(String::isNotBlank)

    /** What is popular right now, for the browse screen's first row. */
    suspend fun trending(type: MediaType): List<MediaItem> =
        list("/trending/${type.path}/week", type)

    suspend fun popular(type: MediaType): List<MediaItem> = list("/${type.path}/popular", type)

    suspend fun topRated(type: MediaType): List<MediaItem> = list("/${type.path}/top_rated", type)

    /** Recent releases; TMDb calls these "now playing" and "on the air". */
    suspend fun recent(type: MediaType): List<MediaItem> = when (type) {
        MediaType.MOVIE -> list("/movie/now_playing", type)
        MediaType.SERIES -> list("/tv/on_the_air", type)
    }

    suspend fun byGenre(type: MediaType, genreId: Int): List<MediaItem> =
        list("/discover/${type.path}", type) { it.addQueryParameter("with_genres", "$genreId") }

    suspend fun search(query: String, type: MediaType): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return list("/search/${type.path}", type) { it.addQueryParameter("query", query) }
    }

    suspend fun similar(id: MediaId): List<MediaItem> =
        list("/${id.type.path}/${id.tmdbId}/similar", id.type)

    /**
     * Everything the detail panel shows, in one request.
     *
     * `append_to_response` is the whole reason this is one call rather than
     * four. The panel updates as the cursor moves along a row, so four
     * round-trips per title would mean the synopsis, the cast and the trailer
     * arriving at different moments — a panel visibly assembling itself while
     * the user has already moved on.
     */
    suspend fun details(id: MediaId): MediaItem? {
        val body = get("/${id.type.path}/${id.tmdbId}") {
            it.addQueryParameter(
                "append_to_response",
                "credits,videos,external_ids,release_dates,content_ratings,images",
            )
            // Logos and stills without a language baked in, plus the localised set.
            it.addQueryParameter("include_image_language", "en,null")
        } ?: return null

        return runCatching { json.decodeFromString<TmdbDetail>(body).toItem(id.type) }
            .onFailure { ThorLog.w(TAG, "Could not read details for ${id.key}", it) }
            .getOrNull()
    }

    /**
     * One season's episodes.
     *
     * Fetched separately and on demand: a long-running series has thousands of
     * episodes, and the detail panel only ever shows one season at a time.
     */
    suspend fun season(seriesId: Int, seasonNumber: Int): Season? {
        val body = get("/tv/$seriesId/season/$seasonNumber") ?: return null

        return runCatching { json.decodeFromString<TmdbSeasonDetail>(body).toSeason() }
            .onFailure { ThorLog.w(TAG, "Could not read season $seasonNumber of $seriesId", it) }
            .getOrNull()
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun list(
        path: String,
        type: MediaType,
        extra: (okhttp3.HttpUrl.Builder) -> Unit = {},
    ): List<MediaItem> {
        val body = get(path, extra) ?: return emptyList()

        return runCatching {
            json.decodeFromString<TmdbPage>(body)
                .results
                .orEmpty()
                // Trending returns both types in one list and labels each; every
                // other endpoint is single-type and labels none.
                .filter { it.mediaType == null || it.mediaType == type.path }
                .mapNotNull { it.toItem(type) }
        }
            .onFailure { ThorLog.w(TAG, "Could not read $path", it) }
            .getOrDefault(emptyList())
    }

    private suspend fun get(
        path: String,
        extra: (okhttp3.HttpUrl.Builder) -> Unit = {},
    ): String? {
        val key = apiKey() ?: return null
        val url = "$BASE_URL$path".toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply {
                addQueryParameter("api_key", key)
                addQueryParameter("language", "en-US")
                extra(this)
            }
            ?.build()
            ?: return null

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "TMDb ${response.code} for $path")
                    return null
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "TMDb request failed for $path", e)
            null
        }
    }

    // -------------------------------------------------------------------- DTOs

    @Serializable
    private data class TmdbPage(val results: List<TmdbSummary>? = null)

    @Serializable
    private data class TmdbSummary(
        val id: Int? = null,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
        @SerialName("media_type") val mediaType: String? = null,
    ) {
        fun toItem(type: MediaType): MediaItem? {
            val tmdbId = id ?: return null
            val displayTitle = (title ?: name)?.takeIf(String::isNotBlank) ?: return null

            return MediaItem(
                id = MediaId(type, tmdbId),
                title = displayTitle,
                overview = overview.orEmpty(),
                posterUrl = posterPath?.let { "$IMAGE_BASE$POSTER_SIZE$it" },
                backdropUrl = backdropPath?.let { "$IMAGE_BASE$BACKDROP_SIZE$it" },
                releaseYear = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
                ratings = MediaRatings(tmdb = voteAverage?.takeIf { it > 0f }),
            )
        }
    }

    @Serializable
    private data class TmdbDetail(
        val id: Int? = null,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null,
        val runtime: Int? = null,
        @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
        val genres: List<TmdbGenre>? = null,
        val seasons: List<TmdbSeasonSummary>? = null,
        val credits: TmdbCredits? = null,
        val videos: TmdbVideos? = null,
        val images: TmdbImages? = null,
        @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
        @SerialName("content_ratings") val contentRatings: TmdbContentRatings? = null,
        @SerialName("release_dates") val releaseDates: TmdbReleaseDates? = null,
    ) {
        fun toItem(type: MediaType): MediaItem? {
            val tmdbId = id ?: return null
            val displayTitle = (title ?: name)?.takeIf(String::isNotBlank) ?: return null

            return MediaItem(
                id = MediaId(type, tmdbId),
                title = displayTitle,
                overview = overview.orEmpty(),
                posterUrl = posterPath?.let { "$IMAGE_BASE$POSTER_SIZE$it" },
                backdropUrl = backdropPath?.let { "$IMAGE_BASE$BACKDROP_SIZE$it" },
                logoUrl = images?.logos?.firstOrNull()?.filePath
                    ?.let { "$IMAGE_BASE$LOGO_SIZE$it" },
                releaseYear = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull(),
                runtimeMinutes = runtime ?: episodeRunTime?.firstOrNull(),
                genres = genres.orEmpty().mapNotNull { it.name },
                contentRating = certification(),
                ratings = MediaRatings(tmdb = voteAverage?.takeIf { it > 0f }),
                cast = credits?.cast.orEmpty().take(CAST_LIMIT).map {
                    CreditedPerson(
                        name = it.name.orEmpty(),
                        role = it.character.orEmpty(),
                        profileUrl = it.profilePath?.let { path -> "$IMAGE_BASE$PROFILE_SIZE$path" },
                    )
                },
                crew = credits?.crew.orEmpty()
                    .filter { it.job in KEY_CREW_JOBS }
                    .map { CreditedPerson(it.name.orEmpty(), it.job.orEmpty()) },
                trailerUrl = videos?.results.orEmpty()
                    .firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                    ?.key
                    ?.let { "https://www.youtube.com/watch?v=$it" },
                seasons = seasons.orEmpty().mapNotNull { it.toSeason() },
                imdbId = externalIds?.imdbId,
            )
        }

        /**
         * The certification, from whichever of two differently-shaped fields the
         * type happens to use — films nest it a level deeper than series do.
         */
        private fun certification(): String? =
            contentRatings?.results.orEmpty()
                .firstOrNull { it.iso31661 == REGION }
                ?.rating
                ?.takeIf(String::isNotBlank)
                ?: releaseDates?.results.orEmpty()
                    .firstOrNull { it.iso31661 == REGION }
                    ?.releaseDates.orEmpty()
                    .firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) }
    }

    @Serializable
    private data class TmdbGenre(val id: Int? = null, val name: String? = null)

    @Serializable
    private data class TmdbSeasonSummary(
        @SerialName("season_number") val seasonNumber: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
    ) {
        fun toSeason(): Season? {
            val number = seasonNumber ?: return null
            return Season(
                number = number,
                name = name.orEmpty().ifBlank { "Season $number" },
                overview = overview.orEmpty(),
                posterUrl = posterPath?.let { "$IMAGE_BASE$POSTER_SIZE$it" },
            )
        }
    }

    @Serializable
    private data class TmdbSeasonDetail(
        @SerialName("season_number") val seasonNumber: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val episodes: List<TmdbEpisode>? = null,
    ) {
        fun toSeason(): Season? {
            val number = seasonNumber ?: return null
            return Season(
                number = number,
                name = name.orEmpty().ifBlank { "Season $number" },
                overview = overview.orEmpty(),
                posterUrl = posterPath?.let { "$IMAGE_BASE$POSTER_SIZE$it" },
                episodes = episodes.orEmpty().mapNotNull { it.toEpisode(number) },
            )
        }
    }

    @Serializable
    private data class TmdbEpisode(
        @SerialName("episode_number") val episodeNumber: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("still_path") val stillPath: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        val runtime: Int? = null,
    ) {
        fun toEpisode(season: Int): Episode? {
            val number = episodeNumber ?: return null
            return Episode(
                seasonNumber = season,
                number = number,
                title = name.orEmpty().ifBlank { "Episode $number" },
                overview = overview.orEmpty(),
                stillUrl = stillPath?.let { "$IMAGE_BASE$STILL_SIZE$it" },
                airDate = airDate,
                runtimeMinutes = runtime,
            )
        }
    }

    @Serializable
    private data class TmdbCredits(
        val cast: List<TmdbCastMember>? = null,
        val crew: List<TmdbCrewMember>? = null,
    )

    @Serializable
    private data class TmdbCastMember(
        val name: String? = null,
        val character: String? = null,
        @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    private data class TmdbCrewMember(val name: String? = null, val job: String? = null)

    @Serializable
    private data class TmdbVideos(val results: List<TmdbVideo>? = null)

    @Serializable
    private data class TmdbVideo(
        val key: String? = null,
        val site: String? = null,
        val type: String? = null,
    )

    @Serializable
    private data class TmdbImages(val logos: List<TmdbImage>? = null)

    @Serializable
    private data class TmdbImage(@SerialName("file_path") val filePath: String? = null)

    @Serializable
    private data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

    @Serializable
    private data class TmdbContentRatings(val results: List<TmdbContentRating>? = null)

    @Serializable
    private data class TmdbContentRating(
        @SerialName("iso_3166_1") val iso31661: String? = null,
        val rating: String? = null,
    )

    @Serializable
    private data class TmdbReleaseDates(val results: List<TmdbReleaseRegion>? = null)

    @Serializable
    private data class TmdbReleaseRegion(
        @SerialName("iso_3166_1") val iso31661: String? = null,
        @SerialName("release_dates") val releaseDates: List<TmdbReleaseDate>? = null,
    )

    @Serializable
    private data class TmdbReleaseDate(val certification: String? = null)

    private companion object {
        const val TAG = "Media"
        const val BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

        /*
         * Fixed widths rather than "original".
         *
         * A browse row shows twenty posters at a couple of hundred pixels each;
         * fetching the print-resolution version of every one of them is the
         * difference between a section that scrolls and one that stutters while
         * filling the cache with images no panel here can display.
         */
        const val POSTER_SIZE = "w500"
        const val BACKDROP_SIZE = "w1280"
        const val LOGO_SIZE = "w500"
        const val PROFILE_SIZE = "w185"
        const val STILL_SIZE = "w300"

        const val REGION = "US"
        const val CAST_LIMIT = 20
        val KEY_CREW_JOBS = setOf("Director", "Writer", "Screenplay", "Creator")
    }
}

/** TMDb's path segment for a type: `movie` or `tv`. */
private val MediaType.path: String
    get() = when (this) {
        MediaType.MOVIE -> "movie"
        MediaType.SERIES -> "tv"
    }
