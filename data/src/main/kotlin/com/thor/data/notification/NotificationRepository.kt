package com.thor.data.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.thor.core.common.log.ThorLog
import com.thor.core.model.LauncherNotification
import com.thor.core.model.NotificationAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the notification shade knows, and the handle for acting on it.
 *
 * The listener service is owned by the system — it is created and destroyed on
 * the system's schedule, not the launcher's — so it registers a [Host] here
 * while it is connected rather than being injected anywhere. Everything else
 * talks to this, and gets an empty list when nothing is listening.
 */
@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Implemented by the listener service; the only route back to the system. */
    interface Host {
        fun dismiss(key: String)
        fun dismissAll()
        fun open(key: String)
    }

    private val posted = MutableStateFlow<List<LauncherNotification>>(emptyList())
    private val connected = MutableStateFlow(false)

    /**
     * Bumped to re-read the permission.
     *
     * Access is granted in Android's settings, so nothing tells the launcher it
     * happened. Binding the service does emit, which covers the common case, but
     * a user who grants access and comes straight back would otherwise sit on a
     * stale prompt for as long as the bind takes.
     */
    private val refreshes = MutableStateFlow(0)

    @Volatile
    private var host: Host? = null

    val notifications: StateFlow<List<LauncherNotification>> = posted.asStateFlow()

    /**
     * Whether the panel can show anything, and what to offer if it cannot.
     *
     * Access is re-read from the system rather than cached: it is granted in
     * Android's own settings, so the launcher is not told when it changes and
     * would otherwise keep showing the prompt after the user had said yes.
     */
    val access = combine(posted, connected, refreshes) { list, isConnected, _ ->
        when {
            !isListenerEnabled() -> NotificationAccess.Denied
            !isConnected -> NotificationAccess.Connecting
            else -> NotificationAccess.Connected(list)
        }
    }

    /** Re-reads whether access has been granted. Cheap; a single settings lookup. */
    fun refresh() {
        refreshes.value += 1
    }

    val unreadCount = posted.map { list -> list.count(LauncherNotification::isClearable) }

    /** True when the user has granted notification access in Android's settings. */
    fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_LISTENERS,
        ).orEmpty()
        // The setting is a colon-separated list of flattened component names, so
        // a substring match on the package would also match another app whose
        // package merely contains ours.
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == context.packageName }
    }

    /** Opens the system page where the permission is granted. */
    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Opens this app's own App info page.
     *
     * Needed because of restricted settings. Android 13 refuses notification
     * listener and accessibility access to any app installed from outside a
     * store, and shows "currently disabled for security" on the very page
     * [accessSettingsIntent] opens — with no hint of what to do about it. The
     * unlock is an overflow item on App info, so sending the user there is the
     * only route through, and a launcher is sideloaded by definition.
     */
    fun appInfoIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun attach(host: Host) {
        this.host = host
        connected.value = true
        ThorLog.i(TAG, "Notification listener connected")
    }

    fun detach(host: Host) {
        // Guarded on identity: the system can create the replacement service
        // before tearing down the old one, and an unguarded detach would then
        // clear the new one's registration.
        if (this.host === host) {
            this.host = null
            connected.value = false
            posted.value = emptyList()
        }
    }

    fun publish(notifications: List<LauncherNotification>) {
        posted.value = notifications.filter(LauncherNotification::hasContent)
    }

    fun dismiss(key: String) = host?.dismiss(key) ?: Unit

    fun dismissAll() = host?.dismissAll() ?: Unit

    fun open(key: String) = host?.open(key) ?: Unit

    private companion object {
        const val TAG = "Notifications"
        const val ENABLED_LISTENERS = "enabled_notification_listeners"
    }
}
