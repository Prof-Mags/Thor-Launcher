package com.thor.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * One row of any menu in the launcher.
 *
 * There is a single implementation on purpose. The side menu, the long-press
 * menu, the dropdowns, the platform picker, the folder picker and the sort
 * dialog were six hand-written versions of the same row, and they had already
 * drifted into six different looks — a menu opened in one place did not resemble
 * a menu opened in another, and each new one drifted again. Anything that offers
 * a list of choices uses this, so there is nothing left to keep in step by hand.
 *
 * The shape is: a marker down the leading edge, an optional icon tile, a label
 * with an optional second line, and an optional trailing note.
 *
 * @param focused the controller cursor is on this row — draws the focus ring as
 *   well as lighting the row, because a pad user has no pointer to follow
 * @param selected this row is the value currently in force. Lit but unringed, so
 *   in a menu where both apply the cursor is still distinguishable from the
 *   current setting.
 * @param accent overrides the theme accent for the marker and the lit plate.
 *   Used where the row's subject has a colour of its own — a platform's, or the
 *   error colour on a destructive action.
 * @param leading drawn in place of [icon], for rows whose mark is artwork rather
 *   than a glyph.
 */
@Composable
fun ThorMenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    focused: Boolean = false,
    selected: Boolean = false,
    accent: Color? = null,
    enabled: Boolean = true,
    /**
     * How many lines the title may run to before it is cut.
     *
     * One nearly everywhere, because a menu row is scanned rather than read and a
     * wrapping title makes the rows different heights. Raised where the label is a
     * name rather than a caption and the row exists to identify it — a platform
     * picker offering "Nintendo Entertainment Sy…" is offering a guess.
     */
    labelMaxLines: Int = 1,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel

    val hover = rememberPointerHover()
    val lit = focused || selected || hover.isHovered
    val tint = accent ?: colors.cursor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingSmall, vertical = 2.dp)
            .clip(shape)
            /*
             * An accent wash rather than a surface step.
             *
             * These rows sit on containers at three different levels — a menu
             * panel, the highest surface inside a dropdown, a dialog — and a
             * step reads on some and vanishes on others. A tint of the accent
             * reads on all of them, which is what lets one row serve every menu.
             */
            .background(if (lit) tint.copy(alpha = LIT_ALPHA) else Color.Transparent)
            .thorCursor(focused = focused, shape = shape)
            .pointerHover(hover)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = ROW_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        // Drawn only when lit, and holding its width either way, so the rows do
        // not shift sideways as the cursor runs down them.
        Box(
            modifier = Modifier
                .width(MARKER_WIDTH.dp)
                .height(MARKER_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(
                    when {
                        !lit -> SolidColor(Color.Transparent)
                        accent != null -> SolidColor(accent)
                        else -> Brush.verticalGradient(colors.accentStops)
                    },
                ),
        )

        when {
            leading != null -> leading()
            icon != null -> Box(
                modifier = Modifier
                    .size(ICON_TILE.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(if (lit) tint.copy(alpha = 0.16f) else colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (lit) tint else colors.onSurfaceVariant,
                    modifier = Modifier.size(ICON_GLYPH.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                // The same size the rest of the launcher reads at.
                //
                // These were `labelLarge` over `labelSmall` — 14sp over 10sp, the
                // smallest style in the whole scale — while every settings row and
                // every card beside them used 16 over 12. That is a fifth off the
                // title and a third off the description, and since this one row is
                // what draws the side menu, the long-press menu, the folder
                // picker, the sort dialog and every dropdown, the effect was that
                // every menu in the launcher looked like a scaled-down copy of the
                // screen that raised it. Label styles are for chips and buttons,
                // where the text is one word inside its own container; a row with a
                // title and a sentence under it is body copy.
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                    accent != null && lit -> accent
                    lit -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    // Raised along with the size. A description that carries real
                    // information — "Stop keeping this at the front" is the only
                    // thing telling you what the row does — should not also be the
                    // faintest text on screen.
                    color = colors.onSurfaceVariant.copy(alpha = DESCRIPTION_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (lit) tint else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The tile size a [ThorMenuRow]'s [leading] slot should fill, for artwork marks. */
const val THOR_MENU_ICON_TILE = 44

private const val MARKER_WIDTH = 3
private const val MARKER_HEIGHT = 26
private const val ICON_TILE = THOR_MENU_ICON_TILE
private const val ICON_GLYPH = 23

/** Vertical breathing room, matched to the taller type it now sits around. */
private const val ROW_PADDING = 10

private const val LIT_ALPHA = 0.14f

/** Muted, but still legible — this line often carries the row's only explanation. */
private const val DESCRIPTION_ALPHA = 0.85f
