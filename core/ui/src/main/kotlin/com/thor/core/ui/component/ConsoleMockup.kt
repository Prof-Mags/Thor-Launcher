package com.thor.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * The launcher drawn inside the AYN Thor, which is what a recording is.
 *
 * The two windows sit on two displays no capture API can see together, so a
 * recording cannot photograph them. It re-draws them instead — the same
 * composables, from the same state — onto a private display whose output is the
 * video encoder's input surface, and drops each into the screen cut-out of a console
 * drawn around them.
 *
 * **Drawn as the actual device**, from the manufacturer's own photography: the cream
 * shell, the gold trim around the lid's display, the wide flat hinge, the staggered
 * controls — left stick above the d-pad, face buttons above the right stick — and
 * the coloured buttons in their Nintendo arrangement. Vector rather than a
 * photograph, so it scales to any panel shape, costs nothing in the APK, and has no
 * lighting of its own to fight the screens.
 *
 * **The lid's screen is much the larger.** On this hardware the lid is nearly all
 * display while the base has to find room for two sticks, a d-pad and four buttons
 * beside its screen — which is most of what makes the drawing read as this device
 * rather than as two rectangles in a frame.
 *
 * **Why an earlier body was removed, and why this one is safe.** The first version
 * laid each panel out at 86% of the frame while the frame carried a further 22% of
 * height for chrome, and the two figures lived in different files with nothing
 * shared. Each panel was composed into a box that was not the shape of the screen it
 * stood for, laid out for a smaller screen, and showed less of itself. Two things
 * prevent that here, and both are load-bearing:
 *
 *  - the frame comes from [recordingFrameSize], derived from the same constants this
 *    file lays out with, so chrome cannot claim space the frame never budgeted
 *  - each panel overrides [LocalDensity] so the *dp* box it is measured in is its
 *    real screen's, whatever pixel box it was drawn into — see [Screen]
 *
 * The base's panel is drawn at about half width and still lays out **identically** to
 * the real screen; it is merely rendered at fewer pixels. Nothing reflows, nothing
 * crops.
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
            Lid(frameWidth = w, aspect = topAspect, widthDp = topWidthDp, panel = topPanel)
            Hinge(frameWidth = w)
            Base(frameWidth = w, aspect = bottomAspect, widthDp = bottomWidthDp, panel = bottomPanel)
        }
    }
}

/**
 * The lid: almost entirely display, inside a thin gold surround.
 *
 * That trim is the device's one piece of jewellery and the quickest thing the eye
 * uses to recognise it, so it is drawn as a real ring around the glass rather than
 * as a tint on the bezel.
 */
@Composable
private fun Lid(frameWidth: Dp, aspect: Float, widthDp: Float, panel: @Composable () -> Unit) {
    Shell(frameWidth = frameWidth) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(frameWidth * LID_PAD),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(frameWidth * SCREEN_ROUND))
                    .background(TRIM)
                    .padding(frameWidth * TRIM_WIDTH),
            ) {
                Screen(
                    aspect = aspect,
                    realWidthDp = widthDp,
                    round = frameWidth * SCREEN_ROUND,
                    content = panel,
                )
            }
        }
    }
}

/**
 * The base: a smaller screen with the controls staggered around it.
 *
 * Left is a stick above the d-pad, right is the buttons above a stick — the offset
 * arrangement this device actually uses, and not a mirror image, which is what a
 * symmetrical drawing gets wrong first.
 */
@Composable
private fun Base(frameWidth: Dp, aspect: Float, widthDp: Float, panel: @Composable () -> Unit) {
    Shell(frameWidth = frameWidth) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(frameWidth * BASE_PAD),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Controls(frameWidth, left = true, modifier = Modifier.weight(CONTROL_COLUMN))

                Box(
                    modifier = Modifier
                        .weight(SCREEN_COLUMN)
                        .clip(RoundedCornerShape(frameWidth * SCREEN_ROUND))
                        .background(BEZEL)
                        .padding(frameWidth * BEZEL_WIDTH),
                ) {
                    Screen(
                        aspect = aspect,
                        realWidthDp = widthDp,
                        round = frameWidth * SCREEN_ROUND,
                        content = panel,
                    )
                }

                Controls(frameWidth, left = false, modifier = Modifier.weight(CONTROL_COLUMN))
            }

            Foot(frameWidth = frameWidth)
        }
    }
}

/** A moulded half of the shell, in the cream the hardware actually is. */
@Composable
private fun Shell(frameWidth: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(frameWidth * SHELL_ROUND))
            .background(Brush.verticalGradient(listOf(SHELL_LIGHT, SHELL_SHADE)))
            .border(
                width = frameWidth * SHELL_EDGE,
                color = SHELL_EDGE_COLOUR,
                shape = RoundedCornerShape(frameWidth * SHELL_ROUND),
            ),
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
    round: Dp,
    content: @Composable () -> Unit,
) {
    val outer = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(MIN_ASPECT, MAX_ASPECT))
            .clip(RoundedCornerShape(round))
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
 * One side's controls: stick over d-pad on the left, buttons over stick on the right.
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

        // The upper item sits high and the lower one low, and which is which is the
        // whole of the stagger: a stick above a d-pad, buttons above a stick.
        val upperY = size.height * UPPER_Y
        val lowerY = size.height * LOWER_Y

        if (left) {
            stick(cx, upperY, unit)
            dpad(cx, lowerY, unit)
        } else {
            faceButtons(cx, upperY, unit)
            stick(cx, lowerY, unit)
        }
    }
}

