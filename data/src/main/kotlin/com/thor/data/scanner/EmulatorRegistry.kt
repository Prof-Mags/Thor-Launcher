package com.thor.data.scanner

import com.thor.core.model.BuiltInPlatforms

/**
 * Known Android emulators and how to hand a ROM to them.
 *
 * Most Android emulators accept a `VIEW` intent carrying the game's URI, but
 * several want a specific component or an extra, and getting that wrong means
 * the emulator opens on its own file browser instead of the selected game. The
 * table below records the working invocation for each.
 */
data class EmulatorSpec(
    val packageName: String,
    val displayName: String,
    /** Platforms this emulator can run, by [BuiltInPlatforms] id. */
    val platformIds: Set<String>,
    /**
     * Explicit activity to target. When null, the launcher resolves the
     * package's default `VIEW` handler instead.
     */
    val activityName: String? = null,
    /**
     * Extra key the emulator expects the file path under, for the ones that do
     * not read the intent data URI.
     */
    val pathExtraKey: String? = null,
    /** True when the emulator needs a real file path rather than a content URI. */
    val requiresFilePath: Boolean = false,
)

object EmulatorRegistry {

    val KNOWN: List<EmulatorSpec> = listOf(
        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            platformIds = setOf("gamecube", "wii"),
            activityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "org.ppsspp.ppsspp",
            displayName = "PPSSPP",
            platformIds = setOf("psp"),
            activityName = "org.ppsspp.ppsspp.PpssppActivity",
            pathExtraKey = "org.ppsspp.ppsspp.Shortcuts",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "org.ppsspp.ppssppgold",
            displayName = "PPSSPP Gold",
            platformIds = setOf("psp"),
            activityName = "org.ppsspp.ppsspp.PpssppActivity",
            pathExtraKey = "org.ppsspp.ppsspp.Shortcuts",
            requiresFilePath = true,
        ),
        // --- Switch ---------------------------------------------------------
        EmulatorSpec(
            packageName = "skyline.emu",
            displayName = "Skyline",
            platformIds = setOf("switch"),
        ),
        EmulatorSpec(
            packageName = "org.yuzu.yuzu_emu",
            displayName = "Yuzu",
            platformIds = setOf("switch"),
        ),
        EmulatorSpec(
            packageName = "org.yuzu.yuzu_emu.ea",
            displayName = "Yuzu Early Access",
            platformIds = setOf("switch"),
        ),
        EmulatorSpec(
            packageName = "org.sudachi.sudachi_emu",
            displayName = "Sudachi",
            platformIds = setOf("switch"),
        ),
        EmulatorSpec(
            packageName = "org.citron.citron_emu",
            displayName = "Citron",
            platformIds = setOf("switch"),
        ),
        EmulatorSpec(
            packageName = "dev.eden.eden_emulator",
            displayName = "Eden",
            platformIds = setOf("switch"),
        ),

