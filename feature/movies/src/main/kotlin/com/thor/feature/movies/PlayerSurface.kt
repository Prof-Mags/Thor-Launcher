package com.thor.feature.movies

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.thor.core.common.log.ThorLog
import kotlinx.coroutines.delay

/** What the controls need to know, sampled from the player on a timer. */
data class PlayerStatus(
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val error: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/**
 * Commands the bottom panel sends to the player on the top one.
 *
 * An interface rather than a shared player reference, because the two panels are
 * separate windows with separate compositions. Handing the `ExoPlayer` across
 * that boundary would mean the controls could touch it from a composition that
 * had been paused, which is how the frozen-panel bug started life.
 */
interface PlayerCommands {
    fun playPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)
}

/**
 * The video, alone, on the top panel.
 *
 * No controls are drawn over it and none ever should be: the whole reason this
 * device has two screens is that the picture can stay unobstructed while
 * everything else happens below. See [PlayerControls].
 *
 * A `TextureView` for the same reason the game preview clips use one — it draws
 * into the view hierarchy rather than into its own compositor layer beneath the
 * window, which is what lets Compose put anything at all above or below it.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    url: String,
    resumeFromMs: Long,
    onStatus: (PlayerStatus) -> Unit,
    onCommands: (PlayerCommands) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var surface by remember { mutableStateOf<TextureView?>(null) }
    var videoAspect by remember { mutableFloatStateOf(0f) }
    val currentOnStatus by rememberUpdatedState(onStatus)

    val player = remember(context) {
        /*
         * Debrid links redirect, often across protocols, and the default source
         * refuses that — which surfaces as a clip that will not start rather than
         * as a redirect that was declined.
         */
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                val height = size.height.toFloat()
                videoAspect = if (height > 0f) {
                    size.width * size.pixelWidthHeightRatio / height
                } else {
                    0f
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                ThorLog.w(TAG, "Playback failed (${error.errorCodeName})", error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, url) {
        runCatching {
            player.setMediaItem(MediaItem.fromUri(url.toUri()))
            player.prepare()
            if (resumeFromMs > 0L) player.seekTo(resumeFromMs)
        }.onFailure { ThorLog.w(TAG, "Could not prepare $url", it) }
    }

    // Handed up once. The controls live in the other window and drive the player
    // only through this.
    DisposableEffect(player) {
        val commands = object : PlayerCommands {
            override fun playPause() {
                player.playWhenReady = !player.playWhenReady
            }

            override fun seekTo(positionMs: Long) {
                player.seekTo(positionMs.coerceAtLeast(0L))
            }

            override fun seekBy(deltaMs: Long) {
                val target = (player.currentPosition + deltaMs)
                    .coerceIn(0L, player.duration.coerceAtLeast(0L))
                player.seekTo(target)
            }

            override fun setSpeed(speed: Float) {
                player.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
            }
        }
        onCommands(commands)
        onDispose { }
    }

    /*
     * Polled rather than pushed.
     *
     * ExoPlayer reports state changes but not position, and the timeline on the
     * other panel has to advance smoothly. Four samples a second is enough for a
     * scrubber to look continuous and cheap enough to run for a whole film.
     */
    LaunchedEffect(player) {
        while (true) {
            currentOnStatus(
                PlayerStatus(
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    ended = player.playbackState == Player.STATE_ENDED,
                    error = player.playerError?.errorCodeName,
                    videoWidth = player.videoSize.width,
                    videoHeight = player.videoSize.height,
                ),
            )
            delay(STATUS_INTERVAL_MS)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
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
    }

    DisposableEffect(surface) {
        player.setVideoTextureView(surface)
        onDispose { player.setVideoTextureView(null) }
    }
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

private const val TAG = "Movies"
private const val USER_AGENT = "THOR-Launcher"
private const val CONNECT_TIMEOUT_MS = 20_000
private const val READ_TIMEOUT_MS = 20_000
private const val STATUS_INTERVAL_MS = 250L
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2.0f
