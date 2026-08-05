package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The complete, serialisable user configuration.
 *
 * This is persisted as a single JSON document by `:core:datastore`. Keeping it
 * as one tree (rather than a bag of loose preference keys) means backup,
 * restore, cloud sync and the settings importer all operate on the same value,
 * and adding a field with a default is automatically backwards compatible.
 */
@Serializable
data class ThorSettings(
    val personalization: PersonalizationSettings = PersonalizationSettings(),
    val grid: GridSpec = GridSpec.DEFAULT,
    val dock: DockSettings = DockSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val metadata: MetadataSettings = MetadataSettings(),
    val controls: ControlSettings = ControlSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val audio: AudioSettings = AudioSettings(),
    val performance: PerformanceSettings = PerformanceSettings(),
    val accessibility: AccessibilitySettings = AccessibilitySettings(),
    val cloud: CloudSettings = CloudSettings(),
    val developer: DeveloperSettings = DeveloperSettings(),
    val mouse: MouseSettings = MouseSettings(),
    val media: MediaSettings = MediaSettings(),
    val stream: StreamSettings = StreamSettings(),
    /**
     * Installed platform icon packs, newest last.
     *
     * Top level rather than inside one of the settings groups because it is not a
     * preference: it is a record of content the user installed, including the
     * artwork held for platforms THOR does not model yet. Losing it would orphan
     * every copied file on disk with no way to know what they belonged to.
     */
    val iconPacks: List<IconPack> = emptyList(),
    /**
     * Whether the walkthrough has been seen.
     *
     * Top level rather than inside a settings group for the same reason
     * [iconPacks] is: it records something that happened, not a preference. It is
     * also why it is stored at all — a walkthrough that reappeared on every cold
     * start would be an obstacle rather than an introduction, and the launcher's
     * process is killed often enough on a handheld that "once per run" would mean
     * several times a day.
     */
    val tutorialCompleted: Boolean = false,
    /**
     * Whether the edit-mode gestures have been explained once.
     *
     * Its own flag rather than part of [tutorialCompleted], because the two are
     * read at different moments and only one of them is asked for. The
     * walkthrough runs before the user has a library to arrange, so anything it
     * said about resizing a cell would be about a screen they had not seen; this
     * is shown the first time they actually enter edit mode, which is the moment
     * the gestures become answerable questions.
     *
     * Recorded rather than counted. Edit mode is entered by holding a cell, and
     * a card that reappeared every session would be in the way of the gesture it
     * describes.
     */
    val editModeTutorialSeen: Boolean = false,
    /**
     * Whether the first-run list of permissions has been shown.
     *
     * Separate from [tutorialCompleted] because they are asked at different
     * moments and one is not evidence of the other: the permission list appears
     * before the walkthrough, and someone who replays the walkthrough later is
     * not asking to be prompted for accessibility again.
     */
    val permissionsPromptSeen: Boolean = false,
    /**
     * Extensions the user has enabled, by [LauncherExtension.id].
     *
     * Stored as ids rather than as the enum so an id Loki no longer has is
     * carried harmlessly rather than failing to read — and so a manifest naming
     * something from a newer build does not corrupt an older one's settings.
     */
    val enabledExtensions: Set<String> = emptySet(),
    /**
     * Extensions whose own short walkthrough has already been played.
     *
     * Separate from [enabledExtensions] so removing an extension and adding it
     * back does not replay its tour, and separate from [tutorialCompleted]
     * because the two are read at different moments: the main tour is the first
     * run, and an extension's is whenever the user chooses to add it — possibly
     * months later, on a launcher they already know.
     */
    val seenExtensionTours: Set<String> = emptySet(),
    /** Bumped by migrations in `SettingsSerializer`. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    /** True when [extension] has been enabled by importing its manifest. */
    fun has(extension: LauncherExtension): Boolean = extension.id in enabledExtensions

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val DEFAULT = ThorSettings()
    }
}

