package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlatformGlyph
import com.thor.core.ui.component.ArtworkImage
import java.util.concurrent.TimeUnit

/** Game information in the same translucent, artwork-led language as platforms. */
@Composable
fun GameDetailPanel(
    game: GameEntry,
    platform: Platform?,
    selectedScreenshot: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val accent = platform?.let { Color(it.accentArgb) }
        ?: game.metadata.artwork.dominantArgb?.let(::Color)
        ?: colors.cursor
    val density = LocalDensity.current
    val profileDensity = remember(density.density, density.fontScale) {
        Density(
            density = density.density * GAME_PROFILE_SCALE,
            fontScale = density.fontScale,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDensity provides profileDensity) {
            GameProfileCard(
                game = game,
                platform = platform,
                selectedScreenshot = selectedScreenshot,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth(GAME_PANEL_WIDTH)
                    .fillMaxHeight()
                    .padding(GAME_PANEL_OUTER_PADDING.dp),
            )
        }
    }
}

@Composable
private fun GameProfileCard(
    game: GameEntry,
    platform: Platform?,
    selectedScreenshot: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val artwork = game.metadata.artwork
    val screenshots = artwork.cappedScreenshots
    val selectedMedia = screenshots.getOrNull(
        selectedScreenshot.coerceIn(0, (screenshots.size - 1).coerceAtLeast(0)),
    ) ?: artwork.hero

    Box(
        modifier = modifier
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            /*
             * Opaque, not a tint over the artwork.
             *
             * The panel sits on a screenshot that is itself the subject, and a
             * half-transparent card over one puts detail behind text — every value
             * on it was being read against whatever happened to be underneath, which
             * changes per game and per screenshot. The gradient stays, because the
             * card still wants a top-to-bottom fall; it simply stops letting the
             * backdrop through.
             *
             * The stops sit at the top of the surface ramp. Going opaque against
             * `surface`/`background` made the card land at or below the page it is
             * meant to float over — the alpha had been borrowing light from the
             * artwork behind it, and once that was gone it just read as dark.
             */
            .background(
                Brush.verticalGradient(
                    0f to colors.surfaceHighest,
                    .58f to colors.surfaceHighest,
                    1f to colors.surfaceElevated,
                ),
            )
            .border(1.dp, platformAccentBrush(accent, alpha = .22f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(platformAccentBrush(accent, alpha = .36f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = GAME_HORIZONTAL_PADDING.dp,
                    end = GAME_HORIZONTAL_PADDING.dp,
                    top = GAME_TOP_PADDING.dp,
                    bottom = GAME_BOTTOM_RESERVE.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(GAME_SECTION_GAP.dp),
        ) {
            GameMasthead(game = game, platform = platform, accent = accent)
            GameDivider()
            GameSectionTitle("YOUR ACTIVITY")
            GameActivityStats(game = game, accent = accent)

            /*
             * The description, below the figures and above the media.
             *
             * It used to sit inside the masthead, immediately under the title,
             * where it pushed the statistics down the panel and competed with the
             * cover for the first thing read. Here it is what it actually is: the
             * paragraph you go on to once the numbers have been taken in.
             */
            val description = game.metadata.description?.takeIf(String::isNotBlank)
            if (description != null) {
                GameDivider()
                GameSectionTitle("DESCRIPTION")
                /*
                 * The description takes the panel's slack rather than a fixed
                 * three lines, which was cutting most synopses off mid-sentence
                 * while empty space sat underneath them. It still ellipsizes, but
                 * only once it has genuinely run out of panel.
                 */
                val style = MaterialTheme.typography.bodySmall
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val lineHeight = with(LocalDensity.current) {
                        style.lineHeight.takeIf { it.isSp }?.toDp()
                            ?: (style.fontSize.toDp() * DEFAULT_LINE_SPACING)
                    }
                    Text(
                        text = description,
                        style = style,
                        color = colors.onSurfaceVariant,
                        maxLines = gameDescriptionLines(maxHeight, lineHeight),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (selectedMedia != null) {
                GameSectionTitle("MEDIA")
                GameMedia(
                    model = selectedMedia,
                    selected = selectedScreenshot,
                    count = screenshots.size,
                    accent = accent,
                )
            }
        }

        GameControllerHints(
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = GAME_HORIZONTAL_PADDING.dp,
                    bottom = GAME_HINT_BOTTOM_PADDING.dp,
                ),
        )
    }
}

@Composable
private fun GameMasthead(game: GameEntry, platform: Platform?, accent: Color) {
    val colors = ThorTheme.colors
    val artwork = game.metadata.artwork
    val cover = artwork.boxArt ?: artwork.icon
    val coverAspectRatio = if (artwork.boxArt != null) 2f / 3f else 1f
    val creditLine = listOfNotNull(
        game.metadata.developer?.takeIf(String::isNotBlank),
        game.metadata.publisher?.takeIf(String::isNotBlank),
    ).distinct().joinToString("  •  ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GAME_HEADER_GAP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(GAME_COVER_WIDTH.dp)
                .aspectRatio(coverAspectRatio)
                .shadow(7.dp, ThorTheme.shapes.small, clip = false)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest.copy(alpha = .42f))
                .border(
                    1.dp,
                    platformAccentBrush(accent, alpha = .34f),
                    ThorTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                ArtworkImage(
                    model = cover,
                    contentDescription = game.title,
                    fallbackText = game.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = game.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (artwork.logo != null) {
                ArtworkImage(
                    model = artwork.logo,
                    contentDescription = game.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth(.96f)
                        .heightIn(min = 48.dp, max = 82.dp),
                )
            }
            Text(
                text = game.title,
                style = if (artwork.logo != null) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (creditLine.isNotEmpty()) {
                Text(
                    text = creditLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                platform?.shortName?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, accent)
                }
                game.metadata.releaseYear?.let {
                    GameBadge(it.toString(), colors.onSurfaceVariant)
                }
                game.metadata.genres.firstOrNull()?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, colors.primary)
                }
                game.metadata.region?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, colors.secondary)
                }
            }

            if (game.isFavorite || game.metadata.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (game.isFavorite) GameBadge("FAVOURITE", accent)
                    game.metadata.rating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformLineIcon(
                                glyph = PlatformGlyph.FAVOURITE,
                                tint = accent,
                                modifier = Modifier.size(23.dp),
                            )
                            Text(
                                text = "$rating / 100",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun GameBadge(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(tint.copy(alpha = .06f))
            .border(
                1.dp,
                platformAccentBrush(tint, alpha = .17f),
                ThorTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun GameActivityStats(game: GameEntry, accent: Color) {
    val stats = listOf(
        GameStatModel(
            glyph = PlatformGlyph.PLAYTIME,
            value = formatDuration(game.stats.totalPlayMillis),
            label = "Play time",
        ),
        GameStatModel(
            glyph = PlatformGlyph.CLOCK,
            value = game.stats.lastPlayedEpochMs?.let(::formatRelative) ?: "Never",
            label = "Last played",
        ),
        GameStatModel(
            glyph = PlatformGlyph.PLAY,
            value = game.stats.launchCount.toString(),
            label = "Times played",
        ),
        GameStatModel(
            glyph = PlatformGlyph.CLASSICS,
            value = game.stats.firstPlayedEpochMs?.let(::formatRelative) ?: "Never",
            label = "First played",
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(GAME_STAT_GAP.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GAME_STAT_GAP.dp),
            ) {
                rowStats.forEach { stat ->
                    GameStat(
                        stat = stat,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class GameStatModel(
    val glyph: PlatformGlyph,
    val value: String,
    val label: String,
)

@Composable
private fun GameStat(
    stat: GameStatModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier
            .height(GAME_STAT_CARD_HEIGHT.dp)
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = .32f))
            .border(
                1.dp,
                platformAccentBrush(accent, alpha = .15f),
                ThorTheme.shapes.small,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(GAME_STAT_ICON_SHELL.dp)
                .clip(ThorTheme.shapes.pill)
                .background(accent.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) {
            PlatformLineIcon(
                glyph = stat.glyph,
                tint = accent,
                modifier = Modifier.size(GAME_STAT_ICON_SIZE.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stat.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GameFacts(game: GameEntry, accent: Color) {
    val metadata = game.metadata
    val facts = listOf(
        GameFactModel(
            "Developer",
            metadata.developer?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.PERFORMANCE,
        ),
        GameFactModel(
            "Publisher",
            metadata.publisher?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.GAME_LIBRARY,
        ),
        GameFactModel(
            "Players",
            metadata.players?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.MULTIPLAYER,
        ),
        GameFactModel(
            "Rating",
            metadata.rating?.let { "$it / 100" } ?: "Not rated",
            PlatformGlyph.FAVOURITE,
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        facts.chunked(2).forEach { rowFacts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowFacts.forEach { fact ->
                    GameFact(
                        fact = fact,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class GameFactModel(
    val label: String,
    val value: String,
    val glyph: PlatformGlyph,
)

@Composable
private fun GameFact(fact: GameFactModel, accent: Color, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformLineIcon(
            glyph = fact.glyph,
            tint = accent,
            modifier = Modifier.size(GAME_FACT_ICON_SIZE.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fact.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = .72f),
                maxLines = 1,
            )
            Text(
                text = fact.value,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GameMedia(
    model: String,
    selected: Int,
    count: Int,
    accent: Color,
) {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = .28f))
            .border(
                1.dp,
                platformAccentBrush(accent, alpha = .16f),
                ThorTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ArtworkImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (count > 1) {
            Text(
                text = "${selected.coerceIn(0, count - 1) + 1} / $count",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Color.Black.copy(alpha = .48f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun GameControllerHints(accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameControllerHint("A", "Launch", accent)
        GameControllerHint("Y", "More Options", accent)
        GameControllerHint("B", "Back", accent)
    }
}

@Composable
private fun GameControllerHint(button: String, label: String, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(ThorTheme.shapes.pill)
                .background(ThorTheme.colors.surface.copy(alpha = .58f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = button,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ThorTheme.colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GameSectionTitle(text: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = .76f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outline.copy(alpha = .15f)),
        )
    }
}

@Composable
private fun GameDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ThorTheme.colors.outline.copy(alpha = .15f)),
    )
}

internal fun formatDuration(millis: Long): String {
    if (millis <= 0) return "Never"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours >= 1) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatRelative(epochMs: Long): String {
    val delta = System.currentTimeMillis() - epochMs
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        days > 365 -> "${days / 365}y ago"
        days > 30 -> "${days / 30}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

private const val GAME_PROFILE_SCALE = .70f
private const val GAME_PANEL_WIDTH = .395f
private const val GAME_PANEL_OUTER_PADDING = 14
private const val GAME_HORIZONTAL_PADDING = 20
private const val GAME_TOP_PADDING = 20
private const val GAME_BOTTOM_RESERVE = 46
private const val GAME_HINT_BOTTOM_PADDING = 13
private const val GAME_SECTION_GAP = 5
private const val GAME_HEADER_GAP = 16
private const val GAME_COVER_WIDTH = 128
/**
 * How many lines of synopsis fit in the space the panel has left over.
 *
 * A constant cap cannot know how tall the panel is — the masthead grows with the
 * title, the activity row is fixed, and whatever remains before the media strip
 * is the description's. Deriving the count from that leftover keeps long
 * synopses readable without letting one push the media off the bottom.
 */
internal fun gameDescriptionLines(available: Dp, lineHeight: Dp): Int =
    if (lineHeight <= 0.dp) MIN_DESCRIPTION_LINES
    else (available / lineHeight).toInt().coerceAtLeast(MIN_DESCRIPTION_LINES)

/** Never show less than this, even when the panel is squeezed. */
private const val MIN_DESCRIPTION_LINES = 2

/** Fallback line spacing when a text style leaves its line height unset. */
private const val DEFAULT_LINE_SPACING = 1.35f
private const val GAME_STAT_CARD_HEIGHT = 44
private const val GAME_STAT_GAP = 7
private const val GAME_STAT_ICON_SHELL = 28
private const val GAME_STAT_ICON_SIZE = 19
private const val GAME_FACT_ICON_SIZE = 27
