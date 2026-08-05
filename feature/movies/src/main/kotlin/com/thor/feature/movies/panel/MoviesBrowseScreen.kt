package com.thor.feature.movies.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaType
import com.thor.core.model.WatchProgress
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.movies.MoviesUiState

/** The catalogue on the top display. */
@Composable
fun MoviesBrowseScreen(
    state: MoviesUiState,
    onTypeSelected: (MediaType) -> Unit = {},
    onItemSelected: (row: Int, column: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()
    val rows = state.visibleRows

    LaunchedEffect(state.cursor.row, rows.size) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(state.cursor.row.coerceIn(0, rows.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                Modifier.background(
                        Brush.verticalGradient(
                            listOf(colors.surfaceElevated.copy(alpha = 0.42f), colors.background),
                        ),
                    ),
            ),
    ) {
        CinemaHeader(
            state = state,
            onTypeSelected = onTypeSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.setupMessage != null -> BrowseMessage(state.setupMessage)
                state.loading && rows.isEmpty() -> BrowseMessage("Loading library...")
                state.searching && rows.isEmpty() -> BrowseMessage("Searching...")
                state.isSearching && rows.isEmpty() ->
                    BrowseMessage("Nothing matched \"${state.query}\".")

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = dimens.spacingSmall,
                        bottom = dimens.spacing,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacing),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        Shelf(
                            row = row,
                            focusedColumn = state.cursor.column.takeIf { index == state.cursor.row },
                            onItemSelected = { column -> onItemSelected(index, column) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaHeader(
    state: MoviesUiState,
    onTypeSelected: (MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val selectedRow = state.visibleRows.getOrNull(state.cursor.row)

    GlassSurface(
        modifier = modifier.padding(
            start = dimens.spacing,
            top = dimens.spacingSmall,
            end = dimens.spacing,
        ),
        shape = ThorTheme.shapes.panel,
        color = colors.surface,
        bordered = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = dimens.spacing,
                vertical = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Loki CINEMA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Movies & TV",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            MediaTypeTabs(
                selected = state.type,
                onSelected = onTypeSelected,
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = selectedRow?.title?.uppercase() ?: "YOUR LIBRARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = state.highlighted?.title ?: "Choose something to watch",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

@Composable
private fun Shelf(
    row: MediaRow,
    focusedColumn: Int?,
    onItemSelected: (column: Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    LaunchedEffect(focusedColumn) {
        if (focusedColumn != null && row.items.isNotEmpty()) {
            listState.animateScrollToItem(focusedColumn.coerceIn(0, row.items.lastIndex))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(
                        Brush.verticalGradient(colors.accentStops),
                    ),
            )
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = dimens.spacingSmall),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (row.landscape) "PICK UP WHERE YOU LEFT OFF" else "${row.items.size} TITLES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        /*
         * No paging arrows over the rail.
         *
         * They sat on top of the first and last poster of every row, so on a
         * full shelf two titles were permanently half-covered by a control for
         * moving past them. Nothing was lost with them: the row is driven by the
         * stick and by the cursor, both of which already scroll it, and the
         * contentPadding that had been reserved for the arrows goes back to the
         * posters.
         */
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = dimens.spacing),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            itemsIndexed(
                items = row.items,
                key = { index, _ -> "${row.id}:${row.entryKeyAt(index) ?: index}" },
            ) { index, item ->
                PosterCell(
                    item = item,
                    focused = index == focusedColumn,
                    progress = row.progressAt(index),
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}

@Composable
private fun PosterCell(
    item: MediaItem,
    focused: Boolean,
    progress: WatchProgress? = null,
    onClick: () -> Unit,
) {
    AuroraPosterCell(item, focused, progress, onClick)
}

@Composable
private fun AuroraPosterCell(
    item: MediaItem,
    focused: Boolean,
    progress: WatchProgress?,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val highlighted = focused || hover.isHovered

    // The explicit width is important. LazyRow measures children with unbounded
    // horizontal constraints; without it a long title becomes the item's width
    // and looks like an enormous gap before the next poster.
    Column(
        modifier = Modifier
            .width(POSTER_CARD_WIDTH.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_RATIO)
                .pointerHover(hover)
                .thorCursor(focused = highlighted, shape = shape)
                .clip(shape)
                .background(colors.surface),
        ) {
            ArtworkImage(
                model = item.posterUrl,
                contentDescription = item.title,
                fallbackText = item.title,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    ),
            )

            if (progress != null && progress.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    color = colors.cursor,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlighted) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = cardSubtitle(item, progress),
                style = MaterialTheme.typography.labelSmall,
                color = if (progress != null) {
                    colors.cursor
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.72f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private fun cardSubtitle(item: MediaItem, progress: WatchProgress?): String {
    if (progress == null) {
        return listOfNotNull(
            item.releaseYear?.toString(),
            if (item.isSeries) "Series" else "Film",
        ).joinToString("  /  ")
    }

    val episode = if (progress.seasonNumber != null && progress.episodeNumber != null) {
        "S%02dE%02d".format(progress.seasonNumber, progress.episodeNumber)
    } else {
        null
    }
    val minutesLeft = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L)
        .coerceAtLeast(1L)
    return listOfNotNull(episode, "$minutesLeft min left").joinToString("  /  ")
}

/** Films or shows, as compact controls in the catalogue header. */
@Composable
fun MediaTypeTabs(
    selected: MediaType,
    modifier: Modifier = Modifier,
    onSelected: (MediaType) -> Unit = {},
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaType.entries.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .clip(ThorTheme.shapes.pill)
                    .background(if (isSelected) colors.cursor else colors.surfaceElevated)
                    .clickable { onSelected(type) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    text = if (type == MediaType.MOVIE) "LB  Films" else "Shows  RB",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        contrastingContentColor(colors.cursor)
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private const val POSTER_RATIO = 2f / 3f
private const val POSTER_CARD_WIDTH = 112
private const val FULL_BLEED_CARD_WIDTH = 104
private const val ROW_CARD_WIDTH = 214
private const val ROW_CARD_HEIGHT = 78
private const val ROW_THUMB_WIDTH = 54
