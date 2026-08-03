package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFlagships
import com.thor.core.ui.component.ArtworkImage

/** Platform folders use the same media-dossier hierarchy as individual games. */
@Composable
fun PlatformDetailPanel(
    platform: Platform,
    folderTitle: String,
    children: List<GridEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val accent = Color(platform.accentArgb)
    val games = remember(children) { children.filterIsInstance<GameEntry>() }
    val played = games.count { it.stats.hasBeenPlayed }
    val favourites = games.count { it.isFavorite }
    val totalMillis = games.sumOf { it.stats.totalPlayMillis }
    val continuePlaying = games
        .filter { it.stats.hasBeenPlayed }
        .sortedWith(
            compareByDescending<GameEntry> { it.stats.lastPlayedEpochMs ?: 0L }
                .thenByDescending { it.stats.totalPlayMillis },
        )
        .take(PLATFORM_GAME_CARD_COUNT)
    val highlights = games.sortedWith(
        compareBy<GameEntry> {
            PlatformFlagships.rankOf(platform.id, it.title) ?: FLAGSHIP_MISS
        }
            .thenByDescending { it.isFavorite }
            .thenBy { it.sortTitle },
    ).take(PLATFORM_GAME_CARD_COUNT)

    Row(modifier = modifier.fillMaxSize()) {
        DossierCard(
            accent = accent,
            modifier = Modifier
                .weight(DOSSIER_PANEL_WEIGHT)
                .fillMaxHeight()
                .padding(dimens.spacing),
            masthead = {
                PlatformMasthead(
                    platform = platform,
                    fallbackTitle = folderTitle,
                    accent = accent,
                )
            },
            body = {
                DossierSection("THIS LIBRARY") {
                    DossierStats {
                        DossierStat("Games", games.size.toString(), accent)
                        DossierStat("Played", played.takeIf { it > 0 }?.toString() ?: "None", accent)
                        DossierStat(
                            "Favourites",
                            favourites.takeIf { it > 0 }?.toString() ?: "None",
                            accent,
                        )
                        DossierStat(
                            "Play time",
                            formatDuration(totalMillis),
                            accent,
                        )
                    }
                }

                platform.description.takeIf(String::isNotBlank)?.let { description ->
                    DossierSection("ABOUT") {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (continuePlaying.isNotEmpty()) {
                    DossierSection("CONTINUE PLAYING") {
                        PlatformGameCards(
                            games = continuePlaying,
                            accent = accent,
                            showPlayActivity = true,
                        )
                    }
                } else {
                    highlights.takeIf(List<GameEntry>::isNotEmpty)?.let { featured ->
                        DossierSection("PLATFORM HIGHLIGHTS") {
                            PlatformGameCards(
                                games = featured,
                                accent = accent,
                                showPlayActivity = false,
                            )
                        }
                    }
                }
            },
        )

        Spacer(modifier = Modifier.weight(1f - DOSSIER_PANEL_WEIGHT))
    }
}

@Composable
private fun PlatformMasthead(platform: Platform, fallbackTitle: String, accent: Color) {
    val colors = ThorTheme.colors
    val icon = platform.artwork.iconUri
    val logo = platform.artwork.logoUri

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(PLATFORM_ICON_SIZE.dp)
                .aspectRatio(1f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                ArtworkImage(
                    model = icon,
                    contentDescription = platform.name,
                    fallbackText = platform.shortName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = platform.shortName,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (logo != null) {
                ArtworkImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(LOGO_HEIGHT.dp)
                        .widthIn(max = LOGO_MAX_WIDTH.dp),
                )
            }
            Text(
                text = platform.name.ifBlank { fallbackTitle },
                style = if (logo == null) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                platform.manufacturer.takeIf(String::isNotBlank)?.let {
                    DossierBadge(it, accent)
                }
                platform.releaseYear?.let {
                    DossierBadge(it.toString(), colors.secondary)
                }
                platform.shortName.takeIf(String::isNotBlank)?.let {
                    DossierBadge(it, colors.primary)
                }
            }
        }
    }
}

@Composable
private fun PlatformGameCards(
    games: List<GameEntry>,
    accent: Color,
    showPlayActivity: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        games.forEach { game ->
            PlatformGameCard(
                game = game,
                accent = accent,
                showPlayActivity = showPlayActivity,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlatformGameCard(
    game: GameEntry,
    accent: Color,
    showPlayActivity: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val cover = game.metadata.artwork.boxArt ?: game.metadata.artwork.cellImage
    val supportingText = if (showPlayActivity) {
        listOfNotNull(
            game.stats.lastPlayedEpochMs?.let(::formatRelative),
            game.stats.totalPlayMillis.takeIf { it > 0L }?.let(::formatDuration),
        ).joinToString("  \u00b7  ")
    } else {
        listOfNotNull(
            game.metadata.releaseYear?.toString(),
            game.metadata.developer?.takeIf(String::isNotBlank),
            game.metadata.genres.firstOrNull()?.takeIf(String::isNotBlank),
        ).joinToString("  \u00b7  ")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.70f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            model = cover,
            contentDescription = game.title,
            fallbackText = game.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(PLATFORM_GAME_COVER_WIDTH.dp)
                .aspectRatio(2f / 3f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surface),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText.isNotEmpty()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showPlayActivity) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .fillMaxWidth(0.35f)
                        .height(2.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(accent),
                )
            }
        }
    }
}

/** Chooses stable platform backdrop art from flagship and play-history ranking. */
fun representativeImageFor(platformId: String, children: List<GridEntry>): String? {
    val games = children.filterIsInstance<GameEntry>()
    if (games.isEmpty()) return null

    val ranked = games.sortedWith(
        compareBy<GameEntry> { PlatformFlagships.rankOf(platformId, it.title) ?: FLAGSHIP_MISS }
            .thenByDescending { it.stats.launchCount }
            .thenByDescending { it.stats.totalPlayMillis }
            .thenBy { it.sortTitle },
    )
    return ranked.firstNotNullOfOrNull { game ->
        val artwork = game.metadata.artwork
        artwork.backgroundImage ?: artwork.cappedScreenshots.firstOrNull()
    }
}

private const val FLAGSHIP_MISS = Int.MAX_VALUE
private const val PLATFORM_ICON_SIZE = 60
private const val LOGO_HEIGHT = 30
private const val LOGO_MAX_WIDTH = 240
private const val PLATFORM_GAME_CARD_COUNT = 2
private const val PLATFORM_GAME_COVER_WIDTH = 34
