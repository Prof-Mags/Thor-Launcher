package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The bundled themes.
 *
 * Twelve of them, four to a [ThemeFamily], and every one available light or dark —
 * which is the change that made twelve enough. The set used to be fifteen because
 * light and dark were different *themes*: five lights that were nobody's first
 * choice of colour, and ten darks among which the differences were often a single
 * accent. Brightness is a preference, not an identity, so it moved to
 * [PersonalizationSettings.themeMode] and the gallery went back to being twelve
 * distinct answers to "what colour is this launcher".
 *
 * A theme is a [ThemeRecipe] — a seed and a material character — not a table of
 * hex values. See [ThemeRecipe] for why.
 *
 * Retiring an id is safe: a settings file naming a theme that no longer exists
 * coerces back to the default rather than failing to parse — see
 * `SettingsSerializer` — and [ThemeRecipe.of] never throws for an id it cannot
 * find.
 */
@Serializable
enum class ThemeId(val displayName: String) {
    // ---- Neutral -------------------------------------------------------
    // Material first, because it is what a fresh install opens on: a gallery
    // whose default sits six cards along asks the reader to hunt for where they
    // already are.
    MATERIAL("Material"),
    OBSIDIAN("Obsidian"),
    SLATE("Slate"),
    LINEN("Linen"),

    // ---- Warm ----------------------------------------------------------
    EMBER("Ember"),
    CITRINE("Citrine"),
    SAKURA("Sakura"),
    VAPOR("Vapor"),

    // ---- Cool ----------------------------------------------------------
    NOCTURNE("Nocturne"),
    AURORA("Aurora"),
    ORCHID("Orchid"),
    TERMINAL("Terminal"),

    // ---- Editor --------------------------------------------------------
    ONE_DARK("One Dark"),
    PALENIGHT("Palenight"),
}

/**
 * Which shelf of the gallery a theme belongs on.
 *
 * Temperature rather than brightness, and that is only possible now that
 * brightness is the reader's own choice: a "Light" shelf made sense when a theme
 * dictated its polarity, and became a lie the moment every theme could be either.
 * What a theme still commits to is its colour — whether it leans warm, leans cool,
 * or declines to lean at all — so that is what the shelves are.
 */
@Serializable
enum class ThemeFamily(val label: String) {
    /** Barely-there chroma: the greys are the design and the accent stays quiet. */
    NEUTRAL("Neutral"),

    /** Reds through yellows. */
    WARM("Warm"),

    /** Greens through violets. */
    COOL("Cool"),

    /**
     * Palettes taken from code editors.
     *
     * A shelf about where a theme came from rather than what colour it is, which
     * is the axis somebody looking for One Dark is actually searching on — nobody
     * arrives at that name by browsing temperature. What the themes on it have in
     * common is the thing that makes an editor palette an editor palette: a
     * mid-toned ground rather than a near-black one, because a screen you read
     * text on all day is not a screen you want at maximum depth.
     */
    EDITOR("Editor"),
}

/**
 * Light, dark, or whatever the system is doing.
 *
 * Applies to every theme rather than to some of them, which is the point of
 * generating palettes instead of writing them down: Ember light and Ember dark are
 * the same recipe resolved against a different ground, so neither one had to be
 * drawn by hand and neither can drift from the other.
 */
@Serializable
enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),

    /** Follows Android's own light/dark setting, including its schedule. */
    SYSTEM("Follow system"),
}

/**
 * How far apart the palette pushes its foreground and its ground.
 *
 * A generated palette can be held to a contrast *ratio* rather than checked
 * against one after the fact, so this is a real dial rather than the single
 * high-contrast override it replaces. That override worked by pinning text to
 * pure white or black over a pure black or white ground, which cleared the bar and
 * threw away the theme; here the same bar is met by moving text only as far as it
 * has to go, and the ground keeps its colour at every level.
 *
 * The ratios are WCAG. [NORMAL] already clears AA for body text with room to
 * spare — the launcher is read at arm's length on a handheld and across a room on
 * a television, neither of which is the desk the standard was written for.
 */
@Serializable
enum class ContrastLevel(
    val label: String,
    /** Lightness of the ground when the palette resolves dark. */
    internal val darkGround: Float,
    /** Gap in lightness between adjacent dark surfaces. */
    internal val darkStep: Float,
    /**
     * Lightness of the ground when the palette resolves light.
     *
     * Near white, and the light ramp descends from it — see [surfaceRamp].
     */
    internal val lightGround: Float,
    /** Gap in lightness between adjacent light surfaces. */
    internal val lightStep: Float,
    /** Ratio body text must clear against the surface behind it. */
    val bodyRatio: Float,
    /** Ratio secondary and metadata text must clear. */
    val mutedRatio: Float,
    /** Extra distance the accent is pushed away from the ground. */
    internal val accentPush: Float,
    /**
     * How much of a theme's own [ThemeRecipe.groundShift] survives at this level.
     *
     * A theme that lifts its ground off the extreme is asking for a softer look,
     * and at the top of this dial that is the opposite of what the user asked for
     * — so the dial wins, progressively. It also has to: a ground lifted to
     * One Dark's mid-grey simply cannot carry 17.5:1, and a promise the palette
     * cannot keep is worse than a theme that reads a little less like itself.
     */
    internal val groundShiftScale: Float,
) {
    /** Lower contrast than the default: a softer, flatter, more ambient look. */
    SOFT("Softened", 0.205f, 0.042f, 0.962f, 0.045f, 6.8f, 4.6f, -0.02f, 1.0f),

    /*
     * Well past AA, on purpose.
     *
     * The bar the standard sets is 4.5, and text generated to sit exactly on it
     * comes out a mid-grey — measurably readable and visibly dishwater, which is
     * not what the hand-tuned palettes this replaced looked like. The standard was
     * written for a document at desk distance; this is read at arm's length on a
     * handheld and from a sofa on a television, and both of those want more.
     */
    NORMAL("Normal", 0.158f, 0.050f, 0.982f, 0.050f, 10.5f, 5.6f, 0f, 1.0f),

    HIGH("High", 0.112f, 0.055f, 0.992f, 0.052f, 14.0f, 7.0f, 0.05f, 0.5f),

    /** As far as the palette can be pushed while still being the same theme. */
    /*
     * The top of the dial is bounded by physics, not by taste.
     *
     * A light palette's ceiling is the ratio between its own base surface and pure
     * black, and that surface cannot be white — three panels have to fit below it
     * and still be told apart. 17:1 is what is left once both of those are paid
     * for, and asking for more would be a promise the generator could not keep.
     */
    MAXIMUM("Maximum", 0.045f, 0.060f, 1.0f, 0.052f, 17.0f, 10.0f, 0.09f, 0.15f),
}

