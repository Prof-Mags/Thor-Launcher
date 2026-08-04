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
     * The README quotes this figure, and had been quoting a guess.
     *
     * It claimed 62 before a dozen were added and 73 afterwards, while the table
     * actually held 72 — edited by arithmetic on the last wrong number rather than
     * read off the list. Failing here is the reminder: change the count, then
     * change the README to match.
     */
    @Test
    fun `the emulator count is what the README claims`() {
        assertThat(EmulatorRegistry.KNOWN).hasSize(EMULATORS_IN_README)
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

    /**
     * Systems that could only be run through a many-core front-end.
     *
     * Each of these needed a core downloaded and assigned before RetroArch would
     * open anything, which is a long way from "it launched". PS3 was worse than
     * awkward: no core runs it at all, so every PS3 game in the library was
     * unlaunchable.
     */
    @Test
    fun `the systems that had no dedicated emulator now have one`() {
        listOf("ps3", "mastersystem", "gamegear", "sg1000", "segacd").forEach { platformId ->
            val dedicated = EmulatorRegistry.candidatesFor(platformId).filterNot { it.isFrontEnd }

            assertThat(dedicated).isNotEmpty()
        }
    }

    /**
     * A front-end never outranks a dedicated emulator.
     *
     * This is the whole of the automatic pick: [EmulatorRegistry.candidatesFor]
     * is ordered, and the launcher takes the first *installed* candidate.
     */
    @Test
    fun `dedicated emulators are offered before the many-core front-ends`() {
        BuiltInPlatforms.ALL.forEach { platform ->
            val candidates = EmulatorRegistry.candidatesFor(platform.id)
            val firstFrontEnd = candidates.indexOfFirst { it.isFrontEnd }
            val lastDedicated = candidates.indexOfLast { !it.isFrontEnd }

            if (firstFrontEnd >= 0 && lastDedicated >= 0) {
                assertThat(lastDedicated).isLessThan(firstFrontEnd)
            }
        }
    }

    /** Two specs for one package would make the picker show it twice. */
    @Test
    fun `no package is listed twice`() {
        val packages = EmulatorRegistry.KNOWN.map { it.packageName }

        assertThat(packages).containsNoDuplicates()
    }
}

/** Kept in step with the figure in README.md, by the test above. */
private const val EMULATORS_IN_README = 80
