package com.thor.feature.home.couch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage

/**
 * Sofa-readable details raised only when requested with Y.
 *
 * The Home screen stays open and spacious; the heavier metadata and screenshots
 * exist here instead of occupying a permanent side panel.
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
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion

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
                .background(colors.scrim.copy(alpha = 0.86f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight(0.78f)
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
                    modifier = Modifier.fillMaxSize(),
                )
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
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val artwork = game?.metadata?.artwork
    val scroll = rememberScrollState()

    LaunchedEffect(entry.id) { scroll.scrollTo(0) }

    Row(
        modifier = modifier.padding(DETAILS_PADDING.dp),
        horizontalArrangement = Arrangement.spacedBy(DETAILS_GAP.dp),
    ) {
        Column(
            modifier = Modifier.width(COVER_WIDTH.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val cover = artwork?.boxArt ?: artwork?.backgroundImage
            if (cover != null) {
                ArtworkImage(
                    model = cover,
                    contentDescription = entry.title,
                    fallbackText = entry.title,
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

            platform?.let {
                DetailBadge(
                    text = it.name,
                    tint = Color(it.accentArgb),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            game?.stats?.totalPlayMillis?.takeIf { it > 0L }?.let { millis ->
                DetailFact("PLAY TIME", millis.asDetailsPlaytime())
            }
            game?.stats?.launchCount?.takeIf { it > 0 }?.let { count ->
                DetailFact("TIMES PLAYED", count.toString())
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (artwork?.logo != null) {
                    ArtworkImage(
                        model = artwork.logo,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .heightIn(min = 48.dp, max = 92.dp),
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

                if (game != null) {
                    val metadata = game.metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        metadata.releaseYear?.let {
                            DetailBadge(it.toString(), colors.secondary)
                        }
                        metadata.genres.take(2).forEach {
                            DetailBadge(it, colors.primary)
                        }
                        metadata.rating?.let { rating ->
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
                            )
                        }
                    }

                    metadata.description?.takeIf(String::isNotBlank)?.let { description ->
                        DetailSectionTitle("ABOUT")
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
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
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DetailAction(
                    key = "A",
                    label = if (entry is FolderEntry) "OPEN" else "PLAY",
                    icon = if (entry is FolderEntry) Icons.Rounded.FolderOpen else Icons.Rounded.PlayArrow,
                    primary = true,
                    focused = focusedAction == 0,
                    onClick = onPlay,
                )
                DetailAction(
                    key = "X",
                    label = if (entry.isFavorite) "UNFAVOURITE" else "FAVOURITE",
                    icon = if (entry.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    focused = focusedAction == 1,
                    onClick = onToggleFavorite,
                )
                DetailAction(
                    key = "Y",
                    label = "MORE",
                    icon = Icons.Rounded.MoreHoriz,
                    focused = focusedAction == 2,
                    onClick = onMore,
                )
                Spacer(modifier = Modifier.weight(1f))
                DetailAction(
                    key = "B",
                    label = "CLOSE",
                    icon = Icons.Rounded.Close,
                    focused = focusedAction == 3,
                    onClick = onDismiss,
                )
            }
        }
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
private fun DetailBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
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

@Composable
private fun DetailAction(
    key: String,
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val background = if (primary) colors.cursor else colors.surfaceHighest
    val content = if (primary) contrastingContentColor(colors.cursor) else colors.onSurface
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(background)
            .thorCursor(focused = focused, shape = ThorTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.72f),
            fontWeight = FontWeight.Black,
        )
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Long.asDetailsPlaytime(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private const val DETAILS_MAX_WIDTH = 980
private const val DETAILS_PADDING = 22
private const val DETAILS_GAP = 22
private const val COVER_WIDTH = 190
