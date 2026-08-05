package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.IconPack
import com.thor.core.model.Platform
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.DirectoryPickerRow
import com.thor.feature.settings.component.row.FilePickerRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.IconPackStatus
import com.thor.feature.settings.SettingsViewModel

/**
 * Switch the bundled artwork on, then import, list and remove packs.
 *
 * Two ways in because packs arrive both ways: extracted into a folder, or still
 * as the archive they were downloaded as. Neither is more correct than the other
 * and guessing wrong means the user cannot find their pack in the picker.
 */
@Composable
internal fun IconPacksPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    packs: List<IconPack>,
    status: IconPackStatus,
    importStatus: String?,
) {
    /*
     * First, because it is the artwork most people will be looking at.
     *
     * Loki ships console renders for every system it models, and until this
     * switch existed there was no way to see anything else on a platform folder
     * short of installing a pack over the top of them.
     */
    SwitchRow(
        title = "Bundled console icons",
        subtitle = "Loki's own artwork on platform folders",
        checked = settings.personalization.bundledPlatformIcons,
        focused = focusedRow == 0,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(bundledPlatformIcons = on) }
        },
    )
    RowDivider()
    DirectoryPickerRow(
        title = "Import from folder",
        subtitle = "Pick an extracted pack folder",
        focused = focusedRow == 1,
        onPicked = { uri, _ -> viewModel.installIconPackFromFolder(uri) },
    )
    RowDivider()
    FilePickerRow(
        title = "Import from archive",
        subtitle = "Pick a .zip pack",
        mimeTypes = ZIP_MIME_TYPES,
        focused = focusedRow == 2,
        onPicked = { uri, _ -> viewModel.installIconPackFromZip(uri) },
    )

    /*
     * Game artwork from another launcher, on the same page as platform artwork.
     *
     * Different in what it fills — this one dresses games rather than systems —
     * but the same question from the user's side: where does the picture come
     * from. Worth more than any scraper for a library that has already been
     * curated once, because it inherits decisions somebody made by hand instead
     * of guessing them again.
     */
    RowDivider()
    DirectoryPickerRow(
        title = "Import game artwork",
        subtitle = "Pick another launcher’s downloaded media folder",
        focused = focusedRow == 3,
        onPicked = { uri, _ -> viewModel.importArtworkFolder(uri) },
    )
    importStatus?.let { message ->
        RowDivider()
        InfoRow("Last artwork import", message)
    }

    // Said out loud rather than left to be inferred from the list: an import can
    // succeed for most platforms and hold artwork for the rest, and a silent
    // partial success reads as a broken pack.
    status.message?.let { message ->
        RowDivider()
        InfoRow("Last import", message)
    }

    if (packs.isEmpty()) {
        RowDivider()
        InfoRow(
            "Installed",
            "None. Loki ships no packs — platform artwork comes from ones you import.",
        )
        return
    }

    packs.forEachIndexed { index, pack ->
        RowDivider()
        ActionRow(
            title = pack.name,
            subtitle = buildString {
                append("${pack.author} · v${pack.version} · ")
                append("${pack.appliedCount} platform")
                if (pack.appliedCount != 1) append("s")
                if (pack.heldCount > 0) append(", ${pack.heldCount} held")
            },
            focused = focusedRow == IMPORT_ROWS + index,
            destructive = true,
            trailingLabel = "Remove",
            onClick = { viewModel.removeIconPack(pack.id) },
        )
    }
}

/**
 * Zip, spelled several ways.
 *
 * Providers disagree on what a `.zip` is: the Downloads provider usually reports
 * `application/zip`, some file managers report `application/x-zip-compressed`, and
 * anything that has lost the association reports `application/octet-stream`.
 * Filtering on the first alone hides the file the user came to pick.
 */
private val ZIP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)

/**
 * Rows above the list of installed packs: the bundled switch, the two pack
 * imports and the game-artwork import.
 */
internal const val IMPORT_ROWS = 4
