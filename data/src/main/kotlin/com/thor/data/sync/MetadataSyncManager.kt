package com.thor.data.sync

import com.thor.core.common.coroutines.launchSafely
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.database.model.GameEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.GameMetadata
import com.thor.core.model.PlatformFlagships
import com.thor.core.model.PlatformFolders
import com.thor.data.metadata.MetadataAggregator
import com.thor.data.metadata.MetadataQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of a metadata scrape. */
sealed interface ScrapeState {
    data object Idle : ScrapeState
    data class Running(val done: Int, val total: Int, val currentTitle: String) : ScrapeState
    data class Completed(val updated: Int, val skipped: Int) : ScrapeState
    data class Failed(val message: String) : ScrapeState

    /** No provider is enabled and configured, so a scrape would do nothing. */
    data object NotConfigured : ScrapeState
}

/**
 * Downloads metadata and artwork for the library.
 *
 * Separate from [LibrarySyncManager] because the two have different costs and
 * different failure modes: a file scan is local and fast, while a scrape is
 * hundreds of rate-limited network calls that the user may want to start,
 * watch and cancel independently of finding their games.
 */
@Singleton
class MetadataSyncManager @Inject constructor(
    private val aggregator: MetadataAggregator,
    private val gameDao: GameDao,
    private val folderDao: FolderDao,
    private val platformDao: PlatformDao,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow<ScrapeState>(ScrapeState.Idle)
    val state: StateFlow<ScrapeState> = _state.asStateFlow()

    private var runningJob: Job? = null

    val isRunning: Boolean get() = runningJob?.isActive == true

    /**
     * Starts a scrape.
     *
     * @param onlyMissing when true, entries already scraped are skipped. This
     *   is the normal case; a full re-scrape is only useful after changing
     *   provider priority or credentials.
     */
    /**
     * Fetches trailers for games that have none.
     *
     * A separate entry point rather than a flag on the scrape button, because it
     * answers a different question: not "fill in what is missing" but "go and get
     * this one field for everything that could have it".
     */
    fun requestTrailerRefresh() = requestScrape(onlyMissing = false, trailersOnly = true)

    fun requestScrape(onlyMissing: Boolean = true, trailersOnly: Boolean = false) {
        if (isRunning) return
        /*
         * [launchSafely] rather than `runCatching`, and the difference is visible to
         * the user.
         *
         * `runCatching` catches `Throwable`, which includes `CancellationException` —
         * so pressing Cancel reported the cancellation it had just asked for as a
         * failure: [cancel] set the state to Idle, the coroutine then unwound through
         * `ensureActive`, and the handler immediately overwrote Idle with
         * "Failed: Job was cancelled". `launchSafely` rethrows cancellation and
         * handles only real errors.
         */
        runningJob = scope.launchSafely(
            tag = TAG,
            onError = { error ->
                _state.value = ScrapeState.Failed(error.message ?: "Scrape failed")
            },
        ) {
            scrape(onlyMissing = onlyMissing, trailersOnly = trailersOnly)
        }
    }

    fun cancel() {
        runningJob?.cancel()
        runningJob = null
        _state.value = ScrapeState.Idle
    }

    private suspend fun scrape(
        onlyMissing: Boolean,
        trailersOnly: Boolean = false,
    ) = withContext(ioDispatcher) {
        // A provider that is enabled but unconfigured contributes nothing, so a
        // scrape with none usable would churn through the whole library and
        // change not one row. Say so instead. The providers are asked directly
        // because their credential requirements differ.
        if (!aggregator.hasUsableProvider()) {
            _state.value = ScrapeState.NotConfigured
            return@withContext
        }

        // Only two providers carry video, and a library-wide pass against one that
        // cannot return any is a long wait ending in "0 updated" — which reads as
        // "there are no trailers for your games" instead of "nothing here can
        // fetch one". Said before the work rather than after it.
        if (trailersOnly && !aggregator.hasTrailerProvider()) {
            _state.value = ScrapeState.NotConfigured
            return@withContext
        }

        val platforms = platformDao.getAll().associateBy { it.id }
        val all = gameDao.getVisible()
        val targets = when {
            /*
             * Games with no trailer, whether or not they have been scraped.
             *
             * Trailers arrived after most libraries were already scraped, and
             * "only missing" means *never scraped* — so every existing game was
             * skipped and no trailer ever appeared. Re-scraping the whole library
             * to fetch them is hundreds of rate-limited calls for a field most of
             * them will not have; this asks only about the ones that could gain
             * one.
             */
            trailersOnly -> all.filter { it.metadata.artwork.videoUri.isNullOrBlank() }
            onlyMissing -> all.filter { it.metadata.lastScrapedEpochMs == null }
            else -> all
        }

        if (targets.isEmpty()) {
            _state.value = ScrapeState.Completed(updated = 0, skipped = all.size)
            return@withContext
        }

        var updated = 0
        var skipped = 0
        var trailersFound = 0

        targets.forEachIndexed { index, game ->
            currentCoroutineContext().ensureActive()
            _state.value = ScrapeState.Running(
                done = index,
                total = targets.size,
                currentTitle = game.title,
            )

            val platform = platforms[game.platformId]
            val merged = aggregator.scrape(
                query = MetadataQuery(
                    title = game.title,
                    sortTitle = game.sortTitle,
                    platformId = game.platformId,
                    providerPlatformId = platform?.providerIds?.get("screenscraper"),
                    fileName = game.fileName,
                    fileSizeBytes = game.fileSizeBytes,
                    releaseYearHint = game.metadata.releaseYear,
                    region = game.metadata.region,
                ),
                existing = game.metadata,
            )

            /*
             * A trailer pass counts trailers, not rows written.
             *
             * `merged != existing` is true for every game on every pass, because
             * the merge always stamps `lastScrapedEpochMs`. So the pass reported
             * the whole library as updated whether or not a single video had been
             * found — "Updated 500" while nothing played, which reads as a
             * playback bug and sent me looking in the wrong place twice.
             */
            val gained = merged.artwork.videoUri != null &&
                game.metadata.artwork.videoUri.isNullOrBlank()
            if (gained) trailersFound++

            if (merged != game.metadata) {
                gameDao.setMetadata(game.id, merged)
                if (!trailersOnly || gained) updated++ else skipped++
            } else {
                skipped++
            }
        }

        if (trailersOnly) {
            ThorLog.i(
                TAG,
                "Trailer pass: $trailersFound found across ${targets.size} games",
            )
        }

        // Skipped entirely for a trailer refresh. That pass exists to fill one
        // field on games that lack it; re-fetching every folder's artwork on the
        // way past is unrelated work the user did not ask for, and — before this —
        // work that actively undid their icon pack.
        if (!trailersOnly) {
            updated += scrapeFolderArtwork(onlyMissing)
            updated += dressPlatformFolders()
        }

        _state.value = ScrapeState.Completed(updated = updated, skipped = skipped)
    }

    /**
     * Gives folders artwork from the same providers as games.
     *
     * A folder is usually named after a series or a system — "Zelda", "Mario
     * Kart", "Arcade" — so the providers can find cover art for it exactly as
     * they would for a title. Hand-picking an image for every folder is the only
     * alternative, and it is the sort of chore that leaves folders looking like
     * placeholder glyphs forever.
     *
     * Smart folders are included; their artwork is presentation, not contents.
     * Folders whose artwork the user chose by hand are never touched — that URI is
     * their own, and a scrape overwriting it would be the same silent revert the
     * locked-field mechanism exists to prevent for games.
     *
     * @return how many folders gained artwork
     */
    private suspend fun scrapeFolderArtwork(onlyMissing: Boolean): Int {
        /*
         * Platform folders are never scraped, and this is the important part.
         *
         * A platform folder is titled after a machine — "Super Nintendo", "Sega
         * Dreamcast" — and these providers index games, not hardware. Asked about
         * a console they answer with a game that merely mentions it, so every
         * system on the grid ended up wearing an unrelated screenshot. Worse, a
         * full rescrape then wrote that over the icon pack's artwork, which is
         * why the platforms looked right until the next scrape and never again.
         *
         * A platform's artwork has exactly one source — an installed pack — and
         * nothing else may write it.
         */
        val folders = folderDao.getAll()
            .filter { PlatformFolders.platformIdOf(it.id) == null }

        val targets = if (onlyMissing) folders.filter { it.artworkUri == null } else folders
        if (targets.isEmpty()) return 0

        var updated = 0
        targets.forEachIndexed { index, folder ->
            currentCoroutineContext().ensureActive()
            _state.value = ScrapeState.Running(
                done = index,
                total = targets.size,
                currentTitle = folder.title,
            )

            val scraped = aggregator.scrape(
                query = MetadataQuery(
                    title = folder.title,
                    sortTitle = folder.sortTitle,
                    // No platform: a folder spans systems, and constraining the
                    // search to one would miss most of the matches.
                    platformId = "",
                    providerPlatformId = null,
                    // A folder has no file, so the filename and size matchers
                    // have nothing to work with and only title matching applies.
                    fileName = folder.title,
                    fileSizeBytes = 0L,
                ),
                existing = GameMetadata.EMPTY,
            )

            // Square art first, then the cover: a folder renders in the same
            // square cell a game does.
            val artwork = scraped.artwork.icon
                ?: scraped.artwork.boxArt
                ?: scraped.artwork.hero
            if (artwork != null) {
                folderDao.upsert(folder.copy(artworkUri = artwork))
                updated++
            }
        }
        return updated
    }

    /**
     * Gives platform folders artwork from the games inside them.
     *
     * Platform folders are never *searched* for — see [scrapeFolderArtwork] —
     * because these providers index games, not hardware, and asking one about
     * "Super Nintendo" returns a game that merely mentions it. Removing that
     * left the folders bare, which is the opposite fault: after a scrape the
     * artwork was sitting right there in the library and none of it was used.
     *
     * So the folder wears its own best game's cover. The ordering is fixed —
     * landmark titles first, then play count, then play time, then title — which
     * makes it representative rather than arbitrary, and makes it the *same*
     * every run. That last part is what the original complaint was really about:
     * not that a game's art appeared, but that a different one appeared each time.
     *
     * An installed icon pack always wins; a platform it dressed is skipped
     * entirely, so this can never undo the user's own artwork.
     */
    private suspend fun dressPlatformFolders(): Int {
        val platforms = platformDao.getAll().associateBy { it.id }
        val folders = folderDao.getAll()
            .mapNotNull { folder ->
                PlatformFolders.platformIdOf(folder.id)?.let { platformId -> folder to platformId }
            }
            // Dressed by a pack, and not ours to touch.
            .filter { (_, platformId) -> platforms[platformId]?.artworkPackId == null }

        if (folders.isEmpty()) return 0

        val gamesByPlatform = gameDao.getVisible().groupBy { it.platformId }
        var updated = 0

        folders.forEach { (folder, platformId) ->
            currentCoroutineContext().ensureActive()

            val artwork = gamesByPlatform[platformId]
                .orEmpty()
                .sortedWith(
                    compareBy<GameEntity> {
                        PlatformFlagships.rankOf(platformId, it.title) ?: FLAGSHIP_MISS
                    }
                        .thenByDescending { it.launchCount }
                        .thenByDescending { it.totalPlayMillis }
                        .thenBy { it.sortTitle },
                )
                .firstNotNullOfOrNull { it.metadata.artwork.cellImage }
                ?: return@forEach

            if (folder.artworkUri == artwork) return@forEach
            folderDao.upsert(folder.copy(artworkUri = artwork))
            updated++
        }

        return updated
    }

    /** Scrapes one entry, used by the "refresh metadata" context action. */
    fun requestScrapeFor(gameId: String) {
        if (isRunning) return
        // Cancellation-safe for the same reason as [requestScrape].
        runningJob = scope.launchSafely(
            tag = TAG,
            onError = { error ->
                ThorLog.e(TAG, "Scrape failed for $gameId", error)
                _state.value = ScrapeState.Failed(error.message ?: "Scrape failed")
            },
        ) {
            withContext(ioDispatcher) {
                val game: GameEntity = gameDao.getById(gameId) ?: return@withContext
                val platform = platformDao.getById(game.platformId)
                _state.value = ScrapeState.Running(0, 1, game.title)

                val merged = aggregator.scrape(
                    query = MetadataQuery(
                        title = game.title,
                        sortTitle = game.sortTitle,
                        platformId = game.platformId,
                        providerPlatformId = platform?.providerIds?.get("screenscraper"),
                        fileName = game.fileName,
                        fileSizeBytes = game.fileSizeBytes,
                    ),
                    existing = game.metadata,
                )
                gameDao.setMetadata(gameId, merged)
                _state.value = ScrapeState.Completed(updated = 1, skipped = 0)
            }
        }
    }

    private companion object {
        const val TAG = "MetadataSync"

        /** Sorts every non-flagship below every flagship, without excluding it. */
        const val FLAGSHIP_MISS = Int.MAX_VALUE
    }
}
