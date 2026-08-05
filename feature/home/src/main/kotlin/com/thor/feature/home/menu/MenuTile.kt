package com.thor.feature.home.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * One tile in a menu card.
 *
 * Extracted from the long-press menu so the menus raised from an empty cell and
 * from a widget are the same object rather than a second thing that looks
 * nearly like it. The launcher already has a visual language for "a grid of
 * things you can press"; a near-copy of it is how two menus end up disagreeing
 * about their corner radius after the next theme change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuTile(
    icon: ImageVector,
    caption: String,
    focused: Boolean,
    height: Dp,
    onClick: () -> Unit,
    /** Drawn in the error colour throughout, focused or not. */
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    // The user's corner choice, not a number of my own. A literal radius here
    // is exactly how a launcher set to square corners ends up with rounded
    // buttons on one card.
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered

    // The cursor tint would otherwise make "Uninstall" the one tile that stops
    // looking dangerous at the moment it is about to be pressed.
    val accent = if (destructive) colors.error else colors.cursor

    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            withFrameNanos { }
            runCatching { requester.bringIntoView() }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .clip(shape)
            /*
             * Exactly what a side-menu row does: the cursor's tint when lit,
             * nothing at all when it is not.
             *
             * Filling a resting tile with any surface colour was the mistake
             * behind them reading as dark blocks. A row in the side menu is
             * transparent until the cursor arrives, so the card shows through
             * and the only thing carrying colour is the one the user is on —
             * which is also what makes the highlight legible rather than one
             * shade among twelve.
             */
            .background(if (lit) accent.copy(alpha = MENU_TILE_LIT_ALPHA) else Color.Transparent)
            // A hairline the side menu has no use for: its rows are stacked and
            // separated by their own spacing, whereas these are a grid and need
            // an edge to sit in columns rather than float.
            .border(1.dp, colors.outline.copy(alpha = MENU_TILE_EDGE_ALPHA), shape)
            .thorCursor(focused = lit, shape = shape)
            .pointerHover(hover)
            .bringIntoViewRequester(requester)
            .clickable(onClick = onClick)
            .padding(horizontal = MENU_TILE_INSET.dp),
    ) {
        // Both follow the side menu's rule: the tint when lit, the variant when
        // resting, and the error colour throughout for anything destructive.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                destructive -> colors.error
                lit -> accent
                else -> colors.onSurfaceVariant
            },
            modifier = Modifier.size(MENU_TILE_GLYPH.dp),
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                destructive -> colors.error
                lit -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = MENU_TILE_GAP.dp),
        )
    }
}

internal const val MENU_TILE_GAP = 6
internal const val MENU_TILE_INSET = 10
internal const val MENU_TILE_GLYPH = 18

/** How visible a resting tile's edge is; the fill itself is transparent. */
internal const val MENU_TILE_EDGE_ALPHA = 0.45f

/** Tint under a tile the cursor or the pointer is on; the side menu's own value. */
internal const val MENU_TILE_LIT_ALPHA = 0.14f

/**
 * The range a tile is allowed to be squeezed into.
 *
 * The floor is set by the label: below about this the caption is smaller than
 * the subtitle above it and the card stops reading as a deliberate object. The
 * ceiling is set by the television, where the leftover is large enough to give
 * eleven actions the proportions of a billboard if nothing said otherwise.
 */
internal const val MENU_TILE_MIN = 34
internal const val MENU_TILE_MAX = 52
