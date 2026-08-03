package com.thor.launcher.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The one screen a screen recording has to pass through.
 *
 * `MediaProjection` is granted by a system dialog and only to an *activity* result —
 * there is no way for a service, a notification action or a tile to obtain one on
 * its own. So this exists purely to be started, show the dialog, hand the result to
 * [RecordingService] and disappear. It has no layout and no theme of its own beyond
 * being transparent, which is why the user sees the consent dialog and nothing else.
 *
 * The grant is per session by design, on Android's part rather than the launcher's:
 * an app that could silently re-acquire the right to watch the screen would be a
 * different and much worse thing than one that asks.
 */
class ProjectionConsentActivity : ComponentActivity() {

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START_SCREEN
                    putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(RecordingService.EXTRA_CONSENT, data)
                },
            )
        }
        // Either way this activity is done. A refusal is an answer, not an error, and
        // it needs no message: the user has just dismissed a dialog on purpose.
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finish()
            return
        }

        consent.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        /**
         * Asks for a screen recording from anywhere, including a notification.
         *
         * `NEW_TASK` because the caller is often not an activity — a service or a
         * notification action has no task to put this in.
         */
        fun request(context: Context) {
            context.startActivity(
                Intent(context, ProjectionConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
