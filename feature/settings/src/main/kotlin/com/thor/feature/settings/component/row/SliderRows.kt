package com.thor.feature.settings.component.row

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable

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
    // Step size scales with the range so a 128..4096 MB setting is not
    // forty tedious presses wide.
    val step = ((range.last - range.first) / TARGET_STEPS).coerceAtLeast(1)
    val clamped = value.coerceIn(range.first, range.last)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = "$clamped$suffix",
        focused = focused,
        canDecrease = clamped > range.first,
        canIncrease = clamped < range.last,
        onWrap = { onValueChange(range.first) },
        onDecrease = { onValueChange((clamped - step).coerceAtLeast(range.first)) },
        onIncrease = { onValueChange((clamped + step).coerceAtMost(range.last)) },
    )
}

/** A fractional setting, stepped in tenths of its range. */
@Composable
fun SliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    focused: Boolean = false,
    valueLabel: (Float) -> String = { "%.2f".format(it) },
    onValueChange: (Float) -> Unit,
) {
    val span = range.endInclusive - range.start
    val step = span / TARGET_STEPS
    val clamped = value.coerceIn(range.start, range.endInclusive)

    StepperRow(
        title = title,
        subtitle = subtitle,
        value = valueLabel(clamped),
        focused = focused,
        canDecrease = clamped > range.start + EPSILON,
        canIncrease = clamped < range.endInclusive - EPSILON,
        onWrap = { onValueChange(range.start) },
        onDecrease = { onValueChange((clamped - step).coerceAtLeast(range.start)) },
        onIncrease = { onValueChange((clamped + step).coerceAtMost(range.endInclusive)) },
    )
}

/** How many presses it takes to cross a setting's full range. */
private const val TARGET_STEPS = 10
private const val EPSILON = 0.0001f
