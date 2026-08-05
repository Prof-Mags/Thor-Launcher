package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
