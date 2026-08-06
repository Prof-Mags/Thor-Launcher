package com.thor.data.scanner

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    /**
     * Two emulators sharing a name is the same problem as sharing a package.
     *
     * The N64 entries were "M64Plus FZ Pro", "Mupen64Plus FZ (Pro)" and
     * "Mupen64Plus FZ" — three near-identical captions over three different
     * applications, two of them naming the wrong build. The picker is a list of
     * names, so a duplicate there is a choice the user cannot make correctly.
     */
    @Test
    fun `no two emulators wear the same name`() {
        val names = EmulatorRegistry.KNOWN.map { it.displayName }

        assertThat(names).containsNoDuplicates()
    }

    /**
     * Wii U was the last system with nothing that could open a game on it.
     *
     * RetroArch claims it the way it claims everything, by declaring the whole
     * platform list — and no libretro core runs Wii U, so the claim was empty and
     * every Wii U game in the library was unlaunchable.
     */
    @Test
    fun `Wii U has a dedicated emulator`() {
        val dedicated = EmulatorRegistry.candidatesFor("wiiu").filterNot { it.isFrontEnd }

        assertThat(dedicated.map { it.packageName }).contains("info.cemu.Cemu")
    }

    /**
     * The three the user asks for by name, reachable for their systems.
     *
     * Snes9x is here under the name it ships as: Broglia's build is the Snes9x
     * port on Android, and there is no application called plain "Snes9x" to add.
     */
    @Test
    fun `Cemu, M64Plus FZ and Snes9x are all offered`() {
        fun packagesFor(platformId: String) =
            EmulatorRegistry.candidatesFor(platformId).map { it.packageName }

        assertThat(packagesFor("wiiu")).contains("info.cemu.Cemu")
        assertThat(packagesFor("n64")).containsAtLeast(
            "org.mupen64plusae.v3.fzurita",
            "org.mupen64plusae.v3.fzurita.pro",
        )
        assertThat(packagesFor("snes")).contains("com.explusalpha.Snes9xPlus")
    }

    /**
     * The Mupen family is launched by component, not by the package's default.
     *
     * Without it the intent resolves to whatever the package declares as its VIEW
     * handler, and on this family that is the file browser — so Launch opened the
     * emulator on a list of folders rather than on the game. That is the failure
     * this whole table exists to prevent, and it is invisible from here: nothing
     * throws, the emulator simply opens on the wrong screen.
     */
    @Test
    fun `every Mupen build is launched through its splash activity`() {
        val mupen = EmulatorRegistry.KNOWN.filter { it.packageName.startsWith("org.mupen64plusae") }

        assertThat(mupen).isNotEmpty()
        mupen.forEach { spec ->
            assertWithMessage("activity for ${spec.packageName}")
                .that(spec.activityName)
                .isEqualTo("paulscode.android.mupen64plusae.SplashActivity")
        }
    }

    /**
     * The fault this was reported as: emulators installed and not detected.
     *
     * A table of exact ids can only name builds that existed when it was
     * written, and these projects ship nightlies, forks and store editions that
     * each append a segment to the id. Every one of those was invisible, on a
     * device where the emulator was sitting on the home screen.
     */
    @Test
    fun `a suffixed build resolves to the emulator it came from`() {
        val nightly = EmulatorRegistry.resolve("org.dolphinemu.dolphinemu.debug")

        assertThat(nightly).isNotNull()
        assertThat(nightly!!.packageName).isEqualTo("org.dolphinemu.dolphinemu")
        assertThat(nightly.platformIds).contains("gamecube")
    }

    @Test
    fun `a build with its own row keeps it rather than its parent's`() {
        // Yuzu Early Access is listed in its own right, and its id is also
        // Yuzu's with a segment appended. The longest base has to win.
        val early = EmulatorRegistry.resolve("org.yuzu.yuzu_emu.ea")

        assertThat(early?.packageName).isEqualTo("org.yuzu.yuzu_emu.ea")
        assertThat(early?.displayName).isEqualTo("Yuzu Early Access")
    }

    /**
     * The separating dot is what stops this being a bare prefix match.
     *
     * "My Boy! Free" is a different application from "My Boy!", not a build of
     * it, and its id happens to start with the same characters. Without the dot
     * it would inherit the paid version's row.
     */
    @Test
    fun `an unrelated package sharing a prefix is not treated as a variant`() {
        assertThat(EmulatorRegistry.resolve("com.fastemulator.gbafree")?.displayName)
            .isEqualTo("My Boy! Free")
    }

    @Test
    fun `a package with nothing to do with emulation resolves to nothing`() {
        assertThat(EmulatorRegistry.resolve("com.android.chrome")).isNull()
        assertThat(EmulatorRegistry.isKnownEmulator("com.android.chrome")).isFalse()
    }

    /** A variant has to be nameable, or the picker shows a raw application id. */
    @Test
    fun `a variant is named after the build it actually is`() {
        assertThat(EmulatorRegistry.displayNameFor("org.dolphinemu.dolphinemu"))
            .isEqualTo("Dolphin")
        assertThat(EmulatorRegistry.displayNameFor("org.dolphinemu.dolphinemu.debug"))
            .isEqualTo("Dolphin (Debug)")
    }

    @Test
    fun `every package in the table is unique`() {
        // Two rows with one id would make `resolve` depend on list order, and
        // the second of them would be unreachable.
        assertThat(EmulatorRegistry.KNOWN.map { it.packageName }).containsNoDuplicates()
    }
}

/** Kept in step with the figure in README.md, by the test above. */
private const val EMULATORS_IN_README = 87
