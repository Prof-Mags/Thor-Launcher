package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.Episode
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.core.model.StremioAddons
import com.thor.data.network.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One browsable shelf a catalogue can serve.
 *
 * @param id the catalogue's own id, e.g. `top`
 * @param genre a genre filter the Stremio protocol passes as an `extra`
 */
data class CatalogRequest(
    val id: String,
    val title: String,
    val type: MediaType,
    val genre: String? = null,
)

/**
 * Films and shows, from the Stremio catalogue protocol.
 *
 * **This is why the section needs no API key.** THOR already speaks the Stremio
 * protocol to find *streams*; the same protocol serves catalogues, metadata,
 * artwork and search from the same endpoints, keyed by the same IMDb ids. Asking
 * one protocol for all of it removes both the credential and the identity
 * mismatch that came with keying titles on TMDb: a title read out of a catalogue
 * here can be handed straight to a source search with nothing in between.
 *
 * The default is **Cinemeta**, Stremio's own metadata addon, which is public,
 * keyless and the same catalogue the Stremio clients themselves browse. A user
 * who installs another metadata addon gets theirs instead; the launcher takes no
 * view on which, exactly as it does not for streams or for game scrapers.
 */
@Singleton
class StremioCatalogProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) {

    /**
     * The addon catalogues are read from.
     *
     * A user-configured addon that serves catalogues wins; Cinemeta is the
     * fallback rather than a hard-coded answer, so the section works on a fresh
     * install without pretending the choice is not the user's.
     */
    private suspend fun baseUrl(): String {
        val configured = settings.media.first().addons
            .filter { it.isUsable }
            .map { StremioAddons.normalise(it.url) }
            .firstOrNull { it.isNotBlank() && it.contains("cinemeta", ignoreCase = true) }
        return configured ?: CINEMETA
    }

    /**
     * The shelves the browse screen shows, in order.
     *
     * Popular and New come from the catalogue's own ordering; the rest are the
     * same catalogue filtered by genre, which is how the protocol expresses them
     * and means one endpoint serves every row.
     */
    fun shelves(type: MediaType): List<CatalogRequest> = buildList {
        add(CatalogRequest("top", "Popular", type))
        add(CatalogRequest("year", if (type == MediaType.MOVIE) "New releases" else "New episodes", type))
        GENRES.forEach { genre ->
            add(CatalogRequest("top", genre, type, genre = genre))
        }
    }

    /** One shelf. Empty rather than throwing: a missing row is not a broken screen. */
    suspend fun catalog(request: CatalogRequest): List<MediaItem> {
        val base = baseUrl()
        val type = request.type.slug
        val extra = request.genre?.let { "/genre=${it.encoded()}" }.orEmpty()
        val url = "$base/catalog/$type/${request.id}$extra.json"

        return fetch(url) { body ->
            json.decodeFromString<CatalogResponse>(body)
                .metas
                .orEmpty()
                .mapNotNull { it.toItem(request.type) }
        }.orEmpty()
    }

    /**
     * Everything known about one title, including a series' episodes.
     *
     * Cinemeta returns a series' whole episode list as a flat `videos` array
     * rather than as nested seasons, so it is grouped here — the panel wants
     * seasons and the protocol does not have them.
     */
    suspend fun details(id: MediaId): MediaItem? {
        val base = baseUrl()
        val url = "$base/meta/${id.type.slug}/${id.imdbId}.json"

        return fetch(url) { body ->
            json.decodeFromString<MetaResponse>(body).meta?.toItem(id.type, full = true)
        }
    }

    /**
     * Titles matching [query].
     *
     * The protocol expresses search as an `extra` on a catalogue rather than as
     * its own endpoint, so this is the same call the shelves make with one more
     * segment.
     */
    suspend fun search(query: String, type: MediaType): List<MediaItem> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()

        val base = baseUrl()
        val url = "$base/catalog/${type.slug}/top/search=${term.encoded()}.json"

