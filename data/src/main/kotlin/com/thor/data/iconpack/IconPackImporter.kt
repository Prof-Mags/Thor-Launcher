package com.thor.data.iconpack

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.model.IconPack
import com.thor.core.model.IconPackSlugs
import com.thor.core.model.PlatformArtwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What an import attempt produced. */
sealed interface IconPackImport {
    /**
     * @param applied platforms THOR has that now wear this pack's artwork
     * @param held slugs imported and kept for platforms THOR does not model yet
     */
    data class Success(
        val pack: IconPack,
        val applied: List<String>,
        val held: List<String>,
    ) : IconPackImport

    data class Failed(val reason: String) : IconPackImport
}

/**
 * Reads a platform icon pack and copies it into THOR's own storage.
 *
 * **The layout is discovered, not assumed.** These packs come from a handful of
 * different front-ends and none of them agree on where the platform folders sit —
 * the reference pack nests them under `smart_folders/by_platform`, others put them
 * at the root or under `icons`. So rather than hardcoding a path, this walks the
 * tree and treats *any* directory directly containing `icon.png`, `hero.png` or
 * `logo.png` as a platform, named by that directory. Every layout that puts one
 * platform's images in one folder therefore works, which is all of them.
 *
 * **Everything is copied in.** A pack referenced where the user left it would stop
 * working the moment they tidied their Downloads folder, moved it to a card, or
 * revoked the grant — and it would fail as missing artwork weeks later, with no
 * obvious cause. Tens of megabytes of copy is a one-off cost for artwork that then
 * cannot be taken away from underneath the launcher.
 *
 * **Nothing matched is discarded.** Packs routinely cover more systems than THOR
 * models; those are imported and held against their slug so that adding the
 * platform later is enough to make them appear. See [IconPack.artworkBySlug].
 */
