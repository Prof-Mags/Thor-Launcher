package com.thor.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Where a single entry sits on the bottom-screen grid.
 *
 * Positions are stored rather than derived so that a user's hand-placed layout
 * survives library rescans, and so gaps are preserved exactly as arranged —
 * matching 3DS HOME Menu behaviour, where an empty cell stays empty.
 */
@Serializable
data class GridPlacement(
    val entryId: String,
    val pageIndex: Int,
    val row: Int,
    val column: Int,
) {
    /** Row-major index within a page of [columns] width. */
    fun cellIndex(columns: Int): Int = row * columns + column

    companion object {
        fun fromCellIndex(entryId: String, pageIndex: Int, cellIndex: Int, columns: Int) =
            GridPlacement(
                entryId = entryId,
                pageIndex = pageIndex,
                row = cellIndex / columns,
                column = cellIndex % columns,
            )
    }
}

/** A named page of the grid. Pages are created and reordered freely. */
@Serializable
data class GridPage(
    val id: String,
    val index: Int,
    val title: String = "",
    /** Optional per-page wallpaper override. */
    val wallpaperUri: String? = null,
)

/**
 * The user's grid geometry.
 *
 * [columns] and [rows] define the cell matrix per page. The renderer derives
 * cell size from the available viewport, so changing these values re-flows
 * rather than scaling the whole surface.
 */
@Serializable
data class GridSpec(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    /** Icon size multiplier applied on top of the computed cell size. */
    val iconScale: Float = 1.0f,
    /** Gap between cells, in dp. */
    val spacingDp: Int = DEFAULT_SPACING_DP,
    /** Outer padding around the grid, in dp. */
    val paddingDp: Int = DEFAULT_PADDING_DP,
    val showLabels: Boolean = true,
    val labelLines: Int = 1,
    val iconShape: IconShape = IconShape.SQUIRCLE,
) {
    val cellsPerPage: Int get() = columns * rows

    /**
     * Gap between cells, as a fraction of one cell's edge length.
     *
     * Spacing has to be relative, not a fixed dp. A constant gap is the reason
     * the grid looked cramped as soon as it was pinched: at ten columns the
     * cells shrink but the gap does not, so it eats most of each cell, while at
     * three columns the same gap is a hairline between huge icons. Expressing it
     * as a proportion of the cell means every column count is spaced the same
     * way, which is what keeps the layout balanced at every interval.
     *
     * [spacingDp] survives as the user's tuning knob and is reinterpreted as
     * that proportion, so the existing setting still does what it says.
     */
    val spacingFraction: Float
        get() = (spacingDp / SPACING_DIVISOR).coerceIn(MIN_SPACING_FRACTION, MAX_SPACING_FRACTION)

    /**
     * Outer margin around the page, as a fraction of one cell's edge length.
     *
     * Derived the same way and from the same cell size so the margin around the
     * grid always relates to the gaps inside it, rather than the two drifting
     * apart as the matrix changes.
     */
    val paddingFraction: Float
        get() = (paddingDp / PADDING_DIVISOR).coerceIn(MIN_PADDING_FRACTION, MAX_PADDING_FRACTION)

    /** Clamps user input to a range the layout engine can actually render. */
    fun coerced(): GridSpec = copy(
        columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        rows = rows.coerceIn(MIN_ROWS, MAX_ROWS),
        iconScale = iconScale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
        spacingDp = spacingDp.coerceIn(0, 48),
        paddingDp = paddingDp.coerceIn(0, 64),
        labelLines = labelLines.coerceIn(1, 2),
    )

    /**
     * Applies a pinch gesture by stepping to the next preset layout.
     *
     * Pinch used to scale the icons continuously and only change the matrix once
     * the scale ran past a limit. That produced arbitrary in-between states —
     * every column count sharing one spacing value, and icons at 1.3× crowding
     * their neighbours — which is why the grid still looked cramped at most
     * sizes however the proportional spacing was tuned.
     *
     * Stepping between [PRESETS] instead means every size the user can reach is
     * one somebody chose, with spacing tuned for that density. Nothing in
     * between is reachable, which is the point.
     */
    fun pinched(scaleDelta: Float): GridSpec {
        val current = GridPreset.nearest(columns, rows)
        val index = PRESETS.indexOf(current)
        // Zooming in means bigger icons, which means fewer of them.
        val target = when {
            scaleDelta > 1f -> index - 1
            scaleDelta < 1f -> index + 1
            else -> index
        }
        return PRESETS[target.coerceIn(PRESETS.indices)].applyTo(this)
    }

    /** The preset this spec currently sits on, or the closest one to it. */
    val preset: GridPreset get() = GridPreset.nearest(columns, rows)

    companion object {
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 10
        const val MIN_ROWS = 2
        const val MAX_ROWS = 8
        const val MIN_ICON_SCALE = 0.7f
        const val MAX_ICON_SCALE = 1.45f

        /**
         * Tuned for the AYN Thor's bottom panel in landscape.
         *
         * 5×3 rather than 6×3: cells are square, so a six-column page on a
         * 16:9 panel produces cells narrower than the row height can use, and
         * the grid ends up with dead vertical space and undersized icons.
         * Five columns lets the square fill the row.
         */
        const val DEFAULT_COLUMNS = 5
        const val DEFAULT_ROWS = 3

        /**
         * Spacing is the gap *between* cells and padding is the inset around
         * the page. The previous 12/20 left icons nearly touching each other
         * while floating well away from the screen edge; these sit the other
         * way round, which reads as a deliberate grid rather than a cluster.
         */
        const val DEFAULT_SPACING_DP = 26
        const val DEFAULT_PADDING_DP = 20

        /**
         * The layouts pinch steps through, coarsest first.
         *
         * Spacing is not constant across them: a three-column page can afford a
         * wide gutter, and an eight-column one would be all gutter if it used the
         * same proportion. Each entry is tuned for its own density, which is what
         * "spaced out well at every interval" actually requires.
         */
        val PRESETS: List<GridPreset> = listOf(
            GridPreset(columns = 3, rows = 2, spacingDp = 34, paddingDp = 28, label = "Huge"),
            GridPreset(columns = 4, rows = 2, spacingDp = 30, paddingDp = 24, label = "Extra large"),
            GridPreset(columns = 4, rows = 3, spacingDp = 28, paddingDp = 22, label = "Large"),
            GridPreset(columns = 5, rows = 3, spacingDp = 26, paddingDp = 20, label = "Medium"),
            GridPreset(columns = 6, rows = 3, spacingDp = 23, paddingDp = 18, label = "Small"),
            GridPreset(columns = 6, rows = 4, spacingDp = 21, paddingDp = 16, label = "Extra small"),
            GridPreset(columns = 7, rows = 4, spacingDp = 19, paddingDp = 14, label = "Dense"),
            GridPreset(columns = 8, rows = 5, spacingDp = 17, paddingDp = 12, label = "Densest"),
        )

        /**
         * Divisors mapping the dp settings onto proportions of a cell.
         *
         * Chosen so the defaults land on the values that look right — an 18dp
         * setting becomes an 18%-of-a-cell gap, 14dp a 14% margin — which keeps
         * the settings screen's numbers meaningful after the change to
         * proportional spacing.
         */
        const val SPACING_DIVISOR = 100f
        const val PADDING_DIVISOR = 100f

        /** Never zero, so cells cannot touch; never so wide it starves the icon. */
        const val MIN_SPACING_FRACTION = 0.05f
        const val MAX_SPACING_FRACTION = 0.42f
        const val MIN_PADDING_FRACTION = 0.04f
        const val MAX_PADDING_FRACTION = 0.45f

        val DEFAULT = GridSpec()
    }
}

