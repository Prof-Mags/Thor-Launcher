package com.thor.feature.settings.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import kotlin.math.roundToInt

/**
 * A titled group of related settings.
 *
 * Deliberately light: a heading, generous breathing room and hairline
 * separators between rows. An earlier version boxed every group in a bordered,
 * tinted card, which at six or seven groups per pane turned the screen into a
 * stack of competing containers.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(modifier = modifier.padding(bottom = dimens.spacingHuge)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
            modifier = Modifier.padding(bottom = if (description == null) 10.dp else 2.dp),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Column(content = content)
    }
}

/**
 * Scrolls this row into view whenever it takes the cursor.
 *
 * Controller focus is the launcher's own concept, not the framework's, so no
 * scroll container knows to follow it — moving down a pane with the D-pad
 * walked the highlight straight off the bottom of the screen while the list
 * stayed put. `BringIntoViewRequester` asks the nearest scrollable ancestor to
 * reveal this element, which works regardless of how the pane is chunked into
 * lazy items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.revealWhenFocused(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(focused) {
        if (focused) {
            // Yielding a frame lets the row be laid out before its bounds are
            // requested; asking during the same composition reveals the
            // position it had *before* this change.
            withFrameNanos { }
            runCatching { requester.bringIntoView() }
        }
    }

    return this.bringIntoViewRequester(requester)
}

/** Hairline separator between rows. */
@Composable
fun RowDivider() {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // height, not size: `size` fixes both axes, so it overrode the
            // fillMaxWidth above it and left the rule zero pixels wide.
            .height(1.dp)
            .background(colors.outline.copy(alpha = 0.22f)),
    )
}

/**
 * Shared row shell.
 *
 * Title and description on the left, one control on the right. Every settings
 * row uses this, so a pane reads as a single column of labels with a single
 * column of controls rather than a mix of inline widgets at varying heights.
 */
@Composable
private fun SettingsRowShell(
    title: String,
    subtitle: String?,
    focused: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    /*
     * Every settings row passes through here, so the pointer highlights all of
     * them from one place — pickers, switches, sliders and actions alike.
     *
     * Only rows that do something light up. A read-only row has nothing for a
     * click to land on, and highlighting it would promise otherwise; the
     * controller cursor already skips them for the same reason.
     */
    val hover = rememberPointerHover()
    val lit = focused || (onClick != null && hover.isHovered)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT.dp)
            // Keyed on controller focus alone: scrolling a row into view because
            // the *pointer* drifted over it would drag the list out from under
            // the cursor that was aiming at it.
            .revealWhenFocused(focused)
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .thorCursor(focused = lit, cornerRadius = dimens.cornerRadiusSmall)
            .pointerHover(hover)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            // Horizontal padding is inside the cursor bounds: without it the
            // highlight ring is drawn straight over the first and last
            // characters of the row's text.
            .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor ?: colors.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A boolean setting. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    focused: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ThorTheme.colors
    ActivateOnConfirm(focused) { onCheckedChange(!checked) }
    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        focused = focused,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.cursor,
                    checkedBorderColor = colors.cursor,
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = colors.outline,
                ),
            )
        },
    )
}

/**
 * A one-of-many setting, shown as a dropdown.
 *
 * Replaces a horizontal chip strip. Chips put every option on screen at all
 * times, which for a fifteen-theme list or a six-mode list meant a scrolling
 * row inside a scrolling pane and no way to see the current value at a glance.
 * A dropdown shows the selection and nothing else until asked.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    subtitle: String? = null,
    options: List<T>,
    selected: T,
    focused: Boolean = false,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    var expanded by remember { mutableStateOf(false) }

    // Confirm advances to the next option rather than opening the menu. The
    // launcher intercepts input before Compose sees it, so an open dropdown
    // would have no way to be navigated — cycling is operable from a pad and
    // leaves the dropdown for touch.
    ActivateOnConfirm(focused) {
        if (options.isNotEmpty()) {
            val next = (options.indexOf(selected) + 1).mod(options.size)
            onSelected(options[next])
        }
    }

    Box {
        SettingsRowShell(
            title = title,
            subtitle = subtitle,
            focused = focused,
            onClick = { expanded = true },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH.dp),
                ) {
                    Text(
                        text = label(selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.cursor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = MENU_MAX_HEIGHT.dp)
                .background(colors.surfaceElevated),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) colors.cursor else colors.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

/**
 * A numeric setting, adjusted with explicit −/+ buttons.
 *
 * Replaces a drag slider. A slider is a poor fit here on two counts: it cannot
 * be operated at all from a D-pad without inventing a focus-then-adjust mode,
 * and dragging for a value like "5 columns" is imprecise for no benefit. Two
 * buttons and a readout are exact, and map onto Left/Right directly.
 */
@Composable
fun StepperRow(
    title: String,
    subtitle: String? = null,
    value: String,
    focused: Boolean = false,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    /** Called by Confirm once the value is already at its maximum. */
    onWrap: (() -> Unit)? = null,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = ThorTheme.colors

    // Confirm steps the value up, wrapping back to the minimum at the top, so a
    // stepper is reachable from a pad without stealing Left and Right — which
    // are already navigation inside a settings page.
    ActivateOnConfirm(focused) {
        if (canIncrease) onIncrease() else onWrap?.invoke()
    }

    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        focused = focused,
        onClick = null,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StepperButton(
                    icon = Icons.Rounded.Remove,
                    enabled = canDecrease,
                    description = "Decrease $title",
                    onClick = onDecrease,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.cursor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = STEPPER_VALUE_WIDTH.dp),
                )
                StepperButton(
                    icon = Icons.Rounded.Add,
                    enabled = canIncrease,
                    description = "Increase $title",
                    onClick = onIncrease,
                )
            }
        },
    )
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (enabled) colors.surfaceElevated else Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) colors.onSurface else colors.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** An integer setting. */
@Composable
fun IntSliderRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    range: IntRange,
    focused: Boolean = false,
    suffix: String = "",
    onValueChange: (Int) -> Unit,
) {
    // Step size scales with the range so a 128..4096 MB setting is not
    // forty tedious presses wide.
    val step = ((range.last - range.first) / TARGET_STEPS).coerceAtLeast(1)
    val clamped = value.coerceIn(range.first, range.last)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = "$clamped$suffix",
        focused = focused,
        canDecrease = clamped > range.first,
        canIncrease = clamped < range.last,
        onWrap = { onValueChange(range.first) },
        onDecrease = { onValueChange((clamped - step).coerceAtLeast(range.first)) },
        onIncrease = { onValueChange((clamped + step).coerceAtMost(range.last)) },
    )
}

