package com.thor.core.common.text

/**
 * Splitting prose where a reader would.
 *
 * Game synopses arrive at wildly different lengths — Wikipedia writes a
 * paragraph, IGDB writes a marketing blurb, ScreenScraper sometimes writes an
 * essay — and every surface that shows one has a fixed amount of room. Cutting
 * at a character count leaves "...the princess is kidnapped by Bows", which
 * reads as a rendering fault. Cutting at a sentence leaves something shorter
 * that is still true and still finished.
 */

/**
 * Full stops that are never the end of a sentence.
 *
 * Only the ones whose next word is a proper noun, which is what makes them
 * undecidable from the capital letter alone: a title before a name, "vs." in a
 * fighting game's subtitle, "Vol." and "Pt." before a numeral.
 *
 * "Inc.", "Ltd." and "etc." are deliberately absent. They read like members of
 * this set and are not: each one ends a sentence at least as often as it sits
 * inside one, and listing them welded "…published by Capcom Inc." to whatever
 * followed it. Their mid-sentence use is caught by the lower-case rule below
 * instead — "Capcom Inc. was founded" continues, "Capcom Inc. The sequel"
 * does not — which decides each occurrence on its own evidence rather than
 * ruling out the whole word.
 */
private val ABBREVIATIONS = setOf(
    "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr",
    "vs", "e.g", "i.e", "no", "vol", "pt", "approx", "feat", "ft", "ca",
)

/** A terminator, any closing quote or bracket after it, then the gap. */
private val SENTENCE_BREAK = Regex("""[.!?]["'’”)\]]*\s+""")

/** The word carrying the full stop, lower-cased and stripped of punctuation. */
private fun String.wordBefore(end: Int): String =
    substring(0, end).takeLastWhile { !it.isWhitespace() }.trimEnd('.', '!', '?').lowercase()

/**
 * Whole sentences, each keeping its own terminating punctuation.
 *
 * A break is rejected when the word before it is a known abbreviation, when it
 * is a single letter — "J. R. R. Tolkien" is one name, not three sentences —
 * or when what follows opens in lower case, which in practice means the stop
 * belonged to a version number or a file extension rather than to a sentence.
 *
 * Text with no terminator at all comes back as a single sentence rather than as
 * nothing, so a caller can always take the first one.
 */
fun String.splitSentences(): List<String> {
    val text = trim()
    if (text.isEmpty()) return emptyList()

    val sentences = mutableListOf<String>()
    var start = 0

    for (match in SENTENCE_BREAK.findAll(text)) {
        val stop = match.range.first
        val word = text.wordBefore(stop)
        if (word in ABBREVIATIONS || (word.length == 1 && word[0].isLetter())) continue

        val next = text.getOrNull(match.range.last + 1)
        if (next != null && next.isLowerCase()) continue

        sentences += text.substring(start, match.range.last + 1).trim()
        start = match.range.last + 1
    }

    if (start < text.length) sentences += text.substring(start).trim()
    return sentences.filter(String::isNotEmpty)
}

/**
 * The longest run of whole sentences fitting [maxChars].
 *
 * The first sentence is always kept even when it alone is over the limit: a
 * synopsis whose opening sentence is two hundred characters is unusual but
 * returning nothing for it would be worse than returning it, and nothing
 * downstream is prepared for a description that vanished because it was too
 * long.
 */
fun String.truncateToSentences(maxChars: Int): String {
    val text = trim()
    if (text.length <= maxChars) return text

    val sentences = text.splitSentences()
    if (sentences.isEmpty()) return text

    val kept = StringBuilder(sentences.first())
    for (sentence in sentences.drop(1)) {
        if (kept.length + 1 + sentence.length > maxChars) break
        kept.append(' ').append(sentence)
    }
    return kept.toString()
}
