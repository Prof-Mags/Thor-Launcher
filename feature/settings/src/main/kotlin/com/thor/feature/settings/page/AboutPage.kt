package com.thor.feature.settings.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.input.RawKeyPress
import com.thor.core.model.Platform
import com.thor.core.model.ThemeMode
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.SettingsViewModel

/**
 * What this launcher is, and the handful of controls for looking after it.
 *
 * Diagnostics used to be a page of its own under System, and the split never
 * earned itself: "what version is this" and "why is that button doing nothing"
 * are the same visit, and a category holding one page is a category the user
 * walks through rather than reads.
 *
 * The facts are stated first and are not focusable — there is nothing to press
 * on a version number — so the controller's first stop is the default-launcher
 * row and the indices below count only the rows that can be reached.
 */
@Composable
fun AboutPane(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    isDefaultLauncher: Boolean,
    keyCaptureEnabled: Boolean,
    capturedKeys: List<RawKeyPress>,
    /** Systems the user has added, which is not a thing settings knows. */
    platformCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Theme and polarity together, because half the answer is misleading:
        // "Material" alone says nothing about whether this launcher is currently
        // white or black, which is the first thing anybody reporting a problem with
        // it would describe.
        InfoRow(
            "Active theme",
            with(settings.personalization) { "${themeId.displayName} · ${themeMode.label}" },
        )
        RowDivider()
        InfoRow("Grid", "${settings.grid.columns} × ${settings.grid.rows}")
        RowDivider()
        /*
         * The systems added, counted from the platforms themselves.
         *
         * This read `romDirectoryUris.size`, which is a count of ROM *folders* —
         * a different thing wearing the same label, and one that is legitimately
         * zero for anyone who granted folders per platform rather than as extras.
         * So it reported "0 platforms" to people with a full library.
         */
        InfoRow("Platforms configured", platformCount.toString())
        RowDivider()
        InfoRow("ROM folders", settings.library.romDirectoryUris.size.toString())
        RowDivider()
        InfoRow("Settings schema", settings.schemaVersion.toString())
        RowDivider()
        InfoRow("Built for", "AYN Thor dual screen handheld")
        RowDivider()

        ActionRow(
            title = "Set as default launcher",
            subtitle = if (isDefaultLauncher) {
                "Loki is your home app"
            } else {
                "Opens Android's home app chooser"
            },
            focused = focusedRow == 0,
            trailingLabel = if (isDefaultLauncher) "Active" else "Choose",
            onClick = viewModel::requestDefaultLauncher,
        )
        RowDivider()
        ActionRow(
            title = "Replay the walkthrough",
            subtitle = "The guided tour of both panels, the grid, the controls " +
                "and every setting. Shown once when Loki is first set up.",
            focused = focusedRow == 1,
            trailingLabel = "Replay",
            onClick = viewModel::replayTutorial,
        )
        RowDivider()
        SwitchRow(
            title = "Verbose logging",
            subtitle = "Writes detailed output to logcat",
            checked = settings.developer.verboseLogging,
            focused = focusedRow == 2,
            onCheckedChange = { on -> viewModel.updateDeveloper { it.copy(verboseLogging = on) } },
        )
        RowDivider()
        SwitchRow(
            title = "Button tester",
            subtitle = "Reports what each button sends, without acting on it. " +
                "Use Back to leave.",
            checked = keyCaptureEnabled,
            focused = focusedRow == 3,
            onCheckedChange = viewModel::setKeyCapture,
        )

        if (keyCaptureEnabled) {
            if (capturedKeys.isEmpty()) {
                InfoRow(
                    "Listening",
                    "Press any button. A button that never appears here is being " +
                        "handled by the system before the launcher sees it, and cannot " +
                        "be remapped by an app.",
                )
            } else {
                capturedKeys.forEach { press ->
                    InfoRow(
                        press.keyName,
                        buildString {
                            append("code ${press.keyCode}")
                            press.deviceName?.let { append(" · $it") }
                            append(" · ")
                            append(press.boundTo?.let { "bound to ${it.label}" } ?: "unbound")
                        },
                    )
                }
            }
        }

        RowDivider()
        ActionRow(
            title = "Reset all settings",
            subtitle = "Restores every option to its default. Library data is untouched.",
            focused = focusedRow == 4,
            destructive = true,
            trailingLabel = "RESET",
            onClick = viewModel::resetToDefaults,
        )
    }
}

/** Focusable rows on [AboutPane]; the information rows above them are not. */
const val ABOUT_ROWS = 5
