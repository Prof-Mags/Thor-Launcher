package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The bundled themes.
 *
 * A theme is more than a colour swap: each carries its own accent pair, surface
 * ramp, corner radius, motion character, grain and paired wallpaper, which is
 * what makes the presets read as distinct systems rather than as recoloured
 * copies of one another.
 *
 * Fifteen of them, five to a [ThemeFamily], and the even split is the point. The
 * set had grown to twenty by accretion and was badly lopsided — sixteen darks
 * against four lights, several of which differed only in accent — so choosing a
 * theme meant scrolling a long row of near-identical dark cards to reach the
 * handful that actually changed anything. Five distinct answers per family is
 * shorter to read and covers more ground than twenty overlapping ones did.
 *
 * Retiring an id is safe: a settings file naming a theme that no longer exists
 * coerces back to the default rather than failing to parse — see
 * `SettingsSerializer` — and [ThemeSpec.of] never throws for an id it cannot
 * find.
 */
@Serializable
enum class ThemeId(val displayName: String) {
    // ---- Dark ----------------------------------------------------------
    // Material first, because it is what a fresh install opens on: a gallery
    // whose default sits six cards along asks the reader to hunt for where they
    // already are.
    MATERIAL_YOU("Material"),
    DARK("Midnight"),
    OBSIDIAN("Obsidian"),
    OLED_BLACK("OLED"),
    STEAM("Slate"),

    // ---- Colourful -----------------------------------------------------
    NEON("Neon"),
    CYBER("Cyber"),
    EMBER("Ember"),
    LAGOON("Lagoon"),
    ORCHID("Orchid"),

    // ---- Light ---------------------------------------------------------
    LIGHT("Daylight"),
    MERIDIAN("Meridian"),
    PAPER("Paper"),
    SWITCH("Cherry"),
    THREE_DS("Sherbet"),
}

/**
 * Which shelf of the gallery a theme belongs on.
 *
 * [COLOURFUL] is about chroma rather than brightness — every one of them is
 * dark-grounded, because a saturated accent needs somewhere dim to be saturated
 * against — so it is not a third point on the light/dark axis but a separate
 * question: does this theme lead with a colour, or with a neutral?
 */
@Serializable
enum class ThemeFamily(val label: String) {
    /** Leads with a colour: vivid accents over a ground tinted to match. */
    COLOURFUL("Colourful"),

    /** Leads with a neutral: restrained accents, low-chroma surfaces. */
    DARK("Dark"),

    /** Light-grounded, elevating toward white. */
    LIGHT("Light"),
}

/**
 * The colour and material description of a theme.
 *
 * Colours are ARGB longs rather than Compose `Color` so that `:core:model`
 * stays a pure-Kotlin module; `:core:designsystem` converts them.
 *
 * The surface fields form a deliberate ramp — [backgroundArgb] behind
 * [surfaceArgb] behind [surfaceElevatedArgb] behind [surfaceHighestArgb] — so a
 * card on a panel on the background stays legible at every level. Two steps was
 * not enough: the dock, grid cells and dialogs all landed on the same tone and
 * the depth collapsed.
 */
