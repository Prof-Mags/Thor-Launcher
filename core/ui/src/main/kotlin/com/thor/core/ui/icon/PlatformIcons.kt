package com.thor.core.ui.icon

import androidx.annotation.DrawableRes
import com.thor.core.model.PlatformArtwork
import com.thor.core.ui.R

/**
 * Artwork shipped with the launcher for the systems it knows about.
 *
 * Not an icon pack, deliberately. A pack is content the user installed: it is
 * recorded in settings, written onto the platform rows and onto the folders on
 * the grid, and survives being uninstalled because the files were copied in.
 * This is a *fallback*, resolved when something is drawn and stored nowhere — so
 * installing a pack, or picking an image by hand, simply wins without anything
 * having to be undone first, and nothing is duplicated into every profile's
 * database.
 *
 * Six systems have no artwork here — SG-1000, Amiga, ZX Spectrum, MSX,
 * ColecoVision and Intellivision — and fall back to the drawn glyph as before.
 * A missing entry is not an error; it is a platform this set does not cover.
 */
object PlatformIcons {

    /** The bundled icon for a platform, or null if this set has none. */
    @DrawableRes
    fun forPlatform(platformId: String?): Int? = platformId?.let(ICONS::get)

    /**
     * The bundled icon, unless something with a better claim is already there.
     *
     * The rule the user asked for is "these, unless another icon pack is
     * selected" — and mere presence of artwork is not that. A platform folder
     * usually *has* an icon already: the scraper writes one onto the platform
     * and the folder wears it from the moment it is created, which is why simply
     * preferring existing artwork showed none of these on a scraped library.
     *
     * Ownership is the question, and [PlatformArtwork.packId] answers it. A pack
     * the user installed, or an image they picked by hand, keeps its place.
     * Anything a scraper happened to find does not: this set is chosen, and one
     * console render beats a stray platform image pulled off the internet.
     */
    @DrawableRes
    fun preferredOver(artwork: PlatformArtwork?, platformId: String?): Int? {
        val ownedByUserOrPack = artwork?.packId != null
        return if (ownedByUserOrPack) null else forPlatform(platformId)
    }

    /**
     * Keyed by platform id rather than by the artwork's own filename.
     *
     * The two disagree more often than they agree — the artist names a file
     * after what the console was called, and several of those names are the
     * regional one: `segagen` for the Mega Drive, `turbografx16` for the PC
     * Engine, `ps1` for what the model calls `psx`. Resolving that here, once,
     * keeps every caller asking the same question it always asked.
     */
    private val ICONS: Map<String, Int> = mapOf(
        "3do" to R.drawable.platform_3do,
        "3ds" to R.drawable.platform_3ds,
        "amstradcpc" to R.drawable.platform_amstradcpc,
        "arcade" to R.drawable.platform_arcade,
        "atari2600" to R.drawable.platform_atari2600,
        "atari7800" to R.drawable.platform_atari7800,
        "c64" to R.drawable.platform_c64,
        "dos" to R.drawable.platform_dos,
        "dreamcast" to R.drawable.platform_dreamcast,
        "gamecube" to R.drawable.platform_gamecube,
        "gamegear" to R.drawable.platform_gamegear,
        "gb" to R.drawable.platform_gb,
        "gba" to R.drawable.platform_gba,
        "gbc" to R.drawable.platform_gbc,
        "genesis" to R.drawable.platform_genesis,
        "jaguar" to R.drawable.platform_jaguar,
        "lynx" to R.drawable.platform_lynx,
        "mastersystem" to R.drawable.platform_mastersystem,
        "n64" to R.drawable.platform_n64,
        "nds" to R.drawable.platform_nds,
        "neogeo" to R.drawable.platform_neogeo,
        "nes" to R.drawable.platform_nes,
        "ngpc" to R.drawable.platform_ngpc,
        "pcengine" to R.drawable.platform_pcengine,
        "pcenginecd" to R.drawable.platform_pcenginecd,
        "ps2" to R.drawable.platform_ps2,
        "ps3" to R.drawable.platform_ps3,
        "psp" to R.drawable.platform_psp,
        "psvita" to R.drawable.platform_psvita,
        "psx" to R.drawable.platform_psx,
        "saturn" to R.drawable.platform_saturn,
        "scummvm" to R.drawable.platform_scummvm,
        "sega32x" to R.drawable.platform_sega32x,
        "segacd" to R.drawable.platform_segacd,
        "snes" to R.drawable.platform_snes,
        "switch" to R.drawable.platform_switch,
        "virtualboy" to R.drawable.platform_virtualboy,
        "wii" to R.drawable.platform_wii,
        "wiiu" to R.drawable.platform_wiiu,
        "wonderswan" to R.drawable.platform_wonderswan,
        "xbox" to R.drawable.platform_xbox,
    )
}
