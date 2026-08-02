package com.thor.feature.home.couch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.LauncherTab
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.home.EditMode
import com.thor.feature.home.LauncherUiState
import com.thor.feature.home.component.AppIcon

/**
 * The launcher as one screen, read from across a room.
 *
 * Couch mode is not the handheld layout rearranged — it is a different reading
 * distance, and almost every decision follows from that. The handheld panels are
 * held at arm's length and answer "what is this, exactly": small square icons,
 * dense text, a whole panel of facts. From a sofa none of that is legible, and
 * the questions change to "what do I have" and "what is this one".
 *
 * So: artwork carries the page rather than captioning it, tiles are the shape box
 * art actually is, the section bar moves to the top where a television puts its
 * navigation, and the details shrink to the single line worth reading at that
 * size. The focused game's own art fills the screen behind everything.
 *
 * What it deliberately does *not* change is the grid underneath. Same pages, same
 * placements, same cursor, same buttons — an icon is in the cell you put it in
 * here too, and moving one here moves it everywhere. This is a way of drawing the
 * launcher, not a second launcher.
 */
@Composable
fun CouchScreen(
    state: LauncherUiState,
    tabs: List<LauncherTab>,
    selectedTab: LauncherTab,
    navCursor: LauncherTab?,
    onTabSelected: (LauncherTab) -> Unit,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    /** Content for a section other than Home, hosted whole. */
    sectionContent: (@Composable (LauncherTab) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val focused = state.selection

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Backdrop(entry = focused)

        Column(modifier = Modifier.fillMaxSize()) {
            CouchTopBar(
                tabs = tabs,
                selectedTab = selectedTab,
                focusedTab = navCursor,
                pageCount = state.visiblePageCount,
                currentPage = state.currentPage,
                onTabSelected = onTabSelected,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (selectedTab.isHome) {
                    CouchShelf(
                        state = state,
                        onCellTapped = onCellTapped,
                        onCellLongPressed = onCellLongPressed,
                        onPageChanged = onPageChanged,
                    )
                } else {
                    sectionContent?.invoke(selectedTab)
                }
            }

            // Only on Home: a section hosting its own content has said what it is,
            // and a caption about a grid cell under it would be describing
            // something no longer on screen.
            if (selectedTab.isHome) {
                CouchCaption(entry = focused, platforms = state.platformsById)
            }
        }
    }
}

/**
 * The focused entry's own artwork, filling the screen behind everything.
 *
 * The single biggest difference from the handheld layout, and the reason couch
 * mode reads at distance: what the screen is mostly showing is the thing you are
 * pointing at, at the size a television shows it.
 *
 * Scrimmed hard, and by a gradient rather than a flat wash — the tiles and the
 * caption sit at the bottom, so that is where the backdrop has to give way, while
 * the top can stay bright enough to still be a picture.
 */
@Composable
private fun Backdrop(entry: GridEntry?) {
    val art = (entry as? GameEntry)?.metadata?.artwork?.backgroundImage ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // Keyed on the URL so a new game cross-fades in rather than popping.
        ArtworkImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(BACKDROP_ALPHA),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.45f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
    }
}

/**
 * Sections at the top, pages at the right.
 *
 * Above the content because that is where a ten-foot interface puts navigation —
 * the handheld bar is at the bottom because that is where the thumbs are, and in
 * couch mode there are no thumbs on the screen at all.
 *
 * Sections are drawn even when there is only one, unlike the handheld bar. That
 * bar disappears at one tab because it is 52dp taken out of a small panel; here
 * it is the frame of the screen, and a launcher that loses its header when you
 * remove an extension would look broken rather than tidy.
 */
