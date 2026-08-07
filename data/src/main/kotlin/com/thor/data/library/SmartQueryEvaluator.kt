package com.thor.data.library

import com.thor.core.common.text.TitleNormalizer
import com.thor.core.model.AppEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.SmartQuery
import com.thor.core.model.SortOrder

/**
 * What a [SmartQuery] currently matches.
 *
 * Pure, and over a collection already in hand rather than over the database. That
 * is what lets the *same* rule answer two questions that were previously answered
 * in one place and asked in neither: the repository's one-shot evaluation, and the
 * grid's live one when a smart folder is opened.
 *
 * The second of those is the reason this exists. `LibraryRepository` held a
 * complete and correct evaluator that nothing ever called, and the grid resolved a
 * folder's contents from its stored `childIds` — which a smart folder does not
 * have, because its whole point is not to store them. So a smart folder could be
 * created, placed and opened, and was always empty. Evaluating in memory against
 * the entry map the grid already observes also means the contents follow the
 * library: favourite a game and it appears in the Favourites folder without
 * anything being told to refresh.
 */
object SmartQueryEvaluator {

    /**
     * @param entries every entry in the library, not only the placed ones. A smart
     *   folder matches against the whole library by definition — a game filed
     *   inside a platform folder is still a game the query should see.
     * @param now the clock, passed rather than read, so a caller inside a state
     *   transform is not doing IO and a test can pin it.
     */
    fun evaluate(
        entries: Collection<GridEntry>,
        query: SmartQuery,
        now: Long,
    ): List<GridEntry> = entries.asSequence()
        .filterIsInstance<GameEntry>()
        .filter { query.platformIds.isEmpty() || it.platformId in query.platformIds }
        .filter { game -> query.genres.isEmpty() || game.metadata.genres.any { it in query.genres } }
        .filter { query.tags.isEmpty() || it.tags.any { tag -> tag in query.tags } }
        .filter { !query.favoritesOnly || it.isFavorite }
        .filter { !query.unplayedOnly || !it.stats.hasBeenPlayed }
        .filter { game ->
            val within = query.playedWithinDays ?: return@filter true
            // A game never played matches no recency window, rather than matching
            // every one of them by having no date to fall outside.
            val last = game.stats.lastPlayedEpochMs ?: return@filter false
            now - last <= within * MILLIS_PER_DAY
        }
        .filter { game -> query.minRating?.let { (game.metadata.rating ?: 0) >= it } ?: true }
        .filter { game ->
            query.releasedAfterYear?.let { (game.metadata.releaseYear ?: Int.MIN_VALUE) >= it }
                ?: true
        }
        .filter { game ->
            query.releasedBeforeYear?.let { (game.metadata.releaseYear ?: Int.MAX_VALUE) <= it }
                ?: true
        }
        .filter { game ->
            query.titleContains?.let { needle ->
                // Both sides through the same normaliser, so "final fantasy"
                // matches "Final Fantasy VII" and punctuation never decides.
                game.sortTitle.contains(TitleNormalizer.sortKey(needle))
            } ?: true
        }
        .toList()
        // The launcher's one sort, not a second one written to match it. Writing a
        // parallel comparator here is how a smart folder set to "most played" and
        // the grid set to "most played" come to disagree about which is first.
        .sortedWith(gridEntryComparator(query.sort, query.sortDescending))
        .let { results -> query.limit?.let(results::take) ?: results }

    private const val MILLIS_PER_DAY = 86_400_000L
}

/**
 * Comparator matching a [SortOrder], for anything on the grid.
 *
 * Top-level rather than a method, because two callers need it and only one of them
 * has a repository: the library's own sorting, and [SmartQueryEvaluator], which is
 * pure by design so the grid can run it inside a state transform.
 *
 * Several branches sort descending by default — highest rating, most recently
 * played, largest file — because that is what the label means. `descending` then
 * reverses whatever the natural order was, rather than imposing one.
 */
fun gridEntryComparator(order: SortOrder, descending: Boolean): Comparator<GridEntry> {
    val base: Comparator<GridEntry> = when (order) {
        SortOrder.TITLE, SortOrder.MANUAL -> compareBy { it.sortTitle }
        SortOrder.PLATFORM -> compareBy<GridEntry> { (it as? GameEntry)?.platformId ?: "" }
            .thenBy { it.sortTitle }

        SortOrder.RELEASE_DATE -> compareBy<GridEntry> {
            (it as? GameEntry)?.metadata?.releaseYear ?: Int.MAX_VALUE
        }.thenBy { it.sortTitle }

        SortOrder.RATING -> compareByDescending<GridEntry> {
            (it as? GameEntry)?.metadata?.rating ?: -1
        }.thenBy { it.sortTitle }

        SortOrder.LAST_PLAYED -> compareByDescending<GridEntry> { entry ->
            when (entry) {
                is GameEntry -> entry.stats.lastPlayedEpochMs ?: 0L
                is AppEntry -> entry.lastPlayedEpochMs ?: 0L
                else -> 0L
            }
        }.thenBy { it.sortTitle }

        SortOrder.PLAY_TIME -> compareByDescending<GridEntry> {
            (it as? GameEntry)?.stats?.totalPlayMillis ?: 0L
        }.thenBy { it.sortTitle }

        SortOrder.LAUNCH_COUNT -> compareByDescending<GridEntry> { entry ->
            when (entry) {
                is GameEntry -> entry.stats.launchCount
                is AppEntry -> entry.launchCount
                else -> 0
            }
        }.thenBy { it.sortTitle }

        SortOrder.DATE_ADDED -> compareByDescending<GridEntry> {
            (it as? AppEntry)?.installedAtEpochMs ?: 0L
        }.thenBy { it.sortTitle }

        SortOrder.FILE_SIZE -> compareByDescending<GridEntry> {
            (it as? GameEntry)?.fileSizeBytes ?: 0L
        }.thenBy { it.sortTitle }
    }
    return if (descending) base.reversed() else base
}
