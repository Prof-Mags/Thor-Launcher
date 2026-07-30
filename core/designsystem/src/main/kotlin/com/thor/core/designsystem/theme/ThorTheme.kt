package com.thor.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thor.core.model.AccessibilitySettings
import com.thor.core.model.CursorAnimation
import com.thor.core.model.CursorStyle
import com.thor.core.model.PerformanceSettings
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.ThemeSpec

/**
 * Resolved, ready-to-use styling for one composition.
 *
 * Everything the launcher's own components need is here, so a component takes
 * no styling parameters and instead reads [ThorTheme]. That is what lets a
 * theme switch animate across the whole UI without threading state.
 */
@Immutable
data class ThorThemeState(
    val spec: ThemeSpec,
    val colors: ThorColors,
    val motion: ThorMotion,
    val dimens: ThorDimens,
    val materials: ThorMaterials,
    val cursor: ThorCursorSpec,
)

/**
 * How the selection cursor looks.
 *
 * Carried on the theme rather than passed to each `thorCursor` call: the
 * modifier is applied from a dozen places across four modules, and threading
 * three preference values through every one of them is how they end up ignored
 * — which is exactly what happened to the cursor style and glow settings before
 * this existed.
 */
@Immutable
data class ThorCursorSpec(
    val style: CursorStyle,
    val animation: CursorAnimation,
    /** 0..1 intensity of the glow behind the cursor. */
    val glowIntensity: Float,
)

/** Spacing and sizing, scaled by the user's density preference. */
@Immutable
data class ThorDimens(
    val cornerRadius: Dp,
    val cornerRadiusSmall: Dp,
    val cornerRadiusLarge: Dp,
    val spacingTiny: Dp,
    val spacingSmall: Dp,
    val spacing: Dp,
    val spacingLarge: Dp,
    val spacingHuge: Dp,
    /** Height of the floating dock at scale 1. */
    val dockHeight: Dp,
    val cursorThickness: Dp,
    /** Minimum touch target, respecting the accessibility scale. */
    val minTouchTarget: Dp,
) {
    companion object {
        fun build(density: Float, cornerRadius: Dp, cursorThickness: Dp, touchScale: Float) =
            ThorDimens(
                cornerRadius = cornerRadius,
                cornerRadiusSmall = cornerRadius * 0.5f,
                cornerRadiusLarge = cornerRadius * 1.6f,
                spacingTiny = (4 * density).dp,
                spacingSmall = (8 * density).dp,
                spacing = (16 * density).dp,
                spacingLarge = (24 * density).dp,
                spacingHuge = (40 * density).dp,
                dockHeight = (74 * density).dp,
                cursorThickness = cursorThickness,
                minTouchTarget = (48 * touchScale).dp,
            )
    }
}

/**
 * Surface treatment: how translucent panels are, and how hard they blur.
 *
 * Blur is expensive and only available as a real backdrop effect from API 31,
 * so [blurRadius] is resolved to zero on older devices and in performance mode,
 * and [surfaceAlpha] is raised to compensate — otherwise a glass theme would
 * turn into an unreadable transparent sheet.
 */
@Immutable
data class ThorMaterials(
    val surfaceAlpha: Float,
    val blurRadius: Dp,
    val glassEnabled: Boolean,
    val animationsEnabled: Boolean,
) {
    val isBlurActive: Boolean get() = glassEnabled && blurRadius > 0.dp
}

val LocalThorTheme: ProvidableCompositionLocal<ThorThemeState> =
    staticCompositionLocalOf { error("ThorTheme not provided") }

/** Selection ring thickness. Fixed rather than user-tunable. */
private val DEFAULT_CURSOR_THICKNESS = 3.dp

/**
 * Applies THOR's theme.
 *
 * @param personalization the user's appearance preferences
 * @param accessibility drives contrast, motion reduction and text size
 * @param performance disables blur and animation wholesale in performance mode
 */
