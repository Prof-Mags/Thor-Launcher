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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.zIndex
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.MediaItem
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
 * One category at a time, the way the games library is arranged: the shelf on
 * screen is the shelf being walked, and up and down change which one that is
 * rather than scrolling past it. Everything above it belongs to whichever title
 * the cursor is on — its backdrop fills the panel, and its name, facts and story
 * sit over the left of it.
 *
 * The handheld arrangement this replaced was a catalogue pane beside a detail
 * pane, which is right for a screen held at arm's length and wrong for one across
 * a room: it made the artwork thumbnail-sized and the prose unreadable from a
 * sofa, and it spent a third of a television on a column of text.
 *
 * A stack of shelves was the intermediate step, and the trouble with it is that
 * the description has to leave for the shelves to be reached. Holding the one
 * shelf still and swapping its contents keeps the title's own page on screen for
 * the whole time it is selected, which is the only reason to be reading it.
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
    // so the billboard never blanks between moving and the details landing.
    val highlighted = detail.item ?: state.highlighted
    val rowIndex = state.cursor.row.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val row = rows.getOrNull(rowIndex)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        val available = (maxHeight - BAR_HEIGHT.dp).coerceAtLeast(MIN_CONTENT_HEIGHT.dp)
        val shelfHeight = couchShelfHeight(available)
        val posterHeight = couchPosterHeight(shelfHeight)

        CouchBackdrop(item = highlighted)

        Column(modifier = Modifier.fillMaxSize()) {
            CouchCatalogueBar(
                type = state.type,
                query = query,
                onQueryChanged = onQueryChanged,
                searchRequested = searchRequested,
                onSearchFocused = onSearchFocused,
                onTypeSelected = onTypeSelected,
            )

            val message = browseMessage(state)
            if (message != null || row == null) {
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
                return@Column
            }

            // The billboard takes whatever the shelf leaves rather than a height
            // of its own: it is the description of one title, and there is no
            // length it should be on a screen that has room for it.
            CouchBillboard(
                item = highlighted,
                resume = detail.resumeProgress,
                overviewLines = couchOverviewLines(available - shelfHeight),
                onPlay = onPlay,
                onOpen = { onItemSelected(rowIndex, state.cursor.column) },
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            )

            /*
             * Keyed on the shelf, so each category gets its own scroll position.
             *
             * Without this the row's list state is reused across categories, and
             * changing shelf keeps the offset the last one was left at — a new
             * category that opens halfway along itself, with its first titles
             * already scrolled off the left.
             */
            key(row.id) {
                CouchShelf(
                    row = row,
                    rowIndex = rowIndex,
                    rowCount = rows.size,
                    focusedColumn = state.cursor.column,
                    posterHeight = posterHeight,
                    onItemFocused = { column -> onItemFocused(rowIndex, column) },
                    onItemSelected = { column -> onItemSelected(rowIndex, column) },
                    modifier = Modifier.fillMaxWidth().height(shelfHeight),
                )
            }
        }
    }
}

/** Which of the browse states has nothing to draw a shelf for. */
private fun browseMessage(state: MoviesUiState): String? = when {
    state.setupMessage != null -> state.setupMessage
    state.visibleRows.isNotEmpty() -> null
    state.loading -> "Loading library..."
    state.searching -> "Searching..."
    state.isSearching -> "Nothing matched \"${state.query}\"."
    else -> null
}

/**
 * The highlighted title, filling the screen behind everything.
 *
 * Cropped rather than fitted. A scraped backdrop is 16:9 and so is the
 * television, so filling it is the one arrangement that shows the picture at the
 * size it was made for; fitting would letterbox a photograph inside a screen of
 * exactly its own shape.
 */
