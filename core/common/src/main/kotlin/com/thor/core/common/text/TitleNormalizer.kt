package com.thor.core.common.text

import java.util.Locale

/**
 * Turns raw ROM file names into human titles and into the normalised keys the
 * library uses for sorting, searching and duplicate detection.
 *
 * ROM sets are full of decorations — `(USA)`, `[!]`, `(Rev A)`, `(Disc 1)` —
 * and matching a scraper against them verbatim fails constantly, so every
 * lookup goes through here first.
 */
object TitleNormalizer {

    /** Bracketed groups: `(USA, Europe)`, `[!]`, `{v1.1}`. */
    private val DECORATION = Regex("""[\(\[\{][^)\]\}]*[\)\]\}]""")

    /** Separators used in place of spaces in dump names. */
    private val SEPARATORS = Regex("""[._]+""")

    private val WHITESPACE = Regex("""\s+""")

    private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

    /** Region hints, in the order scrapers usually rank them. */
    private val REGION_TOKENS = mapOf(
        "usa" to "USA", "u" to "USA", "ntsc-u" to "USA",
        "europe" to "Europe", "e" to "Europe", "pal" to "Europe",
        "japan" to "Japan", "j" to "Japan", "ntsc-j" to "Japan",
        "world" to "World", "w" to "World",
        "korea" to "Korea", "china" to "China", "brazil" to "Brazil",
        "australia" to "Australia", "spain" to "Spain", "france" to "France",
        "germany" to "Germany", "italy" to "Italy",
    )

    private val DISC_PATTERN = Regex("""dis[ck]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val REVISION_PATTERN = Regex("""rev\s*([0-9a-z]+)""", RegexOption.IGNORE_CASE)

    /**
     * `"Legend of Zelda, The - Ocarina of Time (USA) (Rev A).z64"` ->
     * `"The Legend of Zelda - Ocarina of Time"`.
     */
    fun displayTitle(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        val cleaned = withoutExtension
            .replace(DECORATION, " ")
            .replace(SEPARATORS, " ")
            .replace(WHITESPACE, " ")
            .trim()
        return restoreLeadingArticle(cleaned).ifEmpty { withoutExtension }
    }

    /**
     * Key used for sorting and fuzzy matching: lowercase, no punctuation, no
     * leading article.
     */
    fun sortKey(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
            .replace(DECORATION, " ")
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(WHITESPACE, " ")
            .trim()
        return LEADING_ARTICLES.firstOrNull { lower.startsWith(it) }
            ?.let { lower.removePrefix(it).trim() }
            ?: lower
    }

    /** Extracts the region marker from a dump name, if present. */
    fun region(fileName: String): String? =
        DECORATION.findAll(fileName)
            .flatMap { match -> match.value.trim('(', ')', '[', ']', '{', '}').split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }
            .firstNotNullOfOrNull { REGION_TOKENS[it] }

    /** Disc number for multi-disc sets, 1-based. */
    fun discNumber(fileName: String): Int? =
        DISC_PATTERN.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /** Revision marker, e.g. `"A"` from `(Rev A)`. */
    fun revision(fileName: String): String? =
        REVISION_PATTERN.find(fileName)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)

    /**
     * Builds the label shown in the detail panel's version picker, e.g.
     * `"USA · Rev A · Disc 2"`.
     */
    fun versionLabel(fileName: String): String {
        val parts = listOfNotNull(
            region(fileName),
            revision(fileName)?.let { "Rev $it" },
            discNumber(fileName)?.let { "Disc $it" },
        )
        return if (parts.isEmpty()) "Standard" else parts.joinToString(" · ")
    }

    /**
     * `"Legend of Zelda, The"` -> `"The Legend of Zelda"`.
     *
     * No-Intro style sets move the article to the end; scrapers expect it back
     * at the front.
     */
    private fun restoreLeadingArticle(title: String): String {
        val separator = title.lastIndexOf(", ")
        if (separator <= 0) return title
        val tail = title.substring(separator + 2)
        val article = tail.substringBefore(' ').trim()
        val isArticle = LEADING_ARTICLES.any { it.trim().equals(article, ignoreCase = true) }
        if (!isArticle) return title
        val head = title.substring(0, separator)
        val remainder = tail.removePrefix(article).trim()
        return buildString {
            append(article)
            append(' ')
            append(head)
            if (remainder.isNotEmpty()) {
                append(' ')
                append(remainder)
            }
        }
    }
}
