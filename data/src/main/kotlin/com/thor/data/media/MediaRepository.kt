package com.thor.data.media

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.core.model.SourceRanking
import com.thor.core.model.StreamSource
import com.thor.core.model.TorznabIndexer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Movies section's data.
 *
 * Sits between three services that know nothing of each other: TMDb describes
 * titles, addons find files for them, and Real-Debrid turns a file into a URL.
 * The joins between them live here — an IMDb id carried from the catalogue to
 * the source query, and a cache check applied across a whole result set at once
 * rather than per source.
 *
 * Caches in memory only, deliberately. Artwork URLs and synopses go stale, the
 * cost of refetching a row is one request, and a media catalogue persisted to
 * disk becomes a second library to migrate and invalidate for no benefit the
 * user would notice.
 */
@Singleton
class MediaRepository @Inject constructor(
    /**
     * Where titles come from, and it needs no credential.
     *
     * The Stremio catalogue protocol, keyed by the same IMDb ids the source
     * providers below index by. TMDb was the primary here and required an API
     * key to show anything at all — so the section's first screen on a fresh
     * install was an instruction to go and register for one, and every title it
     * did return then needed a second lookup before it could be searched for.
     */
    private val catalog: StremioCatalogProvider,
    private val debrid: DebridGateway,
    /**
     * Every way of finding a file, asked together.
     *
     * The built-in torrent search and the addon client answer the same question
     * through different protocols, and a user may have one, the other or both.
     * Merging them here means nothing downstream — ranking, the source list, the
     * player — has to know which one a given source came from.
     */
    private val torznab: TorznabProvider,
    private val addons: StremioAddonProvider,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /*
     * Bounded, and safe to touch from more than one thread.
     *
     * Both were plain maps read and written from coroutines on the IO
     * dispatcher, which is a pool — two shelves loading at once could write
     * concurrently, and a plain `LinkedHashMap` answers that with a
     * `ConcurrentModificationException` at some unrelated later read. They also
     * grew without limit: a `MediaItem` carries its cast, crew, genres and
     * season list, so browsing for a while accumulated the entire catalogue in
     * memory with nothing ever releasing it.
     *
     * An access-ordered LinkedHashMap with a size cap is the whole fix: oldest
     * entry out when full, and every access under one lock.
     */
    private val detailCache = LruCache<MediaItem>(DETAIL_CACHE_SIZE)
    private val seasonCache = LruCache<Season>(SEASON_CACHE_SIZE)

    /**
     * The browse screen's shelves, fetched concurrently.
     *
     * Concurrent because they are independent and the screen is worthless until
     * the first few arrive; a sequential fetch of a dozen rows makes opening the
     * section feel like a dozen separate loads.
     *
     * Served by the catalogue rather than by TMDb, which is what lets the section
     * work on a fresh install. A shelf that comes back empty — a genre the
     * catalogue has nothing for, or one request that failed — is dropped rather
     * than shown blank.
     */
    suspend fun browseRows(type: MediaType): List<MediaRow> = withContext(ioDispatcher) {
        coroutineScope {
            val shelves = catalog.shelves(type)
            val fetched = shelves.map { shelf -> async { catalog.catalog(shelf) } }.awaitAll()

            shelves.mapIndexedNotNull { index, shelf ->
                fetched[index]
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        MediaRow(
                            // Genre rows share the catalogue id, so the genre is
                            // part of the row's identity or they collide as keys.
                            id = listOfNotNull(shelf.id, shelf.genre).joinToString(":"),
                            title = shelf.title,
                            items = it,
                        )
                    }
            }
        }
    }

    suspend fun search(query: String, type: MediaType): List<MediaItem> =
        withContext(ioDispatcher) { catalog.search(query, type) }

    /**
     * Full details, memoised.
     *
     * The panel asks for these every time the cursor lands on a title, including
     * on the way back along a row the user has just walked. Without the cache
     * that is a request per keypress.
     */
    suspend fun details(id: MediaId): MediaItem? = withContext(ioDispatcher) {
        detailCache[id.key]?.let { return@withContext it }
        catalog.details(id)?.also { detailCache.put(id.key, it) }
    }

    /**
     * One season's episodes.
     *
     * Read from the title's own record rather than fetched separately: the
     * catalogue returns a series' whole episode list with its metadata, so the
     * season is already in hand by the time anything asks for it.
     */
    suspend fun season(id: MediaId, seasonNumber: Int): Season? = withContext(ioDispatcher) {
        val key = "${id.key}:$seasonNumber"
        seasonCache[key]?.let { return@withContext it }
        details(id)
            ?.seasons
            ?.firstOrNull { it.number == seasonNumber }
            ?.also { seasonCache.put(key, it) }
    }

    /**
     * Titles like this one.
     *
     * The catalogue protocol has no "similar" endpoint, so this is the title's
     * own leading genre — which is what a viewer means by it often enough to be
     * worth showing, and is one request rather than none.
     */
    suspend fun similar(id: MediaId): List<MediaItem> = withContext(ioDispatcher) {
        val genre = details(id)?.genres?.firstOrNull() ?: return@withContext emptyList()
        catalog
            .catalog(CatalogRequest(id = "top", title = genre, type = id.type, genre = genre))
            .filterNot { it.id == id }
    }

    /**
     * Every source for a title, ranked, with cache status filled in.
     *
     * The cache check is one request for the whole set and happens before
     * ranking, because cache status is the first thing the ordering looks at —
     * ranking first and annotating after would show the user a list that
     * immediately rearranged itself.
     */
    suspend fun sourcesFor(
        item: MediaItem,
        season: Int? = null,
        episode: Int? = null,
    ): SourceResult = withContext(ioDispatcher) {
        val imdbId = item.imdbId.takeIf(String::isNotBlank)
            ?: return@withContext SourceResult.NoImdbId

        val providers = listOf(torznab, addons).filter { it.isConfigured() }
        if (providers.isEmpty()) return@withContext SourceResult.NoProviders

        val query = SourceQuery(
            imdbId = imdbId,
            type = item.id.type,
            title = item.title,
            season = season,
            episode = episode,
        )

        /*
         * Bounded per provider, and one slow one no longer holds up the rest.
         *
         * `awaitAll` waits for the slowest, so a single addon that has stopped
         * answering left the panel on "Searching…" for as long as the HTTP
         * client would allow — which for a client tuned to API calls is a minute,
         * and for one that is merely slow rather than dead is longer still. The
         * others had already answered by then and their results sat unused.
         *
         * A provider that misses the deadline contributes nothing rather than
         * failing the search, for the same reason a metadata provider without
         * credentials is skipped: some sources are better than an error.
         */
        val results = supervisorScope {
            providers.map { provider ->
                async {
                    try {
                        withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { provider.find(query) }
                            ?: ProviderResult(
                                outcomes = listOf(
                                    ProviderOutcome(
                                        provider.displayName, 0, "did not answer in time",
                                    ),
                                ),
                            ).also {
                                ThorLog.w(TAG, "${provider.displayName} did not answer in time")
                            }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        ThorLog.w(TAG, "${provider.displayName} search failed", error)
                        ProviderResult(
                            outcomes = listOf(
                                ProviderOutcome(provider.displayName, 0, error.shortReason()),
                            ),
                        )
                    }
                }
            }.awaitAll()
        }

        val outcomes = results.flatMap { it.outcomes }
        val found = results
            .flatMap { it.sources }
            .distinctBy { it.infoHash?.lowercase() ?: it.id }

        if (found.isEmpty()) return@withContext SourceResult.Empty(outcomes)

        val annotated = withCacheStatus(found)
        val media = settings.media.first()
        val ranked = SourceRanking.rank(annotated, media)

        SourceResult.Found(
            all = annotated,
            ranked = ranked,
            outcomes = outcomes,
            /*
             * Said when the filters, not the providers, emptied the list.
             *
             * Sources were found and every one of them was ruled out by a
             * setting — most often "only cached", which is on by default and
             * silently removes everything when the debrid service will not
             * report availability. Without this the screen is identical to
             * having found nothing at all, and the remedy is in a completely
             * different place.
             */
            filteredOut = annotated.size.takeIf { ranked.isEmpty() && annotated.isNotEmpty() } ?: 0,
        )
    }

    /**
     * Annotates [sources] with what the debrid service holds, when it will say.
     *
     * Leaves every status [CacheStatus.UNKNOWN] when it will not — and that
     * distinction is the whole of this function. `cachedHashes` returns null for
     * "could not find out", and the difference between that and "holds none of
     * them" is the difference between a list ordered slightly worse and no list
     * at all: `cachedOnly` is on by default and drops everything marked
     * `NOT_CACHED`, so guessing "not cached" on a failed check deleted every
     * source that had been found. `SourceRanking` already treats unknown as
     * permitted for exactly this reason; it just never used to be told.
     */
    private suspend fun withCacheStatus(sources: List<StreamSource>): List<StreamSource> {
        if (!debrid.isConfigured()) return sources

        val hashes = sources.mapNotNull { it.infoHash }
        val availability = withTimeoutOrNull(CACHE_CHECK_TIMEOUT_MS) {
            debrid.cachedHashes(hashes)
        } ?: run {
            ThorLog.w(TAG, "No cache status for ${hashes.size} sources; offering them all")
            return sources
        }

        return sources.map { source ->
            val hash = source.infoHash ?: return@map source
            val variants = availability.variantsByHash[hash.lowercase()].orEmpty()
            val instantVariant = variants.firstOrNull { variant ->
                source.fileIndex?.plus(1) in variant.fileIds
            } ?: variants.firstOrNull()
            source.copy(
                /*
                 * The claim, not the evidence for it.
                 *
                 * This read "has a file variant" as "is cached", which is true
                 * of Real-Debrid — it answers by naming the files inside — and
                 * false of a service that simply says yes. TorBox says yes, so
                 * on TorBox every cached source was being marked not-cached and
                 * then deleted by `cachedOnly`, which is on by default: the
                 * account works, the hashes come back, and the list is empty.
                 */
                cached = when {
                    hash.lowercase() in availability.cachedHashes -> CacheStatus.CACHED
                    hash.lowercase() in availability.checkedHashes -> CacheStatus.NOT_CACHED
                    else -> CacheStatus.UNKNOWN
                },
                instantFileIds = instantVariant?.fileIds.orEmpty(),
            )
        }
    }

    /**
     * Turns a chosen source into something the player can open.
     *
     * A source that already carries a URL is returned as is — some addons
     * resolve their own links — and everything else goes through the debrid
     * service.
     */
    suspend fun resolve(source: StreamSource): ResolvedStream = withContext(ioDispatcher) {
        source.directUrl?.let {
            return@withContext ResolvedStream.Ready(
                url = it,
                fileName = source.title,
                requestHeaders = source.requestHeaders,
            )
        }

        val magnet = source.magnetUri
            ?: return@withContext ResolvedStream.Failed("This source has nothing to open")

        debrid.resolve(
            magnetUri = magnet,
            fileIndex = source.fileIndex,
            instantFileIds = source.instantFileIds,
            // A season pack with no file index named would otherwise resolve to
            // whichever episode happens to be biggest.
            preferLargest = source.fileIndex == null,
        )
    }

    suspend fun debridStatus(): DebridStatus = withContext(ioDispatcher) { debrid.checkConnection() }

    /** What the selected debrid service is called, for anything reporting on it. */
    suspend fun debridServiceName(): String = withContext(ioDispatcher) { debrid.serviceName() }

    /**
     * Asks one indexer whether it works, and reports what it said.
     *
     * The settings page could otherwise only report whether its fields were
     * filled in — which says nothing about a mistyped host, a revoked key, or a
     * Jackett that is not running, all of which surface much later as a film with
     * no sources.
     */
    suspend fun testIndexer(indexer: TorznabIndexer): String =
        withContext(ioDispatcher) { torznab.test(indexer) }

    /**
     * Asks an addon what it is called.
     *
     * The only way to tell a working install from a URL that was merely pasted:
     * both look identical in a settings list, and the difference only shows up
     * later as a source list that is always empty.
     */
    suspend fun identifyAddon(url: String): String? =
        withContext(ioDispatcher) { addons.identify(url) }

    /**
     * Asks an addon whether it will actually serve streams.
     *
     * Distinct from [identifyAddon], which only reads the manifest — an addon can
     * serve streams perfectly while its manifest is unreachable, and reporting
     * only the second makes a working addon look broken.
     */
    suspend fun checkAddon(url: String): AddonCheck =
        withContext(ioDispatcher) { addons.check(url) }

    private companion object {
        const val TAG = "Media"

        /**
         * Enough to cover walking a few shelves and coming back.
         *
         * The cache exists so that moving the cursor back along a row does not
         * re-fetch what it just showed; it is not a library. Beyond a few
         * hundred titles it is holding things the user will not return to.
         */
        const val DETAIL_CACHE_SIZE = 200
        const val SEASON_CACHE_SIZE = 60

        /**
         * How long one source provider gets before the search goes on without it.
         *
         * Long enough for an addon that is thinking, short enough that the panel
         * does not sit on "Searching…" while one of them is unreachable.
         */
        const val PROVIDER_TIMEOUT_MS = 20_000L
        const val CACHE_CHECK_TIMEOUT_MS = 12_000L
    }
}

