package com.thor.feature.home.couch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The arithmetic behind couch mode's drawn backgrounds.
 *
 * None of this can be judged by looking at it in a unit test — whether a ridge
 * is a pleasing shape is a question for a television. What can be checked is the
 * part that fails as a visible defect rather than as a matter of taste: a scroll
 * that jumps at the end of its cycle, a mote that pops into existence at full
 * brightness, or a field that scatters itself differently every time the theme
 * changes. Those are all properties of these four functions.
 */
class CouchWallpaperTest {

    // ---- Ridge profile ------------------------------------------------------

    /**
     * The same phase gives the same hills, every time.
     *
     * This composable is re-entered on a rotation, a theme change and a UI scale
     * change. A profile that came from a random source would redraw the horizon
     * on each of those, behind panels somebody is reading.
     */
    @Test
    fun `the ridge profile is a pure function of its phase`() {
        val once = (0..40).map { couchRidgeHeight(it * 0.1f) }
        val again = (0..40).map { couchRidgeHeight(it * 0.1f) }

        assertThat(again).isEqualTo(once)
    }

    /** Two sines at 0.65 and 0.35 cannot leave the band the amplitude assumes. */
    @Test
    fun `the ridge profile stays inside the amplitude it is given`() {
        val samples = (0..400).map { couchRidgeHeight(it * 0.05f) }

        samples.forEach {
            assertThat(it).isAtLeast(-1f)
            assertThat(it).isAtMost(1f)
        }
    }

    @Test
    fun `the ridge profile actually varies`() {
        // Guards against the harmonics cancelling to a flat line, which would
        // draw as a rectangle and look deliberate.
        val samples = (0..100).map { couchRidgeHeight(it * 0.05f) }

        assertThat(samples.max() - samples.min()).isGreaterThan(1f)
    }

    // ---- Perspective grid ---------------------------------------------------

    /**
     * The scroll has no seam.
     *
     * The rows are positioned by distance from the horizon taken modulo one, so
     * the row leaving the bottom edge and the row appearing at the horizon are
     * the same row. If that failed the whole grid would jump once per cycle —
     * every nine seconds, in the corner of the eye, forever.
     */
    @Test
    fun `the grid returns to the same rows after a full cycle`() {
        val start = (0 until 14).map { couchGridRow(it, 14, 0f) }.sorted()
        val wrapped = (0 until 14).map { couchGridRow(it, 14, 1f) }.sorted()

        // Within a tolerance rather than exactly: `i / 14 + 1 - 1` does not
        // round-trip in a float, and the residue is about 1e-7 of the panel's
        // height — four orders of magnitude below the pixel this decides.
        start.zip(wrapped).forEach { (before, after) ->
            assertThat(after).isWithin(SEAM_TOLERANCE).of(before)
        }
    }

    @Test
    fun `every grid row sits between the horizon and the bottom edge`() {
        val phases = listOf(0f, 0.13f, 0.5f, 0.87f, 0.999f)

        phases.forEach { phase ->
            (0 until 14).forEach { index ->
                val u = couchGridRow(index, 14, phase)
                assertThat(u).isAtLeast(0f)
                assertThat(u).isLessThan(1f)
            }
        }
    }

    /** A grid with no rows is a division by zero waiting for a resize. */
    @Test
    fun `an empty grid does not divide by its row count`() {
        assertThat(couchGridRow(0, 0, 0.5f)).isEqualTo(0f)
    }

    // ---- Embers -------------------------------------------------------------

    @Test
    fun `mote positions are spread rather than clustered`() {
        val seeds = (0 until 34).map(::couchEmberSeed)

        seeds.forEach {
            assertThat(it).isAtLeast(0f)
            assertThat(it).isLessThan(1f)
        }
        // Two motes on the same track read as one brighter mote, which is the
        // whole reason the step is irrational rather than a neat fraction.
        val sorted = seeds.sorted()
        val closest = sorted.zipWithNext { a, b -> b - a }.min()
        assertThat(closest).isGreaterThan(0.005f)
    }

    /**
     * A mote never leaves the panel.
     *
     * Its rise is wrapped, and the alpha that hides the wrap is a sine of it —
     * so a rise outside 0..1 would not merely put a mote off screen, it would
     * make the fade negative and the mote reappear at the wrong end.
     */
    @Test
    fun `a mote rise always wraps into the panel`() {
        val phases = listOf(0f, 0.25f, 0.5f, 0.75f, 1f, 3.4f)

        phases.forEach { phase ->
            (0 until 34).forEach { index ->
                val rise = couchEmberRise(couchEmberSeed(index), phase)
                assertThat(rise).isAtLeast(0f)
                assertThat(rise).isLessThan(1f)
            }
        }
    }

    @Test
    fun `motes do not all travel at the same speed`() {
        // A field moving as one block reads as a texture being panned rather
        // than as anything rising through anything.
        val phase = 0.5f
        val rises = (0 until 34).map { couchEmberRise(couchEmberSeed(it), phase) }

        assertThat(rises.distinct()).hasSize(rises.size)
    }

    private companion object {
        /** Far below one pixel on any panel this runs on. */
        const val SEAM_TOLERANCE = 1e-5f
    }
}
