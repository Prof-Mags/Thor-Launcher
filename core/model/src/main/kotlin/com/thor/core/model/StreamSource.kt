package com.thor.core.model

import kotlinx.serialization.Serializable

/** Picture size, ordered worst to best so the enum's own order ranks them. */
@Serializable
enum class Resolution(val label: String) {
    UNKNOWN("—"),
    SD("SD"),
    HD_720("720p"),
    FHD_1080("1080p"),
    QHD_1440("1440p"),
    UHD_4K("4K"),
}

/** High dynamic range format, ordered by how much most people prefer it. */
@Serializable
enum class HdrFormat(val label: String) {
    NONE(""),
    HDR10("HDR"),
    HLG("HLG"),
    HDR10_PLUS("HDR10+"),
    DOLBY_VISION("Dolby Vision"),
}

@Serializable
enum class VideoCodec(val label: String) {
    UNKNOWN(""),
    H264("H.264"),
    H265("H.265"),
    AV1("AV1"),
    VP9("VP9"),
}

@Serializable
enum class AudioCodec(val label: String) {
    UNKNOWN(""),
    AAC("AAC"),
    AC3("AC3"),
    EAC3("EAC3"),
    DTS("DTS"),
    DTS_HD("DTS-HD"),
    TRUEHD("TrueHD"),
    ATMOS("Atmos"),
    FLAC("FLAC"),
    OPUS("Opus"),
}

/** Where the release came from, which is the best proxy for how good it looks. */
@Serializable
enum class ReleaseSource(val label: String) {
    UNKNOWN(""),
    CAM("CAM"),
    TELESYNC("TS"),
    SCREENER("SCR"),
    HDTV("HDTV"),
    WEBRIP("WEBRip"),
    WEB_DL("WEB-DL"),
    BLURAY("BluRay"),
    REMUX("REMUX"),
}

/** Whether the debrid service already holds this, which decides the wait. */
@Serializable
enum class CacheStatus {
    /** Known to be on the debrid service: playback starts immediately. */
    CACHED,

    /** Not held: the service must download it first, which can take minutes. */
    NOT_CACHED,

    /** Not asked, or the service did not say. */
    UNKNOWN,
}

/**
 * What a release name says about the file.
 *
 * Parsed rather than trusted from a provider field, because providers do not
 * agree on which fields exist and most supply none of them — the release name is
 * the one thing every source has. See [ReleaseName].
 */
@Serializable
data class StreamQuality(
    val resolution: Resolution = Resolution.UNKNOWN,
    val hdr: HdrFormat = HdrFormat.NONE,
    val videoCodec: VideoCodec = VideoCodec.UNKNOWN,
    val audioCodec: AudioCodec = AudioCodec.UNKNOWN,
    val source: ReleaseSource = ReleaseSource.UNKNOWN,
    /** ISO-ish language tags found in the name, e.g. "en", "fr". */
    val languages: List<String> = emptyList(),
    /** A dub or a hardcoded-subtitle release, which most people do not want. */
    val isDubbed: Boolean = false,
    val is3d: Boolean = false,
) {
    /** The short line shown against a source, e.g. "4K · Dolby Vision · H.265". */
    val summary: String
        get() = listOf(
            resolution.takeIf { it != Resolution.UNKNOWN }?.label,
            hdr.takeIf { it != HdrFormat.NONE }?.label,
            source.takeIf { it != ReleaseSource.UNKNOWN }?.label,
            videoCodec.takeIf { it != VideoCodec.UNKNOWN }?.label,
            audioCodec.takeIf { it != AudioCodec.UNKNOWN }?.label,
        ).filterNotNull().joinToString(" · ")
}

/**
 * One playable candidate for a title.
 *
 * A source is not yet a stream. Most arrive as a torrent that a debrid service
 * has to turn into an HTTP URL, and whether that takes a moment or ten minutes
 * is [cached] — which is why it is part of the model rather than a detail of the
 * resolver, and why the list is sorted by it before anything else.
 */
