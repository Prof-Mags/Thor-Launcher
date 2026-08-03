package com.thor.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * The launcher drawn inside a dual-screen console, which is what a recording is.
 *
 * The two windows sit on two displays no capture API can see together, so a
 * recording cannot photograph them. It re-draws them instead — the same composables,
 * from the same state — onto a private display whose output is the video encoder's
 * input surface, and drops each into the screen cut-out of a console drawn around
 * them.
 *
 * **Why a console and not two stacked rectangles.** Bare, the video is a tall square
 * that reads as two screenshots glued together. The point of a recording is to show
 * the launcher off, and a mock-up does that: the shell explains the proportions, the
 * hinge explains why there are two screens, and the controls explain what the thing
 * is. It is drawn rather than shipped as an image so it scales to any panel shape and
 * costs nothing in the APK.
 *
 * **The lid's screen is larger than the base's**, as it is on every dual-screen
 * handheld — the base has to find room for the controls beside its screen. That
 * difference is most of what makes the drawing read as a device rather than as two
 * equal rectangles in a frame.
 *
 * **Why an earlier body was removed, and why this one is safe.** The first version
 * laid each panel out at 86% of the frame while the frame carried a further 22% of
 * height for chrome, and the two figures lived in different files with nothing
 * shared. Each panel was composed into a box that was not the shape of the screen it
 * stood for, laid out for a smaller screen, and showed less of itself. Two things
 * prevent that here, and both are load-bearing:
 *
 *  - the frame comes from [recordingFrameSize], derived
 *    from the same constants this file lays out with, so chrome cannot claim space
 *    the frame never budgeted
 *  - each panel overrides [LocalDensity] so the *dp* box it is measured in is its
 *    real screen's, whatever pixel box it was drawn into — see [Screen]
 *
 * A panel drawn at half width therefore lays out **identically** to the real screen
 * and is merely rendered at fewer pixels. Nothing reflows, nothing crops.
 */
@Composable
fun ConsoleMockup(
    topAspect: Float,
    bottomAspect: Float,
    /** The real top panel's width in dp, so its layout can be reproduced exactly. */
    topWidthDp: Float,
    /** The real bottom panel's width in dp. */
    bottomWidthDp: Float,
    topPanel: @Composable () -> Unit,
    bottomPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BACKDROP)
            .alwaysRedrawing(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val w = maxWidth

        Column(
            modifier = Modifier
                .fillMaxWidth(BODY_WIDTH)
                .padding(top = w * MARGIN),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UpperShell(frameWidth = w, aspect = topAspect, widthDp = topWidthDp, panel = topPanel)
            Hinge(frameWidth = w)
            LowerShell(
                frameWidth = w,
                aspect = bottomAspect,
                widthDp = bottomWidthDp,
                panel = bottomPanel,
            )
        }
    }
}

/**
 * The lid: one large screen, a camera and a status light.
 *
 * Nothing else, because that is what a lid has — the asymmetry between the halves is
 * most of what makes a drawing read as a clamshell rather than as a box with two
 * holes cut in it.
 */
@Composable
private fun UpperShell(
    frameWidth: Dp,
    aspect: Float,
    widthDp: Float,
    panel: @Composable () -> Unit,
) {
    Shell(frameWidth = frameWidth) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(frameWidth * SHELL_PAD),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Camera and light, on the bezel above the screen.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(frameWidth * LID_TRIM),
            ) {
                val r = size.height * 0.24f
                val centre = Offset(size.width / 2f, size.height / 2f)
                drawCircle(LENS, radius = r, center = centre)
                drawCircle(LENS_INNER, radius = r * 0.45f, center = centre)
                drawCircle(LED, radius = r * 0.34f, center = Offset(size.width * 0.9f, centre.y))
            }

            Screen(
                aspect = aspect,
                realWidthDp = widthDp,
                // Of the shell's inner width, so the constant stays a share of the
                // whole frame and [recordingFrameSize] can use it directly.
                modifier = Modifier.fillMaxWidth(TOP_SCREEN_WIDTH / SHELL_INNER_WIDTH),
                content = panel,
            )
        }
    }
}

