package com.thor.data.iconpack

import kotlinx.serialization.Serializable

/**
 * The three images a platform folder can hold.
 *
 * Matched on filename without extension, so `icon.png`, `icon.jpg` and `ICON.PNG`
 * are all the icon. Packs are hand-assembled and the casing is not reliable.
 */
enum class ArtworkKind(val fileName: String) {
    ICON("icon.png"),
    HERO("hero.png"),
    LOGO("logo.png"),
    ;

    companion object {
        private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** The kind this filename represents, or null if it is not artwork. */
        fun of(fileName: String): ArtworkKind? {
            val lower = fileName.lowercase()
            val extension = lower.substringAfterLast('.', "")
            if (extension !in EXTENSIONS) return null
            return when (lower.substringBeforeLast('.')) {
                "icon" -> ICON
                "hero", "banner", "background" -> HERO
                "logo", "wordmark" -> LOGO
                else -> null
            }
        }
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
