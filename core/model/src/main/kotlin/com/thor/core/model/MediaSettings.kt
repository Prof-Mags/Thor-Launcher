package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * Everything the Movies section needs configuring.
 *
 * Credentials sit alongside preferences here for the same reason the metadata
 * providers' do: the whole settings tree is one serialisable document, and
 * splitting secrets into a second store would mean backup and restore had to
 * know about two.
 *
 * Nothing here has a working default. A media section that shipped with someone
 * else's keys in it would be both a licence problem and a support problem, and
 * an empty field that says what it wants is clearer than a broken one that does
 * not say why.
 */
@Serializable
data class MediaSettings(
    /** TMDb API key: artwork, synopses, cast, seasons and episodes. */
    val tmdbApiKey: String = "",

    /**
     * Real-Debrid API token.
     *
     * Without it the section still browses and still lists sources; it simply
     * cannot turn a torrent into something playable, which is what the panel
     * says rather than showing an empty source list.
     */
    val realDebridToken: String = "",

    /**
     * Stream-source addons, as base URLs.
     *
     * The Stremio addon protocol: an addon is an HTTP endpoint that answers
     * `/manifest.json` and `/stream/{type}/{id}.json`. It is an open, documented
     * protocol with many independent implementations, which is exactly what a
     * launcher wants — THOR ships no sources of its own and takes no view on
     * which the user runs. Ordered: earlier addons are asked first and their
     * results rank ahead of later ones on a tie.
     */
    val addonUrls: List<String> = emptyList(),

    // ---- Automatic source selection ---------------------------------------

    /**
     * Whether pressing play picks a source rather than opening the list.
     *
     * On by default. The list is always one press away and is worth having, but
     * a media app that makes you choose a torrent every time you press play has
     * put its plumbing in front of its content.
     */
    val autoSelectSource: Boolean = true,

    /** The best resolution worth choosing; higher ones are ranked below it. */
    val preferredResolution: Resolution = Resolution.FHD_1080,

    /**
     * Whether to prefer HDR when the panel can show it.
     *
     * Off by default, and deliberately: HDR content on a display that cannot
     * present it looks washed out and grey, which reads as a broken stream
     * rather than as a mismatched format.
     */
    val preferHdr: Boolean = false,

    /** Skip sources larger than this. 0 means no limit. */
    val maxSizeGb: Float = 0f,

    /**
     * Only offer sources the debrid service already holds.
     *
     * On by default. An uncached torrent is not a stream — it is a download that
     * may take minutes and may never complete, and offering it beside instant
     * ones without distinction is how "streaming" comes to mean "waiting".
     */
    val cachedOnly: Boolean = true,

    /** Preferred audio languages, best first, as ISO 639-1 codes. */
    val preferredLanguages: List<String> = listOf("en"),

    /** Reject dubbed releases, which most people want only deliberately. */
    val avoidDubbed: Boolean = true,

    // ---- Playback ----------------------------------------------------------

    /** Roll straight into the next episode when one finishes. */
    val autoPlayNextEpisode: Boolean = true,

    /** How long the "next episode" prompt waits before doing it. */
    val nextEpisodeCountdownSeconds: Int = 12,

    /** Seconds a skip button moves. */
    val skipSeconds: Int = 15,

    /** Resume where you left off without asking. */
    val resumeAutomatically: Boolean = true,

    /** Subtitle language to enable on start, or empty for none. */
    val defaultSubtitleLanguage: String = "",
) {
    val isMetadataConfigured: Boolean get() = tmdbApiKey.isNotBlank()
    val isDebridConfigured: Boolean get() = realDebridToken.isNotBlank()
    val hasSources: Boolean get() = addonUrls.any { it.isNotBlank() }

    /** Everything needed to actually play something. */
    val isPlayable: Boolean get() = isMetadataConfigured && hasSources
}

