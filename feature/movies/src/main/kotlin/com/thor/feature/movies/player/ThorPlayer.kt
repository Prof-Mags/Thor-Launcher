package com.thor.feature.movies.player

import android.content.Context
import android.os.SystemClock
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.thor.core.common.log.ThorLog
import com.thor.data.network.await
import com.thor.feature.movies.Playback
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The Movies section's player.
 *
 * **Owned by the view model, not by a composition**, and that is the point of it
 * rather than a detail of it. The player used to be `remember`ed inside the top
 * panel, which on this device is the worst place for it: that panel is a
 * composition that gets torn down and rebuilt whenever the display configuration
 * changes or the two surfaces swap windows, and each rebuild released the player
 * and started the stream again from nothing. It also meant the status the *other*
 * panel's controls read was computed in the first panel's composition — the one
 * thing this codebase has learned repeatedly not to do, because a composition
 * whose window is covered stops computing and the values it produced freeze
 * exactly where they were.
 *
 * Here the player, its clock and its status all live in a plain object with its
 * own scope. Panels attach a surface to it and read a flow. Neither can stop it,
 * and neither can be out of date.
 */
@OptIn(UnstableApi::class)
class ThorPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    /**
     * THOR's own client, rather than the platform's `HttpURLConnection`.
     *
     * ExoPlayer's default data source is built on `HttpURLConnection`, whose
     * timeouts are advisory on some Android builds and whose redirect handling
     * gives up when a link crosses protocols. Debrid links are redirects to a CDN
     * node by definition, so both matter here — and sharing the client means the
     * player reuses connections the source search already opened.
     */
    private val httpClient: OkHttpClient,
) {

    /**
     * The shared client, retuned for streaming — and it must be retuned.
     *
     * THOR's client is built for API calls: a 60-second **call** timeout and a
     * 64 MB disk cache. Both are actively wrong here and the first is fatal.
     * `callTimeout` bounds the entire call *including reading the body*, and the
     * body of a film is read for as long as the film lasts — so every stream was
     * guaranteed to be cut off, and a slow first chunk killed it before a frame
     * ever appeared. The cache is merely wasteful: it would try to write a
     * multi-gigabyte video through a cache sized for JSON, evicting the metadata
     * it exists to hold.
     *
     * Derived with `newBuilder` rather than built fresh, so the connection pool
     * and dispatcher are still shared with the search that found the source —
     * which is the reason for using this client at all.
     */
    private val streamingClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(null)
            .build()
    }

    /**
     * A short-tempered client for the stall probe.
     *
     * The probe exists to answer quickly when the stream will not, so it gets a
     * hard overall bound. Waiting a minute to be told why nothing is happening is
     * barely better than not being told.
     */
    private val probeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(null)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    private var exo: ExoPlayer? = null
    private var ticker: Job? = null
    private var probeJob: Job? = null

    /** What is open, for the stall probe to ask about. */
    private var source: String? = null

    /** Headers required by the currently open direct addon stream. */
    private var sourceHeaders: Map<String, String> = emptyMap()

    // Written by the listener, read by the ticker. Both are on the main thread.
    private var audioUnsupported = false
    private var audioTracks = emptyList<String>()
    private var audioSelections = emptyList<AudioSelection>()
    private var selectedAudioTrack = 0
    private var nothingPlayable = false
    private var retried = false

    /** What the probe found out, once it has been anywhere. */
    private var stallReason: String? = null

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            ThorLog.i(TAG, "Playback ${state.stateName()} from ${source.hostOnly()}")
        }

        override fun onPlayerError(error: PlaybackException) {
            ThorLog.w(TAG, "Playback failed (${error.errorCodeName}) from ${source.hostOnly()}", error)

            /*
             * One retry, because the common failures here are transient: a debrid
             * link is a redirect to a CDN node, and a node that drops the first
             * connection usually accepts the second. From the last known position
             * rather than the start, so a failure an hour in does not begin the
             * film again.
             *
             * Never for a decoding failure. That is the device saying it cannot
             * play this release, and it will say it again.
             */
            val player = exo ?: return
            if (!retried && error.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) {
                retried = true
                val resumeAt = player.currentPosition
                ThorLog.i(TAG, "Retrying from ${resumeAt}ms")
                player.prepare()
                player.seekTo(resumeAt)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

            /*
             * A stream with no video and no audio in it.
             *
             * Not an error anywhere in the player: such a file prepares
             * successfully, reports its tracks, and then buffers forever with
             * nothing to render. It is also the commonest way a torrent stream
             * goes wrong — being handed the sample, the `.nfo` or the subtitles
             * rather than the film — so it is worth naming rather than leaving
             * as a spinner. Read only once a track list has actually arrived, so
             * the empty one before preparation finishes is not mistaken for it.
             */
            nothingPlayable = tracks.groups.isNotEmpty() &&
                tracks.groups.none {
                    it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO
                }
            if (nothingPlayable) ThorLog.w(TAG, "Nothing playable in ${source.hostOnly()}")

            /*
             * Silence that is nobody's error.
             *
             * A group the device cannot decode is reported as unsupported rather
             * than failing the playback, so the film runs with no sound and no
             * complaint anywhere. Comparing "has audio" against "has *playable*
             * audio" is the only way to tell that from a film that is silent.
             */
            audioUnsupported = audio.isNotEmpty() && audio.none { group ->
                (0 until group.length).any { trackIndex ->
                    group.isTrackSupported(trackIndex)
                }
            }
            var selected = -1
            audioSelections = buildList {
                audio.forEach { group ->
                    repeat(group.length) { trackIndex ->
                        if (!group.isTrackSupported(trackIndex)) return@repeat
                        val format = group.getTrackFormat(trackIndex)
                        val label = listOfNotNull(
                            format.label?.takeIf(String::isNotBlank),
                            format.language?.uppercase(),
                            format.sampleMimeType?.substringAfter('/')?.uppercase(),
                            format.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
                        ).distinct().joinToString(" ").ifBlank { "Track ${size + 1}" }
                        add(
                            AudioSelection(
                                group = group.mediaTrackGroup,
                                trackIndex = trackIndex,
                                label = label,
                            ),
                        )
                        if (group.isTrackSelected(trackIndex)) selected = lastIndex
                    }
                }
            }
            audioTracks = audioSelections.map(AudioSelection::label)
            selectedAudioTrack = selected.takeIf { it >= 0 }
                ?: selectedAudioTrack.coerceIn(0, (audioTracks.size - 1).coerceAtLeast(0))
        }
    }

    // ------------------------------------------------------------------ opening

    /** Opens [url], from [resumeFromMs] when there is somewhere to resume to. */
    fun open(
        url: String,
        resumeFromMs: Long = 0L,
        requestHeaders: Map<String, String> = emptyMap(),
    ) {
        val player = exo ?: build().also { exo = it }

        source = url
        sourceHeaders = requestHeaders
        audioUnsupported = false
        audioTracks = emptyList()
        audioSelections = emptyList()
        selectedAudioTrack = 0
        nothingPlayable = false
        retried = false
        stallReason = null
        probeJob?.cancel()
        _status.value = PlayerStatus(buffering = true)

        ThorLog.i(TAG, "Opening ${url.hostOnly()}")
        runCatching {
            player.setMediaItem(MediaItem.fromUri(url))
            player.setPlaybackSpeed(1f)
            player.playWhenReady = true
            player.prepare()
            if (resumeFromMs > 0L) player.seekTo(resumeFromMs)
        }.onFailure { error ->
            ThorLog.w(TAG, "Could not prepare ${url.hostOnly()}", error)
            _status.value = PlayerStatus(error = "Could not open this stream")
        }

        startTicking()
    }

    /** Stops and forgets what is open, keeping the player itself for the next one. */
    fun close() {
        probeJob?.cancel()
        ticker?.cancel()
        ticker = null
        source = null
        sourceHeaders = emptyMap()
        stallReason = null
        exo?.run {
            playWhenReady = false
            clearMediaItems()
            stop()
        }
        _status.value = PlayerStatus()
    }

    /**
     * Releases everything. Called when the section's view model is cleared, which
     * is the only moment at which nothing can want the player again.
     */
    fun release() {
        close()
        exo?.run {
            removeListener(listener)
            release()
        }
        exo = null
        scope.cancel()
    }

    private fun build(): ExoPlayer {
        /*
         * Start sooner than the defaults allow.
         *
         * The stock load control waits for two and a half seconds of media before
         * it will begin and fifty on either side before it stops filling. On a
         * remux streamed over a debrid link that is tens of megabytes to acquire
         * before the first frame — long enough that a working stream is
         * indistinguishable from a broken one, which is exactly what "it just
         * says buffering" is.
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
         * The user agent is a browser's on purpose. These are CDN links, and
         * several of the hosts behind them answer an unrecognised agent with a
         * 403 or a redirect loop — which arrives here as a stream that connects
         * and then never delivers a byte, indistinguishable from a slow network.
         */
        /*
         * Headers are a property of a Stremio stream, not of the whole player.
         * Creating a factory per data-source lets the long-lived player honour a
         * source's proxy headers without rebuilding codecs between episodes.
         */
        val dataSource = DataSource.Factory {
            OkHttpDataSource.Factory(streamingClient)
                .setUserAgent(USER_AGENT)
                .setDefaultRequestProperties(BASE_HEADERS + sourceHeaders)
                .createDataSource()
        }

        /*
         * Fall back to another decoder rather than giving up. A hardware decoder
         * that refuses a stream — a profile it does not implement, or one already
         * in use elsewhere on the device — is otherwise fatal, and presents as a
         * track that never starts.
         */
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                // Reads whatever the URL turns out to be: a progressive file, or
                // an HLS playlist, which addons hand back as readily as an MKV.
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource),
            )
            /*
             * Declared as movie content, but focus is *not* handled here.
             *
             * Asking ExoPlayer to manage audio focus reads as the correct thing
             * to do and is why nothing played: when the request is not granted it
             * does not start, does not error, and does not say why. On a launcher,
             * which is a long-lived foreground app on a device where something
             * else may hold focus indefinitely, a player that refuses to start
             * without it is worse than one that is impolite. The attributes still
             * matter — they route the stream as media rather than as a
             * notification blip.
             */
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(listener) }
    }

    // ------------------------------------------------------------------ surface

    /**
     * Points the video at [view], or at nothing.
     *
     * The panel drawing the picture calls this as its surface comes and goes. It
     * is the *only* thing a panel does to the player, which is what lets either
     * panel be the one that draws.
     */
    fun attach(view: TextureView?) {
        exo?.setVideoTextureView(view)
    }

    /**
     * The player itself, for a view that manages its own surface.
     *
     * Couch mode hands this to a stock `PlayerView`, which draws the picture and
     * runs the transport controls on its own — so there is nothing for [attach]
     * to do there. Exposed rather than wrapped because a `PlayerView` wants a
     * `Player`, and inventing a facade for it would mean re-implementing the
     * controller this exists to avoid writing.
     */
    val media: Player? get() = exo

    // ----------------------------------------------------------------- commands

    fun playPause() {
        exo?.let { it.playWhenReady = !it.playWhenReady }
    }

    fun seekTo(positionMs: Long) {
        exo?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val player = exo ?: return
        val target = (player.currentPosition + deltaMs)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        exo?.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
    }

    /** Advances through the rates exposed by the bottom-screen speed button. */
    fun cycleSpeed() {
        val player = exo ?: return
        val current = player.playbackParameters.speed
        val index = PLAYBACK_SPEEDS.indexOfFirst { speed ->
            kotlin.math.abs(speed - current) < SPEED_EPSILON
        }
        player.setPlaybackSpeed(PLAYBACK_SPEEDS[(index + 1).mod(PLAYBACK_SPEEDS.size)])
    }

    /** Selects the next supported audio track and lets ExoPlayer switch in place. */
    fun cycleAudioTrack() {
        val player = exo ?: return
        if (audioSelections.size <= 1) return
        val next = (selectedAudioTrack + 1).mod(audioSelections.size)
        val selection = audioSelections[next]
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(selection.group, selection.trackIndex),
            )
            .build()
        selectedAudioTrack = next
    }

    // ------------------------------------------------------------------- status

    /**
     * Samples the player onto [status].
     *
     * Polled rather than pushed, because ExoPlayer reports state changes but not
     * position and the timeline on the other panel has to advance smoothly. Four
     * samples a second is enough for a scrubber to look continuous and cheap
     * enough to run for a whole film — and it runs on this object's own scope, so
     * a panel whose window is covered does not stop it.
     */
    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            /*
             * A stall is measured against the buffer, not the clock.
             *
             * Time passing proves nothing — a slow link legitimately spends a
             * while filling. What distinguishes a stream that is working from one
             * that is not is whether *any* more of it has arrived.
             */
            var lastBuffered = -1L
            var lastProgressAt = SystemClock.uptimeMillis()
            var probed = false

            while (isActive) {
                val player = exo ?: return@launch
                val state = player.playbackState

                val buffered = player.bufferedPosition
                if (buffered != lastBuffered) {
                    lastBuffered = buffered
                    lastProgressAt = SystemClock.uptimeMillis()
                }

                if (state == Player.STATE_READY) {
                    probed = false
                    stallReason = null
                }

                val waiting = state != Player.STATE_READY && state != Player.STATE_ENDED
                val stalled = waiting &&
                    SystemClock.uptimeMillis() - lastProgressAt > STALL_TIMEOUT_MS

                // Asked once per stall, of the source itself, because the player
                // has already said everything it knows: nothing.
                if (stalled && !probed) {
                    probed = true
                    probeSource()
                }

                _status.value = PlayerStatus(
                    playing = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                    bufferedMs = buffered.coerceAtLeast(0L),
                    buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE,
                    /*
                     * Distinguished from buffering, because they look identical
                     * and are nothing alike: one is the network catching up, the
                     * other is the player being told to hold.
                     */
                    suppressed = player.playbackSuppressionReason !=
                        Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    ended = state == Player.STATE_ENDED,
                    /*
                     * Most specific first. "Nothing is arriving" is true of all of
                     * these and useful for only one: a file with no film in it is
                     * arriving perfectly well, and blaming the connection sends
                     * someone off to fix the wrong thing.
                     */
                    error = player.playerError?.errorCodeName
                        ?: NO_CONTENT_MESSAGE.takeIf { nothingPlayable }
                        ?: stallReason
                        ?: WAITING_MESSAGE.takeIf { stalled },
                    videoWidth = player.videoSize.width,
                    videoHeight = player.videoSize.height,
                    audioUnsupported = audioUnsupported,
                    audioTracks = audioTracks,
                    selectedAudioTrack = selectedAudioTrack,
                    playbackSpeed = player.playbackParameters.speed,
                )

                delay(STATUS_INTERVAL_MS)
            }
        }
    }

    /**
     * Asks the source what is wrong, when the player cannot say.
     *
     * This is the difference between a spinner and a diagnosis. A stall has no
     * error attached to it anywhere — an expired link, a host refusing an
     * unrecognised agent, a CDN node that accepted the connection and then went
     * quiet, and a genuinely slow network are one picture from inside the player.
     * One range request settles which, and costs two bytes.
     */
    private fun probeSource() {
        val target = source ?: return
        probeJob?.cancel()
        probeJob = scope.launch {
            val reason = withContext(Dispatchers.IO) {
                runCatching {
                    probeClient.newCall(
                        Request.Builder()
                            .url(target)
                            .headers((mapOf("User-Agent" to USER_AGENT) + sourceHeaders).toHeaders())
                            // A range, not the whole file: this is asking whether
                            // the host will serve it, not downloading it.
                            .header("Range", "bytes=0-1")
                            .build(),
                    ).await().use { response ->
                        if (response.isSuccessful) {
                            "The source is answering but no video is arriving"
                        } else {
                            "The source answered HTTP ${response.code} — the link may have expired"
                        }
                    }
                }.getOrElse { error ->
                    val detail = error.message?.lineSequence()?.firstOrNull()?.trim()
                    "Could not reach the source" + if (detail.isNullOrBlank()) "" else ": $detail"
                }
            }
            ThorLog.w(TAG, "Stalled on ${target.hostOnly()}: $reason")
            stallReason = reason
        }
    }

    private fun Int.stateName(): String = when (this) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "$this"
    }

    /**
     * The host alone.
     *
     * Enough to identify a failing CDN in a log, and deliberately not enough to
     * put a debrid token in one — the rest of these URLs is the credential.
     */
    private fun String?.hostOnly(): String =
        this?.let { runCatching { it.toUri().host }.getOrNull() } ?: "?"

    private companion object {
        const val TAG = "Movies"

        /**
         * A browser's, deliberately. Several CDN hosts answer an unrecognised
         * agent with a 403 or a redirect loop, which arrives as a stream that
         * connects and never delivers.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

        val BASE_HEADERS = mapOf("Accept" to "*/*")

        const val STATUS_INTERVAL_MS = 250L

        /**
         * Per-read, not per-call. A film's body is read for hours; only an
         * individual read going quiet means anything has gone wrong.
         */
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val READ_TIMEOUT_SECONDS = 30L
        const val PROBE_TIMEOUT_SECONDS = 15L

        /** Enough media to start on, rather than enough to be comfortable. */
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 60_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_000
        const val BUFFER_FOR_REBUFFER_MS = 2_500

        /**
         * How long a stall may last before the source is asked about it.
         *
         * Buffering that never ends is the least informative thing a player can
         * do. After this, something else goes and finds out.
         */
        const val STALL_TIMEOUT_MS = 12_000L

        const val WAITING_MESSAGE = "Nothing is arriving — checking the source…"

        /**
         * Said plainly, because the remedy is not in any setting: the file opened
         * and is streaming, it simply is not the film. Going back and choosing
         * another source is the whole fix.
         */
        const val NO_CONTENT_MESSAGE = "This source has no video in it — pick another"
    }
}

private data class AudioSelection(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
)

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val SPEED_EPSILON = 0.01f
