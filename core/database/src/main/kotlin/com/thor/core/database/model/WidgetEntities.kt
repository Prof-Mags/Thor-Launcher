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
    /**
     * The flattened `ComponentName` of an app widget's provider, or the name of a
     * [com.thor.core.model.LauncherWidget] for one the launcher draws itself.
     *
     * One column for both because it is the same fact — which widget this is —
     * and [kind] already says how to read it. A second nullable column would
     * have made "both set" and "neither set" expressible, and neither is.
     */
    @ColumnInfo(name = "provider") val provider: String,
    /** The provider's own label at the time it was placed. */
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "span_columns") val spanColumns: Int,
    @ColumnInfo(name = "span_rows") val spanRows: Int,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long,
    /**
     * `app` for a hosted Android widget, `builtin` for one of the launcher's own.
     *
     * Defaulted rather than inferred from the id's sign, which is what an earlier
     * draft did: negative ids happen to be safe today because the platform hands
     * out positive ones, but that is a property of an implementation nobody
     * promised, and a row whose meaning depends on it cannot be read by eye.
     */
    @ColumnInfo(name = "kind", defaultValue = KIND_APP) val kind: String = KIND_APP,
) {
    companion object {
        const val KIND_APP = "app"
        const val KIND_BUILT_IN = "builtin"
    }
}
