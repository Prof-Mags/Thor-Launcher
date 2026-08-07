package com.thor.feature.home.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CornerStyle
import com.thor.core.model.LauncherTab
import com.thor.core.model.PanelLayout
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.home.grid.GridCell

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

    // Resolved outside the draw lambda: a draw scope has no access to the theme,
    // and reading density here keeps the hairline one physical pixel rather than
    // one dp, which is what a hairline means.
    val edgeColor = colors.outline.copy(alpha = BAR_EDGE_ALPHA)
    val edgeStroke = with(LocalDensity.current) { 1.dp.toPx() }

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
            // The edge the bar meets the grid along; see [BAR_EDGE_ALPHA].
            .drawWithContent {
                drawContent()
                drawLine(
                    color = edgeColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = edgeStroke,
                )
            }
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

    /*
     * One animated value for "this is the section you are in", driving all of it.
     *
     * Selection used to be said three times over and quietly by each: a gradient
     * wash at 18% alpha, an eighteen-dp underline, and a colour change. Three
     * weak signals do not add up to one strong one — on the flat themes the wash
     * was invisible, the underline was a detail at arm's length, and the tint was
     * the only thing left carrying it. Worse, all three snapped: the underline
     * went from nothing to full width in a single frame, so switching sections
     * read as a glitch rather than as a move.
     *
     * Now one value crossfades and every part of the treatment is a function of
     * it, so the pill fills, the underline grows and the colour warms together.
     */
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.tweenSpec(motion.selectionMillis),
        label = "navTabSelection",
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
                // Drawn at all only while there is something to draw, so an
                // unselected tab still costs no brush and no border.
                if (selection > 0f) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(colors.accentStops),
                            shape = shape,
                            alpha = SELECTED_PILL_ALPHA * selection,
                        )
                        // An edge as well as a fill. The fill alone disappears on
                        // the themes whose accent is nearly the surface colour —
                        // Obsidian's accent is a warm white — and an accent-tinted
                        // hairline is legible on every one of them.
                        .border(
                            width = SELECTED_PILL_BORDER.dp,
                            color = colors.cursor.copy(alpha = SELECTED_PILL_BORDER_ALPHA * selection),
                            shape = shape,
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

        // The underline grows out of the centre rather than appearing whole, which
        // is what turns switching sections into a movement you can follow. Its
        // width is the same animated value as the pill, so nothing arrives early.
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .height(UNDERLINE_HEIGHT.dp)
                .width(UNDERLINE_WIDTH.dp * selection)
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

/**
 * The underline, widened from eighteen.
 *
 * Eighteen dp under a tab that is a fifth of a 640dp panel is a tick mark rather
 * than an indicator — it read as a dot at arm's length and disappeared entirely
 * across a room. This is about a third of the tab, which is enough to be seen as
 * a bar without becoming a second pill under the first.
 */
private const val UNDERLINE_HEIGHT = 3
private const val UNDERLINE_WIDTH = 28

/**
 * The selected pill is a wash, not a fill: a solid accent bar is too loud here.
 *
 * Raised from 0.18. At that value the pill was doing nothing on any theme whose
 * accent sits near its surface colour, which left the tint carrying selection on
 * its own — and the tint is the weakest of the three signals, because it is the
 * one a colour-vision mode can flatten.
 */
private const val SELECTED_PILL_ALPHA = 0.26f

/** The accent hairline round the pill, for the themes the fill cannot reach. */
private const val SELECTED_PILL_BORDER = 1
private const val SELECTED_PILL_BORDER_ALPHA = 0.45f

/**
 * The line where the bar meets the grid.
 *
 * The bar takes the theme's raised treatment, which on the flat presets is an
 * opaque rectangle in a surface colour a shade off the page behind it — so on
 * those themes there was no edge at all and the tabs looked like they were
 * floating at the bottom of the wallpaper. One hairline is the whole fix, and on
 * the themes that already cast a shadow it is invisible under it.
 */
private const val BAR_EDGE_ALPHA = 0.5f
