package com.thor.core.common.profile

import android.content.Context
import java.io.File

/**
 * Where a profile's belongings live on disk.
 *
 * Everything a profile owns sits under one directory named after its id, so
 * deleting a profile is deleting a directory, and a profile cannot leave
 * fragments behind in four different places. Ids are validated as inert (see
 * `LauncherProfile.isValidId`) precisely because they become path segments.
 */
object ProfileFiles {

    private const val ROOT = "profiles"
    private const val SETTINGS = "settings.json"
    private const val DATABASE = "library.db"
    const val AVATAR_PREFIX = "avatar"

    fun root(context: Context): File = File(context.filesDir, ROOT)

    fun directory(context: Context, profileId: String): File =
        File(root(context), profileId).apply { mkdirs() }

    fun settings(context: Context, profileId: String): File =
        File(directory(context, profileId), SETTINGS)

    /**
     * Room owns three files per database — the database, its write-ahead log and
     * its shared-memory index — and creates the latter two beside the first, so
     * only the primary path is named here.
     */
    fun database(context: Context, profileId: String): File =
        File(directory(context, profileId), DATABASE)

    fun avatar(context: Context, profileId: String, fileName: String): File =
        File(directory(context, profileId), fileName)

    /**
     * Profile directories already on disk, newest first.
     *
     * Deliberately does not create anything — unlike [directory], which mkdirs
     * as a side effect. This is asked *before* a profile exists, to find out
     * whether one already does.
     */
    fun existingIds(context: Context): List<String> =
        root(context).listFiles().orEmpty()
            .filter(File::isDirectory)
            .sortedByDescending(File::lastModified)
            .map(File::getName)

    /**
     * How much a directory looks like somebody's actual profile.
     *
     * Not a byte count, a ranking. A settings file outranks any database,
     * because it exists only once something has been configured — where Room
     * creates a database the instant it is opened, so a profile nobody ever used
     * still has one, and going by mere existence would rate an empty directory
     * as highly as a full one. Below that, the larger library wins.
     *
     * Zero means nothing worth recovering.
     */
    fun dataWeight(context: Context, profileId: String): Long {
        val directory = File(root(context), profileId)
        val settings = if (File(directory, SETTINGS).exists()) SETTINGS_WEIGHT else 0L
        return settings + File(directory, DATABASE).length()
    }

    /** Dominates any plausible database size, so settings always win the ranking. */
    private const val SETTINGS_WEIGHT = 1L shl 40

    /** Removes a profile's entire directory. Safe to call for an unknown id. */
    fun delete(context: Context, profileId: String): Boolean =
        File(root(context), profileId).deleteRecursively()
}
