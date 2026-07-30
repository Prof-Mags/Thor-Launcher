package com.thor.data.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports and requests default-launcher status.
 *
 * Android gives no API to *become* the home app — that decision belongs to the
 * user. What an app can do is ask the system to show the chooser, which is what
 * [requestDefault] does. Anything that claimed to set it directly would be
 * lying about what the button does.
 */
@Singleton
class DefaultLauncherManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True when THOR is already the system's home app. */
    fun isDefault(): Boolean {
        val resolved = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Opens the system's home-app chooser.
     *
     * Tries the dedicated home settings page first, because it names the choice
     * plainly. Older or reduced builds may not expose it, so the fallback is to
     * fire a HOME intent with no default set — which makes the system show its
     * own "which app should open this?" chooser.
     */
    fun requestDefault(): Boolean {
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (canResolve(homeSettings)) {
            return runCatching { context.startActivity(homeSettings) }
                .onFailure { ThorLog.w(TAG, "Home settings refused to open", it) }
                .isSuccess
        }

        // The chooser only appears when there is no current default, so this is
        // a genuine fallback rather than an equivalent path.
        val chooser = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching { context.startActivity(chooser) }
            .onFailure { ThorLog.w(TAG, "Could not present a home chooser", it) }
            .isSuccess
    }

    private fun canResolve(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private companion object {
        const val TAG = "DefaultLauncher"
    }
}
