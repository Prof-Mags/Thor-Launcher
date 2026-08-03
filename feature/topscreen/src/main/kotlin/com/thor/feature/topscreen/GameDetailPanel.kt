package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import java.util.concurrent.TimeUnit

/** Normal-mode game information presented as a media-first dossier. */
@Composable
fun GameDetailPanel(
    game: GameEntry,
    platform: Platform?,
    selectedScreenshot: Int,
    onScreenshotSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val accent = platform?.let { Color(it.accentArgb) } ?: colors.cursor
    val artwork = game.metadata.artwork

    Row(modifier = modifier.fillMaxSize()) {
        DossierCard(
            accent = accent,
            modifier = Modifier
                .weight(DOSSIER_PANEL_WEIGHT)
                .fillMaxHeight()
                .padding(dimens.spacing),
            masthead = {
                GameMasthead(game = game, platform = platform, accent = accent)
            },
            body = {
                DossierSection("YOUR ACTIVITY") {
                    DossierStats {
                        DossierStat(
                            label = "Play time",
                            value = formatDuration(game.stats.totalPlayMillis),
                            accent = accent,
                        )
                        DossierStat(
                            label = "Last played",
                            value = game.stats.lastPlayedEpochMs?.let(::formatRelative) ?: "Never",
                            accent = accent,
                        )
                        DossierStat(
                            label = "Times played",
                            value = game.stats.launchCount.takeIf { it > 0 }?.toString() ?: "Never",
                            accent = accent,
                        )
                        DossierStat(
                            label = "First played",
                            value = game.stats.firstPlayedEpochMs?.let(::formatRelative) ?: "Never",
                            accent = accent,
                        )
                    }
                }

                game.metadata.description?.takeIf(String::isNotBlank)?.let { description ->
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

                artwork.cappedScreenshots.takeIf(List<String>::isNotEmpty)?.let { screenshots ->
                    DossierSection("MEDIA") {
                        ScreenshotGallery(
                            urls = screenshots,
                            selected = selectedScreenshot,
                            accent = accent,
                            onSelected = onScreenshotSelected,
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.weight(1f - DOSSIER_PANEL_WEIGHT))
    }
}

@Composable
private fun GameMasthead(game: GameEntry, platform: Platform?, accent: Color) {
    val colors = ThorTheme.colors
    val artwork = game.metadata.artwork

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = artwork.boxArt ?: artwork.icon
        Box(
            modifier = Modifier
                .width(MASTHEAD_COVER_WIDTH.dp)
                .aspectRatio(2f / 3f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                ArtworkImage(
                    model = cover,
                    contentDescription = game.title,
                    fallbackText = game.title,
                    contentScale = ContentScale.Crop,
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
                        .fillMaxWidth(0.84f)
                        .heightIn(min = 36.dp, max = 62.dp),
                )
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                platform?.let { DossierBadge(it.shortName, accent) }
                game.metadata.releaseYear?.let {
                    DossierBadge(it.toString(), colors.secondary)
                }
                game.metadata.genres.firstOrNull()?.let {
                    DossierBadge(it, colors.primary)
                }
                if (game.isFavorite) DossierBadge("FAVOURITE", colors.cursor)

                game.metadata.rating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Rating",
                            tint = accent,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "$rating",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotGallery(
    urls: List<String>,
    selected: Int,
    accent: Color,
    onSelected: (Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val safeSelected = selected.coerceIn(0, urls.lastIndex)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val previewHeight = (maxWidth * 9f / 16f).coerceIn(108.dp, 196.dp)
        val thumbnailHeight = (previewHeight * 0.30f).coerceIn(38.dp, 54.dp)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest),
        ) {
            ArtworkImage(
                model = urls[safeSelected],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp),
            )
            Text(
                text = "${safeSelected + 1} / ${urls.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (urls.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                urls.forEachIndexed { index, url ->
                    val isSelected = index == safeSelected
                    ArtworkImage(
                        model = url,
                        contentDescription = "Screenshot ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .height(thumbnailHeight)
                            .clip(ThorTheme.shapes.small)
                            .background(colors.surfaceHighest)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accent else colors.outline.copy(alpha = 0.35f),
                                shape = ThorTheme.shapes.small,
                            )
                            .clickable { onSelected(index) },
                    )
                }
            }
        }
        }
    }
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

private const val MASTHEAD_COVER_WIDTH = 60
