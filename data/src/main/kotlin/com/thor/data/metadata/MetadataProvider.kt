package com.thor.data.metadata

import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata

/** What the aggregator asks a provider to look up. */
data class MetadataQuery(
    val title: String,
    /** Normalised title, articles and decorations stripped. */
    val sortTitle: String,
    val platformId: String,
    /**
     * Every provider-specific platform id the platform declares, keyed by
     * provider.
     *
     * A map rather than one value. It was a single field back when ScreenScraper
     * was the only provider that filtered by system, and it carried
     * ScreenScraper's id regardless of who read it — so IGDB filtered its
     * catalogue by a ScreenScraper system number and matched nothing at all.
     */
    val providerPlatformIds: Map<String, String> = emptyMap(),
    val fileName: String,
    val fileSizeBytes: Long,
    /**
     * The file's own fingerprints, when it was small enough to read.
     *
     * The difference between asking a provider "is there a game called
     * something like this?" and telling it "this is the file I have".
     * ScreenScraper's database is keyed by these, so a match against one is
     * exact — right game, right region, right revision — where a name match has
     * to infer all three from a filename somebody else chose.
     *
     * Null for anything too large to hash; see `RomHasher`. Providers that
     * cannot use them ignore them, and every provider must still work without.
     */
    val crc32: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
    val releaseYearHint: Int? = null,
    val region: String? = null,
)

/** What a provider found. Every field is optional. */
data class MetadataCandidate(
    val providerId: String,
    /** The provider's own id for the matched game, kept for follow-up calls. */
    val remoteId: String,
    val matchedTitle: String,
    /** 0..1 confidence that this is the same game as the query. */
    val confidence: Float,
    val metadata: GameMetadata = GameMetadata.EMPTY,
    val artwork: ArtworkSet = ArtworkSet.EMPTY,
)

/**
 * A source of game metadata.
 *
 * Providers are intentionally narrow: they search and they return candidates.
 * Ranking, merging and conflict resolution across providers all happen in
 * [MetadataAggregator], so adding a provider never requires touching merge
 * logic, and a provider that is down or unconfigured simply contributes
 * nothing.
 */
interface MetadataProvider {

    /** Stable id, matching the keys used in `MetadataSettings`. */
    val id: String

    val displayName: String

    /**
     * True when this provider has everything it needs to issue a request.
     *
     * Asked of the provider rather than inferred from the settings, because
     * "configured" means different things per service: an API key for
     * SteamGridDB and RAWG, but a developer pair for ScreenScraper. Deciding
     * centrally is how a fully configured provider ends up reported as unusable.
     */
    suspend fun isConfigured(): Boolean

    /** True when the provider only supplies images, not textual metadata. */
    val artworkOnly: Boolean get() = false

    /**
     * Searches for [query].
     *
     * Implementations must not throw: network and parsing failures are reported
     * as an empty list so one unavailable provider cannot fail a whole scrape.
     */
    suspend fun search(query: MetadataQuery): List<MetadataCandidate>

    /**
     * Issues one cheap request to confirm the credentials work.
     *
     * A scrape swallows provider failures by design, which makes a wrong API key
     * indistinguishable from a game that simply has no artwork. This is the
     * explicit check that tells the difference, so the settings screen can say
     * which providers are actually connected.
     */
    suspend fun checkConnection(): ProviderStatus
}

/** Result of a [MetadataProvider.checkConnection] probe. */
sealed interface ProviderStatus {
    /** Never checked in this session. */
    data object Unknown : ProviderStatus

    /** Credentials are missing, so there is nothing to check. */
    data object NotConfigured : ProviderStatus

    data object Connected : ProviderStatus

    /** The service answered, and rejected the credentials. */
    data object InvalidCredentials : ProviderStatus

    /** The service could not be reached. */
    data class Unreachable(val detail: String) : ProviderStatus

    /** The service answered with something unexpected. */
    data class Error(val detail: String) : ProviderStatus
}

/**
 * Scores how well a result title matches what was searched for.
 *
 * Uses token overlap plus a length penalty rather than raw edit distance:
 * scraper results routinely differ by subtitle or punctuation ("Pokemon Red"
 * vs "Pokémon Red Version"), and token overlap handles that far better, while
 * the length penalty stops a short query matching a much longer title.
 */
object TitleMatcher {

    fun confidence(query: String, candidate: String): Float {
        val a = tokenize(query)
        val b = tokenize(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f

        val overlap = a.intersect(b).size.toFloat()
        val union = a.union(b).size.toFloat()
        val jaccard = overlap / union

        // Length similarity *scales* the overlap rather than being added to it.
        // Added, it would hand two completely unrelated titles a third of a
        // point purely for having the same word count.
        val lengthRatio = minOf(a.size, b.size).toFloat() / maxOf(a.size, b.size).toFloat()
        val base = jaccard * (0.7f + 0.3f * lengthRatio)

        // A candidate that starts with the full query is very likely correct
        // even if it carries extra subtitle tokens.
        val prefixBonus = if (candidate.lowercase().startsWith(query.lowercase())) 0.15f else 0f

        return (base + prefixBonus).coerceIn(0f, 1f)
    }

    private fun tokenize(value: String): Set<String> = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() && it !in STOP_WORDS }
        .toSet()

    private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "version", "edition")
}
