package com.thor.feature.topscreen.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.InfoPanelStyle

/**
 * The information panel's own surface, in whichever style the user chose.
 *
 * Shared because the game panel and the platform panel are the same object with
 * different contents: they alternate as the cursor crosses the grid, and any
 * difference between how they meet the artwork reads as one of the two being
 * wrong. The style is read from [ThorTheme] rather than passed in, so neither
 * panel — nor the four composables between them and the screen — has to carry a
 * preference it does not otherwise care about.
 *
 * See [InfoPanelStyle] for what the two answers are and what each costs.
 */

/** How wide the panel's box is, which the blended style needs more of. */
@Composable
fun Modifier.infoPanelBounds(): Modifier = when (ThorTheme.materials.infoPanel) {
    InfoPanelStyle.CARD -> this
        .fillMaxWidth(PANEL_WIDTH)
        .fillMaxHeight()
        .padding(PANEL_OUTER_PADDING.dp)

    /*
     * Wider, and run to the edges.
     *
     * A card is inset so it reads as an object sitting on the picture. A blended
     * panel is the opposite claim, so an inset would undo it: a gap on three
     * sides is an edge, however soft the fourth one is.
     *
     * The extra width is the fade, not more room — see [infoPanelContentInset],
     * which gives it straight back so the text keeps the measure it was written
     * for.
     */
    InfoPanelStyle.BLENDED -> this
        .fillMaxWidth(BLENDED_WIDTH)
        .fillMaxHeight()
}

/**
 * Extra room to leave at the panel's end, so the words stop before the fade.
 *
 * The whole risk of the blended style is text read against artwork instead of
 * against a known colour. Keeping the content at the width it has in the card
 * style means the fade happens in space that was never going to hold anything —
 * so it is a change to the panel's edge rather than to its layout.
 */
@Composable
fun infoPanelContentInset(): PaddingValues = when (ThorTheme.materials.infoPanel) {
    InfoPanelStyle.CARD -> PaddingValues(0.dp)
    InfoPanelStyle.BLENDED -> PaddingValues(end = BLENDED_FADE_WIDTH.dp)
}

/**
 * The surface itself: a card, or a gradient that runs out.
 *
 * @param outline the accent-tinted edge a card wears; ignored when blended,
 *   which by definition has none.
 */
@Composable
fun Modifier.infoPanelSurface(outline: Brush, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val colors = ThorTheme.colors
    return when (ThorTheme.materials.infoPanel) {
        /*
         * Opaque, not a tint over the artwork.
         *
         * The panel sits on a screenshot that is itself the subject, and a
         * half-transparent card over one puts detail behind text — every value on
         * it would be read against whatever happened to be underneath, which
         * changes per game and per screenshot.
         */
        InfoPanelStyle.CARD -> this
            .shadow(CARD_ELEVATION.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to colors.surfaceHighest,
                    .58f to colors.surfaceHighest,
                    1f to colors.surfaceElevated,
                ),
            )
            .border(1.dp, outline, shape)

        /*
         * Opaque where the words are, gone by the far edge.
         *
         * Held at full strength across the whole content width and only then
         * allowed to fall, rather than fading evenly across the panel: a gradient
         * that starts at the left starts dimming the surface under the first line
         * of text, which is the one thing this style must not do.
         *
         * No shadow and no clip. Both describe an object with an edge, and a
         * shadow in particular would draw a hard line down the middle of the
         * artwork — the exact seam this style exists to remove.
         */
        InfoPanelStyle.BLENDED -> this.background(
            Brush.horizontalGradient(
                0f to colors.surfaceHighest,
                BLENDED_SOLID_FRACTION to colors.surfaceHighest,
                1f to Color.Transparent,
            ),
        )
    }
}

/** The card's share of the panel, and the inset that makes it read as one. */
private const val PANEL_WIDTH = .395f
private const val PANEL_OUTER_PADDING = 14
private const val CARD_ELEVATION = 12

/**
 * The blended panel's share, which is the card's plus its fade.
 *
 * Everything here is in design units rather than pixels — the surface is drawn
 * against a fixed canvas, so a dp is the same fraction of the screen on every
 * device; see `DesignScale`.
 */
private const val BLENDED_WIDTH = .52f

/** How much of the extra width is fade rather than content. */
private const val BLENDED_FADE_WIDTH = 120

/**
 * Where the surface starts falling away.
 *
 * Past the end of the content, so the fade never crosses a word.
 */
private const val BLENDED_SOLID_FRACTION = .55f

/** True while the panel is an object with edges rather than a fade. */
@Composable
fun infoPanelHasEdges(): Boolean = ThorTheme.materials.infoPanel == InfoPanelStyle.CARD
