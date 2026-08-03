package com.thor.core.model

/**
 * Editorial metadata used by the platform information panel.
 *
 * This is packaged with Loki, just like [BuiltInPlatforms]' descriptions. It is
 * intentionally separate from scraped game metadata: these facts describe the
 * hardware and remain useful before a library has ever been scraped.
 */
data class PlatformProfile(
    val category: String,
    val systemGlyph: PlatformGlyph,
    val highlights: List<PlatformHighlight>,
)

data class PlatformHighlight(
    val title: String,
    val description: String,
    val glyph: PlatformGlyph,
)

/** Stable semantic icon identities; each UI can draw them in its own style. */
enum class PlatformGlyph {
    GAME_LIBRARY,
    FAVOURITE,
    CLOCK,
    PLAYTIME,
    PLAY,
    DUAL_SCREEN,
    DEPTH,
    TOUCH,
    SOCIAL,
    MOTION,
    HYBRID,
    PORTABLE,
    LINK,
    DISC,
    CUBE,
    ONLINE,
    ARCADE,
    PERFORMANCE,
    KEYBOARD,
    MULTIPLAYER,
    MEDIA,
    CLASSICS,
}

object PlatformProfiles {
    fun forPlatform(platform: Platform): PlatformProfile =
        exact[platform.id] ?: when (platform.id) {
            in handhelds -> handheldProfile
            in homeComputers -> computerProfile
            in discSystems -> discProfile
            else -> classicConsoleProfile
        }

    private fun profile(
        category: String,
        systemGlyph: PlatformGlyph,
        vararg highlights: PlatformHighlight,
    ) = PlatformProfile(category, systemGlyph, highlights.toList())

    private fun feature(
        title: String,
        description: String,
        glyph: PlatformGlyph,
    ) = PlatformHighlight(title, description, glyph)