/**
 * A small, thread-safe, least-recently-used map.
 *
 * Android's own `LruCache` would do, but it is in `android.util` and this class
 * is otherwise plain Kotlin that a unit test can exercise without a device.
 */
private class LruCache<V>(private val maxSize: Int) {

    private val entries = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean =
            size > maxSize
    }

    @Synchronized
    operator fun get(key: String): V? = entries[key]

    @Synchronized
    fun put(key: String, value: V) {
        entries[key] = value
    }
}

/** Why a source list looks the way it does, so the panel can say so. */
sealed interface SourceResult {

    /** What each place THOR asked had to say; see [ProviderOutcome]. */
    val outcomes: List<ProviderOutcome> get() = emptyList()

    data class Found(
        /** Everything found, for the "show all" list. */
        val all: List<StreamSource>,
        /** What the user's preferences allow, best first. */
        val ranked: List<StreamSource>,
        override val outcomes: List<ProviderOutcome> = emptyList(),
        /** How many were found and then ruled out by a setting. */
        val filteredOut: Int = 0,
    ) : SourceResult

    /** No addons configured — the section cannot find anything for any title. */
    data object NoProviders : SourceResult

    /** This title has no IMDb id, so no provider can be asked about it. */
    data object NoImdbId : SourceResult

    /** Asked, and nothing came back — with what each was asked and answered. */
    data class Empty(override val outcomes: List<ProviderOutcome> = emptyList()) : SourceResult
}
