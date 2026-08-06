package com.thor.data.launcher

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.app.DownloadManager
import android.provider.Settings
import android.view.Display
import androidx.core.net.toUri
import com.thor.core.common.log.ThorLog
import com.thor.core.model.AppEntry
import com.thor.core.model.GameEntry
import com.thor.data.scanner.EmulatorRegistry
import com.thor.data.scanner.EmulatorSpec
import com.thor.data.scanner.RomLaunchContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Why a launch could not be performed. */
sealed interface LaunchFailure {
    data class EmulatorMissing(val platformId: String) : LaunchFailure
    data class EmulatorNotInstalled(val packageName: String) : LaunchFailure
    data object RomUnavailable : LaunchFailure
    data class UnsupportedEmulatorLaunch(val message: String) : LaunchFailure
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

    /**
     * The system's own downloads list.
     *
     * Not a launcher feature and deliberately not one: what "downloads" means on
     * an Android device is already answered by the download manager, and every
     * device has it. Loki has nothing of its own to show here and pointing at
     * the real thing is more use than a screen that lists nothing.
     */
    DOWNLOADS(DownloadManager.ACTION_VIEW_DOWNLOADS),
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
            ThorLog.w(TAG, "No activity $component", e)
            failure = LaunchFailure.NoHandler(app.packageName)
            false
        } catch (e: SecurityException) {
            ThorLog.w(TAG, "Not permitted to launch $component", e)
            failure = LaunchFailure.Unknown(e)
            false
        } catch (e: IllegalStateException) {
            // Some ROMs report a refused display placement this way rather than
            // as a SecurityException. Uncaught it escaped the whole launch path
            // and left the panel handed over to an app that never started.
            ThorLog.w(TAG, "Refused to start $component", e)
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

            // This falls back off the pinned display itself, so there is no
            // second call to make here.
            val direct = startFirstThatOpens(target, listOf(intent), app.packageName)
            if (direct is LaunchResult.Success) {
                ThorLog.i(TAG, "${app.packageName} started by intent")
            }
            return direct
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
    suspend fun launchGame(
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
        // Resolved rather than looked up exactly: a build the table does not name
        // by id still takes its parent's launch contract, and falling through to
        // the generic one here is how a nightly ends up opening its own file
        // browser instead of the game.
        val spec = EmulatorRegistry.resolve(emulatorPackage)
        if ((contentUriOverride == null && game.isMissing) || !canReadRom(uri)) {
            ThorLog.w(TAG, "ROM is unavailable: $uri")
            return LaunchResult.Failed(LaunchFailure.RomUnavailable)
        }

        val declared = spec?.launchContract ?: RomLaunchContract.ContentUriView

        /*
         * A row that documents no contract still gets the ordinary one tried.
         *
         * This used to return here — the launch was refused before an intent was
         * ever built, and the user was told to open the emulator and find the
         * game themselves. That is the table's ignorance charged to the user, and
         * these are exactly the builds most likely to have gained a VIEW filter
         * since the row was written, which nobody would ever discover while the
         * launch was rejected on sight.
         *
         * The hint survives as the message *if* every attempt fails, which is the
         * right place for it: advice about a fallback rather than an instruction
         * issued instead of trying.
         */
        val hint = (declared as? RomLaunchContract.Undocumented)?.hint
        val contract = if (declared is RomLaunchContract.Undocumented) {
            RomLaunchContract.ContentUriView
        } else {
            declared
        }
        val filePath = when (contract) {
            is RomLaunchContract.PathExtra,
            RomLaunchContract.RetroArch,
            -> uri.toFilePathOrNull()
                ?: return LaunchResult.Failed(
                    LaunchFailure.UnsupportedEmulatorLaunch(
                        "This emulator needs a shared-storage ROM path. Re-add the ROM folder.",
                    ),
                )

            else -> null
        }

        fun romIntent(withComponent: Boolean) = Intent(
            if (contract == RomLaunchContract.RetroArch) Intent.ACTION_MAIN else Intent.ACTION_VIEW,
        ).apply {
            setPackage(emulatorPackage)
            if (withComponent) spec?.activityName?.let { setClassName(emulatorPackage, it) }

            when (contract) {
                RomLaunchContract.ContentUriView -> {
                    setDataAndType(uri, MIME_ANY)
                    clipData = ClipData.newRawUri("ROM", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                is RomLaunchContract.PathExtra -> {
                    type = MIME_ANY
                    putExtra(contract.key, filePath)
                }

                RomLaunchContract.RetroArch -> {
                    putExtra(RETROARCH_ROM_EXTRA, filePath)
                }

                is RomLaunchContract.Undocumented ->
                    error("An undocumented contract is resolved to a real one above")
            }

            /*
             * Read only, because read is all THOR has to give.
             *
             * This asked to pass on write access as well, and that single flag is
             * why no game would start on any screen. A grant is not a request:
             * `startActivity` checks that the caller actually holds every
             * permission it is handing on, and throws `SecurityException` before
             * it has looked at the intent's target — let alone at which display
             * was asked for. THOR takes its ROM directories with
             * `FLAG_GRANT_READ_URI_PERMISSION` alone (see the storage pickers),
             * so the write it was offering was never its to offer.
             *
             * The failure was invisible in the worst way. It presents as
             * "Android would not let Loki open that app on this screen", which
             * is a true sentence about the wrong thing entirely, and it survived
             * being retried on every display because every retry carried the same
             * flag. Applications were unaffected throughout — their intents carry
             * no URI — which is what made the grid look display-related when it
             * was not.
             *
             * Emulators that want to write save data beside the ROM cannot be
             * given that from here regardless; they ask for their own folder.
             */
        }

        val intent = romIntent(withComponent = true)
        val reuseRisk = target == LaunchTarget.SECOND_SCREEN && (
            spec?.mayReuseExistingTask == true || activityMayReuseExistingTask(intent)
        )
        val launchTarget = if (reuseRisk) LaunchTarget.MAIN_SCREEN else target
        if (reuseRisk) {
            ThorLog.i(TAG, "$emulatorPackage can reuse an existing task; launching on main screen")
        }

        // The second panel goes through THOR's activity there for the same reason
        // an app does; see the note in [launchApp].
        if (launchTarget == LaunchTarget.SECOND_SCREEN && secondaryHomeHost.start(intent)) {
            return LaunchResult.Success()
        }

        /*
         * Games get the same treatment applications got, and for the same reason.
         *
         * This used to be one `startActivity` with the display pinned, and the two
         * ways it fails were handled unequally: a missing component was retried
         * without one, and a refused *display* was reported as a failure and
         * nothing else. On this hardware that refusal is the ordinary case rather
         * than the exceptional one — `setLaunchDisplayId` onto a built-in second
         * panel is not something an unprivileged app is granted, and the route
         * that does work needs THOR to have an activity on that panel already. So
         * pressing A on a game on the grid's own screen answered "Android would
         * not let THOR open that app on this screen" for a game that runs
         * perfectly well the moment the pin is dropped.
         *
         * An emulator opening on the near panel is a worse outcome than opening
         * on the far one, and a far better one than not opening at all.
         */
        val candidates = listOfNotNull(
            intent,
            romIntent(withComponent = false).takeIf { spec?.activityName != null },
        )
        val result = startFirstThatOpens(launchTarget, candidates, emulatorPackage)
        return if (reuseRisk && result is LaunchResult.Success) {
            result.copy(onRequestedTarget = false)
        } else {
            result
        }
    }

    /**
     * Starts the first of [candidates] that opens, on [target]'s panel if it can.
     *
     * Two axes, tried in that order of preference: which way of naming the
     * activity works, and whether the requested panel will take it. Every caller
     * needs both, and each of them used to implement some part of it — which is
     * how a refused display placement came to be a fatal error for games and a
     * recoverable one for applications.
     *
     * @param handlerName what to name in the failure when nothing opened
     */
    private fun startFirstThatOpens(
        target: LaunchTarget,
        candidates: List<Intent>,
        handlerName: String,
    ): LaunchResult {
        val options = optionsFor(target)
        var failure: LaunchFailure? = null

        for (intent in candidates) {
            val error = startActivityOrNull(intent, options) ?: return LaunchResult.Success()
            failure = error
        }

        /*
         * Then anywhere the system will have it.
         *
         * Only worth doing when a panel was actually asked for — with no options
         * this is the same call a second time — and reported as *not* on the
         * requested target, because the caller may have stood a panel down for an
         * app that is now arriving somewhere else entirely.
         */
        if (options != null) {
            for (intent in candidates) {
                if (startActivityOrNull(intent, null) == null) {
                    ThorLog.i(TAG, "$handlerName refused the target panel; opened on the default one")
                    return LaunchResult.Success(onRequestedTarget = false)
                }
            }
        }

        return LaunchResult.Failed(failure ?: LaunchFailure.NoHandler(handlerName))
    }

    /** Starts [intent], returning why it did not start, or null when it did. */
    private fun startActivityOrNull(intent: Intent, options: Bundle?): LaunchFailure? = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
        null
    } catch (e: ActivityNotFoundException) {
        ThorLog.w(TAG, "Nothing handles ${intent.describe()}", e)
        LaunchFailure.NoHandler(intent.`package` ?: intent.action ?: "unknown")
    } catch (e: SecurityException) {
        // Some ROMs guard their own settings activities, and every ROM guards a
        // display it will not place a third-party activity on. Both are refusals
        // to report and recover from, not crashes.
        ThorLog.w(TAG, "Not permitted to start ${intent.describe()}", e)
        LaunchFailure.Unknown(e)
    } catch (e: IllegalStateException) {
        // The other way a refused display placement arrives. Uncaught it escaped
        // the whole launch path and left a panel handed to an app that never came.
        ThorLog.w(TAG, "Refused to start ${intent.describe()}", e)
        LaunchFailure.Unknown(e)
    }

    private fun Intent.describe(): String =
        component?.flattenToShortString() ?: `package` ?: action ?: "unknown"

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
     * Whether a launch onto the second panel can be started from THOR's own
     * activity there rather than pinned to the display.
     *
     * The distinction decides *ordering*, which is why the caller needs it. The
     * direct route's claim on that display is `SecondaryHomeActivity`, which is
     * unaffected by the presentation coming down — so the panel can be handed
     * over before the app starts, and the app arrives on top of an empty display
     * rather than underneath a window that has not gone yet. A pinned launch has
     * no such claim: the presentation is the claim, and taking it away first is
     * what gets the launch refused.
     */
    fun canStartOnSecondPanelDirectly(): Boolean = secondaryHomeHost.isAvailable

    /**
     * Waits for THOR's activity on the second panel to exist.
     *
     * Meaningful only just after the presentation has stood down, which is what
     * uncovers that activity and has the system recreate it if it had been
     * reclaimed. See [SecondaryHomeHost.awaitAvailable].
     */
    suspend fun awaitSecondPanelHost(timeoutMs: Long): Boolean =
        secondaryHomeHost.awaitAvailable(timeoutMs)

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

    /**
     * Starts an arbitrary intent, used by launcher actions and shortcuts.
     *
     * Falls back off the requested panel like every other launch: a settings
     * screen that opens on the near panel is still a settings screen, and the
     * refusal is not something the person who pressed the tile can act on.
     */
    fun startIntent(
        intent: Intent,
        target: LaunchTarget = LaunchTarget.DEFAULT,
    ): LaunchResult = startFirstThatOpens(
        target = target,
        candidates = listOf(intent),
        handlerName = intent.action ?: "unknown",
    )

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

    /**
     * Every installed package this launcher recognises as an emulator.
     *
     * Asked of the device rather than of the table, which is the whole fix. The
     * table can only name builds that existed when it was written, and emulators
     * ship under ids it cannot enumerate in advance — nightlies, forks and store
     * editions each append their own suffix. Checking every known id for
     * installation therefore reported a great many installed emulators as
     * missing, because the id on the device was one segment longer than the row.
     *
     * Enumerating instead and resolving each result means a build nobody here
     * has heard of is still recognised, as long as it descends from something
     * this table knows; see [EmulatorRegistry.resolve].
     *
     * Cached, because this is a binder call returning every package on the
     * device and the settings screen asks the question once per system. Held
     * only for [INSTALLED_CACHE_MS], so an emulator installed while the launcher
     * is running turns up without a restart.
     */
    fun installedEmulators(): List<InstalledEmulator> {
        val cached = installedEmulatorCache
        if (cached != null && SystemClock.elapsedRealtime() - cachedAt < INSTALLED_CACHE_MS) {
            return cached
        }

        /*
         * Asked one id at a time, which cannot fail as a set.
         *
         * This is the whole list on most devices and it is checked this way on
         * purpose: `getPackageInfo` answers about one package, so a failure is
         * one emulator missing rather than all of them. Enumerating is the part
         * that can go wrong wholesale — see [enumerateEmulators] — and it is
         * additive below precisely so that when it does, this is still the
         * answer instead of an empty screen.
         */
        val known = EmulatorRegistry.KNOWN
            .filter { isInstalled(it.packageName) }
            .map { spec ->
                InstalledEmulator(
                    packageName = spec.packageName,
                    displayName = spec.displayName,
                    spec = spec,
                )
            }

        // Only the builds the table cannot name: nightlies, forks, store
        // editions. Anything already found above is dropped rather than listed a
        // second time under the same id.
        val exact = known.mapTo(mutableSetOf(), InstalledEmulator::packageName)
        val variants = enumerateEmulators().filterNot { it.packageName in exact }

        val resolved = known + variants
        installedEmulatorCache = resolved
        cachedAt = SystemClock.elapsedRealtime()
        return resolved
    }

    /**
     * Emulators found by listing what is installed, or nothing.
     *
     * The only way to find a build whose id this launcher has never seen, and
     * the only call here that can fail for reasons having nothing to do with
     * emulators: `getInstalledPackages` returns every package in one parcel, and
     * on a device with enough of them that parcel exceeds the binder transaction
     * limit and the call throws. Swallowing that and returning nothing was how a
     * previous version of this reported every emulator on the device as missing
     * — the failure is wholesale, so the damage was too.
     *
     * It stays because it is genuinely the only way to find a fork, but nothing
     * depends on it: [installedEmulators] adds this to a list it has already
     * built, so an empty answer costs the variants and nothing else.
     */
    private fun enumerateEmulators(): List<InstalledEmulator> = runCatching {
        packageManager.getInstalledPackages(0).mapNotNull { info ->
            EmulatorRegistry.resolve(info.packageName)?.let { spec ->
                InstalledEmulator(
                    packageName = info.packageName,
                    displayName = EmulatorRegistry.displayNameFor(info.packageName),
                    spec = spec,
                )
            }
        }
    }.getOrElse {
        ThorLog.w("Launcher", "Could not list installed packages; known ids only", it)
        emptyList()
    }

    /**
     * Installed emulators able to run [platformId], in registry order.
     *
     * Grouped by the row each build descends from, so the table's own ordering —
     * dedicated emulators before the many-core front-ends — still decides what
     * an unassigned game launches with. Within a group the exact id comes first,
     * so a plain install wins over a nightly of the same thing.
     */
    fun installedEmulatorsFor(platformId: String): List<String> {
        val installed = installedEmulators()
        return EmulatorRegistry.candidatesFor(platformId).flatMap { spec ->
            installed
                .filter { it.spec.packageName == spec.packageName }
                .sortedBy { if (it.packageName == spec.packageName) 0 else 1 }
                .map(InstalledEmulator::packageName)
        }
    }

    private var installedEmulatorCache: List<InstalledEmulator>? = null
    private var cachedAt = 0L

    private fun firstInstalledEmulatorFor(platformId: String): String? =
        installedEmulatorsFor(platformId).firstOrNull()

    /**
     * A single-task/single-instance activity can receive a new intent in an
     * existing task on another display. Its start call succeeds, but it has not
     * taken the requested panel, so that panel must stay owned by THOR.
     */
    private fun activityMayReuseExistingTask(intent: Intent): Boolean {
        val info = runCatching {
            intent.component?.let { packageManager.getActivityInfo(it, 0) }
                ?: packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        }.getOrNull() ?: return false

        return info.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK ||
            info.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE ||
            info.launchMode == LAUNCH_SINGLE_INSTANCE_PER_TASK
    }

    /** Check the exact persisted ROM URI before handing the panel to an emulator. */
    private suspend fun canReadRom(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        when (uri.scheme?.lowercase()) {
            "content" -> runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
            }.getOrDefault(false)

            "file" -> uri.path?.let(::File)?.canRead() == true
            null -> File(uri.path ?: uri.toString()).canRead()
            else -> false
        }
    }

    /**
     * Converts only document URIs that have a real shared-storage path.
     *
     * Passing a content URI as a fake raw path makes a path-only emulator accept
     * the activity launch yet ignore the ROM. Returning null lets the launcher
     * keep its grid alive and show a useful fix instead.
     */
    private fun Uri.toFilePathOrNull(): String? {
        if (scheme == "file") return path?.takeIf(String::isNotBlank)
        if (scheme == null) return path?.takeIf(String::isNotBlank)
        val documentId = runCatching {
            android.provider.DocumentsContract.getDocumentId(this)
        }.getOrNull() ?: return null

        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) return null
        val (volume, relativePath) = parts
        return if (volume.equals("primary", ignoreCase = true)) {
            "${android.os.Environment.getExternalStorageDirectory()}/$relativePath"
        } else {
            "/storage/$volume/$relativePath"
        }
    }

    private companion object {
        const val TAG = "Launcher"

        /**
         * How long the installed-emulator list is trusted.
         *
         * Short enough that installing one and going straight to Settings shows
         * it; long enough that drawing a screen with a system per row does not
         * enumerate every package on the device once per row.
         */
        const val INSTALLED_CACHE_MS = 10_000L

        /**
         * ROMs have no registered MIME types, and emulators match on a wildcard
         * rather than on any specific type.
         */
        const val MIME_ANY = "*/*"

        /** RetroArch's public Android activity reads the ROM from this extra. */
        const val RETROARCH_ROM_EXTRA = "ROM"

        // ActivityInfo's singleInstancePerTask mode is API-gated in older Android
        // stubs; its stable framework value is 4.
        const val LAUNCH_SINGLE_INSTANCE_PER_TASK = 4
    }
}

/**
 * An emulator that is actually on the device.
 *
 * Carries both ids because they can differ: [packageName] is what is installed
 * and what an intent must be aimed at, while [spec] is the row it descends from
 * and holds everything about how to hand it a ROM. A nightly build has its own
 * package and its parent's contract.
 */
data class InstalledEmulator(
    val packageName: String,
    val displayName: String,
    val spec: EmulatorSpec,
)
