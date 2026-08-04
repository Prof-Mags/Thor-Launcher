package com.thor.feature.movies

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaType
import com.thor.core.model.WatchProgress
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The catalogue as a television screen.
 *
 * A rail down the left, a featured card for whatever the cursor is on, and the
 * shelves stacked beneath it. The handheld arrangement this replaced was a
 * catalogue pane beside a detail pane, which is right for a screen held at arm's
 * length and wrong for one across a room: it made the artwork thumbnail-sized and
 * the prose unreadable from a sofa, and it spent a third of a television on a
 * column of text.
 *
 * The artwork lives inside the cards rather than behind everything. A backdrop
 * across the whole panel is the more cinematic of the two and the harder to read
 * over — every label needs its own scrim, and the scrims are what end up being
 * looked at. Contained art needs none, and it lets the shelves keep the flat dark
 * field that makes a poster the brightest thing on the screen.
 */
@Composable
internal fun MoviesCouchBrowse(
    state: MoviesUiState,
    detail: DetailState,
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean,
    onSearchFocused: () -> Unit,
    onTypeSelected: (MediaType) -> Unit,
    onItemFocused: (row: Int, column: Int) -> Unit,
    onItemSelected: (row: Int, column: Int) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val rows = state.visibleRows
    // The fetched record when it has arrived, the shelf's own summary until then,
    // so the featured card never blanks between moving and the details landing.
    val highlighted = detail.item ?: state.highlighted
    val rowIndex = state.cursor.row.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()
    val stats = remember(rows) { couchMediaStats(rows) }

    LaunchedEffect(rowIndex, rows.size) {
        if (rows.isNotEmpty()) {
            /*
             * The item above the focused shelf goes to the top, not the shelf
             * itself, so there is always something overhead to have come from —
             * and on the first shelf that something is the featured card. One
             * rule covers both, and the card needs no special case to stay on
             * screen while the top shelf is being walked.
             */
            listState.animateScrollToItem(rowIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(colors.surfaceElevated.copy(alpha = FIELD_ALPHA), colors.background),
            ),
        ),
    ) {
        val available = (maxHeight - LEGEND_HEIGHT.dp).coerceAtLeast(MIN_CONTENT_HEIGHT.dp)
        val heroHeight = couchHeroHeight(available)
        val shelfHeight = couchShelfHeight(available)
        val posterHeight = couchPosterHeight(shelfHeight)

        Row(modifier = Modifier.fillMaxSize()) {
            CouchMediaRail(
                type = state.type,
                stats = stats,
                query = query,
                onQueryChanged = onQueryChanged,
                searchRequested = searchRequested,
                onSearchFocused = onSearchFocused,
                onTypeSelected = onTypeSelected,
                modifier = Modifier.width(RAIL_WIDTH.dp).fillMaxHeight(),
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val message = browseMessage(state)
                if (message != null || rows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = message ?: "Nothing to watch here yet.",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = SCREEN_INSET.dp * 2),
                        )
                    }
                    CouchLegend()
                    return@Column
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = ROW_GAP.dp),
                    verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
                ) {
                    item(key = "featured") {
                        CouchFeaturedCard(
                            item = highlighted,
                            resume = detail.resumeProgress,
                            shelfTitle = rows.getOrNull(rowIndex)?.title,
                            position = state.cursor.column + 1,
                            of = rows.getOrNull(rowIndex)?.items?.size ?: 0,
                            overviewLines = couchOverviewLines(heroHeight),
                            onPlay = onPlay,
                            onOpen = { onItemSelected(rowIndex, state.cursor.column) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .padding(horizontal = SCREEN_INSET.dp),
                        )
                    }

                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        CouchShelf(
                            row = row,
                            focusedColumn = state.cursor.column.takeIf { index == rowIndex },
                            posterHeight = posterHeight,
                            onItemFocused = { column -> onItemFocused(index, column) },
                            onItemSelected = { column -> onItemSelected(index, column) },
                            modifier = Modifier.fillMaxWidth().height(shelfHeight),
                        )
                    }
                }

                CouchLegend()
            }
        }
    }
}

/** Which of the browse states has nothing to draw shelves for. */
private fun browseMessage(state: MoviesUiState): String? = when {
    state.setupMessage != null -> state.setupMessage
    state.visibleRows.isNotEmpty() -> null
    state.loading -> "Loading library..."
    state.searching -> "Searching..."
    state.isSearching -> "Nothing matched \"${state.query}\"."
    else -> null
}