/**
 * A theme, written as intent rather than as a table of colours.
 *
 * Every palette in the launcher used to be about thirty hand-picked ARGB values,
 * which is thirty chances per theme to write a surface ramp that goes backwards or
 * body text nobody can read — and the only defence was a test that caught the
 * mistake after it was made. Worse, it made the set *lopsided by gravity*: adding
 * a theme meant an evening with a colour picker, so the ones that got added were
 * the easy ones, near-copies of what was already there, and light themes were
 * always an afterthought because they are the hardest to hand-tune.
 *
 * A recipe is a seed and a character. The ramp, the on-colours, the outline and
 * the accent gradient are *derived* from it, in OKLCH, where lightness means what
 * the eye means by it — see [Oklch]. That is what makes contrast a guarantee
 * rather than a hope, makes light and dark two resolutions of one description
 * rather than two themes, and makes the user's own dials — hue, intensity,
 * contrast, pure black — apply to all twelve instead of to none.
 *
 * What is left here is only what a theme genuinely disagrees with another about.
 */
data class ThemeRecipe(
    val id: ThemeId,
    /**
     * Set when this recipe came from a [CustomTheme] rather than from the list below.
     *
     * Identity for a user-made theme had to live *beside* [id] rather than inside
     * it. [ThemeId] is a sealed set the bundled gallery is built from and that
     * `ThemeSpecTest` holds to a shelf count, so a `CUSTOM` constant would have been
     * an entry with no recipe behind it — and every exhaustive `when` over the enum
     * would have gained a branch that could not answer. A nullable string beside the
     * enum leaves the bundled themes exactly as they were and says the one extra
     * thing that needs saying.
     *
     * [id] on a custom recipe is only the fallback identity. Nothing about the
     * palette comes from it.
     */
    val customId: String? = null,
    /** The name a custom theme was saved under; null for the bundled ones. */
    val customName: String? = null,
    val family: ThemeFamily,
    /** Hue of the accent, in OKLCH degrees. See [Oklch] for the landmarks. */
    val accentHue: Float,
    /**
     * Chroma of the accent — how colourful the theme is at its loudest point.
     *
     * The single number that separates a [ThemeFamily.NEUTRAL] theme from the
     * rest, so it is not a free parameter: below [NEUTRAL_CHROMA_CEILING] a theme
     * belongs on the neutral shelf whatever its hue says.
     */
    val accentChroma: Float,
    /** Degrees the secondary accent sits from the primary. */
    val secondaryHueShift: Float = 34f,
    /** Degrees the far end of the accent gradient sits from the primary. */
    val accentSpread: Float = 20f,
    /**
     * Degrees the selection cursor sits from the primary.
     *
     * Zero on almost every theme — the cursor is the accent, and a launcher whose
     * highlight disagrees with its own palette looks broken rather than designed.
     * Non-zero only where the contrast is the character, as on [ThemeId.VAPOR],
     * whose whole identity is a cyan cursor against magenta furniture.
     */
    val cursorHueShift: Float = 0f,
    /** Hue the greys are tinted toward. Usually the accent's own. */
    val neutralHue: Float = accentHue,
    /**
     * How far the greys are tinted, 0 being a true neutral.
     *
     * Small numbers: past about 0.03 the surfaces stop reading as grey and start
     * competing with the accent for the same job.
     */
    val neutralChroma: Float = 0.018f,
    /**
     * How far this theme lifts its ground off the extreme the contrast level picked.
     *
     * Zero on almost everything: a theme's ground is the contrast dial's business,
     * not the theme's. The exception is a theme whose whole character *is* a
     * mid-toned field rather than a near-black one — the code-editor palettes on
     * the [ThemeFamily.EDITOR] shelf are exactly that, and resolving One Dark onto
     * a near-black ground would produce something that is not One Dark in any
     * respect a user would recognise.
     *
     * Scaled down as the contrast dial rises; see [ContrastLevel.groundShiftScale].
     */
    val groundShift: Float = 0f,
    val material: ThemeMaterial,
    val motion: MotionStyle = MotionStyle.SMOOTH,
    /** Wallpaper applied when this theme is selected from the gallery. */
    val defaultWallpaper: AnimatedWallpaper = AnimatedWallpaper.MESH,
) {
    /**
     * What identifies this theme among all of them, bundled or made.
     *
     * The gallery keys its cards on this and the settings file stores it, so it has
     * to be unique across both sets. Custom ids are checked to be inert and are
     * generated with a `custom-` prefix, which no [ThemeId] constant can collide
     * with.
     */
    val key: String get() = customId ?: id.name

    /** What the gallery writes under the card. */
    val displayName: String get() = customName ?: id.displayName

    /** True for a theme the user made, which is the only kind that can be edited. */
    val isCustom: Boolean get() = customId != null

    /**
     * Builds the palette.
     *
     * The whole of it comes from four decisions: where the ground sits, how far
     * apart the surfaces step, which direction the colour points, and how hard the
     * text has to work to be read. Everything else follows, which is why there are
     * no colours written down anywhere in this file.
     */
    fun resolve(options: ThemeOptions = ThemeOptions()): ThemeSpec {
        val dark = options.dark
        val contrast = options.contrast
        val intensity = options.colorIntensity.coerceIn(0f, MAX_INTENSITY)

        /*
         * A picked accent supplies a direction, not a colour.
         *
         * Its chroma is floored rather than taken as given, because a swatch that
         * is almost grey would otherwise silently turn every theme into Obsidian —
         * and a user who picks a colour has said they want one.
         */
        val override = options.accentOverrideArgb?.let { Oklch.fromArgb(it) }
        val hue = (override?.h ?: (accentHue + options.hueShift)).mod(360f)
        val chroma = (override?.c?.coerceAtLeast(MIN_OVERRIDE_CHROMA) ?: accentChroma) * intensity
        // An overridden accent drags the greys with it. Leaving them tinted toward
        // the hue that was replaced is what made a custom accent look bolted on.
        val greyHue = if (override != null) hue else (neutralHue + options.hueShift).mod(360f)
        val greyChroma = neutralChroma * intensity

        val ramp = surfaceRamp(
            dark = dark,
            contrast = contrast,
            groundShift = groundShift * contrast.groundShiftScale,
            pureBlack = options.pureBlack,
        )
        val taper = if (dark) DARK_TINT_TAPER else LIGHT_TINT_TAPER
        val surfaces = ramp.mapIndexed { level, lightness ->
            Oklch(lightness, greyChroma * taper[level], greyHue)
        }
        val background = surfaces[0]
        val panel = surfaces[1]

        val accentLightness = if (dark) {
            DARK_ACCENT_LIGHTNESS + contrast.accentPush
        } else {
            LIGHT_ACCENT_LIGHTNESS - contrast.accentPush
        }.coerceIn(0.2f, 0.95f)
        val accent = Oklch(accentLightness, chroma, hue)
        val cursor = accent.rotate(cursorHueShift)

        // Text chroma is capped well below the greys': a tint that reads as warmth
        // on a large panel reads as a printing fault on a glyph stroke.
        val textChroma = minOf(greyChroma, MAX_TEXT_CHROMA)

        return ThemeSpec(
            id = id,
            customId = customId,
            displayName = displayName,
            family = family,
            isDark = dark,
            primaryArgb = accent.toArgb(),
            secondaryArgb = accent.rotate(secondaryHueShift).saturate(0.9f).toArgb(),
            accentEndArgb = accent
                .rotate(accentSpread)
                .lighten(if (dark) ACCENT_END_LIFT else -ACCENT_END_LIFT * 0.5f)
                .saturate(0.95f)
                .toArgb(),
            backgroundArgb = background.toArgb(),
            surfaceArgb = panel.toArgb(),
            surfaceElevatedArgb = surfaces[2].toArgb(),
            surfaceHighestArgb = surfaces[3].toArgb(),
            onBackgroundArgb =
                readableOn(background, dark, greyHue, textChroma, contrast.bodyRatio).toArgb(),
            onSurfaceArgb =
                readableOn(panel, dark, greyHue, textChroma, contrast.bodyRatio).toArgb(),
            onSurfaceVariantArgb =
                readableOn(panel, dark, greyHue, textChroma, contrast.mutedRatio).toArgb(),
            cursorArgb = cursor.toArgb(),
            glowArgb = cursor.toArgb(alpha = if (dark) DARK_GLOW_ALPHA else LIGHT_GLOW_ALPHA),
            outlineArgb = Oklch(
                // One step past the far end of the ramp, so the edge reads on the
                // highest panel as well as on the ground. Measured from `surface`
                // before, which on a light palette put it *inside* the ramp — an
                // outline lighter than the card it was outlining.
                l = if (dark) {
                    surfaces[3].l + DARK_OUTLINE_STEP
                } else {
                    surfaces[3].l - LIGHT_OUTLINE_STEP
                },
                c = greyChroma * 0.9f,
                h = greyHue,
            ).toArgb(),
            errorArgb = Oklch(
                l = if (dark) DARK_ERROR_LIGHTNESS else LIGHT_ERROR_LIGHTNESS,
                c = ERROR_CHROMA,
                h = ERROR_HUE,
            ).toArgb(),
            cornerRadiusDp = material.cornerRadiusDp,
            surfaceAlpha = material.surfaceAlpha,
            blurRadiusDp = material.blurRadiusDp,
            // Grain and the accent wash both go over true black: noise on #000
            // reads as sensor dirt, and a gradient on it is banding. The whole
            // reason to ask for pure black is that the ground is nothing at all.
            grain = if (options.pureBlack && dark) {
                0f
            } else {
                (material.grain * options.grainScale).coerceIn(0f, 1f)
            },
            defaultWallpaper = defaultWallpaper,
            motion = motion,
            // An overridden style takes its preset whole rather than keeping the
            // theme's adjustments to the style it replaced: "glass, but with
            // Terminal's hard 1.5dp border" is not glass, and the label said glass.
            surface = (options.surfaceStyle?.let { SurfaceTreatment.forStyle(it) }
                ?: material.surface)
                .let { if (dark) it else it.forLightGround() },
            backgroundDepth = if (options.pureBlack && dark) {
                0f
            } else {
                (material.backgroundDepth * options.depthScale).coerceIn(0f, 1f)
            },
        )
    }

    companion object {
        /**
         * Every bundled theme, in gallery order.
         *
         * Grouped by [ThemeFamily] so the row reads as shelves rather than as one
         * long strip: the neutrals first, because the default is one of them and
         * the first card should be where the reader already is, then warm, then
         * cool, then the editor ports.
         */
        val ALL: List<ThemeRecipe> = listOf(
            // ---- Neutral ---------------------------------------------------
            ThemeRecipe(
                id = ThemeId.MATERIAL, family = ThemeFamily.NEUTRAL,
                accentHue = 278f, accentChroma = 0.078f,
                secondaryHueShift = 34f, neutralChroma = 0.019f,
                // The strongest elevation tint in the set, which is the idea this
                // preset is named for. It is also what a fresh install opens on,
                // so it has to look considered with no wallpaper chosen and
                // nothing in the library yet.
                material = ThemeMaterial(
                    surface = SurfaceTreatment.TINTED.copy(elevationTint = 0.11f),
                    cornerRadiusDp = 24, surfaceAlpha = 0.94f, blurRadiusDp = 18,
                    grain = 0.03f, backgroundDepth = 0.09f,
                ),
                defaultWallpaper = AnimatedWallpaper.MESH,
            ),
            ThemeRecipe(
                // Chroma this low is a deliberate refusal: the accent is a warm
                // white, so nothing on screen is coloured and the surface ramp
                // has to carry the whole design on its own.
                id = ThemeId.OBSIDIAN, family = ThemeFamily.NEUTRAL,
                accentHue = 264f, accentChroma = 0.013f,
                secondaryHueShift = 0f, accentSpread = 0f, neutralChroma = 0.005f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.FLAT.copy(borderAlpha = 0.7f),
                    cornerRadiusDp = 10, surfaceAlpha = 1f, blurRadiusDp = 0,
                    grain = 0.05f, backgroundDepth = 0.02f,
                ),
                motion = MotionStyle.FLUID,
                defaultWallpaper = AnimatedWallpaper.BOKEH,
            ),
            ThemeRecipe(
                id = ThemeId.SLATE, family = ThemeFamily.NEUTRAL,
                accentHue = 236f, accentChroma = 0.068f,
                secondaryHueShift = -24f, neutralHue = 240f, neutralChroma = 0.023f,
                // Hard corners and a real shadow: a tool rather than a toy.
                material = ThemeMaterial(
                    surface = SurfaceTreatment.RAISED.copy(shadowElevationDp = 6),
                    cornerRadiusDp = 6, surfaceAlpha = 0.96f, blurRadiusDp = 12,
                    grain = 0.04f, backgroundDepth = 0.05f,
                ),
                defaultWallpaper = AnimatedWallpaper.GRADIENT_DRIFT,
            ),
            ThemeRecipe(
                id = ThemeId.LINEN, family = ThemeFamily.NEUTRAL,
                accentHue = 76f, accentChroma = 0.064f,
                secondaryHueShift = 62f, neutralHue = 78f, neutralChroma = 0.021f,
                // Visible grain and a short, soft shadow: paper stock lying on
                // paper stock, not a card floating over a page.
                material = ThemeMaterial(
                    surface = SurfaceTreatment.RAISED.copy(
                        shadowElevationDp = 4,
                        borderAlpha = 0.5f,
                    ),
                    cornerRadiusDp = 14, surfaceAlpha = 0.97f, blurRadiusDp = 10,
                    grain = 0.085f, backgroundDepth = 0.03f,
                ),
                defaultWallpaper = AnimatedWallpaper.NONE,
            ),

            // ---- Warm ------------------------------------------------------
            ThemeRecipe(
                id = ThemeId.EMBER, family = ThemeFamily.WARM,
                accentHue = 44f, accentChroma = 0.155f,
                secondaryHueShift = -20f, neutralHue = 52f, neutralChroma = 0.023f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.GLASS,
                    cornerRadiusDp = 18, surfaceAlpha = 0.88f, blurRadiusDp = 24,
                    grain = 0.055f, backgroundDepth = 0.17f,
                ),
                defaultWallpaper = AnimatedWallpaper.AURORA,
            ),
            ThemeRecipe(
                id = ThemeId.CITRINE, family = ThemeFamily.WARM,
                accentHue = 88f, accentChroma = 0.145f,
                secondaryHueShift = -32f, neutralHue = 84f, neutralChroma = 0.021f,
                // Opaque tiles with generous shadows under them, and no blur to
                // pay for: the one theme in the set built for a weak GPU.
                material = ThemeMaterial(
                    surface = SurfaceTreatment.RAISED.copy(
                        shadowElevationDp = 10,
                        borderAlpha = 0.3f,
                    ),
                    cornerRadiusDp = 20, surfaceAlpha = 1f, blurRadiusDp = 0,
                    grain = 0.03f, backgroundDepth = 0.12f,
                ),
                motion = MotionStyle.SNAPPY,
                defaultWallpaper = AnimatedWallpaper.BOKEH,
            ),
            ThemeRecipe(
                id = ThemeId.SAKURA, family = ThemeFamily.WARM,
                accentHue = 355f, accentChroma = 0.118f,
                secondaryHueShift = 26f, neutralHue = 350f, neutralChroma = 0.021f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.GLASS.copy(specularAlpha = 0.3f),
                    cornerRadiusDp = 28, surfaceAlpha = 0.9f, blurRadiusDp = 30,
                    grain = 0.035f, backgroundDepth = 0.14f,
                ),
                motion = MotionStyle.FLUID,
                defaultWallpaper = AnimatedWallpaper.MESH,
            ),
            ThemeRecipe(
                id = ThemeId.VAPOR, family = ThemeFamily.WARM,
                accentHue = 335f, accentChroma = 0.2f,
                // Magenta furniture, cyan cursor: the two-colour clash is the
                // whole theme, so the cursor is thrown to the far side of the
                // wheel rather than sitting beside the accent.
                secondaryHueShift = -140f, cursorHueShift = -140f,
                neutralHue = 315f, neutralChroma = 0.03f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.FLAT.copy(
                        borderWidthDp = 1.5f,
                        specularAlpha = 0.1f,
                    ),
                    cornerRadiusDp = 2, surfaceAlpha = 0.9f, blurRadiusDp = 20,
                    grain = 0.075f, backgroundDepth = 0.22f,
                ),
                motion = MotionStyle.SNAPPY,
                defaultWallpaper = AnimatedWallpaper.WAVES,
            ),

            // ---- Cool ------------------------------------------------------
            ThemeRecipe(
                id = ThemeId.NOCTURNE, family = ThemeFamily.COOL,
                accentHue = 256f, accentChroma = 0.135f,
                secondaryHueShift = 46f, neutralHue = 262f, neutralChroma = 0.025f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.GLASS,
                    cornerRadiusDp = 22, surfaceAlpha = 0.86f, blurRadiusDp = 28,
                    grain = 0.035f, backgroundDepth = 0.16f,
                ),
                defaultWallpaper = AnimatedWallpaper.STARFIELD,
            ),
            ThemeRecipe(
                id = ThemeId.AURORA, family = ThemeFamily.COOL,
                accentHue = 168f, accentChroma = 0.14f,
                secondaryHueShift = 76f, neutralHue = 176f, neutralChroma = 0.025f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.GLASS.copy(specularAlpha = 0.28f),
                    cornerRadiusDp = 20, surfaceAlpha = 0.88f, blurRadiusDp = 26,
                    grain = 0.04f, backgroundDepth = 0.16f,
                ),
                motion = MotionStyle.FLUID,
                defaultWallpaper = AnimatedWallpaper.WAVES,
            ),
            ThemeRecipe(
                id = ThemeId.ORCHID, family = ThemeFamily.COOL,
                accentHue = 302f, accentChroma = 0.15f,
                secondaryHueShift = 42f, neutralHue = 298f, neutralChroma = 0.027f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.GLASS,
                    cornerRadiusDp = 26, surfaceAlpha = 0.88f, blurRadiusDp = 30,
                    grain = 0.04f, backgroundDepth = 0.18f,
                ),
                motion = MotionStyle.FLUID,
                defaultWallpaper = AnimatedWallpaper.PARTICLES,
            ),
            ThemeRecipe(
                id = ThemeId.TERMINAL, family = ThemeFamily.COOL,
                accentHue = 148f, accentChroma = 0.19f,
                secondaryHueShift = 50f, neutralHue = 150f, neutralChroma = 0.028f,
                // Square, hard-edged, heavily grained and unblurred. The only
                // theme in the set with a zero corner radius, and it means it.
                material = ThemeMaterial(
                    surface = SurfaceTreatment.FLAT.copy(borderWidthDp = 1.5f),
                    cornerRadiusDp = 0, surfaceAlpha = 0.94f, blurRadiusDp = 0,
                    grain = 0.11f, backgroundDepth = 0.2f,
                ),
                motion = MotionStyle.MECHANICAL,
                defaultWallpaper = AnimatedWallpaper.PARTICLES,
            ),

            // ---- Editor ----------------------------------------------------
            /*
             * Ports rather than homages, as far as a launcher can be one.
             *
             * What carries over is the part that makes these palettes
             * recognisable: the ground and the accent, measured out of the
             * originals in OKLCH. #282C34 and #292D3E are not near-black, and that
             * is the whole of why they read as editor themes rather than as
             * another two dark launchers — hence `groundShift`, which exists for
             * these two and nothing else.
             *
             * What does not carry over is the typography. A theme no longer
             * carries a typeface at all: a whole launcher set in a monospace face
             * is a costume rather than a theme, and the launcher has one family
             * everywhere now. See `ThorTypography`.
             */
            ThemeRecipe(
                // Atom's One Dark, by way of the editor extension. Ground #282C34,
                // accent #61AFEF.
                id = ThemeId.ONE_DARK, family = ThemeFamily.EDITOR,
                accentHue = 245f, accentChroma = 0.121f,
                secondaryHueShift = 66f, accentSpread = 16f,
                neutralHue = 254f, neutralChroma = 0.0204f,
                groundShift = 0.142f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.TINTED.copy(elevationTint = 0.05f),
                    cornerRadiusDp = 12, surfaceAlpha = 0.96f, blurRadiusDp = 14,
                    grain = 0.03f, backgroundDepth = 0.06f,
                ),
                defaultWallpaper = AnimatedWallpaper.GRADIENT_DRIFT,
            ),
            ThemeRecipe(
                // Material Theme's Palenight. Ground #292D3E, accent #C792EA, with
                // its blue #82AAFF as the secondary.
                id = ThemeId.PALENIGHT, family = ThemeFamily.EDITOR,
                accentHue = 311f, accentChroma = 0.1345f,
                secondaryHueShift = -60f, accentSpread = 18f,
                neutralHue = 274f, neutralChroma = 0.0369f,
                groundShift = 0.143f,
                material = ThemeMaterial(
                    surface = SurfaceTreatment.TINTED.copy(elevationTint = 0.06f),
                    cornerRadiusDp = 10, surfaceAlpha = 0.94f, blurRadiusDp = 18,
                    grain = 0.03f, backgroundDepth = 0.08f,
                ),
                defaultWallpaper = AnimatedWallpaper.MESH,
            ),
        )

        val BY_ID: Map<ThemeId, ThemeRecipe> = ALL.associateBy(ThemeRecipe::id)

        /** What a fresh install opens on, and the fallback for a retired id. */
        val DEFAULT: ThemeId = ThemeId.MATERIAL

        /**
         * The recipe for an id, falling back to the default theme.
         *
         * Never throws: an id can outlive its recipe if a theme is retired while a
         * user has it selected, and a launcher that cannot build a palette cannot
         * draw anything at all.
         */
        fun of(id: ThemeId): ThemeRecipe = BY_ID[id] ?: BY_ID.getValue(DEFAULT)

        /** The themes on one shelf of the gallery, in gallery order. */
        fun family(family: ThemeFamily): List<ThemeRecipe> = ALL.filter { it.family == family }

        /**
         * At or below this a theme is neutral whatever its hue claims.
         *
         * A hue is only a direction; with no chroma to travel along it, it points
         * nowhere. Enforced rather than documented — see `ThemeSpecTest`.
         */
        const val NEUTRAL_CHROMA_CEILING = 0.085f

        /** At or above this a theme is genuinely leading with a colour. */
        const val COLOURED_CHROMA_FLOOR = 0.1f
    }
}

