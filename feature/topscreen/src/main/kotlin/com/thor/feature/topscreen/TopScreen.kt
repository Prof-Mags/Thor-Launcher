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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.DesignScale
import com.thor.core.designsystem.theme.PANEL_SHORT_SIDE
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
import com.thor.core.ui.profile.ProfileNotificationCluster
import com.thor.core.ui.profile.ShellStatus
import com.thor.core.ui.profile.ShellStatusActions
import com.thor.feature.topscreen.component.GameVideoBackground
import com.thor.feature.topscreen.panel.AppDetailPanel
import com.thor.feature.topscreen.panel.FolderDetailPanel
import com.thor.feature.topscreen.panel.GameDetailPanel
import com.thor.feature.topscreen.panel.IdleWallpaperPanel
import com.thor.feature.topscreen.panel.platformAccentBrush
import com.thor.feature.topscreen.panel.PlatformDetailPanel
import com.thor.feature.topscreen.panel.representativeImageFor
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
    /** Whether the user has enabled preview clips. */
    videoPreviewsEnabled: Boolean,
    /**
     * Which screenshot fills the backdrop, owned by the view model rather than
     * remembered locally — LB/RB cycle it from the controller, so the panel
     * needs to react to the same value the input layer changes.
     */
    selectedScreenshot: Int,
    onScreenshotSelected: (Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit = {},
    /**
     * Whether the controller is currently aimed at this panel.
     *
     * Drawn, not just tracked. This panel is a picture of the selection and has no
     * cursor of its own, so touching it to take the controller changed nothing
     * visible — the same panel, quietly reacting to different buttons. The edge and
     * its hints are the only way to tell that a press will land here.
     */
    focused: Boolean = false,
    /**
     * Who is signed in and what the system is saying, or null to draw neither.
     *
     * Null on the couch and single-screen layouts, which have their own chrome
     * and no room in the corner this occupies.
     */
    status: ShellStatus? = null,
    statusActions: ShellStatusActions = ShellStatusActions(),
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
    // A scraper can replace a stale trailer URL while this game remains selected.
    // Retry that new source instead of keeping the earlier failure latched until
    // the user leaves and re-enters the game.
    var videoFailed by remember(game?.id, game?.metadata?.artwork?.videoUri) {
        mutableStateOf(false)
    }

    val videoUri = game?.metadata?.artwork?.videoUri
        ?.takeIf { videoPreviewsEnabled && !videoFailed }

    /*
     * The panel's own design canvas.
     *
     * The same correction the grid panel gets, for the same reason: this side
     * mixes fixed sizes — the card's width, its padding, the height of a game
     * row — with fractions of the panel, and the two only agree at one screen
     * size unless the density is chosen to make them. Wrapped here rather than
     * per panel so the backdrop, the cards and the status bar are all measured
     * against one canvas.
     */
    DesignScale(referenceShortSide = PANEL_SHORT_SIDE, modifier = modifier) {
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
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
                    platform = platform,
                    folderChildren = folderChildren,
                    wallpaperUri = wallpaperUri,
                    screenshotIndex = selectedScreenshot,
                )
            }

            // A fetched trailer is always placed above its still backdrop. The still
            // remains beneath the TextureView until its first frame reaches the panel.
            if (videoUri != null) {
                GameVideoBackground(
                    videoUri = videoUri,
                    playing = true,
                    onFailure = { videoFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Scrim(
                artworkLed = selection is GameEntry ||
                    (selection is FolderEntry && platform != null),
            )

            AnimatedContent(
                // Keyed on the selection, so moving between entries crossfades.
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
                    )

                    // A platform's folder is a system, not a folder, and gets a panel
                    // that says so — name, maker, year and a paragraph about it.
                    is FolderEntry -> if (platform != null) {
                        PlatformDetailPanel(
                            platform = platform,
                            folderTitle = entry.title,
                            children = folderChildren,
                            onGameSelected = onEntrySelected,
                        )
                    } else {
                        FolderDetailPanel(folder = entry, children = folderChildren)
                    }
                    is AppEntry -> AppDetailPanel(app = entry)
                    else -> IdleWallpaperPanel(wallpaper = wallpaper, wallpaperUri = wallpaperUri)
                }
            }

            // The clock lives on this panel only, centred. It used to sit above the
            // grid as well, which put two clocks on screen at once.
            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                LauncherStatusBar(clockStyle = clockStyle, visible = showStatusBar)
            }

            // Drawn last of the corner chrome so the opened shade lies over the
            // panel rather than being clipped behind it.
            if (status != null) {
                ProfileNotificationCluster(
                    profile = status.profile,
                    avatarPath = status.avatarPath,
                    access = status.notifications,
                    expanded = status.shadeOpen,
                    onToggleExpanded = statusActions.onToggleShade,
                    onGrantAccess = statusActions.onGrantAccess,
                    onOpenAppInfo = statusActions.onOpenAppInfo,
                    onNotificationOpened = statusActions.onNotificationOpened,
                    onNotificationDismissed = statusActions.onNotificationDismissed,
                    onDismissAll = statusActions.onDismissAll,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(STATUS_CLUSTER_INSET.dp),
                )
            }

            ControllerFocusEdge(
                visible = focused,
                canCycleScreenshots = screenshots.size > 1,
                canLaunch = selection != null,
                platformAccent = when (selection) {
                    is FolderEntry -> platform?.let { Color(it.accentArgb) }
                    is GameEntry -> platform?.let { Color(it.accentArgb) }
                        ?: selection.metadata.artwork.dominantArgb?.let(::Color)
                        ?: colors.cursor
                    else -> null
                },
                modifier = Modifier.fillMaxSize(),
            )
    }
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
    platformAccent: Color?,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion
    val focusBrush = platformAccent?.let { platformAccentBrush(it, alpha = .54f) }
        ?: SolidColor(colors.cursor)

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
                    brush = focusBrush,
                    shape = RoundedCornerShape(FOCUS_EDGE_RADIUS),
                ),
        ) {
            if (platformAccent == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = HINT_INSET)
                        .clip(ThorTheme.shapes.pill)
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
    platform: Platform?,
    folderChildren: List<GridEntry>,
    wallpaperUri: String?,
    screenshotIndex: Int,
) {
    /*
     * Ordered by how specific it is to what is selected — with one exception.
     *
     * For a game, the platform's hero sits between the entry's own artwork and the
     * wallpaper, and that position is the useful one: most of a fresh library has
     * never been scraped, so an unscraped SNES game still looks like a SNES game,
     * with no network and no scrape.
     *
     * For a **platform folder** the hero comes *first*, ahead of the folder's own
     * artwork, and that is deliberate rather than an inconsistency. A platform
     * folder's artwork is the pack's square icon — the same image the detail panel
     * draws beside the title — so preferring it here filled the backdrop with a
     * stretched copy of the icon already on screen. The hero is the wide image the
     * pack ships *for* this job; the icon is not, and there is no sense in which a
     * blown-up icon is a better backdrop than the artwork made to be one.
     */
    val image = when (selection) {
        is GameEntry -> {
            val artwork = selection.metadata.artwork
            artwork.cappedScreenshots.getOrNull(screenshotIndex)
                ?: artwork.backgroundImage
                ?: platform?.artwork?.heroUri
        }

        /*
         * A platform folder with no pack installed falls back to one of that
         * system's landmark games, if the user owns one.
         *
         * Only to a landmark, and never to just any game in the folder — see
         * `representativeImageFor`. When none is owned this lands on the wallpaper,
         * which says nothing about the system but at least does not claim to.
         */
        is FolderEntry -> platform?.artwork?.heroUri
            ?: selection.artworkUri
            ?: platform?.let { representativeImageFor(it.id, folderChildren) }

        else -> platform?.artwork?.heroUri
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
private fun Scrim(artworkLed: Boolean) {
    val colors = ThorTheme.colors
    val leadingAlpha = if (artworkLed) .38f else .70f
    val middleAlpha = if (artworkLed) .16f else .35f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to colors.background.copy(alpha = leadingAlpha),
                        0.45f to colors.background.copy(alpha = middleAlpha),
                        1.0f to Color.Transparent,
                    ),
                ),
            ),
    )
}

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

/** Keeps the profile cluster clear of the screen edge and the clock. */
private const val STATUS_CLUSTER_INSET = 10
