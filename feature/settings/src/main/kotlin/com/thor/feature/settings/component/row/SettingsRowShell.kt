package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.SettingsCard

/**
 * Shared row shell.
 *
 * Title and description on the left, one control on the right. Every settings
 * row uses this, so a pane reads as a single column of labels with a single
 * column of controls rather than a mix of inline widgets at varying heights.
 */
@Composable
internal fun SettingsRowShell(
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

    SettingsCard(
        focused = focused,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ROW_HEIGHT.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = if (subtitle == null) 26.dp else 38.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(
                        if (focused) colors.cursor else colors.outline.copy(alpha = 0.34f),
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor ?: colors.onSurface,
                    fontWeight = FontWeight.Medium,
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
}

private const val ROW_HEIGHT = 60

/** Inset that keeps the focus ring clear of the row's content. */
internal const val ROW_INSET = 14
internal const val VALUE_MAX_WIDTH = 200
