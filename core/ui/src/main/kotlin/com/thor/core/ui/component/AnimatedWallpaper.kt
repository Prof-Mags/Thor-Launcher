package com.thor.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.blend
import com.thor.core.model.AnimatedWallpaper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The launcher's background.
 *
 * Renders the user's chosen wallpaper image, the selected [AnimatedWallpaper]
 * effect, or both — the effect is drawn *over* the image so a photo wallpaper
 * still picks up the theme's motion rather than the two being mutually
 * exclusive. A grain and vignette pass finishes every mode.
 *
 * Each effect is a handful of shapes driven by one or two animated floats. That
 * is a deliberate ceiling: this composable sits behind everything else on both
 * displays, so it redraws whenever anything above it does, and a real particle
 * system here would tax the GPU permanently for something almost nobody looks at
 * directly.
 *
 * When motion is disabled — reduce-motion or performance mode — the animation
 * settles to a constant phase and the effect renders as a static composition
 * rather than disappearing, so the theme still reads as intended.
 *
 * @param accentTint hue for [AnimatedWallpaper.ADAPTIVE]; when null that mode
 *   falls back to the theme's own accent
 */
@Composable
fun AnimatedWallpaperBackground(
    wallpaper: AnimatedWallpaper,
    imageUri: String?,
    modifier: Modifier = Modifier,
    /** Drawn beneath everything; defaults to the theme background. */
    baseColor: Color = ThorTheme.colors.background,
    accentTint: Color? = null,
) {
    val colors = ThorTheme.colors
    val spec = ThorTheme.spec
    val animate = ThorTheme.materials.animationsEnabled

    /*
     * The transition exists only while it has somewhere to go.
     *
     * These were created unconditionally and animated from 0f to 0f when motion
     * was off — which is not the same as not animating. An infinite transition
     * with a running animation asks for a frame callback forever, so the
     * launcher never went idle: performance mode and reduce-motion switched off
     * the *appearance* of movement while leaving the frame loop that drove it,
     * behind every panel, for as long as the launcher was on screen.
     *
     * Composed conditionally instead. Both phases are zero when motion is off,
     * which is exactly what they evaluated to before, so nothing looks different.
     */
    val phase: Float
    val drift: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "wallpaper")
        phase = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = wallpaper.periodMillis,
                    easing = LinearEasing,
                ),
                repeatMode = if (wallpaper.pingPongs) RepeatMode.Reverse else RepeatMode.Restart,
            ),
            label = "wallpaperPhase",
        ).value

        // A second, slower phase on a co-prime period. One phase makes every
        // layer return to the same arrangement at the same instant, which reads
        // as a loop; two make the composition drift for minutes before repeating.
        drift = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (wallpaper.periodMillis * DRIFT_PERIOD_RATIO).toInt(),
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wallpaperDrift",
        ).value
    } else {
        phase = 0f
        drift = 0f
    }

    // Cross-faded rather than switched, so moving the cursor between systems in
    // ADAPTIVE mode is a wash of colour rather than a hard cut.
    val adaptive by animateColorAsState(
        targetValue = accentTint ?: colors.primary,
        animationSpec = ThorTheme.motion.tweenSpec(ThorTheme.motion.backdropMillis * 2),
        label = "adaptiveTint",
    )

    /*
     * The theme's own ground, under everything else.
     *
     * A launcher background used to be one flat colour with an effect drawn over
     * it, which is why the presets with their effects turned off were
     * indistinguishable from each other: at that point a theme *was* one
     * rectangle of one colour. The wash graduates the base toward the accent over
     * the height of the panel, so a theme still reads as itself with every effect
     * off — and it sits beneath the wallpaper image rather than replacing it.
     */
    val depth = ThorTheme.materials.backgroundDepth
    val ground = remember(baseColor, colors.primary, depth) {
        if (depth <= 0f) {
            Brush.verticalGradient(listOf(baseColor, baseColor))
        } else {
            Brush.verticalGradient(
                listOf(
                    baseColor.blend(Color.Black, depth * 0.35f),
                    baseColor,
                    baseColor.blend(colors.primary, depth),
                ),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ground)) {
        if (imageUri != null) {
            ArtworkImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when (wallpaper) {
            AnimatedWallpaper.NONE -> Unit

            AnimatedWallpaper.MESH ->
                MeshLayer(phase, drift, colors.primary, colors.secondary, colors.accentEnd)

            AnimatedWallpaper.AURORA ->
                AuroraLayer(phase, drift, colors.primary, colors.secondary, colors.accentEnd)

            AnimatedWallpaper.WAVES ->
                WaveLayer(phase, colors.primary, colors.secondary)

            AnimatedWallpaper.BOKEH ->
                BokehLayer(phase, drift, colors.primary, colors.accentEnd)

            AnimatedWallpaper.PARTICLES ->
                PointFieldLayer(phase, colors.primary, count = 54, twinkle = false)

            AnimatedWallpaper.STARFIELD ->
                PointFieldLayer(phase, colors.onBackground, count = 110, twinkle = true)

            AnimatedWallpaper.GRADIENT_DRIFT ->
                GradientDriftLayer(phase, colors.primary, colors.secondary, colors.accentEnd)

            AnimatedWallpaper.PARALLAX_ART ->
                ParallaxLayer(phase, drift, colors.primary, colors.secondary)

            // Reuses the mesh geometry, driven by the highlighted platform's
            // colour instead of the theme's — the effect is the tint, not a
            // different arrangement.
            AnimatedWallpaper.ADAPTIVE ->
                MeshLayer(
                    phase = phase,
                    drift = drift,
                    first = adaptive,
                    second = adaptive.blend(colors.secondary, 0.45f),
                    third = adaptive.blend(Color.White, 0.3f),
                )
        }

        // Vignette then grain, over everything including a photo wallpaper.
        // Without these the procedural gradients band visibly on an OLED panel
        // and the whole background reads as a compression artefact.
        if (wallpaper != AnimatedWallpaper.NONE || imageUri != null) {
            Vignette(baseColor)
        }
        if (spec.grain > 0f) {
            GrainOverlay(strength = spec.grain, dark = colors.isDark)
        }

        /*
         * The user's dim, last of all.
         *
         * Over the grain and the vignette rather than under them, because it is
         * not part of the background's own look — it is the amount by which the
         * whole background gets out of the way of what is drawn on top. Toward the
         * theme's ground rather than toward black, so dimming a light theme
         * brightens the field instead of dropping a grey sheet over it.
         */
        val dim = ThorTheme.materials.wallpaperDim
        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = dim.coerceIn(0f, 1f))),
            )
        }
    }
}

