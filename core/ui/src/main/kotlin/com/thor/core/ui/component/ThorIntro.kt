package com.thor.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * The launcher's cold-start intro.
 *
 * A pure function of [progress], with no animation state of its own. That is
 * deliberate: the launcher owns two windows on two panels, and the only way to have
 * them animate as one thing rather than as two coincidentally similar things is for
 * a single driver to hand the same number to both.
 *
 * Plays once per process. It is not a loading screen — the launcher is already
 * composed behind it — so it is skippable on any button or tap, and short enough
 * that reaching for the button is a choice rather than a rescue.
 *
 * @param progress 0 at the first frame, 1 when the launcher is fully revealed
 * @param motion false under reduced-motion or performance mode, which collapses the
 *   whole thing to a fade — the mark still appears, it simply does not travel
 * @param onSkip the surface was tapped
 */
@Composable
fun ThorIntro(
    progress: Float,
    motion: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    // The overlay fades out over the last fifth, so the launcher is revealed rather
    // than swapped in.
    val overlayAlpha = 1f - span(progress, REVEAL_FROM, 1f)
    if (overlayAlpha <= 0f) return

    val lineProgress = span(progress, 0f, LINE_TO)
    val markProgress = span(progress, MARK_FROM, MARK_TO)
    val wordProgress = span(progress, WORD_FROM, WORD_TO)

    // Under reduced motion every element is placed at its final geometry and only
    // opacity moves.
    val markScale = if (motion) MARK_SCALE_FROM + (1f - MARK_SCALE_FROM) * markProgress else 1f
    val lineWidth = if (motion) lineProgress else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(colors.background)
            // Owns the panel's touches while it is up: a tap is a skip, and nothing
            // beneath it should be reachable through it.
            .clickable(onClick = onSkip)
            .drawBehind {
                /*
                 * A hairline opening outwards from the centre, under everything else.
                 * It is what makes the mark feel like it arrives rather than simply
                 * being there — the eye is already at the centre when it lands.
                 */
                val half = size.width * LINE_WIDTH_FRACTION * 0.5f * lineWidth
                if (half > 0f) {
                    val thickness = LINE_THICKNESS_PX
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.primary,
                                colors.accentEnd,
                                Color.Transparent,
                            ),
                            startX = size.width / 2f - half,
                            endX = size.width / 2f + half,
                        ),
                        topLeft = Offset(size.width / 2f - half, size.height / 2f - thickness / 2f),
                        size = Size(half * 2f, thickness),
                        alpha = 1f - span(progress, GLOW_FADE_FROM, 1f),
                    )
                }

                // A bloom behind the mark, so the accent reads as light rather than
                // as a flat shape on a flat field.
                if (markProgress > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.glow.copy(alpha = GLOW_ALPHA * markProgress),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * GLOW_RADIUS_FRACTION,
                        ),
                        radius = size.minDimension * GLOW_RADIUS_FRACTION,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The same bolt as the launcher icon, drawn rather than loaded so it can
            // take the current theme's accent pair instead of the icon's fixed blue.
            Box(
                modifier = Modifier
                    .size(MARK_SIZE.dp)
                    .graphicsLayer {
                        alpha = markProgress
                        scaleX = markScale
                        scaleY = markScale
                    }
                    .drawBehind {
                        drawPath(
                            path = boltPath(size.minDimension),
                            brush = Brush.linearGradient(
                                colors = colors.accentStops,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                            ),
                        )
                    },
            )

            Box(modifier = Modifier.height(MARK_GAP.dp))

            Text(
                text = "THOR",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = colors.onBackground,
                // Tracking closes as it fades in, which is the whole trick: the word
                // settles instead of appearing.
                letterSpacing = if (motion) {
                    (TRACKING_FROM + (TRACKING_TO - TRACKING_FROM) * wordProgress).sp
                } else {
                    TRACKING_TO.sp
                },
                modifier = Modifier.graphicsLayer { alpha = wordProgress },
            )
        }
    }
}

/**
 * The THOR bolt, in the same 108-unit space as `ic_thor_logo`.
 *
 * Built as a path rather than loaded as a vector so the mark can be filled with the
 * live theme gradient — and so `:core:ui` needs no drawable of its own.
 */
private fun boltPath(size: Float): Path {
    val s = size / VIEWPORT
    return Path().apply {
        moveTo(60f * s, 20f * s)
        lineTo(34f * s, 58f * s)
        lineTo(50f * s, 58f * s)
        lineTo(46f * s, 88f * s)
        lineTo(74f * s, 48f * s)
        lineTo(57f * s, 48f * s)
        close()
    }
}

/** Progress within one stage of the sequence, clamped to 0..1 outside it. */
private fun span(progress: Float, from: Float, to: Float): Float {
    if (to <= from) return if (progress >= to) 1f else 0f
    return ((progress - from) / (to - from)).coerceIn(0f, 1f)
}

/** How far through the whole sequence each element runs. */
private const val LINE_TO = 0.34f
private const val MARK_FROM = 0.20f
private const val MARK_TO = 0.62f
private const val WORD_FROM = 0.44f
private const val WORD_TO = 0.82f
private const val GLOW_FADE_FROM = 0.62f
private const val REVEAL_FROM = 0.80f

private const val VIEWPORT = 108f
private const val MARK_SIZE = 92
private const val MARK_GAP = 20
private const val MARK_SCALE_FROM = 0.82f
private const val LINE_WIDTH_FRACTION = 0.66f
private const val LINE_THICKNESS_PX = 3f
private const val GLOW_ALPHA = 0.5f
private const val GLOW_RADIUS_FRACTION = 0.45f
private const val TRACKING_FROM = 22f
private const val TRACKING_TO = 9f
