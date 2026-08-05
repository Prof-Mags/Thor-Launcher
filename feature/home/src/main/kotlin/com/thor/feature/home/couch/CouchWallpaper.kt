package com.thor.feature.home.couch

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Modifier
import com.thor.core.designsystem.theme.blend
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.CouchWallpaperStyle
import com.thor.core.ui.component.AnimatedWallpaperBackground
import kotlin.math.cos
import kotlin.math.sin

/**
 * Couch mode's own background.
 *
 * The selected game's artwork used to fill this, blurred and darkened behind
 * everything. On a handheld panel that reads as atmosphere; across a room it
 * does not. Three things went wrong with it at television size: the picture
 * changed every time the cursor moved, which is a full-screen crossfade running
 * constantly while somebody is only browsing; text sat over whatever part of the
 * artwork happened to be behind it, so contrast was a different problem for
 * every game in the library; and the largest surface on the screen was spent
 * repeating information the spotlight panel already shows in full.
 *
 * So the background is drawn rather than loaded. It costs no decode, no memory
 * and no network, and it is the same every time — which is what lets the panels
 * above it be designed against a known contrast instead of an arbitrary one.
 *
 * It is not a fixed picture either. Every style is tinted by [accent], the
 * focused system's colour, so the room still shifts as you cross from one console
 * to another, and every style moves — slowly, and in wide shapes, because this is
 * a surface somebody leaves open while they read the panels in front of it. The
 * whole set is drawn from a handful of primitives driven by two floats, which is
 * the same ceiling [AnimatedWallpaperBackground] sets itself and for the same
 * reason: it sits behind everything, so it redraws whenever anything above it
 * does.
 *
 * @param themeWallpaper what [CouchWallpaperStyle.THEME] defers to
 * @param wallpaperImageUri the user's own picture, for that mode only
 */
@Composable
internal fun CouchWallpaper(
    style: CouchWallpaperStyle,
    accent: Color,
    modifier: Modifier = Modifier,
    themeWallpaper: AnimatedWallpaper = AnimatedWallpaper.NONE,
    wallpaperImageUri: String? = null,
) {
    val colors = ThorTheme.colors
    val base = colors.background

    if (style == CouchWallpaperStyle.THEME) {
        // The launcher's own set, for anyone who would rather couch mode looked
        // like the rest of the launcher than like a television.
        AnimatedWallpaperBackground(
            wallpaper = themeWallpaper,
            imageUri = wallpaperImageUri,
            modifier = modifier,
            accentTint = accent,
        )
        return
    }

    /*
     * The transition exists only while it has somewhere to go.
     *
     * An infinite transition with a running animation asks for a frame callback
     * forever, so creating one and animating it from 0f to 0f would switch off
     * the appearance of movement while leaving the frame loop that drove it —
     * behind a screen the user is likely to leave open. Composed conditionally
     * instead; both phases are constant when motion is off, which renders each
     * style as a still composition rather than as nothing at all. The same
     * reasoning, and the same shape, as [AnimatedWallpaperBackground].
     */
    val phase: Float
    val drift: Float
    if (ThorTheme.materials.animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "couch-wallpaper")
        phase = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(style.periodMillis, easing = LinearEasing),
                // Restart for the styles built to wrap at 1f; the rest sway.
                repeatMode = if (style.wraps) RepeatMode.Restart else RepeatMode.Reverse,
            ),
            label = "couch-wallpaper-phase",
        ).value
        // A second, much slower phase. One phase returns every layer to the same
        // arrangement at the same instant, which is what makes a background read
        // as a loop; two let it drift for minutes before repeating.
        drift = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    (style.periodMillis * DRIFT_PERIOD_RATIO).toInt(),
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "couch-wallpaper-drift",
        ).value
    } else {
        phase = STILL_PHASE
        drift = STILL_PHASE
    }

    // Toward the accent rather than to it: a saturated sky behind a dark
    // interface is the thing that makes a launcher look like a demo.
    val sky = base.blend(accent, SKY_TINT)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (style == CouchWallpaperStyle.SOLID) {
                    Brush.verticalGradient(0f to base, 1f to base)
                } else {
                    Brush.verticalGradient(
                        0f to sky,
                        HORIZON to base.blend(accent, HORIZON_TINT),
                        1f to base,
                    )
                },
            ),
    ) {
        if (style == CouchWallpaperStyle.SOLID) return@Box

        Canvas(modifier = Modifier.fillMaxSize()) {
            when (style) {
                CouchWallpaperStyle.RIDGES -> drawRidges(base, accent, drift)
                CouchWallpaperStyle.AURORA -> drawAurora(accent, phase, drift)
                CouchWallpaperStyle.DRIFT -> drawDriftFields(accent, phase, drift)
                CouchWallpaperStyle.HORIZON -> drawHorizonGrid(base, accent, phase)
                CouchWallpaperStyle.EMBERS -> drawEmbers(accent, phase, drift)
                CouchWallpaperStyle.PULSE -> drawPulse(accent, phase)
                // Both returned above; listed so a new style cannot be forgotten.
                CouchWallpaperStyle.THEME, CouchWallpaperStyle.SOLID -> Unit
            }
            drawVignette(base)
        }
    }
}

