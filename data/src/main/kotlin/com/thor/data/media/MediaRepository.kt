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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    private val tmdb: TmdbClient,
    private val debrid: RealDebridClient,
    private val sources: StremioAddonProvider,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val detailCache = mutableMapOf<String, MediaItem>()
    private val seasonCache = mutableMapOf<String, Season>()

    /**
     * The browse screen's shelves, fetched concurrently.
     *
     * Concurrent because they are independent and the screen is worthless until
     * the first few arrive; a sequential fetch of six rows makes opening the
     * section feel like six separate loads.
     */
    suspend fun browseRows(type: MediaType): List<MediaRow> = withContext(ioDispatcher) {
        coroutineScope {
            val definitions = listOf(
                "trending" to "Trending now",
                "popular" to "Popular",
                "recent" to if (type == MediaType.MOVIE) "In cinemas" else "On the air",
                "top" to "Top rated",
            )

            val fetched = definitions.map { (key, _) ->
                async {
                    when (key) {
                        "trending" -> tmdb.trending(type)
                        "popular" -> tmdb.popular(type)
                        "recent" -> tmdb.recent(type)
                        else -> tmdb.topRated(type)
                    }
                }
            }.awaitAll()

            definitions.mapIndexedNotNull { index, (key, title) ->
                fetched[index]
                    .takeIf { it.isNotEmpty() }
                    ?.let { MediaRow(id = key, title = title, items = it) }
            }
        }
    }

    suspend fun search(query: String, type: MediaType): List<MediaItem> =
        withContext(ioDispatcher) { tmdb.search(query, type) }

    /**
     * Full details, memoised.
     *
     * The panel asks for these every time the cursor lands on a title, including
     * on the way back along a row the user has just walked. Without the cache
     * that is a request per keypress.
     */
    suspend fun details(id: MediaId): MediaItem? = withContext(ioDispatcher) {
        detailCache[id.key]?.let { return@withContext it }
        tmdb.details(id)?.also { detailCache[id.key] = it }
    }

    suspend fun season(seriesId: Int, seasonNumber: Int): Season? = withContext(ioDispatcher) {
        val key = "$seriesId:$seasonNumber"
        seasonCache[key]?.let { return@withContext it }
        tmdb.season(seriesId, seasonNumber)?.also { seasonCache[key] = it }
    }

    suspend fun similar(id: MediaId): List<MediaItem> = withContext(ioDispatcher) {
        tmdb.similar(id)
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
        val imdbId = item.imdbId?.takeIf(String::isNotBlank)
            ?: return@withContext SourceResult.NoImdbId

        if (!sources.isConfigured()) return@withContext SourceResult.NoProviders

        val found = sources.find(
            SourceQuery(
                imdbId = imdbId,
                type = item.id.type,
                season = season,
                episode = episode,
            ),
        )

        if (found.isEmpty()) return@withContext SourceResult.Empty

        val annotated = withCacheStatus(found)
        val media = settings.media.first()
        SourceResult.Found(
            all = annotated,
            ranked = SourceRanking.rank(annotated, media),
        )
    }

    private suspend fun withCacheStatus(sources: List<StreamSource>): List<StreamSource> {
        if (!debrid.isConfigured()) return sources

        val hashes = sources.mapNotNull { it.infoHash }
        val cached = debrid.cachedHashes(hashes)
        if (cached.isEmpty() && hashes.isNotEmpty()) {
            ThorLog.i(TAG, "Debrid holds none of ${hashes.size} sources")
        }

        return sources.map { source ->
            val hash = source.infoHash ?: return@map source
            source.copy(
                cached = if (hash in cached) CacheStatus.CACHED else CacheStatus.NOT_CACHED,
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
        source.directUrl?.let { return@withContext ResolvedStream.Ready(it, source.title) }

        val magnet = source.magnetUri
            ?: return@withContext ResolvedStream.Failed("This source has nothing to open")

        debrid.resolve(
            magnetUri = magnet,
            fileIndex = source.fileIndex,
            // A season pack with no file index named would otherwise resolve to
            // whichever episode happens to be biggest.
            preferLargest = source.fileIndex == null,
        )
    }

    suspend fun debridStatus(): DebridStatus = withContext(ioDispatcher) { debrid.checkConnection() }

    private companion object {
        const val TAG = "Media"
    }
}

/** Why a source list looks the way it does, so the panel can say so. */
sealed interface SourceResult {
    data class Found(
        /** Everything found, for the "show all" list. */
        val all: List<StreamSource>,
        /** What the user's preferences allow, best first. */
        val ranked: List<StreamSource>,
    ) : SourceResult

    /** No addons configured — the section cannot find anything for any title. */
    data object NoProviders : SourceResult

    /** This title has no IMDb id, so no provider can be asked about it. */
    data object NoImdbId : SourceResult

    /** Asked, and nothing came back. */
    data object Empty : SourceResult
}
