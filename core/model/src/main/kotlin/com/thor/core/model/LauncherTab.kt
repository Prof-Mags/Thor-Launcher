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

    companion object {
        val DEFAULT: LauncherTab = HOME

        /** Tabs in draw order. */
        val ORDERED: List<LauncherTab> = entries

        /**
         * The tab [steps] places from this one, clamped at both ends.
         *
         * Clamped rather than wrapped: a three-item bar that wraps means pressing
         * Left on the first tab lands on the last, which reads as the cursor
         * jumping rather than as running out of bar.
         */
        fun step(from: LauncherTab, steps: Int): LauncherTab =
            ORDERED[(ORDERED.indexOf(from) + steps).coerceIn(0, ORDERED.lastIndex)]
    }
}
