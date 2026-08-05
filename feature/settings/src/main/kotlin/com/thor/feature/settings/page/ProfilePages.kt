package com.thor.feature.settings.page

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.thor.core.model.LauncherProfile
import com.thor.core.model.ProfileRegistry
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ColorRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.TextFieldRow
import com.thor.feature.settings.SettingsViewModel

/**
 * Who uses this device, and which of them is using it now.
 *
 * A profile owns its settings and its library outright, so switching here
 * replaces the entire launcher — theme, grid, games, play time. That is why the
 * page says so before it lists anybody: a switcher that looks like an account
 * picker but silently swaps the library is a nasty surprise the first time.
 */
@Composable
internal fun ProfilesPage(
    registry: ProfileRegistry,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val active = registry.active

    /*
     * Deletion is armed in place rather than confirmed by dialog.
     *
     * This page is driven through a flat list of rows by a D-pad; a dialog would
     * layer a second focus model over the first. Arming keeps one, and the armed
     * row states what is about to be destroyed — which for a profile is a whole
     * library, not a preference.
     */
    var armedForDelete by remember { mutableStateOf<String?>(null) }

    InfoRow(
        "Profiles",
        "Each profile keeps its own settings, its own library and its own play " +
            "time. Switching changes all of it at once — nothing is shared, and a " +
            "game added on one profile is not added to the others.",
    )
    RowDivider()

    var row = 0
    registry.profiles.forEach { profile ->
        val isActive = profile.id == active?.id
        val armed = armedForDelete == profile.id

        ActionRow(
            title = profile.name,
            subtitle = if (isActive) "Signed in" else "Switch to this profile",
            focused = focusedRow == row,
            trailingLabel = if (isActive) null else "SWITCH",
            onClick = { if (!isActive) viewModel.switchProfile(profile.id) },
        )
        row++

        // No delete row for the profile in use. Deleting the library you are
        // looking at, from inside it, is not something to make easy — switch
        // away first, which is one row up.
        if (!isActive) {
            ActionRow(
                title = if (armed) "Keep ${profile.name}" else "Delete ${profile.name}",
                subtitle = if (armed) {
                    "Press again to delete its settings, library and play time for good"
                } else {
                    "Removes its settings, library and play time"
                },
                focused = focusedRow == row,
                destructive = !armed,
                trailingLabel = if (armed) "CONFIRM" else "DELETE",
                onClick = {
                    if (armed) {
                        armedForDelete = null
                        viewModel.deleteProfile(profile.id)
                    } else {
                        armedForDelete = profile.id
                    }
                },
            )
            row++
        }
        RowDivider()
    }

    ActionRow(
        title = "Add a profile",
        subtitle = "Starts with default settings and no games until it scans",
        focused = focusedRow == row,
        trailingLabel = "ADD",
        onClick = { viewModel.createProfile() },
    )

    // Stated rather than hidden: the alternative is a page where the last
    // profile simply has no delete row, which reads as a missing feature.
    if (registry.isLastProfile) {
        RowDivider()
        InfoRow(
            "Only one profile",
            "The last profile cannot be deleted — the launcher has to sign in as " +
                "somebody. Add another first if you want this one gone.",
        )
    }
}

/**
 * Name, picture and colour for whoever is signed in.
 *
 * Its own page rather than four more rows under the list. Repeating them per
 * profile would make a page walked one row at a time very long, and offering
 * them for somebody else's profile from inside your own is a strange power to
 * have — this page is always about the profile in use, which is why it never
 * asks which.
 */
@Composable
internal fun ProfileEditPage(
    registry: ProfileRegistry,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val profile = registry.active
    if (profile == null) {
        InfoRow("No profile", "The launcher has not finished signing in yet.")
        return
    }
    val avatarPath = viewModel.avatarPathFor(profile)

    InfoRow(
        "Signed in as ${profile.name}",
        "These belong to this profile alone. So does everything else in settings " +
            "— this is simply the part of it other profiles can see.",
    )
    RowDivider()

    var row = 0
    AvatarRow(
        profile = profile,
        hasAvatar = avatarPath != null,
        focused = focusedRow == row,
        onPicked = { uri -> viewModel.setProfileAvatar(profile.id, uri) },
    )
    row++

    if (avatarPath != null) {
        ActionRow(
            title = "Remove picture",
            subtitle = "Goes back to the drawn initial",
            focused = focusedRow == row,
            trailingLabel = "REMOVE",
            onClick = { viewModel.clearProfileAvatar(profile.id) },
        )
        row++
    }

    TextFieldRow(
        title = "Name",
        subtitle = "Shown on the top screen and in the profile list",
        value = profile.name,
        placeholder = "Player 1",
        focused = focusedRow == row,
        onValueChange = { viewModel.renameProfile(profile.id, it) },
    )
    row++

    ColorRow(
        title = "Profile colour",
        subtitle = "Tints the avatar and this profile's notification panel",
        colorsToPick = PROFILE_COLOURS,
        selected = Color(profile.accentArgb),
        focused = focusedRow == row,
        onSelected = { picked ->
            viewModel.setProfileAccent(
                profile.id,
                (picked ?: Color(LauncherProfile.DEFAULT_ACCENT)).value.toLong(),
            )
        },
    )
}

/**
 * Focusable rows on the profile list.
 *
 * Shares its arithmetic with nothing, which is the risk: a count that overshoots
 * produces presses that appear to do nothing. Kept immediately beside the page
 * so the two are edited in the same breath.
 */
internal fun profilesRowCount(registry: ProfileRegistry): Int {
    // Every profile has a switch row; every one but the active also has a delete.
    val activeId = registry.active?.id
    val perProfile = registry.profiles.fold(0) { total, profile ->
        total + if (profile.id == activeId) 1 else 2
    }
    // Plus "Add a profile", which is always last.
    return perProfile + 1
}

/** Picture, name and colour — plus the remove row, when there is a picture. */
internal fun profileEditRowCount(hasAvatar: Boolean): Int = if (hasAvatar) 4 else 3

/**
 * Picks a photo for the profile.
 *
 * The photo picker rather than the document picker: it needs no storage
 * permission at all, it is the flow Android puts in front of users everywhere
 * else, and the image is copied into the profile immediately, so the grant not
 * outliving the picker does not matter.
 */
@Composable
private fun AvatarRow(
    profile: LauncherProfile,
    hasAvatar: Boolean,
    focused: Boolean,
    onPicked: (String) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(it.toString()) } }

    ActionRow(
        title = "Profile picture",
        subtitle = if (hasAvatar) {
            "Choose a different one"
        } else {
            "Currently the drawn initial, ${profile.initial}"
        },
        focused = focused,
        trailingLabel = if (hasAvatar) "CHANGE" else "CHOOSE",
        onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}

/** Enough separation that two profiles' avatars are told apart at a glance. */
private val PROFILE_COLOURS = listOf(
    Color(0xFF6C8CFF),
    Color(0xFFFF6C8C),
    Color(0xFF4CC9A0),
    Color(0xFFFFB74D),
    Color(0xFFB07CFF),
    Color(0xFF44C0E6),
)
