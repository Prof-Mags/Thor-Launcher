package com.thor.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.thor.core.model.ProfileRegistry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Reads and writes the profile registry.
 *
 * Deliberately separate from [SettingsSerializer]: settings are per-profile and
 * this document is what decides which profile's settings to read, so it cannot
 * live inside one.
 */
class ProfileRegistrySerializer @Inject constructor() : Serializer<ProfileRegistry> {

    override val defaultValue: ProfileRegistry = ProfileRegistry.EMPTY

    override suspend fun readFrom(input: InputStream): ProfileRegistry = try {
        json.decodeFromString(
            ProfileRegistry.serializer(),
            input.readBytes().decodeToString(),
        )
    } catch (e: SerializationException) {
        throw CorruptionException("Unable to read profiles", e)
    } catch (e: IllegalArgumentException) {
        // Same reasoning as the settings serializer: a malformed document that
        // is not reported as corruption fails every subsequent read instead of
        // being replaced, which would leave the launcher with no profile at all.
        throw CorruptionException("Unable to read profiles", e)
    }

    override suspend fun writeTo(t: ProfileRegistry, output: OutputStream) {
        output.write(
            json.encodeToString(ProfileRegistry.serializer(), t).encodeToByteArray(),
        )
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
