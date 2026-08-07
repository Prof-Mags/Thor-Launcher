package com.thor.data.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Surface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.thor.core.common.log.ThorLog
import com.thor.core.model.RecordingAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the recorder is doing. */
sealed interface RecordingState {
    data object Idle : RecordingState

    /**
     * Recording, onto [displayId].
     *
     * The id is the point: it is a display the launcher created for itself, and
     * whatever is rendered onto it is what lands in the file.
     *
     * @param mirrored a live projection of the real screen, when the user asked to
     *   record something the launcher did not draw. It is *not* the video — the
     *   video is still the console mock-up on [displayId] — it is the picture that
     *   goes into the mock-up's top screen, so a game is recorded inside the device
     *   with the launcher's own panel underneath it. Null for a recording of the
     *   launcher alone, where the top screen shows the launcher's own top panel.
     */
    data class Active(
        val displayId: Int,
        val mirrored: MediaProjection? = null,
    ) : RecordingState

    /** The last attempt failed; carried so the UI can say what happened. */
    data class Failed(val reason: String) : RecordingState
}

/**
 * Records the launcher onto a private display of its own.
 *
 * **Why not screen capture.** `MediaProjection` — what every screen recorder uses —
 * captures the *default* display and only that one. There is no public API to point
 * it at a secondary panel, so on a two-screen handheld it can never see the bottom
 * screen. Which makes it useless for the one thing worth recording here.
 *
 * So the launcher does not capture a screen at all: it draws a third copy of itself.
 * A private `VirtualDisplay` is created with the encoder's input surface as its
 * output, and the launcher renders both panels onto it, stacked, each at the full
 * size of the screen it stands for. The frames go straight from the compositor to
 * the encoder, never through a bitmap, and no permission is involved because the app
 * owns both the display and its contents.
 *
 * The consequence is worth being clear about: this records **the launcher**, not the
 * screen. A game running on a panel is another app's window on a display this
 * recorder cannot see, so it does not appear.
 */
