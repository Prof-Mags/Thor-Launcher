package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CustomTheme
import com.thor.core.model.Oklch
import com.thor.feature.settings.component.SettingsCard
import kotlin.math.roundToInt

/**
 * A colour picker that works from a thumbstick.
 *
 * The editor's colour used to be four rows — a hex field and three channel
 * sliders — which is a way of *typing* a colour rather than choosing one. That is
 * the wrong shape for this device: the launcher is driven by a D-pad from across a
 * room, and dragging a red channel to find a shade of teal means arithmetic in your
 * head with the answer two rows away.
 *
 * So the spectrum is the control. Left and Right walk the hue while the cursor is
 * on this row, the big swatch is the colour you are choosing, and the strip below
 * shows where on the wheel you are. Every position is a colour somebody might
 * actually pick, because the whole strip is generated at the theme's own chroma
 * rather than at full saturation — what you see on the bar is what the launcher
 * will wear.
 *
 * Touch works too, because the panel is a touchscreen: tapping the strip jumps
 * straight to that hue rather than making you walk there.
 */
@Composable
fun ColorPickerRow(
    title: String,
    subtitle: String?,
    /** Hue in OKLCH degrees, 0..360. */
    hue: Float,
    /** How colourful the swatch and the strip are drawn — the theme's own chroma. */
    chroma: Float,
    focused: Boolean,
    /** Declares this row as one that navigates sideways while it holds the cursor. */
    onTakesHorizontalInput: (Boolean) -> Unit,
    onHueChange: (Float) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shapes = ThorTheme.shapes

    /*
     * The strip, at the theme's own chroma.
     *
     * Generated once per chroma rather than per frame: it is thirty-six colour
     * conversions, each of which does a gamut search, and the cursor crossing the
     * row would otherwise pay for all of them on every step.
     */
    val spectrum = remember(chroma) {
        (0 until SPECTRUM_STOPS).map { stop ->
            val stopHue = stop * 360f / (SPECTRUM_STOPS - 1)
            Color(
                Oklch(CustomTheme.REFERENCE_LIGHTNESS, chroma, stopHue)
                    .toArgb()
                    .toULong()
                    .toLong(),
            )
        }
    }

    val current = remember(hue, chroma) {
        Color(Oklch(CustomTheme.REFERENCE_LIGHTNESS, chroma, hue).toArgb().toULong().toLong())
    }

    // Left and Right belong to this row while it holds the cursor, and go back to
    // the page the moment it leaves — the same contract the theme gallery uses.
    RegisterForHorizontalSteps(focused)
    StepOnHorizontal(focused) { delta ->
        onHueChange((hue + delta * HUE_STEP).mod(360f))
    }

    SettingsCard(focused = focused) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The colour itself, big enough to judge.
                Box(
                    modifier = Modifier
                        .size(SWATCH.dp)
                        .clip(shapes.small)
                        .background(current)
                        .border(1.dp, colors.outline, shapes.small),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (focused) colors.cursor else colors.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "${hue.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.cursor,
                    fontWeight = FontWeight.Medium,
                )
            }

            // The wheel, laid flat. The marker says where on it you are.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STRIP_HEIGHT.dp)
                    .clip(shapes.pill)
                    .background(Brush.horizontalGradient(spectrum))
                    .pointerInput(chroma) {
                        detectTapGestures { offset ->
                            onHueChange(
                                (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f,
                            )
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hue / 360f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    // A light bar over whatever colour it lands on, edged in the
                    // ground colour so it reads against the pale end of the strip
                    // as well as the dark end.
                    Box(
                        modifier = Modifier
                            .width(MARKER_WIDTH.dp)
                            .fillMaxHeight()
                            .background(colors.background)
                            .border(1.dp, colors.onSurface),
                    )
                }
            }
        }
    }
}

/** Enough stops that the gradient reads as continuous rather than as bands. */
private const val SPECTRUM_STOPS = 36

/** Five degrees a press, matching the fine step the hue slider used. */
private const val HUE_STEP = 5f

private const val SWATCH = 44
private const val STRIP_HEIGHT = 22
private const val MARKER_WIDTH = 3
