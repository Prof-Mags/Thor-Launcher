package com.thor.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's window onto other applications' widgets.
 *
 * A widget is not drawn by us. The provider builds a `RemoteViews` in its own
 * process and the host inflates it here, which is why this is a platform object
 * with a lifecycle rather than a repository: while the host is not listening,
 * every widget on the grid is a frozen picture of whenever it last updated.
 *
 * The id is the whole of the bookkeeping. [allocate] takes one from the
 * platform, and from that moment it is a resource this launcher owns — if the
 * process dies before the id reaches the database it is leaked, and the only
 * thing that can find it again is [orphans], because the platform will happily
 * keep it forever.
 */
@Singleton
class LauncherWidgetHost @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private var listening = false

    /**
     * Starts delivering updates to inflated widgets.
     *
     * Paired with [stopListening] on the launcher's own visibility rather than
     * on its process: a host that listens while the launcher is behind a game is
     * paying for clock ticks and weather refreshes nobody can see.
     */
    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }
            .onSuccess { listening = true }
            .onFailure { ThorLog.w(TAG, "Widget host could not start listening", it) }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
            .onFailure { ThorLog.w(TAG, "Widget host could not stop listening", it) }
        listening = false
    }

    /** Every widget the device offers, in the order the picker should show them. */
    fun availableProviders(): List<AppWidgetProviderInfo> =
        runCatching {
            manager.installedProviders.sortedBy { it.loadLabel(appContext.packageManager).lowercase() }
        }.getOrElse {
            ThorLog.w(TAG, "Could not list widget providers", it)
            emptyList()
        }

    fun providerFor(component: String): AppWidgetProviderInfo? {
        val name = ComponentName.unflattenFromString(component) ?: return null
        return runCatching { manager.installedProviders.firstOrNull { it.provider == name } }
            .getOrNull()
    }

    /** Takes an id from the platform. The caller must store it or [release] it. */
    fun allocate(): Int = host.allocateAppWidgetId()

    /**
     * Binds an id to a provider without asking, which only works sometimes.
     *
     * A launcher holding `BIND_APPWIDGET` may bind silently; that permission is
     * signature-level, so on an ordinary install this returns false and the user
     * has to be asked with [bindIntent]. Trying first and falling back is the
     * documented route — there is no way to query the answer that is any cheaper
     * than attempting it.
     */
    fun bind(appWidgetId: Int, provider: ComponentName, user: Bundle? = null): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(appWidgetId, provider, user) }
            .getOrElse {
                ThorLog.w(TAG, "Silent bind refused for $provider", it)
                false
            }

    /** The consent dialog to run when [bind] refuses. */
    fun bindIntent(appWidgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }

    /**
     * The provider's own setup screen, when it has one.
     *
     * Null when it does not, which is the common case. A widget that declares a
     * configuration activity and is placed without running it renders as an
     * empty box — the provider is waiting for settings it will never be given.
     */
    fun configureIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent? {
        val activity = info.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = activity
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** The provider bound to [appWidgetId], or null when nothing is. */
    fun infoFor(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /**
     * Inflates the provider's remote views for [appWidgetId].
     *
     * Takes a plain [Context] rather than an Activity because the bottom panel is
     * not one: the grid is drawn inside a `Presentation` on the second display,
     * and a host view inflated against the activity would resolve its
     * configuration — density, size, and therefore the layout the provider picks
     * — against the wrong screen.
     */
    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = infoFor(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }
            .getOrElse {
                ThorLog.w(TAG, "Could not inflate widget $appWidgetId", it)
                null
            }
    }

    /**
     * Tells the provider how much room it has, in dp.
     *
     * Worth doing rather than leaving to the default: many providers switch
     * layout on the size they are given, and one never told its size draws the
     * layout it was authored against instead of the one that fits.
     */
    fun resize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        runCatching { manager.updateAppWidgetOptions(appWidgetId, options) }
            .onFailure { ThorLog.w(TAG, "Could not resize widget $appWidgetId", it) }
    }

    /** Hands an id back to the platform. */
    fun release(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
            .onFailure { ThorLog.w(TAG, "Could not release widget $appWidgetId", it) }
    }

    /**
     * Ids the platform is holding for us that the launcher no longer knows about.
     *
     * These accumulate: an id is allocated before the user has chosen anything,
     * and every abandoned picker, refused consent or crash between allocating and
     * storing leaves one behind. Nothing surfaces them — the widget is invisible
     * because it was never placed — so they are only ever found by asking the
     * platform what it thinks we own and subtracting what we know we do.
     */
    fun orphans(known: Collection<Int>): List<Int> {
        val owned = runCatching { host.appWidgetIds }.getOrElse {
            ThorLog.w(TAG, "Could not list allocated widget ids", it)
            return emptyList()
        }
        val keep = known.toSet()
        return owned.filterNot { it in keep }
    }

    private companion object {
        const val TAG = "WidgetHost"

        /**
         * This launcher's host id, which must never change.
         *
         * The platform keys every allocation to the pair of package and host id,
         * so changing it abandons every widget the user has placed — they stay
         * allocated, stop updating, and cannot be recovered by the new host.
         */
        const val HOST_ID = 0x10C1
    }
}
