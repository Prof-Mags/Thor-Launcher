package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A widget the user has placed, and the size they gave it.
 *
 * Stored separately from the entry tables rather than as another column on one
 * of them, because a widget shares almost nothing with a game or an app: it has
 * no package to launch, no artwork to scrape, no play history and no sort title
 * anybody will ever sort by. What it does share is a cell on the grid, which is
 * why its placement still lives in `placements` alongside everything else.
 *
 * [appWidgetId] is the primary key because it *is* the identity: the host
 * allocates it, the platform keys its own bookkeeping on it, and the same
 * provider placed twice produces two rows that differ in nothing else.
 *
 * Not exported with a profile. The id is meaningless outside the host that
 * allocated it, so carrying these to another install would restore a grid full
 * of widgets that cannot be bound to anything.
 */
@Entity(
    tableName = "widgets",
    indices = [Index(value = ["provider"])],
)
data class WidgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    /** Flattened `ComponentName`, so the provider can be named after a restart. */
    @ColumnInfo(name = "provider") val provider: String,
    /** The provider's own label at the time it was placed. */
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "span_columns") val spanColumns: Int,
    @ColumnInfo(name = "span_rows") val spanRows: Int,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long,
)