// ---- Ridges -----------------------------------------------------------------

/**
 * Layered hills, drifting against each other.
 *
 * The parallax is the whole effect: the near ridge travels several times as far
 * as the one behind it, which is the only depth cue available to three flat
 * silhouettes. It sways rather than travelling in one direction because the
 * profile does not repeat on any convenient interval — see [couchRidgeHeight] —
 * so a one-way scroll would need either a seam or a shape chosen for the
 * convenience of the animation rather than for how it looks.
 */
private fun DrawScope.drawRidges(base: Color, accent: Color, drift: Float) {
    val sway = (drift - 0.5f) * RIDGE_SWAY
    drawRidge(base.blend(accent, RIDGE_FAR_TINT), crest = 0.62f, amplitude = 0.05f, phase = 0.15f + sway * 0.35f)
    drawRidge(base.blend(accent, RIDGE_MID_TINT), crest = 0.72f, amplitude = 0.07f, phase = 0.55f + sway * 0.70f)
    drawRidge(base.blend(accent, RIDGE_NEAR_TINT), crest = 0.83f, amplitude = 0.06f, phase = 0.90f + sway)
}

/**
 * One ridge line across the full width.
 *
 * Built from a handful of cubic segments rather than from noise, because the
 * shape has to be derived from its phase alone: a background that reshuffled
 * itself on rotation, on a theme change or on a scale change would be movement
 * nobody asked for behind text they are reading.
 *
 * @param crest how far down the panel the ridge's highest point sits, 0 at the
 *   top and 1 at the bottom
 * @param amplitude how tall the peaks are, as a fraction of the panel's height
 * @param phase shifts the peaks sideways so the three layers do not line up
 */
private fun DrawScope.drawRidge(
    colour: Color,
    crest: Float,
    amplitude: Float,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    val baseY = h * crest
    val peak = h * amplitude

    fun yAt(step: Int): Float =
        baseY + peak * couchRidgeHeight(phase + (step.toFloat() / RIDGE_STEPS) * RIDGE_FREQUENCY)

    val path = Path().apply {
        moveTo(0f, yAt(0))
        for (step in 1..RIDGE_STEPS) {
            val x = w * step.toFloat() / RIDGE_STEPS
            val previousX = w * (step - 1).toFloat() / RIDGE_STEPS
            val midX = previousX + (x - previousX) / 2f
            // Cubic rather than straight: a ridge made of line segments reads as
            // a chart, which is exactly the wrong association behind a library.
            cubicTo(midX, yAt(step - 1), midX, yAt(step), x, yAt(step))
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path = path, color = colour)
}

/**
 * A deterministic ridge profile.
 *
 * Two sine terms at unrelated frequencies, which gives peaks that do not repeat
 * on any obvious interval without needing a random source — and a random source
 * is the thing that would make this different on every composition.
 */
internal fun couchRidgeHeight(t: Float): Float {
    val a = sin(t * TWO_PI)
    val b = sin(t * TWO_PI * SECOND_HARMONIC + HARMONIC_OFFSET)
    return -(a * 0.65f + b * 0.35f)
}

// ---- Aurora -----------------------------------------------------------------

/**
 * Vertical curtains of accent light.
 *
 * Soft-edged rectangles rather than shaped bands: at television size a curtain
 * is read from its gradient and not its outline, and a rectangle with a
 * transparent top and bottom costs one brush where a path costs a tessellation
 * on every frame.
 */
private fun DrawScope.drawAurora(accent: Color, phase: Float, drift: Float) {
    val w = size.width
    val h = size.height
    repeat(AURORA_BANDS) { index ->
        val seed = index.toFloat() / AURORA_BANDS
        // Each band on its own phase offset, so they cross rather than march.
        val wobble = sin((phase + seed) * TWO_PI + index) * AURORA_WANDER
        val centre = w * (seed + wobble + AURORA_INSET)
        val bandWidth = w * (AURORA_WIDTH + 0.04f * cos((drift + seed) * TWO_PI))
        val top = h * (AURORA_TOP + 0.06f * sin((drift + seed) * TWO_PI + index))
        val alpha = AURORA_ALPHA * (0.6f + 0.4f * cos((phase + seed) * TWO_PI))

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to accent.copy(alpha = alpha),
                1f to Color.Transparent,
                startY = top,
                endY = top + h * AURORA_HEIGHT,
            ),
            topLeft = Offset(centre - bandWidth / 2f, top),
            size = Size(bandWidth, h * AURORA_HEIGHT),
        )
    }
}

