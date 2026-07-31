package com.thor.data.launcher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.Display
import androidx.core.net.toUri
import com.thor.core.common.log.ThorLog
import com.thor.core.model.AppEntry
import com.thor.core.model.GameEntry
import com.thor.data.scanner.EmulatorRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Why a launch could not be performed. */
sealed interface LaunchFailure {
    data class EmulatorMissing(val platformId: String) : LaunchFailure
    data class EmulatorNotInstalled(val packageName: String) : LaunchFailure
    data class NoHandler(val detail: String) : LaunchFailure
    data class Unknown(val cause: Throwable) : LaunchFailure
}

sealed interface LaunchResult {
    /**
     * @param onRequestedTarget whether the app actually went to the panel that was
     *   asked for. False when it had to fall back to the default display — which
     *   the caller has to know, because it may have already stood a panel down for
     *   an app that is not going to arrive on it.
     */
    data class Success(val onRequestedTarget: Boolean = true) : LaunchResult

    data class Failed(val reason: LaunchFailure) : LaunchResult
}

/**
 * Which panel an entry should open on.
 *
 * Android can start an activity on a specific display through
 * `ActivityOptions.setLaunchDisplayId`, which is what makes "play this on the
 * other screen" possible at all. The target is expressed by role rather than by
 * display id so callers do not have to resolve hardware themselves, and so the
 * choice survives the second panel being reattached with a different id.
 */
enum class LaunchTarget {
    /** Wherever the system would normally put it. */
    DEFAULT,

    /** The device's built-in main panel. */
    MAIN_SCREEN,

    /** The secondary panel, when one is attached. */
    SECOND_SCREEN,
}

/**
 * A system surface the shortcut panel can open.
 *
 * All of these are ordinary exported activities, so they need no permission —
 * which is the whole reason the panel offers *these* and not brightness, rotation
 * or the notification shade. Changing those from an unprivileged app requires
 * either `WRITE_SETTINGS` or a system-signed permission, so a tile for them could
 * only ever open a settings page pretending to be a toggle.
 *
 * @param action the preferred intent, usually a slide-up `Settings.Panel`
 * @param fallbackAction a full settings screen for devices whose ROM has removed
 *   the panel variant. Panels are a stock-Android feature and handhelds ship
 *   heavily modified ROMs, so the fallback is not theoretical.
 */
enum class SystemPanel(
    internal val action: String,
    internal val fallbackAction: String? = null,
) {
    WIFI(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS),
    BLUETOOTH(Settings.ACTION_BLUETOOTH_SETTINGS),
    VOLUME(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS),
    ALL_SETTINGS(Settings.ACTION_SETTINGS),
}

/**
 * Starts apps and games.
 *
 * Games are the interesting case: an emulator has to be chosen (per-game
 * override, then platform default, then the first installed emulator that
 * supports the platform), and then handed the ROM in whichever way that
 * particular emulator expects — see [EmulatorRegistry].
 */
