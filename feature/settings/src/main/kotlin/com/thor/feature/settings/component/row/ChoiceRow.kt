package com.thor.feature.settings.component.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ThorDropdownItem
import com.thor.core.ui.component.ThorDropdownMenu
import com.thor.feature.settings.component.SettingsTextButton

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
    /**
     * Short form for the value button, where [label] is too long for it.
     *
     * The button is a fixed-width pill with one line of text, so a label
     * carrying a description ran off the end of it — and a caption cut mid-way
     * leaves whatever happens to be at the cut, which for a layout preset was a
     * stray column count. The full [label] still names each option in the menu,
     * where there is room; this is only what the row reports at rest.
     */
    valueLabel: ((T) -> String)? = null,
    /** A second line per option in the menu, for detail the label omits. */
    optionDescription: ((T) -> String?)? = null,
    onSelected: (T) -> Unit,
) {
    val colors = ThorTheme.colors
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
    RegisterForHorizontalSteps(focused && options.isNotEmpty())
    StepOnHorizontal(focused && options.isNotEmpty()) { direction ->
        val current = options.indexOf(selected).coerceAtLeast(0)
        onSelected(options[(current + direction).mod(options.size)])
    }

    Box {
        SettingsRowShell(
            title = title,
            subtitle = subtitle,
            focused = focused,
            onClick = { expanded = true },
            trailing = {
                SettingsTextButton(
                    label = (valueLabel ?: label)(selected),
                    modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH.dp),
                    containerColor = if (focused) {
                        colors.cursor.copy(alpha = 0.14f)
                    } else {
                        colors.surfaceHighest
                    },
                    contentColor = colors.cursor,
                    trailingIcon = Icons.Rounded.ExpandMore,
                )
            },
        )

        ThorDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = MENU_MAX_HEIGHT.dp),
        ) {
            options.forEach { option ->
                ThorDropdownItem(
                    label = label(option),
                    description = optionDescription?.invoke(option),
                    selected = option == selected,
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

private const val MENU_MAX_HEIGHT = 340
