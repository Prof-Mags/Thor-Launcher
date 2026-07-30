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
) {
    /** The emulator games on this platform launch with, if any. */
    val defaultEmulatorPackage: String? get() = emulatorPackages.firstOrNull()
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
        ),
        platform(
            id = "snes", name = "Super Nintendo", short = "SNES",
            maker = "Nintendo", year = 1990, argb = 0xFF5C4B8B,
            ext = setOf("sfc", "smc", "swc", "fig", "bs"),
            screenscraper = "4", igdb = "19",
        ),
        platform(
            id = "n64", name = "Nintendo 64", short = "N64",
            maker = "Nintendo", year = 1996, argb = 0xFF2E7D32,
            ext = setOf("n64", "z64", "v64", "ndd"),
            screenscraper = "14", igdb = "4",
        ),
        platform(
            id = "gamecube", name = "Nintendo GameCube", short = "GCN",
            maker = "Nintendo", year = 2001, argb = 0xFF4527A0,
            ext = setOf("iso", "gcm", "gcz", "rvz", "ciso", "dol", "elf"),
            screenscraper = "13", igdb = "21",
        ),
        platform(
            id = "wii", name = "Nintendo Wii", short = "Wii",
            maker = "Nintendo", year = 2006, argb = 0xFF1E88E5,
            ext = setOf("iso", "wbfs", "rvz", "wad", "gcz", "ciso"),
            screenscraper = "16", igdb = "5",
        ),
        platform(
            id = "wiiu", name = "Nintendo Wii U", short = "Wii U",
            maker = "Nintendo", year = 2012, argb = 0xFF0277BD,
            ext = setOf("wud", "wux", "wua", "rpx"),
            screenscraper = "18", igdb = "41",
        ),
        platform(
            id = "switch", name = "Nintendo Switch", short = "Switch",
            maker = "Nintendo", year = 2017, argb = 0xFFE53935,
            ext = setOf("nsp", "xci", "nca", "nro"),
            screenscraper = "225", igdb = "130",
        ),
        platform(
            id = "gb", name = "Game Boy", short = "GB",
            maker = "Nintendo", year = 1989, argb = 0xFF7A8B3F,
            ext = setOf("gb"),
            screenscraper = "9", igdb = "33",
        ),
        platform(
            id = "gbc", name = "Game Boy Color", short = "GBC",
            maker = "Nintendo", year = 1998, argb = 0xFFF9A825,
            ext = setOf("gbc", "cgb"),
            screenscraper = "10", igdb = "22",
        ),
        platform(
            id = "gba", name = "Game Boy Advance", short = "GBA",
            maker = "Nintendo", year = 2001, argb = 0xFF6A1B9A,
            ext = setOf("gba", "agb"),
            screenscraper = "12", igdb = "24",
        ),
        platform(
            id = "nds", name = "Nintendo DS", short = "DS",
            maker = "Nintendo", year = 2004, argb = 0xFF00838F,
            ext = setOf("nds", "dsi", "ids"),
            screenscraper = "15", igdb = "20",
        ),
        platform(
            id = "3ds", name = "Nintendo 3DS", short = "3DS",
            maker = "Nintendo", year = 2011, argb = 0xFFD81B60,
            ext = setOf("3ds", "cci", "cxi", "cia", "app", "3dsx"),
            screenscraper = "17", igdb = "37",
        ),
        platform(
            id = "virtualboy", name = "Virtual Boy", short = "VB",
            maker = "Nintendo", year = 1995, argb = 0xFFC62828,
            ext = setOf("vb", "vboy"),
            screenscraper = "11", igdb = "87",
        ),
        platform(
            id = "psx", name = "PlayStation", short = "PS1",
            maker = "Sony", year = 1994, argb = 0xFF37474F,
            ext = setOf("cue", "bin", "img", "pbp", "ecm", "m3u", "iso"),
            screenscraper = "57", igdb = "7",
        ),
        platform(
            id = "ps2", name = "PlayStation 2", short = "PS2",
            maker = "Sony", year = 2000, argb = 0xFF1A237E,
            ext = setOf("iso", "bin", "cue", "img", "gz", "cso"),
            screenscraper = "58", igdb = "8",
        ),
        platform(
            id = "ps3", name = "PlayStation 3", short = "PS3",
            maker = "Sony", year = 2006, argb = 0xFF263238,
            ext = setOf("iso", "pkg", "self", "elf"),
            screenscraper = "59", igdb = "9",
        ),
        platform(
            id = "psp", name = "PlayStation Portable", short = "PSP",
            maker = "Sony", year = 2004, argb = 0xFF455A64,
            ext = setOf("iso", "cso", "pbp", "chd", "prx"),
            screenscraper = "61", igdb = "38",
        ),
        platform(
            id = "psvita", name = "PlayStation Vita", short = "Vita",
            maker = "Sony", year = 2011, argb = 0xFF3949AB,
            ext = setOf("vpk", "psvgamesd", "mai"),
            screenscraper = "62", igdb = "46",
        ),
        platform(
            id = "xbox", name = "Xbox", short = "Xbox",
            maker = "Microsoft", year = 2001, argb = 0xFF2E7D32,
            ext = setOf("iso", "xbe"),
            screenscraper = "32", igdb = "11",
        ),
        platform(
            id = "arcade", name = "Arcade", short = "Arcade",
            maker = "Various", year = null, argb = 0xFFF57C00,
            ext = setOf("zip", "7z", "chd"),
            screenscraper = "75", igdb = "52",
        ),
        platform(
            id = "dreamcast", name = "Sega Dreamcast", short = "DC",
            maker = "Sega", year = 1998, argb = 0xFFEF6C00,
            ext = setOf("cdi", "gdi", "chd", "cue", "bin"),
            screenscraper = "23", igdb = "23",
        ),
        platform(
            id = "saturn", name = "Sega Saturn", short = "Saturn",
            maker = "Sega", year = 1994, argb = 0xFF212121,
            ext = setOf("cue", "bin", "iso", "chd", "mds", "ccd"),
            screenscraper = "22", igdb = "32",
        ),
        platform(
            id = "genesis", name = "Sega Genesis / Mega Drive", short = "MD",
            maker = "Sega", year = 1988, argb = 0xFF0D47A1,
            ext = setOf("md", "gen", "smd", "bin", "68k", "sgd"),
            screenscraper = "1", igdb = "29",
        ),
        platform(
            id = ID_PC, name = "PC", short = "PC",
            maker = "Various", year = null, argb = 0xFF546E7A,
            ext = setOf("exe", "lnk", "sh"),
            screenscraper = "135", igdb = "6",
        ),
        platform(
            id = ID_ANDROID, name = "Android", short = "Android",
            maker = "Google", year = 2008, argb = 0xFF3DDC84,
            ext = setOf("apk"),
            screenscraper = "63", igdb = "34",
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
    )
}
