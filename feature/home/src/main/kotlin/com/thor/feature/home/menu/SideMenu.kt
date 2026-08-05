package com.thor.feature.home.menu

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ThorMenuRow
import com.thor.feature.home.shell.icon

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
                        // A share of the screen with a ceiling, rather than a flat
                        // width. The rows read at body size now and a fixed 288dp
                        // was already tight for them; a fraction also keeps the
                        // drawer proportionate on the panel it is actually drawn
                        // on, which is not the one this number was picked against.
                        .fillMaxWidth(PANEL_FRACTION)
                        .widthIn(max = PANEL_WIDTH.dp)
                        // The panel swallows taps so they do not reach the
                        // dismiss handler on the scrim behind it.
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(vertical = dimens.spacing)) {
                        /*
                         * No wordmark above the heading.
                         *
                         * It was there to match Settings, which is a fair
                         * instinct and the wrong one here: this drawer is opened
                         * from the launcher's own home screen, so the only
                         * question it can answer is one nobody was asking. A
                         * launcher naming itself on top of itself is the sort of
                         * thing an app does when it is trying to be remembered.
                         */
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
    ThorMenuRow(
        label = action.label,
        description = action.description,
        icon = action.icon,
        focused = focused,
        modifier = modifier,
        onClick = onClick,
    )
}

private const val PANEL_WIDTH = 320

/** Enough of the screen to be a drawer, not so much that it is a page. */
private const val PANEL_FRACTION = 0.68f
private const val EDGE_MARKER_FRACTION = 0.5f
private const val ICON_TILE = 40
private const val ICON_GLYPH = 21

/** Beyond this a row is a bar; the drawer is tall and has only five of them. */
private const val MAX_ROW_HEIGHT = 92
