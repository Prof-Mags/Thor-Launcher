package com.thor.data.media

import android.util.Xml
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaType
import com.thor.core.model.ReleaseName
import com.thor.core.model.StreamSource
import com.thor.core.model.TorznabIndexer
import com.thor.data.network.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Torrent search, built into the launcher.
 *
 * Speaks Torznab directly — the indexer API that Jackett, Prowlarr and NZBHydra
 * all expose, and the closest thing this space has to a standard. That means
 * searching, parsing, ranking and resolving all happen inside THOR: there is no
 * addon runtime, no second app in the stream path, and nothing to install
 * alongside. The user supplies an indexer endpoint and its key, exactly as they
 * supply ScreenScraper's credentials, and everything after that is native.
 *
 * THOR ships no indexers of its own and no list of them. That is the same
 * position it takes on game metadata: the launcher knows the protocol, the user
 * decides who to ask.
 *
 * Torznab is XML rather than JSON — an inheritance from Newznab, which inherited
 * it from RSS — so this parses with the platform's pull parser instead of the
 * serialization used everywhere else in this module.
 */
@Singleton
class TorznabProvider @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) : StreamSourceProvider {

    override val id: String = "torznab"
    override val displayName: String = "Torrent search"

    override suspend fun isConfigured(): Boolean = indexers().isNotEmpty()

    private suspend fun indexers(): List<TorznabIndexer> =
        settings.media.first().indexers.filter { it.isUsable }

    override suspend fun find(query: SourceQuery): ProviderResult = supervisorScope {
        val configured = indexers()
        if (configured.isEmpty()) return@supervisorScope ProviderResult()

        val results = configured
            .map { indexer -> async { searchBounded(indexer, query) } }
            .awaitAll()

        ProviderResult(
            sources = results
                .flatMap { it.sources }
                // The same release is carried by many indexers. Keyed on the
                // hash, because names differ between them while the torrent is
                // one file.
                .distinctBy { it.infoHash?.lowercase() ?: it.id },
            outcomes = results.flatMap { it.outcomes },
        )
    }

