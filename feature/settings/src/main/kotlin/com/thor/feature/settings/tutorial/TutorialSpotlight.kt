package com.thor.feature.settings.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * Which part of the device a page is about.
 *
 * The walkthrough is read away from the thing it describes, so "the nav bar" and
 * "the info panel" are just words unless something shows *where*. This names the
 * region so the illustration can light one and dim the rest.
 */
enum class TutorialFocus {
    /** Both panels together, for pages about the device as a whole. */
    BOTH,
    TOP_PANEL,
    GRID,
    NAV_BAR,
    KEYBOARD,
    CONTROLS,
    POINTER,

    /** Nothing in particular — the page is not about a place. */
    NONE,
}

/**
 * A diagram of the two panels with one region lit.
 *
 * Deliberately a drawing rather than a cut-out over the real launcher. A true
 * coach mark has to know where a view actually is, which means the walkthrough
 * would have to open every surface it describes and hold both panels in a known
 * state to measure them — including twenty-five settings pages it cannot see
 * from here. A diagram makes the same point about *where* without any of that,
 * and it is the same drawing whichever panel the grid happens to be on.
 *
 * The lit region breathes rather than sitting still, because a static highlight
 * on a static diagram reads as part of the picture instead of as the answer to
 * "which bit".
 */
@Composable
fun TutorialSpotlight(
    focus: TutorialFocus,
    modifier: Modifier = Modifier,
) {
    if (focus == TutorialFocus.NONE) return

    val colors = ThorTheme.colors
    val transition = rememberInfiniteTransition(label = "spotlight")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spotlight-pulse",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(DIAGRAM_HEIGHT.dp),
    ) {
        val gap = size.height * PANEL_GAP_FRACTION
        val panelHeight = (size.height - gap) / 2f
        val panelWidth = size.width
        val radius = CornerRadius(size.height * PANEL_RADIUS_FRACTION)

        val top = Rect(Offset(0f, 0f), Size(panelWidth, panelHeight))
        val bottom = Rect(Offset(0f, panelHeight + gap), Size(panelWidth, panelHeight))

        // Both panels first, dim, so the lit one is lit *against* something.
        drawPanel(top, radius, colors.outline.copy(alpha = DIM_ALPHA))
        drawPanel(bottom, radius, colors.outline.copy(alpha = DIM_ALPHA))

        val lit = when (focus) {
            TutorialFocus.BOTH, TutorialFocus.CONTROLS ->
                Rect(Offset(0f, 0f), Size(panelWidth, size.height))

            TutorialFocus.TOP_PANEL -> top

            TutorialFocus.GRID -> bottom

            // The strip along the bottom edge of the grid panel.
            TutorialFocus.NAV_BAR -> Rect(
                Offset(0f, bottom.bottom - panelHeight * NAV_BAR_FRACTION),
                Size(panelWidth, panelHeight * NAV_BAR_FRACTION),
            )

            // Square across the lower half of the panel being held.
            TutorialFocus.KEYBOARD -> Rect(
                Offset(0f, bottom.top + panelHeight * (1f - KEYBOARD_FRACTION)),
                Size(panelWidth, panelHeight * KEYBOARD_FRACTION),
            )

            // A point rather than a region: the cursor is somewhere, not an area.
            TutorialFocus.POINTER -> Rect(
                Offset(
                    panelWidth * POINTER_X_FRACTION,
                    bottom.top + panelHeight * POINTER_Y_FRACTION,
                ),
                Size(panelHeight * POINTER_SIZE_FRACTION, panelHeight * POINTER_SIZE_FRACTION),
            )

            TutorialFocus.NONE -> return@Canvas
        }

        val glow = LIT_ALPHA_LOW + (LIT_ALPHA_HIGH - LIT_ALPHA_LOW) * pulse
        drawRoundRect(
            color = colors.cursor.copy(alpha = glow * FILL_SCALE),
            topLeft = lit.topLeft,
            size = lit.size,
            cornerRadius = radius,
        )
        drawRoundRect(
            color = colors.cursor.copy(alpha = glow),
            topLeft = lit.topLeft,
            size = lit.size,
            cornerRadius = radius,
            style = Stroke(width = LIT_STROKE_DP.dp.toPx()),
        )
    }
}

private fun DrawScope.drawPanel(
    rect: Rect,
    radius: CornerRadius,
    color: androidx.compose.ui.graphics.Color,
) {
    drawRoundRect(
        color = color,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = radius,
        style = Stroke(width = PANEL_STROKE_DP.dp.toPx()),
    )
}

private const val DIAGRAM_HEIGHT = 92
private const val PANEL_GAP_FRACTION = 0.08f
private const val PANEL_RADIUS_FRACTION = 0.06f
private const val PANEL_STROKE_DP = 1.5f
private const val LIT_STROKE_DP = 2f
private const val DIM_ALPHA = 0.35f
private const val LIT_ALPHA_LOW = 0.45f
private const val LIT_ALPHA_HIGH = 1f

/** The fill is a wash behind the outline, not a block: the outline is the answer. */
private const val FILL_SCALE = 0.14f

private const val NAV_BAR_FRACTION = 0.22f
private const val KEYBOARD_FRACTION = 0.55f
private const val POINTER_X_FRACTION = 0.58f
private const val POINTER_Y_FRACTION = 0.34f
private const val POINTER_SIZE_FRACTION = 0.22f

private const val PULSE_MS = 1_100
