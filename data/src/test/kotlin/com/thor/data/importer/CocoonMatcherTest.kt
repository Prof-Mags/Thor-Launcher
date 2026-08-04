package com.thor.data.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pairing Cocoon's names with the launcher's games.
 *
 * The requirement is that they need not agree word for word — which is the whole
 * difficulty, because "close enough" and "a different game in the same series"
 * are not far apart. Batman Arkham City and Batman Arkham Knight share three
 * words out of four.
 */
class CocoonMatcherTest {

    private val cocoon = listOf(
        "Batman Arkham Asylum",
        "Batman Arkham City",
        "Batman Arkham Knight",
        "Mario Kart 8 Deluxe",
        "Super Mario Odyssey",
        "The Legend of Zelda Breath of the Wild",
        "Yoshi's Crafted World",
    )

    private fun target(title: String, fileName: String = "$title.nsp") =
        ImportTarget(entryId = title, title = title, fileName = fileName)

    private fun matchFor(title: String, fileName: String = "$title.nsp"): CocoonMatch? =
        matchCocoonTitles(listOf(target(title, fileName)), cocoon).firstOrNull()

    @Test
    fun `an exact name matches itself`() {
        assertThat(matchFor("Super Mario Odyssey")?.cocoonTitle).isEqualTo("Super Mario Odyssey")
    }

    @Test
    fun `punctuation need not agree`() {
        assertThat(matchFor("Yoshis Crafted World")?.cocoonTitle)
            .isEqualTo("Yoshi's Crafted World")
    }

    @Test
    fun `a colon in the launcher's title is forgiven`() {
        assertThat(matchFor("The Legend of Zelda: Breath of the Wild")?.cocoonTitle)
            .isEqualTo("The Legend of Zelda Breath of the Wild")
    }

    @Test
    fun `a region tag does not prevent a match`() {
        assertThat(matchFor("Mario Kart 8 Deluxe (USA)")?.cocoonTitle)
            .isEqualTo("Mario Kart 8 Deluxe")
    }

    @Test
    fun `the nearest of several similar titles wins`() {
        // Three names sharing "Batman Arkham" — the fourth word has to decide it.
        assertThat(matchFor("Batman Arkham Knight")?.cocoonTitle)
            .isEqualTo("Batman Arkham Knight")
        assertThat(matchFor("Batman Arkham City")?.cocoonTitle)
            .isEqualTo("Batman Arkham City")
    }

    @Test
    fun `a game nobody has artwork for matches nothing`() {
        // The cost of a miss is a game with no artwork, which is where it began.
        // The cost of a wrong match is another game's picture, kept.
        assertThat(matchFor("Metroid Dread")).isNull()
    }

    @Test
    fun `the filename is tried when the title is unhelpful`() {
        // A library scanned from terse filenames has titles a human would not
        // recognise; the file itself often carries the real name.
        val match = matchFor(title = "smo", fileName = "Super Mario Odyssey.nsp")

        assertThat(match?.cocoonTitle).isEqualTo("Super Mario Odyssey")
    }

    @Test
    fun `two entries of one game both get the artwork`() {
        val targets = listOf(
            target("Batman Arkham City"),
            target("Batman Arkham City (Europe)"),
        )

        val matches = matchCocoonTitles(targets, cocoon)

        assertThat(matches).hasSize(2)
        assertThat(matches.map { it.cocoonTitle }.distinct())
            .containsExactly("Batman Arkham City")
    }

    @Test
    fun `nothing matches when there is nothing to match against`() {
        assertThat(matchCocoonTitles(listOf(target("Super Mario Odyssey")), emptyList()))
            .isEmpty()
    }

    @Test
    fun `the floor can be raised to demand a closer match`() {
        val loose = matchCocoonTitles(
            listOf(target("Batman Arkham")),
            cocoon,
            minimumConfidence = 0.5f,
        )
        val strict = matchCocoonTitles(
            listOf(target("Batman Arkham")),
            cocoon,
            minimumConfidence = 0.95f,
        )

        assertThat(loose).isNotEmpty()
        assertThat(strict).isEmpty()
    }
}
