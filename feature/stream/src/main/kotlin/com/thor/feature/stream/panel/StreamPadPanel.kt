package com.thor.feature.stream.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.SessionQuality
import com.thor.core.ui.component.ThorKeyboard
import com.thor.data.stream.StreamPad
import kotlin.math.abs

/**
 * The second screen while a PC is on the first: a trackpad and a keyboard.
 *
 * Pinned rather than summoned, because both are needed constantly and there is
 * nothing else for this panel to show — the stream owns the other screen
 * completely. It also solves a problem with no other solution on this device:
 * Android's own keyboard cannot render on the second display at all, and cannot
 * be raised over a surface THOR does not own, so a streamed desktop was
 * literally impossible to type into.
 *
 * The keyboard is THOR's own, the same one the launcher uses everywhere else, so
 * it is already familiar and already driveable by the controller.
 */
@Composable
fun StreamPadPanel(
    pad: StreamPad,
    quality: SessionQuality,
    controller: StreamPanelController,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        if (controller.mode == PanelMode.SETTINGS) {
            StreamQuickSettings(
                quality = quality,
                onClose = controller::showPad,
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        Trackpad(
            pad = pad,
            quality = quality,
            // Touching the trackpad is the other half of the handover: it means
            // the user has moved on from typing, so the pad goes back to the game.
            onTouched = controller::releaseKeyboard,
            modifier = Modifier
                .fillMaxWidth()
                .weight(TRACKPAD_WEIGHT),
        )

        /*
         * Touching the keyboard hands it the controller.
         *
         * The whole pad follows focus — a button cannot be split between two
         * windows — so this is the moment the user says which screen they are
         * working on. B gives it back.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(KEYBOARD_WEIGHT)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controller.takeKeyboard()
                    }
                },
        ) {
            ThorKeyboard(
                text = controller.typed,
                label = if (controller.keyboardFocused) {
                    "Typing on the PC · B gives the controller back"
                } else {
                    "Typing on the PC · tap to use the controller here"
                },
                layer = controller.layer,
                shifted = controller.shifted,
                /*
                 * A cursor only while this panel holds the controller.
                 *
                 * A highlighted key that cannot be moved would say the pad works
                 * here when it does not — so out of focus there is deliberately
                 * nothing to see.
                 */
                cursorRow = if (controller.keyboardFocused) controller.cursorRow else -1,
                cursorColumn = if (controller.keyboardFocused) controller.cursorColumn else -1,
                onKey = controller::press,
                // Nothing to dismiss to: this panel is the whole screen for as
                // long as the stream is up.
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The settings worth reaching without leaving a stream.
 *
 * Deliberately a summary rather than the real settings screen: those live in the
 * launcher, on the other side of a stream that would have to be torn down to get
 * to them, and most of them cannot take effect mid-session anyway — resolution
 * and codec are agreed with the host at launch.
 */
@Composable
private fun StreamQuickSettings(
    quality: SessionQuality,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier.padding(dimens.spacing),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        Text(
            text = "This stream",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
        )
        Text(
            text = "${quality.width}×${quality.height} · ${quality.fps} fps · " +
                "${quality.bitrateKbps / 1000} Mbps · ${quality.codec.label}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = "Picture settings are agreed with the PC when the stream starts, so " +
                "changing them means starting it again. They are in Settings → PC " +
                "streaming.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = "Leaving: tap Back, or hold Start, Select, LB and RB together.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = "Press Start again for the trackpad",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            modifier = Modifier.clickable(onClick = onClose),
        )
    }
}

/**
 * A relative pointer surface.
 *
 * Relative rather than absolute, unlike touching the video: a pad this size
 * addressing a whole desktop one-to-one would make every pixel a fifth of a
 * millimetre of finger. Dragging repeatedly to cross the screen is how every
 * trackpad works and is what makes small targets reachable.
 */
@Composable
private fun Trackpad(
    pad: StreamPad,
    quality: SessionQuality,
    onTouched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .thorSurface(
                shape = RectangleShape,
                color = colors.surface,
                level = SurfaceLevel.RAISED,
            )
            .pointerInput(quality.trackpadSpeed, quality.naturalScroll, quality.tapToClick) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    onTouched()
                    var travelled = 0f
                    var fingers = 1
                    var scrolled = false
                    // Carried forward so the release can be timed against the
                    // press; the up event itself is not delivered to this loop.
                    var lastTime = first.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        lastTime = event.changes.firstOrNull()?.uptimeMillis ?: lastTime
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        fingers = maxOf(fingers, pressed.size)

                        /*
                         * Two fingers scroll; one moves the pointer.
                         *
                         * Decided per event rather than once at the start, so a
                         * second finger landing mid-drag becomes a scroll — which
                         * is what the hand does naturally when it turns out the
                         * page needs moving rather than the cursor.
                         */
                        if (pressed.size >= 2) {
                            val dy = pressed[0].positionChange().y
                            if (abs(dy) > 0f) {
                                pad.scroll(dy)
                                scrolled = true
                            }
                        } else {
                            val change = pressed[0].positionChange()
                            travelled += abs(change.x) + abs(change.y)
                            if (change != androidx.compose.ui.geometry.Offset.Zero) {
                                pad.move(change.x, change.y)
                            }
                        }

                        pressed.forEach { if (it.positionChanged()) it.consume() }
                    }

                    /*
                     * A tap is a press that went nowhere.
                     *
                     * Two fingers make it a right click, which is the gesture
                     * every trackpad uses and the only way to reach a context
                     * menu from here.
                     */
                    val quick = lastTime - first.uptimeMillis <= TAP_TIME_MS
                    if (quality.tapToClick && !scrolled && travelled < TAP_SLOP_PX && quick) {
                        pad.click(
                            if (fingers >= 2) StreamPad.BUTTON_RIGHT else StreamPad.BUTTON_LEFT,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Trackpad",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "Drag to move · tap to click · two fingers to scroll or right-click",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    top = dimens.spacingSmall,
                    start = dimens.spacing,
                    end = dimens.spacing,
                ),
            )
        }
    }
}

/** The next layer, so the layout switch has somewhere to go. */
private fun KeyboardLayer.next(): KeyboardLayer =
    if (this == KeyboardLayer.LETTERS) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS

/*
 * The keyboard takes the larger share.
 *
 * It has to hold five rows of keys big enough to hit with a thumb, while the
 * trackpad works at any size — a small pad simply needs more strokes.
 */
private const val TRACKPAD_WEIGHT = 0.38f
private const val KEYBOARD_WEIGHT = 0.62f

/** Far enough to be a drag rather than a tap. */
private const val TAP_SLOP_PX = 24f
private const val TAP_TIME_MS = 250L
