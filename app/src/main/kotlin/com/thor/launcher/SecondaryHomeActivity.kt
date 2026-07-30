package com.thor.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        report(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // `singleTask`, so a Home press while this is already the display's home
        // arrives here rather than creating anything.
        report(intent)
    }

    /**
     * Reports a genuine Home press.
     *
     * Only `CATEGORY_HOME` counts. This activity is also started by the system when
     * the display is first attached, and treating *that* as a Home press would reset
     * the launcher out from under the user every time a panel woke up.
     */
    private fun report(intent: Intent?) {
        if (intent?.categories?.contains(Intent.CATEGORY_HOME) == true) {
            homeRequests.onHomePressed()
        }
    }
}
