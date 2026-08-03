package com.thor.launcher

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.log.ThorLog
import com.thor.core.model.LauncherProfile
import com.thor.core.model.NotificationAccess
import com.thor.core.model.ProfileRegistry
import com.thor.data.notification.NotificationRepository
import com.thor.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The profile cluster's state, and the actions behind it.
 *
 * Separate from the launcher's main view model because none of it is about the
 * library: it survives a profile switch that replaces everything the main one
 * holds, and it is the thing that *causes* that switch.
 */
@HiltViewModel
class ProfileStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {

    val registry: StateFlow<ProfileRegistry> = profiles.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileRegistry.EMPTY)

    val profile: StateFlow<LauncherProfile?> = profiles.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The avatar's path, resolved from the profile rather than stored beside it.
     *
     * The registry holds a bare file name so a profile survives the data
     * directory moving; turning it into a path is this layer's job, and it
     * returns null for a file that has gone so a deleted picture falls back to
     * the drawn initial instead of showing a broken image.
     */
    val avatarPath: StateFlow<String?> = profiles.activeProfile
        .map { it?.let(profiles::avatarPath) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val access: StateFlow<NotificationAccess> = notifications.access
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            NotificationAccess.Denied,
        )

    private val _shadeOpen = MutableStateFlow(false)
    val shadeOpen: StateFlow<Boolean> = _shadeOpen.asStateFlow()

    fun toggleShade() {
        val opening = !_shadeOpen.value
        _shadeOpen.value = opening
        // Opening is the moment the prompt would be seen, so it is the moment to
        // find out whether it is still needed.
        if (opening) notifications.refresh()
    }

    fun closeShade() {
        _shadeOpen.value = false
    }

    /**
     * Sends the user to Android's notification access page.
     *
     * There is no runtime permission request for this one — no dialog an app can
     * raise — so opening the page is the whole of what "prompt for it" can mean.
     */
    fun requestNotificationAccess() {
        runCatching { context.startActivity(notifications.accessSettingsIntent()) }
            .onFailure { ThorLog.w(TAG, "No notification access settings page", it) }
    }

    /**
     * Opens this app's App info page.
     *
     * Where the restricted-settings unlock lives. Android 13 refuses this
     * permission to anything installed outside a store and says only that it is
     * disabled for security, with no route to the overflow item that lifts it.
     */
    fun openAppInfo() {
        runCatching { context.startActivity(notifications.appInfoIntent()) }
            .onFailure { ThorLog.w(TAG, "No app info page", it) }
    }

    fun refreshAccess() = notifications.refresh()

    fun openNotification(key: String) = notifications.open(key)

    fun dismissNotification(key: String) = notifications.dismiss(key)

    fun dismissAllNotifications() = notifications.dismissAll()

    fun switchProfile(id: String) {
        viewModelScope.launch {
            profiles.switchTo(id)
            _shadeOpen.value = false
        }
    }

    fun createProfile(name: String, accentArgb: Long, switchTo: Boolean = true) {
        viewModelScope.launch {
            val created = profiles.create(name, accentArgb)
            if (switchTo) profiles.switchTo(created.id)
        }
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch { profiles.rename(id, name) }
    }

    fun setProfileAccent(id: String, accentArgb: Long) {
        viewModelScope.launch { profiles.setAccent(id, accentArgb) }
    }

    fun setProfileAvatar(id: String, source: Uri) {
        viewModelScope.launch { profiles.setAvatar(id, source) }
    }

    fun clearProfileAvatar(id: String) {
        viewModelScope.launch { profiles.clearAvatar(id) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { profiles.delete(id) }
    }

    private companion object {
        const val TAG = "ProfileStatus"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
