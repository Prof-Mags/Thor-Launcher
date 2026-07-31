package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaType
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The library, on the top panel.
 *
 * Shelves of posters rather than a single grid, because a catalogue has no
 * natural order and a flat grid of two thousand titles offers none — the shelf
 * *is* the organising idea, and each one answers a different question about what
 * to watch.
 *
 * Scrolling is driven from the cursor rather than from touch inertia. The two
 * panels share one cursor, so the list has to follow the selection wherever it
 * came from; letting the list scroll independently would let the highlighted
 * title leave the screen while the panel below still described it.
 */
@Composable
fun MoviesBrowseScreen(
    state: MoviesUiState,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    // Follows the cursor rather than the finger; see the note above.
    LaunchedEffect(state.cursor.row) {
        if (state.rows.isNotEmpty()) {
            listState.animateScrollToItem(state.cursor.row.coerceIn(0, state.rows.lastIndex))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        when {
            state.setupMessage != null -> BrowseMessage(state.setupMessage)
            state.loading && state.rows.isEmpty() -> BrowseMessage("Loading…")
            else -> LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = dimens.spacing,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacing),
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    Shelf(
                        row = row,
                        focusedColumn = state.cursor.column.takeIf { index == state.cursor.row },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = ThorTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

/**
 * One shelf.
 *
 * The row scrolls itself to keep the cursor visible, and only when this shelf
 * holds the cursor — an off-cursor shelf that also scrolled would animate
 * several rows at once every time the user moved down.
 */
@Composable
private fun Shelf(row: MediaRow, focusedColumn: Int?) {
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    LaunchedEffect(focusedColumn) {
        if (focusedColumn != null && row.items.isNotEmpty()) {
            listState.animateScrollToItem(focusedColumn.coerceIn(0, row.items.lastIndex))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleSmall,
            color = ThorTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = dimens.spacing),
        )

        LazyRow(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = dimens.spacing,
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.id.key }) { index, item ->
                PosterCell(item = item, focused = index == focusedColumn)
            }
        }
    }
}

@Composable
private fun PosterCell(item: MediaItem, focused: Boolean) {
    val shape = ThorTheme.shapes.small
    // Lit by the pointer exactly as by the controller cursor, as everywhere else.
    val hover = rememberPointerHover()
    val highlighted = focused || hover.isHovered

    Column(
        modifier = Modifier.height(POSTER_HEIGHT.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(POSTER_RATIO)
                .pointerHover(hover)
                .thorCursor(focused = highlighted, shape = shape)
                .clip(shape),
        ) {
            ArtworkImage(
                model = item.posterUrl,
                contentDescription = item.title,
                fallbackText = item.title,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                ThorTheme.colors.onSurface
            } else {
                ThorTheme.colors.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Films or shows, as a pair of headings above the shelves. */
@Composable
fun MediaTypeTabs(
    selected: MediaType,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier.padding(horizontal = ThorTheme.dimens.spacing),
        horizontalArrangement = Arrangement.spacedBy(ThorTheme.dimens.spacing),
    ) {
        MediaType.entries.forEach { type ->
            val label = if (type == MediaType.MOVIE) "Films" else "Shows"
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (type == selected) colors.cursor else colors.onSurfaceVariant,
            )
        }
    }
}

/** Posters are 2:3, which is the shape every catalogue publishes them in. */
private const val POSTER_RATIO = 2f / 3f
private const val POSTER_HEIGHT = 190
