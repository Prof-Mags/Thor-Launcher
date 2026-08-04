package com.thor.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.thor.core.designsystem.theme.ThorTheme
import kotlin.math.max

/**
 * Covers the moment the launcher changes shape.
 *
 * Couch mode does not transition so much as *become* — the wallpaper, the grid
 * and the section bar are all replaced in a single frame, which reads as a
 * glitch rather than as a change of mode, and on a television across the room it
 * is the only thing announcing that anything happened. This is drawn over the
 * top while it happens: light sweeps out from the middle, holds long enough to
 * hide the swap, and clears.
 *
 * Deliberately an overlay rather than a transition between two layouts. Cross-
 * fading them means composing both at once — two grids, two wallpapers, two
 * backdrops decoding artwork — for the sake of a third of a second, on the exact
 * frame where the device is already busy rebuilding a screen.
 *
 * [key] is what the effect watches: any change plays the veil once. Passing the
 * mode itself means a switch in either direction plays, which is right — arriving
 * at the television and leaving it are the same event.
 */
@Composable
fun ModeChangeVeil(
    key: Any?,
    modifier: Modifier = Modifier,
    accent: Color = ThorTheme.colors.cursor,
) {
    val motionEnabled = ThorTheme.materials.animationsEnabled
    val progress = remember { Animatable(0f) }

    // The launcher starting is not a mode change. Without this the veil plays
    // over the intro on every cold boot, which already has a sequence of its own.
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        if (!motionEnabled) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SWEEP_MS, easing = LinearOutSlowInEasing))
        progress.animateTo(0f, tween(CLEAR_MS, easing = LinearOutSlowInEasing))
    }

    val value = progress.value
    if (value <= 0f) return

    /*
     * Painted rather than filled, and with nothing clickable on it.
     *
     * The veil covers the whole screen for half a second while the controller is
     * still live; a node that took part in hit testing would swallow whatever
     * the user pressed during it.
     */
    Box(
        modifier = modifier.fillMaxSize().drawBehind {
            val centre = Offset(size.width / 2f, size.height / 2f)
            // Reaches past the corners at full extent, so the wash covers the
            // rectangle instead of leaving four lit triangles.
            val reach = max(size.width, size.height) * (0.25f + value * 0.95f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.5f * value),
                        accent.copy(alpha = 0.26f * value),
                        Color.Black.copy(alpha = 0.85f * value),
                    ),
                    center = centre,
                    radius = reach.coerceAtLeast(1f),
                ),
            )
        },
    )
}

/** Out fast enough to cover the swap, back slowly enough to feel deliberate. */
private const val SWEEP_MS = 190
private const val CLEAR_MS = 540
