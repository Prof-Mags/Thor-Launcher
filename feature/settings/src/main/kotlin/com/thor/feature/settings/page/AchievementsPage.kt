package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.ThorSettings
import com.thor.data.achievements.AchievementSyncState
import com.thor.data.achievements.RetroAchievementsStatus
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.TextFieldRow
import com.thor.feature.settings.SettingsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * RetroAchievements: the account, and what to do with it.
 *
 * The credentials come last on the page rather than first, which is the order
 * every other integration here uses — the thing the user came to do is at the
 * top, and the fields they filled in months ago are below it.
 */
@Composable
internal fun AchievementsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    syncState: AchievementSyncState,
    status: RetroAchievementsStatus?,
    checking: Boolean,
) {
    val account = settings.retroAchievements

    SwitchRow(
        title = "RetroAchievements",
        subtitle = if (account.isConfigured) {
            "Show achievement progress on the game panel"
        } else {
            "Add your username and web API key below to switch this on"
        },
        checked = account.enabled,
        focused = focusedRow == 0,
        onCheckedChange = { on -> viewModel.updateRetroAchievements { it.copy(enabled = on) } },
    )

    RowDivider()
    ActionRow(
        title = "Match my library",
        subtitle = when (syncState) {
            is AchievementSyncState.Running ->
                if (syncState.total > 0) {
                    "Matching ${syncState.done} of ${syncState.total}…"
                } else {
                    "Starting…"
                }

            is AchievementSyncState.Completed ->
                if (syncState.matched > 0) {
                    "${syncState.matched} games have achievements"
                } else {
                    "No games matched an achievement set"
                }

            is AchievementSyncState.Failed -> syncState.reason
            AchievementSyncState.Idle -> account.lastSyncedEpochMs
                ?.let { "Last matched ${formatWhen(it)}" }
                ?: "Find which of your games have achievement sets"
        },
        focused = focusedRow == 1,
        trailingLabel = if (syncState is AchievementSyncState.Running) "Cancel" else "Match",
        onClick = {
            if (syncState is AchievementSyncState.Running) {
                viewModel.cancelAchievementSync()
            } else {
                viewModel.syncAchievements()
            }
        },
    )

    RowDivider()
    ActionRow(
        title = "Check connection",
        subtitle = status.describe(),
        focused = focusedRow == 2,
        trailingLabel = if (checking) "Checking…" else "Check",
        onClick = viewModel::checkRetroAchievements,
    )

    RowDivider()
    SwitchRow(
        title = "Hardcore only",
        subtitle = "Count only achievements earned without save states or rewind",
        checked = account.hardcoreOnly,
        focused = focusedRow == 3,
        onCheckedChange = { on ->
            viewModel.updateRetroAchievements { it.copy(hardcoreOnly = on) }
        },
    )

    RowDivider()
    TextFieldRow(
        title = "Username",
        subtitle = "Your RetroAchievements account name",
        value = account.username,
        focused = focusedRow == 4,
        onValueChange = { value ->
            viewModel.updateRetroAchievements { it.copy(username = value.trim()) }
        },
    )

    RowDivider()
    TextFieldRow(
        title = "Web API key",
        subtitle = "Found on retroachievements.org under Settings → Keys. " +
            "This is not your password, and resetting it changes nothing else.",
        value = account.apiKey,
        focused = focusedRow == 5,
        isSecret = true,
        onValueChange = { value ->
            viewModel.updateRetroAchievements { it.copy(apiKey = value.trim()) }
        },
    )

    RowDivider()
    ActionRow(
        title = "Forget matches",
        subtitle = "Clears every stored match so the next run resolves from scratch",
        focused = focusedRow == 6,
        trailingLabel = "Forget",
        onClick = viewModel::forgetAchievementMatches,
    )

    /*
     * The limitation, said plainly and on the screen where it matters.
     *
     * RetroAchievements identifies a game by the hash of the ROM, with rules
     * that differ per console. Loki matches by title within the console instead,
     * which is right for a correctly named ROM and wrong for a hack, a
     * translation or an unusual regional release. Somebody whose game shows no
     * achievements deserves to know why before they go looking for a bug.
     */
    RowDivider()
    InfoRow(
        "How games are matched",
        "By title, within the game's own console. The site matches by ROM hash, " +
            "which Loki does not compute — so hacks, translations and unusually " +
            "named files will not be found even when they have achievements.",
    )
}

private fun RetroAchievementsStatus?.describe(): String = when (this) {
    null -> "Not checked yet"
    RetroAchievementsStatus.Connected -> "Connected"
    RetroAchievementsStatus.NotConfigured -> "Add a username and key below"
    RetroAchievementsStatus.InvalidCredentials -> "That username and key were refused"
    is RetroAchievementsStatus.Unreachable -> "Could not reach the site: $detail"
    is RetroAchievementsStatus.Error -> detail
}

private fun formatWhen(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

/** Enable, match, check, hardcore, two credentials, forget, and the note. */
internal const val ACHIEVEMENTS_ROWS = 7
