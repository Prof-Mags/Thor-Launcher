package com.thor.data.metadata

import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata
import com.thor.core.model.TimeToBeat
import com.thor.data.network.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IGDB — screenshots and artwork at a size the panel can actually use.
 *
 * Chosen because it separates its images by *class*, which is the thing the
 * panel actually needs. `screenshots` and `artworks` are landscape by
 * definition — captures and promotional stills — while covers live in their own
 * field and never leak into them. Everywhere else the classes are mixed and the
 * aspect is whatever the uploader had, which is how the panel came to be fed
 * portrait box scans and ultra-wide banners as though they were screenshots.
 *
 * The size token is a cap, not a shape: `t_1080p` scales to 1080 on the
 * constrained edge and preserves the aspect it was given. A cover asked for at
 * that size comes back 810 by 1080, so the widescreen guarantee comes from
 * asking for the right *class* of image, not from the size.
 *
 * It is also the only free source with real coverage of both halves of a retro
 * library — the modern games RAWG knows and the twenty-year-old console
 * releases it does not — and its key is self-service, which ScreenScraper's is
 * not.
 *
 * Authentication is Twitch's, because Amazon owns both: a client id and secret
 * are exchanged for a bearer token that lasts about two months. The token is
 * fetched on demand and cached until it expires; there is no refresh flow, and
 * asking for a new one is a single request.
 */
@Singleton
class IgdbProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : MetadataProvider {

    override val id: String = ID
    override val displayName: String = "IGDB"

    private val tokenLock = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    override suspend fun isConfigured(): Boolean {
        val config = settings.metadata.first()
        return config.igdbClientId.isNotBlank() && config.igdbClientSecret.isNotBlank()
    }

    override suspend fun checkConnection(): ProviderStatus {
        if (!isConfigured()) return ProviderStatus.NotConfigured
        return try {
            when (token()) {
                null -> ProviderStatus.InvalidCredentials
                else -> ProviderStatus.Connected
            }
        } catch (e: IOException) {
            ProviderStatus.Unreachable(e.message ?: "No connection")
        }
    }

    override suspend fun search(query: MetadataQuery): List<MetadataCandidate> {
        if (!isConfigured()) return emptyList()
        val bearer = try {
            token() ?: return emptyList()
        } catch (e: IOException) {
            ThorLog.d(TAG) { "Token request failed for '${query.title}'" }
            return emptyList()
        }

        /*
         * IGDB's query language, not query parameters.
         *
         * Fields are named explicitly because the API returns *nothing* by
         * default — an unlisted field is simply absent rather than null — and
         * the dotted paths pull related records in the same request, which is
         * what keeps a lookup to one round trip instead of four.
         *
         * The platform clause is a filter rather than part of the search,
         * because IGDB matches titles across every system it knows: without it
         * a Mega Drive ROM happily matches the PlayStation remake.
         */
        val body = igdbSearchBody(
            title = query.title,
            platformId = query.providerPlatformIds["igdb"],
        )

        return try {
            val response = post("$BASE_URL/games", body, bearer) ?: return emptyList()
            val games = json.decodeFromString<List<IgdbGame>>(response)

            /*
             * How long each of them takes, in one request for all of them.
             *
             * A second endpoint rather than a field on the game, which is IGDB's
             * own shape — and asked in a batch because the alternative is a
             * request per candidate, eight of them, for a figure most of them
             * will not have. A failure here costs the figures and nothing else;
             * the candidates are already built.
             */
            val times = timesToBeat(games.map(IgdbGame::id), bearer)

            games
                .map { it.toCandidate(query, times[it.id]) }
                .filter { it.confidence > 0f }
        } catch (e: IOException) {
            ThorLog.w(TAG, "Request failed for '${query.title}'", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Malformed response for '${query.title}'", e)
            emptyList()
        }
    }

    /**
     * A bearer token, cached until shortly before it expires.
     *
     * The margin matters: a token that expires between the check and the request
     * produces a 401 that looks exactly like a bad client secret, and would send
     * a user to re-enter credentials that were never wrong.
     */
    private suspend fun token(): String? = tokenLock.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiresAtMs }?.let { return it }

