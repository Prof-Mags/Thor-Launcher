package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Reading release names.
 *
 * Every failure here is silent and looks like something else: a misread codec
 * ranks a source wrongly, a missed `2160p` hides the only 4K copy, and a false
 * `cam` match buries a perfectly good release. None of it throws, and none of it
 * is visible except as "it picked a bad one", so it is pinned here instead.
 *
 * The names below are real shapes, not invented ones.
 */
class ReleaseNameTest {

    @Test
    fun `reads a 4K Dolby Vision remux`() {
        val quality = ReleaseName.parse(
            "The.Matrix.1999.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.7.1.Atmos-FGT",
        )

        assertThat(quality.resolution).isEqualTo(Resolution.UHD_4K)
        assertThat(quality.hdr).isEqualTo(HdrFormat.DOLBY_VISION)
        assertThat(quality.videoCodec).isEqualTo(VideoCodec.H265)
        assertThat(quality.audioCodec).isEqualTo(AudioCodec.ATMOS)
        assertThat(quality.source).isEqualTo(ReleaseSource.REMUX)
    }

    /**
     * Dolby Vision releases almost always carry an HDR10 base layer and name it.
     * Reporting the base layer would hide the better format from the label and
     * rank the source as if it were ordinary HDR.
     */
    @Test
    fun `Dolby Vision wins over the HDR10 layer it is built on`() {
        assertThat(ReleaseName.parse("Dune.2021.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265").hdr)
            .isEqualTo(HdrFormat.DOLBY_VISION)
    }

    /** Atmos is carried inside TrueHD and both appear; the better one must win. */
    @Test
    fun `Atmos wins over the TrueHD track carrying it`() {
        assertThat(ReleaseName.parse("Movie.2020.1080p.BluRay.TrueHD.Atmos.7.1.x264").audioCodec)
            .isEqualTo(AudioCodec.ATMOS)
    }

    @Test
    fun `reads a plain 1080p web release`() {
        val quality = ReleaseName.parse("Some.Show.S02E07.1080p.WEB-DL.DDP5.1.H.264-NTb")

        assertThat(quality.resolution).isEqualTo(Resolution.FHD_1080)
        assertThat(quality.source).isEqualTo(ReleaseSource.WEB_DL)
        assertThat(quality.videoCodec).isEqualTo(VideoCodec.H264)
        assertThat(quality.audioCodec).isEqualTo(AudioCodec.EAC3)
        assertThat(quality.hdr).isEqualTo(HdrFormat.NONE)
    }

    /**
     * The reason matching is whole-word.
     *
     * Substring matching finds "ts" in "hits", "cam" in "camera" and "dv" in
     * almost anything — each of which demotes a good release to the bottom of
     * the list for a reason nobody could see.
     */
    @Test
    fun `does not find tokens inside longer words`() {
        val quality = ReleaseName.parse("Greatest.Hits.Camera.Advent.2019.1080p.BluRay.x264")

        assertThat(quality.source).isEqualTo(ReleaseSource.BLURAY)
        assertThat(quality.hdr).isEqualTo(HdrFormat.NONE)
    }

    @Test
    fun `separators do not change the reading`() {
        val dots = ReleaseName.parse("Film.2021.1080p.BluRay.x265-GRP")
        val spaces = ReleaseName.parse("Film 2021 1080p BluRay x265 GRP")
        val brackets = ReleaseName.parse("Film (2021) [1080p] [BluRay] [x265]")

        assertThat(spaces).isEqualTo(dots)
        assertThat(brackets.resolution).isEqualTo(dots.resolution)
        assertThat(brackets.videoCodec).isEqualTo(dots.videoCodec)
    }

    @Test
    fun `flags dubbed and 3D releases`() {
        assertThat(ReleaseName.parse("Film.2019.1080p.BluRay.DUBBED.x264").isDubbed).isTrue()
        assertThat(ReleaseName.parse("Film.2019.1080p.3D.HSBS.BluRay.x264").is3d).isTrue()
    }

    @Test
    fun `picks up languages`() {
        val quality = ReleaseName.parse("Film.2019.1080p.MULTi.TrueFrench.English.BluRay.x264")

        assertThat(quality.languages).containsAtLeast("fr", "en")
    }

    /** An unreadable name must still parse; it simply says nothing. */
    @Test
    fun `an opaque name yields unknowns rather than failing`() {
        val quality = ReleaseName.parse("aXXo-release-final2")

        assertThat(quality.resolution).isEqualTo(Resolution.UNKNOWN)
        assertThat(quality.videoCodec).isEqualTo(VideoCodec.UNKNOWN)
        assertThat(quality.summary).isEmpty()
    }

    @Test
    fun `summary lists only what was actually read`() {
        val summary = ReleaseName.parse("Film.2021.2160p.WEB-DL.DV.H.265").summary

        assertThat(summary).isEqualTo("4K · Dolby Vision · WEB-DL · H.265")
    }
}
