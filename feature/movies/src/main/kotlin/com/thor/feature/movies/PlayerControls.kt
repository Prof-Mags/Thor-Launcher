package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CacheStatus
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import java.util.concurrent.TimeUnit

/** The transport buttons, in the order the cursor walks them. */
enum class PlayerAction { REWIND, PLAY_PAUSE, FORWARD, NEXT_EPISODE, STOP }

/**
 * Playback controls, on the bottom panel.
 *
 * Every control lives here and none is drawn over the video. That is the whole
 * point of the arrangement: on a single screen, controls either cover the
 * picture or vanish and have to be summoned back, and both are compromises this
 * device does not have to make. Position, remaining time and total runtime are
 * always visible because there is no reason to hide them.
 */
@Composable
fun PlayerControls(
    playback: Playback,
    status: PlayerStatus,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimens.spacing),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Text(
            text = playback.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
            maxLines = 2,
        )

        Timeline(status = status, onSeek = onSeek)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = status.positionMs.asClock(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
            )
            Text(
                // Remaining rather than a second copy of the total, which the
                // right-hand label already gives.
                text = "-" + (status.durationMs - status.positionMs)
                    .coerceAtLeast(0L)
                    .asClock(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = status.durationMs.asClock(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = Icons.Rounded.Replay10,
                label = "Back",
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
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                icon = Icons.Rounded.Forward30,
                label = "Forward",
                action = PlayerAction.FORWARD,
                focused = focusedAction == PlayerAction.FORWARD,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            if (hasNextEpisode) {
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    label = "Next",
                    action = PlayerAction.NEXT_EPISODE,
                    focused = focusedAction == PlayerAction.NEXT_EPISODE,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
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

        StreamFacts(playback = playback, status = status)
    }
}

/**
 * The scrubber, with the buffered extent behind the played one.
 *
 * Buffering is drawn rather than described because it answers the question the
 * viewer actually has during a stall — is more coming, or has it stopped — and
 * a spinner cannot distinguish those.
 */
@Composable
private fun Timeline(status: PlayerStatus, onSeek: (Long) -> Unit) {
    val colors = ThorTheme.colors
    val duration = status.durationMs.coerceAtLeast(1L)
    val playedFraction = (status.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (status.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(TRACK_HEIGHT.dp / 2)

    /*
     * Tapped and dragged directly.
     *
     * The whole point of putting the controls on their own screen is that this
     * bar is a real, permanently visible object rather than something summoned
     * over the picture — so it should behave like one. Seeking by holding a skip
     * button when the target is visible on screen is the sort of thing that makes
     * a remote feel like a remote.
     *
     * Width is captured from layout because the gesture reports a position in
     * pixels and the seek needs a fraction; there is nothing else that knows how
     * wide the track ended up.
     */
    var trackWidth by remember { mutableFloatStateOf(0f) }
    fun seekTo(x: Float) {
        if (trackWidth <= 0f) return
        onSeek(((x / trackWidth).coerceIn(0f, 1f) * duration).toLong())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A larger target than the bar is tall: an eight-pixel line is
            // accurate to look at and impossible to hit.
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
            .background(colors.surfaceElevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferedFraction)
                .fillMaxSize()
                .background(colors.onSurfaceVariant.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(playedFraction)
                .fillMaxSize()
                .background(colors.cursor),
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

    Column(
        modifier = modifier
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(colors.surface)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (lit) colors.cursor else colors.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (lit) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/**
 * What is actually being streamed.
 *
 * Shown during playback rather than only in the source list, because when a
 * stream misbehaves the first question is always which one is playing — and
 * hunting for that answer means leaving the film.
 */
@Composable
private fun StreamFacts(playback: Playback, status: PlayerStatus) {
    val colors = ThorTheme.colors
    val source = playback.source

    /*
     * How far ahead the buffer is, in seconds rather than as a percentage.
     *
     * A percentage of a two-hour film says nothing useful when playback stalls;
     * "12s ahead" answers the only question that matters at that moment, which is
     * whether it is about to recover on its own.
     */
    val bufferAheadSeconds = ((status.bufferedMs - status.positionMs) / 1000L)
        .coerceAtLeast(0L)

    val facts = listOfNotNull(
        source.quality.summary.takeIf(String::isNotBlank),
        source.sizeLabel,
        status.videoWidth.takeIf { it > 0 }?.let { "${it}×${status.videoHeight}" },
        when (source.cached) {
            CacheStatus.CACHED -> "Real-Debrid: cached"
            CacheStatus.NOT_CACHED -> "Real-Debrid: fetching"
            CacheStatus.UNKNOWN -> null
        },
        source.providerName,
        source.seeders?.let { "$it seeders" },
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "STREAM",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = when {
                    status.error != null -> status.error
                    status.audioUnsupported -> "No playable audio"
                    status.buffering -> "Buffering…"
                    else -> "${bufferAheadSeconds}s buffered"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (status.error != null || status.audioUnsupported) {
                    colors.error
                } else {
                    colors.onSurfaceVariant
                },
            )
        }
        Text(
            text = facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = source.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2,
        )

        /*
         * Said out loud when it happens, because nothing else will say it.
         *
         * Silence has no error code and no visible symptom beyond itself, so a
         * viewer's first assumption is that the player is broken. The remedy is
         * not in any setting — it is to go back and choose a different release —
         * and that is only obvious if someone says so.
         */
        if (status.audioUnsupported) {
            Text(
                text = "This release's audio (often DTS or TrueHD) cannot be decoded " +
                    "on this device. Go back and pick another source — one listing " +
                    "AAC, AC3 or EAC3 will have sound.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
            )
        }

        if (status.audioTracks.size > 1) {
            Text(
                text = "Audio tracks: " + status.audioTracks.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
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

/** The bar is thin to read accurately and would be impossible to hit at that size. */
private const val TRACK_TOUCH_HEIGHT = 36
