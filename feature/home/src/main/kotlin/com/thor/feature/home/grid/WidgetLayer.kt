package com.thor.feature.home.grid

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GridEntry
import com.thor.core.model.GridSpec
import com.thor.core.model.WidgetEntry
import com.thor.feature.home.CursorPosition

/**
 * The measured geometry of one grid page.
 *
 * Cell size is not a setting — the page's own size decides it, and the gap and
 * margin are proportions of that. Anything drawn *over* the matrix rather than
 * inside it has to solve the same arithmetic to land on the cells, so it is
 * solved once here and handed out.
 */
@Immutable
data class GridMetrics(
    val cellWidth: Dp,
    val cellHeight: Dp,
    val gap: Dp,
    val margin: Dp,
) {
    fun x(column: Int): Dp = margin + (cellWidth + gap) * column

    fun y(row: Int): Dp = margin + (cellHeight + gap) * row

    /** Width of [span] cells, including the gaps swallowed between them. */
    fun width(span: Int): Dp = cellWidth * span + gap * (span - 1).coerceAtLeast(0)

    fun height(span: Int): Dp = cellHeight * span + gap * (span - 1).coerceAtLeast(0)
}

/** A widget and the cell it is anchored to. */
@Immutable
data class PlacedWidget(
    val entry: WidgetEntry,
    val row: Int,
    val column: Int,
)

/**
 * Every placed widget, by page.
 *
 * A wrapper rather than the bare map because Compose treats `Map` as unstable,
 * and anything capturing one is unstable in turn — including the lambda that
 * draws this layer, which would then be rebuilt on every cursor move and take
 * the whole cell matrix with it.
 */
@Immutable
data class PlacedWidgets(val byPage: Map<Int, List<PlacedWidget>> = emptyMap()) {
    fun on(page: Int): List<PlacedWidget> = byPage[page].orEmpty()
}

/**
 * Widgets, drawn over the cell matrix rather than inside it.
 *
 * The matrix is a nested `repeat(rows) { repeat(columns) }` of equally weighted
 * boxes, which cannot express one child covering four of them. Teaching it spans
 * would mean replacing the layout every other surface shares — the app drawer
 * included — for a case only the home grid has.
 *
 * Positioning over the top costs one thing: the cells underneath a widget must
 * be left empty by whoever supplies them, or an icon is drawn beneath it. That
 * is [GridFootprint]'s job on the data side, and the reason the two have to
 * agree about geometry is why [GridMetrics] is measured rather than assumed.
 */
@Composable
fun WidgetLayer(
    widgets: List<PlacedWidget>,
    metrics: GridMetrics,
    spec: GridSpec,
    /**
     * The live cursor, read inside each frame rather than here.
     *
     * A plain value would invalidate this whole layer — and the cell matrix it
     * is drawn over — every time the cursor moved. Read one level down, a move
     * recomposes only the widget it arrived at and the one it left, which is the
     * same bargain the cells make; see `GridCellSlot`.
     */
    cursor: State<CursorPosition>,
    onThisPage: Boolean,
    editing: Boolean,
    /** The entry the cursor is carrying, so a held widget shows it is held. */
    heldId: String?,
    createView: (Context, Int) -> View?,
    onMeasured: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> Unit,
    /** What the launcher's own widgets draw from; see [LauncherWidgetCard]. */
    widgetData: LauncherWidgetData,
    onLaunch: (GridEntry) -> Unit,
    /** Both report the widget's *anchor* cell, so the grid's own handlers fit. */
    onTapped: (row: Int, column: Int) -> Unit,
    onLongPressed: (row: Int, column: Int) -> Unit,
) {
    widgets.forEach { placed ->
        // Clipped to the page, so a widget that outlived a pinch down to a
        // smaller matrix still draws the part of itself that is on screen.
        val spanColumns = placed.entry.spanColumns
            .coerceIn(1, (spec.columns - placed.column).coerceAtLeast(1))
        val spanRows = placed.entry.spanRows
            .coerceIn(1, (spec.rows - placed.row).coerceAtLeast(1))

        key(placed.entry.appWidgetId) {
            WidgetFrame(
                entry = placed.entry,
                cursor = cursor,
                onThisPage = onThisPage,
                row = placed.row,
                column = placed.column,
                spanColumns = spanColumns,
                spanRows = spanRows,
                held = placed.entry.id == heldId,
                editing = editing,
                createView = createView,
                onMeasured = onMeasured,
                widgetData = widgetData,
                onLaunch = onLaunch,
                onTapped = { onTapped(placed.row, placed.column) },
                onLongPressed = { onLongPressed(placed.row, placed.column) },
                modifier = Modifier
                    .offset(x = metrics.x(placed.column), y = metrics.y(placed.row))
                    .size(
                        width = metrics.width(spanColumns),
                        height = metrics.height(spanRows),
                    ),
            )
        }
    }
}

