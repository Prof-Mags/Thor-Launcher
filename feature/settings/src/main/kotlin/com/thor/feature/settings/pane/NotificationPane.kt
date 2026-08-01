package com.thor.feature.settings.pane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.ActionRow
import com.thor.feature.settings.component.InfoRow
import com.thor.feature.settings.component.RowDivider

/** How many rows [NotificationsPage] draws. */
internal const val NOTIFICATIONS_ROWS = 2

/**
 * Notification access, and what THOR does with it.
 *
 * Two rows and a lot of words, deliberately. This is the one permission in the
 * launcher that sounds alarming — "read your notifications" is the phrasing the
 * system uses — and a page that offered a button without saying what is read,
 * where it goes and how to take it back would be asking for trust it had not
 * earned.
 *
 * @param granted whether the system currently permits it
 * @param connected whether the service is actually bound, which is a different
 *   question: the grant survives an update and the binding does not
 */
@Composable
internal fun NotificationsPage(
    granted: Boolean,
    connected: Boolean,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            title = if (granted) "Notification access granted" else "Grant notification access",
            /*
             * Names the two states separately, because "granted but not running"
             * is real and looks identical to "working" from a settings screen.
             *
             * It happens after an update, a force stop, or on a ROM that drops
             * listeners at reboot — and the remedy is to toggle the switch off
             * and on again, which nobody guesses at from a page that only says
             * "granted".
             */
            subtitle = when {
                granted && connected ->
                    "Working. Notifications appear on the top screen."

                granted ->
                    "Granted, but the service is not running. Turn THOR off and on " +
                        "again in the system list to rebind it."

                else ->
                    "Opens the system's notification access list. Find THOR there and " +
                        "turn it on — there is no in-app prompt for this one."
            },
            focused = focusedRow == 0,
            onClick = viewModel::openNotificationAccessSettings,
        )
        RowDivider()

        InfoRow(
            title = "What THOR reads",
            value = "The app name, title and text of notifications already on your " +
                "lock screen, held in memory only. Nothing is written to disk and " +
                "nothing leaves the device. Revoking access in system settings stops " +
                "it immediately and clears what was held.",
        )
        RowDivider()
    }
}