@Serializable
data class ThemeSpec(
    val id: ThemeId,
    val isDark: Boolean,
    /**
     * Which shelf of the gallery this sits on.
     *
     * Distinct from [isDark], which is a fact about the background and drives
     * contrast decisions. This is an editorial grouping and drives ordering: a
     * colourful theme is dark-grounded too, so [isDark] cannot separate the two.
     */
    val family: ThemeFamily,
    val primaryArgb: Long,
    val secondaryArgb: Long,
    /**
     * Far end of the accent gradient.
     *
     * Accents are a pair, not a single colour: a cursor, a progress bar or a
     * badge drawn with a two-stop gradient reads as lit rather than filled, and
     * a flat accent is the main reason a palette looks cheap.
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
    /**
     * Muted text: metadata labels, secondary rows.
     *
     * Explicit rather than [onSurfaceArgb] at a fixed alpha. On the translucent
     * themes that blanket alpha compounded with the surface's own transparency
     * and left secondary text almost invisible.
     */
    val onSurfaceVariantArgb: Long,
    /** Colour of the selection ring on the focused grid cell. */
    val cursorArgb: Long,
    /** Additive glow drawn behind the cursor. */
    val glowArgb: Long,
    val outlineArgb: Long,
    val errorArgb: Long,
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
    /** Wallpaper applied when this theme is selected from the gallery. */
    val defaultWallpaper: AnimatedWallpaper,
    /** Motion personality; drives easing and duration multipliers. */
    val motion: MotionStyle,
    val fontFamily: FontChoice,
    val soundPack: SoundPack,
    /**
     * How this theme's panels are actually drawn.
     *
     * Colour alone was never what separated these presets. A Switch panel is an
     * opaque card with a shadow under it, a Vision panel is a lit sheet of glass,
     * and a Retro panel is a flat rectangle with a hard edge — and with only a
     * radius and an alpha to describe them, all three came out as the same
     * rounded box in different colours. This is the part that makes them read as
     * different systems.
     */
    val surface: SurfaceTreatment = SurfaceTreatment.TINTED,
    /**
     * How far the background graduates toward the accent, 0..1. Zero is flat.
     *
     * A single number rather than a hand-written gradient per theme: the wash is
     * always the same shape — darker at the top, lifted toward [primaryArgb] at
     * the bottom — and only its strength differs, so twenty literal gradients
     * would be twenty chances to get one subtly wrong. Sits *under* the wallpaper
     * rather than instead of it, so a theme still has ground of its own when
     * every effect is switched off.
     *
     * Zero on the themes whose whole point is a flat field: a wash over true
     * black is banding, and over the CRT preset it is a gradient nobody's CRT had.
     */
    val backgroundDepth: Float = 0.07f,
) {
    companion object {
        /**
         * Every bundled theme, in menu order.
         *
         * Ordered by [ThemeFamily] and five to each, so the gallery reads as
         * three shelves rather than as one long row: the neutrals first, because
         * the default is one of them and the first card should be where the
         * reader already is, then the colourful ones, then the lights.
         */
        val ALL: List<ThemeSpec> = listOf(
            // ---- Dark ----------------------------------------------------
            ThemeSpec(
                id = ThemeId.MATERIAL_YOU, isDark = true, family = ThemeFamily.DARK,
                primaryArgb = 0xFFB6C6FF, secondaryArgb = 0xFFC6C8DE,
                accentEndArgb = 0xFFDCE1FF,
                backgroundArgb = 0xFF101218, surfaceArgb = 0xFF181A20,
                surfaceElevatedArgb = 0xFF22242B, surfaceHighestArgb = 0xFF2D2F37,
                onBackgroundArgb = 0xFFE5E4E9, onSurfaceArgb = 0xFFD2D1D7,
                onSurfaceVariantArgb = 0xFF9594A0,
                cursorArgb = 0xFFB6C6FF, glowArgb = 0x5CB6C6FF,
                outlineArgb = 0xFF474751, errorArgb = 0xFFFFB4AB,
                cornerRadiusDp = 24, surfaceAlpha = 0.94f, blurRadiusDp = 18,
                grain = 0.03f, defaultWallpaper = AnimatedWallpaper.MESH,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                // The strongest elevation tint in the set, which is the whole
                // idea this preset is named for. It is also what a fresh install
                // opens on, so it is the one that has to look considered with no
                // wallpaper chosen and nothing in the library yet.
                surface = SurfaceTreatment.TINTED.copy(elevationTint = 0.11f),
                backgroundDepth = 0.09f,
            ),
            ThemeSpec(
                id = ThemeId.DARK, isDark = true, family = ThemeFamily.DARK,
                primaryArgb = 0xFF5B93FF, secondaryArgb = 0xFF9D7BFF,
                accentEndArgb = 0xFF7FD4FF,
                backgroundArgb = 0xFF0B0E14, surfaceArgb = 0xFF12161F,
                surfaceElevatedArgb = 0xFF1A2029, surfaceHighestArgb = 0xFF232A35,
                onBackgroundArgb = 0xFFEDF1F8, onSurfaceArgb = 0xFFDCE3EE,
                onSurfaceVariantArgb = 0xFF98A3B5,
                cursorArgb = 0xFF6BA4FF, glowArgb = 0x705B93FF,
                outlineArgb = 0xFF2B3543, errorArgb = 0xFFFF6B6B,
                cornerRadiusDp = 20, surfaceAlpha = 0.88f, blurRadiusDp = 28,
                grain = 0.035f, defaultWallpaper = AnimatedWallpaper.MESH,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
            ),
            ThemeSpec(
                id = ThemeId.OBSIDIAN, isDark = true, family = ThemeFamily.DARK,
                primaryArgb = 0xFFC8CEDA, secondaryArgb = 0xFF8E97A8,
                accentEndArgb = 0xFFFFFFFF,
                backgroundArgb = 0xFF0C0D10, surfaceArgb = 0xFF14161A,
                surfaceElevatedArgb = 0xFF1D2025, surfaceHighestArgb = 0xFF272B32,
                onBackgroundArgb = 0xFFF2F4F8, onSurfaceArgb = 0xFFE0E4EB,
                onSurfaceVariantArgb = 0xFF9AA1AE,
                cursorArgb = 0xFFE8ECF3, glowArgb = 0x55FFFFFF,
                outlineArgb = 0xFF2C3037, errorArgb = 0xFFFF7A7A,
                cornerRadiusDp = 18, surfaceAlpha = 0.9f, blurRadiusDp = 24,
                grain = 0.05f, defaultWallpaper = AnimatedWallpaper.BOKEH,
                motion = MotionStyle.FLUID, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.MINIMAL,
            ),
            ThemeSpec(
                id = ThemeId.OLED_BLACK, isDark = true, family = ThemeFamily.DARK,
                primaryArgb = 0xFF25E0FF, secondaryArgb = 0xFF8A6BFF,
                accentEndArgb = 0xFF6BFFE0,
                backgroundArgb = 0xFF000000, surfaceArgb = 0xFF060708,
                surfaceElevatedArgb = 0xFF0E1012, surfaceHighestArgb = 0xFF16191C,
                onBackgroundArgb = 0xFFF4F6F8, onSurfaceArgb = 0xFFE2E6EA,
                onSurfaceVariantArgb = 0xFF8C949C,
                cursorArgb = 0xFF25E0FF, glowArgb = 0x9025E0FF,
                outlineArgb = 0xFF1C2024, errorArgb = 0xFFFF5C5C,
                cornerRadiusDp = 14, surfaceAlpha = 1.0f, blurRadiusDp = 0,
                // No grain on a true-black theme: noise over #000 is the one
                // place it reads as sensor dirt rather than as texture.
                grain = 0f, defaultWallpaper = AnimatedWallpaper.STARFIELD,
                motion = MotionStyle.SNAPPY, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.MINIMAL,
                // Flat and unlit, for the same reason it carries no grain: any
                // wash or sheen over #000 is banding on an OLED panel.
                surface = SurfaceTreatment.FLAT, backgroundDepth = 0f,
            ),
            ThemeSpec(
                id = ThemeId.STEAM, isDark = true, family = ThemeFamily.DARK,
                primaryArgb = 0xFF6FC3F7, secondaryArgb = 0xFF4E7BA8,
                accentEndArgb = 0xFF9BE0FF,
                backgroundArgb = 0xFF0E141C, surfaceArgb = 0xFF161F2B,
                surfaceElevatedArgb = 0xFF1F2C3C, surfaceHighestArgb = 0xFF2A3A4E,
                onBackgroundArgb = 0xFFDCE7F0, onSurfaceArgb = 0xFFC5D4E2,
                onSurfaceVariantArgb = 0xFF8497A9,
                cursorArgb = 0xFF6FC3F7, glowArgb = 0x7A6FC3F7,
                outlineArgb = 0xFF2D3F53, errorArgb = 0xFFE07A6B,
                cornerRadiusDp = 8, surfaceAlpha = 0.94f, blurRadiusDp = 16,
                grain = 0.045f, defaultWallpaper = AnimatedWallpaper.GRADIENT_DRIFT,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.MINIMAL,
            ),

            // ---- Colourful -----------------------------------------------
            // All five are dark-grounded and carry a heavy `backgroundDepth`:
            // the ground is washed toward the theme's own accent, which is what
            // makes them read as coloured rather than as a neutral dark with a
            // coloured cursor on it.
            ThemeSpec(
                id = ThemeId.NEON, isDark = true, family = ThemeFamily.COLOURFUL,
                primaryArgb = 0xFF4BFF37, secondaryArgb = 0xFFFF31E4,
                accentEndArgb = 0xFFD8FF3C,
                backgroundArgb = 0xFF04060A, surfaceArgb = 0xFF0A0F16,
                surfaceElevatedArgb = 0xFF111823, surfaceHighestArgb = 0xFF1A2331,
                onBackgroundArgb = 0xFFEEFFEC, onSurfaceArgb = 0xFFD4EDD1,
                onSurfaceVariantArgb = 0xFF8AA588,
                cursorArgb = 0xFF4BFF37, glowArgb = 0xB04BFF37,
                outlineArgb = 0xFF1F2E1E, errorArgb = 0xFFFF3560,
                cornerRadiusDp = 10, surfaceAlpha = 0.9f, blurRadiusDp = 22,
                grain = 0.07f, defaultWallpaper = AnimatedWallpaper.PARTICLES,
                motion = MotionStyle.SNAPPY, fontFamily = FontChoice.MONO,
                soundPack = SoundPack.ARCADE,
                surface = SurfaceTreatment.FLAT.copy(
                    borderWidthDp = 1.5f,
                    specularAlpha = 0.08f,
                ),
                backgroundDepth = 0.18f,
            ),
            ThemeSpec(
                id = ThemeId.CYBER, isDark = true, family = ThemeFamily.COLOURFUL,
                primaryArgb = 0xFFFF3D8B, secondaryArgb = 0xFF1BE7FF,
                accentEndArgb = 0xFFFFA23D,
                backgroundArgb = 0xFF08030F, surfaceArgb = 0xFF120823,
                surfaceElevatedArgb = 0xFF1B0F33, surfaceHighestArgb = 0xFF261648,
                onBackgroundArgb = 0xFFF8ECFF, onSurfaceArgb = 0xFFE2CFF6,
                onSurfaceVariantArgb = 0xFFA189BE,
                cursorArgb = 0xFF1BE7FF, glowArgb = 0xA01BE7FF,
                outlineArgb = 0xFF3A1F60, errorArgb = 0xFFFF4470,
                cornerRadiusDp = 4, surfaceAlpha = 0.88f, blurRadiusDp = 20,
                grain = 0.075f, defaultWallpaper = AnimatedWallpaper.WAVES,
                motion = MotionStyle.SNAPPY, fontFamily = FontChoice.MONO,
                soundPack = SoundPack.ARCADE,
                // Hard-edged rather than soft: a 4dp corner with a full-strength
                // outline and no shadow is what makes this read as a terminal
                // instead of as a rounded card that happens to be magenta.
                surface = SurfaceTreatment.FLAT.copy(
                    borderWidthDp = 1.5f,
                    specularAlpha = 0.1f,
                ),
                backgroundDepth = 0.2f,
            ),
            ThemeSpec(
                id = ThemeId.EMBER, isDark = true, family = ThemeFamily.COLOURFUL,
                primaryArgb = 0xFFFF8A3D, secondaryArgb = 0xFFE0483C,
                accentEndArgb = 0xFFFFC46B,
                backgroundArgb = 0xFF120A07, surfaceArgb = 0xFF1B0F0A,
                surfaceElevatedArgb = 0xFF261710, surfaceHighestArgb = 0xFF332018,
                onBackgroundArgb = 0xFFFFF2E8, onSurfaceArgb = 0xFFF0DDCE,
                onSurfaceVariantArgb = 0xFFB09384,
                cursorArgb = 0xFFFF8A3D, glowArgb = 0x8AFF8A3D,
                outlineArgb = 0xFF3D281D, errorArgb = 0xFFFF5044,
                cornerRadiusDp = 16, surfaceAlpha = 0.9f, blurRadiusDp = 22,
                grain = 0.055f, defaultWallpaper = AnimatedWallpaper.AURORA,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                backgroundDepth = 0.17f,
            ),
            ThemeSpec(
                id = ThemeId.LAGOON, isDark = true, family = ThemeFamily.COLOURFUL,
                primaryArgb = 0xFF2FD9C4, secondaryArgb = 0xFF3D8BFF,
                accentEndArgb = 0xFF8BF5E4,
                backgroundArgb = 0xFF05110F, surfaceArgb = 0xFF0A1B19,
                surfaceElevatedArgb = 0xFF102724, surfaceHighestArgb = 0xFF183531,
                onBackgroundArgb = 0xFFE8FBF8, onSurfaceArgb = 0xFFCFE9E5,
                onSurfaceVariantArgb = 0xFF83A5A0,
                cursorArgb = 0xFF2FD9C4, glowArgb = 0x7A2FD9C4,
                outlineArgb = 0xFF1D3E39, errorArgb = 0xFFFF6E6E,
                cornerRadiusDp = 20, surfaceAlpha = 0.9f, blurRadiusDp = 26,
                grain = 0.04f, defaultWallpaper = AnimatedWallpaper.WAVES,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                backgroundDepth = 0.16f,
            ),
            ThemeSpec(
                id = ThemeId.ORCHID, isDark = true, family = ThemeFamily.COLOURFUL,
                primaryArgb = 0xFFB57BFF, secondaryArgb = 0xFFFF7BD0,
                accentEndArgb = 0xFFE0B0FF,
                backgroundArgb = 0xFF0C0814, surfaceArgb = 0xFF150F22,
                surfaceElevatedArgb = 0xFF1F1730, surfaceHighestArgb = 0xFF2B2141,
                onBackgroundArgb = 0xFFF6F0FF, onSurfaceArgb = 0xFFE3D9F2,
                onSurfaceVariantArgb = 0xFF9F92B8,
                cursorArgb = 0xFFC79BFF, glowArgb = 0x7AB57BFF,
                outlineArgb = 0xFF33284A, errorArgb = 0xFFFF6B8A,
                cornerRadiusDp = 22, surfaceAlpha = 0.9f, blurRadiusDp = 30,
                grain = 0.04f, defaultWallpaper = AnimatedWallpaper.MESH,
                motion = MotionStyle.FLUID, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                backgroundDepth = 0.16f,
            ),

            // ---- Light ---------------------------------------------------
            // Light themes elevate by getting whiter — a white card over an
            // off-white page — so the ramp ascends exactly as a dark one does,
            // and they lean on a shadow for depth because there is nowhere
            // brighter to go above white.
            ThemeSpec(
                id = ThemeId.LIGHT, isDark = false, family = ThemeFamily.LIGHT,
                primaryArgb = 0xFF2563EB, secondaryArgb = 0xFF7C3AED,
                accentEndArgb = 0xFF4F9BFF,
                backgroundArgb = 0xFFEEF1F7, surfaceArgb = 0xFFF6F8FC,
                surfaceElevatedArgb = 0xFFFBFCFE, surfaceHighestArgb = 0xFFFFFFFF,
                onBackgroundArgb = 0xFF0F1420, onSurfaceArgb = 0xFF1E2634,
                onSurfaceVariantArgb = 0xFF5F6B7D,
                cursorArgb = 0xFF2563EB, glowArgb = 0x452563EB,
                outlineArgb = 0xFFD3DAE5, errorArgb = 0xFFD32F2F,
                cornerRadiusDp = 20, surfaceAlpha = 0.94f, blurRadiusDp = 20,
                grain = 0.02f, defaultWallpaper = AnimatedWallpaper.MESH,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                surface = SurfaceTreatment.RAISED, backgroundDepth = 0.05f,
            ),
            ThemeSpec(
                // The cool light the set was missing: every other one is either
                // blue-neutral or warm, so a green-grounded page is the only
                // light here that changes the temperature rather than the accent.
                id = ThemeId.MERIDIAN, isDark = false, family = ThemeFamily.LIGHT,
                primaryArgb = 0xFF0F8A6A, secondaryArgb = 0xFF3E7CA8,
                accentEndArgb = 0xFF4FC59B,
                backgroundArgb = 0xFFE9F0EC, surfaceArgb = 0xFFF2F7F4,
                surfaceElevatedArgb = 0xFFF9FCFA, surfaceHighestArgb = 0xFFFFFFFF,
                onBackgroundArgb = 0xFF16211C, onSurfaceArgb = 0xFF26332C,
                onSurfaceVariantArgb = 0xFF5E6F66,
                cursorArgb = 0xFF0F8A6A, glowArgb = 0x400F8A6A,
                outlineArgb = 0xFFCEDCD5, errorArgb = 0xFFC0392B,
                cornerRadiusDp = 18, surfaceAlpha = 0.95f, blurRadiusDp = 18,
                grain = 0.02f, defaultWallpaper = AnimatedWallpaper.WAVES,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
                surface = SurfaceTreatment.RAISED, backgroundDepth = 0.06f,
            ),
            ThemeSpec(
                id = ThemeId.PAPER, isDark = false, family = ThemeFamily.LIGHT,
                primaryArgb = 0xFFB4643C, secondaryArgb = 0xFF7A8C5A,
                accentEndArgb = 0xFFD99A6C,
                backgroundArgb = 0xFFEFE9DA, surfaceArgb = 0xFFF6F1E7,
                surfaceElevatedArgb = 0xFFFBF7EF, surfaceHighestArgb = 0xFFFFFCF6,
                onBackgroundArgb = 0xFF2A2419, onSurfaceArgb = 0xFF3B3427,
                onSurfaceVariantArgb = 0xFF7A7060,
                cursorArgb = 0xFFB4643C, glowArgb = 0x40B4643C,
                outlineArgb = 0xFFDED5C4, errorArgb = 0xFFC0442F,
                cornerRadiusDp = 14, surfaceAlpha = 0.96f, blurRadiusDp = 12,
                // Visible grain: this theme is meant to read as paper stock.
                grain = 0.08f, defaultWallpaper = AnimatedWallpaper.NONE,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SERIF,
                soundPack = SoundPack.MINIMAL,
                // A softer, shorter shadow than the other lights: paper stock
                // sitting on paper stock, not a card floating over a page.
                surface = SurfaceTreatment.RAISED.copy(
                    shadowElevationDp = 4,
                    borderAlpha = 0.5f,
                ),
                backgroundDepth = 0.04f,
            ),
            ThemeSpec(
                id = ThemeId.SWITCH, isDark = false, family = ThemeFamily.LIGHT,
                primaryArgb = 0xFFE8323C, secondaryArgb = 0xFF00B8DE,
                accentEndArgb = 0xFFFF6B72,
                backgroundArgb = 0xFFE8EAED, surfaceArgb = 0xFFF3F5F7,
                surfaceElevatedArgb = 0xFFFAFBFC, surfaceHighestArgb = 0xFFFFFFFF,
                onBackgroundArgb = 0xFF23262B, onSurfaceArgb = 0xFF33373D,
                onSurfaceVariantArgb = 0xFF6E747C,
                cursorArgb = 0xFF00B8DE, glowArgb = 0x5000B8DE,
                outlineArgb = 0xFFCFD4DA, errorArgb = 0xFFE8323C,
                cornerRadiusDp = 10, surfaceAlpha = 1.0f, blurRadiusDp = 0,
                grain = 0f, defaultWallpaper = AnimatedWallpaper.NONE,
                motion = MotionStyle.SNAPPY, fontFamily = FontChoice.ROUNDED,
                soundPack = SoundPack.CONSOLE,
                // Crisp opaque cards on a flat grey field, which is the console
                // this is named after almost exactly.
                surface = SurfaceTreatment.RAISED.copy(shadowElevationDp = 6),
                backgroundDepth = 0f,
            ),
            ThemeSpec(
                id = ThemeId.THREE_DS, isDark = false, family = ThemeFamily.LIGHT,
                primaryArgb = 0xFFF25C7A, secondaryArgb = 0xFF5FC5E8,
                accentEndArgb = 0xFFFFB38A,
                backgroundArgb = 0xFFEDF2F7, surfaceArgb = 0xFFF5F8FB,
                surfaceElevatedArgb = 0xFFFBFCFE, surfaceHighestArgb = 0xFFFFFFFF,
                onBackgroundArgb = 0xFF2E353C, onSurfaceArgb = 0xFF41494F,
                onSurfaceVariantArgb = 0xFF77828C,
                cursorArgb = 0xFFFFAF2B, glowArgb = 0x66FFAF2B,
                outlineArgb = 0xFFCBD6E0, errorArgb = 0xFFE8455F,
                cornerRadiusDp = 14, surfaceAlpha = 1.0f, blurRadiusDp = 0,
                grain = 0f, defaultWallpaper = AnimatedWallpaper.BOKEH,
                motion = MotionStyle.MECHANICAL, fontFamily = FontChoice.ROUNDED,
                soundPack = SoundPack.CONSOLE,
                // Soft, generous shadows under rounded plastic tiles.
                surface = SurfaceTreatment.RAISED.copy(
                    shadowElevationDp = 10,
                    borderAlpha = 0.25f,
                ),
                backgroundDepth = 0.06f,
            ),
        )

        val BY_ID: Map<ThemeId, ThemeSpec> = ALL.associateBy(ThemeSpec::id)

        /**
         * The spec for an id, falling back to the default theme.
         *
         * Never throws: an id can outlive its spec if a theme is retired while a
         * user has it selected, and a launcher that cannot build a palette cannot
         * draw anything at all.
         *
         * Falls back to [DEFAULT] rather than to any particular preset, so the
         * one place that decides what "no theme" looks like is the same one a
         * fresh install uses.
         */
        fun of(id: ThemeId): ThemeSpec = BY_ID[id] ?: BY_ID.getValue(DEFAULT)

        /** What a fresh install opens on, and the fallback for a retired id. */
        val DEFAULT: ThemeId = ThemeId.MATERIAL_YOU

        /** The themes on one shelf of the gallery, in menu order. */
        fun family(family: ThemeFamily): List<ThemeSpec> = ALL.filter { it.family == family }
    }
}

