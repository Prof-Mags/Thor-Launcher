package com.thor.feature.home.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.LauncherWidget
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the launcher's own widgets draw from.
 *
 * One object rather than a parameter each, and resolved once per library change
 * by whoever is composing the grid: four of the five widgets are a different
 * question asked of the same list of games, and each of them recomputing it
 * would put four passes over the library on every recomposition of a page.
 */
@Immutable
data class LauncherWidgetData(
    val recent: List<GameEntry> = emptyList(),
    val favourites: List<GameEntry> = emptyList(),
    /** Never started, for the backlog strip. */
    val unplayed: List<GameEntry> = emptyList(),
    /** Where the hours have actually gone. */
    val mostPlayed: List<GameEntry> = emptyList(),
    /** One game to suggest, held steady for the day; see how it is chosen. */
    val surprise: GameEntry? = null,
    val gameCount: Int = 0,
    val totalPlayMillis: Long = 0L,
    val platformsById: Map<String, Platform> = emptyMap(),
)

/**
 * A widget the launcher draws itself.
 *
 * The counterpart to the `AndroidView` an app widget gets: same box, same cursor
 * ring, same resize behaviour, and content this process can actually render —
 * which is the point of them existing. See [LauncherWidget].
 *
 * Sized against its own box rather than against the cell count. A widget can be
 * resized to anything from one cell to sixteen, and the number of tiles a row
 * can show, the size of the artwork and whether there is room for a heading are
 * all consequences of the box, not of a span the user is free to change.
 */
@Composable
fun LauncherWidgetCard(
    widget: LauncherWidget,
    data: LauncherWidgetData,
    onLaunch: (GridEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(CARD_INSET.dp)) {
        when (widget) {
            LauncherWidget.CONTINUE_PLAYING -> GameStrip(
                heading = "Continue playing",
                games = data.recent,
                data = data,
                empty = "Nothing played yet",
                onLaunch = onLaunch,
            )

            LauncherWidget.FAVOURITES -> GameStrip(
                heading = "Favourites",
                games = data.favourites,
                data = data,
                empty = "Star a game to see it here",
                onLaunch = onLaunch,
            )

            LauncherWidget.SPOTLIGHT -> Spotlight(
                game = data.recent.firstOrNull(),
                data = data,
                onLaunch = onLaunch,
            )

            LauncherWidget.BACKLOG -> GameStrip(
                heading = "Backlog",
                games = data.unplayed,
                data = data,
                empty = "You have started everything",
                onLaunch = onLaunch,
            )

            LauncherWidget.MOST_PLAYED -> GameStrip(
                heading = "Most played",
                games = data.mostPlayed,
                data = data,
                empty = "No play time recorded yet",
                onLaunch = onLaunch,
            )

            // The same treatment Spotlight gets, because it is the same shape of
            // answer — one game, given room to be a picture rather than an icon.
            LauncherWidget.SURPRISE -> Spotlight(
                game = data.surprise,
                data = data,
                onLaunch = onLaunch,
            )

            LauncherWidget.LIBRARY -> LibraryFacts(data)
            LauncherWidget.CLOCK -> ClockFace()
        }
    }
}

/**
 * A heading and a row of game tiles.
 *
 * How many tiles fit is measured, not assumed: the same widget is a strip of two
 * at three cells and a strip of five stretched across a page, and a fixed count
 * would either waste the width or squeeze the artwork to a smear.
 */