/**
 * Orders candidate sources, best first.
 *
 * Pure, and separated from everything that fetches, because this is the part of
 * the feature a user experiences as an opinion: pick badly and the app is slow,
 * or grainy, or silent on their sound system. It is far easier to be sure about
 * the ordering here than by watching what happens to play.
 *
 * The comparisons are lexicographic in a fixed priority, rather than a weighted
 * score. A score means a slightly larger file can outrank a cached one if the
 * weights drift, and nobody can explain why a given source won.
 */
object SourceRanking {

    /**
     * Sources the user's settings do not rule out, best first.
     *
     * Filtering and ordering together because the answer to "why is this not in
     * the list" and "why did this one play" come from the same rules.
     */
    fun rank(sources: List<StreamSource>, settings: MediaSettings): List<StreamSource> =
        sources
            .filter { it.isAllowedBy(settings) }
            .sortedWith(comparator(settings))

    /** The single source [autoSelect] would play, or null when none qualifies. */
    fun best(sources: List<StreamSource>, settings: MediaSettings): StreamSource? =
        rank(sources, settings).firstOrNull()

    private fun StreamSource.isAllowedBy(settings: MediaSettings): Boolean {
        // An unknown cache status is not a "no". Providers that do not report it
        // are common, and treating silence as uncached empties the list.
        if (settings.cachedOnly && cached == CacheStatus.NOT_CACHED) return false
        if (settings.avoidDubbed && quality.isDubbed) return false
        if (quality.is3d) return false

        val limit = settings.maxSizeGb
        if (limit > 0f && sizeBytes != null && sizeBytes > limit * (1L shl 30)) return false

        return true
    }

    private fun comparator(settings: MediaSettings): Comparator<StreamSource> =
        // Cached first, always and before anything else. A 4K remux that has to
        // be downloaded is not better than a 1080p file that starts now.
        compareByDescending<StreamSource> { it.cached == CacheStatus.CACHED }
            .thenByDescending { it.resolutionScore(settings) }
            .thenByDescending { it.languageScore(settings) }
            .thenByDescending { it.hdrScore(settings) }
            .thenByDescending { it.quality.source.ordinal }
            .thenByDescending { it.quality.audioCodec.ordinal }
            .thenByDescending { it.seeders ?: 0 }
            // Smallest last: among sources that are otherwise equal, the larger
            // file is usually the better encode of the same release.
            .thenByDescending { it.sizeBytes ?: 0L }

    /**
     * Distance from the preferred resolution, as a score where more is better.
     *
     * Exact match wins outright. Below the preference is better than above it:
     * asking for 1080p and being given 4K costs bandwidth and decode headroom on
     * a handheld for a difference this panel cannot show.
     */
    private fun StreamSource.resolutionScore(settings: MediaSettings): Int {
        val want = settings.preferredResolution.ordinal
        val got = quality.resolution.ordinal
        return when {
            quality.resolution == Resolution.UNKNOWN -> -100
            got == want -> 100
            got < want -> 50 - (want - got)
            else -> 20 - (got - want)
        }
    }

    private fun StreamSource.hdrScore(settings: MediaSettings): Int = when {
        !settings.preferHdr -> if (quality.hdr == HdrFormat.NONE) 1 else 0
        else -> quality.hdr.ordinal
    }

    /**
     * How well the release's languages match, as a score where more is better.
     *
     * A release naming no language at all scores between a match and a mismatch:
     * most English releases do not say so, and demoting them below a file that
     * announces it is in another language gets it exactly backwards.
     */
    private fun StreamSource.languageScore(settings: MediaSettings): Int {
        val wanted = settings.preferredLanguages
        if (wanted.isEmpty() || quality.languages.isEmpty()) return 1

        val bestIndex = quality.languages
            .mapNotNull { language -> wanted.indexOf(language).takeIf { it >= 0 } }
            .minOrNull()

        return if (bestIndex == null) 0 else wanted.size - bestIndex + 1
    }
}
