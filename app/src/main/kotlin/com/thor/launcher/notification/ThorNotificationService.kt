package com.thor.launcher.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.thor.core.common.log.ThorLog
import com.thor.core.model.DeviceNotification
import com.thor.data.notification.NotificationHub
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reads the device's notifications so the top screen can show them.
 *
 * A `NotificationListenerService` is the only route to this — there is no
 * permission an ordinary app can request — and the access is granted by the user
 * in system settings, exactly as the pointer's accessibility service is. THOR
 * therefore treats it the same way: off until granted, and never assumed.
 *
 * **Nothing is stored and nothing leaves the device.** The service flattens each
 * notification into a title, a body and the app that posted it, hands that to
 * [NotificationHub] in memory, and forgets it when unbound. It reads no message
 * contents beyond what is already drawn on the lock screen.
 */
@AndroidEntryPoint
class ThorNotificationService : NotificationListenerService() {

    @Inject lateinit var hub: NotificationHub

    /**
     * The system creates this and calls back on its own schedule.
     *
     * `onListenerConnected` is the only point at which `activeNotifications` is
     * safe to read — before it, the binder is not ready and the call throws. It
     * is also the moment to take the current state wholesale, because
     * notifications posted while the service was unbound produce no callback and
     * would otherwise be invisible until they changed.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        hub.onDismiss = { key -> runCatching { cancelNotification(key) } }
        hub.onDismissAll = { runCatching { cancelAllNotifications() } }
        hub.setConnected(true)
        republish()
        ThorLog.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        /*
         * Cleared, not left pointing at a dead service.
         *
         * The hub is a singleton and outlives every binding, so a callback left
         * behind would keep this instance reachable and would try to cancel
         * notifications through a binder the system has already taken away.
         */
        hub.onDismiss = null
        hub.onDismissAll = null
        hub.setConnected(false)
        ThorLog.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = republish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = republish()

    /**
     * Re-reads the whole set rather than tracking individual changes.
     *
     * A list of twenty is nothing to rebuild, and the alternative — maintaining
     * a map against posted and removed callbacks — drifts the first time the
     * system delivers a change THOR does not model, such as a notification being
     * updated in place or a group summary collapsing. The platform already holds
     * the truth; asking it is cheaper than mirroring it.
     */
    private fun republish() {
        val current = runCatching { activeNotifications }
            .onFailure { ThorLog.w(TAG, "Could not read notifications", it) }
            .getOrNull()
            ?: return

        hub.publish(current.mapNotNull(::toDomain))
    }

    private fun toDomain(sbn: StatusBarNotification): DeviceNotification? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            )?.toString().orEmpty()

        /*
         * An entry with neither a title nor a body has nothing to show.
         *
         * Media sessions and some foreground services post these purely to hold
         * a slot in the shade; drawn here they would be blank rows the user
         * cannot act on or dismiss.
         */
        if (title.isBlank() && text.isBlank()) return null

        return DeviceNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appLabel(sbn.packageName),
            title = title,
            text = text,
            postedAtMs = sbn.postTime,
            isClearable = sbn.isClearable,
            /*
             * Ranked below the rest rather than hidden.
             *
             * A silent notification is one the system decided not to interrupt
             * for — sync status, a slow charger — which is a statement about
             * urgency, not about whether it is worth seeing.
             */
            isSilent = notification.priority <= Notification.PRIORITY_LOW,
        )
    }

    /**
     * The app's name, as the user knows it.
     *
     * Falls back to the package, which is ugly but honest: a notification from an
     * app that has just been uninstalled still has a key and still needs a row
     * until the system removes it.
     */
    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()?.takeIf(String::isNotBlank) ?: packageName

    private companion object {
        const val TAG = "Notifications"
    }
}
