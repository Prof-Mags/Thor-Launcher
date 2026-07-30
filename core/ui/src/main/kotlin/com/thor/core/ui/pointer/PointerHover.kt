package com.thor.core.ui.pointer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.drop

/**
 * Where the pointer is inside this window, or null when it is not in it.
 *
 * A `State` rather than a plain value, and that is the whole reason this works.
 * The cursor moves every frame the stick is held; a value read during composition
 * would recompose every hoverable element on the screen sixty times a second to
 * find out that all but one of them are still not hovered. Handed over as state,
 * the read happens inside a [derivedStateOf] that only notifies anyone when the
 * *answer* changes — so a cursor crossing a grid recomposes two cells, the one it
 * left and the one it entered.
 */
val LocalPointerPosition = staticCompositionLocalOf<State<Offset?>> {
    mutableStateOf(null)
}

/**
 * Haptics for the pointer entering something.
 *
 * A lambda rather than the feedback object, so this file needs to know nothing
 * about how the launcher makes a click. Null where no one has provided it, which
 * is every preview and test.
 */
val LocalPointerHoverFeedback = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Whether the pointer is over one element.
 *
 * The element reports its own bounds and answers the question itself; there is no
 * central registry of what is on screen. A registry would have to be told when
 * anything moved, scrolled or was removed, and the one thing every launcher
 * surface already does correctly is lay itself out.
 */
@Stable
class PointerHoverState internal constructor(
    private val position: State<Offset?>,
) {
    internal var bounds by mutableStateOf(Rect.Zero)

    /**
     * True while the cursor is inside this element.
     *
     * False whenever the pointer is down, because the position is null then — so
     * nothing has to remember to switch the highlight off when the pointer is put
     * away, and no element can be left lit by a cursor that no longer exists.
     */
    val isHovered: Boolean by derivedStateOf {
        val point = position.value ?: return@derivedStateOf false
        !bounds.isEmpty && bounds.contains(point)
    }
}

/**
 * Tracks the pointer against one element, and buzzes when it arrives.
 *
 * Pair with [Modifier.pointerHover], which is what supplies the bounds:
 *
 * ```
 * val hover = rememberPointerHover()
 * Box(Modifier.pointerHover(hover)) { ... }
 * // then treat `hover.isHovered` exactly as the controller cursor is treated
 * ```
 *
 * The haptic fires on entry only. Firing on exit as well would double every
 * movement across a grid, which reads as a rattle rather than as feedback.
 */
@Composable
fun rememberPointerHover(): PointerHoverState {
    val position = LocalPointerPosition.current
    val state = remember(position) { PointerHoverState(position) }

    // Keyed on the element, never on the callback. A caller that rebuilds its
    // lambda each recomposition would otherwise restart this collector — and each
    // restart drops the first value again, which is the very value that says the
    // cursor has just arrived.
    val feedback = rememberUpdatedState(LocalPointerHoverFeedback.current)

    LaunchedEffect(state) {
        snapshotFlow { state.isHovered }
            // The first value is the state of the world, not a change in it. An
            // element composed under a resting cursor would otherwise buzz for
            // having been drawn.
            .drop(1)
            .collect { hovered -> if (hovered) feedback.value?.invoke() }
    }

    return state
}

/** Reports this element's position so [PointerHoverState] can answer for it. */
fun Modifier.pointerHover(state: PointerHoverState): Modifier =
    onGloballyPositioned { coordinates -> state.bounds = coordinates.boundsInWindow() }