@Serializable
data class PersonalizationSettings(
    /**
     * What a fresh install looks like before anything is chosen.
     *
     * These three travel together and are a deliberate first impression rather
     * than a neutral one: Material's elevation tint, flowing bands behind it and
     * hard corners throughout. Existing installs are untouched — the settings
     * file is written with `encodeDefaults = true`, so every field is stored
     * explicitly and a changed default here reaches only a device with no file
     * yet.
     */
    val themeId: ThemeId = ThemeId.MATERIAL,
    /**
     * Light or dark, for whichever theme is chosen.
     *
     * Every theme resolves both ways — see [ThemeRecipe] — so this is a real
     * preference rather than a filter over a list. Dark by default because that is
     * what a handheld games console is read on, and [ThemeMode.SYSTEM] is one
     * selection away for anyone whose phone already switches at dusk.
     */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /**
     * How hard the palette separates text from ground.
     *
     * Distinct from [AccessibilitySettings.highContrast], which is a switch that
     * forces the maximum; this is the dial underneath it, and exists because
     * "slightly more than the default" was previously unreachable — the only
     * options were the theme as drawn or white text on black.
     */
    val contrastLevel: ContrastLevel = ContrastLevel.NORMAL,
    /**
     * Scales every chroma in the palette, 0 to 2. 1 is the theme as designed.
     *
     * Turned down far enough, any theme becomes a greyscale one with a coloured
     * cursor; turned up, a restrained one becomes loud. Cheaper than shipping
     * three versions of each theme, and more useful, because it also reaches the
     * tint on the surfaces rather than only the accent.
     */
    val colorIntensity: Float = 1.0f,
    /**
     * Rotates the whole palette in degrees, -180 to 180.
     *
     * Applies to the accent and to the tint on the greys together, so the theme
     * stays coherent — this is the same theme wearing a different colour, not an
     * accent pasted over somebody else's surfaces.
     */
    val accentHueShift: Float = 0f,
    /**
     * Draws the darkest surface as true black.
     *
     * A property of the screen, not of a theme, which is why it sits over all
     * twelve of them: the AYN Thor's panels switch pixels off at #000, so this is
     * both the deepest look available and the only one that costs less power.
     * Nothing when the palette resolves light.
     */
    val pureBlack: Boolean = false,
    /**
     * Overrides the theme's own accent when set, as an ARGB long.
     *
     * The hue and chroma of it, rather than the colour verbatim — see
     * [ThemeOptions.accentOverrideArgb] for why a picked colour is a direction
     * rather than a value.
     */
    val accentOverrideArgb: Long? = null,
    /**
     * Overrides how every panel is built. Null keeps the theme's own.
     *
     * The materials were the part of a theme nobody could reach: a user who liked
     * Terminal's palette but wanted its panels to stop being hard-edged rectangles
     * had to pick a different theme entirely.
     */
    val surfaceStyleOverride: SurfaceStyle? = null,
    /** Scales the theme's background wash toward its accent. 0 is a flat ground. */
    val surfaceDepth: Float = 1.0f,
    /** Scales the theme's film grain. 0 removes it. */
    val grainAmount: Float = 1.0f,
    /** Follows the system wallpaper/dynamic colour when supported (API 31+). */
    val useDynamicColor: Boolean = false,
    val wallpaperUri: String? = null,
    val topScreenWallpaperUri: String? = null,
    val animatedWallpaper: AnimatedWallpaper = AnimatedWallpaper.WAVES,
    /**
     * How far the wallpaper is dimmed behind the interface, 0 to 1.
     *
     * A photograph chosen for how it looks is rarely a photograph that content
     * reads well over, and the alternative was picking a different photograph.
     */
    val wallpaperDim: Float = 0f,
    val cursorStyle: CursorStyle = CursorStyle.RING,
    val cursorAnimation: CursorAnimation = CursorAnimation.BREATHE,
    /** 0..1 intensity of the glow behind the cursor. */
    val highlightGlow: Float = 0.6f,
    val glassEffects: Boolean = true,
    /**
     * Overrides every corner in the launcher, so nothing is rounded on its own.
     *
     * Corners were the theme's business alone, which meant a theme with a 2dp
     * radius still had pill-shaped tabs and circular dock slots hardcoded next to
     * its hard-edged panels. This is one answer for the whole interface.
     *
     * Square by default, which overrides the starting theme's own 24dp radius.
     * A console grid reads as a grid when its cells share the panel's edges, and
     * [THEME] is one selection away for anyone who wants the rounder shape back.
     */
    val cornerStyle: CornerStyle = CornerStyle.SQUARE,
    /**
     * Plays a game's trailer on the information panel while it is highlighted.
     *
     * On by default: a shelf of games that move is most of what makes a library
     * feel browsable rather than filed. The bumpers put the stills up instead for
     * whichever game is highlighted, and performance mode turns it off entirely —
     * a decoder per dwell is exactly the sort of cost that switch exists to avoid.
     */
    val autoplayTrailers: Boolean = true,
    /**
     * Overrides the theme's typeface. Null keeps the theme's own.
     *
     * Five faces have shipped since the first theme and none of them were
     * selectable: the launcher's font was whatever the chosen palette happened to
     * declare, so reading it in a serif meant living with Linen's colours.
     */
    val fontOverride: FontChoice? = null,
    /** Overrides the theme's motion personality. Null keeps the theme's own. */
    val motionOverride: MotionStyle? = null,
    val fontScale: Float = 1.0f,
    val transitionSpeed: Float = 1.0f,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL_24,
    val showStatusBar: Boolean = true,
    val showPageIndicators: Boolean = true,
    val folderStyle: FolderStyle = FolderStyle.STACK,
    /**
     * Whether the console artwork Loki ships with dresses platform folders.
     *
     * On by default: it is the launcher's own set, and a grid whose systems are
     * lettered plates until the user finds a switch has simply shipped its
     * artwork turned off.
     *
     * A preference rather than content, and so it lives here beside
     * [folderStyle]: the artwork is resolved when a cell is drawn and stored
     * nowhere, which is what lets an installed pack or a hand-picked image win
     * without anything having to be undone. Turning this off reveals whatever
     * else the platform has, not a blank cell.
     */
    val bundledPlatformIcons: Boolean = true,
) {
    /**
     * These preferences in the form the palette generator takes.
     *
     * The only place the mapping lives, so nothing has to remember that light/dark
     * is three-valued or that the intensity slider is a chroma multiplier — and so
     * that a preview card and the live launcher cannot disagree about what a
     * setting means.
     *
     * @param systemDark what Android's own light/dark setting currently says,
     *   which is the answer [ThemeMode.SYSTEM] defers to
     */
    fun themeOptions(systemDark: Boolean = true): ThemeOptions = ThemeOptions(
        dark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        },
        contrast = contrastLevel,
        colorIntensity = colorIntensity,
        hueShift = accentHueShift,
        pureBlack = pureBlack,
        accentOverrideArgb = accentOverrideArgb,
        surfaceStyle = surfaceStyleOverride,
        depthScale = surfaceDepth,
        grainScale = grainAmount,
    )

    /** The finished palette: [themeId] resolved against everything else chosen. */
    fun resolveTheme(systemDark: Boolean = true): ThemeSpec =
        ThemeRecipe.of(themeId).resolve(themeOptions(systemDark))
}

