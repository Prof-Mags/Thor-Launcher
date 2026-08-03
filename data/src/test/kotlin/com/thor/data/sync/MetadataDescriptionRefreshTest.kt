package com.thor.data.sync

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.GameMetadata
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
}
