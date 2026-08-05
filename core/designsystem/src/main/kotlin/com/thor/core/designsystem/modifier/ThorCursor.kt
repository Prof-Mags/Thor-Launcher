package com.thor.core.designsystem.modifier

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Outline
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import com.thor.core.designsystem.theme.ThorTheme
import androidx.compose.runtime.getValue
import com.thor.core.model.CursorAnimation
import androidx.compose.runtime.getValue
import com.thor.core.model.CursorStyle

/**
 * Draws THOR's selection cursor around a cell.
 *
 * The cursor is painted as a modifier rather than as a sibling composable so it
 * can be attached to whatever currently holds focus — grid cell, dock slot,
 * settings row — without each of those needing to lay out an extra element.
 *
 * @param focused whether this element currently holds the cursor
 * @param style the shape the cursor takes
 * @param animation the idle animation applied while focused
 * @param cornerRadius corner radius to match the host element
 */
@Composable
fun Modifier.thorCursor(
    focused: Boolean,
    cornerRadius: Dp = ThorTheme.dimens.cornerRadius,
    /**
     * The host's own shape, traced exactly when given.
     *
     * [cornerRadius] can only describe a rounded rectangle, so a square cell got a
     * rounded cursor, a circular one got a rounded square, and a hexagonal one got
     * nothing like itself. Passing the same `Shape` the host is clipped to makes the
     * cursor follow it — including the shapes no radius can express — because the
     * outline is asked of the shape itself at draw time, when the size is known.
     */
    shape: Shape? = null,
    // Defaulted from the theme so the user's cursor preferences reach every
    // call site without each one having to remember to forward them.
    style: CursorStyle = ThorTheme.cursor.style,
    animation: CursorAnimation = ThorTheme.cursor.animation,
    glowIntensity: Float = ThorTheme.cursor.glowIntensity,
): Modifier {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motionEnabled = ThorTheme.materials.animationsEnabled

    if (!focused) return this

    // A grid can have dozens of cells. Giving every unfocused one an infinite
    // transition keeps the frame clock busy even though none of them can draw a
    // cursor. Create it only for the single focused element, and dispose it as
    // soon as focus moves on.
    val pulse = if (motionEnabled && animation != CursorAnimation.NONE) {
        val transition = rememberInfiniteTransition(label = "cursor")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = when (animation) {
                        CursorAnimation.PULSE -> 900
                        CursorAnimation.SHIMMER -> 1600
                        CursorAnimation.ROTATE -> 2400
                        else -> 1800
                    },
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cursorPulse",
        )
        value
    } else {
        0f
    }

    val strokeWidth = dimens.cursorThickness
    val glowAlpha = when (animation) {
        CursorAnimation.NONE -> glowIntensity
        CursorAnimation.BREATHE -> glowIntensity * (0.62f + 0.38f * pulse)
        CursorAnimation.PULSE -> glowIntensity * (0.35f + 0.65f * pulse)
        CursorAnimation.SHIMMER -> glowIntensity * (0.5f + 0.5f * pulse)
        CursorAnimation.ROTATE -> glowIntensity
    }

    /*
     * Drawn *over* the element's content, not behind it.
     *
     * `drawBehind` put the whole indicator underneath everything the call site
     * drew afterwards. On a settings row, which paints nothing over it, that
     * looked fine — but a grid cell follows this modifier with an opaque
     * `background` and then fills the box with artwork, so the ring was painted
     * and then completely covered. The cursor appeared to work on the info screen
     * and not on the grid, purely as an artefact of modifier order.
     *
     * Content is composited first and the indicator laid on top, so no call site
     * can hide it by ordering. The soft parts still go underneath, so a glow never
     * washes over a label or a face.
     */
    return this.drawWithContent {
        val stroke = strokeWidth.toPx()

        // Everything is drawn *inside* the element's own bounds. An earlier
        // version bloomed the glow outwards, which two things then broke: any
        // ancestor `clip` cut it off, and on a row whose content runs to the
        // edge the ring landed directly on the text.
        val inset = stroke / 2f
        val innerSize = Size(
            width = (size.width - stroke).coerceAtLeast(0f),
            height = (size.height - stroke).coerceAtLeast(0f),
        )
        val innerTopLeft = Offset(inset, inset)

        /*
         * The shape being traced. Falling back to a rounded rectangle built from
         * [cornerRadius] keeps every existing call site — rows, list items, tiles —
         * drawing exactly what it drew before.
         */
        val hostShape = shape ?: RoundedCornerShape(cornerRadius)

        // Asked of the shape per inset rather than computed once: a percentage-based
        // corner (a circle is one) resolves differently at each size, so the ring and
        // the bloom inside it each need their own outline.
        fun outlineOf(pad: Float): Outline = hostShape.createOutline(
            size = Size(
                width = (size.width - pad * 2f).coerceAtLeast(0f),
                height = (size.height - pad * 2f).coerceAtLeast(0f),
            ),
            layoutDirection = layoutDirection,
            density = this,
        )

        val innerOutline = outlineOf(inset)

        // The accent pair, sweeping across the element. A single flat colour is
        // what made the cursor read as a plain border; the gradient catches the
        // eye along its length without needing a thicker stroke.
        val accentBrush = Brush.linearGradient(
            colors = colors.accentStops,
            start = innerTopLeft,
            end = Offset(innerTopLeft.x + innerSize.width, innerTopLeft.y + innerSize.height),
        )

        // Soft washes go under the content so they tint the plate, not the
        // artwork or the label sitting on it.
        when (style) {
            CursorStyle.FILL -> translate(inset, inset) {
                drawOutline(
                    outline = innerOutline,
                    color = colors.cursor.copy(alpha = 0.20f + 0.10f * pulse),
                )
            }

            CursorStyle.RING -> {
                // The bloom sits just inside the ring, so the whole cursor stays
                // within the element it marks.
                val bloomInset = stroke * 1.5f
                translate(bloomInset, bloomInset) {
                    drawOutline(
                        outline = outlineOf(bloomInset),
                        color = colors.glow.copy(alpha = colors.glow.alpha * glowAlpha),
                        style = Stroke(width = stroke * 2f),
                    )
                }
            }

            CursorStyle.SPOTLIGHT -> translate(inset, inset) {
                drawOutline(
                    outline = innerOutline,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.cursor.copy(alpha = 0.30f * (0.7f + 0.3f * pulse)),
                            Color.Transparent,
                        ),
                        center = Offset(innerSize.width / 2f, innerSize.height / 2f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }

            CursorStyle.CORNERS, CursorStyle.UNDERLINE -> Unit
        }

        drawContent()

        // Outlines go over it, so nothing a call site draws can bury them.
        when (style) {
            CursorStyle.FILL, CursorStyle.RING -> translate(inset, inset) {
                drawOutline(
                    outline = innerOutline,
                    brush = accentBrush,
                    style = Stroke(width = stroke),
                )
            }

            CursorStyle.CORNERS -> {
                val len = size.minDimension * 0.24f
                val c = colors.cursor
                // Four L-shaped brackets, Xbox/Steam reticle style.
                listOf(
                    Offset(0f, 0f) to listOf(Offset(len, 0f), Offset(0f, len)),
                    Offset(size.width, 0f) to listOf(Offset(size.width - len, 0f), Offset(size.width, len)),
                    Offset(0f, size.height) to listOf(Offset(len, size.height), Offset(0f, size.height - len)),
                    Offset(size.width, size.height) to
                        listOf(Offset(size.width - len, size.height), Offset(size.width, size.height - len)),
                ).forEach { (corner, arms) ->
                    arms.forEach { arm -> drawLine(c, corner, arm, strokeWidth = stroke) }
                }
            }

            CursorStyle.UNDERLINE -> {
                // Sits on the bottom edge, not below it, so it is never clipped.
                val y = size.height - stroke
                drawLine(
                    color = colors.cursor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke * 1.5f,
                )
            }

            CursorStyle.SPOTLIGHT -> translate(inset, inset) {
                drawOutline(
                    outline = innerOutline,
                    color = colors.cursor.copy(alpha = 0.85f),
                    style = Stroke(width = stroke * 0.75f),
                )
            }
        }
    }
}
