package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A gaming platform a library entry can belong to.
 *
 * THOR ships the set defined in [BuiltInPlatforms] and lets users define an
 * unlimited number of additional platforms at runtime; the only difference
 * between the two is the [isCustom] flag and the fact that built-ins cannot be
 * deleted (they can, however, be renamed, recoloured and re-iconed).
 */
@Serializable
data class Platform(
    /** Stable identifier. Built-ins use their [BuiltInPlatforms] slug. */
    val id: String,
    /** Display name, e.g. "Nintendo 64". */
    val name: String,
    /** Short label used in dense UI, e.g. "N64". */
    val shortName: String,
    /** Manufacturer, used for grouping in the platform browser. */
    val manufacturer: String,
    /** Year the platform launched; `null` for meta-platforms such as PC. */
    val releaseYear: Int?,
    /** ARGB accent used for platform badges and folder tinting. */
    val accentArgb: Long,
    /** File extensions treated as ROMs for this platform, lowercase, no dot. */
    val romExtensions: Set<String>,
    /**
     * Provider-specific identifiers, keyed by provider id (see
     * `MetadataProvider.id`). Used to scope scraper queries to the right system.
     */
    val providerIds: Map<String, String> = emptyMap(),
    /**
     * Emulators assigned to this platform, in preference order.
     *
     * The first entry is what games launch with; the rest are offered in the
     * context menu's "Launch with" list. Ordered rather than a set because
     * which emulator is *preferred* is the whole point — several emulators can
     * run a system, and the right one is often per-user.
     */
    val emulatorPackages: List<String> = emptyList(),
    val isCustom: Boolean = false,
    /**
     * Whether the user has added this system to their setup.
     *
     * Every built-in platform exists in the database from first run so the
     * scanner can recognise its file types, but only added ones appear in the
     * emulator settings and the platform browser.
     */
    val isAdded: Boolean = false,
    val sortIndex: Int = 0,
    /**
     * Artwork from an installed icon pack, or [PlatformArtwork.NONE].
     *
     * On the platform rather than on the folder its games are filed into,
     * because it belongs to the system and not to one grid entry: the same icon
     * dresses the folder, the same hero backs the information panel, and both
     * survive the folder being renamed, moved or deleted and rebuilt by a rescan.
     */
    val artwork: PlatformArtwork = PlatformArtwork.NONE,
    /**
     * A sentence or two about the system, for the information panel.
     *
     * Packaged content rather than a stored column: it never varies per install,
     * is never edited, and putting it in the database would mean a migration to
     * ship a typo fix. Resolved from [BuiltInPlatforms] when a row is read back;
     * empty for platforms the user created themselves, which have nothing to say
     * that the user did not already type.
     */
    val description: String = "",
) {
    /** The emulator games on this platform launch with, if any. */
    val defaultEmulatorPackage: String? get() = emulatorPackages.firstOrNull()

    /** "Nintendo · 1990", for the line under the title. */
    val subtitle: String
        get() = listOfNotNull(
            manufacturer.takeIf { it.isNotBlank() },
            releaseYear?.toString(),
        ).joinToString(" · ")
}

/**
 * The platforms THOR knows about out of the box.
 *
 * Extensions listed here are the ones a scanner should treat as a game file.
 * Archive containers (`zip`, `7z`, `rar`, `chd`) are handled separately by the
 * scanner because they are shared across nearly every platform.
 */
object BuiltInPlatforms {

    const val ID_ANDROID = "android"
    const val ID_PC = "pc"

    val ALL: List<Platform> = buildPlatforms().mapIndexed { index, platform ->
        platform.copy(sortIndex = index)
    }

