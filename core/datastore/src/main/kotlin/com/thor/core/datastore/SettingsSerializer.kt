package com.thor.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.thor.core.model.ThorSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Reads and writes [ThorSettings] as a single JSON document.
 *
 * `ignoreUnknownKeys` plus per-field defaults gives free forwards and backwards
 * compatibility: a settings file written by a newer build still loads, and a
 * field added later simply takes its default on first read.
 */
class SettingsSerializer @Inject constructor() : Serializer<ThorSettings> {

    override val defaultValue: ThorSettings = ThorSettings.DEFAULT

    override suspend fun readFrom(input: InputStream): ThorSettings = try {
        json.decodeFromString(
            deserializer = ThorSettings.serializer(),
            string = input.readBytes().decodeToString(),
        ).migrated()
    } catch (e: SerializationException) {
        // A corrupt settings file must not brick the launcher; DataStore replaces
        // it with the default once we signal corruption.
        throw CorruptionException("Unable to read THOR settings", e)
    } catch (e: IllegalArgumentException) {
        /*
         * Not every malformed document arrives as a `SerializationException`.
         *
         * `isLenient` widens what the parser will accept, and what it rejects it can
         * reject as a plain `IllegalArgumentException` — a bad numeric literal or an
         * out-of-range enum ordinal, say. Those escaped the catch above and came out
         * of `readFrom` unhandled, which DataStore treats as a read failure rather
         * than as corruption: it does not replace the file, so every subsequent read
         * fails the same way and the launcher cannot load its settings again at all.
         * Signalling corruption is what lets it recover to defaults.
         */
        throw CorruptionException("Unable to read THOR settings", e)
    }

    override suspend fun writeTo(t: ThorSettings, output: OutputStream) {
        output.write(
            json.encodeToString(ThorSettings.serializer(), t).encodeToByteArray(),
        )
    }

    /**
     * Applies schema migrations.
     *
     * Version 1 is the initial schema, so this is currently a version stamp
     * only; future migrations branch on [ThorSettings.schemaVersion] here.
     */
    private fun ThorSettings.migrated(): ThorSettings =
        if (schemaVersion == ThorSettings.CURRENT_SCHEMA_VERSION) {
            this
        } else {
            copy(schemaVersion = ThorSettings.CURRENT_SCHEMA_VERSION)
        }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            isLenient = true
        }
    }
}