@Serializable
data class StreamSource(
    /** Stable within a result set; used for selection and for resume. */
    val id: String,
    val providerId: String,
    val providerName: String,
    /** The raw release name, shown verbatim because people read these. */
    val title: String,
    val quality: StreamQuality = StreamQuality(),
    val infoHash: String? = null,
    val magnetUri: String? = null,
    /** Set when a provider hands over a ready HTTP stream and no debrid is needed. */
    val directUrl: String? = null,
    /** Headers an addon requires when opening [directUrl]. */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** Which file inside a multi-file torrent, when the provider names one. */
    val fileIndex: Int? = null,
    /**
     * Real-Debrid file IDs which must be selected together for this cached
     * availability variant to remain instant. Empty when availability was not
     * checked, the source is direct, or no compatible variant was returned.
     */
    val instantFileIds: List<Int> = emptyList(),
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val cached: CacheStatus = CacheStatus.UNKNOWN,
) {
    val isPlayableDirectly: Boolean get() = directUrl != null

    /** "12.4 GB", or null when the provider did not say. */
    val sizeLabel: String?
        get() = sizeBytes?.let { bytes ->
            when {
                bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
                bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
                else -> "$bytes B"
            }
        }
}

/**
 * Reads a release name.
 *
 * Scene and web release names are not a format, but they are a strong
 * convention, and after thirty years of it the tokens below cover almost
 * everything a source will return. Everything is best-effort and every field
 * falls back to `UNKNOWN` — a name this cannot read still plays, it just sorts
 * lower than one that could be understood.
 *
 * Deliberately a pure function on a string, in `:core:model`, with tests. Source
 * ranking is the part of this feature users will notice being wrong, and it is
 * far cheaper to be sure about "does `x265` imply H.265" here than in a UI test
 * against a live provider.
 */
object ReleaseName {

    fun parse(raw: String): StreamQuality {
        // Separators vary by release group; normalising once means every matcher
        // below can assume spaces.
        val name = " ${raw.replace(Regex("[._\\-\\[\\]()]+"), " ").lowercase()} "

        return StreamQuality(
            resolution = resolutionOf(name),
            hdr = hdrOf(name),
            videoCodec = videoCodecOf(name),
            audioCodec = audioCodecOf(name),
            source = sourceOf(name),
            languages = languagesOf(name),
            isDubbed = name.containsToken("dubbed", "dub", "dublado", "multi audio"),
            is3d = name.containsToken("3d", "sbs", "hsbs"),
        )
    }

    private fun resolutionOf(name: String): Resolution = when {
        name.containsToken("2160p", "4k", "uhd", "hdr4k") -> Resolution.UHD_4K
        name.containsToken("1440p", "2k") -> Resolution.QHD_1440
        name.containsToken("1080p", "1080i", "fullhd", "fhd") -> Resolution.FHD_1080
        name.containsToken("720p", "hd") -> Resolution.HD_720
        name.containsToken("480p", "576p", "360p", "sd", "dvdrip") -> Resolution.SD
        else -> Resolution.UNKNOWN
    }

    private fun hdrOf(name: String): HdrFormat = when {
        // Checked before plain HDR: a Dolby Vision release almost always carries
        // an HDR10 base layer and says so, and reporting the base layer would
        // hide the better format from both the label and the ranking.
        name.containsToken("dv", "dovi", "dolby vision", "dolbyvision") -> HdrFormat.DOLBY_VISION
        name.containsToken("hdr10+", "hdr10plus", "hdrplus") -> HdrFormat.HDR10_PLUS
        name.containsToken("hlg") -> HdrFormat.HLG
        name.containsToken("hdr", "hdr10") -> HdrFormat.HDR10
        else -> HdrFormat.NONE
    }

    private fun videoCodecOf(name: String): VideoCodec = when {
        name.containsToken("x265", "h265", "h 265", "hevc") -> VideoCodec.H265
        name.containsToken("av1") -> VideoCodec.AV1
        name.containsToken("vp9") -> VideoCodec.VP9
        name.containsToken("x264", "h264", "h 264", "avc") -> VideoCodec.H264
        else -> VideoCodec.UNKNOWN
    }

