package com.thor.data.sync

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.GameMetadata
import com.thor.core.model.TimeToBeat
import org.junit.Test

class MetadataDescriptionRefreshTest {

    @Test
    fun `blank description is refreshed when a prose provider is available`() {
        val metadata = GameMetadata(lastScrapedEpochMs = 123L)

        assertThat(metadata.needsDescriptionRefresh(providerAvailable = true)).isTrue()
    }

    @Test
    fun `manual blank description remains locked`() {
        val metadata = GameMetadata(
            lockedFields = setOf(GameMetadata.FIELD_DESCRIPTION),
            lastScrapedEpochMs = 123L,
        )

        assertThat(metadata.needsDescriptionRefresh(providerAvailable = true)).isFalse()
    }

    @Test
    fun `descriptions are not repeatedly refreshed once filled`() {
        val metadata = GameMetadata(description = "Already present", lastScrapedEpochMs = 123L)

        assertThat(metadata.needsDescriptionRefresh(providerAvailable = true)).isFalse()
    }

    // The case that kept every progress bar off the panel: a library scraped
    // before completion times existed is stamped, so "only missing" skipped it
    // forever and no amount of re-scraping could fetch the figure.
    @Test
    fun `an already-scraped game with no completion time is asked again`() {
        val metadata = GameMetadata(description = "Present", lastScrapedEpochMs = 123L)

        assertThat(metadata.needsCompletionRefresh(providerAvailable = true)).isTrue()
    }

    @Test
    fun `completion times are not refetched once either source has answered`() {
        val fromRawg = GameMetadata(completionMinutes = 1_500, lastScrapedEpochMs = 123L)
        val fromIgdb = GameMetadata(
            timeToBeat = TimeToBeat(normallySeconds = 90_000, submissions = 12),
            lastScrapedEpochMs = 123L,
        )

        assertThat(fromRawg.needsCompletionRefresh(providerAvailable = true)).isFalse()
        assertThat(fromIgdb.needsCompletionRefresh(providerAvailable = true)).isFalse()
    }

    // ScreenScraper alone is the common setup and carries no completion field,
    // so this must not drag the whole library into every "only missing" pass.
    @Test
    fun `nothing is refetched when no provider could supply a completion time`() {
        val metadata = GameMetadata(lastScrapedEpochMs = 123L)

        assertThat(metadata.needsCompletionRefresh(providerAvailable = false)).isFalse()
    }
}
