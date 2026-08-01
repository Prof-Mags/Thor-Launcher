package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaType
import com.thor.core.model.ReleaseName
import com.thor.core.model.StreamSource
import com.thor.core.model.StremioAddons
import com.thor.data.network.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a stream source is asked for.
 *
 * Keyed by IMDb id rather than TMDb's, because that is the identifier every
 * source provider indexes by. A title without one cannot be looked up at all,
 * which the panel says rather than showing an empty list.
 */
data class SourceQuery(
    val imdbId: String,
    val type: MediaType,
    /** The title, for indexers whose free-text search cannot take an id. */
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
) {
    /**
     * The addon protocol's id form: `tt0903747` for a film, `tt0903747:2:7` for
     * an episode.
     */
    val streamId: String
        get() = if (season != null && episode != null) "$imdbId:$season:$episode" else imdbId
}

/**
 * What one place THOR asked had to say.
 *
 * Carried alongside the sources rather than logged, because "no sources found"
 * is the least informative sentence this section can produce and it was the only
 * one available. A search that asked two addons and an indexer can fail in three
 * unrelated ways — a URL that 404s, a key that is refused, a host that never
 * answers — and every one of them looked identical from the panel. Each provider
 * now says what happened to *it*, and the panel shows them.
 *
 * @param note null when there is nothing to explain: a provider that answered
 *   normally needs no commentary, and inventing some would bury the one that
 *   does.
 */
data class ProviderOutcome(
    val provider: String,
    val found: Int,
    val note: String? = null,
)

/** What a provider returned, and what it had to say about it. */
data class ProviderResult(
    val sources: List<StreamSource> = emptyList(),
    val outcomes: List<ProviderOutcome> = emptyList(),
)

/**
 * What asking an addon whether it works produced.
 *
 * @param name the addon's own name, when its manifest answered
 * @param note what to tell the user, or null when it is simply working
 */
data class AddonCheck(val name: String? = null, val note: String? = null)

/** One line of a throwable, short enough to sit under a source list. */
internal fun Throwable.shortReason(): String =
    message?.lineSequence()?.firstOrNull()?.trim()?.take(80)?.ifBlank { null }
        ?: this::class.simpleName
        ?: "failed"

/** Somewhere sources come from. */
interface StreamSourceProvider {
    val id: String
    val displayName: String
    suspend fun isConfigured(): Boolean
    suspend fun find(query: SourceQuery): ProviderResult
}

/**
 * Stream sources from Stremio-protocol addons.
 *
 * THOR ships no sources and indexes nothing itself. An addon is an HTTP endpoint
 * the user configures — it answers `/manifest.json` describing itself and
 * `/stream/{type}/{id}.json` with candidates — and the protocol is open,
 * documented and has many independent implementations. That is deliberately the
 * same arrangement as the game scrapers: the launcher knows how to *talk* to a
 * source and takes no view on which one the user runs.
 *
 * Addons are queried concurrently and their results concatenated in
 * configuration order, so an earlier addon wins a tie against a later one.
 */
@Singleton
class StremioAddonProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : StreamSourceProvider {

    override val id: String = "stremio-addon"
    override val displayName: String = "Addons"

    override suspend fun isConfigured(): Boolean = addonUrls().isNotEmpty()

    private suspend fun addonUrls(): List<String> =
        settings.media.first().addons
            .filter { addon -> addon.isUsable }
            // Normalised again on read rather than trusted from storage: settings
            // can be restored from a backup written by an older build, and one
            // stray `/manifest.json` makes every request answer nothing at all.
            .map { addon -> StremioAddons.normalise(addon.url) }
            .filter { url -> url.isNotBlank() }

    /** The addon's own name, for the settings list. Null when it does not answer. */
    suspend fun identify(url: String): String? = manifestName(StremioAddons.normalise(url))

    /**
     * Whether this addon will actually serve streams, and what to say if not.
     *
     * **Asks the stream endpoint, not just the manifest**, and the difference is
     * not academic. The check used to read `/manifest.json` alone, so an addon
     * whose manifest was momentarily unreachable reported nothing at all — the
     * row simply kept saying "Check this addon", which is what it says before it
     * has ever been pressed. Indistinguishable from having done nothing.
     *
     * Torrentio in particular sits behind a CDN that returns 522 for its manifest
     * while serving streams perfectly well, so the one endpoint that was being
     * consulted is the one that fails independently of whether the addon works.
     * The stream endpoint is what the section depends on, so that is what decides
     * the verdict; the manifest is asked only for the name.
     */
    suspend fun check(url: String): AddonCheck {
        val base = StremioAddons.normalise(url)
        if (base.isBlank()) return AddonCheck(note = "That URL is not valid")

        val name = manifestName(base)

        // A well-known id every addon indexes, so the answer is about the addon
        // rather than about whether it happens to carry one obscure film.
        val probe = fetch(base, SourceQuery(imdbId = PROBE_IMDB_ID, type = MediaType.MOVIE))
        val outcome = probe.outcomes.firstOrNull()

        return AddonCheck(
            name = name,
            note = when {
                probe.sources.isNotEmpty() && name != null -> null
                probe.sources.isNotEmpty() ->
                    "Serving streams. Its manifest is not answering, which only " +
                        "affects the name shown here."

                outcome?.note != null -> outcome.note
                else -> "Answered, but served no streams"
            },
        )
    }

