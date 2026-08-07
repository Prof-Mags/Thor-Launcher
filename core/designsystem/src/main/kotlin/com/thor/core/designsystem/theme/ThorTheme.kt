package com.thor.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thor.core.model.AccessibilitySettings
import com.thor.core.model.ContrastLevel
import com.thor.core.model.CornerStyle
import com.thor.core.model.CursorAnimation
import com.thor.core.model.CursorStyle
import com.thor.core.model.PerformanceSettings
import com.thor.core.model.InfoPanelStyle
import com.thor.core.model.PersonalizationSettings
import com.thor.core.model.SurfaceStyle
import com.thor.core.model.SurfaceTreatment
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
    val shapes: ThorShapes,
    /**
     * Whether the platform artwork Loki ships with is drawn.
     *
     * Carried on the theme rather than passed down because the two places that
     * ask are a grid cell and a couch card, each six or more layers below the
     * screen that holds the settings. Threading one boolean through every one of
     * those — and through the app drawer, which reuses the same cell — would put
     * a parameter nothing in between has any use for into all of them.
     */
    val bundledPlatformIcons: Boolean,
)

/**
 * Every corner in the launcher, resolved once.
 *
 * Components read these rather than constructing their own, which is the whole
 * point: panels used to take a radius from the theme while pills, tabs, dock slots
 * and dialogs were shaped where they happened to be written. A theme with a 2dp
 * radius therefore still had circular furniture in it and nothing matched anything
 * else, and there was no single place a user's preference could reach.
 *
 * [pill] is separate from [panel] because a fully-rounded element is a distinct
 * intent — a tab, a badge, a toggle track — and squaring the interface has to
 * square those too, which a shared radius cannot express.
 */
@Immutable
data class ThorShapes(
    /**
     * The user's choice itself, for the few places a `Shape` cannot express it.
     *
     * Grid icons are the reason: their shape is chosen from five options
     * including a circle and a hexagon, which no corner radius describes, so that
     * call site has to branch on the choice rather than be handed a shape.
     */
    val style: CornerStyle,
    /** Panels, cards, sheets, grid cells. */
    val panel: Shape,
    /** Inner elements on a panel: rows, chips, small controls. */
    val small: Shape,
    /** Dialogs and full-height menus. */
    val large: Shape,
    /** Anything normally drawn as a capsule or a circle. */
    val pill: Shape,
) {
    companion object {
        /**
         * Builds the set from the user's choice and the theme's own radius.
         *
         * Square is genuinely square everywhere, including the pill: a "square"
         * interface with capsule tabs still in it is the inconsistency this
         * setting exists to remove.
         */
        fun build(style: CornerStyle, themeRadius: Dp): ThorShapes = when (style) {
            CornerStyle.SQUARE -> ThorShapes(
                style = style,
                panel = RectangleShape,
                small = RectangleShape,
                large = RectangleShape,
                pill = RectangleShape,
            )

            CornerStyle.ROUNDED -> ThorShapes(
                style = style,
                panel = RoundedCornerShape(ROUNDED_PANEL.dp),
                small = RoundedCornerShape(ROUNDED_SMALL.dp),
                large = RoundedCornerShape(ROUNDED_LARGE.dp),
                pill = CircleShape,
            )

            CornerStyle.THEME -> ThorShapes(
                style = style,
                panel = RoundedCornerShape(themeRadius),
                small = RoundedCornerShape(themeRadius * 0.5f),
                large = RoundedCornerShape(themeRadius * 1.6f),
                // A theme with square corners gets square pills too, so the two
                // settings do not contradict each other on the same screen.
                pill = if (themeRadius <= SQUARE_THRESHOLD.dp) RectangleShape else CircleShape,
            )
        }

        private const val ROUNDED_PANEL = 22
        private const val ROUNDED_SMALL = 12
        private const val ROUNDED_LARGE = 32

        /** At or below this a theme is treating its corners as square. */
        private const val SQUARE_THRESHOLD = 4
    }
}

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
    /**
     * The theme's panel treatment, already degraded to what this device will draw.
     *
     * Resolved here rather than read from the spec at each call site, for the same
     * reason [surfaceAlpha] is: every consumer would otherwise have to remember
     * the same three fallbacks, and the one that forgets is the one that renders
     * an unreadable panel.
     */
    val surface: SurfaceTreatment,
    /** How far the background graduates toward the accent; 0 is flat. */
    val backgroundDepth: Float,
    /**
     * How the information panel meets the artwork behind it.
     *
     * Here with the other surface treatments rather than threaded through the
     * panels as a parameter: it is exactly the kind of thing this object exists
     * for, and the two panels that read it are four composables deep on the
     * other screen.
     */
    val infoPanel: InfoPanelStyle,
    /**
     * How far the wallpaper is dimmed behind the interface, 0..1.
     *
     * Resolved here beside the other material values rather than read from
     * settings at the one place that draws it, so anything else that ever paints
     * over the wallpaper reads the same number.
     */
    val wallpaperDim: Float,
) {
    val isBlurActive: Boolean get() = glassEnabled && blurRadius > 0.dp
}

val LocalThorTheme: ProvidableCompositionLocal<ThorThemeState> =
    staticCompositionLocalOf { error("ThorTheme not provided") }

/** Selection ring thickness. Fixed rather than user-tunable. */
private val DEFAULT_CURSOR_THICKNESS = 3.dp

/**
 * Panel radius when the user asks for rounded corners everywhere.
 *
 * Generous on purpose: this setting exists for someone who wants the whole
 * interface soft, and a timid radius reads as the theme's own rather than as a
 * choice that was applied.
 */