/**
 * Overlapping soft colour fields.
 *
 * The modern default: several large radial stops at low alpha, offset on two
 * phases so the field kneads rather than orbits. Cheaper than it looks — five
 * radial gradients, no blur pass.
 */
@Composable
private fun MeshLayer(
    phase: Float,
    drift: Float,
    first: Color,
    second: Color,
    third: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val a = phase * TWO_PI
        val b = drift * TWO_PI
        val blobs = listOf(
            MeshBlob(0.18f + 0.12f * cos(a), 0.22f + 0.10f * sin(b), 0.85f, first, 0.34f),
            MeshBlob(0.82f - 0.11f * sin(a), 0.28f + 0.13f * cos(b * 0.8f), 0.78f, second, 0.30f),
            MeshBlob(0.30f + 0.09f * sin(b), 0.82f - 0.10f * cos(a * 0.6f), 0.90f, third, 0.26f),
            MeshBlob(0.72f + 0.10f * cos(b * 1.3f), 0.74f + 0.08f * sin(a), 0.70f, first, 0.22f),
            MeshBlob(0.50f + 0.14f * sin(a * 0.45f), 0.48f, 1.05f, second, 0.18f),
        )
        blobs.forEach { blob ->
            val center = Offset(size.width * blob.x, size.height * blob.y)
            val radius = safeRadius(size.maxDimension * blob.radius)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(blob.color.copy(alpha = blob.alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                // Additive, so overlaps brighten into new hues instead of the
                // topmost blob simply covering the ones beneath.
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Slow, overlapping radial blooms with a horizon. */
@Composable
private fun AuroraLayer(
    phase: Float,
    drift: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val a = phase * TWO_PI
        val b = drift * TWO_PI

        // Curtains: tall, narrow, vertically faded — the part that reads as an
        // aurora rather than as three coloured circles.
        val curtains = listOf(
            Triple(0.24f + 0.08f * cos(a), primary, 0.30f),
            Triple(0.52f + 0.10f * sin(b), accent, 0.24f),
            Triple(0.78f - 0.07f * sin(a * 0.7f), secondary, 0.28f),
        )
        curtains.forEach { (x, color, alpha) ->
            val cx = size.width * x
            val width = safeRadius(size.width * 0.42f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, size.height * 0.34f),
                    radius = width,
                ),
                topLeft = Offset(cx - width, 0f),
                size = Size(width * 2f, size.height),
                blendMode = BlendMode.Plus,
            )
        }

        // A low band anchoring the curtains, so the effect has a ground.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, primary.copy(alpha = 0.14f)),
                startY = size.height * 0.55f,
                endY = size.height,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Stacked flowing bands.
 *
 * Each band is a sampled sine filled to the bottom of the canvas, so they layer
 * into depth. Sampled every few pixels rather than every pixel: at this scale
 * the difference is invisible and the path is an order of magnitude cheaper.
 */
@Composable
private fun WaveLayer(phase: Float, primary: Color, secondary: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val bands = 4
        repeat(bands) { index ->
            val depth = index / (bands - 1f)
            val color = primary.blend(secondary, depth)
            val amplitude = size.height * (0.05f + 0.03f * (1f - depth))
            val baseline = size.height * (0.42f + 0.16f * depth)
            val frequency = TWO_PI * (1.2f + 0.5f * index)
            val shift = phase * TWO_PI * (if (index % 2 == 0) 1f else -1f)

            val path = Path().apply {
                moveTo(0f, baseline + amplitude * sin(shift))
                var x = 0f
                while (x <= size.width) {
                    val t = x / size.width
                    lineTo(x, baseline + amplitude * sin(t * frequency + shift))
                    x += WAVE_STEP_PX
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.20f - 0.03f * index),
                        Color.Transparent,
                    ),
                    startY = baseline,
                    endY = size.height,
                ),
            )
        }
    }
}

