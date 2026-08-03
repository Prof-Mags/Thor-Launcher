package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.GameMetadata
import org.junit.Test

class RawgMetadataMergeTest {

    @Test
    fun `detail response fills prose and credits missing from search`() {
        val search = GameMetadata(
            genres = listOf("Action"),
            rating = 82,
        )
        val details = GameMetadata(
            description = "A detailed game description.",
            developer = "Rocksteady Studios",
            publisher = "Warner Bros. Interactive",
            providerSources = mapOf(GameMetadata.FIELD_DESCRIPTION to RawgProvider.ID),
        )

        val merged = mergeRawgDetailMetadata(search, details)

        assertThat(merged.description).isEqualTo("A detailed game description.")
        assertThat(merged.developer).isEqualTo("Rocksteady Studios")
        assertThat(merged.publisher).isEqualTo("Warner Bros. Interactive")
        assertThat(merged.genres).containsExactly("Action")
        assertThat(merged.rating).isEqualTo(82)
        assertThat(merged.providerSources[GameMetadata.FIELD_DESCRIPTION])
            .isEqualTo(RawgProvider.ID)
    }
}
