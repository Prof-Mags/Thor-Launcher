package com.thor.data.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether THOR may read notifications, and how to go and grant it.
 *
 * The same shape as the pointer service's manager, because it is the same kind
 * of thing: an access the user turns on in system settings, which THOR can ask
 * about but never award itself.
 */
@Singleton
class NotificationAccessManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Read from the secure setting rather than inferred from the service.
     *
     * A listener that has been granted but not yet bound looks identical to one
     * that was never granted if the only evidence is whether callbacks have
     * arrived — and the two need opposite things from the user. This answers
     * "are we allowed"; [NotificationHub.connected] answers "are we running".
     */
    fun isGranted(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_LISTENERS,
        ).orEmpty()

        /*
         * Matched on the component, not on the package.
         *
         * The setting is a colon-separated list of flattened component names,
         * and a substring test on the package alone would report THOR as granted
         * when some *other* service of THOR's had been enabled — or, worse, when
         * an unrelated app's package merely contained the same text.
         */
        val us = ComponentName(context, LISTENER_CLASS)
        enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == us }
    }.onFailure { ThorLog.w(TAG, "Could not read notification access", it) }
        .getOrDefault(false)

    /**
     * Opens the system screen where the grant is made.
     *
     * There is no dialog for this and no way to request it in-app: the user has
     * to find THOR in a system list and turn it on, which is why the settings
     * page that calls this has to say so rather than simply offering a button.
     */
    fun openSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { ThorLog.w(TAG, "Could not open notification access settings", it) }
    }

    private companion object {
        const val TAG = "Notifications"
        const val ENABLED_LISTENERS = "enabled_notification_listeners"

        /**
         * Named as a string rather than by class reference.
         *
         * The service lives in the app module and this manager in `data`, which
         * the app module depends on and not the other way round. Referencing the
         * class directly would invert that.
         */
        const val LISTENER_CLASS = "com.thor.launcher.notification.ThorNotificationService"
    }
}
