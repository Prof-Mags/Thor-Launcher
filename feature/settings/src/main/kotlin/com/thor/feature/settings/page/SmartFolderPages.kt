package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.FolderEntry
import com.thor.core.model.Platform
import com.thor.core.model.SmartFolderPreset
import com.thor.core.model.SmartQuery
import com.thor.core.model.SortOrder
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.row.IntSliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.TextFieldRow

/**
 * Folders that fill themselves.
 *
 * A smart folder is a *query* rather than a list: it holds whatever currently
 * matches, and never needs maintaining. All of that already worked — the query
 * model, the evaluator, the storage and every surface that meets one — and the
 * only missing piece was any way to write a query without writing Kotlin. This
 * page is that piece.
 *
 * Two states, as the theme editor has, and for the same reason: closed, it is a
 * short list of what you have and the ways to get another; open, it is one
 * folder's fields. A settings rail with "Smart folders" and "Editing a smart
 * folder" as separate destinations would be describing the software's modes
 * rather than the user's task.
 *
 * Presets lead because the useful queries are a small set that everyone wants and
 * nobody enjoys assembling — "what have I not played", "what was I playing" — and
 * because a page of twelve empty fields is not somewhere anyone starts. Every
 * field stays editable afterwards, so a preset is a beginning rather than a menu.
 */
@Composable
internal fun SmartFoldersPage(
    folders: List<FolderEntry>,
    platforms: List<Platform>,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    /** Which folder is open, from [SettingsViewModel.editingSmartFolderId]. */
    editingId: String?,
    status: String?,
) {
    val editing = folders.firstOrNull { it.id == editingId }
    if (editing == null) {
        SmartFolderList(folders, focusedRow, viewModel, status)
    } else {
        SmartFolderFields(editing, platforms, focusedRow, viewModel, status)
    }
}

@Composable
private fun SmartFolderList(
    folders: List<FolderEntry>,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
) {
    InfoRow(
        "Smart folders",
        status ?: "A smart folder holds whatever matches its query rather than " +
            "what you put in it, so it keeps itself up to date as the library " +
            "changes. Start from one of the presets, then change anything.",
    )
    RowDivider()

    ChoiceRow(
        title = "New smart folder",
        subtitle = "Creates it on the grid and opens it here",
        options = SmartFolderPreset.entries,
        // No selection to remember: the row is an action wearing a chooser, so it
        // rests on the first preset rather than pretending to recall a past one.
        selected = SmartFolderPreset.entries.first(),
        label = SmartFolderPreset::label,
        optionDescription = { it.description },
        focused = focusedRow == 0,
        onSelected = viewModel::createSmartFolder,
    )

    if (folders.isEmpty()) return

    RowDivider()
    folders.forEachIndexed { index, folder ->
        ActionRow(
            title = folder.title,
            subtitle = folder.smartQuery?.let(::describeQuery) ?: "Everything",
            focused = focusedRow == SMART_FOLDER_LIST_FIRST_ROW + index,
            trailingLabel = "Edit",
            onClick = { viewModel.editSmartFolder(folder.id) },
        )
        if (index != folders.lastIndex) RowDivider()
    }
}

/**
 * One folder's query, field by field.
 *
 * Every one of [SmartQuery]'s fields is here except the tag and genre sets, which
 * have no fixed vocabulary to choose from — they are whatever the scrapers
 * happened to return for your library, so a picker over them would be a list of
 * several hundred strings that differs per install. Title contains covers the
 * same ground for now.
 */
