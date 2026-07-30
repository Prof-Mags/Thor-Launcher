package com.thor.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.model.GameEntity
import com.thor.core.database.model.GameVersionEntity
import com.thor.core.model.BuiltInPlatforms
import com.thor.core.model.GameMetadata
import com.thor.core.model.Platform
import com.thor.core.model.RomDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Progress reported while a scan runs. */
sealed interface ScanProgress {
    data class Started(val directoryCount: Int) : ScanProgress
    data class Scanning(
        val directoryName: String,
        val filesSeen: Int,
        val gamesFound: Int,
    ) : ScanProgress

    data class Completed(
        val games: List<GameEntity>,
        val versions: List<GameVersionEntity>,
        val filesSeen: Int,
        val durationMillis: Long,
    ) : ScanProgress

    data class Failed(val message: String, val cause: Throwable?) : ScanProgress
}

/**
 * Walks the user's ROM directories and builds library entries.
 *
 * Traversal uses the storage access framework's bulk child query rather than
 * `DocumentFile.listFiles()`. `DocumentFile` issues one IPC per file for each
 * attribute it reads, which turns a 5,000-ROM set into tens of thousands of
 * round trips; querying the children cursor directly reads every attribute for
 * a whole directory in one call and keeps a full scan to a few seconds.
 */
