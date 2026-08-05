package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The launcher's top-level sections, as the bottom nav bar presents them.
 *
 * Ordered as they are drawn, left to right, with [HOME] deliberately in the
 * middle: it is the one the user returns to constantly, and on a handheld the
 * centre of the bottom edge is where a thumb already rests. Home is also the
 * default, so a launcher restored from cold opens on the grid rather than on
 * whichever section happened to be last.
 *
 * [STREAM] and [MOVIES] are declared with no content source yet — the sections
 * exist, are navigable and are themed, and each says plainly that nothing has
 * been connected to it. That is a deliberate step rather than a stub: the shape
 * of the launcher is being decided before what fills it, and a tab that quietly
 * showed an empty grid would be indistinguishable from one that was broken.
 */
@Serializable
enum class LauncherTab(val label: String) {
    STREAM("Stream"),
    HOME("Home"),
    MOVIES("Movies"),

    /**
     * Films and shows are one section with two catalogues, split in two only on a
     * television.
     *
     * They share a view model, a cursor and a player, so this is not a second
     * section — it is the Movies section with its media type chosen from the bar
     * instead of from inside itself. Which is right for a screen across a room,
     * where the top-level bar is the whole of the navigation and reaching a toggle
     * inside a section costs a journey; and wrong for the handheld, where the bar
     * is a strip along the bottom edge of a small panel and the toggle is already
     * on the screen you are looking at.
     *
     * So it is drawn by exactly one shell. See [visible].
     */
    SHOWS("Shows"),
    ;

    /** True for the section that owns the icon grid. */
    val isHome: Boolean get() = this == HOME

    /** True for a tab only Couch Mode draws. */
    val isCouchOnly: Boolean get() = this == SHOWS

    /**
     * True for either face of the films-and-shows section.
     *
     * Asked wherever the shell needs to know which *section* is open rather than
     * which tab is lit — routing a press, deciding what fills a panel, hiding the
     * bar for a playing film. All of those are true of Shows exactly when they are
     * true of Movies, because there is one section behind both.
     */
    val isMoviesSection: Boolean get() = this == MOVIES || this == SHOWS

    /** The media type this tab stands for, or null where it stands for no section. */
    val mediaType: MediaType?
        get() = when (this) {
            MOVIES -> MediaType.MOVIE
            SHOWS -> MediaType.SERIES
            HOME, STREAM -> null
        }

    /** The extension this section belongs to, or null for Home. */
    val extension: LauncherExtension?
        get() = when (this) {
            STREAM -> LauncherExtension.STREAM
            MOVIES, SHOWS -> LauncherExtension.MOVIES
            HOME -> null
        }

    companion object {
        val DEFAULT: LauncherTab = HOME

        /** The tab that stands for [type], for a bar that separates the two. */
        fun forMediaType(type: MediaType): LauncherTab =
            if (type == MediaType.SERIES) SHOWS else MOVIES

        /** Every tab, in draw order, whether or not it is available. */
        val ORDERED: List<LauncherTab> = entries

        /**
         * The tabs the bar actually draws.
         *
         * Home alone until an extension is enabled. A section the user has not
         * asked for is not shown greyed out or with an explanation inside it —
         * it is simply not there, because a bar advertising two things that
         * cannot be opened is worse than a bar with one thing on it.
         *
         * With only Home left the bar has nothing to switch between, and the
         * surfaces that draw it can leave it out entirely.
         *
         * @param couch whether the bar being drawn is Couch Mode's, which is the
         *   only one that separates films from shows. Everywhere else [SHOWS] is
         *   not a place: the section is Movies, and its media type is a control
         *   inside it.
         */
        fun visible(enabled: Set<String>, couch: Boolean = false): List<LauncherTab> =
            ORDERED.filter { tab ->
                if (tab.isCouchOnly && !couch) return@filter false
                tab.extension?.id?.let { it in enabled } ?: true
            }

        /**
         * The section a tab collapses to on a shell that does not draw it.
         *
         * Leaving couch mode while on Shows lands on Movies, which is the same
         * catalogue reached the other way, rather than throwing the user back to
         * Home for having been on a tab the handheld does not have.
         */
        fun landing(tab: LauncherTab, enabled: Set<String>, couch: Boolean = false): LauncherTab {
            val sections = visible(enabled, couch)
            if (tab in sections) return tab
            if (tab == SHOWS && MOVIES in sections) return MOVIES
            return DEFAULT
        }

        /**
         * The tab [steps] places from this one, clamped at both ends.
         *
         * Clamped rather than wrapped: a bar that wraps means pressing Left on
         * the first tab lands on the last, which reads as the cursor jumping
         * rather than as running out of bar.
         *
         * Walks the *visible* tabs, so a disabled section is not a dead stop the
         * cursor lands on halfway along.
         */
        fun step(
            from: LauncherTab,
            steps: Int,
            enabled: Set<String> = ALL_IDS,
            couch: Boolean = false,
        ): LauncherTab {
            val tabs = visible(enabled, couch)
            if (tabs.isEmpty()) return DEFAULT
            val current = tabs.indexOf(from).takeIf { it >= 0 } ?: tabs.indexOf(DEFAULT)
            return tabs[(current + steps).coerceIn(0, tabs.lastIndex)]
        }

        /** Every extension id, for callers that do not gate. */
        private val ALL_IDS: Set<String> =
            LauncherExtension.entries.mapTo(mutableSetOf(), LauncherExtension::id)
    }
}
