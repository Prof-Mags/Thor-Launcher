package com.thor.data.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.common.log.ThorLog
import com.thor.core.database.dao.WidgetDao
import com.thor.core.database.model.WidgetEntity
import com.thor.core.model.CellSpan
import com.thor.core.model.LauncherWidget
import com.thor.core.model.WidgetEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widgets the user has placed, and the ids they are made of.
 *
 * The reason this sits between the view model and [LauncherWidgetHost] rather
 * than letting callers use the host directly is that an id and a row have to
 * agree. The host hands out ids and the database remembers them, and every way
 * those two can disagree is a bug the user sees: an id with no row is a widget
 * paid for and never drawn, and a row with no id is a cell that renders as a
 * grey box forever. Allocation, storage and release are therefore all done here,
 * in pairs, and nothing else is given the host's `allocate` or `release`.
 */
@Singleton
class WidgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetDao: WidgetDao,
    private val host: LauncherWidgetHost,
    @Dispatcher(ThorDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    val widgets: Flow<List<WidgetEntry>> = widgetDao.observeAll()
        .map { rows -> rows.map(WidgetEntity::toDomain) }
        .distinctUntilChanged()

    /**
     * How many cells each placed widget takes, keyed by grid entry id.
     *
     * The shape the occupancy rules want: everything not in this map is one
     * cell, which is every other kind of entry.
     */
    val spans: Flow<Map<String, CellSpan>> = widgets
        .map { list -> list.associate { it.id to it.span } }
        .distinctUntilChanged()

    // ------------------------------------------------------------ the host

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun availableProviders(): List<AppWidgetProviderInfo> = host.availableProviders()

    /**
     * Everything installed, described well enough for a picker to draw it.
     *
     * Resolved here rather than in the UI because all of it is package-manager
     * work — labels, icons and preview bitmaps are loaded across a binder, once
     * per installed provider, and a device with fifty of them would do all of it
     * on the frame that opened the picker.
     */
    suspend fun options(): List<WidgetOption> = withContext(defaultDispatcher) {
        val packageManager = context.packageManager
        host.availableProviders().mapNotNull { info ->
            val component = info.provider ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(packageManager) }.getOrNull().orEmpty()
            if (label.isBlank()) return@mapNotNull null

            val appLabel = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(component.packageName, 0),
                ).toString()
            }.getOrNull().orEmpty()

            WidgetOption(
                component = component.flattenToString(),
                packageName = component.packageName,
                label = label,
                appLabel = appLabel,
                spanColumns = cellsFor(info.minWidth, info.targetCellWidth()),
                spanRows = cellsFor(info.minHeight, info.targetCellHeight()),
                preview = previewOf(info),
            )
        }
    }

    /**
     * How many cells a provider is asking for.
     *
     * Android's own conversion, which every launcher reimplements because the
     * platform does not expose it: a cell is nominally 70dp with a 30dp margin
     * already spent, so a widget declaring 110dp wants two. Newer providers say
     * so directly and those are believed instead.
     *
     * Clamped to what this grid will draw. A five-cell weather widget on a
     * five-column page is the whole row, which is not a size anyone chose.
     */
    private fun cellsFor(minSizeDp: Int, targetCells: Int): Int {
        if (targetCells > 0) return targetCells.coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN)
        val cells = (minSizeDp + CELL_MARGIN_DP + CELL_SIZE_DP - 1) / CELL_SIZE_DP
        return cells.coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN)
    }

    private fun AppWidgetProviderInfo.targetCellWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetCellWidth else 0

    private fun AppWidgetProviderInfo.targetCellHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) targetCellHeight else 0

    /**
     * The picture a provider offers of itself, if it is a sane size.
     *
     * Optional on purpose: `previewImage` is a resource in someone else's
     * package and there is no contract about how large it is. A provider
     * shipping a 2000px preview would otherwise be decoded at full size into a
     * list of fifty, so anything without usable intrinsic bounds is simply left
     * out and the picker falls back to the app's icon.
     */
    private fun previewOf(info: AppWidgetProviderInfo): Bitmap? = runCatching {
        val drawable = info.loadPreviewImage(context, 0) ?: return null
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return null
        if (width > PREVIEW_MAX_PX || height > PREVIEW_MAX_PX) return null
        drawable.toBitmap()
    }.getOrNull()

    fun providerFor(component: String): AppWidgetProviderInfo? = host.providerFor(component)

    fun infoFor(appWidgetId: Int): AppWidgetProviderInfo? = host.infoFor(appWidgetId)

    /** [viewContext] is the panel's, not the application's; see the host. */
    fun createView(viewContext: Context, appWidgetId: Int): View? =
        host.createView(viewContext, appWidgetId)

    /** Tells the provider the size of the box the grid has given it. */
    fun notifySize(appWidgetId: Int, widthDp: Int, heightDp: Int) =
        host.resize(appWidgetId, widthDp, heightDp)

    // ------------------------------------------------------------ placement

    /**
     * Takes an id and tries to bind it, without yet storing anything.
     *
     * The two halves are returned together because the caller cannot act on
     * either alone: an unbound id needs the consent dialog, and a bound one
     * still needs the provider's own setup screen when it has one. Nothing is
     * written to the database until [place], so abandoning the flow at any point
     * costs exactly one [discard].
     */
    suspend fun beginPlacement(provider: ComponentName): WidgetPlacementRequest =
        withContext(defaultDispatcher) {
            val appWidgetId = host.allocate()
            WidgetPlacementRequest(
                appWidgetId = appWidgetId,
                provider = provider,
                bound = host.bind(appWidgetId, provider),
            )
        }

    fun bindIntent(appWidgetId: Int, provider: ComponentName): Intent =
        host.bindIntent(appWidgetId, provider)

    /** The provider's setup activity, or null when it does not have one. */
    fun configureIntent(appWidgetId: Int): Intent? =
        host.infoFor(appWidgetId)?.let { info -> host.configureIntent(appWidgetId, info) }

    /**
     * Stores a widget the user finished placing.
     *
     * @return the grid entry id, so the caller can give it a cell.
     */
    suspend fun place(
        appWidgetId: Int,
        provider: ComponentName,
        label: String,
        span: CellSpan,
        nowEpochMs: Long,
    ): String = withContext(defaultDispatcher) {
        widgetDao.upsert(
            WidgetEntity(
                appWidgetId = appWidgetId,
                provider = provider.flattenToString(),
                label = label,
                spanColumns = span.columns.coerceAtLeast(1),
                spanRows = span.rows.coerceAtLeast(1),
                addedAtEpochMs = nowEpochMs,
            ),
        )
        WidgetEntry.idFor(appWidgetId)
    }

    /**
     * Stores one of the launcher's own widgets.
     *
     * None of [beginPlacement]'s ceremony applies: there is no provider to bind,
     * no consent to ask for and no setup screen to run, because nothing outside
     * this process is involved. It still needs a row, and that row still needs a
     * key — so it gets an id from below zero, where the platform's allocator
     * never goes, and [WidgetEntity.kind] records which sort it is rather than
     * leaving the sign of the id to say so.
     *
     * @return the grid entry id, so the caller can give it a cell.
     */
    suspend fun placeBuiltIn(
        widget: LauncherWidget,
        span: CellSpan,
        nowEpochMs: Long,
    ): String = withContext(defaultDispatcher) {
        val appWidgetId = (widgetDao.lowestId() ?: 0).coerceAtMost(0) - 1
        widgetDao.upsert(
            WidgetEntity(
                appWidgetId = appWidgetId,
                provider = widget.name,
                label = widget.title,
                spanColumns = span.columns.coerceAtLeast(1),
                spanRows = span.rows.coerceAtLeast(1),
                addedAtEpochMs = nowEpochMs,
                kind = WidgetEntity.KIND_BUILT_IN,
            ),
        )
        WidgetEntry.idFor(appWidgetId)
    }

    suspend fun resize(appWidgetId: Int, span: CellSpan) = withContext(defaultDispatcher) {
        widgetDao.resize(
            id = appWidgetId,
            columns = span.columns.coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN),
            rows = span.rows.coerceIn(WidgetEntry.MIN_SPAN, WidgetEntry.MAX_SPAN),
        )
    }

    /**
     * Forgets a widget entirely: the row goes and the id goes back.
     *
     * Both, in that order. Releasing without deleting leaves a row pointing at an
     * id the platform has reassigned, which is how one launcher's clock ends up
     * drawing another's calendar.
     */
    suspend fun remove(appWidgetId: Int) = withContext(defaultDispatcher) {
        widgetDao.delete(appWidgetId)
        // Only a real allocation is handed back. A built-in's id came from this
        // class, not from the platform, and asking the host to release one it
        // never issued is at best a no-op and at worst a log line per removal.
        if (appWidgetId >= 0) host.release(appWidgetId)
    }

    /** Hands back an id the user allocated and then abandoned. */
    fun discard(appWidgetId: Int) = host.release(appWidgetId)

    /**
     * Releases every id the platform holds for us that has no row.
     *
     * Run at startup. Each of these is a widget the user began adding and did not
     * finish — a cancelled picker, a refused consent, a crash — and left alone
     * they accumulate for the life of the install without ever being visible.
     */
    suspend fun reconcile() = withContext(defaultDispatcher) {
        // Built-in ids were never allocated, so they are not the host's to be
        // told about — and the host would report every one of them as an
        // orphan, which is a set this method deletes.
        val known = widgetDao.allIds().filter { it >= 0 }
        val orphans = host.orphans(known)
        if (orphans.isEmpty()) return@withContext
        ThorLog.i(TAG, "Releasing ${orphans.size} abandoned widget id(s)")
        orphans.forEach(host::release)
    }

    private companion object {
        const val TAG = "Widgets"

        /** Android's nominal widget cell, and the margin already counted in it. */
        const val CELL_SIZE_DP = 70
        const val CELL_MARGIN_DP = 30

        /** Beyond this a "preview" is a wallpaper, and is not worth decoding. */
        const val PREVIEW_MAX_PX = 1024
    }
}

/**
 * A widget part-way through being added.
 *
 * Carries the id because everything after this point needs it and nothing else
 * can produce it again: the id is what the consent dialog is about, what the
 * configuration activity is handed, and what has to be released if the user
 * changes their mind at either step.
 */
data class WidgetPlacementRequest(
    val appWidgetId: Int,
    val provider: ComponentName,
    /** False when the user has to be asked; see [LauncherWidgetHost.bind]. */
    val bound: Boolean,
)

/**
 * An installed widget, as the picker needs to show it.
 *
 * Carries a [Bitmap] rather than a resource id because the resource belongs to
 * the provider's package and resolving one from another process needs a context
 * for *that* package — work the picker should not be doing per row while the
 * user scrolls.
 */
data class WidgetOption(
    val component: String,
    val packageName: String,
    val label: String,
    val appLabel: String,
    val spanColumns: Int,
    val spanRows: Int,
    /** Null when the provider offers none, or offers one too large to be one. */
    val preview: Bitmap? = null,
)
