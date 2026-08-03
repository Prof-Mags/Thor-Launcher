package com.thor.core.model

/**
 * One posted notification, flattened to what the panel draws.
 *
 * A snapshot rather than a handle on the live `StatusBarNotification`: those
 * carry a `Notification` holding bitmaps and pending intents, and keeping a list
 * of them alive holds that memory for as long as the panel is open. [key] is
 * enough to act on the original.
 */
data class LauncherNotification(
    /** The system's key, used to dismiss or open the original. */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    /** False for ongoing notifications — a running download, a foreground service. */
    val isClearable: Boolean = true,
    /** True when the notification demanded attention rather than merely appearing. */
    val isAlerting: Boolean = false,
) {
    /** Notifications with neither title nor body are chrome, not content. */
    val hasContent: Boolean get() = title.isNotBlank() || text.isNotBlank()
}

/**
 * What the notification panel can currently show.
 *
 * The three states are genuinely different screens: without the listener
 * permission there is nothing to show and an action to offer, with it and no
 * notifications there is an empty state, and otherwise there is a list.
 */
sealed interface NotificationAccess {
    /** The listener permission has not been granted. */
    data object Denied : NotificationAccess

    /** Granted, but the service has not connected yet. */
    data object Connecting : NotificationAccess

    data class Connected(val notifications: List<LauncherNotification>) : NotificationAccess
}
