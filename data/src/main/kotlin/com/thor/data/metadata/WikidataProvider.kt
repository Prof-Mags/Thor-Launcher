package com.thor.data.metadata

import com.thor.core.common.log.ThorLog
import com.thor.core.model.GameMetadata
import com.thor.data.network.await
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
 * Wikidata and Wikipedia — descriptions and facts, with no API key.
 *
 * Every other textual source needs a credential: RAWG and SteamGridDB want an API
 * key, ScreenScraper a registered developer pair. That left a launcher with no
 * keys configured able to download artwork and nothing else, which reads as a
 * broken scraper rather than as an unconfigured one. Wikidata is open, needs no
 * registration, and has developer and publisher for essentially every
 * commercially released game. The exact English Wikipedia article linked from
 * the matched entity supplies a concise introduction for the description field.
 *
 * It carries no artwork — Commons images are inconsistently framed and licensed —
 * so this complements SteamGridDB rather than replacing it.
 *
 * Two requests per game, plus one extract request for the best match:
 *  1. `wbsearchentities` to turn a title into candidate entity ids. Its matching
 *     is fuzzy and good, which is why it is used instead of an exact SPARQL label
 *     match — "Pokemon Red" does not equal `"Pokémon Red Version"@en`.
 *  2. one SPARQL query resolving claims, labels and the exact article server-side. Reading
 *     the claims directly would return entity ids for developer and publisher and
 *     need a third round trip to turn them into names.
 *  3. one MediaWiki extracts request for that article's plain-text introduction.
 */
