package com.thor.data.sync

import com.thor.core.model.Platform
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.database.dao.AppDao
import com.thor.core.database.dao.GameDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.common.profile.ActiveProfileId
import com.thor.core.datastore.SettingsRepository
import com.thor.data.iconpack.IconPackRepository
import com.thor.data.library.GridLayoutRepository
import com.thor.data.library.LibraryRepository
import com.thor.data.library.toDomain
import com.thor.data.scanner.AppScanner
import com.thor.data.scanner.RomScanner
import com.thor.data.scanner.ScanProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Coarse status of the library, for the UI to show progress. */
sealed interface SyncState {
    data object Idle : SyncState
    data class Scanning(val label: String, val filesSeen: Int, val found: Int) : SyncState
    data class Completed(val appsFound: Int, val gamesFound: Int, val durationMillis: Long) :
        SyncState

    data class Failed(val message: String) : SyncState
}

/**
 * Coordinates library scanning.
 *
 * A scan is a three-phase operation — enumerate, reconcile, place — and all
 * three have to happen together or the grid ends up referencing entries that
 * were never written. Keeping the sequence here means the UI only ever asks for
 * "a scan" and observes [state].
 */
@Singleton
class LibrarySyncManager @Inject constructor(
    private val appScanner: AppScanner,
    private val romScanner: RomScanner,
    private val appDao: AppDao,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val libraryRepository: LibraryRepository,
    private val gridRepository: GridLayoutRepository,
    private val iconPackRepository: IconPackRepository,
    private val settings: SettingsRepository,
    @ActiveProfileId profileIds: Flow<String>,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var runningJob: Job? = null

    init {
        /*
         * Every profile gets the installed applications, including a brand new one.
         *
         * Installed apps are a fact about the device, not a preference — the same
         * packages exist for everyone using the handheld — but each profile keeps
         * its own library database, so nothing would have written them into a
         * profile created after this one started. Its drawer would simply be
         * empty, and no amount of looking through settings would explain why.
         *
         * The scan is a package-manager enumeration, so re-running it per profile
         * costs nothing worth saving. What stays per profile is what each person
         * did with those apps: which are hidden, and where they sit on the grid.
         */
        profileIds
            .filter(String::isNotEmpty)
            .distinctUntilChanged()
            .onEach { requestAppRefresh() }
            .launchIn(scope)
    }

    /** True while a scan is in flight. */
    val isScanning: Boolean get() = runningJob?.isActive == true

    /**
     * Starts a full scan.
     *
     * Re-entrant calls are ignored rather than queued: two concurrent scans
     * would race on the same tables, and the second would find nothing new.
     */
    fun requestFullScan() {
        if (isScanning) {
            ThorLog.d(TAG) { "Scan already running; ignoring request" }
            return
        }
        /*
         * [launchSafely] rather than `runCatching`, which catches `Throwable` and so
         * also catches `CancellationException` — reporting a scan the launcher itself
         * stopped as a scan that failed, and breaking structured concurrency on the
         * way. Cancellation is rethrown here; only real errors reach [onError].
         */
        runningJob = scope.launchSafely(
            tag = TAG,
            onError = { error ->
                _state.value = SyncState.Failed(error.message ?: "Scan failed")
            },
        ) {
            fullScan()
        }
    }

    /**
     * Refreshes the installed-application list only.
     *
     * Run on every launcher start. Enumerating packages is fast and must happen
     * unconditionally — otherwise a fresh install shows an empty grid and an
     * empty app drawer until the user goes looking for a scan command. ROM
     * directories are left alone here because walking them is expensive and
     * their contents do not change behind the launcher's back.
     */
    fun requestAppRefresh() {
        if (isScanning) return
        runningJob = scope.launchSafely(TAG) { refreshApps() }
    }

    private suspend fun refreshApps() = withContext(ioDispatcher) {
        libraryRepository.ensurePlatformsSeeded()
        // After the seed, because it writes to platform rows those must exist.
        // A platform held by a pack that is not there shows no artwork at all,
        // so this runs before the grid is drawn rather than at the next scan.
        iconPackRepository.repairMissingPacks()
        val librarySettings = settings.library.first()

        val apps = appScanner.scan(includeSystemApps = !librarySettings.hideSystemApps)
        val knownAppIds = appDao.allIds().toSet()
        val scannedAppIds = apps.mapTo(mutableSetOf()) { it.id }

        appDao.upsertAll(apps)
        appDao.deleteByIds((knownAppIds - scannedAppIds).toList())

        gridRepository.pruneOrphans()
        fileGamesByPlatform()
        // Applications land on the grid only when the user has asked for all of them.
        // Otherwise they stay in the drawer until added by hand, and the grid is not
        // filled with system utilities on first run.
        if (librarySettings.showAppsOnGrid) {
            gridRepository.placeUnplacedEntries(appDao.allIds())
        }
    }

    /**
     * Puts newly found games into their platform's folder.
     *
     * Games go to a folder rather than onto the grid because a platform arrives all at
     * once — adding one system can be hundreds of ROMs, and dropping those onto pages
     * buries whatever was there. Only games with no placement are moved, so this is
     * safe to run after every scan: anything the user has since arranged themselves
     * stays arranged.
     */
    private suspend fun fileGamesByPlatform() {
        val platforms = platformDao.getAll().map { it.toDomain() }
        val added = platforms.filter(Platform::isAdded).map(Platform::id).toSet()

        val titles = platforms.associate { platform ->
            // The short label: it is a grid cell, and "SNES" fits where "Super
            // Nintendo Entertainment System" would be three ellipsised words.
            platform.id to platform.shortName.ifBlank { platform.name }
        }
        // So a folder created by this scan is already wearing its platform's icon,
        // rather than appearing blank until something else dresses it.
        val icons = platforms.associate { it.id to it.artwork.iconUri }

        /*
         * Games whose system is no longer added go, before anything is filed.
         *
         * Without this the filing below re-creates the folder on every scan out
         * of games that should not still be there — a deleted system reappearing
         * on the grid by itself, indefinitely. See [pruneRemovedPlatforms].
         */
        libraryRepository.pruneRemovedPlatforms().forEach { platformId ->
            ThorLog.i(TAG, "Removed $platformId: no longer an added platform")
        }

        val gamesByPlatform = gameDao.getVisible().groupBy(
            keySelector = { it.platformId },
            valueTransform = { it.id },
        )

        gridRepository.fileGamesIntoPlatformFolders(
            gamesByPlatform = gamesByPlatform,
            titleFor = { platformId -> titles[platformId] ?: platformId },
            artworkFor = { platformId -> icons[platformId] },
        )

        // Last, because everything above can empty pages: a scan that finds a
        // system gone leaves behind however many pages its games occupied.
        val pruned = gridRepository.pruneEmptyPages()
        if (pruned > 0) ThorLog.i(TAG, "Removed $pruned empty page(s) from the grid")
    }

    fun cancelScan() {
        runningJob?.cancel()
        runningJob = null
        _state.value = SyncState.Idle
    }

    private suspend fun fullScan() = withContext(ioDispatcher) {
        val startedAt = System.currentTimeMillis()
        libraryRepository.ensurePlatformsSeeded()

        val librarySettings = settings.library.first()

        // --- Applications -------------------------------------------------
        _state.value = SyncState.Scanning("Applications", 0, 0)
        val apps = appScanner.scan(includeSystemApps = !librarySettings.hideSystemApps)
        val knownAppIds = appDao.allIds().toSet()
        val scannedAppIds = apps.mapTo(mutableSetOf()) { it.id }

        appDao.upsertAll(apps)
        // Uninstalled apps are removed outright; keeping ghosts on the grid is
        // worse than losing their placement.
        appDao.deleteByIds((knownAppIds - scannedAppIds).toList())

        /*
         * --- ROMs ---------------------------------------------------------
         *
         * Only the systems the user has actually added.
         *
         * This passed *every* built-in platform, so the extension index matched
         * `.nes` to the NES whether or not the NES had been added — which meant
         * removing a system did nothing to scanning. The row was marked
         * not-added, the Platforms page stopped listing it, and the very next
         * scan imported all of its games again and filed them into a folder on
         * the grid. From the front that is a system you deleted coming back
         * while the settings insist you never had it.
         *
         * `isAdded` is the user saying which systems they own. Recognition was
         * never the thing that should ignore it.
         */
        val platforms = platformDao.getAll()
            .map { it.toDomain() }
            .filter(Platform::isAdded)
        var gamesFound = 0

        if (librarySettings.romDirectoryUris.isNotEmpty()) {
            romScanner.scan(
                directories = librarySettings.romDirectoryUris,
                platforms = platforms,
                groupVersions = librarySettings.groupVersions,
                scanArchives = librarySettings.scanArchives,
            ).collect { progress ->
                when (progress) {
                    is ScanProgress.Started -> Unit

                    is ScanProgress.Scanning -> {
                        _state.value = SyncState.Scanning(
                            label = progress.directoryName,
                            filesSeen = progress.filesSeen,
                            found = progress.gamesFound,
                        )
                    }

                    is ScanProgress.Completed -> {
                        gamesFound = progress.games.size
                        reconcileGames(progress.games, progress.versions, librarySettings.detectDuplicates)
                    }

                    is ScanProgress.Failed -> {
                        ThorLog.w(TAG, "ROM scan reported failure: ${progress.message}")
                    }
                }
            }
        }

        // --- Grid reconciliation -------------------------------------------
        gridRepository.pruneOrphans()
        fileGamesByPlatform()
        if (librarySettings.showAppsOnGrid) {
            gridRepository.placeUnplacedEntries(appDao.allIds())
        }

        _state.value = SyncState.Completed(
            appsFound = apps.size,
            gamesFound = gamesFound,
            durationMillis = System.currentTimeMillis() - startedAt,
        )
    }

    /**
     * Writes scanned games, preserving anything the user or the scraper already
     * established.
     *
     * A rescan must not wipe metadata, favourites or play statistics, so
     * existing rows are updated in place and only their file-derived fields are
     * refreshed.
     */
    private suspend fun reconcileGames(
        scanned: List<com.thor.core.database.model.GameEntity>,
        versions: List<com.thor.core.database.model.GameVersionEntity>,
        detectDuplicates: Boolean,
    ) {
        val deduped = if (detectDuplicates) {
            scanned.distinctBy(com.thor.core.database.model.GameEntity::duplicateKey)
        } else {
            scanned
        }

        val merged = deduped.map { fresh ->
            val existing = gameDao.getById(fresh.id)
            if (existing == null) {
                fresh
            } else {
                existing.copy(
                    // File facts come from the scan.
                    contentUri = fresh.contentUri,
                    fileName = fresh.fileName,
                    fileSizeBytes = fresh.fileSizeBytes,
                    isMissing = false,
                    // Everything else — metadata, favourite, stats — is retained.
                )
            }
        }

        gameDao.upsertAll(merged)
        gameDao.upsertVersions(versions)

        // Files that vanished are flagged rather than deleted, so their
        // metadata and play history survive an unmounted SD card.
        val scannedIds = merged.mapTo(mutableSetOf()) { it.id }
        val missing = gameDao.allIds().filterNot { it in scannedIds }
        if (missing.isNotEmpty()) gameDao.setMissing(missing, true)
    }

    private companion object {
        const val TAG = "LibrarySync"
    }
}
