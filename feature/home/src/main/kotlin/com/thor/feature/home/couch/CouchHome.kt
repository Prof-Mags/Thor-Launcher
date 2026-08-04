package com.thor.feature.home.couch

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.home.LauncherUiState
import java.util.Locale

/**
 * Couch mode's home screen, laid out as a television dashboard.
 *
 * Six regions, and the reason it is one screen rather than a set of pages is the
 * same reason the shelf below it shows one rail at a time: on a television the
 * user is across the room with a controller, and every extra press to find out
 * what is on the machine is a press made blind.
 *
 * ```
 * ┌──┬──────────────────────────────────────────────┐
 * │  │  CONTINUE PLAYING            YOUR LIBRARY    │
 * │r │  ┌────┐ title, facts, actions   all games    │
 * │a │  │art │ ▓▓▓▓▓▓▓░░░ 48%          favourites   │
 * │i │  └────┘                         recent …     │
 * │l ├──────────────────────────────────────────────┤
 * │  │  YOUR GAMES                        VIEW ALL  │
 * │  │  ▢ ▢ ▢ ▢ ▢ ▢ ▢ ▢ ▢ ▢                        │
 * │  ├──────────────────────────────────────────────┤
 * │  │  QUICK ACCESS    SYSTEM        STORAGE       │
 * └──┴──────────────────────────────────────────────┘
 * ```
 *
 * **What is real and what is not.** The counts, the shelf, the hero and the
 * storage bar are read from the library and the filesystem. Trophies, downloads,
 * controllers, power and the search and filter actions have nothing behind them
 * yet — this launcher has no achievement client, no download manager and no way
 * for an unprivileged app to power the device down. They are drawn as
 * [CouchPlaceholder] rather than omitted, so the shape of the screen can be
 * judged before the parts that would fill it exist; every one of them is marked
 * in the source and dimmed on screen, because a control that looks live and does
 * nothing is worse than one that says it is not ready.
 */