@Composable
private fun SmartFolderFields(
    folder: FolderEntry,
    platforms: List<Platform>,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
) {
    val query = folder.smartQuery ?: SmartQuery()

    InfoRow(folder.title, status ?: describeQuery(query))
    RowDivider()

    TextFieldRow(
        title = "Name",
        subtitle = "What the folder is called on the grid",
        value = folder.title,
        focused = focusedRow == 0,
        onValueChange = { name -> viewModel.renameSmartFolder(folder.id, name) },
    )
    RowDivider()

    // A single platform rather than the model's set, for the same reason as tags:
    // a multi-select over 47 systems is a worse control than one that says "any".
    ChoiceRow(
        title = "System",
        subtitle = "Limit the folder to one console, or leave it open to all",
        options = listOf<Platform?>(null) + platforms,
        selected = platforms.firstOrNull { it.id in query.platformIds },
        label = { it?.name ?: ANY },
        focused = focusedRow == 1,
        onSelected = { platform ->
            viewModel.updateSmartQuery(folder.id) {
                it.copy(platformIds = setOfNotNull(platform?.id))
            }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Favourites only",
        subtitle = "Only games you have starred",
        checked = query.favoritesOnly,
        focused = focusedRow == 2,
        onCheckedChange = { on ->
            viewModel.updateSmartQuery(folder.id) { it.copy(favoritesOnly = on) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Unplayed only",
        subtitle = "Games you have never started. The other half of a backlog " +
            "folder, and mutually exclusive with a recently-played window.",
        checked = query.unplayedOnly,
        focused = focusedRow == 3,
        onCheckedChange = { on ->
            viewModel.updateSmartQuery(folder.id) { it.copy(unplayedOnly = on) }
        },
    )
    RowDivider()
    IntSliderRow(
        title = "Played within",
        subtitle = "Only games played this recently. Zero turns the window off.",
        value = query.playedWithinDays ?: 0,
        range = 0..NINETY_DAYS,
        focused = focusedRow == 4,
        suffix = " days",
        onValueChange = { days ->
            viewModel.updateSmartQuery(folder.id) {
                it.copy(playedWithinDays = days.takeIf { d -> d > 0 })
            }
        },
    )
    RowDivider()
    IntSliderRow(
        title = "Minimum rating",
        subtitle = "Out of 100, as the scrapers report it. Zero accepts anything, " +
            "including games nothing has rated.",
        value = query.minRating ?: 0,
        range = 0..100,
        focused = focusedRow == 5,
        onValueChange = { rating ->
            viewModel.updateSmartQuery(folder.id) {
                it.copy(minRating = rating.takeIf { r -> r > 0 })
            }
        },
    )
    RowDivider()
    /*
     * A chooser rather than a slider, and not a matter of taste.
     *
     * `IntSliderRow` picks a step that crosses its range in about ten presses, so
     * a range of 0..2035 stepped in *two hundred and fifty years*: 0, 250, 500.
     * There was no way to express 1995 at all. Years are not a continuum anybody
     * scrubs through — they are picked — and five-year marks from 1970 cover every
     * console generation there is.
     */
    ChoiceRow(
        title = "Released after",
        subtitle = "Only games from this year onwards",
        options = YEAR_OPTIONS,
        selected = query.releasedAfterYear,
        label = { it?.toString() ?: ANY },
        focused = focusedRow == 6,
        onSelected = { year ->
            viewModel.updateSmartQuery(folder.id) { it.copy(releasedAfterYear = year) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Released before",
        subtitle = "With the row above, this is how a folder for one console " +
            "generation is made",
        options = YEAR_OPTIONS,
        selected = query.releasedBeforeYear,
        label = { it?.toString() ?: ANY },
        focused = focusedRow == 7,
        onSelected = { year ->
            viewModel.updateSmartQuery(folder.id) { it.copy(releasedBeforeYear = year) }
        },
    )
    RowDivider()
    TextFieldRow(
        title = "Title contains",
        subtitle = "Matches part of a name — \"Mario\", \"Final Fantasy\"",
        value = query.titleContains.orEmpty(),
        placeholder = "Any",
        focused = focusedRow == 8,
        onValueChange = { text ->
            viewModel.updateSmartQuery(folder.id) {
                it.copy(titleContains = text.trim().takeIf(String::isNotEmpty))
            }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Order",
        subtitle = "How the folder's contents are sorted",
        options = SortOrder.entries,
        selected = query.sort,
        label = SortOrder::label,
        focused = focusedRow == 9,
        onSelected = { order ->
            viewModel.updateSmartQuery(folder.id) { it.copy(sort = order) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Reverse the order",
        subtitle = "Newest, highest or longest first",
        checked = query.sortDescending,
        focused = focusedRow == 10,
        onCheckedChange = { on ->
            viewModel.updateSmartQuery(folder.id) { it.copy(sortDescending = on) }
        },
    )
    RowDivider()
    IntSliderRow(
        title = "Keep at most",
        subtitle = "Caps the folder, which is what makes a \"last ten games\" " +
            "folder stay ten. Zero keeps everything that matches.",
        value = query.limit ?: 0,
        range = 0..MAX_LIMIT,
        focused = focusedRow == 11,
        onValueChange = { limit ->
            viewModel.updateSmartQuery(folder.id) {
                it.copy(limit = limit.takeIf { l -> l > 0 })
            }
        },
    )
    RowDivider()
    ActionRow(
        title = "Delete this folder",
        subtitle = "The folder goes; nothing inside it does, because nothing is " +
            "inside it — the games were only ever matching it.",
        focused = focusedRow == 12,
        trailingLabel = "Delete",
        destructive = true,
        onClick = { viewModel.deleteSmartFolder(folder.id) },
    )
    RowDivider()
    ActionRow(
        title = "Done",
        subtitle = "Back to the list",
        focused = focusedRow == 13,
        trailingLabel = "Done",
        onClick = { viewModel.editSmartFolder(null) },
    )
}

/**
 * A query in a sentence.
 *
 * Worth the trouble because the list is otherwise a column of folder names with
 * nothing to tell them apart, and because it is the only confirmation that a
 * field did what the user expected — a row saying "0" is not obviously "off".
 */
internal fun describeQuery(query: SmartQuery): String {
    val parts = buildList {
        if (query.favoritesOnly) add("favourites")
        if (query.unplayedOnly) add("unplayed")
        query.playedWithinDays?.let { add("played in $it days") }
        query.minRating?.let { add("rated $it+") }
        query.releasedAfterYear?.let { add("after $it") }
        query.releasedBeforeYear?.let { add("before $it") }
        query.titleContains?.let { add("named \"$it\"") }
        if (query.platformIds.isNotEmpty()) add("one system")
    }
    val what = if (parts.isEmpty()) "Everything" else parts.joinToString(", ")
        .replaceFirstChar(Char::uppercase)
    val order = ", by ${query.sort.label.lowercase()}"
    val cap = query.limit?.let { ", top $it" }.orEmpty()
    return "$what$order$cap"
}

/** The preset chooser sits above the list of folders. */
private const val SMART_FOLDER_LIST_FIRST_ROW = 1

/** Name, system, two switches, six numbers, order, reverse, cap, delete, done. */
internal const val SMART_FOLDER_EDIT_ROWS = 14

internal fun smartFolderRows(folderCount: Int, editing: Boolean): Int =
    if (editing) SMART_FOLDER_EDIT_ROWS else SMART_FOLDER_LIST_FIRST_ROW + folderCount

private const val ANY = "Any"

/** A quarter is as far back as "recently" stretches before it stops meaning it. */
private const val NINETY_DAYS = 90

/**
 * "Any", then five-year marks covering every console generation.
 *
 * From 1970 because nothing Loki emulates predates it, and to 2035 so the list
 * does not need revisiting. Thirteen entries is a chooser somebody can walk.
 */
private val YEAR_OPTIONS: List<Int?> = listOf(null) + (1970..2035 step 5).toList()

/** Beyond this a cap is not capping anything a grid page could show. */
private const val MAX_LIMIT = 100
