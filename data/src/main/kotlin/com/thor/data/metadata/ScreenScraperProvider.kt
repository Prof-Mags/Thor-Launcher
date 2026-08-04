package com.thor.data.metadata

import com.thor.core.common.log.ThorLog
import com.thor.data.BuildConfig
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata
import com.thor.data.network.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ScreenScraper — the deepest catalogue for retro systems.
 *
 * Unlike the other providers this one matches on the *file*, not the title:
 * `jeuInfos.php` is given the ROM's name, size and system id, and ScreenScraper
 * resolves it against its own dump database. That makes it far more accurate
 * than a title search for exactly the platforms THOR targets, where filenames
 * follow No-Intro and Redump conventions.
 *
 * It also means a result needs no fuzzy confidence: a hit is the right game.
 * The aggregator's floor is satisfied by reporting full confidence, and a miss
 * simply returns nothing.
 */
@Singleton
class ScreenScraperProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "ScreenScraper"

    /**
     * ScreenScraper needs only the application's own developer key to answer.
     *
     * A user account is optional — it raises the daily quota and unlocks the
     * higher-resolution media, but anonymous requests against a registered
     * developer key work. That is why a launcher never asks the user for
     * anything to make ScreenScraper function.
     */
    override suspend fun isConfigured(): Boolean = hasDeveloperKey

    override suspend fun checkConnection(): ProviderStatus {
        if (!hasDeveloperKey) {
            return ProviderStatus.Error("This build has no ScreenScraper developer key")
        }
        val config = settings.metadata.first()

        // `ssinfraInfos` is the cheapest endpoint that still validates the key.
        val url = "$BASE_URL/ssinfraInfos.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("output", "json")
            ?.addQueryParameter("softname", SOFT_NAME)
            ?.addQueryParameter("devid", DEV_ID)
            ?.addQueryParameter("devpassword", DEV_PASSWORD)
            ?.apply {
                config.screenScraperUser.takeIf(String::isNotBlank)
                    ?.let { addQueryParameter("ssid", it) }
                config.screenScraperPassword.takeIf(String::isNotBlank)
                    ?.let { addQueryParameter("sspassword", it) }
            }
            ?.build()
            ?: return ProviderStatus.Error("Malformed URL")

        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                when {
                    response.isSuccessful -> ProviderStatus.Connected
                    response.code == 401 || response.code == 403 ->
                        ProviderStatus.InvalidCredentials

                    response.code == 429 -> ProviderStatus.Error("Daily quota reached")
                    else -> ProviderStatus.Error("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            ProviderStatus.Unreachable(e.message ?: "No connection")
        }
    }

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        if (!hasDeveloperKey) return emptyList()
        val config = settings.metadata.first()

        val systemId = query.providerPlatformIds["screenscraper"] ?: run {
            ThorLog.d(TAG) { "No ScreenScraper system id for ${query.platformId}" }
            return emptyList()
        }

        val url = "$BASE_URL/jeuInfos.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("softname", SOFT_NAME)
            ?.addQueryParameter("output", "json")
            ?.addQueryParameter("systemeid", systemId)
            ?.addQueryParameter("romnom", query.fileName)
            // Size narrows an ambiguous filename to a specific dump.
            ?.addQueryParameter("romtaille", query.fileSizeBytes.toString())
            ?.addQueryParameter("devid", DEV_ID)
            ?.addQueryParameter("devpassword", DEV_PASSWORD)
            ?.apply {
                // The user's own account is optional; it lifts the quota.
                config.screenScraperUser.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("ssid", it)
                }
                config.screenScraperPassword.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("sspassword", it)
                }
            }
            ?.build()
            ?: return emptyList()

        return try {
            val body = get(url.toString()) ?: return emptyList()
            val game = json.decodeFromString<SsEnvelope>(body).response?.jeu
                ?: return emptyList()
            listOf(game.toCandidate(query))
        } catch (e: IOException) {
            ThorLog.w(TAG, "Request failed for '${query.fileName}'", e)
            emptyList()
        } catch (e: IllegalStateException) {
            // A quota rejection comes back as plain text rather than JSON, so a
            // parse failure here is expected rather than exceptional.
            ThorLog.w(TAG, "Unexpected response for '${query.fileName}'", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Malformed response for '${query.fileName}'", e)
            emptyList()
        }
    }

    private suspend fun get(url: String): String? {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).await().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                // 404 is ScreenScraper's "no such game", which is routine.
                response.code == 404 -> null
                response.code == 401 || response.code == 403 -> {
                    ThorLog.w(TAG, "ScreenScraper rejected the credentials (${response.code})")
                    null
                }
                response.code == 429 -> {
                    ThorLog.w(TAG, "ScreenScraper quota exhausted for today")
                    null
                }
                else -> {
                    ThorLog.w(TAG, "ScreenScraper returned ${response.code}")
                    null
                }
            }
        }
    }

    private fun SsGame.toCandidate(query: MetadataQuery): MetadataCandidate {
        val region = query.region
        val year = dates.pick(region)?.take(4)?.toIntOrNull()

        return MetadataCandidate(
            providerId = ID,
            remoteId = id?.toString().orEmpty(),
            matchedTitle = names.pick(region) ?: query.title,
            // A filename match is exact, so there is nothing to be uncertain
            // about; the aggregator's confidence floor exists for title guesses.
            confidence = 1f,
            metadata = GameMetadata(
                description = synopsis.pickLanguage(),
                genres = genres.orEmpty().mapNotNull { it.names.pickLanguage() }.distinct(),
                developer = developer?.text,
                publisher = publisher?.text,
                releaseDate = dates.pick(region),
                releaseYear = year,
                // ScreenScraper rates out of 20.
                rating = rating?.text?.toIntOrNull()?.let { (it * 5).coerceIn(0, 100) },
                players = players?.text,
                region = region,
                providerSources = buildMap {
                    if (synopsis.pickLanguage() != null) put(GameMetadata.FIELD_DESCRIPTION, ID)
                    if (!genres.isNullOrEmpty()) put(GameMetadata.FIELD_GENRES, ID)
                    if (developer != null) put(GameMetadata.FIELD_DEVELOPER, ID)
                    if (publisher != null) put(GameMetadata.FIELD_PUBLISHER, ID)
                    if (dates.pick(region) != null) put(GameMetadata.FIELD_RELEASE_DATE, ID)
                    if (rating != null) put(GameMetadata.FIELD_RATING, ID)
                },
            ),
            artwork = medias.orEmpty().toArtworkSet(region),
        )
    }

    /**
     * Maps ScreenScraper's flat media list onto THOR's artwork slots.
     *
     * The `type` strings are ScreenScraper's own vocabulary. Region-matched
     * media is preferred, then world, then anything — a Japanese box scan is
     * better than no box scan.
     */
    private fun List<SsMedia>.toArtworkSet(region: String?): ArtworkSet {
        fun pick(vararg types: String): String? = types.firstNotNullOfOrNull { type ->
            val matching = filter { it.type == type }
            matching.firstOrNull { it.region.equals(region, ignoreCase = true) }?.url
                ?: matching.firstOrNull { it.region in WORLD_REGIONS }?.url
                ?: matching.firstOrNull()?.url
        }

        return ArtworkSet(
            boxArt = pick("box-2D", "box-2D-side", "box-texture"),
            hero = pick("fanart", "screenmarquee", "ss"),
            logo = pick("wheel", "wheel-hd", "screenmarquee"),
            // ScreenScraper has no square icon type; `wheel-carbon-steel` and
            // the support (cartridge) scans are the closest to 1:1, and a
            // cartridge photo reads far better in a square cell than a cropped
            // box scan does.
            icon = pick("support-2D", "wheel-carbon-steel"),
            screenshots = pickWide(region),
            videoUri = pick("video-normalized", "video"),
        )
    }

    /**
     * Every wide image the entry has, best first, up to the model's cap.
     *
     * [toArtworkSet]'s `pick` answers "the one best media of this type", which is
     * right for a box scan and wrong for screenshots — it returned a single shot
     * however many the entry carried, so the panel had one image to cycle and the
     * strip looked broken. This keeps going instead: the region's own media
     * first, then world, then whatever is left, deduplicated because the same
     * shot is commonly registered under several regions.
     *
     * Ordered by type as well: `fanart` is the wide promotional still, `ss` the
     * in-game capture, and `sstitle` a title screen, which is the least
     * interesting of the three and so goes last rather than displacing anything.
     */
    private fun List<SsMedia>.pickWide(region: String?): List<String> {
        val ranked = WIDE_TYPES.flatMap { type ->
            val matching = filter { it.type == type }
            val regional = matching.filter { it.region.equals(region, ignoreCase = true) }
            val world = matching.filter { it.region in WORLD_REGIONS }
            (regional + world + matching).mapNotNull(SsMedia::url)
        }
        return ranked.distinct().take(ArtworkSet.MAX_SCREENSHOTS)
    }

    // ------------------------------------------------------------------ DTOs

    @Serializable
    private data class SsEnvelope(val response: SsResponse? = null)

    @Serializable
    private data class SsResponse(val jeu: SsGame? = null)

    @Serializable
    private data class SsGame(
        val id: Int? = null,
        val noms: List<SsRegionText>? = null,
        val synopsis: List<SsLanguageText>? = null,
        val editeur: SsText? = null,
        val developpeur: SsText? = null,
        val dates: List<SsRegionText>? = null,
        val genres: List<SsGenre>? = null,
        val note: SsText? = null,
        val joueurs: SsText? = null,
        val medias: List<SsMedia>? = null,
    ) {
        val names: List<SsRegionText>? get() = noms
        val publisher: SsText? get() = editeur
        val developer: SsText? get() = developpeur
        val rating: SsText? get() = note
        val players: SsText? get() = joueurs
    }

    @Serializable
    private data class SsGenre(val noms: List<SsLanguageText>? = null) {
        val names: List<SsLanguageText>? get() = noms
    }

    /**
     * A region-tagged value.
     *
     * `text` is declared as a raw [JsonElement] because ScreenScraper returns it
     * as a string in most places and as a number in a few (ratings, player
     * counts), and a strict `String` would fail the whole payload on those.
     */
    @Serializable
    private data class SsRegionText(
        val region: String? = null,
        val text: JsonElement? = null,
    ) {
        val value: String? get() = text?.asText()
    }

    @Serializable
    private data class SsLanguageText(
        val langue: String? = null,
        val text: JsonElement? = null,
    ) {
        val language: String? get() = langue
        val value: String? get() = text?.asText()
    }

    @Serializable
    private data class SsText(@SerialName("text") val raw: JsonElement? = null) {
        val text: String? get() = raw?.asText()
    }

    @Serializable
    private data class SsMedia(
        val type: String? = null,
        val url: String? = null,
        val region: String? = null,
    )

    private companion object {
        const val ID = "screenscraper"
        private const val TAG = "ScreenScraper"
        private const val BASE_URL = "https://api.screenscraper.fr/api2"

        /**
         * Landscape media only, best first.
         *
         * Every one of these is wider than it is tall, which is the whole
         * requirement: the strip is a sixteen-by-nine frame, and an image that
         * arrives portrait either letterboxes into slivers or crops to a
         * meaningless middle. `fanart` is key art and leads because it is drawn
         * rather than captured; `ss` and `sstitle` are frames from the game,
         * which fill the strip when there is no key art to be had.
         *
         * Box and flyer scans were briefly here and are the reason the panel
         * filled with mismatched shapes — they are portrait, whatever else they
         * are. They still reach the grid cell through the `boxArt` slot, which
         * is the frame shaped for them.
         */
        private val WIDE_TYPES = listOf("fanart", "screenmarquee", "ss", "sstitle")

        /** Identifies this client to ScreenScraper in its request logs. */
        private const val SOFT_NAME = "Loki"

        /**
         * The application's registered developer key, compiled in at build time.
         *
         * This is what identifies THOR to ScreenScraper. It belongs to the
         * application, not to the person using it, which is why no launcher asks
         * the user for it — see the `thor.screenscraper.*` gradle properties.
         */
        private val DEV_ID: String = BuildConfig.SCREENSCRAPER_DEV_ID
        private val DEV_PASSWORD: String = BuildConfig.SCREENSCRAPER_DEV_PASSWORD

        private val hasDeveloperKey: Boolean
            get() = DEV_ID.isNotBlank() && DEV_PASSWORD.isNotBlank()

        private val WORLD_REGIONS = setOf("wor", "world", "us", "eu")

        /** Preferred description languages, best first. */
        private val LANGUAGES = listOf("en", "us", "wor")

        /** Region-preference order when no query region is known. */
        private fun List<SsRegionText>?.pick(region: String?): String? {
            val list = this ?: return null
            val wanted = region?.lowercase()?.take(2)
            return list.firstOrNull { it.region?.lowercase() == wanted }?.value
                ?: list.firstOrNull { it.region in WORLD_REGIONS }?.value
                ?: list.firstOrNull()?.value
        }

        private fun List<SsLanguageText>?.pickLanguage(): String? {
            val list = this ?: return null
            return LANGUAGES.firstNotNullOfOrNull { language ->
                list.firstOrNull { it.language?.lowercase() == language }?.value
            } ?: list.firstOrNull()?.value
        }

        /** Reads a JSON value that may be a string or a number. */
        private fun JsonElement.asText(): String? = (this as? JsonPrimitive)
            ?.let { primitive -> primitive.content.takeIf { it.isNotBlank() } }
    }
}
