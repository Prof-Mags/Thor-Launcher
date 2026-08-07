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
    val favouriteCount: Int,
    val totalPlayMillis: Long,
    /** When this system was last touched, for "Last played 2 days ago". */
    val lastPlayedEpochMs: Long?,
    /**
     * Artwork borrowed from a game, for a system the pack did not cover.
     *
     * The last thing played, because a system with no artwork of its own is
     * better represented by something recognisable from it than by a placeholder
     * — and the most recently played game is the one the user will recognise.
     */
    val previewUri: String?,
    /**
     * A few covers from inside, so the card answers "what is in here".
     *
     * The one thing a count cannot say. "142 games" is the same sentence for a
     * shelf of favourites and a shelf of things never opened, and on a flow that
     * shows one system at a time the user is deciding whether to press A on the
     * strength of whatever the card manages to say.
     *
     * Most recently played first, falling back to whatever has cover art at all
     * for a system nothing has been launched on — a new system should still show
     * its contents rather than four empty frames.
     */
    val recentArtwork: List<String>,
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

        /*
         * Played first, most recent at the front, then everything else by title.
         *
         * One ordering serving both the borrowed backdrop and the cover strip, so
         * the picture behind the card and the first cover on it are the same game
         * — two different orderings would put a system's most-played title in the
         * backdrop and something arbitrary beside it, which reads as a mistake
         * rather than as two rules.
         */
        val ranked = entries.sortedWith(
            compareByDescending<GameEntry> { it.stats.lastPlayedEpochMs ?: Long.MIN_VALUE }
                .thenByDescending { it.stats.totalPlayMillis }
                .thenBy(GameEntry::sortTitle),
        )
        val played = ranked.filter { it.stats.lastPlayedEpochMs != null }

        PlatformCard(
            platform = platform,
            gameCount = entries.size,
            unplayedCount = entries.count { !it.stats.hasBeenPlayed },
            favouriteCount = entries.count(GameEntry::isFavorite),
            totalPlayMillis = entries.sumOf { it.stats.totalPlayMillis },
            lastPlayedEpochMs = played.firstOrNull()?.stats?.lastPlayedEpochMs,
            previewUri = played.firstOrNull()
                ?.metadata
                ?.artwork
                ?.let { it.backgroundImage ?: it.boxArt },
            // Falls back to the whole ranked list, not just the played half: a
            // system nothing has been launched on should still show its covers
            // rather than an empty row where the covers go.
            recentArtwork = ranked
                .asSequence()
                .mapNotNull { game ->
                    game.metadata.artwork.let { it.boxArt ?: it.cappedScreenshots.firstOrNull() }
                }
                .distinct()
                .take(COVER_STRIP_COUNT)
                .toList(),
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

/**
 * The system's identity, as one line: who made it, when, and what it is called.
 *
 * Separate from the statistics line because it says something different in kind.
 * Everything else on the card is about what the *user* has done with the system;
 * this is about the machine, and it is the same on a library of four hundred games
 * and on one with none.
 *
 * Each part is dropped when it is blank rather than printed as an empty gap — a
 * user-added platform may have no manufacturer and no year, and "· · N64" is
 * worse than "N64".
 */
fun formatPlatformIdentity(
    manufacturer: String,
    releaseYear: Int?,
    shortName: String,
    name: String,
): String = listOfNotNull(
    manufacturer.takeIf(String::isNotBlank),
    releaseYear?.toString(),
    // The short name only when it adds something. On a system whose full name is
    // already short the two are the same string, and printing "N64 · N64" reads
    // as a bug.
    shortName.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
).joinToString(SEPARATOR)

/**
 * When the system was last touched, in the coarsest unit that is still useful.
 *
 * Coarse on purpose: the question a card answers is "have I been here recently",
 * not "when exactly". A timestamp would be more precise and less informative, and
 * it would also be the only thing on the card that changed every minute.
 *
 * [nowMs] is passed rather than read, so the boundaries can be tested — the
 * interesting cases are all *at* a boundary, and a function that reads the clock
 * cannot be asked about them.
 */
fun formatLastPlayed(lastPlayedEpochMs: Long?, nowMs: Long): String? {
    if (lastPlayedEpochMs == null) return null
    val elapsed = nowMs - lastPlayedEpochMs
    // A clock that has gone backwards — a timezone change, a manual set, a
    // restored backup — reads as "just now" rather than as a negative age.
    if (elapsed < MILLIS_PER_HOUR) return "Played recently"
    if (elapsed < MILLIS_PER_DAY) return "Played today"
    val days = elapsed / MILLIS_PER_DAY
    if (days == 1L) return "Played yesterday"
    if (days < DAYS_PER_WEEK) return "Played $days days ago"
    if (days < DAYS_PER_MONTH) {
        val weeks = days / DAYS_PER_WEEK
        return if (weeks == 1L) "Played last week" else "Played $weeks weeks ago"
    }
    if (days < DAYS_PER_YEAR) {
        val months = days / DAYS_PER_MONTH
        return if (months == 1L) "Played last month" else "Played $months months ago"
    }
    val years = days / DAYS_PER_YEAR
    return if (years == 1L) "Played last year" else "Played $years years ago"
}

/** How many covers the strip along the card can show. */
const val COVER_STRIP_COUNT = 4

/** The dot every one of these lines is joined with, in one place. */
const val SEPARATOR = "  ·  "

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val DAYS_PER_WEEK = 7L

/** Approximate, and deliberately so: a card says "3 months ago", not a date. */
private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L
