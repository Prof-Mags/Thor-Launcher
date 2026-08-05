package com.thor.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour in OKLCH: perceptual lightness, chroma, and hue in degrees.
 *
 * The launcher's palettes are *generated* rather than hand-written, and this is
 * the space they are generated in. sRGB cannot do the job: "the same lightness in
 * a different hue" is meaningless there — `#0000FF` and `#FFFF00` share a channel
 * magnitude and differ in apparent brightness by a factor of ten — so a set of
 * themes built by picking hex values ends up with one whose body text is crisp and
 * another whose is mud, and no amount of care at the keyboard prevents it. In
 * OKLCH [l] is what the eye calls lightness, so a ramp built once holds its
 * legibility whatever hue is poured into it.
 *
 * @param l perceptual lightness, 0 (black) to 1 (white)
 * @param c chroma — distance from grey. Roughly 0 to 0.37 in sRGB, and the
 *   reachable maximum depends heavily on [l] and [h], which is what [toArgb]
 *   handles.
 * @param h hue angle in degrees. 29 is red, 70 orange, 110 yellow, 145 green,
 *   195 cyan, 264 blue, 305 violet, 345 magenta.
 */
data class Oklch(val l: Float, val c: Float, val h: Float) {

    /** Moves toward white (positive) or black (negative), keeping hue and chroma. */
    fun lighten(delta: Float): Oklch = copy(l = (l + delta).coerceIn(0f, 1f))

    /** Scales chroma — 0 greys the colour out, above 1 saturates it. */
    fun saturate(factor: Float): Oklch = copy(c = (c * factor).coerceAtLeast(0f))

    fun withChroma(chroma: Float): Oklch = copy(c = chroma.coerceAtLeast(0f))

    fun withLightness(lightness: Float): Oklch = copy(l = lightness.coerceIn(0f, 1f))

    /** Rotates the hue, wrapping at 360. */
    fun rotate(degrees: Float): Oklch = copy(h = (h + degrees).mod(360f))

    /**
     * The nearest sRGB colour, as an opaque ARGB long.
     *
     * Most (l, c, h) triples name a colour no screen can show — chroma runs out
     * long before the theoretical maximum at the light and dark ends of the ramp,
     * and it runs out at a different place for every hue. Rather than clip the
     * channels, which shifts both hue and lightness and turns a deep red into a
     * flat orange, chroma is reduced until the colour fits. Lightness and hue are
     * the parts a palette is reasoning about; chroma is the part it can afford to
     * give up.
     */
    fun toArgb(): Long {
        val fitted = if (inGamut(l, c, h)) c else largestFittingChroma()
        val (r, g, b) = toLinearSrgb(l, fitted, h)
        return pack(
            linearToSrgb(r),
            linearToSrgb(g),
            linearToSrgb(b),
        )
    }

    /** As [toArgb], but carrying [alpha] (0..1) rather than full opacity. */
    fun toArgb(alpha: Float): Long {
        val opaque = toArgb() and 0x00FFFFFFL
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt().toLong() and 0xFFL
        return (a shl 24) or opaque
    }

    /**
     * WCAG relative luminance of the colour once resolved to sRGB.
     *
     * Not the same as [l], and the difference is the point: [l] is what the eye
     * calls lightness and drives how a palette is *composed*, while this is the
     * quantity the contrast standard is written in and drives whether text on it
     * can be read. A palette that conflated the two would look even and measure
     * badly.
     */
    fun luminance(): Float = relativeLuminance(toArgb())

    private fun largestFittingChroma(): Float {
        // Binary search rather than a closed form: the sRGB gamut boundary in
        // OKLCH has no tidy expression, and sixteen halvings land within 0.00001
        // of the edge, which is far below a perceptible step.
        var low = 0f
        var high = c
        repeat(GAMUT_SEARCH_STEPS) {
            val mid = (low + high) / 2f
            if (inGamut(l, mid, h)) low = mid else high = mid
        }
        return low
    }

