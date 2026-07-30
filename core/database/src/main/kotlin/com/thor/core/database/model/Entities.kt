package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameMetadata
import com.thor.core.model.PerformanceProfile
import com.thor.core.model.SmartQuery

/**
 * Room entities backing the launcher library.
 *
 * Rich value objects ([GameMetadata], [ArtworkSet], [SmartQuery]) are stored as
 * JSON columns via the type converters rather than being flattened into dozens
 * of nullable columns. They are only ever read as a whole, never queried
 * field-by-field, so normalising them would cost joins and buy nothing.
 * Anything the launcher *does* filter or sort on gets a real, indexed column.
 */

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

@Entity(
    tableName = "folders",
    indices = [Index(value = ["sort_title"])],
)
data class FolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "accent_argb") val accentArgb: Long? = null,
    @ColumnInfo(name = "icon_key") val iconKey: String = "folder",
    @ColumnInfo(name = "artwork_uri") val artworkUri: String? = null,
    /** Ordered child ids; empty for smart folders, which compute their own. */
    @ColumnInfo(name = "child_ids") val childIds: List<String> = emptyList(),
    @ColumnInfo(name = "smart_query") val smartQuery: SmartQuery? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
)

/** A page of the bottom-screen grid. */
@Entity(
    tableName = "pages",
    indices = [Index(value = ["page_index"], unique = true)],
)
data class PageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "title") val title: String = "",
    @ColumnInfo(name = "wallpaper_uri") val wallpaperUri: String? = null,
)

/**
 * Where an entry sits on the grid.
 *
 * `entry_id` deliberately has no foreign key: it may point at an app, a game, a
 * folder or a shortcut, which Room cannot express as a single constraint. The
 * repository prunes orphans after every scan instead.
 */
@Entity(
    tableName = "placements",
    indices = [
        Index(value = ["entry_id"], unique = true),
        Index(value = ["page_index", "row", "column_index"]),
        Index(value = ["parent_folder_id"]),
    ],
)
data class PlacementEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "row") val row: Int,
    @ColumnInfo(name = "column_index") val column: Int,
    /** Non-null when the entry lives inside a folder rather than on a page. */
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: String? = null,
    /** Position within the parent folder. */
    @ColumnInfo(name = "folder_index") val folderIndex: Int = 0,
    /** True for dock slots; [column] is then the slot number. */
    @ColumnInfo(name = "is_dock") val isDock: Boolean = false,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "accent_argb") val accentArgb: Long? = null,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String? = null,
    @ColumnInfo(name = "sort_index") val sortIndex: Int = 0,
)

@Entity(
    tableName = "collection_entries",
    primaryKeys = ["collection_id", "entry_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id"]), Index(value = ["collection_id"])],
)
data class CollectionEntryCrossRef(
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "position") val position: Int = 0,
)

/**
 * One play session. Kept as individual rows rather than a running total so the
 * detail panel can show a play history and so totals can be recomputed if a
 * session is ever recorded twice.
 */
@Entity(
    tableName = "play_sessions",
    indices = [Index(value = ["entry_id"]), Index(value = ["started_at"])],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMs: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
)

/** A single RetroAchievements achievement and this user's progress on it. */
@Entity(
    tableName = "achievements",
    indices = [Index(value = ["entry_id"])],
)
data class AchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "points") val points: Int,
    @ColumnInfo(name = "badge_uri") val badgeUri: String? = null,
    @ColumnInfo(name = "earned_at") val earnedEpochMs: Long? = null,
    @ColumnInfo(name = "is_hardcore") val isHardcore: Boolean = false,
)
