package com.thor.feature.settings

import com.thor.core.model.LauncherFeatures

/**
 * A single settings page.
 *
 * Settings is two levels: a category holds a short list of pages, and a page
 * holds the controls. Putting every control for a category on one surface meant
 * the Appearance pane alone ran to thirty-odd rows, which is unscannable and —
 * more practically — a very long way to travel with a D-pad. A page is small
 * enough to fit a screen, so opening one shows all of it at once.
 */
enum class SettingsPage(
    val category: SettingsCategory,
    val title: String,
    val summary: String,
) {
    // ---- Appearance --------------------------------------------------------
    THEME(
        SettingsCategory.APPEARANCE, "Theme",
        "Colour scheme and accent",
    ),
    WALLPAPER(
        SettingsCategory.APPEARANCE, "Wallpaper",
        "Background image and animated effect",
    ),
    GRID(
        SettingsCategory.APPEARANCE, "Grid",
        "Size, spacing, icon shape and labels",
    ),
    DOCK(
        SettingsCategory.APPEARANCE, "Dock",
        "Size, transparency and behaviour",
    ),
    CURSOR(
        SettingsCategory.APPEARANCE, "Cursor",
        "Selection highlight style and glow",
    ),
    INTERFACE(
        SettingsCategory.APPEARANCE, "Interface",
        "Text size, motion, clock and folders",
    ),

    // ---- Library -----------------------------------------------------------
    PLATFORMS(
        SettingsCategory.LIBRARY, "Platforms",
        "Consoles, their ROM folders and emulators",
    ),
    ROM_FOLDERS(
        SettingsCategory.LIBRARY, "Extra ROM folders",
        "Locations not tied to one platform",
    ),
    SCANNING(
        SettingsCategory.LIBRARY, "Scanning",
        "How games and apps are found",
    ),
    METADATA(
        SettingsCategory.LIBRARY, "Metadata & accounts",
        "Artwork providers and their credentials",
    ),
    SORTING(
        SettingsCategory.LIBRARY, "Sorting",
        "Default library order",
    ),

    // ---- Controls ----------------------------------------------------------
    NAVIGATION(
        SettingsCategory.CONTROLS, "Navigation",
        "Cursor movement and stick behaviour",
    ),
    FEEDBACK(
        SettingsCategory.CONTROLS, "Feedback",
        "Haptics and interface sound",
    ),

    // ---- Display -----------------------------------------------------------
    DUAL_SCREEN(
        SettingsCategory.DISPLAY, "Dual screen",
        "How the two panels are used",
    ),
    PERFORMANCE(
        SettingsCategory.DISPLAY, "Performance",
        "Animation and visual effects",
    ),

    // ---- System ------------------------------------------------------------
    ACCESSIBILITY(
        SettingsCategory.SYSTEM, "Accessibility",
        "Contrast, motion, text and colour vision",
    ),
    DIAGNOSTICS(
        SettingsCategory.SYSTEM, "Diagnostics",
        "Logging and resetting",
    ),
    ;

    companion object {
        /**
         * Pages belonging to [category], in declaration order.
         *
         * Pages for a hidden feature are left out entirely rather than shown
         * disabled: a settings page whose controls reach nothing on screen is
         * worse than a missing one, because the user changes a value, sees no
         * effect, and has no way to tell a dead page from a broken setting. The
         * page itself is kept — see [LauncherFeatures] — so restoring the feature
         * restores its configuration with it.
         */
        fun forCategory(category: SettingsCategory): List<SettingsPage> =
            entries.filter { it.category == category && it.isAvailable }

        private val SettingsPage.isAvailable: Boolean
            get() = when (this) {
                DOCK -> LauncherFeatures.DOCK_ENABLED
                else -> true
            }
    }
}
