package com.thor.feature.home.couch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.completionProgress
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.home.BottomScreen
import com.thor.feature.home.LauncherViewModel
import com.thor.feature.home.shell.icon

/**
 * A request from the controller to move the reading column.
 *
 * Where the column has been scrolled to belongs to the composable that owns it;
 * what a press produces is "one more step, this way". [tick] rises with every
 * press so two presses in the same direction arrive as two events rather than as
 * one value that never changed.
 */
data class CouchDetailScroll(val tick: Int = 0, val direction: Int = 0)

/**
 * The game's own page, raised with Y.
 *
 * ```
 * ┌────────────────────────────────────────────────────────┐
 * │ ░░░ the game's artwork, filling the screen ░░░░░░░░░░░ │
 * │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
 * │  SUPER NINTENDO                                   ░░░░ │
 * │  Logo, or the title                               ░░░░ │
 * │  1994 · Platformer · 4h 12m · 9 plays · ★ 96      ░░░░ │
 * │  ▓▓▓▓▓▓▓░░░ 62% complete                          ░░░░ │
 * │  [A PLAY] [X FAVOURITE]                           ░░░░ │
 * │  ┌──────┐  ABOUT               ▲ scrolls          ░░░░ │
 * │  │cover │  DETAILS  dev · publisher · players     ░░░░ │
 * │  │      │  SCREENSHOTS                            ░░░░ │
 * │  └──────┘  ▢▢▢▢  ▢▢▢▢  ▢▢▢▢       ▲ always here   ░░░░ │
 * └────────────────────────────────────────────────────────┘
 * ```
 *
 * It takes the whole screen, and the artwork takes the whole of it. Scraped
 * backdrops are 16:9 and so is the television, so filling the panel is the one
 * arrangement that shows the picture as it was drawn — the band this used to
 * crop it into was a fixed 250dp inside a floating card, which on a wide screen
 * meant a letterbox slot cutting the top and bottom off every image.
 *
 * Everything sized in shares of the screen for the same reason. The card was
 * fixed dp inside fractions of the panel, so the couch UI scale moved the
 * contents and the frame by different amounts and the two only agreed at one
 * setting.
 *
 * Two buttons, and both act on the game. There is no Close — B leaves, as B
 * leaves everywhere in the launcher, and a press on the page itself leaves too,
 * which is what a pointer uses now that the page has no outside to click on.
 * There is no More either: it opened the long-press menu, and couch mode does
 * not raise that menu at all. This page is what a long press reaches instead.
 *
 * Driven by all three inputs. The stick walks the actions left and right and
 * scrolls the reading up and down; the pointer lights and clicks the same
 * buttons, and its scroll is a drag into this window, so the column follows the
 * wheel wherever the cursor is over it.
 */
