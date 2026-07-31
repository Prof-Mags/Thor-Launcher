package com.thor.data.media

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaType
import com.thor.core.model.ReleaseName
import com.thor.core.model.StreamSource
import com.thor.data.network.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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

/** Somewhere sources come from. */
interface StreamSourceProvider {
    val id: String
    val displayName: String
    suspend fun isConfigured(): Boolean
    suspend fun find(query: SourceQuery): List<StreamSource>
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
        settings.media.first().addonUrls
            .map { url -> url.trim().removeSuffix("/") }
            .filter { url -> url.isNotBlank() }

    override suspend fun find(query: SourceQuery): List<StreamSource> = coroutineScope {
        val addons = addonUrls()
        if (addons.isEmpty()) return@coroutineScope emptyList()

        addons
            .map { base -> async { fetch(base, query) } }
            .awaitAll()
            .flatten()
            // The same release is often carried by several addons. Keyed on the
            // hash rather than the name, because names differ by indexer while
            // the torrent is the same file.
            .distinctBy { it.infoHash?.lowercase() ?: it.id }
    }

    private suspend fun fetch(baseUrl: String, query: SourceQuery): List<StreamSource> {
        val type = if (query.type == MediaType.MOVIE) "movie" else "series"
        val url = "$baseUrl/stream/$type/${query.streamId}.json"

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "Addon ${response.code} for $url")
                    return emptyList()
                }
                val body = response.body?.string() ?: return emptyList()
                json.decodeFromString<AddonStreamResponse>(body)
                    .streams
                    .orEmpty()
                    .mapNotNull { it.toSource(baseUrl) }
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "Addon request failed: $url", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Unexpected addon response: $url", e)
            emptyList()
        }
    }

    /** The addon's own name, for the settings list. */
    suspend fun manifestName(baseUrl: String): String? {
        val url = "${baseUrl.trim().removeSuffix("/")}/manifest.json"
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
        val infoHash: String? = null,
        val fileIdx: Int? = null,
        /** Set when the addon has already resolved a playable link. */
        val url: String? = null,
        val behaviorHints: AddonBehaviorHints? = null,
    ) {
        fun toSource(providerUrl: String): StreamSource? {
            // Something to play is the only hard requirement. A stream with
            // neither a hash nor a URL is an entry in a list that cannot be
            // opened, which is worse than not listing it.
            if (infoHash.isNullOrBlank() && url.isNullOrBlank()) return null

            val releaseName = title?.lineSequence()?.firstOrNull()?.trim()
                ?: name?.trim()
                ?: return null

            return StreamSource(
                id = infoHash?.lowercase() ?: url.orEmpty(),
                providerId = providerUrl,
                providerName = name?.substringBefore('\n')?.trim().orEmpty().ifBlank { "Addon" },
                title = releaseName,
                // Parsed from the release name and from the addon's extra lines
                // together: seeders and size usually live on the later lines.
                quality = ReleaseName.parse(listOfNotNull(name, title).joinToString(" ")),
                infoHash = infoHash?.lowercase(),
                magnetUri = infoHash?.let { magnetFor(it, releaseName) },
                directUrl = url,
                fileIndex = fileIdx,
                sizeBytes = behaviorHints?.videoSize ?: title?.let(::parseSize),
                seeders = title?.let(::parseSeeders),
                // Addons do not know what the user's debrid account holds; the
                // repository fills this in once, for the whole list.
                cached = CacheStatus.UNKNOWN,
            )
        }
    }

    @Serializable
    private data class AddonBehaviorHints(val videoSize: Long? = null)

    private companion object {
        const val TAG = "Media"

        fun magnetFor(infoHash: String, name: String): String = buildString {
            append("magnet:?xt=urn:btih:").append(infoHash)
            append("&dn=").append(name.replace(' ', '.'))
            TRACKERS.forEach { append("&tr=").append(it) }
        }

        /**
         * Public trackers appended to every magnet.
         *
         * Not for peer discovery by THOR, which never joins a swarm — the debrid
         * service does the fetching, and a bare hash with no trackers gives it
         * markedly worse odds of finding an uncached torrent.
         */
        val TRACKERS = listOf(
            "udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce",
            "udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce",
            "udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce",
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