/**
 * One widget's box.
 *
 * The provider draws the inside of it; this owns the plate behind, the cursor
 * ring and — while the grid is being arranged — a lid over the whole thing.
 *
 * The lid is the only way the launcher can have a widget's touches. A widget is
 * a live view from another process and it takes every event that lands on it, so
 * there is no gesture on top of one that the launcher can also see. Rather than
 * fight that, the two states are kept plainly separate: outside edit mode the
 * widget is a widget and the touches are its own, and inside edit mode the lid
 * takes them all and the widget is inert. The way to arrange a widget by hand is
 * therefore to enter edit mode first — which is also the answer for every other
 * cell, so it is one rule rather than an exception.
 *
 * The controller is not affected either way: the cursor lands on a widget's
 * cells and Y opens its menu, in or out of edit mode.
 */
@Composable
private fun WidgetFrame(
    entry: WidgetEntry,
    cursor: State<CursorPosition>,
    onThisPage: Boolean,
    row: Int,
    column: Int,
    spanColumns: Int,
    spanRows: Int,
    held: Boolean,
    editing: Boolean,
    createView: (Context, Int) -> View?,
    onMeasured: (appWidgetId: Int, widthDp: Int, heightDp: Int) -> Unit,
    widgetData: LauncherWidgetData,
    onLaunch: (GridEntry) -> Unit,
    onTapped: () -> Unit,
    onLongPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val density = LocalDensity.current
    val context = LocalContext.current

    // Read here, inside this frame's own restart scope, so a cursor move
    // invalidates the two widgets it concerns and nothing else.
    val position = cursor.value
    val focused = onThisPage &&
        position.row in row until row + spanRows &&
        position.column in column until column + spanColumns

    /*
     * Inflated once and kept.
     *
     * `AndroidView`'s factory already runs once per composition slot, but a
     * widget is expensive enough — an inter-process inflate of another app's
     * `RemoteViews` — that it is worth being explicit: the pager composes the
     * neighbouring pages too, and a widget rebuilt on every page settle is a
     * visible stutter on the panel.
     */
    val hostView = remember(entry.appWidgetId, entry.builtIn) {
        // Nothing to inflate for one of the launcher's own — it is Compose, not
        // a `RemoteViews` from another process — and asking the host for a view
        // on an id it never allocated would log a failure per composition.
        if (entry.isBuiltIn) null else createView(context, entry.appWidgetId)
    }

    /*
     * The ring gets pixels of its own.
     *
     * An app widget is a real View from another process, and a View carrying any
     * elevation is composited by the platform rather than in the order Compose
     * drew it — so a highlight painted by an ancestor over the same pixels can
     * end up underneath it. Insetting the content by the ring's width means the
     * two never share a pixel, and the highlight is right whatever the provider
     * does with its own layers.
     */
    Box(
        modifier = modifier
            .thorCursor(focused = focused, shape = shape)
            .clip(shape)
            .background(colors.surfaceElevated, shape)
            .padding(FRAME_INSET.dp)
            .onSizeChanged { size ->
                // Only a provider needs telling; the launcher's own widgets are
                // measured by the same layout pass that sizes this box.
                if (entry.isBuiltIn || size.width == 0 || size.height == 0) return@onSizeChanged
                with(density) {
                    onMeasured(
                        entry.appWidgetId,
                        size.width.toDp().value.toInt(),
                        size.height.toDp().value.toInt(),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val builtIn = entry.builtIn
        if (builtIn != null) {
            LauncherWidgetCard(
                widget = builtIn,
                data = widgetData,
                onLaunch = onLaunch,
            )
        } else if (hostView == null) {
            UnavailableWidget(entry)
        } else {
            AndroidView(
                factory = { hostView },
                // The layout params come from us, not from the provider: a host
                // view left at its own wrap-content collapses to nothing inside
                // a box that was sized by the grid.
                onReset = null,
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                },
            )
        }

        if (editing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = if (held) HELD_LID_ALPHA else EDIT_LID_ALPHA))
                    .border(if (held) 2.dp else 1.dp, colors.cursor, shape)
                    .pointerInput(entry.appWidgetId) {
                        detectTapGestures(
                            onTap = { onTapped() },
                            onLongPress = { onLongPressed() },
                        )
                    },
            )
        }
    }
}

/**
 * What is drawn when the provider is gone.
 *
 * An uninstalled app leaves its widgets bound to ids that no longer resolve, and
 * the platform's own answer is an empty grey rectangle. Saying which widget it
 * was is the difference between something the user can act on and a hole.
 */
@Composable
private fun UnavailableWidget(entry: WidgetEntry) {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Widgets,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp).align(Alignment.TopCenter),
        )
        Text(
            text = "${entry.title} is unavailable",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The gutter the cursor ring lives in.
 *
 * Wide enough for the thickest cursor the theme offers, so the ring always lands
 * on pixels the widget's own content never touches.
 */
private const val FRAME_INSET = 3

/** How far a widget is dimmed while the grid is being arranged. */
private const val EDIT_LID_ALPHA = 0.35f

/** Darker again while it is the thing being carried, so the two read apart. */
private const val HELD_LID_ALPHA = 0.55f
