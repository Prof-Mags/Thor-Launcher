package com.thor.feature.topscreen.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thor.core.model.PlatformGlyph
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

/**
 * Loki's platform icon language: rounded, open line work in the platform accent.
 *
 * Drawn rather than shipped as vectors because every one of them is tinted with
 * the system's own accent, and a compiled-in drawable cannot take a colour that
 * is computed from the platform's palette at runtime.
 *
 * Three rules hold the set together, and each of them was being broken:
 *
 * Every glyph is drawn at [MAIN] weight, with [LIGHT] reserved for detail that
 * is genuinely secondary — a sheen on a disc, a motion arc. There were six
 * different widths in here, chosen per glyph, so a row of them read as icons
 * from three different sets.
 *
 * Every glyph fills the same optical box, about a seventh in from each edge.
 * Nothing enforces that — a Canvas has no opinion — but each of them keeps to
 * it. Some were running to the full extent and others stopping well short, and
 * at the 34dp these are drawn at that is the difference between two icons
 * looking the same size and one looking broken.
 *
 * Every glyph is its own drawing. Four pairs shared one — a "StreetPass" and a
 * "Local multiplayer" highlight drew the identical mark — which is the one fault
 * here the user can actually name, because it makes two different facts about a
 * console look like the same fact stated twice.
 */
@Composable
internal fun PlatformLineIcon(
    glyph: PlatformGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val pen = LinePen(this, tint)
        when (glyph) {
            PlatformGlyph.GAME_LIBRARY -> pen.gamepad()
            PlatformGlyph.FAVOURITE -> pen.star()
            PlatformGlyph.CLOCK -> pen.clock(withProgress = false)
            PlatformGlyph.PLAYTIME -> pen.clock(withProgress = true)
            PlatformGlyph.PLAY -> pen.play()
            PlatformGlyph.DUAL_SCREEN -> pen.dualScreen()
            PlatformGlyph.DEPTH -> pen.depth()
            PlatformGlyph.TOUCH -> pen.touch()
            PlatformGlyph.SOCIAL -> pen.social()
            PlatformGlyph.MULTIPLAYER -> pen.multiplayer()
            PlatformGlyph.MOTION -> pen.motion()
            PlatformGlyph.HYBRID -> pen.hybrid()
            PlatformGlyph.PORTABLE -> pen.portable()
            PlatformGlyph.LINK -> pen.link()
            PlatformGlyph.DISC -> pen.disc()
            PlatformGlyph.CUBE -> pen.cube()
            PlatformGlyph.ONLINE -> pen.globe()
            PlatformGlyph.ARCADE -> pen.arcade()
            PlatformGlyph.PERFORMANCE -> pen.speedometer()
            PlatformGlyph.KEYBOARD -> pen.keyboard()
            PlatformGlyph.MEDIA -> pen.filmStrip()
            PlatformGlyph.CLASSICS -> pen.cartridge()
        }
    }
}

/**
 * The drawing surface, in glyph coordinates.
 *
 * Everything below works in a 0..1 square that is centred in whatever box the
 * caller gave, so a glyph is written once and is correct at any size. The helper
 * methods exist so that a glyph reads as a description of a shape rather than as
 * arithmetic — which is what the previous set had become, with every point
 * spelled out twice to build a path.
 */
private class LinePen(private val scope: DrawScope, private val tint: Color) {

    private val unit = scope.size.minDimension
    private val originX = (scope.size.width - unit) / 2f
    private val originY = (scope.size.height - unit) / 2f

    private fun at(x: Float, y: Float) = Offset(originX + x * unit, originY + y * unit)

    private fun strokeOf(width: Float) =
        Stroke(unit * width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = MAIN) {
        scope.drawLine(tint, at(x1, y1), at(x2, y2), unit * width, StrokeCap.Round)
    }

    fun circle(x: Float, y: Float, radius: Float, width: Float = MAIN) {
        scope.drawCircle(tint, unit * radius, at(x, y), style = strokeOf(width))
    }

    fun dot(x: Float, y: Float, radius: Float = DOT) {
        scope.drawCircle(tint, unit * radius, at(x, y), style = Fill)
    }

    fun box(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 0.07f,
        width: Float = MAIN,
    ) {
        scope.drawRoundRect(
            color = tint,
            topLeft = at(left, top),
            size = Size((right - left) * unit, (bottom - top) * unit),
            cornerRadius = CornerRadius(radius * unit),
            style = strokeOf(width),
        )
    }