@Singleton
class ScreenRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val displayManager: DisplayManager? =
        context.getSystemService(DisplayManager::class.java)

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var pendingUri: Uri? = null
    private var activeProjection: MediaProjection? = null

    val isRecording: Boolean get() = _state.value is RecordingState.Active

    /**
     * What sound the next recording captures.
     *
     * Set by the shell from the user's setting rather than read from a repository
     * here, because this class is deliberately free of settings: it is handed a
     * size, a density and a surface, and everything else about it is the caller's
     * decision. See [RecordingAudio] for why there is no game-audio option.
     */
    var audio: RecordingAudio = RecordingAudio.OFF

    /**
     * Whether the microphone has actually been granted.
     *
     * Checked rather than assumed, because `setAudioSource` does not fail where it
     * is called — it fails at `prepare()`, several lines later, by which point the
     * output file has been created and the failure reads as "recording could not
     * be started" rather than as "you have not allowed the microphone".
     */
    private fun hasMicrophonePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Starts a recording and returns the display to render the mock-up onto.
     *
     * @param width video width in pixels; rounded to an even number because H.264
     *   macroblocks come in pairs and an odd dimension is rejected by some encoders
     */
    fun start(width: Int, height: Int, densityDpi: Int): RecordingState {
        if (isRecording) return _state.value

        /*
         * Scaled as a pair, never clamped apart.
         *
         * Each dimension used to be coerced into range on its own, and two panels
         * stacked are tall enough for that to bite: the height hit the ceiling, the
         * width did not, and the frame the launcher was asked to draw into was a
         * different shape from the one it had measured for. Compose does not shrink
         * a column to fit — it overflows — so the bottom panel was simply cut off,
         * and a recording of two screens showed one and a half.
         *
         * One factor applied to both keeps the shape whatever the ceiling is, and
         * the density goes with it: pixels and density together decide the dp box a
         * composition is measured in, so scaling only the pixels would shrink the
         * recorded launcher's layout rather than its resolution.
         */
        val scale = captureScale(width, height)
        val videoWidth = (width * scale).toInt().roundToEven()
        val videoHeight = (height * scale).toInt().roundToEven()
        val videoDensity = (densityDpi * scale).toInt().coerceAtLeast(MIN_DENSITY)

        return begin(videoWidth, videoHeight, videoDensity) { surface ->
            /*
             * Own content only, and never mirrored: this display exists to be drawn
             * into, so anything the system might otherwise duplicate onto it — the
             * default display's content — would be exactly wrong. The presentation
             * flag is what lets a `Presentation` window attach to it.
             */
            displayManager?.createVirtualDisplay(
                DISPLAY_NAME,
                videoWidth,
                videoHeight,
                videoDensity,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            ) ?: error("The device would not create a virtual display")
        }
    }

    /**
     * Records with the real screen in the console's top display.
     *
     * The same video as [start] — the mock-up, both screens, drawn onto the
     * launcher's own private display — with one difference: the picture in the lid's
     * screen is a live mirror of the actual display rather than the launcher's top
     * panel. So a game is recorded *inside the device*, with the launcher's own
     * bottom panel beneath it, instead of as a bare rectangle.
     *
     * The projection is handed back on the state rather than pointed at the encoder.
     * Aiming it straight at the encoder is the obvious thing and produces exactly
     * what this exists to avoid: one screen, no device, none of the launcher. What
     * the recording actually wants is a *texture* it can draw inside the mock-up,
     * and the surface for that belongs to the composition, not to this class.
     *
     * It still sees **one screen** — `MediaProjection` mirrors the default display
     * and has no public route to a secondary panel — but that is the lid, and the
     * base is drawn by the launcher as it always was.
     *
     * And it needs **consent every session**, granted by the system's own dialog.
     * That is not something an app can remember on the user's behalf.
     *
     * @param projection a projection already obtained from a granted consent result
     */
    fun startProjection(
        projection: MediaProjection,
        width: Int,
        height: Int,
        densityDpi: Int,
    ): RecordingState {
        if (isRecording) return _state.value

        /*
         * The projection can end without us: the user revokes it from the system
         * UI, or the platform tears it down. Left unhandled that leaves a recording
         * of a frozen screen and a file nobody asked for.
         */
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (isRecording) stop()
            }
        }
        projection.registerCallback(callback, null)
        activeProjection = projection

        return when (val state = start(width, height, densityDpi)) {
            is RecordingState.Active -> state.copy(mirrored = projection)
                .also { _state.value = it }

            else -> {
                projection.unregisterCallback(callback)
                projection.stop()
                activeProjection = null
                state
            }
        }
    }

    /**
     * Everything a recording needs whichever picture is going into it.
     *
     * The encoder, the output file and the bookkeeping are identical for both; only
     * where the frames come from differs, which is what [display] supplies. Shared
     * rather than written twice because the failure path in particular — release the
     * encoder, delete the half-written entry, report why — is the part that is easy
     * to get subtly different in a second copy and never notice.
     */
    private fun begin(
        videoWidth: Int,
        videoHeight: Int,
        videoDensity: Int,
        display: (Surface) -> VirtualDisplay,
    ): RecordingState {
        val uri = createOutputEntry() ?: return fail("Could not create the video file")

        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: error("No file descriptor for $uri")

            /*
             * Whether sound is going in, decided once at the moment of starting.
             *
             * Read here rather than held, because `MediaRecorder` is configured
             * before `prepare()` and cannot be changed afterwards — a setting
             * changed mid-recording must not take effect until the next one, and
             * reading it once is the only way to be sure it does not.
             *
             * Falls back to silence rather than failing when the permission is
             * missing. `setAudioSource` throws at `prepare()` without RECORD_AUDIO,
             * and a recording that refuses to start because of a setting the user
             * forgot is worse than one with no sound on it.
             */
            val withAudio = audio == RecordingAudio.MICROPHONE && hasMicrophonePermission()

            val newRecorder = descriptor.use { file ->
                buildRecorder().apply {
                    // Sources before the format, and both before the encoders:
                    // MediaRecorder is a state machine and this is the only order
                    // it accepts.
                    if (withAudio) setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    if (withAudio) {
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                        setAudioEncodingBitRate(AUDIO_BIT_RATE)
                        setAudioChannels(AUDIO_CHANNELS)
                    }
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(videoWidth, videoHeight)
                    setVideoFrameRate(FRAME_RATE)
                    setVideoEncodingBitRate(bitRateFor(videoWidth, videoHeight))
                    setOutputFile(file.fileDescriptor)
                    prepare()
                }
            }

            val newDisplay = display(newRecorder.surface)

            newRecorder.start()

            recorder = newRecorder
            virtualDisplay = newDisplay
            pendingUri = uri

            RecordingState.Active(newDisplay.display.displayId).also { _state.value = it }
        }.getOrElse { error ->
            ThorLog.e(TAG, "Could not start recording", error)
            releaseEverything()
            discard(uri)
            fail(error.message ?: "Recording could not be started")
        }
    }

    /**
     * Stops and publishes the file.
     *
     * @return the file's display name when something was written, or null
     */
    fun stop(): String? {
        val uri = pendingUri
        val active = recorder

        /*
         * `stop()` throws when the encoder was handed no frames at all, which is not
         * an error the user should see as a crash — it means the recording was ended
         * within a frame or two of starting. The file is discarded either way.
         */
        val wrote = runCatching { active?.stop() }.isSuccess
        releaseEverything()

        _state.value = RecordingState.Idle

        if (uri == null) return null
        if (!wrote) {
            discard(uri)
            return null
        }
        return publish(uri)
    }

    /** API 31 deprecated the parameterless constructor; both are still needed. */
    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun createOutputEntry(): Uri? = runCatching {
        val name = "Loki-${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, OUTPUT_DIRECTORY)
            // Hidden from galleries until it is finished, so a half-written file is
            // never offered to the user as a video.
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()

    private fun publish(uri: Uri): String? = runCatching {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun discard(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun releaseEverything() {
        // The projection first: it owns the display about to be dropped, and one
        // left running is a screen the system still believes is being captured.
        runCatching { activeProjection?.stop() }
        activeProjection = null
        runCatching { virtualDisplay?.release() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        virtualDisplay = null
        recorder = null
        pendingUri = null
    }

    private fun fail(reason: String): RecordingState =
        RecordingState.Failed(reason).also { _state.value = it }

    /** Roughly 0.15 bits per pixel per frame, which is generous for flat UI. */
    private fun bitRateFor(width: Int, height: Int): Int =
        (width.toLong() * height * FRAME_RATE * 15 / 100)
            .coerceIn(MIN_BIT_RATE, MAX_BIT_RATE)
            .toInt()

    private fun Int.roundToEven(): Int = this - (this % 2)

    private companion object {
        const val TAG = "Recorder"
        const val DISPLAY_NAME = "Loki capture"
        const val OUTPUT_DIRECTORY = "Movies/Loki"
        const val FRAME_RATE = 30

        /** Enough for speech and game audio off a handheld's speakers. */
        const val AUDIO_SAMPLE_RATE = 44_100
        const val AUDIO_BIT_RATE = 128_000

        /** One channel: the microphone on this device is mono whatever is asked. */
        const val AUDIO_CHANNELS = 1
        const val MIN_DENSITY = 160
        const val MIN_BIT_RATE = 2_000_000L
        const val MAX_BIT_RATE = 24_000_000L
    }
}

/**
 * One factor that brings both video dimensions inside the encoder's range.
 *
 * Shrinks when the longer side is over the ceiling — which two stacked panels reach
 * on any device with 1080p screens — and grows when the shorter side is under the
 * floor. Returns 1 when the frame already fits, so the ordinary case is recorded at
 * exactly the panels' own resolution.
 *
 * Top level, and `internal`, so it can be tested without a `Context`: the rule it
 * encodes is the one that broke the recorder, and it broke silently — a clamped
 * dimension produces a valid file of the wrong shape, with no error anywhere.
 */
internal fun captureScale(
    width: Int,
    height: Int,
    minDimension: Int = CAPTURE_MIN_DIMENSION,
    maxDimension: Int = CAPTURE_MAX_DIMENSION,
): Float {
    if (width <= 0 || height <= 0) return 1f

    val shrink = maxDimension.toFloat() / maxOf(width, height)
    val grow = minDimension.toFloat() / minOf(width, height)

    return when {
        shrink < 1f -> shrink
        grow > 1f -> grow
        else -> 1f
    }
}

/** Smallest side an encoder here is asked to accept. */
internal const val CAPTURE_MIN_DIMENSION = 240

/** Largest side. Two 1080p panels stacked land exactly on it. */
internal const val CAPTURE_MAX_DIMENSION = 2160
