package com.thor.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.DockSettings
import com.thor.core.model.GameEntry
import com.thor.core.model.LauncherAction
import com.thor.core.model.SortOrder
import com.thor.core.ui.component.AnimatedWallpaperBackground
import com.thor.feature.home.component.ContextAction
import com.thor.feature.home.component.EntryContextMenu
import com.thor.feature.home.component.FloatingDock
import com.thor.feature.home.component.FolderPickerDialog
import com.thor.feature.home.component.FolderPickerState
import com.thor.feature.home.component.LauncherGrid
import com.thor.feature.home.component.PageIndicators
import com.thor.feature.home.component.SideMenu
import com.thor.feature.home.component.SideMenuAction
import com.thor.feature.home.component.SortDialog
import com.thor.feature.home.component.dockHeightFor

/**
 * The bottom display: wallpaper, grid, page indicators, dock and Start panel.
 */
@Composable
fun BottomScreen(
    state: LauncherUiState,
    dockSettings: DockSettings,
    wallpaper: AnimatedWallpaper,
    wallpaperUri: String?,
    showPageIndicators: Boolean,
    currentSort: SortOrder,
    focusedDockSlot: Int?,
    focusedMenuAction: SideMenuAction?,
    onCellTapped: (Int, Int) -> Unit,
    onCellLongPressed: (Int, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onPinch: (Float) -> Unit,
    onDockSlotSelected: (Int) -> Unit,
    onDockAction: (LauncherAction) -> Unit,
    onMenuAction: (SideMenuAction) -> Unit,
    onMenuDismissed: () -> Unit,
    onContextAction: (ContextAction) -> Unit,
    onContextMenuDismissed: () -> Unit,
    appDrawer: AppDrawerState,
    onDrawerCellTapped: (Int, Int) -> Unit,
    onDrawerCellLongPressed: (Int, Int) -> Unit,
    onDrawerPageChanged: (Int) -> Unit,
    sortPicker: SortPickerState,
    onSortPicked: (SortOrder) -> Unit,
    onSortDirectionToggled: () -> Unit,
    onSortDismissed: () -> Unit,
    folderPicker: FolderPickerState,
    onFolderPicked: (String) -> Unit,
    onFolderCreated: () -> Unit,
    onFolderPickerDismissed: () -> Unit,
    /** Whether any non-smart folder exists, so filing can be offered. */
    foldersExist: Boolean,
    /** Whether the context-menu entry currently sits inside a folder. */
    entryInFolder: Boolean,
    /** Leaves the open folder and returns the grid to the page it came from. */
    onFolderClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens

    // Clearance comes from the dock itself so the two cannot drift apart and
    // leave the bottom row of icons half-covered.
    val dockClearance = if (dockSettings.visible) {
        dockHeightFor(dockSettings) + dimens.spacingSmall
    } else {
        0.dp
    }

    // Drives the Adaptive wallpaper: the highlighted game's system colours the
    // background, so it shifts as the cursor crosses platforms. Null for apps,
    // folders and empty cells, which falls back to the theme's own accent.
    val adaptiveTint = (state.selection as? GameEntry)
        ?.let { game -> state.platformsById[game.platformId] }
        ?.let { platform -> Color(platform.accentArgb) }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedWallpaperBackground(
            wallpaper = wallpaper,
            imageUri = wallpaperUri,
            accentTint = adaptiveTint,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            /*
             * In the layout, not over it.
             *
             * Floating this above the grid put it straight through the top row of
             * cells — the grid takes all the height it is given, so anything laid over
             * it lands on icons. As a row of its own the grid simply gets the height
             * that is left, and nothing overlaps.
             */
            state.openFolder?.let { folder ->
                OpenFolderBanner(
                    title = folder.title,
                    count = state.openFolderContents.size,
                    onClose = onFolderClosed,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = dimens.spacingSmall, bottom = dimens.spacingTiny),
                )
            }

            LauncherGrid(
                state = state,
                onCellTapped = onCellTapped,
                onCellLongPressed = onCellLongPressed,
                onPageChanged = onPageChanged,
                onPinch = onPinch,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            if (showPageIndicators) {
                PageIndicators(
                    // The folder's own pages while one is open, so the dots match
                    // what the grid is actually showing.
                    pageCount = state.visiblePageCount,
                    currentPage = state.currentPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimens.spacingTiny),
                )
            }

            Spacer(modifier = Modifier.height(dockClearance))
        }

        if (state.isScanning) {
            ScanBanner(
                label = state.scanLabel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(dimens.spacingSmall),
            )
        }

        // Edit mode has no other affordance: the icons wobble, but nothing says
        // what the buttons now do, and grab-and-drop is not guessable.
        if (state.editMode.isActive) {
            EditModeBanner(
                holding = state.editMode is EditMode.Holding,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(dimens.spacingSmall),
            )
        }

        // Above the grid but below the menus, so a long press in the drawer can
        // still raise a context menu over it.
        if (appDrawer.visible) {
            AppDrawerScreen(
                apps = appDrawer.apps,
                spec = state.spec,
                currentPage = appDrawer.page,
                cursor = appDrawer.cursor,
                touchEnabled = state.touchEnabled,
                wallpaper = wallpaper,
                wallpaperUri = wallpaperUri,
                dockClearance = dockClearance,
                onCellTapped = onDrawerCellTapped,
                onCellLongPressed = onDrawerCellLongPressed,
                onPageChanged = onDrawerPageChanged,
                onPinch = onPinch,
            )
        }

        // Drawn after the drawer so it stays visible over it — the dock is how
        // the drawer was opened and how it is closed again, and hiding it there
        // made the launcher feel like it had switched to a different app.
        FloatingDock(
            settings = dockSettings,
            focusedSlot = focusedDockSlot,
            iconShape = state.spec.iconShape,
            onSlotSelected = onDockSlotSelected,
            onSlotActivated = onDockAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimens.spacingSmall),
        )

        SortDialog(
            visible = sortPicker.visible,
            currentOrder = sortPicker.order,
            descending = sortPicker.descending,
            focusedIndex = sortPicker.focusedIndex,
            onPick = onSortPicked,
            onToggleDirection = onSortDirectionToggled,
            onDismiss = onSortDismissed,
        )

        SideMenu(
            visible = state.sideMenuOpen,
            focusedAction = focusedMenuAction,
            onAction = onMenuAction,
            onDismiss = onMenuDismissed,
        )

        FolderPickerDialog(
            state = folderPicker,
            onPick = onFolderPicked,
            onCreateFolder = onFolderCreated,
            onDismiss = onFolderPickerDismissed,
        )

        EntryContextMenu(
            entry = state.contextMenuEntry,
            hasSecondScreen = state.hasSecondScreen,
            focusedIndex = state.contextMenuIndex,
            fromDrawer = appDrawer.visible,
            foldersExist = foldersExist,
            inFolder = entryInFolder,
            onGrid = state.contextMenuEntry?.let { entry ->
                state.placements.any { it.entryId == entry.id }
            } ?: true,
            onAction = onContextAction,
            onDismiss = onContextMenuDismissed,
        )
    }
}

