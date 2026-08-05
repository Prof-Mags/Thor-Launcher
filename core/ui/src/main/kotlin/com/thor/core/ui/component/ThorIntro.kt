package com.thor.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thor.core.designsystem.theme.ThorTheme
import kotlin.math.roundToInt

/**
 * THOR's cold-start sequence, driven by [progress].
 *
 * The background is deliberately one flat theme color. Motion comes from the
 * mark, rings, typography, and progress rail instead of a gradient or glow that
 * competes with the launcher before it has even appeared.
 *
 * @param showContent whether to draw the mark, wordmark and loader, or only the
 *   plate they sit on. The two panels are separate windows, so each has to draw
 *   its own overlay; drawing the whole sequence on both put two of everything on
 *   a device whose screens sit one above the other, which read as a mirror
 *   rather than as one launcher starting. The bottom panel takes the plate
 *   alone — same colour, same fade, driven by the same progress, so it clears at
 *   the same instant without competing for the eye.
 * @param subtitle the line under the wordmark, saying which launcher is starting.
 * @param stages what the rail reports as it fills. Four of them, in order; the
 *   last quarter is the launcher's own, and reaching the end says READY. They are
 *   a parameter because the sequence is reused for entering couch mode, where
 *   "INITIALIZING CORE" would be describing something that happened minutes ago.
 */
@Composable
fun ThorIntro(
    progress: Float,
    motion: Boolean,
    modifier: Modifier = Modifier,
    showContent: Boolean = true,
    subtitle: String = "DUAL-SCREEN LAUNCHER",
    stages: List<String> = COLD_START_STAGES,
) {
    val colors = ThorTheme.colors
    val value = progress.coerceIn(0f, 1f)
    val visualProgress = if (motion) value else REVEAL_FROM
    val overlayAlpha = if (motion) {
        1f - span(value, REVEAL_FROM, 1f)
    } else {
        1f - value
    }
    if (overlayAlpha <= 0f) return

    val markProgress = span(visualProgress, MARK_FROM, MARK_TO)
    // The rings are loaders, not a brief logo flourish: they advance alongside
    // the rail and close only when the loading state reaches 100%.
    val ringProgress = span(visualProgress, RING_FROM, LOAD_TO)
    val wordProgress = span(visualProgress, WORD_FROM, WORD_TO)
    val loadingProgress = span(visualProgress, LOAD_FROM, LOAD_TO)
    val loaderAlpha = span(visualProgress, LOADER_FADE_FROM, LOADER_FADE_TO)
    val ready = loadingProgress >= 1f

    val markScale = if (motion) {
        MARK_SCALE_FROM + (1f - MARK_SCALE_FROM) * markProgress
    } else {
        1f
    }
    val markRotation = if (motion) MARK_ROTATION_FROM * (1f - markProgress) else 0f
    val visibleRingProgress = if (motion) ringProgress else 1f
    val percent = (loadingProgress * 100f).roundToInt().coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(colors.background)
            /*
             * Takes every touch and gives none of them back.
             *
             * Not a skip: the sequence runs to its own end. This is here so a tap
             * aimed at the launcher underneath cannot reach it — the intro covers
             * a live grid, and a press landing on a cell nobody can see would
             * launch something.
             */
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        // The plate still blocks touches and still fades on the same schedule.
        // Only the sequence itself is left off.
        if (!showContent) return@Box

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(MARK_SIZE.dp)
                    .graphicsLayer {
                        alpha = markProgress
                        scaleX = markScale
                        scaleY = markScale
                        rotationZ = markRotation
                    }
                    .drawBehind {
                        val outerStroke = 2.dp.toPx()
                        val activeStroke = 3.dp.toPx()
                        val outerRadius = size.minDimension * 0.47f
                        val innerRadius = size.minDimension * 0.37f
                        val outerTopLeft = androidx.compose.ui.geometry.Offset(
                            center.x - outerRadius,
                            center.y - outerRadius,
                        )
                        val outerSize = androidx.compose.ui.geometry.Size(
                            outerRadius * 2f,
                            outerRadius * 2f,
                        )
                        val innerTopLeft = androidx.compose.ui.geometry.Offset(
                            center.x - innerRadius,
                            center.y - innerRadius,
                        )
                        val innerSize = androidx.compose.ui.geometry.Size(
                            innerRadius * 2f,
                            innerRadius * 2f,
                        )

                        drawCircle(
                            color = colors.outline.copy(alpha = 0.28f),
                            radius = outerRadius,
                            style = Stroke(width = outerStroke),
                        )
                        drawCircle(
                            color = colors.outline.copy(alpha = 0.16f),
                            radius = innerRadius,
                            style = Stroke(width = outerStroke),
                        )
                        if (visibleRingProgress >= COMPLETE_RING_THRESHOLD) {
                            // A circle has no cap seam, so the completed state is
                            // visibly closed rather than merely a 360-degree arc.
                            drawCircle(
                                color = colors.cursor,
                                radius = outerRadius,
                                style = Stroke(width = activeStroke),
                            )
                            drawCircle(
                                color = colors.accentEnd,
                                radius = innerRadius,
                                style = Stroke(width = outerStroke),
                            )
                        } else {
                            drawArc(
                                color = colors.cursor,
                                startAngle = -90f,
                                sweepAngle = FULL_CIRCLE_DEGREES * visibleRingProgress,
                                useCenter = false,
                                topLeft = outerTopLeft,
                                size = outerSize,
                                style = Stroke(width = activeStroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = colors.accentEnd,
                                startAngle = 90f,
                                sweepAngle = -FULL_CIRCLE_DEGREES * visibleRingProgress,
                                useCenter = false,
                                topLeft = innerTopLeft,
                                size = innerSize,
                                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
                            )
                        }
                        drawDeviceMark(
                            extent = size.minDimension,
                            shell = colors.onBackground.copy(alpha = MARK_SHELL_ALPHA),
                            hinge = colors.onBackground.copy(alpha = MARK_HINGE_ALPHA),
                            topScreen = colors.cursor,
                            bottomScreen = colors.accentEnd,
                        )
                    },
            )

            Spacer(modifier = Modifier.height(MARK_GAP.dp))

            Text(
                text = "Loki",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                color = colors.onBackground,
                letterSpacing = if (motion) {
                    (TRACKING_FROM + (TRACKING_TO - TRACKING_FROM) * wordProgress).sp
                } else {
                    TRACKING_TO.sp
                },
                modifier = Modifier.graphicsLayer { alpha = wordProgress },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.4.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .graphicsLayer { alpha = wordProgress },
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = LOADER_BOTTOM_GAP.dp),
            contentAlignment = Alignment.Center,
        ) {
            val loaderWidth = minOf(
                maxWidth * LOADER_WIDTH_FRACTION,
                LOADER_MAX_WIDTH.dp,
            )
            Column(
                modifier = Modifier
                    .width(loaderWidth)
                    .graphicsLayer { alpha = loaderAlpha },
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = loadingLabel(loadingProgress, stages),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = if (ready) colors.cursor else colors.onSurfaceVariant,
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (ready) colors.cursor else colors.onSurface,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LOADER_HEIGHT.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(colors.surfaceHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadingProgress)
                            .height(LOADER_HEIGHT.dp)
                            .clip(ThorTheme.shapes.pill)
                            .background(colors.cursor),
                    ) {
                    }
                }

                // The percentage above already reports progress, so this line
                // reports *state* instead — and no longer offers a way out, because
                // there is not one.
                Text(
                    text = if (ready) "SYSTEM READY" else "STARTING",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** What the launcher says it is doing while the rail fills. */
val COLD_START_STAGES = listOf(
    "INITIALIZING CORE",
    "SYNCING DISPLAYS",
    "RESTORING LIBRARY",
    "PREPARING HOME",
)

/** The same sequence, for the switch into couch mode. */
val COUCH_STAGES = listOf(
    "SWITCHING DISPLAY",
    "SCALING INTERFACE",
    "RESTORING LIBRARY",
    "PREPARING COUCH",
)

private fun loadingLabel(progress: Float, stages: List<String>): String {
    if (stages.isEmpty() || progress >= 1f) return "READY"
    val index = (progress * stages.size).toInt().coerceIn(0, stages.lastIndex)
    return stages[index]
}

/**
 * The launcher's mark: the device it runs on.
 *
 * The same drawing as `ic_launcher_foreground`, in the same 108-unit space, so
 * the icon the user pressed and the mark that greets them are one shape rather
 * than two things that resemble each other. It is drawn rather than loaded
 * because these strokes are themed — the screens take the current accent, which
 * a compiled-in vector cannot.
 *
 * Scaled to two thirds of the ring it sits inside, so the shell clears the inner
 * arc instead of touching it.
 */
private fun DrawScope.drawDeviceMark(
    extent: Float,
    shell: Color,
    hinge: Color,
    topScreen: Color,
    bottomScreen: Color,
) {
    val scale = extent / VIEWPORT * MARK_INSET
    val originX = center.x - VIEWPORT * scale / 2f
    val originY = center.y - VIEWPORT * scale / 2f

    fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Color) {
        drawRoundRect(
            color = color,
            topLeft = Offset(originX + left * scale, originY + top * scale),
            size = Size((right - left) * scale, (bottom - top) * scale),
            cornerRadius = CornerRadius(radius * scale, radius * scale),
        )
    }

    // One body, as the icon has: the hinge is a mark on it, not a seam in it.
    rect(32f, 26f, 76f, 82f, 7f, shell)
    rect(35.5f, 29.5f, 72.5f, 51f, 2f, topScreen)
    rect(43.8f, 52.6f, 64.2f, 55.4f, 1.2f, hinge)
    rect(38f, 57f, 70f, 78.5f, 2f, bottomScreen)
}