// ---- The rail ----------------------------------------------------------------

/**
 * Films or shows, search, and what is behind them.
 *
 * The section's own navigation, down the side where a television expects to find
 * it. It deliberately does not repeat the shell's tabs above: Home, Stream and
 * Settings are one bar away, and a second copy of them here would be two places
 * to be in the same place.
 *
 * Search is a row here rather than a box in a strip along the top, because the
 * strip cost more than it was worth. This panel is about half the height in dp
 * that a television's pixel dimensions suggest - couch mode composes through a
 * scaled density - so forty-odd dp of chrome is the difference between two lines
 * of synopsis on the featured card and none.
 */
@Composable
private fun CouchMediaRail(
    type: MediaType,
    stats: CouchMediaStats,
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean,
    onSearchFocused: () -> Unit,
    onTypeSelected: (MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    Column(
        modifier = modifier
            .background(colors.background.copy(alpha = RAIL_ALPHA))
            .padding(horizontal = RAIL_INSET.dp, vertical = RAIL_TOP_INSET.dp),
        verticalArrangement = Arrangement.spacedBy(RAIL_GAP.dp),
    ) {
        Text(
            text = "CINEMA",
            style = MaterialTheme.typography.titleMedium,
            color = colors.cursor,
            fontWeight = FontWeight.Black,
            letterSpacing = WORDMARK_TRACKING.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = RAIL_ROW_PADDING.dp, bottom = RAIL_GAP.dp),
        )

        RailDestination(
            icon = Icons.Rounded.Movie,
            label = "Films",
            hint = "LB",
            selected = type == MediaType.MOVIE,
            onClick = { onTypeSelected(MediaType.MOVIE) },
        )
        RailDestination(
            icon = Icons.Rounded.Tv,
            label = "Shows",
            hint = "RB",
            selected = type == MediaType.SERIES,
            onClick = { onTypeSelected(MediaType.SERIES) },
        )

        CouchSearchChip(
            query = query,
            onQueryChanged = onQueryChanged,
            requestFocus = searchRequested,
            onFocused = onSearchFocused,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ThorTheme.shapes.panel)
                .background(colors.surface.copy(alpha = STATS_ALPHA))
                .padding(STATS_INSET.dp),
            verticalArrangement = Arrangement.spacedBy(STATS_GAP.dp),
        ) {
            StatLine(
                icon = Icons.Rounded.VideoLibrary,
                value = "%,d".format(stats.titles),
                label = if (type == MediaType.MOVIE) "Films listed" else "Shows listed",
            )
            StatLine(
                icon = Icons.Rounded.Schedule,
                value = stats.continueWatching.toString(),
                label = "Continue watching",
            )
            StatLine(
                icon = Icons.Rounded.GridView,
                value = stats.categories.toString(),
                label = "Categories",
            )
        }
    }
}

