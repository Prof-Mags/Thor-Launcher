package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * Everything the top screen needs to render a rich detail panel.
 *
 * Metadata is merged from multiple providers (see the `data` module's
 * `MetadataAggregator`), so every field is optional and carries no meaning when
 * absent. [providerSources] records which provider won each field so the
 * metadata editor can show provenance and let the user pin a value.
 */
@Serializable
data class GameMetadata(
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    /** Normalised 0..100 critic/user rating. */
    val rating: Int? = null,
    val players: String? = null,
    val languages: List<String> = emptyList(),
    val region: String? = null,
    /** Typical completion time in minutes, when a provider reports it. */
    val completionMinutes: Int? = null,
    /** Controller/input support summary, e.g. "Gamepad, Touch". */
    val inputSupport: List<String> = emptyList(),
    val artwork: ArtworkSet = ArtworkSet.EMPTY,
    /** Locally cached manual, if one was downloaded. */
    val manualUri: String? = null,
    val achievements: AchievementSummary? = null,
    /** Field name -> provider id that supplied the winning value. */
    val providerSources: Map<String, String> = emptyMap(),
    /** Fields the user edited by hand; these are never overwritten by a rescan. */
    val lockedFields: Set<String> = emptySet(),
    val lastScrapedEpochMs: Long? = null,
) {
    companion object {
        val EMPTY = GameMetadata()

        // Field keys used by both providerSources and lockedFields.
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_GENRES = "genres"
        const val FIELD_DEVELOPER = "developer"
        const val FIELD_PUBLISHER = "publisher"
        const val FIELD_RELEASE_DATE = "releaseDate"
        const val FIELD_RATING = "rating"
        const val FIELD_PLAYERS = "players"
        const val FIELD_ARTWORK = "artwork"
    }
}

/**
 * The set of images/videos associated with an entry.
 *
 * Each value is a URI: `https://` before download, `file://` once the artwork
 * cache has pulled it local. The UI never cares which.
 */
@Serializable
data class ArtworkSet(
    /** Vertical box art — the grid icon and the detail panel's cover. */
    val boxArt: String? = null,
    /** Wide key art used as the top-screen background. */
    val hero: String? = null,
    /** Transparent title treatment overlaid on the hero. */
    val logo: String? = null,
    /** Square icon, preferred for grid cells in "icon" display mode. */
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    /** Short looping gameplay clip shown after a dwell delay. */
    val videoUri: String? = null,
    /** Dominant colour sampled from the hero, cached to avoid re-decoding. */
    val dominantArgb: Long? = null,
) {
    companion object {
        val EMPTY = ArtworkSet()

        /**
         * How many screenshots are ever kept for one game.
         *
         * Three, alongside the single cover. Providers will happily return
         * dozens, and every one is a download, a disk-cache entry and another
         * step in the information screen's slideshow — across a large ROM
         * library that is the difference between tens and hundreds of megabytes.
         * Capped at the point of ingestion rather than trimmed at display time,
         * so the space is never spent in the first place.
         */
        const val MAX_SCREENSHOTS = 3
    }

    /**
     * Screenshots, never more than [MAX_SCREENSHOTS].
     *
     * Enforced on read as well as on write: a library scraped by an earlier
     * build already has over-long lists persisted, and this keeps the slideshow
     * and the editor bounded without needing a migration to rewrite them.
     */
    val cappedScreenshots: List<String> get() = screenshots.take(MAX_SCREENSHOTS)

    /**
     * The image a square grid cell should use.
     *
     * Square sources only, in descending order of how well they fill a 1:1 cell:
     * a true icon, then cover art. Wide art is deliberately absent — a 16:9 hero
     * or screenshot in a square cell is either cropped to an unrecognisable strip
     * of background or letterboxed into a stripe, and letterboxing is exactly
     * what made the grid look like it was full of 16:9 images.
     *
     * Null when nothing suitable exists, which renders the initials plate — square
     * by construction, and a better cell than a squashed screenshot.
     */
    val cellImage: String? get() = icon ?: boxArt

    /** Best available image for the top-screen background. */
    val backgroundImage: String? get() = hero ?: screenshots.firstOrNull() ?: boxArt

    val isEmpty: Boolean
        get() = boxArt == null && hero == null && logo == null &&
            icon == null && screenshots.isEmpty() && videoUri == null
}

/** Per-entry play tracking, maintained locally. */
@Serializable
data class PlayStats(
    val totalPlayMillis: Long = 0L,
    val launchCount: Int = 0,
    val lastPlayedEpochMs: Long? = null,
    val firstPlayedEpochMs: Long? = null,
    /** User-selected performance profile applied when launching. */
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
) {
    companion object {
        val EMPTY = PlayStats()
    }

    val hasBeenPlayed: Boolean get() = launchCount > 0
}

/**
 * Governs CPU/GPU behaviour hints applied around a launch, and drives the
 * battery estimate shown on the detail panel.
 */
@Serializable
enum class PerformanceProfile(
    val label: String,
    /** Rough multiplier against the device's idle drain, used for estimates. */
    val drainMultiplier: Float,
) {
    BATTERY_SAVER("Battery Saver", 1.4f),
    BALANCED("Balanced", 2.0f),
    PERFORMANCE("Performance", 2.8f),
    MAX("Maximum", 3.6f),
}

/** RetroAchievements progress rollup for one game. */
@Serializable
data class AchievementSummary(
    val gameProviderId: String,
    val earned: Int,
    val total: Int,
    val earnedPoints: Int,
    val totalPoints: Int,
    val isHardcore: Boolean = false,
    val recentlyEarned: List<Achievement> = emptyList(),
) {
    val completionFraction: Float
        get() = if (total <= 0) 0f else earned.toFloat() / total.toFloat()

    val isMastered: Boolean get() = total > 0 && earned == total
}

/** A single achievement definition plus this user's progress against it. */
@Serializable
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUri: String? = null,
    val earnedEpochMs: Long? = null,
    val isHardcore: Boolean = false,
) {
    val isEarned: Boolean get() = earnedEpochMs != null
}
