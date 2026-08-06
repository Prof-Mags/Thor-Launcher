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

    /**
     * Nothing documented is known about how this build takes a ROM.
     *
     * Not a refusal. It used to be one — the launch was rejected before an
     * intent was ever built, and the user was told to open the emulator and
     * find the game themselves — which is a table admitting ignorance and
     * charging the user for it. These builds are also the ones most likely to
     * have gained a VIEW filter since the row was written, and nobody would
     * ever find out.
     *
     * So the generic contract is attempted anyway, and [hint] is only said if
     * every attempt fails. Trying costs one intent; refusing costs the feature.
     */
    data class Undocumented(val hint: String) : RomLaunchContract
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
    /**
     * Many systems behind one application, rather than an emulator of one thing.
     *
     * Marks the multi-core front-ends — RetroArch, Lemuroid — which are offered
     * last when nothing has been chosen explicitly. They can open almost any file
     * in the table, and being both installed and first in the list meant they won
     * every automatic pick: someone with RetroArch and melonDS both installed got
     * RetroArch for a DS game, which is the wrong answer twice over. It needs a
     * core downloaded and assigned for that system before it will run anything,
     * where the dedicated emulator simply runs the file.
     *
     * Still listed, still pickable, and still the answer for the systems nothing
     * else here covers.
     */
    val isFrontEnd: Boolean = false,
)

object EmulatorRegistry {

