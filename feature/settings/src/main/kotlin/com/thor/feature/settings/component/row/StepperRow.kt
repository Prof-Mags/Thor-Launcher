package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

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

    RegisterForHorizontalSteps(focused)
    StepOnHorizontal(focused) { direction ->
        when {
            direction < 0 && canDecrease -> onDecrease()
            direction > 0 && canIncrease -> onIncrease()
            direction > 0 -> onWrap?.invoke()
        }
    }

    // Confirm remains a one-button shortcut; Left and Right operate the visible
    // minus and plus buttons through StepOnHorizontal above.
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
                modifier = Modifier
                    .clip(ThorTheme.shapes.pill)
                    .background(colors.surfaceElevated.copy(alpha = 0.88f))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StepperButton(
                    icon = Icons.Rounded.Remove,
                    enabled = canDecrease,
                    highlighted = focused,
                    description = "Decrease $title",
                    onClick = onDecrease,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) colors.cursor else colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = STEPPER_VALUE_WIDTH.dp),
                )
                StepperButton(
                    icon = Icons.Rounded.Add,
                    enabled = canIncrease,
                    highlighted = focused,
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
    highlighted: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    // The two smallest targets on the settings screen, and the two a pointer
    // presses most: a stepper is pressed repeatedly, and one that gives nothing
    // back until the number moves is one the user cannot tell they have hit.
    val hover = rememberPointerHover()
    val lit = enabled && hover.isHovered
    Box(
        modifier = Modifier
            .size(34.dp)
            .pointerHover(hover)
            .clip(ThorTheme.shapes.small)
            .background(
                when {
                    !enabled -> Color.Transparent
                    lit -> colors.cursor.copy(alpha = 0.24f)
                    else -> colors.surfaceHighest
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> colors.outline
                highlighted || lit -> colors.cursor
                else -> colors.onSurface
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val STEPPER_VALUE_WIDTH = 62
