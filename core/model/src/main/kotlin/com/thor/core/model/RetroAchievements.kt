package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * The user's RetroAchievements account.
 *
 * Two values and a switch. The web API key is not the site password — it is
 * found on the account's Settings page and can be reset there without changing
 * anything else, which is why asking for it is reasonable where asking for a
 * password would not be.
 */
@Serializable
data class RetroAchievementsSettings(
    val username: String = "",
    /** The web API key, from the account's own settings page. */
    val apiKey: String = "",
    val enabled: Boolean = false,
    /**
     * Count only achievements earned without save states or rewind.
     *
     * The site tracks both and most people who care about this care about
     * hardcore specifically — an achievement earned with rewind is not the same
     * claim. Off by default because a fresh account has nothing in hardcore and
     * a panel reading 0 of 40 on a game they have finished reads as broken.
     */
    val hardcoreOnly: Boolean = false,
    /** Epoch millis of the last successful sync; null before the first. */
    val lastSyncedEpochMs: Long? = null,
) {
    /** True when there is enough here to make a request at all. */
    val isConfigured: Boolean get() = username.isNotBlank() && apiKey.isNotBlank()

    val isActive: Boolean get() = enabled && isConfigured
}

/**
 * Which RetroAchievements console a platform corresponds to.
 *
 * The site keys everything on its own console ids, and they are stable — they
 * are part of its public API and its URLs. Hard-coded rather than fetched
 * because the mapping is between *this* launcher's platform ids and theirs, and
 * no endpoint can supply that half of it: `API_GetConsoleIDs` would tell us that
 * console 3 is called "SNES", and deciding that Loki's `snes` means console 3 is
 * still a judgement someone has to make.
 *
 * Systems with no entry simply have no achievements, which is the honest answer
 * for the ones RetroAchievements does not cover.
 */
object RetroAchievementsConsoles {

    /** Loki platform id to RetroAchievements console id. */
    val BY_PLATFORM: Map<String, Int> = mapOf(
        "genesis" to 1,
        "n64" to 2,
        "snes" to 3,
        "gb" to 4,
        "gba" to 5,
        "gbc" to 6,
        "nes" to 7,
        "pcengine" to 8,
        "segacd" to 9,
        "sega32x" to 10,
        "mastersystem" to 11,
        "psx" to 12,
        "lynx" to 13,
        "ngpc" to 14,
        "gamegear" to 15,
        "jaguar" to 17,
        "nds" to 18,
        "psp" to 41,
        "ps2" to 21,
        "atari2600" to 25,
        "arcade" to 27,
        "virtualboy" to 28,
        "msx" to 29,
        "sg1000" to 33,
        "amstradcpc" to 37,
        "saturn" to 39,
        "dreamcast" to 40,
        "3do" to 43,
        "colecovision" to 44,
        "intellivision" to 45,
        "pcenginecd" to 76,
        "atari7800" to 51,
        "wonderswan" to 53,
        "neogeo" to 27,
        "3ds" to 62,
    )

    fun consoleFor(platformId: String): Int? = BY_PLATFORM[platformId]

    /** True when this platform has any chance of a match. */
    fun isSupported(platformId: String): Boolean = platformId in BY_PLATFORM

    /**
     * Where a badge image lives.
     *
     * Built rather than returned by the API, which gives only the badge's name.
     * The path shape is part of the site's public media layout.
     */
    fun badgeUrl(badgeName: String): String =
        "https://media.retroachievements.org/Badge/$badgeName.png"
}
