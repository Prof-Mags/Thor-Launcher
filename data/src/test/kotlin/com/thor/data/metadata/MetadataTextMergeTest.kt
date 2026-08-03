package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetadataTextMergeTest {

    @Test
    fun `blank persisted description is replaced by scraped prose`() {
        val result = selectNonBlankMetadataText(
            current = "   ",
            locked = false,
            candidates = listOf(null, "A complete scraped description."),
        )

        assertThat(result).isEqualTo("A complete scraped description.")
    }

    @Test
    fun `locked blank description remains untouched`() {
        val result = selectNonBlankMetadataText(
            current = "",
            locked = true,
            candidates = listOf("Scraped prose"),
        )

        assertThat(result).isEmpty()
    }
}
