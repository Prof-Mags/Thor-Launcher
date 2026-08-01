package com.thor.data.stream

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.thor.core.common.log.ThorLog

/**
 * Plays the stream's sound.
 *
 * Short of work by design: the streaming core decodes Opus itself, in C, and
 * hands over finished PCM — so there is no decoding here, only a track to write
 * it to. Called from the core's audio thread, which must not be blocked, so
 * every write is non-blocking and a full buffer drops rather than waits.
 */
class ThorAudioRenderer : AudioRenderer {

    private var track: AudioTrack? = null
    private var channelCount = 2

    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ): Int {
        channelCount = audioConfiguration.channelCount

        val channelMask = when (channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> {
                ThorLog.w(TAG, "Unsupported channel count $channelCount")
                return -1
            }
        }

        /*
         * Sized from the frame the host is actually sending, not from the
         * platform minimum.
         *
         * `getMinBufferSize` answers the smallest a track may be, which is a
         * different question from the smallest one that will not stutter. Two
         * frames of headroom is enough to absorb a late packet and small enough
         * that a full buffer is a handful of milliseconds of delay rather than a
         * noticeable lag behind the picture.
         */
        val frameBytes = samplesPerFrame * channelCount * BYTES_PER_SAMPLE
        val minimum = AudioTrack.getMinBufferSize(sampleRate, channelMask, ENCODING)
        val bufferBytes = maxOf(minimum, frameBytes * BUFFERED_FRAMES)

        return try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        /*
                         * Declared as a game rather than as media.
                         *
                         * It decides what the system does to the sound: media is
                         * allowed to be delayed and processed for quality, while
                         * a game is routed for latency. It also means the volume
                         * keys move the right slider and that the launcher's own
                         * sounds duck correctly around it.
                         */
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                /*
                 * Low-latency performance mode, which is the whole point.
                 *
                 * Without it the platform is free to add its own buffering on
                 * top, and sound arriving after the picture is worse than sound
                 * that is slightly rougher.
                 */
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            0
        } catch (e: Exception) {
            ThorLog.w(TAG, "Could not open the audio track", e)
            -1
        }
    }

    override fun start() {
        runCatching { track?.play() }
            .onFailure { ThorLog.w(TAG, "Audio would not start", it) }
    }

    override fun stop() {
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        /*
         * Non-blocking, always.
         *
         * This runs on the core's audio thread, which also feeds the depacketiser
         * — so blocking here does not merely delay sound, it stalls the receive
         * path and drops video with it. A full buffer means the track is behind,
         * and the right answer is to lose a few milliseconds of audio rather
         * than a frame of picture.
         */
        val track = track ?: return
        runCatching {
            track.write(audioData, 0, audioData.size, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    override fun cleanup() {
        runCatching { track?.release() }
        track = null
    }

    private companion object {
        const val TAG = "Stream"

        /** The core decodes Opus to signed 16-bit PCM; this is not a choice. */
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2

        /** Enough to ride out a late packet, few enough to stay in sync. */
        const val BUFFERED_FRAMES = 2
    }
}
