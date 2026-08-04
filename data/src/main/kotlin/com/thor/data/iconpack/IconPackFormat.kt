package com.thor.data.iconpack

import kotlinx.serialization.Serializable

/**
 * The images a platform can have in a pack.
 *
 * Matched on filename without extension, so `icon.png`, `icon.jpg` and `ICON.PNG`
 * are all the icon. Packs are hand-assembled and the casing is not reliable.
 */
enum class ArtworkKind(val fileName: String) {
    ICON("icon.png"),
    HERO("hero.png"),
    LOGO("logo.png"),

    /**
     * A frame drawn over the games of this platform, not over the platform.
     *
     * The odd one out: the other three dress the *system* — its folder on the
     * grid, its backdrop on the information panel — while this one dresses every
     * game filed under it, so a shelf of mixed systems reads as which machine
     * each game is for. Carried on the platform all the same, because that is
     * where "which system is this" is answered once, rather than copied onto
     * several hundred game rows that every rescan would then have to keep in step.
     */
    OVERLAY("overlay.png"),
    ;

    companion object {
        private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** Whether this filename is an image at all. */
        fun isImage(fileName: String): Boolean =
            fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS

        /** The kind this filename represents, or null if it is not artwork. */
        fun of(fileName: String): ArtworkKind? {
            val lower = fileName.lowercase()
            if (!isImage(lower)) return null
            return when (lower.substringBeforeLast('.')) {
                "icon" -> ICON
                "hero", "banner", "background" -> HERO
                "logo", "wordmark" -> LOGO
                "overlay", "frame" -> OVERLAY
                else -> null
            }
        }
    }
}

/**
 * The icon styles a flat pack can offer for one system.
 *
 * A pack in the nested layout has one icon per platform and there is nothing to
 * choose. A flat one ships the same console under several treatments, and which
 * of them somebody wants is a matter of taste rather than of correctness — so
 * each becomes a pack in its own right, and they are switched between exactly
 * the way any two packs are.
 */
enum class IconStyle(val suffix: String, val displayName: String) {
    CONSOLE("console", "Console"),
    CONTROLLER("controller", "Controller"),
    PIXEL("pixel", "Pixel"),
    ;

    companion object {
        fun bySuffix(suffix: String): IconStyle? = entries.firstOrNull { it.suffix == suffix }
    }
}

/**
 * One image in a pack that files everything in a single directory.
 *
 * The nested layout says which platform an image belongs to with a directory —
 * `snes/icon.png` — and the flat one says it with the filename,
 * `snes-console.png`. Both are in use: the front-ends the nested layout came from
 * build a folder per system, while a set exported in one batch comes out flat,
 * which is the shape an artist's own output folder is in.
 */
sealed interface FlatArtwork {
    val slug: String

    /** `snes-console.png` — this system under one of the offered styles. */
    data class Icon(override val slug: String, val style: IconStyle) : FlatArtwork

    /** `snes-overlay.png` — shared by every style, because it dresses the games. */
    data class Overlay(override val slug: String) : FlatArtwork
}

/**
 * Reads `<slug>-<style>.png`.
 *
 * The slug is everything before the *last* hyphen, so `neo-geo-console.png`
 * belongs to `neo-geo` rather than to `neo`. Splitting on the first hyphen
 * instead would quietly file every multi-word system under a fragment of its own
 * name, and the result — a pack that imported cleanly and dressed nothing — gives
 * the user no way to tell that the naming was what went wrong.
 *
 * Returns null for anything that is not `<something>-<known style>.<image>`,
 * which is how a pack in the nested layout falls through to the directory-named
 * path rather than being half-read by this one.
 */
fun parseFlatArtworkName(fileName: String): FlatArtwork? {
    if (!ArtworkKind.isImage(fileName)) return null
    val stem = fileName.lowercase().substringBeforeLast('.')
    val separator = stem.lastIndexOf('-')
    if (separator <= 0 || separator == stem.lastIndex) return null

    val slug = stem.substring(0, separator)
    return when (val suffix = stem.substring(separator + 1)) {
        "overlay", "frame" -> FlatArtwork.Overlay(slug)
        else -> IconStyle.bySuffix(suffix)?.let { FlatArtwork.Icon(slug, it) }
    }
}

/**
 * A pack's `metadata.json`.
 *
 * Every field is optional. These files are written by whatever tool produced the
 * pack and the shape varies — the reference pack ships two of them, one with a
 * `settings` key and a `file_count`, one with a `preview_url` and a
 * `downloaded_at`, neither with the same set. Nothing here is load-bearing: a
 * pack with no metadata at all still imports, named after the file it came from.
 */
@Serializable
data class PackMetadata(
    val id: String? = null,
    val name: String? = null,
    val author: String? = null,
    val version: String? = null,
    val description: String? = null,
    val category: String? = null,
)
