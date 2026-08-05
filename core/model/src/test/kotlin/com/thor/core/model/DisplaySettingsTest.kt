package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What Couch Mode's interface size setting actually means.
 *
 * It used to mean two things at once: the user's preference, and a constant
 * three-quarter correction folded into the same number because couch mode is
 * drawn for a room rather than for a hand. A constant could never be that
 * correction — it never asks how large the screen is — so it is a design canvas
 * now, `COUCH_SHORT_SIDE`, which is the size the interface is laid out against
 * rather than a multiplier applied to whatever density a panel happens to have.
 *
 * What is left here is the preference and its clamp.
 */
class DisplaySettingsTest {

    @Test
    fun `the default setting is the designed size, unmodified`() {
        val actual = DisplaySettings.couchDensityScale(DisplaySettings.DEFAULT_COUCH_UI_SCALE)

        // 100% means "the size couch mode was designed at", which the canvas now
        // states rather than a number multiplied into the density.
        assertThat(actual).isWithin(0.001f).of(1f)
    }

    /**
     * The slider reads as a plain percentage.
     *
     * Half as much again on the setting is half as much again on the screen.
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

        assertThat(floor).isWithin(0.001f).of(DisplaySettings.MIN_COUCH_UI_SCALE)
        assertThat(ceiling).isWithin(0.001f).of(DisplaySettings.MAX_COUCH_UI_SCALE)
    }

    /**
     * The range still spans both sides of the designed size.
     *
     * Down to three quarters and up to nearly half again, around a default that
     * is the designed size rather than a rebased one — so every setting this ever
     * opened at is still reachable.
     */
    @Test
    fun `the range reaches either side of the designed size`() {
        assertThat(DisplaySettings.MIN_COUCH_UI_SCALE).isLessThan(1f)
        assertThat(DisplaySettings.MAX_COUCH_UI_SCALE).isGreaterThan(1f)
    }
}
