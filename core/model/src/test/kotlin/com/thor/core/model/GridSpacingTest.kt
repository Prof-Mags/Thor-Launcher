package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spacing is proportional to the cell, not a fixed dp.
 *
 * This is what stops the grid tightening up as it is pinched: the gap has to be
 * the same *share* of a cell at three columns and at ten. The bug being locked
 * out here is a constant gap, which at ten columns ate most of each cell and at
 * three was a hairline between huge icons.
 */
class GridSpacingTest {

    @Test
    fun `spacing fraction is independent of the matrix`() {
        val narrow = GridSpec.DEFAULT.copy(columns = 3, rows = 2)
        val wide = GridSpec.DEFAULT.copy(columns = 10, rows = 8)

        assertThat(narrow.spacingFraction).isEqualTo(wide.spacingFraction)
        assertThat(narrow.paddingFraction).isEqualTo(wide.paddingFraction)
    }

    @Test
    fun `the dp setting maps directly onto its proportion`() {
        // The setting reads as a percentage of a cell, which is what keeps the
        // numbers on the settings screen meaningful after the move to
        // proportional spacing. Derived from the constants rather than restated,
        // so retuning the defaults cannot silently break the mapping.
        assertThat(GridSpec.DEFAULT.spacingFraction)
            .isWithin(TOLERANCE)
            .of(GridSpec.DEFAULT_SPACING_DP / GridSpec.SPACING_DIVISOR)
        assertThat(GridSpec.DEFAULT.paddingFraction)
            .isWithin(TOLERANCE)
            .of(GridSpec.DEFAULT_PADDING_DP / GridSpec.PADDING_DIVISOR)
    }

    @Test
    fun `spacing is never zero even when the setting is`() {
        val none = GridSpec.DEFAULT.copy(spacingDp = 0, paddingDp = 0)

        // Cells that touch read as one block rather than as a grid, so there is a
        // floor regardless of what the setting says.
        assertThat(none.spacingFraction).isAtLeast(GridSpec.MIN_SPACING_FRACTION)
        assertThat(none.paddingFraction).isAtLeast(GridSpec.MIN_PADDING_FRACTION)
    }

    @Test
    fun `spacing is capped so it cannot starve the icon`() {
        val absurd = GridSpec.DEFAULT.copy(spacingDp = 48, paddingDp = 64)

        assertThat(absurd.spacingFraction).isAtMost(GridSpec.MAX_SPACING_FRACTION)
        assertThat(absurd.paddingFraction).isAtMost(GridSpec.MAX_PADDING_FRACTION)
    }

    @Test
    fun `spacing rises monotonically with the setting`() {
        val values = listOf(0, 6, 12, 18, 24, 30).map { dp ->
            GridSpec.DEFAULT.copy(spacingDp = dp).spacingFraction
        }

        assertThat(values).isInOrder()
    }

    @Test
    fun `every column count the user can pinch to stays within bounds`() {
        // Walking the whole reachable range, because the failure the user saw
        // only appeared at the extremes of the pinch.
        for (columns in GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) {
            val spec = GridSpec.DEFAULT.copy(columns = columns).coerced()
            assertThat(spec.spacingFraction).isAtLeast(GridSpec.MIN_SPACING_FRACTION)
            assertThat(spec.spacingFraction).isAtMost(GridSpec.MAX_SPACING_FRACTION)
        }
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
