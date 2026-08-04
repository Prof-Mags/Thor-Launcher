package com.thor.feature.home.couch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.AppEntry
import com.thor.core.model.DisplaySettings
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * Sofa-readable details raised only when requested with Y.
 *
 * The Home screen stays open and spacious; the heavier metadata and screenshots
 * exist here instead of occupying a permanent side panel.
 *
 * ```
 * ┌────────────────────────────────────────────────────────┐
 * │ ░ backdrop, dimmed ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
 * │  SUPER NINTENDO                                        │
 * │  Logo, or the title                                    │
 * │  1994 · Platformer · ★ 96 · 4h 12m · 9 plays           │
 * │  [A PLAY] [X FAVOURITE] [Y MORE]          [B CLOSE]    │
 * ├────────────────────────────────────────────────────────┤
 * │ ┌──────┐  ABOUT                                        │
 * │ │cover │  …                                            │
 * │ │      │  DETAILS   dev · publisher · players          │
 * │ │ 62% ▓│  SCREENSHOTS  ▢ ▢ ▢                           │
 * │ └──────┘                                               │
 * └────────────────────────────────────────────────────────┘
 * ```
 *
 * The shape is the point. This was two columns of equal weight with the buttons
 * squeezed into the bottom of the right-hand one, so on a television the first
 * thing the eye found was a paragraph of scraped prose and the thing every visit
 * here is *for* — Play — was the furthest object from the centre. The backdrop
 * carries the identity, the buttons sit directly under the title where a press is
 * aimed, and the reading matter goes below the fold where reading matter belongs.
 */
