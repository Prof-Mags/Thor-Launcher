package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thor.core.model.SmartQuery

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
