package com.thor.core.common.profile

import android.content.Context
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adopts a pre-profile installation into its first profile.
 *
 * Before profiles there was one settings file and one library database at fixed
 * paths. Leaving them there would present every existing user with an empty
 * launcher and a fresh scan — their configuration would still be on disk, just
 * unreachable. So the first profile inherits them.
 *
 * Files are moved rather than copied: a copy doubles a library database that can
 * run to hundreds of megabytes of artwork, and leaves a stale original that the
 * next version would have to know to ignore.
 */
@Singleton
class ProfileMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Moves any legacy files into [profileId], if it has none of its own.
     *
     * Guarded on the destination rather than on a "migrated" flag: the check is
     * the thing the flag would be recording, and one less piece of state to get
     * out of step. Returns true when anything moved.
     */
    @Synchronized
    fun adoptLegacyData(profileId: String): Boolean {
        var moved = false
        val settings = ProfileFiles.settings(context, profileId)
        val legacySettings = File(context.filesDir, "datastore/$LEGACY_SETTINGS")
        if (!settings.exists() && legacySettings.exists()) {
            moved = legacySettings.moveTo(settings) || moved
        }

        val database = ProfileFiles.database(context, profileId)
        val legacyDatabase = context.getDatabasePath(LEGACY_DATABASE)
        if (!database.exists() && legacyDatabase.exists()) {
            // The write-ahead log and shared-memory index are part of the
            // database's state, not scratch files — a database moved without its
            // WAL loses every transaction that had not been checkpointed.
            moved = legacyDatabase.moveTo(database) || moved
            SIDECARS.forEach { suffix ->
                File(legacyDatabase.path + suffix)
                    .takeIf(File::exists)
                    ?.moveTo(File(database.path + suffix))
            }
        }
        if (moved) ThorLog.i(TAG, "Adopted pre-profile data into $profileId")
        return moved
    }

    private fun File.moveTo(destination: File): Boolean {
        destination.parentFile?.mkdirs()
        if (renameTo(destination)) return true
        // renameTo fails across mount points — internal storage and a device
        // encrypted directory can be different volumes on some devices.
        return runCatching {
            copyTo(destination, overwrite = true)
            delete()
            true
        }.getOrElse { error ->
            ThorLog.w(TAG, "Could not adopt ${this.name}", error)
            false
        }
    }

    private companion object {
        const val TAG = "ProfileMigrator"
        const val LEGACY_SETTINGS = "thor-settings.json"
        const val LEGACY_DATABASE = "thor-library.db"
        val SIDECARS = listOf("-wal", "-shm")
    }
}