        val config = settings.metadata.first()
        val url = TOKEN_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client_id", config.igdbClientId)
            ?.addQueryParameter("client_secret", config.igdbClientSecret)
            ?.addQueryParameter("grant_type", "client_credentials")
            ?.build()
            ?: return null

        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .build()

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                ThorLog.w(TAG, "Token rejected (${response.code})")
                cachedToken = null
                return null
            }
            val payload = response.body?.string() ?: return null
            val token = runCatching { json.decodeFromString<IgdbToken>(payload) }.getOrNull()
                ?: return null
            cachedToken = token.accessToken
            tokenExpiresAtMs = now + (token.expiresIn * 1000L) - TOKEN_MARGIN_MS
            return token.accessToken
        }
    }

    private suspend fun post(url: String, body: String, bearer: String): String? {
        val config = settings.metadata.first()
        val request = Request.Builder()
            .url(url)
            .header("Client-ID", config.igdbClientId)
            .header("Authorization", "Bearer $bearer")
            .post(body.toRequestBody())
            .build()

        return client.newCall(request).await().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 401 || response.code == 403 -> {
                    // Force a fresh token next time: the usual cause is one that
                    // expired early rather than a credential that is wrong.
                    cachedToken = null
                    ThorLog.w(TAG, "IGDB rejected the token (${response.code})")
                    null
                }
                response.code == 429 -> {
                    ThorLog.w(TAG, "IGDB rate limit reached")
                    null
                }
                else -> {
                    ThorLog.w(TAG, "IGDB returned ${response.code}")
                    null
                }
            }
        }
    }

    /**
     * How long each of [gameIds] takes to finish, keyed by game.
     *
     * IGDB's own figures, drawn from play-throughs people submitted — the same
     * question HowLongToBeat answers, from a source this launcher is already
     * credentialled for and allowed to use. Empty when nobody has submitted one,
     * which is the honest answer for most of a retro library and is why the
     * interface has to treat "no data" and "not started" as different things.
     */
    private suspend fun timesToBeat(gameIds: List<Long>, bearer: String): Map<Long, TimeToBeat> {
        if (gameIds.isEmpty()) return emptyMap()

        val body = buildString {
            append("fields game_id,hastily,normally,completely,count;")
            append("where game_id = (${gameIds.joinToString(",")});")
            append("limit ${gameIds.size};")
        }

        return try {
            val response = post("$BASE_URL/game_time_to_beat", body, bearer)
                ?: return emptyMap()
            json.decodeFromString<List<IgdbTimeToBeat>>(response)
                .mapNotNull { entry -> entry.gameId?.let { it to entry.toDomain() } }
                .filterNot { it.second.isEmpty }
                .toMap()
        } catch (e: IOException) {
            ThorLog.w(TAG, "Could not read completion times", e)
            emptyMap()
        } catch (e: IllegalArgumentException) {
            ThorLog.w(TAG, "Malformed completion times", e)
            emptyMap()
        }
    }

    private fun IgdbGame.toCandidate(
        query: MetadataQuery,
        timeToBeat: TimeToBeat?,
    ): MetadataCandidate {
        val companies = involvedCompanies.orEmpty()

        // Captures first, then whichever promotional art is the right shape.
        val wide = (landscapeImages(screenshots.orEmpty()) + landscapeImages(artworks.orEmpty()))
            .distinct()

        return MetadataCandidate(
            providerId = ID,
            remoteId = id.toString(),
            matchedTitle = name.orEmpty(),
            confidence = TitleMatcher.confidence(query.title, name.orEmpty()),
            metadata = GameMetadata(
                // `summary` is the blurb; `storyline` is a longer synopsis that
                // many entries lack, so it stands in only when there is no blurb.
                description = summary?.takeIf(String::isNotBlank)
                    ?: storyline?.takeIf(String::isNotBlank),
                developer = companies.firstOrNull { it.developer == true }?.company?.name,
                publisher = companies.firstOrNull { it.publisher == true }?.company?.name,
                releaseYear = firstReleaseDate?.let(::yearOfEpochSeconds),
                genres = genres.orEmpty().mapNotNull { it.name },
                rating = totalRating?.toInt(),
                timeToBeat = timeToBeat,
                providerSources = buildMap {
                    if (!summary.isNullOrBlank()) put(GameMetadata.FIELD_DESCRIPTION, ID)
                    if (companies.isNotEmpty()) put(GameMetadata.FIELD_DEVELOPER, ID)
                    if (!genres.isNullOrEmpty()) put(GameMetadata.FIELD_GENRES, ID)
                },
            ),
            artwork = ArtworkSet(
                boxArt = cover?.imageId?.let { igdbImage(it, COVER_SIZE) },
                // Artwork ahead of captures for the backdrop, which wants a
                // painted image behind text rather than a busy game frame.
                hero = wide.firstOrNull()?.let { igdbImage(it, WIDE_SIZE) },
                /*
                 * Everything else, filtered to the shape the strip is.
                 *
                 * Screenshots lead because they are the only class IGDB keeps at
                 * a consistent size — 1280 by 720, every one of them. Artworks
                 * are whatever was uploaded and come second, once [landscapeImages]
                 * has discarded the portrait and square ones.
                 *
                 * The first is dropped because it is already the backdrop, and
                 * showing it again in the row in front of the panel is the rule
                 * every provider here follows.
                 */
                screenshots = wide.drop(1).map { igdbImage(it, WIDE_SIZE) },
            ),
        )
    }

    private companion object {
        const val ID = "igdb"
        const val TAG = "IGDB"
        const val BASE_URL = "https://api.igdb.com/v4"
        const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val MAX_CANDIDATES = 8

        /**
         * Release, remake, remaster, expanded edition, port.
         *
         * What a ROM on a shelf can actually be. Mods and DLC are excluded not
         * because they are uninteresting but because they outrank the game in
         * IGDB's own search ordering.
         */
        const val MAIN_GAME_TYPES = "0,8,9,10,11"

        /** Renewed this long before expiry, so a request never races the clock. */
        const val TOKEN_MARGIN_MS = 60_000L

        /**
         * The largest size IGDB serves before the original.
         *
         * A cap on the constrained edge rather than a crop: a 1920 by 1080
         * screenshot arrives whole at 1080p, and nothing is cut to reach it.
         * `t_original` would also do, but is unbounded — a handful of uploads
         * are 4K, and the panel draws these a few hundred pixels wide.
         */
        const val WIDE_SIZE = "t_1080p"
        const val COVER_SIZE = "t_cover_big"
    }
}

