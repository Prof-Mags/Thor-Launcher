package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thor.core.model.GameMetadata
import com.thor.core.model.PerformanceProfile

@Entity(
    tableName = "apps",
    indices = [
        Index(value = ["package_name", "user_serial"], unique = true),
        Index(value = ["sort_title"]),
        Index(value = ["is_favorite"]),
    ],
)
data class AppEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long = 0L,
    @ColumnInfo(name = "version_name") val versionName: String? = null,
    @ColumnInfo(name = "installed_at") val installedAtEpochMs: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long = 0L,
    @ColumnInfo(name = "is_emulator") val isEmulator: Boolean = false,
    @ColumnInfo(name = "is_system") val isSystemApp: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "last_played_at") val lastPlayedEpochMs: Long? = null,
    @ColumnInfo(name = "launch_count") val launchCount: Int = 0,
    @ColumnInfo(name = "total_play_millis") val totalPlayMillis: Long = 0L,
    /** Custom artwork chosen by the user; overrides the packaged icon. */
    @ColumnInfo(name = "custom_icon_uri") val customIconUri: String? = null,
)


@Entity(
    tableName = "platforms",
    indices = [Index(value = ["sort_index"])],
)
data class PlatformEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    @ColumnInfo(name = "manufacturer") val manufacturer: String,
    @ColumnInfo(name = "release_year") val releaseYear: Int?,
    @ColumnInfo(name = "accent_argb") val accentArgb: Long,
    @ColumnInfo(name = "rom_extensions") val romExtensions: List<String>,
    @ColumnInfo(name = "provider_ids") val providerIds: Map<String, String>,
    /** Assigned emulators in preference order; the first is the default. */
    @ColumnInfo(name = "emulator_packages", defaultValue = "[]")
    val emulatorPackages: List<String> = emptyList(),
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false,
    /** True once the user adds this system to their setup. */
    @ColumnInfo(name = "is_added", defaultValue = "0") val isAdded: Boolean = false,
    @ColumnInfo(name = "sort_index") val sortIndex: Int = 0,
    /*
     * Artwork supplied by an installed icon pack.
     *
     * Columns on the platform rather than a table of their own: a platform wears
     * at most one pack's artwork at a time, so this is a one-to-one relationship
     * and a join table would be three extra queries to express "sometimes null".
     * [artworkPackId] is what lets removing a pack put back exactly what it
     * changed — without it, uninstalling would have to either strip every
     * platform's artwork or leave orphans behind.
     */
    @ColumnInfo(name = "artwork_icon_uri") val artworkIconUri: String? = null,
    @ColumnInfo(name = "artwork_hero_uri") val artworkHeroUri: String? = null,
    @ColumnInfo(name = "artwork_logo_uri") val artworkLogoUri: String? = null,
    @ColumnInfo(name = "artwork_pack_id") val artworkPackId: String? = null,
)


@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platform_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["platform_id"]),
        Index(value = ["sort_title"]),
        Index(value = ["is_favorite"]),
        Index(value = ["last_played_at"]),
        Index(value = ["duplicate_key"]),
        Index(value = ["content_uri"], unique = true),
    ],
)
data class GameEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "platform_id") val platformId: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSizeBytes: Long,
    /** Grouping key for regional variants and revisions of one title. */
    @ColumnInfo(name = "duplicate_key") val duplicateKey: String,
    @ColumnInfo(name = "metadata") val metadata: GameMetadata,
    @ColumnInfo(name = "emulator_package") val emulatorPackage: String? = null,
    @ColumnInfo(name = "tags") val tags: List<String> = emptyList(),
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long = 0L,
    @ColumnInfo(name = "last_played_at") val lastPlayedEpochMs: Long? = null,
    @ColumnInfo(name = "first_played_at") val firstPlayedEpochMs: Long? = null,
    @ColumnInfo(name = "launch_count") val launchCount: Int = 0,
    @ColumnInfo(name = "total_play_millis") val totalPlayMillis: Long = 0L,
    @ColumnInfo(name = "performance_profile") val performanceProfile: PerformanceProfile =
        PerformanceProfile.BALANCED,
    /** Set when the file was missing during the last scan. */
    @ColumnInfo(name = "is_missing") val isMissing: Boolean = false,
    /*
     * The file's own fingerprints, computed once.
     *
     * On the game rather than in its metadata because they are facts about the
     * file, not about the game: a rescrape replaces metadata wholesale and would
     * throw these away, and they cost a full read of the ROM to recover.
     *
     * Null means not hashed — either not yet, or too large to be worth reading;
     * see `RomHasher.MAX_HASHED_BYTES`. Both are answered the same way, by
     * matching on the name instead.
     */
    @ColumnInfo(name = "rom_crc32") val romCrc32: String? = null,
    @ColumnInfo(name = "rom_md5") val romMd5: String? = null,
    @ColumnInfo(name = "rom_sha1") val romSha1: String? = null,
)


/**
 * An alternate dump of a game already present in [GameEntity] — a different
 * region, revision or disc.
 */
@Entity(
    tableName = "game_versions",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["game_id"]), Index(value = ["content_uri"], unique = true)],
)
data class GameVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "game_id") val gameId: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSizeBytes: Long,
    @ColumnInfo(name = "region") val region: String? = null,
    @ColumnInfo(name = "languages") val languages: List<String> = emptyList(),
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
)
