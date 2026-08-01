package com.thor.core.model

/**
 * One notification the system is currently showing.
 *
 * A flattened snapshot rather than Android's `StatusBarNotification`, which is a
 * platform type carrying a live `Notification` and a binder handle. Keeping that
 * out of the model means the UI layer holds nothing the system can revoke
 * underneath it, and the panel can be rendered from a plain list.
 *
 * The [key] is the system's own, and is what dismissal is addressed to — not the
 * package, and not the id, because one app posts many and they are only unique
 * together.
 */
data class DeviceNotification(
    val key: String,
    val packageName: String,
    /** The app's own label, resolved once when the notification arrives. */
    val appName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    /**
     * Whether the user is allowed to swipe it away.
     *
     * Ongoing notifications — a running download, a media session, a foreground
     * service — refuse dismissal, and offering it anyway produces a row that
     * springs back. Better to show it as permanent.
     */
    val isClearable: Boolean,
    /**
     * Whether this is a silent, low-priority entry.
     *
     * Sync status, "charging slowly", keyboard switchers. Worth showing but not
     * worth ranking above a message, so the panel keeps them below the rest.
     */
    val isSilent: Boolean,
) {
    /** What to show when an app posts a notification with no title of its own. */
    val displayTitle: String get() = title.ifBlank { appName }

    /** Something to show for an entry whose text is empty — rare but not absent. */
    val hasBody: Boolean get() = text.isNotBlank()
}
