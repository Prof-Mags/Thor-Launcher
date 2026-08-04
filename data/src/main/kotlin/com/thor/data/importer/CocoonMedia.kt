package com.thor.data.importer

import com.thor.core.model.ArtworkSet

/**
 * Which of Loki's artwork slots a Cocoon media folder fills.
 *
 * Cocoon files by purpose, one directory per class, which maps onto our slots
 * almost exactly — the only join is that both kinds of capture become
 * screenshots, gameplay first, since a title screen is the less interesting of
 * the two at panel size.
 */
enum class CocoonSlot(val folder: String) {
    ICON("icon"),
    HERO("hero"),
    LOGO("logo"),
    SCREENSHOT_GAMEPLAY("screenshot_gameplay"),
    SCREENSHOT_TITLE("screenshot_title"),
    ;

    companion object {
        fun of(folderName: String): CocoonSlot? =
            entries.firstOrNull { it.folder.equals(folderName, ignoreCase = true) }
    }
}

/**
 * Whether a media file belongs to add-on content rather than to the game.
 *
 * Worth its own check because the marker is bracketed like every other one, so
 * stripping brackets folds `Mario Kart 8 Deluxe [DLC Booster Course Pass]` into
 * `Mario Kart 8 Deluxe` — and a course-pass banner would then compete for the
 * game's own hero and screenshots, and could win them.
 *
 * A DLC has its own artwork because it is its own product. The launcher has no
 * entry for it, so there is nothing here for that artwork to belong to.
 */
fun isCocoonDlc(fileName: String): Boolean =
    BRACKETED.findAll(fileName).any { match ->
        match.value.trim('[', ']').trimStart().startsWith("DLC", ignoreCase = true)
    }

/** One imported file: which game it is for, and what it is a picture of. */
data class CocoonImage(
    /** The game's name with every marker stripped, for matching. */
    val title: String,
    val slot: CocoonSlot,
    /** Where the file is, in whatever form the caller can reopen it. */
    val source: String,
    /**
     * Byte length, which is how one picture is told from three copies of it.
     *
     * Zero when the source could not say, which is treated as "unknown" rather
     * than as a size — see [selectCocoonArtwork].
     */
    val sizeBytes: Long = 0L,
)

/**
 * Recovers a game's name from a Cocoon media filename.
 *
 * The names carry a great deal besides the title, and every part of it has to
 * come off before the title can be matched against a library that spells things
 * its own way. Taken from a real folder rather than guessed:
 *
 * ```
 * Batman Arkham Asylum [0100E870163CA800][v65536] (2).png
 * Batman Arkham Knight [0100ACD0163D0800][v327680] (26.88 GB).jpg
 * Mario Kart 8 Deluxe [DLC Booster Course Pass] [0100152000023001][v65536].png
 * The Legend of Zelda Breath of the Wild [NSZ].jpg
 * ```
 *
 * So: the extension, then every bracketed group — title id, version, format tag,
 * DLC marker — then a trailing size in parentheses, then a trailing duplicate
 * counter. The order matters, because a size and a counter can both be present
 * and the counter is always last.
 *
 * The counter is matched on digits alone, deliberately. `(2)` is Android's
 * "downloaded this twice" suffix and `(26.88 GB)` is part of the name Cocoon was
 * given; a rule loose enough to strip both would also strip `(Europe)` or
 * `(Disc 1)` from a library that uses them.
 */
fun cocoonTitleOf(fileName: String): String {
    var name = fileName.substringBeforeLast('.', fileName)

    // Repeatedly, because a counter can follow a size: "… (26.88 GB) (1)".
    var previous: String
    do {
        previous = name
        name = name.trim()
            .replace(DUPLICATE_COUNTER, "")
            .replace(SIZE_SUFFIX, "")
    } while (name != previous)

    return name
        .replace(BRACKETED, " ")
        .replace(WHITESPACE, " ")
        .trim()
}

/**
 * Picks the images to keep for one game, in the order the launcher wants them.
 *
 * Cocoon holds several files per class — a base game, its update, and Android's
 * duplicate copies — which after [cocoonTitleOf] all carry the same title. Only
 * the first of each class is taken: they are the same picture at that point, and
 * a strip filled with four copies of one screenshot is worse than one.
 *
 * Screenshots are capped at [ArtworkSet.MAX_SCREENSHOTS] because that is what the
 * panel shows, and copying more means writing files that nothing will ever open.
 */
fun selectCocoonArtwork(images: List<CocoonImage>): CocoonArtwork {
    fun first(slot: CocoonSlot): String? =
        images.firstOrNull { it.slot == slot }?.source

    /*
     * Deduplicated by size, not by path.
     *
     * The copies are separate files holding the same picture: a base game, its
     * update and Android's `(1)` and `(2)` downloads all reduce to one title,
     * and comparing their locations finds four different things. In a real
     * folder three of the four Batman Arkham Asylum captures are 272233 bytes to
     * the byte and the fourth is 100766 — one picture and a genuinely different
     * one, which is exactly the distinction wanted.
     *
     * A size of zero means the source would not say, and those are all kept:
     * treating "unknown" as a value would collapse every such file into one.
     */
    val screenshots = buildList {
        addAll(images.filter { it.slot == CocoonSlot.SCREENSHOT_GAMEPLAY })
        addAll(images.filter { it.slot == CocoonSlot.SCREENSHOT_TITLE })
    }
        .distinctBySize()
        .map(CocoonImage::source)
        .take(ArtworkSet.MAX_SCREENSHOTS)

    return CocoonArtwork(
        icon = first(CocoonSlot.ICON),
        hero = first(CocoonSlot.HERO),
        logo = first(CocoonSlot.LOGO),
        screenshots = screenshots,
    )
}

/** What one game's import resolves to, before the files are copied. */
data class CocoonArtwork(
    val icon: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val screenshots: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = icon == null && hero == null && logo == null && screenshots.isEmpty()

    /** Every file this import would copy, for a caller that wants the whole list. */
    val sources: List<String>
        get() = listOfNotNull(icon, hero, logo) + screenshots
}

/** `[0100E870163CA800]`, `[v65536]`, `[NSZ]`, `[DLC Booster Course Pass]`. */
private val BRACKETED = Regex("""\[[^\]]*]""")

/** `(26.88 GB)`, `(0.01 GB)` — the download size, written into the name. */
private val SIZE_SUFFIX = Regex("""\s*\(\s*[\d.]+\s*[KMGT]B\s*\)\s*$""", RegexOption.IGNORE_CASE)

/** `(1)`, `(2)` — Android's duplicate-download suffix. Digits only. */
private val DUPLICATE_COUNTER = Regex("""\s*\(\d+\)\s*$""")

private val WHITESPACE = Regex("""\s+""")

/**
 * Drops images whose byte length has already been seen.
 *
 * Length is a cheap stand-in for content and an exact one here, because the
 * duplicates are literally the same download saved twice rather than two
 * encodings of one picture. Two genuinely different captures agreeing to the
 * byte is possible and would cost one screenshot; reading every file to hash it
 * would cost the whole import.
 */
private fun List<CocoonImage>.distinctBySize(): List<CocoonImage> {
    val seen = mutableSetOf<Long>()
    return filter { image ->
        // Unknown lengths are never equal to anything, including each other.
        image.sizeBytes <= 0L || seen.add(image.sizeBytes)
    }
}
