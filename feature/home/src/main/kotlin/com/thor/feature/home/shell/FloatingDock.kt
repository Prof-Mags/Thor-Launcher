package com.thor.feature.home.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.DockSettings
import com.thor.core.model.DockStyle
import com.thor.core.model.IconShape
import com.thor.core.model.LauncherAction
import com.thor.feature.home.grid.toComposeShape

/**
 * The floating dock beneath the grid.
 *
 * A compact pill sized to its contents. Slots hold [LauncherAction]s rather
 * than fixed buttons, which is what lets a user swap any of the five defaults
 * for an app, a folder or another launcher action without the dock knowing what
 * it is hosting.
 *
 * Focus is shown as a filled shape behind the glyph rather than as the grid's
 * cursor ring: at this size a ring plus its glow is thicker than the icon it
 * surrounds. The shape follows the icon-shape setting so the dock and the grid
 * never disagree about whether icons are round or square.
 */
@Composable
fun FloatingDock(
    settings: DockSettings,
    focusedSlot: Int?,
    /** Matches the grid's icon shape so the two surfaces agree. */
    iconShape: IconShape,
    onSlotSelected: (Int) -> Unit,
    onSlotActivated: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = ThorTheme.motion
    val dimens = ThorTheme.dimens
    val scale = settings.scale.coerceIn(MIN_SCALE, MAX_SCALE)
    val slotSize = SLOT_SIZE.dp * scale
    val height = DOCK_HEIGHT.dp * scale

    // Square style matches the grid's own corner treatment and drops the
    // translucency, so the dock reads as the row below the grid rather than as a
    // separate object floating over it.
    val isSquare = settings.style == DockStyle.SQUARE
    val surfaceShape = if (isSquare) {
        RoundedCornerShape(dimens.cornerRadiusSmall)
    } else {
        RoundedCornerShape(percent = 50)
    }

    AnimatedVisibility(
        // Auto-hide keeps the dock out of the way until a slot is focused,
        // which is the only way to reach it without touch anyway.
        visible = settings.visible && (!settings.autoHide || focusedSlot != null),
        enter = slideInVertically(
            animationSpec = motion.tweenSpec(motion.panelMillis),
        ) { it } + fadeIn(motion.tweenSpec(motion.panelMillis)),
        exit = slideOutVertically(
            animationSpec = motion.tweenSpec(motion.panelMillis),
        ) { it } + fadeOut(motion.tweenSpec(motion.panelMillis)),
        modifier = modifier,
    ) {
        GlassSurface(
            shape = surfaceShape,
            // Elevated, matching the grid cells it sits beside rather than the
            // base surface behind them.
            color = ThorTheme.colors.surfaceElevated,
            alphaOverride = settings.backgroundAlpha,
            // Square style blends in rather than standing out, so it forgoes the
            // blur that makes the pill read as a pane of glass over the grid.
            translucent = settings.blurEnabled && !isSquare,
            modifier = Modifier
                .height(height)
                .wrapContentWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth()
                    .padding(horizontal = EDGE_PADDING.dp * scale),
                // Both axes centred. The slots used to sit in a column sized for
                // a label that is no longer drawn, which left them riding high
                // in the dock with dead space underneath.
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SLOT_GAP.dp * scale),
            ) {
                // The indicator follows the user's icon shape, so a square grid
                // does not get a circular highlight in the dock beneath it.
                val indicatorShape = iconShape.toComposeShape(
                    radius = dimens.cornerRadius.value,
                    cornerStyle = ThorTheme.shapes.style,
                )

                settings.slots.forEachIndexed { index, action ->
                    DockSlot(
                        action = action,
                        // Not drawn: the dock is glyph-only. Retained as the
                        // slot's accessibility name, which it would otherwise
                        // not have.
                        label = settings.labels.getOrNull(index).orEmpty(),
                        focused = focusedSlot == index,
                        slotSize = slotSize,
                        indicatorShape = indicatorShape,
                        onActivated = {
                            onSlotSelected(index)
                            onSlotActivated(action)
                        },
                    )
                }
            }
        }
    }
}

/**
 * One dock slot: a centred glyph in a square touch target.
 *
 * Deliberately a single [Box] rather than an icon-over-label column. The label
 * is gone, and keeping the column meant the glyph was centred within the column
 * rather than within the dock.
 */
@Composable
private fun DockSlot(
    action: LauncherAction,
    label: String,
    focused: Boolean,
    slotSize: Dp,
    indicatorShape: Shape,
    onActivated: () -> Unit,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion

    val lift by animateFloatAsState(
        targetValue = if (focused) FOCUS_LIFT else 1f,
        animationSpec = motion.tweenSpec(motion.selectionMillis),
        label = "dockSlotLift",
    )

    // No ripple: the dock sits over artwork, and a rectangular ripple bounded
    // by the slot would flash outside a rounded indicator.
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(slotSize)
            .clip(indicatorShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onActivated,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (focused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.cursor.copy(alpha = FOCUS_TINT_ALPHA), indicatorShape),
            )
        }
        Icon(
            imageVector = action.icon(),
            contentDescription = label.ifEmpty { null },
            tint = if (focused) colors.cursor else colors.onSurface,
            modifier = Modifier
                .size(slotSize * ICON_FRACTION)
                .scale(lift),
        )
    }
}

/** Stock glyph for a launcher action. */
fun LauncherAction.icon(): ImageVector = when (this) {
    LauncherAction.OpenSettings, is LauncherAction.OpenSettingsCategory -> Icons.Rounded.Settings
    LauncherAction.OpenAppDrawer -> Icons.Rounded.Apps
    LauncherAction.OpenSearch -> Icons.Rounded.Search
    LauncherAction.ToggleKeyboard -> Icons.Rounded.Keyboard
    LauncherAction.OpenGallery -> Icons.Rounded.Image
    LauncherAction.ShowRecents -> Icons.Rounded.SpaceDashboard
    LauncherAction.OpenPowerMenu -> Icons.Rounded.PowerSettingsNew
    else -> Icons.Rounded.Widgets
}

/**
 * The dock's rendered height.
 *
 * Exposed so the grid can reserve exactly the clearance the dock occupies —
 * deriving it separately is how the two drift apart and the bottom row ends up
 * half-hidden.
 */
fun dockHeightFor(settings: DockSettings): Dp {
    val scale = settings.scale.coerceIn(MIN_SCALE, MAX_SCALE)
    return DOCK_HEIGHT.dp * scale
}

// Tuned so five slots form a pill roughly a third of the panel's width.
private const val SLOT_SIZE = 38
private const val SLOT_GAP = 6
private const val EDGE_PADDING = 10

/** One height for both styles, now that no slot can carry a label. */
private const val DOCK_HEIGHT = 52
private const val ICON_FRACTION = 0.58f
private const val FOCUS_LIFT = 1.12f
private const val FOCUS_TINT_ALPHA = 0.22f
private const val MIN_SCALE = 0.7f
private const val MAX_SCALE = 1.4f
