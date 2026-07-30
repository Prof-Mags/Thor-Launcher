package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Snapping an arbitrary matrix onto a preset.
 *
 * The matrix can be set directly from Settings, so it will not always sit on a
 * preset. Pinching from there has to step to the neighbour of wherever the user
 * actually is — snapping to a distant default would make the first pinch jump.
 */
class GridPresetTest {

    @Test
    fun `nearest returns the exact preset when one matches`() {
        GridSpec.PRESETS.forEach { preset ->
            val found = GridPreset.nearest(preset.columns, preset.rows)
            assertWithMessage("nearest for ${preset.columns}x${preset.rows}")
                .that(found)
                .isEqualTo(preset)
        }
    }

    @Test
    fun `nearest snaps an off-grid matrix to a comparable density`() {
        // 9x6 is denser than every preset, so it must land on the densest one
        // rather than somewhere in the middle.
        assertThat(GridPreset.nearest(9, 6)).isEqualTo(GridSpec.PRESETS.last())
        // 3x2 is the coarsest.
        assertThat(GridPreset.nearest(3, 2)).isEqualTo(GridSpec.PRESETS.first())
    }

    @Test
    fun `nearest never throws for any renderable matrix`() {
        for (columns in GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS) {
            for (rows in GridSpec.MIN_ROWS..GridSpec.MAX_ROWS) {
                assertThat(GridSpec.PRESETS).contains(GridPreset.nearest(columns, rows))
            }
        }
    }

    @Test
    fun `applying a preset discards a leftover manual zoom`() {
        // Otherwise the old scale would compound with the new matrix and produce
        // exactly the crowded in-between state presets exist to remove.
        val zoomed = GridSpec.DEFAULT.copy(iconScale = GridSpec.MAX_ICON_SCALE)
        val applied = GridSpec.PRESETS.first().applyTo(zoomed)

        assertThat(applied.iconScale).isEqualTo(1f)
    }

    @Test
    fun `applying a preset keeps unrelated preferences`() {
        val customised = GridSpec.DEFAULT.copy(
            showLabels = false,
            labelLines = 2,
            iconShape = IconShape.CIRCLE,
        )
        val applied = GridSpec.PRESETS.last().applyTo(customised)

        assertThat(applied.showLabels).isFalse()
        assertThat(applied.labelLines).isEqualTo(2)
        assertThat(applied.iconShape).isEqualTo(IconShape.CIRCLE)
    }

    @Test
    fun `stepping through every preset stays within the renderable range`() {
        // Walked in both directions from both ends, because the clamp at the
        // boundaries is where an off-by-one would hide.
        var spec = GridSpec.PRESETS.first().applyTo(GridSpec.DEFAULT)
        repeat(GridSpec.PRESETS.size + 2) {
            spec = spec.pinched(0.5f)
            assertThat(spec.columns).isIn(GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS)
            assertThat(spec.rows).isIn(GridSpec.MIN_ROWS..GridSpec.MAX_ROWS)
        }
        assertThat(spec.preset).isEqualTo(GridSpec.PRESETS.last())

        repeat(GridSpec.PRESETS.size + 2) {
            spec = spec.pinched(2f)
            assertThat(spec.columns).isIn(GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS)
            assertThat(spec.rows).isIn(GridSpec.MIN_ROWS..GridSpec.MAX_ROWS)
        }
        assertThat(spec.preset).isEqualTo(GridSpec.PRESETS.first())
    }

    @Test
    fun `every preset fits a landscape panel without wasting a dimension`() {
        // Cells are square, so a page far wider than it is tall in cell counts
        // leaves dead vertical space and undersized icons. Loosely bounded, but
        // enough to catch a preset like 10x2.
        GridSpec.PRESETS.forEach { preset ->
            val ratio = preset.columns.toFloat() / preset.rows
            assertWithMessage("aspect for ${preset.label}").that(ratio).isAtLeast(1.0f)
            assertWithMessage("aspect for ${preset.label}").that(ratio).isAtMost(2.5f)
        }
    }
}