/**
 * Large defocused orbs at several depths.
 *
 * Depth drives size, alpha and speed together, which is what produces the
 * illusion of a focal plane rather than a flat scatter of circles.
 */
@Composable
private fun BokehLayer(phase: Float, drift: Float, primary: Color, accent: Color) {
    val orbs = remember {
        val random = Random(seed = SEED)
        List(BOKEH_COUNT) {
            val depth = random.nextFloat()
            OrbSpec(
                x = random.nextFloat(),
                y = random.nextFloat(),
                depth = depth,
                phaseOffset = random.nextFloat(),
                warm = random.nextBoolean(),
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        orbs.forEach { orb ->
            val wobble = sin((phase + orb.phaseOffset) * TWO_PI) * (0.02f + 0.05f * orb.depth)
            val rise = (orb.y - drift * (0.05f + 0.12f * orb.depth) + 1f) % 1f
            val radius = safeRadius(size.minDimension * (0.05f + 0.16f * orb.depth))
            val center = Offset(size.width * (orb.x + wobble), size.height * rise)
            val color = if (orb.warm) accent else primary
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.05f + 0.13f * (1f - orb.depth)),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** A wide multi-stop gradient sliding across the surface. */
@Composable
private fun GradientDriftLayer(
    phase: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val travel = size.width * 1.6f
        val start = -travel * 0.5f + phase * travel
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.28f to primary.copy(alpha = 0.24f),
                    0.5f to accent.copy(alpha = 0.16f),
                    0.72f to secondary.copy(alpha = 0.22f),
                    1.0f to Color.Transparent,
                ),
                start = Offset(start, 0f),
                end = Offset(start + travel * 0.9f, size.height),
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Drifting points, used for both the particle and starfield effects.
 *
 * Positions are generated once from a fixed seed so the field is stable across
 * recompositions — a re-randomised layout every frame would read as noise.
 * Radius, speed and brightness all track one depth value, so the field has
 * front and back rather than being uniformly scattered.
 */
@Composable
private fun PointFieldLayer(
    phase: Float,
    color: Color,
    count: Int,
    twinkle: Boolean,
) {
    val points = remember(count) {
        val random = Random(seed = SEED)
        List(count) {
            val depth = random.nextFloat()
            PointSpec(
                x = random.nextFloat(),
                y = random.nextFloat(),
                depth = depth,
                offset = random.nextFloat(),
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        points.forEach { point ->
            // Points wrap vertically rather than being respawned, which keeps
            // the field a pure function of the phase and needs no state.
            val speed = 0.2f + 0.9f * point.depth
            val drift = (point.y + phase * speed) % 1f
            val base = 0.12f + 0.5f * point.depth
            val alpha = if (twinkle) {
                base * (0.55f + 0.45f * sin((phase + point.offset) * TWO_PI * 3f))
            } else {
                base
            }
            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = (0.5f + 1.9f * point.depth) * density,
                center = Offset(size.width * point.x, size.height * drift),
            )
        }
    }
}

/**
 * Layered shapes moving at different rates.
 *
 * Actually parallaxes now. This used to be a single vignette breathe, which is
 * why the mode never looked like its name — three bands at different speeds is
 * the cheapest thing that genuinely reads as depth.
 */
@Composable
private fun ParallaxLayer(phase: Float, drift: Float, primary: Color, secondary: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val layers = listOf(
            ParallaxBand(0.72f, 0.9f, 0.16f, secondary),
            ParallaxBand(0.82f, 0.55f, 0.20f, primary),
            ParallaxBand(0.92f, 0.28f, 0.26f, secondary),
        )
        layers.forEach { band ->
            // Far layers travel less, which is the whole of the effect.
            val shift = (phase * 2f - 1f) * size.width * 0.10f * band.speed
            val lift = sin(drift * TWO_PI) * size.height * 0.012f * band.speed
            val top = size.height * band.top + lift
            val path = Path().apply {
                moveTo(-size.width * 0.2f + shift, top)
                cubicTo(
                    size.width * 0.25f + shift, top - size.height * 0.06f,
                    size.width * 0.75f + shift, top + size.height * 0.05f,
                    size.width * 1.2f + shift, top - size.height * 0.02f,
                )
                lineTo(size.width * 1.2f + shift, size.height)
                lineTo(-size.width * 0.2f + shift, size.height)
                close()
            }
            drawPath(path = path, color = band.color.copy(alpha = band.alpha))
        }
    }
}

/**
 * Darkens the edges toward the base colour.
 *
 * Keeps attention in the middle of the panel and hides the seam where a
 * procedural layer runs out, which is otherwise a visible straight edge.
 */
@Composable
private fun Vignette(baseColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1.0f to baseColor.copy(alpha = 0.52f),
                ),
                center = Offset(size.width / 2f, size.height * 0.46f),
                radius = safeRadius(size.maxDimension * 0.78f),
            ),
        )
    }
}