@Singleton
class EntryLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    /** THOR's activity on the second panel, when one is alive. */
    private val secondaryHomeHost: SecondaryHomeHost,
) {

    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager
    private val packageManager: PackageManager = context.packageManager
    private val displayManager: DisplayManager? =
        context.getSystemService(DisplayManager::class.java)

    /**
     * Launches an installed application, honouring work-profile ownership.
     *
     * Tries up to four ways before giving up, because a single refusal used to end
     * the whole attempt — and a refused launch is not a quiet one: the caller has
     * already stood the second panel down, so failing puts the grid straight back
     * and the press reads as having done nothing at all. Trying once was
     * particularly poor for the two most common refusals, both of which have an
     * obvious next move:
     *
     *  - **The display would not take it.** Pinning an activity to a panel with
     *    `setLaunchDisplayId` is a request, and the system refuses it for
     *    activities it will not place there. Dropping the pin and letting the app
     *    open on the default display is better than not opening it.
     *  - **The component moved.** A stored `activityName` is a snapshot from the
     *    last scan; an app that has updated since may have renamed or removed that
     *    activity. Asking `LauncherApps` what the package launches *now* costs one
     *    call and fixes it without waiting for a rescan.
     */
    fun launchApp(app: AppEntry, target: LaunchTarget = LaunchTarget.DEFAULT): LaunchResult {
        val user = userManager.userProfiles.firstOrNull {
            userManager.getSerialNumberForUser(it) == app.userSerial
        } ?: Process.myUserHandle()

        val stored = ComponentName(app.packageName, app.activityName)
        var failure: LaunchFailure? = null

        fun attempt(component: ComponentName, options: Bundle?): Boolean = try {
            launcherApps.startMainActivity(component, user, null, options)
            true
        } catch (e: ActivityNotFoundException) {
            ThorLog.w("Launcher", "No activity $component", e)
            failure = LaunchFailure.NoHandler(app.packageName)
            false
        } catch (e: SecurityException) {
            ThorLog.w("Launcher", "Not permitted to launch $component", e)
            failure = LaunchFailure.Unknown(e)
            false
        } catch (e: IllegalStateException) {
            // Some ROMs report a refused display placement this way rather than
            // as a SecurityException. Uncaught it escaped the whole launch path
            // and left the panel handed over to an app that never started.
            ThorLog.w("Launcher", "Refused to start $component", e)
            failure = LaunchFailure.Unknown(e)
            false
        }

        /*
         * The second panel, started from THOR's own activity on it.
         *
         * Tried before anything else for that target, because it is the route
         * that does not depend on a permission THOR cannot hold. Android places
         * an activity on a secondary display when the app already has an
         * *activity* there; the grid on that panel is a `Presentation`, which is
         * a window and not an activity, so every `setLaunchDisplayId` attempt was
         * refused however it was phrased. A new task inherits the display of the
         * activity that started it, and `SecondaryHomeActivity` is on that one.
         */
        if (target == LaunchTarget.SECOND_SCREEN) {
            val onPanel = secondaryHomeHost.start(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(stored),
            )
            if (onPanel) return LaunchResult.Success()
        }

        val options = optionsFor(target)

        // As stored, on the requested panel.
        if (attempt(stored, options)) return LaunchResult.Success()

        // Same activity, wherever the system will have it.
        if (options != null && attempt(stored, null)) {
            ThorLog.i("Launcher", "${app.packageName} refused the target panel")
            return LaunchResult.Success(onRequestedTarget = false)
        }

        // Whatever the package launches now, in case the stored one is stale.
        val current = currentMainActivity(app.packageName, user)
        if (current != null && current != stored) {
            ThorLog.i("Launcher", "Retrying ${app.packageName} as $current")
            if (attempt(current, options)) return LaunchResult.Success()
            if (options != null && attempt(current, null)) {
                return LaunchResult.Success(onRequestedTarget = false)
            }
        }

        /*
         * Last resort: an ordinary intent, not `LauncherApps` at all.
         *
         * Every attempt above goes through `startMainActivity`, and some ROMs
         * refuse that outright — it is the *launcher* API, and vendors guard it
         * against apps they have not blessed as a home screen. When they do, all
         * four attempts fail identically and the user is told Android would not
         * open their app, which is true and useless: the app opens perfectly well
         * through the route every other app on the device uses.
         *
         * Only for the user's own profile. A work-profile app genuinely cannot be
         * started this way, and pretending otherwise would replace a clear refusal
         * with a confusing one.
         */
        if (user == Process.myUserHandle()) {
            val component = current ?: stored
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)

            val direct = startIntent(intent, target)
            if (direct is LaunchResult.Success) {
                ThorLog.i("Launcher", "${app.packageName} started by intent")
                return direct
            }

            // Once more with no display preference, in case that was the refusal.
            if (options != null) {
                val anywhere = startIntent(intent, LaunchTarget.DEFAULT)
                if (anywhere is LaunchResult.Success) {
                    return LaunchResult.Success(onRequestedTarget = false)
                }
            }
        }

        return LaunchResult.Failed(failure ?: LaunchFailure.NoHandler(app.packageName))
    }

    /**
     * The activity a package launches with today.
     *
     * Read from [LauncherApps] rather than the library, which is only as current
     * as the last scan.
     */
    private fun currentMainActivity(packageName: String, user: UserHandle): ComponentName? =
        runCatching {
            launcherApps.getActivityList(packageName, user).firstOrNull()?.componentName
        }.getOrNull()

    /**
     * Launches a game.
     *
     * @param game the entry to run
     * @param platformDefaultEmulator the emulator configured for the platform
     * @param contentUriOverride launches a specific alternate version instead of
     *   the primary file
     */
    fun launchGame(
        game: GameEntry,
        platformDefaultEmulator: String?,
        contentUriOverride: String? = null,
        target: LaunchTarget = LaunchTarget.DEFAULT,
    ): LaunchResult {
        val emulatorPackage = game.emulatorPackage
            ?: platformDefaultEmulator
            ?: firstInstalledEmulatorFor(game.platformId)
            ?: return LaunchResult.Failed(LaunchFailure.EmulatorMissing(game.platformId))

        if (!isInstalled(emulatorPackage)) {
            return LaunchResult.Failed(LaunchFailure.EmulatorNotInstalled(emulatorPackage))
        }

        val uri = (contentUriOverride ?: game.contentUri).toUri()
        val spec = EmulatorRegistry.specFor(emulatorPackage)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_ANY)
            setPackage(emulatorPackage)
            spec?.activityName?.let { setClassName(emulatorPackage, it) }

            // Emulators that predate scoped storage want a plain path in an
            // extra rather than a content URI in the intent data.
            spec?.pathExtraKey?.let { key ->
                putExtra(key, uri.toFilePathOrString())
            }

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        // The second panel goes through THOR's activity there for the same reason
        // an app does; see the note in [launchApp].
        if (target == LaunchTarget.SECOND_SCREEN && secondaryHomeHost.start(intent)) {
            return LaunchResult.Success()
        }

        return try {
            context.startActivity(intent, optionsFor(target))
            LaunchResult.Success()
        } catch (e: ActivityNotFoundException) {
            // An explicit component can be wrong if the emulator was updated and
            // renamed its activity; retry letting the system resolve it.
            ThorLog.w("Launcher", "Explicit component failed for $emulatorPackage; retrying", e)
            retryWithoutComponent(uri, emulatorPackage, target)
        } catch (e: SecurityException) {
            LaunchResult.Failed(LaunchFailure.Unknown(e))
        }
    }

    private fun retryWithoutComponent(
        uri: Uri,
        emulatorPackage: String,
        target: LaunchTarget,
    ): LaunchResult = try {
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_ANY)
            setPackage(emulatorPackage)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.startActivity(fallback, optionsFor(target))
        LaunchResult.Success()
    } catch (e: ActivityNotFoundException) {
        LaunchResult.Failed(LaunchFailure.NoHandler(emulatorPackage))
    }

    /**
     * Builds the launch options that pin an activity to a display.
     *
     * Returns null for [LaunchTarget.DEFAULT] and whenever the requested panel
     * is not present, so the launch still happens on the default display rather
     * than failing. Note that the target app must be resizeable and the display
     * must permit it — if either is untrue, Android silently redirects to the
     * default display, which is the correct outcome anyway.
     */
    private fun optionsFor(target: LaunchTarget): Bundle? {
        val displayId = when (target) {
            LaunchTarget.DEFAULT -> return null
            LaunchTarget.MAIN_SCREEN -> Display.DEFAULT_DISPLAY
            LaunchTarget.SECOND_SCREEN -> secondaryDisplayId() ?: return null
        }
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    /**
     * Whether Android will accept a launch onto [target]'s display at all.
     *
     * Asked rather than discovered by exception. An app may place an activity on
     * a secondary display only when that display is public, or when it already
     * has a window there — so the answer depends on what THOR happens to have on
     * the panel at that moment, and it changes as the launcher hands the panel
     * over. Asking first turns a refusal into a decision the launcher can act on,
     * instead of a `SecurityException` surfaced to someone who pressed A.
     *
     * A false answer is not a failure; it means "open it on the near panel".
     */
    fun canLaunchOn(target: LaunchTarget): Boolean {
        val displayId = when (target) {
            LaunchTarget.DEFAULT -> return true
            LaunchTarget.MAIN_SCREEN -> Display.DEFAULT_DISPLAY
            LaunchTarget.SECOND_SCREEN -> secondaryDisplayId() ?: return false
        }
        if (displayId == Display.DEFAULT_DISPLAY) return true

        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return true

        return runCatching {
            activityManager.isActivityStartAllowedOnDisplay(
                context,
                displayId,
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            )
        }.getOrDefault(true)
    }

    /**
     * The display the launcher's own second panel occupies.
     *
     * Set by the shell, which is the one place that knows which display it put its
     * presentation on. Without it this class made its own guess — the first attached
     * display that is not the built-in one — and the two answers are not necessarily
     * the same: any extra display the system reports (a screen recorder, a cast
     * target, a vendor overlay) can come first and take the app somewhere nobody is
     * looking, while the panel the user *is* looking at stands its grid down for it.
     */
    @Volatile
    var secondPanelDisplayId: Int? = null

    /** The panel the launcher projects onto, or the first non-default display. */
    private fun secondaryDisplayId(): Int? = secondPanelDisplayId
        ?: displayManager
            ?.displays
            ?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
            ?.displayId

    /** True when a second panel is attached and can host an activity. */
    fun hasSecondaryDisplay(): Boolean = secondaryDisplayId() != null

    /**
     * Opens the system's application details page.
     *
     * `LauncherApps.startAppDetailsActivity` is used in preference to a raw
     * `ACTION_APPLICATION_DETAILS_SETTINGS` intent because it is the only form
     * that resolves correctly for apps belonging to a work profile.
     */
    fun openAppInfo(app: AppEntry): LaunchResult = try {
        val user = userManager.userProfiles.firstOrNull {
            userManager.getSerialNumberForUser(it) == app.userSerial
        } ?: Process.myUserHandle()

        launcherApps.startAppDetailsActivity(
            ComponentName(app.packageName, app.activityName),
            user,
            null,
            null,
        )
        LaunchResult.Success()
    } catch (e: ActivityNotFoundException) {
        LaunchResult.Failed(LaunchFailure.NoHandler(app.packageName))
    } catch (e: SecurityException) {
        LaunchResult.Failed(LaunchFailure.Unknown(e))
    }

    /** Asks the system to uninstall a package. */
    fun requestUninstall(packageName: String): LaunchResult = startIntent(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)),
    )

    /** Starts an arbitrary intent, used by launcher actions and shortcuts. */
    fun startIntent(
        intent: Intent,
        target: LaunchTarget = LaunchTarget.DEFAULT,
    ): LaunchResult = try {
        context.startActivity(
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            optionsFor(target),
        )
        LaunchResult.Success()
    } catch (e: ActivityNotFoundException) {
        LaunchResult.Failed(LaunchFailure.NoHandler(intent.action ?: "unknown"))
    } catch (e: SecurityException) {
        // Some ROMs guard their own settings activities; that is a refusal to
        // report, not a crash.
        ThorLog.w("Launcher", "Not permitted to start ${intent.action}", e)
        LaunchResult.Failed(LaunchFailure.Unknown(e))
    }

    /**
     * Opens a system settings surface.
     *
     * Always on the main panel, never on the secondary one. The launcher's second
     * screen is a `Presentation`, which sits *above* application windows on its
     * display — so an activity sent there renders behind the grid the user is
     * looking at, receiving the input they think is going to the grid. Sending
     * these to the panel that has no presentation over it is the only placement
     * that is visible in every display mode.
     */
    fun openSystemPanel(panel: SystemPanel): LaunchResult {
        val result = startIntent(Intent(panel.action), LaunchTarget.MAIN_SCREEN)
        if (result is LaunchResult.Success) return result

        val fallback = panel.fallbackAction ?: return result
        ThorLog.w("Launcher", "${panel.action} unavailable; falling back to $fallback")
        return startIntent(Intent(fallback), LaunchTarget.MAIN_SCREEN)
    }

    fun isInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Installed emulators able to run [platformId], in registry order. */
    fun installedEmulatorsFor(platformId: String): List<String> =
        EmulatorRegistry.candidatesFor(platformId)
            .map(com.thor.data.scanner.EmulatorSpec::packageName)
            .filter(::isInstalled)

    private fun firstInstalledEmulatorFor(platformId: String): String? =
        installedEmulatorsFor(platformId).firstOrNull()

    /**
     * Best-effort conversion of a document URI to a filesystem path.
     *
     * Emulators requiring a real path can only open ROMs on primary shared
     * storage; when the URI does not decode to such a path the original string
     * is passed through so the emulator can report the problem itself rather
     * than being handed something silently wrong.
     */
    private fun Uri.toFilePathOrString(): String {
        if (scheme == "file") return path ?: toString()
        val documentId = runCatching {
            android.provider.DocumentsContract.getDocumentId(this)
        }.getOrNull() ?: return toString()

        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) return toString()
        val (volume, relativePath) = parts
        return if (volume.equals("primary", ignoreCase = true)) {
            "${android.os.Environment.getExternalStorageDirectory()}/$relativePath"
        } else {
            "/storage/$volume/$relativePath"
        }
    }

    private companion object {
        /**
         * ROMs have no registered MIME types, and emulators match on a wildcard
         * rather than on any specific type.
         */
        const val MIME_ANY = "*/*"
    }
}