/**
 * How a theme's panels are built, independent of what colour they are.
 *
 * Colour alone was never what separated the presets. A raised panel is an opaque
 * card with a shadow under it, a glass panel is a lit sheet, and a flat one is a
 * rectangle with a hard edge — and with only a radius and an alpha to describe
 * them, all three came out as the same rounded box in different colours.
 */
data class ThemeMaterial(
    /** How panels composite over what is behind them. */
    val surface: SurfaceTreatment,
    /** Corner radius applied to panels, in dp. */
    val cornerRadiusDp: Int,
    /** Alpha applied to translucent surfaces, 0..1. */
    val surfaceAlpha: Float,
    /** Backdrop blur radius behind translucent surfaces, in dp. */
    val blurRadiusDp: Int,
    /**
     * Film grain strength over the wallpaper, 0..1.
     *
     * Large flat gradients band visibly on an OLED panel; a little noise breaks
     * the bands up and is the cheapest way to stop a background looking like a
     * compression artefact.
     */
    val grain: Float,
    /**
     * How far the background graduates toward the accent, 0..1. Zero is flat.
     *
     * One number rather than a hand-written gradient per theme: the wash is always
     * the same shape — darker at one end, lifted toward the accent at the other —
     * and only its strength differs. Sits *under* the wallpaper rather than
     * instead of it, so a theme still has ground of its own when every effect is
     * switched off.
     */
    val backgroundDepth: Float,
)