/**
 * Static film grain.
 *
 * Deliberately static rather than re-rolled per frame: animated noise on a
 * launcher background is distracting and forces a full redraw every frame, while
 * a fixed pattern does the one job that matters — breaking up the colour bands
 * that large flat gradients produce on an OLED panel.
 */
@Composable
private fun GrainOverlay(strength: Float, dark: Boolean) {
    val specks = remember {
        val random = Random(seed = GRAIN_SEED)
        List(GRAIN_COUNT) {
            GrainSpeck(
                x = random.nextFloat(),
                y = random.nextFloat(),
                weight = random.nextFloat(),
            )
        }
    }
    val tint = if (dark) Color.White else Color.Black

    Canvas(modifier = Modifier.fillMaxSize()) {
        specks.forEach { speck ->
            drawCircle(
                color = tint.copy(alpha = strength * speck.weight * GRAIN_ALPHA_SCALE),
                radius = density * 0.7f,
                center = Offset(size.width * speck.x, size.height * speck.y),
            )
        }
    }
}

private data class MeshBlob(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val alpha: Float,
)

private data class OrbSpec(
    val x: Float,
    val y: Float,
    /** 0 = far and faint, 1 = near, large and fast. */
    val depth: Float,
    val phaseOffset: Float,
    val warm: Boolean,
)

