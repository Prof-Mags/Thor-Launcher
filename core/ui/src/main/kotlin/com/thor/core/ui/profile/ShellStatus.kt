package com.thor.core.ui.profile

import androidx.compose.runtime.Immutable
import com.thor.core.model.LauncherProfile
import com.thor.core.model.NotificationAccess

/**
 * What the top screen's corner shows about the session itself.
 *
 * Grouped rather than passed as five more parameters: they are read together,
 * change together, and `TopScreen` already carries a dozen arguments.
 */
@Immutable
data class ShellStatus(
    val profile: LauncherProfile?,
    val avatarPath: String?,
    val notifications: NotificationAccess,
    val shadeOpen: Boolean,
)

/**
 * What the corner can do.
 *
 * Immutable so Compose can skip the cluster when only the library changed —
 * a bare bag of lambdas is unstable, and recomposes it on every frame the grid
 * moves.
 */
@Immutable
class ShellStatusActions(
    val onToggleShade: () -> Unit = {},
    val onGrantAccess: () -> Unit = {},
    val onOpenAppInfo: () -> Unit = {},
    val onNotificationOpened: (String) -> Unit = {},
    val onNotificationDismissed: (String) -> Unit = {},
    val onDismissAll: () -> Unit = {},
)