/**
 * How every corner in the launcher is drawn.
 *
 * A single answer for the whole interface rather than a value each component
 * chooses. Panels took their radius from the theme, but pills, tabs, dock slots
 * and dialogs were shaped where they were written — so a hard-edged theme still
 * had rounded furniture in it, and nothing quite matched anything else.
 *
 * [THEME] keeps that per-theme character, which is right for presets built around
 * it; the other two impose one shape on everything and let the theme carry only
 * colour and material.
 */
@Serializable
enum class CornerStyle(val label: String) {
    /**
     * Every corner square, including the ones that are normally circles.
     *
     * First because it is the default, and a list whose default is third asks
     * the reader to hunt for where they already are. Declaration order is the
     * order these are offered in.
     */
    SQUARE("Square"),

    /** Every corner rounded, generously and identically. */
    ROUNDED("Rounded"),

    /** Each theme's own radius, from hard-edged presets to very round ones. */
    THEME("Theme default"),
}

@Serializable
enum class AnimatedWallpaper(val label: String) {
    /** Stacked flowing bands, and what a fresh install opens on. */
    WAVES("Waves"),

    NONE("Static"),

    /** Multi-point mesh gradient — soft overlapping colour fields. */
    MESH("Mesh"),
    AURORA("Aurora"),

