package com.thor.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.thor.core.model.ColorBlindMode
import com.thor.core.model.ThemeSpec
import kotlin.math.max
import kotlin.math.min

/**
 * THOR's colour roles.
 *
 * Material 3's scheme is generated alongside this (see [toMaterialScheme]) so
 * that stock M3 components look right, but the launcher's own surfaces read
 * from here because they need roles Material has no name for — the cursor ring
 * and its glow in particular.
 */
@Immutable
data class ThorColors(
    val primary: Color,
    val secondary: Color,
    /**
     * Far end of the accent gradient.
     *
     * Paired with [primary] wherever an accent is filled — the cursor, progress,
     * badges. A single flat accent is the main reason a palette reads as cheap.
     */
    val accentEnd: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    /** Highest surface, for menus and dialogs over an already-elevated panel. */
    val surfaceHighest: Color,
    val onBackground: Color,
    val onSurface: Color,
    /** Muted text: metadata labels, secondary rows. */
    val onSurfaceVariant: Color,
    val cursor: Color,
    val glow: Color,
    val outline: Color,
    val error: Color,
    val scrim: Color,
) {
    /** True when the scheme is dark enough to want light-on-dark content. */
    val isDark: Boolean get() = background.luminance() < 0.5f

    /** The accent pair, for gradient fills. */
    val accentStops: List<Color> get() = listOf(primary, accentEnd)
}

/**
 * Builds the launcher palette for a theme, applying the user's accent override,
 * high-contrast preference and colour-blind correction in that order.
 */
fun buildThorColors(
    spec: ThemeSpec,
    accentOverride: Color? = null,
    highContrast: Boolean = false,
    colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
): ThorColors {
    val primary = accentOverride ?: Color(spec.primaryArgb)
    val base = ThorColors(
        primary = primary,
        secondary = Color(spec.secondaryArgb),
        // An overridden accent supplies its own far stop by lightening, so a
        // custom colour still gets a gradient rather than falling back to the
        // retired theme's second stop and clashing with it.
        accentEnd = accentOverride?.lighten(ACCENT_END_LIFT) ?: Color(spec.accentEndArgb),
        background = Color(spec.backgroundArgb),
        surface = Color(spec.surfaceArgb),
        surfaceElevated = Color(spec.surfaceElevatedArgb),
        surfaceHighest = Color(spec.surfaceHighestArgb),
        onBackground = Color(spec.onBackgroundArgb),
        onSurface = Color(spec.onSurfaceArgb),
        onSurfaceVariant = Color(spec.onSurfaceVariantArgb),
        cursor = accentOverride ?: Color(spec.cursorArgb),
        glow = accentOverride?.copy(alpha = 0.45f) ?: Color(spec.glowArgb),
        outline = Color(spec.outlineArgb),
        error = Color(spec.errorArgb),
        scrim = Color.Black.copy(alpha = if (spec.isDark) 0.62f else 0.38f),
    )
    return base
        .let { if (highContrast) it.withHighContrast() else it }
        .let { it.withColorBlindCorrection(colorBlindMode) }
}

/**
 * Pushes foreground/background apart and hardens outlines.
 *
 * Rather than swapping in a separate palette, this pins text to pure white or
 * black and makes the cursor fully opaque, which keeps every theme recognisable
 * while clearing the WCAG AA contrast bar.
 */
private fun ThorColors.withHighContrast(): ThorColors {
    val dark = isDark
    val foreground = if (dark) Color.White else Color.Black
    return copy(
        background = if (dark) Color.Black else Color.White,
        surface = if (dark) Color(0xFF0A0A0A) else Color(0xFFFAFAFA),
        surfaceElevated = if (dark) Color(0xFF161616) else Color.White,
        // The ramp is flattened rather than dropped: high contrast still needs
        // the three levels to be distinguishable, just by less.
        surfaceHighest = if (dark) Color(0xFF222222) else Color(0xFFF0F0F0),
        onBackground = foreground,
        onSurface = foreground,
        onSurfaceVariant = foreground.copy(alpha = 0.86f),
        outline = foreground.copy(alpha = 0.6f),
        cursor = cursor.copy(alpha = 1f),
        glow = cursor.copy(alpha = 0.75f),
    )
}