@Composable
fun CouchQuickDetails(
    visible: Boolean,
    entry: GridEntry?,
    platform: Platform?,
    focusedAction: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * The couch UI scale, which this sheet was drawn without.
     *
     * Everything else in couch mode is composed through a scaled density, and
     * this is hosted by [com.thor.feature.home.BottomScreen] rather than by
     * [CouchScreen] — so it alone kept the panel's own density and came up in a
     * different size from the screen that raised it. At the top of the range that
     * is a third smaller than everything around it.
     */
    uiScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion
    val baseDensity = LocalDensity.current
    val safeUiScale = uiScale.coerceIn(
        DisplaySettings.MIN_COUCH_UI_SCALE,
        DisplaySettings.MAX_COUCH_UI_SCALE,
    )
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, safeUiScale) {
        Density(
            density = baseDensity.density * safeUiScale,
            fontScale = baseDensity.fontScale,
        )
    }

    AnimatedVisibility(
        visible = visible && entry != null,
        enter = fadeIn(motion.tweenSpec(motion.panelMillis)) +
            scaleIn(motion.tweenSpec(motion.panelMillis), initialScale = 0.96f),
        exit = fadeOut(motion.tweenSpec(motion.panelMillis)) +
            scaleOut(motion.tweenSpec(motion.panelMillis), targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        val shown = entry ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    // No ripple across the whole screen: the launcher draws its
                    // own cursor and a Material splash behind this sheet reads as
                    // the background having been pressed rather than dismissed.
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth(DETAILS_WIDTH_FRACTION)
                        .fillMaxHeight(DETAILS_HEIGHT_FRACTION)
                        .widthIn(max = DETAILS_MAX_WIDTH.dp)
                        .clickable(enabled = false) {},
                    shape = ThorTheme.shapes.panel,
                    alphaOverride = 0.96f,
                ) {
                    DetailsContent(
                        entry = shown,
                        platform = platform,
                        focusedAction = focusedAction,
                        onPlay = onPlay,
                        onToggleFavorite = onToggleFavorite,
                        onMore = onMore,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            // The header's artwork runs to the sheet's own edge,
                            // so the corner has to be cut here as well — the
                            // surface draws the shape, it does not clip to it.
                            .clip(ThorTheme.shapes.panel),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsContent(
    entry: GridEntry,
    platform: Platform?,
    focusedAction: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork
    val accent = platform?.let { Color(it.accentArgb) } ?: ThorTheme.colors.cursor
    val scroll = rememberScrollState()

    LaunchedEffect(entry.id) { scroll.scrollTo(0) }

    Column(modifier = modifier) {
        DetailsHeader(
            entry = entry,
            platform = platform,
            accent = accent,
            focusedAction = focusedAction,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            onMore = onMore,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(DETAILS_PADDING.dp),
            horizontalArrangement = Arrangement.spacedBy(DETAILS_GAP.dp),
        ) {
            DetailsCoverColumn(
                entry = entry,
                accent = accent,
                modifier = Modifier.width(COVER_WIDTH.dp).fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (game != null) {
                    val metadata = game.metadata

                    metadata.description?.takeIf(String::isNotBlank)?.let { description ->
                        DetailSectionTitle("ABOUT")
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ThorTheme.colors.onSurfaceVariant,
                            // Not clipped to a handful of lines any more: the
                            // column scrolls, and the shoulder buttons scroll it
                            // with a pointer up. Cutting a description short in a
                            // panel with room below it was throwing away the one
                            // thing this screen exists to show.
                        )
                    }

                    val facts = listOf(
                        "DEVELOPER" to metadata.developer,
                        "PUBLISHER" to metadata.publisher,
                        "PLAYERS" to metadata.players,
                        "RELEASED" to (metadata.releaseDate ?: metadata.releaseYear?.toString()),
                    )
                    if (facts.any { !it.second.isNullOrBlank() }) {
                        DetailSectionTitle("DETAILS")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            facts.forEach { (label, value) ->
                                DetailFact(label, value ?: "—", modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    artwork?.cappedScreenshots
                        ?.takeIf(List<String>::isNotEmpty)
                        ?.let { shots ->
                            DetailSectionTitle("SCREENSHOTS")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                shots.forEach { shot ->
                                    ArtworkImage(
                                        model = shot,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(16f / 9f)
                                            .clip(ThorTheme.shapes.small),
                                    )
                                }
                                // Keeps three slots wide whatever arrived, so two
                                // screenshots are two thirds of the row rather
                                // than two halves at a size nothing else uses.
                                repeat(SCREENSHOT_SLOTS - shots.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                } else {
                    Text(
                        text = when (entry) {
                            is AppEntry -> "Android application"
                            is FolderEntry -> "${entry.childIds.size} items in this collection"
                            else -> "Library item"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = ThorTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The banner: what this is, and what can be done to it.
 *
 * The backdrop is drawn faint and under a gradient rather than at full strength.
 * It is there to say which game is open from across the room — the colour and the
 * shape of it are legible at ten feet where none of the text is — and artwork
 * bright enough to compete with the title would make the title the thing that has
 * to be squinted at.
 */
@Composable
private fun DetailsHeader(
    entry: GridEntry,
    platform: Platform?,
    accent: Color,
    focusedAction: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork
    val backdrop = artwork?.backgroundImage
        ?: artwork?.cappedScreenshots?.firstOrNull()
        ?: artwork?.cellImage

    Box(modifier = modifier) {
        if (backdrop != null) {
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(BACKDROP_ALPHA),
            )
        } else {
            // No wide art scraped: the system's own colour instead of a grey
            // rectangle, which still tells the room which shelf this came from.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        ),
                    ),
            )
        }
        // Into the panel below rather than stopping at a hard line, so the header
        // reads as the top of one sheet instead of a picture stuck above it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surface.copy(alpha = 0.55f),
                            colors.surface.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DETAILS_PADDING.dp, vertical = HEADER_INSET.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = (platform?.name ?: entry.typeLabel()).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (artwork?.logo != null) {
                ArtworkImage(
                    model = artwork.logo,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth(LOGO_WIDTH_FRACTION)
                        .heightIn(min = LOGO_MIN_HEIGHT.dp, max = LOGO_MAX_HEIGHT.dp),
                )
            } else {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            /*
             * One line of facts rather than a column of them.
             *
             * Year, genre, score, time on the clock and launches used to be split
             * between a badge row on the right and two stacked facts under the
             * cover on the left, which is the same five figures read in two
             * places. Nothing here needs a label to be understood.
             */
            val facts = buildList {
                game?.metadata?.releaseYear?.let { add(it.toString()) }
                game?.metadata?.genres?.firstOrNull()?.let(::add)
                game?.stats?.totalPlayMillis
                    ?.takeIf { it > 0L }
                    ?.let { add(it.asDetailsPlaytime()) }
                game?.stats?.launchCount
                    ?.takeIf { it > 0 }
                    ?.let { add(if (it == 1) "1 play" else "$it plays") }
                entry.lastPlayedAt()?.let { add(it.asCouchRelativeTime()) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                game?.metadata?.rating?.let { rating ->
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = colors.cursor,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "$rating / 100",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailAction(
                    key = "A",
                    label = if (entry is FolderEntry) "OPEN" else "PLAY",
                    icon = if (entry is FolderEntry) {
                        Icons.Rounded.FolderOpen
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    accent = accent,
                    primary = true,
                    focused = focusedAction == ACTION_PLAY,
                    onClick = onPlay,
                )
                DetailAction(
                    key = "X",
                    label = if (entry.isFavorite) "UNFAVOURITE" else "FAVOURITE",
                    icon = if (entry.isFavorite) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    accent = accent,
                    focused = focusedAction == ACTION_FAVOURITE,
                    onClick = onToggleFavorite,
                )
                DetailAction(
                    key = "Y",
                    label = "MORE",
                    icon = Icons.Rounded.MoreHoriz,
                    accent = accent,
                    focused = focusedAction == ACTION_MORE,
                    onClick = onMore,
                )
                Spacer(modifier = Modifier.weight(1f))
                DetailAction(
                    key = "B",
                    label = "CLOSE",
                    icon = Icons.Rounded.Close,
                    accent = accent,
                    focused = focusedAction == ACTION_CLOSE,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * Cover art, and how far through this is.
 *
 * The completion bar is the one figure the shelf card already draws and this
 * panel did not, which made the detailed view the less informative of the two.
 */
@Composable
private fun DetailsCoverColumn(
    entry: GridEntry,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork
    val cover = artwork?.boxArt ?: artwork?.cellImage ?: artwork?.backgroundImage

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cover != null) {
            ArtworkImage(
                model = cover,
                contentDescription = entry.title,
                fallbackText = entry.title,
                fallbackTint = accent,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ThorTheme.shapes.small),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ThorTheme.shapes.small)
                    .background(colors.surfaceHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title.take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        val progress = game?.completionProgress()
        if (progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${(progress * 100f).toInt()}% complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(colors.outline.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(ThorTheme.shapes.pill)
                            .background(accent),
                    )
                }
            }
        }
    }
}

/** Play time over the scraped completion time, or null when either is missing. */
private fun GameEntry.completionProgress(): Float? {
    val completionMillis = metadata.completionMinutes
        ?.takeIf { it > 0 }
        ?.times(60_000L)
        ?: return null
    if (stats.totalPlayMillis <= 0L) return null
    return (stats.totalPlayMillis.toFloat() / completionMillis).coerceIn(0f, 1f)
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant.copy(alpha = 0.72f),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DetailFact(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.68f),
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One button, wearing the button it is bound to.
 *
 * The primary action takes the platform's colour rather than the theme's cursor:
 * this sheet is about one game, and it is the only surface in couch mode where
 * the accent can be that specific without moving as the shelf does.
 */
@Composable
private fun DetailAction(
    key: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    primary: Boolean = false,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    val background = if (primary) accent else colors.surfaceHighest
    val content = if (primary) contrastingContentColor(accent) else colors.onSurface
    Row(
        modifier = Modifier
            .height(ACTION_HEIGHT.dp)
            .clip(shape)
            .background(background)
            /*
             * A plain ring rather than `thorCursor`, which animates and glows.
             *
             * Four buttons a stick-flick apart want the plainest possible answer
             * to "which one", and a pulsing outline on a sheet that is already
             * lifted over a scrim reads as decoration rather than as position.
             */
            .border(
                width = if (lit) 2.dp else 1.dp,
                color = if (lit) colors.cursor else colors.outline.copy(alpha = 0.24f),
                shape = shape,
            )
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.72f),
            fontWeight = FontWeight.Black,
        )
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun Long.asDetailsPlaytime(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

/*
 * The action order, which is also the cursor's position.
 *
 * Mirrors `LauncherViewModel.COUCH_DETAIL_*`, which is what the controller moves
 * through — a button reordered here without the view model agreeing is a press
 * that lands on its neighbour.
 */
private const val ACTION_PLAY = 0
private const val ACTION_FAVOURITE = 1
private const val ACTION_MORE = 2
private const val ACTION_CLOSE = 3

/**
 * Room to breathe around the sheet.
 *
 * Wider and shorter than the panel it replaces: the reading is now in one column
 * rather than two, so the line length is set by the column and not by the sheet,
 * and the height that used to hold a stack of badges is spent on the banner.
 */
private const val DETAILS_WIDTH_FRACTION = 0.88f
private const val DETAILS_HEIGHT_FRACTION = 0.82f
private const val DETAILS_MAX_WIDTH = 1120
private const val DETAILS_PADDING = 24
private const val DETAILS_GAP = 24
private const val COVER_WIDTH = 190
private const val HEADER_HEIGHT = 250
private const val HEADER_INSET = 20
private const val ACTION_HEIGHT = 46
private const val LOGO_WIDTH_FRACTION = 0.62f
private const val LOGO_MIN_HEIGHT = 46
private const val LOGO_MAX_HEIGHT = 88

/** How far the backdrop is knocked back so the title stays the loudest thing. */
private const val BACKDROP_ALPHA = 0.5f

/** Darker than the dashboard's shade: this sheet is the whole screen's business. */
private const val SCRIM_ALPHA = 0.86f

/** Screenshots always occupy three slots; see the note at the call site. */
private const val SCREENSHOT_SLOTS = 3
