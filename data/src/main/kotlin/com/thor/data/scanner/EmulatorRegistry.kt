package com.thor.data.scanner

import com.thor.core.model.BuiltInPlatforms

/** The exact public intent shape an emulator accepts for a ROM launch. */
sealed interface RomLaunchContract {
    /** A normal ACTION_VIEW intent carrying THOR's persisted content URI. */
    data object ContentUriView : RomLaunchContract

    /** A raw shared-storage path under the emulator's documented extra key. */
    data class PathExtra(val key: String) : RomLaunchContract

    /** RetroArch's explicit ROM extra; core selection is handled by RetroArch. */
    data object RetroArch : RomLaunchContract

    /** This emulator exposes no supported public arbitrary-ROM launch contract. */
    data class Unsupported(val reason: String) : RomLaunchContract
}

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
    /** How this emulator publicly accepts a selected ROM. */
    val launchContract: RomLaunchContract = RomLaunchContract.ContentUriView,
    /** Its activity may be reused on another display instead of being re-created. */
    val mayReuseExistingTask: Boolean = false,
)

object EmulatorRegistry {

    val KNOWN: List<EmulatorSpec> = listOf(
        /*
         * Lemuroid, which like RetroArch is many cores behind one application.
         *
         * Listed with an explicit platform set rather than "everything" because
         * it ships a fixed roster of cores, unlike RetroArch where the user
         * installs whichever they want. Claiming systems it cannot run would put
         * it in the "launch with" list for games it would refuse.
         */
        EmulatorSpec(
            packageName = "com.swordfish.lemuroid",
            displayName = "Lemuroid",
            platformIds = setOf(
                "nes", "snes", "n64", "gb", "gbc", "gba", "nds", "psx",
                "genesis", "mastersystem", "gamegear", "segacd", "sega32x",
                "atari2600", "atari7800", "lynx", "pcengine", "ngpc", "arcade",
            ),
        ),

        // ---- PlayStation ----------------------------------------------------
        EmulatorSpec(
            packageName = "com.epsxe.ePSXe",
            displayName = "ePSXe",
            platformIds = setOf("psx"),
        ),
        EmulatorSpec(
            packageName = "com.emulator.fpse",
            displayName = "FPse",
            platformIds = setOf("psx"),
        ),
        EmulatorSpec(
            packageName = "com.emulator.fpse64",
            displayName = "FPse64",
            platformIds = setOf("psx"),
        ),

        // ---- Sega -----------------------------------------------------------
        EmulatorSpec(
            packageName = "io.recompiled.redream",
            displayName = "Redream",
            platformIds = setOf("dreamcast"),
        ),
        EmulatorSpec(
            packageName = "org.uoyabause.uranus",
            displayName = "Yaba Sanshiro 2",
            platformIds = setOf("saturn"),
        ),

        // ---- Nintendo handhelds ---------------------------------------------
        EmulatorSpec(
            packageName = "it.dbtecno.pizzaboygba",
            displayName = "Pizza Boy GBA",
            platformIds = setOf("gba"),
        ),
        EmulatorSpec(
            packageName = "it.dbtecno.pizzaboygbc",
            displayName = "Pizza Boy GBC",
            platformIds = setOf("gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.fzurita.pro",
            displayName = "M64Plus FZ Pro",
            platformIds = setOf("n64"),
        ),

        // ---- Arcade and home computers --------------------------------------
        EmulatorSpec(
            packageName = "com.seleuco.mame4d2024",
            displayName = "MAME4droid 2024",
            platformIds = setOf("arcade", "neogeo"),
        ),
        EmulatorSpec(
            packageName = "com.fms.speccy.deluxe",
            displayName = "Speccy Deluxe",
            platformIds = setOf("zxspectrum"),
        ),
        EmulatorSpec(
            packageName = "com.fms.colem.deluxe",
            displayName = "ColEm Deluxe",
            platformIds = setOf("colecovision"),
        ),

        /*
         * Winlator, which runs Windows games through Wine and Box64.
         *
         * Launched by **shortcut**, not by executable. Winlator does not accept a
         * `.exe` handed to it: a Windows program needs a container — a Wine
         * prefix, a graphics driver, a set of DLL overrides — and the shortcut is
         * what names all of that alongside the executable. `XServerDisplayActivity`
         * takes the path of a `.desktop` file that Winlator itself wrote, which is
         * why the PC platform now scans for those.
         *
         * The practical consequence for the user: set the game up in Winlator
         * once, and it appears on the grid afterwards. THOR does not create
         * containers and should not — everything about which driver and which
         * Proton build is Winlator's business.
         *
         * `mayReuseExistingTask` because the X server is expensive to start and
         * Winlator holds one session at a time in any case.
         */
        EmulatorSpec(
            packageName = "com.winlator.cmod",
            displayName = "Winlator (cmod)",
            platformIds = setOf(BuiltInPlatforms.ID_PC, "dos"),
            activityName = "com.winlator.cmod.XServerDisplayActivity",
            launchContract = RomLaunchContract.PathExtra("shortcut_path"),
            mayReuseExistingTask = true,
        ),
        EmulatorSpec(
            packageName = "com.winlator",
            displayName = "Winlator",
            platformIds = setOf(BuiltInPlatforms.ID_PC, "dos"),
            activityName = "com.winlator.XServerDisplayActivity",
            launchContract = RomLaunchContract.PathExtra("shortcut_path"),
            mayReuseExistingTask = true,
        ),

        /*
         * Robert Broglia's ".emu" family, which covers most of the eight- and
         * sixteen-bit systems on its own.
         *
         * Listed individually rather than as one entry because they are separate
         * applications with separate packages — a user may own three of them and
         * none of the others, and the registry's job is to say which of the
         * installed ones can open a given file.
         */
        EmulatorSpec(
            packageName = "com.explusalpha.MsxEmu",
            displayName = "MSX.emu",
            platformIds = setOf("msx"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.NeoEmu",
            displayName = "NEO.emu",
            platformIds = setOf("neogeo"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.PceEmu",
            displayName = "PCE.emu",
            platformIds = setOf("pcengine", "pcenginecd"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.LynxEmu",
            displayName = "Lynx.emu",
            platformIds = setOf("lynx"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.A2600Emu",
            displayName = "2600.emu",
            platformIds = setOf("atari2600"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.C64Emu",
            displayName = "C64.emu",
            platformIds = setOf("c64"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.SwanEmu",
            displayName = "Swan.emu",
            platformIds = setOf("wonderswan"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.NgpEmu",
            displayName = "NGP.emu",
            platformIds = setOf("ngpc"),
        ),

        /*
         * ScummVM takes a game *folder* rather than a file, which is why it is
         * marked unsupported for direct launch: handing it a content URI to one
         * file inside a game's directory does nothing useful.
         */
        EmulatorSpec(
            packageName = "org.scummvm.scummvm",
            displayName = "ScummVM",
            platformIds = setOf("scummvm"),
            launchContract = RomLaunchContract.Unsupported(
                "Add the game's folder in ScummVM, then launch it from there.",
            ),
        ),

        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            platformIds = setOf("gamecube", "wii"),
            activityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            launchContract = RomLaunchContract.Unsupported(
                "Import this game into Dolphin's own library, then launch it there.",
            ),
        ),
        EmulatorSpec(
            packageName = "org.ppsspp.ppsspp",
            displayName = "PPSSPP",
            platformIds = setOf("psp"),
            activityName = "org.ppsspp.ppsspp.PpssppActivity",
            mayReuseExistingTask = true,
        ),
        EmulatorSpec(
            packageName = "org.ppsspp.ppssppgold",
            displayName = "PPSSPP Gold",
            platformIds = setOf("psp"),
            activityName = "org.ppsspp.ppsspp.PpssppActivity",
            mayReuseExistingTask = true,
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
            launchContract = RomLaunchContract.PathExtra("bootPath"),
        ),
        EmulatorSpec(
            packageName = "xyz.aethersx2.android",
            displayName = "AetherSX2",
            platformIds = setOf("ps2"),
            activityName = "xyz.aethersx2.android.EmulationActivity",
            launchContract = RomLaunchContract.PathExtra("bootPath"),
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
            launchContract = RomLaunchContract.RetroArch,
            mayReuseExistingTask = true,
        ),
        EmulatorSpec(
            packageName = "com.retroarch.aarch64",
            displayName = "RetroArch (64-bit)",
            platformIds = BuiltInPlatforms.ALL.map { it.id }.toSet(),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            launchContract = RomLaunchContract.RetroArch,
            mayReuseExistingTask = true,
        ),
        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu.mmjr",
            displayName = "Dolphin MMJR",
            platformIds = setOf("gamecube", "wii"),
            launchContract = RomLaunchContract.Unsupported(
                "This Dolphin build does not expose a supported arbitrary-ROM launch intent.",
            ),
        ),
        EmulatorSpec(
            packageName = "org.mmjr.dolphinemu",
            displayName = "Dolphin MMJR2",
            platformIds = setOf("gamecube", "wii"),
            launchContract = RomLaunchContract.Unsupported(
                "This Dolphin build does not expose a supported arbitrary-ROM launch intent.",
            ),
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