@Composable
internal fun CouchBackdrop(item: MediaItem?) {
    val colors = ThorTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        val art = item?.backdropUrl ?: item?.posterUrl
        if (art != null) {
            ArtworkImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Two scrims, each doing one job: the first keeps the billboard's prose
        // legible over whatever is behind it, the second buries the bottom of
        // the picture so the shelves are read as being in front of it rather
        // than lost in it.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to colors.background.copy(alpha = 0.94f),
                    SCRIM_KNEE to colors.background.copy(alpha = 0.62f),
                    1f to colors.background.copy(alpha = 0.18f),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0.42f),
                    SCRIM_HORIZON to Color.Transparent,
                    1f to colors.background.copy(alpha = 0.96f),
                ),
            ),
        )
    }
}

/**
 * Everything the catalogue can be told, in one group at the left.
 *
 * Films, shows and search are three ways of choosing what the shelves hold, so
 * they sit together rather than at opposite ends of the bar. The right-hand end
 * is deliberately empty: the couch shell's own clock and profile cluster is
 * directly above it, and a second right-aligned group under the first reads as
 * one crowded corner on a screen with a whole empty half.
 */
@Composable
private fun CouchCatalogueBar(
    type: MediaType,
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean,
    onSearchFocused: () -> Unit,
    onTypeSelected: (MediaType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT.dp)
            .padding(horizontal = SCREEN_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
    ) {
        Text(
            text = "CINEMA",
            style = MaterialTheme.typography.labelLarge,
            color = ThorTheme.colors.cursor,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        MediaTypeTabs(selected = type, onSelected = onTypeSelected)
        CouchSearchChip(
            query = query,
            onQueryChanged = onQueryChanged,
            requestFocus = searchRequested,
            onFocused = onSearchFocused,
        )
    }
}

/**
 * The way in to the keyboard, and the record of what was asked.
 *
 * Not a text field. The bottom panel's search box is one because it is composed
 * beside the keyboard that fills it in, where showing a caret in the box being
 * typed into is the whole point; here the keyboard is a full-screen overlay and
 * the box underneath it would be a wide empty rectangle for as long as nobody
 * had searched for anything. A chip states its shortcut when idle and becomes
 * the query when there is one, so the bar says what the shelves are answering.
 *
 * Focus is claimed directly rather than through a [ThorInputField]. The field is
 * a display of a value the keyboard is already editing through the sink below,
 * and this screen has somewhere better to show that value than a box of its own.
 */
@Composable
private fun CouchSearchChip(
    query: String,
    onQueryChanged: (String) -> Unit,
    requestFocus: Boolean,
    onFocused: () -> Unit,
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
        modifier = Modifier
            .widthIn(min = SEARCH_MIN_WIDTH.dp, max = SEARCH_MAX_WIDTH.dp)
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(if (lit) colors.surfaceHighest else colors.surfaceElevated)
            .clickable(onClick = claim)
            .padding(horizontal = SEARCH_PADDING_H.dp, vertical = SEARCH_PADDING_V.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (searching || lit) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(ACTION_ICON.dp),
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

/**
 * What the cursor is resting on, said at the size of a room.
 *
 * The wordmark is used in place of the title wherever the provider has one, the
 * way the title's own marketing would set it, and the plain name is the fallback
 * rather than an extra line under it — printing both says the same thing twice
 * in two different typefaces.
 */
@Composable
private fun CouchBillboard(
    item: MediaItem?,
    resume: WatchProgress?,
    overviewLines: Int,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    if (item == null) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            Text(
                text = "Choose something to watch",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SCREEN_INSET.dp),
            )
        }
        return
    }

    Column(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = BILLBOARD_INSET.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(BILLBOARD_WIDTH_FRACTION),
            verticalArrangement = Arrangement.spacedBy(BILLBOARD_GAP.dp),
        ) {
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
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Black,
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

            Ratings(item.ratings)

            if (item.overview.isNotBlank() && overviewLines > 0) {
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = overviewLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            resume?.takeIf { it.isResumable }?.let { progress ->
                Text(
                    text = couchResumeLabel(progress),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ACTION_GAP.dp)) {
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
                    label = if (item.isSeries) "Episodes" else "Sources",
                    hint = "A",
                    icon = Icons.Rounded.Info,
                    primary = false,
                    onClick = onOpen,
                )
            }
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

/** The one shelf on screen: its name, where it sits, and the titles on it. */
@Composable
private fun CouchShelf(
    row: MediaRow,
    rowIndex: Int,
    rowCount: Int,
    focusedColumn: Int,
    posterHeight: Dp,
    onItemFocused: (column: Int) -> Unit,
    onItemSelected: (column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val cardWidth = couchCardWidth(posterHeight)

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
        if (row.items.isEmpty()) return@LaunchedEffect
        val target = focusedColumn.coerceIn(0, row.items.lastIndex)
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
            horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = ThorTheme.colors.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CategoryPosition(index = rowIndex, count = rowCount)
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
 * Which category this is, of how many.
 *
 * One shelf on screen answers "what is on this one" perfectly and "what else is
 * there" not at all, so the count is drawn beside its name. Marks while they can
 * be told apart, a figure once they cannot: twenty identical dashes say no more
 * than "several" and take a third of the row saying it.
 */
@Composable
private fun CategoryPosition(index: Int, count: Int) {
    if (count <= 1) return
    val colors = ThorTheme.colors

    if (count > PIP_LIMIT) {
        Text(
            text = "${index + 1} / $count",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(PIP_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { position ->
            val current = position == index
            Box(
                modifier = Modifier
                    .size(
                        width = if (current) PIP_CURRENT_WIDTH.dp else PIP_WIDTH.dp,
                        height = PIP_HEIGHT.dp,
                    )
                    .clip(ThorTheme.shapes.pill)
                    .background(
                        if (current) {
                            colors.cursor
                        } else {
                            colors.onSurfaceVariant.copy(alpha = PIP_ALPHA)
                        },
                    ),
            )
        }
    }
}

/**
 * One title on a shelf, always as its poster.
 *
 * Continue watching is a landscape shelf everywhere else, because a still from
 * the episode is the better reminder of where you got to on a screen with room
 * for one row. Here it is the only row on screen and it sits beside the same
 * titles in every other category, so a shelf of wide stills would be the one
 * that broke the rhythm — and its cards would be half the height of the rest for
 * no gain, since the film's own poster is what the eye is already scanning for.
 *
 * The pointer moves the cursor rather than merely lighting the card, for the same
 * reason it does on the home shelf: the billboard above describes whatever is
 * selected, and a hover that only highlighted would leave the largest thing on
 * the television describing a different film from the one being pointed at.
 */
@Composable
private fun CouchTitleCard(
    item: MediaItem,
    progress: WatchProgress?,
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
                model = item.posterUrl ?: item.backdropUrl,
                contentDescription = item.title,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(artAlpha),
            )

            if (progress != null && progress.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    color = colors.cursor,
                    trackColor = Color.White.copy(alpha = 0.24f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(PROGRESS_HEIGHT.dp),
                )
            }
        }

        Text(
            text = progress?.let(::couchResumeLabel) ?: item.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(CARD_LABEL_HEIGHT.dp),
        )
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

/**
 * How tall the one shelf is, header and cards together.
 *
 * The only region on this screen with a height of its own; the description above
 * takes whatever is left. It is written as a share rather than a number because
 * the shelf has to leave room for a synopsis on a panel of any size, and as a
 * clamped share because a fifth of a very tall panel is a shelf of enormous
 * posters and a fifth of a short one is a shelf with no cards on it.
 */
internal fun couchShelfHeight(available: Dp): Dp =
    (available * SHELF_FRACTION).coerceIn(MIN_SHELF.dp, MAX_SHELF.dp)

/** The artwork height left inside a shelf once its header and label are spent. */
internal fun couchPosterHeight(shelfHeight: Dp): Dp {
    val spent = SHELF_HEADER_HEIGHT + SHELF_HEADER_GAP +
        CARD_LABEL_HEIGHT + CARD_LABEL_GAP + CARD_GROWTH * 2
    return (shelfHeight - spent.dp).coerceIn(MIN_POSTER.dp, MAX_POSTER.dp)
}

/**
 * The card's width, taken from its height rather than set on its own.
 *
 * Cards inside a `LazyRow` are measured with no width limit, so a card that does
 * not state one takes the width of the longest word in its caption - which is
 * how a shelf ends up with one enormous gap in it and no obvious cause.
 */
internal fun couchCardWidth(posterHeight: Dp): Dp = posterHeight * POSTER_ASPECT

/**
 * How much of the story fits above the shelf, in lines.
 *
 * The synopsis is the one part of the billboard with no length of its own, so it
 * is the part that gets measured. Everything else there - the wordmark, the
 * facts, the scores, the buttons - is furniture of a known height, and a fixed
 * line count would push all of it up off the top of the screen on a short panel
 * rather than simply printing less prose.
 *
 * Zero is a real answer. A panel with room for the name of the film and the
 * button that plays it, and nothing else, should show those two things.
 */
internal fun couchOverviewLines(billboardHeight: Dp): Int {
    val forProse = billboardHeight.value - BILLBOARD_FURNITURE
    return (forProse / OVERVIEW_LINE_HEIGHT).toInt().coerceIn(0, MAX_OVERVIEW_LINES)
}

/** The one-line summary under the billboard's title. */
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
    return listOfNotNull(episode, "$minutesLeft min left").joinToString(FACT_SEPARATOR)
}

internal const val FACT_SEPARATOR = "  /  "

private const val SHELF_FRACTION = 0.40f
private const val MIN_SHELF = 150
private const val MAX_SHELF = 300
private const val MIN_POSTER = 96
private const val MAX_POSTER = 250
private const val MIN_CONTENT_HEIGHT = 260

private const val POSTER_ASPECT = 2f / 3f

private const val BAR_HEIGHT = 56
private const val SCREEN_INSET = 26
private const val SECTION_GAP = 16
private const val COUCH_SEARCH_FIELD_ID = "movies-couch-search"
private const val SEARCH_MIN_WIDTH = 132
private const val SEARCH_MAX_WIDTH = 300
private const val SEARCH_PADDING_H = 14
private const val SEARCH_PADDING_V = 9
private const val SHELF_HEADER_HEIGHT = 24
private const val SHELF_HEADER_GAP = 8
/** Above this many categories the marks stop being countable and become a figure. */
private const val PIP_LIMIT = 10
private const val PIP_WIDTH = 8
private const val PIP_CURRENT_WIDTH = 22
private const val PIP_HEIGHT = 4
private const val PIP_GAP = 5
private const val PIP_ALPHA = 0.38f
private const val CARD_GAP = 12
private const val CARD_LABEL_HEIGHT = 18
private const val CARD_LABEL_GAP = 6
/** Room around a card for the focused one to grow into without being clipped. */
private const val CARD_GROWTH = 7
private const val CARD_FOCUS_SCALE = 1.06f
private const val RESTING_ALPHA = 0.86f
private const val FOCUS_MILLIS = 160
private const val PROGRESS_HEIGHT = 5

private const val BILLBOARD_INSET = 14
private const val BILLBOARD_WIDTH_FRACTION = 0.54f
private const val BILLBOARD_GAP = 8
private const val LOGO_WIDTH_FRACTION = 0.62f
private const val LOGO_HEIGHT = 56
/**
 * The billboard minus its story: insets, wordmark, facts, scores and buttons.
 *
 * A measurement of the layout above rather than a preference. It is deliberately
 * a little generous - erring high prints one line fewer than would have fitted,
 * erring low pushes the title off the top of the screen.
 */
private const val BILLBOARD_FURNITURE = 220f
private const val OVERVIEW_LINE_HEIGHT = 21f
private const val MAX_OVERVIEW_LINES = 6
private const val ACTION_GAP = 10
private const val ACTION_PADDING_H = 18
private const val ACTION_PADDING_V = 11
private const val ACTION_ICON = 20
private const val HINT_ALPHA = 0.66f
private const val GENRES_SHOWN = 3

private const val SCRIM_KNEE = 0.58f
private const val SCRIM_HORIZON = 0.34f
