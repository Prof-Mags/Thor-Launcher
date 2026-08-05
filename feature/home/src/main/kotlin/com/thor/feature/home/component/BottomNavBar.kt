package com.thor.feature.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.model.PanelLayout
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.core.model.CornerStyle
import com.thor.core.model.LauncherTab

/**
 * The launcher's three top-level sections, along the bottom of the grid panel.
 *
 * Replaces the floating dock as the bottom furniture. The two are different kinds
 * of thing and it is worth being clear which this is: the dock held five
 * *actions*, each doing something and returning you where you were, while this
 * selects *where you are*. Only one of them can own the bottom edge, and on a
 * panel this size a section switcher earns it — the actions have somewhere else
 * to live, and a place you cannot navigate to does not.
 *
 * Every part of it is reachable both ways. Touch taps a tab directly; the
 * controller arrives from the grid by pressing Down past the bottom row, moves
 * with Left and Right, and leaves upward. That last part is why the dock is being
 * replaced rather than joined: the dock never had controller focus at all, so on a
 * controller-first launcher its five slots were unreachable without a touchscreen.
 *
 * @param focusedTab the tab the controller cursor is on, or null when the cursor
 *   is up in the grid. Distinct from [selectedTab]: you move across the bar to
 *   look before pressing, exactly as in the theme gallery.
 */
@Composable
fun BottomNavBar(
    selectedTab: LauncherTab,
    focusedTab: LauncherTab?,
    onTabSelected: (LauncherTab) -> Unit,
    /**
     * Sections the user has enabled.
     *
     * The bar draws only these. A section whose extension has not been imported
     * is absent rather than disabled — an empty tab is a promise the launcher
     * cannot keep.
     */
    tabs: List<LauncherTab> = LauncherTab.ORDERED,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens
    val colors = ThorTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT.dp)
            // The bar is a raised thing lying on the page, so it takes the
            // theme's raised treatment — which is what gives it a shadow on
            // Switch, a lit edge on Vision and a hard outline on Retro without
            // this file knowing any of those exist.
            .thorSurface(
                /*
                 * Follows the interface-wide corner setting, but only on its top
                 * edge — the bar sits flush on the bottom of the panel, and
                 * rounding corners that meet the screen edge leaves two slivers of
                 * wallpaper showing and reads as a floating card rather than as
                 * the frame of the panel.
                 */
                shape = when (ThorTheme.shapes.style) {
                        CornerStyle.SQUARE -> RectangleShape
                        else -> RoundedCornerShape(
                            topStart = dimens.cornerRadius,
                            topEnd = dimens.cornerRadius,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp,
                        )
                    },
                color = colors.surfaceElevated,
                level = SurfaceLevel.RAISED,
            )
            .padding(
                horizontal = dimens.spacingSmall,
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            NavTab(
                tab = tab,
                selected = tab == selectedTab,
                cursorOn = tab == focusedTab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One tab.
 *
 * Selected and cursor-on are independent, for the reason the theme cards had to
 * learn: the cursor arrives on the selected tab every time it enters the bar, so
 * ranking them in one `when` would leave the cursor invisible at precisely the
 * moment it appears. Selection is the filled pill and the accent; the cursor is
 * the launcher's own ring.
 */
@Composable
private fun NavTab(
    tab: LauncherTab,
    selected: Boolean,
    cursorOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion

    // The pointer lights a tab the same way the controller cursor does; see the
    // note in GridCell for why it is the same treatment and not a second one.
    val hover = rememberPointerHover()
    val lit = cursorOn || hover.isHovered

    val tint by animateColorAsState(
        targetValue = if (selected) colors.cursor else colors.onSurfaceVariant,
        animationSpec = motion.tweenSpec(motion.cursorMillis),
        label = "navTabTint",
    )

    // Lifts under the cursor the way a focused tile does on a television, and
    // the same amount the theme cards lift, so the two read as one system.
    val lift by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = motion.tweenSpec(motion.cursorMillis),
        label = "navTabLift",
    )

    // The pill follows the interface-wide corner setting, so a squared launcher
    // does not keep one capsule in the middle of the bar.
    val shape = ThorTheme.shapes.pill
    // No ripple: the launcher draws its own cursor, and a Material ripple
    // underneath it is a second focus treatment disagreeing with the first.
    val interaction = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            /*
             * Tight, and deliberately so.
             *
             * The bar is a fixed height and the content has to fit inside it: icon,
             * label and underline together come to within a couple of dp of
             * [PanelLayout.NAV_BAR_HEIGHT], so the insets here are the slack. Loosening any of
             * them without raising the bar clips the underline off the bottom.
             */
            .padding(
                horizontal = dimens.spacingTiny,
                vertical = TAB_INSET.dp,
            )
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.horizontalGradient(colors.accentStops),
                        shape = shape,
                        alpha = SELECTED_PILL_ALPHA,
                    )
                } else {
                    Modifier
                },
            )
            .thorCursor(focused = lit, shape = shape)
            .pointerHover(hover)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = 4.dp,
                vertical = TAB_PADDING.dp,
            ),
    ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size((ICON_SIZE + lift * ICON_LIFT).dp),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1,
                )

        // A short underline under the selected tab, because on the flat themes
        // the pill behind it is barely there and colour alone is not enough to
        // say which section you are in.
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .height(UNDERLINE_HEIGHT.dp)
                .width(
if (selected) UNDERLINE_WIDTH.dp else 0.dp,
                )
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor),
        )
    }
}

/** Each section's mark. Rounded to match the launcher's other iconography. */
private val LauncherTab.icon: ImageVector
    get() = when (this) {
        LauncherTab.STREAM -> Icons.Rounded.Sensors
        LauncherTab.HOME -> Icons.Rounded.Home
        LauncherTab.MOVIES -> Icons.Rounded.Movie
        LauncherTab.SHOWS -> Icons.Rounded.Tv
    }

private val LauncherTab.indexLabel: String
    get() = when (this) {
        LauncherTab.HOME -> "01"
        LauncherTab.MOVIES -> "02"
        LauncherTab.STREAM -> "03"
        // This bar never draws Shows - it is a couch-mode tab - but the label has
        // to exist for the same reason every branch here does.
        LauncherTab.SHOWS -> "04"
    }

private const val BAR_HEIGHT = PanelLayout.NAV_BAR_HEIGHT

/**
 * Vertical breathing room, inside and outside the tab's pill.
 *
 * Both are part of the height budget described on the tab's modifier; see
 * [PanelLayout.NAV_BAR_HEIGHT].
 */
private const val TAB_INSET = 3
private const val TAB_PADDING = 2

private const val ICON_SIZE = 20
private const val ICON_LIFT = 3
private const val UNDERLINE_HEIGHT = 2
private const val UNDERLINE_WIDTH = 18
private const val SEGMENT_UNDERLINE_WIDTH = 42
private const val INDEX_UNDERLINE_WIDTH = 54

/** The selected pill is a wash, not a fill: a solid accent bar is too loud here. */
private const val SELECTED_PILL_ALPHA = 0.18f
