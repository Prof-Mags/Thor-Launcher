package com.thor.data.iconpack

import android.net.Uri
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.datastore.SettingsRepository
import com.thor.data.library.GridLayoutRepository
import com.thor.core.model.IconPack
import com.thor.core.model.PlatformArtwork
import com.thor.core.model.PlatformFolders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs, applies and removes platform icon packs.
 *
 * The importer copies files and the DAO stores rows; this owns the part that is
 * neither — which pack is currently dressing which platform, and what putting one
 * back should restore. That bookkeeping is the whole difficulty: a second pack
 * installed over a first shares platforms with it, and removing either has to
 * leave the other's artwork exactly as it was.
 */
@Singleton
class IconPackRepository @Inject constructor(
    private val importer: IconPackImporter,
    private val platformDao: PlatformDao,
    private val folderDao: FolderDao,
    private val settings: SettingsRepository,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Packs the user installed, which is not everything the list holds.
     *
     * The reserved [PlatformArtwork.USER_PACK_ID] record is a stash of images a
     * pack displaced — see [stashWith] — rather than something installed, and
     * showing it would put a pack in the settings list that cannot be uninstalled
     * and was never installed.
     */
    val installed: Flow<List<IconPack>> = settings.iconPacks.map(::realPacks)

    /**
     * Drops pack records whose artwork is not on disk, and undresses what they
     * dressed.
     *
     * Every real pack is copied into a directory of its own before its record is
     * written, so a record without one is a claim on platforms that nothing can
     * satisfy: the platform rows still name it as their owner, which suppresses
     * the artwork Loki ships, while the URIs they hold resolve to nothing. The
     * result is a system with no icon at all and no way for the user to work out
     * why — the pack looks installed, because its record says so.
     *
     * Reached by a settings backup restored onto a device that has not got the
     * files, by storage being cleared underneath the launcher, and by a build
     * that wrote records for artwork it did not copy.
     *
     * Reverting is [remove]'s job and is done by calling it, so an orphan is put
     * back exactly as an uninstall would put it back — falling to another pack
     * where one covers the platform, rather than stripping it bare.
     */
    suspend fun repairMissingPacks() = withContext(ioDispatcher) {
        val packs = settings.iconPacks.first()
        val missing = orphanedPacks(packs) { importer.hasFiles(it) }

        missing.forEach { pack ->
            ThorLog.w(TAG, "Dropping ${pack.id}: its artwork is not on disk")
            remove(pack.id)
        }
    }

    /** Imports a pack from a granted folder and applies it. */
    suspend fun installFromFolder(treeUri: Uri): IconPackImport =
        install(importer.importFromFolder(treeUri))

    /** Imports a pack from a zip and applies it. */
    suspend fun installFromZip(zipUri: Uri): IconPackImport =
        install(importer.importFromZip(zipUri))

    private suspend fun install(result: IconPackImport): IconPackImport {
        if (result !is IconPackImport.Success) return result
        withContext(ioDispatcher) {
            // Reinstalling replaces the previous record rather than stacking a
            // second one with the same id, which would leave the first's files
            // already deleted by the importer and its rows pointing at nothing.
            settings.updateIconPacks { packs ->
                packs.filterNot { it.id == result.pack.id } + result.pack
            }
            applyToPlatforms(result.pack)
        }
        return result
    }

    /**
     * Writes a pack's artwork onto every platform it covers.
     *
     * Last one installed wins, on every platform the pack covers and without
     * exception, which is the only rule that matches what the user just did:
     * installing a pack is a request to see it.
     *
     * That includes platforms dressed by hand. It did not, once — a hand-picked
     * image was treated as outranking a pack — and the result was a pack that
     * installed, reported success, and visibly changed nothing on the systems the
     * user cared enough about to dress themselves. An exception nobody asked for
     * is indistinguishable from the feature being broken.
     *
     * Displacing is not discarding: what comes off is kept, so uninstalling the
     * pack gives it back. See [stashWith] and [fallbackFor].
     */
    private suspend fun applyToPlatforms(pack: IconPack) {
        val platforms = platformDao.getAll()
        val displaced = mutableMapOf<String, PlatformArtwork>()

        platforms.forEach { platform ->
            val artwork = pack.artworkFor(platform.id) ?: return@forEach

            // Read before the upsert overwrites it — this is the one image the
            // user cannot recover by reinstalling anything.
            if (PlatformArtwork(packId = platform.artworkPackId).isUserChosen) {
                displaced[platform.id] = PlatformArtwork(
                    iconUri = platform.artworkIconUri,
                    heroUri = platform.artworkHeroUri,
                    logoUri = platform.artworkLogoUri,
                    packId = PlatformArtwork.USER_PACK_ID,
                )
            }

            platformDao.upsert(
                platform.copy(
                    artworkIconUri = artwork.iconUri,
                    artworkHeroUri = artwork.heroUri,
                    artworkLogoUri = artwork.logoUri,
                    artworkPackId = pack.id,
                ),
            )
            dressFolder(platform.id, artwork.iconUri)
        }

        if (displaced.isEmpty()) return
        settings.updateIconPacks { packs ->
            realPacks(packs) + stashWith(userStash(packs), displaced)
        }
        ThorLog.i(TAG, "${pack.id} covered ${displaced.size} hand-picked image(s), kept to restore")
    }

    /**
     * Puts a platform's icon on the folder its games are filed into.
     *
     * This is the visible half of "applied automatically": the platform row is
     * where the artwork lives, but the folder on the grid is where the user sees
     * it. Done here rather than in the scanner so that installing a pack dresses
     * the grid immediately, instead of at the next rescan.
     *
     * Folders are only touched if they exist — a platform with no games has no
     * folder, and creating an empty one to hold an icon would put a cell on the
     * grid that opens onto nothing.
     */
    private suspend fun dressFolder(platformId: String, iconUri: String?) {
        val folderId = PlatformFolders.idFor(platformId)
        val folder = folderDao.getById(folderId) ?: return
        if (folder.artworkUri == iconUri) return
        folderDao.upsert(folder.copy(artworkUri = iconUri))
    }

    /**
     * Re-applies installed packs to platforms that have no artwork.
     *
     * Called after the platform table changes. This is what makes the artwork
     * held for platforms THOR did not model at import time actually arrive: add
     * Game Gear months later and the pack that shipped its icon dresses it
     * without the user having to find and re-import that pack — which by then
     * they may no longer have.
     *
     * Only touches platforms with no artwork, so it can never overwrite a choice
     * the user made by installing something else.
     */
    suspend fun applyToNewPlatforms() = withContext(ioDispatcher) {
        val packs = realPacks(settings.iconPacks.first())
        if (packs.isEmpty()) return@withContext

        val bare = platformDao.getAll().filter { it.artworkPackId == null }
        if (bare.isEmpty()) return@withContext

        var dressed = 0
        bare.forEach { platform ->
            // Newest pack first: the most recently installed is the one whose
            // look the user last asked for.
            val match = packs.asReversed().firstNotNullOfOrNull { pack ->
                pack.artworkFor(platform.id)?.let { pack.id to it }
            } ?: return@forEach

            val (packId, artwork) = match
            platformDao.upsert(
                platform.copy(
                    artworkIconUri = artwork.iconUri,
                    artworkHeroUri = artwork.heroUri,
                    artworkLogoUri = artwork.logoUri,
                    artworkPackId = packId,
                ),
            )
            dressFolder(platform.id, artwork.iconUri)
            dressed++
        }
        if (dressed > 0) ThorLog.i(TAG, "Applied held artwork to $dressed new platform(s)")
    }

    /**
     * Removes a pack, its files, and the artwork it applied.
     *
     * Platforms it dressed fall back to whatever *other* installed pack covers
     * them, rather than to nothing — with two packs installed, uninstalling the
     * newer should reveal the older, not strip the platform bare. Failing that,
     * to the image the user picked by hand before any pack covered it.
     */
    suspend fun remove(packId: String) = withContext(ioDispatcher) {
        val current = settings.iconPacks.first()
        val stash = userStash(current)
        val remaining = realPacks(current).filterNot { it.id == packId }

        val handedBack = mutableSetOf<String>()
        platformDao.getAll()
            .filter { it.artworkPackId == packId }
            .forEach { platform ->
                val fallback = fallbackFor(platform.id, remaining, stash)
                if (fallback?.first == PlatformArtwork.USER_PACK_ID) handedBack += platform.id

                val artwork = fallback?.second ?: PlatformArtwork.NONE
                platformDao.upsert(
                    platform.copy(
                        artworkIconUri = artwork.iconUri,
                        artworkHeroUri = artwork.heroUri,
                        artworkLogoUri = artwork.logoUri,
                        artworkPackId = fallback?.first,
                    ),
                )
                dressFolder(platform.id, artwork.iconUri)
            }

        /*
         * What was handed back is on its platform again, and holding a second
         * copy of it here would be a stale one: the user is free to change that
         * image now, and the next uninstall would put this one back over the top
         * of their newer choice.
         */
        val kept = stash
            ?.let { it.copy(artworkBySlug = it.artworkBySlug - handedBack) }
            ?.takeIf { it.artworkBySlug.isNotEmpty() }
        settings.updateIconPacks { remaining + listOfNotNull(kept) }
        // Files last: if anything above fails the pack is still installed and
        // still has its artwork, rather than being a record pointing at a
        // directory that has already gone.
        importer.deleteFiles(packId)
    }

    private companion object {
        const val TAG = "IconPack"
    }
}

