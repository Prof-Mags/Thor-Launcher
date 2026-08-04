package com.thor.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import com.thor.core.common.dispatchers.ApplicationScope
import com.thor.core.common.profile.ProfileFiles
import com.thor.core.common.log.ThorLog
import com.thor.core.model.LauncherProfile
import com.thor.core.model.ProfileRegistry
import com.thor.core.model.sanitizeProfileName
import com.thor.core.model.uniqueProfileName
import com.thor.core.model.withProfileRemoved
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks which existing directory, if any, an empty registry should adopt.
 *
 * Only directories holding something are considered — several may exist, and a
 * launcher that opened a database and wrote nothing leaves one behind that is
 * indistinguishable from a profile by name alone. The heaviest wins; ties go to
 * the first, which [ProfileFiles.existingIds] orders most recent first.
 */
internal fun chooseSeedProfileId(
    candidates: List<Pair<String, Long>>,
    default: String,
): String = candidates
    .filter { (_, weight) -> weight > 0L }
    .maxByOrNull { (_, weight) -> weight }
    ?.first
    ?: default

/**
 * Owns the list of profiles and which one is in use.
 *
 * Every read goes through [ensureSeeded], because "no profiles" is not a state
 * the launcher can render — it has to be somebody. The first run, and any run
 * after the registry is lost to corruption, silently gains a default profile
 * rather than presenting an empty switcher.
 */
@Singleton
class ProfileRegistryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ProfileStore private val dataStore: DataStore<ProfileRegistry>,
    @ApplicationScope private val scope: CoroutineScope,
) {

    init {
        /*
         * The seed is written, not merely computed.
         *
         * Reads repair themselves through [seeded], which made it easy to miss
         * that nothing was persisting the result: the launcher ran perfectly all
         * session against a profile that existed only in memory, and started the
         * next one with an empty registry to seed again. Every cold start looked
         * like a factory reset.
         *
         * Doing it here means the file exists from the first moment the app is
         * built, before any screen has asked who is signed in.
         */
        scope.launch { runCatching { ensureSeeded() } }
    }

    val registry: Flow<ProfileRegistry> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                ThorLog.w(TAG, "Profile registry unreadable, using defaults", error)
                emit(ProfileRegistry.EMPTY)
            } else {
                throw error
            }
        }
        .map(::seeded)
        .distinctUntilChanged()

    /** The active profile's id, and nothing else — the key most callers want. */
    val activeProfileId: Flow<String> = registry
        .map { it.active?.id.orEmpty() }
        .distinctUntilChanged()

    val activeProfile: Flow<LauncherProfile?> = registry
        .map { it.active }
        .distinctUntilChanged()

    suspend fun current(): ProfileRegistry = registry.first()

    /**
     * Writes the seeded registry back if it was only seeded in memory.
     *
     * Reads repair themselves through [seeded], but nothing else knows the id of
     * a profile that was never persisted — the settings store would be handed an
     * id that vanishes on the next write.
     */
    suspend fun ensureSeeded(): ProfileRegistry = dataStore.updateData(::seeded)

    suspend fun createProfile(name: String, accentArgb: Long, nowEpochMs: Long): LauncherProfile {
        lateinit var created: LauncherProfile
        dataStore.updateData { current ->
            val seededRegistry = seeded(current)
            created = LauncherProfile(
                id = UUID.randomUUID().toString(),
                name = uniqueProfileName(name, seededRegistry.profiles.map(LauncherProfile::name)),
                accentArgb = accentArgb,
                createdAtEpochMs = nowEpochMs,
                lastUsedEpochMs = nowEpochMs,
            )
            seededRegistry.copy(profiles = seededRegistry.profiles + created)
        }
        return created
    }

    suspend fun renameProfile(id: String, name: String) = edit(id) {
        it.copy(name = sanitizeProfileName(name, fallback = it.name))
    }

    suspend fun setAccent(id: String, accentArgb: Long) = edit(id) {
        it.copy(accentArgb = accentArgb)
    }

    suspend fun setAvatar(id: String, avatarFile: String?) = edit(id) {
        it.copy(avatarFile = avatarFile)
    }

    suspend fun switchTo(id: String, nowEpochMs: Long) {
        dataStore.updateData { current ->
            val seededRegistry = seeded(current)
            if (seededRegistry.profiles.none { it.id == id }) return@updateData seededRegistry
            seededRegistry.copy(
                activeProfileId = id,
                profiles = seededRegistry.profiles.map {
                    if (it.id == id) it.copy(lastUsedEpochMs = nowEpochMs) else it
                },
            )
        }
    }

    /**
     * Removes a profile and everything it owns.
     *
     * The registry is written first: a crash between the two leaves an orphaned
     * directory, which is harmless, where the other order leaves a profile in
     * the switcher whose settings and library are gone.
     */
    suspend fun deleteProfile(id: String): Boolean {
        var removed = false
        dataStore.updateData { current ->
            val seededRegistry = seeded(current)
            val next = seededRegistry.withProfileRemoved(id)
            removed = next != seededRegistry
            next
        }
        if (removed) ProfileFiles.delete(context, id)
        return removed
    }

    private suspend fun edit(id: String, transform: (LauncherProfile) -> LauncherProfile) {
        dataStore.updateData { current ->
            val seededRegistry = seeded(current)
            seededRegistry.copy(
                profiles = seededRegistry.profiles.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    private fun seeded(registry: ProfileRegistry): ProfileRegistry = when {
        registry.profiles.isEmpty() -> ProfileRegistry(
            profiles = listOf(
                LauncherProfile(id = seedProfileId, name = DEFAULT_PROFILE_NAME),
            ),
            activeProfileId = seedProfileId,
        )
        // A registry whose active id names nothing — a half-applied delete, or a
        // hand-edited file — would otherwise render as no profile at all.
        registry.profiles.none { it.id == registry.activeProfileId } ->
            registry.copy(activeProfileId = registry.profiles.first().id)
        else -> registry
    }

    /**
     * The id the first profile takes, decided once.
     *
     * Two properties matter here, and the first version had neither.
     *
     * It is *stable*. A random id was minted on every read that found an empty
     * registry, and since a read does not write, the next launch minted another
     * — a new empty profile directory each time, with the previous launch's
     * settings and library stranded under the id before it. Everything looked
     * reset on every start. A fixed id means that even if nothing is ever
     * persisted, every launch lands on the same directory.
     *
     * And it *recovers*. Anyone who already ran that version has real data under
     * a directory whose id was lost, so an empty registry looks on disk first
     * and adopts what it finds rather than inventing a name beside it.
     */
    private val seedProfileId: String by lazy {
        val candidates = ProfileFiles.existingIds(context)
            .filter(LauncherProfile::isValidId)
            .map { it to ProfileFiles.dataWeight(context, it) }
        chooseSeedProfileId(candidates, DEFAULT_PROFILE_ID).also { chosen ->
            if (chosen != DEFAULT_PROFILE_ID) {
                ThorLog.i(TAG, "Adopting profile directory $chosen left without a registry")
            }
        }
    }

    private companion object {
        const val TAG = "ProfileRegistry"
        const val DEFAULT_PROFILE_NAME = "Player 1"

        /**
         * Fixed, not generated. See [seedProfileId] — a generated first id is
         * only as durable as the write that records it.
         */
        const val DEFAULT_PROFILE_ID = "default"
    }
}
