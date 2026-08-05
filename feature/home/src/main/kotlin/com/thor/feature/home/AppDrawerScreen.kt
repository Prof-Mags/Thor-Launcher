package com.thor.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderStyle
import com.thor.core.model.GridSpec
import com.thor.core.ui.component.AnimatedWallpaperBackground
import com.thor.feature.home.grid.GridCellData
import com.thor.feature.home.grid.GridPager
import com.thor.feature.home.grid.PageIndicators

/**
 * Every installed application, in the launcher's own grid.
 *
 * Not merely "laid out like" the home grid — it is the same [GridPager], the
 * same cells, the same wallpaper and the same pinch-to-resize. It previously had
 * a header, its own padding and its own page dots, which made a surface the user
 * had to learn separately; the only real differences are that the contents are
 * computed (alphabetical, all apps) rather than hand-placed, and that nothing
 * here can be rearranged.
 *
 * @param dockClearance space reserved at the bottom so the dock, which stays
 *   visible over this surface, cannot cover the last row
 */
@Composable
fun AppDrawerScreen(
    apps: List<AppEntry>,
    spec: GridSpec,
    currentPage: Int,
    cursor: CursorPosition,
    touchEnabled: Boolean,
    wallpaper: AnimatedWallpaper,
    wallpaperUri: String?,
    dockClearance: Dp,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPinch: (Float) -> Unit,
    /**
     * The user's own preference, obeyed here as it is on the home grid.
     *
     * Not cosmetic: the dots live *in* the column, so a surface that draws them
     * when the grid does not gives its pager a shorter box and lays the same
     * matrix out at a smaller cell size. Sharing the setting is what keeps a
     * grid and a drawer at the same size at every preset.
     */
    showPageIndicators: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pageCount = pageCountFor(apps.size, spec.cellsPerPage)

    Box(modifier = modifier.fillMaxSize()) {
        // The same background as the grid, so opening the drawer reads as the
        // same surface showing different contents rather than a modal taking over.
        AnimatedWallpaperBackground(
            wallpaper = wallpaper,
            imageUri = wallpaperUri,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            GridPager(
                spec = spec,
                pageCount = pageCount,
                currentPage = currentPage,
                cursor = cursor,
                touchEnabled = touchEnabled,
                // The drawer is a fixed view of what is installed, so it never
                // enters edit mode.
                jiggling = false,
                folderStyle = FolderStyle.GLYPH,
                prefetchRadius = 1,
                // The list is already sorted upstream; it is the drawer's sole
                // content dependency and need not be rebuilt for cursor moves.
                contentVersion = apps,
                onCellTapped = onCellTapped,
                onCellLongPressed = onCellLongPressed,
                onPageChanged = onPageChanged,
                onPinch = onPinch,
                cellAt = { page, row, column ->
                    val index = page * spec.cellsPerPage + row * spec.columns + column
                    GridCellData(entry = apps.getOrNull(index))
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            // The same dots, in the same place, as the home grid — the drawer
            // used to draw its own at a different size.
            if (showPageIndicators) {
                PageIndicators(
                    pageCount = pageCount,
                    currentPage = currentPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ThorTheme.dimens.spacingTiny),
                )
            }

            Spacer(modifier = Modifier.height(dockClearance))
        }
    }
}

/** Pages needed to hold [count] entries, never fewer than one. */
internal fun pageCountFor(count: Int, cellsPerPage: Int): Int {
    if (cellsPerPage <= 0) return 1
    if (count <= 0) return 1
    return (count + cellsPerPage - 1) / cellsPerPage
}
