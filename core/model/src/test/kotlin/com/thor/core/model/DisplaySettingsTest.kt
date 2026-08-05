package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What Couch Mode's interface size setting actually means.
 *
 * The number the user sees and the number the density is multiplied by are not
 * the same, and they are not meant to be - the setting is a percentage of a size
 * chosen for a television, not of the handheld panel's own density. Getting that
 * wrong is invisible in code and unmistakable on a wall.
 */
class DisplaySettingsTest {

    @Test
    fun `the default setting draws at the size chosen for a television`() {
        val actual = DisplaySettings.couchDensityScale(DisplaySettings.DEFAULT_COUCH_UI_SCALE)

        assertThat(actual).isWithin(0.001f).of(DisplaySettings.COUCH_BASE_SCALE)
    }

    /**
     * The slider still reads as a plain percentage.
     *
     * Half as much again on the setting is half as much again on the screen; the
     * rebasing moves where 100% sits, it does not bend the scale around it.
     */
    @Test
    fun `the setting stays proportional`() {
        val full = DisplaySettings.couchDensityScale(1.0f)
        val more = DisplaySettings.couchDensityScale(1.2f)

        assertThat(more / full).isWithin(0.001f).of(1.2f)
    }

    /**
     * Clamped centrally rather than at each screen.
     *
     * A value out of range from an import or an older release would otherwise
     * make one surface tiny while the one beside it was unaffected.
     */
    @Test
    fun `a value from outside the range cannot reach the density`() {
        val floor = DisplaySettings.couchDensityScale(0.1f)
        val ceiling = DisplaySettings.couchDensityScale(9f)

        assertThat(floor).isWithin(0.001f).of(
            DisplaySettings.MIN_COUCH_UI_SCALE * DisplaySettings.COUCH_BASE_SCALE,
        )
        assertThat(ceiling).isWithin(0.001f).of(
            DisplaySettings.MAX_COUCH_UI_SCALE * DisplaySettings.COUCH_BASE_SCALE,
        )
    }

    /**
     * The top of the range still reaches the size couch mode used to open at.
     *
     * Rebasing without widening would have made the old default unreachable, so
     * anybody who liked it would have had no way back to it.
     */
    @Test
    fun `the largest setting still reaches the old default size`() {
        val largest = DisplaySettings.couchDensityScale(DisplaySettings.MAX_COUCH_UI_SCALE)

        assertThat(largest).isAtLeast(1f)
    }
}