    fun arc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweep: Float,
        width: Float = MAIN,
        alpha: Float = 1f,
    ) {
        scope.drawArc(
            color = tint.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = at(left, top),
            size = Size((right - left) * unit, (bottom - top) * unit),
            style = strokeOf(width),
        )
    }

    /** A closed outline through the given points. */
    fun shape(vararg points: Pair<Float, Float>, width: Float = MAIN, filled: Boolean = false) {
        val path = Path()
        points.forEachIndexed { index, (x, y) ->
            val point = at(x, y)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        scope.drawPath(path, tint, style = if (filled) Fill else strokeOf(width))
    }

    // ------------------------------------------------------------- the glyphs

    /**
     * A gamepad, which is the general "games" mark.
     *
     * Grips rather than a plain rounded box: a pad without them is a remote
     * control, and at this size the silhouette is doing most of the work.
     */
    fun gamepad() {
        val path = Path()
        val start = at(0.34f, 0.34f)
        path.moveTo(start.x, start.y)
        path.cubicTo(
            at(0.18f, 0.34f).x, at(0.18f, 0.34f).y,
            at(0.12f, 0.70f).x, at(0.12f, 0.70f).y,
            at(0.24f, 0.74f).x, at(0.24f, 0.74f).y,
        )
        path.cubicTo(
            at(0.33f, 0.77f).x, at(0.33f, 0.77f).y,
            at(0.34f, 0.62f).x, at(0.34f, 0.62f).y,
            at(0.43f, 0.62f).x, at(0.43f, 0.62f).y,
        )
        path.lineTo(at(0.57f, 0.62f).x, at(0.57f, 0.62f).y)
        path.cubicTo(
            at(0.66f, 0.62f).x, at(0.66f, 0.62f).y,
            at(0.67f, 0.77f).x, at(0.67f, 0.77f).y,
            at(0.76f, 0.74f).x, at(0.76f, 0.74f).y,
        )
        path.cubicTo(
            at(0.88f, 0.70f).x, at(0.88f, 0.70f).y,
            at(0.82f, 0.34f).x, at(0.82f, 0.34f).y,
            at(0.66f, 0.34f).x, at(0.66f, 0.34f).y,
        )
        path.close()
        scope.drawPath(path, tint, style = strokeOf(MAIN))

        line(0.27f, 0.47f, 0.39f, 0.47f, LIGHT)
        line(0.33f, 0.41f, 0.33f, 0.53f, LIGHT)
        dot(0.66f, 0.43f)
        dot(0.74f, 0.51f)
    }

    fun star() {
        val points = (0 until 10).map { index ->
            val radius = if (index % 2 == 0) 0.34f else 0.15f
            val angle = -PI / 2 + index * PI / 5
            (0.5f + cos(angle).toFloat() * radius) to (0.5f + sin(angle).toFloat() * radius)
        }
        shape(*points.toTypedArray())
    }

    /**
     * A clock, and the same clock with an elapsed ring around it.
     *
     * The ring is the whole difference between "time" and "time spent", and it
     * used to be a faint arc laid over an identical face — near enough invisible
     * at this size, which is why the two glyphs read as one.
     */
    fun clock(withProgress: Boolean) {
        val face = if (withProgress) 0.26f else 0.33f
        circle(0.5f, 0.5f, face)
        line(0.5f, 0.5f, 0.5f, 0.5f - face * 0.62f, LIGHT)
        line(0.5f, 0.5f, 0.5f + face * 0.55f, 0.5f + face * 0.30f, LIGHT)
        if (withProgress) {
            arc(0.13f, 0.13f, 0.87f, 0.87f, startAngle = -90f, sweep = 250f)
        }
    }

    /** A play triangle inside its button, which is how it is met everywhere else. */
    fun play() {
        circle(0.5f, 0.5f, 0.34f)
        shape(0.42f to 0.35f, 0.68f to 0.5f, 0.42f to 0.65f, filled = true)
    }

    /**
     * Two screens and a hinge — the launcher's own shape.
     *
     * Deliberately the same drawing as the app icon: this glyph appears on the
     * platform this launcher was built for, and the two marks agreeing is worth
     * more than either being individually optimal.
     */
    fun dualScreen() {
        box(0.20f, 0.16f, 0.80f, 0.44f, radius = 0.05f)
        line(0.34f, 0.50f, 0.66f, 0.50f, LIGHT)
        box(0.26f, 0.56f, 0.74f, 0.84f, radius = 0.05f)
    }

    /**
     * Depth, as a receding stack.
     *
     * Two overlapping circles were the old drawing, which is the mark for a
     * Venn diagram or for stereo audio and reads as neither depth nor 3D.
     */
    fun depth() {
        box(0.14f, 0.30f, 0.62f, 0.78f, radius = 0.05f, width = LIGHT)
        box(0.26f, 0.24f, 0.74f, 0.72f, radius = 0.05f, width = LIGHT)
        box(0.38f, 0.18f, 0.86f, 0.66f, radius = 0.05f)
    }

    /** A screen with a fingertip on it, and the ripple that makes it a touch. */
    fun touch() {
        box(0.16f, 0.14f, 0.68f, 0.86f, radius = 0.06f)
        line(0.32f, 0.22f, 0.52f, 0.22f, LIGHT)
        dot(0.48f, 0.52f, 0.055f)
        arc(0.32f, 0.36f, 0.64f, 0.68f, startAngle = -60f, sweep = 120f, width = LIGHT)
        arc(0.24f, 0.28f, 0.72f, 0.76f, startAngle = -55f, sweep = 110f, width = LIGHT, alpha = 0.55f)
    }

    /** Two figures and the exchange between them. */
    fun social() {
        circle(0.32f, 0.30f, 0.11f)
        arc(0.14f, 0.46f, 0.50f, 0.82f, startAngle = 180f, sweep = 180f)
        circle(0.68f, 0.30f, 0.11f)
        arc(0.50f, 0.46f, 0.86f, 0.82f, startAngle = 180f, sweep = 180f)
        line(0.44f, 0.62f, 0.56f, 0.62f, LIGHT)
    }

    /** Three figures: a group rather than a pair, so it is not [social] again. */
    fun multiplayer() {
        circle(0.5f, 0.24f, 0.10f)
        arc(0.32f, 0.38f, 0.68f, 0.74f, startAngle = 180f, sweep = 180f)
        circle(0.20f, 0.42f, 0.085f, LIGHT)
        arc(0.05f, 0.55f, 0.35f, 0.85f, startAngle = 180f, sweep = 180f, width = LIGHT)
        circle(0.80f, 0.42f, 0.085f, LIGHT)
        arc(0.65f, 0.55f, 0.95f, 0.85f, startAngle = 180f, sweep = 180f, width = LIGHT)
    }

    /** A controller mid-swing, which is what motion control looks like. */
    fun motion() {
        box(0.36f, 0.20f, 0.64f, 0.80f, radius = 0.11f)
        dot(0.50f, 0.31f, 0.035f)
        line(0.44f, 0.52f, 0.56f, 0.52f, LIGHT)
        arc(0.10f, 0.28f, 0.32f, 0.72f, startAngle = 120f, sweep = 120f, width = LIGHT)
        arc(0.68f, 0.28f, 0.90f, 0.72f, startAngle = -60f, sweep = 120f, width = LIGHT)
    }

    /** A console body with its two rails off the sides. */
    fun hybrid() {
        box(0.28f, 0.28f, 0.72f, 0.72f, radius = 0.04f)
        box(0.10f, 0.22f, 0.24f, 0.78f, radius = 0.06f, width = LIGHT)
        box(0.76f, 0.22f, 0.90f, 0.78f, radius = 0.06f, width = LIGHT)
        dot(0.17f, 0.38f, 0.03f)
        dot(0.83f, 0.62f, 0.03f)
    }

    /** A handheld: screen in the middle, pad one side, buttons the other. */
    fun portable() {
        box(0.10f, 0.26f, 0.90f, 0.74f, radius = 0.12f)
        box(0.34f, 0.34f, 0.66f, 0.66f, radius = 0.03f, width = LIGHT)
        line(0.18f, 0.50f, 0.28f, 0.50f, LIGHT)
        line(0.23f, 0.45f, 0.23f, 0.55f, LIGHT)
        dot(0.76f, 0.45f, 0.03f)
        dot(0.82f, 0.55f, 0.03f)
    }

    /** Two devices talking to each other, side by side. */
    fun link() {
        box(0.08f, 0.24f, 0.34f, 0.76f, radius = 0.05f)
        box(0.66f, 0.24f, 0.92f, 0.76f, radius = 0.05f)
        line(0.38f, 0.42f, 0.62f, 0.42f, LIGHT)
        line(0.38f, 0.58f, 0.62f, 0.58f, LIGHT)
        dot(0.50f, 0.50f, 0.03f)
    }

    fun disc() {
        circle(0.5f, 0.5f, 0.34f)
        circle(0.5f, 0.5f, 0.085f, LIGHT)
        arc(0.24f, 0.24f, 0.76f, 0.76f, startAngle = 200f, sweep = 80f, width = LIGHT, alpha = 0.6f)
    }

    fun cube() {
        shape(
            0.50f to 0.14f,
            0.82f to 0.32f,
            0.82f to 0.68f,
            0.50f to 0.86f,
            0.18f to 0.68f,
            0.18f to 0.32f,
        )
        line(0.18f, 0.32f, 0.50f, 0.50f, LIGHT)
        line(0.82f, 0.32f, 0.50f, 0.50f, LIGHT)
        line(0.50f, 0.50f, 0.50f, 0.86f, LIGHT)
    }

    /** A globe: the outline, one meridian, and two latitudes. */
    fun globe() {
        circle(0.5f, 0.5f, 0.34f)
        scope.drawOval(
            color = tint,
            topLeft = at(0.36f, 0.16f),
            size = Size(0.28f * unit, 0.68f * unit),
            style = strokeOf(LIGHT),
        )
        line(0.17f, 0.50f, 0.83f, 0.50f, LIGHT)
        arc(0.20f, 0.28f, 0.80f, 0.62f, startAngle = 200f, sweep = 140f, width = LIGHT)
    }

    /** A cabinet with a stick and two buttons on the panel. */
    fun arcade() {
        box(0.20f, 0.16f, 0.80f, 0.84f, radius = 0.06f)
        line(0.20f, 0.52f, 0.80f, 0.52f, LIGHT)
        line(0.42f, 0.72f, 0.42f, 0.62f, LIGHT)
        dot(0.42f, 0.60f, 0.045f)
        dot(0.58f, 0.68f, 0.032f)
        dot(0.67f, 0.72f, 0.032f)
    }

    /** A dial with its needle past the middle. */
    fun speedometer() {
        arc(0.14f, 0.22f, 0.86f, 0.94f, startAngle = 180f, sweep = 180f)
        line(0.50f, 0.58f, 0.70f, 0.36f)
        dot(0.50f, 0.58f, 0.045f)
        dot(0.22f, 0.52f, 0.028f)
        dot(0.34f, 0.32f, 0.028f)
        dot(0.66f, 0.32f, 0.028f)
    }

    fun keyboard() {
        box(0.08f, 0.28f, 0.92f, 0.72f, radius = 0.05f)
        repeat(2) { row ->
            repeat(6) { column ->
                dot(0.18f + column * 0.128f, 0.39f + row * 0.11f, 0.022f)
            }
        }
        line(0.32f, 0.63f, 0.68f, 0.63f, LIGHT)
    }

    /** A strip of film: the frame with its two perforated edges. */
    fun filmStrip() {
        box(0.12f, 0.20f, 0.88f, 0.80f, radius = 0.05f)
        line(0.28f, 0.20f, 0.28f, 0.80f, LIGHT)
        line(0.72f, 0.20f, 0.72f, 0.80f, LIGHT)
        listOf(0.34f, 0.50f, 0.66f).forEach { y ->
            dot(0.20f, y, 0.03f)
            dot(0.80f, y, 0.03f)
        }
    }

    /** A cartridge, label and all — the mark for everything before discs. */
    fun cartridge() {
        box(0.22f, 0.22f, 0.78f, 0.82f, radius = 0.05f)
        box(0.32f, 0.32f, 0.68f, 0.54f, radius = 0.03f, width = LIGHT)
        line(0.36f, 0.22f, 0.36f, 0.14f, LIGHT)
        line(0.36f, 0.14f, 0.64f, 0.14f, LIGHT)
        line(0.64f, 0.14f, 0.64f, 0.22f, LIGHT)
        line(0.38f, 0.66f, 0.62f, 0.66f, LIGHT)
        line(0.38f, 0.74f, 0.54f, 0.74f, LIGHT)
    }
}

/**
 * The set's two weights.
 *
 * [MAIN] carries the silhouette — the part that has to survive being seen from
 * across a room — and [LIGHT] is for detail inside it. There is no third: an
 * icon set with a weight per glyph is a set only in the sense that it is in one
 * file.
 */
private const val MAIN = 0.07f
private const val LIGHT = 0.045f

/** The default filled dot, which is the smallest mark the set uses. */
private const val DOT = 0.035f