private const val ROUNDED_OVERRIDE_RADIUS = 22

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

    /*
     * The whole palette, generated here and nowhere else.
     *
     * Every appearance preference the user holds is an *input* to this rather than
     * a patch applied afterwards, which is what makes them compose: pure black
     * under a hue shift under maximum contrast is one palette built to satisfy all
     * three, not three transforms fighting over the same colours in whatever order
     * they happened to be written.
     */
    val spec = remember(personalization, systemDark, accessibility.highContrast) {
        val options = personalization.themeOptions(systemDark).let {
            // The accessibility switch is a shortcut to the top of the dial rather
            // than a separate mechanism. It wins, because someone who has turned it
            // on has said the default is not enough for them.
            if (accessibility.highContrast) it.copy(contrast = ContrastLevel.MAXIMUM) else it
        }
        // The applied custom theme if there is one, and the bundled theme otherwise;
        // see [PersonalizationSettings.activeRecipe] for what happens when the
        // custom theme it names has been deleted.
        personalization.activeRecipe.resolve(options)
    }

    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = personalization.useDynamicColor && supportsDynamicColor

    val colors = remember(spec, accessibility.colorBlindMode) {
        buildThorColors(spec = spec, colorBlindMode = accessibility.colorBlindMode)
    }

    // In performance mode blur and animation are off regardless of the theme,
    // and the reduce-motion switch wins over the user's speed slider.
    val reduceMotion = accessibility.reduceMotion || !performance.animationsEnabled
    val motionStyle = personalization.motionOverride ?: spec.motion
    val motion = remember(motionStyle, personalization.transitionSpeed, reduceMotion) {
        ThorMotion(
            style = motionStyle,
            speedMultiplier = personalization.transitionSpeed,
            reduceMotion = reduceMotion,
        )
    }

    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    /*
     * Every value read inside is a key, including the ones read only to be
     * copied.
     *
     * A `remember` whose block reads a setting absent from its keys is a setting
     * that cannot be changed: the block does not run again, so the old value is
     * kept and the preference silently does nothing. That is what happened to
     * the info panel style — it was passed through faithfully and never
     * recomputed, so the option looked broken while the code that used it was
     * correct.
     */
    val materials = remember(
        spec, personalization.glassEffects, personalization.wallpaperDim,
        personalization.infoPanelStyle,
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

        /*
         * A glass treatment with nothing blurred behind it is not glass; it is a
         * flat grey sheet with a bright line on it. Where the backdrop cannot be
         * blurred the panel falls back to a tinted one — which still has an edge
         * and still steps with elevation — and the specular highlight is dialled
         * back, because a lit edge over an unblurred background reads as a
         * rendering mistake rather than as a material.
         *
         * Shadows go entirely in performance mode. They are the one part of a
         * treatment that costs a render pass per panel.
         */
        val treatment = spec.surface
            .let {
                if (it.wantsBlur && !blurAllowed) {
                    it.copy(style = SurfaceStyle.TINTED, specularAlpha = it.specularAlpha * 0.35f)
                } else {
                    it
                }
            }
            .let { if (performance.performanceMode) it.copy(shadowElevationDp = 0) else it }

        ThorMaterials(
            surfaceAlpha = alpha,
            blurRadius = radius,
            glassEnabled = blurAllowed,
            animationsEnabled = !reduceMotion,
            surface = treatment,
            // Flattened in performance mode along with everything else that costs
            // a gradient the user did not ask for.
            backgroundDepth = if (performance.performanceMode) 0f else spec.backgroundDepth,
            infoPanel = personalization.infoPanelStyle,
            wallpaperDim = personalization.wallpaperDim.coerceIn(0f, 1f),
        )
    }

    val dimens = remember(spec, accessibility.touchTargetScale, personalization.cornerStyle) {
        /*
         * The user's corner choice overrides the theme's own radius here, at the
         * source, rather than at each call site.
         *
         * Nearly every shape in the launcher is already built from
         * `dimens.cornerRadius` or one of its derivatives, so overriding the
         * radius itself squares or rounds all of them at once — panels, rows,
         * dialogs, keys, cells. Only the handful of elements hardcoded as capsules
         * need [ThorShapes.pill] as well, and those are the ones a radius could
         * never have described anyway.
         */
        val radius = when (personalization.cornerStyle) {
            CornerStyle.SQUARE -> 0.dp
            CornerStyle.ROUNDED -> ROUNDED_OVERRIDE_RADIUS.dp
            CornerStyle.THEME -> spec.cornerRadiusDp.dp
        }
        ThorDimens.build(
            // Density and corner scaling were user settings; they are now taken
            // from the theme alone, which is what made them redundant.
            density = 1f,
            cornerRadius = radius,
            cursorThickness = DEFAULT_CURSOR_THICKNESS,
            touchScale = accessibility.touchTargetScale,
        )
    }

    // One family for every theme; only the scale is the user's. See [ThorTypography].
    val fontScale = personalization.fontScale * if (accessibility.largeText) 1.2f else 1f
    val typography = remember(fontScale) {
        ThorTypography.build(ThorTypography.FAMILY, fontScale)
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

    val shapes = remember(personalization.cornerStyle, dimens.cornerRadius) {
        ThorShapes.build(personalization.cornerStyle, dimens.cornerRadius)
    }

    val state = ThorThemeState(
        spec = spec,
        colors = colors,
        motion = motion,
        dimens = dimens,
        materials = materials,
        cursor = cursorSpec,
        shapes = shapes,
        bundledPlatformIcons = personalization.bundledPlatformIcons,
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

    val shapes: ThorShapes
        @Composable get() = LocalThorTheme.current.shapes

    val bundledPlatformIcons: Boolean
        @Composable get() = LocalThorTheme.current.bundledPlatformIcons
}
