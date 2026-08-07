package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ThemeSpec

/**
 * The theme being edited, drawn as a small launcher.
 *
 * The editor already applies whatever it is editing, so the real launcher is the
 * true preview — but only the half of it behind the settings overlay, and only for
 * a theme you have already committed to. This is the other thing a preview is for:
 * seeing what a change does to the *grid*, to the information panel and to the
 * selection cursor while you are still deciding, without leaving the page and
 * coming back.
 *
 * Drawn from the [ThemeSpec] it is handed rather than from the active theme, which
 * is what lets it show a candidate. Everything in it is a real value from that
 * palette — the surface ramp, the accent pair, the cursor, the outline, the text
 * colours, the corner radius and the surface treatment — so a preview that looks
 * wrong means the palette is wrong, not the preview.
 *
 * Not focusable. There is nothing here to press; it is the page's illustration.
 */
@Composable
fun ThemePreviewPanel(
    spec: ThemeSpec,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerRadius)
    // The candidate's own radius, not the active theme's — clamped, because a 36dp
    // radius on a cell this size would round it away to a circle.
    val inner = RoundedCornerShape(spec.cornerRadiusDp.coerceAtMost(MAX_INNER_RADIUS).dp)

    val background = Color(spec.backgroundArgb)
    val surface = Color(spec.surfaceArgb)
    val elevated = Color(spec.surfaceElevatedArgb)
    val primary = Color(spec.primaryArgb)
    val accentEnd = Color(spec.accentEndArgb)
    val cursor = Color(spec.cursorArgb)
    val onSurface = Color(spec.onSurfaceArgb)
    val onSurfaceVariant = Color(spec.onSurfaceVariantArgb)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, Color(spec.outlineArgb), shape),
    ) {
        // The accent wash a theme puts under its wallpaper, at the depth this
        // palette asks for — which is why changing Background depth is visible here.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            primary.copy(alpha = WASH_ALPHA * spec.backgroundDepth * DEPTH_GAIN),
                            Color.Transparent,
                            accentEnd.copy(alpha = WASH_ALPHA * spec.backgroundDepth * DEPTH_GAIN),
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(PANEL_INSET.dp),
            horizontalArrangement = Arrangement.spacedBy(PANEL_INSET.dp),
        ) {
            // Left: the information panel, in this theme's own surface treatment.
            Column(
                modifier = Modifier
                    .weight(INFO_WEIGHT)
                    .fillMaxSize()
                    .miniatureSurface(spec, inner, surface)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Text stands in as bars: real glyphs at this size are noise, and
                // the thing being judged is whether the tones separate.
                Bar(width = 46, height = 5, color = onSurface, shape = inner)
                Bar(width = 30, height = 3, color = onSurfaceVariant, shape = inner)
                Bar(width = 36, height = 3, color = onSurfaceVariant, shape = inner)
                Box(modifier = Modifier.weight(1f))
                // The accent pair, which is what a progress bar or a badge uses.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(inner)
                        .background(Brush.horizontalGradient(listOf(primary, accentEnd))),
                )
            }

            // Right: the grid, with the first cell holding the cursor.
            Column(
                modifier = Modifier.weight(GRID_WEIGHT).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CELL_GAP.dp),
            ) {
                repeat(GRID_ROWS) { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CELL_GAP.dp),
                    ) {
                        repeat(GRID_COLUMNS) { column ->
                            val focused = row == 0 && column == 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .miniatureSurface(spec, inner, elevated)
                                    .then(
                                        if (focused) {
                                            Modifier.border(2.dp, cursor, inner)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
                // The section bar along the bottom edge.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NAV_HEIGHT.dp)
                        .miniatureSurface(spec, inner, elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 3.dp)
                            .clip(inner)
                            .background(cursor),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(width: Int, height: Int, color: Color, shape: Shape) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = height.dp)
            .clip(shape)
            .background(color),
    )
}

/**
 * A panel in an *arbitrary* theme's treatment.
 *
 * Deliberately not `thorSurface`, which reads the treatment from the active theme
 * — the wrong source here, where the whole point is to draw one that is not
 * active. Shadows are scaled down hard: an 8dp shadow under a 20dp cell is a
 * smudge rather than a lift.
 */
private fun Modifier.miniatureSurface(spec: ThemeSpec, shape: Shape, color: Color): Modifier {
    val treatment = spec.surface
    val shadow = (treatment.shadowElevationDp * SHADOW_SCALE).dp

    return this
        .then(
            if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier,
        )
        .clip(shape)
        .background(color.copy(alpha = color.alpha * spec.surfaceAlpha))
        .then(
            if (treatment.specularAlpha > 0f) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = treatment.specularAlpha), Color.Transparent),
                    ),
                )
            } else {
                Modifier
            },
        )
        .then(
            if (treatment.borderWidthDp > 0f && treatment.borderAlpha > 0f) {
                Modifier.border(
                    width = treatment.borderWidthDp.dp,
                    color = Color(spec.outlineArgb).copy(alpha = treatment.borderAlpha),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

private const val PANEL_HEIGHT = 132
private const val PANEL_INSET = 8
private const val INFO_WEIGHT = 1f
private const val GRID_WEIGHT = 1.25f
private const val GRID_ROWS = 2
private const val GRID_COLUMNS = 4
private const val CELL_GAP = 4
private const val NAV_HEIGHT = 14

/** Preview cells are small, so a large theme radius would round them away. */
private const val MAX_INNER_RADIUS = 10

/** A preview panel is a fraction of a real one, and its shadows scale with it. */
private const val SHADOW_SCALE = 0.4f

/**
 * How visible the background wash is here.
 *
 * Exaggerated a little against the real thing: the panel is a fifth of the height
 * of the screen it stands for, and a gradient that reads as a soft lift across a
 * whole display is invisible across a hundred and thirty dp.
 */
private const val WASH_ALPHA = 0.5f
private const val DEPTH_GAIN = 2.5f
