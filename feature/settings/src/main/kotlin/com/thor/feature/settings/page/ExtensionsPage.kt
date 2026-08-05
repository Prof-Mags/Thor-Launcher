package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.LauncherExtension
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.FilePickerRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.SettingsViewModel

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
        if (enabled) {
            ActionRow(
                title = extension.displayName,
                subtitle = "Added. ${extension.summary}",
                focused = focusedRow == index + 1,
                trailingLabel = "Remove",
                destructive = true,
                onClick = { viewModel.removeExtension(extension) },
            )
        } else {
            /*
             * A picker, not a dead button.
             *
             * This row used to be an [ActionRow] whose click did nothing when the
             * extension was not yet added — focusable, drawn with a cursor-tinted
             * button reading "Not added", and completely inert. Pressing A on it
             * is the obvious way to try to add the thing, and the obvious way did
             * nothing at all: no action, no message, no cue.
             *
             * It opens the same picker the import row does, so the obvious way
             * now works. Any manifest is accepted from here rather than only this
             * extension's — the importer reads what the file names, and refusing
             * a valid file because it was picked from the neighbouring row would
             * be a second dead end where the first one was.
             */
            FilePickerRow(
                title = extension.displayName,
                subtitle = "${extension.summary}  ·  Not added — pick its file to add it",
                mimeTypes = arrayOf("application/json", "text/plain", "*/*"),
                focused = focusedRow == index + 1,
                onPicked = { uri, _ -> viewModel.importExtension(uri) },
            )
        }
        if (index != LauncherExtension.entries.lastIndex) RowDivider()
    }
}

/** Import row, then one per extension. */
internal val EXTENSIONS_ROWS: Int = 1 + LauncherExtension.entries.size