@Composable
internal fun CouchHome(
    state: LauncherUiState,
    rails: List<CouchRail>,
    focus: CouchFocus,
    onEntryFocused: (rail: Int, item: Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    onRailSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val entries = remember(state.entriesById) {
        state.entriesById.values.filterNot(GridEntry::isHidden)
    }
    val counts = remember(entries) { couchLibraryCounts(entries) }

    val shelfRail = rails.getOrNull(focus.rail)
    val focusedEntry = shelfRail?.entries?.getOrNull(focus.item)
    val accent = focusedEntry?.platform(state.platformsById)
        ?.let { Color(it.accentArgb) }
        ?: colors.cursor

    // The hero is what the user was last doing, which is not the same thing as
    // what the cursor is on: the shelf moves as they browse and the top of the
    // screen should not follow it around.
    val continueEntry = remember(entries) {
        entries.filter { it.lastPlayedAt() != null }.maxByOrNull { it.lastPlayedAt() ?: 0L }
    }

    Row(modifier = modifier.fillMaxSize()) {
        CouchSideRail(
            rails = rails,
            selectedRail = focus.rail,
            accent = accent,
            onRailSelected = onRailSelected,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CONTENT_INSET.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
            ) {
                CouchContinuePlaying(
                    entry = continueEntry,
                    platform = continueEntry?.platform(state.platformsById),
                    accent = accent,
                    onResume = { continueEntry?.let(onEntrySelected) },
                    onMoreInfo = { continueEntry?.let(onEntryLongPressed) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                CouchLibraryPanel(
                    counts = counts,
                    modifier = Modifier.width(LIBRARY_PANEL_WIDTH.dp).fillMaxHeight(),
                )
            }

            CouchGamesShelf(
                rail = shelfRail,
                focusedItem = focus.item,
                platforms = state.platformsById,
                accent = accent,
                onEntryFocused = { item -> onEntryFocused(focus.rail, item) },
                onEntrySelected = onEntrySelected,
                onEntryLongPressed = onEntryLongPressed,
                modifier = Modifier.fillMaxWidth().height(SHELF_BLOCK_HEIGHT.dp),
            )

            CouchDashboardBar(
                modifier = Modifier.fillMaxWidth().height(DASHBOARD_HEIGHT.dp),
            )
        }
    }
}

// ---- Left rail --------------------------------------------------------------

/**
 * The vertical rail, which jumps between shelves rather than between screens.
 *
 * Every icon here is a rail the deck already builds, so this is a shortcut into
 * the library rather than a second navigation model — walking down with the stick
 * reaches all of the same places. Downloads is the exception and is marked as
 * such.
 */
@Composable
private fun CouchSideRail(
    rails: List<CouchRail>,
    selectedRail: Int,
    accent: Color,
    onRailSelected: (Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val destinations = remember(rails) {
        listOf(
            CouchRailShortcut(Icons.Rounded.Home, "Home", rails.indexOfFirst { it.id == "continue" }),
            CouchRailShortcut(Icons.Rounded.SportsEsports, "Games", rails.indexOfFirst { it.id.startsWith("platform:") }),
            CouchRailShortcut(Icons.Rounded.FavoriteBorder, "Favourites", rails.indexOfFirst { it.id == "favourites" }),
            CouchRailShortcut(Icons.Rounded.History, "Recent", rails.indexOfFirst { it.id == "continue" }),
            CouchRailShortcut(Icons.Rounded.Download, "Downloads", -1),
            CouchRailShortcut(Icons.Rounded.FolderOpen, "Collections", rails.indexOfFirst { it.id == "collections" }),
        )
    }

    Column(
        modifier = Modifier
            .width(SIDE_RAIL_WIDTH.dp)
            .fillMaxHeight()
            .padding(vertical = CONTENT_INSET.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SIDE_RAIL_GAP.dp),
    ) {
        destinations.forEach { destination ->
            val reachable = destination.railIndex >= 0
            val selected = reachable && destination.railIndex == selectedRail
            Box(
                modifier = Modifier
                    .size(SIDE_RAIL_ITEM.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                    )
                    .then(
                        if (selected) {
                            Modifier.border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = reachable) { onRailSelected(destination.railIndex) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = when {
                        selected -> accent
                        reachable -> colors.onSurfaceVariant
                        else -> colors.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class CouchRailShortcut(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val railIndex: Int,
)

// ---- Continue playing -------------------------------------------------------

@Composable
private fun CouchContinuePlaying(
    entry: GridEntry?,
    platform: Platform?,
    accent: Color,
    onResume: () -> Unit,
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    Column(modifier = modifier) {
        CouchSectionLabel("Continue playing")
        Spacer(Modifier.height(10.dp))

        if (entry == null) {
            CouchEmptyPanel(
                message = "Nothing played yet. Anything you launch shows up here.",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }

        val game = entry as? GameEntry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = 0.55f))
                .border(1.dp, colors.outline.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .padding(HERO_PADDING.dp),
            horizontalArrangement = Arrangement.spacedBy(HERO_PADDING.dp),
        ) {
            ArtworkImage(
                model = game?.metadata?.artwork?.cellImage,
                contentDescription = entry.title,
                fallbackText = entry.title,
                fallbackTint = accent,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(HERO_ART_ASPECT)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = (platform?.shortName?.ifBlank { platform.name } ?: entry.typeLabel())
                        .uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    game?.stats?.totalPlayMillis
                        ?.takeIf { it > 0L }
                        ?.let { CouchFactChip(Icons.Rounded.Schedule, it.asCouchPlaytime()) }
                    entry.lastPlayedAt()
                        ?.let { CouchFactChip(Icons.Rounded.History, it.asCouchRelativeTime()) }
                    // No achievement client ships, so there is nothing to count.
                    CouchFactChip(Icons.Rounded.EmojiEvents, "-- / --", placeholder = true)
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CouchActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Resume",
                        primary = true,
                        accent = accent,
                        onClick = onResume,
                    )
                    CouchActionButton(
                        icon = Icons.Rounded.Info,
                        label = "More info",
                        primary = false,
                        accent = accent,
                        onClick = onMoreInfo,
                    )
                }

                Spacer(Modifier.weight(1f))
                CouchCompletionBar(game = game, accent = accent)
            }
        }
    }
}

/**
 * How far through the game is, when the library knows.
 *
 * Real whenever a scraper supplied a completion time — play time over that,
 * which is the same figure the shelf card's own bar uses. Without one there is
 * no honest percentage to draw, so it says so rather than showing an empty bar
 * that reads as zero progress.
 */
@Composable
private fun CouchCompletionBar(game: GameEntry?, accent: Color) {
    val colors = ThorTheme.colors
    val completionMillis = game?.metadata?.completionMinutes
        ?.takeIf { it > 0 }
        ?.times(60_000L)
    val progress = if (completionMillis != null && game.stats.totalPlayMillis > 0L) {
        (game.stats.totalPlayMillis.toFloat() / completionMillis).coerceIn(0f, 1f)
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.outline.copy(alpha = 0.25f)),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Text(
            text = progress
                ?.let { "${(it * 100).toInt()}% complete" }
                ?: "No completion time",
            style = MaterialTheme.typography.labelMedium,
            color = if (progress != null) {
                colors.onSurfaceVariant
            } else {
                colors.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA)
            },
            maxLines = 1,
        )
    }
}

// ---- Library panel ----------------------------------------------------------

/** What the library holds, counted from the entries themselves. */
internal data class CouchLibraryCounts(
    val allGames: Int,
    val favourites: Int,
    val recentlyPlayed: Int,
    val installed: Int,
    val collections: Int,
)

internal fun couchLibraryCounts(entries: List<GridEntry>): CouchLibraryCounts {
    val games = entries.filterIsInstance<GameEntry>()
    return CouchLibraryCounts(
        allGames = games.size,
        favourites = entries.count(GridEntry::isFavorite),
        recentlyPlayed = entries.count { it.lastPlayedAt() != null },
        installed = entries.count { it is AppEntry },
        // Folders the user made. The per-platform folders the scanner creates are
        // the library organising itself and are not collections anybody chose.
        collections = entries.count {
            it is FolderEntry && PlatformFolders.platformIdOf(it.id) == null
        },
    )
}

@Composable
private fun CouchLibraryPanel(counts: CouchLibraryCounts, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(modifier = modifier) {
        CouchSectionLabel("Your library")
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = 0.55f))
                .border(1.dp, colors.outline.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
        ) {
            CouchLibraryRow(Icons.Rounded.GridView, "All games", counts.allGames)
            CouchLibraryRow(Icons.Rounded.FavoriteBorder, "Favourites", counts.favourites)
            CouchLibraryRow(Icons.Rounded.Schedule, "Recently played", counts.recentlyPlayed)
            CouchLibraryRow(Icons.Rounded.Download, "Installed", counts.installed)
            CouchLibraryRow(Icons.Rounded.FolderOpen, "Collections", counts.collections, last = true)
        }
    }
}

@Composable
private fun CouchLibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    last: Boolean = false,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIBRARY_ROW_HEIGHT.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline.copy(alpha = 0.12f)),
        )
    }
}

