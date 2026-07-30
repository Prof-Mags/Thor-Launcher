package com.thor.data.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether THOR's pointer service is switched on, and how to get there.
 *
 * An accessibility service cannot ask for itself. There is no dialog, no
 * `requestPermissions` call and no way to grant it programmatically — the user has
 * to walk into system settings and enable it by name. That is deliberate on
 * Android's part, because the permission is genuinely powerful, and it means the
 * launcher's whole job here is to say plainly what is needed and open the right
 * screen.
 *
 * The state is read on demand rather than observed: it changes only while the user
 * is inside the system settings app, which is to say only while THOR is not in
 * front.
 */
@Singleton
class PointerServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val component = ComponentName(context.packageName, SERVICE_CLASS)

    /**
     * True when the service is enabled for the current user.
     *
     * Read from the secure setting rather than from `AccessibilityManager`, which
     * lists only services that are both enabled *and* currently bound — so a
     * service the system has not started yet reads as off, and the settings screen
     * would offer to enable something already enabled.
     */
    fun isEnabled(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        enabled.split(':').any { entry ->
            val parsed = ComponentName.unflattenFromString(entry) ?: return@any false
            parsed == component ||
                (parsed.packageName == component.packageName &&
                    parsed.className.endsWith(SERVICE_CLASS.substringAfterLast('.')))
        }
    }.getOrDefault(false)

    /**
     * Opens the system's accessibility settings.
     *
     * Some builds accept an extra naming the service and open straight to it;
     * most ignore it and show the list, which is why the description string is
     * written to be findable rather than relying on the deep link landing.
     */
    fun openSettings(): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component.flattenToString())
        context.startActivity(intent)
        true
    }.onFailure { error ->
        ThorLog.w(TAG, "Could not open accessibility settings", error)
    }.getOrDefault(false)

    private companion object {
        const val TAG = "Pointer"
        const val SERVICE_CLASS = "com.thor.launcher.mouse.ThorMouseService"

        /** Undocumented but long-standing; ignored where unsupported. */
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    }
}