    companion object {
        /** Reads an opaque or translucent ARGB long back into OKLCH. */
        fun fromArgb(argb: Long): Oklch {
            val r = srgbToLinear(((argb shr 16) and 0xFF).toFloat() / 255f)
            val g = srgbToLinear(((argb shr 8) and 0xFF).toFloat() / 255f)
            val b = srgbToLinear((argb and 0xFF).toFloat() / 255f)

            val lCone = cubeRoot(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val mCone = cubeRoot(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val sCone = cubeRoot(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

            val lightness = 0.2104542553f * lCone + 0.7936177850f * mCone - 0.0040720468f * sCone
            val aAxis = 1.9779984951f * lCone - 2.4285922050f * mCone + 0.4505937099f * sCone
            val bAxis = 0.0259040371f * lCone + 0.7827717662f * mCone - 0.8086757660f * sCone

            val chroma = kotlin.math.sqrt(aAxis * aAxis + bAxis * bAxis)
            // A grey has no meaningful hue; reporting one invites a caller to
            // rotate it and get a colour out of something that had none.
            val hue = if (chroma < ACHROMATIC) 0f else {
                Math.toDegrees(kotlin.math.atan2(bAxis, aAxis).toDouble()).toFloat().mod(360f)
            }
            return Oklch(lightness, chroma, hue)
        }

        /** WCAG relative luminance for an ARGB long, ignoring alpha. */
        fun relativeLuminance(argb: Long): Float {
            fun channel(shift: Int): Float =
                srgbToLinear(((argb shr shift) and 0xFF).toFloat() / 255f)
            return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
        }

        /**
         * WCAG contrast ratio between two ARGB longs, 1 (identical) to 21.
         *
         * The bar the generated palettes are held to: 4.5 for body text, 3.0 for
         * anything secondary.
         */
        fun contrastRatio(foreground: Long, background: Long): Float {
            val a = relativeLuminance(foreground)
            val b = relativeLuminance(background)
            val lighter = maxOf(a, b)
            val darker = minOf(a, b)
            return (lighter + 0.05f) / (darker + 0.05f)
        }

        private fun inGamut(l: Float, c: Float, h: Float): Boolean {
            val (r, g, b) = toLinearSrgb(l, c, h)
            return r >= -EPSILON && r <= 1f + EPSILON &&
                g >= -EPSILON && g <= 1f + EPSILON &&
                b >= -EPSILON && b <= 1f + EPSILON
        }

        private fun toLinearSrgb(l: Float, c: Float, h: Float): Triple<Float, Float, Float> {
            val radians = Math.toRadians(h.toDouble())
            val aAxis = c * cos(radians).toFloat()
            val bAxis = c * sin(radians).toFloat()

            val lCone = l + 0.3963377774f * aAxis + 0.2158037573f * bAxis
            val mCone = l - 0.1055613458f * aAxis - 0.0638541728f * bAxis
            val sCone = l - 0.0894841775f * aAxis - 1.2914855480f * bAxis

            val lLin = lCone * lCone * lCone
            val mLin = mCone * mCone * mCone
            val sLin = sCone * sCone * sCone

            return Triple(
                4.0767416621f * lLin - 3.3077115913f * mLin + 0.2309699292f * sLin,
                -1.2684380046f * lLin + 2.6097574011f * mLin - 0.3413193965f * sLin,
                -0.0041960863f * lLin - 0.7034186147f * mLin + 1.7076147010f * sLin,
            )
        }

        private fun pack(r: Float, g: Float, b: Float): Long {
            fun byte(value: Float): Long =
                ((value.coerceIn(0f, 1f) * 255f) + 0.5f).toInt().toLong() and 0xFFL
            return (0xFFL shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
        }

        private fun srgbToLinear(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f
            else ((channel + 0.055f) / 1.055f).pow(2.4f)

        private fun linearToSrgb(channel: Float): Float =
            if (channel <= 0.0031308f) channel * 12.92f
            else 1.055f * channel.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f

        /**
         * Cube root that keeps its sign.
         *
         * `pow(1/3)` returns NaN for a negative base, and the cone responses do go
         * negative for colours outside the gamut — which is exactly the case the
         * gamut search spends its time in, so a NaN here would make the search
         * report every chroma as unreachable and drain the colour out of the whole
         * palette.
         */
        private fun cubeRoot(value: Float): Float {
            val magnitude = abs(value).pow(1f / 3f)
            return if (value < 0f) -magnitude else magnitude
        }

        /** Below this the a/b axes are noise and the hue they imply is arbitrary. */
        private const val ACHROMATIC = 0.0002f

        /** Slack for floating-point error at the gamut boundary. */
        private const val EPSILON = 0.0005f

        private const val GAMUT_SEARCH_STEPS = 16
    }
}