/**
 * Builds an image URL.
 *
 * IGDB takes the size as a path segment rather than a query parameter, so the
 * variant is chosen here and the CDN serves it directly — there is no
 * negotiation and no redirect.
 */
internal fun igdbImage(imageId: String, size: String): String =
    "https://images.igdb.com/igdb/image/upload/$size/$imageId.jpg"

/** IGDB reports release dates as Unix seconds. */
internal fun yearOfEpochSeconds(seconds: Long): Int? =
    runCatching {
        java.time.Instant.ofEpochSecond(seconds)
            .atZone(java.time.ZoneOffset.UTC)
            .year
    }.getOrNull()

/**
 * Escapes a title for IGDB's query language.
 *
 * The search term is a quoted string in a body that is otherwise code, so an
 * unescaped quote in a game's name ends the string early and leaves the rest of
 * the title as syntax — which fails the whole request rather than that one game.
 */
internal fun String.escapedForApicalypse(): String =
    replace("\\", "").replace("\"", "")

@Serializable
private data class IgdbToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0L,
)

@Serializable
private data class IgdbGame(
    val id: Long,
    val name: String? = null,
    val summary: String? = null,
    val storyline: String? = null,
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    @SerialName("total_rating") val totalRating: Double? = null,
    val cover: IgdbImage? = null,
    val screenshots: List<IgdbImage>? = null,
    val artworks: List<IgdbImage>? = null,
    val genres: List<IgdbNamed>? = null,
    @SerialName("involved_companies") val involvedCompanies: List<IgdbCompany>? = null,
)

/**
 * IGDB's completion figures, in seconds.
 *
 * Every one is optional independently: a game with few submissions routinely has
 * a normal time and nothing else.
 */
@Serializable
private data class IgdbTimeToBeat(
    @SerialName("game_id") val gameId: Long? = null,
    val hastily: Int? = null,
    val normally: Int? = null,
    val completely: Int? = null,
    val count: Int = 0,
) {
    fun toDomain() = TimeToBeat(
        hastilySeconds = hastily?.takeIf { it > 0 },
        normallySeconds = normally?.takeIf { it > 0 },
        completelySeconds = completely?.takeIf { it > 0 },
        submissions = count,
    )
}

