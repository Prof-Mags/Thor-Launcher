package com.thor.data.library

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.dao.AchievementDao
import com.thor.core.database.dao.AppDao
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.GridDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.database.dao.PlayHistoryDao
import com.thor.core.database.dao.WidgetDao
import com.thor.core.database.model.AppEntity
import com.thor.core.database.model.FolderEntity
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.PlatformEntity
import com.thor.core.database.model.PlaySessionEntity
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.AppEntry
import com.thor.core.model.ArtworkSet
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
import com.thor.core.model.SmartQuery
import com.thor.core.model.SortOrder
import com.thor.core.model.WidgetEntry
import com.thor.data.metadata.MetadataAggregator
import com.thor.data.metadata.MetadataCandidate
import com.thor.data.metadata.MetadataQuery
import com.thor.data.widget.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    /** Placements, so removing a system takes its folder off the grid at once. */
    private val gridDao: GridDao,
    private val platformDao: PlatformDao,
    private val playHistoryDao: PlayHistoryDao,
    /** Placed widgets, which share the grid with everything else here. */
    private val widgetDao: WidgetDao,
    private val achievementDao: AchievementDao,
    private val settings: SettingsRepository,
    /** The scrapers, for the manual match picker; see [matchCandidatesFor]. */
    private val aggregator: MetadataAggregator,
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
        widgetDao.observeAll(),
        showHidden,
    ) { apps, games, folders, widgets, revealHidden ->
        buildMap<String, GridEntry> {
            apps.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
            games.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
            folders.filter { revealHidden || !it.isHidden }.forEach { put(it.id, it.toDomain()) }
            /*
             * Widgets are never hidden.
             *
             * "Hidden" means an entry the library found and the user does not
             * want listed — it is a property of something that was discovered.
             * A widget is only here because the user placed it by hand, so the
             * gesture that would hide one is the gesture that removes it.
             */
            widgets.forEach { put(WidgetEntry.idFor(it.appWidgetId), it.toDomain()) }
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
     * Removes a system from the user's setup, and everything it brought with it.
     *
     * The platform *row* survives — it is a built-in definition, not the user's
     * data, and it has to exist for the system to be added again later.
     * Everything derived from it goes: its games, and the folder they were filed
     * into on the grid.
     *
     * Deleting the games is the part that was missing, and its absence is why
     * removing a system did not take. The row was marked not-added and the
     * Platforms page stopped listing it — while several hundred of its games sat
     * in the library, on the grid, inside a folder named after a system the
     * settings said was not installed. The next scan then imported any that had
     * been pruned, so it came back on its own.
     *
     * Recoverable in the only sense that matters: the ROMs are untouched on
     * disk, and adding the system back and rescanning restores all of it.
     */
    suspend fun removePlatform(platformId: String) = withContext(defaultDispatcher) {
        platformDao.setAdded(platformId, false)
        purgePlatformContent(platformId)
    }

    /**
     * Clears out any system that is no longer added, and reports what it removed.
     *
     * Run before every scan files its games, because the invariant it enforces —
     * a game belongs to a platform the user has added — can be broken by any
     * build that predates [removePlatform] doing this. A library that had systems
     * removed under an older version still holds their games, and the filing step
     * rebuilds their folder from those games on every scan: a deleted system
     * reappearing on the grid on its own, indefinitely, with nothing the user can
     * press to stop it.
     *
     * Healing it here rather than in a migration because it is not a schema
     * problem and can be reached again — by a restored backup, or by anything
     * that writes a game without checking. The scan is where the library is made
     * to agree with the settings, so this is where it belongs.
     */
    suspend fun pruneRemovedPlatforms(): List<String> = withContext(defaultDispatcher) {
        val added = platformDao.getAll().filter { it.isAdded }.mapTo(mutableSetOf()) { it.id }
        val orphaned = gameDao.allPlatformIds().filterNot { it in added }

        orphaned.forEach { platformId -> purgePlatformContent(platformId) }
        orphaned
    }

    /**
     * Everything a platform put on the grid, minus the platform row itself.
     *
     * The row is a built-in definition rather than the user's data, and it has to
     * survive for the system to be added again later. Its games, its folder and
     * that folder's cell are all derived from it and go with it — a platform
     * folder with no platform cannot be opened, renamed or removed by any means
     * the interface offers.
     *
     * The ROMs on disk are untouched: adding the system back and rescanning
     * restores all of this.
     */
    private suspend fun purgePlatformContent(platformId: String) {
        /*
         * Placements first, and this is the part that was missing.
         *
         * A placement is keyed by entry id and outlives the row it points at, so
         * deleting the games alone left one stranded for every game the system
         * had. Re-adding the system rescanned the same ROMs to the same ids,
         * which the filing step then read as *already placed* — nothing was
         * fresh, so it created no folder, and the system came back with its games
         * nowhere at all.
         */
        val gameIds = gameDao.idsByPlatform(platformId)
        if (gameIds.isNotEmpty()) gridDao.deleteByEntryIds(gameIds)
        gameDao.deleteByPlatform(platformId)

        val folderId = PlatformFolders.idFor(platformId)
        gridDao.deleteByEntryId(folderId)
        folderDao.deleteById(folderId)

        /*
         * And the artwork the system was wearing.
         *
         * The platform row survives — it is a built-in definition rather than the
         * user's data, and it has to be there to add the system back. What it
         * accumulated does not: scraped icons, a hero, a wordmark and the note of
         * which pack supplied them all describe a library that has just been
         * deleted, and leaving them means a system added back later silently
         * wears whatever it wore before.
         */
        platformDao.clearArtwork(platformId)
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
                        // Clearing the cover does not release the set. The editor
                        // writes the other slots immediately before this and
                        // locks artwork when any of them was chosen by hand, so
                        // unlocking on a missing cover would undo that and hand a
                        // deliberate backdrop back to the scraper. Releasing
                        // artwork outright is [clearGameArtwork]'s job.
                        lockedFields = if (uri != null) {
                            metadata.lockedFields + GameMetadata.FIELD_ARTWORK
                        } else {
                            metadata.lockedFields
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
    /**
     * Hand-picked artwork for one game, which the scrapers must then leave alone.
     *
     * Locking [GameMetadata.FIELD_ARTWORK] is what makes it stick, and it is the
     * same lock the metadata editor uses: every merge already checks it, so a
     * rescrape, an import and a top-up pass all skip a game whose pictures were
     * chosen by hand. Without that the next scrape would quietly undo the choice
     * and there would be no way to tell why.
     *
     * A null for a slot leaves it as it was, so choosing a cover does not clear a
     * backdrop.
     */
    suspend fun setGameArtwork(
        gameId: String,
        coverUri: String? = null,
        heroUri: String? = null,
    ) = withContext(defaultDispatcher) {
        val game = gameDao.getById(gameId) ?: return@withContext
        val metadata = game.toDomain().metadata
        val artwork = metadata.artwork

        gameDao.setMetadata(
            gameId,
            metadata.copy(
                artwork = artwork.copy(
                    // The cover fills both slots the cell can draw from, so the
                    // chosen image is what appears whether or not a square icon
                    // was ever scraped; see `GameMetadata.cellImage`.
                    icon = coverUri ?: artwork.icon,
                    boxArt = coverUri ?: artwork.boxArt,
                    hero = heroUri ?: artwork.hero,
                ),
                lockedFields = metadata.lockedFields + GameMetadata.FIELD_ARTWORK,
            ),
        )
    }

    /**
     * Gives a game back to the scrapers.
     *
     * Clears the images *and* the lock, because leaving the lock would freeze the
     * game in its emptied state — the next scrape would skip it and the reset
     * would look like a deletion rather than a request to try again.
     */
    suspend fun clearGameArtwork(gameId: String) = withContext(defaultDispatcher) {
        val game = gameDao.getById(gameId) ?: return@withContext
        val metadata = game.toDomain().metadata

        gameDao.setMetadata(
            gameId,
            metadata.copy(
                artwork = ArtworkSet.EMPTY,
                lockedFields = metadata.lockedFields - GameMetadata.FIELD_ARTWORK,
            ),
        )
    }

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
    /**
     * Every game the scrapers think this file might be.
     *
     * The search half of a scrape, without the deciding half. Hashes are passed
     * when the game has them, so a provider that indexes by them answers exactly
     * rather than joining in the guesswork — which is usually why the user is
     * here.
     */
    suspend fun matchCandidatesFor(entryId: String): List<MetadataCandidate> =
        withContext(defaultDispatcher) {
            val game = gameDao.getById(entryId) ?: return@withContext emptyList()
            val platform = platformDao.getById(game.platformId)
            aggregator.candidates(
                MetadataQuery(
                    title = game.title,
                    sortTitle = game.sortTitle,
                    platformId = game.platformId,
                    providerPlatformIds = platform?.providerIds.orEmpty(),
                    fileName = game.fileName,
                    fileSizeBytes = game.fileSizeBytes,
                    releaseYearHint = game.metadata.releaseYear,
                    region = game.metadata.region,
                    crc32 = game.romCrc32,
                    md5 = game.romMd5,
                    sha1 = game.romSha1,
                ),
            )
        }

    /**
     * Replaces a game's metadata with one candidate the user chose.
     *
     * Locked field by field inside the aggregator, which is the point of having
     * chosen: a hand-picked match quietly replaced by the next scrape would be
     * worse than not offering the choice at all.
     */
    suspend fun applyChosenMatch(entryId: String, candidate: MetadataCandidate) =
        withContext(defaultDispatcher) {
            val game = gameDao.getById(entryId) ?: return@withContext
            gameDao.upsert(
                game.copy(metadata = aggregator.applyChosen(game.metadata, candidate)),
            )
        }

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
            // The editor can set every picture, not just the cover, so the
            // artwork lock cannot be left to `setCustomIcon` alone — a
            // hand-picked backdrop with no cover would otherwise be replaced by
            // the next scrape. Locked on a *change* rather than on artwork being
            // present, so opening the dialog and pressing Save without touching
            // a picture does not pin whatever the scraper last found.
            if (artworkDiffers(metadata.artwork, game.metadata.artwork)) {
                add(GameMetadata.FIELD_ARTWORK)
            }
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
            // Delegated, so this and the grid agree by construction. The rule lived
            // here alone and nothing ever called it, while the grid resolved a
            // smart folder from stored children that a smart folder does not have.
            // Two evaluations that could drift apart would have been the next bug
            // rather than the fix for this one.
            SmartQueryEvaluator.evaluate(
                entries = gameDao.getVisible().map(GameEntity::toDomain),
                query = query,
                now = System.currentTimeMillis(),
            )
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

    /**
     * Comparator matching a [SortOrder].
     *
     * Kept as a method because the grid calls it through the repository, but the
     * rule itself is [gridEntryComparator] — shared with the smart-folder
     * evaluator, which is pure and cannot reach a repository.
     */
    fun comparatorFor(order: SortOrder, descending: Boolean): Comparator<GridEntry> =
        gridEntryComparator(order, descending)

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/**
 * Whether the entry editor changed any picture it owns.
 *
 * Decides whether a hand edit locks artwork against the next scrape. Locked on a
 * change rather than on artwork merely being present, so opening the dialog and
 * pressing Save without touching a picture does not pin whatever the scraper
 * last found.
 *
 * Box art is excluded because it travels the other path: the editor reports it
 * as the custom icon and `setCustomIcon` handles its lock straight afterwards.
 * Screenshots are compared capped, which is the form the editor is seeded with
 * and writes back; an older library holding more than the cap would otherwise
 * read as edited on every save.
 */
internal fun artworkDiffers(edited: ArtworkSet, stored: ArtworkSet): Boolean =
    edited.icon != stored.icon ||
        edited.hero != stored.hero ||
        edited.logo != stored.logo ||
        edited.cappedScreenshots != stored.cappedScreenshots