/** A fractional setting, stepped in tenths of its range. */
@Composable
fun SliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    focused: Boolean = false,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    onValueChange: (Float) -> Unit,
) {
    val span = range.endInclusive - range.start
    val step = span / TARGET_STEPS
    val clamped = value.coerceIn(range.start, range.endInclusive)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = valueLabel(clamped),
        focused = focused,
        canDecrease = clamped > range.start + EPSILON,
        canIncrease = clamped < range.endInclusive - EPSILON,
        onWrap = { onValueChange(range.start) },
        onDecrease = { onValueChange((clamped - step).coerceAtLeast(range.start)) },
        onIncrease = { onValueChange((clamped + step).coerceAtMost(range.endInclusive)) },
    )
}

/** A colour swatch picker, used for the accent colour. */
@Composable
fun ColorRow(
    title: String,
    subtitle: String? = null,
    colorsToPick: List<Color>,
    selected: Color?,
    focused: Boolean = false,
    onSelected: (Color?) -> Unit,
) {
    val theme = ThorTheme.colors
    val dimens = ThorTheme.dimens

    // Confirm advances through the swatches, ending on "no override".
    ActivateOnConfirm(focused) {
        val index = colorsToPick.indexOf(selected)
        onSelected(colorsToPick.getOrNull(index + 1))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .revealWhenFocused(focused)
            .thorCursor(focused = focused, cornerRadius = dimens.cornerRadiusSmall)
            // Horizontal padding is inside the cursor bounds: without it the
            // highlight ring is drawn straight over the first and last
            // characters of the row's text.
            .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.onSurface,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceVariant,
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(colorsToPick) { color ->
                val isSelected = color == selected
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) {
                                Modifier.thorCursor(focused = true, cornerRadius = 13.dp)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelected(color) },
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(theme.surfaceElevated)
                        .clickable { onSelected(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A free-text setting, used for API keys and account names.
 *
 * Secrets are masked with a reveal toggle — a key is unusable if it cannot be
 * checked for a typo, but leaving it in plain sight on a shared handheld is
 * worse.
 */
@Composable
fun TextFieldRow(
    title: String,
    subtitle: String? = null,
    value: String,
    placeholder: String? = null,
    isSecret: Boolean = false,
    focused: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    var revealed by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    /*
     * Confirm hands the row over to the launcher's keyboard.
     *
     * A text row cannot be edited by the cursor alone, and it used to ask a platform
     * `FocusRequester` for focus — which summons an IME that does not render on this
     * hardware. Claiming THOR's own text focus raises THOR's own keyboard instead,
     * which is the only one that appears.
     */
    val textInput = LocalThorTextInput.current
    val fieldId = remember(title) { "setting-" + title }
    ActivateOnConfirm(focused) {
        textInput.focus(id = fieldId, label = title, initial = draft) { edited ->
            draft = edited
            onValueChange(edited.trim())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .revealWhenFocused(focused)
            .thorCursor(focused = focused, cornerRadius = dimens.cornerRadiusSmall)
            // Horizontal padding is inside the cursor bounds: without it the
            // highlight ring is drawn straight over the first and last
            // characters of the row's text.
            .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            ThorInputField(
                id = fieldId,
                label = title,
                value = draft,
                onValueChange = {
                    draft = it
                    onValueChange(it.trim())
                },
                placeholder = placeholder,
                secret = isSecret && !revealed,
                modifier = Modifier.weight(1f),
            )
            if (isSecret) {
                Text(
                    text = if (revealed) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { revealed = !revealed }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** A section heading used outside a [SettingsSection]. */
@Composable
fun SectionHeader(text: String) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = colors.onBackground,
        modifier = Modifier.padding(top = dimens.spacingLarge, bottom = 10.dp),
    )
}

/** A tappable row that performs an action rather than holding a value. */
@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    focused: Boolean = false,
    destructive: Boolean = false,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    ActivateOnConfirm(focused, onClick)
    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        focused = focused,
        titleColor = if (destructive) colors.error else null,
        onClick = onClick,
        trailing = trailingLabel?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) colors.error else colors.cursor,
                )
            }
        },
    )
}

/** A read-only informational row. */
@Composable
fun InfoRow(title: String, value: String) {
    val colors = ThorTheme.colors
    SettingsRowShell(
        title = title,
        subtitle = null,
        focused = false,
        onClick = null,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private const val ROW_HEIGHT = 60

/** Inset that keeps the focus ring clear of the row.s content. */
private const val ROW_INSET = 14
private const val VALUE_MAX_WIDTH = 200
private const val STEPPER_VALUE_WIDTH = 62
private const val MENU_MAX_HEIGHT = 340

/** How many presses it takes to cross a setting's full range. */
private const val TARGET_STEPS = 10
private const val EPSILON = 0.0001f
