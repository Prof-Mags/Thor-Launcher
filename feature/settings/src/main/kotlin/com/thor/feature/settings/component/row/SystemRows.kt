package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.Platform
import com.thor.feature.settings.EmulatorChoice
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.THOR_MENU_ICON_TILE
import com.thor.core.ui.component.ThorMenuRow
import com.thor.feature.settings.component.SettingsCard
import com.thor.feature.settings.component.SettingsTextButton

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
    var controllerIndex by remember { mutableIntStateOf(0) }
    val controllerChoice = available.getOrNull(controllerIndex)

    LaunchedEffect(available.size) {
        controllerIndex = controllerIndex.coerceIn(0, available.lastIndex.coerceAtLeast(0))
    }
    /*
     * Left and right are claimed only while the picker is open.
     *
     * Outside it they keep their page-level meaning, which matters because this
     * row previously stole them permanently to cycle a hidden selection — so the
     * only way to choose a system was to nudge sideways at a row that gave no
     * indication it was holding a list, and Confirm added whatever happened to
     * be current. There was no way to see what was on offer before committing.
     */
    RegisterForHorizontalSteps(focused && expanded)
    StepOnHorizontal(focused && expanded) { direction ->
        controllerIndex = (controllerIndex + direction).coerceIn(0, available.lastIndex)
    }

    /*
     * Confirm opens the picker, and confirms inside it.
     *
     * One button doing both is what makes it feel like a menu rather than a
     * setting: press to look, press to choose. Nothing is added until the second
     * press, so the list can be browsed without consequence.
     */
    ActivateOnConfirm(focused) {
        if (expanded) {
            available.getOrNull(controllerIndex)?.let(onAdd)
            expanded = false
        } else if (available.isNotEmpty()) {
            expanded = true
        }
    }

    Box {
        SettingsCard(
            focused = focused,
            onClick = { if (available.isNotEmpty()) expanded = true },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 38.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(if (focused) colors.cursor else colors.outline),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add platform",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        // Says what the button does now that it opens a list
                        // rather than committing whatever was quietly selected.
                        text = when {
                            available.isEmpty() -> "Every supported platform has been added"
                            focused -> "A opens the list of ${available.size} systems"
                            else -> "Choose a console to set up"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                controllerChoice?.let { platform ->
                    SettingsTextButton(
                        label = platform.name,
                        containerColor = colors.cursor.copy(alpha = 0.12f),
                        contentColor = colors.cursor,
                        borderColor = Color(platform.accentArgb).copy(alpha = 0.52f),
                    )
                }
            }
        }

        if (expanded) {
            PlatformPicker(
                available = available,
                selected = controllerIndex,
                onSelect = { controllerIndex = it },
                onConfirm = { platform ->
                    expanded = false
                    onAdd(platform)
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

/**
 * The list of systems on offer, as a box in the middle of the screen.
 *
 * A dialog rather than the dropdown this used to be. A dropdown anchors itself
 * to the row that opened it, which on a settings page near the bottom of a short
 * panel meant a menu of forty systems opening upward into three visible lines —
 * and it drew in Material's own colours rather than the launcher's. Centred, it
 * has the whole screen to use and can be sized once.
 *
 * Controller and touch reach the same list: the highlight follows Left and Right
 * from the pad, a finger can tap any row directly, and both commit through the
 * same callback.
 */
@Composable
private fun PlatformPicker(
    available: List<Platform>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onConfirm: (Platform) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    // Keeps the highlighted system on screen as the pad walks past the fold.
    LaunchedEffect(selected) {
        if (available.isNotEmpty()) {
            listState.animateScrollToItem(selected.coerceIn(0, available.lastIndex))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(PICKER_WIDTH_FRACTION)
                .heightIn(max = PICKER_MAX_HEIGHT.dp),
            shape = RoundedCornerShape(dimens.cornerRadius),
            // A dialog over an already-raised settings card.
            level = SurfaceLevel.OVERLAY,
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = "Add a platform",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "L and R move · A adds · B closes",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimens.spacing),
                )

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(available, key = { _, it -> it.id }) { index, platform ->
                        PickerRow(
                            platform = platform,
                            highlighted = index == selected,
                            onClick = {
                                // A tap both highlights and commits: pointing at
                                // a row is already an unambiguous choice.
                                onSelect(index)
                                onConfirm(platform)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    platform: Platform,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    ThorMenuRow(
        label = platform.name,
        description = platform.subtitle,
        focused = highlighted,
        // The one list where the label is the whole point. "Nintendo Entertainment
        // System", "PC Engine / TurboGrafx-16" and "Sega Genesis / Mega Drive" all
        // ran past the end of a single line, and the halves that got cut are
        // exactly the halves that tell two similar systems apart.
        labelMaxLines = 2,
        // The system's own colour, in the slot the accent gradient occupies
        // everywhere else. A list of forty consoles is easier to run an eye down
        // when each carries the colour it wears on the grid.
        accent = Color(platform.accentArgb),
        leading = {
            Box(
                modifier = Modifier
                    .size(THOR_MENU_ICON_TILE.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(Color(platform.accentArgb).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                val image = platform.artwork.iconUri ?: platform.artwork.logoUri
                if (image != null) {
                    ArtworkImage(
                        model = image,
                        contentDescription = null,
                        fallbackText = platform.shortName,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = platform.shortName.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(platform.accentArgb),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }
        },
        onClick = onClick,
    )
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
    /** Every emulator known for this system, installed or not. */
    emulators: List<EmulatorChoice>,
    romFolder: String?,
    focused: Boolean = false,
    /** Current completed/total count when this platform is being scraped. */
    scrapeProgress: String? = null,
    /** Opens the emulator list; see [EmulatorPickerDialog] for why it moved. */
    onEditEmulators: () -> Unit,
    /** Re-scrapes just this system's games. */
    onScrape: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = ThorTheme.colors
    val selected = platform.emulatorPackages
    val accent = Color(platform.accentArgb)
    val ready = romFolder != null && selected.isNotEmpty()
    val assignable = emulators.filter { it.installed }
    // Emulators, scrape, remove. One stop for the whole list rather than one per
    // emulator: a well-served console has a dozen, and stepping through all of
    // them to reach Remove made the two actions on this card hard to get to.
    val controlCount = EMULATOR_CONTROL + PLATFORM_ACTION_COUNT
    var highlightedControl by remember(platform.id) { mutableIntStateOf(0) }
    val emulatorSummary = when {
        assignable.isEmpty() -> "No compatible emulator installed"
        selected.isEmpty() -> "No emulator assigned"
        else -> assignable.firstOrNull { it.packageName == selected.first() }
            ?.displayName ?: selected.first()
    }

    LaunchedEffect(controlCount) {
        highlightedControl = highlightedControl.coerceIn(0, controlCount - 1)
    }
    RegisterForHorizontalSteps(focused)
    StepOnHorizontal(focused) { direction ->
        highlightedControl = (highlightedControl + direction).mod(controlCount)
    }
    ActivateOnConfirm(focused) {
        when (highlightedControl) {
            EMULATOR_CONTROL -> onEditEmulators()
            EMULATOR_CONTROL + 1 -> onScrape()
            EMULATOR_CONTROL + 2 -> onRemove()
        }
    }

    SettingsCard(focused = focused) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ThorTheme.shapes.small)
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.52f), ThorTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = platform.name.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (focused) {
                            "${platform.manufacturer}  ·  L/R select  ·  A use"
                        } else {
                            platform.manufacturer
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) colors.cursor else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = when {
                        scrapeProgress != null -> "SCRAPING $scrapeProgress"
                        ready -> "READY"
                        else -> "SETUP NEEDED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        scrapeProgress != null -> colors.cursor
                        ready -> colors.cursor
                        else -> colors.error
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(ThorTheme.shapes.pill)
                        .background(
                            (if (scrapeProgress != null || ready) colors.cursor else colors.error)
                                .copy(alpha = 0.12f),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlatformFact(
                    label = "ROM FOLDER",
                    value = romFolder ?: "Not configured",
                    warning = romFolder == null,
                    modifier = Modifier.weight(1f),
                )
                PlatformFact(
                    label = "DEFAULT EMULATOR",
                    value = emulatorSummary,
                    warning = selected.isEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                /*
                 * One control for the whole list, not one per emulator.
                 *
                 * Every emulator this launcher knows for a console belongs on
                 * offer — that part was right — but on a well-served system that
                 * is a dozen chips wrapping across a card which also carries a
                 * ROM folder, a default, a scrape button and a remove button. The
                 * list was never the problem; this row was the wrong place for
                 * it. It opens in a dialog now, which also has room for the
                 * installed applications this table has never heard of.
                 */
                PlatformAction(
                    label = when {
                        assignable.isEmpty() -> "CHOOSE EMULATOR"
                        selected.isEmpty() -> "ASSIGN EMULATOR"
                        selected.size == 1 -> "EMULATOR"
                        else -> "EMULATORS (${selected.size})"
                    },
                    icon = Icons.Rounded.Tune,
                    focused = focused && highlightedControl == EMULATOR_CONTROL,
                    onClick = onEditEmulators,
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PlatformAction(
                        label = scrapeProgress ?: "SCRAPE",
                        icon = Icons.Rounded.Refresh,
                        focused = focused && highlightedControl == EMULATOR_CONTROL + 1,
                        onClick = onScrape,
                    )
                    PlatformAction(
                        label = "REMOVE",
                        icon = Icons.Rounded.Close,
                        focused = focused && highlightedControl == EMULATOR_CONTROL + 2,
                        destructive = true,
                        onClick = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformEmulatorChip(
    displayName: String,
    isSelected: Boolean,
    isDefault: Boolean,
    controllerFocused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    SettingsTextButton(
        label = if (isDefault) "$displayName · DEFAULT" else displayName,
        icon = Icons.Rounded.Check.takeIf { isSelected },
        containerColor = if (isSelected) {
            colors.cursor.copy(alpha = 0.16f)
        } else {
            colors.surfaceElevated
        },
        contentColor = if (isSelected) colors.cursor else colors.onSurface,
        borderColor = if (isSelected) colors.cursor.copy(alpha = 0.64f) else colors.outline,
        focused = controllerFocused,
        reactToHover = true,
        onClick = onClick,
    )
}

@Composable
private fun MissingEmulatorChip(displayName: String) {
    val colors = ThorTheme.colors
    Text(
        text = "$displayName · NOT INSTALLED",
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceElevated.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlatformFact(
    label: String,
    value: String,
    warning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Column(
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (warning) colors.error else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlatformAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focused: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val tint = if (destructive) colors.error else colors.cursor
    SettingsTextButton(
        label = label,
        icon = icon,
        containerColor = tint.copy(alpha = 0.12f),
        contentColor = tint,
        borderColor = tint.copy(alpha = 0.34f),
        focused = focused,
        reactToHover = true,
        onClick = onClick,
    )
}

/** The emulator control comes first, then Scrape and Remove. */
private const val EMULATOR_CONTROL = 0

private const val PLATFORM_ACTION_COUNT = 3

/**
 * Nearly the whole panel.
 *
 * It was 0.72, chosen so the box still read as a dialog — but the thing it is a
 * dialog *for* is a list of names, several of which are four words long, and after
 * the artwork tile and the insets there was not a line's worth of room left for
 * them. A picker that cannot show what it is offering has stopped being a picker,
 * and the margin it was protecting is worth less than the names.
 */
private const val PICKER_WIDTH_FRACTION = 0.94f

/** Tall enough to be worth scrolling, short enough to still float over the page. */
private const val PICKER_MAX_HEIGHT = 460
