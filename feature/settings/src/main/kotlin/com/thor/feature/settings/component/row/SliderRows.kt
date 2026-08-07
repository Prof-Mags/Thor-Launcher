package com.thor.feature.settings.component.row

import androidx.compose.runtime.Composable
import kotlin.math.ceil
import kotlin.math.floor

/** An integer setting. */
@Composable
fun IntSliderRow(
    title: String,
    subtitle: String? = null,
    value: Int,
    range: IntRange,
    focused: Boolean = false,
    suffix: String = "",
    onValueChange: (Int) -> Unit,
) {
    val step = niceStep(range.last - range.first)
    val clamped = value.coerceIn(range.first, range.last)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = "$clamped$suffix",
        focused = focused,
        canDecrease = clamped > range.first,
        canIncrease = clamped < range.last,
        onWrap = { onValueChange(range.first) },
        onDecrease = {
            onValueChange((snapDown(clamped, step)).coerceAtLeast(range.first))
        },
        onIncrease = {
            onValueChange((snapUp(clamped, step)).coerceAtMost(range.last))
        },
    )
}

/** A fractional setting. */
@Composable
fun SliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    focused: Boolean = false,
    /**
     * Overrides the derived step, for a range the ten-press rule reads wrongly.
     *
     * [niceStep] aims to cross any range in about ten presses, which is right for
     * a setting somebody adjusts and wrong for one they *compose*. A hue runs
     * 0..360, so the rule chose fifty-degree steps — eight reachable colours on a
     * wheel — and an accent chroma of 0..0.24 stepped straight over the entire
     * band that separates a neutral theme from a coloured one. Both are the
     * primary controls of the theme editor and neither could express what it was
     * for. Passed only where that is true; everything else still derives.
     */
    stepOverride: Float? = null,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    onValueChange: (Float) -> Unit,
) {
    val step = stepOverride ?: niceStep(range.endInclusive - range.start)
    val clamped = value.coerceIn(range.start, range.endInclusive)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = valueLabel(clamped),
        focused = focused,
        canDecrease = clamped > range.start + EPSILON,
        canIncrease = clamped < range.endInclusive - EPSILON,
        onWrap = { onValueChange(range.start) },
        onDecrease = { onValueChange(snapDown(clamped, step).coerceAtLeast(range.start)) },
        onIncrease = { onValueChange(snapUp(clamped, step).coerceAtMost(range.endInclusive)) },
    )
}

/**
 * The next value up, on the step's own multiples rather than the current value's.
 *
 * This is what keeps the numbers round. Adding the step to wherever the value
 * happens to be preserves whatever offset it started with, so a setting stored
 * as 128 walked to 628 and 1128 — arithmetically correct and unreadable. Landing
 * on multiples means the value is always something a person would have chosen,
 * and the ends of the range are still reachable because the caller clamps.
 */
private fun snapUp(value: Int, step: Int): Int = (floor(value.toDouble() / step) + 1).toInt() * step

private fun snapDown(value: Int, step: Int): Int = (ceil(value.toDouble() / step) - 1).toInt() * step

private fun snapUp(value: Float, step: Float): Float = (floor(value / step) + 1) * step

private fun snapDown(value: Float, step: Float): Float = (ceil(value / step) - 1) * step

/**
 * A step somebody would have picked, for a range of this size.
 *
 * Chosen from a fixed series rather than computed as `span / 10`, which is where
 * the odd numbers came from: a 128..4096 range divided into ten gives a step of
 * 396. The smallest series member that crosses the range in about ten presses
 * wins, so a short range still steps by one and a long one steps by hundreds —
 * and every value on the way is a multiple of five or ten unless the range is
 * too small for that to mean anything.
 */
private fun niceStep(span: Int): Int =
    INT_STEPS.firstOrNull { span / it <= TARGET_STEPS } ?: INT_STEPS.last()

private fun niceStep(span: Float): Float =
    FLOAT_STEPS.firstOrNull { span / it <= TARGET_STEPS } ?: FLOAT_STEPS.last()

/**
 * Deliberately not a 1-2-5 series.
 *
 * The usual one includes 2, 20 and 200, and a setting stepping 0, 20, 40 is
 * exactly the "odd number" complaint: it is a round number nobody thinks in.
 * Fives and tens are what a person reads off a settings screen.
 */
private val INT_STEPS = intArrayOf(1, 5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)

private val FLOAT_STEPS =
    floatArrayOf(0.01f, 0.05f, 0.1f, 0.25f, 0.5f, 1f, 5f, 10f, 25f, 50f, 100f)

/** How many presses it takes to cross a setting's full range. */
private const val TARGET_STEPS = 10
private const val EPSILON = 0.0001f