        return fetch(url) { body ->
            json.decodeFromString<CatalogResponse>(body)
                .metas
                .orEmpty()
                .mapNotNull { it.toItem(type) }
        }.orEmpty()
    }

    /**
     * Bounded, and never fatal.
     *
     * A catalogue that does not answer costs one empty shelf. Letting it throw
     * would cost the whole screen, and letting it hang would cost the whole
     * screen for as long as the client's timeouts allow.
     */
    private suspend fun <T> fetch(url: String, parse: (String) -> T?): T? = try {
        withTimeout(REQUEST_TIMEOUT_MS) {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "Catalogue ${response.code} for $url")
                    null
                } else {
                    parse(response.body?.string().orEmpty())
                }
            }
        }
    } catch (e: IOException) {
        ThorLog.w(TAG, "Catalogue request failed: $url", e)
        null
    } catch (e: TimeoutCancellationException) {
        ThorLog.w(TAG, "Catalogue timed out: $url", e)
        null
    } catch (e: IllegalArgumentException) {
        ThorLog.w(TAG, "Unexpected catalogue response: $url", e)
        null
    }

    // -------------------------------------------------------------------- DTOs

    @Serializable
    private data class CatalogResponse(val metas: List<Meta>? = null)

    @Serializable
    private data class MetaResponse(val meta: Meta? = null)

    @Serializable
    private data class Meta(
        val id: String? = null,
        val name: String? = null,
        val poster: String? = null,
        val background: String? = null,
        val logo: String? = null,
        val description: String? = null,
        /** "2014" for a film, "2014-2019" or "2014-" for a running series. */
        val year: String? = null,
        val released: String? = null,
        val runtime: String? = null,
        val genres: List<String>? = null,
        @SerialName("imdbRating") val imdbRating: String? = null,
        @SerialName("cast") val cast: List<String>? = null,
        @SerialName("director") val director: List<String>? = null,
        val videos: List<Video>? = null,
    ) {
        fun toItem(type: MediaType, full: Boolean = false): MediaItem? {
            // No IMDb id means nothing downstream can search for it, so it is
            // better absent from the shelf than present and unplayable.
            val imdb = id?.takeIf { it.startsWith("tt") } ?: return null
            val name = this.name?.takeIf(String::isNotBlank) ?: return null

            return MediaItem(
                id = MediaId(type = type, imdbId = imdb),
                title = name,
                overview = description.orEmpty(),
                posterUrl = poster,
                backdropUrl = background,
                logoUrl = logo,
                releaseYear = year?.take(4)?.toIntOrNull()
                    ?: released?.take(4)?.toIntOrNull(),
                runtimeMinutes = runtime?.filter(Char::isDigit)?.toIntOrNull(),
                genres = genres.orEmpty(),
                ratings = MediaRatings(tmdb = imdbRating?.toFloatOrNull()),
                seasons = if (full) videos.orEmpty().toSeasons() else emptyList(),
            )
        }
    }

    @Serializable
    private data class Video(
        val id: String? = null,
        val title: String? = null,
        val name: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val overview: String? = null,
        val description: String? = null,
        val thumbnail: String? = null,
        val released: String? = null,
    )

    private companion object {
        const val TAG = "Media"

        /**
         * Stremio's own metadata addon: public, keyless, and the catalogue its
         * clients browse. Used when the user has configured no catalogue of
         * their own, so the section is useful before it is configured.
         */
        const val CINEMETA = "https://v3-cinemeta.strem.io"

        /** Long enough for a cold addon, short enough not to hold a shelf open. */
        const val REQUEST_TIMEOUT_MS = 15_000L

        /**
         * The rows beyond Popular and New.
         *
         * Cinemeta's genre vocabulary, in the order a browser is most likely to
         * want them. Both media types use the same list — the protocol filters
         * the same way for each, and a genre with nothing behind it simply
         * yields an empty shelf, which is dropped.
         */
        val GENRES = listOf(
            "Action",
            "Comedy",
            "Drama",
            "Thriller",
            "Sci-Fi",
            "Horror",
            "Animation",
            "Documentary",
            "Crime",
            "Fantasy",
            "Adventure",
            "Mystery",
        )

        val MediaType.slug: String get() = if (this == MediaType.MOVIE) "movie" else "series"

        /** Percent-encoding for one path segment's worth of user text. */
        fun String.encoded(): String =
            java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

        /**
         * Cinemeta's flat episode list, grouped the way a viewer reads it.
         *
         * The protocol returns every episode of every season in one array; the
         * detail panel wants seasons with episodes inside them, so the grouping
         * happens here rather than in the UI.
         */
        fun List<Video>.toSeasons(): List<Season> = this
            .filter { it.season != null && it.episode != null }
            .groupBy { it.season!! }
            .map { (number, videos) ->
                Season(
                    number = number,
                    name = if (number == 0) "Specials" else "Season $number",
                    episodes = videos
                        .sortedBy { it.episode }
                        .map { video ->
                            Episode(
                                seasonNumber = number,
                                number = video.episode!!,
                                title = video.title ?: video.name ?: "Episode ${video.episode}",
                                overview = video.overview ?: video.description.orEmpty(),
                                stillUrl = video.thumbnail,
                                airDate = video.released?.take(10),
                            )
                        },
                )
            }
            .sortedBy { it.number }
    }
}
