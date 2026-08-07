package com.thor.feature.home.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorColors
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ShortcutAction
import com.thor.core.model.ShortcutGrid
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * Fast launcher and system controls opened by either stick click.
 *
 * The controller still sees one flat four-column grid. Visual grouping is kept
 * inside each tile so the cursor arithmetic remains simple and predictable.
 */
@Composable
fun ShortcutPanel(
    visible: Boolean,
    actions: List<ShortcutAction>,
    focusedIndex: Int,
    onAction: (ShortcutAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion
    val focusedAction = actions.getOrNull(focusedIndex) ?: actions.first()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenSpec(motion.panelMillis)),
        exit = fadeOut(motion.tweenSpec(motion.selectionMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    animationSpec = motion.tweenSpec(motion.panelMillis),
                    initialScale = 0.94f,
                ),
                exit = scaleOut(
                    animationSpec = motion.tweenSpec(motion.selectionMillis),
                    targetScale = 0.97f,
                ),
            ) {
                GlassSurface(
                    shape = ThorTheme.shapes.large,
                    color = colors.surfaceHighest,
                    level = SurfaceLevel.OVERLAY,
                    modifier = Modifier
                        .widthIn(max = PANEL_MAX_WIDTH.dp)
                        .fillMaxWidth(PANEL_WIDTH_FRACTION)
                        // Consume taps inside the panel instead of dismissing it.
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ACCENT_HEIGHT.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            colors.primary,
                                            colors.accentEnd,
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimens.spacing),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                        ) {
                            ShortcutHeader(
                                selected = focusedIndex.coerceIn(0, actions.lastIndex) + 1,
                                total = actions.size,
                            )

                            ShortcutGrid(
                                actions = actions,
                                focusedIndex = focusedIndex,
                                onAction = onAction,
                            )

                            ShortcutFooter(description = focusedAction.description)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutHeader(selected: Int, total: Int) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(HEADER_ICON_SIZE.dp)
                .clip(ThorTheme.shapes.small)
                .background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Quick controls",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Launcher  \u00b7  Capture  \u00b7  System",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$selected / $total",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "L3 / R3",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShortcutGrid(
    actions: List<ShortcutAction>,
    focusedIndex: Int,
    onAction: (ShortcutAction) -> Unit,
) {
    val dimens = ThorTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        actions.chunked(ShortcutGrid.COLUMNS).forEachIndexed { rowIndex, rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                rowActions.forEachIndexed { columnIndex, action ->
                    val actionIndex = rowIndex * ShortcutGrid.COLUMNS + columnIndex
                    ShortcutTile(
                        action = action,
                        index = actionIndex,
                        focused = actionIndex == focusedIndex,
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(ShortcutGrid.COLUMNS - rowActions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    action: ShortcutAction,
    index: Int,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val highlighted = focused || hover.isHovered
    val group = action.group()
    val accent = group.accent(colors)
    val background by animateColorAsState(
        targetValue = if (highlighted) {
            accent.copy(alpha = 0.16f)
        } else {
            colors.surfaceElevated.copy(alpha = 0.72f)
        },
        animationSpec = motion.tweenSpec(motion.selectionMillis),
        label = "shortcutTileBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (highlighted) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = motion.tweenSpec(motion.selectionMillis),
        label = "shortcutTileContent",
    )

    Column(
        modifier = modifier
            .height(TILE_HEIGHT.dp)
            .clip(shape)
            .background(background)
            .pointerHover(hover)
            .thorCursor(focused = highlighted, shape = shape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Box(
                    modifier = Modifier
                        .size(TILE_ICON_PLATE_SIZE.dp)
                        .clip(shape)
                        .background(accent.copy(alpha = if (highlighted) 0.24f else 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon(),
                        contentDescription = action.description,
                        tint = if (highlighted) accent else contentColor,
                        modifier = Modifier.size(TILE_ICON_SIZE.dp),
                    )
                }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = if (highlighted) 1f else 0.72f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShortcutFooter(description: String) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ShortcutHint(button = "A", label = "SELECT")
        ShortcutHint(button = "B", label = "CLOSE")
    }
}

@Composable
private fun ShortcutHint(button: String, label: String) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(colors.surfaceElevated.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button,
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private enum class ShortcutGroup(val label: String) {
    LAUNCHER("LAUNCHER"),
    CAPTURE("CAPTURE"),
    SYSTEM("SYSTEM"),
}

private fun ShortcutAction.group(): ShortcutGroup = when (this) {
    ShortcutAction.APPS,
    ShortcutAction.SEARCH,
    ShortcutAction.THOR_SETTINGS,
    ShortcutAction.SWAP_SCREENS,
    ShortcutAction.COUCH_MODE,
    ShortcutAction.SCAN_LIBRARY,
    -> ShortcutGroup.LAUNCHER

    ShortcutAction.RECORD,
    ShortcutAction.RECORD_SCREEN,
    ShortcutAction.SCREENSHOT,
    -> ShortcutGroup.CAPTURE

    ShortcutAction.WIFI,
    ShortcutAction.BLUETOOTH,
    ShortcutAction.VOLUME,
    ShortcutAction.SYSTEM_SETTINGS,
    -> ShortcutGroup.SYSTEM
}

private fun ShortcutGroup.accent(colors: ThorColors): Color = when (this) {
    ShortcutGroup.LAUNCHER -> colors.primary
    ShortcutGroup.CAPTURE -> colors.error
    ShortcutGroup.SYSTEM -> colors.secondary
}

private fun ShortcutAction.icon(): ImageVector = when (this) {
    ShortcutAction.APPS -> Icons.Rounded.Apps
    ShortcutAction.SEARCH -> Icons.Rounded.Search
    ShortcutAction.THOR_SETTINGS -> Icons.Rounded.Tune
    ShortcutAction.SWAP_SCREENS -> Icons.Rounded.SwapVert
    ShortcutAction.COUCH_MODE -> Icons.Rounded.Weekend
    ShortcutAction.SCAN_LIBRARY -> Icons.Rounded.Refresh
    ShortcutAction.RECORD -> Icons.Rounded.Videocam
    ShortcutAction.RECORD_SCREEN -> Icons.AutoMirrored.Rounded.ScreenShare
    ShortcutAction.SCREENSHOT -> Icons.Rounded.PhotoCamera

    ShortcutAction.WIFI -> Icons.Rounded.Wifi
    ShortcutAction.BLUETOOTH -> Icons.Rounded.Bluetooth
    ShortcutAction.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    ShortcutAction.SYSTEM_SETTINGS -> Icons.Rounded.Settings
}

private const val PANEL_WIDTH_FRACTION = 0.90f
private const val PANEL_MAX_WIDTH = 620
private const val ACCENT_HEIGHT = 4
private const val HEADER_ICON_SIZE = 42
private const val TILE_HEIGHT = 78
private const val TILE_ICON_PLATE_SIZE = 32
private const val TILE_ICON_SIZE = 20