/**
 * How a panel composites over whatever is behind it.
 *
 * The style is the *character*; [SurfaceTreatment] carries the numbers. Keeping
 * them apart means a theme can say "glass, but with a heavier edge" without a new
 * enum entry for every combination.
 */
@Serializable
enum class SurfaceStyle(val label: String) {
    /** Opaque, hard-edged, no depth. The flat presets: Retro, Minimal, OLED. */
    FLAT("Flat"),

    /** Opaque with a real drop shadow — a card lying on a page. Switch, 3DS. */
    RAISED("Raised"),

    /** Slightly translucent, tinted upward by elevation. Material, Steam. */
    TINTED("Tinted"),

    /** Translucent, blurred, with a lit top edge. Glass, Vision. */
    GLASS("Glass"),
}

/**
 * The measurements behind a [SurfaceStyle].
 *
 * Every value is a fraction or a dp rather than a colour, because all of them are
 * resolved against the theme's own palette at draw time — a border is the theme's
 * outline at [borderAlpha], not a colour of its own. That is what stops a
 * treatment from fighting the palette it is applied to.
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
     * The single cheapest thing that makes a translucent panel read as a lit
     * sheet rather than as reduced opacity, which is what glass looked like
     * before this: flat, grey and slightly see-through.
     */
    val specularAlpha: Float,
    /** Drop-shadow depth in dp; 0 draws none. */
    val shadowElevationDp: Int,
    /**
     * How strongly the accent tints each elevation step, 0..1.
     *
     * Material's idea, and it is the reason a stack of panels stays legible
     * without every level needing its own hand-picked colour: each step up
     * carries a little more of the primary.
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

        /** The default: a faint edge, a little accent tint, almost no shadow. */
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
    /** The default: quick but eased. */
    SMOOTH("Smooth", 1.0f),
    /** Short and sharp, minimal overshoot. */
    SNAPPY("Snappy", 0.75f),
    /** Stepped, slightly stiff — suits the retro presets. */
    MECHANICAL("Mechanical", 0.9f),
}

@Serializable
enum class FontChoice(val label: String) {
    SYSTEM("System"),
    ROUNDED("Rounded"),
    MONO("Monospace"),
    PIXEL("Pixel"),
    SERIF("Serif"),
}

@Serializable
enum class SoundPack(val label: String) {
    NONE("Silent"),
    MINIMAL("Minimal"),
    SOFT("Soft"),
    CONSOLE("Console"),
    ARCADE("Arcade"),
}