    private fun buildPlatforms(): List<Platform> = listOf(
        platform(
            id = "nes", name = "Nintendo Entertainment System", short = "NES",
            maker = "Nintendo", year = 1983, argb = 0xFFB71C1C,
            ext = setOf("nes", "unf", "unif", "fds"),
            screenscraper = "3", igdb = "18",
            about = "The machine that rebuilt console gaming after the 1983 crash. Nintendo's 8-bit debut established the platformer, the side-scroller and the idea of a house style, and its library is still the reference for tight design under hard constraints."
        ),
        platform(
            id = "snes", name = "Super Nintendo", short = "SNES",
            maker = "Nintendo", year = 1990, argb = 0xFF5C4B8B,
            ext = setOf("sfc", "smc", "swc", "fig", "bs"),
            screenscraper = "4", igdb = "19",
            about = "16-bit, and arguably the most consistently excellent library ever assembled. Mode 7 scaling, a sound chip designed by Sony and a run of first-party releases that defined role-playing games and platformers for a decade."
        ),
        platform(
            id = "n64", name = "Nintendo 64", short = "N64",
            maker = "Nintendo", year = 1996, argb = 0xFF2E7D32,
            ext = setOf("n64", "z64", "v64", "ndd"),
            screenscraper = "14", igdb = "4",
            about = "The first Nintendo console built around three dimensions and an analogue stick, both of which it largely invented the grammar for. Cartridge-based to the end, which kept load times at nothing and the library small and sharp."
        ),
        platform(
            id = "gamecube", name = "Nintendo GameCube", short = "GCN",
            maker = "Nintendo", year = 2001, argb = 0xFF4527A0,
            ext = setOf("iso", "gcm", "gcz", "rvz", "ciso", "dol", "elf"),
            screenscraper = "13", igdb = "21",
            about = "A compact purple cube with a handle, sold on power rather than gimmicks. Its mini-DVDs held less than a rival disc but loaded faster, and its first-party output is regarded as one of Nintendo's strongest runs."
        ),
        platform(
            id = "wii", name = "Nintendo Wii", short = "Wii",
            maker = "Nintendo", year = 2006, argb = 0xFF1E88E5,
            ext = setOf("iso", "wbfs", "rvz", "wad", "gcz", "ciso"),
            screenscraper = "16", igdb = "5",
            about = "Motion control taken mainstream, and the best-selling console of its generation by a wide margin. Underpowered on paper and aimed squarely at people who had never owned a console before."
        ),
        platform(
            id = "wiiu", name = "Nintendo Wii U", short = "Wii U",
            maker = "Nintendo", year = 2012, argb = 0xFF0277BD,
            ext = setOf("wud", "wux", "wua", "rpx"),
            screenscraper = "18", igdb = "41",
            about = "A tablet controller with a screen of its own, years before anyone agreed what that was for. A commercial failure whose best ideas — and much of its library — were carried over wholesale to the Switch."
        ),
        platform(
            id = "switch", name = "Nintendo Switch", short = "Switch",
            maker = "Nintendo", year = 2017, argb = 0xFFE53935,
            ext = setOf("nsp", "xci", "nca", "nro"),
            screenscraper = "225", igdb = "130",
            about = "A home console that detaches into a handheld, which turned out to be the thing everybody wanted. The format this launcher's hardware is built around, and the best-selling Nintendo system of all."
        ),
        platform(
            id = "gb", name = "Game Boy", short = "GB",
            maker = "Nintendo", year = 1989, argb = 0xFF7A8B3F,
            ext = setOf("gb"),
            screenscraper = "9", igdb = "33",
            about = "A green-grey screen, four shades, and battery life measured in days. It outsold far more capable rivals for a decade on stamina, price and a library nobody else could match."
        ),
        platform(
            id = "gbc", name = "Game Boy Color", short = "GBC",
            maker = "Nintendo", year = 1998, argb = 0xFFF9A825,
            ext = setOf("gbc", "cgb"),
            screenscraper = "10", igdb = "22",
            about = "Colour at last, and backwards compatible with everything that came before it. A stopgap between the original and the Advance that still ran for five years on the strength of its catalogue."
        ),
        platform(
            id = "gba", name = "Game Boy Advance", short = "GBA",
            maker = "Nintendo", year = 2001, argb = 0xFF6A1B9A,
            ext = setOf("gba", "agb"),
            screenscraper = "12", igdb = "24",
            about = "Effectively a portable Super Nintendo, and treated as one — the 16-bit library was ported to it wholesale alongside a deep run of originals. The high-water mark for 2D handheld gaming."
        ),
        platform(
            id = "nds", name = "Nintendo DS", short = "DS",
            maker = "Nintendo", year = 2004, argb = 0xFF00838F,
            ext = setOf("nds", "dsi", "ids"),
            screenscraper = "15", igdb = "20",
            about = "Two screens, one of them a touchscreen, at a time when nobody carried a touchscreen. The dual-screen idea this launcher is built on, and the best-selling handheld ever made."
        ),
        platform(
            id = "3ds", name = "Nintendo 3DS", short = "3DS",
            maker = "Nintendo", year = 2011, argb = 0xFFD81B60,
            ext = setOf("3ds", "cci", "cxi", "cia", "app", "3dsx"),
            screenscraper = "17", igdb = "37",
            about = "Glasses-free stereoscopic 3D on the top screen, a touchscreen below. The 3D was the headline and the least of it; the library and the street-pass social features are what people kept it for."
        ),
        platform(
            id = "virtualboy", name = "Virtual Boy", short = "VB",
            maker = "Nintendo", year = 1995, argb = 0xFFC62828,
            ext = setOf("vb", "vboy"),
            screenscraper = "11", igdb = "87",
            about = "A tabletop headset rendering everything in red on black, discontinued within a year. Commercially catastrophic and genuinely strange — its two dozen games are collected more than they are played."
        ),
        platform(
            id = "psx", name = "PlayStation", short = "PS1",
            maker = "Sony", year = 1994, argb = 0xFF37474F,
            ext = setOf("cue", "bin", "img", "pbp", "ecm", "m3u", "iso"),
            screenscraper = "57", igdb = "7",
            about = "Sony's first console, built after a falling-out with Nintendo over a CD add-on. Cheap 3D, cheap discs and a deliberately broad catalogue took gaming out of the bedroom and into the living room."
        ),
        platform(
            id = "ps2", name = "PlayStation 2", short = "PS2",
            maker = "Sony", year = 2000, argb = 0xFF1A237E,
            ext = setOf("iso", "bin", "cue", "img", "gz", "cso"),
            screenscraper = "58", igdb = "8",
            about = "The best-selling console of all time, and a DVD player at a price that undercut DVD players. Backwards compatible, endlessly supported, and host to one of the largest libraries ever assembled."
        ),
        platform(
            id = "ps3", name = "PlayStation 3", short = "PS3",
            maker = "Sony", year = 2006, argb = 0xFF263238,
            ext = setOf("iso", "pkg", "self", "elf"),
            screenscraper = "59", igdb = "9",
            about = "An expensive, difficult machine built around the Cell processor that took developers most of a generation to master — after which it produced some of the most technically accomplished games of its era."
        ),
        platform(
            id = "psp", name = "PlayStation Portable", short = "PSP",
            maker = "Sony", year = 2004, argb = 0xFF455A64,
            ext = setOf("iso", "cso", "pbp", "chd", "prx"),
            screenscraper = "61", igdb = "38",
            about = "Console-grade 3D in a pocket, on a widescreen display that was far ahead of anything else portable. Sold on multimedia as much as games, and adopted by homebrew almost immediately."
        ),
        platform(
            id = "psvita", name = "PlayStation Vita", short = "Vita",
            maker = "Sony", year = 2011, argb = 0xFF3949AB,
            ext = setOf("vpk", "psvgamesd", "mai"),
            screenscraper = "62", igdb = "46",
            about = "Beautiful, powerful and commercially stranded — an OLED handheld released as phones were eating the market. Its library skews indie and Japanese, and it has aged into a cult machine."
        ),
        platform(
            id = "xbox", name = "Xbox", short = "Xbox",
            maker = "Microsoft", year = 2001, argb = 0xFF2E7D32,
            ext = setOf("iso", "xbe"),
            screenscraper = "32", igdb = "11",
            about = "Microsoft's first console: a PC in a black box, with a hard drive as standard and an ethernet port nobody else had fitted. Xbox Live started here."
        ),
        platform(
            id = "arcade", name = "Arcade", short = "Arcade",
            maker = "Various", year = null, argb = 0xFFF57C00,
            ext = setOf("zip", "7z", "chd"),
            screenscraper = "75", igdb = "52",
            about = "Not one machine but hundreds, from the late 1970s onward, each built around its own game. Designed to be difficult and paid for by the coin, which is why the whole catalogue plays in short, sharp bursts."
        ),
        platform(
            id = "dreamcast", name = "Sega Dreamcast", short = "DC",
            maker = "Sega", year = 1998, argb = 0xFFEF6C00,
            ext = setOf("cdi", "gdi", "chd", "cue", "bin"),
            screenscraper = "23", igdb = "23",
            about = "Sega's last console, and years ahead of itself: a modem as standard, online play in 1999, and arcade-perfect conversions. Discontinued after eighteen months, and beloved ever since."
        ),
        platform(
            id = "saturn", name = "Sega Saturn", short = "Saturn",
            maker = "Sega", year = 1994, argb = 0xFF212121,
            ext = setOf("cue", "bin", "iso", "chd", "mds", "ccd"),
            screenscraper = "22", igdb = "32",
            about = "A 2D powerhouse awkwardly pressed into a 3D generation, with two processors that were notoriously hard to program. Its Japanese library is exceptional and much of it never left the country."
        ),
        platform(
            id = "genesis", name = "Sega Genesis / Mega Drive", short = "MD",
            maker = "Sega", year = 1988, argb = 0xFF0D47A1,
            ext = setOf("md", "gen", "smd", "bin", "68k", "sgd"),
            screenscraper = "1", igdb = "29",
            about = "Sega's 16-bit answer to Nintendo, sold on attitude and arcade conversions. Blast processing was marketing; the Yamaha sound chip and a genuinely fast library were not."
        ),
        // ---- Sega, before and around the Mega Drive ------------------------
        platform(
            id = "mastersystem", name = "Sega Master System", short = "SMS",
            maker = "Sega", year = 1985, argb = 0xFF1565C0,
            ext = setOf("sms", "bin"),
            screenscraper = "2", igdb = "64",
            about = "Sega's 8-bit machine, outsold at home by the NES and enormously popular in Brazil and Europe. Its library leans on arcade ports and is the reason several Sonic-adjacent games exist at all."
        ),
        platform(
            id = "gamegear", name = "Sega Game Gear", short = "GG",
            maker = "Sega", year = 1990, argb = 0xFF283593,
            ext = setOf("gg", "bin"),
            screenscraper = "21", igdb = "35",
            about = "A backlit colour handheld built on Master System hardware, which is why so much of its library is a port. Six AA batteries bought roughly three hours."
        ),
        platform(
            id = "segacd", name = "Sega CD / Mega CD", short = "SCD",
            maker = "Sega", year = 1991, argb = 0xFF0277BD,
            ext = setOf("cue", "chd", "iso", "bin"),
            screenscraper = "20", igdb = "78",
            about = "A CD add-on for the Mega Drive, remembered for full-motion video and for costing as much as the console it sat under. The good games are the ones that used the disc for audio rather than for footage."
        ),
        platform(
            id = "sega32x", name = "Sega 32X", short = "32X",
            maker = "Sega", year = 1994, argb = 0xFF01579B,
            ext = setOf("32x", "bin"),
            screenscraper = "19", igdb = "30",
            about = "A stopgap 32-bit add-on released a year before the Saturn, which is exactly how it was received. Around forty games exist and a handful are genuinely good."
        ),
        platform(
            id = "sg1000", name = "Sega SG-1000", short = "SG",
            maker = "Sega", year = 1983, argb = 0xFF37474F,
            ext = setOf("sg", "bin"),
            screenscraper = "109", igdb = "84",
            about = "Sega's first console, released in Japan on the same day as the Famicom and comprehensively beaten by it. Of interest mainly as the ancestor of the Master System."
        ),

        // ---- NEC ------------------------------------------------------------
        platform(
            id = "pcengine", name = "PC Engine / TurboGrafx-16", short = "PCE",
            maker = "NEC", year = 1987, argb = 0xFFE64A19,
            ext = setOf("pce", "sgx", "bin"),
            screenscraper = "31", igdb = "86",
            about = "An 8-bit CPU with a 16-bit graphics chip, tiny by the standards of its rivals, and dominant in Japan for years. Its shooters are the reason people still import the hardware."
        ),
        platform(
            id = "pcenginecd", name = "PC Engine CD", short = "PCE-CD",
            maker = "NEC", year = 1988, argb = 0xFFBF360C,
            ext = setOf("cue", "chd", "iso"),
            screenscraper = "114", igdb = "150",
            about = "The first CD add-on for any console, two years ahead of everyone else. Needs a system card image as well as the game."
        ),

        // ---- SNK ------------------------------------------------------------
        platform(
            id = "neogeo", name = "Neo Geo", short = "NG",
            maker = "SNK", year = 1990, argb = 0xFFB71C1C,
            ext = setOf("zip", "7z", "neo"),
            screenscraper = "142", igdb = "80",
            about = "Arcade hardware sold for the home at arcade prices — a single cartridge cost more than some consoles. The fighting and run-and-gun libraries are why the hardware is remembered rather than the price."
        ),
        platform(
            id = "ngpc", name = "Neo Geo Pocket Color", short = "NGPC",
            maker = "SNK", year = 1999, argb = 0xFFC62828,
            ext = setOf("ngp", "ngc", "npc"),
            screenscraper = "82", igdb = "119",
            about = "A handheld with a genuinely excellent microswitched stick, released into a market the Game Boy Color already owned. Its fighting games are far better than a system this obscure has any right to."
        ),

        // ---- Atari ----------------------------------------------------------
        platform(
            id = "atari2600", name = "Atari 2600", short = "2600",
            maker = "Atari", year = 1977, argb = 0xFF6D4C41,
            ext = setOf("a26", "bin"),
            screenscraper = "26", igdb = "59",
            about = "The console that established the idea of swapping cartridges, and the one whose flood of unsold stock helped crash the industry in 1983. 128 bytes of RAM."
        ),
        platform(
            id = "atari7800", name = "Atari 7800", short = "7800",
            maker = "Atari", year = 1986, argb = 0xFF5D4037,
            ext = setOf("a78", "bin"),
            screenscraper = "41", igdb = "60",
            about = "Backwards compatible with the 2600 and released two years late, into a market the NES had already taken. Strong arcade conversions, thin everywhere else."
        ),
        platform(
            id = "lynx", name = "Atari Lynx", short = "Lynx",
            maker = "Atari", year = 1989, argb = 0xFFF9A825,
            ext = setOf("lnx", "o"),
            screenscraper = "28", igdb = "61",
            about = "The first colour backlit handheld, and the first that could be held either way up for left-handed players. Far ahead of the Game Boy technically and nowhere near it commercially."
        ),
        platform(
            id = "jaguar", name = "Atari Jaguar", short = "Jaguar",
            maker = "Atari", year = 1993, argb = 0xFF424242,
            ext = setOf("j64", "jag", "rom"),
            screenscraper = "27", igdb = "62",
            about = "Marketed as 64-bit on arithmetic nobody has ever satisfactorily explained, and Atari's last console. Its small library contains two or three things worth the trouble."
        ),

        // ---- Home computers --------------------------------------------------
        platform(
            id = "c64", name = "Commodore 64", short = "C64",
            maker = "Commodore", year = 1982, argb = 0xFF6A1B9A,
            ext = setOf("d64", "t64", "prg", "crt", "tap"),
            screenscraper = "66", igdb = "15",
            about = "The best-selling computer model ever made, and a games machine by accident of price. The SID chip is the reason its music is still covered."
        ),
        platform(
            id = "amiga", name = "Commodore Amiga", short = "Amiga",
            maker = "Commodore", year = 1985, argb = 0xFF4527A0,
            ext = setOf("adf", "adz", "dms", "ipf", "lha"),
            screenscraper = "64", igdb = "16",
            about = "Custom chips for graphics and sound years before anyone else bothered, which is why European development moved there wholesale. Needs a Kickstart ROM as well as the disks."
        ),
        platform(
            id = "zxspectrum", name = "ZX Spectrum", short = "Speccy",
            maker = "Sinclair", year = 1982, argb = 0xFFAD1457,
            ext = setOf("z80", "tap", "tzx", "sna", "dsk"),
            screenscraper = "76", igdb = "26",
            about = "Cheap, rubber-keyed and everywhere in Britain, with a colour system that fought itself at every attribute boundary. An entire industry grew out of bedrooms because of it."
        ),
        platform(
            id = "amstradcpc", name = "Amstrad CPC", short = "CPC",
            maker = "Amstrad", year = 1984, argb = 0xFF00695C,
            ext = setOf("dsk", "cdt", "sna"),
            screenscraper = "65", igdb = "25",
            about = "Sold as a complete system with its own monitor, which made it the sensible choice in French and Spanish households. Shares much of its library with the Spectrum, usually in better colour."
        ),
        platform(
            id = "msx", name = "MSX", short = "MSX",
            maker = "ASCII / Microsoft", year = 1983, argb = 0xFF00838F,
            ext = setOf("rom", "mx1", "mx2", "dsk", "cas"),
            screenscraper = "113", igdb = "27",
            about = "A standard rather than a machine, built by a dozen manufacturers to one specification. Metal Gear and Castlevania both started here."
        ),

        // ---- The rest --------------------------------------------------------
        platform(
            id = "colecovision", name = "ColecoVision", short = "CV",
            maker = "Coleco", year = 1982, argb = 0xFF37474F,
            ext = setOf("col", "rom", "bin"),
            screenscraper = "48", igdb = "68",
            about = "Sold on the strength of its arcade conversions, which were closer to the originals than anything else at home. Killed along with everything else in 1983."
        ),
        platform(
            id = "intellivision", name = "Intellivision", short = "INTV",
            maker = "Mattel", year = 1979, argb = 0xFF4E342E,
            ext = setOf("int", "bin", "rom"),
            screenscraper = "115", igdb = "67",
            about = "The 2600's serious rival, with a numeric keypad controller and a genuine claim to better sports games. Its adverts spent their entire budget insulting Atari."
        ),
        platform(
            id = "wonderswan", name = "WonderSwan Color", short = "WS",
            maker = "Bandai", year = 2000, argb = 0xFF00897B,
            ext = setOf("ws", "wsc"),
            screenscraper = "45", igdb = "57",
            about = "Designed by the creator of the Game Boy after he left Nintendo, sold only in Japan, and held either way up depending on the game. Its Final Fantasy remakes are the usual reason to visit."
        ),
        platform(
            id = "3do", name = "3DO", short = "3DO",
            maker = "Panasonic", year = 1993, argb = 0xFF37474F,
            ext = setOf("cue", "chd", "iso"),
            screenscraper = "29", igdb = "50",
            about = "A standard licensed to any manufacturer that wanted it, launched at 699 dollars. The price is the whole story, though the library is stranger and better than its reputation."
        ),
        platform(
            id = "dos", name = "MS-DOS", short = "DOS",
            maker = "Microsoft", year = 1981, argb = 0xFF455A64,
            ext = setOf("exe", "com", "bat", "conf"),
            screenscraper = "135", igdb = "13",
            about = "Two decades of PC gaming before Windows took over, run here through DOSBox. Configuration is part of the experience in a way no console platform is."
        ),
        platform(
            id = "scummvm", name = "ScummVM", short = "SCUMM",
            maker = "Various", year = null, argb = 0xFF00838F,
            ext = setOf("scummvm", "svm"),
            screenscraper = "123", igdb = "13",
            about = "Not hardware — an engine reimplementation that runs point-and-click adventures without the machines they were written for. Games are added as folders rather than as single files."
        ),

        platform(
            id = ID_PC, name = "PC", short = "PC",
            maker = "Various", year = null, argb = 0xFF546E7A,
            // `desktop` is Winlator's own shortcut file, which is what it
            // launches by — see the Winlator entry in EmulatorRegistry.
            ext = setOf("exe", "lnk", "sh", "desktop"),
            screenscraper = "135", igdb = "6",
            about = "Not a console at all, and the only platform here nobody designed. Everything from storefront launchers to loose executables, held together by whatever the user points Loki at."
        ),
        platform(
            id = ID_ANDROID, name = "Android", short = "Android",
            maker = "Google", year = 2008, argb = 0xFF3DDC84,
            ext = setOf("apk"),
            screenscraper = "63", igdb = "34",
            about = "The system this launcher runs on. Installed applications appear here automatically, so a handheld's store apps, emulators and browsers sit alongside everything scanned from disk."
        ),
    )

    val BY_ID: Map<String, Platform> = ALL.associateBy(Platform::id)

    /**
     * Extensions that are containers rather than platform-specific dumps. The
     * scanner peeks inside these to work out what they actually hold.
     */
    val ARCHIVE_EXTENSIONS: Set<String> = setOf("zip", "7z", "rar", "chd", "gz", "tar")

    private fun platform(
        id: String,
        name: String,
        short: String,
        maker: String,
        year: Int?,
        argb: Long,
        ext: Set<String>,
        screenscraper: String? = null,
        igdb: String? = null,
        about: String = "",
    ): Platform = Platform(
        id = id,
        name = name,
        shortName = short,
        manufacturer = maker,
        releaseYear = year,
        accentArgb = argb,
        romExtensions = ext,
        providerIds = buildMap {
            screenscraper?.let { put("screenscraper", it) }
            igdb?.let { put("igdb", it) }
        },
        description = about,
    )

    /**
     * What a built-in platform says about itself.
     *
     * Looked up by id rather than carried through the database, so a platform row
     * written before these existed still reads back with one.
     */
    fun descriptionFor(platformId: String): String =
        BY_ID[platformId]?.description.orEmpty()
}