@Composable
private fun RailDestination(
    icon: ImageVector,
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = selected || hover.isHovered
    val shape = ThorTheme.shapes.small
    val content = if (selected) contrastingContentColor(colors.cursor) else colors.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = hover.isHovered && !selected, shape = shape)
            .clip(shape)
            .background(
                when {
                    selected -> colors.cursor
                    lit -> colors.surfaceHighest
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = RAIL_ROW_PADDING.dp, vertical = RAIL_ROW_PADDING_V.dp),
        horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) content else colors.onSurfaceVariant,
            modifier = Modifier.size(RAIL_ICON.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) content else colors.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // The bumper that does this without the pointer. Both switch media type
        // from anywhere in the section, so the rail says so rather than being the
        // only route anybody finds.
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = (if (selected) content else colors.onSurfaceVariant).copy(alpha = HINT_ALPHA),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatLine(icon: ImageVector, value: String, label: String) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.cursor,
            modifier = Modifier.size(STAT_ICON.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- The featured card -------------------------------------------------------

/**
 * What the cursor is resting on, said at the size of a room.
 *
 * The wordmark is used in place of the title wherever the provider has one, the
 * way the title's own marketing would set it, and the plain name is the fallback
 * rather than an extra line under it — printing both says the same thing twice in
 * two different typefaces.
 */
@Composable
private fun CouchFeaturedCard(
    item: MediaItem?,
    resume: WatchProgress?,
    shelfTitle: String?,
    position: Int,
    of: Int,
    overviewLines: Int,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .clipToBounds(),
    ) {
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Choose something to watch",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Box
        }

        val art = item.backdropUrl ?: item.posterUrl
        if (art != null) {
            ArtworkImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // One scrim, across. The card is its own frame, so the picture needs
        // darkening only where the words are.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to colors.background.copy(alpha = 0.95f),
                    HERO_SCRIM_KNEE to colors.background.copy(alpha = 0.68f),
                    1f to Color.Transparent,
                ),
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(HERO_WIDTH_FRACTION)
                .fillMaxHeight()
                .padding(HERO_PADDING.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HERO_GAP.dp)) {
                shelfTitle?.let { title ->
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contrastingContentColor(colors.cursor),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(ThorTheme.shapes.small)
                            .background(colors.cursor)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }

                if (item.logoUrl != null) {
                    ArtworkImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth(LOGO_WIDTH_FRACTION)
                            .height(LOGO_HEIGHT.dp),
                    )
                } else {
                    Text(
                        text = item.title.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Black,
                        letterSpacing = TITLE_TRACKING.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = couchFactLine(item),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.overview.isNotBlank() && overviewLines > 0) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = overviewLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ACTION_GAP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Hold A rather than A: the press itself opens the title, which
                    // is where the source list lives, and the hold is the launcher's
                    // existing shortcut for taking the best one without looking.
                    CouchMediaButton(
                        label = if (resume?.isResumable == true) "Resume" else "Play",
                        hint = "HOLD A",
                        icon = Icons.Rounded.PlayArrow,
                        primary = true,
                        onClick = onPlay,
                    )
                    CouchMediaButton(
                        label = if (item.isSeries) "Episodes" else "More info",
                        hint = "A",
                        icon = Icons.Rounded.Info,
                        primary = false,
                        onClick = onOpen,
                    )
                }
            }
        }

        couchScore(item.ratings)?.let { (source, value) ->
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(HERO_PADDING.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = colors.cursor,
                        modifier = Modifier.size(SCORE_ICON.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "$source score",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Where along the shelf this is. A television has no scroll bar and a
        // catalogue row runs to a hundred titles, so without this there is no
        // answer to how much of it is left.
        if (of > 1) {
            Text(
                text = "$position / $of",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(HERO_PADDING.dp),
            )
        }
    }
}

/** A labelled action with the button that performs it printed on the end. */
@Composable
internal fun CouchMediaButton(
    label: String,
    hint: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = hover.isHovered
    val shape = ThorTheme.shapes.pill
    val background = when {
        primary -> colors.cursor
        lit -> colors.surfaceHighest
        else -> colors.surfaceElevated.copy(alpha = 0.88f)
    }
    val content = if (primary) contrastingContentColor(colors.cursor) else colors.onSurface

    Row(
        modifier = Modifier
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = ACTION_PADDING_H.dp, vertical = ACTION_PADDING_V.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(ACTION_ICON.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = HINT_ALPHA),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

// ---- The shelves -------------------------------------------------------------

/** One shelf: what it is, how much of it there is, and the titles on it. */
@Composable
private fun CouchShelf(
    row: MediaRow,
    focusedColumn: Int?,
    posterHeight: Dp,
    onItemFocused: (column: Int) -> Unit,
    onItemSelected: (column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val listState = rememberLazyListState()
    val cardWidth = couchCardWidth(posterHeight, row.landscape)

    /*
     * Nudged, not snapped.
     *
     * Scrolling the focused card to the edge on every move drags the whole shelf
     * under a cursor that only stepped one place, which from a sofa reads as the
     * shelf moving rather than the cursor. Moving only when the card would
     * otherwise be off the screen keeps the row still for the whole of the middle
     * of it, and the ends measure against the inset edges rather than the
     * viewport, so the first card is never left half under the screen edge.
     */
    LaunchedEffect(focusedColumn, row.items.size) {
        val target = focusedColumn?.takeIf { row.items.isNotEmpty() }
            ?.coerceIn(0, row.items.lastIndex)
            ?: return@LaunchedEffect
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible == null) {
            listState.animateScrollToItem(target)
            return@LaunchedEffect
        }
        val leading = layout.viewportStartOffset + layout.beforeContentPadding
        val trailing = layout.viewportEndOffset - layout.afterContentPadding
        val start = visible.offset
        val end = visible.offset + visible.size
        val overhang = when {
            start < leading -> start - leading
            end > trailing -> end - trailing
            else -> 0
        }
        if (overhang != 0) listState.animateScrollBy(overhang.toFloat())
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SHELF_HEADER_HEIGHT.dp)
                .padding(horizontal = SCREEN_INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SHELF_ICON_GAP.dp),
        ) {
            Icon(
                imageVector = couchRowIcon(row),
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(SHELF_ICON.dp),
            )
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The count, where a pointer interface would put "view all". There is
            // nowhere else for a shelf to go on this screen — it is already the
            // whole row — so the slot says how long it is instead of offering a
            // page that would show the same titles again.
            Text(
                text = couchCountLabel(row.items.size),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(SHELF_HEADER_GAP.dp))

        // Full width with the inset carried as content padding, so the first card
        // scrolls out from under the edge instead of being clipped by it while a
        // focused card is drawn larger than its slot.
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                horizontal = SCREEN_INSET.dp,
                vertical = CARD_GROWTH.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = row.items,
                key = { index, _ -> "${row.id}:${row.entryKeyAt(index) ?: index}" },
            ) { index, item ->
                CouchTitleCard(
                    item = item,
                    progress = row.progressAt(index),
                    landscape = row.landscape,
                    focused = index == focusedColumn,
                    width = cardWidth,
                    posterHeight = posterHeight,
                    onFocus = { onItemFocused(index) },
                    onClick = { onItemSelected(index) },
                )
            }
        }
    }
}