    /** Large defocused orbs drifting at different depths. */
    BOKEH("Bokeh"),
    PARTICLES("Particles"),
    STARFIELD("Starfield"),
    GRADIENT_DRIFT("Gradient drift"),

    /** Layered shapes moving at different rates. */
    PARALLAX_ART("Parallax"),

    /**
     * Tinted by whatever is highlighted.
     *
     * Takes its hue from the selected game's platform accent, so the background
     * shifts as the cursor moves across systems. The one effect that makes the
     * wallpaper part of browsing rather than decoration behind it.
     */
    ADAPTIVE("Adaptive"),
}

@Serializable
enum class CursorStyle(val label: String) {
    RING("Ring"),
    FILL("Fill"),
    CORNERS("Corner brackets"),
    UNDERLINE("Underline"),
    SPOTLIGHT("Spotlight"),
}

@Serializable
enum class CursorAnimation(val label: String) {
    NONE("None"),
    BREATHE("Breathe"),
    PULSE("Pulse"),
    ROTATE("Rotate"),
    SHIMMER("Shimmer"),
}

@Serializable
enum class UiDensity(val label: String, val scale: Float) {
    COMPACT("Compact", 0.88f),
    COMFORTABLE("Comfortable", 1.0f),
    SPACIOUS("Spacious", 1.14f),
}

@Serializable
enum class ClockStyle(val label: String) {
    HIDDEN("Hidden"),
    DIGITAL_12("12-hour"),
    DIGITAL_24("24-hour"),
    ANALOG("Analog"),
}

@Serializable
enum class FolderStyle(val label: String) {
    /** Contents fan out of the folder icon. */
    STACK("Stack"),
    /** A 2x2 preview of the first four children. */
    GRID_PREVIEW("Grid preview"),
    /** A single glyph on a coloured shell. */
    GLYPH("Glyph"),
}

@Serializable
data class DockSettings(
    val visible: Boolean = true,
    /** Serialised launcher actions occupying the five slots. */
    val slots: List<LauncherAction> = DefaultDock.ACTIONS,
    /**
     * Slot names.
     *
     * Never drawn — the dock is glyph-only — but kept as the accessibility
     * label for each slot, which otherwise has no readable name at all.
     */
    val labels: List<String> = DefaultDock.LABELS,
    val backgroundAlpha: Float = 0.55f,
    val blurEnabled: Boolean = true,
    val scale: Float = 1.0f,
    /** Hides the dock until the cursor moves into it. */
    val autoHide: Boolean = false,
    val style: DockStyle = DockStyle.PILL,
)

/** How the dock's surface is drawn. */
@Serializable
enum class DockStyle(val label: String) {
    /** A fully-rounded floating pill, distinct from the grid above it. */
    PILL("Rounded pill"),

    /**
     * Square-cornered and flush, matching the grid's own cells.
     *
     * Uses the same corner treatment and a flatter surface so the dock reads as
     * the bottom row of the grid rather than as something floating over it.
     */
    SQUARE("Square"),
}

