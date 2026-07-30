package com.thor.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.thor.core.common.log.ThorLog
import com.thor.core.display.hideSystemBars
import com.thor.data.launcher.HomeRequests
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * THOR's home activity for the *second* panel.
 *
 * It exists because of how Android routes Home. `CATEGORY_HOME` is delivered per
 * display: press Home while the second panel is the focused display and the system
 * starts that display's **secondary** home activity. THOR declared only a primary
 * home, so the system used its own default there — and the stock launcher appeared on
 * the panel THOR had just handed to an app. Pressing Home again did nothing, because
 * the launcher had never been told.
 *
 * This claims that role. It draws nothing and does nothing except report the press,
 * so the running launcher can take its panel back. Deliberately *not* a second copy
 * of the launcher UI: the second panel is a `Presentation` owned by the main activity,
 * and two independent launchers on one device would be two of everything — two view
 * models, two cursors, two ideas of which cell is selected.
 *
 * It stays alive rather than finishing, and that is the other half of the fix: while
 * it sits there, it is the thing behind THOR's own presentation on that display. When
 * the presentation stands down for a launched app, what is uncovered is this — a
 * black panel belonging to THOR — rather than somebody else's home screen.
 */
@AndroidEntryPoint
class SecondaryHomeActivity : ComponentActivity() {

    @Inject lateinit var homeRequests: HomeRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars(window)

        /*
         * The *first* creation in a process is the system placing a home activity on
         * the panel as it attaches, which is not a Home press and must not reset the
         * launcher out from under the user. Every creation after that is a real press
         * arriving at an instance the system had destroyed to reclaim memory.
         *
         * A process-scoped flag is the right lifetime for exactly the reason the
         * cold-start intro uses one: "has this happened yet in this process" is the
         * question, and a killed process has no launcher state left to disturb.
         */
        if (createdOnce) {
            report(intent)
        } else {
            createdOnce = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // `singleTop`, and a home activity sits at the top of its display's home
        // task — so a Home press while this is already that display's home arrives
        // here rather than creating anything. Kept as the activity's current intent
        // so anything reading it later sees the one that actually arrived.
        setIntent(intent)
        report(intent)
    }

    /**
     * Reports a genuine Home press.
     *
     * **`CATEGORY_SECONDARY_HOME`, not `CATEGORY_HOME`** — and getting that wrong is
     * why Home on the second panel did nothing at all for so long.
     *
     * Android does not deliver the same intent to both home roles. A press on the
     * default display resolves through `getHomeIntent()`, which carries
     * `CATEGORY_HOME`; a press on a secondary display resolves through
     * `getSecondaryHomeIntent()`, which carries `CATEGORY_SECONDARY_HOME` instead.
     * This activity only ever receives the second kind — it is registered for no
     * other — so a check for `CATEGORY_HOME` could never once be true. The press was
     * received, the activity was started, and the running launcher was never told,
     * which from the front is a Home button that does nothing on one screen.
     *
     * Both are accepted because it costs nothing and some ROMs are not fussy about
     * which they send.
     */
    private fun report(intent: Intent?) {
        val categories = intent?.categories ?: return
        val isHome = Intent.CATEGORY_SECONDARY_HOME in categories ||
            Intent.CATEGORY_HOME in categories
        if (isHome) {
            ThorLog.d(TAG) { "Home pressed on the secondary panel" }
            homeRequests.onHomePressed()
        }
    }

    private companion object {
        const val TAG = "SecondaryHome"

        /**
         * Whether this activity has been created before in this process.
         *
         * Static on purpose: the system destroys and recreates this activity freely,
         * and the question being asked is about the process, not the instance.
         */
        @Volatile
        var createdOnce = false
    }
}
