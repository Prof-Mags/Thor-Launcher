package com.thor.data.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.common.text.TitleNormalizer
import com.thor.core.database.model.AppEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates launchable applications.
 *
 * [LauncherApps] is used in preference to `PackageManager.queryIntentActivities`
 * because it is the only API that reports apps belonging to work profiles and
 * secondary users, which a launcher is expected to show. The package manager is
 * still consulted for install timestamps, which `LauncherApps` does not expose.
 */
@Singleton
class AppScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager
    private val packageManager: PackageManager = context.packageManager

    /**
     * Every launchable activity across all profiles.
     *
     * @param includeSystemApps when false, apps flagged as system are excluded
     *   unless they are also user-updated (Chrome, Maps and friends).
     */
    suspend fun scan(includeSystemApps: Boolean): List<AppEntity> = withContext(ioDispatcher) {
        val ownPackage = context.packageName
        val now = System.currentTimeMillis()

        userManager.userProfiles.flatMap { userHandle ->
            val serial = userManager.getSerialNumberForUser(userHandle)
            val activities = runCatching {
                launcherApps.getActivityList(null, userHandle)
            }.getOrElse { error ->
                ThorLog.w("AppScanner", "Could not query profile $serial", error)
                emptyList()
            }

            activities.mapNotNull { info ->
                val packageName = info.componentName.packageName
                // The launcher must not list itself as an app to launch.
                if (packageName == ownPackage) return@mapNotNull null

                val appInfo = runCatching {
                    packageManager.getApplicationInfo(packageName, 0)
                }.getOrNull()

                val isSystem = appInfo?.let {
                    it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 &&
                        it.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                } ?: false

                if (isSystem && !includeSystemApps) return@mapNotNull null

                val label = info.label?.toString().orEmpty().ifEmpty { packageName }
                val packageInfo = runCatching {
                    packageManager.getPackageInfo(packageName, 0)
                }.getOrNull()

                AppEntity(
                    id = appEntryId(packageName, serial),
                    title = label,
                    sortTitle = TitleNormalizer.sortKey(label),
                    packageName = packageName,
                    activityName = info.componentName.className,
                    userSerial = serial,
                    versionName = packageInfo?.versionName,
                    installedAtEpochMs = packageInfo?.firstInstallTime ?: now,
                    updatedAtEpochMs = packageInfo?.lastUpdateTime ?: now,
                    isEmulator = EmulatorRegistry.isKnownEmulator(packageName) ||
                        EmulatorRegistry.looksLikeEmulator(packageName, label),
                    isSystemApp = isSystem,
                )
            }
        }.distinctBy { it.id }
    }

    /** Resolves a single package, for handling install broadcasts incrementally. */
    suspend fun scanPackage(packageName: String): AppEntity? = withContext(ioDispatcher) {
        val userHandle = Process.myUserHandle()
        val serial = userManager.getSerialNumberForUser(userHandle)
        val info = runCatching {
            launcherApps.getActivityList(packageName, userHandle).firstOrNull()
        }.getOrNull() ?: return@withContext null

        val label = info.label?.toString().orEmpty().ifEmpty { packageName }
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val appInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()

        AppEntity(
            id = appEntryId(packageName, serial),
            title = label,
            sortTitle = TitleNormalizer.sortKey(label),
            packageName = packageName,
            activityName = info.componentName.className,
            userSerial = serial,
            versionName = packageInfo?.versionName,
            installedAtEpochMs = packageInfo?.firstInstallTime ?: System.currentTimeMillis(),
            updatedAtEpochMs = packageInfo?.lastUpdateTime ?: System.currentTimeMillis(),
            isEmulator = EmulatorRegistry.isKnownEmulator(packageName) ||
                EmulatorRegistry.looksLikeEmulator(packageName, label),
            isSystemApp = appInfo?.let {
                it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            } ?: false,
        )
    }

    /** True when the device has a handler for [intent]. */
    fun canHandle(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    companion object {
        /**
         * App ids are derived from package + profile rather than random, so a
         * reinstall keeps the app in the same grid cell.
         */
        fun appEntryId(packageName: String, userSerial: Long): String =
            "app:$packageName:$userSerial"
    }
}
