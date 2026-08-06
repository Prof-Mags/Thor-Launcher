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
    /** How long this takes to finish, when a source knows; see [TimeToBeat]. */
    val timeToBeat: TimeToBeat? = null,
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
        const val FIELD_TIME_TO_BEAT = "timeToBeat"
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
    /**
     * A few still to earn, cheapest first.
     *
     * Carried so the panel has something to show on a game the user has not
     * started. Without it a set of forty achievements and none earned rendered
     * as an empty strip under a zero — technically accurate and no use to
     * somebody deciding what to play, which is the question this panel exists to
     * answer.
     *
     * Cheapest first because points are RetroAchievements' own difficulty
     * signal, so these are the ones actually within reach.
     */
    val upcoming: List<Achievement> = emptyList(),
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

/**
 * How long a game takes to finish, as reported by people who have.
 *
 * Three answers rather than one, because "how long is this" has never had a
 * single one: rushing the story, playing it the way it was meant, and finishing
 * everything in it are different games with the same title. Kept in seconds
 * because that is what the source reports; the interface decides what is worth
 * showing.
 *
 * Every field is optional. A game with only a handful of submissions may have
 * one figure and not the others, and a game nobody has submitted has none — the
 * honest answer there is to say nothing rather than to estimate.
 */
@Serializable
data class TimeToBeat(
    /** Straight through the story, skipping what can be skipped. */
    val hastilySeconds: Int? = null,
    /** The ordinary play-through, which is the figure most people mean. */
    val normallySeconds: Int? = null,
    /** Everything: side content, collectables, the lot. */
    val completelySeconds: Int? = null,
    /** How many play-throughs the figures are drawn from. */
    val submissions: Int = 0,
) {
    /**
     * The figure to measure progress against.
     *
     * The ordinary play-through where there is one, because that is what
     * somebody means when they ask how far through they are. Falling back to the
     * rushed figure rather than the completionist one: overstating what is left
     * is discouraging in a way that understating it is not, and a bar that never
     * fills is worse than one that fills early.
     */
    val referenceSeconds: Int?
        get() = normallySeconds ?: hastilySeconds ?: completelySeconds

    val isEmpty: Boolean
        get() = hastilySeconds == null && normallySeconds == null && completelySeconds == null

    /**
     * How far [playedMillis] is through this game, 0..1.
     *
     * Null when there is nothing to measure against, which the interface has to
     * distinguish from zero: no data and not started look nothing alike to
     * somebody deciding what to play.
     */
    fun progressOf(playedMillis: Long): Float? {
        val reference = referenceSeconds?.takeIf { it > 0 } ?: return null
        return (playedMillis / 1000f / reference).coerceIn(0f, 1f)
    }
}

/**
 * How long this game takes, in seconds, from whichever source knows.
 *
 * [TimeToBeat] first because it is the better answer: three figures drawn from
 * submitted play-throughs, against one average. The older single figure stands
 * in where nothing has submitted a time — which on a retro library is most of
 * it, so the fallback is not a formality.
 *
 * Null means nobody knows, which every caller has to keep distinct from zero: no
 * data and not started look nothing alike to somebody deciding what to play.
 */
val GameMetadata.completionSeconds: Int?
    get() = timeToBeat?.referenceSeconds
        ?: completionMinutes?.takeIf { it > 0 }?.times(60)

/**
 * How far through this game the player is, 0..1, or null.
 *
 * One definition, shared by the information panel and the couch shelves. They
 * showed the same bar computed two ways before, from the weaker of the two
 * sources — and a progress bar that disagrees with itself between screens is
 * worse than no progress bar.
 *
 * Null when the length is unknown *or* nothing has been played: a bar sitting at
 * zero says "you have not started this", which is a claim, and it should only be
 * made when it is true rather than when the figure is missing.
 */
fun GameEntry.completionProgress(): Float? {
    val seconds = metadata.completionSeconds?.takeIf { it > 0 } ?: return null
    if (stats.totalPlayMillis <= 0L) return null
    return (stats.totalPlayMillis / 1000f / seconds).coerceIn(0f, 1f)
}
