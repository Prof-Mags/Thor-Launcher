package com.thor.data.iconpack

import android.net.Uri
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.database.dao.FolderDao
import com.thor.core.database.dao.PlatformDao
import com.thor.core.datastore.SettingsRepository
import com.thor.data.repository.GridLayoutRepository
import com.thor.core.model.IconPack
import com.thor.core.model.PlatformArtwork
import com.thor.core.model.PlatformFolders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    val installed: Flow<List<IconPack>> = settings.iconPacks

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
     * Last one installed wins, which is the only rule that matches what the user
     * just did: installing a pack is a request to see it.
     */
    private suspend fun applyToPlatforms(pack: IconPack) {
        val platforms = platformDao.getAll()
        platforms.forEach { platform ->
            val artwork = pack.artworkFor(platform.id) ?: return@forEach
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
        val packs = settings.iconPacks.first()
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
     * newer should reveal the older, not strip the platform bare.
     */
    suspend fun remove(packId: String) = withContext(ioDispatcher) {
        val remaining = settings.iconPacks.first().filterNot { it.id == packId }

        platformDao.getAll()
            .filter { it.artworkPackId == packId }
            .forEach { platform ->
                val fallback = remaining.asReversed().firstNotNullOfOrNull { pack ->
                    pack.artworkFor(platform.id)?.let { pack.id to it }
                }
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

        settings.updateIconPacks { remaining }
        // Files last: if anything above fails the pack is still installed and
        // still has its artwork, rather than being a record pointing at a
        // directory that has already gone.
        importer.deleteFiles(packId)
    }

    private companion object {
        const val TAG = "IconPack"
    }
}
