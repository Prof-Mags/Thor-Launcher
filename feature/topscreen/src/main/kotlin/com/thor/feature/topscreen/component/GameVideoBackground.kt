package com.thor.feature.topscreen.component

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.thor.core.common.log.ThorLog
import com.thor.feature.topscreen.TopScreen

/**
 * Plays a game's preview clip behind the detail panel.
 *
 * Silent and looping. Not Media3's `PlayerView`, because none of the transport
 * controls apply — this is decoration, and the video surface is the only part of
 * that view actually wanted.
 *
 * **A `TextureView`, and it has to be.** A `SurfaceView` gets its own compositor
 * layer *behind* the app's window, which is fine for a video with nothing under
 * it and useless here: this clip has to sit above the still backdrop and below
 * the scrim and the information panel. Compose draws all of those into the window
 * surface, so the window always won and the clip was never visible — trailers
 * played correctly, inaudibly and entirely behind a screenshot, for as long as
 * the cursor rested on the game. A `SurfaceView` cannot be put between two
 * Compose layers at all; it is either below the window or, with `setZOrderOnTop`,
 * above every part of it including the panel. A `TextureView` draws into the view
 * hierarchy like anything else and lands exactly where it is placed.
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
        /*
         * An explicit HTTP source, for one setting.
         *
         * Metadata providers hand out media through a redirector rather than a
         * direct file — ScreenScraper's clips in particular arrive via a PHP
         * endpoint that bounces to its CDN. The default data source refuses
         * redirects that change protocol, so those bounces failed, and a failed
         * clip quietly falls back to the still artwork: indistinguishable, from
         * the front, from a game that simply has no trailer.
         *
         * A real user agent for the same reason — several of these hosts refuse
         * the default one outright.
         */
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                // Muted always: a launcher that starts making noise because the
                // cursor paused on a game would be intolerable.
                volume = 0f
                playWhenReady = false
            }
    }

    /*
     * The clip's shape, so it can be cropped to the panel rather than stretched.
     *
     * A `TextureView` fills its bounds with the video whatever its proportions,
     * which for a 16:9 trailer on a panel of any other shape means a visibly
     * squashed picture. This replaces a screenshot that was drawn `Crop`, so it
     * has to be cropped the same way or swapping between them distorts.
     */
    var videoAspect by remember { mutableFloatStateOf(0f) }

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
                // A missing or unsupported clip is routine, not exceptional — but
                // the error name is logged because the fallback to stills is
                // silent, and "this game has no trailer" and "this trailer would
                // not load" look identical from the front.
                ThorLog.w(
                    "TopScreen",
                    "Preview clip failed (${error.errorCodeName}): $currentUri",
                    error,
                )
                currentOnFailure()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.setVideoTextureView(null)
            player.release()
        }
    }

    // Re-prepared per clip rather than per recomposition, so moving the cursor
    // within one game does not restart its video.
    LaunchedEffect(videoUri) {
        runCatching {
            // Both trailer providers return MP4 streams, but their redirect URLs
            // often have no `.mp4` suffix. Tell Media3 the container explicitly so
            // it does not fall back to extension guessing and reject a valid clip.
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(videoUri.toUri())
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build(),
            )
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

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).also { view ->
                // Attach at the point the surface is actually created. Waiting
                // for a state-driven follow-up composition left some display
                // implementations with a prepared player but no output target,
                // so the still backdrop remained visible despite a valid trailer.
                player.setVideoTextureView(view)
                // Also on layout: the aspect usually arrives before the view has
                // been measured, and `update` alone would then compute against a
                // zero-sized view and never run again.
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    view.cropToFill(videoAspect)
                }
            }
        },
        update = { view -> view.cropToFill(videoAspect) },
        modifier = modifier,
    )
}

/**
 * Scales the view so the video covers it instead of being squashed into it.
 *
 * The texture always fills the view, so the correction is to grow the axis that
 * filling had to compress — the same result `ContentScale.Crop` gives the still
 * artwork this clip is drawn over.
 */
private fun TextureView.cropToFill(videoAspect: Float) {
    val viewAspect = if (height > 0) width.toFloat() / height else 0f
    if (videoAspect <= 0f || viewAspect <= 0f) return

    val ratio = videoAspect / viewAspect
    if (ratio > 1f) {
        scaleX = ratio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = 1f / ratio
    }
}

/**
 * Sent to metadata hosts, several of which reject the default.
 *
 * A plain product token rather than anything imitating a browser: these are
 * THOR's own requests to APIs it is credentialled for, and they should say so.
 */
private const val USER_AGENT = "Loki-Launcher"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 15_000