    /**
     * The activity every Mupen64Plus descendant on Android still starts from.
     *
     * M64Plus FZ is a fork of Paul Lamb's Mupen64Plus AE and kept its
     * `paulscode.android.mupen64plusae` class namespace while changing its
     * application id, so the free build, the paid build and the original all
     * answer to the same component.
     */
    private const val MUPEN_SPLASH_ACTIVITY = "paulscode.android.mupen64plusae.SplashActivity"

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
            isFrontEnd = true,
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
        /*
         * MasterGear, which is the answer for three systems that had none.
         *
         * The Master System, the Game Gear and the SG-1000 could only be run
         * through RetroArch or Lemuroid before this — every one of them a system
         * where the front-end needs a core downloading and assigning first.
         * Marat Fayzullin's emulator covers all three in one application and has
         * been on the Play Store for years.
         */
        EmulatorSpec(
            packageName = "com.fms.mg",
            displayName = "MasterGear",
            platformIds = setOf("mastersystem", "gamegear", "sg1000"),
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
        /*
         * ---- Nintendo 64 ----------------------------------------------------
         *
         * Three packages, one lineage, and the names had drifted a long way from
         * what is actually on the store. `…fzurita.pro` is the paid M64Plus FZ,
         * `…fzurita` is the free one — it was labelled "Mupen64Plus FZ (Pro)",
         * which named the wrong build — and `…v3.alpha` is not FZ at all but
         * Paul Lamb's original Mupen64Plus AE that FZ was forked from. Three
         * confusingly similar entries in the N64 picker, two of them lying about
         * which application they would open.
         *
         * All three take the same explicit activity. FZ kept its parent's
         * `paulscode.android.mupen64plusae` namespace when it changed its
         * application id, which is why one component name serves the family.
         * Without it the intent resolves to whatever the package declares as its
         * default VIEW handler, and on this family that is the file browser — so
         * pressing Launch opened the emulator on a list of folders rather than on
         * the game, which is the exact failure this table exists to prevent.
         *
         * Harmless if the component ever moves: `EntryLauncher` retries without it
         * before giving up, so a stale name costs one refused intent and nothing
         * else.
         */
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.fzurita.pro",
            displayName = "M64Plus FZ Pro",
            platformIds = setOf("n64"),
            activityName = MUPEN_SPLASH_ACTIVITY,
        ),
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.fzurita",
            displayName = "M64Plus FZ",
            platformIds = setOf("n64"),
            activityName = MUPEN_SPLASH_ACTIVITY,
        ),
        EmulatorSpec(
            packageName = "org.mupen64plusae.v3.alpha",
            displayName = "Mupen64Plus AE",
            platformIds = setOf("n64"),
            activityName = MUPEN_SPLASH_ACTIVITY,
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
        /*
         * The dual-screen build, which is a separate app rather than a patch.
         *
         * It carries its own applicationId so that it installs *beside* stock
         * Winlator instead of upgrading over it — the two do not share containers,
         * and an upgrade that silently adopted them would be a very large
         * surprise. That separate id is also why this entry has to exist: without
         * it the fork is simply an app THOR has never heard of, and every PC game
         * on the grid would go on being handed to a Winlator that draws its
         * controls over the top of the picture.
         *
         * Listed above the others deliberately. [resolve] takes the longest
         * matching base, but these are distinct ids rather than variants of one
         * another, so order is what decides which is offered first — and on a
         * device with two screens the one that uses both is the better default.
         *
         * Everything else is inherited: same activity name, same shortcut
         * contract, same session reuse. Only where the controls are drawn changed.
         */
        EmulatorSpec(
            packageName = "com.loki.winlator",
            displayName = "Loki Winlator (dual-screen)",
            platformIds = setOf(BuiltInPlatforms.ID_PC, "dos"),
            activityName = "com.winlator.cmod.XServerDisplayActivity",
            launchContract = RomLaunchContract.PathExtra("shortcut_path"),
            mayReuseExistingTask = true,
        ),
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
            launchContract = RomLaunchContract.Undocumented(
                "ScummVM may need the game's folder adding in its own library first.",
            ),
        ),

        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            platformIds = setOf("gamecube", "wii"),
            activityName = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            launchContract = RomLaunchContract.Undocumented(
                "This Dolphin build may need the game importing into its own library first.",
            ),
        ),
        /*
         * ---- Wii U ----------------------------------------------------------
         *
         * The last built-in system with no emulator of any kind. RetroArch claims
         * it the way it claims everything — by declaring the whole platform list —
         * and no libretro core runs Wii U, so a Wii U game in the library was
         * unlaunchable rather than merely awkward, exactly as PS3 was.
         *
         * Cemu's own Android build, rather than a port of it: the project ships
         * one, under `info.cemu.Cemu`.
         *
         * Left on the default contract, which is an `ACTION_VIEW` carrying the
         * game's URI. That is what most Android emulators accept and it is the
         * right first assumption, but it is an assumption — the Android build is
         * new and its manifest has not been read from here. If it turns out to
         * open on its own game list instead of the game, it belongs with Dolphin
         * below, on an `Unsupported` contract that says so plainly rather than
         * failing at the intent.
         */
        EmulatorSpec(
            packageName = "info.cemu.Cemu",
            displayName = "Cemu",
            platformIds = setOf("wiiu"),
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
            packageName = "org.ryujinx.android",
            displayName = "Ryujinx",
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
        EmulatorSpec(
            packageName = "org.stratoemu.strato",
            displayName = "Strato",
            platformIds = setOf("switch"),
        ),

        /*
         * ---- 3DS ------------------------------------------------------------
         *
         * Citra was taken down, and what people run now are its descendants.
         * Azahar is the merge of Lime3DS and PabloMK7's fork, and it ships under
         * two ids: its own for the build from GitHub, and Lime3DS's inherited one
         * for the Play Store build. Both are listed, because which one is
         * installed is not something the launcher can infer.
         */
        EmulatorSpec(
            packageName = "org.azahar_emu.azahar",
            displayName = "Azahar",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "io.github.lime3ds.android",
            displayName = "Azahar (Play Store build)",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "com.panda3ds.pandroid",
            displayName = "Panda3DS",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "org.citra.citra_emu",
            displayName = "Citra",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            // Citra MMJ, whose id shares nothing with the upstream one.
            packageName = "org.citra.emu",
            displayName = "Citra MMJ",
            platformIds = setOf("3ds"),
        ),
        EmulatorSpec(
            packageName = "org.citra.citra_emu.canary",
            displayName = "Citra Canary",
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
        /*
         * Snes9x, under the name it actually ships as on Android.
         *
         * Robert Broglia's build is the Snes9x port people have: it is Snes9x's
         * own core inside his front-end, and there is no separate application
         * called plain "Snes9x" to add. Named "Snes9x EX+" here because that is
         * what the store calls it and what the installed app calls itself — a
         * picker that renamed it to "Snes9x" would be tidier to read and wrong
         * about which application it was going to open.
         */
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
        /*
         * MD.emu is not only the Mega Drive.
         *
         * It runs the Master System and Mark III, and Sega CD, which is how a
         * system with nothing else on this list gets a dedicated emulator.
         * Game Gear and SG-1000 are deliberately not claimed here — MasterGear
         * covers those and this one's support for them is not documented, and a
         * claimed platform that turns out not to run is worse than an
         * unclaimed one that does.
         */
        EmulatorSpec(
            packageName = "com.explusalpha.MdEmu",
            displayName = "MD.emu",
            platformIds = setOf("genesis", "mastersystem", "segacd"),
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
            isFrontEnd = true,
        ),
        EmulatorSpec(
            // The 32-bit build, still current on older handhelds.
            packageName = "com.retroarch.ra32",
            displayName = "RetroArch (32-bit)",
            platformIds = BuiltInPlatforms.ALL.map { it.id }.toSet(),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            launchContract = RomLaunchContract.RetroArch,
            mayReuseExistingTask = true,
            isFrontEnd = true,
        ),
        EmulatorSpec(
            packageName = "com.retroarch.aarch64",
            displayName = "RetroArch (64-bit)",
            platformIds = BuiltInPlatforms.ALL.map { it.id }.toSet(),
            activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture",
            launchContract = RomLaunchContract.RetroArch,
            mayReuseExistingTask = true,
            isFrontEnd = true,
        ),
        EmulatorSpec(
            packageName = "org.dolphinemu.dolphinemu.mmjr",
            displayName = "Dolphin MMJR",
            platformIds = setOf("gamecube", "wii"),
            launchContract = RomLaunchContract.Undocumented(
                "This Dolphin build may need the game importing into its own library first.",
            ),
        ),
        EmulatorSpec(
            packageName = "org.mmjr.dolphinemu",
            displayName = "Dolphin MMJR2",
            platformIds = setOf("gamecube", "wii"),
            launchContract = RomLaunchContract.Undocumented(
                "This Dolphin build may need the game importing into its own library first.",
            ),
        ),
        EmulatorSpec(
            packageName = "me.magnum.melonds",
            displayName = "melonDS",
            platformIds = setOf("nds"),
        ),
        EmulatorSpec(
            packageName = "com.hydra.noods",
            displayName = "NooDS",
            platformIds = setOf("nds", "gba"),
        ),
        EmulatorSpec(
            packageName = "us.rewrite.nds",
            displayName = "nds4droid",
            platformIds = setOf("nds"),
        ),

        /*
         * The "John" family, which is what a great many people actually have
         * installed — they have been on the Play Store for over a decade and were
         * most people's first Android emulator.
         *
         * Listed individually because they are separate applications, and the
         * paid and Lite editions are separate again: someone who owns John GBA
         * does not necessarily have John GBC, and the registry's job is to say
         * which of the installed ones can open a given file.
         */
        EmulatorSpec(
            packageName = "com.johnemulators.johngba",
            displayName = "John GBA",
            platformIds = setOf("gba"),
        ),
        EmulatorSpec(
            packageName = "com.johnemulators.johngbalite",
            displayName = "John GBA Lite",
            platformIds = setOf("gba"),
        ),
        EmulatorSpec(
            packageName = "com.johnemulators.johngbc",
            displayName = "John GBC",
            platformIds = setOf("gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.johnemulators.johngbclite",
            displayName = "John GBC Lite",
            platformIds = setOf("gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.johnemulators.johngbac",
            displayName = "John GBAC",
            platformIds = setOf("gba", "gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.johnemulators.johnness",
            displayName = "John NESS",
            platformIds = setOf("nes", "snes"),
        ),
        EmulatorSpec(
            packageName = "com.explusalpha.GbcEmu",
            displayName = "GBC.emu",
            platformIds = setOf("gb", "gbc"),
        ),
        /*
         * "com.explusalpha.MdEmu.n64" used to be here, as "N64 Plus FZ Pro".
         *
         * No such package exists. The `com.explusalpha.*` family is Robert
         * Broglia's `.emu` applications and MD.emu is the Genesis one; there is no
         * N64 member and never was. The app that name was reaching for is
         * M64Plus FZ, which is with the rest of its family further up.
         *
         * It could never match an installed package, so it did nothing except
         * appear in the picker for N64 as a choice that would never launch.
         */
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
        // The free editions, which are separate applications rather than
        // variants — the id has no separating dot, so nothing infers them.
        EmulatorSpec(
            packageName = "com.fastemulator.gbafree",
            displayName = "My Boy! Free",
            platformIds = setOf("gba", "gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.fastemulator.gbcfree",
            displayName = "My OldBoy! Free",
            platformIds = setOf("gb", "gbc"),
        ),
        EmulatorSpec(
            packageName = "com.dsemu.drastic",
            displayName = "DraStic",
            platformIds = setOf("nds"),
        ),
        EmulatorSpec(
            packageName = "com.seleuco.mame4droid",
            displayName = "MAME4droid",
            platformIds = setOf("arcade"),
        ),
        /*
         * Flycast twice, because it has two application ids and both are still
         * installed out there: `com.flycast.emulator` is the current one, and
         * `com.reicast.emulator` is the id it carried when the project was
         * Reicast. Told apart in the picker rather than left as two rows both
         * reading "Flycast", which is a choice nobody can make correctly.
         */
        EmulatorSpec(
            packageName = "com.flycast.emulator",
            displayName = "Flycast",
            platformIds = setOf("dreamcast"),
        ),
        EmulatorSpec(
            packageName = "com.reicast.emulator",
            displayName = "Flycast (Reicast build)",
            platformIds = setOf("dreamcast"),
        ),
        EmulatorSpec(
            packageName = "org.vita3k.emulator",
            displayName = "Vita3K",
            platformIds = setOf("psvita"),
        ),

        /*
         * ---- PlayStation 3 --------------------------------------------------
         *
         * A platform that had no emulator at all until now, dedicated or
         * otherwise: no RetroArch core runs PS3, so every PS3 game in the
         * library was unlaunchable rather than merely awkward.
         *
         * Two of them, because two exist and which one somebody installed is not
         * something the launcher can infer. aPS3e ships free and paid builds
         * under separate ids, so both are listed for the same reason the John
         * and Pizza Boy editions are.
         */
        EmulatorSpec(
            packageName = "aenu.aps3e",
            displayName = "aPS3e",
            platformIds = setOf("ps3"),
        ),
        EmulatorSpec(
            packageName = "aenu.aps3e.premium",
            displayName = "aPS3e Premium",
            platformIds = setOf("ps3"),
        ),
        EmulatorSpec(
            packageName = "net.rpcs3",
            displayName = "RPCS3",
            platformIds = setOf("ps3"),
        ),

        /*
         * The rest of Marat Fayzullin's family, whose ColEm and Speccy are
         * already above.
         *
         * Each ships a free build and a paid "deluxe" one under its own id, and
         * only the deluxe ids were listed — so someone running the free build
         * had an emulator installed that the launcher could not see.
         */
        EmulatorSpec(
            packageName = "com.fms.colem",
            displayName = "ColEm",
            platformIds = setOf("colecovision"),
        ),
        EmulatorSpec(
            packageName = "com.fms.speccy",
            displayName = "Speccy",
            platformIds = setOf("zxspectrum"),
        ),
        EmulatorSpec(
            packageName = "com.fms.fmsx",
            displayName = "fMSX",
            platformIds = setOf("msx"),
        ),
        EmulatorSpec(
            packageName = "com.fms.fmsx.deluxe",
            displayName = "fMSX Deluxe",
            platformIds = setOf("msx"),
        ),
        EmulatorSpec(
            packageName = "com.fms.ines.free",
            displayName = "iNES",
            platformIds = setOf("nes"),
        ),
    )

    private val byPackage: Map<String, EmulatorSpec> = KNOWN.associateBy(EmulatorSpec::packageName)

    fun specFor(packageName: String): EmulatorSpec? = byPackage[packageName]

    /** True for anything this table names, and for builds descended from one. */
    fun isKnownEmulator(packageName: String): Boolean = resolve(packageName) != null

    /**
     * The spec for an installed package, allowing for builds this table predates.
     *
     * An exact table match is not enough, and never could be. Emulators here ship
     * under ids this list cannot enumerate in advance: Dolphin's own nightlies are
     * `org.dolphinemu.dolphinemu.debug`, a fork appends its own name, a store
     * build appends the store's. Every one of those was reported as not installed
     * while sitting on the user's home screen, because the id was one character
     * different from a row here.
     *
     * A suffixed id is treated as the same emulator: it is the same project, it
     * accepts the same intent, and it runs the same systems. The longest matching
     * base wins so `org.yuzu.yuzu_emu.ea` uses its own row rather than Yuzu's, and
     * the dot is required so `com.fastemulator.gbafree` cannot be mistaken for a
     * variant of `com.fastemulator.gba` — a different application that happens to
     * share a prefix, and one this table lists in its own right.
     */
    fun resolve(packageName: String): EmulatorSpec? =
        byPackage[packageName] ?: KNOWN
            .filter { packageName.startsWith("${it.packageName}.") }
            .maxByOrNull { it.packageName.length }

    /**
     * What to call a build that is not the one this table named.
     *
     * "Dolphin" for both Dolphin and its nightly would leave two rows that cannot
     * be told apart, and the assignment picker is exactly where that matters. The
     * suffix is the only thing that distinguishes them, so the suffix is what is
     * shown.
     */
    fun displayNameFor(packageName: String): String {
        val spec = resolve(packageName) ?: return packageName
        if (spec.packageName == packageName) return spec.displayName
        val suffix = packageName.removePrefix("${spec.packageName}.")
            .replace('.', ' ')
            .replaceFirstChar(Char::uppercaseChar)
        return "${spec.displayName} ($suffix)"
    }

    /**
     * Every known emulator able to run [platformId], best first.
     *
     * Dedicated emulators come before the many-core front-ends. The launcher
     * picks the first *installed* candidate when the user has not chosen one, and
     * ordering this list is the whole of that decision — so a DS game goes to
     * melonDS or DraStic if either is installed, and to RetroArch only when
     * neither is.
     *
     * Stable within each group, so the table's own order still decides between
     * two emulators of the same kind.
     */
    fun candidatesFor(platformId: String): List<EmulatorSpec> =
        KNOWN.filter { platformId in it.platformIds }
            .sortedBy { it.isFrontEnd }

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
        // The current generation, and the long-lived Play Store families.
        "azahar", "lime3ds", "panda3ds", "pandroid", "stratoemu", "skyline",
        "sudachi", "citron", "noods", "johnemulators", "lemuroid", "vita3k",
        "winlator", "scummvm", "pizzaboy", "cemu",
    )
}