/**
 * Everything outside the recipe that changes what a theme resolves to.
 *
 * All of it is the user's, which is the other half of the redesign: a theme used
 * to own its brightness, its contrast, its saturation, its material and its
 * texture outright, and the only dial over the top of them was a single accent
 * swatch. These are the same properties, handed over.
 */
data class ThemeOptions(
    /** Resolve against a dark ground. Comes from [ThemeMode] plus the system. */
    val dark: Boolean = true,
    val contrast: ContrastLevel = ContrastLevel.NORMAL,
    /** Scales every chroma in the palette. 1 is the theme as designed. */
    val colorIntensity: Float = 1f,
    /** Rotates the whole palette, accent and tinted greys together. */
    val hueShift: Float = 0f,
    /**
     * Pins the dark ground to true black.
     *
     * A property of the *screen* rather than of any theme, which is why it is here
     * and not a preset of its own: an OLED panel draws #000 by switching pixels
     * off, and that is worth having under every theme rather than under one.
     * Ignored when the palette resolves light, where it would mean nothing.
     */
    val pureBlack: Boolean = false,
    /**
     * Replaces the recipe's accent hue and chroma, as an ARGB long.
     *
     * Hue and chroma only — the lightness is still the ground's business. A picked
     * colour used to be dropped in as the primary verbatim, so a deep navy chosen
     * on a light theme became an accent nobody could see, and the greys around it
     * went on being tinted toward the accent it had replaced. Taking the direction
     * and leaving the brightness is what makes an arbitrary swatch produce a whole
     * palette rather than one wrong colour in an old one.
     */
    val accentOverrideArgb: Long? = null,
    /** Replaces the recipe's panel treatment. Null keeps the theme's own. */
    val surfaceStyle: SurfaceStyle? = null,
    /** Scales [ThemeMaterial.backgroundDepth]. */
    val depthScale: Float = 1f,
    /** Scales [ThemeMaterial.grain]. */
    val grainScale: Float = 1f,
)