/**
 * One title on a shelf.
 *
 * The pointer moves the cursor here rather than merely lighting the card, for the
 * same reason it does on the home shelf: the featured card above describes
 * whatever is selected, and a hover that only highlighted would leave the largest
 * thing on the television describing a different film from the one being pointed
 * at.
 */
@Composable
private fun CouchTitleCard(
    item: MediaItem,
    progress: WatchProgress?,
    landscape: Boolean,
    focused: Boolean,
    width: Dp,
    posterHeight: Dp,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val hovered = hover.isHovered
    LaunchedEffect(hovered, focused) {
        if (hovered && !focused) onFocus()
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) CARD_FOCUS_SCALE else 1f,
        animationSpec = tween(FOCUS_MILLIS),
        label = "couch-title-focus",
    )
    val artAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else RESTING_ALPHA,
        animationSpec = tween(FOCUS_MILLIS),
        label = "couch-title-depth",
    )

    Column(
        modifier = Modifier.width(width).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(CARD_LABEL_GAP.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .zIndex(if (focused) 1f else 0f)
                // Before the scale, so the hover target stays the card's resting
                // box and the highlight cannot enlarge the test that produced it.
                .pointerHover(hover)
                .scale(scale)
                .thorCursor(focused = focused, shape = shape)
                .clip(shape)
                .background(colors.surface),
        ) {
            ArtworkImage(
                model = if (landscape) (item.backdropUrl ?: item.posterUrl) else item.posterUrl,
                contentDescription = item.title,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(artAlpha),
            )

            couchScore(item.ratings)?.let { (_, value) ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = contrastingContentColor(colors.cursor),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(BADGE_INSET.dp)
                        .clip(ThorTheme.shapes.small)
                        .background(colors.cursor)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (progress != null && progress.durationMs > 0L) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Text(
                        text = couchResumeLabel(progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                                ),
                            )
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        color = colors.cursor,
                        trackColor = Color.White.copy(alpha = 0.24f),
                        modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().height(CARD_LABEL_HEIGHT.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = couchCardSubtitle(item),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = SUBTITLE_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What each button does, along the bottom where a television legend belongs. */
@Composable
private fun CouchLegend() {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LEGEND_HEIGHT.dp)
            .padding(horizontal = SCREEN_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_GAP.dp, Alignment.End),
    ) {
        LEGEND.forEach { (button, action) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = button,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---- Layout, as arithmetic rather than fixed panels --------------------------

/*
 * Every region below is a share of the screen with a floor and a ceiling.
 *
 * Couch mode composes through a scaled density, so a height written in dp is not
 * a fixed share of the panel: the same 240dp band is a quarter of the screen at
 * one interface size and a third of it at another. Fractions hold their
 * proportions at every scale, and the clamps keep them sensible on a panel that
 * is not the shape of a television.
 */

/** How tall the featured card is on a browse area [available] high. */
internal fun couchHeroHeight(available: Dp): Dp =
    (available * HERO_FRACTION).coerceIn(MIN_HERO.dp, MAX_HERO.dp)

/** How tall one shelf is, header and cards together. */
internal fun couchShelfHeight(available: Dp): Dp =
    (available * SHELF_FRACTION).coerceIn(MIN_SHELF.dp, MAX_SHELF.dp)

/** The artwork height left inside a shelf once its header and caption are spent. */
internal fun couchPosterHeight(shelfHeight: Dp): Dp {
    val spent = SHELF_HEADER_HEIGHT + SHELF_HEADER_GAP +
        CARD_LABEL_HEIGHT + CARD_LABEL_GAP + CARD_GROWTH * 2
    return (shelfHeight - spent.dp).coerceIn(MIN_POSTER.dp, MAX_POSTER.dp)
}

/**
 * The card's width, taken from its height rather than set on its own.
 *
 * Cards inside a `LazyRow` are measured with no width limit, so a card that does
 * not state one takes the width of the longest word in its caption - which is how
 * a shelf ends up with one enormous gap in it and no obvious cause. Deriving it
 * from the artwork also keeps every card the shape of the picture on it, so the
 * continue-watching stills stay stills instead of being cropped into portraits.
 */
internal fun couchCardWidth(posterHeight: Dp, landscape: Boolean): Dp =
    posterHeight * if (landscape) STILL_ASPECT else POSTER_ASPECT

/**
 * How much of the story fits inside the featured card, in lines.
 *
 * The synopsis is the one part of that card with no length of its own, so it is
 * the part that gets measured. Everything else there - the wordmark, the facts,
 * the buttons - is furniture of a known height, and a fixed line count would push
 * all of it out of the card on a short panel rather than simply printing less
 * prose.
 *
 * Zero is a real answer. A card with room for the name of the film and the button
 * that plays it, and nothing else, should show those two things.
 */
internal fun couchOverviewLines(heroHeight: Dp): Int {
    val forProse = heroHeight.value - HERO_FURNITURE
    return (forProse / OVERVIEW_LINE_HEIGHT).toInt().coerceIn(0, MAX_OVERVIEW_LINES)
}

// ---- Words -------------------------------------------------------------------

/** What the rail reports about the catalogue behind it. */
internal data class CouchMediaStats(
    val titles: Int,
    val continueWatching: Int,
    val categories: Int,
)

/**
 * Counted from what is on screen, not from a library.
 *
 * There is no local collection here to measure: the shelves are a catalogue
 * fetched per media type, and the honest figure is how much of it came back.
 * Titles are counted once however many shelves they appear on, which they
 * regularly do - a film can be trending and new and in its genre row at once.
 */
internal fun couchMediaStats(rows: List<MediaRow>): CouchMediaStats {
    val titles = rows.flatMap { row -> row.items.map { it.id.key } }.distinct().size
    val resuming = rows
        .flatMap { row ->
            row.items.indices.mapNotNull { index ->
                row.progressAt(index)?.takeIf { it.isResumable }?.mediaId?.key
            }
        }
        .distinct()
        .size
    return CouchMediaStats(titles = titles, continueWatching = resuming, categories = rows.size)
}

/** The one-line summary under the featured title. */
internal fun couchFactLine(item: MediaItem): String = listOfNotNull(
    if (item.isSeries) "Series" else "Film",
    item.releaseYear?.toString(),
    item.runtimeMinutes?.takeIf { it > 0 }?.let(::couchRuntimeLabel),
    item.contentRating?.takeIf(String::isNotBlank),
    item.seasons.count { !it.isSpecials }
        .takeIf { item.isSeries && it > 0 }
        ?.let { if (it == 1) "1 season" else "$it seasons" },
    item.genres.take(GENRES_SHOWN).takeIf { it.isNotEmpty() }?.joinToString(", "),
).joinToString(FACT_SEPARATOR)

/** The shorter form under a card, where there is room for two facts. */
internal fun couchCardSubtitle(item: MediaItem): String = listOfNotNull(
    item.releaseYear?.toString(),
    item.genres.firstOrNull() ?: if (item.isSeries) "Series" else "Film",
).joinToString(FACT_SEPARATOR)

/** Hours and minutes, because "134m" is arithmetic the viewer should not do. */
internal fun couchRuntimeLabel(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/**
 * What is left of a part-watched title.
 *
 * Time remaining rather than time elapsed: the question being asked of a resume
 * card is whether there is room for the rest of it this evening.
 */
internal fun couchResumeLabel(progress: WatchProgress): String {
    val episode = if (progress.seasonNumber != null && progress.episodeNumber != null) {
        "S%02dE%02d".format(progress.seasonNumber, progress.episodeNumber)
    } else {
        null
    }
    val minutesLeft = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L)
        .coerceAtLeast(1L)
    val remaining = if (minutesLeft >= 60L) {
        "${minutesLeft / 60}h ${minutesLeft % 60}m left"
    } else {
        "$minutesLeft min left"
    }
    return listOfNotNull(episode, remaining).joinToString(FACT_SEPARATOR)
}

/**
 * The best score the title has, with whoever published it.
 *
 * One figure, not three. Two badges reading 8.7 and 82% invite the arithmetic of
 * reconciling them, which is not a thing to be doing while choosing a film.
 */
internal fun couchScore(ratings: MediaRatings): Pair<String, String>? = when {
    ratings.imdb != null -> "IMDb" to "%.1f".format(ratings.imdb)
    ratings.tmdb != null -> "TMDb" to "%.1f".format(ratings.tmdb)
    ratings.rottenTomatoes != null -> "RT" to "${ratings.rottenTomatoes}%"
    else -> null
}

internal fun couchCountLabel(count: Int): String =
    if (count == 1) "1 title" else "$count titles"

/**
 * A mark for what kind of shelf this is.
 *
 * Read from the title rather than the id, because only the continue-watching row
 * is built here - every other shelf is named by whichever addon supplied it, and
 * its id is that addon's, not a name this launcher chose.
 */
internal fun couchRowIcon(row: MediaRow): ImageVector = when {
    row.landscape -> Icons.Rounded.Schedule
    row.title.containsAny("new", "recent", "latest") -> Icons.Rounded.CalendarMonth
    row.title.containsAny("popular", "trending", "top") -> Icons.Rounded.TrendingUp
    row.title.containsAny("featured", "best") -> Icons.Rounded.Star
    else -> Icons.Rounded.LocalMovies
}

private fun String.containsAny(vararg words: String): Boolean =
    words.any { contains(it, ignoreCase = true) }

internal const val FACT_SEPARATOR = "  /  "

/** The buttons that do something on this screen, in the order they are reached. */
private val LEGEND = listOf(
    "A" to "Select",
    "HOLD A" to "Play",
    "Y" to "Search",
    "B" to "Back",
)

private const val HERO_FRACTION = 0.52f
private const val MIN_HERO = 200
private const val MAX_HERO = 340
private const val SHELF_FRACTION = 0.44f
private const val MIN_SHELF = 148
private const val MAX_SHELF = 300
private const val MIN_POSTER = 92
private const val MAX_POSTER = 230
private const val MIN_CONTENT_HEIGHT = 240

private const val POSTER_ASPECT = 2f / 3f
private const val STILL_ASPECT = 16f / 9f

private const val SCREEN_INSET = 22
private const val LEGEND_HEIGHT = 24
private const val LEGEND_GAP = 18
private const val ROW_GAP = 10
private const val FIELD_ALPHA = 0.34f

private const val RAIL_WIDTH = 186
private const val RAIL_ALPHA = 0.55f
private const val RAIL_INSET = 12
private const val RAIL_TOP_INSET = 16
private const val RAIL_GAP = 6
private const val RAIL_ROW_PADDING = 12
private const val RAIL_ROW_PADDING_V = 10
private const val RAIL_ICON_GAP = 10
private const val RAIL_ICON = 20
private const val WORDMARK_TRACKING = 3
/** The wide setting a film's name is given on a poster, when it has no wordmark. */
private const val TITLE_TRACKING = 2
private const val STATS_ALPHA = 0.7f
private const val STATS_INSET = 12
private const val STATS_GAP = 10
private const val STAT_ICON = 18

private const val SHELF_HEADER_HEIGHT = 24
private const val SHELF_HEADER_GAP = 6
private const val SHELF_ICON = 17
private const val SHELF_ICON_GAP = 8
private const val CARD_GAP = 11
private const val CARD_LABEL_HEIGHT = 30
private const val CARD_LABEL_GAP = 5
/** Room around a card for the focused one to grow into without being clipped. */
private const val CARD_GROWTH = 6
private const val CARD_FOCUS_SCALE = 1.06f
private const val RESTING_ALPHA = 0.86f
private const val FOCUS_MILLIS = 160
private const val PROGRESS_HEIGHT = 4
private const val BADGE_INSET = 6
private const val SUBTITLE_ALPHA = 0.74f

private const val HERO_PADDING = 16
private const val HERO_WIDTH_FRACTION = 0.56f
private const val HERO_GAP = 7
private const val HERO_SCRIM_KNEE = 0.5f
private const val LOGO_WIDTH_FRACTION = 0.66f
private const val LOGO_HEIGHT = 44
/**
 * The featured card minus its story: padding, badge, wordmark, facts and buttons.
 *
 * A measurement of the layout above rather than a preference. It is deliberately
 * a little generous - erring high prints one line fewer than would have fitted,
 * erring low pushes the title out of the card.
 */
private const val HERO_FURNITURE = 180f
private const val OVERVIEW_LINE_HEIGHT = 21f
private const val MAX_OVERVIEW_LINES = 4
private const val SCORE_ICON = 20
private const val ACTION_GAP = 10
private const val ACTION_PADDING_H = 16
private const val ACTION_PADDING_V = 9
private const val ACTION_ICON = 19
private const val HINT_ALPHA = 0.66f
private const val GENRES_SHOWN = 3

private const val COUCH_SEARCH_FIELD_ID = "movies-couch-search"
private const val SEARCH_PADDING_H = 12
private const val SEARCH_PADDING_V = 10
private const val SEARCH_ICON_GAP = 10

// ---- Search ------------------------------------------------------------------

/**
 * The way in to the keyboard, and the record of what was asked.
 *
 * Not a text field. The bottom panel's search box is one because it is composed
 * beside the keyboard that fills it in, where showing a caret in the box being
 * typed into is the whole point; here the keyboard is a full-screen overlay and
 * the box underneath it would be a wide empty rectangle for as long as nobody had
 * searched for anything. A chip states its shortcut when idle and becomes the
 * query when there is one, so the bar says what the shelves are answering.
 *
 * Focus is claimed directly rather than through a text field. The field is a
 * display of a value the keyboard is already editing through the sink below, and
 * this screen has somewhere better to show that value than a box of its own.
 */
@Composable
private fun CouchSearchChip(
    query: String,
    onQueryChanged: (String) -> Unit,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val textInput = LocalThorTextInput.current
    // Kept current so the sink registered on focus always writes to the latest
    // state holder, however many times this screen has recomposed since.
    val currentOnQueryChanged by rememberUpdatedState(onQueryChanged)
    val hover = rememberPointerHover()
    val typing = textInput.focusedId == COUCH_SEARCH_FIELD_ID
    val lit = typing || hover.isHovered
    val searching = query.isNotBlank()
    val shape = ThorTheme.shapes.pill

    val claim: () -> Unit = {
        textInput.focus(
            id = COUCH_SEARCH_FIELD_ID,
            label = "Search",
            initial = query,
        ) { edited -> currentOnQueryChanged(edited) }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            claim()
            onFocused()
        }
    }

    // Leaving the catalogue must not leave the keyboard pointed at it. A field
    // does this for itself on disposal; a chip that stands in for one has to.
    DisposableEffect(Unit) {
        onDispose { textInput.release(COUCH_SEARCH_FIELD_ID) }
    }

    Row(
        modifier = modifier
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(if (lit) colors.surfaceHighest else colors.surfaceElevated)
            .clickable(onClick = claim)
            .padding(horizontal = SEARCH_PADDING_H.dp, vertical = SEARCH_PADDING_V.dp),
        horizontalArrangement = Arrangement.spacedBy(SEARCH_ICON_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (searching || lit) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(RAIL_ICON.dp),
        )
        Text(
            text = if (searching) query else "Search",
            style = MaterialTheme.typography.labelLarge,
            color = if (searching) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (searching) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            // B is what leaves a search, and it is the only way out that does not
            // need the keyboard raised again to empty the box by hand.
            text = if (searching) "B  CLEAR" else "Y",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = HINT_ALPHA),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