@Serializable
data class LibrarySettings(
    /** Tree URIs granted through the storage access framework. */
    val romDirectoryUris: List<RomDirectory> = emptyList(),
    val scanArchives: Boolean = true,
    val detectDuplicates: Boolean = true,
    /** Groups regional variants and revisions under one entry. */
    val groupVersions: Boolean = true,
    val hideSystemApps: Boolean = true,
    /**
     * Whether every installed application is placed on the grid automatically.
     *
     * Off by default. The app drawer is where applications live; the grid is for
     * what the user chose to put there. Auto-placing all of them filled the
     * home pages with system utilities before the user had arranged anything.
     * Apps added by hand from the drawer are unaffected by this.
     */
    val showAppsOnGrid: Boolean = false,
    /**
     * Reveals entries hidden from the grid, dimmed, so they can be got back.
     *
     * Hiding is durable by design — it survives rescans, because an entry the
     * user hid should not reappear every time the library is refreshed. That is
     * correct and also a trap: a hidden entry has no cell, so there is nothing to
     * long-press, and nothing anywhere else that lists it. Hide a game and it is
     * gone with no route back at all.
     *
     * This is that route. Off by default, because the hidden things are hidden on
     * purpose; on, they return dimmed and can be unhidden or removed outright.
     */
    val showHiddenEntries: Boolean = false,
    val defaultSort: SortOrder = SortOrder.MANUAL,
    val sortDescending: Boolean = false,
)

