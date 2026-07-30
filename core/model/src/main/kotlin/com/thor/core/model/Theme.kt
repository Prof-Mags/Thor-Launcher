package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The bundled themes.
 *
 * A theme is more than a colour swap: each carries its own accent pair, surface
 * ramp, corner radius, motion character, grain and paired wallpaper, which is
 * what makes the presets read as distinct systems rather than as recoloured
 * copies of one another.
 */
@Serializable
enum class ThemeId(val displayName: String) {
    DARK("Midnight"),
    LIGHT("Daylight"),
    OLED_BLACK("OLED"),
    GLASS("Glass"),
    MATERIAL_YOU("Material"),
    CYBER("Cyber"),
    RETRO("Retro"),
    NEON("Neon"),
    MINIMAL("Graphite"),
    PLAYSTATION("Cobalt"),
    SWITCH("Cherry"),
    THREE_DS("Sherbet"),
    STEAM("Slate"),
    XBOX("Verdant"),
    VISION("Aether"),

    // Added to fill real gaps in the set: there was no warm dark, no violet, no
    // teal, no warm light and no opaque premium neutral.
    EMBER("Ember"),
    ORCHID("Orchid"),
    LAGOON("Lagoon"),
    PAPER("Paper"),
    OBSIDIAN("Obsidian"),
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
) {
    companion object {
        /** Every bundled theme, in menu order. */
        val ALL: List<ThemeSpec> = listOf(
            // ---- Neutral darks -------------------------------------------
            ThemeSpec(
                id = ThemeId.DARK, isDark = true,
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
                id = ThemeId.OBSIDIAN, isDark = true,
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
                id = ThemeId.OLED_BLACK, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.MINIMAL, isDark = true,
                primaryArgb = 0xFFFFFFFF, secondaryArgb = 0xFFA8A8A8,
                accentEndArgb = 0xFFD6D6D6,
                backgroundArgb = 0xFF101010, surfaceArgb = 0xFF171717,
                surfaceElevatedArgb = 0xFF202020, surfaceHighestArgb = 0xFF2A2A2A,
                onBackgroundArgb = 0xFFF4F4F4, onSurfaceArgb = 0xFFDEDEDE,
                onSurfaceVariantArgb = 0xFF949494,
                cursorArgb = 0xFFFFFFFF, glowArgb = 0x3DFFFFFF,
                outlineArgb = 0xFF2E2E2E, errorArgb = 0xFFE88080,
                cornerRadiusDp = 8, surfaceAlpha = 1.0f, blurRadiusDp = 0,
                grain = 0.04f, defaultWallpaper = AnimatedWallpaper.NONE,
                motion = MotionStyle.SMOOTH, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.NONE,
            ),
            ThemeSpec(
                id = ThemeId.STEAM, isDark = true,
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

            // ---- Translucent ---------------------------------------------
            ThemeSpec(
                id = ThemeId.GLASS, isDark = true,
                primaryArgb = 0xFFA8E9FF, secondaryArgb = 0xFFCDB0FF,
                accentEndArgb = 0xFFFFFFFF,
                backgroundArgb = 0xFF080D16, surfaceArgb = 0x38FFFFFF,
                surfaceElevatedArgb = 0x4DFFFFFF, surfaceHighestArgb = 0x63FFFFFF,
                onBackgroundArgb = 0xFFF6FAFF, onSurfaceArgb = 0xFFF0F6FF,
                // Bright, because this sits on a translucent surface over
                // arbitrary artwork; a dim variant vanishes there.
                onSurfaceVariantArgb = 0xFFC3D2E4,
                cursorArgb = 0xFFFFFFFF, glowArgb = 0x70FFFFFF,
                outlineArgb = 0x4DFFFFFF, errorArgb = 0xFFFF8A8A,
                cornerRadiusDp = 26, surfaceAlpha = 0.44f, blurRadiusDp = 48,
                grain = 0.03f, defaultWallpaper = AnimatedWallpaper.AURORA,
                motion = MotionStyle.FLUID, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
            ),
            ThemeSpec(
                id = ThemeId.VISION, isDark = true,
                primaryArgb = 0xFFFFFFFF, secondaryArgb = 0xFFC6D2E0,
                accentEndArgb = 0xFFE8F2FF,
                backgroundArgb = 0xFF04060A, surfaceArgb = 0x30FFFFFF,
                surfaceElevatedArgb = 0x45FFFFFF, surfaceHighestArgb = 0x5CFFFFFF,
                onBackgroundArgb = 0xFFFFFFFF, onSurfaceArgb = 0xFFF4F8FC,
                onSurfaceVariantArgb = 0xFFCAD6E4,
                cursorArgb = 0xFFFFFFFF, glowArgb = 0x66FFFFFF,
                outlineArgb = 0x3DFFFFFF, errorArgb = 0xFFFF8080,
                cornerRadiusDp = 32, surfaceAlpha = 0.36f, blurRadiusDp = 60,
                grain = 0.025f, defaultWallpaper = AnimatedWallpaper.BOKEH,
                motion = MotionStyle.FLUID, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.SOFT,
            ),
            ThemeSpec(
                id = ThemeId.MATERIAL_YOU, isDark = true,
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
            ),

            // ---- Saturated darks -----------------------------------------
            ThemeSpec(
                id = ThemeId.PLAYSTATION, isDark = true,
                primaryArgb = 0xFF2E86FF, secondaryArgb = 0xFF0BC4F0,
                accentEndArgb = 0xFF7BD0FF,
                backgroundArgb = 0xFF070C16, surfaceArgb = 0xFF0D1626,
                surfaceElevatedArgb = 0xFF142138, surfaceHighestArgb = 0xFF1D2E4B,
                onBackgroundArgb = 0xFFF2F7FF, onSurfaceArgb = 0xFFDCE8F8,
                onSurfaceVariantArgb = 0xFF8DA2BE,
                cursorArgb = 0xFF7BD0FF, glowArgb = 0x8A2E86FF,
                outlineArgb = 0xFF1F3557, errorArgb = 0xFFFF6058,
                cornerRadiusDp = 16, surfaceAlpha = 0.9f, blurRadiusDp = 32,
                grain = 0.04f, defaultWallpaper = AnimatedWallpaper.AURORA,
                motion = MotionStyle.FLUID, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.CONSOLE,
            ),
            ThemeSpec(
                id = ThemeId.XBOX, isDark = true,
                primaryArgb = 0xFF6EDB2E, secondaryArgb = 0xFF107C10,
                accentEndArgb = 0xFFB6F53C,
                backgroundArgb = 0xFF080B08, surfaceArgb = 0xFF0F140F,
                surfaceElevatedArgb = 0xFF171E17, surfaceHighestArgb = 0xFF212A21,
                onBackgroundArgb = 0xFFF0F6F0, onSurfaceArgb = 0xFFD8E4D8,
                onSurfaceVariantArgb = 0xFF8C9C8C,
                cursorArgb = 0xFF9BF00B, glowArgb = 0x8A6EDB2E,
                outlineArgb = 0xFF253025, errorArgb = 0xFFFF6060,
                cornerRadiusDp = 12, surfaceAlpha = 0.92f, blurRadiusDp = 18,
                grain = 0.045f, defaultWallpaper = AnimatedWallpaper.WAVES,
                motion = MotionStyle.SNAPPY, fontFamily = FontChoice.SYSTEM,
                soundPack = SoundPack.CONSOLE,
            ),
            ThemeSpec(
                id = ThemeId.CYBER, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.NEON, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.EMBER, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.ORCHID, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.LAGOON, isDark = true,
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
            ),
            ThemeSpec(
                id = ThemeId.RETRO, isDark = true,
                primaryArgb = 0xFFF0B942, secondaryArgb = 0xFF8CB854,
                accentEndArgb = 0xFFFFD980,
                backgroundArgb = 0xFF12100B, surfaceArgb = 0xFF1C1810,
                surfaceElevatedArgb = 0xFF262016, surfaceHighestArgb = 0xFF322A1D,
                onBackgroundArgb = 0xFFF4E8CE, onSurfaceArgb = 0xFFDECDA9,
                onSurfaceVariantArgb = 0xFFA3937A,
                cursorArgb = 0xFFF0B942, glowArgb = 0x8AF0B942,
                outlineArgb = 0xFF463C29, errorArgb = 0xFFD85C44,
                cornerRadiusDp = 2, surfaceAlpha = 1.0f, blurRadiusDp = 0,
                // The heaviest grain in the set, on purpose: this is the CRT one.
                grain = 0.11f, defaultWallpaper = AnimatedWallpaper.GRADIENT_DRIFT,
                motion = MotionStyle.MECHANICAL, fontFamily = FontChoice.PIXEL,
                soundPack = SoundPack.ARCADE,
            ),

            // ---- Lights --------------------------------------------------
            ThemeSpec(
                id = ThemeId.LIGHT, isDark = false,
                primaryArgb = 0xFF2563EB, secondaryArgb = 0xFF7C3AED,
                accentEndArgb = 0xFF4F9BFF,
                // Light themes elevate by getting whiter — a white card over an
                // off-white page — so the ramp ascends exactly as a dark one does.
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
            ),
            ThemeSpec(
                id = ThemeId.PAPER, isDark = false,
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
            ),
            ThemeSpec(
                id = ThemeId.SWITCH, isDark = false,
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
            ),
            ThemeSpec(
                id = ThemeId.THREE_DS, isDark = false,
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
            ),
        )

        val BY_ID: Map<ThemeId, ThemeSpec> = ALL.associateBy(ThemeSpec::id)

        /**
         * The spec for an id, falling back to the default theme.
         *
         * Never throws: an id can outlive its spec if a theme is retired while a
         * user has it selected, and a launcher that cannot build a palette cannot
         * draw anything at all.
         */
        fun of(id: ThemeId): ThemeSpec = BY_ID[id] ?: BY_ID.getValue(ThemeId.DARK)
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
