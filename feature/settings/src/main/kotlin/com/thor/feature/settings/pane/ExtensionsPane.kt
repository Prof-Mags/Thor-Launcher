package com.thor.feature.settings.pane

import androidx.compose.runtime.Composable
import com.thor.core.model.LauncherExtension
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.ActionRow
import com.thor.feature.settings.component.FilePickerRow
import com.thor.feature.settings.component.InfoRow
import com.thor.feature.settings.component.RowDivider

/**
 * Adding and removing the optional parts of the launcher.
 *
 * Loki ships with Movies and PC streaming built in but switched off, and this is
 * where they are switched on: import the small manifest file for one and its
 * section, its settings category and everything under it appear.
 *
 * The rows below are the *only* mention of either feature in a launcher that has
 * neither enabled. There is no greyed-out tab, no empty category, no settings
 * page explaining what you are missing — a section that cannot be opened should
 * not be advertised by the thing that cannot open it.
 */
@Composable
internal fun ExtensionsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
) {
    InfoRow(
        "Extensions",
        "Movies and PC streaming are optional. Import an extension file from the " +
            "Loki releases page to add one — the section, its settings and " +
            "everything it needs appear immediately, and nothing is downloaded.",
    )
    RowDivider()

    FilePickerRow(
        title = "Import an extension",
        subtitle = status ?: "Pick a .json extension file",
        // Some file providers report JSON as plain text, and a picker that
        // hides the file the user is looking at is a picker they cannot use.
        mimeTypes = arrayOf("application/json", "text/plain", "*/*"),
        focused = focusedRow == 0,
        onPicked = { uri, _ -> viewModel.importExtension(uri) },
    )
    RowDivider()

    /*
     * Listed whether enabled or not, and only here.
     *
     * Someone who has imported neither still needs to know what the two are, or
     * an extension file arrives with nothing to explain it. This is the one
     * place the base launcher names them.
     */
    LauncherExtension.entries.forEachIndexed { index, extension ->
        val enabled = settings.has(extension)
        ActionRow(
            title = extension.displayName,
            subtitle = if (enabled) {
                "Added. ${extension.summary}"
            } else {
                extension.summary
            },
            focused = focusedRow == index + 1,
            trailingLabel = if (enabled) "Remove" else "Not added",
            destructive = enabled,
            onClick = { if (enabled) viewModel.removeExtension(extension) },
        )
        if (index != LauncherExtension.entries.lastIndex) RowDivider()
    }
}

/** Import row, then one per extension. */
internal val EXTENSIONS_ROWS: Int = 1 + LauncherExtension.entries.size