@Composable
fun CouchQuickDetails(
    visible: Boolean,
    entry: GridEntry?,
    platform: Platform?,
    focusedAction: Int,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * The pointer arriving over one of the actions.
     *
     * It moves the cursor rather than lighting a second one. Two rings on a
     * television is not a highlight, it is a question about which one the next
     * press hits — and the answer has to be the same whether that press comes
     * from the pad or from the button under the cursor.
     */
    onActionFocused: (Int) -> Unit = {},
    /**
     * The couch UI scale, which this page was drawn without.
     *
     * Everything else in couch mode is composed through a scaled density, and
     * this is hosted by [com.thor.feature.home.BottomScreen] rather than by
     * [CouchScreen] — so it alone kept the panel's own density and came up in a
     * different size from the screen that raised it.
     */
    uiScale: Float = 1f,
    /** The controller's requests to move the reading column. */
    scroll: CouchDetailScroll = CouchDetailScroll(),
    modifier: Modifier = Modifier,
) {
    val motion = ThorTheme.motion
    // Composed inside the surface's own canvas, like the rest of couch mode; see
    // `BottomScreen`. [uiScale] is applied there and is kept as a parameter only
    // so the call sites do not have to change.

    AnimatedVisibility(
        visible = visible && entry != null,
        // A page, so it fades. Growing it from 96% was right for a card lifted
        // over a screen; done to the screen itself it reads as the television
        // zooming rather than as something arriving on it.
        enter = fadeIn(motion.tweenSpec(motion.panelMillis)),
        exit = fadeOut(motion.tweenSpec(motion.panelMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        val shown = entry ?: return@AnimatedVisibility
        DetailsPage(
            entry = shown,
            platform = platform,
            focusedAction = focusedAction,
            scroll = scroll,
            onActionFocused = onActionFocused,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DetailsPage(
    entry: GridEntry,
    platform: Platform?,
    focusedAction: Int,
    scroll: CouchDetailScroll,
    onActionFocused: (Int) -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork
    val accent = platform?.let { Color(it.accentArgb) } ?: colors.cursor
    val backdrop = artwork?.backgroundImage
        ?: artwork?.cappedScreenshots?.firstOrNull()
        ?: artwork?.cellImage
    val reading = rememberScrollState()
    val step = with(LocalDensity.current) { READING_STEP.dp.toPx() }

    LaunchedEffect(entry.id) { reading.scrollTo(0) }
    /*
     * One press, one step.
     *
     * Keyed on the whole request rather than on a position: the view model does
     * not know how long this game's description is, so it says which way and
     * leaves the clamping to the column, which does.
     */
    LaunchedEffect(scroll) {
        if (scroll.tick > 0 && scroll.direction != 0) {
            reading.animateScrollBy(scroll.direction * step)
        }
    }

    Box(
        modifier = modifier
            .background(colors.background)
            /*
             * A press on the page itself closes it, and nothing falls past it.
             *
             * Both halves matter. This is a page over the dashboard rather than
             * a panel floating on it, so a press that reached a card through the
             * artwork would launch a game. And with the Close button gone — B
             * does that, and always did — a pointer would otherwise have no way
             * out of a screen with no outside to click on. A wheel scroll is a
             * drag with movement in it, so it is not mistaken for this.
             */
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    ) {
        if (backdrop != null) {
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Nothing wide was scraped: the system's own colour rather than a
            // grey screen, which still says which shelf this came from.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.40f), colors.background),
                        ),
                    ),
            )
        }

        /*
         * Two scrims, and both are doing a job.
         *
         * Down the page, because the reading half has to be a surface while the
         * top stays a picture. Across it, because the text is against the left
         * edge and a backdrop is not a background — whatever is bright in the
         * image would otherwise decide whether the paragraph could be read.
         */
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0.30f),
                    SCRIM_KNEE to colors.background.copy(alpha = 0.86f),
                    1f to colors.background.copy(alpha = 0.98f),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to colors.background.copy(alpha = 0.88f),
                    SIDE_SCRIM_END to Color.Transparent,
                ),
            ),
        )

        Column(modifier = Modifier.fillMaxSize().padding(PAGE_INSET.dp)) {
            // The picture keeps the top of the screen to itself.
            Spacer(modifier = Modifier.weight(ART_WEIGHT))

            Text(
                text = (platform?.name ?: entry.typeLabel()).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(PLATFORM_LABEL_GAP.dp))

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
                    modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION),
                )
            }

            /*
             * One line of facts, the score at the end of it.
             *
             * The score used to be pushed to the far edge by a weighted spacer,
             * which on a card was the other side of the same sentence and on a
             * whole television is the other side of the room.
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
            if (facts.isNotEmpty() || game?.metadata?.rating != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
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
                            maxLines = 1,
                        )
                    }
                }
            }

            game?.completionProgress()?.let { progress ->
                Spacer(modifier = Modifier.height(10.dp))
                DetailsCompletion(
                    progress = progress,
                    accent = accent,
                    modifier = Modifier.fillMaxWidth(COMPLETION_WIDTH_FRACTION),
                )
            }

            Spacer(modifier = Modifier.height(ACTIONS_GAP.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ACTION_GAP.dp),
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
                    onHover = { onActionFocused(ACTION_PLAY) },
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
                    onHover = { onActionFocused(ACTION_FAVOURITE) },
                    onClick = onToggleFavorite,
                )
            }

            Spacer(modifier = Modifier.height(BODY_GAP.dp))
            DetailsBody(
                entry = entry,
                accent = accent,
                reading = reading,
                modifier = Modifier.fillMaxWidth().weight(BODY_WEIGHT),
            )
        }
    }
}

/**
 * The cover, the reading, and the screenshots that no longer hide under it.
 */
