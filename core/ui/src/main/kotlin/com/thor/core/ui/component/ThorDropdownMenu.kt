package com.thor.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The launcher's dropdown, in the same idiom as the side and long-press menus.
 *
 * Material's own menu is a white-ish elevated sheet with dense single-line
 * rows, which read as a different product wherever one opened over the
 * launcher. This is the same panel those two menus use — the highest surface,
 * the panel corner radius, an accent marker down the leading edge of the live
 * row — so a menu looks like part of the launcher regardless of which screen
 * opened it.
 *
 * A wrapper around [DropdownMenu] rather than a replacement: positioning,
 * dismissal and the popup window are all fiddly and correct already. Only the
 * surface and the rows are ours.
 */
@Composable
fun ThorDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThorTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = ThorTheme.shapes.panel,
        containerColor = colors.surfaceHighest,
        // Flat. The shadow under Material's default menu is what made it read as
        // a sheet floating above the launcher rather than a panel belonging to
        // it; the surface step does the separating instead.
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.background(colors.surfaceHighest),
        content = content,
    )
}

/**
 * One row of a [ThorDropdownMenu].
 *
 * @param selected the option currently in force, marked rather than merely
 *   tinted so the current value is findable in a long list at a glance
 * @param description a second line, for options whose label does not say enough
 *   on its own. This is where the detail that used to be crammed into the row's
 *   button belongs — there is room for it here and there was none there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThorDropdownItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    selected: Boolean = false,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel

    val hover = rememberPointerHover()
    // Selected and hovered are drawn the same. The row under the pointer is the
    // one about to be chosen, and the one already chosen carries the marker, so
    // both are "the row this menu is about" at the moment it is looked at.
    val lit = selected || hover.isHovered

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingSmall, vertical = 2.dp)
            .clip(shape)
            .background(if (lit) colors.cursor.copy(alpha = 0.12f) else Color.Transparent)
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        // Drawn only when lit, and holding its width either way, so the rows do
        // not shift sideways as the pointer runs down them.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(EDGE_MARKER_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(
                    if (lit) {
                        Brush.verticalGradient(colors.accentStops)
                    } else {
                        SolidColor(Color.Transparent)
                    },
                ),
        )
        icon?.let {
            Box(
                modifier = Modifier
                    .size(ICON_TILE.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(
                        if (lit) colors.cursor.copy(alpha = 0.16f) else colors.surfaceElevated,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (lit) colors.cursor else colors.onSurfaceVariant,
                    modifier = Modifier.size(ICON_GLYPH.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) colors.cursor else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Matches the side and long-press menus, whose rows these are a copy of. */
private const val EDGE_MARKER_HEIGHT = 22
private const val ICON_TILE = 40
private const val ICON_GLYPH = 21
