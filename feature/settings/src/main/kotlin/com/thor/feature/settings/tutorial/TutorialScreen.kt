package com.thor.feature.settings.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.model.PanelLayout
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.SettingsTextButton

/**
 * One panel's share of the walkthrough.
 *
 * Drawn on *both* panels, and each decides for itself what to show: the one the
 * current step names gets the card, and the other gets nothing but the dimming
 * if the step points at it. That is what lets a step about the grid sit beside
 * the grid rather than describing it from the far screen.
 *
 * The launcher underneath stays visible. Earlier versions covered the panel
 * completely, which meant every step about the grid was read with the grid
 * hidden — the tour talking about something the reader could not see.
 */
@Composable
fun TutorialScreen(
    steps: List<TutorialStep>,
    index: Int,
    panel: TutorialPanel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val isLast = index == steps.lastIndex

    /*
     * Every touch on both panels, swallowed for as long as the tour runs.
     *
     * The walkthrough already takes every *button* at the top of the shell's
     * input collector, and that was the whole of "modal" — which was true only
     * while an earlier version also hid the grid behind a solid cover. The grid
     * is deliberately visible now, and visible means live: without this, tapping
     * a cell during a step about the grid launches the game under it, and a long
     * press opens a context menu over the card explaining what long press does.
     *
     * On the far panel too, not only the one holding the card. The other screen
     * is just as touchable, and a step is no less interrupted for the tap having
     * landed on the screen the reader was not looking at.
     *
     * The default `Main` pass, deliberately: it travels child before parent, so
     * the card's own BACK and NEXT are offered the touch first and this only
     * swallows what they did not want. Consuming on `Initial` would reach here
     * before the buttons and leave the tour with no way forward but the pad.
     */
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Spotlight(spot = if (step.panel == panel) step.spot else TutorialSpot.NONE)

        // Only the named panel carries the card; the other is dimmed or left alone.
        if (step.panel != panel) return@BoxWithConstraints

        /*
         * A ceiling on the text, not on the card.
         *
         * The two panels are not the same size and the steps are not the same
         * length, and the one thing that must never happen is the buttons being
         * pushed off the bottom of the panel — the tour holds every control, so a
         * NEXT that has slid out of sight on a touch-only surface is a launcher
         * with no way forward. The prose scrolls instead; everything below it
         * stays put.
         */
        val textMaxHeight = maxHeight * TEXT_HEIGHT_FRACTION

        /*
         * Pinned to whichever end the highlight is not.
         *
         * A card centred over the grid while the step is about the grid hides
         * the thing it is pointing at, which is the fault the whole rebuild
         * exists to fix.
         */
        val alignment = when (step.spot) {
            TutorialSpot.NAV_BAR -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        GlassSurface(
            shape = ThorTheme.shapes.panel,
            color = colors.surface,
            level = SurfaceLevel.RAISED,
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth()
                .padding(dimens.spacing),
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "WALKTHROUGH",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cursor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${index + 1} / ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tutorial-step",
                    modifier = Modifier.heightIn(max = textMaxHeight),
                ) { current ->
                    val shown = steps[current.coerceIn(0, steps.lastIndex)]
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    ) {
                        Text(
                            text = shown.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )
                        Text(
                            text = shown.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        shown.hint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.cursor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                ProgressRail(index = index, total = steps.size)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsTextButton(
                        label = "BACK",
                        enabled = index > 0,
                        reactToHover = index > 0,
                        onClick = onBack.takeIf { index > 0 },
                    )
                    Box(modifier = Modifier.weight(1f))
                    SettingsTextButton(
                        label = if (isLast) "DONE" else "NEXT",
                        containerColor = colors.cursor.copy(alpha = 0.16f),
                        contentColor = colors.cursor,
                        borderColor = colors.cursor.copy(alpha = 0.5f),
                        focused = true,
                        reactToHover = true,
                        onClick = onNext,
                    )
                }

                Text(
                    text = if (isLast) "A finishes" else "A continues  ·  B goes back",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Dims the panel except the part being talked about.
 *
 * Four rectangles around the lit region rather than a punched-out layer: the
 * same result with no offscreen buffer, and nothing that behaves differently
 * when the theme turns its effects off.
 *
 * Regions come from the panel's own proportions — the section bar is the strip
 * along the bottom, the grid is everything above it — so nothing has to be
 * measured or reported up from the surfaces being pointed at.
 */
@Composable
private fun Spotlight(spot: TutorialSpot) {
    if (spot == TutorialSpot.NONE) return

    val colors = ThorTheme.colors
    val transition = rememberInfiniteTransition(label = "spotlight")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spotlight-pulse",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        /*
         * The bar's own height, not a share of the panel.
         *
         * This was `height * 0.16f`, which on a panel of any real size lit a
         * strip about twice as tall as the bar — swallowing the bottom row of
         * icons and the page dots, and ringing them as though they were what the
         * step was pointing at. The two numbers were independent and had already
         * drifted; there is one now, and it is the one the bar is laid out from.
         */
        val barTop = (size.height - PanelLayout.NAV_BAR_HEIGHT.dp.toPx())
            .coerceAtLeast(0f)
        val lit = when (spot) {
            TutorialSpot.GRID -> Rect(Offset.Zero, Size(size.width, barTop))
            TutorialSpot.NAV_BAR ->
                Rect(Offset(0f, barTop), Size(size.width, size.height - barTop))

            // The whole panel is the subject, so nothing is dimmed — only ringed.
            TutorialSpot.PANEL -> Rect(Offset.Zero, size)
            TutorialSpot.NONE -> return@Canvas
        }

        val shade = colors.scrim.copy(alpha = DIM_ALPHA)
        // Above, below, left and right of the lit region.
        if (lit.top > 0f) {
            drawRect(shade, Offset.Zero, Size(size.width, lit.top))
        }
        if (lit.bottom < size.height) {
            drawRect(shade, Offset(0f, lit.bottom), Size(size.width, size.height - lit.bottom))
        }
        if (lit.left > 0f) {
            drawRect(shade, Offset(0f, lit.top), Size(lit.left, lit.height))
        }
        if (lit.right < size.width) {
            drawRect(
                shade,
                Offset(lit.right, lit.top),
                Size(size.width - lit.right, lit.height),
            )
        }

        // The ring, inset so it sits inside the region rather than over its edge.
        val inset = RING_INSET_DP.dp.toPx()
        val glow = RING_ALPHA_LOW + (RING_ALPHA_HIGH - RING_ALPHA_LOW) * pulse
        drawRoundRect(
            color = colors.cursor.copy(alpha = glow),
            topLeft = Offset(lit.left + inset, lit.top + inset),
            size = Size(
                (lit.width - inset * 2).coerceAtLeast(0f),
                (lit.height - inset * 2).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(RING_RADIUS_DP.dp.toPx()),
            style = Stroke(width = RING_STROKE_DP.dp.toPx()),
        )
    }
}

@Composable
private fun ProgressRail(index: Int, total: Int) {
    val colors = ThorTheme.colors
    val fraction = ((index + 1).toFloat() / total).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RAIL_HEIGHT.dp)
            .clip(ThorTheme.shapes.pill)
            .background(colors.surfaceHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(RAIL_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor),
        )
    }
}

/**
 * How much of the panel the step's prose may take before it scrolls.
 *
 * Leaves room for the heading, the rail, the buttons and the hint beneath — the
 * parts that have to stay reachable however long the text runs.
 */
private const val TEXT_HEIGHT_FRACTION = 0.42f

private const val DIM_ALPHA = 0.78f
private const val RING_INSET_DP = 3
private const val RING_STROKE_DP = 2
private const val RING_RADIUS_DP = 10
private const val RING_ALPHA_LOW = 0.4f
private const val RING_ALPHA_HIGH = 1f
private const val PULSE_MS = 1_100
private const val RAIL_HEIGHT = 4
