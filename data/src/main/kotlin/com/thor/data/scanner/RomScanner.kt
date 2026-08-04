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
import com.thor.core.model.IconPackSlugs
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
        val platformsById = platforms.associateBy(Platform::id)
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
                    platforms = platforms,
                    platformsById = platformsById,
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
        platforms: List<Platform>,
        platformsById: Map<String, Platform>,
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

            /*
             * The directory is read whole before any of it is judged.
             *
             * Two of the rules below are about a file's *neighbours* rather than
             * about the file: a disc's tracks are only recognisable as tracks
             * because a sheet naming them sits beside them. Deciding as the
             * cursor walked meant every rule could only see one row at a time.
             */
            val children = mutableListOf<Child>()
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
                    children += Child(
                        documentId = childId,
                        name = name,
                        isDirectory = c.getString(mimeIndex) ==
                            DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = c.getLong(sizeIndex),
                        lastModified = c.getLong(modifiedIndex),
                    )
                }
            }

            val (directories, files) = children.partition(Child::isDirectory)

            if (directory.recursive) {
                directories.forEach { child ->
                    // A folder named after a platform scopes everything beneath
                    // it, which is how most ROM sets are laid out.
                    val hint = platformHintFor(child.name, platforms) ?: inheritedPlatform
                    stack.addLast(child.documentId to hint)
                }
            }

            val shadowed = discTracksShadowedBySheet(files.map(Child::name))

            for (child in files) {
                onFile()

                val extension = child.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension.isEmpty()) continue

                val isArchive = extension in BuiltInPlatforms.ARCHIVE_EXTENSIONS
                if (isArchive && !scanArchives) continue

                // A disc's own tracks are not games. See [discTracksShadowedBySheet].
                if (child.name in shadowed) continue

                // An explicit directory assignment wins; then a folder-name
                // hint; then the extension, which is ambiguous for archives
                // and for the many platforms sharing `.iso` / `.bin`.
                val platformId = inheritedPlatform
                    ?: extensionIndex[extension]
                    ?: continue

                /*
                 * A folder says which system its games are for, not that
                 * everything in it is a game.
                 *
                 * Without this the hint alone was enough: every file under a
                 * folder named for a platform — saves, save states, box art,
                 * `.txt` notes, the `.srm` beside each ROM — became a library
                 * entry with a title taken off its filename. A tidy ROM set
                 * scanned to two or three times its real size, and the extra
                 * entries looked exactly like real ones.
                 *
                 * Archives stay exempt: a zipped ROM's extension describes the
                 * container, not the system inside it.
                 */
                if (!isArchive && !platformAcceptsExtension(platformsById[platformId], extension)) {
                    continue
                }

                onRom(
                    ScannedRom(
                        uri = DocumentsContract
                            .buildDocumentUriUsingTree(treeUri, child.documentId)
                            .toString(),
                        fileName = child.name,
                        sizeBytes = child.sizeBytes,
                        platformId = platformId,
                        lastModified = child.lastModified,
                    ),
                )
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

    /** One row of a directory listing, read before any of it is judged. */
    private data class Child(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long,
    )

    companion object {
        /**
         * Game ids are derived from platform and normalised title, so a rescan
         * after moving files keeps grid placements and play statistics intact.
         */
        fun gameEntryId(platformId: String, sortKey: String): String = "game:$platformId:$sortKey"
    }
}

/**
 * The platform a folder name is announcing, or null if it announces none.
 *
 * Restricted to the platforms actually being scanned — which is the systems the
 * user has *added*. The extension index was fixed to respect that and this was
 * not, so a folder named for a system the user had removed still scoped every
 * file beneath it and imported the lot: the Platforms page said the system was
 * gone while the very next scan filed its games back onto the grid.
 *
 * Three readings of the name, because ROM sets are named three ways:
 *
 *  - **As the platform.** `SNES`, `Nintendo 64` — its id, short name or name.
 *  - **As the maker and the platform.** `Sony - PlayStation` is how No-Intro and
 *    Redump name their sets, and it is the most common layout there is.
 *  - **As the front-ends name it.** `ps1`, `megadrive`, `gc`, `n3ds`, `mame` —
 *    the shared vocabulary every other launcher's folders use. That table
 *    already exists as [IconPackSlugs], which answers exactly this question for
 *    icon packs; a second copy of it here would be one to keep in step.
 */
