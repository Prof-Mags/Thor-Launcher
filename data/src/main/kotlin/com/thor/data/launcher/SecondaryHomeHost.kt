package com.thor.data.launcher

import android.app.Activity
import android.content.Intent
import com.thor.core.common.log.ThorLog
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THOR's activity on the second panel, when one is alive.
 *
 * Held so that launches aimed at that panel can be started *from* it, which is
 * the only route that reliably works. Android will place an activity on a
 * secondary display when the app already has an activity there — and a
 * `Presentation` does not count, however much of the panel it is drawing. THOR's
 * presentation is a window on that display, not an activity on it, so
 * `setLaunchDisplayId` was refused no matter how the launch was phrased.
 *
 * Starting from the secondary home activity sidesteps the question entirely:
 * a new task inherits the display of the activity that started it, so no display
 * option is needed and no permission is consulted. It is also what the platform
 * documents as the normal case — an activity started by the launcher lands on
 * the launcher's own display.
 *
 * A weak reference, cleared on destroy. The system creates and destroys this
 * activity freely to reclaim memory, and a singleton holding a strong reference
 * to an `Activity` is the textbook way to leak a whole view hierarchy.
 */
@Singleton
class SecondaryHomeHost @Inject constructor() {

    private var reference: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    /** Only clears when [activity] is the one currently held, not a newer one. */
    fun detach(activity: Activity) {
        if (reference?.get() === activity) reference = null
    }

    private val live: Activity?
        get() = reference?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    val isAvailable: Boolean get() = live != null

    /**
     * Starts [intent] on the second panel.
     *
     * @return true when the activity accepted it. False means there was nothing
     *   to start from, or the start was refused — either way the caller should
     *   fall back rather than report a failure.
     */
    fun start(intent: Intent): Boolean {
        val activity = live ?: return false

        return runCatching {
            // NEW_TASK because this is a launch rather than a navigation, and the
            // new task lands on the starting activity's display.
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.onFailure { error ->
            ThorLog.w(TAG, "Second panel refused ${intent.component}", error)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "Launcher"
    }
}
