package com.thor.data.notification

import com.thor.core.model.DeviceNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the device is currently notifying about, and the way to act on it.
 *
 * Sits between the listener service — which the system creates, destroys and
 * rebinds on its own schedule — and the launcher, which wants a list it can
 * render. The service pushes; everything else reads.
 *
 * Deliberately holds no `Context` and no platform notification objects, so a
 * service that has been torn down leaves nothing behind but stale data, and the
 * next bind replaces it wholesale.
 */
@Singleton
class NotificationHub @Inject constructor() {

    private val _active = MutableStateFlow<List<DeviceNotification>>(emptyList())

    /**
     * Everything on screen, newest first, with the quiet ones last.
     *
     * Sorted here rather than in the panel so every reader agrees on the order —
     * the badge counts what the list shows, and a panel that ranked them
     * differently from the count would be describing two different sets.
     */
    val active: StateFlow<List<DeviceNotification>> = _active.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /**
     * Whether the listener is actually bound.
     *
     * Distinct from having been granted permission, and the distinction matters:
     * the grant survives, the binding does not. A service can be permitted and
     * still not running — after an update, a force stop, or a ROM that drops it
     * on reboot — and a panel that trusted the grant would sit empty claiming
     * there was nothing to show.
     */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Set by the service on connect and disconnect. Nothing else may call it. */
    fun setConnected(connected: Boolean) {
        _connected.value = connected
        // Whatever was listed belonged to the old binding; keeping it would show
        // notifications that may since have been dismissed elsewhere.
        if (!connected) _active.value = emptyList()
    }

    /** Replaces the whole list, which is how the service reports every change. */
    fun publish(notifications: List<DeviceNotification>) {
        _active.value = notifications.sortedWith(
            compareBy<DeviceNotification> { it.isSilent }
                .thenByDescending { it.postedAtMs },
        )
    }

    /**
     * How the panel asks for one to be dismissed.
     *
     * A callback registered by the service rather than a direct call, because
     * only the service can cancel a notification and only while it is bound —
     * and this object outlives it.
     */
    @Volatile
    var onDismiss: ((key: String) -> Unit)? = null

    @Volatile
    var onDismissAll: (() -> Unit)? = null

    fun dismiss(key: String) {
        onDismiss?.invoke(key)
    }

    fun dismissAll() {
        onDismissAll?.invoke()
    }

    /** How many are worth badging: the ones a person would want to know about. */
    val badgeCount: Int get() = _active.value.count { !it.isSilent }
}