@Composable
private fun DetailsBody(
    entry: GridEntry,
    accent: Color,
    reading: ScrollState,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork

    BoxWithConstraints(modifier = modifier) {
        // A share of what this region actually got, with limits. Sized off the
        // images themselves the strip grew with the width of the column beside
        // it, and on a screen already short of height it would have taken all of
        // what was left for the words.
        val shotsHeight = (maxHeight * SHOTS_HEIGHT_FRACTION)
            .coerceIn(MIN_SHOT_HEIGHT.dp, MAX_SHOT_HEIGHT.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(BODY_COLUMN_GAP.dp),
        ) {
            val cover = artwork?.boxArt ?: artwork?.cellImage
            if (cover != null) {
                ArtworkImage(
                    model = cover,
                    contentDescription = entry.title,
                    fallbackText = entry.title,
                    fallbackTint = accent,
                    contentScale = ContentScale.Crop,
                    // Sized from the height it was given rather than from a
                    // width chosen in advance. Box art is 2:3 and this slot
                    // is whatever the screen had left, so deriving one from
                    // the other is what keeps it inside the page at every
                    // scale instead of running off the bottom of it.
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(COVER_ASPECT)
                        .clip(ThorTheme.shapes.small),
                )
            }

            /*
             * The words scroll; the pictures do not.
             *
             * Screenshots used to be the last section of one long scrolling
             * column, which put the three images this page has of the game
             * below the fold of a description that can run to any length —
             * so the artwork was the one thing you had to go looking for.
             * They are a sibling of the scrolling box now rather than its
             * last child: laid out first at the height they need, with the
             * reading taking whatever is left above them.
             */
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(reading),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (game != null) {
                        val metadata = game.metadata

                        metadata.description?.takeIf(String::isNotBlank)?.let { description ->
                            DetailSectionTitle("ABOUT")
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }

                        val details = listOf(
                            "DEVELOPER" to metadata.developer,
                            "PUBLISHER" to metadata.publisher,
                            "PLAYERS" to metadata.players,
                            "RELEASED" to (
                                metadata.releaseDate ?: metadata.releaseYear?.toString()
                                ),
                        )
                        if (details.any { !it.second.isNullOrBlank() }) {
                            DetailSectionTitle("DETAILS")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                details.forEach { (label, value) ->
                                    DetailFact(
                                        label = label,
                                        value = value ?: "—",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = when (entry) {
                                is AppEntry -> "Android application"
                                is FolderEntry ->
                                    "${entry.childIds.size} items in this collection"
                                else -> "Library item"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                artwork?.cappedScreenshots
                    ?.takeIf(List<String>::isNotEmpty)
                    ?.let { shots ->
                        Spacer(modifier = Modifier.height(SHOTS_GAP.dp))
                        DetailSectionTitle("SCREENSHOTS")
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(shotsHeight),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            shots.forEach { shot ->
                                ArtworkImage(
                                    model = shot,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(ThorTheme.shapes.small),
                                )
                            }
                            // Three slots whatever arrived, so two
                            // screenshots are two thirds of the row rather
                            // than two halves at a size nothing else on the
                            // page uses.
                            repeat(SCREENSHOT_SLOTS - shots.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
            }

            // The reading stops short of the edge and the picture carries on
            // behind it, which is the whole reason the page is the artwork.
            Spacer(modifier = Modifier.weight(GUTTER_WEIGHT))
        }
    }
}

/** How far through the game is, when a scraper supplied something to divide by. */
@Composable
private fun DetailsCompletion(progress: Float, accent: Color, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.outline.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(ThorTheme.shapes.pill)
                    .background(accent),
            )
        }
        Text(
            text = "${(progress * 100f).toInt()}% complete",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
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
 * this page is about one game, and it is the only surface in couch mode where
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
    onHover: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val hovered = hover.isHovered
    // Reported as a move of the cursor, not drawn as a second one.
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val lit = focused || hovered
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
             * to "which one", and a pulsing outline over artwork reads as
             * decoration rather than as position.
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

/**
 * Everything the page is measured in.
 *
 * Shares of the screen where the screen decides, dp where the eye does. A button
 * is 46dp tall because that is a comfortable target at arm's length from a sofa
 * and it should not change with the shape of the panel; how much of the page is
 * picture and how much is reading is exactly the thing that should.
 */
private const val PAGE_INSET = 32

/*
 * How the height left over is split between picture and reading.
 *
 * The identity in the middle takes what it needs and these two share the rest,
 * which is the right way round: a title is as tall as a title, whereas how much
 * bare artwork is worth showing depends entirely on how much screen there is.
 * Weighted towards the reading because the backdrop is behind the whole page
 * anyway — the share above is breathing room, not the only place it is seen.
 */
private const val ART_WEIGHT = 0.18f
private const val BODY_WEIGHT = 0.82f

/** The reading column stops here; the rest of the row stays artwork. */
private const val GUTTER_WEIGHT = 0.55f

private const val BODY_COLUMN_GAP = 22
private const val BODY_GAP = 20
private const val ACTIONS_GAP = 18
private const val ACTION_GAP = 9
private const val ACTION_HEIGHT = 46
private const val PLATFORM_LABEL_GAP = 6

/** Between the reading and the screenshots pinned under it. */
private const val SHOTS_GAP = 14

/**
 * How tall the strip of screenshots is.
 *
 * A share of the body with limits at both ends, and the slots crop to it rather
 * than the images setting it. Three 16:9 pictures across a wide column are tall
 * pictures — sized off themselves they grew with the width of the reading and
 * on a short screen would have left the description with nothing.
 */
private const val SHOTS_HEIGHT_FRACTION = 0.34f
private const val MIN_SHOT_HEIGHT = 62
private const val MAX_SHOT_HEIGHT = 124
private const val LOGO_WIDTH_FRACTION = 0.44f
private const val LOGO_MIN_HEIGHT = 50
private const val LOGO_MAX_HEIGHT = 92
private const val TITLE_WIDTH_FRACTION = 0.62f
private const val COMPLETION_WIDTH_FRACTION = 0.3f
private const val COVER_ASPECT = 2f / 3f

/** How far down the page the scrim has finished turning picture into surface. */
private const val SCRIM_KNEE = 0.52f

/** And how far across it before the picture is left alone. */
private const val SIDE_SCRIM_END = 0.72f

/** One press of the stick, in the reading column. */
private const val READING_STEP = 150

/** Screenshots always occupy three slots; see the note at the call site. */
private const val SCREENSHOT_SLOTS = 3
