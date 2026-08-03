package com.thor.launcher.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.thor.core.common.log.ThorLog
import com.thor.core.model.LauncherNotification
import com.thor.data.notification.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Mirrors the system shade into the launcher's own panel.
 *
 * Bound by the system once the user grants notification access in Android's
 * settings; there is no way for an app to grant it to itself, which is why the
 * panel has a prompt rather than a permission request.
 *
 * Every callback republishes the whole list rather than applying a delta.
 * [getActiveNotifications] is the system's own answer to "what is showing", and
 * a locally maintained list drifts from it — a notification updated in place, a
 * group summary collapsing, a dismissal from another surface.
 */
@AndroidEntryPoint
class LokiNotificationListener : NotificationListenerService(), NotificationRepository.Host {

    @Inject lateinit var repository: NotificationRepository

    private val labels = mutableMapOf<String, String>()

    override fun onListenerConnected() {
        repository.attach(this)
        republish()
    }

    override fun onListenerDisconnected() {
        repository.detach(this)
    }

    override fun onDestroy() {
        repository.detach(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = republish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = republish()

    override fun dismiss(key: String) {
        runCatching { cancelNotification(key) }
            .onFailure { ThorLog.w(TAG, "Could not dismiss $key", it) }
    }

    override fun dismissAll() {
        // Only the clearable ones: cancelAll would also try to take down ongoing
        // notifications, which the system refuses, and an ignored request looks
        // to the user like the button did nothing.
        val clearable = runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .filter(StatusBarNotification::isClearable)
            .map(StatusBarNotification::getKey)
            .toTypedArray()
        if (clearable.isNotEmpty()) {
            runCatching { cancelNotifications(clearable) }
                .onFailure { ThorLog.w(TAG, "Could not clear notifications", it) }
        }
    }

    override fun open(key: String) {
        val sbn = runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .firstOrNull { it.key == key } ?: return
        val intent = sbn.notification?.contentIntent ?: return
        runCatching { intent.send() }
            .onFailure { ThorLog.w(TAG, "Could not open $key", it) }
        if (sbn.isClearable) dismiss(key)
    }

    private fun republish() {
        val active = runCatching { activeNotifications.orEmpty() }
            .getOrElse { error ->
                // Thrown when the binding has gone away underneath us; the system
                // will reconnect and republish, so this is not worth surfacing.
                ThorLog.w(TAG, "Notification list unavailable", error)
                return
            }
        repository.publish(active.mapNotNull(::toLauncherNotification))
    }

    private fun toLauncherNotification(sbn: StatusBarNotification): LauncherNotification? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras
        return LauncherNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = labelFor(sbn.packageName),
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAtEpochMs = sbn.postTime,
            isClearable = sbn.isClearable,
            // Anything above the default priority asked to interrupt; the panel
            // marks those rather than reordering, so the shade's order is kept.
            isAlerting = notification.priority > Notification.PRIORITY_DEFAULT,
        )
    }

    /**
     * The app's display name, cached.
     *
     * Resolving a label goes to the package manager, and this runs for every
     * notification on every posted callback — on a busy device that is hundreds
     * of lookups a minute for a set of packages that barely changes.
     */
    private fun labelFor(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val manager = packageManager
            manager.getApplicationLabel(
                manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA),
            ).toString()
        }.getOrDefault(packageName)
    }

    private companion object {
        const val TAG = "NotificationListener"
    }
}
