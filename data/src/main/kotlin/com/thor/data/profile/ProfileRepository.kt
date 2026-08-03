package com.thor.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.thor.core.common.log.ThorLog
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.datastore.ProfileRegistryRepository
import com.thor.core.model.LauncherProfile
import com.thor.core.model.ProfileRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Profiles, plus the file work the registry has no business doing.
 *
 * The registry is a document; this is where an avatar is decoded, scaled and
 * written into the profile's own directory so that revoking the picker's
 * permission — or the user deleting the original photo — cannot leave a profile
 * with a broken picture.
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: ProfileRegistryRepository,
) {

    val profiles: Flow<ProfileRegistry> = registry.registry
    val activeProfile: Flow<LauncherProfile?> = registry.activeProfile

    suspend fun create(name: String, accentArgb: Long): LauncherProfile =
        registry.createProfile(name, accentArgb, System.currentTimeMillis())

    suspend fun rename(id: String, name: String) = registry.renameProfile(id, name)

    suspend fun setAccent(id: String, accentArgb: Long) = registry.setAccent(id, accentArgb)

    suspend fun switchTo(id: String) = registry.switchTo(id, System.currentTimeMillis())

    suspend fun delete(id: String): Boolean = registry.deleteProfile(id)

    /**
     * Copies a picked image into the profile as its avatar.
     *
     * Written under a new name each time, and the previous one deleted after the
     * registry points at the replacement. Overwriting in place would keep the
     * old bitmap on screen — the file path is unchanged, so nothing downstream
     * has any reason to reload it.
     */
    suspend fun setAvatar(id: String, source: Uri): Boolean {
        val bitmap = decodeScaled(source) ?: return false
        val previous = registry.current().profiles.firstOrNull { it.id == id }?.avatarFile
        val fileName = "${ProfileFiles.AVATAR_PREFIX}-${System.currentTimeMillis()}.png"
        val destination = ProfileFiles.avatar(context, id, fileName)
        return runCatching {
            destination.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            registry.setAvatar(id, fileName)
            previous
                ?.takeIf { it != fileName }
                ?.let { ProfileFiles.avatar(context, id, it).delete() }
            true
        }.getOrElse { error ->
            ThorLog.w(TAG, "Could not save avatar for $id", error)
            destination.delete()
            false
        }.also { bitmap.recycle() }
    }

    suspend fun clearAvatar(id: String) {
        val previous = registry.current().profiles.firstOrNull { it.id == id }?.avatarFile
        registry.setAvatar(id, null)
        previous?.let { ProfileFiles.avatar(context, id, it).delete() }
    }

    /** Absolute path of a profile's avatar, if it has one that still exists. */
    fun avatarPath(profile: LauncherProfile): String? = profile.avatarFile
        ?.let { ProfileFiles.avatar(context, profile.id, it) }
        ?.takeIf(File::exists)
        ?.path

    /**
     * Decodes at roughly the size it will be drawn.
     *
     * A modern phone photo is several thousand pixels square; decoded whole to
     * draw a 96dp circle it is tens of megabytes of bitmap, and two profiles
     * would be enough to matter on a handheld.
     */
    private fun decodeScaled(source: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        var sample = 1
        while (longestEdge / sample > AVATAR_SIZE_PX * 2) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrElse { error ->
        ThorLog.w(TAG, "Could not read picked avatar", error)
        null
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val AVATAR_SIZE_PX = 256
        const val PNG_QUALITY = 100
    }
}
