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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The catalogue as a television screen.
 *
 * The whole screen is the highlighted title: its backdrop fills the panel, its
 * name and story sit over the left of it, and the shelves lie across the bottom.
 * The handheld arrangement this replaced was a catalogue pane beside a detail
 * pane, which is right for a screen held at arm's length and wrong for one across
 * a room — it made the artwork thumbnail-sized and the prose unreadable from a
 * sofa, and it spent a third of a television on a column of text.
 *
 * Moving down the shelves scrolls the billboard away rather than resizing it. A
 * panel that shrinks while its contents change has to reflow mid-animation and
 * clips whatever no longer fits; a list that scrolls is one movement, and it is
 * the movement every other set-top catalogue makes, so it needs no explaining.
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
    val listState = rememberLazyListState()

    LaunchedEffect(state.cursor.row, rows.size) {
        if (rows.isNotEmpty()) {
            /*
             * The row above the focused one goes to the top, not the focused row
             * itself, so there is always something overhead to have come from —
             * and on the first shelf that something is the billboard. One rule
             * covers both, and the billboard needs no special case to stay on
             * screen while the top shelf is being walked.
             */
            listState.animateScrollToItem(state.cursor.row.coerceIn(0, rows.lastIndex))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        val available = (maxHeight - BAR_HEIGHT.dp).coerceAtLeast(MIN_CONTENT_HEIGHT.dp)
        val billboardHeight = couchBillboardHeight(available)
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
            if (message != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = SCREEN_INSET.dp * 2),
                    )
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = SHELF_GAP.dp),
                verticalArrangement = Arrangement.spacedBy(SHELF_GAP.dp),
            ) {
                item(key = "billboard") {
                    CouchBillboard(
                        item = highlighted,
                        resume = detail.resumeProgress,
                        shelfTitle = rows.getOrNull(state.cursor.row)?.title,
                        onPlay = onPlay,
                        onOpen = { onItemSelected(state.cursor.row, state.cursor.column) },
                        modifier = Modifier.fillMaxWidth().height(billboardHeight),
                    )
                }

                itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                    CouchShelf(
                        row = row,
                        focusedColumn = state.cursor.column.takeIf { index == state.cursor.row },
                        posterHeight = posterHeight,
                        onItemFocused = { column -> onItemFocused(index, column) },
                        onItemSelected = { column -> onItemSelected(index, column) },
                        modifier = Modifier.fillMaxWidth().height(shelfHeight),
                    )
                }
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

/** Films or shows on the left, the search box on the right. */
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
        Spacer(modifier = Modifier.weight(1f))
        MediaSearchField(
            query = query,
            onQueryChanged = onQueryChanged,
            requestFocus = searchRequested,
            onFocused = onSearchFocused,
            modifier = Modifier.width(SEARCH_WIDTH.dp),
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
    shelfTitle: String?,
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
            shelfTitle?.let { title ->
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

            if (item.overview.isNotBlank()) {
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = OVERVIEW_LINES,
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

/** One shelf: its name, and the titles on it. */
@Composable
private fun CouchShelf(
    row: MediaRow,
    focusedColumn: Int?,
    posterHeight: Dp,
    onItemFocused: (column: Int) -> Unit,
    onItemSelected: (column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            color = ThorTheme.colors.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(SHELF_HEADER_HEIGHT.dp)
                .padding(horizontal = SCREEN_INSET.dp),
        )
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
 * The pointer moves the cursor here rather than merely lighting the card, for
 * the same reason it does on the home shelf: the billboard above describes
 * whatever is selected, and a hover that only highlighted would leave the
 * largest thing on the television describing a different film from the one being
 * pointed at.
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

/** How tall the billboard is on a browse area [available] high. */
internal fun couchBillboardHeight(available: Dp): Dp =
    (available * BILLBOARD_FRACTION).coerceIn(MIN_BILLBOARD.dp, MAX_BILLBOARD.dp)

/** How tall one shelf is, header and cards together. */
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
 * not state one takes the width of the longest word in its caption. Deriving it
 * from the artwork keeps every card the shape of the picture on it, and keeps
 * the continue-watching shelf's stills from being cropped into portraits.
 */
internal fun couchCardWidth(posterHeight: Dp, landscape: Boolean): Dp =
    posterHeight * if (landscape) STILL_ASPECT else POSTER_ASPECT

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

private const val BILLBOARD_FRACTION = 0.54f
private const val MIN_BILLBOARD = 210
private const val MAX_BILLBOARD = 420
private const val SHELF_FRACTION = 0.42f
private const val MIN_SHELF = 150
private const val MAX_SHELF = 290
private const val MIN_POSTER = 96
private const val MAX_POSTER = 220
private const val MIN_CONTENT_HEIGHT = 260

private const val POSTER_ASPECT = 2f / 3f
private const val STILL_ASPECT = 16f / 9f

private const val BAR_HEIGHT = 56
private const val SCREEN_INSET = 26
private const val SECTION_GAP = 16
private const val SEARCH_WIDTH = 300
private const val SHELF_GAP = 12
private const val SHELF_HEADER_HEIGHT = 22
private const val SHELF_HEADER_GAP = 8
private const val CARD_GAP = 12
private const val CARD_LABEL_HEIGHT = 18
private const val CARD_LABEL_GAP = 6
/** Room around a card for the focused one to grow into without being clipped. */
private const val CARD_GROWTH = 7
private const val CARD_FOCUS_SCALE = 1.06f
private const val RESTING_ALPHA = 0.86f
private const val FOCUS_MILLIS = 160
private const val PROGRESS_HEIGHT = 5

private const val BILLBOARD_INSET = 18
private const val BILLBOARD_WIDTH_FRACTION = 0.54f
private const val BILLBOARD_GAP = 10
private const val LOGO_WIDTH_FRACTION = 0.62f
private const val LOGO_HEIGHT = 72
private const val OVERVIEW_LINES = 3
private const val ACTION_GAP = 10
private const val ACTION_PADDING_H = 18
private const val ACTION_PADDING_V = 11
private const val ACTION_ICON = 20
private const val HINT_ALPHA = 0.66f
private const val GENRES_SHOWN = 3

private const val SCRIM_KNEE = 0.58f
private const val SCRIM_HORIZON = 0.34f
