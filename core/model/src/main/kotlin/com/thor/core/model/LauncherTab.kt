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
    ;

    /** True for the section that owns the icon grid. */
    val isHome: Boolean get() = this == HOME

    /** The extension this section belongs to, or null for Home. */
    val extension: LauncherExtension?
        get() = when (this) {
            STREAM -> LauncherExtension.STREAM
            MOVIES -> LauncherExtension.MOVIES
            HOME -> null
        }

    companion object {
        val DEFAULT: LauncherTab = HOME

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
         */
        fun visible(enabled: Set<String>): List<LauncherTab> =
            ORDERED.filter { tab -> tab.extension?.id?.let { it in enabled } ?: true }

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
        fun step(from: LauncherTab, steps: Int, enabled: Set<String> = ALL_IDS): LauncherTab {
            val tabs = visible(enabled)
            if (tabs.isEmpty()) return DEFAULT
            val current = tabs.indexOf(from).takeIf { it >= 0 } ?: tabs.indexOf(DEFAULT)
            return tabs[(current + steps).coerceIn(0, tabs.lastIndex)]
        }

        /** Every extension id, for callers that do not gate. */
        private val ALL_IDS: Set<String> =
            LauncherExtension.entries.mapTo(mutableSetOf(), LauncherExtension::id)
    }
}
