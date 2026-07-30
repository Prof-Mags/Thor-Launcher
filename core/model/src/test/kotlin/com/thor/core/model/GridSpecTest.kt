package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The pinch gesture is the one piece of grid geometry with non-obvious
 * behaviour: past a threshold it must change the matrix rather than keep
 * scaling, and it must never produce a grid the renderer cannot lay out.
 */
class GridSpecTest {

    @Test
    fun `default spec is already within bounds`() {
        val coerced = GridSpec.DEFAULT.coerced()
        assertThat(coerced).isEqualTo(GridSpec.DEFAULT)
    }

    @Test
    fun `coerced clamps out of range values`() {
        val absurd = GridSpec(
            columns = 99,
            rows = -4,
            iconScale = 12f,
            spacingDp = -20,
            paddingDp = 900,
            labelLines = 7,
        ).coerced()

        assertThat(absurd.columns).isEqualTo(GridSpec.MAX_COLUMNS)
        assertThat(absurd.rows).isEqualTo(GridSpec.MIN_ROWS)
        assertThat(absurd.iconScale).isEqualTo(GridSpec.MAX_ICON_SCALE)
        assertThat(absurd.spacingDp).isEqualTo(0)
        assertThat(absurd.paddingDp).isEqualTo(64)
        assertThat(absurd.labelLines).isEqualTo(2)
    }

    @Test
    fun `the default spec sits exactly on a preset`() {
        // Otherwise the first pinch would jump to a different size rather than
        // stepping one interval from where the user actually is.
        val preset = GridSpec.DEFAULT.preset
        assertThat(preset.columns).isEqualTo(GridSpec.DEFAULT.columns)
        assertThat(preset.rows).isEqualTo(GridSpec.DEFAULT.rows)
        assertThat(preset.spacingDp).isEqualTo(GridSpec.DEFAULT.spacingDp)
    }

    @Test
    fun `zooming in steps to the next coarser preset`() {
        val pinched = GridSpec.DEFAULT.pinched(1.4f)
        val expected = GridSpec.PRESETS[GridSpec.PRESETS.indexOf(GridSpec.DEFAULT.preset) - 1]

        assertThat(pinched.columns).isEqualTo(expected.columns)
        assertThat(pinched.rows).isEqualTo(expected.rows)
        // Spacing travels with the matrix; that pairing is the whole point of
        // presets, and leaving it behind is what made the grid look crowded.
        assertThat(pinched.spacingDp).isEqualTo(expected.spacingDp)
    }

    @Test
    fun `zooming out steps to the next denser preset`() {
        val pinched = GridSpec.DEFAULT.pinched(0.7f)
        val expected = GridSpec.PRESETS[GridSpec.PRESETS.indexOf(GridSpec.DEFAULT.preset) + 1]

        assertThat(pinched.columns).isEqualTo(expected.columns)
        assertThat(pinched.rows).isEqualTo(expected.rows)
        assertThat(pinched.spacingDp).isEqualTo(expected.spacingDp)
    }

    @Test
    fun `a pinch always lands exactly on a preset`() {
        var spec = GridSpec.DEFAULT
        val factors = listOf(2.5f, 0.3f, 4f, 0.1f, 1.4f, 0.6f, 3f, 0.2f)

        factors.forEach { factor ->
            spec = spec.pinched(factor)
            // No in-between states are reachable, so no column count can end up
            // paired with another size's spacing.
            assertThat(GridSpec.PRESETS).contains(spec.preset)
            assertThat(spec.columns).isEqualTo(spec.preset.columns)
            assertThat(spec.spacingDp).isEqualTo(spec.preset.spacingDp)
            assertThat(spec.iconScale).isEqualTo(1f)
        }
    }

    @Test
    fun `pinching in at the coarsest preset stays there`() {
        val coarsest = GridSpec.PRESETS.first().applyTo(GridSpec.DEFAULT)
        val pinched = coarsest.pinched(4f)

        assertThat(pinched.columns).isEqualTo(coarsest.columns)
        assertThat(pinched.rows).isEqualTo(coarsest.rows)
    }

    @Test
    fun `pinching out at the densest preset stays there`() {
        val densest = GridSpec.PRESETS.last().applyTo(GridSpec.DEFAULT)
        val pinched = densest.pinched(0.1f)

        assertThat(pinched.columns).isEqualTo(densest.columns)
        assertThat(pinched.rows).isEqualTo(densest.rows)
    }

    @Test
    fun `every preset is renderable and distinct`() {
        GridSpec.PRESETS.forEach { preset ->
            val spec = preset.applyTo(GridSpec.DEFAULT)
            assertThat(spec.columns).isIn(GridSpec.MIN_COLUMNS..GridSpec.MAX_COLUMNS)
            assertThat(spec.rows).isIn(GridSpec.MIN_ROWS..GridSpec.MAX_ROWS)
            assertThat(spec.cellsPerPage).isGreaterThan(0)
        }
        // Two presets with the same matrix would be an unreachable pinch step.
        assertThat(GridSpec.PRESETS.map { it.columns to it.rows }).containsNoDuplicates()
    }

    @Test
    fun `presets get denser and their spacing tightens monotonically`() {
        val cells = GridSpec.PRESETS.map { it.cellsPerPage }
        assertThat(cells).isInStrictOrder()

        // Denser layouts need proportionally less gutter, or the gaps swallow
        // the icons; looser ones need more, or the icons run together.
        val spacings = GridSpec.PRESETS.map { -it.spacingDp }
        assertThat(spacings).isInOrder()
    }

    @Test
    fun `coercion guarantees the invariants the renderer depends on`() {
        // The grid divides by `columns` and passes `labelLines` to Text; both
        // throw on a zero. A settings file from a restore or an older schema
        // can carry anything, so coercion has to make these safe.
        val hostile = listOf(
            GridSpec(columns = 0, rows = 0, labelLines = 0),
            GridSpec(columns = -5, rows = -5, labelLines = -1),
            GridSpec(columns = Int.MIN_VALUE, rows = Int.MIN_VALUE, labelLines = Int.MIN_VALUE),
        )

        hostile.forEach { spec ->
            val safe = spec.coerced()
            assertThat(safe.columns).isAtLeast(GridSpec.MIN_COLUMNS)
            assertThat(safe.rows).isAtLeast(GridSpec.MIN_ROWS)
            assertThat(safe.labelLines).isAtLeast(1)
            assertThat(safe.cellsPerPage).isGreaterThan(0)
        }
    }

    @Test
    fun `cell index round trips through placement`() {
        val spec = GridSpec.DEFAULT
        val placement = GridPlacement.fromCellIndex("game:test", pageIndex = 2, cellIndex = 14, columns = spec.columns)

        assertThat(placement.row).isEqualTo(14 / spec.columns)
        assertThat(placement.column).isEqualTo(14 % spec.columns)
        assertThat(placement.cellIndex(spec.columns)).isEqualTo(14)
    }
}