/**
 * The base: a smaller screen with the controls beside it, speaker along the foot.
 *
 * The controls flank rather than sit beneath, which is both what a dual-screen
 * handheld looks like and what makes the base's screen visibly the smaller of the
 * two without costing the frame another band of height.
 */
@Composable
private fun LowerShell(
    frameWidth: Dp,
    aspect: Float,
    widthDp: Float,
    panel: @Composable () -> Unit,
) {
    Shell(frameWidth = frameWidth) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(frameWidth * SHELL_PAD),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Controls(frameWidth, left = true, modifier = Modifier.weight(CONTROL_COLUMN))
                Screen(
                    aspect = aspect,
                    realWidthDp = widthDp,
                    modifier = Modifier.weight(SCREEN_COLUMN),
                    content = panel,
                )
                Controls(frameWidth, left = false, modifier = Modifier.weight(CONTROL_COLUMN))
            }

            Foot(frameWidth = frameWidth)
        }
    }
}

/** A moulded half of the body, with the soft top edge plastic has. */
@Composable
private fun Shell(frameWidth: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(frameWidth * SHELL_CORNER))
            .background(Brush.verticalGradient(listOf(SHELL_TOP, SHELL_BOTTOM))),
    ) {
        content()
    }
}

/**
 * A screen cut-out with a live panel in it.
 *
 * The density override is the load-bearing part. Layout is decided in dp, and dp is
 * pixels over density — so handing the launcher a smaller pixel box at the display's
 * own density tells it the *screen* is smaller, and it lays out for a smaller screen:
 * different wrapping, different room, a different everything. Overriding the density
 * so the dp width comes back out at the real screen's figure means the panel believes
 * it is exactly the screen it stands in for, and the shrink becomes a pure scale.
 */
@Composable
private fun Screen(
    aspect: Float,
    realWidthDp: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val outer = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT))
            .clip(RoundedCornerShape(SCREEN_CORNER_PERCENT))
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
            content()
        }
    }
}

/**
 * One flanking control column: a stick above, d-pad or face buttons below.
 *
 * Drawn on a canvas rather than assembled from composables — these are decoration
 * with no state and no interaction, and a dozen nested boxes to describe a d-pad
 * would cost a layout pass every frame for something that never changes.
 */
@Composable
private fun Controls(frameWidth: Dp, left: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxHeight().padding(horizontal = frameWidth * CONTROL_PAD)) {
        val unit = size.width
        val cx = size.width / 2f

        val stickR = unit * STICK_RADIUS
        val stickY = size.height * STICK_Y
        drawCircle(RECESS, radius = stickR * 1.3f, center = Offset(cx, stickY))
        drawCircle(STICK, radius = stickR, center = Offset(cx, stickY))
        drawCircle(STICK_TOP, radius = stickR * 0.6f, center = Offset(cx, stickY))

        val clusterY = size.height * CLUSTER_Y
        if (left) {
            val arm = unit * DPAD_ARM
            val thick = arm * 0.62f
            drawRoundRect(
                color = BUTTON,
                topLeft = Offset(cx - arm, clusterY - thick / 2f),
                size = Size(arm * 2f, thick),
                cornerRadius = CornerRadius(thick * 0.3f),
            )
            drawRoundRect(
                color = BUTTON,
                topLeft = Offset(cx - thick / 2f, clusterY - arm),
                size = Size(thick, arm * 2f),
                cornerRadius = CornerRadius(thick * 0.3f),
            )
            drawCircle(BUTTON_TOP, radius = thick * 0.28f, center = Offset(cx, clusterY))
        } else {
            val spread = unit * FACE_SPREAD
            val r = unit * FACE_RADIUS
            listOf(
                Offset(cx, clusterY - spread) to FACE_N,
                Offset(cx + spread, clusterY) to FACE_E,
                Offset(cx, clusterY + spread) to FACE_S,
                Offset(cx - spread, clusterY) to FACE_W,
            ).forEach { (centre, colour) ->
                drawCircle(RECESS, radius = r * 1.26f, center = centre)
                drawCircle(colour, radius = r, center = centre)
            }
        }
    }
}