    override suspend fun find(query: SourceQuery): ProviderResult = supervisorScope {
        val addons = addonUrls()
        if (addons.isEmpty()) return@supervisorScope ProviderResult()

        val results = addons.map { base -> async { fetchBounded(base, query) } }.awaitAll()

        ProviderResult(
            sources = results
                .flatMap { it.sources }
                // The same release is often carried by several addons. Keyed on
                // the hash rather than the name, because names differ by indexer
                // while the torrent is the same file.
                .distinctBy { it.infoHash?.lowercase() ?: it.id },
            outcomes = results.flatMap { it.outcomes },
        )
    }

    /** One unreachable addon must not discard completed results from its siblings. */
    private suspend fun fetchBounded(baseUrl: String, query: SourceQuery): ProviderResult = try {
        withTimeoutOrNull(ADDON_TIMEOUT_MS) { fetch(baseUrl, query) }
            ?: ProviderResult(
                outcomes = listOf(outcome(baseUrl, 0, "did not answer in time")),
            ).also { ThorLog.w(TAG, "Addon did not answer in time: $baseUrl") }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        ThorLog.w(TAG, "Addon failed: $baseUrl", error)
        ProviderResult(outcomes = listOf(outcome(baseUrl, 0, error.shortReason())))
    }

    private suspend fun fetch(baseUrl: String, query: SourceQuery): ProviderResult {
        val type = if (query.type == MediaType.MOVIE) "movie" else "series"
        val url = "$baseUrl/stream/$type/${query.streamId}.json"

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "Addon ${response.code} for $url")
                    /*
                     * A 404 here is nearly always the URL rather than the title.
                     * The commonest way to get one is pasting the addon's
                     * *configure* page instead of its manifest, which looks
                     * identical in the settings list and fails for every title.
                     */
                    val hint = if (response.code == 404) {
                        "HTTP 404 — check the addon URL"
                    } else {
                        "HTTP ${response.code}"
                    }
                    return ProviderResult(outcomes = listOf(outcome(baseUrl, 0, hint)))
                }

                val body = response.body?.string().orEmpty()
                val streams = json.decodeFromString<AddonStreamResponse>(body).streams.orEmpty()
                val sources = streams.mapNotNull { it.toSource(baseUrl) }

                ProviderResult(
                    sources = sources,
                    outcomes = listOf(
                        outcome(
                            baseUrl,
                            sources.size,
                            // A note only when the numbers disagree or there is
                            // nothing: an addon that answered normally is not
                            // news, and saying so would bury the one that failed.
                            when {
                                streams.isEmpty() -> "has nothing for ${query.streamId}"
                                sources.isEmpty() ->
                                    "returned ${streams.size} unusable streams"

                                else -> null
                            },
                        ),
                    ),
                )
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "Addon request failed: $url", e)
            ProviderResult(outcomes = listOf(outcome(baseUrl, 0, e.shortReason())))
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unexpected addon response: $url", e)
            ProviderResult(outcomes = listOf(outcome(baseUrl, 0, "sent something unreadable")))
        }
    }

    /** Named by host, because the whole URL can carry a debrid key in its path. */
    private fun outcome(baseUrl: String, found: Int, note: String?) = ProviderOutcome(
        provider = runCatching { baseUrl.toHttpUrlOrNull()?.host }.getOrNull() ?: "Addon",
        found = found,
        note = note,
    )

    private suspend fun manifestName(baseUrl: String): String? {
        val url = StremioAddons.manifestUrl(baseUrl)
        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) return null
                json.decodeFromString<AddonManifest>(response.body?.string().orEmpty()).name
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // -------------------------------------------------------------------- DTOs

    @Serializable
    private data class AddonManifest(val name: String? = null)

    @Serializable
    private data class AddonStreamResponse(val streams: List<AddonStream>? = null)

    @Serializable
    private data class AddonStream(
        /** Usually the addon's own label, e.g. "Torrentio 1080p". */
        val name: String? = null,
        /** The release name and details, newline separated. */
        val title: String? = null,
        /** Modern addons use this in place of the older `title` field. */
        val description: String? = null,
        val infoHash: String? = null,
        val fileIdx: Int? = null,
        /** Set when the addon has already resolved a playable link. */
        val url: String? = null,
        /** Trackers/DHT nodes accompanying [infoHash], in Stremio's source format. */
        val sources: List<String>? = null,
        val behaviorHints: AddonBehaviorHints? = null,
    ) {
        fun toSource(providerUrl: String): StreamSource? {
            // Something to play is the only hard requirement. A stream with
            // neither a hash nor a URL is an entry in a list that cannot be
            // opened, which is worse than not listing it.
            val suppliedUrl = url?.trim().orEmpty()
            val directUrl = suppliedUrl.takeIf { candidate ->
                candidate.toHttpUrlOrNull()?.scheme in HTTP_SCHEMES
            }
            val magnetUrl = suppliedUrl.takeIf { it.startsWith("magnet:", ignoreCase = true) }
            val hash = infoHash?.trim()?.takeIf(String::isNotBlank)?.lowercase()

            // `url` means an HTTP stream only when it is actually HTTP(S). Addons
            // are also allowed to put a magnet there; handing that to ExoPlayer
            // made it wait forever for bytes that no HTTP server can deliver.
            if (hash == null && magnetUrl == null && directUrl == null) return null

            val releaseName = description?.lineSequence()?.firstOrNull()?.trim()
                ?: title?.lineSequence()?.firstOrNull()?.trim()
                ?: name?.trim()
                ?: return null

            return StreamSource(
                id = hash ?: magnetUrl ?: directUrl.orEmpty(),
                providerId = providerUrl,
                providerName = name?.substringBefore('\n')?.trim().orEmpty().ifBlank { "Addon" },
                title = releaseName,
                // Parsed from the release name and from the addon's extra lines
                // together: seeders and size usually live on the later lines.
                quality = ReleaseName.parse(listOfNotNull(name, title, description).joinToString(" ")),
                infoHash = hash,
                magnetUri = magnetUrl ?: hash?.let { magnetFor(it, releaseName, sources.orEmpty()) },
                directUrl = directUrl,
                requestHeaders = behaviorHints?.proxyHeaders?.request.orEmpty(),
                fileIndex = fileIdx,
                sizeBytes = behaviorHints?.videoSize ?: description?.let(::parseSize) ?: title?.let(::parseSize),
                seeders = description?.let(::parseSeeders) ?: title?.let(::parseSeeders),
                // Addons do not know what the user's debrid account holds; the
                // repository fills this in once, for the whole list.
                cached = CacheStatus.UNKNOWN,
            )
        }
    }

    @Serializable
    private data class AddonBehaviorHints(
        val videoSize: Long? = null,
        val proxyHeaders: AddonProxyHeaders? = null,
    )

    @Serializable
    private data class AddonProxyHeaders(val request: Map<String, String>? = null)

    private companion object {
        const val TAG = "Media"
        const val ADDON_TIMEOUT_MS = 12_000L

        /**
         * The Matrix, for probing an addon.
         *
         * A title every stream addon carries, so a check that comes back empty
         * says something about the addon rather than about the film.
         */
        const val PROBE_IMDB_ID = "tt0133093"

        private val HTTP_SCHEMES = setOf("http", "https")

        fun magnetFor(infoHash: String, name: String, sources: List<String>): String = buildString {
            append("magnet:?xt=urn:btih:").append(infoHash)
            append("&dn=").append(name.urlEncode())
            (sources.mapNotNull(::trackerUrl) + TRACKERS)
                .distinct()
                .forEach { tracker -> append("&tr=").append(tracker.urlEncode()) }
        }

        private fun trackerUrl(source: String): String? = source
            .removePrefix("tracker:")
            .takeIf { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://") }

        private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

        /**
         * Public trackers appended to every magnet.
         *
         * Not for peer discovery by THOR, which never joins a swarm — the debrid
         * service does the fetching, and a bare hash with no trackers gives it
         * markedly worse odds of finding an uncached torrent.
         */
        val TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
        )

        /** "💾 12.4 GB" and similar, which is how addons report size in text. */
        val SIZE_PATTERN = Regex("""([\d.]+)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
        val SEEDERS_PATTERN = Regex("""(?:👤|seeders?[:\s])\s*(\d+)""", RegexOption.IGNORE_CASE)

        fun parseSize(text: String): Long? {
            val match = SIZE_PATTERN.find(text) ?: return null
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            val unit = match.groupValues[2].uppercase()
            val multiplier = if (unit == "GB") 1L shl 30 else 1L shl 20
            return (value * multiplier).toLong()
        }

        fun parseSeeders(text: String): Int? =
            SEEDERS_PATTERN.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }
}
