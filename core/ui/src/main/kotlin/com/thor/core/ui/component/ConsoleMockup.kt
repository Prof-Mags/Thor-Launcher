package com.thor.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * Both panels, inside a console body — what a recording renders.
 *
 * The launcher's two windows are on two displays that no capture API can see
 * together, so a recording cannot photograph them. It re-draws them instead: the
 * same composables, from the same state, laid out one above the other in a shell
 * that reads as a dual-screen handheld. The result is a single video of a device
 * that does not exist as one surface anywhere else.
 *
 * @param topAspect the real panel's width/height, so the mock-up is not a guess
 */
@Composable
fun ConsoleMockup(
    topAspect: Float,
    bottomAspect: Float,
    topPanel: @Composable () -> Unit,
    bottomPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BODY_TOP, BODY_BOTTOM),
                ),
            )
            .alwaysRedrawing(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(BODY_WIDTH_FRACTION)
                .padding(vertical = BODY_PADDING.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HINGE_HEIGHT.dp),
        ) {
            Panel(aspect = topAspect, content = topPanel)

            // The hinge: a seam rather than a gap, so the two panels read as one
            // device rather than as two screenshots stacked up.
            Box(
                modifier = Modifier
                    .fillMaxWidth(HINGE_WIDTH_FRACTION)
                    .height(HINGE_BAR.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(HINGE_COLOUR),
            )

            Panel(aspect = bottomAspect, content = bottomPanel)

            Text(
                text = "THOR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = WORDMARK_TRACKING.sp,
                modifier = Modifier.padding(top = WORDMARK_GAP.dp),
            )
        }
    }
}

/** One panel in its bezel, at the real display's shape. */
@Composable
private fun Panel(aspect: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BEZEL.dp)
            .clip(RoundedCornerShape(SCREEN_CORNER.dp))
            .background(Color.Black)
            .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)),
    ) {
        content()
    }
}

/**
 * Keeps the frame pipeline moving.
 *
 * Compose only redraws what changes, and a launcher sitting still changes nothing —
 * which starves a video encoder of frames and produces a file with one picture in it
 * or none at all. This animates a value that is *used* in a draw, at a magnitude no
 * eye resolves, so every vsync produces a frame for the encoder to take.
 */
@Composable
private fun Modifier.alwaysRedrawing(): Modifier {
    val transition = rememberInfiniteTransition(label = "capture")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TICK_PERIOD_MS, easing = LinearEasing),
        ),
        label = "captureTick",
    )

    return this.drawBehind {
        // One almost-transparent pixel band, moved by the tick. Cheap, invisible, and
        // enough to make the frame dirty.
        drawRect(
            color = Color.White.copy(alpha = TICK_ALPHA),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * tick),
            size = androidx.compose.ui.geometry.Size(size.width, 1f),
        )
    }
}

private val BODY_TOP = Color(0xFF15181D)
private val BODY_BOTTOM = Color(0xFF0B0D10)
private val HINGE_COLOUR = Color(0xFF272C34)

private const val BODY_WIDTH_FRACTION = 0.86f
private const val BODY_PADDING = 18
private const val BEZEL = 10
private const val SCREEN_CORNER = 6
private const val HINGE_HEIGHT = 10
private const val HINGE_BAR = 5
private const val HINGE_WIDTH_FRACTION = 0.34f
private const val WORDMARK_GAP = 6
private const val WORDMARK_TRACKING = 6f

/** Guards against a display reporting a nonsensical shape. */
private const val MIN_ASPECT = 0.4f
private const val MAX_ASPECT = 3.5f

private const val TICK_PERIOD_MS = 2_000
private const val TICK_ALPHA = 0.02f
