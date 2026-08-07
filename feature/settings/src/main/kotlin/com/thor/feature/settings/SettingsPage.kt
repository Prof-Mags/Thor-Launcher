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
 *
 * **Declaration order is display order** within a category, so the order here is
 * the order of the list the user reads. Pages are grouped by the question they
 * answer rather than by which part of the code owns them: `METADATA` sits with
 * icon packs because both are places artwork comes from, not with scanning
 * because both happen to be run by a sync manager.
 */
enum class SettingsPage(
    val category: SettingsCategory,
    val title: String,
    val summary: String,
    /**
     * The heading this page sits under, or null for a category short enough not
     * to need one.
     *
     * Headings rather than more categories, and that is the whole of this
     * reorganisation. Personalization and Games & artwork had grown to eight
     * pages each — long enough that finding one meant reading all of them — and
     * the obvious fix, splitting them, is the one this file has already tried
     * twice and folded back twice: see [SettingsCategory], where Home screen and
     * Artwork are both kept as invisible ids from those attempts. A rail entry
     * holding three pages is a stop on the way to somewhere else, and the rail is
     * walked far more often than any one category.
     *
     * A heading costs nothing to walk past. It is drawn, not focused, so the
     * cursor still steps page to page and the row count is still the page count —
     * which is why this change needed no arithmetic anywhere.
     *
     * Null on every category with four pages or fewer. Three headings over five
     * pages is filing for its own sake.
     */
    val group: String? = null,
) {
    // ---- Personalization ---------------------------------------------------
    THEME(
        SettingsCategory.APPEARANCE, "Theme & colour",
        "The gallery, light or dark, accent, contrast and intensity",
        group = "Theme",
    ),

    /**
     * What panels are *made of*, as opposed to what colour they are.
     *
     * Its own page rather than a section of the theme one, because it is the half
     * of a theme nobody could previously reach: the material, the corners, the
     * depth of the ground and the texture over it were all declared by whichever
     * palette was selected and overridable nowhere. Splitting them says the
     * quiet part out loud — a theme is a colour *and* a construction, and both are
     * yours.
     */
    SURFACES(
        SettingsCategory.APPEARANCE, "Surfaces",
        "Panel material, corners, depth and texture",
        group = "Theme",
    ),

    /**
     * Building a theme, as opposed to choosing one and adjusting it.
     *
     * Third rather than first: the overwhelming majority of visits to
     * Personalization are somebody picking from the gallery, and a rail whose
     * opening row is an authoring tool tells them the easy thing is somewhere
     * further down. It sits directly under the two pages whose values it is the
     * source of, which is where somebody who has run out of adjustment will look.
     */
    THEME_EDITOR(
        SettingsCategory.APPEARANCE, "Theme editor",
        "Build a theme of your own, and share it",
        group = "Theme",
    ),

    /*
     * The home screen's own three, together.
     *
     * They were scattered through Personalization between the theme pages and the
     * wallpaper — which is how "make the icons bigger" became a hunt. What the
     * grid holds, what the cursor on it looks like and what sits along its bottom
     * edge are one question asked three ways.
     */
    // Named for what it holds rather than for where it is: under a "Home screen"
    // heading, a page called "Home screen" says the heading twice and says nothing.
    GRID(
        SettingsCategory.APPEARANCE, "Grid & cards",
        "Which layout Home uses, then size, spacing, icons and labels",
        group = "Home screen",
    ),
    CURSOR(
        SettingsCategory.APPEARANCE, "Selection cursor",
        "Selection highlight style and glow",
        group = "Home screen",
    ),
    DOCK(
        SettingsCategory.APPEARANCE, "Dock",
        "Size, transparency and behaviour",
        group = "Home screen",
    ),

    WALLPAPER(
        SettingsCategory.APPEARANCE, "Wallpaper",
        "Background image, animated effect and how far it is dimmed",
        group = "Background & text",
    ),
    INTERFACE(
        SettingsCategory.APPEARANCE, "Interface",
        "Text size, motion, clock and folder style",
        group = "Background & text",
    ),

    // ---- Games & artwork ---------------------------------------------------
    PLATFORMS(
        SettingsCategory.LIBRARY, "Platforms",
        "Consoles, their ROM folders and emulators",
        group = "Where games come from",
    ),
    ROM_FOLDERS(
        SettingsCategory.LIBRARY, "Extra ROM folders",
        "Locations not tied to one platform",
        group = "Where games come from",
    ),
    SCANNING(
        SettingsCategory.LIBRARY, "Scanning",
        "How games and apps are found",
        group = "Where games come from",
    ),

    SORTING(
        SettingsCategory.LIBRARY, "Sorting",
        "Default library order",
        group = "How they are arranged",
    ),

    /**
     * Folders defined by a query rather than by what was filed into them.
     *
     * Beside Sorting, because both answer "how is my library arranged" — and a
     * smart folder is closer to a saved sort than it is to the folders you make by
     * hand, which are made from the grid where they live.
     */
    SMART_FOLDERS(
        SettingsCategory.LIBRARY, "Smart folders",
        "Folders that fill themselves from a query",
        group = "How they are arranged",
    ),

    METADATA(
        SettingsCategory.LIBRARY, "Metadata & scraping",
        "Where descriptions and artwork are fetched from",
        group = "Artwork & progress",
    ),
    ICON_PACKS(
        SettingsCategory.LIBRARY, "Platform artwork",
        "Icon packs, and the art Loki ships",
        group = "Artwork & progress",
    ),

    /**
     * With artwork rather than with metadata, which it is not.
     *
     * Metadata describes the game; this describes the player's progress through
     * it. They come from different places, are keyed to different things, and one
     * of them needs an account. It was also declared at the very bottom of this
     * enum, which — since declaration order is display order — put it under
     * Accessibility's neighbours in the file and dead last in Games & artwork,
     * several pages away from everything it belongs with.
     */
    ACHIEVEMENTS(
        SettingsCategory.LIBRARY, "Achievements",
        "RetroAchievements account and matching",
        group = "Artwork & progress",
    ),

    // ---- Films & shows -----------------------------------------------------
    MOVIES_CATALOGUE(
        SettingsCategory.MOVIES, "Sources & accounts",
        "Debrid account, addons and torrent indexers",
    ),
    MOVIES_PLAYBACK(
        SettingsCategory.MOVIES, "Playback",
        "Which source is chosen, and how it plays",
    ),

    // ---- PC streaming ------------------------------------------------------
    STREAM_QUALITY(
        SettingsCategory.STREAMING, "Picture",
        "Resolution, frame rate and bandwidth",
    ),
    STREAM_CONTROLS(
        SettingsCategory.STREAMING, "Controls",
        "Trackpad, keyboard, touch and the pad",
    ),
    STREAM_HOSTS(
        SettingsCategory.STREAMING, "PCs",
        "How PCs are found, and what Loki calls itself",
    ),

    // ---- Controls ----------------------------------------------------------
    /**
     * First in Controls, because it is the one that decides what the rest mean.
     *
     * Navigation and Feedback tune how a press behaves; this decides which press
     * it was. It said so already and was declared second, which — since
     * declaration order is display order — meant it was drawn second and the
     * comment described an arrangement that did not exist.
     *
     * No group headings here: four pages is a list you read rather than search.
     */
    BUTTON_MAPPING(
        SettingsCategory.CONTROLS, "Button mapping",
        "Which button does what, and how it feels",
    ),
    NAVIGATION(
        SettingsCategory.CONTROLS, "Navigation",
        "Cursor movement and stick behaviour",
    ),
    POINTER(
        SettingsCategory.CONTROLS, "Pointer",
        "Controller mouse for apps and games",
    ),
    FEEDBACK(
        SettingsCategory.CONTROLS, "Feedback",
        "Haptics and interface sound",
    ),

    // ---- Profiles ----------------------------------------------------------
    // The list first, then the one profile you are actually signed in as. Whose
    // launcher this is comes before what it is called.
    PROFILES(
        SettingsCategory.PROFILES, "Profiles",
        "Switch, add and remove the people using this device",
    ),
    PROFILE_EDIT(
        SettingsCategory.PROFILES, "This profile",
        "Name, picture and colour for whoever is signed in",
    ),

    // ---- System ------------------------------------------------------------
    // Display and performance live here rather than in a category of their own.
    // Two pages is not a category, and "how the screens behave" is the same visit
    // as "how it reads and how hard it works".
    DUAL_SCREEN(
        SettingsCategory.SYSTEM, "Dual screen",
        "How the two panels are used",
        group = "This device",
    ),
    PERFORMANCE(
        SettingsCategory.SYSTEM, "Performance",
        "Animation and visual effects",
        group = "This device",
    ),
    ACCESSIBILITY(
        SettingsCategory.SYSTEM, "Accessibility",
        "Contrast, motion, text and colour vision",
        group = "This device",
    ),

    /**
     * Copying a profile out and putting it back.
     *
     * Under System rather than under Profiles: it is about the device and its
     * storage, and it is where somebody looks after deciding to reinstall — which
     * is the same visit as "how do I get my launcher back".
     */
    BACKUP(
        SettingsCategory.SYSTEM, "Backup",
        "Save this profile to a file, or restore one",
        group = "Data & features",
    ),

    EXTENSIONS(
        SettingsCategory.SYSTEM, "Extensions",
        "Add Films & shows or PC streaming to the launcher",
        group = "Data & features",
    ),

    /**
     * What a recording captures, as opposed to when one is started.
     *
     * Starting is two tiles in the shortcut panel and one on the companion panel,
     * where the decision is actually made; this is the standing choice those
     * inherit. Under Data & features because a recording is a file, and beside
     * Backup because both are about what leaves the device.
     */
    RECORDING(
        SettingsCategory.SYSTEM, "Recording",
        "Whether captures have sound",
        group = "Data & features",
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
        fun forCategory(
            category: SettingsCategory,
            enabledExtensions: Set<String> = emptySet(),
        ): List<SettingsPage> = entries.filter {
            it.category == category && it.isAvailable && it.isUnlocked(enabledExtensions)
        }

        private val SettingsPage.isAvailable: Boolean
            get() = when (this) {
                DOCK -> LauncherFeatures.DOCK_ENABLED
                else -> true
            }

        /**
         * Whether the extension this page's category belongs to is enabled.
         *
         * The same question [SettingsCategory] answers for the rail, asked again
         * here — not a finer-grained one. It used to claim to be per *page*, on
         * the reasoning that a page could belong to an extension its category
         * does not; nothing supports that, because [SettingsPage] carries no
         * extension of its own and this reads `category.extension`. A page in
         * that position would still be listed.
         *
         * Worth keeping as the second gate even so. The rail cannot offer a
         * hidden category, but the *selected* one is held in the view model and
         * outlives the rail that chose it: removing an extension while its
         * category is open leaves a selection naming something no longer there,
         * and this is what stops its pages being handed back.
         */
        private fun SettingsPage.isUnlocked(enabled: Set<String>): Boolean =
            category.extension?.id?.let { it in enabled } ?: true
    }
}
