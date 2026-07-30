package com.thor.data.metadata

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata
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
 * RAWG — descriptions, genres, developers, publishers, ratings and screenshots.
 *
 * RAWG's catalogue is modern-leaning, so it is ranked below ScreenScraper for
 * retro platforms in the default provider priority, but it is the only free
 * source with good coverage of PC and recent console titles.
 */
@Singleton
class RawgProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "RAWG"

    override suspend fun isConfigured(): Boolean =
        !settings.metadata.first().apiKeys[ID].isNullOrBlank()

    override suspend fun checkConnection(): ProviderStatus {
        val apiKey = settings.metadata.first().apiKeys[ID]?.takeIf(String::isNotBlank)
            ?: return ProviderStatus.NotConfigured

        val url = "$BASE_URL/games".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("key", apiKey)
            ?.addQueryParameter("page_size", "1")
            ?.build()
            ?: return ProviderStatus.Error("Malformed URL")

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                when {
                    response.isSuccessful -> ProviderStatus.Connected
                    // RAWG answers 401 for a bad key.
                    response.code == 401 || response.code == 403 ->
                        ProviderStatus.InvalidCredentials

                    else -> ProviderStatus.Error("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            ProviderStatus.Unreachable(e.message ?: "No connection")
        }
    }

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        val apiKey = settings.metadata.first().apiKeys[ID]?.takeIf(String::isNotBlank)
            ?: return emptyList()

        val url = "$BASE_URL/games".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("key", apiKey)
            ?.addQueryParameter("search", query.title)
            ?.addQueryParameter("page_size", MAX_RESULTS.toString())
            ?.build()
            ?: return emptyList()

        return try {
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    ThorLog.w(TAG, "RAWG returned ${response.code} for '${query.title}'")
                    return emptyList()
                }
                response.body?.string()
            } ?: return emptyList()

            json.decodeFromString<RawgSearchResponse>(body)
                .results
                .orEmpty()
                .map { it.toCandidate(query) }
                .sortedByDescending(MetadataCandidate::confidence)
        } catch (e: IOException) {
            ThorLog.w(TAG, "Search failed for '${query.title}'", e)
            emptyList()
        } catch (e: IllegalStateException) {
            ThorLog.w(TAG, "Unexpected response for '${query.title}'", e)
            emptyList()
        }
    }

    private fun RawgGame.toCandidate(query: MetadataQuery): MetadataCandidate {
        val year = released?.take(4)?.toIntOrNull()
        return MetadataCandidate(
            providerId = ID,
            remoteId = id.toString(),
            matchedTitle = name,
            confidence = TitleMatcher.confidence(query.title, name),
            metadata = GameMetadata(
                description = descriptionRaw?.takeIf(String::isNotBlank),
                genres = genres.orEmpty().mapNotNull { it.name },
                developer = developers.orEmpty().firstNotNullOfOrNull { it.name },
                publisher = publishers.orEmpty().firstNotNullOfOrNull { it.name },
                releaseDate = released,
                releaseYear = year,
                // RAWG reports a 0..5 rating; the launcher stores 0..100.
                rating = rating?.let { (it * 20).toInt().coerceIn(0, 100) },
                completionMinutes = playtime?.let { it * 60 },
                providerSources = buildMap {
                    if (!descriptionRaw.isNullOrBlank()) put(GameMetadata.FIELD_DESCRIPTION, ID)
                    if (!genres.isNullOrEmpty()) put(GameMetadata.FIELD_GENRES, ID)
                    if (!developers.isNullOrEmpty()) put(GameMetadata.FIELD_DEVELOPER, ID)
                    if (!publishers.isNullOrEmpty()) put(GameMetadata.FIELD_PUBLISHER, ID)
                    if (released != null) put(GameMetadata.FIELD_RELEASE_DATE, ID)
                    if (rating != null) put(GameMetadata.FIELD_RATING, ID)
                },
            ),
            artwork = ArtworkSet(
                hero = backgroundImage,
                screenshots = shortScreenshots.orEmpty().mapNotNull { it.image },
            ),
        )
    }

    @Serializable
    private data class RawgSearchResponse(val results: List<RawgGame>? = null)

    @Serializable
    private data class RawgGame(
        val id: Int,
        val name: String,
        val released: String? = null,
        @SerialName("background_image") val backgroundImage: String? = null,
        val rating: Float? = null,
        /** Median play time, in hours. */
        val playtime: Int? = null,
        @SerialName("description_raw") val descriptionRaw: String? = null,
        val genres: List<RawgNamed>? = null,
        val developers: List<RawgNamed>? = null,
        val publishers: List<RawgNamed>? = null,
        @SerialName("short_screenshots") val shortScreenshots: List<RawgScreenshot>? = null,
    )

    @Serializable
    private data class RawgNamed(val id: Int? = null, val name: String? = null)

    @Serializable
    private data class RawgScreenshot(val id: Int? = null, val image: String? = null)

    companion object {
        const val ID = "rawg"
        private const val TAG = "RAWG"
        private const val BASE_URL = "https://api.rawg.io/api"
        private const val MAX_RESULTS = 5
    }
}
