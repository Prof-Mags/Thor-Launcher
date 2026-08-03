package com.thor.feature.topscreen

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
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thor.core.model.PlatformGlyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Loki's platform icon language: rounded, open line work in the platform accent. */
@Composable
internal fun PlatformLineIcon(
    glyph: PlatformGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val unit = size.minDimension
        val xInset = (size.width - unit) / 2f
        val yInset = (size.height - unit) / 2f
        fun point(x: Float, y: Float) = Offset(xInset + x * unit, yInset + y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 0.065f) {
            drawLine(
                color = tint,
                start = point(x1, y1),
                end = point(x2, y2),
                strokeWidth = unit * width,
                cap = StrokeCap.Round,
            )
        }
        fun circle(x: Float, y: Float, radius: Float, filled: Boolean = false) {
            drawCircle(
                color = tint,
                radius = unit * radius,
                center = point(x, y),
                style = if (filled) {
                    androidx.compose.ui.graphics.drawscope.Fill
                } else {
                    Stroke(unit * 0.06f, cap = StrokeCap.Round)
                },
            )
        }
        fun roundRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            radius: Float = 0.08f,
        ) {
            drawRoundRect(
                color = tint,
                topLeft = point(left, top),
                size = Size((right - left) * unit, (bottom - top) * unit),
                cornerRadius = CornerRadius(radius * unit),
                style = Stroke(unit * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        fun path(build: Path.() -> Unit) {
            drawPath(
                path = Path().apply(build),
                color = tint,
                style = Stroke(unit * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        when (glyph) {
            PlatformGlyph.GAME_LIBRARY -> {
                path {
                    moveTo(point(.25f, .38f).x, point(.25f, .38f).y)
                    cubicTo(
                        point(.13f, .40f).x, point(.13f, .40f).y,
                        point(.08f, .73f).x, point(.08f, .73f).y,
                        point(.21f, .78f).x, point(.21f, .78f).y,
                    )
                    cubicTo(
                        point(.30f, .82f).x, point(.30f, .82f).y,
                        point(.34f, .67f).x, point(.34f, .67f).y,
                        point(.43f, .66f).x, point(.43f, .66f).y,
                    )
                    lineTo(point(.57f, .66f).x, point(.57f, .66f).y)
                    cubicTo(
                        point(.66f, .67f).x, point(.66f, .67f).y,
                        point(.70f, .82f).x, point(.70f, .82f).y,
                        point(.79f, .78f).x, point(.79f, .78f).y,
                    )
                    cubicTo(
                        point(.92f, .73f).x, point(.92f, .73f).y,
                        point(.87f, .40f).x, point(.87f, .40f).y,
                        point(.75f, .38f).x, point(.75f, .38f).y,
                    )
                    close()
                }
                line(.27f, .52f, .43f, .52f)
                line(.35f, .44f, .35f, .60f)
                circle(.68f, .49f, .035f, filled = true)
                circle(.77f, .57f, .035f, filled = true)
            }

            PlatformGlyph.FAVOURITE -> {
                val star = Path()
                repeat(10) { index ->
                    val radius = if (index % 2 == 0) .39f else .17f
                    val angle = -PI / 2 + index * PI / 5
                    val p = point(
                        .5f + cos(angle).toFloat() * radius,
                        .5f + sin(angle).toFloat() * radius,
                    )
                    if (index == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
                }
                star.close()
                drawPath(star, tint, style = Stroke(unit * .06f, join = StrokeJoin.Round))
            }

            PlatformGlyph.CLOCK, PlatformGlyph.PLAYTIME -> {
                drawCircle(
                    color = tint,
                    radius = unit * .34f,
                    center = point(.5f, .5f),
                    style = Stroke(unit * .06f, cap = StrokeCap.Round),
                )
                line(.5f, .28f, .5f, .52f)
                line(.5f, .52f, .67f, .61f)
                if (glyph == PlatformGlyph.PLAYTIME) {
                    drawArc(
                        color = tint.copy(alpha = .45f),
                        startAngle = 25f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = point(.08f, .08f),
                        size = Size(.84f * unit, .84f * unit),
                        style = Stroke(unit * .035f, cap = StrokeCap.Round),
                    )
                }
            }

            PlatformGlyph.PLAY -> {
                path {
                    moveTo(point(.35f, .25f).x, point(.35f, .25f).y)
                    lineTo(point(.75f, .5f).x, point(.75f, .5f).y)
                    lineTo(point(.35f, .75f).x, point(.35f, .75f).y)
                    close()
                }
            }

            PlatformGlyph.DUAL_SCREEN -> {
                roundRect(.24f, .14f, .76f, .43f)
                roundRect(.29f, .57f, .71f, .86f)
                line(.45f, .50f, .55f, .50f, .045f)
            }

            PlatformGlyph.DEPTH -> {
                circle(.40f, .50f, .28f)
                circle(.60f, .50f, .28f)
                line(.50f, .22f, .50f, .78f, .035f)
            }

            PlatformGlyph.TOUCH -> {
                roundRect(.24f, .10f, .66f, .86f, .06f)
                line(.38f, .19f, .52f, .19f, .035f)
                path {
                    moveTo(point(.56f, .76f).x, point(.56f, .76f).y)
                    lineTo(point(.56f, .47f).x, point(.56f, .47f).y)
                    cubicTo(
                        point(.56f, .37f).x, point(.56f, .37f).y,
                        point(.69f, .37f).x, point(.69f, .37f).y,
                        point(.69f, .48f).x, point(.69f, .48f).y,
                    )
                    lineTo(point(.69f, .57f).x, point(.69f, .57f).y)
                    cubicTo(
                        point(.83f, .49f).x, point(.83f, .49f).y,
                        point(.86f, .61f).x, point(.86f, .61f).y,
                        point(.78f, .78f).x, point(.78f, .78f).y,
                    )
                }
            }

            PlatformGlyph.SOCIAL, PlatformGlyph.MULTIPLAYER -> {
                circle(.50f, .31f, .11f)
                path {
                    moveTo(point(.30f, .73f).x, point(.30f, .73f).y)
                    cubicTo(
                        point(.33f, .50f).x, point(.33f, .50f).y,
                        point(.67f, .50f).x, point(.67f, .50f).y,
                        point(.70f, .73f).x, point(.70f, .73f).y,
                    )
                }
                circle(.21f, .40f, .075f)
                circle(.79f, .40f, .075f)
                line(.10f, .68f, .27f, .57f)
                line(.90f, .68f, .73f, .57f)
            }

            PlatformGlyph.MOTION -> {
                roundRect(.31f, .17f, .69f, .82f, .11f)
                circle(.50f, .29f, .035f, filled = true)
                line(.41f, .51f, .59f, .51f)
                line(.50f, .42f, .50f, .60f)
                drawArc(tint, 130f, 100f, false, point(.07f, .25f), Size(.27f * unit, .50f * unit), style = Stroke(unit * .045f))
                drawArc(tint, -50f, 100f, false, point(.66f, .25f), Size(.27f * unit, .50f * unit), style = Stroke(unit * .045f))
            }

            PlatformGlyph.HYBRID -> {
                roundRect(.25f, .26f, .75f, .72f, .04f)
                roundRect(.08f, .19f, .25f, .79f, .08f)
                roundRect(.75f, .19f, .92f, .79f, .08f)
                circle(.165f, .37f, .035f, filled = true)
                circle(.835f, .61f, .035f, filled = true)
            }

            PlatformGlyph.PORTABLE -> {
                roundRect(.09f, .27f, .91f, .73f, .13f)
                roundRect(.31f, .34f, .69f, .66f, .03f)
                line(.17f, .50f, .27f, .50f)
                line(.22f, .45f, .22f, .55f)
                circle(.79f, .46f, .025f, filled = true)
                circle(.84f, .54f, .025f, filled = true)
            }

            PlatformGlyph.LINK -> {
                roundRect(.08f, .20f, .37f, .80f, .06f)
                roundRect(.63f, .20f, .92f, .80f, .06f)
                line(.37f, .43f, .63f, .43f)
                line(.37f, .57f, .63f, .57f)
            }

            PlatformGlyph.DISC -> {
                circle(.50f, .50f, .36f)
                circle(.50f, .50f, .09f)
                drawArc(tint.copy(alpha = .55f), 205f, 85f, false, point(.24f, .24f), Size(.52f * unit, .52f * unit), style = Stroke(unit * .035f))
            }

            PlatformGlyph.CUBE -> {
                path {
                    moveTo(point(.50f, .12f).x, point(.50f, .12f).y)
                    lineTo(point(.83f, .30f).x, point(.83f, .30f).y)
                    lineTo(point(.83f, .68f).x, point(.83f, .68f).y)
                    lineTo(point(.50f, .88f).x, point(.50f, .88f).y)
                    lineTo(point(.17f, .68f).x, point(.17f, .68f).y)
                    lineTo(point(.17f, .30f).x, point(.17f, .30f).y)
                    close()
                    moveTo(point(.17f, .30f).x, point(.17f, .30f).y)
                    lineTo(point(.50f, .50f).x, point(.50f, .50f).y)
                    lineTo(point(.83f, .30f).x, point(.83f, .30f).y)
                    moveTo(point(.50f, .50f).x, point(.50f, .50f).y)
                    lineTo(point(.50f, .88f).x, point(.50f, .88f).y)
                }
            }

            PlatformGlyph.ONLINE -> {
                circle(.50f, .50f, .36f)
                drawOval(tint, point(.34f, .14f), Size(.32f * unit, .72f * unit), style = Stroke(unit * .05f))
                line(.16f, .50f, .84f, .50f, .045f)
                drawArc(tint, 200f, 140f, false, point(.17f, .30f), Size(.66f * unit, .40f * unit), style = Stroke(unit * .04f))
            }

            PlatformGlyph.ARCADE -> {
                roundRect(.16f, .54f, .84f, .80f, .04f)
                line(.39f, .54f, .50f, .27f)
                circle(.53f, .20f, .09f)
                circle(.68f, .64f, .035f, filled = true)
                circle(.77f, .68f, .035f, filled = true)
            }

            PlatformGlyph.PERFORMANCE -> {
                drawArc(tint, 180f, 180f, false, point(.14f, .25f), Size(.72f * unit, .72f * unit), style = Stroke(unit * .06f, cap = StrokeCap.Round))
                line(.50f, .62f, .72f, .37f)
                circle(.50f, .62f, .045f, filled = true)
                line(.25f, .72f, .75f, .72f)
            }

            PlatformGlyph.KEYBOARD -> {
                roundRect(.09f, .25f, .91f, .75f, .05f)
                repeat(3) { row ->
                    repeat(6) { column ->
                        circle(.20f + column * .12f, .37f + row * .12f, .018f, filled = true)
                    }
                }
                line(.32f, .66f, .68f, .66f, .04f)
            }

            PlatformGlyph.MEDIA -> {
                roundRect(.13f, .20f, .87f, .80f, .04f)
                line(.28f, .20f, .28f, .80f, .035f)
                line(.72f, .20f, .72f, .80f, .035f)
                line(.13f, .38f, .28f, .38f, .035f)
                line(.72f, .38f, .87f, .38f, .035f)
                line(.13f, .62f, .28f, .62f, .035f)
                line(.72f, .62f, .87f, .62f, .035f)
            }

            PlatformGlyph.CLASSICS -> {
                roundRect(.22f, .27f, .78f, .78f, .05f)
                line(.36f, .27f, .36f, .16f)
                line(.36f, .16f, .64f, .16f)
                line(.64f, .16f, .64f, .27f)
                circle(.50f, .52f, .12f)
                line(.42f, .78f, .42f, .86f)
                line(.58f, .78f, .58f, .86f)
            }
        }
    }
}