private data class PointSpec(
    val x: Float,
    val y: Float,
    val depth: Float,
    val offset: Float,
)

private data class ParallaxBand(
    val top: Float,
    val speed: Float,
    val alpha: Float,
    val color: Color,
)

private data class GrainSpeck(val x: Float, val y: Float, val weight: Float)

/**
 * Clamps a computed gradient radius to something the platform will accept.
 *
 * `android.graphics.RadialGradient` throws on a radius of zero or less, and every
 * radius here is derived from the canvas size — so a layer measured at zero, which
 * happens on the frame a panel is attached or detached, would take the whole
 * launcher down rather than simply drawing nothing.
 */
private fun safeRadius(value: Float): Float =
    if (value.isFinite() && value > MIN_GRADIENT_RADIUS) value else MIN_GRADIENT_RADIUS

/** How long one full cycle of an effect takes. */
private val AnimatedWallpaper.periodMillis: Int
    get() = when (this) {
        AnimatedWallpaper.NONE -> 1
        AnimatedWallpaper.MESH -> 26_000
        AnimatedWallpaper.AURORA -> 20_000
        AnimatedWallpaper.WAVES -> 16_000
        AnimatedWallpaper.BOKEH -> 34_000
        AnimatedWallpaper.PARTICLES -> 28_000
        AnimatedWallpaper.STARFIELD -> 36_000
        AnimatedWallpaper.GRADIENT_DRIFT -> 15_000
        AnimatedWallpaper.PARALLAX_ART -> 22_000
        AnimatedWallpaper.ADAPTIVE -> 26_000
    }

/** Effects that read better reversing than snapping back to the start. */
private val AnimatedWallpaper.pingPongs: Boolean
    get() = when (this) {
        AnimatedWallpaper.MESH,
        AnimatedWallpaper.AURORA,
        AnimatedWallpaper.GRADIENT_DRIFT,
        AnimatedWallpaper.PARALLAX_ART,
        AnimatedWallpaper.ADAPTIVE,
        -> true

        // Waves, point fields and bokeh all travel in one direction; reversing
        // them would visibly rewind.
        else -> false
    }

/**
 * Ratio between the drift phase's period and the main one.
 *
 * Irrational-ish on purpose: a whole-number ratio would bring both phases back
 * into alignment on every cycle, which is exactly the looping the second phase
 * exists to hide.
 */
private const val DRIFT_PERIOD_RATIO = 1.618f

/** Fixed seeds so the generated fields are identical on every recomposition. */
private const val SEED = 0x7405
private const val GRAIN_SEED = 0x51AB

private const val BOKEH_COUNT = 18
private const val GRAIN_COUNT = 900

/** Keeps the per-theme grain values in a usable range. */
private const val GRAIN_ALPHA_SCALE = 0.55f

/** Below this a radial gradient is rejected by the platform. */
private const val MIN_GRADIENT_RADIUS = 1f

private const val WAVE_STEP_PX = 12f

private val TWO_PI = (2.0 * Math.PI).toFloat()