/**
 * A theme resolved against a set of options: the actual colours, ready to draw.
 *
 * Nothing constructs one of these by hand any more — it is the output of
 * [ThemeRecipe.resolve] and nothing else, which is what lets its guarantees be
 * guarantees. The surface fields form a ramp, [backgroundArgb] behind
 * [surfaceArgb] behind [surfaceElevatedArgb] behind [surfaceHighestArgb], and the
 * three on-colours are each computed to clear a contrast ratio against the surface
 * they will be drawn on rather than chosen to look about right.
 *
 * Colours are ARGB longs rather than Compose `Color` so that `:core:model` stays a
 * pure-Kotlin module; `:core:designsystem` converts them.
 */
data class ThemeSpec(
    val id: ThemeId,
    /** Set when the recipe behind this was a [CustomTheme]. See [ThemeRecipe.customId]. */
    val customId: String? = null,
    /** What to write under a gallery card: the theme's own name, or the bundled one's. */
    val displayName: String = id.displayName,
    val family: ThemeFamily,
    val isDark: Boolean,
    val primaryArgb: Long,
    val secondaryArgb: Long,
    /**
     * Far end of the accent gradient.
     *
     * Accents are a pair, not a single colour: a cursor, a progress bar or a badge
     * drawn with a two-stop gradient reads as lit rather than filled, and a flat
     * accent is the main reason a palette looks cheap.
     */
    val accentEndArgb: Long,
    val backgroundArgb: Long,
    /** Base surface for panels and sheets. */
    val surfaceArgb: Long,
    /** Elevated surface for cards, dock and grid cells. */
    val surfaceElevatedArgb: Long,
    /** Highest surface, for dialogs and menus sitting over an elevated panel. */
    val surfaceHighestArgb: Long,
    val onBackgroundArgb: Long,
    val onSurfaceArgb: Long,
    /** Muted text: metadata labels, secondary rows. */
    val onSurfaceVariantArgb: Long,
    /** Colour of the selection ring on the focused grid cell. */
    val cursorArgb: Long,
    /** Additive glow drawn behind the cursor. */
    val glowArgb: Long,
    val outlineArgb: Long,
    val errorArgb: Long,
    val cornerRadiusDp: Int,
    val surfaceAlpha: Float,
    val blurRadiusDp: Int,
    val grain: Float,
    val defaultWallpaper: AnimatedWallpaper,
    val motion: MotionStyle,
    val surface: SurfaceTreatment,
    val backgroundDepth: Float,
) {
    /** Unique across bundled and custom themes alike. See [ThemeRecipe.key]. */
    val key: String get() = customId ?: id.name
}