@Composable
private fun GameStrip(
    heading: String,
    games: List<GameEntry>,
    data: LauncherWidgetData,
    empty: String,
    onLaunch: (GridEntry) -> Unit,
) {
    val colors = ThorTheme.colors

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The heading is the first thing to go. Below about this height it is
        // taking the room the artwork needs to be recognisable, and a strip of
        // covers explains itself better than a word above it would.
        val showHeading = maxHeight >= HEADING_MIN_HEIGHT.dp
        val tileHeight = if (showHeading) maxHeight - HEADING_BLOCK.dp else maxHeight
        val fit = ((maxWidth + TILE_GAP.dp) / (tileHeight * TILE_ASPECT + TILE_GAP.dp))
            .toInt()
            .coerceIn(1, MAX_TILES)

        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeading) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = HEADING_GAP.dp),
                )
            }

            if (games.isEmpty()) {
                EmptyNote(empty)
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
            ) {
                games.take(fit).forEach { game ->
                    GameTile(
                        game = game,
                        accent = data.accentFor(game),
                        onLaunch = onLaunch,
                        modifier = Modifier.fillMaxHeight().weight(1f),
                    )
                }
                // Holds the row's shape when there are fewer games than fit, so
                // three covers in a five-wide widget stay cover-sized instead of
                // stretching to fill it.
                repeat((fit - games.size).coerceAtLeast(0)) {
                    Box(modifier = Modifier.fillMaxHeight().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameEntry,
    accent: Color,
    onLaunch: (GridEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ThorTheme.shapes.small
    Box(
        modifier = modifier
            .clip(shape)
            .background(ThorTheme.colors.surfaceHighest)
            .clickable { onLaunch(game) },
        contentAlignment = Alignment.Center,
    ) {
        ArtworkImage(
            model = game.metadata.artwork.cellImage,
            contentDescription = game.title,
            fallbackText = game.title,
            fallbackTint = accent,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The last game played, as a picture.
 *
 * Cropped rather than fitted, unlike a grid cell: this is the one place a game's
 * artwork is given a box big enough to be a backdrop, and letterboxing it inside
 * that box would waste exactly the room that makes it worth having.
 */
@Composable
private fun Spotlight(
    game: GameEntry?,
    data: LauncherWidgetData,
    onLaunch: (GridEntry) -> Unit,
) {
    val colors = ThorTheme.colors
    if (game == null) {
        EmptyNote("Nothing played yet")
        return
    }

    val shape = ThorTheme.shapes.small
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .clickable { onLaunch(game) },
    ) {
        ArtworkImage(
            model = game.metadata.artwork.backgroundImage ?: game.metadata.artwork.cellImage,
            contentDescription = game.title,
            fallbackText = game.title,
            fallbackTint = data.accentFor(game),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // The caption sits on the artwork, so it carries its own darkness rather
        // than trusting whatever image happens to be underneath it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = SPOTLIGHT_SCRIM),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    data.platformsById[game.platformId]?.shortName,
                    game.stats.totalPlayMillis.takeIf { it > 0 }?.let(::formatPlayed),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How much is here, and how much of it has been played. */
@Composable
private fun LibraryFacts(data: LauncherWidgetData) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FACT_GAP.dp),
    ) {
        Fact(
            icon = Icons.Rounded.SportsEsports,
            value = data.gameCount.toString(),
            label = if (data.gameCount == 1) "game" else "games",
            tint = colors.cursor,
            modifier = Modifier.weight(1f),
        )
        Fact(
            icon = Icons.Rounded.Star,
            value = formatPlayed(data.totalPlayMillis),
            label = "played",
            tint = colors.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Fact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(FACT_GLYPH.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The time, ticked to the minute.
 *
 * Waking on the minute boundary rather than every second: nothing here shows
 * seconds, so a per-second recomposition would be a frame's work sixty times an
 * hour for a value that has not changed.
 */
@Composable
private fun ClockFace() {
    val colors = ThorTheme.colors
    val now by produceState(initialValue = Date()) {
        while (true) {
            delay(MINUTE_MS - (System.currentTimeMillis() % MINUTE_MS))
            value = Date()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = format(now, "HH:mm"),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            maxLines = 1,
        )
        Text(
            text = format(now, "EEE d MMM"),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyNote(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = ThorTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A game's system colour, or the theme's when it has none. */
@Composable
private fun LauncherWidgetData.accentFor(game: GameEntry): Color =
    platformsById[game.platformId]?.accentArgb?.let(::Color) ?: ThorTheme.colors.primary

private fun format(date: Date, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(date)

/**
 * Play time, at the coarsest honest resolution.
 *
 * Minutes below an hour and whole hours above it. "2h 47m" is more precise than
 * anybody reads off a tile the size of a stamp, and the extra characters are
 * what push the line into an ellipsis.
 */
private fun formatPlayed(millis: Long): String {
    val minutes = millis / 60_000
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h"
}

private const val MINUTE_MS = 60_000L

/** Breathing room between the widget's content and the plate behind it. */
private const val CARD_INSET = 6

private const val TILE_GAP = 4

/** Roughly box-art proportions, which is what decides how many tiles fit. */
private const val TILE_ASPECT = 0.78f
private const val MAX_TILES = 6

private const val HEADING_GAP = 3
private const val HEADING_BLOCK = 17
private const val HEADING_MIN_HEIGHT = 62

private const val FACT_GAP = 8
private const val FACT_GLYPH = 15

private const val SPOTLIGHT_SCRIM = 0.72f