@Composable
fun ThorTheme(
    personalization: PersonalizationSettings = PersonalizationSettings(),
    accessibility: AccessibilitySettings = AccessibilitySettings(),
    performance: PerformanceSettings = PerformanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val spec = remember(personalization.themeId) { ThemeSpec.of(personalization.themeId) }

    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = personalization.useDynamicColor && supportsDynamicColor

    val colors = remember(
        spec, personalization.accentOverrideArgb, accessibility.highContrast,
        accessibility.colorBlindMode, useDynamic, systemDark,
    ) {
        buildThorColors(
            spec = spec,
            accentOverride = personalization.accentOverrideArgb?.let(::Color),
            highContrast = accessibility.highContrast,
            colorBlindMode = accessibility.colorBlindMode,
        )
    }

    // In performance mode blur and animation are off regardless of the theme,
    // and the reduce-motion switch wins over the user's speed slider.
    val reduceMotion = accessibility.reduceMotion || !performance.animationsEnabled
    val motion = remember(spec.motion, personalization.transitionSpeed, reduceMotion) {
        ThorMotion(
            style = spec.motion,
            speedMultiplier = personalization.transitionSpeed,
            reduceMotion = reduceMotion,
        )
    }

    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val materials = remember(
        spec, personalization.glassEffects,
        performance.blurEnabled, performance.performanceMode,
        blurSupported, reduceMotion,
    ) {
        val blurAllowed = blurSupported && personalization.glassEffects &&
            performance.blurEnabled && !performance.performanceMode
        val radius = if (blurAllowed) spec.blurRadiusDp.dp else 0.dp
        // Without a blur backdrop, translucency has nothing to hide behind, so
        // push alpha toward opaque to keep contrast.
        val alpha = spec.surfaceAlpha
            .let { if (radius <= 0.dp) maxOf(it, 0.92f) else it }
            .coerceIn(0.05f, 1f)
        ThorMaterials(
            surfaceAlpha = alpha,
            blurRadius = radius,
            glassEnabled = blurAllowed,
            animationsEnabled = !reduceMotion,
        )
    }

    val dimens = remember(spec, accessibility.touchTargetScale) {
        ThorDimens.build(
            // Density and corner scaling were user settings; they are now taken
            // from the theme alone, which is what made them redundant.
            density = 1f,
            cornerRadius = spec.cornerRadiusDp.dp,
            cursorThickness = DEFAULT_CURSOR_THICKNESS,
            touchScale = accessibility.touchTargetScale,
        )
    }

    val fontChoice = spec.fontFamily
    val fontScale = personalization.fontScale * if (accessibility.largeText) 1.2f else 1f
    val typography = remember(fontChoice, fontScale) {
        ThorTypography.build(ThorTypography.familyFor(fontChoice), fontScale)
    }

    val materialScheme = remember(colors, useDynamic, systemDark) {
        when {
            !useDynamic -> colors.toMaterialScheme()
            systemDark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }

    val cursorSpec = remember(
        personalization.cursorStyle,
        personalization.cursorAnimation,
        personalization.highlightGlow,
        accessibility.reduceMotion,
    ) {
        ThorCursorSpec(
            style = personalization.cursorStyle,
            // Reduce-motion suppresses the idle animation but keeps the cursor
            // itself; removing the selection indicator would be a usability
            // regression, not an accessibility win.
            animation = if (accessibility.reduceMotion) {
                CursorAnimation.NONE
            } else {
                personalization.cursorAnimation
            },
            glowIntensity = personalization.highlightGlow,
        )
    }

    val state = ThorThemeState(
        spec = spec,
        colors = colors,
        motion = motion,
        dimens = dimens,
        materials = materials,
        cursor = cursorSpec,
    )

    CompositionLocalProvider(LocalThorTheme provides state) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = typography,
            content = content,
        )
    }
}

/** Shorthand accessors, so components read `ThorTheme.colors.cursor`. */
object ThorTheme {
    val colors: ThorColors
        @Composable get() = LocalThorTheme.current.colors

    val motion: ThorMotion
        @Composable get() = LocalThorTheme.current.motion

    val dimens: ThorDimens
        @Composable get() = LocalThorTheme.current.dimens

    val materials: ThorMaterials
        @Composable get() = LocalThorTheme.current.materials

    val spec: ThemeSpec
        @Composable get() = LocalThorTheme.current.spec

    val cursor: ThorCursorSpec
        @Composable get() = LocalThorTheme.current.cursor
}
