package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.EmulatorChoice
import com.thor.feature.settings.component.row.ActivateOnConfirm

/** Which system's emulators are being chosen, and what is on offer. */
@Immutable
data class EmulatorPickerState(
    val visible: Boolean = false,
    val platformId: String = "",
    val platformName: String = "",
    /** Known emulators for this system, installed ones first. */
    val choices: List<EmulatorChoice> = emptyList(),
    /**
     * Everything else installed that looks like an emulator.
     *
     * The escape hatch, and the reason this dialog earns its place. This
     * launcher's table can only name builds somebody has added to it, so an
     * emulator it has not heard of — a fork, a rename, a build from a source it
     * does not know — was simply unassignable no matter that it was sitting on
     * the device. Offering the installed applications means being unrecognised
     * costs a scroll rather than the feature.
     */
    val otherApps: List<EmulatorChoice> = emptyList(),
    /** Packages currently assigned, in order; the first is the default. */
    val assigned: List<String> = emptyList(),
    val focusedIndex: Int = 0,
) {
    val rows: List<EmulatorChoice> get() = choices + otherApps

    val rowCount: Int get() = rows.size
}

/**
 * Assigns emulators to a system.
 *
 * A dialog rather than a strip of chips on the system's own row. That row lists
 * every emulator this launcher knows for the console — which is the right list,
 * and on a well-served system it is a dozen entries wrapping across a card that
 * also has to carry a ROM folder, a default, a scrape button and a remove
 * button. The list is not the problem; the row was never the place for it.
 *
 * Multi-select, because more than one emulator can run a system and people
 * routinely keep a fast one and an accurate one. Order is preserved and the
 * first is the default — the one games actually launch with.
 */
@Composable
fun EmulatorPickerDialog(
    state: EmulatorPickerState,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            modifier = Modifier
                .width(CARD_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = "Emulators",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = if (state.assigned.isEmpty()) {
                        "${state.platformName} — none assigned"
                    } else {
                        "${state.platformName} — first is the default"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.choices.forEachIndexed { index, choice ->
                        EmulatorRow(
                            choice = choice,
                            order = state.assigned.indexOf(choice.packageName),
                            focused = index == state.focusedIndex,
                            onClick = { if (choice.installed) onToggle(choice.packageName) },
                        )
                    }

                    if (state.otherApps.isNotEmpty()) {
                        PickerHeading("OTHER INSTALLED APPS")
                        state.otherApps.forEachIndexed { index, choice ->
                            EmulatorRow(
                                choice = choice,
                                order = state.assigned.indexOf(choice.packageName),
                                focused = state.choices.size + index == state.focusedIndex,
                                onClick = { onToggle(choice.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmulatorRow(
    choice: EmulatorChoice,
    /** Position in the assigned list, or -1 when this is not assigned. */
    order: Int,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val assigned = order >= 0

    // The controller's Confirm lands on whichever row holds the cursor, the same
    // way every other row on this screen answers it.
    ActivateOnConfirm(focused && choice.installed, onClick)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .thorCursor(focused = focused, shape = shape)
            .clickable(enabled = choice.installed, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Box(modifier = Modifier.size(GLYPH.dp), contentAlignment = Alignment.Center) {
            if (assigned) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.cursor,
                    modifier = Modifier.size(GLYPH.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = choice.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    // Unassignable, and it has to look it: pressing an emulator
                    // that is not there would store a launch that always fails.
                    !choice.installed -> colors.onSurfaceVariant.copy(alpha = NOT_INSTALLED_ALPHA)
                    assigned -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    !choice.installed -> "Not installed"
                    order == 0 -> "Default — games launch with this"
                    assigned -> "Assigned"
                    else -> choice.packageName
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (order == 0) colors.cursor else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val CARD_WIDTH = 400
private const val CONTENT_MAX_HEIGHT = 300
private const val GLYPH = 18
private const val NOT_INSTALLED_ALPHA = 0.45f
