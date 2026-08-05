package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.Platform
import com.thor.core.model.RomDirectory
import com.thor.core.model.SortOrder
import com.thor.core.model.ThorSettings
import com.thor.data.sync.ScrapeState
import com.thor.data.sync.SyncState
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.AddSystemRow
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.DirectoryPickerRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.SystemRow
import com.thor.feature.settings.PlatformEmulatorOption
import com.thor.feature.settings.SettingsViewModel

@Composable
internal fun PlatformsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    platformOptions: List<PlatformEmulatorOption>,
    availablePlatforms: List<Platform>,
    scanState: SyncState,
    scrapeState: ScrapeState,
) {
    platformOptions.forEachIndexed { index, option ->
        val scrapeProgress = (scrapeState as? ScrapeState.Running)
            ?.takeIf { it.platformId == option.platform.id }
            ?.let { "${it.done}/${it.total}" }
        if (index > 0) RowDivider()
        SystemRow(
            platform = option.platform,
            emulators = option.emulators,
            romFolder = settings.library.romDirectoryUris
                .firstOrNull { it.platformId == option.platform.id }
                ?.displayName,
            focused = focusedRow == index,
            scrapeProgress = scrapeProgress,
            onEditEmulators = { viewModel.openEmulatorPicker(option.platform.id) },
            onScrape = { viewModel.scrapePlatform(option.platform.id) },
            onRemove = { viewModel.removePlatform(option.platform.id) },
        )
    }

    if (platformOptions.isNotEmpty()) RowDivider()

    AddSystemRow(
        available = availablePlatforms,
        focused = focusedRow == platformOptions.size,
        onAdd = viewModel::beginAddPlatform,
    )

    if (platformOptions.isNotEmpty()) {
        RowDivider()
        ActionRow(
            title = "Scan library now",
            subtitle = when (scanState) {
                is SyncState.Scanning -> "Scanning ${scanState.label} — ${scanState.found} found"
                is SyncState.Completed ->
                    "Found ${scanState.gamesFound} games and ${scanState.appsFound} apps"

                is SyncState.Failed -> scanState.message
                SyncState.Idle -> "Re-read every configured folder"
            },
            focused = focusedRow == platformOptions.size + 1,
            trailingLabel = if (scanState is SyncState.Scanning) "Running" else "Scan",
            onClick = viewModel::scanLibrary,
        )
    }
}

@Composable
internal fun RomFoldersPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val library = settings.library
    val extras = library.romDirectoryUris.filter { it.platformId == null }

    if (extras.isEmpty()) {
        InfoRow(
            title = "No extra folders",
            value = "Platforms bring their own",
        )
        RowDivider()
    }

    extras.forEachIndexed { index, directory ->
        ActionRow(
            title = directory.displayName,
            subtitle = "Mixed folder — platform detected per file",
            focused = focusedRow == index,
            trailingLabel = "Remove",
            onClick = {
                viewModel.updateLibrary { current ->
                    current.copy(romDirectoryUris = current.romDirectoryUris - directory)
                }
            },
        )
        RowDivider()
    }

    DirectoryPickerRow(
        title = "Add folder",
        subtitle = "For collections spanning several systems",
        focused = focusedRow == extras.size,
        onPicked = { uri, name ->
            viewModel.updateLibrary { current ->
                // Re-adding a folder must not create a duplicate that would then
                // be scanned twice.
                if (current.romDirectoryUris.any { it.uri == uri }) {
                    current
                } else {
                    current.copy(
                        romDirectoryUris = current.romDirectoryUris +
                            RomDirectory(uri = uri, displayName = name),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ScanningPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    gridClearResult: String?,
) {
    val library = settings.library

    SwitchRow(
        title = "Look inside archives",
        subtitle = "Scan .zip and .7z containers",
        checked = library.scanArchives,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(scanArchives = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Detect duplicates",
        checked = library.detectDuplicates,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(detectDuplicates = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Group versions",
        subtitle = "Collapse regional variants and revisions into one entry",
        checked = library.groupVersions,
        focused = focusedRow == 2,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(groupVersions = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Show apps on the grid",
        checked = library.showAppsOnGrid,
        focused = focusedRow == 3,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(showAppsOnGrid = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Hide system apps",
        checked = library.hideSystemApps,
        focused = focusedRow == 4,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(hideSystemApps = on) } },
    )
    RowDivider()
    // The way back. Hiding survives rescans, so without this an entry hidden by
    // mistake has no cell to long-press and no list that mentions it.
    SwitchRow(
        title = "Show hidden entries",
        subtitle = "Reveal hidden games and apps, dimmed, so they can be restored",
        checked = library.showHiddenEntries,
        focused = focusedRow == 5,
        onCheckedChange = { on ->
            viewModel.updateLibrary { it.copy(showHiddenEntries = on) }
        },
    )
    RowDivider()
    ActionRow(
        title = "Scan library now",
        subtitle = "Apply these settings to the whole library",
        focused = focusedRow == 6,
        trailingLabel = "Scan",
        onClick = viewModel::scanLibrary,
    )
    RowDivider()
    /*
     * Clearing the grid, not the library.
     *
     * Marked destructive because it undoes arranging that may have taken a
     * while, but it is recoverable in a way deleting is not: the games stay
     * scanned, stay searchable and stay inside their platform folders, so a
     * rescan files them back. Says how many it took, because everything it does
     * happens on a screen the user is not currently looking at.
     */
    ActionRow(
        title = "Remove all games from the grid",
        subtitle = gridClearResult
            ?: "Clears every game's cell. The games stay in your library and in " +
            "their platform folders — only the grid is emptied.",
        focused = focusedRow == 7,
        trailingLabel = "Remove",
        destructive = true,
        onClick = viewModel::clearGamesFromGrid,
    )
}


@Composable
internal fun SortingPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val library = settings.library

    ChoiceRow(
        title = "Default sort",
        options = SortOrder.entries,
        selected = library.defaultSort,
        focused = focusedRow == 0,
        label = SortOrder::label,
        onSelected = { order -> viewModel.updateLibrary { it.copy(defaultSort = order) } },
    )
    RowDivider()
    SwitchRow(
        title = "Reverse order",
        checked = library.sortDescending,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateLibrary { it.copy(sortDescending = on) } },
    )
}
