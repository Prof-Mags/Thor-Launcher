package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.LauncherProfile
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.FilePickerRow
import com.thor.feature.settings.component.row.FileSaverRow
import com.thor.feature.settings.component.row.InfoRow

/**
 * Copying a profile out, and putting one back.
 *
 * Cloud sync has been modelled since early on — a provider, six per-category
 * toggles, an interval — with nothing behind any of it. This is the half that
 * needs no server and no account, and on a device where the launcher is installed
 * by sideloading an APK it is the half that actually gets used: reinstalling
 * otherwise costs every grid placement, every scraped cover, every API key and
 * every theme.
 *
 * One file holds the whole profile — settings, library database and avatar —
 * because those three are only meaningful together. Restoring a settings document
 * that names placements a database does not have would be worse than having no
 * backup at all.
 */
@Composable
internal fun BackupPage(
    activeProfile: LauncherProfile?,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
    restartRequired: Boolean,
) {
    val name = activeProfile?.name ?: "this profile"

    InfoRow(
        "Backup",
        status ?: "Saves everything belonging to $name — settings, the library " +
            "with its artwork and play time, themes and grid layout — into one " +
            "file. Your ROMs and emulators are not in it and are not touched.",
    )
    RowDivider()

    FileSaverRow(
        title = "Back up now",
        subtitle = "Choose where to write the file",
        suggestedName = backupFileName(activeProfile),
        mimeType = "application/zip",
        focused = focusedRow == 0,
        trailingLabel = "SAVE",
        onChosen = viewModel::backUpProfile,
    )
    RowDivider()

    FilePickerRow(
        title = "Restore from a file",
        subtitle = if (restartRequired) {
            "Restored. Close Loki completely and open it again."
        } else {
            "Replaces everything belonging to $name. Cannot be undone."
        },
        // Some providers report a zip as a generic stream, and a picker that hides
        // the file you are looking at is one you cannot use.
        mimeTypes = arrayOf("application/zip", "application/octet-stream", "*/*"),
        focused = focusedRow == 1,
        onPicked = { uri, _ -> viewModel.restoreProfile(uri) },
    )
    RowDivider()

    InfoRow(
        "Restoring needs a restart",
        "The library and the settings file are both open while Loki is running, " +
            "so a restore swaps the files underneath them. Nothing reads the new " +
            "ones until the launcher is started again.",
    )
}

/**
 * A name that sorts and identifies without a clock.
 *
 * The profile is in it because a device with two profiles produces two backups
 * that are otherwise indistinguishable in a downloads folder. No date: the file
 * picker supplies one, and a name generated here would disagree with the file's
 * own modified time the moment somebody copied it.
 */
private fun backupFileName(profile: LauncherProfile?): String {
    val slug = profile?.name?.lowercase()?.replace(NON_FILENAME, "-")?.trim('-')
    return "loki-backup-${slug.orEmpty().ifBlank { "profile" }}.zip"
}

private val NON_FILENAME = Regex("[^a-z0-9]+")

/** Save, restore. The two info rows are not focusable. */
internal const val BACKUP_ROWS = 2
