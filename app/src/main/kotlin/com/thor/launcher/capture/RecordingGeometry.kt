package com.thor.launcher.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A video's pixel shape and the density its content should be laid out at. */
data class CaptureGeometry(val width: Int, val height: Int, val densityDpi: Int)

/**
 * What shape a recording should be, for either kind.
 *
 * Application-scoped and written from the launcher shell, because the two callers
 * are in different worlds: the shell knows the panels' real sizes and computes the
 * console frame from them, while [RecordingService] runs whether or not the shell
 * is on screen and cannot ask a stopped composition anything.
 *
 * A recording started from the notification while a game is in front therefore uses
 * the last shape the launcher reported, which is the right one — the panels have not
 * changed size just because something else is drawn on them.
 */
@Singleton
class RecordingGeometry @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var launcherFrame: CaptureGeometry? = null

    /** Reported by the shell whenever the panels' geometry is resolved. */
    fun setLauncherFrame(width: Int, height: Int, densityDpi: Int) {
        launcherFrame = CaptureGeometry(width, height, densityDpi)
    }

    /**
     * The shape both kinds of recording are drawn at: the two panels stacked.
     *
     * Falls back to the real screen if the shell has never reported — a recording
     * of the wrong shape is worth more than a refusal the user cannot act on.
     */
    fun frame(): CaptureGeometry = launcherFrame ?: screen()

    /**
     * The default display, which is all a projection can ever see.
     *
     * Read at the moment it is asked for rather than cached: a recording started
     * from the shade may follow a rotation, an external display being plugged in, or
     * a resolution change the launcher never observed.
     */
    fun screen(): CaptureGeometry {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)

        return CaptureGeometry(
            width = metrics.widthPixels.takeIf { it > 0 } ?: FALLBACK_WIDTH,
            height = metrics.heightPixels.takeIf { it > 0 } ?: FALLBACK_HEIGHT,
            densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT,
        )
    }

    private companion object {
        /** Only reached if the system reports no display at all. */
        const val FALLBACK_WIDTH = 1920
        const val FALLBACK_HEIGHT = 1080
    }
}
