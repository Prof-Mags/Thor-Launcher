package com.thor.feature.movies.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CacheStatus
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.movies.MoviesSectionState
import com.thor.feature.movies.Playback
import java.util.concurrent.TimeUnit

/** The playback buttons, split into transport and tools rows by [MoviesSectionState]. */
enum class PlayerAction {
    REWIND,
    PLAY_PAUSE,
    FORWARD,
    NEXT_EPISODE,
    STOP,
    RESTART,
    SPEED,
    AUDIO,
    CHANGE_SOURCE,
}

/**
 * The companion-screen playback deck.
 *
 * The video remains clean on the other display. This screen carries the title,
 * timeline, transport and stream health permanently, arranged over the title's
 * artwork so playback feels like a deliberate destination instead of a debug
 * panel attached to a video texture.
 */
@Composable
fun PlayerControls(
    playback: Playback,
    status: PlayerStatus,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    skipSeconds: Int,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        playback.item.backdropUrl?.let { backdrop ->
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to colors.background.copy(alpha = 0.96f),
                    0.58f to colors.background.copy(alpha = 0.82f),
                    1f to colors.background.copy(alpha = 0.68f),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        colors.background.copy(alpha = 0.28f),
                        colors.background.copy(alpha = 0.76f),
                    ),
                ),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NowPlayingHeader(playback = playback, status = status)
            TimelinePanel(status = status, onSeek = onSeek)
            TransportDeck(
                status = status,
                focusedAction = focusedAction,
                hasNextEpisode = hasNextEpisode,
                skipSeconds = skipSeconds,
                onAction = onAction,
            )
            PlaybackTools(
                status = status,
                focusedAction = focusedAction,
                onAction = onAction,
            )
            StreamFacts(playback = playback, status = status)
        }
    }
}

/**
 * Couch Mode's controls live over the full-width video instead of permanently
 * taking a second panel. The gradient preserves the picture above the deck and
 * keeps every controller and touch action available at the bottom of the player.
 */
