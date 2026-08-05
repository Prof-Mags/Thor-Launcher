package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The single visual contract for text actions throughout Settings.
 *
 * Labels may grow wider, but every action shares one height, minimum width,
 * type style and theme-controlled shape. This keeps ON/OFF, OPEN, REMOVE,
 * CHOOSE and dialog actions from looking like unrelated control families.
 */
@Composable
fun SettingsTextButton(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    focused: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    reactToHover: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.pill
    val hover = rememberPointerHover()
    val highlighted = focused || (reactToHover && hover.isHovered)
    val actualContainer = containerColor ?: colors.surfaceHighest
    val actualContent = contentColor ?: colors.onSurface
    val actualBorder = borderColor ?: colors.outline.copy(alpha = 0.34f)

    Row(
        modifier = modifier
            .height(SETTINGS_BUTTON_HEIGHT.dp)
            .widthIn(min = SETTINGS_BUTTON_MIN_WIDTH.dp)
            .let { button -> if (reactToHover) button.pointerHover(hover) else button }
            .clip(shape)
            .background(actualContainer)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) actualContent.copy(alpha = 0.78f) else actualBorder,
                shape = shape,
            )
            .thorCursor(focused = highlighted, shape = shape)
            .let { button ->
                if (onClick != null) button.clickable(enabled = enabled, onClick = onClick)
                else button
            }
            .padding(horizontal = SETTINGS_BUTTON_HORIZONTAL_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = actualContent,
                modifier = Modifier.size(SETTINGS_BUTTON_ICON_SIZE.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) actualContent else actualContent.copy(alpha = 0.46f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = actualContent,
                modifier = Modifier.size(SETTINGS_BUTTON_ICON_SIZE.dp),
            )
        }
    }
}

private const val SETTINGS_BUTTON_HEIGHT = 36
private const val SETTINGS_BUTTON_MIN_WIDTH = 76
private const val SETTINGS_BUTTON_HORIZONTAL_PADDING = 12
private const val SETTINGS_BUTTON_ICON_SIZE = 15