        // --- 3DS ------------------------------------------------------------
        EmulatorSpec(
            packageName = "org.citra.citra_emu",
            displayName = "Citra",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "org.citra.citra_emu.canary",
            displayName = "Citra Canary",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "io.github.lime3ds.android",
            displayName = "Lime3DS",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "io.github.mandarine3ds.mandarine",
            displayName = "Mandarine",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "com.antutu.ABenchMark.citra",
            displayName = "Citra (legacy build)",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "com.github.stenzek.duckstation",
            displayName = "DuckStation",
            platformIds = setOf("psx"),
            activityName = "com.github.stenzek.duckstation.EmulationActivity",
            pathExtraKey = "bootPath",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "xyz.aethersx2.android",
            displayName = "AetherSX2",
            platformIds = setOf("ps2"),
            activityName = "xyz.aethersx2.android.EmulationActivity",
            pathExtraKey = "bootPath",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "com.play.emu",
            displayName = "Play!",
            platformIds = setOf("ps2"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.Snes9xPlus",
            displayName = "Snes9x EX+",
            platformIds = setOf("snes"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.NesEmu",
            displayName = "NES.emu",
            platformIds = setOf("nes"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.GbaEmu",
            displayName = "GBA.emu",
            platformIds = setOf("gba"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.MdEmu",
            displayName = "MD.emu",
            platformIds = setOf("genesis"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.Saturn",
            displayName = "Saturn.emu",
            platformIds = setOf("saturn"),
        ),
        EmulatorSpec(
            packageName = "com.retroarch",
            displayName = "RetroArch",
            platformIds = BuiltInPlatforms.ALL.map { it.id }.toSet(),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "com.retroarch.aarch64",
            displayName = "RetroArch (64-bit)",
            platformIds = BuiltInPlatforms.ALL.map { it.id }.toSet(),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu.mmjr",
            displayName = "Dolphin MMJR",
            platformIds = setOf("gamecube", "wii"),
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "org.mmjr.dolphinemu",
            displayName = "Dolphin MMJR2",
            platformIds = setOf("gamecube", "wii"),
            requiresFilePath = true,
        ),
        EmulatorSpec(
            packageName = "me.magnum.melonds",
            displayName = "melonDS",
            platformIds = setOf("nds"),
        ),
        EmulatorSpec(
            packageName = "us.rewrite.nds",
            displayName = "nds4droid",
            platformIds = setOf("nds"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.MdEmu.n64",
            displayName = "N64 Plus FZ Pro",
            platformIds = setOf("n64"),
        ),
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.fzurita",
            displayName = "Mupen64Plus FZ (Pro)",
            platformIds = setOf("n64"),
        ),
        EmulatorSpec(
            packageName = "com.fastemulator.gba",
            displayName = "My Boy!",
            platformIds = setOf("gba", "gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.fastemulator.gbc",
            displayName = "My OldBoy!",
            platformIds = setOf("gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.dsemu.drastic",
            displayName = "DraStic",
            platformIds = setOf("nds"),
        ),
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.alpha",
            displayName = "Mupen64Plus FZ",
            platformIds = setOf("n64"),
        ),
        EmulatorSpec(
            packageName = "com.seleuco.mame4droid",
            displayName = "MAME4droid",
            platformIds = setOf("arcade"),
        ),
        EmulatorSpec(
            packageName = "com.reicast.emulator",
            displayName = "Flycast",
            platformIds = setOf("dreamcast"),
        ),
        EmulatorSpec(
            packageName = "com.flycast.emulator",
            displayName = "Flycast",
            platformIds = setOf("dreamcast"),
        ),
        EmulatorSpec(
            packageName = "org.vita3k.emulator",
            displayName = "Vita3K",
            platformIds = setOf("psvita"),
        ),
    )

    private val byPackage: Map<String, EmulatorSpec> = KNOWN.associateBy(EmulatorSpec::packageName)

    fun specFor(packageName: String): EmulatorSpec? = byPackage[packageName]

    fun isKnownEmulator(packageName: String): Boolean = packageName in byPackage

    /** Every known emulator able to run [platformId]. */
    fun candidatesFor(platformId: String): List<EmulatorSpec> =
        KNOWN.filter { platformId in it.platformIds }

    /**
     * Heuristic for packages not in the table.
     *
     * Deliberately conservative — it only flags names that are unambiguous —
     * because a false positive puts a normal app in the emulator picker.
     */
    fun looksLikeEmulator(packageName: String, appLabel: String): Boolean {
        val haystack = "$packageName ${appLabel.lowercase()}"
        return EMULATOR_HINTS.any { haystack.contains(it) }
    }

    private val EMULATOR_HINTS = listOf(
        "emulator", "emu.", ".emu", "retroarch", "libretro",
        "dolphin", "ppsspp", "citra", "yuzu", "ryujinx", "duckstation",
        "aethersx2", "pcsx", "epsxe", "mupen", "drastic", "melonds",
        "snes9x", "fceux", "nestopia", "mame", "flycast", "redream",
    )
}
