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
    /**
     * Kept only so an older settings file still reads back.
     *
     * TMDb used to supply the catalogue and required this key to supply
     * anything, which made an API key the first thing the section asked a new
     * user for. The catalogue is the Stremio protocol now — the same one the
     * source addons speak, keyed by the same IMDb ids — and it needs no
     * credential, so nothing reads this any more. The field stays because
     * removing it from a serialised document is a migration, and gains nothing.
     */
    @Deprecated("The catalogue needs no key; nothing reads this.")
    val tmdbApiKey: String = "",

    /**
     * Real-Debrid API token.
     *
     * Without it the section still browses and still lists sources; it simply
     * cannot turn a torrent into something playable, which is what the panel
     * says rather than showing an empty source list.
     */
    val realDebridToken: String = "",

    /** TorBox API key, used when [debridService] names it. */
    val torBoxApiKey: String = "",

    /**
     * Which debrid service turns a torrent into a stream.
     *
     * One at a time rather than both at once. They answer the same question and
     * a source can only be opened through one of them, so running both would
     * mean checking two caches for every hash and then explaining which account
     * a stream came from — for a gain of nothing, since a source cached on
     * either service plays identically.
     *
     * Both credentials are kept whichever is selected, so switching back does
     * not mean finding the other token again.
     */
    val debridService: DebridService = DebridService.REAL_DEBRID,

    /**
     * Torrent indexers, searched by THOR itself.
     *
     * The built-in path: THOR speaks Torznab directly, so searching, parsing,
     * ranking and resolving all happen in-app with nothing else in the stream
     * path. THOR ships no indexers and no list of them — the launcher knows the
     * protocol and the user decides who to ask, which is the same position it
     * takes on game metadata providers.
     */
    val indexers: List<TorznabIndexer> = emptyList(),

    /**
     * Stream-source addons.
     *
     * The Stremio addon protocol: an HTTP endpoint answering `/manifest.json`
     * describing itself and `/stream/{type}/{id}.json` with candidates. Open,
     * documented, and with many independent implementations — which is exactly
     * what a launcher wants. THOR speaks the protocol and ships no addons; which
     * one to install is the user's choice, as it is in Stremio itself.
     *
     * Simpler to set up than [indexers] and listed first for that reason: an
     * addon is one URL with no key, where a Torznab indexer needs an endpoint and
     * a credential per site. Both are queried and their results merged.
     */
    val addons: List<StremioAddon> = emptyList(),

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
    /**
     * Always true, and kept as a name rather than deleted.
     *
     * Browsing needs no credential now. This used to gate the whole section on a
     * TMDb key, so a fresh install opened Movies onto an instruction to go and
     * register with a website — the first thing the section ever said, on a
     * screen that could otherwise have been showing films.
     */
    val isMetadataConfigured: Boolean get() = true

    /** The credential for whichever service is selected, which may be blank. */
    val debridToken: String
        get() = when (debridService) {
            DebridService.REAL_DEBRID -> realDebridToken
            DebridService.TORBOX -> torBoxApiKey
        }

    val isDebridConfigured: Boolean get() = debridToken.isNotBlank()
    val hasSources: Boolean
        get() = addons.any { it.isUsable } || indexers.any { it.isUsable }

    /**
     * Everything needed to actually play something.
     *
     * A source is the only requirement: the catalogue answers without one, so a
     * user who has configured nothing can still browse — and finds out what is
     * missing at the point where it matters rather than at the door.
     */
    val isPlayable: Boolean get() = hasSources
}

/**
 * A service that already holds the file a torrent points at.
 *
 * The distinction the Movies section is built on: a magnet on its own is a
 * peer-to-peer download that starts slowly and may never finish, and the same
 * magnet handed to a service that has the file becomes an ordinary HTTP URL that
 * seeks instantly. Which service is a preference rather than a capability —
 * both do the same job, people hold accounts with one or the other, and the
 * difference is visible nowhere except in this setting.
 */
@Serializable
enum class DebridService(val label: String) {
    REAL_DEBRID("Real-Debrid"),
    TORBOX("TorBox"),
    ;

    /** What its credential is called on its own website, so the field matches. */
    val credentialLabel: String
        get() = when (this) {
            REAL_DEBRID -> "API token"
            TORBOX -> "API key"
        }
}

/**
 * One installed Stremio-protocol addon.
 *
 * The name is resolved from the addon's own manifest rather than typed, so the
 * list reads as the addons the user installed rather than as a column of URLs —
 * and so that a working install is visibly distinguishable from a URL that was
 * merely pasted.
 */
@Serializable
data class StremioAddon(
    /** Base URL, already normalised by [StremioAddons.normalise]. */
    val url: String = "",
    /** From the manifest; empty until the addon has answered. */
    val name: String = "",
    val enabled: Boolean = true,
) {
    val isUsable: Boolean get() = enabled && url.isNotBlank()

    /** What to call it in a list: its own name, else its host. */
    val label: String
        get() = name.ifBlank {
            url.substringAfter("://").substringBefore('/').ifBlank { "Addon" }
        }
}

/**
 * Turns whatever the user pasted into a base URL.
 *
 * Addon links are shared in several shapes and people paste all of them: the
 * manifest URL from a browser, the `stremio://` link an install button produces,
 * a configured URL with options embedded in the path, or a bare host. They all
 * describe the same endpoint, and the protocol wants its base — so rather than
 * telling the user which form is acceptable, every form is accepted.
 */
object StremioAddons {

    fun normalise(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return ""

        // The scheme an install button uses. It is an ordinary HTTPS endpoint
        // underneath; only the link is dressed up.
        url = url.removePrefix("stremio://").let { stripped ->
            if (stripped != url) "https://$stripped" else url
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        // The manifest is how an addon describes itself; the stream endpoint sits
        // beside it, so the base is what gets stored.
        return url.removeSuffix("/manifest.json").removeSuffix("/").trim()
    }

    /** Where the manifest lives for a normalised base URL. */
    fun manifestUrl(baseUrl: String): String = "${normalise(baseUrl)}/manifest.json"
}

/**
 * One torrent indexer THOR searches directly.
 *
 * Name is the user's own label, so a list of several is readable at a glance
 * rather than being a column of near-identical URLs.
 */
@Serializable
data class TorznabIndexer(
    val name: String = "",
    /** Base URL of the Torznab endpoint, without the trailing `/api`. */
    val url: String = "",
    val apiKey: String = "",
    val enabled: Boolean = true,
) {
    val isUsable: Boolean get() = enabled && url.isNotBlank() && apiKey.isNotBlank()
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
