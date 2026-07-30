package com.thor.core.model

/**
 * The games that stand for a system.
 *
 * A platform folder with no icon pack needs a picture, and the obvious source is
 * the library itself. The first attempt ranked the platform's games by play count
 * and took the top one — which is fine after a month and useless on day one,
 * because a fresh library has no play counts at all. Every tie then fell through
 * to alphabetical order, so the backdrop for the SNES was whichever game happened
 * to sort first. It looked exactly like what it was: an arbitrary pick.
 *
 * So the choice is made from a list instead of from statistics. These are the two
 * or three titles most people picture when they think of the machine, and they are
 * matched against what the user actually owns — a system whose flagship is not in
 * the library gets no game backdrop at all, which is better than a random one.
 *
 * Keys are normalised: lowercase, letters and digits only. That makes "Super Mario
 * World (USA).sfc", "super_mario_world" and "Super Mario World" the same string,
 * and makes a prefix match do the work of a fuzzy one — "supermariobros" covers
 * the whole numbered series without listing each entry.
 */
object PlatformFlagships {

    /**
     * Ordered by how strongly the title says the platform's name, not by sales.
     *
     * First match wins, so the head of each list is the picture that should
     * appear when the user owns more than one of them.
     */
    private val BY_PLATFORM: Map<String, List<String>> = mapOf(
        "nes" to listOf("supermariobros", "thelegendofzelda", "metroid", "megaman"),
        "snes" to listOf("supermarioworld", "thelegendofzeldaalinktothepast", "supermetroid", "chronotrigger", "donkeykongcountry"),
        "n64" to listOf("supermario64", "thelegendofzeldaocarinaoftime", "goldeneye", "mariokart64"),
        "gamecube" to listOf("thelegendofzeldathewindwaker", "supersmashbrosmelee", "metroidprime", "supermariosunshine"),
        "wii" to listOf("supermariogalaxy", "thelegendofzeldatwilightprincess", "mariokartwii", "wiisports"),
        "wiiu" to listOf("supermario3dworld", "thelegendofzeldabreathofthewild", "splatoon", "mariokart8"),
        "switch" to listOf("thelegendofzeldabreathofthewild", "supermarioodyssey", "animalcrossingnewhorizons", "metroiddread"),
        "gb" to listOf("pokemonred", "pokemonblue", "thelegendofzeldalinksawakening", "tetris", "supermarioland"),
        "gbc" to listOf("pokemongold", "pokemoncrystal", "thelegendofzeldaoracle", "pokemonsilver"),
        "gba" to listOf("thelegendofzeldaminishcap", "pokemonemerald", "metroidfusion", "castlevaniaaria", "advancewars"),
        "nds" to listOf("thelegendofzeldaphantomhourglass", "pokemonplatinum", "mariokartds", "castlevaniadawnofsorrow"),
        "3ds" to listOf("thelegendofzeldaalinkbetweenworlds", "supermario3dland", "fireemblemawakening", "monsterhunter4"),
        "virtualboy" to listOf("virtualboywarioland", "marioclash", "teleroboxer"),
        "psx" to listOf("finalfantasyvii", "metalgearsolid", "castlevaniasymphonyofthenight", "crashbandicoot", "residentevil2"),
        "ps2" to listOf("shadowofthecolossus", "godofwar", "grandtheftautosanandreas", "metalgearsolid3", "okami"),
        "ps3" to listOf("thelastofus", "uncharted2", "demonssouls", "metalgearsolid4"),
        "psp" to listOf("grandtheftautovicecitystories", "metalgearsolidpeacewalker", "godofwarchainsofolympus", "patapon"),
        "psvita" to listOf("persona4golden", "unchartedgoldenabyss", "gravityrush", "tearaway"),
        "xbox" to listOf("halocombatevolved", "halo2", "fable", "ninjagaidenblack"),
        "arcade" to listOf("streetfighterii", "metalslug", "pacman", "donkeykong"),
        "dreamcast" to listOf("sonicadventure", "shenmue", "jetsetradio", "crazytaxi"),
        "saturn" to listOf("nightsintodreams", "panzerdragoonsaga", "guardianheroes", "radiantsilvergun"),
        "genesis" to listOf("sonicthehedgehog", "streetsofrage", "goldenaxe", "phantasystariv"),
    )

    /**
     * Where [title] sits in [platformId]'s flagship list, or `null` if absent.
     *
     * Absence is a real answer and the caller must respect it: a game that is not
     * on the list is not a stand-in for the system, and reaching for it anyway is
     * how the arbitrary picks got in.
     */
    fun rankOf(platformId: String, title: String): Int? {
        val flagships = BY_PLATFORM[platformId] ?: return null
        val normalised = normalise(title)
        if (normalised.isEmpty()) return null

        val index = flagships.indexOfFirst { normalised.startsWith(it) }
        return index.takeIf { it >= 0 }
    }

    /** Lowercase, letters and digits only. */
    fun normalise(title: String): String = buildString(title.length) {
        title.forEach { char -> if (char.isLetterOrDigit()) append(char.lowercaseChar()) }
    }
}
