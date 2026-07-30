package com.thor.data.capture

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.thor.core.common.log.ThorLog
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
     */
    data class Active(val displayId: Int) : RecordingState

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
 * output, and the launcher renders a console mock-up onto it holding both panels.
 * The frames go straight from the compositor to the encoder, never through a bitmap,
 * and no permission is involved because the app owns both the display and its
 * contents.
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

    val isRecording: Boolean get() = _state.value is RecordingState.Active

    /**
     * Starts a recording and returns the display to render the mock-up onto.
     *
     * @param width video width in pixels; rounded to an even number because H.264
     *   macroblocks come in pairs and an odd dimension is rejected by some encoders
     */
    fun start(width: Int, height: Int, densityDpi: Int): RecordingState {
        if (isRecording) return _state.value

        val videoWidth = width.coerceIn(MIN_DIMENSION, MAX_DIMENSION).roundToEven()
        val videoHeight = height.coerceIn(MIN_DIMENSION, MAX_DIMENSION).roundToEven()

        val uri = createOutputEntry() ?: return fail("Could not create the video file")

        return runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: error("No file descriptor for $uri")

            val newRecorder = descriptor.use { file ->
                buildRecorder().apply {
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(videoWidth, videoHeight)
                    setVideoFrameRate(FRAME_RATE)
                    setVideoEncodingBitRate(bitRateFor(videoWidth, videoHeight))
                    setOutputFile(file.fileDescriptor)
                    prepare()
                }
            }

            /*
             * Own content only, and never mirrored: this display exists to be drawn
             * into, so anything the system might otherwise duplicate onto it — the
             * default display's content — would be exactly wrong. The presentation
             * flag is what lets a `Presentation` window attach to it.
             */
            val display = displayManager?.createVirtualDisplay(
                DISPLAY_NAME,
                videoWidth,
                videoHeight,
                densityDpi.coerceAtLeast(MIN_DENSITY),
                newRecorder.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            ) ?: error("The device would not create a virtual display")

            newRecorder.start()

            recorder = newRecorder
            virtualDisplay = display
            pendingUri = uri

            RecordingState.Active(display.display.displayId).also { _state.value = it }
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
        val name = "THOR-${System.currentTimeMillis()}.mp4"
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
        const val DISPLAY_NAME = "THOR capture"
        const val OUTPUT_DIRECTORY = "Movies/THOR"
        const val FRAME_RATE = 30
        const val MIN_DIMENSION = 240
        const val MAX_DIMENSION = 2160
        const val MIN_DENSITY = 160
        const val MIN_BIT_RATE = 2_000_000L
        const val MAX_BIT_RATE = 24_000_000L
    }
}
