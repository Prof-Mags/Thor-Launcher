package com.thor.feature.home.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme

/**
 * What a long press on an empty cell offers.
 *
 * The gesture had no answer before: a long press resolves the cell to an entry
 * and opens its menu, and on an empty cell there is no entry, so nothing
 * happened at all. That is the one place on the grid where the user has clearly
 * said "do something *here*", and it is the natural home for the actions that
 * need a cell to put something in.
 */
enum class CellAction(
    val label: String,
    val description: String,
    val icon: ImageVector,
    /** The caption on the tile; see [ContextAction.short] for why it is separate. */
    val short: String,
) {
    ADD_WIDGET(
        "Add a widget…",
        "A clock, the weather, media controls",
        Icons.Rounded.Widgets,
        "Widget…",
    ),
    ADD_APP(
        "Add an app…",
        "Opens the drawer to pick one",
        Icons.Rounded.Apps,
        "App…",
    ),
    NEW_FOLDER(
        "New folder",
        "An empty folder, here",
        Icons.Rounded.CreateNewFolder,
        "Folder",
    ),
    ARRANGE(
        "Arrange the grid",
        "Move, resize and tidy what is already placed",
        Icons.Rounded.OpenWith,
        "Arrange",
    ),
    ADD_PAGE(
        "Add a page",
        "Another screenful, after the last one",
        Icons.AutoMirrored.Rounded.AddToHomeScreen,
        "New page",
    ),
}

/** Every action, in the order the menu shows them. */
val CELL_ACTIONS: List<CellAction> = CellAction.entries.toList()

/** Which cell the menu is about, and which tile the cursor is on. */
@Immutable
data class CellMenuState(
    val visible: Boolean = false,
    val page: Int = 0,
    val row: Int = 0,
    val column: Int = 0,
    val focusedIndex: Int = 0,
)

/**
 * The empty-cell menu.
 *
 * The same card, header and tiles as [EntryContextMenu], because it is raised by
 * the same gesture on the same surface and differs only in what it is about. The
 * header names the cell instead of an entry, which is the honest subject: the
 * user pressed on a position, and everything here acts on that position.
 */
@Composable
fun EmptyCellMenu(
    visible: Boolean,
    page: Int,
    row: Int,
    column: Int,
    focusedIndex: Int,
    onAction: (CellAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenSpec(motion.selectionMillis)),
        exit = fadeOut(motion.tweenSpec(motion.selectionMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val actions = CELL_ACTIONS
            val rows = contextMenuRows(actions.size)
            val chrome = (CELL_HEADER_HEIGHT + CELL_HEADER_GAP + CELL_HINT_HEIGHT +
                CELL_CARD_PADDING * 2).dp + CELL_TILE_GAP.dp * (rows - 1)
            val budget = maxHeight * CELL_CARD_HEIGHT_FRACTION - chrome
            val tileHeight = (budget / rows).coerceIn(MENU_TILE_MIN.dp, MENU_TILE_MAX.dp)

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(motion.tweenSpec(motion.selectionMillis), initialScale = 0.92f),
                exit = scaleOut(motion.tweenSpec(motion.selectionMillis)),
            ) {
                GlassSurface(
                    shape = ThorTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth(CELL_CARD_FRACTION)
                        .widthIn(max = CELL_CARD_WIDTH.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.padding(CELL_CARD_PADDING.dp)) {
                        CellHeader(page = page, row = row, column = column)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(CELL_TILE_GAP.dp),
                            modifier = Modifier.padding(top = CELL_HEADER_GAP.dp),
                        ) {
                            repeat(rows) { tileRow ->
                                Row(
                                    horizontalArrangement =
                                    Arrangement.spacedBy(CELL_TILE_GAP.dp),
                                ) {
                                    repeat(CONTEXT_MENU_COLUMNS) { tileColumn ->
                                        // Down one column and then down the next,
                                        // as the entry menu fills; see
                                        // [contextMenuRows].
                                        val index = tileColumn * rows + tileRow
                                        val action = actions.getOrNull(index)
                                        if (action == null) {
                                            Spacer(Modifier.weight(1f))
                                        } else {
                                            MenuTile(
                                                icon = action.icon,
                                                caption = action.short,
                                                focused = index == focusedIndex,
                                                height = tileHeight,
                                                onClick = { onAction(action) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        CellHint(actions.getOrNull(focusedIndex))
                    }
                }
            }
        }
    }
}

/** Which cell this is about, said the way the page indicators count them. */
@Composable
private fun CellHeader(page: Int, row: Int, column: Int) {
    val colors = ThorTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(CELL_HEADER_HEIGHT.dp),
    ) {
        Box(
            modifier = Modifier
                .size(CELL_HEADER_ICON.dp)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.AddToHomeScreen,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(CELL_HEADER_GLYPH.dp),
            )
        }

        Column(
            modifier = Modifier
                .padding(start = CELL_HEADER_GAP.dp)
                .weight(1f),
        ) {
            Text(
                text = "Empty cell",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // One-based, because the page dots are counted that way by
                // everyone who looks at them.
                text = "Page ${page + 1} · row ${row + 1}, column ${column + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CellHint(action: CellAction?) {
    val colors = ThorTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(CELL_HINT_HEIGHT.dp),
    ) {
        if (action == null) return@Row
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = action.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = CELL_HINT_GAP.dp),
        )
    }
}

// Deliberately the entry menu's own proportions. The two cards are raised by the
// same gesture and appear in the same place, and any difference between them
// reads as one of the two being wrong.
private const val CELL_CARD_WIDTH = 460
private const val CELL_CARD_FRACTION = 0.88f
private const val CELL_CARD_HEIGHT_FRACTION = 0.94f
private const val CELL_CARD_PADDING = 12
private const val CELL_HEADER_HEIGHT = 56
private const val CELL_HEADER_ICON = 48
private const val CELL_HEADER_GLYPH = 26
private const val CELL_HEADER_GAP = 10
private const val CELL_HINT_HEIGHT = 26
private const val CELL_HINT_GAP = 8
private const val CELL_TILE_GAP = MENU_TILE_GAP
