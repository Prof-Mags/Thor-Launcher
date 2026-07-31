package com.thor.feature.movies

import android.os.SystemClock
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
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

    // Read from the track list rather than guessed at; see [PlayerStatus].
    var audioUnsupported by remember(url) { mutableStateOf(false) }
    var audioTracks by remember(url) { mutableStateOf(emptyList<String>()) }

    // One retry per stream, reset when the stream changes.
    var retried by remember(url) { mutableStateOf(false) }

    // The host alone: enough to identify a failing CDN, without writing a debrid
    // token into the log.
    val host = remember(url) { runCatching { url.toUri().host }.getOrNull() ?: "?" }

    val player = remember(context) {
        /*
         * Debrid links redirect, often across protocols, and the default source
         * refuses that — which surfaces as a clip that will not start rather than
         * as a redirect that was declined.
         *
         * The user agent is a browser's on purpose. These are CDN links, and
         * several of the hosts behind them answer an unrecognised agent with a
         * 403 or a redirect loop — which arrives here as a stream that connects
         * and then never delivers a byte, indistinguishable from a slow network.
         */
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            // Some hosts serve the file only when a range is asked for, and
            // answer a plain GET with an HTML interstitial that never plays.
            .setDefaultRequestProperties(mapOf("Accept" to "*/*"))

        /*
         * Start sooner than the defaults allow.
         *
         * ExoPlayer's stock load control waits for two and a half seconds of
         * media before it will begin, and fifty on either side of the position
         * before it stops filling. On a remux streamed over a debrid link that is
         * tens of megabytes of buffer to acquire before the first frame appears —
         * long enough that a working stream is indistinguishable from a broken
         * one, which is exactly what "it just says buffering" is.
         */
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        /*
         * Fall back to another decoder rather than giving up.
         *
         * A hardware decoder that refuses a stream — a profile it does not
         * implement, or one already in use by something else on the device — is
         * otherwise fatal, and presents as a track that never starts. With
         * fallback on, the software decoder gets its turn.
         */
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            )

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            /*
             * Declared as movie content, but focus is *not* handled here.
             *
             * Asking ExoPlayer to manage audio focus reads as the correct thing
             * to do and is why nothing played: when the request is not granted it
             * does not start, does not error, and does not say why — the panel
             * simply sat on "buffering" forever. On a launcher, which is itself a
             * long-lived foreground app on a device where something else may hold
             * focus indefinitely, a player that refuses to start without it is
             * worse than one that is impolite.
             *
             * The attributes still matter: they route the stream as media rather
             * than as a notification blip, which is the part that affects volume
             * and output selection.
             */
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            // Pausing when the headphones are pulled out is what every other
            // player does, and its absence reads as the launcher ignoring them.
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            /**
             * Logged because a stall has no other evidence.
             *
             * "It just says buffering" is the same picture whether the network is
             * slow, the host is refusing, the link has expired or the file cannot
             * be parsed — and none of those raise an error. The state transitions
             * are the only record of which one happened.
             */
            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "$state"
                }
                ThorLog.i(TAG, "Playback $name from $host")
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                val height = size.height.toFloat()
                videoAspect = if (height > 0f) {
                    size.width * size.pixelWidthHeightRatio / height
                } else {
                    0f
                }
            }

            /**
             * Logged with the URL's host, which is the part that identifies the
             * failure without putting a debrid token in the log.
             */
            override fun onPlayerError(error: PlaybackException) {
                ThorLog.w(TAG, "Playback failed (${error.errorCodeName}) from $host", error)

                /*
                 * One retry, because the common failures here are transient.
                 *
                 * A debrid link is a redirect to a CDN node, and a node that
                 * refuses or drops the first connection will usually accept the
                 * second. Retrying at the last known position rather than from
                 * the start, so a failure ten minutes in does not start the film
                 * again.
                 */
                if (!retried && error.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) {
                    retried = true
                    val resumeAt = player.currentPosition
                    ThorLog.i(TAG, "Retrying from ${resumeAt}ms")
                    player.prepare()
                    player.seekTo(resumeAt)
                }
            }

            /**
             * Notices silence that is nobody's error.
             *
             * A group the device cannot decode is reported as unsupported rather
             * than failing the playback, so the film runs with no sound and no
             * complaint anywhere. Comparing "has audio" against "has *playable*
             * audio" is the only way to tell that apart from a film that is
             * genuinely silent.
             */
            override fun onTracksChanged(tracks: Tracks) {
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                audioUnsupported = audio.isNotEmpty() && audio.none { it.isSupported }
                audioTracks = audio.mapIndexed { index, group ->
                    val format = group.mediaTrackGroup.getFormat(0)
                    listOfNotNull(
                        format.language?.uppercase(),
                        format.sampleMimeType?.substringAfter('/')?.uppercase(),
                        format.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
                        if (group.isSupported) null else "unsupported",
                    ).joinToString(" ").ifBlank { "Track ${index + 1}" }
                }
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
    LaunchedEffect(player, url) {
        /*
         * A stall is measured against the buffer, not the clock.
         *
         * Time passing proves nothing — a slow link legitimately spends a long
         * while filling. What distinguishes a stream that is working from one
         * that is not is whether *any* more of it has arrived, so that is what is
         * watched.
         */
        var lastBuffered = -1L
        var lastProgressAt = SystemClock.uptimeMillis()

        while (true) {
            val buffered = player.bufferedPosition
            if (buffered != lastBuffered) {
                lastBuffered = buffered
                lastProgressAt = SystemClock.uptimeMillis()
            }
            val stalledMs = SystemClock.uptimeMillis() - lastProgressAt
            val stalled = player.playbackState != Player.STATE_READY &&
                stalledMs > STALL_TIMEOUT_MS

            currentOnStatus(
                PlayerStatus(
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                    buffering = player.playbackState == Player.STATE_BUFFERING,
                    /*
                     * Distinguished from buffering, because they look identical
                     * and are nothing alike: one is the network catching up, the
                     * other is the player being told to hold. Reporting the
                     * second as the first is what made a stalled stream and a
                     * suppressed one indistinguishable.
                     */
                    suppressed = player.playbackSuppressionReason !=
                        Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    ended = player.playbackState == Player.STATE_ENDED,
                    error = player.playerError?.errorCodeName
                        ?: "Nothing is arriving from this source".takeIf { stalled },
                    videoWidth = player.videoSize.width,
                    videoHeight = player.videoSize.height,
                    audioUnsupported = audioUnsupported,
                    audioTracks = audioTracks,
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

/**
 * A browser's, deliberately.
 *
 * These are CDN links, and several hosts behind them answer an unrecognised
 * agent with a 403 or a redirect loop — which arrives as a stream that connects
 * and never delivers, looking exactly like a slow network.
 */
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

private const val CONNECT_TIMEOUT_MS = 20_000
private const val READ_TIMEOUT_MS = 20_000
private const val STATUS_INTERVAL_MS = 250L

/** Enough media to start on, rather than enough to be comfortable. */
private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 60_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_000
private const val BUFFER_FOR_REBUFFER_MS = 2_500

/**
 * How long a stall may last before it is called one.
 *
 * Buffering that never ends is the single least informative thing a player can
 * do: it is the same picture whether the network is slow, the host is refusing,
 * or the link has expired. After this it says so.
 */
private const val STALL_TIMEOUT_MS = 25_000L
const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2.0f
