package com.thor.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings navigation rail.
 *
 * Six categories, down from seventeen. The original list mirrored the feature
 * breakdown rather than the way anyone actually looks for a setting: separate
 * pages for Themes and Personalization, for Networking and Achievements, for
 * Performance and Display, meant the rail needed scrolling and every lookup
 * became a guess about which of two plausible pages owned the option.
 *
 * These group by the question being asked — how does it look, what is in it,
 * how do I drive it, how does it run, how is it stored, what is it — and each
 * pane uses section headers for the finer structure.
 */
enum class SettingsCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val summary: String,
) {
    APPEARANCE(
        "appearance", "Appearance", Icons.Rounded.Palette,
        "Theme, wallpaper, grid, dock and motion",
    ),
    LIBRARY(
        "library", "Library", Icons.AutoMirrored.Rounded.LibraryBooks,
        "ROM directories, scanning, platforms and metadata",
    ),
    CONTROLS(
        "controls", "Controls", Icons.Rounded.Gamepad,
        "Buttons, navigation, haptics and sound",
    ),
    DISPLAY(
        "display", "Display", Icons.Rounded.Monitor,
        "Dual screen behaviour and performance",
    ),
    SYSTEM(
        "system", "System", Icons.Rounded.Tune,
        "Accessibility, backup, storage and developer tools",
    ),
    ABOUT(
        "about", "About", Icons.Rounded.Info,
        "Version and device information",
    ),
    ;

    companion object {
        fun byId(id: String): SettingsCategory? = entries.firstOrNull { it.id == id }
    }
}