// ---- Games shelf ------------------------------------------------------------

@Composable
private fun CouchGamesShelf(
    rail: CouchRail?,
    focusedItem: Int,
    platforms: Map<String, Platform>,
    accent: Color,
    onEntryFocused: (Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(rail?.id, focusedItem) {
        if (rail != null && focusedItem in rail.entries.indices) {
            listState.animateScrollToItem(focusedItem.coerceAtLeast(0))
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouchSectionLabel(rail?.title ?: "Your games", modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VIEW ALL",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (rail == null || rail.entries.isEmpty()) {
            CouchEmptyPanel(
                message = "No games on this shelf yet.",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(rail.entries, key = { _, entry -> entry.id }) { index, entry ->
                CouchCard(
                    entry = entry,
                    platform = entry.platform(platforms),
                    size = CARD_SIZE,
                    focused = index == focusedItem,
                    onFocus = { onEntryFocused(index) },
                    onSelected = { onEntrySelected(entry) },
                    onLongPressed = { onEntryLongPressed(entry) },
                )
            }
        }
    }
}

// ---- Dashboard bar ----------------------------------------------------------

/**
 * The bottom strip: what you can do, what the system can do, and what is left.
 *
 * Only storage is live. The six actions beside it are the shape of the row
 * rather than the row itself — see the note on [CouchHome] for why they are
 * drawn rather than left out.
 */
@Composable
private fun CouchDashboardBar(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.45f))
            .border(1.dp, colors.outline.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
    ) {
        CouchDashboardGroup(title = "Quick access") {
            CouchPlaceholder(Icons.Rounded.Search, "Search")
            CouchPlaceholder(Icons.Rounded.FilterList, "Filters")
            CouchPlaceholder(Icons.Rounded.Shuffle, "Random game")
        }
        CouchDashboardGroup(title = "System") {
            CouchPlaceholder(Icons.Rounded.Gamepad, "Controllers")
            CouchPlaceholder(Icons.Rounded.Download, "Downloads")
            CouchPlaceholder(Icons.Rounded.PowerSettingsNew, "Power")
        }
        CouchStorageMeter(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CouchDashboardGroup(title: String, content: @Composable () -> Unit) {
    Column {
        CouchSectionLabel(title)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * Internal storage, read from the filesystem.
 *
 * The one genuinely live thing on this row. `StatFs` on the external storage
 * directory is what every file manager reports and needs no permission, since it
 * describes the volume rather than anything on it.
 */
@Composable
private fun CouchStorageMeter(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val storage = remember { readInternalStorage() }

    Column(modifier = modifier) {
        CouchSectionLabel("Storage")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Internal storage",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "${storage.usedGb} GB / ${storage.totalGb} GB",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.outline.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(storage.usedFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colors.cursor),
            )
        }
    }
}

internal data class CouchStorage(val usedGb: Long, val totalGb: Long, val usedFraction: Float)

private fun readInternalStorage(): CouchStorage = runCatching {
    val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    val total = stat.blockCountLong * stat.blockSizeLong
    val free = stat.availableBlocksLong * stat.blockSizeLong
    couchStorageOf(totalBytes = total, freeBytes = free)
}.getOrElse { CouchStorage(0, 0, 0f) }

/** Split out so the arithmetic can be tested without a filesystem. */
internal fun couchStorageOf(totalBytes: Long, freeBytes: Long): CouchStorage {
    if (totalBytes <= 0L) return CouchStorage(0, 0, 0f)
    val used = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
    return CouchStorage(
        usedGb = used / BYTES_PER_GB,
        totalGb = totalBytes / BYTES_PER_GB,
        usedFraction = used.toFloat() / totalBytes,
    )
}

private const val BYTES_PER_GB = 1_000_000_000L

// ---- Shared pieces ----------------------------------------------------------

@Composable
private fun CouchSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = ThorTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun CouchFactChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    placeholder: Boolean = false,
) {
    val colors = ThorTheme.colors
    val alpha = if (placeholder) PLACEHOLDER_ALPHA else 1f
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.7f * alpha))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}

@Composable
private fun CouchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) accent else colors.surfaceHighest.copy(alpha = 0.8f))
            .then(
                if (primary) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val content = if (primary) contrastingOn(accent) else colors.onSurface
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * A control drawn for the layout's sake, with nothing behind it yet.
 *
 * Dimmed and inert on purpose. The alternative — leaving the row out until its
 * features exist — makes the screen impossible to judge, and the alternative to
 * *that*, wiring it to something plausible, produces a button that responds and
 * lies.
 */
@Composable
private fun CouchPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.35f))
            .border(1.dp, colors.outline.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface.copy(alpha = PLACEHOLDER_ALPHA),
            maxLines = 1,
        )
    }
}

@Composable
private fun CouchEmptyPanel(message: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.35f))
            .border(1.dp, colors.outline.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/** Black or white, whichever stays legible on [background]. */
private fun contrastingOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

// ---- Measurements -----------------------------------------------------------

private const val CONTENT_INSET = 20
private const val SECTION_GAP = 20
private const val SIDE_RAIL_WIDTH = 60
private const val SIDE_RAIL_ITEM = 40
private const val SIDE_RAIL_GAP = 10
private const val LIBRARY_PANEL_WIDTH = 300
private const val LIBRARY_ROW_HEIGHT = 46
private const val HERO_PADDING = 16
private const val HERO_ART_ASPECT = 0.72f
private const val SHELF_BLOCK_HEIGHT = 200
private const val DASHBOARD_HEIGHT = 92
private val CARD_SIZE: Dp = 128.dp
private const val CARD_GAP = 12

/** How faint a control with nothing behind it is drawn. */
private const val PLACEHOLDER_ALPHA = 0.38f