/**
 * The four surface lightnesses, ground first.
 *
 * The ramp climbs on a dark ground and *descends* on a light one, which is the
 * opposite of what it used to do and the fix for the only thing genuinely wrong
 * with the light palettes. Elevating toward white sounds right — a white card on
 * an off-white page — but white is a wall: pin the top of the ramp at 1.0 and
 * there is nowhere for four surfaces to go, so they land 0.024 apart in lightness
 * where a dark ramp spreads them 0.050. Half the separation, and the result was
 * that you could not see where a grid slot ended and the page began.
 *
 * Descending from a near-white ground has all the room in the world, and it is
 * also what every light interface actually does — a container on a light page is
 * distinguished by being *dimmer* than the page, not brighter than it. The steps
 * are now the same size in both polarities.
 *
 * @param groundShift how far this theme lifts its ground off the extreme, already
 *   scaled by the contrast level's appetite for it
 */
private fun surfaceRamp(
    dark: Boolean,
    contrast: ContrastLevel,
    groundShift: Float,
    pureBlack: Boolean,
): List<Float> {
    // Away from the extreme means lighter on a dark ground and darker on a light
    // one: a theme that declares a soft ground means the same thing either way.
    val ground = if (dark) {
        (contrast.darkGround + groundShift).coerceIn(0f, MAX_DARK_GROUND)
    } else {
        (contrast.lightGround - groundShift * LIGHT_GROUND_SHIFT_SCALE)
            .coerceIn(MIN_LIGHT_GROUND, 1f)
    }
    val step = (if (dark) contrast.darkStep else contrast.lightStep) * if (dark) 1f else -1f
    val ramp = listOf(ground, ground + step, ground + 2 * step, ground + 3.1f * step)

    // Only the ground goes to black; the panels above it keep their own lightness,
    // so the ramp gets *deeper* rather than being flattened into it.
    return if (dark && pureBlack) listOf(0f) + ramp.drop(1) else ramp
}