/**
 * Applies a daltonisation-style correction.
 *
 * The transforms rotate hues away from the axis a given dichromacy cannot
 * separate, so that colour-coded platform badges stay distinguishable. This is
 * a per-colour approximation, not a full LMS simulation — it is cheap enough to
 * run at theme-build time and does not need a shader pass.
 */
private fun ThorColors.withColorBlindCorrection(mode: ColorBlindMode): ThorColors {
    if (mode == ColorBlindMode.NONE) return this
    fun adjust(color: Color): Color = when (mode) {
        ColorBlindMode.NONE -> color
        ColorBlindMode.GRAYSCALE -> {
            val l = color.luminance()
            Color(l, l, l, color.alpha)
        }
        // Red-blind: fold red energy into the blue channel so red and green
        // separate on the blue axis instead of collapsing together.
        ColorBlindMode.PROTANOPIA -> Color(
            red = min(1f, color.red * 0.56f + color.green * 0.44f),
            green = min(1f, color.green * 0.75f + color.red * 0.25f),
            blue = min(1f, color.blue + color.red * 0.30f),
            alpha = color.alpha,
        )
        // Green-blind: same idea, biased toward preserving red.
        ColorBlindMode.DEUTERANOPIA -> Color(
            red = min(1f, color.red * 0.63f + color.green * 0.37f),
            green = min(1f, color.green * 0.70f + color.red * 0.30f),
            blue = min(1f, color.blue + color.green * 0.30f),
            alpha = color.alpha,
        )
        // Blue-blind: shift blue toward green, which remains discriminable.
        ColorBlindMode.TRITANOPIA -> Color(
            red = min(1f, color.red * 0.95f + color.blue * 0.05f),
            green = min(1f, color.green * 0.70f + color.blue * 0.30f),
            blue = min(1f, color.blue * 0.55f + color.green * 0.45f),
            alpha = color.alpha,
        )
    }
    return copy(
        primary = adjust(primary),
        secondary = adjust(secondary),
        accentEnd = adjust(accentEnd),
        cursor = adjust(cursor),
        glow = adjust(glow),
        error = adjust(error),
    )
}

/** Lifts a colour toward white, for deriving a gradient's far stop. */
fun Color.lighten(fraction: Float): Color = blend(Color.White, fraction)

/** How far an overridden accent is lifted to produce its gradient end. */
private const val ACCENT_END_LIFT = 0.32f

/** Projects the launcher palette onto a Material 3 scheme. */
fun ThorColors.toMaterialScheme(): ColorScheme {
    val onPrimary = contrastingContentColor(primary)
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = contrastingContentColor(secondary),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHighest = surfaceHighest,
            outline = outline,
            error = error,
            onError = contrastingContentColor(error),
            scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = contrastingContentColor(secondary),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerHighest = surfaceHighest,
            outline = outline,
            error = error,
            onError = contrastingContentColor(error),
            scrim = scrim,
        )
    }
}

/** Picks black or white content for [background], whichever contrasts more. */
fun contrastingContentColor(background: Color): Color {
    val l = background.luminance()
    val contrastWithWhite = (1.0f + 0.05f) / (l + 0.05f)
    val contrastWithBlack = (l + 0.05f) / 0.05f
    return if (contrastWithWhite >= contrastWithBlack) Color.White else Color.Black
}

/** Blends [this] toward [other] by [fraction]. */
fun Color.blend(other: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * f,
        green = green + (other.green - green) * f,
        blue = blue + (other.blue - blue) * f,
        alpha = max(alpha, other.alpha),
    )
}
