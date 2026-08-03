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

    /** Removes a profile's entire directory. Safe to call for an unknown id. */
    fun delete(context: Context, profileId: String): Boolean =
        File(root(context), profileId).deleteRecursively()
}
