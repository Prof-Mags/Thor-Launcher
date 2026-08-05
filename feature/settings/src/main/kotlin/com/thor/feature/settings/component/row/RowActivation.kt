package com.thor.feature.settings.component.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A counter that increments each time Confirm is pressed inside a settings page.
 *
 * Rows cannot be reached through the framework's focus system — the launcher
 * runs its own cursor — so a press has to be broadcast and claimed by whichever
 * row currently holds that cursor. A monotonically increasing tick is used
 * rather than an event stream because it survives recomposition and needs no
 * subscription bookkeeping in every row.
 */
val LocalRowActivation: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { 0 }

/**
 * Runs [onActivate] when Confirm is pressed while this row holds the cursor.
 *
 * Every row observes the tick, but only the focused one acts on it. Unfocused
 * rows still record the value they have seen, so a row that gains the cursor
 * later does not immediately fire on a press that was meant for its neighbour.
 */
@Composable
fun ActivateOnConfirm(focused: Boolean, onActivate: () -> Unit) {
    val tick = LocalRowActivation.current
    var lastSeen by remember { mutableIntStateOf(tick) }

    LaunchedEffect(tick) {
        if (tick != lastSeen) {
            lastSeen = tick
            if (focused) onActivate()
        }
    }
}

/**
 * A running signed count of horizontal steps taken inside a settings page.
 *
 * The same broadcast idea as [LocalRowActivation], for rows that navigate sideways
 * rather than being switched on and off — a gallery of themes, for instance. Rows
 * that want it say so, so left and right keep their page-level meaning everywhere
 * else.
 */
val LocalRowStep: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { 0 }

/** Registers the currently focused row as a consumer of Left/Right commands. */
val LocalHorizontalRowRegistration: ProvidableCompositionLocal<(Boolean) -> Unit> =
    staticCompositionLocalOf { { } }

/**
 * Claims horizontal controller input only while this control owns the settings
 * cursor. The captured registration callback remembers the row index, so losing
 * focus reliably releases the same row even after the cursor has moved.
 */
@Composable
fun RegisterForHorizontalSteps(focused: Boolean) {
    val register = LocalHorizontalRowRegistration.current
    DisposableEffect(focused, register) {
        if (focused) register(true)
        onDispose {
            if (focused) register(false)
        }
    }
}

/**
 * Runs [onStep] with the direction each time Left or Right is pressed while this row
 * holds the cursor.
 *
 * The delta since this row last looked, rather than a single step, so a row cannot
 * lose presses to a recomposition that happens between them.
 */
@Composable
fun StepOnHorizontal(focused: Boolean, onStep: (Int) -> Unit) {
    val steps = LocalRowStep.current
    var lastSeen by remember { mutableIntStateOf(steps) }

    LaunchedEffect(steps) {
        val delta = steps - lastSeen
        lastSeen = steps
        if (delta != 0 && focused) onStep(delta)
    }
}