// ---- Drift ------------------------------------------------------------------

/**
 * Large defocused fields kneading through one another.
 *
 * Each is one radial gradient on a Lissajous path, and the paths are given
 * co-prime-ish rates so the arrangement takes minutes to come back round. Five
 * of them at low alpha reads as a slowly stirred wash without a blur pass, which
 * on a television-sized surface is the difference between free and not.
 */
private fun DrawScope.drawDriftFields(accent: Color, phase: Float, drift: Float) {
    val w = size.width
    val h = size.height
    val radius = maxOf(w, h) * DRIFT_RADIUS

    repeat(DRIFT_FIELDS) { index ->
        val seed = index.toFloat() / DRIFT_FIELDS
        val a = (phase + seed) * TWO_PI
        val b = (drift + seed) * TWO_PI * DRIFT_CROSS_RATE
        val centre = Offset(
            x = w * (0.5f + 0.34f * sin(a + index)),
            y = h * (0.45f + 0.30f * cos(b + index)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = DRIFT_ALPHA), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

// ---- Horizon ----------------------------------------------------------------

/**
 * A perspective grid running away to a horizon line.
 *
 * The rows flow toward the viewer, which is the one style here with real
 * direction to it. It wraps because the rows are positioned by their fractional
 * distance from the horizon and that fraction is taken modulo one — the row that
 * reaches the bottom edge and the row appearing at the horizon are the same row,
 * so there is no seam to hide at the end of the period.
 */
private fun DrawScope.drawHorizonGrid(base: Color, accent: Color, phase: Float) {
    val w = size.width
    val h = size.height
    val horizonY = h * HORIZON
    val depth = h - horizonY

    // The glow along the horizon, which is what sells the grid as distance
    // rather than as a floor tilted toward the camera.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to accent.copy(alpha = GRID_GLOW_ALPHA),
            1f to Color.Transparent,
            startY = horizonY - h * GRID_GLOW_SPREAD,
            endY = horizonY + h * GRID_GLOW_SPREAD,
        ),
        topLeft = Offset(0f, horizonY - h * GRID_GLOW_SPREAD),
        size = Size(w, h * GRID_GLOW_SPREAD * 2f),
    )

    // Rows: near the horizon they crowd together, which is the perspective.
    repeat(GRID_ROWS) { index ->
        val u = couchGridRow(index, GRID_ROWS, phase)
        val y = horizonY + depth * u * u
        drawLine(
            color = accent.copy(alpha = GRID_LINE_ALPHA * u),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = GRID_STROKE * u,
        )
    }

    // Columns converge on the vanishing point and do not move; a grid where both
    // axes travel reads as a camera swinging rather than as ground going past.
    repeat(GRID_COLUMNS + 1) { index ->
        val t = index.toFloat() / GRID_COLUMNS
        val spread = (t - 0.5f) * 2f
        drawLine(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to accent.copy(alpha = GRID_LINE_ALPHA),
                startY = horizonY,
                endY = h,
            ),
            start = Offset(w * 0.5f + spread * w * GRID_VANISH_SPREAD, horizonY),
            end = Offset(w * 0.5f + spread * w * GRID_FLOOR_SPREAD, h),
            strokeWidth = GRID_STROKE,
        )
    }

    // The ground under the grid, so the lines sit on something.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to base.copy(alpha = GRID_FLOOR_ALPHA),
            startY = horizonY,
            endY = h,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, depth),
    )
}

