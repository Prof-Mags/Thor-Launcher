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
    /**
     * Installed platform icon packs, newest last.
     *
     * Top level rather than inside one of the settings groups because it is not a
     * preference: it is a record of content the user installed, including the
     * artwork held for platforms THOR does not model yet. Losing it would orphan
     * every copied file on disk with no way to know what they belonged to.
     */
    val iconPacks: List<IconPack> = emptyList(),
    /** Bumped by migrations in `SettingsSerializer`. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val DEFAULT = ThorSettings()
    }
}

@Serializable
data class PersonalizationSettings(
    val themeId: ThemeId = ThemeId.DARK,
    /** Overrides the theme's own accent when set. */
    val accentOverrideArgb: Long? = null,
    /** Follows the system wallpaper/dynamic colour when supported (API 31+). */
    val useDynamicColor: Boolean = false,
    val wallpaperUri: String? = null,
    val topScreenWallpaperUri: String? = null,
    val animatedWallpaper: AnimatedWallpaper = AnimatedWallpaper.AURORA,
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
     */
    val cornerStyle: CornerStyle = CornerStyle.THEME,
    /**
     * Plays a game's trailer on the information panel while it is highlighted.
     *
     * On by default: a shelf of games that move is most of what makes a library
     * feel browsable rather than filed. The bumpers put the stills up instead for
     * whichever game is highlighted, and performance mode turns it off entirely —
     * a decoder per dwell is exactly the sort of cost that switch exists to avoid.
     */
    val autoplayTrailers: Boolean = true,
    val fontScale: Float = 1.0f,
    val transitionSpeed: Float = 1.0f,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL_24,
    val showStatusBar: Boolean = true,
    val showPageIndicators: Boolean = true,
    val folderStyle: FolderStyle = FolderStyle.STACK,
)

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
    /** Each theme's own radius, from sharp Retro to very round Vision. */
    THEME("Theme default"),

    /** Every corner rounded, generously and identically. */
    ROUNDED("Rounded"),

    /** Every corner square, including the ones that are normally circles. */
    SQUARE("Square"),
}

@Serializable
enum class AnimatedWallpaper(val label: String) {
    NONE("Static"),

    /** Multi-point mesh gradient — soft overlapping colour fields. */
    MESH("Mesh"),
    AURORA("Aurora"),

    /** Stacked flowing bands. */
    WAVES("Waves"),

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
    ),
    /** Provider id -> priority; lower wins when merging conflicting fields. */
    val providerPriority: Map<String, Int> = mapOf(
        "screenscraper" to 0,
        "steamgriddb" to 1,
        // Above RAWG: Wikidata needs no key, so on a fresh install it is the
        // only one of the two that can answer at all.
        "wikidata" to 2,
        "rawg" to 3,
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
    val keepTopScreenAwake: Boolean = true,
)

@Serializable
enum class DualScreenMode(val label: String) {
    AUTO("Automatic"),
    /** Force use of a secondary Display via a Presentation. */
    DUAL_DISPLAY("Dual display"),
    /** Split one display into a top and bottom half. */
    SPLIT_SINGLE("Split single display"),
    /** Bottom surface only; the info panel becomes an overlay sheet. */
    SINGLE("Single screen"),
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