/** One user-granted ROM location. */
@Serializable
data class RomDirectory(
    val uri: String,
    val displayName: String,
    /** When set, everything found here is assigned to this platform. */
    val platformId: String? = null,
    /** Recurse into subdirectories, using folder names as platform hints. */
    val recursive: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
data class MetadataSettings(
    /**
     * Providers consulted by a scrape.
     *
     * ScreenScraper is included even though it is priority 0: it was previously
     * absent here, so the launcher's highest-priority source never ran no matter
     * how it was credentialled. `retroachievements` is gone — that integration was
     * dropped, and an enabled provider with no client is a scrape slot that
     * silently returns nothing.
     *
     * Only providers with a working client belong here. An enabled-but-unbuilt
     * provider is indistinguishable, from the grid, from one that found no match.
     */
    val enabledProviders: Set<String> = setOf(
        "screenscraper",
        "steamgriddb",
        "wikidata",
        "rawg",
        "igdb",
    ),
    /** Provider id -> priority; lower wins when merging conflicting fields. */
    val providerPriority: Map<String, Int> = mapOf(
        "screenscraper" to 0,
        "steamgriddb" to 1,
        // Above RAWG: Wikidata needs no key, so on a fresh install it is the
        // only one of the two that can answer at all.
        "wikidata" to 2,
        // Above RAWG for artwork reasons rather than textual ones: IGDB is the
        // only source that lets the *shape* of an image be asked for, so where
        // both answer, its screenshots are the ones that fit the panel.
        "igdb" to 3,
        "rawg" to 4,
    ),
    /** Provider id -> API key/token. Stored encrypted at rest by the datastore. */
    val apiKeys: Map<String, String> = emptyMap(),
    /**
     * The user's own ScreenScraper account.
     *
     * Optional. The application's developer key is what authorises requests at
     * all and is compiled into the build; an account on top of it raises the
     * daily quota and unlocks higher-resolution media.
     */
    val screenScraperUser: String = "",
    val screenScraperPassword: String = "",
    /**
     * IGDB, which authenticates through Twitch because Amazon owns both.
     *
     * Two values rather than one key: the pair is exchanged for a bearer token
     * that the provider caches. Both are obtained from the Twitch developer
     * console in a couple of minutes, with no approval step — which is the
     * reason this provider exists alongside ScreenScraper rather than instead
     * of it.
     */
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val scrapeOnlyMissing: Boolean = true,
)

@Serializable
data class ControlSettings(
    val activeProfileId: String = ControllerProfile.DEFAULT_ID,
    val customProfiles: List<ControllerProfile> = emptyList(),
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.7f,
    /** Wraps the cursor around grid edges instead of stopping. */
    val wrapNavigation: Boolean = false,
    /** Moves to the next page when navigating past the last column. */
    val edgeFlipsPage: Boolean = true,
    val touchEnabled: Boolean = true,
    /** Analog stick sensitivity multiplier. */
    val stickSensitivity: Float = 1.0f,
)

@Serializable
data class DisplaySettings(
    /**
     * How THOR maps its two surfaces onto the hardware. `AUTO` uses a real
     * secondary display when one is present and falls back to splitting a
     * single display, which is also what makes the launcher testable on an
     * ordinary phone or emulator.
     */
    val mode: DualScreenMode = DualScreenMode.AUTO,
    /** Swaps which physical panel shows the grid. */
    val swapScreens: Boolean = false,
    /** Fraction of a single display given to the top surface in split mode. */
    val splitRatio: Float = 0.5f,
    /**
     * Switches to Couch Mode on its own when a monitor is plugged in.
     *
     * On by default, because a monitor is a statement about where the user is: they
     * have put the device in a dock and sat back, and the handheld layout is
     * unreadable from there. Making them go and find the setting first is asking
     * them to fix something the launcher could see for itself.
     *
     * Only applies to [DualScreenMode.AUTO]. Someone who has chosen a mode outright
     * has said what they want, and a monitor is not a reason to overrule them —
     * which is also why this is a switch rather than behaviour: unplug-and-replug
     * with a specific layout in mind should stay put.
     */
    val couchOnExternalDisplay: Boolean = true,
    /** Physical scale of Couch Mode's navigation, hero, shelves and section UIs. */
    val couchUiScale: Float = DEFAULT_COUCH_UI_SCALE,
    /** What Couch Mode draws behind its dashboard. */
    val couchWallpaper: CouchWallpaperStyle = CouchWallpaperStyle.RIDGES,
    val keepTopScreenAwake: Boolean = true,
) {
    companion object {
        const val MIN_COUCH_UI_SCALE = 0.75f
        const val MAX_COUCH_UI_SCALE = 1.4f
        const val DEFAULT_COUCH_UI_SCALE = 1.0f

        /**
         * What the setting's 100% is worth in density.
         *
         * Couch Mode used to draw at the panel's own density, which on a
         * television is a handheld's interface enlarged to fill a wall: correct
         * arithmetic, far too big to be read comfortably from a sofa, and it took
         * turning the setting down to three quarters before the screen held as
         * much as a set-top box's does.
         *
         * Folded in here rather than left as a number the user has to find. The
         * setting stays a plain percentage of a sensible size, which is what a
         * percentage should be - it is a preference, not a correction.
         */
        const val COUCH_BASE_SCALE = 0.75f

        /**
         * The density multiplier a stored [couchUiScale] actually means.
         *
         * Clamped here rather than at each screen, so a value that arrives out of
         * range from an import or an older release cannot make one surface tiny
         * while its neighbour is unaffected.
         */
        fun couchDensityScale(scale: Float): Float =
            scale.coerceIn(MIN_COUCH_UI_SCALE, MAX_COUCH_UI_SCALE) * COUCH_BASE_SCALE
    }
}

/**
 * What Couch Mode draws behind its dashboard.
 *
 * Separate from [AnimatedWallpaper] rather than reusing it, because the two are
 * behind different things. The launcher's own wallpaper sits under a grid of
 * icons on a panel held at arm's length, where detail reads; these sit under
 * large translucent panels on a television across a room, where it does not —
 * so they are built from a few slow, wide shapes and stay dark and even under
 * the regions that carry text.
 *
 * All of them are drawn rather than decoded: no asset, no memory, no decode on a
 * screen the user leaves open. Each is tinted by the highlighted system's accent,
 * so the room still shifts as the cursor crosses from one console to another.
 *
 * [THEME] is the way back to the launcher's own set for anyone who wants it, and
 * every one of these settles to a fixed composition rather than disappearing when
 * motion is turned off — see [AnimatedWallpaper] for why that distinction matters.
 */
@Serializable
enum class CouchWallpaperStyle(val label: String) {
    /** Layered hills drifting against each other. The default. */
    RIDGES("Ridges"),

    /** Slow vertical curtains of accent light. */
    AURORA("Aurora"),

    /** Large defocused fields kneading through one another. */
    DRIFT("Drift"),

    /** A perspective grid running away to a horizon line. */
    HORIZON("Horizon"),

    /** Motes rising slowly through the dark. */
    EMBERS("Embers"),

    /** Concentric rings breathing out from behind the panels. */
    PULSE("Pulse"),

    /** Whatever the launcher's own wallpaper setting is. */
    THEME("Match launcher"),

    /** The theme's background colour, and nothing else. */
    SOLID("Solid colour"),
}

@Serializable
enum class DualScreenMode(val label: String) {
    AUTO("Automatic"),
    /** Force use of a secondary Display via a Presentation. */
    DUAL_DISPLAY("Dual display"),
    /** Split one display into a top and bottom half. */
    SPLIT_SINGLE("Split single display"),
    /** Bottom surface only; the info panel becomes an overlay sheet. */
    SINGLE("Single screen"),

    /**
     * The top screen alone, with the bottom panel dark.
     *
     * For the device sitting in a dock with a controller in your hands rather
     * than the device itself. The bottom panel is under the dock, or facing the
     * ceiling, or simply not where you are looking — and a launcher that keeps it
     * lit is spending battery and throwing light at nobody, while putting half of
     * itself somewhere unreadable.
     *
     * Both surfaces share the top screen, split the way [SPLIT_SINGLE] splits
     * one: details above, grid below. Not a reduced launcher — everything is
     * still here, and the second panel comes back the moment the mode changes.
     */
    COUCH("Couch mode"),
}

@Serializable
data class AudioSettings(
    /**
     * Master switch for every interface sound.
     *
     * Separate from the per-category flags so "off" is one toggle rather than
     * three, and so turning sound back on restores the previous mix.
     */
    val soundEffectsEnabled: Boolean = true,
    val uiVolume: Float = 0.6f,
    val navigationSounds: Boolean = true,
    val launchSounds: Boolean = true,
)

@Serializable
data class PerformanceSettings(
    /** Disables blur, animated wallpapers and previews in one switch. */
    val performanceMode: Boolean = false,
    val animationsEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
)

@Serializable
data class AccessibilitySettings(
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
    val reduceMotion: Boolean = false,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.NONE,
    /** Shifts interactive UI toward one side for single-handed use. */
    /** Extra multiplier on all touch target sizes. */
    val touchTargetScale: Float = 1.0f,
)

@Serializable
enum class ColorBlindMode(val label: String) {
    NONE("Off"),
    PROTANOPIA("Protanopia"),
    DEUTERANOPIA("Deuteranopia"),
    TRITANOPIA("Tritanopia"),
    GRAYSCALE("Grayscale"),
}

@Serializable
enum class OneHandedMode(val label: String) {
    OFF("Off"),
    LEFT("Left-handed"),
    RIGHT("Right-handed"),
}

@Serializable
data class CloudSettings(
    val syncEnabled: Boolean = false,
    val provider: CloudProvider = CloudProvider.NONE,
    val syncSettings: Boolean = true,
    val syncLayout: Boolean = true,
    val syncMetadata: Boolean = true,
    val syncArtwork: Boolean = false,
    val syncCollections: Boolean = true,
    val syncControllerProfiles: Boolean = true,
    val lastSyncEpochMs: Long? = null,
    /** Tree URI of the folder backups are written to. */
    val backupDirectoryUri: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalHours: Int = 168,
)

@Serializable
enum class CloudProvider(val label: String) {
    NONE("None"),
    /** Any location reachable through the storage access framework. */
    SAF_FOLDER("Storage folder"),
    WEBDAV("WebDAV"),
}

@Serializable
data class DeveloperSettings(
    val verboseLogging: Boolean = false,
)