/**
 * Strengthens a panel treatment for a light ground.
 *
 * The measurements in a [SurfaceTreatment] were all chosen against a dark field,
 * where a panel separates itself mostly by being lighter than what is behind it
 * and the edge is a finishing touch. None of that transfers. On a light page the
 * lightness step is doing far less work, a white specular highlight on a
 * near-white panel is invisible, and the border and the shadow are the whole of
 * what says "this is a card" — so both are given a floor, and the sheen that has
 * stopped meaning anything is turned down.
 *
 * [SurfaceStyle.FLAT] keeps its zero shadow. Having no depth is the entire claim
 * of that treatment, and it pays for it with a full-strength border.
 */
private fun SurfaceTreatment.forLightGround(): SurfaceTreatment = copy(
    borderWidthDp = if (borderWidthDp > 0f) maxOf(borderWidthDp, LIGHT_MIN_BORDER) else 0f,
    borderAlpha = if (borderAlpha > 0f) maxOf(borderAlpha, LIGHT_MIN_BORDER_ALPHA) else 0f,
    specularAlpha = specularAlpha * LIGHT_SPECULAR_SCALE,
    shadowElevationDp = if (style == SurfaceStyle.FLAT) {
        shadowElevationDp
    } else {
        maxOf(shadowElevationDp, LIGHT_MIN_SHADOW)
    },
)

/** A hairline below 1dp does not survive on a light field. */
private const val LIGHT_MIN_BORDER = 1f
private const val LIGHT_MIN_BORDER_ALPHA = 0.6f

/** White on near-white says nothing, so the lit edge mostly goes. */
private const val LIGHT_SPECULAR_SCALE = 0.3f

/** Enough shadow to read as a card without becoming a smudge. */
private const val LIGHT_MIN_SHADOW = 5

/**
 * The least extreme text lightness that still clears [targetRatio] on [surface].
 *
 * Binary search rather than pure white or black. Contrast rises monotonically as
 * the text moves away from its backdrop, so there is exactly one boundary to find,
 * and stopping at it matters: white body text on a dark panel is harsh to read for
 * any length of time and is well past what the standard asks for. If even the
 * extreme cannot clear the bar — which the ramp is built to prevent — this returns
 * the extreme, because the closest possible is still the right answer.
 */
private fun readableOn(
    surface: Oklch,
    dark: Boolean,
    hue: Float,
    chroma: Float,
    targetRatio: Float,
): Oklch {
    val backdrop = surface.toArgb()
    // `near` always fails the target and `far` always meets it, so the search can
    // only converge on the boundary between them.
    var near = surface.l
    var far = if (dark) 1f else 0f
    repeat(CONTRAST_SEARCH_STEPS) {
        val mid = (near + far) / 2f
        if (Oklch.contrastRatio(Oklch(mid, chroma, hue).toArgb(), backdrop) >= targetRatio) {
            far = mid
        } else {
            near = mid
        }
    }
    return Oklch(far, chroma, hue)
}

/** Lightness of the accent against a dark ground, before the contrast push. */
private const val DARK_ACCENT_LIGHTNESS = 0.775f

/** And against a light one, where it has to go darker rather than brighter. */
private const val LIGHT_ACCENT_LIGHTNESS = 0.52f

/** How far the gradient's far stop is lifted from the accent. */
private const val ACCENT_END_LIFT = 0.09f

/**
 * How far past the end of the ramp the outline sits, on a dark ground.
 *
 * Small, because a pale hairline on a dark field is conspicuous already. It is
 * measured from the *far* end of the ramp rather than from the base surface, so
 * the outline is guaranteed to read on every panel rather than only on the one it
 * was measured against.
 */
private const val DARK_OUTLINE_STEP = 0.02f

/**
 * And on a light ground, where it has to be much further.
 *
 * A grey line on white is the least conspicuous mark in the whole palette, and
 * light themes lean on it hardest — there is no glow and no lit edge doing the
 * work of separating a panel from its page.
 */
private const val LIGHT_OUTLINE_STEP = 0.1f

/** A theme's own ground lift counts for less on a light page, which has less room. */
private const val LIGHT_GROUND_SHIFT_SCALE = 0.45f

/** Past this a "dark" theme is a mid-grey one, whatever it declared. */
private const val MAX_DARK_GROUND = 0.55f

/** And past this a light one has stopped being light. */
private const val MIN_LIGHT_GROUND = 0.62f