    /** A dead indexer must not make the rest of the configured indexers disappear. */
    private suspend fun searchBounded(
        indexer: TorznabIndexer,
        query: SourceQuery,
    ): ProviderResult {
        val name = indexer.name.ifBlank { "Indexer" }
        return try {
            val found = withTimeoutOrNull(INDEXER_TIMEOUT_MS) { search(indexer, query) }
                ?: return ProviderResult(
                    outcomes = listOf(ProviderOutcome(name, 0, "did not answer in time")),
                ).also { ThorLog.w(TAG, "$name: did not answer in time") }

            ProviderResult(
                sources = found,
                outcomes = listOf(
                    ProviderOutcome(
                        provider = name,
                        found = found.size,
                        note = "has nothing for this title".takeIf { found.isEmpty() },
                    ),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ThorLog.w(TAG, "$name: search failed", error)
            ProviderResult(outcomes = listOf(ProviderOutcome(name, 0, error.shortReason())))
        }
    }

    /**
     * The query endpoint, tolerant of the URL people actually paste.
     *
     * Jackett and Prowlarr both show a "Torznab Feed" address, and their copy
     * buttons hand over a form that already ends in `/api`, sometimes with a
     * query string attached. The field's subtitle asks for it without, which is a
     * request rather than a guarantee — and appending unconditionally turned a
     * perfectly good endpoint into `…/api/api`, whose only symptom was an indexer
     * that silently found nothing. Both forms work now.
     */
    private fun endpoint(indexer: TorznabIndexer) = indexer.url
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .removeSuffix("/api")
        .trimEnd('/')
        .plus("/api")
        .toHttpUrlOrNull()

    /**
     * Asks one indexer whether it actually works, and says what it answered.
     *
     * The settings page could previously only report whether the *fields* were
     * filled in — it said "Ready" for a mistyped host, a revoked key, a Jackett
     * that was not running, and an indexer whose categories return nothing. Every
     * one of those presents identically much later, as a film with no sources,
     * on a screen that cannot say why.
     *
     * A real query, against the same endpoint a search would use, so what it
     * proves is what matters. `t=caps` is Torznab's own capabilities call and is
     * the one request every implementation answers without a search term.
     */
    suspend fun test(indexer: TorznabIndexer): String {
        if (indexer.url.isBlank()) return "Needs a URL"
        if (indexer.apiKey.isBlank()) return "Needs an API key"

        val url = endpoint(indexer)
            ?.newBuilder()
            ?.addQueryParameter("apikey", indexer.apiKey)
            ?.addQueryParameter("t", "caps")
            ?.build()
            ?: return "That URL is not valid"

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 || response.code == 403 -> "Rejected the API key"
                    !response.isSuccessful -> "HTTP ${response.code}"

                    /*
                     * Torznab reports its own failures inside a 200.
                     *
                     * An unknown key or a disabled indexer comes back as
                     * `<error code="100" description="…"/>` with a perfectly
                     * successful status line, so the status code alone would call
                     * a refusal a success.
                     */
                    "<error" in body -> ERROR_DESCRIPTION.find(body)
                        ?.groupValues
                        ?.get(1)
                        ?.let { "Refused: $it" }
                        ?: "Refused the request"

                    "<caps" in body || "<categories" in body -> "Answering"
                    else -> "Answered, but not with Torznab"
                }
            }
        } catch (e: IOException) {
            "Could not reach it: ${e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()}"
        }
    }

    /**
     * One indexer, asked the way it expects to be asked.
     *
     * Torznab has a dedicated query shape per media type — `t=movie` takes an
     * IMDb id directly, `t=tvsearch` takes season and episode numbers — and
     * using them rather than a free-text search is the difference between the
     * right title and everything sharing a word with it. Indexers that do not
     * implement them fall back to `t=search`, which every one of them has.
     */
    private suspend fun search(
        indexer: TorznabIndexer,
        query: SourceQuery,
    ): List<StreamSource> {
        val typed = request(indexer, query, freeText = false)
        return typed.ifEmpty { request(indexer, query, freeText = true) }
    }

    private suspend fun request(
        indexer: TorznabIndexer,
        query: SourceQuery,
        freeText: Boolean,
    ): List<StreamSource> {
        val url = endpoint(indexer)
            ?.newBuilder()
            ?.apply {
                addQueryParameter("apikey", indexer.apiKey)
                addQueryParameter("extended", "1")
                addQueryParameter("limit", "$RESULT_LIMIT")

                if (freeText) {
                    addQueryParameter("t", "search")
                    addQueryParameter("q", freeTextTerm(query))
                } else {
                    when (query.type) {
                        MediaType.MOVIE -> {
                            addQueryParameter("t", "movie")
                            addQueryParameter("imdbid", query.imdbId.removePrefix("tt"))
                            addQueryParameter("cat", MOVIE_CATEGORIES)
                        }

                        MediaType.SERIES -> {
                            addQueryParameter("t", "tvsearch")
                            addQueryParameter("imdbid", query.imdbId.removePrefix("tt"))
                            query.season?.let { addQueryParameter("season", "$it") }
                            query.episode?.let { addQueryParameter("ep", "$it") }
                            addQueryParameter("cat", SERIES_CATEGORIES)
                        }
                    }
                }
            }
            ?.build()
            ?: return emptyList()

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "${indexer.name}: HTTP ${response.code}")
                    return emptyList()
                }
                parse(response.body?.string().orEmpty(), indexer)
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "${indexer.name}: request failed", e)
            emptyList()
        }
    }

    /**
     * The fallback query.
     *
     * An IMDb id means nothing to a free-text search, so this is the title-shaped
     * term an indexer without typed queries can actually match. Episodes carry
     * their SxxEyy code because that is how every release names them.
     */
    private fun freeTextTerm(query: SourceQuery): String {
        val episode = if (query.season != null && query.episode != null) {
            " S%02dE%02d".format(query.season, query.episode)
        } else {
            ""
        }
        return (query.title.ifBlank { query.imdbId } + episode).trim()
    }

    /**
     * Reads a Torznab feed.
     *
     * Every field this needs beyond the title lives in `<torznab:attr>` elements
     * rather than in the RSS item itself, and which of them an indexer emits
     * varies. Anything absent is left null and the ranking copes — a source with
     * no seeder count still plays.
     */
    private fun parse(xml: String, indexer: TorznabIndexer): List<StreamSource> {
        if (xml.isBlank()) return emptyList()

        return try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(xml))
            }

            val sources = mutableListOf<StreamSource>()
            var item: MutableItem? = null

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> item = MutableItem()
                        "title" -> item?.let { it.title = parser.nextText().trim() }
                        "size" -> item?.let { it.sizeBytes = parser.nextText().toLongOrNull() }
                        "enclosure" -> item?.readEnclosure(parser)
                        "link" -> item?.let { current ->
                            val link = parser.nextText().trim()
                            if (link.startsWith("magnet:")) current.magnetUri = link
                        }

                        "torznab:attr", "newznab:attr" -> item?.readAttribute(parser)
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "item") {
                        item?.toSource(indexer)?.let(sources::add)
                        item = null
                    }
                }
            }
            sources
        } catch (e: XmlPullParserException) {
            ThorLog.w(TAG, "${indexer.name}: malformed feed", e)
            emptyList()
        } catch (e: IOException) {
            ThorLog.w(TAG, "${indexer.name}: could not read feed", e)
            emptyList()
        }
    }

    private class MutableItem {
        var title: String = ""
        var magnetUri: String? = null
        var infoHash: String? = null
        var sizeBytes: Long? = null
        var seeders: Int? = null

        fun readEnclosure(parser: XmlPullParser) {
            val url = parser.getAttributeValue(null, "url") ?: return
            if (url.startsWith("magnet:")) magnetUri = url
        }

        fun readAttribute(parser: XmlPullParser) {
            val name = parser.getAttributeValue(null, "name") ?: return
            val value = parser.getAttributeValue(null, "value") ?: return
            when (name) {
                "infohash" -> infoHash = value.lowercase()
                "magneturl" -> magnetUri = value
                "seeders" -> seeders = value.toIntOrNull()
                "size" -> sizeBytes = sizeBytes ?: value.toLongOrNull()
            }
        }

        /**
         * A hash is recoverable from a magnet when the indexer did not name one,
         * and it is worth recovering: it is the key the debrid cache check uses,
         * and a source without it can never be reported as instantly playable.
         */
        private fun resolvedHash(): String? =
            infoHash ?: magnetUri?.let { HASH_PATTERN.find(it)?.groupValues?.get(1)?.lowercase() }

        fun toSource(indexer: TorznabIndexer): StreamSource? {
            if (title.isBlank()) return null
            val hash = resolvedHash()
            val magnet = magnetUri ?: hash?.let { "magnet:?xt=urn:btih:$it" } ?: return null

            return StreamSource(
                id = hash ?: magnet,
                providerId = indexer.url,
                providerName = indexer.name.ifBlank { "Indexer" },
                title = title,
                quality = ReleaseName.parse(title),
                infoHash = hash,
                magnetUri = magnet,
                sizeBytes = sizeBytes,
                seeders = seeders,
                // Filled in by the repository, once, for the whole result set.
                cached = CacheStatus.UNKNOWN,
            )
        }
    }

    private companion object {
        const val TAG = "Media"
        const val RESULT_LIMIT = 100
        const val INDEXER_TIMEOUT_MS = 12_000L

        /** Torznab's standard category numbers. */
        const val MOVIE_CATEGORIES = "2000"
        const val SERIES_CATEGORIES = "5000"

        val HASH_PATTERN = Regex("btih:([a-fA-F0-9]{40})")

        /** Torznab reports refusals as `<error code="…" description="…"/>`. */
        val ERROR_DESCRIPTION = Regex("""description="([^"]*)"""")
    }
}