/** A dished analogue stick in its recess. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.stick(
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val r = unit * STICK_RADIUS
    drawCircle(RECESS, radius = r * 1.34f, center = Offset(cx, cy))
    drawCircle(STICK_RIM, radius = r * 1.14f, center = Offset(cx, cy))
    drawCircle(STICK, radius = r, center = Offset(cx, cy))
    drawCircle(STICK_DISH, radius = r * 0.66f, center = Offset(cx, cy))
}

/** The cross, as one moulded piece rather than four keys. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dpad(
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val arm = unit * DPAD_ARM
    val thick = arm * 0.66f
    val round = CornerRadius(thick * 0.26f)

    drawRoundRect(
        color = DPAD,
        topLeft = Offset(cx - arm, cy - thick / 2f),
        size = Size(arm * 2f, thick),
        cornerRadius = round,
    )
    drawRoundRect(
        color = DPAD,
        topLeft = Offset(cx - thick / 2f, cy - arm),
        size = Size(thick, arm * 2f),
        cornerRadius = round,
    )
    drawCircle(DPAD_PIVOT, radius = thick * 0.26f, center = Offset(cx, cy))
}

/**
 * Four buttons in the Nintendo arrangement, in the device's own colours.
 *
 * X blue at the top, Y green to the left, A red to the right, B yellow beneath —
 * which is the layout and the palette the hardware ships with, and the detail most
 * likely to be noticed if it were wrong.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.faceButtons(
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val spread = unit * FACE_SPREAD
    val r = unit * FACE_RADIUS

    listOf(
        Offset(cx, cy - spread) to BUTTON_X,
        Offset(cx - spread, cy) to BUTTON_Y,
        Offset(cx + spread, cy) to BUTTON_A,
        Offset(cx, cy + spread) to BUTTON_B,
    ).forEach { (centre, colour) ->
        drawCircle(RECESS, radius = r * 1.22f, center = centre)
        drawCircle(colour, radius = r, center = centre)
        // A highlight off the top edge, which is what makes a flat disc read as a
        // moulded cap rather than as a dot.
        drawCircle(
            Color.White.copy(alpha = 0.16f),
            radius = r * 0.52f,
            center = Offset(centre.x, centre.y - r * 0.3f),
        )
    }
}

/** Speaker slots and the ports along the bottom edge. */
@Composable
private fun Foot(frameWidth: Dp) {
    Canvas(
        modifier = Modifier.fillMaxWidth().height(frameWidth * FOOT_HEIGHT),
    ) {
        val slotH = size.height * SPEAKER_HEIGHT
        val slotW = size.width * SPEAKER_WIDTH
        val y = size.height * 0.34f
        val round = CornerRadius(slotH / 2f)

        listOf(size.width * 0.08f, size.width * (0.92f - SPEAKER_WIDTH)).forEach { x ->
            drawRoundRect(
                color = SPEAKER,
                topLeft = Offset(x, y),
                size = Size(slotW, slotH),
                cornerRadius = round,
            )
        }

        // The USB-C port, centred on the lower lip.
        val portW = size.width * PORT_WIDTH
        val portH = size.height * PORT_HEIGHT
        drawRoundRect(
            color = PORT,
            topLeft = Offset((size.width - portW) / 2f, size.height - portH * 1.6f),
            size = Size(portW, portH),
            cornerRadius = CornerRadius(portH / 2f),
        )
    }
}

/** The wide flat hinge the lid folds onto. */
@Composable
private fun Hinge(frameWidth: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(HINGE_WIDTH)
            .height(frameWidth * HINGE_HEIGHT)
            .clip(RoundedCornerShape(frameWidth * HINGE_ROUND))
            .background(Brush.verticalGradient(listOf(HINGE_SHADE, HINGE_LIGHT))),
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
    val heightRatio = topScreenWidth() / top + bottomScreenWidth() / bottom + chromeHeight()

    val wanted = (panelWidthPx / topScreenWidth()).toInt().coerceAtLeast(1)
    val width = minOf(wanted, ceiling, (ceiling / heightRatio).toInt()).coerceAtLeast(1)

    return RecordingFrame(
        width = width,
        // Never zero. A frame of no height is not a small video, it is one the
        // encoder refuses outright — and the width is already floored for the same
        // reason, so leaving the height unguarded was an asymmetry waiting to be
        // found by a display reporting something absurd.
        height = (width * heightRatio).toInt().coerceAtLeast(1),
    )
}

