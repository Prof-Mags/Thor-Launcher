package com.thor.data.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WikipediaDescriptionTest {

    @Test
    fun `extract is normalized into persistable prose`() {
        val result = cleanWikipediaExtract(
            "  Batman: Arkham City is an action-adventure game.\n" +
                "It follows Batman through a fortified district.  ",
        )

        assertThat(result).isEqualTo(
            "Batman: Arkham City is an action-adventure game. " +
                "It follows Batman through a fortified district.",
        )
    }

    @Test
    fun `tiny extracts are rejected`() {
        assertThat(cleanWikipediaExtract("Video game.")).isNull()
    }
}