/**
 * A hand-tuned grid layout.
 *
 * Both the matrix and its spacing, together — the pair is what makes a size look
 * deliberate, and letting the user land on a column count with another size's
 * spacing is what made the grid look crowded at every setting but one.
 */
@Serializable
data class GridPreset(
    val columns: Int,
    val rows: Int,
    val spacingDp: Int,
    val paddingDp: Int,
    val label: String,
) {
    val cellsPerPage: Int get() = columns * rows

    /** Applies this layout, leaving the user's unrelated preferences alone. */
    fun applyTo(spec: GridSpec): GridSpec = spec.copy(
        columns = columns,
        rows = rows,
        spacingDp = spacingDp,
        paddingDp = paddingDp,
        // Presets define the size themselves, so any leftover manual zoom is
        // discarded rather than compounding with the new matrix.
        iconScale = 1f,
    ).coerced()

    companion object {
        /**
         * The preset closest to an arbitrary matrix.
         *
         * The matrix can be set directly from Settings, so it will not always sit
         * exactly on a preset; pinching from there should step to the neighbour of
         * wherever the user actually is rather than jumping to a default.
         */
        fun nearest(columns: Int, rows: Int): GridPreset {
            val cells = columns * rows
            return GridSpec.PRESETS.minBy { preset ->
                // Cell count first, then column count as the tie-break: two
                // presets with the same total can look very different.
                val cellDelta = abs(preset.cellsPerPage - cells)
                cellDelta * 10 + abs(preset.columns - columns)
            }
        }
    }
}

/** Mask applied to grid icons. */
@Serializable
enum class IconShape(val label: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    SQUIRCLE("Squircle"),
    CIRCLE("Circle"),
    HEXAGON("Hexagon"),
}

/**
 * A live query backing a smart folder.
 *
 * Evaluated by the library repository; the folder's contents update as the
 * library changes without any user maintenance.
 */
@Serializable
data class SmartQuery(
    val platformIds: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
    /** Include entries played within this many days; null disables the filter. */
    val playedWithinDays: Int? = null,
    val minRating: Int? = null,
    val releasedAfterYear: Int? = null,
    val releasedBeforeYear: Int? = null,
    val titleContains: String? = null,
    val sort: SortOrder = SortOrder.TITLE,
    val sortDescending: Boolean = false,
    /** Hard cap on results; keeps "Recently Played" style folders bounded. */
    val limit: Int? = null,
)

/** Sort fields available in the side menu and in smart folders. */
@Serializable
enum class SortOrder(val label: String) {
    TITLE("Title"),
    PLATFORM("Platform"),
    RELEASE_DATE("Release date"),
    RATING("Rating"),
    LAST_PLAYED("Last played"),
    PLAY_TIME("Play time"),
    LAUNCH_COUNT("Times played"),
    DATE_ADDED("Date added"),
    FILE_SIZE("Size"),
    MANUAL("Custom"),
}

/** Filters applied to the browsing surfaces. */
@Serializable
data class LibraryFilter(
    val platformIds: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val installedOnly: Boolean = false,
    val withAchievementsOnly: Boolean = false,
    val hideHidden: Boolean = true,
) {
    val isActive: Boolean
        get() = platformIds.isNotEmpty() || genres.isNotEmpty() || tags.isNotEmpty() ||
            favoritesOnly || installedOnly || withAchievementsOnly
}
