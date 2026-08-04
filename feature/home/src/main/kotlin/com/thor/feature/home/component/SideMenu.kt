package com.thor.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor

import com.thor.core.designsystem.theme.ThorTheme

/**
 * The four things the Start panel does.
 *
 * The previous menu listed twenty-two entries, most of which either duplicated
 * a settings page or opened something reachable elsewhere. Each of these acts
 * immediately — there are no submenus, because a shortcut panel that needs a
 * second decision is not a shortcut. Everything else lives in Settings.
 */
enum class SideMenuAction(
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    APPS("Apps", "Every installed application", Icons.Rounded.Apps),
    SORT("Sort", "Reorder the grid", Icons.Rounded.SwapVert),
    NEW("New", "Create a folder on this page", Icons.Rounded.Add),
    GRID("Grid", "Rearrange icons and resize the grid", Icons.Rounded.GridView),
    SETTINGS("Settings", "All launcher options", Icons.Rounded.Settings),
}

/**
 * The Start panel.
 */
@Composable
fun SideMenu(
    visible: Boolean,
    focusedAction: SideMenuAction?,
    onAction: (SideMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenSpec(motion.panelMillis)),
        exit = fadeOut(motion.tweenSpec(motion.panelMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(motion.tweenSpec(motion.panelMillis)) { -it },
                exit = slideOutHorizontally(motion.tweenSpec(motion.panelMillis)) { -it },
            ) {
                GlassSurface(
                    // Square, edge to edge. The panel is anchored to the screen
                    // edge, so rounding its corners would leave slivers of the
                    // grid showing through and make it read as a floating card
                    // rather than a drawer.
                    shape = RectangleShape,
                    bordered = false,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(PANEL_WIDTH.dp)
                        // The panel swallows taps so they do not reach the
                        // dismiss handler on the scrim behind it.
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(vertical = dimens.spacing)) {
                        /*
                         * The same heading Settings uses, for the same reason.
                         *
                         * These are the two drawers the launcher has, they open
                         * the same way and are read the same way, and a panel
                         * that titles itself differently reads as another app.
                         */
                        Text(
                            text = "LOKI",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cursor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = dimens.spacingLarge),
                        )
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onBackground,
                            modifier = Modifier.padding(
                                start = dimens.spacingLarge,
                                end = dimens.spacingLarge,
                                bottom = 2.dp,
                            ),
                        )
                        Text(
                            text = "Everything else is in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = dimens.spacingLarge,
                                end = dimens.spacingLarge,
                                bottom = dimens.spacing,
                            ),
                        )

                        // Rows share the remaining height evenly so the panel
                        // is filled rather than leaving a dead gap underneath
                        // four small rows.
                        SideMenuAction.entries.forEach { action ->
                            MenuRow(
                                action = action,
                                focused = action == focusedAction,
                                onClick = { onAction(action) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .heightIn(max = MAX_ROW_HEIGHT.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One menu row, drawn as the settings rail draws a category.
 *
 * Deliberately the same component in all but name: an icon on a tile, a title
 * over a muted line of description, an accent bar down the leading edge and the
 * launcher's own cursor ring. These two panels are the drawers the launcher has,
 * and a row that looked like neither the grid nor Settings was the odd one out.
 *
 * The rows are inset rather than flush, which is what makes the ring possible:
 * the previous version used a filled slab precisely because there was no gap for
 * a ring to sit in.
 */
@Composable
private fun MenuRow(
    action: SideMenuAction,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel

    Row(
        modifier = modifier
            .padding(horizontal = dimens.spacingSmall, vertical = 3.dp)
            .clip(shape)
            .background(if (focused) colors.surfaceHighest else Color.Transparent)
            .thorCursor(focused = focused, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        // Drawn only when focused, and holding its width either way, so the row
        // does not shift sideways as the cursor arrives.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(EDGE_MARKER_FRACTION)
                .clip(ThorTheme.shapes.pill)
                .background(
                    if (focused) {
                        Brush.verticalGradient(colors.accentStops)
                    } else {
                        SolidColor(Color.Transparent)
                    },
                ),
        )
        Box(
            modifier = Modifier
                .size(ICON_TILE.dp)
                .clip(ThorTheme.shapes.small)
                .background(
                    if (focused) colors.cursor.copy(alpha = 0.16f) else colors.surfaceElevated,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = if (focused) colors.cursor else colors.onSurfaceVariant,
                modifier = Modifier.size(ICON_GLYPH.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val PANEL_WIDTH = 288
private const val EDGE_MARKER_FRACTION = 0.5f
private const val ICON_TILE = 40
private const val ICON_GLYPH = 21

/** Beyond this a row is a bar; the drawer is tall and has only five of them. */
private const val MAX_ROW_HEIGHT = 92