@Singleton
class IconPackImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /** Root of THOR's installed packs. */
    private val root: File get() = File(context.filesDir, PACKS_DIRECTORY)

    /**
     * Imports from a folder the user granted through the storage access framework.
     *
     * Traversal uses the bulk children query rather than `DocumentFile.listFiles()`
     * for the same reason the ROM scanner does: one IPC per directory instead of
     * one per file per attribute.
     */
    suspend fun importFromFolder(treeUri: Uri): IconPackImport = withContext(ioDispatcher) {
        runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val scan = FolderScan()
            collectFolderArtwork(treeUri, rootDocId, parentSlug = null, scan = scan)

            install(
                metadata = scan.metadataDocumentId?.let { documentId ->
                    readMetadata {
                        context.contentResolver.openInputStream(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        )
                    }
                },
                fallbackId = treeUri.lastPathSegment.orEmpty(),
                sources = scan.artwork.mapValues { (_, kinds) ->
                    kinds.mapValues { (_, ref) ->
                        ArtworkSource(ref.size) {
                            context.contentResolver.openInputStream(
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, ref.documentId),
                            )
                        }
                    }
                },
            )
        }.getOrElse { error ->
            ThorLog.e(TAG, "Could not import icon pack from $treeUri", error)
            IconPackImport.Failed(error.message ?: "The folder could not be read")
        }
    }

    /**
     * Imports from a zip.
     *
     * Streamed rather than extracted whole and then read: a pack is tens of
     * megabytes and unpacking it to a temporary directory first would double both
     * the disk cost and the time, on a device where neither is plentiful. The
     * archive is read twice instead — once for the metadata, once for the images —
     * which is cheaper than one pass plus a full extraction.
     */
    suspend fun importFromZip(zipUri: Uri): IconPackImport = withContext(ioDispatcher) {
        runCatching {
            val metadata = openZip(zipUri) { entries ->
                entries.firstNotNullOfOrNull { (path, stream) ->
                    if (path.substringAfterLast('/').equals(METADATA_FILE, ignoreCase = true)) {
                        parseMetadata(stream.readBytes().decodeToString())
                    } else {
                        null
                    }
                }
            }

            /*
             * Zip entries are read into memory one at a time rather than kept as
             * lazy streams: the archive is a single forward-only stream, so a
             * "source" that reopened it per file would reopen and re-scan it once
             * per image — 80 passes over 31 MB.
             */
            val sources = mutableMapOf<String, MutableMap<ArtworkKind, ArtworkSource>>()
            openZip(zipUri) { entries ->
                entries.forEach { (path, stream) ->
                    val kind = ArtworkKind.of(path.substringAfterLast('/')) ?: return@forEach
                    val slug = path.substringBeforeLast('/', "")
                        .substringAfterLast('/')
                        .takeIf { it.isNotBlank() } ?: return@forEach
                    val bytes = stream.readBytes()
                    if (bytes.isEmpty()) return@forEach
                    sources.getOrPut(IconPackSlugs.storageKeyFor(slug)) { mutableMapOf() }[kind] =
                        ArtworkSource(bytes.size.toLong()) { bytes.inputStream() }
                }
                Unit
            }

            install(
                metadata = metadata,
                fallbackId = zipUri.lastPathSegment.orEmpty(),
                sources = sources,
            )
        }.getOrElse { error ->
            ThorLog.e(TAG, "Could not import icon pack from $zipUri", error)
            IconPackImport.Failed(error.message ?: "The archive could not be read")
        }
    }

    /** Deletes a pack's copied artwork. */
    suspend fun deleteFiles(packId: String) = withContext(ioDispatcher) {
        runCatching { File(root, packId).deleteRecursively() }
        Unit
    }

    /**
     * Whether this pack's artwork is actually on disk.
     *
     * [install] creates a directory per pack and copies every image into it
     * before the record is written, so a record without one describes something
     * that is not there. See `IconPackRepository.repairMissingPacks`.
     *
     * Not `suspend`, unlike everything else here, because it is one `stat` call
     * rather than a copy of tens of megabytes — and because its caller feeds it
     * to a plain predicate, which is what lets that rule be tested without a
     * filesystem. Call it from a dispatcher that tolerates disk access; the one
     * caller is already inside one.
     */
    fun hasFiles(packId: String): Boolean = File(root, packId).isDirectory

    // ---- Shared ------------------------------------------------------------

    /**
     * Writes the discovered artwork to disk and builds the pack record.
     *
     * Zero-length files are dropped here rather than at the call sites, because
     * both of them can produce one: the reference pack ships several `logo.png`
     * entries of exactly zero bytes, which is a missing logo expressed as a
     * present file. Writing it would give the launcher an image that decodes to
     * nothing and renders as a blank space where a wordmark should be.
     */
    private suspend fun install(
        metadata: PackMetadata?,
        fallbackId: String,
        sources: Map<String, Map<ArtworkKind, ArtworkSource>>,
    ): IconPackImport {
        if (sources.isEmpty()) {
            return IconPackImport.Failed(
                "No platform artwork found. Expected folders containing icon.png, " +
                    "hero.png or logo.png.",
            )
        }

        val packId = (metadata?.id?.takeIf { it.isNotBlank() } ?: fallbackId)
            .lowercase(Locale.ROOT)
            .replace(UNSAFE_ID, "-")
            .trim('-')
            .ifBlank { "pack-${sources.keys.sorted().joinToString().hashCode()}" }

        val packDirectory = File(root, packId)
        // A reinstall replaces rather than merges, so a pack that dropped a
        // platform in a new version does not keep serving the old one's art.
        packDirectory.deleteRecursively()
        packDirectory.mkdirs()

        val artwork = mutableMapOf<String, PlatformArtwork>()

        for ((slug, kinds) in sources) {
            currentCoroutineContext().ensureActive()
            val slugDirectory = File(packDirectory, slug).apply { mkdirs() }
            var entry = PlatformArtwork(packId = packId)

            for ((kind, source) in kinds) {
                if (source.size <= 0L) continue
                val target = File(slugDirectory, kind.fileName)
                val written = runCatching {
                    source.open()?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }.isSuccess && target.length() > 0L

                if (!written) {
                    target.delete()
                    continue
                }
                val uri = Uri.fromFile(target).toString()
                entry = when (kind) {
                    ArtworkKind.ICON -> entry.copy(iconUri = uri)
                    ArtworkKind.HERO -> entry.copy(heroUri = uri)
                    ArtworkKind.LOGO -> entry.copy(logoUri = uri)
                }
            }

            if (entry.isEmpty) {
                slugDirectory.deleteRecursively()
            } else {
                artwork[slug] = entry
            }
        }

        if (artwork.isEmpty()) {
            packDirectory.deleteRecursively()
            return IconPackImport.Failed("Every image in the pack was empty or unreadable")
        }

        val applied = artwork.keys.mapNotNull(IconPackSlugs::platformIdFor).distinct()
        val held = artwork.keys.filter { IconPackSlugs.platformIdFor(it) == null }.sorted()

        val pack = IconPack(
            id = packId,
            name = metadata?.name?.takeIf { it.isNotBlank() } ?: packId,
            author = metadata?.author.orEmpty().ifBlank { "Unknown" },
            version = metadata?.version.orEmpty().ifBlank { "1.0" },
            description = metadata?.description.orEmpty(),
            artworkBySlug = artwork,
            appliedPlatformIds = applied,
            installedAtEpochMs = System.currentTimeMillis(),
        )

        ThorLog.i(
            TAG,
            "Installed pack ${pack.id}: ${applied.size} applied, ${held.size} held",
        )
        return IconPackImport.Success(pack = pack, applied = applied, held = held)
    }

    /** What one pass over a document tree turned up. */
    private class FolderScan {
        val artwork = mutableMapOf<String, MutableMap<ArtworkKind, DocumentRef>>()

        /** The first `metadata.json` seen, at whatever depth. */
        var metadataDocumentId: String? = null
    }

    /**
     * Walks a document tree, collecting artwork and the pack's metadata.
     *
     * One pass rather than two, and recursive rather than a stack, because the
     * directory name *is* the platform slug: traversal has to carry the parent
     * down with it, which a flat "visit every file" walk cannot do.
     */
    private fun collectFolderArtwork(
        treeUri: Uri,
        documentId: String,
        parentSlug: String?,
        scan: FolderScan,
    ) {
        queryChildren(treeUri, documentId) { name, childId, mime, size ->
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                collectFolderArtwork(treeUri, childId, name, scan)
                return@queryChildren
            }

            if (name.equals(METADATA_FILE, ignoreCase = true) && scan.metadataDocumentId == null) {
                scan.metadataDocumentId = childId
                return@queryChildren
            }

            val kind = ArtworkKind.of(name)
            if (kind != null && parentSlug != null) {
                scan.artwork
                    .getOrPut(IconPackSlugs.storageKeyFor(parentSlug)) { mutableMapOf() }[kind] =
                    DocumentRef(childId, size)
            }
        }
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

    private fun <T> openZip(uri: Uri, block: (Sequence<Pair<String, InputStream>>) -> T): T =
        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "The archive could not be opened" }
            ZipArchiveInputStream(raw.buffered()).use { zip ->
                val entries = generateSequence { zip.nextEntry }
                    .filterNot { it.isDirectory }
                    .map { it.name.replace('\\', '/') to (zip as InputStream) }
                block(entries)
            }
        }

    private fun readMetadata(open: () -> InputStream?): PackMetadata? = runCatching {
        open()?.use { parseMetadata(it.readBytes().decodeToString()) }
    }.getOrNull()

    private fun parseMetadata(text: String): PackMetadata? =
        runCatching { json.decodeFromString(PackMetadata.serializer(), text) }.getOrNull()

    /** A file inside a document tree, kept until it is time to copy it. */
    private data class DocumentRef(val documentId: String, val size: Long)

    /** Somewhere bytes can be read from, once. */
    private class ArtworkSource(val size: Long, val open: () -> InputStream?)

    private companion object {
        const val TAG = "IconPack"
        const val PACKS_DIRECTORY = "iconpacks"
        const val METADATA_FILE = "metadata.json"

        /** Anything that has no business in a directory name. */
        val UNSAFE_ID = Regex("[^a-z0-9._-]+")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
