package com.thor.data.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.thor.core.common.log.ThorLog
import java.nio.ByteBuffer

/**
 * Decodes the stream onto a surface with the device's own hardware.
 *
 * Called from the streaming core's video thread, in C, one call per frame-start
 * NAL unit — so everything here is on the hot path and none of it may block. The
 * core owns the buffer it passes in and reuses it the moment this returns, which
 * is why the bytes are copied into the codec's input buffer rather than kept.
 *
 * Submitting directly from the core's thread is what `CAPABILITY_DIRECT_SUBMIT`
 * promises: no queue of our own, no hand-off, and one less frame of latency than
 * a renderer that buffers.
 */
class ThorVideoDecoder(
    private val surface: Surface,
    /** Told what went wrong, so the session can say it rather than just stopping. */
    private val onError: (String) -> Unit,
) : VideoDecoderRenderer() {

    private var codec: MediaCodec? = null
    private var running = false

    /** Set once, from the first frame, so the codec is configured with real values. */
    private var configuredFormat = 0

    override fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int {
        val mime = mimeFor(format)
        if (mime == null) {
            onError("This device cannot decode the video format the PC is sending.")
            return -1
        }

        return try {
            val decoder = findDecoder(mime)
            if (decoder == null) {
                onError("No hardware decoder for $mime on this device.")
                return -1
            }

            val mediaFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_FRAME_RATE, redrawRate)
                /*
                 * No I-frame interval to request: the host decides when to send
                 * one, and asks for them itself when a frame is lost. Setting it
                 * here would be describing an encoder THOR does not run.
                 */
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)

                /*
                 * Low-latency mode where the device has it.
                 *
                 * Decoders buffer several frames by default to smooth playback
                 * of a file, which is exactly wrong for a live stream: it adds
                 * delay between a button press and seeing its effect, which is
                 * the one thing streaming cannot afford.
                 */
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }

            codec = MediaCodec.createByCodecName(decoder).apply {
                configure(mediaFormat, surface, null, 0)
            }
            configuredFormat = format
            ThorLog.i(TAG, "Decoding $mime ${width}x$height@$redrawRate with $decoder")
            0
        } catch (e: Exception) {
            ThorLog.w(TAG, "Could not set up the decoder", e)
            onError("The video decoder would not start: ${e.message}")
            -1
        }
    }

    override fun start() {
        runCatching {
            codec?.start()
            running = true
        }.onFailure {
            ThorLog.w(TAG, "Decoder would not start", it)
            onError("The video decoder would not start.")
        }
    }

    override fun stop() {
        running = false
        runCatching { codec?.stop() }
    }

    override fun cleanup() {
        runCatching { codec?.release() }
        codec = null
    }

    /**
     * Hands one unit of encoded video to the decoder.
     *
     * @return 0 to accept it, or [MoonBridge.DR_NEED_IDR] to tell the host that
     *   this decoder has lost its place and needs a fresh keyframe. Returning the
     *   latter is how a stream recovers from a dropped frame — without it the
     *   picture stays broken until the host happens to send an IDR of its own.
     */
    override fun submitDecodeUnit(
        decodeUnitData: ByteArray,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeMs: Long,
        enqueueTimeMs: Long,
    ): Int {
        val codec = codec ?: return MoonBridge.DR_NEED_IDR
        if (!running) return MoonBridge.DR_NEED_IDR

        return try {
            val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                /*
                 * No input buffer free means the decoder is behind.
                 *
                 * Waiting would push the delay up permanently, so the frame is
                 * dropped and a keyframe requested: one visible glitch now
                 * rather than every frame arriving late from here on.
                 */
                return MoonBridge.DR_NEED_IDR
            }

            val buffer: ByteBuffer = codec.getInputBuffer(index)
                ?: return MoonBridge.DR_NEED_IDR

            buffer.clear()
            if (buffer.capacity() < decodeUnitLength) {
                // Cannot be split across buffers — a NAL unit is decoded whole —
                // so the honest answer is to drop it and ask for a smaller one.
                codec.queueInputBuffer(index, 0, 0, 0, 0)
                return MoonBridge.DR_NEED_IDR
            }
            buffer.put(decodeUnitData, 0, decodeUnitLength)

            /*
             * Parameter sets are flagged as configuration data.
             *
             * A decoder treats SPS, PPS and VPS as description rather than
             * picture: they configure it and produce no frame. Queued as
             * ordinary data they are decoded as a corrupt frame instead, which
             * on some devices is a green flash and on others is a hard failure.
             */
            val flags = when (decodeUnitType) {
                MoonBridge.BUFFER_TYPE_SPS,
                MoonBridge.BUFFER_TYPE_PPS,
                MoonBridge.BUFFER_TYPE_VPS,
                -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG

                else -> 0
            }

            codec.queueInputBuffer(index, 0, decodeUnitLength, receiveTimeMs * 1000, flags)
            drainOutput(codec)
            0
        } catch (e: IllegalStateException) {
            ThorLog.w(TAG, "Decoder rejected a frame", e)
            onError("The video decoder failed mid-stream.")
            MoonBridge.DR_NEED_IDR
        }
    }

    /**
     * Releases whatever the decoder has finished, straight to the surface.
     *
     * Drained here rather than on a thread of its own because the surface does
     * the rendering: `releaseOutputBuffer(index, true)` hands the frame to the
     * compositor, and there is nothing to do with it in between. A second thread
     * would add a hop and a lock for no work.
     */
    private fun drainOutput(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            if (index < 0) return
            codec.releaseOutputBuffer(index, true)
        }
    }

    /**
     * What this decoder promises the core.
     *
     * `DIRECT_SUBMIT` says frames may be handed over on the core's own thread,
     * which removes a queue and a thread hand-off from every frame. It is only
     * true because [submitDecodeUnit] never blocks: it drops rather than waits.
     */
    override fun getCapabilities(): Int = MoonBridge.CAPABILITY_DIRECT_SUBMIT

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        // Accepted and ignored. Announcing HDR without honouring the metadata
        // would produce a washed-out picture that looks like a bug in the game.
        ThorLog.i(TAG, "Host asked for HDR=$enabled; not applied")
    }

    companion object {
        /**
         * Whether this device can decode [mime] in hardware.
         *
         * Asked before a session is negotiated, so THOR only offers the host
         * formats it can actually display. Offering one it cannot is a stream
         * that connects perfectly and shows nothing.
         */
        fun supports(mime: String): Boolean = findDecoder(mime) != null

        private const val TAG = "Stream"

        /** Long enough to be worth waiting, short enough not to stall a frame. */
        private const val DEQUEUE_TIMEOUT_US = 10_000L

        /** One compressed frame at 4K never approaches this; the codec may cap it. */
        private const val MAX_INPUT_BYTES = 1024 * 1024

        private fun mimeFor(format: Int): String? = when {
            format and MoonBridge.VIDEO_FORMAT_MASK_H264 != 0 -> MediaFormat.MIMETYPE_VIDEO_AVC
            format and MoonBridge.VIDEO_FORMAT_MASK_H265 != 0 -> MediaFormat.MIMETYPE_VIDEO_HEVC
            format and MoonBridge.VIDEO_FORMAT_MASK_AV1 != 0 -> MediaFormat.MIMETYPE_VIDEO_AV1
            else -> null
        }

        /**
         * A hardware decoder for [mime], preferred over anything in software.
         *
         * A software decoder will happily accept 1080p60 and then fall minutes
         * behind, which presents as the stream freezing rather than as the
         * device being too slow. Better to say so and stop.
         */
        private fun findDecoder(mime: String): String? {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            return codecs.codecInfos
                .filterNot(MediaCodecInfo::isEncoder)
                .filter { mime in it.supportedTypes.map(String::lowercase) }
                .sortedByDescending { it.isHardwareAcceleratedCompat }
                .firstOrNull()
                ?.name
        }

        /**
         * `isHardwareAccelerated` needs API 29, which is this project's minimum
         * — but the name is still the reliable tell on devices whose codec list
         * mislabels itself, and `OMX.google.*` / `c2.android.*` are software by
         * convention on every Android build.
         */
        private val MediaCodecInfo.isHardwareAcceleratedCompat: Boolean
            get() = isHardwareAccelerated &&
                !name.startsWith("OMX.google.", ignoreCase = true) &&
                !name.startsWith("c2.android.", ignoreCase = true)
    }
}
