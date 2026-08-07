package com.thor.feature.home.cards

import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform

/**
 * One system, as the card layout needs it.
 *
 * A card is a claim about a whole system rather than about one game, so every
 * number on it is an aggregate and none of them can be read off a single entry.
 * Computing them once, here, is also what keeps them out of the composable: a
 * card is redrawn on every step through the flow, and folding a library's play
 * time on each frame would be work proportional to the library for a number that
 * only changes when a game is launched.
 */
data class PlatformCard(
    val platform: Platform,
    val gameCount: Int,
    /** Games in this system that have never been launched. */
    val unplayedCount: Int,
    val totalPlayMillis: Long,
    /**
     * Artwork borrowed from a game, for a system the pack did not cover.
     *
     * The last thing played, because a system with no artwork of its own is
     * better represented by something recognisable from it than by a placeholder
     * — and the most recently played game is the one the user will recognise.
     */
    val previewUri: String?,
) {
    val hasBeenPlayed: Boolean get() = totalPlayMillis > 0L
}

/**
 * The systems the card flow steps through, in the order it steps through them.
 *
 * Only systems that actually have games. An empty system is a card that opens
 * onto nothing, which is worse here than on the grid: the grid can put an empty
 * folder beside a full one and the user can see the difference at a glance, while
 * a flow shows one card at a time and an empty one costs a press to discover and
 * another to leave.
 *
 * Hidden games do not count toward anything. A system whose games are *all*
 * hidden therefore has no card at all, which is the same rule the grid's platform
 * folders follow and the only one that matches what the user asked for when they
 * hid them.
 *
 * Ordered by the platform's own sort index and then by name, which is the order
 * every other list of systems in the launcher uses.
 */
fun platformCards(
    games: Collection<GameEntry>,
    platformsById: Map<String, Platform>,
): List<PlatformCard> = games
    .asSequence()
    .filterNot(GridEntry::isHidden)
    .groupBy(GameEntry::platformId)
    .mapNotNull { (platformId, entries) ->
        val platform = platformsById[platformId] ?: return@mapNotNull null
        PlatformCard(
            platform = platform,
            gameCount = entries.size,
            unplayedCount = entries.count { !it.stats.hasBeenPlayed },
            totalPlayMillis = entries.sumOf { it.stats.totalPlayMillis },
            previewUri = entries
                .filter { it.stats.lastPlayedEpochMs != null }
                .maxByOrNull { it.stats.lastPlayedEpochMs ?: Long.MIN_VALUE }
                ?.metadata
                ?.artwork
                ?.let { it.backgroundImage ?: it.boxArt },
        )
    }
    .sortedWith(compareBy<PlatformCard> { it.platform.sortIndex }.thenBy { it.platform.name })

/**
 * The card a step in [delta] lands on, wrapping at both ends.
 *
 * Wrapping rather than stopping, and that is the whole reason this is a function
 * worth testing. A flow shows one system at a time, so reaching the last of
 * twenty-five costs twenty-four presses if the ends are walls — and the systems
 * a user visits least are exactly the ones that get filed at the far end. Wrapping
 * halves the worst case and costs nothing, because a flow has no edge the user can
 * see and therefore no edge they expect to hit.
 *
 * Returns 0 for an empty list rather than failing, so a library that is still
 * scanning steps harmlessly in place.
 */
fun stepCard(current: Int, delta: Int, count: Int): Int {
    if (count <= 0) return 0
    return (current + delta).mod(count)
}

/**
 * Play time, in the shortest form that is still true.
 *
 * Minutes below an hour, because "0h" on a system with forty minutes on it reads
 * as never played. Whole hours above it, because the minutes stop being
 * interesting once there are hours and a card is glanced at rather than read.
 */
fun formatPlayTime(totalMillis: Long): String? {
    if (totalMillis <= 0L) return null
    val minutes = totalMillis / MILLIS_PER_MINUTE
    if (minutes < MINUTES_PER_HOUR) return "${minutes.coerceAtLeast(1)}m played"
    return "${minutes / MINUTES_PER_HOUR}h played"
}

/** "142 games", and "1 game" — a card is prose, and prose agrees with itself. */
fun formatGameCount(count: Int): String = if (count == 1) "1 game" else "$count games"

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
