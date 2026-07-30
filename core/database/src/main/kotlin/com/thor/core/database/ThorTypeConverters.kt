package com.thor.core.database

import androidx.room.TypeConverter
import com.thor.core.model.GameMetadata
import com.thor.core.model.PerformanceProfile
import com.thor.core.model.SmartQuery
import kotlinx.serialization.json.Json

/**
 * JSON-backed converters for the composite columns.
 *
 * `ignoreUnknownKeys` matters for forward compatibility: a database written by
 * a newer build that added a metadata field must still be readable after a
 * downgrade instead of throwing on every row.
 */
internal val ThorJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

class ThorTypeConverters {

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? =
        value?.let { ThorJson.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        value?.let { runCatching { ThorJson.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: emptyList()

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>?): String? =
        value?.let { ThorJson.encodeToString(it) }

    @TypeConverter
    fun jsonToStringMap(value: String?): Map<String, String> =
        value?.let { runCatching { ThorJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?: emptyMap()

    @TypeConverter
    fun metadataToJson(value: GameMetadata?): String? =
        value?.let { ThorJson.encodeToString(it) }

    @TypeConverter
    fun jsonToMetadata(value: String?): GameMetadata =
        value?.let { runCatching { ThorJson.decodeFromString<GameMetadata>(it) }.getOrNull() }
            ?: GameMetadata.EMPTY

    @TypeConverter
    fun smartQueryToJson(value: SmartQuery?): String? =
        value?.let { ThorJson.encodeToString(it) }

    @TypeConverter
    fun jsonToSmartQuery(value: String?): SmartQuery? =
        value?.let { runCatching { ThorJson.decodeFromString<SmartQuery>(it) }.getOrNull() }

    @TypeConverter
    fun performanceProfileToString(value: PerformanceProfile): String = value.name

    @TypeConverter
    fun stringToPerformanceProfile(value: String?): PerformanceProfile =
        value?.let { runCatching { PerformanceProfile.valueOf(it) }.getOrNull() }
            ?: PerformanceProfile.BALANCED
}
