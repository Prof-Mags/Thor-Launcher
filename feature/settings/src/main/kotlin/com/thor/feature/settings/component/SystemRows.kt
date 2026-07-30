package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.Platform

/**
 * A dropdown that adds a system to the user's setup.
 *
 * The list is grouped implicitly by the platform ordering (Nintendo, Sony,
 * Microsoft, Sega, other) and shows only systems not already added, so the menu
 * shrinks as the user configures their device rather than repeating choices
 * that would do nothing.
 */
@Composable
fun AddSystemRow(
    available: List<Platform>,
    focused: Boolean = false,
    onAdd: (Platform) -> Unit,
) {
    val colors = ThorTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box {
        ActionRow(
            title = "Add platform",
            subtitle = if (available.isEmpty()) {
                "Every supported platform has been added"
            } else {
                "Choose a console to set up"
            },
            focused = focused,
            trailingLabel = if (available.isEmpty()) null else "Add",
            onClick = { if (available.isNotEmpty()) expanded = true },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = MENU_MAX_HEIGHT.dp)
                .background(colors.surfaceElevated),
        ) {
            available.forEach { platform ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = platform.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                text = platform.manufacturer,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(platform.accentArgb)),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAdd(platform)
                    },
                )
            }
        }
    }

}

/**
 * One added system, with its emulators.
 *
 * Emulators are multi-select because more than one can run a system and users
 * routinely keep a fast one and an accurate one side by side. Selection order
 * is preserved and the first is marked as the default, which is the one games
 * actually launch with — the rest are offered from the context menu.
 */
@Composable
fun SystemRow(
    platform: Platform,
    installedEmulators: List<Pair<String, String>>,
    romFolder: String?,
    focused: Boolean = false,
    onToggleEmulator: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val selected = platform.emulatorPackages

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(platform.accentArgb)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                val emulatorSummary = when {
                    installedEmulators.isEmpty() -> "No compatible emulator installed"
                    selected.isEmpty() -> "No emulator assigned"
                    else -> {
                        val name = installedEmulators
                            .firstOrNull { it.first == selected.first() }
                            ?.second ?: selected.first()
                        if (selected.size > 1) "$name +${selected.size - 1}" else name
                    }
                }
                Text(
                    text = "${romFolder ?: "No ROM folder"} · $emulatorSummary",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected.isEmpty() || romFolder == null) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove ${platform.name}",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }

        if (installedEmulators.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = dimens.spacingSmall),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                installedEmulators.forEach { (packageName, displayName) ->
                    val index = selected.indexOf(packageName)
                    val isSelected = index >= 0
                    val isDefault = index == 0

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (isSelected) colors.cursor else colors.surfaceElevated,
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else colors.outline,
                                shape = RoundedCornerShape(percent = 50),
                            )
                            .clickable { onToggleEmulator(packageName) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.background,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) colors.background else colors.onSurface,
                        )
                        if (isDefault) {
                            Text(
                                text = "· default",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.background.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MENU_MAX_HEIGHT = 360
