package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A platform's artwork, as supplied by an icon pack.
 *
 * Three images because packs ship three and each answers a different question:
 * the icon is what a platform looks like as one cell on the grid, the hero is
 * what it looks like filling a panel, and the logo is its name drawn rather than
 * typeset. None is derivable from the others — downscaling a hero gives a muddy
 * icon, and upscaling an icon gives a blurry hero.
 *
 * Every field is nullable and independently so: packs are made by hand and are
 * routinely incomplete. The pack this format was taken from ships several
 * zero-byte `logo.png` files, which is a missing logo written a different way.
 */
@Serializable
data class PlatformArtwork(
    /** Square icon, for the platform's folder on the grid. */
    val iconUri: String? = null,
    /** Wide banner, for the information panel's backdrop. */
    val heroUri: String? = null,
    /** Wordmark, for the open-folder banner. */
    val logoUri: String? = null,
    /** Which pack supplied these, so removing it can take them back out. */
    val packId: String? = null,
) {
    val isEmpty: Boolean get() = iconUri == null && heroUri == null && logoUri == null

    /**
     * Chosen by hand, and therefore nobody else's to change *by accident*.
     *
     * Recorded in [packId] rather than as a separate flag because it answers the
     * same question every other owner does — who put this here — and every rule
     * that already respects pack ownership then respects a hand-picked image for
     * free: the scraper leaves it alone, and the artwork the launcher ships does
     * not show through over the top of it.
     *
     * Installing an icon pack is the deliberate exception and does cover it. A
     * pack is something the user went and found, and one that silently skipped
     * the systems they had dressed themselves looked like a pack that had not
     * installed at all. What it covers is kept and handed back if the pack is
     * uninstalled, so the choice is displaced rather than lost.
     */
    val isUserChosen: Boolean get() = packId == USER_PACK_ID

    companion object {
        val NONE = PlatformArtwork()

        /** Not a real pack; no installed pack can collide with it. */
        const val USER_PACK_ID = "user:custom"
    }
}

/**
 * An installed icon pack.
 *
 * @param artworkBySlug every platform folder the pack shipped, keyed by the
 *   pack's own slug — including the ones THOR has no platform for. Those are
 *   imported and kept rather than discarded, so that adding the platform later
 *   is enough to make its artwork appear; re-importing the pack is not required
 *   and, for a pack the user no longer has to hand, might not be possible.
 * @param appliedPlatformIds the THOR platform ids currently wearing this pack's
 *   artwork. Recorded rather than recomputed, because removing a pack has to put
 *   back exactly what it changed and nothing else — a later pack may have
 *   overwritten some of these since.
 */
@Serializable
data class IconPack(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String = "",
    val artworkBySlug: Map<String, PlatformArtwork> = emptyMap(),
    val appliedPlatformIds: List<String> = emptyList(),
    val installedAtEpochMs: Long = 0L,
) {
    /** Platforms this pack is dressing right now. */
    val appliedCount: Int get() = appliedPlatformIds.size

    /** Platform folders imported but with no THOR platform to put them on yet. */
    val heldCount: Int get() = (artworkBySlug.size - appliedPlatformIds.size).coerceAtLeast(0)

    /**
     * This pack's artwork for a platform, if it has any.
     *
     * Checks the platform's own id first and then every slug that aliases onto
     * it, so a pack that filed its GameCube art under `gc` is found by a lookup
     * for `gamecube`.
     */
    fun artworkFor(platformId: String): PlatformArtwork? =
        artworkBySlug[platformId]
            ?: artworkBySlug.entries
                .firstOrNull { IconPackSlugs.platformIdFor(it.key) == platformId }
                ?.value
}

/**
 * The folder a platform's games are filed into.
 *
 * The id is derived from the platform rather than random, so the folder survives
 * rescans, renames and re-theming — and so anything holding a platform can find
 * its cell on the grid. Both directions live here, in the domain, because both
 * the layout repository that creates these folders and the surfaces that dress
 * them need the format and neither should own it.
 */
object PlatformFolders {

    private const val PREFIX = "folder:platform:"

    fun idFor(platformId: String): String = "$PREFIX$platformId"

