package com.thor.feature.topscreen

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameDescriptionLinesTest {

    @Test
    fun `fills the space the panel has left over`() {
        assertThat(gameDescriptionLines(available = 96.dp, lineHeight = 16.dp)).isEqualTo(6)
    }

    @Test
    fun `a part line is not counted`() {
        // 95 / 16 is 5.9 lines — the sixth would be clipped in half.
        assertThat(gameDescriptionLines(available = 95.dp, lineHeight = 16.dp)).isEqualTo(5)
    }

    @Test
    fun `a squeezed panel still shows something`() {
        assertThat(gameDescriptionLines(available = 8.dp, lineHeight = 16.dp)).isEqualTo(2)
        assertThat(gameDescriptionLines(available = 0.dp, lineHeight = 16.dp)).isEqualTo(2)
    }

    @Test
    fun `an unmeasurable line height does not divide by zero`() {
        assertThat(gameDescriptionLines(available = 96.dp, lineHeight = 0.dp)).isEqualTo(2)
        assertThat(gameDescriptionLines(available = 96.dp, lineHeight = (-4).dp)).isEqualTo(2)
    }
}
