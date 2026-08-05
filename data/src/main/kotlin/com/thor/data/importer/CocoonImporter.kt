package com.thor.data.importer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.database.dao.GameDao
import com.thor.core.datastore.ProfileRegistryRepository
import com.thor.core.model.ArtworkSet
import com.thor.data.library.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What an import did, for the row that started it. */
sealed interface CocoonImportResult {
    data class Success(val games: Int, val images: Int) : CocoonImportResult
    data object NothingFound : CocoonImportResult
    data class Failed(val reason: String) : CocoonImportResult
}

/**
 * Copies artwork out of another launcher's media folder and onto this library.
 *
 * Worth having because the data is already *matched*. Every scraper in here has
 * to guess which game a ROM is; a folder the user has already curated has made
 * that decision correctly, once, by hand. Importing it skips the entire class of
 * problem that produced the wrong covers.
 *
 * Files are copied rather than referenced. A tree permission does not survive a
 * reboot on every device, the source folder is in Downloads and will be deleted,
 * and artwork that vanishes later is worse than artwork that took a moment to
 * arrive. They land in the profile's own directory, so two profiles importing
 * the same folder do not share files that either could delete.
 */
@Singleton
class CocoonImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val profiles: ProfileRegistryRepository,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun import(treeUri: Uri, replaceExisting: Boolean = true): CocoonImportResult =
        withContext(ioDispatcher) {
            val scanned = runCatching { scan(treeUri) }.getOrElse { error ->
                ThorLog.w(TAG, "Could not read the media folder", error)
                return@withContext CocoonImportResult.Failed("Could not read that folder")
            }
            if (scanned.isEmpty()) return@withContext CocoonImportResult.NothingFound

            // One snapshot rather than the observed flow: an import is a single
            // pass over the library as it stands, and re-reading it while writing
            // to it is a way to process a game twice.
            val games = gameDao.observeAll().first()
            if (games.isEmpty()) return@withContext CocoonImportResult.NothingFound

            val targets = games.map { game ->
                ImportTarget(
                    entryId = game.id,
                    title = game.title,
                    fileName = game.fileName,
                )
            }
            val matches = matchCocoonTitles(targets, scanned.keys)
            if (matches.isEmpty()) return@withContext CocoonImportResult.NothingFound

            val profileId = profiles.activeProfileId.first()
            var copied = 0
            var updated = 0

            matches.forEach { match ->
                val chosen = selectCocoonArtwork(scanned[match.cocoonTitle].orEmpty())
                if (chosen.isEmpty) return@forEach

                val entity = games.firstOrNull { it.id == match.entryId } ?: return@forEach
                val local = chosen.copiedInto(profileId, match.entryId) { copied++ }
                if (local.isEmpty) return@forEach

                // Only the metadata column is written, which is the same narrow
                // update the scraper makes — rebuilding the whole row would
                // overwrite anything a scan changed while this was running.
                val current = entity.toDomain().metadata
                val merged = merge(current.artwork, local, replaceExisting)
                gameDao.setMetadata(match.entryId, current.copy(artwork = merged))
                updated++
            }

            if (updated == 0) {
                CocoonImportResult.NothingFound
            } else {
                CocoonImportResult.Success(games = updated, images = copied)
            }
        }

    /**
     * Reads the tree into "title to its images", ignoring everything else.
     *
     * The layout is `<platform>/<class>/<name>`, and the platform level is
     * deliberately *not* used to filter. Cocoon's folder is named for its own
     * platform slugs, which do not have to agree with ours, and the game titles
     * are unambiguous enough on their own — matching by name across the whole
     * folder finds a game filed under a system name we would not have guessed.
     */
    private fun scan(treeUri: Uri): Map<String, List<CocoonImage>> {
        val byTitle = mutableMapOf<String, MutableList<CocoonImage>>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)

        queryChildren(treeUri, rootId) { _, platformId, platformMime, _ ->
            if (platformMime != DocumentsContract.Document.MIME_TYPE_DIR) return@queryChildren

            queryChildren(treeUri, platformId) { className, classId, classMime, _ ->
                if (classMime != DocumentsContract.Document.MIME_TYPE_DIR) return@queryChildren
                val slot = CocoonSlot.of(className) ?: return@queryChildren

                queryChildren(treeUri, classId) { fileName, fileId, fileMime, size ->
                    if (fileMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return@queryChildren
                    }
                    // Add-on artwork reduces to the base game's title and would
                    // otherwise take its slots; see [isCocoonDlc].
                    if (isCocoonDlc(fileName)) return@queryChildren

                    val title = cocoonTitleOf(fileName)
                    if (title.isBlank()) return@queryChildren

                    val document = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileId)
                    byTitle.getOrPut(title) { mutableListOf() }.add(
                        CocoonImage(
                            title = title,
                            slot = slot,
                            source = document.toString(),
                            // Carried through because it is what tells one
                            // picture from three copies of it; see
                            // [selectCocoonArtwork].
                            sizeBytes = size,
                        ),
                    )
                }
            }
        }
        return byTitle
    }

    /**
     * Copies the chosen files in, returning what landed where.
     *
     * A file that fails to copy is dropped from the result rather than failing
     * the import: one unreadable image should cost that image, not the other
     * two hundred.
     */
    private fun CocoonArtwork.copiedInto(
        profileId: String,
        entryId: String,
        onCopied: () -> Unit,
    ): CocoonArtwork {
        val directory = File(ProfileFiles.directory(context, profileId), IMPORT_DIRECTORY)
        directory.mkdirs()

        fun copy(source: String?, suffix: String): String? {
            if (source == null) return null
            val destination = File(directory, "${entryId.hashCode()}-$suffix.img")
            return runCatching {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: return null
                onCopied()
                Uri.fromFile(destination).toString()
            }.getOrElse { error ->
                ThorLog.d(TAG) { "Could not copy $suffix for $entryId: ${error.message}" }
                destination.delete()
                null
            }
        }

        return CocoonArtwork(
            icon = copy(icon, "icon"),
            hero = copy(hero, "hero"),
            logo = copy(logo, "logo"),
            screenshots = screenshots.mapIndexedNotNull { index, source ->
                copy(source, "shot$index")
            },
        )
    }

    /**
     * Folds the imported files into what the game already has.
     *
     * Box art is deliberately untouched. Cocoon has no equivalent class, so
     * there is nothing to put there, and blanking it would trade a cover the
     * scrapers found for nothing at all.
     */
    private fun merge(
        existing: ArtworkSet,
        imported: CocoonArtwork,
        replaceExisting: Boolean,
    ): ArtworkSet {
        fun slot(current: String?, fresh: String?): String? =
            if (replaceExisting) fresh ?: current else current ?: fresh

        return existing.copy(
            icon = slot(existing.icon, imported.icon),
            hero = slot(existing.hero, imported.hero),
            logo = slot(existing.logo, imported.logo),
            screenshots = when {
                imported.screenshots.isEmpty() -> existing.screenshots
                replaceExisting -> imported.screenshots
                else -> (existing.screenshots + imported.screenshots)
                    .distinct()
                    .take(ArtworkSet.MAX_SCREENSHOTS)
            },
        )
    }

    private fun queryChildren(
        treeUri: Uri,
        documentId: String,
        onChild: (name: String, documentId: String, mime: String?, size: Long) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                onChild(name, id, cursor.getString(2), cursor.getLong(3))
            }
        }
    }

    private companion object {
        const val TAG = "CocoonImport"

        /** Kept apart from the profile's other files so an import can be undone. */
        const val IMPORT_DIRECTORY = "imported-artwork"
    }
}