/** Speaker holes and the wordmark, along the bottom lip. */
@Composable
private fun Foot(frameWidth: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(frameWidth * FOOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.height * SPEAKER_DOT
            val gap = r * 3.2f
            val y = size.height / 2f
            listOf(size.width * 0.1f, size.width * 0.9f).forEach { originX ->
                repeat(SPEAKER_DOTS) { i ->
                    val x = originX + (i - (SPEAKER_DOTS - 1) / 2f) * gap
                    drawCircle(SPEAKER, radius = r, center = Offset(x, y))
                }
            }
        }

        Text(
            text = "LOKI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            color = WORDMARK,
            letterSpacing = WORDMARK_TRACKING.sp,
        )
    }
}

/** The seam, so the two halves read as one hinged device. */
@Composable
private fun Hinge(frameWidth: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().height(frameWidth * HINGE_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Barrel(frameWidth, Modifier.weight(1f))
        Box(
            modifier = Modifier
                .weight(2.6f)
                .fillMaxHeight()
                .padding(vertical = frameWidth * HINGE_INSET)
                .background(HINGE_GAP),
        )
        Barrel(frameWidth, Modifier.weight(1f))
    }
}

@Composable
private fun Barrel(frameWidth: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(frameWidth * HINGE_ROUND))
            .background(Brush.verticalGradient(listOf(HINGE_TOP, HINGE_BOTTOM))),
    )
}

/** A recording's pixel dimensions. */
data class RecordingFrame(val width: Int, val height: Int)

/**
 * The video's size, from the same constants the console lays out with.
 *
 * One function, read by the shell that sizes the encoder and describing the layout
 * that fills it. These used to be two independent numbers in two files, and the
 * frame ended up taller than the layout while each panel was drawn narrower than the
 * frame — which is how a recording of two screens came to show one and a half.
 *
 * Sized so the *lid's* screen gets its panel's own pixels, because the console is
 * wider than any of its screens and a frame the width of a panel would render every
 * one of them below native. Where that overshoots [ceiling] the whole frame is
 * brought down in proportion here rather than left for the recorder to clamp: the
 * recorder scales correctly, but a frame that arrives already inside the encoder's
 * range is one fewer resampling of the picture.
 */
fun recordingFrameSize(
    panelWidthPx: Int,
    topAspect: Float,
    bottomAspect: Float,
    ceiling: Int = ENCODER_CEILING,
): RecordingFrame {
    val top = topAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
    val bottom = bottomAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)

    // Height as a multiple of width, so the shape is fixed before any size is chosen.
    val heightRatio = TOP_SCREEN_WIDTH / top + bottomScreenWidth() / bottom + chromeHeight()

    val wanted = (panelWidthPx / TOP_SCREEN_WIDTH).toInt().coerceAtLeast(1)
    val width = minOf(
        wanted,
        ceiling,
        (ceiling / heightRatio).toInt(),
    ).coerceAtLeast(1)

    return RecordingFrame(width = width, height = (width * heightRatio).toInt())
}

/**
 * What the base's screen takes of the whole frame, once the controls have theirs.
 *
 * A function rather than a derived constant only because Kotlin initialises
 * top-level properties in source order and the constants it reads are declared with
 * the rest of the layout, at the foot of the file.
 */
internal fun bottomScreenWidth(): Float =
    SHELL_INNER_WIDTH * (SCREEN_COLUMN / (SCREEN_COLUMN + CONTROL_COLUMN * 2))

/** Everything above, below and between the two screens, as a share of frame width. */
private fun chromeHeight(): Float =
    MARGIN * 2 +
        SHELL_PAD * 4 + // two shells, padded top and bottom
        LID_TRIM +
        HINGE_HEIGHT +
        FOOT_HEIGHT

/**
 * Keeps the frame pipeline moving.
 *
 * Compose only redraws what changes, and a launcher sitting still changes nothing —
 * which starves a video encoder of frames and produces a file with one picture in it
 * or none at all. This animates a value that is *used* in a draw, at a magnitude no
 * eye resolves, so every vsync produces a frame for the encoder to take.
 */
