package com.thor.feature.settings.tutorial

import com.thor.core.model.LauncherExtension
import com.thor.core.model.LauncherTab
import com.thor.feature.settings.SettingsCategory

/** Which screen a step's card is drawn on. */
enum class TutorialPanel {
    /** The panel holding the grid — the one the user is holding. */
    GRID,

    /** The information panel. */
    INFO,
}

/**
 * What a step points at, as a region of the panel it is drawn on.
 *
 * Named regions rather than measured bounds. A real coach mark has to know where
 * a view actually is, which means every surface reporting its position up to the
 * shell and staying correct through a re-layout, a density change and a panel
 * swap. These are structural facts instead — the grid is the panel above the
 * section bar, the bar is the strip along the bottom — so they cannot drift out
 * of step with a layout they never measured.
 */
enum class TutorialSpot {
    /** Nothing dimmed; the step is about the launcher rather than a place in it. */
    NONE,

    /** Everything above the section bar. */
    GRID,

    /** The strip along the bottom edge. */
    NAV_BAR,

    /** The whole panel, for a step about the panel itself. */
    PANEL,
}

/**
 * One screen of the walkthrough.
 *
 * @param panel which screen the card appears on. Steps move between the two on
 *   purpose: an explanation of the grid belongs beside the grid, not on the far
 *   screen where the reader has to look away from the thing being described.
 * @param spot the region to leave lit on [panel], with the rest dimmed
 * @param settingsCategory when set, the settings overlay is opened on the *other*
 *   panel at this category, so the reader sees the real screen being described
 */
data class TutorialStep(
    val title: String,
    val body: String,
    val hint: String? = null,
    val panel: TutorialPanel = TutorialPanel.GRID,
    val spot: TutorialSpot = TutorialSpot.NONE,
    val settingsCategory: SettingsCategory? = null,
)

/**
 * The guided tour.
 *
 * Built rather than declared, because two things about it depend on the device
 * it is running on: which settings categories exist (an extension the user has
 * not added has none), and which extension is being introduced.
 *
 * This replaced a version that was a document on the information panel. It read
 * well and pointed at nothing: every page described a surface the reader was not
 * looking at, including the grid, which was fully drawn on the other screen at
 * the time. A tour of a two-screen launcher has to use both screens.
 */
object ThorTutorial {

    /**
     * The tour shown once, after the permission list.
     *
     * Ends with the settings rail, which is walked category by category from
     * whatever the launcher actually has — so a fresh install with no extensions
     * never mentions Movies or PC streaming, and never opens a category that is
     * not there.
     */
    fun base(enabledExtensions: Set<String>): List<TutorialStep> = buildList {
        add(
            TutorialStep(
                title = "This screen is the grid",
                body = "Your games and apps live here, and this is the panel you " +
                    "drive with the controller.\n\nThe other screen shows " +
                    "everything known about whatever the cursor is resting on.",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.PANEL,
            ),
        )
        add(
            TutorialStep(
                title = "And this one describes it",
                body = "Box art, screenshots, developer, release year and how long " +
                    "you have played — for whatever is selected on the other " +
                    "screen.\n\nBoth panels are driven from one state, so they can " +
                    "never disagree.",
                panel = TutorialPanel.INFO,
                spot = TutorialSpot.PANEL,
            ),
        )
        add(
            TutorialStep(
                title = "Every icon stays where you put it",
                body = "An entry sits in the cell you place it in, and an empty " +
                    "cell stays empty. Nothing reflows when you add or remove " +
                    "something.\n\nHold A to pick an icon up, move, and press A " +
                    "again to drop it.",
                hint = "Hold A to pick up  ·  A to drop  ·  B to cancel",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.GRID,
            ),
        )
        add(
            TutorialStep(
                title = "Pages and density",
                body = "Pinch to snap between eight layouts, from 3×2 to 8×5. The " +
                    "size of your library changes the number of pages, never how " +
                    "much work a page costs to draw.",
                hint = "L2 / R2 turn pages  ·  pinch to change density",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.GRID,
            ),
        )
        add(
            TutorialStep(
                title = "Scanned games go into folders",
                body = "One folder per system, so adding a console costs the grid a " +
                    "single cell instead of three hundred. Anything you move out " +
                    "stays where you put it.",
                hint = "Y opens the menu for whatever is selected",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.GRID,
            ),
        )
        /*
         * Only when there is a bar to point at.
         *
         * The bar is drawn only once an extension has given it a second section
         * to switch between, and the grid reserves no height for it otherwise.
         * Teaching it regardless left a fresh install ringing an empty strip of
         * grid and telling the reader to press Down onto a bar that is not on
         * screen — and putting the cursor somewhere invisible if they did.
         */
        val sections = LauncherTab.visible(enabledExtensions)
        if (sections.size > 1) {
            add(
                TutorialStep(
                    title = "The bar along the bottom",
                    body = "Your sections live here — " +
                        sections.joinToString(", ") { it.label } +
                        ".\n\nPress Down past the last row of the grid to reach " +
                        "it, then Left and Right to move between them.",
                    panel = TutorialPanel.GRID,
                    spot = TutorialSpot.NAV_BAR,
                ),
            )
        }
        add(
            TutorialStep(
                title = "The buttons",
                body = "A launches and held picks up. B goes back. Y opens the " +
                    "menu, X favourites.\n\nStart opens the section menu and " +
                    "Select the app drawer. Click either stick for the shortcut " +
                    "panel — search, settings, rescan and more.",
                hint = "L1 / R1 flip screenshots  ·  L2 / R2 turn pages  ·  " +
                    "Guide goes Home",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.NONE,
            ),
        )
        add(
            TutorialStep(
                title = "Typing, and pointing",
                body = "Loki has its own keyboard, because Android's appears on the " +
                    "wrong screen here.\n\nHold Start and Select to raise a cursor " +
                    "you drive with the stick — for anything a gamepad cannot press.",
                hint = "Right stick scrolls  ·  A clicks  ·  Y opens the keyboard",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.NONE,
            ),
        )

        addAll(settingsSteps(enabledExtensions))

        add(
            TutorialStep(
                title = "That's everything",
                body = "Add your systems, point Loki at your ROMs, and let it " +
                    "scrape.\n\nThis tour is in Settings → About if you want it " +
                    "again.",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.NONE,
            ),
        )
    }

