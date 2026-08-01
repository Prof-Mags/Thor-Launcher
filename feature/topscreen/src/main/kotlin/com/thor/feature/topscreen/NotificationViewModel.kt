package com.thor.feature.topscreen

import androidx.lifecycle.ViewModel
import com.thor.core.model.DeviceNotification
import com.thor.data.notification.NotificationHub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The notification panel's data.
 *
 * Barely a view model, and that is the point: the hub already holds sorted,
 * observable state fed by the listener service, so there is nothing to transform
 * and nothing to cache. Its job is to let a composable reach a `@Singleton`
 * through Hilt without the screen taking a dependency on the service itself.
 *
 * Dismissal is passed straight through. Only the bound service can cancel a
 * notification, and the hub holds the callback it registered.
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val hub: NotificationHub,
) : ViewModel() {

    val notifications: StateFlow<List<DeviceNotification>> = hub.active

    /**
     * Whether the listener is bound, which is not the same as being permitted.
     *
     * The panel needs this to tell "you have not granted access" apart from
     * "nothing is waiting" — two empty lists that need opposite things from the
     * user.
     */
    val connected: StateFlow<Boolean> = hub.connected

    fun dismiss(key: String) = hub.dismiss(key)

    fun dismissAll() = hub.dismissAll()
}
