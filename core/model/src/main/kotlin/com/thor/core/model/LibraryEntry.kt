package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * Anything that can occupy a cell on the bottom-screen grid.
 *
 * The grid is deliberately homogeneous: installed Android apps, scanned ROMs,
 * folders and launcher actions are all [GridEntry] instances, so the layout
 * engine never branches on type. Type-specific data hangs off the sealed
 * subclasses and is only consulted when launching or when the top screen builds
 * its detail panel.
 */
@Serializable
sealed interface GridEntry {
    /** Stable, globally unique id. Also the drag-and-drop key. */
    val id: String

    /** Name shown under the icon. */
    val title: String

    /** Name used for sorting and search; ignores leading articles. */
    val sortTitle: String

    val isFavorite: Boolean

    /** Hidden entries stay in the library but never render on the grid. */
    val isHidden: Boolean
}

/**
 * A home-screen widget belonging to another application.
 *
 * The only entry that is not a thing to launch. It occupies cells and draws
 * itself, and nearly everything the grid does to an entry — open it, favourite
 * it, file it in a folder, put it on the dock — is meaningless here. It is a
 * [GridEntry] regardless because placement is the one thing it does share, and
 * a parallel list of widget positions would be a second grid to keep in step
 * with the first.
 *
 * [appWidgetId] is allocated by the host and is the only durable handle to a
 * live widget; the provider alone is not enough, because the same provider can
 * be placed more than once. It is meaningless to any other install, which is
 * why widgets are not part of a profile export.
 */
@Serializable
data class WidgetEntry(
    override val id: String,
    override val title: String,
    override val sortTitle: String,
    /** The host's handle, from `AppWidgetHost.allocateAppWidgetId`. */
    val appWidgetId: Int,
    /** Flattened `ComponentName` of the provider, for rebinding after a restart. */
    val providerComponent: String,
    /**
     * Set when the launcher draws this itself; see [LauncherWidget].
     *
     * Null means an Android app widget, which is hosted rather than drawn and
     * whose [appWidgetId] is a real allocation from the platform. A built-in has
     * nothing to allocate — its id exists only to give the row a key — which is
     * the one difference every caller has to respect, because releasing an id
     * that was never allocated is a no-op at best.
     */
    val builtIn: LauncherWidget? = null,
    /** How many grid cells wide and tall, at the spec the widget was placed under. */
    val spanColumns: Int = 1,
    val spanRows: Int = 1,
    override val isFavorite: Boolean = false,
    override val isHidden: Boolean = false,
) : GridEntry {

    /** Cells occupied, which is what the grid has to reserve. */
    val cellCount: Int get() = spanColumns * spanRows

    /** True when the widget host is not involved at any point in its life. */
    val isBuiltIn: Boolean get() = builtIn != null

    companion object {
        /** Ids are prefixed so a widget is recognisable without a lookup. */
        const val ID_PREFIX = "widget:"

        fun idFor(appWidgetId: Int): String = "$ID_PREFIX$appWidgetId"

        /** The smallest and largest a widget may be made in edit mode. */
        const val MIN_SPAN = 1
        const val MAX_SPAN = 4
    }
}

/**
 * An installed Android application or emulator front-end.
 */
@Serializable
data class AppEntry(
    override val id: String,
    override val title: String,
    override val sortTitle: String,
    val packageName: String,
    /** Fully qualified launch activity, resolved at scan time. */
    val activityName: String,
    /** Set when the app is provided by a work profile or secondary user. */
    val userSerial: Long = 0L,
    val versionName: String? = null,
    val installedAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    /** True when the package declares itself an emulator (see EmulatorRegistry). */
    val isEmulator: Boolean = false,
    val isSystemApp: Boolean = false,
    override val isFavorite: Boolean = false,
    override val isHidden: Boolean = false,
    val lastPlayedEpochMs: Long? = null,
    val launchCount: Int = 0,
    /** User-chosen icon; overrides the packaged launcher icon when set. */
    val customIconUri: String? = null,
) : GridEntry

/**
 * A game backed by a ROM/disc image on storage, launched through an emulator.
 */
@Serializable
data class GameEntry(
    override val id: String,
    override val title: String,
    override val sortTitle: String,
    val platformId: String,
    /** Content URI or absolute path of the primary game file. */
    val contentUri: String,
    /** File name as found on disk, kept for re-scan matching. */
    val fileName: String,
    val fileSizeBytes: Long,
    /**
     * Other files that are the same game — regional variants, revisions,
     * multi-disc entries. The primary entry owns the grid cell; alternates are
     * offered in the detail panel's version picker.
     */
    val alternateVersions: List<GameVersion> = emptyList(),
    val metadata: GameMetadata = GameMetadata.EMPTY,
    val stats: PlayStats = PlayStats.EMPTY,
    /** Emulator override; falls back to the platform default when null. */
    val emulatorPackage: String? = null,
    val tags: Set<String> = emptySet(),
    /** The last library scan could no longer reach this ROM. */
    val isMissing: Boolean = false,
    override val isFavorite: Boolean = false,
    override val isHidden: Boolean = false,
) : GridEntry {

    /** Best-effort content hash used to de-duplicate across ROM directories. */
    val duplicateKey: String get() = "$platformId:$sortTitle:$fileSizeBytes"
}

/** One alternate dump of the same title. */
@Serializable
data class GameVersion(
    val id: String,
    val label: String,
    val contentUri: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val region: String? = null,
    val languages: List<String> = emptyList(),
    val discNumber: Int? = null,
)

/**
 * A folder on the grid. Folders can nest, which is how "stacking" works.
 */
@Serializable
data class FolderEntry(
    override val id: String,
    override val title: String,
    override val sortTitle: String,
    val description: String = "",
    /** ARGB tint applied to the folder shell and its open animation. */
    val accentArgb: Long? = null,
    val iconKey: String = FolderIcons.DEFAULT,
    /** Custom artwork URI shown on the top screen when the folder is selected. */
    val artworkUri: String? = null,
    /** Ordered ids of the entries inside. */
    val childIds: List<String> = emptyList(),
    /**
     * When set, the folder's contents are computed from a query rather than
     * stored, which is what makes it a "smart folder".
     */
    val smartQuery: SmartQuery? = null,
    override val isFavorite: Boolean = false,
    override val isHidden: Boolean = false,
) : GridEntry {
    val isSmart: Boolean get() = smartQuery != null
}

/**
 * A launcher action bound to a grid cell or dock slot — the built-in shortcuts
 * (settings, app drawer, keyboard…) as well as anything the user pins there.
 */
@Serializable
data class ShortcutEntry(
    override val id: String,
    override val title: String,
    override val sortTitle: String,
    val action: LauncherAction,
    /** Overrides the action's stock icon when the user picks their own. */
    val customIconUri: String? = null,
    override val isFavorite: Boolean = false,
    override val isHidden: Boolean = false,
) : GridEntry

/** Named folder glyphs bundled with the launcher. */
object FolderIcons {
    const val DEFAULT = "folder"
    val ALL = listOf(
        DEFAULT, "controller", "star", "clock", "download", "grid",
        "heart", "bolt", "disc", "cartridge", "handheld", "arcade",
    )
}
