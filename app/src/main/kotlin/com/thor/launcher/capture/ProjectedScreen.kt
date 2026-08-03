package com.thor.launcher.capture

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.thor.core.common.log.ThorLog

/**
 * The real screen, drawn as a texture so it can go inside the console mock-up.
 *
 * A projection has to end up on a `Surface`, and the obvious surface is the video
 * encoder's — which is what a screen recorder normally does and what produces a
 * video of one bare screen. The whole point here is the opposite: the recording is
 * the drawn device with both of its screens, and the projected display is only the
 * picture in the lid.
 *
 * So the projection is pointed at a [TextureView] instead. The view owns a
 * `SurfaceTexture`, the projection mirrors the display into it, and the view draws
 * it like any other content — inside the mock-up, on the launcher's private
 * recording display, which is where the encoder is actually reading from. A game
 * therefore lands in the lid's screen with the launcher's own panel below it.
 *
 * There is no feedback loop in that, though it is the first thing to worry about:
 * the projection mirrors the *default* display, and this is drawn onto the private
 * recording display, which is not it.
 *
 * @param width the mirror's pixel width; the real screen's, so nothing is rescaled
 *   twice on its way into a frame that is about to be scaled anyway
 */
@Composable
fun ProjectedScreen(
    projection: MediaProjection,
    width: Int,
    height: Int,
    densityDpi: Int,
    modifier: Modifier = Modifier,
) {
    /*
     * Held outside the view so it can be released on the way out.
     *
     * A virtual display outlives the view that fed it unless something closes it,
     * and an orphaned one keeps the projection alive — which the system shows as a
     * screen still being captured after the recording has stopped.
     */
    val mirror = remember { MirrorHandle() }

    DisposableEffect(projection) {
        onDispose { mirror.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
                // Opaque: this is a screen, and letting the console body show through
                // it would be a window into the drawing rather than a display on it.
                isOpaque = true

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        viewWidth: Int,
                        viewHeight: Int,
                    ) {
                        /*
                         * The buffer is the *screen's* size, not the view's.
                         *
                         * Left at the view's size the mirror would be resampled to
                         * whatever the lid's cut-out happens to be and then again by
                         * the encoder. Asking for the real resolution and letting the
                         * view scale once is one resampling instead of two.
                         */
                        texture.setDefaultBufferSize(width, height)
                        mirror.attach(projection, Surface(texture), width, height, densityDpi)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        viewWidth: Int,
                        viewHeight: Int,
                    ) {
                        texture.setDefaultBufferSize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        mirror.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** The mirroring display, kept somewhere it can be closed exactly once. */
private class MirrorHandle {

    private var display: VirtualDisplay? = null
    private var surface: Surface? = null

    fun attach(
        projection: MediaProjection,
        target: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
    ) {
        release()
        surface = target

        display = runCatching {
            projection.createVirtualDisplay(
                MIRROR_NAME,
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                target,
                null,
                null,
            )
        }.onFailure { error ->
            ThorLog.e(TAG, "Could not mirror the screen into the recording", error)
        }.getOrNull()
    }

    fun release() {
        runCatching { display?.release() }
        runCatching { surface?.release() }
        display = null
        surface = null
    }

    private companion object {
        const val TAG = "ProjectedScreen"
        const val MIRROR_NAME = "Loki mirror"
    }
}
