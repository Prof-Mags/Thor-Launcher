package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A theme the user made.
 *
 * The same fourteen decisions a bundled [ThemeRecipe] makes, handed over. It is a
 * separate type rather than a `ThemeRecipe` with a name on it for one reason: a
 * recipe is written by hand in Kotlin and is therefore trusted, while this arrives
 * from a settings file or from a JSON document somebody was given, and neither can
 * be trusted to hold a drawable number. Every field is clamped on the way out —
 * see [toRecipe] — so no stored value, however it got there, can produce a palette
 * the launcher cannot render.
 *
 * A custom theme is *not* a set of overrides over a bundled one. It carries the
 * whole recipe, so retiring or changing the theme it was seeded from leaves it
 * exactly as it was — which is the only behaviour that makes "my theme" mean
 * anything a month later.
 */
@Serializable
data class CustomTheme(
    /** Generated, never derived from the name, so renaming is free. */
    val id: String,
    val name: String,

    // ---- Colour -------------------------------------------------------------
    /** Hue of the accent, in OKLCH degrees. See [Oklch] for the landmarks. */
    val accentHue: Float = 278f,
    /** How colourful the theme is at its loudest point. */
    val accentChroma: Float = 0.09f,
    /** Degrees the secondary accent sits from the primary. */
    val secondaryHueShift: Float = 34f,
    /** Degrees the far end of the accent gradient sits from the primary. */
    val accentSpread: Float = 20f,
    /** Degrees the selection cursor sits from the primary. Usually zero. */
    val cursorHueShift: Float = 0f,
    /** Hue the greys are tinted toward. */
    val neutralHue: Float = 278f,
    /** How far the greys are tinted; 0 is a true neutral. */
    val neutralChroma: Float = 0.018f,
    /** How far this theme lifts its ground off the extreme the contrast picked. */
    val groundShift: Float = 0f,

    // ---- Material -----------------------------------------------------------
    val surfaceStyle: SurfaceStyle = SurfaceStyle.TINTED,
    val cornerRadiusDp: Int = 18,
    val surfaceAlpha: Float = 0.94f,
    val blurRadiusDp: Int = 18,
    val grain: Float = 0.03f,
    val backgroundDepth: Float = 0.08f,

    // ---- Character ----------------------------------------------------------
    val motion: MotionStyle = MotionStyle.SMOOTH,
    val wallpaper: AnimatedWallpaper = AnimatedWallpaper.MESH,
) {
    /**
     * Which shelf of the gallery this lands on.
     *
     * Derived rather than stored, and that is deliberate: the family is a *claim
     * about the colour*, and a stored one would go stale the moment the accent was
     * dragged past a boundary. A theme cannot be filed under "Warm" and come out
     * green if nobody is allowed to file it.
     */
    val family: ThemeFamily get() = familyFor(accentChroma, accentHue)

    /**
     * This theme as the palette generator takes it, with every value clamped.
     *
     * The clamping is the point. [ranges] is the same source the editor's sliders
     * read, so a value that cannot be reached by moving a slider cannot be reached
     * by hand-editing an exported file either — and a chroma of 900 or a corner
     * radius of -1 becomes a bounded one here rather than an exception several
     * layers down inside a draw call.
     */
    fun toRecipe(): ThemeRecipe = ThemeRecipe(
        // The seed is only a fallback identity. Nothing about the palette comes
        // from it: every field below is this theme's own.
        id = ThemeRecipe.DEFAULT,
        customId = id,
        customName = name,
        family = family,
        accentHue = accentHue.asHue(),
        accentChroma = accentChroma.coerceIn(ACCENT_CHROMA),
        secondaryHueShift = secondaryHueShift.coerceIn(HUE_OFFSET),
        accentSpread = accentSpread.coerceIn(ACCENT_SPREAD),
        cursorHueShift = cursorHueShift.coerceIn(HUE_OFFSET),
        neutralHue = neutralHue.asHue(),
        neutralChroma = neutralChroma.coerceIn(NEUTRAL_CHROMA),
        groundShift = groundShift.coerceIn(GROUND_SHIFT),
        material = ThemeMaterial(
            surface = SurfaceTreatment.forStyle(surfaceStyle),
            cornerRadiusDp = cornerRadiusDp.coerceIn(CORNER_RADIUS_DP),
            surfaceAlpha = surfaceAlpha.coerceIn(SURFACE_ALPHA),
            blurRadiusDp = blurRadiusDp.coerceIn(BLUR_RADIUS_DP),
            grain = grain.coerceIn(GRAIN),
            backgroundDepth = backgroundDepth.coerceIn(BACKGROUND_DEPTH),
        ),
        motion = motion,
        defaultWallpaper = wallpaper,
    )

    /**
     * The accent as a colour, for showing it.
     *
     * A theme stores a *direction* — a hue and a chroma — and not a colour,
     * because the lightness belongs to the contrast dial and the light/dark
     * choice; see [ThemeOptions.accentOverrideArgb], which has always taken a
     * picked swatch the same way. So this resolves the stored direction at a fixed
     * reference lightness purely so there is something to draw in a swatch and
     * something to name in a list. It is never stored and never reaches the
     * palette.
     */
    val accentArgb: Long get() = Oklch(REFERENCE_LIGHTNESS, accentChroma, accentHue).toArgb()

    /** Six hex digits, which is how most people name a colour to each other. */
    val accentHex: String get() = "#%06X".format(accentArgb and 0xFFFFFFL)

    companion object {
        const val MAX_NAME_LENGTH = 28

        /**
         * The lightness the accent is *shown* at, and nothing more.
         *
         * Mid-bright, so a colour reads as itself in a swatch rather than as a
         * pastel or a near-black. [ThemeRecipe.resolve] computes the real accent
         * lightness from the ground and the contrast level every time.
         */
        const val REFERENCE_LIGHTNESS = 0.62f

        /**
         * The bounds every parameter is held to, shared with the editor's sliders.
         *
         * One source rather than two. A range written once in the editor and again
         * in a sanitiser drifts, and when it does the symptom is a slider that
         * silently cannot reach its own end stop.
         */
        val ACCENT_CHROMA = 0f..0.24f
        val HUE_OFFSET = -180f..180f
        val ACCENT_SPREAD = -90f..90f

        /**
         * Past about 0.03 the greys stop reading as grey and start competing with
         * the accent. The ceiling is above that on purpose — it is a warning, not
         * a rule, and somebody building a deliberately lurid theme is entitled to.
         */
        val NEUTRAL_CHROMA = 0f..0.06f
        val GROUND_SHIFT = 0f..0.28f
        val CORNER_RADIUS_DP = 0..36
        val SURFACE_ALPHA = 0.25f..1f
        val BLUR_RADIUS_DP = 0..48
        val GRAIN = 0f..0.25f
        val BACKGROUND_DEPTH = 0f..0.45f

        /**
         * Above this hue a colour has turned green; below it, it is still warm.
         *
         * These are the same two landmarks `ThemeSpecTest` holds the bundled
         * themes to, so a custom theme is shelved by the rule the bundled ones are
         * checked against rather than by a second opinion.
         */
        private const val WARM_UPPER_BOUND = 110f
        private const val WARM_LOWER_BOUND = 320f

        /**
         * The shelf a colour belongs on.
         *
         * Chroma first, because a hue is only a direction: with no chroma to
         * travel along it, it points nowhere and the theme is neutral whatever its
         * hue claims. [ThemeFamily.EDITOR] is never derived — that shelf is about
         * where a palette came from, which is a fact about its history rather than
         * about its numbers, and nothing the user builds here has one.
         */
        fun familyFor(chroma: Float, hue: Float): ThemeFamily {
            if (chroma <= ThemeRecipe.NEUTRAL_CHROMA_CEILING) return ThemeFamily.NEUTRAL
            val warm = hue.mod(360f).let { it < WARM_UPPER_BOUND || it > WARM_LOWER_BOUND }
            return if (warm) ThemeFamily.WARM else ThemeFamily.COOL
        }

        /**
         * A new theme carrying everything [recipe] declares.
         *
         * Seeding from a bundled theme rather than from blank defaults, because a
         * theme editor opened on nothing is a wall of sliders with no idea which
         * way any of them should go. Starting from one that already works means
         * every change is an edit with something to compare against.
         */
        fun seededFrom(
            recipe: ThemeRecipe,
            id: String,
            name: String,
        ): CustomTheme = CustomTheme(
            id = id,
            name = name,
            accentHue = recipe.accentHue,
            accentChroma = recipe.accentChroma,
            secondaryHueShift = recipe.secondaryHueShift,
            accentSpread = recipe.accentSpread,
            cursorHueShift = recipe.cursorHueShift,
            neutralHue = recipe.neutralHue,
            neutralChroma = recipe.neutralChroma,
            groundShift = recipe.groundShift,
            surfaceStyle = recipe.material.surface.style,
            cornerRadiusDp = recipe.material.cornerRadiusDp,
            surfaceAlpha = recipe.material.surfaceAlpha,
            blurRadiusDp = recipe.material.blurRadiusDp,
            grain = recipe.material.grain,
            backgroundDepth = recipe.material.backgroundDepth,
            motion = recipe.motion,
            wallpaper = recipe.defaultWallpaper,
        )

        /** Ids name a file on export, so they are held to something inert. */
        fun isValidId(id: String): Boolean =
            id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }

        /**
         * An id not already taken.
         *
         * Sequential rather than random, because [Math.random] is not available to
         * the model and a counter is legible in an exported file besides.
         */
        fun freshId(taken: Collection<String>): String {
            var counter = 1
            while ("custom-$counter" in taken) counter++
            return "custom-$counter"
        }
    }
}

