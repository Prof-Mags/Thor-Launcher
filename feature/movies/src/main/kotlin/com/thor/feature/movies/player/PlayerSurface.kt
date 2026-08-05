package com.thor.feature.movies.player

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.movies.Playback

/** What the controls need to know, sampled from the player on a timer. */
data class PlayerStatus(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val buffering: Boolean = false,
    /** Ready, but held: audio focus or a system policy is stopping it. */
    val suppressed: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /**
     * Set when the file has audio but none of it can be decoded.
     *
     * Distinguished from "no audio at all" because the remedy is completely
     * different and neither produces an error: torrent releases very often carry
     * only DTS, DTS-HD or TrueHD, none of which most Android devices can decode.
     * Playback then succeeds in silence, which reads as the player being broken
     * rather than as this release being the wrong one to pick.
     */
    val audioUnsupported: Boolean = false,
    /** Human labels for the selectable audio tracks, in the file's own order. */
    val audioTracks: List<String> = emptyList(),
    /** Index into [audioTracks], when ExoPlayer reports an active track. */
    val selectedAudioTrack: Int = 0,
    /** Current playback rate, reflected immediately after a speed change. */
    val playbackSpeed: Float = 1f,
)

/**
 * The video, alone, on the top panel.
 *
 * No controls are drawn over it and none ever should be: the whole reason this
 * device has two screens is that the picture can stay unobstructed while
 * everything else happens below. See [PlayerControls].
 *
 * This composable owns **nothing**. It hands the player a surface while it is on
 * screen and takes it away again afterwards, and that is the entire contract —
 * the player, its stream and its clock belong to [ThorPlayer], which outlives
 * every panel. It used to be the other way around, with the player remembered
 * here, and the consequence was that a display change or a swap of the two
 * windows released it mid-film and began the stream again from nothing.
 *
 * A `TextureView` for the same reason the game preview clips use one: it draws
 * into the view hierarchy rather than into its own compositor layer beneath the
 * window, which is what lets Compose put anything at all above or below it — and
 * what lets it render inside a `Presentation` on the second panel.
 */
@Composable
fun PlayerSurface(
    player: ThorPlayer,
    playback: Playback,
    status: PlayerStatus,
    showStateOverlay: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var surface by remember { mutableStateOf<TextureView?>(null) }

    // Recomputed as the stream reports its size, and applied on every layout
    // pass because the view can be laid out before the size is known.
    var videoAspect by remember { mutableFloatStateOf(0f) }
    videoAspect = if (status.videoHeight > 0) {
        status.videoWidth.toFloat() / status.videoHeight
    } else {
        0f
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Artwork gives letterbox bars a deliberate ambient treatment while the
        // video itself remains uncropped and completely unobstructed when healthy.
        playback.item.backdropUrl?.let { backdrop ->
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
            )
        }

        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).also { view ->
                    surface = view
                    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        view.fitInside(videoAspect)
                    }
                }
            },
            update = { view -> view.fitInside(videoAspect) },
            modifier = Modifier.fillMaxSize(),
        )

        if (status.buffering) {
            CircularProgressIndicator(
                color = ThorTheme.colors.cursor,
                strokeWidth = 3.dp,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).size(42.dp),
            )
        }

        if (showStateOverlay && status.shouldShowOverlay()) {
            PlaybackStateOverlay(
                playback = playback,
                status = status,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart),
            )
        }
    }

    DisposableEffect(player, surface) {
        player.attach(surface)
        // Detached rather than left dangling: the player outlives this panel, and
        // a released surface it still held would be one it tried to draw into.
        onDispose { player.attach(null) }
    }
}

/** Only appears when the viewer needs state information; normal playback stays clean. */
@Composable
private fun PlaybackStateOverlay(
    playback: Playback,
    status: PlayerStatus,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val state = status.displayState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                ),
            )
            .padding(start = 22.dp, end = 22.dp, top = 46.dp, bottom = 20.dp),
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.panel,
            color = colors.surface,
            alphaOverride = 0.88f,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                when {
                    status.buffering -> CircularProgressIndicator(
                        color = colors.cursor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                    status.error != null -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = colors.error,
                    )
                    status.audioUnsupported -> Icon(
                        imageVector = Icons.Rounded.VolumeOff,
                        contentDescription = null,
                        tint = colors.error,
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = colors.cursor,
                    )
                }
                Column {
                    Text(
                        text = playback.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.error != null || status.audioUnsupported) {
                            colors.error
                        } else {
                            colors.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun PlayerStatus.shouldShowOverlay(): Boolean =
    buffering || suppressed || ended || error != null || audioUnsupported || !playing

private fun PlayerStatus.displayState(): String = when {
    error != null -> error
    audioUnsupported -> "This source has no supported audio track"
    suppressed -> "Playback is being held by the system"
    buffering -> "Buffering stream"
    ended -> "Playback finished"
    !playing -> "Paused"
    else -> "Playing"
}

/**
 * Letterboxes rather than crops, which is the opposite of the preview clips.
 *
 * A hover preview is decoration behind a panel and should fill it; a film is the
 * thing being watched and must not have its edges cut off. The texture always
 * fills the view, so the correction shrinks the axis that filling had to
 * stretch.
 */
private fun TextureView.fitInside(videoAspect: Float) {
    val viewAspect = if (height > 0) width.toFloat() / height else 0f
    if (videoAspect <= 0f || viewAspect <= 0f) return

    val ratio = videoAspect / viewAspect
    if (ratio > 1f) {
        scaleX = 1f
        scaleY = 1f / ratio
    } else {
        scaleX = ratio
        scaleY = 1f
    }
}

const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2.0f
