package com.thor.feature.settings.component

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.common.log.ThorLog
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.Platform
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ActivateOnConfirm
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.row.SwitchRow

/** The configuration collected when a platform is added. */
data class PlatformSetup(
    val romDirectoryUri: String?,
    val romDirectoryName: String,
    val emulatorPackage: String?,
    val scanSubfolders: Boolean,
)

/**
 * Collects everything needed to make a newly added platform usable.
 *
 * Adding a system without a ROM folder and an emulator produces a platform that
 * finds nothing and launches nothing, so both are asked for up front rather
 * than left as settings the user has to discover afterwards. Neither is
 * mandatory — the platform can be added bare and configured later — but the
 * dialog says plainly what will not work if they are skipped.
 */
@Composable
fun AddPlatformDialog(
    platform: Platform,
    installedEmulators: List<Pair<String, String>>,
    focusedRow: Int = 0,
    onConfirm: (PlatformSetup) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val context = LocalContext.current

    var romUri by remember { mutableStateOf<String?>(null) }
    var romName by remember { mutableStateOf("") }
    var emulator by remember(platform.id) {
        // Pre-select when there is exactly one candidate; with several, the
        // choice is the user's and guessing would be wrong half the time.
        mutableStateOf(installedEmulators.singleOrNull()?.first)
    }
    var scanSubfolders by remember { mutableStateOf(true) }
    val cancelRow = if (installedEmulators.isEmpty()) 2 else 3
    val confirmRow = cancelRow + 1

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The scanner runs long after this dialog closes, so the grant has to
        // outlive the picker.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { ThorLog.w("Settings", "Could not persist ROM folder grant", it) }
        romUri = uri.toString()
        romName = uri.lastPathSegment?.substringAfterLast(':') ?: platform.name
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            // Highest surface: this sits over the settings pane, which is itself
            // already an elevated panel.
            color = ThorTheme.colors.surfaceHighest,
            modifier = Modifier
                .width(DIALOG_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacingLarge)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ThorTheme.shapes.small)
                            .background(Color(platform.accentArgb).copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                Color(platform.accentArgb).copy(alpha = 0.55f),
                                ThorTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = platform.name.take(2).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(platform.accentArgb),
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Column {
                        Text(
                            text = "NEW PLATFORM",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cursor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = platform.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )
                    }
                }
                Text(
                    text = "Set up where the games are and what runs them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = dimens.spacing),
                )

                ActionRow(
                    title = "ROM folder",
                    subtitle = romUri?.let { romName }
                        ?: "Not set — no games will be found",
                    trailingLabel = if (romUri == null) "Choose" else "Change",
                    focused = focusedRow == 0,
                    onClick = { directoryPicker.launch(null) },
                )
                RowDivider()

                SwitchRow(
                    title = "Include subfolders",
                    subtitle = "Search folders inside the one chosen above",
                    checked = scanSubfolders,
                    focused = focusedRow == 1,
                    onCheckedChange = { scanSubfolders = it },
                )
                RowDivider()

                if (installedEmulators.isEmpty()) {
                    InfoRow(
                        title = "Emulator",
                        value = "None installed",
                    )
                } else {
                    val emulatorOptions = listOf("") + installedEmulators.map { it.first }
                    ChoiceRow(
                        title = "Emulator",
                        subtitle = if (emulator == null) {
                            "Not set — games will not launch"
                        } else {
                            null
                        },
                        options = emulatorOptions,
                        selected = emulator.orEmpty(),
                        focused = focusedRow == 2,
                        label = { packageName ->
                            if (packageName.isEmpty()) {
                                "Not set"
                            } else {
                                installedEmulators.firstOrNull { it.first == packageName }?.second
                                    ?: packageName
                            }
                        },
                        onSelected = { packageName -> emulator = packageName.ifEmpty { null } },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacing),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    DialogAction(
                        label = "CANCEL",
                        primary = false,
                        focused = focusedRow == cancelRow,
                        onClick = onDismiss,
                    )
                    DialogAction(
                        label = "ADD PLATFORM",
                        primary = true,
                        focused = focusedRow == confirmRow,
                        onClick = {
                            onConfirm(
                                PlatformSetup(
                                    romDirectoryUri = romUri,
                                    romDirectoryName = romName.ifBlank { platform.name },
                                    emulatorPackage = emulator,
                                    scanSubfolders = scanSubfolders,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    primary: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    ActivateOnConfirm(focused, onClick)
    SettingsTextButton(
        label = label,
        containerColor = if (primary) colors.cursor else colors.surfaceElevated,
        contentColor = if (primary) contrastingContentColor(colors.cursor) else colors.onSurface,
        borderColor = if (primary) colors.cursor else colors.outline.copy(alpha = 0.36f),
        focused = focused,
        reactToHover = true,
        onClick = onClick,
    )
}

private const val DIALOG_WIDTH = 500
