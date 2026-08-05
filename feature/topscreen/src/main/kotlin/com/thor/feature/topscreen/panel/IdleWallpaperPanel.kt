package com.thor.feature.topscreen.panel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.ui.component.AnimatedWallpaperBackground

/**
 * Shown when nothing is highlighted.
 *
 * Delegates to the shared wallpaper renderer so the info screen and the grid
 * screen show the same effect — previously this drew its own private gradient,
 * which meant changing the wallpaper setting visibly altered one screen and not
 * the other.
 */
@Composable
fun IdleWallpaperPanel(
    wallpaper: AnimatedWallpaper,
    wallpaperUri: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedWallpaperBackground(
        wallpaper = wallpaper,
        imageUri = wallpaperUri,
        modifier = modifier.fillMaxSize(),
    )
}