    /** The platform a folder belongs to, or null if it is not a platform folder. */
    fun platformIdOf(folderId: String): String? =
        folderId.removePrefix(PREFIX).takeIf { it != folderId && it.isNotEmpty() }
}

/**
 * Maps a pack's platform folder name onto a THOR platform id.
 *
 * Packs for these handhelds follow a shared, undeclared naming convention — the
 * one used by the emulator front-ends they were originally made for — and most of
 * it happens to match THOR's own ids. The rest is this table.
 *
 * Three kinds of entry live here:
 *
 *  - **Different name, same machine.** `gc` and `n3ds` are what everyone else
 *    calls GameCube and the 3DS.
 *  - **More specific than THOR.** `mame` and `fbneo` are two arcade emulators
 *    rather than two arcade platforms; `genesismsu` and `snesmsu1` are the
 *    CD-audio hack of a console THOR already has. All collapse onto the parent.
 *  - **Not a console at all.** `steam` and `windows` are PC, which THOR does have.
 *
 * Anything unmatched is **kept, not dropped**. THOR models 25 platforms and packs
 * routinely ship more — this one has Game Gear, Neo Geo Pocket and Neo Geo Pocket
 * Colour, none of which THOR has yet. Their artwork is imported and held against
 * the slug it arrived under, so adding the platform later is all it takes for the
 * art to appear. Discarding it would mean the user had to still have the pack,
 * and remember which one it was, months after installing it.
 */
object IconPackSlugs {

    /**
     * Slugs that differ from THOR's ids.
     *
     * Several map onto the same platform. That is intentional and not a
     * conflict — see [platformIdFor] for which one wins.
     */
    private val ALIASES: Map<String, String> = mapOf(
        // Different name for the same machine.
        "gc" to "gamecube",
        "n3ds" to "3ds",
        // The mono Neo Geo Pocket runs on the same emulators as the colour
        // model and shares its artwork; THOR models the one machine.
        "ngp" to "ngpc",
        "ngc" to "gamecube",
        "megadrive" to "genesis",
        "md" to "genesis",
        "ps1" to "psx",
        "psone" to "psx",
        "vb" to "virtualboy",
        "gameboy" to "gb",
        "gameboycolor" to "gbc",
        "gameboyadvance" to "gba",

        // Emulators, not platforms.
        "mame" to "arcade",
        "fbneo" to "arcade",
        "fba" to "arcade",
        "neogeo" to "arcade",

        // Variants and hacks of a console THOR already models.
        "genesismsu" to "genesis",
        "snesmsu1" to "snes",
        "segacd" to "genesis",
        "fds" to "nes",
        "famicom" to "nes",
        "superfamicom" to "snes",

        // Not consoles.
        "steam" to "pc",
        "windows" to "pc",
        "dos" to "pc",
    )

    /**
     * The THOR platform this slug belongs to, or null when THOR has no such
     * platform *yet*.
     *
     * An exact id match always wins over an alias, so a pack naming a folder
     * exactly as THOR does is never redirected by a coincidence in this table —
     * and so a platform added to THOR later automatically stops being aliased
     * away. Adding a Famicom Disk System platform with id `fds`, for instance,
     * immediately takes precedence over the `fds` → `nes` fallback below.
     */
    fun platformIdFor(slug: String): String? {
        val normalized = normalize(slug)
        if (normalized.isEmpty()) return null
        if (BuiltInPlatforms.BY_ID.containsKey(normalized)) return normalized
        return ALIASES[normalized]?.takeIf { BuiltInPlatforms.BY_ID.containsKey(it) }
    }

    /**
     * The key a slug's artwork is filed under, whether or not THOR has the
     * platform.
     *
     * Always the slug as the pack wrote it, normalised — never the alias target.
     * Filing `gc` under `gamecube` would be lossy in the one direction that
     * matters: if a future THOR ever models the two separately, the original name
     * is the only thing that could tell them apart, and it would already be gone.
     * Aliasing is a question for lookup time, which is what [platformIdFor] is.
     */
    fun storageKeyFor(slug: String): String = normalize(slug)

    private fun normalize(slug: String): String = slug.trim().lowercase()
}
