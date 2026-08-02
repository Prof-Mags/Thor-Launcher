package com.thor.data.scanner

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.BuiltInPlatforms
import org.junit.Test

/**
 * The table, and the one decision it makes on its own.
 *
 * [EmulatorRegistry.candidatesFor] decides which emulator opens a game when the
 * user has not picked one, and it decides it by order alone — so the order is
 * behaviour, and it is tested here rather than left to the reading of a list
 * seventy entries long.
 */
class EmulatorRegistryTest {

    @Test
    fun `a dedicated emulator is offered before a multi-core front end`() {
        for (platform in BuiltInPlatforms.ALL) {
            val candidates = EmulatorRegistry.candidatesFor(platform.id)
            val firstFrontEnd = candidates.indexOfFirst { it.isFrontEnd }
            val lastDedicated = candidates.indexOfLast { !it.isFrontEnd }

            if (firstFrontEnd >= 0 && lastDedicated >= 0) {
                assertThat(firstFrontEnd).isGreaterThan(lastDedicated)
            }
        }
    }

    /**
     * The case that prompted the ordering: RetroArch claims every system in the
     * table, so before this it won any automatic pick it was installed for.
     */
    @Test
    fun `DS games prefer melonDS and DraStic over RetroArch`() {
        val order = EmulatorRegistry.candidatesFor("nds").map { it.packageName }

        assertThat(order).containsAtLeast("me.magnum.melonds", "com.dsemu.drastic")
        assertThat(order.indexOf("me.magnum.melonds"))
            .isLessThan(order.indexOf("com.retroarch"))
        assertThat(order.indexOf("com.dsemu.drastic"))
            .isLessThan(order.indexOf("com.retroarch"))
    }

    @Test
    fun `every system with any emulator at all can still reach one`() {
        // RetroArch claims them all, so this is really a check that the sort did
        // not drop entries — a filter is easy to write where a sort was meant.
        for (platform in BuiltInPlatforms.ALL) {
            assertThat(EmulatorRegistry.candidatesFor(platform.id)).isNotEmpty()
        }
    }

    @Test
    fun `package names are unique`() {
        val packages = EmulatorRegistry.KNOWN.map { it.packageName }

        assertThat(packages).containsNoDuplicates()
    }

    /**
     * A spec claiming a system the launcher does not have is dead weight: nothing
     * ever asks for that id, so the entry is never offered and the mistake is
     * invisible.
     */
    @Test
    fun `every claimed platform is one the launcher knows`() {
        val known = BuiltInPlatforms.ALL.map { it.id }.toSet()

        for (spec in EmulatorRegistry.KNOWN) {
            assertThat(known).containsAtLeastElementsIn(spec.platformIds)
        }
    }

    @Test
    fun `the newly added emulators are reachable for their systems`() {
        fun packagesFor(platformId: String) =
            EmulatorRegistry.candidatesFor(platformId).map { it.packageName }

        assertThat(packagesFor("3ds")).containsAtLeast(
            "org.azahar_emu.azahar",
            "com.panda3ds.pandroid",
            "org.citra.citra_emu",
        )
        assertThat(packagesFor("switch")).contains("org.stratoemu.strato")
        assertThat(packagesFor("nds")).contains("com.hydra.noods")
        assertThat(packagesFor("gba")).contains("com.johnemulators.johngba")
        assertThat(packagesFor("gbc")).contains("com.explusalpha.GbcEmu")
        assertThat(packagesFor("nes")).contains("com.johnemulators.johnness")
    }
}
