package com.thor.data.metadata

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ArtworkSet
import com.thor.data.network.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SteamGridDB — the launcher's primary source of grid art, heroes and logos.
 *
 * SteamGridDB indexes artwork rather than games, so it contributes no textual
 * metadata; [artworkOnly] tells the aggregator not to rank it against providers
 * that do.
 */
@Singleton
class SteamGridDbProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "SteamGridDB"
    override val artworkOnly: Boolean = true

    override suspend fun isConfigured(): Boolean =
        !settings.metadata.first().apiKeys[ID].isNullOrBlank()

    override suspend fun checkConnection(): ProviderStatus {
        val apiKey = settings.metadata.first().apiKeys[ID]?.takeIf(String::isNotBlank)
            ?: return ProviderStatus.NotConfigured

        // A one-word search is the cheapest authenticated call available.
        val request = Request.Builder()
            .url("$BASE_URL/search/autocomplete/mario")
            .header("Authorization", "Bearer $apiKey")
            .build()

        return try {
            client.newCall(request).await().use { response ->
                when {
                    response.isSuccessful -> ProviderStatus.Connected
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

        return try {
            val games = searchGames(query.title, apiKey)
            games.take(MAX_CANDIDATES).map { game ->
                MetadataCandidate(
                    providerId = ID,
                    remoteId = game.id.toString(),
                    matchedTitle = game.name,
                    confidence = TitleMatcher.confidence(query.title, game.name),
                    artwork = fetchArtwork(game.id, apiKey),
                )
            }
        } catch (e: IOException) {
            ThorLog.w(TAG, "Search failed for '${query.title}'", e)
            emptyList()
        } catch (e: IllegalStateException) {
            ThorLog.w(TAG, "Unexpected response for '${query.title}'", e)
            emptyList()
        }
    }

    private suspend fun searchGames(title: String, apiKey: String): List<SgdbGame> {
        val encoded = java.net.URLEncoder.encode(title, "UTF-8")
        val body = get("$BASE_URL/search/autocomplete/$encoded", apiKey) ?: return emptyList()
        val response = json.decodeFromString<SgdbResponse<List<SgdbGame>>>(body)
        return response.data.orEmpty()
    }

    /**
     * Pulls the three artwork classes in one pass.
     *
     * Each is a separate endpoint; a failure on any one leaves that slot empty
     * rather than losing the whole set, because a game with a grid but no logo
     * is still far better than a game with nothing.
     */
    private suspend fun fetchArtwork(gameId: Int, apiKey: String): ArtworkSet {
        /*
         * Heroes are this provider's wide artwork, and there is usually more
         * than one.
         *
         * Only the first was ever read, into the `hero` slot, and the rest were
         * dropped on the floor — so SteamGridDB contributed nothing at all to the
         * screenshot strip despite holding exactly the kind of image it wants:
         * banner-shaped, drawn for the game rather than captured from it, and
         * the closest thing any provider offers to the panel's ratio. A game with
         * no ScreenScraper entry therefore had a single image or none.
         */
        val heroes = imageUrls("$BASE_URL/heroes/game/$gameId", apiKey)

        return ArtworkSet(
            boxArt = firstImageUrl("$BASE_URL/grids/game/$gameId?dimensions=600x900", apiKey),
            hero = heroes.firstOrNull(),
            logo = firstImageUrl("$BASE_URL/logos/game/$gameId", apiKey),
            // The one already used as the backdrop is not offered again in the
            // strip, which would show the same picture twice.
            screenshots = heroes.drop(1),
        /*
         * Square *box art*, from the grids endpoint — not the icons endpoint.
         *
         * SteamGridDB serves grids in 1:1 as well as 2:3, and the 1:1 grids are
         * proper cover art laid out for a square frame. The icons endpoint returns
         * application-style glyphs instead: technically 1:1, but they look nothing
         * like box art and made the grid look like a folder of file icons.
         *
         * Dimensions are pinned so the response cannot come back 2:3, which would
         * then have to be letterboxed in the cell — the thing that made the grid
         * look full of wide artwork in the first place.
         */
            icon = firstImageUrl(
                "$BASE_URL/grids/game/$gameId?dimensions=$SQUARE_GRID_DIMENSIONS",
                apiKey,
            ),
        )
    }

    private suspend fun firstImageUrl(url: String, apiKey: String): String? =
        imageUrls(url, apiKey).firstOrNull()

    /** Every image an endpoint offers, in SteamGridDB's own order — best first. */
    private suspend fun imageUrls(url: String, apiKey: String): List<String> = try {
        get(url, apiKey)?.let { body ->
            json.decodeFromString<SgdbResponse<List<SgdbImage>>>(body)
                .data
                ?.mapNotNull(SgdbImage::url)
                .orEmpty()
        }.orEmpty()
    } catch (e: IOException) {
        ThorLog.d(TAG) { "Artwork request failed: $url" }
        emptyList()
    } catch (e: IllegalStateException) {
        emptyList()
    }

    private suspend fun get(url: String, apiKey: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()

        return client.newCall(request).await().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                // 404 simply means "no artwork of this kind", which is normal.
                response.code == 404 -> null
                response.code == 401 || response.code == 403 -> {
                    ThorLog.w(TAG, "SteamGridDB rejected the API key (${response.code})")
                    null
                }

                else -> {
                    ThorLog.w(TAG, "SteamGridDB returned ${response.code} for $url")
                    null
                }
            }
        }
    }

    @Serializable
    private data class SgdbResponse<T>(
        val success: Boolean = false,
        val data: T? = null,
    )

    @Serializable
    private data class SgdbGame(
        val id: Int,
        val name: String,
        @SerialName("release_date") val releaseDate: Long? = null,
    )

    @Serializable
    private data class SgdbImage(
        val id: Int,
        val url: String,
        val thumb: String? = null,
    )

    companion object {
        const val ID = "steamgriddb"
        private const val TAG = "SteamGridDB"
        private const val BASE_URL = "https://www.steamgriddb.com/api/v2"
        private const val MAX_CANDIDATES = 3

        /**
         * Square icon sizes, largest first.
         *
         * The grid renders these at cell size, so the largest available is worth
         * asking for; the request falls back to an unfiltered one if none of
         * these exist for a title.
         */
        /**
         * The square sizes the `grids` endpoint offers, largest first.
         *
         * These are cover art composed for a 1:1 frame, which is what a square
         * cell wants. The `icons` endpoint's sizes (512/256/128/64) are
         * deliberately not used: those are application glyphs, not box art.
         */
        private const val SQUARE_GRID_DIMENSIONS = "1024x1024,512x512"
    }
}