@Composable
private fun CouchTopBar(
    tabs: List<LauncherTab>,
    selectedTab: LauncherTab,
    focusedTab: LauncherTab?,
    pageCount: Int,
    currentPage: Int,
    onTabSelected: (LauncherTab) -> Unit,
) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT.dp)
            .padding(horizontal = BAR_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TAB_GAP.dp),
    ) {
        Text(
            text = "LOKI",
            style = MaterialTheme.typography.titleMedium,
            color = colors.cursor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(end = TAB_GAP.dp),
        )

        tabs.forEach { tab ->
            CouchTab(
                label = tab.label,
                selected = tab == selectedTab,
                focused = tab == focusedTab,
                onClick = { onTabSelected(tab) },
            )
        }

        Box(modifier = Modifier.weight(1f))

        if (pageCount > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PAGE_DOT_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { page ->
                    val here = page == currentPage
                    Box(
                        modifier = Modifier
                            .size(if (here) PAGE_DOT_ON.dp else PAGE_DOT_OFF.dp)
                            .clip(CircleShape)
                            .background(
                                if (here) colors.cursor else colors.onSurfaceVariant.copy(alpha = 0.4f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CouchTab(
    label: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors

    Box(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(
                when {
                    focused -> colors.cursor.copy(alpha = 0.22f)
                    selected -> colors.surfaceHighest
                    else -> Color.Transparent
                },
            )
            .then(
                if (focused) {
                    Modifier.border(2.dp, colors.cursor, ThorTheme.shapes.pill)
                } else {
                    Modifier
                },
            )
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = TAB_PADDING_H.dp, vertical = TAB_PADDING_V.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected || focused) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * The grid, as pages of upright tiles.
 *
 * The user's own grid shape, unchanged: `spec.rows` by `spec.columns`, the same
 * pages and the same placements, so the cursor the shell already drives lands
 * exactly where it does on the handheld panel and an icon does not move because
 * the screen mode did.
 *
 * Only the cell's *shape* is different. Each one holds the tallest 2:3 tile that
 * fits its slot, centred — which is why lowering the density is worth doing here:
 * three rows of covers on a television is a wall of stamps, and two rows is a
 * shelf. That is the user's pinch to make, not this file's to force.
 */
@Composable
private fun CouchShelf(
    state: LauncherUiState,
    onCellTapped: (row: Int, column: Int) -> Unit,
    onCellLongPressed: (row: Int, column: Int) -> Unit,
    onPageChanged: (Int) -> Unit,
) {
    val spec = state.spec
    val heldId = (state.editMode as? EditMode.Holding)?.entryId
    val pageCount = state.visiblePageCount.coerceAtLeast(1)
    val cellsPerPage = spec.cellsPerPage.coerceAtLeast(1)

    val pagerState = rememberPagerState(
        initialPage = state.currentPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    // Buttons drive the pager, swipes drive the view model: the same two-way
    // arrangement the handheld grid uses, so both inputs converge on one page.
    LaunchedEffect(state.currentPage, pageCount) {
        val target = state.currentPage.coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }

    // Placements indexed once per layout change, rather than scanned per cell —
    // the same reason the handheld grid does it, and it matters more here because
    // a backdrop change recomposes the whole screen.
    val byCell = remember(state.placements, state.entriesById, spec.columns) {
        buildMap<Int, MutableMap<Int, GridEntry>> {
            state.placements.forEach { placement ->
                state.entriesById[placement.entryId]?.let { entry ->
                    getOrPut(placement.pageIndex) { mutableMapOf() }[
                        placement.row * spec.columns + placement.column,
                    ] = entry
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = state.pagePrefetchRadius,
    ) { page ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BAR_INSET.dp, vertical = SHELF_INSET.dp),
            verticalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
        ) {
            repeat(spec.rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
                ) {
                    repeat(spec.columns) { column ->
                        val cell = row * spec.columns + column
                        val entry = if (state.isFolderOpen) {
                            state.openFolderContents.getOrNull(page * cellsPerPage + cell)
                        } else {
                            byCell[page]?.get(cell)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (state.touchEnabled) {
                                        Modifier.pointerInput(row, column, entry?.id) {
                                            detectTapGestures(
                                                onTap = { onCellTapped(row, column) },
                                                onLongPress = { onCellLongPressed(row, column) },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouchTile(
                                entry = entry,
                                // Resolved for a platform folder too, not only a
                                // game: the folder needs it to know it is a
                                // *system* and may wear that system's wordmark.
                                platform = when (entry) {
                                    is GameEntry -> state.platformsById[entry.platformId]
                                    is FolderEntry -> PlatformFolders.platformIdOf(entry.id)
                                        ?.let { state.platformsById[it] }

                                    else -> null
                                },
                                focused = page == state.currentPage &&
                                    row == state.cursor.row &&
                                    column == state.cursor.column,
                                held = heldId != null && entry?.id == heldId,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One upright tile.
 *
 * Asks for the cover first and falls back to what the grid would have used.
 * `cellImage` is `icon ?: boxArt` and is deliberately square — it exists to fill a
 * 1:1 handheld cell and prefers an app icon precisely so that grid does not
 * letterbox. Handed to a 2:3 slot it gives back the square it was built to be,
 * which is the cover with its top and bottom already gone.
 *
 * An empty cell draws a faint outline rather than nothing. On the handheld panel
 * an empty cell is obvious because the grid is dense and close; at this size a
 * blank region reads as the end of the content, and the cursor moving into
 * nothing looks like the cursor breaking.
 */
@Composable
private fun CouchTile(
    entry: GridEntry?,
    platform: Platform?,
    focused: Boolean,
    held: Boolean,
) {
    val colors = ThorTheme.colors
    val scale by animateFloatAsState(
        targetValue = if (focused) FOCUS_SCALE else 1f,
        animationSpec = tween(FOCUS_MS),
        label = "couch-tile-scale",
    )

    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TILE_LABEL_GAP.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(TILE_ASPECT)
                .scale(scale)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest.copy(alpha = if (entry == null) 0.25f else 1f))
                .then(
                    when {
                        focused -> Modifier.border(FOCUS_RING.dp, colors.cursor, ThorTheme.shapes.small)
                        entry == null -> Modifier.border(
                            1.dp,
                            colors.outline.copy(alpha = 0.25f),
                            ThorTheme.shapes.small,
                        )

                        else -> Modifier
                    },
                )
                .alpha(if (held) HELD_ALPHA else 1f),
            contentAlignment = Alignment.Center,
        ) {
            when (entry) {
                is GameEntry -> ArtworkImage(
                    model = entry.metadata.artwork.boxArt ?: entry.metadata.artwork.cellImage,
                    contentDescription = entry.title,
                    fallbackText = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                /*
                 * An app icon is square and small, and cropping one into a 2:3
                 * tile would cut it in half. Centred at its own size instead, on
                 * the tile's own surface — which is also what tells the eye at a
                 * glance that this is an application rather than a game.
                 */
                is AppEntry -> if (entry.customIconUri != null) {
                    ArtworkImage(
                        model = entry.customIconUri,
                        contentDescription = entry.title,
                        fallbackText = entry.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(APP_ICON_FRACTION).aspectRatio(1f),
                    )
                } else {
                    AppIcon(
                        packageName = entry.packageName,
                        title = entry.title,
                        shape = ThorTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(APP_ICON_FRACTION).aspectRatio(1f),
                    )
                }

                is FolderEntry -> FolderTile(folder = entry, platform = platform)

                else -> Unit
            }
        }

        Text(
            text = entry?.title.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A folder, wearing its system's artwork where it has one.
 *
 * A platform folder is a system rather than a folder the user made, and at this
 * size the difference is worth drawing: a console's wordmark says what is inside
 * far faster than a folder glyph and a name underneath.
 */
@Composable
private fun FolderTile(folder: FolderEntry, platform: Platform?) {
    val colors = ThorTheme.colors
    val logo = platform?.artwork?.logoUri

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            ArtworkImage(
                model = logo,
                contentDescription = folder.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(LOGO_FRACTION),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(FOLDER_GLYPH_FRACTION).aspectRatio(1f),
            )
        }
    }
}

/**
 * The one line worth reading from a sofa.
 *
 * The handheld information panel has room for a paragraph, four statistics and a
 * strip of screenshots, and every one of them is unreadable at this distance. So
 * this is the title at a size a television can carry, and beneath it the three
 * facts that actually decide whether to press A: what system it is, when it came
 * out, and whether you have played it.
 */
@Composable
private fun CouchCaption(entry: GridEntry?, platforms: Map<String, Platform>) {
    val colors = ThorTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CAPTION_HEIGHT.dp)
            .padding(horizontal = BAR_INSET.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = entry?.title ?: "Nothing selected",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val facts = when (entry) {
            is GameEntry -> listOfNotNull(
                platforms[entry.platformId]?.name,
                entry.metadata.releaseYear?.toString(),
                entry.stats.totalPlayMillis.takeIf { it > 0L }?.asPlaytime(),
                "Favourite".takeIf { entry.isFavorite },
            )

            is FolderEntry -> listOf("${entry.childIds.size} items")
            is AppEntry -> listOf("App")
            else -> emptyList()
        }

        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString("  ·  "),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "12h 40m", or "45m" under an hour. Zero never reaches here. */
private fun Long.asPlaytime(): String {
    val minutes = this / 60_000L
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

/** Box art's shape, which is what the tiles are built around. */
private const val TILE_ASPECT = 2f / 3f

private const val TOP_BAR_HEIGHT = 56
private const val CAPTION_HEIGHT = 84
private const val BAR_INSET = 28
private const val SHELF_INSET = 12
private const val TILE_GAP = 14
private const val TILE_LABEL_GAP = 6
private const val TAB_GAP = 10
private const val TAB_PADDING_H = 16
private const val TAB_PADDING_V = 7
private const val PAGE_DOT_ON = 9
private const val PAGE_DOT_OFF = 6
private const val PAGE_DOT_GAP = 7

private const val FOCUS_SCALE = 1.08f
private const val FOCUS_RING = 3
private const val FOCUS_MS = 140
private const val HELD_ALPHA = 0.45f
private const val BACKDROP_ALPHA = 0.55f
private const val APP_ICON_FRACTION = 0.62f
private const val LOGO_FRACTION = 0.74f
private const val FOLDER_GLYPH_FRACTION = 0.44f
