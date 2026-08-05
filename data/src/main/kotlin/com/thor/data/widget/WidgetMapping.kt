package com.thor.data.widget

import com.thor.core.database.model.WidgetEntity
import com.thor.core.model.CellSpan
import com.thor.core.model.LauncherWidget
import com.thor.core.model.WidgetEntry

/**
 * Widget entity ↔ domain mapping.
 *
 * Its own file rather than a few more functions in the library's `EntityMapping`:
 * a widget is not part of the library. It is never scanned, never scraped, never
 * searched and never sorted, and the only thing it shares with a game is a cell.
 */

fun WidgetEntity.toDomain(): WidgetEntry = WidgetEntry(
    id = WidgetEntry.idFor(appWidgetId),
    title = label,
    // Nothing sorts widgets — they are placed by hand and never listed — but the
    // interface requires one, and the label is the only string here.
    sortTitle = label.lowercase(),
    appWidgetId = appWidgetId,
    providerComponent = provider,
    // Null for a hosted app widget, which is what `kind` distinguishes. A row
    // marked built-in whose name no longer parses — a widget removed from a
    // later build — resolves to null and is then drawn as unavailable, which is
    // the same treatment an uninstalled provider gets.
    builtIn = if (kind == WidgetEntity.KIND_BUILT_IN) LauncherWidget.from(provider) else null,
    spanColumns = spanColumns,
    spanRows = spanRows,
)

val WidgetEntry.span: CellSpan get() = CellSpan(columns = spanColumns, rows = spanRows)
