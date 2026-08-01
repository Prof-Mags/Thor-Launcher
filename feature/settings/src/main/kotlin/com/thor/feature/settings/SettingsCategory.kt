package com.thor.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings navigation rail.
 *
 * Nine categories, down from seventeen and up from six. The original list
 * mirrored the feature breakdown rather than the way anyone actually looks for a
 * setting: separate pages for Themes and Personalization, for Networking and
 * Achievements, for Performance and Display, meant the rail needed scrolling and
 * every lookup became a guess about which of two plausible pages owned the
 * option.
 *
 * Collapsing that to six went one step too far in the other direction. Library
 * ended up holding eight pages — ROM folders beside icon packs beside a debrid
 * account — and two of them were about films, which are not a library of games
 * by any reading. A category is a promise about what is inside it, and "Library"
 * had stopped making one.
 *
 * These group by the question being asked, and none holds more than four pages:
 * how does it look, where do my games live, where does artwork come from, what
 * am I watching, how do I drive it, how is it displayed, how does it run, what
 * is it.
 */
enum class SettingsCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
    /** Kept for stored/deep-linked ids even when its pages move into a clearer group. */
    val visible: Boolean = true,
) {
    APPEARANCE(
        "appearance", "Personalization", Icons.Rounded.Palette,
        "Theme, wallpaper, interface and home layout",
    ),

    /**
     * The grid itself, as distinct from the colours over it.
     *
     * Split out because the two are edited for different reasons: a theme is
     * chosen once and admired, while grid density, icon shape and the cursor are
     * fiddled with until the home screen feels right. Having them in one
     * six-page category meant scrolling past the wallpaper to reach the thing
     * being adjusted.
     */
    HOME_SCREEN(
        "home", "Home screen", Icons.Rounded.GridView,
        "Grid layout, icon shape, dock and cursor",
        visible = false,
    ),
    LIBRARY(
        "library", "Games & artwork", Icons.AutoMirrored.Rounded.LibraryBooks,
        "Platforms, ROMs, scanning, metadata and artwork",
    ),

    /**
     * Where pictures come from, for games and platforms alike.
     *
     * Icon packs and the scraper answer the same question from two directions —
     * one imports artwork wholesale, the other fetches it per game — and having
     * them in different halves of a long Library category meant a blank box art
     * had two unrelated places to go and look.
     */
    ARTWORK(
        "artwork", "Artwork", Icons.Rounded.Image,
        "Scrapers, credentials and icon packs",
        visible = false,
    ),

    /**
     * Films and shows, which were filed under Library.
     *
     * A debrid token and a torrent indexer are not a library of games by any
     * reading, and putting them there meant the one category answered two
     * unrelated questions.
     */
    MOVIES(
        "movies", "Films & shows", Icons.Rounded.Movie,
        "Catalogue, sources and playback",
    ),
    /**
     * Streaming a PC, which is neither a library nor a film.
     *
     * Its own category for the same reason Films & shows has one: it is a
     * section of the launcher with its own hardware at the other end, and the
     * questions it raises — how sharp, how smooth, how much bandwidth — have no
     * bearing on anything else here.
     */
    STREAMING(
        "streaming", "PC streaming", Icons.Rounded.Cast,
        "Picture quality and how PCs are found",
    ),
    CONTROLS(
        "controls", "Controls", Icons.Rounded.Gamepad,
        "Buttons, navigation, pointer, haptics and sound",
    ),
    DISPLAY(
        "display", "Display & performance", Icons.Rounded.Monitor,
        "Dual-screen behaviour, animation and visual effects",
    ),
    SYSTEM(
        "system", "System & accessibility", Icons.Rounded.Tune,
        "Accessibility, diagnostics and launcher maintenance",
    ),
    ABOUT(
        "about", "About", Icons.Rounded.Info,
        "Version and device information",
    ),
    ;

    companion object {
        /** Categories actually shown in the navigation rail, in display order. */
        val navigationEntries: List<SettingsCategory> = entries.filter(SettingsCategory::visible)

        fun byId(id: String): SettingsCategory? = entries.firstOrNull { it.id == id }
    }
}
