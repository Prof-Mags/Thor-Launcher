package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.settings.component.SettingsCard

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

    // Confirm advances through the swatches, ending on "no override".
    ActivateOnConfirm(focused) {
        val index = colorsToPick.indexOf(selected)
        onSelected(colorsToPick.getOrNull(index + 1))
    }
    RegisterForHorizontalSteps(focused)
    StepOnHorizontal(focused) { direction ->
        val choices: List<Color?> = colorsToPick + listOf(null)
        val current = choices.indexOf(selected).coerceAtLeast(0)
        onSelected(choices[(current + direction).mod(choices.size)])
    }

    SettingsCard(focused = focused) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                // A swatch is a circle of colour and nothing else, so the ring is
                // the only thing that can say the cursor is on it.
                val hover = rememberPointerHover()
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .pointerHover(hover)
                        .clip(ThorTheme.shapes.pill)
                        .background(color)
                        .border(
                            width = if (isSelected || hover.isHovered) 3.dp else 1.dp,
                            color = when {
                                isSelected -> theme.onSurface
                                hover.isHovered -> theme.cursor
                                else -> theme.outline
                            },
                            shape = ThorTheme.shapes.pill,
                        )
                        .clickable { onSelected(color) },
                )
            }
            item {
                val hover = rememberPointerHover()
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .pointerHover(hover)
                        .clip(ThorTheme.shapes.pill)
                        .background(theme.surfaceHighest)
                        .border(
                            width = if (selected == null || hover.isHovered) 3.dp else 1.dp,
                            color = when {
                                selected == null || hover.isHovered -> theme.cursor
                                else -> theme.outline
                            },
                            shape = ThorTheme.shapes.pill,
                        )
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
}