@Composable
private fun Modifier.alwaysRedrawing(): Modifier {
    val transition = rememberInfiniteTransition(label = "capture")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TICK_PERIOD_MS, easing = LinearEasing),
        ),
        label = "captureTick",
    )

    return this.drawBehind {
        drawRect(
            color = Color.White.copy(alpha = TICK_ALPHA),
            topLeft = Offset(0f, size.height * tick),
            size = Size(size.width, 1f),
        )
    }
}

private val BACKDROP = Color(0xFF07080A)
private val SHELL_TOP = Color(0xFF262C35)
private val SHELL_BOTTOM = Color(0xFF12161C)
private val HINGE_TOP = Color(0xFF39414D)
private val HINGE_BOTTOM = Color(0xFF1C2129)
private val HINGE_GAP = Color(0xFF0D1014)
private val RECESS = Color(0xFF0E1116)
private val BUTTON = Color(0xFF39414D)
private val BUTTON_TOP = Color(0xFF4A5462)
private val STICK = Color(0xFF2A313A)
private val STICK_TOP = Color(0xFF3C4552)
private val SPEAKER = Color(0xFF0F1318)
private val LENS = Color(0xFF0B0E12)
private val LENS_INNER = Color(0xFF1B3348)
private val LED = Color(0xFF2E7D5B)
private val WORDMARK = Color(0xFF6E7580)

private val FACE_N = Color(0xFF4A5462)
private val FACE_E = Color(0xFF44505E)
private val FACE_S = Color(0xFF3E4956)
private val FACE_W = Color(0xFF48525F)

/*
 * Every dimension is a fraction of the frame's width.
 *
 * Not dp, deliberately: a recording's density is whatever its frame needs, so a body
 * measured in dp would drift against the screens it surrounds as that changed.
 * Fractions keep the console the same shape at any resolution, and let the two
 * `recordingFrame…` functions compute the frame from the very numbers laid out with
 * here.
 */
internal const val BODY_WIDTH = 0.94f
private const val MARGIN = 0.028f
internal const val SHELL_PAD = 0.022f
private const val SHELL_CORNER = 0.030f
private const val LID_TRIM = 0.026f
private const val FOOT_HEIGHT = 0.050f

/** A shell's usable width, once its own padding is taken. */
internal const val SHELL_INNER_WIDTH = BODY_WIDTH - SHELL_PAD * 2

/** The lid's screen, as a share of the whole frame — the larger of the two. */
internal const val TOP_SCREEN_WIDTH = 0.83f

/**
 * The base splits into control, screen, control by weight.
 *
 * Chosen so the base's screen lands near 0.52 of the frame against the lid's 0.83:
 * distinctly the smaller screen, which is what a dual-screen handheld looks like and
 * what stops the recording reading as two equal rectangles.
 */
internal const val SCREEN_COLUMN = 2.76f
internal const val CONTROL_COLUMN = 1f
private const val CONTROL_PAD = 0.008f

private const val HINGE_HEIGHT = 0.026f
private const val HINGE_INSET = 0.009f
private const val HINGE_ROUND = 0.008f

private const val STICK_RADIUS = 0.20f
private const val STICK_Y = 0.30f
private const val CLUSTER_Y = 0.66f
private const val DPAD_ARM = 0.26f
private const val FACE_SPREAD = 0.24f
private const val FACE_RADIUS = 0.11f

private const val SPEAKER_DOT = 0.055f
private const val SPEAKER_DOTS = 4

private const val SCREEN_CORNER_PERCENT = 3
private const val WORDMARK_TRACKING = 5f

/** Guards against a display reporting a nonsensical shape. */
private const val MIN_ASPECT = 0.4f
private const val MAX_ASPECT = 3.5f

/** A density of zero would make every dp infinite; far below any real value. */
private const val MIN_DENSITY = 0.05f

private const val TICK_PERIOD_MS = 2_000
private const val TICK_ALPHA = 0.02f

/** Mirrors the recorder's own limit, so the frame arrives already inside it. */
internal const val ENCODER_CEILING = 2160
