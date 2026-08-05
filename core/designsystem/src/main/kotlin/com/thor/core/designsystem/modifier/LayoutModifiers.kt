package com.thor.core.designsystem.modifier

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fades the top and bottom edges of a scrolling region.
 *
 * Uses `drawWithContent` with a destination-in blend so the fade applies to
 * whatever is drawn, rather than being a gradient overlay that would only work
 * against a known background colour.
 */
fun Modifier.fadingEdges(
    topFraction: Float = 0.06f,
    bottomFraction: Float = 0.10f,
): Modifier = this
    // DstIn needs its own layer to blend against, otherwise it would punch
    // through everything already drawn beneath this composable.
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        if (topFraction > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = size.height * topFraction,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottomFraction > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height * (1f - bottomFraction),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }


/** Applies the one-handed inset so interactive UI sits within thumb reach. */
fun Modifier.oneHandedInset(fraction: Float, alignLeft: Boolean): Modifier =
    if (fraction <= 0f) {
        this
    } else {
        this.padding(
            start = if (alignLeft) 0.dp else (fraction * 100).dp,
            end = if (alignLeft) (fraction * 100).dp else 0.dp,
        )
    }