/**
 * Where a grid row sits between the horizon and the bottom edge, 0 to 1.
 *
 * Taken modulo one so the row leaving the bottom of the screen is the row
 * arriving at the horizon, which is what makes the scroll seamless at any period.
 */
internal fun couchGridRow(index: Int, count: Int, phase: Float): Float {
    if (count <= 0) return 0f
    val raw = (index.toFloat() / count) + phase
    val wrapped = raw - kotlin.math.floor(raw)
    return wrapped
}

// ---- Embers -----------------------------------------------------------------

/**
 * Motes rising slowly through the dark.
 *
 * Positions come from the index rather than from a random source, for the reason
 * every other shape here does: this composable is re-entered on a theme change,
 * a rotation and a scale change, and a field that re-scattered itself each time
 * would be a background visibly resetting while somebody was reading over it.
 */
private fun DrawScope.drawEmbers(accent: Color, phase: Float, drift: Float) {
    val w = size.width
    val h = size.height
    repeat(EMBER_COUNT) { index ->
        val seed = couchEmberSeed(index)
        val rise = couchEmberRise(seed, phase)
        val x = w * (seed + EMBER_WANDER * sin((drift + seed) * TWO_PI + index))
        val y = h * (1f - rise)
        // Fading in at the bottom and out at the top is what hides the wrap:
        // a mote is invisible at both the instant it appears and the instant it
        // is recycled.
        val fade = sin(rise * kotlin.math.PI.toFloat())
        val radius = EMBER_RADIUS * (0.6f + 0.4f * seed) * density
        drawCircle(
            color = accent.copy(alpha = EMBER_ALPHA * fade),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/** A stable per-mote value in 0..1, from its index alone. */
internal fun couchEmberSeed(index: Int): Float {
    // The fractional part of an index times an irrational-ish step spreads the
    // motes evenly without clustering, and without a random source.
    val raw = index * EMBER_SEED_STEP
    return raw - kotlin.math.floor(raw)
}

/** How far up the panel a mote has travelled, 0 at the bottom and 1 at the top. */
internal fun couchEmberRise(seed: Float, phase: Float): Float {
    val raw = seed + phase * (EMBER_SLOWEST + seed * EMBER_SPEED_SPREAD)
    return raw - kotlin.math.floor(raw)
}

// ---- Pulse ------------------------------------------------------------------

/**
 * Rings breathing out from behind the panels.
 *
 * The centre sits above the middle of the panel and behind the spotlight, so the
 * rings emerge from under the thing the user is reading rather than from an
 * empty corner. They fade as they expand, which both suggests distance and means
 * the ring at full radius is already invisible when it is recycled.
 */
private fun DrawScope.drawPulse(accent: Color, phase: Float) {
    val centre = Offset(size.width * 0.5f, size.height * PULSE_CENTRE_Y)
    val maxRadius = maxOf(size.width, size.height) * PULSE_REACH
    repeat(PULSE_RINGS) { index ->
        val t = couchGridRow(index, PULSE_RINGS, phase)
        drawCircle(
            color = accent.copy(alpha = PULSE_ALPHA * (1f - t)),
            radius = maxRadius * t,
            center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = PULSE_STROKE * density,
            ),
        )
    }
}

// ---- Shared -----------------------------------------------------------------

/** Darkens the corners so the panels above sit on an even field. */
private fun DrawScope.drawVignette(base: Color) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, base.copy(alpha = VIGNETTE_ALPHA)),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = maxOf(size.width, size.height) * VIGNETTE_RADIUS,
        ),
        size = Size(size.width, size.height),
    )
}

/**
 * Whether the style's phase is built to wrap cleanly at 1f.
 *
 * The ones that do can run in one direction forever; the ones that do not sway
 * back and forth instead, which is not a compromise for hills and curtains — a
 * distant ridge that scrolled steadily in one direction would read as the camera
 * moving, which is a much stronger claim than the background should be making.
 */
private val CouchWallpaperStyle.wraps: Boolean
    get() = when (this) {
        CouchWallpaperStyle.HORIZON,
        CouchWallpaperStyle.EMBERS,
        CouchWallpaperStyle.PULSE,
        -> true

        CouchWallpaperStyle.RIDGES,
        CouchWallpaperStyle.AURORA,
        CouchWallpaperStyle.DRIFT,
        CouchWallpaperStyle.THEME,
        CouchWallpaperStyle.SOLID,
        -> false
    }

