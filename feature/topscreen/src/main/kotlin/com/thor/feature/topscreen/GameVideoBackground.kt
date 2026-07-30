package com.thor.feature.topscreen

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.thor.core.common.log.ThorLog

/**
 * Plays a game's preview clip behind the detail panel.
 *
 * Silent and looping. A `SurfaceView` rather than Media3's `PlayerView` because
 * none of the transport controls apply — this is decoration, and the surface is
 * the only part of that view actually wanted.
 *
 * @param videoUri clip to play; nothing is rendered when null
 * @param playing false pauses and releases the decoder, which is what stops an
 *   off-screen or unfocused panel from holding a hardware codec open
 * @param onFailure called when the clip cannot be played, so the caller can fall
 *   back to still artwork instead of showing black
 */
@OptIn(UnstableApi::class)
@Composable
fun GameVideoBackground(
    videoUri: String?,
    playing: Boolean,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videoUri == null) return

    val context = LocalContext.current
    var surface by remember { mutableStateOf<SurfaceView?>(null) }

    /*
     * Read at call time rather than captured by the listener below.
     *
     * That listener is registered once — its effect is keyed on the player, which
     * outlives every clip — so a captured `onFailure` went on reporting failures to
     * the composition that happened to be current when the *first* clip was prepared.
     * Moving the cursor to another game then had a failed clip mark the wrong entry
     * as unplayable, and the entry that actually failed kept trying.
     */
    val currentUri by rememberUpdatedState(videoUri)
    val currentOnFailure by rememberUpdatedState(onFailure)

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            // Muted always: a launcher that starts making noise because the
            // cursor paused on a game would be intolerable.
            volume = 0f
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // A missing or unsupported clip is routine, not exceptional.
                ThorLog.w("TopScreen", "Preview clip failed: $currentUri", error)
                currentOnFailure()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Re-prepared per clip rather than per recomposition, so moving the cursor
    // within one game does not restart its video.
    LaunchedEffect(videoUri) {
        runCatching {
            player.setMediaItem(MediaItem.fromUri(videoUri.toUri()))
            player.prepare()
        }.onFailure {
            ThorLog.w("TopScreen", "Could not prepare $videoUri", it)
            onFailure()
        }
    }

    LaunchedEffect(playing) {
        player.playWhenReady = playing
        if (!playing) player.seekTo(0)
    }

    DisposableEffect(surface) {
        player.setVideoSurfaceView(surface)
        onDispose { player.setVideoSurfaceView(null) }
    }

    AndroidView(
        factory = { viewContext -> SurfaceView(viewContext).also { surface = it } },
        modifier = modifier,
    )
}
