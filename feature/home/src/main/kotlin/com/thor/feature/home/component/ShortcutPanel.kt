package com.thor.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ShortcutAction
import com.thor.core.model.ShortcutGrid

/**
 * The panel the AYN button raises.
 *
 * Drawn over the grid panel, because its
 * reason to exist is reaching the launcher and the system *without* leaving
 * whatever is running on the other display. It is the launcher's answer to the
 * quick-settings shade, restricted to things an unprivileged app can genuinely
 * do.
 *
 * One caveat is inherent to the hardware rather than to this code: only the
 * focused window is sent key events, so while a game holds focus the button goes
 * to the game. Touching this panel first hands focus back, and then the button
 * arrives here.
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
    if (!visible || actions.isEmpty()) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Anywhere outside the card dismisses, which is the touch equivalent
            // of releasing the button and pressing B.
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(dimens.cornerRadiusLarge),
            // Over an already-elevated panel, so the highest surface — the base
            // one reads as part of whatever it is covering.
            color = colors.surfaceHighest,
            modifier = Modifier
                // Cap outermost, fraction inside it: the other order lets
                // `fillMaxWidth` fix the width before the cap is ever consulted.
                .widthIn(max = CARD_MAX_WIDTH.dp)
                .fillMaxWidth(CARD_WIDTH_FRACTION)
                // Swallows taps that land on the card itself so they do not reach
                // the dismissing scrim underneath.
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dimens.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Names the binding that actually works on this hardware: the
                    // AYN button opens this too, but powers the bottom panel off as
                    // it does, which the launcher cannot prevent.
                    Text(
                        text = "Click a stick",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                /*
                 * A hand-built grid rather than a lazy one. There are ten tiles at
                 * most, all on screen at once, and the cursor is driven from the
                 * view model — a lazy grid would add scroll state and item recycling
                 * to a layout that can never scroll.
                 */
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                    actions.chunked(ShortcutGrid.COLUMNS).forEachIndexed { rowIndex, rowActions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                            rowActions.forEachIndexed { columnIndex, action ->
                                ShortcutTile(
                                    action = action,
                                    focused = rowIndex * ShortcutGrid.COLUMNS + columnIndex ==
                                        focusedIndex,
                                    onClick = { onAction(action) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keeps the last row's tiles the same width as every
                            // other row's instead of stretching them across the card.
                            repeat(ShortcutGrid.COLUMNS - rowActions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    action: ShortcutAction,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .thorCursor(focused = focused, cornerRadius = dimens.cornerRadiusSmall)
            .clickable(onClick = onClick)
            .padding(vertical = dimens.spacingSmall, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = action.description,
            tint = if (focused) colors.cursor else colors.onSurface,
            modifier = Modifier.size(ICON_SIZE.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) colors.cursor else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Two lines is enough for every shipped label; the cap stops a long
            // one from making its row taller than the others.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The icon for a tile. Kept next to the panel, so [ShortcutAction] stays UI-free. */
private fun ShortcutAction.icon(): ImageVector = when (this) {
    ShortcutAction.APPS -> Icons.Rounded.Apps
    ShortcutAction.SEARCH -> Icons.Rounded.Search
    ShortcutAction.THOR_SETTINGS -> Icons.Rounded.Tune
    ShortcutAction.SWAP_SCREENS -> Icons.Rounded.SwapVert
    ShortcutAction.SCAN_LIBRARY -> Icons.Rounded.Refresh
    ShortcutAction.RECORD -> Icons.Rounded.Videocam
    ShortcutAction.WIFI -> Icons.Rounded.Wifi
    ShortcutAction.BLUETOOTH -> Icons.Rounded.Bluetooth
    ShortcutAction.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    ShortcutAction.SYSTEM_SETTINGS -> Icons.Rounded.Settings
}

/** Wide enough for four tiles on the Thor's panels, short of edge-to-edge. */
private const val CARD_WIDTH_FRACTION = 0.92f
private const val CARD_MAX_WIDTH = 480
private const val ICON_SIZE = 26