private fun span(progress: Float, from: Float, to: Float): Float {
    if (to <= from) return if (progress >= to) 1f else 0f
    return ((progress - from) / (to - from)).coerceIn(0f, 1f)
}

/** How much of the ring's width the device fills, leaving the arcs clear. */
private const val MARK_INSET = 0.62f

/** The body, which is the launcher's own foreground colour held back. */
private const val MARK_SHELL_ALPHA = 0.22f

/** Brighter than the body, so the hinge is a mark on it rather than a shadow. */
private const val MARK_HINGE_ALPHA = 0.42f

private const val MARK_FROM = 0.02f
private const val MARK_TO = 0.20f
private const val RING_FROM = 0.07f
private const val WORD_FROM = 0.13f
private const val WORD_TO = 0.28f
private const val LOADER_FADE_FROM = 0.16f
private const val LOADER_FADE_TO = 0.25f
private const val LOAD_FROM = 0.20f
private const val LOAD_TO = 0.90f
private const val REVEAL_FROM = 0.94f

private const val VIEWPORT = 108f
private const val MARK_SIZE = 122
private const val MARK_GAP = 18
private const val MARK_SCALE_FROM = 0.72f
private const val MARK_ROTATION_FROM = -14f
private const val TRACKING_FROM = 18f
private const val TRACKING_TO = 8f
private const val LOADER_WIDTH_FRACTION = 0.68f
private const val LOADER_MAX_WIDTH = 360
private const val LOADER_HEIGHT = 5
private const val LOADER_BOTTOM_GAP = 42
private const val FULL_CIRCLE_DEGREES = 360f
private const val COMPLETE_RING_THRESHOLD = 0.999f
