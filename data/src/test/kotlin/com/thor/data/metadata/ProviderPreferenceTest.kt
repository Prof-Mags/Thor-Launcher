package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which provider owns which slot.
 *
 * A single priority order cannot express it: IGDB has the landscape artwork,
 * SteamGridDB the square grid a cell wants, and Wikipedia the prose. Ranking
 * IGDB high enough to win the artwork hands it the descriptions too; ranking it
 * below SteamGridDB gives the panel a banner instead of a screenshot.
 */
class ProviderPreferenceTest {

    private fun candidate(providerId: String, confidence: Float = 1f) =
        MetadataCandidate(
            providerId = providerId,
            remoteId = providerId,
            matchedTitle = providerId,
            confidence = confidence,
        )

    private val ranked = listOf(
        candidate("screenscraper"),
        candidate("steamgriddb"),
        candidate("wikidata"),
        candidate("igdb"),
    )

    @Test
    fun `the preferred provider is asked first`() {
        assertThat(ranked.preferring("igdb").first().providerId).isEqualTo("igdb")
        assertThat(ranked.preferring("steamgriddb").first().providerId).isEqualTo("steamgriddb")
    }

    @Test
    fun `everyone else keeps their order behind it`() {
        // A preference reorders one entry, not the list. Whatever the ranking
        // decided still decides everything the preference does not.
        assertThat(ranked.preferring("igdb").map { it.providerId })
            .containsExactly("igdb", "screenscraper", "steamgriddb", "wikidata")
            .inOrder()
    }

    @Test
    fun `nobody is excluded, because a preference is not a requirement`() {
        // A game IGDB has never heard of still gets whatever anyone else found.
        assertThat(ranked.preferring("igdb")).hasSize(ranked.size)
    }

    @Test
    fun `preferring an absent provider changes nothing`() {
        val withoutIgdb = ranked.filterNot { it.providerId == "igdb" }

        assertThat(withoutIgdb.preferring("igdb").map { it.providerId })
            .isEqualTo(withoutIgdb.map { it.providerId })
    }

    @Test
    fun `the three slots route to three different providers`() {
        // The whole point: one ordering could not have done this.
        assertThat(ranked.preferring(MetadataAggregatorFields.ARTWORK).first().providerId)
            .isEqualTo("igdb")
        assertThat(ranked.preferring(MetadataAggregatorFields.ICON).first().providerId)
            .isEqualTo("steamgriddb")
        assertThat(ranked.preferring(MetadataAggregatorFields.DESCRIPTION).first().providerId)
            .isEqualTo("wikidata")
    }
}

/** Mirrors the aggregator's private constants, so a rename breaks this loudly. */
private object MetadataAggregatorFields {
    const val ARTWORK = "igdb"
    const val ICON = "steamgriddb"
    const val DESCRIPTION = "wikidata"
}