    private fun audioCodecOf(name: String): AudioCodec = when {
        // Atmos first for the same reason Dolby Vision is: it is carried inside
        // TrueHD or E-AC3 and the name usually lists both.
        name.containsToken("atmos") -> AudioCodec.ATMOS
        name.containsToken("truehd", "true hd") -> AudioCodec.TRUEHD
        name.containsToken("dts hd", "dtshd", "dts x", "dtsx", "dts ma") -> AudioCodec.DTS_HD
        name.containsToken("dts") -> AudioCodec.DTS
        // Dolby Digital carries its channel layout in the token itself — DDP5.1,
        // DD5.1, DD+7.1 — so these are patterns rather than words. Matching the
        // bare "ddp" found nothing at all, because nobody writes it bare.
        name.containsPattern(EAC3_PATTERN) -> AudioCodec.EAC3
        name.containsPattern(AC3_PATTERN) -> AudioCodec.AC3
        name.containsToken("flac") -> AudioCodec.FLAC
        name.containsToken("opus") -> AudioCodec.OPUS
        name.containsToken("aac") -> AudioCodec.AAC
        else -> AudioCodec.UNKNOWN
    }

    private fun sourceOf(name: String): ReleaseSource = when {
        name.containsToken("remux") -> ReleaseSource.REMUX
        name.containsToken("bluray", "blu ray", "bdrip", "brrip", "bdremux") -> ReleaseSource.BLURAY
        name.containsToken("web dl", "webdl", "web") -> ReleaseSource.WEB_DL
        name.containsToken("webrip") -> ReleaseSource.WEBRIP
        name.containsToken("hdtv", "pdtv") -> ReleaseSource.HDTV
        name.containsToken("screener", "scr", "dvdscr") -> ReleaseSource.SCREENER
        name.containsToken("ts", "telesync", "hdts") -> ReleaseSource.TELESYNC
        name.containsToken("cam", "camrip", "hdcam") -> ReleaseSource.CAM
        else -> ReleaseSource.UNKNOWN
    }

    private fun languagesOf(name: String): List<String> =
        LANGUAGE_TOKENS.filterKeys { token -> name.containsToken(token) }.values.distinct()

    /**
     * Whole-word matching.
     *
     * `contains` is wrong here and quietly so: "ts" appears inside "hits",
     * "cam" inside "camera", and "dv" inside almost anything. Every release name
     * has been padded with spaces and had its separators normalised, so a token
     * surrounded by spaces is exactly a whole word.
     */
    private fun String.containsToken(vararg tokens: String): Boolean =
        tokens.any { token -> contains(" $token ") }

    /** Whole-word matching for tokens that carry a channel count or a plus. */
    private fun String.containsPattern(pattern: Regex): Boolean = pattern.containsMatchIn(this)

    /** DDP, DD+, DDP5.1, E-AC3 — all Dolby Digital Plus. */
    private val EAC3_PATTERN = Regex(" (?:e ?ac3|ddp\\d*|dd\\+\\d*) ")

    /** DD, DD5.1, AC3 — plain Dolby Digital. */
    private val AC3_PATTERN = Regex(" (?:ac3|dd\\d*) ")

    private val LANGUAGE_TOKENS: Map<String, String> = mapOf(
        "english" to "en", "eng" to "en",
        "french" to "fr", "vf" to "fr", "truefrench" to "fr",
        "spanish" to "es", "castellano" to "es", "latino" to "es",
        "german" to "de", "deutsch" to "de",
        "italian" to "it", "ita" to "it",
        "portuguese" to "pt", "dublado" to "pt",
        "russian" to "ru", "rus" to "ru",
        "japanese" to "ja", "jpn" to "ja",
        "korean" to "ko",
        "chinese" to "zh", "mandarin" to "zh",
        "hindi" to "hi",
    )
}
