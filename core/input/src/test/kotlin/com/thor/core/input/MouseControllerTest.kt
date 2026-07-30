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

    /**
     * The bug that made the pointer useless outside the launcher.
     *
     * Panels were only ever declared by the launcher's composition, so a service
     * connected without THOR having run had none. Raising the pointer then produced
     * one that was active with no position — nothing draws, nothing clicks, and the
     * chord looks dead. It reported as "mouse mode does not work outside THOR",
     * which sounds like an input problem and was a bootstrapping one.
     */
    @Test
    fun `a pointer raised before the panels are known settles once they arrive`() {
        val bare = MouseController().apply { updateSettings(MouseSettings(enabled = true)) }

        bare.setActive(true)
        assertThat(bare.isActive).isTrue()
        assertThat(bare.state.value.position).isNull()

        bare.setFallbackDisplays(panels)

        val position = requireNotNull(bare.state.value.position)
        assertThat(position.displayId).isEqualTo(0)
        // Centred, not left in the corner it had to start from.
        assertThat(position.x).isWithin(1f).of(960f)
        assertThat(position.y).isWithin(1f).of(540f)
    }

    @Test
    fun `raising with no panels asks for some`() {
        val bare = MouseController().apply { updateSettings(MouseSettings(enabled = true)) }
        var asked = 0
        bare.onPanelsNeeded { asked++ }

        bare.setActive(true)

        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun `the launcher's panels beat the service's`() {
        val fallback = listOf(
            PointerDisplay(displayId = 9, widthPx = 100, heightPx = 100, topOffsetPx = 0),
        )
        // setUp already declared the launcher's; the service must not override it.
        mouse.setFallbackDisplays(fallback)
        mouse.setActive(true)

        assertThat(requireNotNull(mouse.state.value.position).displayId).isEqualTo(0)
    }

    /**
     * Arbitration between the launcher and the accessibility service.
     *
     * Exactly one of them may act on a press. Both acting double-toggles the
     * chord and clicks twice; neither acting is the pointer doing nothing at all,
     * which is unfixable from the front because the buttons that would dismiss it
     * are the ones being dropped.
     *
     * The rule is one fact and nothing else: is the service running. Earlier
     * versions asked where the cursor was and who held focus, and every one of
     * those inputs could be stale at the moment it was consulted.
     */
    @Test
    fun `the launcher owns the pointer while no service is running`() {
        mouse.setActive(true)

        assertThat(mouse.launcherOwnsPointer).isTrue()
    }

    @Test
    fun `the service owns the pointer whenever it is connected`() {
        mouse.setServiceConnected(true)
        mouse.setActive(true)

        assertThat(mouse.launcherOwnsPointer).isFalse()

        // Wherever the cursor is, and whatever is on screen. Nothing else counts.
        mouse.moveByStep(0f, 40f)
        mouse.setActivityVisible(true)
        mouse.setPresentationVisible(true)
        assertThat(mouse.launcherOwnsPointer).isFalse()
    }

    @Test
    fun `ownership returns to the launcher when the service goes away`() {
        mouse.setServiceConnected(true)
        mouse.setServiceConnected(false)

        assertThat(mouse.launcherOwnsPointer).isTrue()
    }

    /** Only decides whether opening THOR's keyboard would be visible. */
    @Test
    fun `the launcher is in front while either surface is on screen`() {
        assertThat(mouse.launcherForeground).isFalse()

        mouse.setPresentationVisible(true)
        assertThat(mouse.launcherForeground).isTrue()

        mouse.setPresentationVisible(false)
        mouse.setActivityVisible(true)
        assertThat(mouse.launcherForeground).isTrue()

        mouse.setActivityVisible(false)
        assertThat(mouse.launcherForeground).isFalse()
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
