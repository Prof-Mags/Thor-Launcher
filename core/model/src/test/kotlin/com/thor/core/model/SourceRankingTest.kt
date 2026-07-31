package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which source plays.
 *
 * The user experiences this as an opinion rather than as code: pick badly and
 * the app is slow, or grainy, or silent on their sound system. Nothing here
 * throws when it is wrong — it just plays the wrong file — so the rules are
 * pinned rather than inferred from what happens to start.
 */
class SourceRankingTest {

    private fun source(
        name: String,
        cached: CacheStatus = CacheStatus.CACHED,
        sizeBytes: Long? = null,
        seeders: Int? = null,
    ) = StreamSource(
        id = name,
        providerId = "test",
        providerName = "Test",
        title = name,
        quality = ReleaseName.parse(name),
        cached = cached,
        sizeBytes = sizeBytes,
        seeders = seeders,
    )

    private val settings = MediaSettings(preferredResolution = Resolution.FHD_1080)

    /**
     * The rule that matters most.
     *
     * A 4K remux that has to be downloaded first is not better than a 1080p file
     * that starts now — it is a different product. Cache beats every other axis.
     */
    @Test
    fun `a cached source beats a better uncached one`() {
        val uncached4k = source("Film.2160p.BluRay.REMUX.x265", cached = CacheStatus.NOT_CACHED)
        val cached1080 = source("Film.1080p.WEB-DL.x264", cached = CacheStatus.CACHED)

        val best = SourceRanking.best(
            listOf(uncached4k, cached1080),
            settings.copy(cachedOnly = false),
        )

        assertThat(best).isEqualTo(cached1080)
    }

    @Test
    fun `uncached sources are hidden entirely by default`() {
        val ranked = SourceRanking.rank(
            listOf(source("Film.1080p.x264", cached = CacheStatus.NOT_CACHED)),
            settings,
        )

        assertThat(ranked).isEmpty()
    }

    /**
     * Providers that do not report cache status are common, and treating their
     * silence as "not cached" empties the list for everyone using one.
     */
    @Test
    fun `an unknown cache status is not treated as uncached`() {
        val unknown = source("Film.1080p.x264", cached = CacheStatus.UNKNOWN)

        assertThat(SourceRanking.rank(listOf(unknown), settings)).containsExactly(unknown)
    }

    @Test
    fun `the preferred resolution wins outright`() {
        val best = SourceRanking.best(
            listOf(
                source("Film.2160p.WEB-DL.x265"),
                source("Film.720p.WEB-DL.x264"),
                source("Film.1080p.WEB-DL.x264"),
            ),
            settings,
        )

        assertThat(best?.quality?.resolution).isEqualTo(Resolution.FHD_1080)
    }

    /**
     * Below the preference beats above it. Asking for 1080p and being handed 4K
     * costs bandwidth and decode headroom for a difference this panel cannot
     * show — so when the exact match is missing, step down rather than up.
     */
    @Test
    fun `a lower resolution is preferred over an unnecessarily higher one`() {
        val best = SourceRanking.best(
            listOf(source("Film.2160p.WEB-DL.x265"), source("Film.720p.WEB-DL.x264")),
            settings,
        )

        assertThat(best?.quality?.resolution).isEqualTo(Resolution.HD_720)
    }

    @Test
    fun `size limits exclude rather than demote`() {
        val huge = source("Film.2160p.REMUX.x265", sizeBytes = 60L shl 30)
        val small = source("Film.1080p.WEB-DL.x264", sizeBytes = 4L shl 30)

        val ranked = SourceRanking.rank(listOf(huge, small), settings.copy(maxSizeGb = 20f))

        assertThat(ranked).containsExactly(small)
    }

    @Test
    fun `dubbed and 3D releases are dropped`() {
        val ranked = SourceRanking.rank(
            listOf(
                source("Film.1080p.BluRay.DUBBED.x264"),
                source("Film.1080p.3D.HSBS.BluRay.x264"),
                source("Film.1080p.BluRay.x264"),
            ),
            settings,
        )

        assertThat(ranked).hasSize(1)
        assertThat(ranked.single().quality.isDubbed).isFalse()
    }

    /**
     * Most English releases do not say they are English. Demoting them below a
     * file that announces another language gets it exactly backwards.
     */
    @Test
    fun `a release naming no language outranks one naming the wrong language`() {
        val silent = source("Film.1080p.WEB-DL.x264")
        val french = source("Film.1080p.TrueFrench.WEB-DL.x264")

        val best = SourceRanking.best(listOf(french, silent), settings)

        assertThat(best).isEqualTo(silent)
    }

    @Test
    fun `a matching language beats one that says nothing`() {
        val english = source("Film.1080p.English.WEB-DL.x264")
        val silent = source("Film.1080p.WEB-DL.x264")

        assertThat(SourceRanking.best(listOf(silent, english), settings)).isEqualTo(english)
    }

    /** Off by default, because HDR on a panel that cannot show it looks broken. */
    @Test
    fun `HDR is avoided unless asked for`() {
        val hdr = source("Film.1080p.WEB-DL.HDR.x265")
        val sdr = source("Film.1080p.WEB-DL.x265")

        assertThat(SourceRanking.best(listOf(hdr, sdr), settings)).isEqualTo(sdr)
        assertThat(SourceRanking.best(listOf(sdr, hdr), settings.copy(preferHdr = true)))
            .isEqualTo(hdr)
    }

    @Test
    fun `otherwise equal sources are separated by seeders then size`() {
        val few = source("Film.1080p.WEB-DL.x264", seeders = 3, sizeBytes = 8L shl 30)
        val many = source("Film.1080p.WEB-DL.x264", seeders = 400, sizeBytes = 8L shl 30)

        assertThat(SourceRanking.best(listOf(few, many), settings)).isEqualTo(many)
    }

    @Test
    fun `nothing to choose from is null rather than an error`() {
        assertThat(SourceRanking.best(emptyList(), settings)).isNull()
    }
}
