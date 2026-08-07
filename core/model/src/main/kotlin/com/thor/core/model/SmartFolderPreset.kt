package com.thor.core.model

/**
 * A smart folder worth having, ready made.
 *
 * [SmartQuery] has twelve fields and most useful folders use two of them. Asking
 * somebody to assemble "games I started and never finished" out of an unplayed
 * switch, a play-time sort and a cap is asking them to already know what a smart
 * folder is for — so these are the answers, and every field stays editable
 * afterwards.
 *
 * Each one is a query and a name together, because the name is part of the
 * answer: a folder called "Backlog" explains itself on the grid in a way that
 * "Unplayed, by title" never will.
 */
enum class SmartFolderPreset(
    val label: String,
    val description: String,
    val folderTitle: String,
    val query: SmartQuery,
) {
    CONTINUE_PLAYING(
        label = "Continue playing",
        description = "What you were last in the middle of",
        folderTitle = "Continue",
        query = SmartQuery(
            sort = SortOrder.LAST_PLAYED,
            sortDescending = true,
            // Capped, because this is a shelf rather than a list: a folder holding
            // everything you have ever touched, newest first, is the library again.
            limit = 12,
        ),
    ),

    BACKLOG(
        label = "Backlog",
        description = "Games you have never started",
        folderTitle = "Backlog",
        query = SmartQuery(unplayedOnly = true, sort = SortOrder.TITLE),
    ),

    FAVOURITES(
        label = "Favourites",
        description = "Everything you have starred",
        folderTitle = "Favourites",
        query = SmartQuery(favoritesOnly = true, sort = SortOrder.TITLE),
    ),

    HIGHLY_RATED(
        label = "Highly rated",
        description = "Rated 80 or better by the scrapers",
        folderTitle = "Best of",
        query = SmartQuery(minRating = 80, sort = SortOrder.RATING, sortDescending = true),
    ),

    RECENTLY_ADDED(
        label = "Recently added",
        description = "The newest arrivals in your library",
        folderTitle = "New",
        query = SmartQuery(sort = SortOrder.DATE_ADDED, sortDescending = true, limit = 20),
    ),

    MOST_PLAYED(
        label = "Most played",
        description = "Where the hours have actually gone",
        folderTitle = "Most played",
        query = SmartQuery(sort = SortOrder.PLAY_TIME, sortDescending = true, limit = 20),
    ),

    RETRO(
        label = "Before 2000",
        description = "The fifth generation and everything before it",
        folderTitle = "Retro",
        query = SmartQuery(releasedBeforeYear = 2000, sort = SortOrder.RELEASE_DATE),
    ),

    /**
     * The empty one, for somebody who wants to build a query rather than adjust one.
     *
     * Last, because a list whose first entry does nothing reads as a list of
     * nothing. It matches the whole library until a field is set, which is the
     * honest starting state and visibly so.
     */
    EVERYTHING(
        label = "Empty query",
        description = "Matches everything; set the fields yourself",
        folderTitle = "New smart folder",
        query = SmartQuery(),
    ),
}
