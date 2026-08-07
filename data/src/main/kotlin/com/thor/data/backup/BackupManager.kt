package com.thor.data.backup

import android.content.Context
import androidx.core.net.toUri
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.profile.ProfileFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup attempt did, in the words the settings page shows. */
sealed interface BackupResult {
    data class Written(val entryCount: Int) : BackupResult
    data class Restored(val entryCount: Int) : BackupResult

    /** The file could not be read or written; the message names which. */
    data class Failed(val reason: String) : BackupResult

    /** The file opened but is not one of ours. */
    data object NotABackup : BackupResult
}

/**
 * Copies a profile's settings and library into a file, and back.
 *
 * Local only, and deliberately so. `CloudSettings` has modelled a sync for a long
 * time — provider, per-category toggles, an interval — and had no transport behind
 * any of it, so nothing it offered was true. A backup to a folder the user picked
 * is the half of that which needs no server, no account and no protocol, and it is
 * the half that matters on a device where the launcher is reinstalled by hand: a
 * wipe otherwise costs every grid placement, every API key and every theme.
 *
 * What goes in is the *whole* profile directory — the settings document, the Room
 * database and the avatar — because those three only make sense together. A
 * settings file naming grid placements that a restored database does not have is
 * worse than no backup at all.
 *
 * Room's journal files are deliberately included. Restoring a database without its
 * write-ahead log can present a database that is missing whatever had not yet been
 * checkpointed, which is a corruption nobody would connect to a restore days later.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Writes the active profile to [uri].
     *
     * Every entry is stored under a single top-level folder whose name marks the
     * file as ours, which is what [restore] checks before writing anything.
     */
    suspend fun backUp(profileId: String, uri: String): BackupResult =
        withContext(ioDispatcher) {
            val source = ProfileFiles.directory(appContext, profileId)
            if (!source.isDirectory) {
                return@withContext BackupResult.Failed("There is nothing to back up yet.")
            }

            runCatching {
                var count = 0
                appContext.contentResolver.openOutputStream(uri.toUri())?.use { out ->
                    ZipOutputStream(out.buffered()).use { zip ->
                        source.walkTopDown()
                            .filter(File::isFile)
                            .forEach { file ->
                                val relative = file.relativeTo(source).invariantSeparatorsPath
                                zip.putNextEntry(ZipEntry("$ROOT/$relative"))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                                count++
                            }
                    }
                } ?: return@runCatching BackupResult.Failed("That file could not be opened.")
                BackupResult.Written(count)
            }.getOrElse { error ->
                ThorLog.e(TAG, "Backup to $uri failed", error)
                BackupResult.Failed("The backup could not be written.")
            }
        }

    /**
     * Replaces the active profile's files with those in [uri].
     *
     * Unpacked to a staging directory first and only swapped in once every entry
     * has been read. A restore that writes as it reads leaves the profile half
     * replaced if the file is truncated — and the half it leaves is a settings
     * document from one device beside a database from another, which is exactly
     * the state this exists to avoid.
     *
     * The caller must restart the launcher afterwards. Nothing here can do it:
     * the database and the settings store are both open, and this returns while
     * they are still holding the files it has just replaced underneath them.
     */
    suspend fun restore(profileId: String, uri: String): BackupResult =
        withContext(ioDispatcher) {
            val target = ProfileFiles.directory(appContext, profileId)
            val staging = File(target.parentFile, "${target.name}$STAGING_SUFFIX")

            runCatching {
                staging.deleteRecursively()
                staging.mkdirs()

                var count = 0
                appContext.contentResolver.openInputStream(uri.toUri())?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            if (!entry.isDirectory && name.startsWith("$ROOT/")) {
                                val relative = name.removePrefix("$ROOT/")
                                val out = File(staging, relative)
                                /*
                                 * Zip-slip guard. An entry named `../../databases`
                                 * would otherwise write outside the staging
                                 * directory and into another profile — or into the
                                 * app's own code cache. The file is checked to be
                                 * inside where it claims to be, not merely to look
                                 * like it.
                                 */
                                if (!out.canonicalPath.startsWith(staging.canonicalPath)) {
                                    ThorLog.w(TAG, "Refusing entry outside the backup: $name")
                                    entry = zip.nextEntry
                                    continue
                                }
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zip.copyTo(it) }
                                count++
                            }
                            entry = zip.nextEntry
                        }
                    }
                } ?: return@runCatching BackupResult.Failed("That file could not be read.")

                if (count == 0) {
                    staging.deleteRecursively()
                    return@runCatching BackupResult.NotABackup
                }

                // The swap. Only now is anything the launcher is using touched.
                target.deleteRecursively()
                if (!staging.renameTo(target)) {
                    staging.copyRecursively(target, overwrite = true)
                    staging.deleteRecursively()
                }
                BackupResult.Restored(count)
            }.getOrElse { error ->
                ThorLog.e(TAG, "Restore from $uri failed", error)
                staging.deleteRecursively()
                BackupResult.Failed("The backup could not be restored.")
            }
        }

    private companion object {
        const val TAG = "Backup"

        /**
         * The folder every entry sits under inside the archive.
         *
         * Doubles as the format check: a zip with nothing under it is some other
         * zip, and saying so is better than unpacking an arbitrary archive over
         * somebody's library.
         */
        const val ROOT = "loki-backup"

        const val STAGING_SUFFIX = ".restoring"
    }
}
