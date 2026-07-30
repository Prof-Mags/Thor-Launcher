package com.thor.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.FolderStyle
import com.thor.core.model.GridEntry
import com.thor.core.model.GridSpec
import com.thor.core.model.Platform
import com.thor.feature.home.CursorPosition

/**
 * Everything one cell needs in order to draw itself.
 *
 * Bundled so [GridPager] can stay agnostic about where cells come from — the
 * home grid resolves them from stored placements, the app drawer from an index
 * into a sorted list, and neither difference reaches the layout.
 */
data class GridCellData(
    val entry: GridEntry? = null,
    val platform: Platform? = null,
    val isHeld: Boolean = false,
    /** Artwork of the first few children, for the folder preview styles. */
    val folderPreview: List<String?> = emptyList(),
)

/**
 * The paged icon matrix shared by the home grid and the app drawer.
 *
 * There used to be two of these. The drawer had its own header, its own
 * padding, its own page dots and no pinch handling, so it read as a different
 * kind of surface and had to be learned separately — this is the one layout,
 * and the drawer is now the same grid over a different set of entries.
 *
 * Pages are laid out as a fixed matrix rather than as a lazy list. That is the
 * right trade here: a page holds at most `columns × rows` cells, the pager only
 * composes the neighbouring pages, and a fixed matrix is what lets a cell keep a
 * stable position when its neighbours are empty — which a lazy list cannot
 * express. Library size therefore affects the number of pages, never the amount
 * of composed UI.
 */
@Composable
fun GridPager(
    spec: GridSpec,
    pageCount: Int,
    currentPage: Int,
    cursor: CursorPosition,
    touchEnabled: Boolean,
    /** True while edit mode is active, so occupied cells wobble. */
    jiggling: Boolean,
    folderStyle: FolderStyle,
    prefetchRadius: Int,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPinch: (Float) -> Unit,
    cellAt: (page: Int, row: Int, column: Int) -> GridCellData,
    modifier: Modifier = Modifier,
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, safePageCount - 1),
        pageCount = { safePageCount },
    )

    // Programmatic page changes (shoulder buttons, edge flips) drive the pager.
    LaunchedEffect(currentPage, safePageCount) {
        val target = currentPage.coerceIn(0, safePageCount - 1)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Swipes drive the view model, so both input paths converge on one value.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }

    Box(modifier = modifier.pinchToResize(onPinch)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = prefetchRadius,
        ) { pageIndex ->
            GridMatrix(
                spec = spec,
                isCurrentPage = pageIndex == currentPage,
                cursor = cursor,
                touchEnabled = touchEnabled,
                jiggling = jiggling,
                folderStyle = folderStyle,
                onCellTapped = onCellTapped,
                onCellLongPressed = onCellLongPressed,
                cellAt = { row, column -> cellAt(pageIndex, row, column) },
            )
        }
    }
}

/**
 * One page's matrix of cells.
 *
 * Spacing is measured, not assumed. The page's own size decides the cell edge
 * length, and the gap and margin are then taken as proportions of it, so the
 * grid stays evenly spaced at every column count the user can pinch to instead
 * of tightening up as the cells shrink.
 */
@Composable
private fun GridMatrix(
    spec: GridSpec,
    isCurrentPage: Boolean,
    cursor: CursorPosition,
    touchEnabled: Boolean,
    jiggling: Boolean,
    folderStyle: FolderStyle,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    cellAt: (row: Int, column: Int) -> GridCellData,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Solving gap and margin against the cell size they themselves affect
        // would be circular, so the cell size is estimated from the raw viewport
        // first. The estimate is only used to pick proportions, and a few
        // percent of error there is invisible.
        val nominalCell = minOf(
            maxWidth / spec.columns.coerceAtLeast(1),
            maxHeight / spec.rows.coerceAtLeast(1),
        )
        val gap = (nominalCell * spec.spacingFraction).coerceIn(MIN_GAP.dp, MAX_GAP.dp)
        val margin = (nominalCell * spec.paddingFraction).coerceIn(MIN_MARGIN.dp, MAX_MARGIN.dp)

        Column(
            modifier = Modifier.fillMaxSize().padding(margin),
            // The gap lives between cells rather than inside them. Padding each
            // cell instead — which is what this did — shrinks every icon and
            // still leaves a double gap at the page edges.
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(spec.rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(spec.columns) { column ->
                        val data = cellAt(row, column)
                        val isFocused = isCurrentPage &&
                            cursor.row == row &&
                            cursor.column == column

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                // Touch handling is attached conditionally so
                                // the controller-only setting genuinely stops
                                // taps rather than ignoring them downstream.
                                .then(
                                    if (touchEnabled) {
                                        Modifier.pointerInput(row, column, data.entry?.id) {
                                            detectTapGestures(
                                                onTap = { onCellTapped(row, column) },
                                                onLongPress = {
                                                    onCellLongPressed(row, column)
                                                },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            GridCell(
                                entry = data.entry,
                                spec = spec,
                                focused = isFocused,
                                isHeld = data.isHeld,
                                jiggling = jiggling && data.entry != null,
                                platform = data.platform,
                                folderStyle = folderStyle,
                                folderPreview = data.folderPreview,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pinch-to-resize, watched on the initial pass.
 *
 * The pager reads drags on the main pass and would otherwise treat a two-finger
 * gesture as a page swipe — which is why attaching `detectTransformGestures` to
 * the pager's own modifier never fired. Single-pointer events are deliberately
 * left alone so swiping between pages still works.
 *
 * A pinch also reports a zoom delta every frame, and each one would otherwise
 * become a settings write, serialising and flushing the whole preferences
 * document dozens of times a second. Deltas accumulate and only commit once they
 * add up to a change worth persisting.
 */
fun Modifier.pinchToResize(onPinch: (Float) -> Unit): Modifier = composed {
    var pendingZoom by remember { mutableFloatStateOf(1f) }

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pendingZoom = 1f

            var event: PointerEvent
            do {
                event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } < 2) continue

                val zoom = event.calculateZoom()
                if (zoom != 1f && zoom > 0f) {
                    pendingZoom *= zoom
                    if (pendingZoom >= 1f + PINCH_COMMIT_THRESHOLD ||
                        pendingZoom <= 1f - PINCH_COMMIT_THRESHOLD
                    ) {
                        onPinch(pendingZoom)
                        pendingZoom = 1f
                    }
                }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * The page dots beneath a grid.
 *
 * Shared with the app drawer so both surfaces indicate paging the same way —
 * the drawer used to draw its own, at a different size and spacing.
 */
@Composable
fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) ACTIVE_DOT.dp else INACTIVE_DOT.dp)
                    .background(
                        color = if (active) {
                            colors.cursor
                        } else {
                            colors.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = ThorTheme.shapes.pill,
                    ),
            )
        }
    }
}

/** Cumulative zoom change required before a pinch is written to settings. */
private const val PINCH_COMMIT_THRESHOLD = 0.12f

private const val ACTIVE_DOT = 8
private const val INACTIVE_DOT = 5

/**
 * Bounds on the derived spacing.
 *
 * The proportional gap is what keeps the grid balanced; these only stop the
 * extremes from becoming absurd — a hairline at three columns, or a gap wider
 * than the icon at ten.
 */
private const val MIN_GAP = 6
private const val MAX_GAP = 28
private const val MIN_MARGIN = 6
private const val MAX_MARGIN = 32