/** Tells the user edit mode is active and what the buttons do. */
@Composable
private fun EditModeBanner(holding: Boolean, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    GlassSurface(modifier = modifier) {
        Text(
            text = if (holding) {
                "Moving — D-pad to position, A to drop, B to cancel"
            } else {
                "Edit mode — A to pick up, pinch to resize, B to finish"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.cursor,
            modifier = Modifier.padding(
                horizontal = dimens.spacing,
                vertical = dimens.spacingSmall,
            ),
        )
    }
}

/**
 * Names the folder the grid is showing, and offers the way out.
 *
 * The close chip is here because Back is a *button*, and this panel is a
 * touchscreen: without it, a folder opened with a finger could only be left with
 * the controller.
 */
@Composable
private fun OpenFolderBanner(
    title: String,
    count: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    GlassSurface(
        // Capped, so a long folder name ellipsizes instead of stretching the banner
        // to the width of the panel.
        modifier = modifier.widthIn(max = BANNER_MAX_WIDTH.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            modifier = Modifier.padding(
                start = dimens.spacing,
                end = dimens.spacingTiny,
                top = 6.dp,
                bottom = 6.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Shrinks to fit rather than pushing the count and the close chip off
                // the end of a capped row.
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "Close",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ScanBanner(label: String?, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    GlassSurface(modifier = modifier) {
        Text(
            text = label?.let { "Scanning $it…" } ?: "Scanning library…",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(
                horizontal = dimens.spacing,
                vertical = dimens.spacingSmall,
            ),
        )
    }
}

/** Keeps a long folder name from stretching its banner across the panel. */
private const val BANNER_MAX_WIDTH = 300
