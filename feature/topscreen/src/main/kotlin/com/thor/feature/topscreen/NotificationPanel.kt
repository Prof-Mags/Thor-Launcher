package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.DeviceNotification
import java.util.concurrent.TimeUnit

/**
 * The device's notifications, on the top screen.
 *
 * Opened deliberately rather than pulled down. The top screen is the one the user
 * does not touch — it shows what the cursor is resting on while both thumbs are
 * on the controller — so a swipe-down gesture would be the one interaction in the
 * launcher that required reaching across the device.
 *
 * Read-only apart from dismissal. THOR does not reply to messages or act on
 * notifications; it shows what is waiting and gets out of the way, which is what
 * a launcher is for.
 */
@Composable
fun NotificationPanel(
    notifications: List<DeviceNotification>,
    /** Whether the listener is bound; see the hub's own note on why this differs. */
    connected: Boolean,
    onDismiss: (String) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimens.spacing),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))

            /*
             * Only offered when there is something it can actually clear.
             *
             * Ongoing notifications refuse dismissal, so a "clear all" shown
             * against a list of nothing but those would appear to do nothing.
             */
            if (notifications.any(DeviceNotification::isClearable)) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    modifier = Modifier
                        .clip(ThorTheme.shapes.pill)
                        .clickable(onClick = onDismissAll)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        when {
            /*
             * Not granted and nothing waiting look identical from here, and they
             * need opposite things from the user — so the panel says which.
             */
            !connected -> Message(
                icon = Icons.Rounded.NotificationsOff,
                title = "THOR cannot read notifications yet",
                detail = "Settings → System → Notifications, then grant access in the " +
                    "system list that opens.",
            )

            notifications.isEmpty() -> Message(
                icon = Icons.Rounded.Notifications,
                title = "Nothing waiting",
                detail = "New notifications appear here as they arrive.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                items(notifications, key = DeviceNotification::key) { notification ->
                    NotificationRow(
                        notification = notification,
                        onDismiss = { onDismiss(notification.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: DeviceNotification,
    onDismiss: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.small

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .thorSurface(shape = shape, color = colors.surface, level = SurfaceLevel.RAISED)
            .padding(horizontal = dimens.spacing, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        // A dot in the app's place rather than its icon: loading a package icon
        // per row costs a PackageManager call each, and the app's name is
        // already the first thing on the row.
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (notification.isSilent) colors.onSurfaceVariant else colors.cursor,
                ),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " · ${notification.postedAtMs.asAge()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            Text(
                text = notification.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.hasBody) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    // Two lines: enough for a message, short enough that one
                    // chatty app cannot push everything else off the panel.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        /*
         * Shown only where dismissal will work.
         *
         * A media session or a running download refuses to be cancelled, and a
         * cross that springs the row back is worse than no cross at all.
         */
        if (notification.isClearable) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun Message(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
            modifier = Modifier.padding(top = dimens.spacing),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = dimens.spacingSmall),
        )
    }
}

/**
 * "4m", "2h", "3d" — how long a notification has been waiting.
 *
 * Deliberately not a clock time: what matters on a glanceable panel is whether
 * something arrived just now or has been sitting there since yesterday, and a
 * relative figure answers that without the reader doing arithmetic.
 */
private fun Long.asAge(): String {
    val delta = System.currentTimeMillis() - this
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}
