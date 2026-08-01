package com.thor.core.ui.pointer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.distinctUntilChanged

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
class PointerHoverState internal constructor(position: State<Offset?>) {
    internal var bounds by mutableStateOf(Rect.Zero)

    /**
     * True while the cursor is inside this element.
     *
     * **Derived, not collected**, and the difference is most of the launcher's
     * frame budget. Every hoverable thing on screen has one of these — every grid
     * cell, every poster on every shelf, every source row, every button — and
     * this used to be a plain field written by a `snapshotFlow` collector, which
     * meant one coroutine per element and a full re-evaluation of *all* of them
     * every time the cursor moved a pixel. On a dense grid beside a dozen shelves
     * that is hundreds of coroutines woken per frame to conclude that all but one
     * of them are still not hovered.
     *
     * `derivedStateOf` is what the pointer position was documented as relying on
     * all along: it recomputes lazily, and notifies a reader only when the
     * *answer* changes — so a cursor crossing a grid invalidates two cells rather
     * than every cell.
     *
     * The haptic reads this same value, so a buzz without a highlight remains
     * inexpressible — which was the reason the collector existed.
     *
     * False whenever the pointer is down, because the position is null then — so
     * nothing has to remember to switch the highlight off when the pointer is put
     * away, and no element can be left lit by a cursor that no longer exists.
     */
    private val hovered = derivedStateOf {
        val point = position.value
        point != null && !bounds.isEmpty && bounds.contains(point)
    }

    val isHovered: Boolean get() = hovered.value

    /**
     * Whether this element has seen its first hover answer yet.
     *
     * Plain, not snapshot state: it is read and written only by the effect that
     * fires the haptic, and making it observable would invalidate that effect's
     * own caller every time it was set.
     */
    internal var settled = false
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
    // lambda each recomposition would otherwise restart this effect.
    val feedback = rememberUpdatedState(LocalPointerHoverFeedback.current)

    /*
     * Keyed on the answer, so this coroutine is created only when the element is
     * actually entered or left — not once per element for the whole time it is on
     * screen, waking on every pixel the cursor moves.
     *
     * Reading `isHovered` here also means the caller recomposes exactly when its
     * own highlight changes, which is the recomposition that has to happen
     * anyway.
     */
    val hovered = state.isHovered
    LaunchedEffect(state, hovered) {
        // The first value still has to light the highlight. It merely is not an
        // arrival, so it should not vibrate just because a new page or folder
        // composed beneath a resting pointer.
        if (hovered && !state.settled) feedback.value?.invoke()
        state.settled = true
    }

    return state
}

/**
 * Reports this element's position so [PointerHoverState] can answer for it.
 *
 * Position and size rather than `boundsInWindow`, which intersects with every
 * ancestor's clip. That sounds harmless and is not: an element inside a pager or
 * a scrolling column reports a *truncated* rectangle near the edges, so the
 * highlight died before the cursor reached the element's actual edge.
 */
fun Modifier.pointerHover(state: PointerHoverState): Modifier =
    onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInWindow()
        state.bounds = Rect(origin, coordinates.size.toSize())
    }
