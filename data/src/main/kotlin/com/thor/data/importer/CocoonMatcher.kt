package com.thor.data.importer

import com.thor.data.metadata.TitleMatcher

/** A game the launcher holds, reduced to what matching needs. */
data class ImportTarget(val entryId: String, val title: String, val fileName: String)

/** One resolved pairing: a game, and the Cocoon title its artwork came from. */
data class CocoonMatch(
    val entryId: String,
    val cocoonTitle: String,
    val confidence: Float,
)

/**
 * Pairs Cocoon's media with the games the launcher actually has.
 *
 * Word-for-word agreement is the one thing that cannot be assumed. Cocoon names
 * a file after the title it was given at download; the launcher names a game
 * after the file on disk. Between them sit apostrophes, colons, subtitles,
 * "The", region tags and the difference between `Yoshi's Crafted World` and
 * `Yoshis Crafted World`, and any of those breaks an exact comparison while
 * leaving the game perfectly recognisable.
 *
 * So each game takes the best-scoring Cocoon title above a floor, using the same
 * comparison the scrapers use — token overlap scaled by length similarity, which
 * is forgiving of punctuation and word order but not of a different game.
 *
 * Matching runs game-first rather than file-first, and each Cocoon title may
 * serve several games. That is deliberate: a library with `Batman Arkham City`
 * and `Batman Arkham City (Europe)` is two entries of one game, and both should
 * get the artwork.
 */
fun matchCocoonTitles(
    targets: List<ImportTarget>,
    cocoonTitles: Collection<String>,
    minimumConfidence: Float = MIN_IMPORT_CONFIDENCE,
): List<CocoonMatch> {
    if (cocoonTitles.isEmpty()) return emptyList()

    /*
     * Apostrophes are removed rather than treated as separators.
     *
     * The comparison tokenises on punctuation, so `Yoshi's` becomes `yoshi` and
     * `s` while `Yoshis` stays one word — two names a person reads as identical
     * scoring as barely related. Deleting the apostrophe makes them the same
     * token, which is what a reader does with it anyway.
     */
    val candidates = cocoonTitles.map { it to it.withoutApostrophes() }

    return targets.mapNotNull { target ->
        /*
         * Both names are tried, because they fail in different ways. The display
         * title is what a human would compare, but a library scanned from
         * unhelpfully named files has titles like `smw` — where the filename,
         * which often carries the full name, is the better key. Whichever scores
         * higher wins, and a bad guess on one does not spoil the other.
         */
        val title = target.title.withoutApostrophes()
        val fileName = target.fileName
            .substringBeforeLast('.', target.fileName)
            .withoutApostrophes()

        val best = candidates
            .map { (original, comparable) ->
                val byTitle = TitleMatcher.confidence(title, comparable)
                val byFile = TitleMatcher.confidence(fileName, comparable)
                original to maxOf(byTitle, byFile)
            }
            .maxByOrNull { it.second }
            ?: return@mapNotNull null

        val (matched, confidence) = best
        if (confidence < minimumConfidence) return@mapNotNull null

        CocoonMatch(entryId = target.entryId, cocoonTitle = matched, confidence = confidence)
    }
}

/**
 * The floor for accepting an import match.
 *
 * Higher than the scrapers' own, and for a different reason. A scraper that
 * guesses wrong wastes a request; an import that guesses wrong writes another
 * game's picture into the library and leaves it there. The cost of a miss is a
 * game with no artwork, which is where it started.
 */
const val MIN_IMPORT_CONFIDENCE = 0.6f

/** Both kinds, since a filename and a store listing rarely use the same one. */
private fun String.withoutApostrophes(): String = replace("'", "").replace("\u2019", "")
