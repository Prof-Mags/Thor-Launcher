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
        platform(
            id = ID_PC, name = "PC", short = "PC",
            maker = "Various", year = null, argb = 0xFF546E7A,
            ext = setOf("exe", "lnk", "sh"),
            screenscraper = "135", igdb = "6",
            about = "Not a console at all, and the only platform here nobody designed. Everything from storefront launchers to loose executables, held together by whatever the user points THOR at."
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