/**
 * A hue angle brought into 0..360.
 *
 * `mod` already wraps a negative angle round the right way; the `+ 0f` is what
 * removes the *negative zero* it returns for an exact multiple of a negative input
 * — `(-720f).mod(360f)` is `-0.0`. That draws identically to `0.0`, since every use
 * of a hue is trigonometric, but it compares as less than zero, which is exactly
 * the kind of value that trips a bounds check several layers away for a reason
 * nobody can see from the failure. Normalised once, here.
 */
private fun Float.asHue(): Float = mod(360f) + 0f

/**
 * Trims a typed theme name to something storable.
 *
 * Display-only — the id does the identifying — so this guards length and
 * emptiness rather than rejecting characters. An all-whitespace name would render
 * as an unlabelled card in the gallery, so it falls back.
 */
fun sanitizeThemeName(raw: String, fallback: String = "My theme"): String {
    val trimmed = raw.trim().take(CustomTheme.MAX_NAME_LENGTH)
    return trimmed.ifBlank { fallback }
}

/**
 * Picks a name not already taken, appending a counter.
 *
 * Duplicate names are legal — themes are keyed by id — but two identically
 * labelled cards in the gallery are indistinguishable to whoever is choosing.
 */
fun uniqueThemeName(desired: String, taken: Collection<String>): String {
    val base = sanitizeThemeName(desired)
    if (taken.none { it.equals(base, ignoreCase = true) }) return base
    var counter = 2
    while (true) {
        val candidate = "$base $counter"
        if (taken.none { it.equals(candidate, ignoreCase = true) }) return candidate
        counter++
    }
}

/**
 * A theme as it travels between devices.
 *
 * Wrapped rather than exported as a bare [CustomTheme] so a file can say what it
 * is before anything tries to parse it as one. The importer checks [kind] first,
 * which is what lets "that is not a theme file" be a message rather than a
 * confusing partial import of whatever JSON was picked.
 *
 * The id is deliberately *not* carried. An id is local — it says which theme this
 * is on *this* device — and importing one that collided with an existing theme
 * would silently overwrite it. The importer assigns a fresh id on arrival.
 */
@Serializable
data class ThemeFile(
    val kind: String = KIND,
    /** Bumped only if the shape changes incompatibly; readers accept anything lower. */
    val version: Int = VERSION,
    val theme: CustomTheme,
) {
    val isValid: Boolean get() = kind == KIND && version <= VERSION

    companion object {
        const val KIND = "loki.theme"
        const val VERSION = 1
    }
}
