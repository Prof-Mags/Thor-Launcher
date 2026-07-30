package com.thor.launcher.mouse

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.thor.core.common.log.ThorLog
import com.thor.core.input.MouseController
import com.thor.core.input.PointerPosition
import com.thor.core.model.MouseAction

/**
 * The pointer, inside THOR's own windows.
 *
 * This needs no permission, and that is the point. The accessibility service
 * exists to carry the pointer *out* of the launcher; within it, THOR already sees
 * every button, already knows where the cursor is, and already owns the window —
 * so it can draw the cursor itself and click by dispatching a touch into its own
 * view hierarchy. An app injecting events into its own windows is ordinary; it is
 * only other apps' windows that need `dispatchGesture` and the permission that
 * comes with it.
 *
 * Placed once per window. Each instance draws only while the pointer is on *its*
 * display, which is how the cursor crosses between panels: the two layers hand it
 * back and forth, and neither has to know the other exists.
 *
 * @param displayId the panel this instance is drawn on
 */
@Composable
fun PointerLayer(
    mouse: MouseController,
    displayId: Int?,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    /*
     * The cursor's position is deliberately *not* collected into composition.
     *
     * It changes on every frame the stick is held, and a value read during
     * composition re-runs composition and layout for the whole layer each time it
     * does — for a shape that has only moved. Held in a plain state that nothing
     * but the draw lambda reads, the frame becomes a redraw of one canvas, which
     * is what a moving cursor should cost. This was most of the stutter.
     */
    val cursor = remember { mutableStateOf<PointerPosition?>(null) }

    LaunchedEffect(mouse, displayId) {
        mouse.state.collect { state ->
            cursor.value = state.position
                ?.takeIf { state.active && it.displayId == displayId }
        }
    }

    /*
     * Actions are performed by whichever layer the pointer is currently over.
     *
     * Keyed on the display so only one window ever acts on a request — without
     * that, both panels would dispatch the same tap and a click would land twice.
     */
    LaunchedEffect(mouse, displayId) {
        mouse.actions.collect { request ->
            if (request.position.displayId != displayId) return@collect
            when (request.action) {
                MouseAction.PRIMARY_CLICK -> view.dispatchTap(request.position, TAP_MS)
                MouseAction.SECONDARY_CLICK -> view.dispatchTap(request.position, LONG_PRESS_MS)
                MouseAction.SCROLL_UP -> view.dispatchScroll(request.position, -SCROLL_PX)
                MouseAction.SCROLL_DOWN -> view.dispatchScroll(request.position, SCROLL_PX)
                // The shell owns the rest: it holds the keyboard and the back
                // stack, neither of which a touch into this window can express.
                else -> Unit
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Cursor(position = cursor)
    }
}

/**
 * Sends a tap into this window's own view hierarchy.
 *
 * `dispatchTouchEvent` on the root view is the same path a finger takes, so
 * everything already built for touch — grid cells, settings rows, the keyboard —
 * responds without knowing a pointer was involved. Nothing here is injected into
 * another app; an app may always dispatch into itself.
 *
 * Both events carry the same down-time, because a view that sees an UP whose
 * down-time does not match a DOWN it saw will discard the pair.
 */
private fun View.dispatchTap(position: PointerPosition, holdMs: Long) {
    val root = rootView ?: return
    val downTime = SystemClock.uptimeMillis()

    fun send(action: Int, at: Long) {
        val event = MotionEvent.obtain(downTime, at, action, position.x, position.y, 0)
        runCatching { root.dispatchTouchEvent(event) }
            .onFailure { error -> ThorLog.w("Pointer", "Tap not delivered", error) }
        event.recycle()
    }

    send(MotionEvent.ACTION_DOWN, downTime)
    send(MotionEvent.ACTION_UP, downTime + holdMs.coerceAtLeast(TAP_MS))
}

/**
 * Drags this window's own hierarchy, so the pointer can scroll a list.
 *
 * A real drag rather than a wheel event: THOR's lists are Compose scrollables,
 * which follow a touch and ignore an injected axis. The intermediate moves matter
 * — a scrollable measures velocity across them, and a down-then-up with nothing
 * between reads as a tap on whatever the cursor was over.
 */
private fun View.dispatchScroll(position: PointerPosition, distancePx: Float) {
    val root = rootView ?: return
    val downTime = SystemClock.uptimeMillis()

    fun send(action: Int, at: Long, y: Float) {
        val event = MotionEvent.obtain(downTime, at, action, position.x, y, 0)
        runCatching { root.dispatchTouchEvent(event) }
            .onFailure { error -> ThorLog.w("Pointer", "Scroll not delivered", error) }
        event.recycle()
    }

    send(MotionEvent.ACTION_DOWN, downTime, position.y)
    for (step in 1..SCROLL_STEPS) {
        val fraction = step.toFloat() / SCROLL_STEPS
        send(
            MotionEvent.ACTION_MOVE,
            downTime + (SCROLL_MS * fraction).toLong(),
            position.y - distancePx * fraction,
        )
    }
    send(MotionEvent.ACTION_UP, downTime + SCROLL_MS, position.y - distancePx)
}

/**
 * The cursor.
 *
 * Drawn rather than an asset so it carries its own outline: a light arrow
 * disappears over light content and a dark one over a game, while one filled light
 * with a dark edge reads on both — which is the only thing a pointer over
 * arbitrary content has to do.
 */
@Composable
private fun Cursor(position: State<PointerPosition?>) {
    // Reused across frames. The arrow is the same seven segments every time and
    // allocating them afresh sixty times a second is work the collector then has
    // to undo.
    val path = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read here and nowhere else: inside the draw lambda this subscribes the
        // draw phase alone, so a move repaints without recomposing or re-laying out.
        val current = position.value ?: return@Canvas

        val size = CURSOR_SIZE.dp.toPx()
        val w = size * 0.62f
        val h = size
        val x = current.x
        val y = current.y

        path.reset()
        path.apply {
            moveTo(x, y)
            lineTo(x, y + h)
            lineTo(x + w * 0.30f, y + h * 0.74f)
            lineTo(x + w * 0.52f, y + h * 1.02f)
            lineTo(x + w * 0.75f, y + h * 0.90f)
            lineTo(x + w * 0.54f, y + h * 0.62f)
            lineTo(x + w, y + h * 0.58f)
            close()
        }

        // A soft drop first, so the arrow separates from bright content that an
        // outline alone would sit flat against.
        translate(left = size * 0.06f, top = size * 0.08f) {
            drawPath(path, Color.Black.copy(alpha = 0.28f))
        }
        drawPath(path, Color.White)
        drawPath(
            path = path,
            color = Color(0xFF101216),
            style = Stroke(width = size * 0.075f),
        )
    }
}

/** The arrow's tip is its hotspot, so the drawn point is the point clicked. */
private const val CURSOR_SIZE = 26
private const val TAP_MS = 40L
private const val LONG_PRESS_MS = 600L

/** One scroll press moves about a third of a panel. */
private const val SCROLL_PX = 320f
private const val SCROLL_MS = 180L
private const val SCROLL_STEPS = 8
