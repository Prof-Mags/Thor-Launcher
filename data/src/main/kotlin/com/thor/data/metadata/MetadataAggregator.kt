package com.thor.data.metadata

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata
import com.thor.core.model.MetadataSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries every enabled provider and merges the results into one record.
 *
 * The merge is field-by-field rather than "best provider wins wholesale",
 * because no single provider is best at everything: SteamGridDB has the
 * artwork, RAWG has the prose, ScreenScraper has the retro coverage. For each
 * field the winner is the highest-priority provider that actually supplied a
 * value, with two hard rules on top:
 *
 *  - a candidate below [MIN_CONFIDENCE] is discarded entirely, so a bad title
 *    match cannot contribute even one field;
 *  - fields the user has edited are never overwritten.
 */
@Singleton
class MetadataAggregator @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards MetadataProvider>,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Scrapes metadata for one game.
     *
     * @param existing the current record, whose locked fields are preserved
     * @return the merged result, or `existing` when nothing usable was found
     */
    suspend fun scrape(query: MetadataQuery, existing: GameMetadata): GameMetadata =
        withContext(ioDispatcher) {
            val config = settings.metadata.first()
            val active = usableProviders(config)

            if (active.isEmpty()) {
                ThorLog.d(TAG) { "No configured providers; skipping '${query.title}'" }
                return@withContext existing
            }

            val candidates = queryProviders(active, query, config)
                .filter { it.confidence >= MIN_CONFIDENCE }

            if (candidates.isEmpty()) return@withContext existing

            merge(existing, candidates, config)
        }

    /** True when at least one enabled provider is fully configured. */
    suspend fun hasUsableProvider(): Boolean =
        usableProviders(settings.metadata.first()).isNotEmpty()

    /**
     * Whether any usable provider can supply text, not just artwork.
     *
     * Worth reporting separately because the two failure modes look identical from
     * the grid and completely different in cause: with only SteamGridDB
     * configured, a scrape downloads artwork for the whole library and fills in no
     * developer, publisher, description or genre at all — which reads as a broken
     * scraper rather than as a provider that never offered those fields.
     */
    suspend fun hasTextualProvider(): Boolean =
        usableProviders(settings.metadata.first()).any { !it.artworkOnly }

    /**
     * Probes every provider, concurrently, and reports what each one said.
     *
     * @return provider id to its status
     */
    suspend fun checkConnections(): Map<String, ProviderStatus> =
        withContext(ioDispatcher) {
            coroutineScope {
                providers
                    .map { provider ->
                        async {
                            provider.id to runCatching { provider.checkConnection() }
                                .getOrElse { ProviderStatus.Error(it.message ?: "Failed") }
                        }
                    }
                    .awaitAll()
                    .toMap()
            }
        }

    /**
     * Enabled providers that can actually issue a request.
     *
     * Each provider is asked rather than having its requirements inferred from
     * the settings map — ScreenScraper is configured by a developer pair, not an
     * API key, and a central check would report it unusable.
     */
    private suspend fun usableProviders(config: MetadataSettings): List<MetadataProvider> =
        providers
            .filter { it.id in config.enabledProviders }
            .filter { it.isConfigured() }

    /**
     * Runs providers concurrently, bounded by the user's request cap so a large
     * library scrape does not open dozens of sockets at once.
     */
    private suspend fun queryProviders(
        active: List<MetadataProvider>,
        query: MetadataQuery,
        config: MetadataSettings,
    ): List<MetadataCandidate> = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        active
            .map { provider ->
                async {
                    gate.withPermit {
                        runCatching { provider.search(query) }
                            .onFailure { ThorLog.w(TAG, "Provider ${provider.id} failed", it) }
                            .getOrDefault(emptyList())
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    /**
     * Field-wise merge.
     *
     * Candidates are sorted so the most trustworthy is consulted first, then
     * each field takes the first non-empty value found.
     */
    private fun merge(
        existing: GameMetadata,
        candidates: List<MetadataCandidate>,
        config: MetadataSettings,
    ): GameMetadata {
        val ranked = candidates.sortedWith(
            compareBy<MetadataCandidate> { config.providerPriority[it.providerId] ?: Int.MAX_VALUE }
                .thenByDescending { it.confidence },
        )

        // Artwork-only providers must not win textual fields even when they
        // rank highly; they still compete for the artwork slots below.
        val artworkOnlyIds = providers.filter(MetadataProvider::artworkOnly).map(MetadataProvider::id).toSet()
        val textual = ranked.filterNot { it.providerId in artworkOnlyIds }

        val locked = existing.lockedFields
        fun <T> pick(field: String, current: T?, selector: (MetadataCandidate) -> T?): T? =
            if (field in locked) current else current ?: textual.firstNotNullOfOrNull(selector)

        val sources = existing.providerSources.toMutableMap()
        textual.forEach { candidate ->
            candidate.metadata.providerSources.forEach { (field, providerId) ->
                if (field !in locked) sources.putIfAbsent(field, providerId)
            }
        }

        val mergedArtwork = mergeArtwork(existing.artwork, ranked, locked)
        if (mergedArtwork != existing.artwork) {
            sources.putIfAbsent(
                GameMetadata.FIELD_ARTWORK,
                ranked.firstOrNull { !it.artwork.isEmpty }?.providerId ?: "",
            )
        }

        return existing.copy(
            description = pick(GameMetadata.FIELD_DESCRIPTION, existing.description) {
                it.metadata.description
            },
            genres = if (GameMetadata.FIELD_GENRES in locked || existing.genres.isNotEmpty()) {
                existing.genres
            } else {
                textual.firstOrNull { it.metadata.genres.isNotEmpty() }?.metadata?.genres.orEmpty()
            },
            developer = pick(GameMetadata.FIELD_DEVELOPER, existing.developer) {
                it.metadata.developer
            },
            publisher = pick(GameMetadata.FIELD_PUBLISHER, existing.publisher) {
                it.metadata.publisher
            },
            releaseDate = pick(GameMetadata.FIELD_RELEASE_DATE, existing.releaseDate) {
                it.metadata.releaseDate
            },
            releaseYear = pick(GameMetadata.FIELD_RELEASE_DATE, existing.releaseYear) {
                it.metadata.releaseYear
            },
            rating = pick(GameMetadata.FIELD_RATING, existing.rating) { it.metadata.rating },
            players = pick(GameMetadata.FIELD_PLAYERS, existing.players) { it.metadata.players },
            completionMinutes = existing.completionMinutes
                ?: textual.firstNotNullOfOrNull { it.metadata.completionMinutes },
            artwork = mergedArtwork,
            providerSources = sources,
            lastScrapedEpochMs = System.currentTimeMillis(),
        )
    }

    /**
     * Artwork merges per slot rather than per provider, so a game can take its
     * hero from SteamGridDB and its screenshots from RAWG.
     *
     * One cover and at most [ArtworkSet.MAX_SCREENSHOTS] screenshots. Providers
     * will return dozens; beyond a handful nobody looks at them, and each one is
     * a download, a cache entry and another step in the information screen's
     * slideshow — so the set is capped here, at ingestion, rather than trimmed
     * every time it is drawn.
     */
    private fun mergeArtwork(
        existing: ArtworkSet,
        ranked: List<MetadataCandidate>,
        locked: Set<String>,
    ): ArtworkSet {
        if (GameMetadata.FIELD_ARTWORK in locked) return existing
        return ArtworkSet(
            boxArt = existing.boxArt ?: ranked.firstNotNullOfOrNull { it.artwork.boxArt },
            hero = existing.hero ?: ranked.firstNotNullOfOrNull { it.artwork.hero },
            logo = existing.logo ?: ranked.firstNotNullOfOrNull { it.artwork.logo },
            icon = existing.icon ?: ranked.firstNotNullOfOrNull { it.artwork.icon },
            screenshots = existing.screenshots
                .ifEmpty {
                    // Pooled across providers rather than taken from the first
                    // that has any: one provider often returns a single shot, and
                    // the slideshow is better with four from two sources than one
                    // from the highest-ranked.
                    ranked.flatMap { it.artwork.screenshots }.distinct()
                }
                .take(ArtworkSet.MAX_SCREENSHOTS),
            videoUri = existing.videoUri ?: ranked.firstNotNullOfOrNull { it.artwork.videoUri },
            dominantArgb = existing.dominantArgb,
        )
    }

    private companion object {
        const val TAG = "Metadata"

        /**
         * Below this, a title match is more likely to be a different game than
         * the right one — attaching wrong artwork is worse than attaching none.
         */
        const val MIN_CONFIDENCE = 0.45f

        /**
         * Concurrent provider requests per game.
         *
         * Fixed rather than user-tunable: there are only ever three providers to
         * ask, and every one of them rate limits, so the useful range was one
         * value wide.
         */
        const val MAX_CONCURRENT_REQUESTS = 3
    }
}
