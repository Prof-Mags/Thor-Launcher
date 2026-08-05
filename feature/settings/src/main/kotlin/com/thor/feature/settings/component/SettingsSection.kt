package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

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
    val shape = ThorTheme.shapes.panel

    Column(
        modifier = modifier
            .padding(top = dimens.spacingSmall, bottom = dimens.spacing)
            .clip(shape)
            .background(
                colors.surface.copy(alpha = 0.58f),
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = if (description == null) 20.dp else 34.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(colors.cursor),
            )
            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Column(content = content)
    }
}

/** Hairline separator between rows. */
@Composable
fun RowDivider() {
    Spacer(modifier = Modifier.height(8.dp))
}

/** Raised option surface shared by every inner settings page. */
@Composable
fun SettingsCard(
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = focused || (onClick != null && hover.isHovered)
    val shape = ThorTheme.shapes.panel

    Box(
        modifier = modifier
            .fillMaxWidth()
            .revealWhenFocused(focused)
            .clip(shape)
            .background(
                if (lit) {
                    colors.surfaceHighest.copy(alpha = 0.94f)
                } else {
                    colors.surfaceElevated.copy(alpha = 0.66f)
                },
            )
            .border(
                width = if (lit) 1.5.dp else 1.dp,
                color = if (lit) colors.cursor.copy(alpha = 0.72f)
                else colors.outline.copy(alpha = 0.24f),
                shape = shape,
            )
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .let { card -> if (onClick != null) card.clickable(onClick = onClick) else card },
        content = content,
    )
}

/** A section heading used outside a [SettingsSection]. */
@Composable
fun SectionHeader(text: String) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    Row(
        modifier = Modifier.padding(top = dimens.spacingLarge, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}