@Singleton
class RomScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Scans every enabled directory.
     *
     * @param directories the user's configured ROM locations
     * @param platforms all known platforms, used for extension matching
     * @param groupVersions when true, regional variants and revisions of a title
     *   collapse into one entry with alternates attached
     */
    fun scan(
        directories: List<RomDirectory>,
        platforms: List<Platform>,
        groupVersions: Boolean,
        scanArchives: Boolean,
    ): Flow<ScanProgress> = flow {
        val startedAt = System.currentTimeMillis()
        val enabled = directories.filter(RomDirectory::enabled)
        emit(ScanProgress.Started(enabled.size))

        val extensionIndex = buildExtensionIndex(platforms)
        val found = mutableListOf<ScannedRom>()
        var filesSeen = 0

        for (directory in enabled) {
            currentCoroutineContext().ensureActive()
            val treeUri = runCatching { Uri.parse(directory.uri) }.getOrNull()
            if (treeUri == null) {
                ThorLog.w("RomScanner", "Skipping malformed directory URI: ${directory.uri}")
                continue
            }

            try {
                traverse(
                    treeUri = treeUri,
                    directory = directory,
                    extensionIndex = extensionIndex,
                    scanArchives = scanArchives,
                    onFile = { filesSeen++ },
                    onRom = { found += it },
                    onProgress = { name ->
                        emit(ScanProgress.Scanning(name, filesSeen, found.size))
                    },
                )
            } catch (e: SecurityException) {
                // A revoked SAF grant is a normal, recoverable condition — the
                // user cleared app data or moved an SD card.
                ThorLog.w("RomScanner", "Lost access to ${directory.displayName}", e)
            }
        }

        val (games, versions) = assemble(found, groupVersions)
        emit(
            ScanProgress.Completed(
                games = games,
                versions = versions,
                filesSeen = filesSeen,
                durationMillis = System.currentTimeMillis() - startedAt,
            ),
        )
    }.flowOn(ioDispatcher)

    /** One ROM file discovered on disk, before de-duplication. */
    private data class ScannedRom(
        val uri: String,
        val fileName: String,
        val sizeBytes: Long,
        val platformId: String,
        val lastModified: Long,
    )

    /**
     * Depth-first walk of a document tree.
     *
     * Written iteratively with an explicit stack rather than recursively: ROM
     * collections are often deeply nested by platform and region, and a
     * recursive walk risks a stack overflow on pathological layouts.
     */
    private suspend fun traverse(
        treeUri: Uri,
        directory: RomDirectory,
        extensionIndex: Map<String, String>,
        scanArchives: Boolean,
        onFile: () -> Unit,
        onRom: (ScannedRom) -> Unit,
        onProgress: suspend (String) -> Unit,
    ) {
        val rootDocId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return

        // Each stack frame is (documentId, platform hint inherited from folder name).
        val stack = ArrayDeque<Pair<String, String?>>()
        stack.addLast(rootDocId to directory.platformId)

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (documentId, inheritedPlatform) = stack.removeLast()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            ) ?: continue

            cursor.use { c ->
                val idIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex =
                    c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val childId = c.getString(idIndex) ?: continue
                    val name = c.getString(nameIndex) ?: continue
                    val mime = c.getString(mimeIndex)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (directory.recursive) {
                            // A folder named after a platform scopes everything
                            // beneath it, which is how most ROM sets are laid out.
                            val hint = platformHintFor(name) ?: inheritedPlatform
                            stack.addLast(childId to hint)
                        }
                        continue
                    }

                    onFile()

                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension.isEmpty()) continue

                    val isArchive = extension in BuiltInPlatforms.ARCHIVE_EXTENSIONS
                    if (isArchive && !scanArchives) continue

                    // An explicit directory assignment wins; then a folder-name
                    // hint; then the extension, which is ambiguous for archives
                    // and for the many platforms sharing `.iso` / `.bin`.
                    val platformId = inheritedPlatform
                        ?: extensionIndex[extension]
                        ?: continue

                    onRom(
                        ScannedRom(
                            uri = DocumentsContract
                                .buildDocumentUriUsingTree(treeUri, childId)
                                .toString(),
                            fileName = name,
                            sizeBytes = c.getLong(sizeIndex),
                            platformId = platformId,
                            lastModified = c.getLong(modifiedIndex),
                        ),
                    )
                }
            }

            onProgress(directory.displayName)
        }
    }

    /**
     * Collapses discovered files into library entries.
     *
     * When [groupVersions] is on, files sharing a normalised title within one
     * platform become a single entry: the largest file (usually the most
     * complete dump) becomes primary and the rest are attached as alternates.
     */
    private fun assemble(
        found: List<ScannedRom>,
        groupVersions: Boolean,
    ): Pair<List<GameEntity>, List<GameVersionEntity>> {
        val now = System.currentTimeMillis()
        val games = mutableListOf<GameEntity>()
        val versions = mutableListOf<GameVersionEntity>()

        // Grouping key differs by mode, but every group is still a list of files
        // that must collapse to one entry, so the body below is shared.
        val groups: Collection<List<ScannedRom>> = if (groupVersions) {
            found.groupBy { rom ->
                rom.platformId to TitleNormalizer.sortKey(TitleNormalizer.displayTitle(rom.fileName))
            }.values
        } else {
            found.map(::listOf)
        }

        for (roms in groups) {
            if (roms.isEmpty()) continue

            // Prefer the largest dump, tie-breaking on the most recent file, so
            // grouping is deterministic across rescans.
            val primary = roms.maxWithOrNull(
                compareBy<ScannedRom> { it.sizeBytes }.thenBy { it.lastModified },
            ) ?: roms.first()

            val platformId = primary.platformId
            val title = TitleNormalizer.displayTitle(primary.fileName)
            val sortKey = TitleNormalizer.sortKey(title)
            // Ungrouped scans can produce several files with the same title, so
            // the id is qualified by the file to stay unique.
            val gameId = if (groupVersions) {
                gameEntryId(platformId, sortKey)
            } else {
                "${gameEntryId(platformId, sortKey)}:${primary.uri.hashCode()}"
            }

            games += GameEntity(
                id = gameId,
                title = title,
                sortTitle = sortKey,
                platformId = platformId,
                contentUri = primary.uri,
                fileName = primary.fileName,
                fileSizeBytes = primary.sizeBytes,
                duplicateKey = "$platformId:$sortKey",
                metadata = GameMetadata(
                    region = TitleNormalizer.region(primary.fileName),
                ),
                addedAtEpochMs = now,
            )

            roms.asSequence()
                .filter { it.uri != primary.uri }
                .forEach { alternate ->
                    versions += GameVersionEntity(
                        id = "$gameId:${alternate.uri.hashCode()}",
                        gameId = gameId,
                        label = TitleNormalizer.versionLabel(alternate.fileName),
                        contentUri = alternate.uri,
                        fileName = alternate.fileName,
                        fileSizeBytes = alternate.sizeBytes,
                        region = TitleNormalizer.region(alternate.fileName),
                        discNumber = TitleNormalizer.discNumber(alternate.fileName),
                    )
                }
        }

        return games.sortedBy(GameEntity::sortTitle) to versions
    }

    /**
     * Extension -> platform id.
     *
     * Extensions claimed by more than one platform (`.iso`, `.bin`, `.cue`) are
     * left out entirely: guessing between PS2 and GameCube from `.iso` alone is
     * a coin flip, so those files are only imported when the directory or a
     * parent folder names the platform.
     */
    private fun buildExtensionIndex(platforms: List<Platform>): Map<String, String> {
        val counts = mutableMapOf<String, MutableList<String>>()
        platforms.forEach { platform ->
            platform.romExtensions.forEach { ext ->
                counts.getOrPut(ext) { mutableListOf() }.add(platform.id)
            }
        }
        return counts
            .filterValues { it.size == 1 }
            .filterKeys { it !in BuiltInPlatforms.ARCHIVE_EXTENSIONS }
            .mapValues { it.value.first() }
    }

    /** Matches a folder name against known platform names and short names. */
    private fun platformHintFor(folderName: String): String? {
        val normalized = folderName.lowercase(Locale.ROOT).trim()
        return BuiltInPlatforms.ALL.firstOrNull { platform ->
            normalized == platform.id ||
                normalized == platform.shortName.lowercase(Locale.ROOT) ||
                normalized == platform.name.lowercase(Locale.ROOT)
        }?.id
    }

    companion object {
        /**
         * Game ids are derived from platform and normalised title, so a rescan
         * after moving files keeps grid placements and play statistics intact.
         */
        fun gameEntryId(platformId: String, sortKey: String): String = "game:$platformId:$sortKey"
    }
}