/**
 * The packs the user actually installed.
 *
 * [PlatformArtwork.USER_PACK_ID] shares this list because it answers the same
 * question every pack does — who put this artwork here — but it is not one: it has
 * no files, cannot be uninstalled, and must never be offered as a fallback ahead
 * of something the user chose more recently.
 */
internal fun realPacks(packs: List<IconPack>): List<IconPack> =
    packs.filterNot { it.id == PlatformArtwork.USER_PACK_ID }

/** The stash of hand-picked images that packs have covered over, if there is one. */
internal fun userStash(packs: List<IconPack>): IconPack? =
    packs.firstOrNull { it.id == PlatformArtwork.USER_PACK_ID }

/**
 * The stash, updated with what a pack has just covered over.
 *
 * Keyed by platform id rather than by the pack's slug, because this is not a pack's
 * naming — it is a record of what was on a platform, and the platform is the only
 * thing that identifies it.
 *
 * Newer displacements win. The case that decides it: a user picks an image by hand,
 * a pack covers it, they pick a *different* image by hand, and a second pack covers
 * that. Keeping the first would hand back an image they had already replaced, which
 * looks exactly like the launcher losing their change.
 */
internal fun stashWith(existing: IconPack?, displaced: Map<String, PlatformArtwork>): IconPack {
    val base = existing ?: IconPack(
        id = PlatformArtwork.USER_PACK_ID,
        name = "Images you picked",
        author = "",
        version = "1",
    )
    return base.copy(artworkBySlug = base.artworkBySlug + displaced)
}

