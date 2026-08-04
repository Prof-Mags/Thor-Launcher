package com.thor.feature.home.couch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.blend

/**
 * Couch mode's own background.
 *
 * The selected game's artwork used to fill this, blurred and darkened behind
 * everything. On a handheld panel that reads as atmosphere; across a room it
 * does not. Three things went wrong with it at television size: the picture
 * changed every time the cursor moved, which is a full-screen crossfade running
 * constantly while somebody is only browsing; text sat over whatever part of the
 * artwork happened to be behind it, so contrast was a different problem for
 * every game in the library; and the largest surface on the screen was spent
 * repeating information the spotlight panel already shows in full.
 *
 * So the background is drawn rather than loaded. It costs no decode, no memory
 * and no network, it never changes while the cursor moves, and it is the same
 * every time — which is what lets the panels above it be designed against a
 * known contrast instead of an arbitrary one.
 *
 * It is not a fixed picture either. The ridges are tinted by [accent], which is
 * the focused system's colour, so the room still shifts as you cross from one
 * console to another — a slow change in the wash rather than a new photograph.
 * Everything is derived from the theme, so it follows a theme change and a
 * light theme without a second asset.
 */
@Composable
internal fun CouchWallpaper(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val base = colors.background
    // Toward the accent rather than to it: a saturated sky behind a dark
    // interface is the thing that makes a launcher look like a demo.
    val sky = base.blend(accent, SKY_TINT)
    val ridgeNear = base.blend(accent, RIDGE_NEAR_TINT)
    val ridgeMid = base.blend(accent, RIDGE_MID_TINT)
    val ridgeFar = base.blend(accent, RIDGE_FAR_TINT)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to sky,
                    HORIZON to base.blend(accent, HORIZON_TINT),
                    1f to base,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Far to near, so the nearer ridge covers the one behind it.
            drawRidge(colour = ridgeFar, crest = 0.62f, amplitude = 0.05f, phase = 0.15f)
            drawRidge(colour = ridgeMid, crest = 0.72f, amplitude = 0.07f, phase = 0.55f)
            drawRidge(colour = ridgeNear, crest = 0.83f, amplitude = 0.06f, phase = 0.90f)
            drawVignette(base)
        }
    }
}

/**
 * One ridge line across the full width.
 *
 * Built from a handful of cubic segments rather than from noise, because the
 * shape has to be identical on every draw: a background that reshuffled itself
 * on rotation, on a theme change or on a scale change would be movement nobody
 * asked for behind text they are reading.
 *
 * @param crest how far down the panel the ridge's highest point sits, 0 at the
 *   top and 1 at the bottom
 * @param amplitude how tall the peaks are, as a fraction of the panel's height
 * @param phase shifts the peaks sideways so the three layers do not line up
 */
private fun DrawScope.drawRidge(
    colour: Color,
    crest: Float,
    amplitude: Float,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    val baseY = h * crest
    val peak = h * amplitude

    val path = Path().apply {
        moveTo(0f, baseY + peak * heightAt(phase))
        val steps = RIDGE_STEPS
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val x = w * t
            val y = baseY + peak * heightAt(phase + t * RIDGE_FREQUENCY)
            // Cubic rather than straight: a ridge made of line segments reads as
            // a chart, which is exactly the wrong association behind a library.
            val previousX = w * (step - 1).toFloat() / steps
            val previousY = baseY + peak * heightAt(phase + ((step - 1f) / steps) * RIDGE_FREQUENCY)
            cubicTo(
                previousX + (x - previousX) / 2f, previousY,
                previousX + (x - previousX) / 2f, y,
                x, y,
            )
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path = path, color = colour)
}

/**
 * A deterministic ridge profile.
 *
 * Two sine terms at unrelated frequencies, which gives peaks that do not repeat
 * on any obvious interval without needing a random source — and a random source
 * is the thing that would make this different on every composition.
 */
private fun heightAt(t: Float): Float {
    val a = kotlin.math.sin(t * TWO_PI)
    val b = kotlin.math.sin(t * TWO_PI * SECOND_HARMONIC + HARMONIC_OFFSET)
    return -(a * 0.65f + b * 0.35f)
}

/** Darkens the corners so the panels above sit on an even field. */
private fun DrawScope.drawVignette(base: Color) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, base.copy(alpha = VIGNETTE_ALPHA)),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = maxOf(size.width, size.height) * VIGNETTE_RADIUS,
        ),
        size = Size(size.width, size.height),
    )
}

private const val TWO_PI = (Math.PI * 2).toFloat()
private const val SECOND_HARMONIC = 2.7f
private const val HARMONIC_OFFSET = 1.3f

/** Enough segments for a smooth crest at 4K, cheap enough to redraw at any size. */
private const val RIDGE_STEPS = 24

/** How many full waves cross the panel. Below two it reads as a single hill. */
private const val RIDGE_FREQUENCY = 2.3f

/** Where the wash stops being sky and starts being ground. */
private const val HORIZON = 0.55f

private const val SKY_TINT = 0.10f
private const val HORIZON_TINT = 0.05f
private const val RIDGE_FAR_TINT = 0.07f
private const val RIDGE_MID_TINT = 0.05f
private const val RIDGE_NEAR_TINT = 0.03f

private const val VIGNETTE_ALPHA = 0.55f
private const val VIGNETTE_RADIUS = 0.85f