    private val exact = mapOf(
        "3ds" to profile(
            "DUAL-SCREEN HANDHELD",
            PlatformGlyph.DUAL_SCREEN,
            feature("Glasses-free 3D", "Adjustable depth without special glasses.", PlatformGlyph.DEPTH),
            feature("Touch Screen", "Dual-screen play with precise touch input.", PlatformGlyph.TOUCH),
            feature("StreetPass & SpotPass", "Connect, share and discover on the go.", PlatformGlyph.SOCIAL),
        ),
        "nds" to profile(
            "DUAL-SCREEN HANDHELD",
            PlatformGlyph.DUAL_SCREEN,
            feature("Two-screen play", "Games span two displays in inventive ways.", PlatformGlyph.DUAL_SCREEN),
            feature("Touch controls", "Stylus input built into every system.", PlatformGlyph.TOUCH),
            feature("Local wireless", "Nearby multiplayer without another cable.", PlatformGlyph.LINK),
        ),
        "switch" to profile(
            "HYBRID GAME SYSTEM",
            PlatformGlyph.HYBRID,
            feature("Hybrid play", "Move from television to handheld instantly.", PlatformGlyph.HYBRID),
            feature("Detachable controls", "Flexible Joy-Con play for one or many.", PlatformGlyph.MOTION),
            feature("Local multiplayer", "Share the screen wherever you are.", PlatformGlyph.MULTIPLAYER),
        ),
        "wii" to profile(
            "MOTION HOME CONSOLE",
            PlatformGlyph.MOTION,
            feature("Motion controls", "Point, swing and steer with natural movement.", PlatformGlyph.MOTION),
            feature("Virtual Console", "A home for generations of Nintendo classics.", PlatformGlyph.CLASSICS),
            feature("Social play", "Designed to bring everyone into the game.", PlatformGlyph.MULTIPLAYER),
        ),
        "wiiu" to profile(
            "DUAL-SCREEN HOME CONSOLE",
            PlatformGlyph.DUAL_SCREEN,
            feature("Second-screen play", "The GamePad adds a private display.", PlatformGlyph.DUAL_SCREEN),
            feature("Touch interaction", "Menus, maps and creation at your fingertips.", PlatformGlyph.TOUCH),
            feature("Off-TV play", "Keep playing when the television is occupied.", PlatformGlyph.PORTABLE),
        ),
        "virtualboy" to profile(
            "STEREOSCOPIC TABLETOP",
            PlatformGlyph.DEPTH,
            feature("True stereoscopy", "Separate displays create physical depth.", PlatformGlyph.DEPTH),
            feature("Distinct library", "A small catalogue built around its display.", PlatformGlyph.CLASSICS),
            feature("Tabletop design", "A singular portable experiment.", PlatformGlyph.PORTABLE),
        ),
        "psx" to playStationProfile("FIFTH-GENERATION CONSOLE"),
        "ps2" to playStationProfile("SIXTH-GENERATION CONSOLE"),
        "ps3" to profile(
            "HD HOME CONSOLE",
            PlatformGlyph.DISC,
            feature("Cell-powered games", "Distinctive hardware built for ambitious worlds.", PlatformGlyph.PERFORMANCE),
            feature("Blu-ray library", "High-capacity games and HD media in one box.", PlatformGlyph.DISC),
            feature("Connected play", "Online services and digital releases built in.", PlatformGlyph.ONLINE),
        ),
        "psp" to portablePlayStationProfile("PORTABLE GAME SYSTEM"),
        "psvita" to profile(
            "PREMIUM HANDHELD",
            PlatformGlyph.PORTABLE,
            feature("OLED handheld", "Console-scale worlds on a vivid display.", PlatformGlyph.PORTABLE),
            feature("Touch front and back", "Two touch surfaces expand every control.", PlatformGlyph.TOUCH),
            feature("Remote Play", "Continue compatible console games away from the TV.", PlatformGlyph.LINK),
        ),
        "xbox" to profile(
            "SIXTH-GENERATION CONSOLE",
            PlatformGlyph.GAME_LIBRARY,
            feature("Built-in storage", "A hard drive made saves and soundtracks effortless.", PlatformGlyph.PERFORMANCE),
            feature("Xbox Live", "A unified online console community.", PlatformGlyph.ONLINE),
            feature("Four-player ready", "Controller ports made local play immediate.", PlatformGlyph.MULTIPLAYER),
        ),
        "dreamcast" to profile(
            "ONLINE-READY CONSOLE",
            PlatformGlyph.DISC,
            feature("Online from day one", "A built-in modem brought the console online.", PlatformGlyph.ONLINE),
            feature("Arcade at home", "Sega's arcade hardware translated beautifully.", PlatformGlyph.ARCADE),
            feature("VMU companion", "Portable memory with a screen of its own.", PlatformGlyph.DUAL_SCREEN),
        ),
        "arcade" to profile(
            "COIN-OPERATED ORIGINALS",
            PlatformGlyph.ARCADE,
            feature("Original hardware", "Purpose-built boards with a distinct feel.", PlatformGlyph.ARCADE),
            feature("Score chasing", "Immediate challenges made for repeat play.", PlatformGlyph.PERFORMANCE),
            feature("Shared screen", "Competitive and cooperative local classics.", PlatformGlyph.MULTIPLAYER),
        ),
        BuiltInPlatforms.ID_PC to profile(
            "OPEN GAME PLATFORM",
            PlatformGlyph.KEYBOARD,
            feature("Flexible library", "Decades of storefronts, discs and executables.", PlatformGlyph.GAME_LIBRARY),
            feature("Scalable performance", "Tune every game for the hardware at hand.", PlatformGlyph.PERFORMANCE),
            feature("Any input", "Controller, keyboard, mouse or a custom setup.", PlatformGlyph.KEYBOARD),
        ),
        BuiltInPlatforms.ID_ANDROID to profile(
            "MOBILE GAME PLATFORM",
            PlatformGlyph.TOUCH,
            feature("Touch first", "Direct interaction designed for the display.", PlatformGlyph.TOUCH),
            feature("Always connected", "Online services and stores travel with you.", PlatformGlyph.ONLINE),
            feature("Launcher native", "Apps and games live beside the emulated library.", PlatformGlyph.GAME_LIBRARY),
        ),
        "scummvm" to profile(
            "ADVENTURE ENGINE",
            PlatformGlyph.CLASSICS,
            feature("Preserved adventures", "Classic stories run on modern hardware.", PlatformGlyph.CLASSICS),
            feature("Point and click", "Mouse-driven interaction remains intact.", PlatformGlyph.TOUCH),
            feature("Broad compatibility", "Many original engines in one library.", PlatformGlyph.GAME_LIBRARY),
        ),
    )

