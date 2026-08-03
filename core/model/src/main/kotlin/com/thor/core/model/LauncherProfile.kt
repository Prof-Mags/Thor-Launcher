package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * One person using the launcher.
 *
 * A profile owns its settings file, its library database and its avatar, all
 * held in a directory named after [id] — which is why the id is generated and
 * never derived from the name. Renaming is then free, and two people called
 * "Guest" cannot collide on disk.
 */
@Serializable
data class LauncherProfile(
    val id: String,
    val name: String,
    /**
     * File name of the avatar inside the profile's own directory, or null for
     * the drawn fallback. Stored as a bare name rather than an absolute path so
     * the profile survives the app's data directory moving — which it does on
     * restore, and on some multi-user devices.
     */
    val avatarFile: String? = null,
    /** Tint for the drawn fallback avatar and the profile's accent chrome. */
    val accentArgb: Long = DEFAULT_ACCENT,
    val createdAtEpochMs: Long = 0L,
    val lastUsedEpochMs: Long = 0L,
) {
    /** First letter, for the fallback avatar. Blank names fall back to "?". */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    companion object {
        const val DEFAULT_ACCENT = 0xFF6C8CFFL
        const val MAX_NAME_LENGTH = 24

        /** Directory and file names are derived from ids, so they must be inert. */
        fun isValidId(id: String): Boolean =
            id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }
    }
}

/**
 * Every profile on the device, and which one is in use.
 *
 * This document lives outside any profile — it is the one thing that cannot be
 * per-profile, since it is what decides which profile to load.
 */
@Serializable
data class ProfileRegistry(
    val profiles: List<LauncherProfile> = emptyList(),
    val activeProfileId: String = "",
) {
    val active: LauncherProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    /** True when removing a profile would leave the launcher with none. */
    val isLastProfile: Boolean get() = profiles.size <= 1

    companion object {
        val EMPTY = ProfileRegistry()
    }
}

/**
 * Trims a typed profile name to something storable.
 *
 * Names are display-only — the id does the identifying — so this only guards
 * length and emptiness rather than rejecting characters. An all-whitespace name
 * would render as an invisible profile, so it falls back.
 */
fun sanitizeProfileName(raw: String, fallback: String = "Player"): String {
    val trimmed = raw.trim().take(LauncherProfile.MAX_NAME_LENGTH)
    return trimmed.ifBlank { fallback }
}

/**
 * Picks a name not already taken, appending a counter.
 *
 * Duplicate names are legal — profiles are keyed by id — but two identical
 * tiles in the switcher are indistinguishable to the person using it.
 */
fun uniqueProfileName(desired: String, taken: Collection<String>): String {
    val base = sanitizeProfileName(desired)
    if (taken.none { it.equals(base, ignoreCase = true) }) return base
    var counter = 2
    while (true) {
        val candidate = "$base $counter"
        if (taken.none { it.equals(candidate, ignoreCase = true) }) return candidate
        counter++
    }
}

/**
 * Applies a deletion, choosing who becomes active if the deleted one was.
 *
 * The last profile cannot be deleted: the launcher has to load something, and a
 * profile-less state would mean inventing one on next boot anyway. Falls to the
 * most recently used of the survivors, which is the one most likely wanted.
 */
fun ProfileRegistry.withProfileRemoved(id: String): ProfileRegistry {
    if (isLastProfile || profiles.none { it.id == id }) return this
    val remaining = profiles.filterNot { it.id == id }
    val nextActive = if (activeProfileId == id) {
        remaining.maxByOrNull(LauncherProfile::lastUsedEpochMs)?.id ?: remaining.first().id
    } else {
        activeProfileId
    }
    return copy(profiles = remaining, activeProfileId = nextActive)
}
