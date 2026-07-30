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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface

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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "THOR",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.cursor,
                            modifier = Modifier.padding(
                                start = dimens.spacing,
                                top = dimens.spacing,
                                bottom = dimens.spacingSmall,
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
                                    .weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One full-height menu row.
 *
 * Focus is a filled slab with an accent edge marker rather than the grid's
 * cursor ring: the rows are flush against each other and against the panel
 * edges, so a ring would have no gap to breathe in and would collide with its
 * neighbours.
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

    Row(
        modifier = modifier
            .background(
                if (focused) colors.cursor.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        // Accent bar on the leading edge marks the focused row without
        // enclosing it.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(EDGE_MARKER_FRACTION)
                .background(if (focused) colors.cursor else Color.Transparent),
        )
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = if (focused) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.headlineSmall,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val PANEL_WIDTH = 268
private const val EDGE_MARKER_FRACTION = 0.55f