/**
 * What the lid's screen takes of the whole frame, glass only.
 *
 * Functions rather than derived constants only because Kotlin initialises top-level
 * properties in source order, and the constants these read are declared with the
 * rest of the layout at the foot of the file.
 */
internal fun topScreenWidth(): Float = BODY_WIDTH - (LID_PAD + TRIM_WIDTH + SHELL_EDGE) * 2

/** And what the base's takes, once the controls and bezel have theirs. */
internal fun bottomScreenWidth(): Float {
    val inner = BODY_WIDTH - (BASE_PAD + SHELL_EDGE) * 2
    val cutout = inner * (SCREEN_COLUMN / (SCREEN_COLUMN + CONTROL_COLUMN * 2))
    return cutout - BEZEL_WIDTH * 2
}

/** Everything above, below and between the two screens, as a share of frame width. */
private fun chromeHeight(): Float =
    MARGIN * 2 +
        (LID_PAD + TRIM_WIDTH + SHELL_EDGE) * 2 +
        (BASE_PAD + BEZEL_WIDTH + SHELL_EDGE) * 2 +
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

/*
 * The device's own colours, taken from AYN's product photography.
 *
 * The shell is a warm off-white rather than grey, which is the single thing that
 * most decides whether the drawing is recognised — a dark body reads as some other
 * handheld however accurate the rest of the shapes are.
 */
private val BACKDROP = Color(0xFF06070A)
private val SHELL_LIGHT = Color(0xFFEFEBE3)
private val SHELL_SHADE = Color(0xFFD9D3C7)
private val SHELL_EDGE_COLOUR = Color(0xFFC7C0B2)
private val TRIM = Color(0xFFC2A265)
private val BEZEL = Color(0xFF1A1A1C)
private val HINGE_LIGHT = Color(0xFFE4DFD5)
private val HINGE_SHADE = Color(0xFFC9C2B4)

private val RECESS = Color(0x33000000)
private val STICK_RIM = Color(0xFF8E8878)
private val STICK = Color(0xFF4C4C4E)
private val STICK_DISH = Color(0xFF3A3A3C)
private val DPAD = Color(0xFF4C4C4E)
private val DPAD_PIVOT = Color(0xFF5C5C5E)
private val SPEAKER = Color(0xFF9A9384)
private val PORT = Color(0xFF6E675A)

/** The face buttons, in their shipped colours. */
private val BUTTON_X = Color(0xFF2F72C8)
private val BUTTON_Y = Color(0xFF35914B)
private val BUTTON_A = Color(0xFFD03A32)
private val BUTTON_B = Color(0xFFE8B71D)

/*
 * Every dimension is a fraction of the frame's width.
 *
 * Not dp, deliberately: a recording's density is whatever its frame needs, so a body
 * measured in dp would drift against the screens it surrounds as that changed.
 * Fractions keep the console the same shape at any resolution, and let
 * [recordingFrameSize] compute the frame from the very numbers laid out with here.
 */
private const val BODY_WIDTH = 0.96f
private const val MARGIN = 0.022f
private const val SHELL_ROUND = 0.024f
private const val SHELL_EDGE = 0.0016f

/** The lid is nearly all glass; its bezel is the trim and little else. */
private const val LID_PAD = 0.012f
private const val TRIM_WIDTH = 0.004f

private const val BASE_PAD = 0.016f
private const val BEZEL_WIDTH = 0.005f
private const val SCREEN_ROUND = 0.010f

/**
 * The base splits into control, screen, control by weight.
 *
 * Weighted so the base's screen lands near half the frame against the lid's ~0.93:
 * on this hardware the lid is nearly all display while the base gives most of itself
 * to two sticks, a d-pad and four buttons.
 */
private const val SCREEN_COLUMN = 2.09f
private const val CONTROL_COLUMN = 1f
private const val CONTROL_PAD = 0.006f

private const val HINGE_WIDTH = 0.82f
private const val HINGE_HEIGHT = 0.020f
private const val HINGE_ROUND = 0.004f

private const val FOOT_HEIGHT = 0.034f
private const val SPEAKER_WIDTH = 0.11f
private const val SPEAKER_HEIGHT = 0.16f
private const val PORT_WIDTH = 0.07f
private const val PORT_HEIGHT = 0.14f

/** Where the two items in a control column sit, as a share of its height. */
private const val UPPER_Y = 0.30f
private const val LOWER_Y = 0.70f

private const val STICK_RADIUS = 0.19f
private const val DPAD_ARM = 0.23f
private const val FACE_SPREAD = 0.21f
private const val FACE_RADIUS = 0.095f

/** Guards against a display reporting a nonsensical shape. */
private const val MIN_ASPECT = 0.4f
private const val MAX_ASPECT = 3.5f

/** A density of zero would make every dp infinite; far below any real value. */
private const val MIN_DENSITY = 0.05f

private const val TICK_PERIOD_MS = 2_000
private const val TICK_ALPHA = 0.02f

/** Mirrors the recorder's own limit, so the frame arrives already inside it. */
internal const val ENCODER_CEILING = 2160