    /**
     * A short tour for an extension, played the first time it is added.
     *
     * Separate from the base tour because it is read at a different moment — the
     * user has just chosen to add this, so it answers "what did I just get" and
     * nothing else.
     */
    fun forExtension(extension: LauncherExtension): List<TutorialStep> = when (extension) {
        LauncherExtension.MOVIES -> listOf(
            TutorialStep(
                title = "Movies is on the bar",
                body = "A new section has appeared beside Home. It browses films " +
                    "and shows, finds sources for a title, and plays them.",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.NAV_BAR,
            ),
            TutorialStep(
                title = "It needs somewhere to look",
                body = "Browsing works out of the box. Playing needs a source: a " +
                    "URL based addon, a torrent indexer, or both — plus a debrid " +
                    "account, which is where most of the reliability comes from.",
                hint = "Everything is under Films & shows",
                panel = TutorialPanel.GRID,
                settingsCategory = SettingsCategory.MOVIES,
            ),
        )

        LauncherExtension.STREAM -> listOf(
            TutorialStep(
                title = "Stream is on the bar",
                body = "Loki can now find PCs on your network running Sunshine, " +
                    "pair with one, and play what it offers — video, audio and " +
                    "controller.",
                panel = TutorialPanel.GRID,
                spot = TutorialSpot.NAV_BAR,
            ),
            TutorialStep(
                title = "While a game is streaming",
                body = "The other panel becomes a trackpad and a keyboard, which is " +
                    "the only way to type into a streamed desktop on this " +
                    "device.\n\nPicture quality and how PCs are found are here.",
                hint = "Back leaves a stream, or hold Start, Select, L1 and R1",
                panel = TutorialPanel.GRID,
                settingsCategory = SettingsCategory.STREAMING,
            ),
        )
    }

    /**
     * One step per settings category the launcher actually has.
     *
     * Read from [SettingsCategory.navigationEntries] rather than written out, so
     * the tour and the rail can never disagree — a category hidden behind an
     * extension is absent from both, and a category added later is described
     * without anyone remembering to come back here.
     */
    private fun settingsSteps(enabledExtensions: Set<String>): List<TutorialStep> {
        val categories = SettingsCategory.navigationEntries(enabledExtensions)

        return buildList {
            add(
                TutorialStep(
                    title = "Settings",
                    body = "Opening on the other screen now. It is two levels: a " +
                        "short list of categories, and inside each a handful of " +
                        "pages.\n\nThe next few steps go through every one.",
                    hint = "Stick click → Settings, any time",
                    panel = TutorialPanel.GRID,
                    settingsCategory = categories.firstOrNull(),
                ),
            )
            categories.forEach { category ->
                add(
                    TutorialStep(
                        title = category.title,
                        body = CATEGORY_BODIES[category] ?: category.summary,
                        panel = TutorialPanel.GRID,
                        settingsCategory = category,
                    ),
                )
            }
        }
    }

    /**
     * What each category is for, in a sentence or two.
     *
     * Written out rather than taken from [SettingsCategory.summary], which is a
     * rail label — four words meant to be scanned, not read. A category with
     * nothing written here still appears in the tour with its label, so adding one
     * degrades to terse rather than to missing.
     */
    private val CATEGORY_BODIES: Map<SettingsCategory, String> = mapOf(
        SettingsCategory.APPEARANCE to
            "Fifteen themes, each with a wallpaper chosen to go with it, plus " +
            "corner shape and how much the interface moves.\n\nThe home screen " +
            "lives here too: grid density, icon shape, the dock and the cursor.",
        SettingsCategory.LIBRARY to
            "Where your games come from. Add a system, point it at a folder, and " +
            "Loki scans it and fetches box art, screenshots and descriptions.\n\n" +
            "Artwork sources and icon packs are on the pages beside it.",
        SettingsCategory.CONTROLS to
            "Every button is remappable, and the tester on the first page shows " +
            "you what the device is actually sending.\n\nThe pointer's speed, " +
            "acceleration and scrolling are here, with haptics and sound.",
        SettingsCategory.SYSTEM to
            "How the two panels behave, how hard the launcher works for its " +
            "animations, and the accessibility controls — contrast, text size, " +
            "reduced motion.\n\nExtensions are added from here.",
        SettingsCategory.ABOUT to
            "Version, device and what has been scanned, plus the diagnostics: " +
            "logs, a settings export, and a reset.\n\nThis walkthrough can be " +
            "replayed from here whenever you want it.",
        SettingsCategory.MOVIES to
            "The catalogue, where sources come from, and how playback behaves.",
        SettingsCategory.STREAMING to
            "Picture quality, bandwidth, and how PCs on your network are found.",
    )
}