@Composable
fun CouchPlayerControlsOverlay(
    playback: Playback,
    status: PlayerStatus,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    skipSeconds: Int,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.78f),
                        Color.Black.copy(alpha = 0.96f),
                    ),
                ),
            )
            .padding(start = 28.dp, top = 72.dp, end = 28.dp, bottom = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NowPlayingHeader(playback = playback, status = status)
            TimelinePanel(status = status, onSeek = onSeek)
            TransportDeck(
                status = status,
                focusedAction = focusedAction,
                hasNextEpisode = hasNextEpisode,
                skipSeconds = skipSeconds,
                onAction = onAction,
            )
            PlaybackTools(
                status = status,
                focusedAction = focusedAction,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun NowPlayingHeader(playback: Playback, status: PlayerStatus) {
    val colors = ThorTheme.colors
    val item = playback.item
    val episode = if (playback.seasonNumber != null && playback.episodeNumber != null) {
        item.episode(playback.seasonNumber, playback.episodeNumber)
    } else {
        null
    }
    val context = listOfNotNull(
        playback.seasonNumber?.let { season ->
            playback.episodeNumber?.let { episodeNumber ->
                "S%02dE%02d".format(season, episodeNumber)
            }
        },
        episode?.title,
        item.releaseYear?.toString(),
        item.contentRating,
        item.genres.take(2).joinToString(" / ").takeIf(String::isNotBlank),
    ).joinToString("  ·  ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(ThorTheme.shapes.pill)
                .background(Brush.verticalGradient(colors.accentStops)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (context.isNotBlank()) {
                Text(
                    text = context,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlaybackStateBadge(status)
    }
}

@Composable
private fun PlaybackStateBadge(status: PlayerStatus) {
    val colors = ThorTheme.colors
    val alert = status.error != null || status.audioUnsupported
    val tint = when {
        alert -> colors.error
        status.buffering || status.suppressed -> colors.primary
        else -> colors.cursor
    }

    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.46f), ThorTheme.shapes.pill)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(ThorTheme.shapes.pill)
                .background(tint),
        )
        Text(
            text = status.stateLabel().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Progress, buffering and clocks live together as one readable instrument. */
@Composable
private fun TimelinePanel(status: PlayerStatus, onSeek: (Long) -> Unit) {
    val colors = ThorTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ThorTheme.shapes.panel,
        color = colors.surface,
        alphaOverride = 0.90f,
        level = SurfaceLevel.RAISED,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status.positionMs.asClock(),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "-" + (status.durationMs - status.positionMs)
                        .coerceAtLeast(0L)
                        .asClock(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Timeline(status = status, onSeek = onSeek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (status.buffering) "BUFFERING" else "PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.buffering) colors.cursor else colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status.durationMs.asClock(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tappable and draggable scrubber with buffered range and a visible playhead. */
@Composable
private fun Timeline(status: PlayerStatus, onSeek: (Long) -> Unit) {
    val colors = ThorTheme.colors
    val duration = status.durationMs.coerceAtLeast(1L)
    val playedFraction = (status.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (status.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val shape = ThorTheme.shapes.pill
    var trackWidth by remember { mutableFloatStateOf(0f) }

    fun seekTo(x: Float) {
        if (trackWidth <= 0f) return
        onSeek(((x / trackWidth).coerceIn(0f, 1f) * duration).toLong())
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_TOUCH_HEIGHT.dp)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .pointerInput(duration) {
                detectTapGestures { offset -> seekTo(offset.x) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures { change, _ -> seekTo(change.position.x) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT.dp)
                .clip(shape)
                .background(colors.surfaceHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .fillMaxSize()
                    .background(colors.onSurfaceVariant.copy(alpha = 0.30f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction)
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(colors.accentStops)),
            )
        }
        if (status.durationMs > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (maxWidth - PLAYHEAD_SIZE.dp) * playedFraction)
                    .size(PLAYHEAD_SIZE.dp)
                    .clip(shape)
                    .background(colors.cursor)
                    .border(2.dp, colors.background, shape),
            )
        }
    }
}

@Composable
private fun TransportDeck(
    status: PlayerStatus,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    skipSeconds: Int,
    onAction: (PlayerAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            icon = Icons.Rounded.Replay10,
            label = "−${skipSeconds}s",
            action = PlayerAction.REWIND,
            focused = focusedAction == PlayerAction.REWIND,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        TransportButton(
            icon = if (status.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = if (status.playing) "Pause" else "Play",
            action = PlayerAction.PLAY_PAUSE,
            focused = focusedAction == PlayerAction.PLAY_PAUSE,
            onAction = onAction,
            modifier = Modifier.weight(1.18f),
        )
        TransportButton(
            icon = Icons.Rounded.Forward30,
            label = "+${skipSeconds}s",
            action = PlayerAction.FORWARD,
            focused = focusedAction == PlayerAction.FORWARD,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        if (hasNextEpisode) {
            TransportButton(
                icon = Icons.Rounded.SkipNext,
                label = "Next episode",
                action = PlayerAction.NEXT_EPISODE,
                focused = focusedAction == PlayerAction.NEXT_EPISODE,
                onAction = onAction,
                modifier = Modifier.weight(1.18f),
            )
        }
        TransportButton(
            icon = Icons.Rounded.Stop,
            label = "Stop",
            action = PlayerAction.STOP,
            focused = focusedAction == PlayerAction.STOP,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Secondary playback controls. Every item calls a live player command. */
@Composable
private fun PlaybackTools(
    status: PlayerStatus,
    focusedAction: PlayerAction,
    onAction: (PlayerAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackToolButton(
            icon = Icons.Rounded.Replay,
            label = "Restart",
            value = "From start",
            action = PlayerAction.RESTART,
            focused = focusedAction == PlayerAction.RESTART,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        PlaybackToolButton(
            icon = Icons.Rounded.Speed,
            label = "Speed",
            value = status.playbackSpeed.asSpeedLabel(),
            action = PlayerAction.SPEED,
            focused = focusedAction == PlayerAction.SPEED,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        PlaybackToolButton(
            icon = Icons.Rounded.VolumeUp,
            label = "Audio",
            value = status.activeAudioLabel(),
            action = PlayerAction.AUDIO,
            focused = focusedAction == PlayerAction.AUDIO,
            enabled = status.audioTracks.size > 1,
            onAction = onAction,
            modifier = Modifier.weight(1.25f),
        )
        PlaybackToolButton(
            icon = Icons.Rounded.Tune,
            label = "Source",
            value = "Change",
            action = PlayerAction.CHANGE_SOURCE,
            focused = focusedAction == PlayerAction.CHANGE_SOURCE,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackToolButton(
    icon: ImageVector,
    label: String,
    value: String,
    action: PlayerAction,
    focused: Boolean,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = enabled && (focused || hover.isHovered)
    val container = if (lit) colors.cursor else colors.surface.copy(alpha = 0.90f)
    val content = when {
        lit -> contrastingContentColor(colors.cursor)
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant.copy(alpha = 0.42f)
    }

    Row(
        modifier = modifier
            .height(PLAYBACK_TOOL_HEIGHT.dp)
            .pointerHover(hover)
            .clip(shape)
            .background(container)
            .border(
                width = if (lit) 2.dp else 1.dp,
                color = if (lit) content.copy(alpha = 0.72f) else colors.outline.copy(alpha = 0.28f),
                shape = shape,
            )
            .thorCursor(focused = lit, shape = shape)
            .clickable(enabled = enabled) { onAction(action) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(21.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.72f),
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    action: PlayerAction,
    focused: Boolean,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    val primary = action == PlayerAction.PLAY_PAUSE
    val intent = if (action == PlayerAction.STOP) colors.error else colors.cursor
    val container = when {
        lit -> intent
        primary -> intent.copy(alpha = 0.18f)
        else -> colors.surface.copy(alpha = 0.90f)
    }
    val content = when {
        lit -> contrastingContentColor(intent)
        primary || action == PlayerAction.STOP -> intent
        else -> colors.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .height(TRANSPORT_HEIGHT.dp)
            .pointerHover(hover)
            .clip(shape)
            .background(container)
            .border(
                width = if (lit) 2.dp else 1.dp,
                color = if (lit) content.copy(alpha = 0.74f) else intent.copy(alpha = 0.24f),
                shape = shape,
            )
            .thorCursor(focused = lit, shape = shape)
            .clickable { onAction(action) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Source identity and health, kept compact but readable during a stall. */
@Composable
private fun StreamFacts(playback: Playback, status: PlayerStatus) {
    val colors = ThorTheme.colors
    val source = playback.source
    val bufferAheadSeconds = ((status.bufferedMs - status.positionMs) / 1000L)
        .coerceAtLeast(0L)
    val facts = listOfNotNull(
        source.quality.summary.takeIf(String::isNotBlank),
        source.sizeLabel,
        status.videoWidth.takeIf { it > 0 }?.let { "${it}×${status.videoHeight}" },
        when (source.cached) {
            CacheStatus.CACHED -> "Cached"
            CacheStatus.NOT_CACHED -> "Fetching"
            CacheStatus.UNKNOWN -> null
        },
        source.providerName,
        source.seeders?.let { "$it seeders" },
        "${bufferAheadSeconds}s buffered",
        status.activeAudioLabel().takeIf { status.audioTracks.isNotEmpty() }?.let { "Audio: $it" },
        "Speed ${status.playbackSpeed.asSpeedLabel()}",
    )

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ThorTheme.shapes.panel,
        color = colors.surface,
        alphaOverride = 0.86f,
        level = SurfaceLevel.RAISED,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PLAYBACK DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status.stateLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.error != null || status.audioUnsupported) {
                        colors.error
                    } else {
                        colors.cursor
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                facts.forEach { fact -> FactChip(fact) }
            }
            Text(
                text = source.title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            status.problemDescription()?.let { problem ->
                Text(
                    text = problem,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ThorTheme.shapes.small)
                        .background(colors.error.copy(alpha = 0.10f))
                        .border(1.dp, colors.error.copy(alpha = 0.30f), ThorTheme.shapes.small)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FactChip(text: String) {
    val colors = ThorTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(colors.surfaceHighest.copy(alpha = 0.86f))
            .border(1.dp, colors.outline.copy(alpha = 0.26f), ThorTheme.shapes.pill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun PlayerStatus.stateLabel(): String = when {
    error != null -> "Playback issue"
    audioUnsupported -> "Audio unavailable"
    suppressed -> "Held by system"
    buffering -> "Buffering"
    ended -> "Finished"
    playing -> "Playing"
    else -> "Paused"
}

private fun PlayerStatus.problemDescription(): String? = when {
    error != null -> error
    audioUnsupported -> "This release's audio cannot be decoded on this device. " +
        "Stop playback and choose a source listing AAC, AC3 or EAC3."
    suppressed -> "Android is temporarily holding playback because another app owns audio focus."
    else -> null
}

private fun PlayerStatus.activeAudioLabel(): String =
    audioTracks.getOrNull(selectedAudioTrack) ?: audioTracks.firstOrNull() ?: "Auto"

private fun Float.asSpeedLabel(): String = when {
    kotlin.math.abs(this - toInt()) < 0.01f -> "${toInt()}x"
    else -> "${this}x"
}

/** "1:42:07", or "3:12" for anything under an hour. */
private fun Long.asClock(): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(this.coerceAtLeast(0L))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val TRACK_HEIGHT = 8
private const val TRACK_TOUCH_HEIGHT = 32
private const val PLAYHEAD_SIZE = 15
private const val TRANSPORT_HEIGHT = 68
private const val PLAYBACK_TOOL_HEIGHT = 52