/**
 * How long one cycle takes.
 *
 * All of these are slow by the standards of the launcher's own wallpapers,
 * because this one is behind a screen somebody sits in front of rather than one
 * they pass through. Anything that can be watched moving is too fast.
 */
private val CouchWallpaperStyle.periodMillis: Int
    get() = when (this) {
        CouchWallpaperStyle.RIDGES -> 90_000
        CouchWallpaperStyle.AURORA -> 48_000
        CouchWallpaperStyle.DRIFT -> 64_000
        // One cycle moves the grid by exactly one row, so this is the only
        // period here that is a speed rather than a duration.
        CouchWallpaperStyle.HORIZON -> 9_000
        CouchWallpaperStyle.EMBERS -> 34_000
        CouchWallpaperStyle.PULSE -> 16_000
        CouchWallpaperStyle.THEME, CouchWallpaperStyle.SOLID -> 1
    }

/**
 * The phase every style settles to when motion is off.
 *
 * Not zero: at zero the grid's first row sits exactly on the horizon and the
 * embers all start at the bottom edge, which are the two frames in the cycle
 * that look like a mistake rather than a still.
 */
private const val STILL_PHASE = 0.35f

/** Slow enough against the main phase that the two rarely coincide. */
private const val DRIFT_PERIOD_RATIO = 2.7f

private const val TWO_PI = (Math.PI * 2).toFloat()
private const val SECOND_HARMONIC = 2.7f
private const val HARMONIC_OFFSET = 1.3f

/** Enough segments for a smooth crest at 4K, cheap enough to redraw at any size. */
private const val RIDGE_STEPS = 24

/** How many full waves cross the panel. Below two it reads as a single hill. */
private const val RIDGE_FREQUENCY = 2.3f

/** How far the nearest ridge travels over a full cycle, in profile units. */
private const val RIDGE_SWAY = 0.5f

/** Where the wash stops being sky and starts being ground. */
private const val HORIZON = 0.55f

private const val SKY_TINT = 0.10f
private const val HORIZON_TINT = 0.05f
private const val RIDGE_FAR_TINT = 0.07f
private const val RIDGE_MID_TINT = 0.05f
private const val RIDGE_NEAR_TINT = 0.03f

private const val AURORA_BANDS = 5
private const val AURORA_WIDTH = 0.20f
private const val AURORA_WANDER = 0.05f
private const val AURORA_INSET = 0.08f
private const val AURORA_TOP = 0.04f
private const val AURORA_HEIGHT = 0.78f
private const val AURORA_ALPHA = 0.16f

private const val DRIFT_FIELDS = 5
private const val DRIFT_RADIUS = 0.46f
private const val DRIFT_ALPHA = 0.13f
private const val DRIFT_CROSS_RATE = 1.4f

private const val GRID_ROWS = 14
private const val GRID_COLUMNS = 16
private const val GRID_STROKE = 2.2f
private const val GRID_LINE_ALPHA = 0.32f
private const val GRID_GLOW_ALPHA = 0.30f
private const val GRID_GLOW_SPREAD = 0.10f
private const val GRID_FLOOR_ALPHA = 0.55f

/** How far the columns are apart at the horizon, and at the bottom edge. */
private const val GRID_VANISH_SPREAD = 0.06f
private const val GRID_FLOOR_SPREAD = 1.1f

private const val EMBER_COUNT = 34
private const val EMBER_RADIUS = 1.6f
private const val EMBER_ALPHA = 0.42f
private const val EMBER_WANDER = 0.03f

/** Irrational enough that the motes never fall into columns. */
private const val EMBER_SEED_STEP = 0.6180339f

/** The slowest mote's travel per cycle, and how much faster the fastest is. */
private const val EMBER_SLOWEST = 0.6f
private const val EMBER_SPEED_SPREAD = 0.8f

private const val PULSE_RINGS = 6
private const val PULSE_CENTRE_Y = 0.42f
private const val PULSE_REACH = 0.85f
private const val PULSE_ALPHA = 0.20f
private const val PULSE_STROKE = 1.4f

private const val VIGNETTE_ALPHA = 0.55f
private const val VIGNETTE_RADIUS = 0.85f
