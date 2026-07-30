package com.thor.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry

/** An action offered for the highlighted entry. */
enum class ContextAction(val label: String, val icon: ImageVector) {
    LAUNCH("Launch", Icons.Rounded.PlayArrow),
    LAUNCH_MAIN_SCREEN("Launch on main screen", Icons.Rounded.Monitor),
    LAUNCH_SECOND_SCREEN("Launch on second screen", Icons.Rounded.Tablet),
    ADD_TO_GRID("Add to grid", Icons.AutoMirrored.Rounded.AddToHomeScreen),
    REMOVE_FROM_GRID("Remove from grid", Icons.Rounded.VisibilityOff),
    MOVE_TO_FOLDER("Move to folder…", Icons.AutoMirrored.Rounded.DriveFileMove),
    REMOVE_FROM_FOLDER("Take out of folder", Icons.Rounded.FolderOff),
    EDIT("Edit…", Icons.Rounded.Edit),
    APP_INFO("App info", Icons.Rounded.Info),
    TOGGLE_FAVORITE("Favourite", Icons.Rounded.StarOutline),
    HIDE("Hide from grid", Icons.Rounded.VisibilityOff),

    /**
     * Offered in place of [HIDE] on an entry that is already hidden.
     *
     * A separate action rather than a re-labelled one because the menu is built
     * from a fixed enum and the label is part of it — and because a toggle whose
     * caption depends on state is exactly the kind of thing that ends up saying
     * "Hide" over an entry that is already hidden.
     */
    UNHIDE("Show on grid", Icons.Rounded.Visibility),

    UNINSTALL("Uninstall", Icons.Rounded.DeleteOutline),

    /**
     * Removes the entry from the library, as opposed to hiding it.
     *
     * A rescan that still finds the file will bring it back, unhidden — which is
     * the point: this is the way to undo a state an entry has got stuck in, not a
     * way to delete anything from disk. THOR never touches the user's files.
     */
    DELETE("Remove from library", Icons.Rounded.DeleteForever),

    DELETE_FOLDER("Delete folder", Icons.Rounded.Delete),
}

/**
 * Builds the action list for an entry.
 *
 * The menu is assembled per entry type rather than shown wholesale and greyed
 * out — an "Uninstall" row on a ROM or an "App info" row on a folder is noise
 * that makes the useful rows harder to find.
 */
fun contextActionsFor(
    entry: GridEntry,
    hasSecondScreen: Boolean,
    /** True when opened from the app drawer rather than from the grid. */
    fromDrawer: Boolean = false,
    /** Whether this entry already occupies a grid cell. */
    onGrid: Boolean = true,
    /** Whether any folder exists to move this entry into. */
    foldersExist: Boolean = false,
    /** Whether this entry currently sits inside a folder. */
    inFolder: Boolean = false,
): List<ContextAction> = buildList {
    add(ContextAction.LAUNCH)
    if (entry !is FolderEntry) {
        add(ContextAction.LAUNCH_MAIN_SCREEN)
        if (hasSecondScreen) add(ContextAction.LAUNCH_SECOND_SCREEN)
    }

    // The drawer's whole purpose is choosing what reaches the grid, so that is
    // the action offered there — and only in the direction that applies.
    if (fromDrawer) {
        if (onGrid) add(ContextAction.REMOVE_FROM_GRID) else add(ContextAction.ADD_TO_GRID)
    }

    // Filing only applies to things that can go in a folder, and only when there
    // is a folder to put them in — offering it with none would open an empty
    // picker.
    if (entry !is FolderEntry && !fromDrawer) {
        if (inFolder) {
            add(ContextAction.REMOVE_FROM_FOLDER)
        } else if (foldersExist) {
            add(ContextAction.MOVE_TO_FOLDER)
        }
    }

    add(ContextAction.EDIT)
    if (entry is AppEntry) add(ContextAction.APP_INFO)
    add(ContextAction.TOGGLE_FAVORITE)
    if (!fromDrawer && entry !is FolderEntry) {
        // The direction that applies. A hidden entry is only reachable at all
        // because "show hidden" is on, and the one thing wanted there is the way
        // back.
        if (entry.isHidden) add(ContextAction.UNHIDE) else add(ContextAction.HIDE)
    }
    if (entry is AppEntry && !entry.isSystemApp) add(ContextAction.UNINSTALL)
    if (entry !is FolderEntry) add(ContextAction.DELETE)
    if (entry is FolderEntry) add(ContextAction.DELETE_FOLDER)
}

/**
 * The context menu, opened with Y on the highlighted entry.
 *
 * Presented as a centred card rather than a bottom sheet: the bottom of the
 * panel is occupied by the dock, and a sheet sliding up from there would cover
 * it and read as part of it.
 */
@Composable
fun EntryContextMenu(
    entry: GridEntry?,
    hasSecondScreen: Boolean,
    focusedIndex: Int,
    fromDrawer: Boolean = false,
    onGrid: Boolean = true,
    foldersExist: Boolean = false,
    inFolder: Boolean = false,
    onAction: (ContextAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion

    AnimatedVisibility(
        visible = entry != null,
        enter = fadeIn(motion.tweenSpec(motion.selectionMillis)),
        exit = fadeOut(motion.tweenSpec(motion.selectionMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // `entry` is captured once so the card keeps rendering its content
            // through the exit animation instead of blanking on dismissal.
            val target = entry ?: return@Box

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(motion.tweenSpec(motion.selectionMillis), initialScale = 0.92f),
                exit = scaleOut(motion.tweenSpec(motion.selectionMillis)),
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(dimens.cornerRadiusLarge),
                    // Highest surface: these sit over an already-elevated panel, and
                    // reusing the base surface made them read as part of it.
                    color = ThorTheme.colors.surfaceHighest,
                    modifier = Modifier
                        .width(CARD_WIDTH.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimens.spacing),
                    ) {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = target.subtitle(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = dimens.spacingSmall),
                        )

                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            contextActionsFor(
                                target, hasSecondScreen, fromDrawer, onGrid,
                                foldersExist, inFolder,
                            )
                                .forEachIndexed { index, action ->
                                    ContextRow(
                                        action = action,
                                        entry = target,
                                        focused = index == focusedIndex,
                                        onClick = { onAction(action) },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextRow(
    action: ContextAction,
    entry: GridEntry,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    // The favourite row reflects current state rather than being a static label.
    val label = if (action == ContextAction.TOGGLE_FAVORITE && entry.isFavorite) {
        "Remove from favourites"
    } else {
        action.label
    }
    val icon = if (action == ContextAction.TOGGLE_FAVORITE && entry.isFavorite) {
        Icons.Rounded.Star
    } else {
        action.icon
    }
    val destructive = action == ContextAction.UNINSTALL || action == ContextAction.DELETE_FOLDER

    // Lit by the controller cursor or by the pointer, indistinguishably.
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .thorCursor(focused = lit, cornerRadius = dimens.cornerRadiusSmall)
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                destructive -> colors.error
                lit -> colors.cursor
                else -> colors.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) colors.error else colors.onSurface,
        )
    }
}

/** One-line description shown under the entry's title. */
private fun GridEntry.subtitle(): String = when (this) {
    is GameEntry -> listOfNotNull(
        platformId.uppercase(),
        metadata.developer,
        metadata.releaseYear?.toString(),
    ).joinToString(" · ")

    is AppEntry -> if (isEmulator) "Emulator · $packageName" else packageName
    is FolderEntry -> "Folder · ${childIds.size} items"
    else -> ""
}

private const val CARD_WIDTH = 320