/**
 * What a platform should wear once the pack dressing it is uninstalled.
 *
 * Ordered by how recently the user asked for each thing. Another installed pack
 * first, newest first among those — uninstalling the top pack of two should reveal
 * the one underneath. Only when no pack covers this platform at all does the
 * hand-picked image come back, because by then it is the most recent choice left.
 *
 * Null means nothing is left to put back, and the platform falls through to the
 * artwork Loki ships. That is a real outcome, not a failure.
 */
internal fun fallbackFor(
    platformId: String,
    remaining: List<IconPack>,
    stash: IconPack?,
): Pair<String, PlatformArtwork>? =
    remaining.asReversed().firstNotNullOfOrNull { pack ->
        pack.artworkFor(platformId)?.let { pack.id to it }
    } ?: stash?.artworkBySlug?.get(platformId)?.let { PlatformArtwork.USER_PACK_ID to it }

/**
 * Pack records describing artwork that is not there.
 *
 * Top level and taking [hasFiles] as a function rather than reading the disk
 * itself, so the rule can be tested without a filesystem — the rule is the part
 * worth holding still, and getting it wrong is expensive in both directions: too
 * eager strips artwork a user installed, too shy leaves systems permanently
 * blank with no way to find out why.
 */
internal fun orphanedPacks(
    packs: List<IconPack>,
    hasFiles: (packId: String) -> Boolean,
): List<IconPack> = packs
    /*
     * Never a record, always an owner written straight onto a platform.
     *
     * Excluded explicitly rather than trusted not to appear: were one ever to
     * turn up in this list, it would have no directory — nothing copies files
     * for it — so it would be read as an orphan and removing it would erase
     * every image the user picked by hand. That is the one thing they cannot
     * get back, and the check costs a line.
     */
    .filterNot { it.id == PlatformArtwork.USER_PACK_ID }
    .filterNot { hasFiles(it.id) }
