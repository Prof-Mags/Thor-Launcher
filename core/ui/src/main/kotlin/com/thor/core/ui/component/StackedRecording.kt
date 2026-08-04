package com.thor.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The two panels, stacked, and nothing else.
 *
 * A recording of a dual-screen device is a recording of two screens; the drawn
 * console that used to hold them was decoration wrapped around the thing being
 * recorded, and it cost picture — the shell is wider than either panel, so every
 * pixel spent on plastic was a pixel not spent on the screen.
 *
 * Each panel keeps its own shape and its own share of the width. The bottom is
 * narrower than the top on this hardware and stays that way, centred beneath it,
 * because a recording that stretched both to the same width would be showing
 * something the device does not look like.
 */
@Composable
fun StackedPanels(
    topAspect: Float,
    bottomAspect: Float,
    /** Real pixel widths, which set the panels' sizes relative to one another. */
    topWidthPx: Int,
    bottomWidthPx: Int,
    /** Real dp widths, so each panel lays out as its own screen. See [Panel]. */
    topWidthDp: Float,
    bottomWidthDp: Float,
    topPanel: @Composable () -> Unit,
    bottomPanel: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widest = maxOf(topWidthPx, bottomWidthPx, 1)

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Panel(
            aspect = topAspect,
            widthFraction = topWidthPx.toFloat() / widest,
            realWidthDp = topWidthDp,
        ) {
            topPanel()
        }
        Panel(
            aspect = bottomAspect,
            widthFraction = bottomWidthPx.toFloat() / widest,
            realWidthDp = bottomWidthDp,
        ) {
            bottomPanel(Modifier.fillMaxSize())
        }
    }
}

/**
 * One screen, laid out as itself and merely drawn at another size.
 *
 * The density override is the whole trick. Compose decides a layout in dp, which
 * is pixels over density — so a panel drawn into a frame narrower than the real
 * screen would otherwise lay out as a *smaller* screen and show correspondingly
 * less: fewer grid columns, a shorter list, a different design. Overriding the
 * density so the drawn width still measures the real screen's dp means the
 * layout is identical and only the rasterisation is smaller, which is what a
 * recording is supposed to be.
 */
@Composable
private fun Panel(
    aspect: Float,
    widthFraction: Float,
    realWidthDp: Float,
    content: @Composable () -> Unit,
) {
    val outer = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(widthFraction.coerceIn(MIN_FRACTION, 1f))
            .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT))
            .background(Color.Black),
    ) {
        val drawnWidthPx = constraints.maxWidth.toFloat()
        val density = if (realWidthDp > 0f) drawnWidthPx / realWidthDp else outer.density

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.coerceAtLeast(MIN_DENSITY),
                fontScale = outer.fontScale,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * The pixel size of a recording.
 */
data class RecordingFrame(val width: Int, val height: Int)

/**
 * The video's size, from the panels themselves.
 *
 * Width is the wider panel's own pixels, so that screen is recorded at native
 * resolution and the narrower one sits inside it at its true proportion. Height
 * is simply the two stacked — there is no chrome to allow for any more.
 *
 * Brought inside [ceiling] here rather than left for the recorder to clamp: the
 * recorder scales correctly, but a frame that arrives already within the
 * encoder's range is one fewer resampling of the picture. Both dimensions are
 * rounded to even numbers, which H.264 requires and which a proportional scale
 * will not give on its own.
 */
fun stackedFrameSize(
    topWidthPx: Int,
    topAspect: Float,
    bottomWidthPx: Int,
    bottomAspect: Float,
    ceiling: Int = STACK_CEILING,
): RecordingFrame {
    val top = topAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
    val bottom = bottomAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
    val widest = maxOf(topWidthPx, bottomWidthPx, 1)

    val topHeight = topWidthPx / top
    val bottomHeight = bottomWidthPx / bottom
    val wantedHeight = topHeight + bottomHeight

    // One scale for both, so the stack keeps its shape whichever dimension is
    // the one over the limit.
    val scale = minOf(1f, ceiling / widest.toFloat(), ceiling / wantedHeight)

    return RecordingFrame(
        width = even(widest * scale),
        height = even(wantedHeight * scale),
    )
}

/** Even, and never zero: an encoder refuses both. */
private fun even(value: Float): Int = (value.toInt() / 2 * 2).coerceAtLeast(2)

private const val MIN_FRACTION = 0.05f
private const val MIN_ASPECT = 0.2f
private const val MAX_ASPECT = 5f
private const val MIN_DENSITY = 0.5f

/** The largest edge the device's encoder is asked for. */
private const val STACK_CEILING = 2160
