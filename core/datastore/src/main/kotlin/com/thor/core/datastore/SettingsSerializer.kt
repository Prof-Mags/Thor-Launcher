package com.thor.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.thor.core.model.ControllerCommand
import com.thor.core.model.LauncherAction
import com.thor.core.model.ThorSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        val document = json.parseToJsonElement(input.readBytes().decodeToString())
        json.decodeFromJsonElement(
            deserializer = ThorSettings.serializer(),
            element = document.withRetiredValuesDropped(),
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
     * Removes values naming something the launcher no longer has.
     *
     * `coerceInputValues` already covers the easy shape — a *property* whose
     * stored enum constant has been retired takes its declared default. It does
     * not cover a retired value held anywhere else, and the launcher stores them
     * in two such places:
     *
     *  - `controls.customProfiles[].bindings`, a `Map<Int, ControllerCommand>`,
     *    where the command is a map *value* and so has no default to fall back to
     *  - `dock.slots`, a list of a sealed type, where an unknown class
     *    discriminator has no default branch either
     *
     * Both threw, and a throw here is reported as corruption, which has DataStore
     * replace the entire document with defaults. Deleting one command therefore
     * reset the grid, the themes, the ROM folders and the API keys of anyone who
     * had bound a button to it. That is what happened when the notifications
     * command and dock action were removed.
     *
     * Each suspect value is decoded on its own and dropped if it fails, rather
     * than checked against a list of names known to be retired. A list has to be
     * remembered at exactly the moment someone is deleting something and thinking
     * about anything else; this cannot fall out of date, and it covers the next
     * removal as well as the last one.
     */
    private fun JsonElement.withRetiredValuesDropped(): JsonElement {
        val root = this as? JsonObject ?: return this

        fun <T> decodes(serializer: KSerializer<T>, element: JsonElement): Boolean =
            runCatching { json.decodeFromJsonElement(serializer, element) }.isSuccess

        val controls = (root["controls"] as? JsonObject)?.let { controls ->
            val profiles = (controls["customProfiles"] as? JsonArray)?.map { profile ->
                val obj = profile as? JsonObject ?: return@map profile
                val bindings = obj["bindings"] as? JsonObject ?: return@map profile
                JsonObject(
                    obj + (
                        "bindings" to JsonObject(
                            bindings.filterValues {
                                decodes(ControllerCommand.serializer(), it)
                            },
                        )
                        ),
                )
            } ?: return@let controls
            JsonObject(controls + ("customProfiles" to JsonArray(profiles)))
        }

        /*
         * A slot is replaced rather than removed, because the dock is five fixed
         * positions and dropping one slides every later slot left — the user
         * loses a binding they still have, on top of the one that was retired.
         */
        val dock = (root["dock"] as? JsonObject)?.let { dock ->
            val slots = (dock["slots"] as? JsonArray)?.map { slot ->
                if (decodes(LauncherAction.serializer(), slot)) {
                    slot
                } else {
                    json.encodeToJsonElement(LauncherAction.serializer(), RETIRED_SLOT)
                }
            } ?: return@let dock
            JsonObject(dock + ("slots" to JsonArray(slots)))
        }

        return JsonObject(
            root +
                listOfNotNull(
                    controls?.let { "controls" to it },
                    dock?.let { "dock" to it },
                ),
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
        /**
         * What a slot becomes when whatever it held has been retired.
         *
         * Home, because it is the one action that always exists, always works and
         * cannot do anything the user would regret pressing by accident.
         */
        val RETIRED_SLOT = LauncherAction.GoHome

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            isLenient = true

            /*
             * A value that is no longer valid takes its default instead of
             * failing the read.
             *
             * `ignoreUnknownKeys` covers a *field* that no longer exists; this
             * covers a field whose stored *value* no longer exists, which is a
             * different failure and the more damaging one. Retiring a theme, a
             * wallpaper or a cursor style leaves every settings file naming it
             * holding an enum constant that will not parse — and an unparseable
             * document is reported as corruption, which resets the user's entire
             * configuration rather than the one setting that went stale.
             *
             * With this, a retired theme quietly becomes the default theme and
             * everything else in the file survives.
             */
            coerceInputValues = true
        }
    }
}
