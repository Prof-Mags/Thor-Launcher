package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A widget the launcher draws itself, out of its own library.
 *
 * Distinct from an Android app widget, which is a view another process builds
 * and this one only hosts. These know what a game is — they can show what you
 * were last playing, how long you have played it, and start it from the cell —
 * none of which an app widget can do, because nothing outside the launcher has
 * ever heard of the library.
 *
 * They are also the ones worth having on a games handheld. The platform's widget
 * catalogue is weather, calendars and news; the grid on this device wants the
 * shelf it is standing next to.
 *
 * The default size is the one it is placed at and is only a starting point —
 * every one of these can be resized afterwards like any other widget, so the
 * numbers here are chosen for what reads well rather than for what is required.
 */
@Serializable
enum class LauncherWidget(
    val title: String,
    val description: String,
    val defaultColumns: Int,
    val defaultRows: Int,
) {
    /**
     * The obvious one, and the reason this exists.
     *
     * A grid is an arrangement the user made once; what they want on any given
     * evening is whatever they were in the middle of. That is a question only
     * the launcher can answer.
     */
    CONTINUE_PLAYING(
        title = "Continue playing",
        description = "The last few games you played, ready to start",
        defaultColumns = 3,
        defaultRows = 1,
    ),

    /**
     * The last game, given room to be a picture rather than an icon.
     *
     * Wider and taller than the rest because its content is artwork — at one
     * cell it would be an icon that already exists elsewhere on the grid.
     */
    SPOTLIGHT(
        title = "Spotlight",
        description = "The game you played last, with its artwork",
        defaultColumns = 3,
        defaultRows = 2,
    ),

    FAVOURITES(
        title = "Favourites",
        description = "The games you have starred",
        defaultColumns = 3,
        defaultRows = 1,
    ),

    BACKLOG(
        title = "Backlog",
        description = "Games you own and have never started",
        defaultColumns = 3,
        defaultRows = 1,
    ),

    MOST_PLAYED(
        title = "Most played",
        description = "Where the hours have actually gone",
        defaultColumns = 3,
        defaultRows = 1,
    ),

    /**
     * One game to try, chosen for you.
     *
     * The answer to a full backlog, which is a list nobody reads. It prefers
     * something unplayed and falls back to the whole library when there is
     * nothing left to start, and it holds its choice for the day rather than
     * re-rolling on every recomposition — a suggestion that changes as the cursor
     * moves past it is not one anybody can act on.
     */
    SURPRISE(
        title = "Surprise me",
        description = "One game to try, with its artwork",
        defaultColumns = 3,
        defaultRows = 2,
    ),

    LIBRARY(
        title = "Library",
        description = "How many games you have, and how long you have played",
        defaultColumns = 2,
        defaultRows = 1,
    ),

    CLOCK(
        title = "Clock",
        description = "The time and the date",
        defaultColumns = 2,
        defaultRows = 1,
    ),
    ;

    val span: CellSpan get() = CellSpan(columns = defaultColumns, rows = defaultRows)

    companion object {
        /** Parsed back from what the database stored; null for anything unknown. */
        fun from(id: String?): LauncherWidget? = entries.firstOrNull { it.name == id }
    }
}
