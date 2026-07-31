package com.thor.data.repository

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.dao.AchievementDao
import com.thor.core.database.dao.AppDao
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.database.dao.PlayHistoryDao
import com.thor.core.database.model.AppEntity
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.PlatformEntity
import com.thor.core.database.model.PlaySessionEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.AppEntry
import com.thor.core.model.BuiltInPlatforms
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GameMetadata
import com.thor.core.model.GameVersion
import com.thor.core.model.GridEntry
import com.thor.core.model.LibraryFilter
import com.thor.core.model.Platform
import com.thor.core.model.PlatformArtwork
import com.thor.core.model.PlatformFolders
import com.thor.core.model.PlayStats
import com.thor.core.model.SmartQuery
import com.thor.core.model.SortOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's view of everything that can appear on the grid.
 *
 * Apps, games and folders live in separate tables but are presented as one
 * stream of [GridEntry], because the grid, search and the top screen all work
 * against that single abstraction. Combining happens here, once, and the result
 * is what every consumer observes.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val appDao: AppDao,
    private val gameDao: GameDao,
    private val folderDao: FolderDao,
    private val platformDao: PlatformDao,
    private val playHistoryDao: PlayHistoryDao,
    private val achievementDao: AchievementDao,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * Every entry that can appear on the grid, keyed by id.
     *
     * Apps are always present here. Whether one *shows* is decided by whether it
     * has a placement, not by a filter at this level — otherwise an app the user
     * deliberately added from the drawer would vanish whenever the
     * "add all apps" preference was off, while still occupying its cell.
     */
    /**
     * Whether entries the user hid are shown anyway.
     *
     * Filtered here in memory rather than by swapping between the DAO's visible
     * and unfiltered queries: switching query would tear down and re-establish a
     * table observer every time the setting changed, and the hidden set is a
     * predicate over a list already in hand.
     */
    private val showHidden: Flow<Boolean> =
        settings.library.map { it.showHiddenEntries }.distinctUntilChanged()

    val entriesById: Flow<Map<String, GridEntry>> = combine(
        appDao.observeAll(),
        gameDao.observeAll(),
        folderDao.observeAll(),
        showHidden,
    ) { apps, games, folders, revealHidden ->
        buildMap<String, GridEntry> {
            apps.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
            games.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
            folders.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
        }
    }.flowOn(defaultDispatcher).distinctUntilChanged()

    val games: Flow<List<GameEntry>> = combine(
        gameDao.observeAll(),
        showHidden,
    ) { list, revealHidden ->
        list.filter { revealHidden || !it.isHidden }.map(GameEntity::toDomain)
    }.flowOn(defaultDispatcher).distinctUntilChanged()

    val apps: Flow<List<AppEntry>> = combine(
        appDao.observeAll(),
        showHidden,
    ) { list, revealHidden ->
        list.filter { revealHidden || !it.isHidden }.map(AppEntity::toDomain)
    }.flowOn(defaultDispatcher).distinctUntilChanged()

    val folders: Flow<List<FolderEntry>> =
        folderDao.observeAll().map { list -> list.map(FolderEntity::toDomain) }

    /**
     * Every known platform.
     *
     * The grid needs all of them for badge colours and launch resolution, even
     * ones the user has not added to their setup — a scanned ROM still belongs
     * to its system.
     */
    val platforms: Flow<List<Platform>> =
        platformDao.observeAll().map { list -> list.map(PlatformEntity::toDomain) }

    /** Only the systems the user has explicitly added. */
    val addedPlatforms: Flow<List<Platform>> =
        platformDao.observeAdded().map { list -> list.map(PlatformEntity::toDomain) }

    val gameCount: Flow<Int> = gameDao.observeCount()

    /** Recently played entry ids, most recent first. */
    fun recentlyPlayed(limit: Int = 20): Flow<List<String>> =
        playHistoryDao.observeRecentlyPlayedIds(limit)

    /**
     * Seeds the platform table on first run.
     *
     * Uses insert-ignore rather than upsert so that a user's edits to a
     * built-in platform (renamed, recoloured, assigned an emulator) survive
     * every subsequent launch.
     */
    suspend fun ensurePlatformsSeeded() = withContext(defaultDispatcher) {
        // Seeded as *not added*: every platform must exist so the ROM scanner
        // can recognise its file types, but the emulator settings should start
        // empty and let the user add only the systems they actually own.
        platformDao.insertIgnoring(
            BuiltInPlatforms.ALL.map { it.toEntity().copy(isAdded = false) },
        )
    }

    suspend fun entry(id: String): GridEntry? = withContext(defaultDispatcher) {
        when {
            id.startsWith("app:") -> appDao.getById(id)?.toDomain()
            id.startsWith("game:") -> gameDao.getById(id)?.let { hydrate(it) }
            else -> folderDao.getById(id)?.toDomain()
        }
    }

    /** Observes a single game, including its alternate versions and achievements. */
    fun observeGame(id: String): Flow<GameEntry?> = combine(
        gameDao.observeById(id),
        achievementDao.observeFor(id),
    ) { game, achievements ->
        game?.toDomain()?.let { entry ->
            val summary = entry.metadata.achievements
            if (summary == null || achievements.isEmpty()) {
                entry
            } else {
                entry.copy(
                    metadata = entry.metadata.copy(
                        achievements = summary.copy(
                            recentlyEarned = achievements
                                .filter { it.earnedEpochMs != null }
                                .sortedByDescending { it.earnedEpochMs }
                                .take(5)
                                .map { achievement ->
                                    com.thor.core.model.Achievement(
                                        id = achievement.id,
                                        title = achievement.title,
                                        description = achievement.description,
                                        points = achievement.points,
                                        badgeUri = achievement.badgeUri,
                                        earnedEpochMs = achievement.earnedEpochMs,
                                        isHardcore = achievement.isHardcore,
                                    )
                                },
                        ),
                    ),
                )
            }
        }
    }.flowOn(defaultDispatcher)

    private suspend fun hydrate(game: GameEntity): GameEntry {
        val versions = gameDao.versionsFor(game.id)
        return game.toDomain().copy(
            alternateVersions = versions.map { version ->
                GameVersion(
                    id = version.id,
                    label = version.label,
                    contentUri = version.contentUri,
                    fileName = version.fileName,
                    fileSizeBytes = version.fileSizeBytes,
                    region = version.region,
                    languages = version.languages,
                    discNumber = version.discNumber,
                )
            },
        )
    }

    suspend fun setFavorite(entryId: String, favorite: Boolean) {
        when {
            entryId.startsWith("app:") -> appDao.setFavorite(entryId, favorite)
            entryId.startsWith("game:") -> gameDao.setFavorite(entryId, favorite)
        }
    }

    /** Adds a system to the user's setup. */
    suspend fun addPlatform(platformId: String) = withContext(defaultDispatcher) {
        platformDao.setAdded(platformId, true)
    }

    /**
     * Removes a system from the user's setup.
     *
     * The platform row survives — games already scanned still reference it, and
     * removing the row would cascade-delete them.
     */
    suspend fun removePlatform(platformId: String) = withContext(defaultDispatcher) {
        platformDao.setAdded(platformId, false)
    }

    /**
     * Sets the emulators a platform's games can launch with, in preference
     * order; the first is the default.
     *
     * Stored on the platform rather than on each game, so adding a thousand
     * ROMs later picks up the choice automatically. A per-game override still
     * wins when one is set.
     */
    suspend fun setPlatformEmulators(platformId: String, packages: List<String>) =
        withContext(defaultDispatcher) {
            platformDao.setEmulators(platformId, packages)
        }

    /** Renames an entry, keeping its sort key consistent with the new title. */
    suspend fun rename(entryId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val sortKey = TitleNormalizer.sortKey(trimmed)
        when {
            entryId.startsWith("app:") -> appDao.rename(entryId, trimmed, sortKey)
            entryId.startsWith("game:") -> gameDao.rename(entryId, trimmed, sortKey)
            else -> folderDao.getById(entryId)?.let { folder ->
                folderDao.upsert(folder.copy(title = trimmed, sortTitle = sortKey))
            }
        }
    }

    /**
     * Sets a user-chosen icon.
     *
     * Apps get a dedicated column; games carry it inside their artwork set and
     * the field is locked so a later scrape cannot overwrite the user's choice.
     */
    suspend fun setCustomIcon(entryId: String, uri: String?) {
        when {
            entryId.startsWith("app:") -> appDao.setCustomIcon(entryId, uri)

            entryId.startsWith("game:") -> {
                val game = gameDao.getById(entryId) ?: return
                val metadata = game.metadata
                gameDao.setMetadata(
                    id = entryId,
                    metadata = metadata.copy(
                        artwork = metadata.artwork.copy(boxArt = uri ?: metadata.artwork.boxArt),
                        lockedFields = if (uri != null) {
                            metadata.lockedFields + GameMetadata.FIELD_ARTWORK
                        } else {
                            metadata.lockedFields - GameMetadata.FIELD_ARTWORK
                        },
                    ),
                )
            }

            else -> folderDao.getById(entryId)?.let { folder ->
                folderDao.upsert(folder.copy(artworkUri = uri))
            }
        }
    }

    /**
     * Sets a platform's artwork by hand.
     *
     * Written to the *platform* rather than to its folder, because that is where
     * it belongs: the same icon dresses the grid cell, the information panel and
     * the open-folder banner, and it has to survive the folder being renamed,
     * moved, or deleted and rebuilt by a rescan. The folder is dressed to match
     * so the grid shows it immediately.
     *
     * Marked as the user's, which is what makes it stick — see
     * [PlatformArtwork.isUserChosen]. Every rule that already respects pack
     * ownership then leaves it alone: the folder scraper skips it, a newly
     * installed pack does not overwrite it, and removing a pack does not strip
     * it.
     *
     * A null for either field leaves that one as it is, so choosing a backdrop
     * does not clear an icon.
     */
    suspend fun setPlatformArtwork(
        platformId: String,
        iconUri: String? = null,
        heroUri: String? = null,
    ) = withContext(defaultDispatcher) {
        val platform = platformDao.getById(platformId) ?: return@withContext
        val icon = iconUri ?: platform.artworkIconUri
        platformDao.upsert(
            platform.copy(
                artworkIconUri = icon,
                artworkHeroUri = heroUri ?: platform.artworkHeroUri,
                artworkPackId = PlatformArtwork.USER_PACK_ID,
            ),
        )

        folderDao.getById(PlatformFolders.idFor(platformId))?.let { folder ->
            folderDao.upsert(folder.copy(artworkUri = icon))
        }
    }

    /**
     * Gives a platform's artwork back to whatever would otherwise supply it.
     *
     * Clears the user's choice *and* its ownership marker, so the next scrape or
     * pack install fills it again. Without clearing the marker the platform would
     * be left permanently bare — owned by a choice that no longer exists.
     */
    suspend fun clearPlatformArtwork(platformId: String) = withContext(defaultDispatcher) {
        val platform = platformDao.getById(platformId) ?: return@withContext
        if (!PlatformArtwork(packId = platform.artworkPackId).isUserChosen) return@withContext

        platformDao.upsert(
            platform.copy(
                artworkIconUri = null,
                artworkHeroUri = null,
                artworkPackId = null,
            ),
        )

        folderDao.getById(PlatformFolders.idFor(platformId))?.let { folder ->
            folderDao.upsert(folder.copy(artworkUri = null))
        }
    }

    /**
     * Applies a hand-edited metadata record.
     *
     * Every field the user actually filled in is added to `lockedFields`, which
     * is what stops the next scrape from quietly reverting the edit.
     */
    suspend fun updateGameMetadata(entryId: String, metadata: GameMetadata) {
        val game = gameDao.getById(entryId) ?: return
        val locked = buildSet {
            addAll(game.metadata.lockedFields)
            if (!metadata.description.isNullOrBlank()) add(GameMetadata.FIELD_DESCRIPTION)
            if (metadata.genres.isNotEmpty()) add(GameMetadata.FIELD_GENRES)
            if (!metadata.developer.isNullOrBlank()) add(GameMetadata.FIELD_DEVELOPER)
            if (!metadata.publisher.isNullOrBlank()) add(GameMetadata.FIELD_PUBLISHER)
            if (metadata.releaseYear != null) add(GameMetadata.FIELD_RELEASE_DATE)
            if (metadata.rating != null) add(GameMetadata.FIELD_RATING)
        }
        gameDao.setMetadata(entryId, metadata.copy(lockedFields = locked))
    }

    /**
     * Reassigns a game to a different system.
     *
     * Offered in the entry editor because the scanner infers the platform from
     * the containing folder and file extension, and both can be wrong — a `.bin`
     * could be any of half a dozen consoles.
     */
    suspend fun setGamePlatform(entryId: String, platformId: String) {
        val game = gameDao.getById(entryId) ?: return
        if (game.platformId == platformId) return
        gameDao.setPlatform(
            id = entryId,
            platformId = platformId,
            duplicateKey = "$platformId:${game.sortTitle}:${game.fileSizeBytes}",
        )
    }

    /**
     * Pins the emulator a single game launches with.
     *
     * Null clears the override, which returns the game to its platform default.
     */
    suspend fun setGameEmulator(entryId: String, packageName: String?) {
        gameDao.setEmulator(entryId, packageName)
    }

    suspend fun setHidden(entryId: String, hidden: Boolean) {
        when {
            entryId.startsWith("app:") -> appDao.setHidden(entryId, hidden)
            entryId.startsWith("game:") -> gameDao.setHidden(entryId, hidden)
        }
    }

    /**
     * Removes an entry from the library outright.
     *
     * Distinct from hiding, and the difference is worth stating because the two
     * read alike from the grid. Hiding is a durable *decision about* an entry: the
     * row stays, the flag survives rescans, and the entry never comes back on its
     * own. Deleting removes the row, so a rescan that still finds the underlying
     * ROM re-adds it — freshly, and not hidden.
     *
     * That is what makes this the way out of a library the user has tangled: a
     * hidden entry whose hidden-ness has outlived its reason can be deleted and
     * re-found in its default state, rather than staying invisible forever because
     * the flag is stickier than the thing that set it.
     *
     * Versions and play history go with a game; both are keyed to the id and would
     * otherwise be orphaned rows referencing an entry that no longer exists.
     */
    suspend fun deleteEntry(entryId: String) = withContext(defaultDispatcher) {
        when {
            entryId.startsWith("app:") -> appDao.deleteByIds(listOf(entryId))
            entryId.startsWith("game:") -> {
                gameDao.deleteVersionsFor(entryId)
                gameDao.deleteByIds(listOf(entryId))
            }
        }
    }

    /** Records a launch and, when known, the duration of the session that followed. */
    suspend fun recordLaunch(entryId: String, timestamp: Long = System.currentTimeMillis()) {
        when {
            entryId.startsWith("app:") -> appDao.recordLaunch(entryId, timestamp)
            entryId.startsWith("game:") -> gameDao.recordLaunch(entryId, timestamp)
        }
    }

    suspend fun recordSession(entryId: String, startedAt: Long, durationMillis: Long) {
        if (durationMillis <= 0) return
        playHistoryDao.insert(
            PlaySessionEntity(
                entryId = entryId,
                startedAtEpochMs = startedAt,
                durationMillis = durationMillis,
            ),
        )
        when {
            entryId.startsWith("app:") -> appDao.addPlayTime(entryId, durationMillis)
            entryId.startsWith("game:") -> gameDao.addPlayTime(entryId, durationMillis)
        }
    }

    /** Full-text search across games and apps. */
    suspend fun search(query: String, limit: Int = 50): List<GridEntry> =
        withContext(defaultDispatcher) {
            if (query.isBlank()) return@withContext emptyList()
            val normalized = TitleNormalizer.sortKey(query)
            val games = gameDao.search(normalized, limit).map(GameEntity::toDomain)
            val apps = appDao.getVisible()
                .filter { it.sortTitle.contains(normalized, ignoreCase = true) }
                .take(limit)
                .map(AppEntity::toDomain)
            (games + apps)
                .sortedWith(
                    // Prefix matches first, then alphabetical — the ordering a
                    // user typing a few characters expects.
                    compareBy<GridEntry> { if (it.sortTitle.startsWith(normalized)) 0 else 1 }
                        .thenBy { it.sortTitle },
                )
                .take(limit)
        }

    /**
     * Evaluates a smart folder's query.
     *
     * Runs entirely in memory over the already-observed library rather than as
     * SQL, because the criteria span JSON metadata columns and play history;
     * the library is small enough that filtering it is far cheaper than the
     * joins the equivalent query would need.
     */
    suspend fun evaluateSmartQuery(query: SmartQuery): List<GridEntry> =
        withContext(defaultDispatcher) {
            val now = System.currentTimeMillis()
            val allGames = gameDao.getVisible().map(GameEntity::toDomain)

            allGames.asSequence()
                .filter { query.platformIds.isEmpty() || it.platformId in query.platformIds }
                .filter { game ->
                    query.genres.isEmpty() ||
                        game.metadata.genres.any { it in query.genres }
                }
                .filter { query.tags.isEmpty() || it.tags.any { tag -> tag in query.tags } }
                .filter { !query.favoritesOnly || it.isFavorite }
                .filter { !query.unplayedOnly || !it.stats.hasBeenPlayed }
                .filter { game ->
                    val within = query.playedWithinDays ?: return@filter true
                    val last = game.stats.lastPlayedEpochMs ?: return@filter false
                    now - last <= within * MILLIS_PER_DAY
                }
                .filter { game ->
                    query.minRating?.let { min -> (game.metadata.rating ?: 0) >= min } ?: true
                }
                .filter { game ->
                    query.releasedAfterYear?.let { year ->
                        (game.metadata.releaseYear ?: Int.MIN_VALUE) >= year
                    } ?: true
                }
                .filter { game ->
                    query.releasedBeforeYear?.let { year ->
                        (game.metadata.releaseYear ?: Int.MAX_VALUE) <= year
                    } ?: true
                }
                .filter { game ->
                    query.titleContains?.let { needle ->
                        game.sortTitle.contains(TitleNormalizer.sortKey(needle))
                    } ?: true
                }
                .toList()
                .sortedWith(comparatorFor(query.sort, query.sortDescending))
                .let { results -> query.limit?.let(results::take) ?: results }
        }

    /** Applies a browsing filter to an entry list. */
    fun applyFilter(entries: List<GridEntry>, filter: LibraryFilter): List<GridEntry> =
        entries.filter { entry ->
            if (filter.hideHidden && entry.isHidden) return@filter false
            if (filter.favoritesOnly && !entry.isFavorite) return@filter false
            when (entry) {
                is GameEntry -> {
                    if (filter.platformIds.isNotEmpty() && entry.platformId !in filter.platformIds) {
                        return@filter false
                    }
                    if (filter.genres.isNotEmpty() &&
                        entry.metadata.genres.none { it in filter.genres }
                    ) {
                        return@filter false
                    }
                    if (filter.tags.isNotEmpty() && entry.tags.none { it in filter.tags }) {
                        return@filter false
                    }
                    if (filter.withAchievementsOnly && entry.metadata.achievements == null) {
                        return@filter false
                    }
                    true
                }

                else -> filter.platformIds.isEmpty() && filter.genres.isEmpty() &&
                    filter.tags.isEmpty() && !filter.withAchievementsOnly
            }
        }

    /** Comparator matching a [SortOrder]. */
    fun comparatorFor(order: SortOrder, descending: Boolean): Comparator<GridEntry> {
        val base: Comparator<GridEntry> = when (order) {
            SortOrder.TITLE, SortOrder.MANUAL -> compareBy { it.sortTitle }
            SortOrder.PLATFORM -> compareBy<GridEntry> { (it as? GameEntry)?.platformId ?: "" }
                .thenBy { it.sortTitle }

            SortOrder.RELEASE_DATE -> compareBy<GridEntry> {
                (it as? GameEntry)?.metadata?.releaseYear ?: Int.MAX_VALUE
            }.thenBy { it.sortTitle }

            SortOrder.RATING -> compareByDescending<GridEntry> {
                (it as? GameEntry)?.metadata?.rating ?: -1
            }.thenBy { it.sortTitle }

            SortOrder.LAST_PLAYED -> compareByDescending<GridEntry> { entry ->
                when (entry) {
                    is GameEntry -> entry.stats.lastPlayedEpochMs ?: 0L
                    is AppEntry -> entry.lastPlayedEpochMs ?: 0L
                    else -> 0L
                }
            }.thenBy { it.sortTitle }

            SortOrder.PLAY_TIME -> compareByDescending<GridEntry> {
                (it as? GameEntry)?.stats?.totalPlayMillis ?: 0L
            }.thenBy { it.sortTitle }

            SortOrder.LAUNCH_COUNT -> compareByDescending<GridEntry> { entry ->
                when (entry) {
                    is GameEntry -> entry.stats.launchCount
                    is AppEntry -> entry.launchCount
                    else -> 0
                }
            }.thenBy { it.sortTitle }

            SortOrder.DATE_ADDED -> compareByDescending<GridEntry> {
                (it as? AppEntry)?.installedAtEpochMs ?: 0L
            }.thenBy { it.sortTitle }

            SortOrder.FILE_SIZE -> compareByDescending<GridEntry> {
                (it as? GameEntry)?.fileSizeBytes ?: 0L
            }.thenBy { it.sortTitle }
        }
        return if (descending) base.reversed() else base
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