internal fun platformHintFor(folderName: String, platforms: List<Platform>): String? {
    if (platforms.isEmpty()) return null
    val candidates = folderNameCandidates(folderName)
    if (candidates.isEmpty()) return null

    platforms.forEach { platform ->
        val names = setOf(
            platform.id.lowercase(Locale.ROOT),
            platform.shortName.lowercase(Locale.ROOT),
            platform.name.lowercase(Locale.ROOT),
        )
        if (candidates.any { it in names }) return platform.id
    }

    // Only onto a system the user has: the alias table knows every platform
    // Loki models, and this must not resurrect one that was removed.
    val available = platforms.mapTo(mutableSetOf(), Platform::id)
    candidates.forEach { candidate ->
        IconPackSlugs.platformIdFor(candidate)?.takeIf { it in available }?.let { return it }
    }
    return null
}

/** The name as written, and with a `Maker - ` prefix taken off. */
private fun folderNameCandidates(folderName: String): List<String> {
    val whole = folderName.lowercase(Locale.ROOT).trim()
    return listOf(whole, whole.substringAfterLast(" - ").trim())
        .filter(String::isNotEmpty)
        .distinct()
}

/**
 * Whether a file with this extension is a game on this platform.
 *
 * An unknown platform accepts nothing. That only happens when a folder named
 * itself after a system the scan is not covering, and importing a file for a
 * platform that is not there produces an entry nothing can ever launch.
 */
internal fun platformAcceptsExtension(platform: Platform?, extension: String): Boolean =
    platform != null && extension in platform.romExtensions

/**
 * A disc's data tracks, which are not games however much they look like files.
 *
 * A CD image is a *sheet* naming its tracks — `.cue`, `.gdi`, `.ccd` — beside the
 * tracks themselves. Every one of those tracks carries an extension the platform
 * genuinely claims, `.bin` and `.img` being the usual ones, so no rule about
 * extensions can tell them apart from a cartridge dump. The only thing that
 * distinguishes them is the sheet sitting next to them.
 *
 * Matched by prefix rather than by equality, because a multi-track rip does not
 * share a stem with its sheet: `Game.cue` is accompanied by
 * `Game (Track 01).bin`, `Game (Track 02).bin` and so on. Left alone, a
 * three-track disc became four library entries — the game, and three copies of
 * it named after its tracks.
 *
 * A folder with no sheet in it suppresses nothing, so a Mega Drive set of plain
 * `.bin` cartridge dumps is untouched.
 */
internal fun discTracksShadowedBySheet(fileNames: List<String>): Set<String> {
    val sheetStems = fileNames
        .filter { it.extensionOf() in DISC_SHEET_EXTENSIONS }
        .map { it.stemOf() }
        .filter(String::isNotEmpty)
    if (sheetStems.isEmpty()) return emptySet()

    return fileNames.filterTo(mutableSetOf()) { name ->
        name.extensionOf() in DISC_TRACK_EXTENSIONS &&
            sheetStems.any { name.stemOf().startsWith(it) }
    }
}

/** Files that name a disc's contents rather than holding them. */
private val DISC_SHEET_EXTENSIONS = setOf("cue", "gdi", "ccd", "m3u", "toc")

/** Files a sheet points at. Never games in their own right when one is present. */
private val DISC_TRACK_EXTENSIONS = setOf("bin", "img", "iso", "raw", "wav", "ogg", "sub")

private fun String.extensionOf(): String =
    substringAfterLast('.', "").lowercase(Locale.ROOT)

private fun String.stemOf(): String =
    substringBeforeLast('.').lowercase(Locale.ROOT).trim()