    private fun playStationProfile(category: String) = profile(
        category,
        PlatformGlyph.DISC,
        feature("Disc-based worlds", "Large soundtracks, cinematics and 3D adventures.", PlatformGlyph.DISC),
        feature("Memory card era", "Progress moved with you between consoles.", PlatformGlyph.MEDIA),
        feature("Genre-defining library", "Landmark releases across every style of play.", PlatformGlyph.CLASSICS),
    )

    private fun portablePlayStationProfile(category: String) = profile(
        category,
        PlatformGlyph.PORTABLE,
        feature("Console in hand", "Full-scale 3D designed for portable play.", PlatformGlyph.PORTABLE),
        feature("Widescreen media", "Games, music and video on one display.", PlatformGlyph.MEDIA),
        feature("Local link play", "Nearby systems connect without a television.", PlatformGlyph.LINK),
    )

    private val handheldProfile = profile(
        "PORTABLE GAME SYSTEM",
        PlatformGlyph.PORTABLE,
        feature("Play anywhere", "A complete library designed to travel.", PlatformGlyph.PORTABLE),
        feature("Link play", "Connect nearby systems for shared games.", PlatformGlyph.LINK),
        feature("Handheld classics", "Focused experiences built for shorter sessions.", PlatformGlyph.CLASSICS),
    )

    private val computerProfile = profile(
        "HOME COMPUTER",
        PlatformGlyph.KEYBOARD,
        feature("Keyboard control", "A full set of keys expands how games play.", PlatformGlyph.KEYBOARD),
        feature("Creative hardware", "Games share the machine with tools and demos.", PlatformGlyph.PERFORMANCE),
        feature("Deep catalogue", "Commercial, shareware and home-grown releases.", PlatformGlyph.GAME_LIBRARY),
    )

    private val discProfile = profile(
        "DISC-BASED GAME SYSTEM",
        PlatformGlyph.DISC,
        feature("Optical media", "More room for audio, art and expansive games.", PlatformGlyph.DISC),
        feature("Arcade heritage", "Fast, expressive games rooted in the arcade.", PlatformGlyph.ARCADE),
        feature("Local multiplayer", "Built around controllers and a shared screen.", PlatformGlyph.MULTIPLAYER),
    )

    private val classicConsoleProfile = profile(
        "CLASSIC GAME SYSTEM",
        PlatformGlyph.GAME_LIBRARY,
        feature("Signature library", "Games that established a distinct identity.", PlatformGlyph.CLASSICS),
        feature("Instant play", "Focused experiences with no unnecessary friction.", PlatformGlyph.GAME_LIBRARY),
        feature("Couch multiplayer", "Shared-screen competition and cooperation.", PlatformGlyph.MULTIPLAYER),
    )

    private val handhelds = setOf(
        "gb", "gbc", "gba", "gamegear", "ngpc", "lynx", "wonderswan",
    )

    private val homeComputers = setOf(
        "c64", "amiga", "zxspectrum", "amstradcpc", "msx", "dos",
    )

    private val discSystems = setOf(
        "gamecube", "segacd", "saturn", "pcenginecd", "neogeo", "3do",
    )
}