private const val DARK_GLOW_ALPHA = 0.55f
private const val LIGHT_GLOW_ALPHA = 0.34f

private const val ERROR_HUE = 27f
private const val ERROR_CHROMA = 0.17f
private const val DARK_ERROR_LIGHTNESS = 0.72f
private const val LIGHT_ERROR_LIGHTNESS = 0.52f

/** Past this the greys stop reading as grey; the sliders are clamped to it. */
private const val MAX_TEXT_CHROMA = 0.014f

/** Below this a picked accent would be a grey, which is not what picking one means. */
private const val MIN_OVERRIDE_CHROMA = 0.045f

/** The ceiling on the colour-intensity dial. */
private const val MAX_INTENSITY = 2f

/**
 * How the grey tint grows with elevation on a dark ramp.
 *
 * Material's observation, and it holds for every theme: a panel that is nearer
 * carries a little more of the theme's colour, which separates a stack of surfaces
 * without any of them needing a hand-picked tone.
 */
private val DARK_TINT_TAPER = listOf(0.85f, 1f, 1.15f, 1.3f)

/**
 * And the same way on a light one, now that the light ramp descends.
 *
 * The ground is nearly a true white — a light launcher should read as white, not
 * as tinted paper — and each container below it carries more of the theme's
 * colour. That is a second axis of separation on top of the lightness step, which
 * is exactly where a light palette needs the help.
 */
private val LIGHT_TINT_TAPER = listOf(0.5f, 0.85f, 1.15f, 1.4f)

private const val CONTRAST_SEARCH_STEPS = 18

/**
 * How a panel composites over whatever is behind it.
 *
 * The style is the *character*; [SurfaceTreatment] carries the numbers. Keeping
 * them apart means a theme can say "glass, but with a heavier edge" without a new
 * enum entry for every combination — and means the user can be offered the four
 * characters without being offered eleven numbers.
 */
@Serializable
enum class SurfaceStyle(val label: String) {
    /** Opaque, hard-edged, no depth. */
    FLAT("Flat"),

    /** Opaque with a real drop shadow — a card lying on a page. */
    RAISED("Raised"),

    /** Slightly translucent, tinted upward by elevation. */
    TINTED("Tinted"),

    /** Translucent, blurred, with a lit top edge. */
    GLASS("Glass"),
}

/**
 * The measurements behind a [SurfaceStyle].
 *
 * Every value is a fraction or a dp rather than a colour, because all of them are
 * resolved against the theme's own palette at draw time — a border is the theme's
 * outline at [borderAlpha], not a colour of its own. That is what stops a treatment
 * from fighting the palette it is applied to.
 */
@Serializable
data class SurfaceTreatment(
    val style: SurfaceStyle,
    /** Border weight in dp; 0 draws none. */
    val borderWidthDp: Float,
    /** Border opacity against the theme's outline colour, 0..1. */
    val borderAlpha: Float,
    /**
     * A brighter hairline along the top edge, 0..1.
     *
     * The single cheapest thing that makes a translucent panel read as a lit sheet
     * rather than as reduced opacity, which is what glass looked like before this:
     * flat, grey and slightly see-through.
     */
    val specularAlpha: Float,
    /** Drop-shadow depth in dp; 0 draws none. */
    val shadowElevationDp: Int,
    /**
     * How strongly the accent tints each elevation step, 0..1.
     *
     * Distinct from the ramp's own tint, which colours the greys themselves: this
     * is applied at draw time over whatever is behind the panel, so it reaches the
     * translucent case where the ramp cannot.
     */
    val elevationTint: Float,
) {
    /** True when this treatment wants the backdrop blurred behind it. */
    val wantsBlur: Boolean get() = style == SurfaceStyle.GLASS

    companion object {
        /** Opaque, hard-edged, no depth at all. */
        val FLAT = SurfaceTreatment(
            style = SurfaceStyle.FLAT,
            borderWidthDp = 1f,
            borderAlpha = 0.9f,
            specularAlpha = 0f,
            shadowElevationDp = 0,
            elevationTint = 0f,
        )

        /** An opaque card with a shadow under it. */
        val RAISED = SurfaceTreatment(
            style = SurfaceStyle.RAISED,
            borderWidthDp = 0.5f,
            borderAlpha = 0.35f,
            specularAlpha = 0.05f,
            shadowElevationDp = 8,
            elevationTint = 0.02f,
        )

        /** A faint edge, a little accent tint, almost no shadow. */
        val TINTED = SurfaceTreatment(
            style = SurfaceStyle.TINTED,
            borderWidthDp = 1f,
            borderAlpha = 0.5f,
            specularAlpha = 0.07f,
            shadowElevationDp = 2,
            elevationTint = 0.06f,
        )

        /** Translucent and lit, for the themes with a blur budget. */
        val GLASS = SurfaceTreatment(
            style = SurfaceStyle.GLASS,
            borderWidthDp = 1f,
            borderAlpha = 0.45f,
            specularAlpha = 0.26f,
            shadowElevationDp = 0,
            elevationTint = 0.04f,
        )

        /** The preset for a style, for when the user picks one over the theme's. */
        fun forStyle(style: SurfaceStyle): SurfaceTreatment = when (style) {
            SurfaceStyle.FLAT -> FLAT
            SurfaceStyle.RAISED -> RAISED
            SurfaceStyle.TINTED -> TINTED
            SurfaceStyle.GLASS -> GLASS
        }
    }
}

/**
 * Motion personality. Multipliers are applied to the design system's base
 * durations, and each style selects a different easing curve.
 */
@Serializable
enum class MotionStyle(val label: String, val durationScale: Float) {
    /** Long, soft, overlapping transitions. */
    FLUID("Fluid", 1.25f),

    /** Quick but eased. */
    SMOOTH("Smooth", 1.0f),

    /** Short and sharp, minimal overshoot. */
    SNAPPY("Snappy", 0.75f),

    /** Stepped, slightly stiff. */
    MECHANICAL("Mechanical", 0.9f),
}

