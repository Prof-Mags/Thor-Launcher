package com.thor.data.launcher

import android.app.Activity
import android.content.Intent
import com.thor.core.common.log.ThorLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * What was last sent to this panel, so relaunching it reuses its task.
     *
     * A component name rather than a reference: nothing is kept alive by it, and
     * the worst a stale value can do is cost one extra task the first time after
     * the user has closed the app themselves.
     */
    private var lastStarted: String? = null

    /**
     * Observable so a launch can *wait* for this activity rather than fail
     * without it.
     *
     * The system destroys and recreates this freely to reclaim memory, and it is
     * uncovered — and therefore recreated — the moment THOR's presentation stands
     * down. A second-screen launch that merely checked and gave up would work or
     * not depending on whether the system had happened to reclaim it, which is
     * exactly the "sometimes" the user sees.
     */
    private val _available = MutableStateFlow(false)

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
        _available.value = true
    }

    /** Only clears when [activity] is the one currently held, not a newer one. */
    fun detach(activity: Activity) {
        if (reference?.get() === activity) {
            reference = null
            _available.value = false
        }
    }

    /**
     * The activity, if there is still one — and correcting the flag if not.
     *
     * The reference is weak and the activity is destroyed freely, so it can go
     * away without [detach] ever running: a process-level reclaim, a
     * configuration change the system handles by dropping it, or simply the
     * garbage collector taking a cleared reference. `_available` was then left
     * saying `true` forever.
     *
     * That is the fault behind second-panel launches degrading after a few
     * minutes of use. [awaitAvailable] exists to *wait* for the activity to come
     * back, and it starts by looking at this flag — so a stale `true` made it
     * return immediately and report failure instead of waiting, and the caller
     * fell back to the display-id route, which starts the app without bringing
     * it forward. The launch "worked" and landed in the background.
     *
     * Reconciling here means the flag can never outlive the thing it describes.
     */
    private val live: Activity?
        get() {
            val activity = reference?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            if (activity == null && _available.value) {
                ThorLog.i(TAG, "Second panel host went away without detaching")
                reference = null
                _available.value = false
            }
            return activity
        }

    val isAvailable: Boolean get() = live != null

    /**
     * Waits for the second panel's activity to exist, up to [timeoutMs].
     *
     * Called after the presentation has been stood down, which is what uncovers
     * this activity and prompts the system to recreate it if it had been
     * reclaimed. Waiting turns "the second screen works unless the system
     * happened to reclaim the activity" into "the second screen works".
     *
     * @return true when there is something to launch from.
     */
    suspend fun awaitAvailable(timeoutMs: Long): Boolean {
        // Reading `isAvailable` first also reconciles a stale flag, so the wait
        // below cannot be satisfied instantly by a value describing an activity
        // that no longer exists.
        if (isAvailable) return true

        return withTimeoutOrNull(timeoutMs) {
            /*
             * Waits for a *fresh* attach rather than for the flag to be true.
             *
             * `first { it }` returns at once on a `true` still sitting in the
             * flow, which is precisely the case this wait is for — so it is only
             * reached once the check above has proven there is no live activity
             * and cleared the flag.
             */
            _available.first { it }
            isAvailable
        } ?: false
    }

    /**
     * Starts [intent] on the second panel.
     *
     * @return true when the activity accepted it. False means there was nothing
     *   to start from, or the start was refused — either way the caller should
     *   fall back rather than report a failure.
     */
    fun start(intent: Intent): Boolean {
        val activity = live ?: return false

        /*
         * A second task only for a *different* app.
         *
         * `MULTIPLE_TASK` means "make a new task even if one exists", and it was
         * applied to every launch — so opening the same game five times left five
         * live tasks of it, each with its own process and memory, none of them
         * ever reused. That accumulates for as long as the launcher is up, which
         * is what makes a session get slower the longer it runs, and the memory
         * pressure it creates is what prompts the system to reclaim the second
         * panel's own activity — the fault above.
         *
         * Relaunching what is already there uses a plain `NEW_TASK`, which brings
         * the existing task forward. It is on this display because this is where
         * it was started from, so the wrong-screen problem `MULTIPLE_TASK` was
         * added to solve does not arise for it.
         */
        val component = intent.component?.flattenToShortString()
        val alreadyHere = component != null && component == lastStarted
        lastStarted = component

        return runCatching {
            /*
             * Its own task, on this activity's display.
             *
             * Letting the app inherit this activity's task is what put it beyond
             * reach: this is the second display's *home* activity, declared
             * `excludeFromRecents`, and its task is a home task — which Android
             * omits from Recents on principle, flag or no flag. An app started
             * into it therefore had no entry to swipe away and no way to be
             * closed at all, short of Home and a relaunch.
             *
             * `NEW_TASK` alone would fix the listing but reintroduce the fault
             * that dropping it was meant to avoid: an app already running on the
             * *other* panel has a task there, and the system satisfies a plain
             * `NEW_TASK` by bringing that one forward on the display it is
             * already on — so the game appears on the wrong screen while the
             * launcher stands the right one down for it.
             *
             * `MULTIPLE_TASK` is the documented answer to exactly that, and its
             * documented caveat — "do not use unless you are implementing your
             * own top-level application launcher" — describes this app. Together
             * they mean: a new task, here, on this display, listed in Recents.
             *
             * An app whose own manifest asks for `singleTask` or `singleInstance`
             * still gets what it asked for; the target's launch mode outranks the
             * caller's flags, and that is the correct precedence.
             */
            activity.startActivity(
                if (alreadyHere) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } else {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                    )
                },
            )
            true
        }.onFailure { error ->
            ThorLog.w(TAG, "Second panel refused ${intent.component}", error)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "Launcher"
    }
}
