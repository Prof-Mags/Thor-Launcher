package com.thor.core.common.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Title normalisation drives search, sorting, duplicate detection and every
 * scraper query, so it is worth pinning down against the real shapes found in
 * No-Intro, Redump and TOSEC ROM sets.
 */
class TitleNormalizerTest {

    @Test
    fun `strips extension and region decoration`() {
        val title = TitleNormalizer.displayTitle("Super Mario World (USA) (Rev A).sfc")
        assertThat(title).isEqualTo("Super Mario World")
    }

    @Test
    fun `moves a trailing article back to the front`() {
        val title = TitleNormalizer.displayTitle(
            "Legend of Zelda, The - Ocarina of Time (USA).z64",
        )
        assertThat(title).isEqualTo("The Legend of Zelda - Ocarina of Time")
    }

    @Test
    fun `treats separators as spaces`() {
        val title = TitleNormalizer.displayTitle("Sonic_The_Hedgehog_2.md")
        assertThat(title).isEqualTo("Sonic The Hedgehog 2")
    }

    @Test
    fun `does not move a trailing word that is not an article`() {
        val title = TitleNormalizer.displayTitle("Final Fantasy, Origins.iso")
        assertThat(title).isEqualTo("Final Fantasy, Origins")
    }

    @Test
    fun `sort key drops the leading article and punctuation`() {
        assertThat(TitleNormalizer.sortKey("The Legend of Zelda")).isEqualTo("legend of zelda")
        // Punctuation is dropped and the resulting run of spaces collapses.
        assertThat(TitleNormalizer.sortKey("Ratchet & Clank")).isEqualTo("ratchet clank")
    }

    @Test
    fun `sort key is stable across decoration differences`() {
        val a = TitleNormalizer.sortKey(TitleNormalizer.displayTitle("Chrono Trigger (USA).sfc"))
        val b = TitleNormalizer.sortKey(TitleNormalizer.displayTitle("Chrono Trigger (Japan).sfc"))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `extracts region from decoration`() {
        assertThat(TitleNormalizer.region("Metroid (USA).nes")).isEqualTo("USA")
        assertThat(TitleNormalizer.region("Metroid (Japan).nes")).isEqualTo("Japan")
        assertThat(TitleNormalizer.region("Metroid (Europe, Australia).nes")).isEqualTo("Europe")
        assertThat(TitleNormalizer.region("Metroid.nes")).isNull()
    }

    @Test
    fun `extracts disc and revision markers`() {
        assertThat(TitleNormalizer.discNumber("Final Fantasy VII (USA) (Disc 2).bin")).isEqualTo(2)
        assertThat(TitleNormalizer.revision("Super Mario World (USA) (Rev A).sfc")).isEqualTo("A")
        assertThat(TitleNormalizer.discNumber("Super Mario World.sfc")).isNull()
    }

    @Test
    fun `builds a readable version label`() {
        val label = TitleNormalizer.versionLabel("Final Fantasy VII (USA) (Rev 1) (Disc 2).bin")
        assertThat(label).contains("USA")
        assertThat(label).contains("Rev 1")
        assertThat(label).contains("Disc 2")
    }

    @Test
    fun `version label falls back when there is no decoration`() {
        assertThat(TitleNormalizer.versionLabel("Tetris.gb")).isEqualTo("Standard")
    }

    @Test
    fun `a name that is entirely decoration keeps its original text`() {
        // Guards against returning an empty title for pathological file names.
        val title = TitleNormalizer.displayTitle("(USA).zip")
        assertThat(title).isNotEmpty()
    }
}