@Serializable
internal data class IgdbImage(
    @SerialName("image_id") val imageId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
private data class IgdbNamed(val name: String? = null)

@Serializable
private data class IgdbCompany(
    val developer: Boolean? = null,
    val publisher: Boolean? = null,
    val company: IgdbNamed? = null,
)

/**
 * The APIcalypse body for a game lookup.
 *
 * A pure function because the last version was assembled inline from two pieces
 * that each carried the `where` keyword, producing `where where platforms = …`.
 * IGDB answers that with a 400 whose body is a JSON object rather than the
 * expected array, so the provider saw a failed request, returned nothing, and
 * did it for every game in the library instantly and without complaint. A string
 * this fiddly needs to be somewhere it can be read back.
 *
 * Fields are named explicitly because the API returns *nothing* by default — an
 * unlisted field is absent rather than null — and the dotted paths pull related
 * records in the same request, which keeps a lookup to one round trip.
 */
internal fun igdbSearchBody(
    title: String,
    platformId: String?,
    limit: Int = IGDB_MAX_CANDIDATES,
    gameTypes: String = IGDB_MAIN_GAME_TYPES,
): String {
    /*
     * The platform clause filters rather than searches, because IGDB matches
     * titles across every system it knows: without it a Mega Drive ROM happily
     * matches the PlayStation remake.
     *
     * The game-type clause is not optional either. IGDB's search ranks mods and
     * add-ons above the game they are built on — "Super Mario Odyssey" came back
     * fourth, behind a mod, a DLC and a joke translation. Type 0 is the release;
     * the rest here are remakes, remasters, expanded editions and ports, which
     * are the game as well. The field is `game_type`: `category` held this until
     * IGDB retired it, and it now returns nothing rather than erroring, so a
     * filter written against it silently matches no games.
     */
    val conditions = buildList {
        platformId?.takeIf(String::isNotBlank)?.let { add("platforms = ($it)") }
        add("game_type = ($gameTypes)")
    }

    return buildString {
        append("search \"${title.escapedForApicalypse()}\";")
        append("fields name,summary,storyline,first_release_date,total_rating,")
        append("cover.image_id,")
        // Dimensions come back with the images so the shapes can be filtered
        // before anything is downloaded; see [landscapeImages].
        append("screenshots.image_id,screenshots.width,screenshots.height,")
        append("artworks.image_id,artworks.width,artworks.height,")
        append("genres.name,involved_companies.developer,involved_companies.publisher,")
        append("involved_companies.company.name;")
        append("where ${conditions.joinToString(" & ")};")
        append("limit $limit;")
    }
}

/** Release, remake, remaster, expanded edition, port — what a ROM can be. */
internal const val IGDB_MAIN_GAME_TYPES = "0,8,9,10,11"

/** Enough that the real game survives IGDB's own ranking of mods above it. */
internal const val IGDB_MAX_CANDIDATES = 8

/**
 * Keeps the images that are actually the shape the panel draws.
 *
 * IGDB reports each image's real dimensions, which turns out to matter a great
 * deal: `screenshots` are uniformly 1280 by 720, but `artworks` are whatever was
 * uploaded — 720 by 1280 portrait, 1200 square, 640 by 399. Feeding those to a
 * widescreen strip is how a row of mismatched shapes appears, and no framing
 * decision downstream can repair an image that is taller than it is wide.
 *
 * Filtered rather than corrected, because there is nothing to correct: a
 * portrait key art is a fine picture that is not this picture's shape. The band
 * is generous — anything from three-by-two to two-by-one — since the frame crops
 * a little either way and being strict would empty the strip for games whose
 * only images are slightly off.
 *
 * Small images are dropped as well. A 640-wide capture upscaled into a panel
 * looks worse than one fewer screenshot.
 */
internal fun landscapeImages(images: List<IgdbImage>): List<String> = images
    .filter { image ->
        val width = image.width ?: return@filter false
        val height = image.height ?: return@filter false
        if (height <= 0 || width < MIN_IMAGE_WIDTH) return@filter false
        val ratio = width.toFloat() / height
        ratio in MIN_WIDE_RATIO..MAX_WIDE_RATIO
    }
    .mapNotNull(IgdbImage::imageId)

/** Three-by-two through two-by-one; sixteen-by-nine sits in the middle at 1.78. */
private const val MIN_WIDE_RATIO = 1.5f
private const val MAX_WIDE_RATIO = 2.1f

/** Below this the panel is upscaling, which reads worse than one fewer image. */
private const val MIN_IMAGE_WIDTH = 900
