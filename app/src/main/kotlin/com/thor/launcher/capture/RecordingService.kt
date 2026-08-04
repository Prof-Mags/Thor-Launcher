package com.thor.launcher.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.thor.core.common.log.ThorLog
import com.thor.data.capture.RecordingState
import com.thor.data.capture.ScreenRecorder
import com.thor.launcher.LauncherActivity
import com.thor.launcher.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Holds a *screen* recording for as long as it runs, wherever the user goes.
 *
 * Only that kind. A recording of the launcher's own two panels is fed by the
 * launcher composing, and the launcher stops composing the moment it is not on
 * screen — so it cannot outlive the activity and a service that could buys nothing.
 * It also costs something real: from Android 14 a foreground service typed for media
 * projection is refused outright unless there is a projection to justify it, so
 * running the mock-up recording through here would have been rejected by the
 * platform. That one stays in the view model.
 *
 * A screen recording is the opposite case in every respect. It mirrors the real
 * display through `MediaProjection`, so it keeps capturing inside a game — which is
 * the point of it — and it therefore needs somewhere to live that is not a screen the
 * user has left, and a control they can reach from where they now are. A foreground
 * service is the only answer Android has to the first, and its notification is the
 * only answer to the second.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recorder: ScreenRecorder

    /** The screen's shape, read fresh so a rotation cannot leave it stale. */
    @Inject lateinit var geometry: RecordingGeometry

    private val notifications by lazy { NotificationManagerCompat.from(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCREEN -> startScreenRecording(intent)
            ACTION_STOP -> stopRecording()
            else -> {
                // Nothing to do and nothing to show: a service with no work must not
                // sit in the foreground holding a notification about it.
                if (!recorder.isRecording) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * The real screen, through a consent result the activity has already collected.
     *
     * Order matters and is dictated by the platform: from Android 14 the foreground
     * service must already be running, and running with the media-projection type,
     * before `getMediaProjection` will hand anything over. Asking first throws.
     */
    private fun startScreenRecording(intent: Intent) {
        if (recorder.isRecording) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? =
            IntentCompat.getParcelableExtra(intent, EXTRA_CONSENT, Intent::class.java)
        if (data == null) {
            ThorLog.w(TAG, "Screen recording asked for with no consent result")
            stopSelf()
            return
        }

        goForeground(recording = true)

        val projection: MediaProjection? = runCatching {
            getSystemService(MediaProjectionManager::class.java)
                ?.getMediaProjection(resultCode, data)
        }.getOrNull()

        if (projection == null) {
            ThorLog.w(TAG, "The system would not grant a projection")
            stopRecording()
            return
        }

        /*
         * The stacked frame, not the raw display.
         *
         * This asked for `screen()` — the default display, 1920 by 1080 — and then
         * drew the same two-panel composition into it. The lid alone fills a
         * sixteen-by-nine canvas at that size, so the base had nowhere to go and
         * ended up inside the picture of the top screen: a recording containing
         * neither panel as itself. The frame the launcher recorder uses is the
         * shape this content was written for, and there is no reason for the two
         * kinds of recording to differ — they draw the same thing.
         */
        val frame = geometry.frame()
        val state = recorder.startProjection(
            projection = projection,
            width = frame.width,
            height = frame.height,
            densityDpi = frame.densityDpi,
        )
        if (state is RecordingState.Failed) {
            ThorLog.w(TAG, "Screen recording refused: ${state.reason}")
            stopRecording()
        }
    }

    private fun stopRecording() {
        val saved = runCatching { recorder.stop() }.getOrNull()
        ThorLog.i(TAG, saved?.let { "Saved $it" } ?: "Nothing was recorded")

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(recording: Boolean) {
        val notification = buildNotification(recording)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * The card in the shade, which is the whole point of the service.
     *
     * Ongoing and unswipeable while a recording runs: dismissing the notification
     * would leave the recording going with nothing to stop it, which is worse than a
     * notification that will not go away.
     */
    private fun buildNotification(recording: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(getString(R.string.recording_title))
            .setContentText(getString(R.string.recording_text))
            .setContentIntent(open)
            .setOngoing(recording)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(R.string.recording_stop),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel),
            // Low: this is a control surface, not news. It should be in the shade
            // when wanted and never in front of whatever is being recorded.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_channel_summary)
            setShowBadge(false)
        }

        notifications.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START_SCREEN = "com.thor.launcher.RECORD_SCREEN"
        const val ACTION_STOP = "com.thor.launcher.RECORD_STOP"

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_CONSENT = "consent"

        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "loki.recording"
        private const val NOTIFICATION_ID = 4201

        /** Stops whatever is recording. */
        fun stop(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
