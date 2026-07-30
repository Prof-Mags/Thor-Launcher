package com.thor.feature.topscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.AppEntry
import com.thor.core.model.ClockStyle
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.LauncherStatusBar
import kotlinx.coroutines.delay

/**
 * The information panel.
 *
 * Everything here is derived from the current selection, so the panel is a pure
 * function of one value. The cross-fade is keyed on entry id rather than on the
 * whole state, which stops unrelated changes — a scan finishing, a page turning
 * — from re-triggering the transition.
 */
@Composable
fun TopScreen(
    selection: GridEntry?,
    platform: Platform?,
    wallpaper: AnimatedWallpaper,
    wallpaperUri: String?,
    folderChildren: List<GridEntry>,
    clockStyle: ClockStyle,
    showStatusBar: Boolean,
    /** Whether preview clips may play; off in performance mode. */
    videoPreviewsEnabled: Boolean,
    /**
     * Which screenshot fills the backdrop, owned by the view model rather than
     * remembered locally — LB/RB cycle it from the controller, so the panel
     * needs to react to the same value the input layer changes.
     */
    selectedScreenshot: Int,
    onScreenshotSelected: (Int) -> Unit,
    /**
     * Whether the controller is currently aimed at this panel.
     *
     * Drawn, not just tracked. This panel is a picture of the selection and has no
     * cursor of its own, so touching it to take the controller changed nothing
     * visible — the same panel, quietly reacting to different buttons. The edge and
     * its hints are the only way to tell that a press will land here.
     */
    focused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion
    val game = selection as? GameEntry

    val screenshots = game?.metadata?.artwork?.cappedScreenshots.orEmpty()

    /*
     * The slideshow advances on its own.
     *
     * Keyed on the current index as well as the game, so the timer restarts
     * whenever the user picks a shot with the bumpers or a tap — an auto-advance
     * firing a moment after a manual choice would feel like the input was
     * ignored. Suspended entirely while a preview clip is playing, since the
     * backdrop is showing video rather than the selected still.
     */
    LaunchedEffect(game?.id, selectedScreenshot, screenshots.size, videoPreviewsEnabled) {
        if (screenshots.size <= 1) return@LaunchedEffect
        delay(SLIDESHOW_INTERVAL_MS)
        onScreenshotSelected((selectedScreenshot + 1) % screenshots.size)
    }

    // A clip that fails to play must not leave the panel black; the still
    // artwork is drawn underneath and this flag simply stops covering it.
    var videoFailed by remember(game?.id) { mutableStateOf(false) }

    val videoUri = game?.metadata?.artwork?.videoUri
        ?.takeIf { videoPreviewsEnabled && !videoFailed }

    // Preview clips start after a dwell, so sweeping the cursor across a shelf
    // does not spin up a decoder for every game it passes over.
    var videoReady by remember(game?.id, videoUri) { mutableStateOf(false) }
    LaunchedEffect(game?.id, videoUri) {
        videoReady = false
        if (videoUri != null) {
            delay(PREVIEW_DWELL_MS)
            videoReady = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        AnimatedContent(
            targetState = selection?.id,
            transitionSpec = {
                fadeIn(motion.tweenSpec(motion.backdropMillis)) togetherWith
                    fadeOut(motion.tweenSpec(motion.backdropMillis))
            },
            label = "topScreenBackdrop",
        ) { _ ->
            Backdrop(
                selection = selection,
                wallpaperUri = wallpaperUri,
                screenshotIndex = selectedScreenshot,
            )
        }

        if (videoUri != null && videoReady) {
            GameVideoBackground(
                videoUri = videoUri,
                playing = true,
                onFailure = { videoFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Scrim()

        AnimatedContent(
            targetState = selection?.id,
            transitionSpec = {
                fadeIn(motion.tweenSpec(motion.detailMillis)) togetherWith
                    fadeOut(motion.tweenSpec(motion.detailMillis))
            },
            label = "topScreenDetail",
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            when (val entry = selection) {
                null -> IdleWallpaperPanel(wallpaper = wallpaper, wallpaperUri = wallpaperUri)

                is GameEntry -> GameDetailPanel(
                    game = entry,
                    platform = platform,
                    selectedScreenshot = selectedScreenshot,
                    onScreenshotSelected = onScreenshotSelected,
                )

                is FolderEntry -> FolderDetailPanel(folder = entry, children = folderChildren)
                is AppEntry -> AppDetailPanel(app = entry)
                else -> IdleWallpaperPanel(wallpaper = wallpaper, wallpaperUri = wallpaperUri)
            }
        }

        // The clock lives on this panel only, centred. It used to sit above the
        // grid as well, which put two clocks on screen at once.
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            LauncherStatusBar(clockStyle = clockStyle, visible = showStatusBar)
        }

        ControllerFocusEdge(
            visible = focused,
            canCycleScreenshots = screenshots.size > 1,
            canLaunch = selection != null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Marks this panel as the one the controller is driving.
 *
 * An edge rather than a cursor, because there is nothing here to put a cursor on:
 * focus belongs to the whole panel. The hints are part of the indicator rather than
 * decoration — the buttons do something different here than they do on the grid, and
 * a panel that silently changed what B meant would be worse than one that never took
 * focus at all.
 */
@Composable
private fun ControllerFocusEdge(
    visible: Boolean,
    canCycleScreenshots: Boolean,
    canLaunch: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenSpec(motion.cursorMillis)),
        exit = fadeOut(motion.tweenSpec(motion.cursorMillis)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = FOCUS_EDGE_WIDTH,
                    color = colors.cursor,
                    shape = RoundedCornerShape(FOCUS_EDGE_RADIUS),
                ),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = HINT_INSET)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.surface.copy(alpha = HINT_BACKGROUND_ALPHA))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (canCycleScreenshots) Hint("◀ ▶  Screenshots")
                if (canLaunch) Hint("A  Launch")
                Hint("B  Grid")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurface,
    )
}

/**
 * Full-bleed artwork behind the panel.
 *
 * Shows the selected screenshot when the user has picked one, so the strip in
 * the panel actually changes what is displayed.
 */
@Composable
private fun Backdrop(
    selection: GridEntry?,
    wallpaperUri: String?,
    screenshotIndex: Int,
) {
    val image = when (selection) {
        is GameEntry -> {
            val artwork = selection.metadata.artwork
            artwork.cappedScreenshots.getOrNull(screenshotIndex) ?: artwork.backgroundImage
        }

        is FolderEntry -> selection.artworkUri
        else -> null
    } ?: wallpaperUri

    if (image != null) {
        ArtworkImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Darkens the left side so the information panel stays legible.
 *
 * Kept light on the right: the artwork is the subject there, not a texture
 * behind text.
 */
@Composable
private fun Scrim() {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to colors.background.copy(alpha = 0.70f),
                        0.45f to colors.background.copy(alpha = 0.35f),
                        1.0f to Color.Transparent,
                    ),
                ),
            ),
    )
}

/** How long the cursor must rest on a game before its clip starts. */
private const val PREVIEW_DWELL_MS = 900L

/**
 * How long each screenshot is held before the slideshow advances.
 *
 * Long enough to actually look at, short enough that all four are seen while
 * deciding what to play.
 */
private const val SLIDESHOW_INTERVAL_MS = 4_000L

/**
 * The focus edge, drawn inside the panel rather than around it.
 *
 * Thin and inset: this panel is mostly artwork, and a heavy frame would read as
 * part of the picture instead of as a state of the launcher.
 */
private val FOCUS_EDGE_WIDTH = 2.dp
private val FOCUS_EDGE_RADIUS = 14.dp
private val HINT_INSET = 18.dp
private const val HINT_BACKGROUND_ALPHA = 0.86f
