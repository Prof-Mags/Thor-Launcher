package com.thor.data.scanner

import android.content.Context
import androidx.core.net.toUri
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** What one pass over a ROM file yields. */
data class RomHashes(
    /** Eight lowercase hex digits, as No-Intro and ScreenScraper write it. */
    val crc32: String,
    val md5: String,
    val sha1: String,
)

/**
 * Hashes a ROM, once, for everything that wants to identify it exactly.
 *
 * This is the difference between asking a scraper "is there a game called
 * something like this?" and telling it "this is the file I have". ScreenScraper's
 * database is keyed by hash, so a match against one is exact — right game, right
 * region, right revision — where a name match has to guess all three from a
 * filename somebody else chose.
 *
 * All three digests come from one read because the read is the expensive part.
 * On a handheld pulling from an SD card the I/O is an order of magnitude slower
 * than the arithmetic, so computing CRC32 alone would cost very nearly what
 * computing all three costs, and each of the three is wanted by something
 * different: ScreenScraper takes any, No-Intro and Redump catalogues are
 * published against CRC32 and SHA1, and RetroAchievements identifies by MD5.
 */
@Singleton
class RomHasher @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Reads [contentUri] and digests it, or returns null.
     *
     * Null rather than an exception for the ordinary cases — the file is gone,
     * the permission has lapsed, it is too large to be worth reading — because
     * every caller's answer to all of them is the same: fall back to matching by
     * name. See [MAX_HASHED_BYTES] for the size rule.
     */
    suspend fun hash(contentUri: String, sizeBytes: Long): RomHashes? =
        withContext(ioDispatcher) {
            if (sizeBytes > MAX_HASHED_BYTES) return@withContext null

            val stream = open(contentUri) ?: return@withContext null
            try {
                stream.use { input -> digest(input) }
            } catch (e: Exception) {
                ThorLog.w(TAG, "Could not hash $contentUri", e)
                null
            }
        }

    private fun open(contentUri: String): InputStream? = runCatching {
        val uri = contentUri.toUri()
        when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let(::File)?.inputStream()
            null -> File(contentUri).inputStream()
            else -> null
        }
    }.getOrNull()

    private suspend fun digest(input: InputStream): RomHashes {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(BUFFER_BYTES)

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            // A scrape can be cancelled, and a multi-hundred-megabyte read that
            // ignores that keeps the file handle and the CPU until it finishes.
            coroutineContext.ensureActive()
            crc.update(buffer, 0, read)
            md5.update(buffer, 0, read)
            sha1.update(buffer, 0, read)
        }

        return RomHashes(
            crc32 = "%08x".format(crc.value),
            md5 = md5.digest().toHex(),
            sha1 = sha1.digest().toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "RomHasher"
        const val BUFFER_BYTES = 64 * 1024

        /**
         * Above this, the file is matched by name instead.
         *
         * Every cartridge system in the table fits comfortably under it, which is
         * where hashing earns the most: those libraries are full of regional
         * variants and revisions that a filename cannot tell apart. Disc images
         * are the ones that exceed it, and they are also where name matching is
         * at its best — a PlayStation game has a long distinctive title and one
         * dump per region rather than a dozen.
         *
         * The number is a time budget rather than a size one. Reading half a
         * gigabyte off an SD card is already several seconds; a four-gigabyte ISO
         * would be a minute of a scrape spent on one game, and a library of them
         * would be an afternoon.
         */
        const val MAX_HASHED_BYTES = 512L * 1024 * 1024
    }
}