@Singleton
class WikidataProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "Wikidata"

    /** Open data: there is nothing to configure, so this is always usable. */
    override suspend fun isConfigured(): Boolean = true

    override suspend fun checkConnection(): ProviderStatus = try {
        val url = "$API_URL".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("action", "wbsearchentities")
            ?.addQueryParameter("search", "video game")
            ?.addQueryParameter("language", "en")
            ?.addQueryParameter("type", "item")
            ?.addQueryParameter("limit", "1")
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: return ProviderStatus.Error("Malformed URL")

        client.newCall(get(url.toString())).await().use { response ->
            if (response.isSuccessful) {
                ProviderStatus.Connected
            } else {
                ProviderStatus.Error("HTTP ${response.code}")
            }
        }
    } catch (e: IOException) {
        ProviderStatus.Unreachable(e.message ?: "No connection")
    }

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        val entities = searchEntities(query.title).ifEmpty {
            // The scanner's normalised title drops region tags and revision
            // markers, which Wikidata has no entry for.
            if (query.sortTitle != query.title) searchEntities(query.sortTitle) else emptyList()
        }
        if (entities.isEmpty()) return emptyList()

        // Ranked before fetching, so only the best few cost a SPARQL request.
        val ranked = entities
            .map { it to TitleMatcher.confidence(query.title, it.label.orEmpty()) }
            .filter { (entity, confidence) ->
                confidence > 0f && looksLikeAGame(entity.description)
            }
            .sortedByDescending { it.second }
            .take(MAX_CANDIDATES)

        if (ranked.isEmpty()) return emptyList()

        val claims = fetchClaims(ranked.map { it.first.id }) ?: return emptyList()
        val bestDescription = ranked.firstOrNull()
            ?.first
            ?.id
            ?.let(claims::get)
            ?.wikipediaTitle
            ?.let { fetchWikipediaDescription(it) }

        return ranked.mapIndexedNotNull { index, (entity, confidence) ->
            val row = claims[entity.id] ?: return@mapIndexedNotNull null
            val description = bestDescription.takeIf { index == 0 }
            MetadataCandidate(
                providerId = ID,
                remoteId = entity.id,
                matchedTitle = entity.label.orEmpty(),
                confidence = confidence,
                metadata = GameMetadata(
                    description = description,
                    developer = row.developer,
                    publisher = row.publisher,
                    releaseDate = row.releaseDate,
                    releaseYear = row.releaseYear,
                    genres = row.genres,
                    providerSources = buildMap {
                        if (description != null) put(GameMetadata.FIELD_DESCRIPTION, ID)
                        if (row.developer != null) put(GameMetadata.FIELD_DEVELOPER, ID)
                        if (row.publisher != null) put(GameMetadata.FIELD_PUBLISHER, ID)
                        if (row.releaseDate != null) put(GameMetadata.FIELD_RELEASE_DATE, ID)
                        if (row.genres.isNotEmpty()) put(GameMetadata.FIELD_GENRES, ID)
                    },
                ),
            )
        }
    }

    /**
     * Turns a title into candidate entity ids.
     *
     * A failure here is a dead end for this provider but must not fail the scrape,
     * so it degrades to no candidates.
     */
    private suspend fun searchEntities(title: String): List<WdEntity> {
        val url = API_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("action", "wbsearchentities")
            ?.addQueryParameter("search", title)
            ?.addQueryParameter("language", "en")
            ?.addQueryParameter("uselang", "en")
            ?.addQueryParameter("type", "item")
            ?.addQueryParameter("limit", SEARCH_LIMIT.toString())
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: return emptyList()

        return try {
            client.newCall(get(url.toString())).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                json.decodeFromString<WdSearchResponse>(body).search.orEmpty()
            }
        } catch (e: IOException) {
            ThorLog.d(TAG) { "Search failed for $title" }
            emptyList()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unparseable search response for $title", e)
            emptyList()
        }
    }

    /**
     * Fetches claims for several entities in one SPARQL query.
     *
     * `wikibase:label` resolves developer, publisher and genre to names on the
     * server, which is what keeps this to a single request instead of one lookup
     * per referenced entity. Rows are grouped because a game with two genres comes
     * back as two rows.
     */
    private suspend fun fetchClaims(ids: List<String>): Map<String, ClaimRow>? {
        val values = ids.joinToString(" ") { "wd:$it" }
        val query = """
            SELECT ?item ?article ?devLabel ?pubLabel ?date ?genreLabel WHERE {
              VALUES ?item { $values }
              OPTIONAL {
                ?article schema:about ?item;
                         schema:isPartOf <https://en.wikipedia.org/>.
              }
              OPTIONAL { ?item wdt:$PROP_DEVELOPER ?dev. }
              OPTIONAL { ?item wdt:$PROP_PUBLISHER ?pub. }
              OPTIONAL { ?item wdt:$PROP_PUBLICATION_DATE ?date. }
              OPTIONAL { ?item wdt:$PROP_GENRE ?genre. }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            }
        """.trimIndent()

        val url = SPARQL_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: return null

        return try {
            client.newCall(get(url.toString())).await().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseClaims(json.decodeFromString<WdSparqlResponse>(body))
            }
        } catch (e: IOException) {
            ThorLog.d(TAG) { "SPARQL request failed" }
            null
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unparseable SPARQL response", e)
            null
        }
    }

    private fun parseClaims(response: WdSparqlResponse): Map<String, ClaimRow> {
        val byEntity = mutableMapOf<String, ClaimRow>()

        response.results?.bindings.orEmpty().forEach { binding ->
            // The item comes back as a full URI; only the trailing id matters.
            val entityId = binding.item?.value?.substringAfterLast('/') ?: return@forEach
            val existing = byEntity[entityId] ?: ClaimRow()
            val date = binding.date?.value

            byEntity[entityId] = existing.copy(
                developer = existing.developer ?: binding.devLabel?.value?.takeIf(::isName),
                publisher = existing.publisher ?: binding.pubLabel?.value?.takeIf(::isName),
                wikipediaTitle = existing.wikipediaTitle
                    ?: binding.article?.value
                        ?.toHttpUrlOrNull()
                        ?.pathSegments
                        ?.lastOrNull()
                        ?.takeIf(String::isNotBlank),
                releaseDate = existing.releaseDate ?: date,
                // Wikidata dates are ISO 8601 timestamps, so the year is the
                // leading four characters when it parses at all.
                releaseYear = existing.releaseYear ?: date?.take(4)?.toIntOrNull(),
                genres = (existing.genres + listOfNotNull(
                    binding.genreLabel?.value?.takeIf(::isName),
                )).distinct().take(MAX_GENRES),
            )
        }
        return byEntity
    }

    /** Plain-text introduction for the exact English article linked by Wikidata. */
    private suspend fun fetchWikipediaDescription(title: String): String? {
        val url = WIKIPEDIA_API_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("action", "query")
            ?.addQueryParameter("prop", "extracts")
            ?.addQueryParameter("exintro", "1")
            ?.addQueryParameter("explaintext", "1")
            ?.addQueryParameter("exsentences", DESCRIPTION_SENTENCES.toString())
            ?.addQueryParameter("redirects", "1")
            ?.addQueryParameter("titles", title)
            ?.addQueryParameter("format", "json")
            ?.addQueryParameter("formatversion", "2")
            ?.build()
            ?: return null

        return try {
            client.newCall(get(url.toString())).await().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                json.decodeFromString<WikipediaExtractResponse>(body)
                    .query
                    ?.pages
                    .orEmpty()
                    .firstNotNullOfOrNull { cleanWikipediaExtract(it.extract) }
            }
        } catch (e: IOException) {
            ThorLog.d(TAG) { "Wikipedia extract request failed for $title" }
            null
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unparseable Wikipedia extract for $title", e)
            null
        }
    }

    /**
     * Rejects unresolved labels.
     *
     * When the label service cannot find an English label it returns the bare
     * entity id, and "Q12345" is worse than showing nothing at all.
     */
    private fun isName(value: String): Boolean =
        value.isNotBlank() && !value.matches(ENTITY_ID_PATTERN)

    /**
     * Whether a search hit is plausibly a game.
     *
     * Wikidata descriptions are short and consistent enough for this ("1995 video
     * game", "video game series"), and it is far cheaper than asking SPARQL to
     * verify `instance of: video game` for every candidate. Missing descriptions
     * are allowed through rather than discarded, since the title match still has
     * to clear the aggregator's confidence floor.
     */
    private fun looksLikeAGame(description: String?): Boolean {
        val text = description?.lowercase() ?: return true
        if (GAME_HINTS.any { it in text }) return true
        // Anything explicitly something else is excluded; films and albums share
        // a great many titles with games.
        return NON_GAME_HINTS.none { it in text }
    }

    /**
     * Wikidata asks API clients to identify themselves, and answers anonymous
     * high-volume traffic with 403. A scrape across a large library is exactly
     * the traffic that gets throttled, so the header is not optional.
     */
    private fun get(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/sparql-results+json,application/json")
        .build()

    /** One entity's claims, accumulated across SPARQL rows. */
    private data class ClaimRow(
        val developer: String? = null,
        val publisher: String? = null,
        val wikipediaTitle: String? = null,
        val releaseDate: String? = null,
        val releaseYear: Int? = null,
        val genres: List<String> = emptyList(),
    )

    // ------------------------------------------------------------------ DTOs

    @Serializable
    private data class WdSearchResponse(val search: List<WdEntity>? = null)

    @Serializable
    private data class WdEntity(
        val id: String,
        val label: String? = null,
        val description: String? = null,
    )

    @Serializable
    private data class WdSparqlResponse(val results: WdResults? = null)

    @Serializable
    private data class WdResults(val bindings: List<WdBinding>? = null)

    @Serializable
    private data class WdBinding(
        val item: WdValue? = null,
        val article: WdValue? = null,
        @SerialName("devLabel") val devLabel: WdValue? = null,
        @SerialName("pubLabel") val pubLabel: WdValue? = null,
        val date: WdValue? = null,
        @SerialName("genreLabel") val genreLabel: WdValue? = null,
    )

    @Serializable
    private data class WdValue(val value: String? = null)

    @Serializable
    private data class WikipediaExtractResponse(val query: WikipediaQuery? = null)

    @Serializable
    private data class WikipediaQuery(val pages: List<WikipediaPage>? = null)

    @Serializable
    private data class WikipediaPage(val extract: String? = null)

    companion object {
        const val ID = "wikidata"

        private const val TAG = "Wikidata"
        private const val API_URL = "https://www.wikidata.org/w/api.php"
        private const val SPARQL_URL = "https://query.wikidata.org/sparql"
        private const val WIKIPEDIA_API_URL = "https://en.wikipedia.org/w/api.php"
        /**
         * Sentences asked of the MediaWiki extract.
         *
         * Four. Eight overflowed the panel outright and five still did once the
         * media strip went to sixteen by nine, which is taller than the shape it
         * replaced. The constraint here is the panel rather than the article: a
         * synopsis the panel has to truncate is worse than a shorter one that
         * ends where it meant to.
         */
        private const val DESCRIPTION_SENTENCES = 3

        /**
         * Identifies THOR to Wikidata, per their API etiquette.
         *
         * A contact URL is expected; the project page stands in for one.
         */
        private const val USER_AGENT =
            "Loki-Launcher/1.0 (https://github.com/thor-launcher) Android"

        // Wikidata property ids.
        private const val PROP_DEVELOPER = "P178"
        private const val PROP_PUBLISHER = "P123"
        private const val PROP_PUBLICATION_DATE = "P577"
        private const val PROP_GENRE = "P136"

        private const val SEARCH_LIMIT = 8

        /** Candidates taken through to the claims request. */
        private const val MAX_CANDIDATES = 3
        private const val MAX_GENRES = 3

        private val ENTITY_ID_PATTERN = Regex("^Q\\d+$")

        private val GAME_HINTS = setOf("video game", "videogame", "game")

        private val NON_GAME_HINTS = setOf(
            "film", "movie", "album", "song", "novel", "book", "manga", "anime",
            "television", "tv series", "band", "musician", "athlete", "politician",
        )
    }
}

/** Rejects empty and boilerplate-only extracts before persisting them. */
internal fun cleanWikipediaExtract(extract: String?): String? = extract
    ?.trim()
    ?.replace(Regex("\\s+"), " ")
    ?.takeIf { it.length >= 40 }
