package com.thor.core.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.LauncherNotification
import com.thor.core.model.LauncherProfile
import com.thor.core.model.NotificationAccess
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * Who is signed in, and what the system is trying to tell them.
 *
 * Top right of the top screen, where a console puts both. Collapsed it is a
 * name, an avatar and a count; opened it slides a themed shade down over the
 * panel rather than letting Android's own shade cover the launcher, which on a
 * dual-screen device lands on the wrong screen entirely.
 */
@Composable
fun ProfileNotificationCluster(
    profile: LauncherProfile?,
    avatarPath: String?,
    access: NotificationAccess,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNotificationOpened: (String) -> Unit,
    onNotificationDismissed: (String) -> Unit,
    onDismissAll: () -> Unit,
    /**
     * Whether the header draws its own surface.
     *
     * False on the couch bar, which has a background of its own — a pill there
     * boxes something already inside a box. True over the information panel,
     * where it floats on artwork and would otherwise be unreadable.
     */
    surfaced: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val accent = profile?.let { Color(it.accentArgb) } ?: colors.cursor
    val notifications = (access as? NotificationAccess.Connected)?.notifications.orEmpty()

    Column(
        modifier = modifier.width(CLUSTER_WIDTH.dp),
        horizontalAlignment = Alignment.End,
    ) {
        ProfileClusterHeader(
            profile = profile,
            avatarPath = avatarPath,
            access = access,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
            surfaced = surfaced,
        )

        AnimatedVisibility(
            visible = expanded,
            // Expanding downward from the header is the shade metaphor; a fade
            // alone would read as a popup that happens to be underneath it.
            enter = expandVertically(tween(SHADE_MS)) + fadeIn(tween(SHADE_MS)),
            exit = shrinkVertically(tween(SHADE_MS)) + fadeOut(tween(SHADE_MS)),
        ) {
            NotificationShadePanel(
                profile = profile,
                access = access,
                onGrantAccess = onGrantAccess,
                onOpenAppInfo = onOpenAppInfo,
                onNotificationOpened = onNotificationOpened,
                onNotificationDismissed = onNotificationDismissed,
                onDismissAll = onDismissAll,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The collapsed half of the cluster: name, face and bell.
 *
 * Public because the two halves do not always live in the same place. The
 * information panel stacks them, which is what [ProfileNotificationCluster]
 * does. Couch mode cannot: its header belongs in a navigation bar of fixed
 * height, and a shade expanding inside that bar is a shade expanding into
 * nothing — it opened, it was clipped to the height of the row it sat in, and
 * from the sofa the bell simply did not work. There the header stays in the bar
 * and [NotificationShadePanel] is drawn over the screen beneath it.
 */
@Composable
fun ProfileClusterHeader(
    profile: LauncherProfile?,
    avatarPath: String?,
    access: NotificationAccess,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    surfaced: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val notifications = (access as? NotificationAccess.Connected)?.notifications.orEmpty()
    Box(modifier = modifier) {
        ClusterHeader(
            profile = profile,
            avatarPath = avatarPath,
            accent = profile?.let { Color(it.accentArgb) } ?: ThorTheme.colors.cursor,
            count = notifications.count(LauncherNotification::isClearable),
            granted = access !is NotificationAccess.Denied,
            expanded = expanded,
            surfaced = surfaced,
            onClick = onToggleExpanded,
        )
    }
}

/** The opened half. See [ProfileClusterHeader] for why it is separable. */
@Composable
fun NotificationShadePanel(
    profile: LauncherProfile?,
    access: NotificationAccess,
    onGrantAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNotificationOpened: (String) -> Unit,
    onNotificationDismissed: (String) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NotificationShade(
        access = access,
        accent = profile?.let { Color(it.accentArgb) } ?: ThorTheme.colors.cursor,
        onGrantAccess = onGrantAccess,
        onOpenAppInfo = onOpenAppInfo,
        onNotificationOpened = onNotificationOpened,
        onNotificationDismissed = onNotificationDismissed,
        onDismissAll = onDismissAll,
        modifier = modifier,
    )
}

@Composable
private fun ClusterHeader(
    profile: LauncherProfile?,
    avatarPath: String?,
    accent: Color,
    count: Int,
    granted: Boolean,
    expanded: Boolean,
    surfaced: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors

    /**
     * The header, with or without a surface under it.
     *
     * On the information panel it is a pill floating over artwork and needs one.
     * On the couch bar it sits on the bar's own background, and a second surface
     * there draws a box around something already inside a box.
     */
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /*
             * Name, face, bell — or bell, name, face when the header has a
             * surface of its own.
             *
             * On a pill the bell leads, so the count sits inside the shape
             * rather than on its rounded end. On the couch bar there is no pill:
             * the picture is the thing the eye goes to, and the bell belongs
             * beside it as its companion rather than a screen-width away.
             */
            if (surfaced) {
                NotificationBell(
                    count = count,
                    granted = granted,
                    accent = accent,
                    open = expanded,
                )
            }
            Text(
                text = profile?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            ProfileAvatar(profile = profile, avatarPath = avatarPath, accent = accent, size = 28)
            if (!surfaced) {
                NotificationBell(
                    count = count,
                    granted = granted,
                    accent = accent,
                    open = expanded,
                )
            }
        }
    }

    // The one control in the couch bar's corner, and the only way to the shade.
    // It lights under the pointer like everything else the cursor can press.
    val hover = rememberPointerHover()
    val shape = ThorTheme.shapes.pill
    if (surfaced) {
        GlassSurface(
            // Same width as the shade below it: the two are one control, and a
            // pill narrower than the panel it opens reads as a button that
            // happens to sit above an unrelated box.
            modifier = Modifier
                .fillMaxWidth()
                .pointerHover(hover)
                .clickable(onClick = onClick),
            shape = shape,
            level = SurfaceLevel.RAISED,
        ) {
            row()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (hover.isHovered) {
                        colors.surfaceHighest.copy(alpha = 0.6f)
                    } else {
                        Color.Transparent
                    },
                )
                .pointerHover(hover)
                .clickable(onClick = onClick),
        ) { row() }
    }
}

/**
 * The bell, and the count on it.
 *
 * Crossed out when access has not been granted, which is the one state where
 * a zero count means "cannot know" rather than "nothing waiting" — showing a
 * plain bell there would say all is quiet when the launcher has no idea.
 */
@Composable
private fun NotificationBell(count: Int, granted: Boolean, accent: Color, open: Boolean) {
    val colors = ThorTheme.colors
    Box(contentAlignment = Alignment.TopEnd) {
        Icon(
            imageVector = when {
                open -> Icons.Rounded.Close
                granted -> Icons.Rounded.Notifications
                else -> Icons.Rounded.NotificationsOff
            },
            contentDescription = if (granted) "Notifications" else "Notification access off",
            tint = if (granted) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        if (count > 0 && !open) {
            Box(
                modifier = Modifier
                    .size(BADGE_SIZE.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    profile: LauncherProfile?,
    avatarPath: String?,
    accent: Color,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    // The user's corner setting reaches the face too. A launcher squared
    // everywhere else with one circle left in the corner of the bar is exactly
    // the inconsistency [ThorShapes] exists to remove.
    val shape = ThorTheme.shapes.pill
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(accent.copy(alpha = .22f))
            .border(1.5.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarPath != null) {
            AsyncImage(
                model = avatarPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(shape),
            )
        } else {
            Text(
                text = profile?.initial ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun NotificationShade(
    access: NotificationAccess,
    accent: Color,
    onGrantAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onNotificationOpened: (String) -> Unit,
    onNotificationDismissed: (String) -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ThorTheme.shapes.panel,
        level = SurfaceLevel.RAISED,
    ) {
        Column(modifier = Modifier.padding(SHADE_PADDING.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NOTIFICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (access is NotificationAccess.Connected && access.notifications.any { it.isClearable }) {
                    val clearHover = rememberPointerHover()
                    Text(
                        text = "CLEAR ALL",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (clearHover.isHovered) colors.onSurface else colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(ThorTheme.shapes.pill)
                            .background(
                                if (clearHover.isHovered) {
                                    colors.surfaceHighest
                                } else {
                                    Color.Transparent
                                },
                            )
                            .pointerHover(clearHover)
                            .clickable(onClick = onDismissAll)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(7.dp))

            when (access) {
                NotificationAccess.Denied -> AccessPrompt(
                    accent = accent,
                    onGrant = onGrantAccess,
                    onOpenAppInfo = onOpenAppInfo,
                )
                NotificationAccess.Connecting -> ShadeMessage(
                    title = "Connecting",
                    detail = "Waiting for Android to hand over the notification feed.",
                )
                is NotificationAccess.Connected -> if (access.notifications.isEmpty()) {
                    ShadeMessage(title = "All clear", detail = "Nothing is waiting for you.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = SHADE_MAX_HEIGHT.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        access.notifications.forEach { notification ->
                            NotificationRow(
                                notification = notification,
                                accent = accent,
                                onOpen = { onNotificationOpened(notification.key) },
                                onDismiss = { onNotificationDismissed(notification.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The prompt, shown in place of the list until access is granted.
 *
 * Notification access is a special permission: there is no runtime request for
 * it and no dialog an app can raise, only a page in Android's settings the user
 * has to visit. So this states what is missing, what it is for, and opens that
 * page — anything less leaves an empty panel that looks broken.
 *
 * The second half is about restricted settings. Android 13 refuses this
 * permission outright to anything installed from outside a store and says only
 * "currently disabled for security" on the page it just sent the user to, with
 * no hint that the unlock is an overflow item on App info. A launcher is
 * sideloaded by definition, so that is not an edge case here — it is what
 * happens to everybody the first time, and the panel had better say so.
 */
@Composable
private fun AccessPrompt(accent: Color, onGrant: () -> Unit, onOpenAppInfo: () -> Unit) {
    val colors = ThorTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Notification access is off",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Loki cannot read notifications until Android grants access. " +
                "Turn it on to see downloads, messages and system alerts here " +
                "instead of pulling down Android's own shade.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        PromptButton(label = "GRANT ACCESS", background = accent, onClick = onGrant)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline.copy(alpha = .3f)),
        )

        Text(
            text = "Says \"disabled for security\"?",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Android blocks this permission for apps installed outside the " +
                "Play Store. Open App info, tap the ⋮ menu at the top right, choose " +
                "\"Allow restricted settings\", then come back and grant access.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        PromptButton(
            label = "OPEN APP INFO",
            background = colors.surfaceHighest,
            content = colors.onSurface,
            onClick = onOpenAppInfo,
        )
    }
}

@Composable
private fun PromptButton(
    label: String,
    background: Color,
    content: Color = Color.White,
    onClick: () -> Unit,
) {
    val hover = rememberPointerHover()
    Box(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(background)
            .then(
                if (hover.isHovered) {
                    Modifier.border(2.dp, ThorTheme.colors.cursor, ThorTheme.shapes.pill)
                } else {
                    Modifier
                },
            )
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ShadeMessage(title: String, detail: String) {
    val colors = ThorTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationRow(
    notification: LauncherNotification,
    accent: Color,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ThorTheme.colors
    // Lit under the pointer. From a sofa the shade is a list of small targets
    // with no cursor of its own, so without this there is nothing to say which
    // notification a click is about to open.
    val hover = rememberPointerHover()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(if (hover.isHovered) colors.surfaceHighest else colors.surfaceElevated)
            .pointerHover(hover)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // A bar rather than an app icon: loading one per row means a package
        // manager lookup and a bitmap decode for every notification on screen.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(NOTIFICATION_BAR_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(if (notification.isAlerting) accent else colors.outline),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.appLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = NOTIFICATION_BODY_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (notification.isClearable) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clip(ThorTheme.shapes.pill)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

/**
 * Half what it was, header and shade together.
 *
 * The two are deliberately one number: the collapsed pill and the panel it
 * opens are a single control, and a pill narrower than its own panel reads as a
 * button sitting above an unrelated box.
 */
private const val CLUSTER_WIDTH = 196
private const val SHADE_PADDING = 10
private const val SHADE_MAX_HEIGHT = 290
private const val SHADE_MS = 220
private const val BADGE_SIZE = 15
private const val BADGE_MAX = 9
private const val NOTIFICATION_BAR_HEIGHT = 30
private const val NOTIFICATION_BODY_LINES = 2

