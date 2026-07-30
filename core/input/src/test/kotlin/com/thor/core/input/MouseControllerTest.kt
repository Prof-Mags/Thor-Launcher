package com.thor.core.input

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.MouseSettings
import org.junit.Before
import org.junit.Test

/**
 * The pointer's geometry.
 *
 * Two panels laid end to end is arithmetic nobody can eyeball, and every mistake
 * in it looks the same from the front: the cursor jumps, sticks at a seam, or
 * vanishes onto a display that is not being looked at. None of that throws.
 */
class MouseControllerTest {

    private lateinit var mouse: MouseController

    /** A Thor-shaped pair: a wide top panel over a shorter bottom one. */
    private val panels = listOf(
        PointerDisplay(displayId = 0, widthPx = 1920, heightPx = 1080, topOffsetPx = 0),
        PointerDisplay(displayId = 1, widthPx = 1920, heightPx = 720, topOffsetPx = 1080),
    )

    @Before
    fun setUp() {
        mouse = MouseController().apply {
            updateSettings(MouseSettings(enabled = true, speed = 1_000f))
            setDisplays(panels)
        }
    }

    @Test
    fun `the pointer starts inactive and has no position`() {
        assertThat(mouse.isActive).isFalse()
        assertThat(mouse.state.value.position).isNull()
    }

    @Test
    fun `raising the pointer puts it in the middle of the top panel`() {
        mouse.setActive(true)

        val position = requireNotNull(mouse.state.value.position)
        assertThat(position.displayId).isEqualTo(0)
        assertThat(position.x).isWithin(1f).of(960f)
        assertThat(position.y).isWithin(1f).of(540f)
    }

    /** The whole point of the stacked space. */
    @Test
    fun `moving off the bottom of the top panel continues onto the second`() {
        mouse.setActive(true)
        // 1000px down from the middle of a 1080-tall panel lands 460px into the
        // one below it.
        mouse.moveByStick(x = 0f, y = 1f, deltaSeconds = 1f)

        val position = requireNotNull(mouse.state.value.position)
        assertThat(position.displayId).isEqualTo(1)
        assertThat(position.y).isWithin(2f).of(460f)
    }

    @Test
    fun `moving back up returns to the panel above`() {
        mouse.setActive(true)
        mouse.moveByStick(x = 0f, y = 1f, deltaSeconds = 1f)
        assertThat(requireNotNull(mouse.state.value.position).displayId).isEqualTo(1)

        mouse.moveByStick(x = 0f, y = -1f, deltaSeconds = 1f)

        val position = requireNotNull(mouse.state.value.position)
        assertThat(position.displayId).isEqualTo(0)
        assertThat(position.y).isWithin(2f).of(540f)
    }

    @Test
    fun `the pointer cannot leave the stack`() {
        mouse.setActive(true)
        repeat(20) { mouse.moveByStick(x = 1f, y = 1f, deltaSeconds = 1f) }

        val bottom = requireNotNull(mouse.state.value.position)
        assertThat(bottom.displayId).isEqualTo(1)
        assertThat(bottom.y).isAtMost(719f)
        assertThat(bottom.x).isAtMost(1919f)

        repeat(40) { mouse.moveByStick(x = -1f, y = -1f, deltaSeconds = 1f) }

        val top = requireNotNull(mouse.state.value.position)
        assertThat(top.displayId).isEqualTo(0)
        assertThat(top.x).isAtLeast(0f)
        assertThat(top.y).isAtLeast(0f)
    }

    /** With spanning off, the seam is a wall. */
    @Test
    fun `the pointer stays on one panel when spanning is off`() {
        mouse.updateSettings(
            MouseSettings(enabled = true, speed = 1_000f, spanDisplays = false),
        )
        mouse.setActive(true)
        repeat(10) { mouse.moveByStick(x = 0f, y = 1f, deltaSeconds = 1f) }

        assertThat(requireNotNull(mouse.state.value.position).displayId).isEqualTo(0)
    }

    @Test
    fun `a centred stick does not drift`() {
        mouse.setActive(true)
        val before = requireNotNull(mouse.state.value.position)
        repeat(50) { mouse.moveByStick(x = 0f, y = 0f, deltaSeconds = 0.016f) }
        val after = requireNotNull(mouse.state.value.position)

        assertThat(after.x).isEqualTo(before.x)
        assertThat(after.y).isEqualTo(before.y)
    }

    /** Nothing moves the pointer while it is down. */
    @Test
    fun `movement is ignored while inactive`() {
        mouse.moveByStick(x = 1f, y = 1f, deltaSeconds = 1f)
        assertThat(mouse.state.value.position).isNull()
    }

    @Test
    fun `the pointer cannot be raised while the feature is off`() {
        mouse.updateSettings(MouseSettings(enabled = false))
        mouse.setActive(true)
        assertThat(mouse.isActive).isFalse()
    }

    /** Turning the feature off while the pointer is up must put it away. */
    @Test
    fun `disabling the feature lowers a raised pointer`() {
        mouse.setActive(true)
        assertThat(mouse.isActive).isTrue()

        mouse.updateSettings(MouseSettings(enabled = false))

        assertThat(mouse.isActive).isFalse()
        assertThat(mouse.state.value.position).isNull()
    }

    @Test
    fun `toggling flips the pointer both ways`() {
        mouse.toggle()
        assertThat(mouse.isActive).isTrue()
        mouse.toggle()
        assertThat(mouse.isActive).isFalse()
    }

    /**
     * A single panel is the split-screen and single-screen modes, where there is
     * no second display to cross onto.
     */
    @Test
    fun `a single panel still works`() {
        mouse.setDisplays(listOf(panels.first()))
        mouse.setActive(true)
        repeat(10) { mouse.moveByStick(x = 0f, y = 1f, deltaSeconds = 1f) }

        val position = requireNotNull(mouse.state.value.position)
        assertThat(position.displayId).isEqualTo(0)
        assertThat(position.y).isAtMost(1079f)
    }

    /** Raised before the shell has reported the hardware. */
    @Test
    fun `no panels is survivable`() {
        val bare = MouseController().apply {
            updateSettings(MouseSettings(enabled = true))
        }
        bare.setActive(true)
        bare.moveByStick(x = 1f, y = 1f, deltaSeconds = 1f)
        // No crash, and nothing claimed about where the pointer is.
        assertThat(bare.state.value.position).isNull()
    }

    @Test
    fun `clicks are counted so the cursor can acknowledge them`() {
        mouse.setActive(true)
        val before = mouse.state.value.clickTick
        mouse.notifyClicked()
        assertThat(mouse.state.value.clickTick).isEqualTo(before + 1)
    }

    @Test
    fun `keyboard requests are counted`() {
        val before = mouse.keyboardRequests.value
        mouse.requestKeyboard()
        assertThat(mouse.keyboardRequests.value).isEqualTo(before + 1)
    }
}
