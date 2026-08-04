package com.thor.data.iconpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
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
     * @param packs what the source contained: one pack, or one per icon style
     *   when it offered several. Ordered least-preferred first, so installing
     *   them in order leaves the most preferred style — the first [IconStyle]
     *   declares — as the one actually on screen.
     * @param applied platforms THOR has that now wear this artwork
     * @param held slugs imported and kept for platforms THOR does not model yet
     */
    data class Success(
        val packs: List<IconPack>,
        val applied: List<String>,
        val held: List<String>,
    ) : IconPackImport {
        /** The one left dressing the platforms, which is the last one applied. */
        val visible: IconPack get() = packs.last()
    }

    data class Failed(val reason: String) : IconPackImport
}

/**
 * Reads a platform icon pack and copies it into THOR's own storage.
 *
 * **Two layouts, discovered rather than assumed.** These packs come from a
 * handful of different front-ends and none of them agree on where anything sits.
 *
 * The *nested* layout names a platform with a directory — `snes/icon.png`. The
 * reference pack nests those under `smart_folders/by_platform`, others put them
 * at the root or under `icons`, so rather than hardcoding a path this walks the
 * tree and treats *any* directory directly containing `icon.png`, `hero.png`,
 * `logo.png` or `overlay.png` as a platform, named by that directory.
 *
 * The *flat* layout names it in the filename instead — `snes-console.png` — which
 * is the shape a set exported in one batch comes out in, and the one an artist's
 * own output folder is in. There the suffix carries a second fact the nested
 * layout has no way to express: the same console is drawn several ways, and which
 * treatment somebody wants is taste rather than correctness. So a flat source
 * yields **one pack per style**, and they are switched between the way any two
 * packs are. See [IconStyle].
 *
 * **Everything is copied in.** A pack referenced where the user left it would stop
 * working the moment they tidied their Downloads folder, moved it to a card, or
 * revoked the grant — and it would fail as missing artwork weeks later, with no
 * obvious cause.
 *
 * **But not at the size it arrived.** Copying verbatim is what made that a
 * reasonable trade when a pack was tens of megabytes; a set shipping three 1024px
 * styles for sixty systems is 285 MB, and three packs out of it would be three
 * copies of that. Nothing is ever drawn near that size — the largest a platform
 * icon gets is a folder cell at the coarsest pinch preset, under 300px — so images
 * are reduced as they are copied. See [maxEdgePx].
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
                fallbackName = displayNameFrom(treeUri.lastPathSegment.orEmpty()),
                contents = scan.contents,
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
            val contents = PackContents()
            openZip(zipUri) { entries ->
                entries.forEach { (path, stream) ->
                    val name = path.substringAfterLast('/')
                    if (!ArtworkKind.isImage(name)) return@forEach
                    val parentSlug = path.substringBeforeLast('/', "")
                        .substringAfterLast('/')
                        .takeIf { it.isNotBlank() }
                    val bytes = stream.readBytes()
                    if (bytes.isEmpty()) return@forEach
                    contents.put(
                        fileName = name,
                        parentSlug = parentSlug,
                        source = ArtworkSource(bytes.size.toLong()) { bytes.inputStream() },
                    )
                }
                Unit
            }

            install(
                metadata = metadata,
                fallbackId = zipUri.lastPathSegment.orEmpty(),
                fallbackName = displayNameFrom(zipUri.lastPathSegment.orEmpty()),
                contents = contents,
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
     * [buildPack] creates a directory per pack and copies every image into it
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
     * Writes the discovered artwork to disk and builds the pack records.
     *
     * One pack, unless the source named its images by style — then one per style,
     * each carrying that style's icons plus everything the source supplied for the
     * pack as a whole. Overlays are the reason that distinction matters: they
     * dress the *games* rather than the system, so every style ships the same ones
     * and a user switching between styles keeps their framing.
     */
    private suspend fun install(
        metadata: PackMetadata?,
        fallbackId: String,
        fallbackName: String,
        contents: PackContents,
    ): IconPackImport {
        if (contents.isEmpty) {
            return IconPackImport.Failed(
                "No platform artwork found. Expected folders containing icon.png, " +
                    "hero.png, logo.png or overlay.png, or files named " +
                    "<system>-console.png.",
            )
        }

        val baseId = (metadata?.id?.takeIf { it.isNotBlank() } ?: fallbackId)
            .lowercase(Locale.ROOT)
            .replace(UNSAFE_ID, "-")
            .trim('-')
            .ifBlank { "pack-${contents.slugs.sorted().joinToString().hashCode()}" }
        val baseName = metadata?.name?.takeIf { it.isNotBlank() }
            ?: fallbackName.takeIf { it.isNotBlank() }
            ?: baseId

        /*
         * Least-preferred first: the repository applies these in order and the
         * last one wins, so reversing the declared order is what leaves the style
         * listed first — the plain console render — as the one on screen. Somebody
         * who wanted a different one removes this one and the next is revealed,
         * which is what removing any pack already does.
         */
        val offered = IconStyle.entries.filter { it in contents.styles }.reversed()

        val packs = if (offered.isEmpty()) {
            listOfNotNull(buildPack(baseId, baseName, metadata, contents.shared, emptyMap()))
        } else {
            offered.mapNotNull { style ->
                buildPack(
                    packId = "$baseId-${style.suffix}",
                    name = "$baseName — ${style.displayName}",
                    metadata = metadata,
                    shared = contents.shared,
                    icons = contents.styles.getValue(style),
                )
            }
        }

        if (packs.isEmpty()) {
            return IconPackImport.Failed("Every image in the pack was empty or unreadable")
        }

        val slugs = packs.flatMap { it.artworkBySlug.keys }.toSet()
        val applied = slugs.mapNotNull(IconPackSlugs::platformIdFor).distinct()
        val held = slugs.filter { IconPackSlugs.platformIdFor(it) == null }.sorted()

        ThorLog.i(
            TAG,
            "Installed ${packs.size} pack(s) from $baseId: " +
                "${applied.size} applied, ${held.size} held",
        )
        return IconPackImport.Success(packs = packs, applied = applied, held = held)
    }

    /**
     * Copies one pack's images into a directory of its own and records them.
     *
     * Zero-length files are dropped here rather than at the call sites, because
     * every source can produce one: the reference pack ships several `logo.png`
     * entries of exactly zero bytes, which is a missing logo expressed as a
     * present file. Writing it would give the launcher an image that decodes to
     * nothing and renders as a blank space where a wordmark should be.
     *
     * A directory per pack, even when several came out of one folder, so that
     * removing any of them can delete its files without reaching into another's.
     * The overlays are therefore copied once per style, which is the same trade
     * the whole importer makes: independence is worth more than the bytes.
     */
    private suspend fun buildPack(
        packId: String,
        name: String,
        metadata: PackMetadata?,
        shared: Map<String, Map<ArtworkKind, ArtworkSource>>,
        icons: Map<String, ArtworkSource>,
    ): IconPack? {
        val packDirectory = File(root, packId)
        // A reinstall replaces rather than merges, so a pack that dropped a
        // platform in a new version does not keep serving the old one's art.
        packDirectory.deleteRecursively()
        packDirectory.mkdirs()

        val artwork = mutableMapOf<String, PlatformArtwork>()

        // Sorted, so that two systems whose artwork collides resolve the same way
        // on every import rather than following whatever order the tree was
        // walked in.
        for (slug in (shared.keys + icons.keys).sorted()) {
            currentCoroutineContext().ensureActive()
            val slugDirectory = File(packDirectory, slug).apply { mkdirs() }
            var entry = PlatformArtwork(packId = packId)

            // The style's own icon outranks any `icon.png` the same source also
            // shipped: the style is precisely what the user chose by installing
            // this pack rather than its sibling.
            val images = buildMap {
                shared[slug]?.let(::putAll)
                icons[slug]?.let { put(ArtworkKind.ICON, it) }
            }

            for ((kind, source) in images) {
                if (source.size <= 0L) continue
                val target = File(slugDirectory, kind.fileName)
                if (!copyScaled(source, target, kind)) {
                    target.delete()
                    continue
                }
                val uri = Uri.fromFile(target).toString()
                entry = when (kind) {
                    ArtworkKind.ICON -> entry.copy(iconUri = uri)
                    ArtworkKind.HERO -> entry.copy(heroUri = uri)
                    ArtworkKind.LOGO -> entry.copy(logoUri = uri)
                    ArtworkKind.OVERLAY -> entry.copy(overlayUri = uri)
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
            return null
        }

        return IconPack(
            id = packId,
            name = name,
            author = metadata?.author.orEmpty().ifBlank { "Unknown" },
            version = metadata?.version.orEmpty().ifBlank { "1.0" },
            description = metadata?.description.orEmpty(),
            artworkBySlug = artwork,
            appliedPlatformIds = artwork.keys.mapNotNull(IconPackSlugs::platformIdFor).distinct(),
            installedAtEpochMs = System.currentTimeMillis(),
        )
    }

    /**
     * Copies one image, no larger than it is ever drawn.
     *
     * Only ever a *reduction*, and only by a power of two — `inSampleSize` is what
     * the decoder applies while reading, so an oversized source never lands in
     * memory whole. A pack of sixty 1024px renders would otherwise decode 250 MB
     * of bitmaps to write 250 MB of files, on a handheld with neither to spare.
     *
     * Anything already within its limit is copied byte for byte. That is not only
     * faster: re-encoding a small image would lose a little of it for no reason at
     * all, and the packs this reads are mostly already sized sensibly.
     *
     * PNG on the way out, always. Sources arrive as PNG, JPEG and WebP, and the
     * one thing every one of these images can carry is transparency — console
     * renders sit on nothing, and an overlay is a frame that is mostly nothing. A
     * format that would flatten that onto white is not a candidate.
     */
    private fun copyScaled(source: ArtworkSource, target: File, kind: ArtworkKind): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { source.open()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)

        // Not an image this device can decode, or already small enough. The first
        // is not necessarily a failure — an unknown format still copies, and
        // whether Coil can read it later is Coil's question, not this one's.
        if (longestEdge <= 0 || longestEdge <= kind.maxEdgePx) {
            return copyVerbatim(source, target)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit(longestEdge / kind.maxEdgePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            source.open()?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return copyVerbatim(source, target)

        return try {
            runCatching {
                target.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                }
            }.isSuccess && target.length() > 0L
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyVerbatim(source: ArtworkSource, target: File): Boolean =
        runCatching {
            source.open()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.isSuccess && target.length() > 0L

    /**
     * Everything one pass over a source turned up.
     *
     * Two buckets because the two layouts say different things. [shared] is
     * artwork that belongs to the pack however many packs come out of it — the
     * nested layout's icons, heroes and wordmarks, and every layout's overlays.
     * [styles] is the flat layout's icons, which are the same systems drawn
     * several ways and therefore become packs of their own.
     */
    private class PackContents {
        val shared = mutableMapOf<String, MutableMap<ArtworkKind, ArtworkSource>>()
        val styles = mutableMapOf<IconStyle, MutableMap<String, ArtworkSource>>()

        val isEmpty: Boolean get() = shared.isEmpty() && styles.isEmpty()

        val slugs: Set<String> get() = shared.keys + styles.values.flatMap { it.keys }

        /**
         * Records one image, whichever layout named it.
         *
         * The flat name is tried first because it is the more specific claim: it
         * identifies both a system and a style out of the filename alone, where
         * the nested reading needs a parent directory to mean anything and has
         * nothing to say about style.
         */
        fun put(fileName: String, parentSlug: String?, source: ArtworkSource) {
            when (val flat = parseFlatArtworkName(fileName)) {
                is FlatArtwork.Icon ->
                    styles.getOrPut(flat.style) { mutableMapOf() }[key(flat.slug)] = source

                is FlatArtwork.Overlay ->
                    shared.getOrPut(key(flat.slug)) { mutableMapOf() }[ArtworkKind.OVERLAY] = source

                null -> {
                    val kind = ArtworkKind.of(fileName) ?: return
                    if (parentSlug == null) return
                    shared.getOrPut(key(parentSlug)) { mutableMapOf() }[kind] = source
                }
            }
        }

        private fun key(slug: String) = IconPackSlugs.storageKeyFor(slug)
    }

    /** What one pass over a document tree turned up. */
    private class FolderScan {
        val contents = PackContents()

        /** The first `metadata.json` seen, at whatever depth. */
        var metadataDocumentId: String? = null
    }

    /**
     * Walks a document tree, collecting artwork and the pack's metadata.
     *
     * One pass rather than two, and recursive rather than a stack, because in the
     * nested layout the directory name *is* the platform slug: traversal has to
     * carry the parent down with it, which a flat "visit every file" walk cannot
     * do.
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

            if (!ArtworkKind.isImage(name)) return@queryChildren
            scan.contents.put(
                fileName = name,
                parentSlug = parentSlug,
                source = ArtworkSource(size) {
                    context.contentResolver.openInputStream(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                    )
                },
            )
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

    /** Somewhere bytes can be read from, more than once. */
    private class ArtworkSource(val size: Long, val open: () -> InputStream?)

    private companion object {
        const val TAG = "IconPack"
        const val PACKS_DIRECTORY = "iconpacks"
        const val METADATA_FILE = "metadata.json"

        /** Ignored for PNG, which is lossless, but the parameter is not optional. */
        const val PNG_QUALITY = 100

        /** Anything that has no business in a directory name. */
        val UNSAFE_ID = Regex("[^a-z0-9._-]+")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}

/**
 * What to call a pack that did not name itself.
 *
 * Not the id, which is derived from the same string and is deliberately
 * unreadable: it has to be safe as a directory name, so it is lowercased with its
 * punctuation replaced. Showing that would list a pack as
 * `primary-documents-icon-forge` when the folder the user picked was plainly
 * called "Icon Forge" — and with several styles out of one source, that name is
 * repeated down the list.
 *
 * Takes the segment rather than the `Uri` it came from, so the rule can be tested
 * without an Android runtime — the same reason [IconPackRepository]'s orphan
 * check takes a predicate instead of reading the disk.
 *
 * A document tree's last segment carries the whole path
 * (`primary:Documents/Icon Forge`), so the leaf is what survives both separators.
 * Only `.zip` is stripped, rather than any extension: a set in a folder called
 * "v1.2 icons" is not a file, and cutting at its last dot would name it "v1".
 */
internal fun displayNameFrom(lastPathSegment: String): String =
    lastPathSegment
        .substringAfterLast('/')
        .substringAfterLast(':')
        .let { if (it.endsWith(".zip", ignoreCase = true)) it.dropLast(".zip".length) else it }
        .trim()

/**
 * The longest edge worth keeping, per kind.
 *
 * Set from where each image is actually drawn rather than from what packs happen
 * to ship, because the gap between the two is the whole point: a 1024px icon in a
 * cell under 300px across costs eleven times the pixels to look identical.
 *
 * An icon fills a folder cell, which is largest at the coarsest pinch preset and
 * still under 300px on this hardware. An overlay is drawn over a game cell, so it
 * is the same size question. A wordmark spans an open folder's banner, and a hero
 * fills a whole panel — which on the Thor is 1080 across, and is the one of these
 * that a low limit would visibly soften.
 */
private val ArtworkKind.maxEdgePx: Int
    get() = when (this) {
        ArtworkKind.ICON -> 512
        ArtworkKind.OVERLAY -> 512
        ArtworkKind.LOGO -> 768
        ArtworkKind.HERO -> 1600
    }
