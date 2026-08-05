package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.abs

/**
 * The colour space every palette is built in.
 *
 * Worth testing on its own rather than only through the themes, because a fault
 * here does not look like a fault. A gamut search that gives up too early drains
 * the colour out of one hue and nothing else; a sign error in the cube root turns
 * an out-of-gamut candidate into a NaN, which propagates into a black rectangle
 * some way downstream. Both would read on a device as "that theme looks a bit
 * flat", which is exactly the report nobody can act on.
 */
class OklchTest {

    @Test
    fun `a colour survives the round trip through sRGB`() {
        // Eight bits per channel is the floor on precision, so this asserts the
        // conversion is faithful to the point where the format runs out — not to
        // the point where the arithmetic does.
        val samples = listOf(
            0xFF000000L, 0xFFFFFFFFL, 0xFF808080L,
            0xFFFF0000L, 0xFF00FF00L, 0xFF0000FFL,
            0xFF4F8CFFL, 0xFFB6C6FFL, 0xFF2FD9C4L, 0xFF120A07L,
        )

        samples.forEach { argb ->
            val round = Oklch.fromArgb(argb).toArgb()
            assertWithMessage("round trip of ${argb.toString(16)}")
                .that(channelDistance(round, argb))
                .isAtMost(1)
        }
    }

    @Test
    fun `lightness means the same thing at every hue`() {
        /*
         * The property the whole redesign rests on.
         *
         * In sRGB, blue and yellow at the same channel magnitude differ in apparent
         * brightness by roughly a factor of ten, which is why a set of hand-picked
         * palettes always ends up with one whose text is crisp and another whose is
         * mud. Here one lightness is one lightness, whatever direction the colour
         * points — so a surface ramp built once holds for all twelve themes.
         */
        val luminances = (0 until 360 step 15).map { hue ->
            Oklch(l = 0.55f, c = 0.08f, h = hue.toFloat()).luminance()
        }

        val spread = luminances.max() - luminances.min()
        assertWithMessage("luminance spread across the hue wheel at one lightness")
            .that(spread)
            .isLessThan(LUMINANCE_SPREAD_TOLERANCE)
    }

    @Test
    fun `an unreachable chroma is reduced rather than clipped`() {
        // Nobody's screen can show this, and the two ways to deal with it are not
        // equivalent: clipping the channels shifts the hue, which turns a deep red
        // into a flat orange. Giving up chroma keeps the colour pointing where it
        // was asked to point.
        val requested = Oklch(l = 0.5f, c = 0.5f, h = 29f)
        val delivered = Oklch.fromArgb(requested.toArgb())

        assertThat(delivered.c).isLessThan(requested.c)
        assertThat(abs(delivered.l - requested.l)).isLessThan(LIGHTNESS_TOLERANCE)
        assertThat(hueDistance(delivered.h, requested.h)).isLessThan(HUE_TOLERANCE)
    }

    @Test
    fun `every lightness and hue produces a usable colour`() {
        // The sweep that catches a NaN. A cube root that loses its sign returns one
        // for any out-of-gamut candidate, and the gamut search spends most of its
        // time in exactly that region — so the failure would be silent everywhere
        // except at the extremes of the ramp.
        for (lightness in 0..20) {
            for (hue in 0 until 360 step 20) {
                val argb = Oklch(lightness / 20f, 0.25f, hue.toFloat()).toArgb()
                assertWithMessage("l=${lightness / 20f} h=$hue")
                    .that(alpha(argb))
                    .isEqualTo(0xFF)
                listOf(16, 8, 0).forEach { shift ->
                    val channel = ((argb shr shift) and 0xFF).toInt()
                    assertWithMessage("channel at l=${lightness / 20f} h=$hue")
                        .that(channel)
                        .isIn(com.google.common.collect.Range.closed(0, 255))
                }
            }
        }
    }

    @Test
    fun `black and white are exactly black and white`() {
        assertThat(Oklch(0f, 0f, 0f).toArgb()).isEqualTo(0xFF000000L)
        assertThat(Oklch(1f, 0f, 0f).toArgb()).isEqualTo(0xFFFFFFFFL)
    }

    @Test
    fun `a grey reports no hue rather than an arbitrary one`() {
        // Whatever `atan2` returns for a pair of rounding errors is not a hue, and
        // a caller rotating it would get a colour out of something that had none.
        val grey = Oklch.fromArgb(0xFF808080L)

        assertThat(grey.c).isLessThan(0.002f)
        assertThat(grey.h).isEqualTo(0f)
    }

    @Test
    fun `contrast is the WCAG ratio`() {
        // Checked against the two values the standard fixes, because a contrast
        // function that is subtly wrong lets an unreadable palette through while
        // reporting that it passed.
        assertThat(Oklch.contrastRatio(0xFFFFFFFFL, 0xFF000000L)).isWithin(0.01f).of(21f)
        assertThat(Oklch.contrastRatio(0xFF808080L, 0xFF808080L)).isWithin(0.01f).of(1f)
    }

    @Test
    fun `alpha is carried without disturbing the colour`() {
        val colour = Oklch(0.7f, 0.12f, 200f)
        val translucent = colour.toArgb(alpha = 0.5f)

        assertThat(translucent and 0x00FFFFFFL).isEqualTo(colour.toArgb() and 0x00FFFFFFL)
        assertThat(((translucent shr 24) and 0xFF).toInt()).isEqualTo(127)
    }

    @Test
    fun `rotation wraps rather than running off the wheel`() {
        assertThat(Oklch(0.5f, 0.1f, 350f).rotate(20f).h).isWithin(0.01f).of(10f)
        assertThat(Oklch(0.5f, 0.1f, 10f).rotate(-20f).h).isWithin(0.01f).of(350f)
    }

    // ------------------------------------------------------------- helpers

    private fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    /** Largest per-channel difference between two ARGB values. */
    private fun channelDistance(a: Long, b: Long): Int = listOf(16, 8, 0).maxOf { shift ->
        abs(((a shr shift) and 0xFF).toInt() - ((b shr shift) and 0xFF).toInt())
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val raw = abs(a - b).mod(360f)
        return minOf(raw, 360f - raw)
    }

    private companion object {
        /**
         * How much WCAG luminance may vary across the hue wheel at one OKLCH
         * lightness.
         *
         * Not zero, and not meant to be: the two are different quantities — OKLCH
         * lightness is perceptual, WCAG luminance is a weighted sum of linear
         * channels — so a small spread is correct. The same sweep in sRGB spreads
         * by an order of magnitude, which is the difference being asserted.
         */
        const val LUMINANCE_SPREAD_TOLERANCE = 0.08f

        const val LIGHTNESS_TOLERANCE = 0.01f
        const val HUE_TOLERANCE = 2f
    }
}
