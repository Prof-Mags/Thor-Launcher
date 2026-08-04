package com.thor.feature.home.couch

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.thor.core.model.ShortcutAction
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.home.LauncherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Everything here does something. The counts and the shelf come from the
 * library, storage is read from the filesystem, and every control opens what it
 * names — search, the sort picker, a random game, the Bluetooth panel where
 * controllers are paired, the system's downloads list, and the power dialog
 * through the accessibility service, which is the only route an unprivileged app
 * has to it.
 *
 * The one exception is the trophy count, which is drawn dimmed and says `-- / --`
 * because no achievement client ships. It is left visible rather than removed so
 * the row keeps its shape, and a figure that cannot be had says so rather than
 * showing a zero that would read as "none earned".
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
    actions: CouchDashboardActions,
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

    Row(modifier = modifier.fillMaxSize()) {
        CouchSideRail(
            rails = rails,
            selectedRail = focus.rail,
            accent = accent,
            onRailSelected = onRailSelected,
            onOpenDownloads = actions.onOpenDownloads,
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
                CouchSpotlight(
                    entry = focusedEntry,
                    platform = focusedEntry?.platform(state.platformsById),
                    accent = accent,
                    focusedAction = focus.action.takeIf { focus.zone == CouchZone.SPOTLIGHT },
                    onPlay = { focusedEntry?.let(onEntrySelected) },
                    onMoreInfo = { focusedEntry?.let(onEntryLongPressed) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                CouchLibraryPanel(
                    counts = counts,
                    rails = rails,
                    accent = accent,
                    focusedRow = focus.action.takeIf { focus.zone == CouchZone.LIBRARY },
                    onRailSelected = onRailSelected,
                    onOpenInstalled = { actions.onShortcut(ShortcutAction.APPS) },
                    modifier = Modifier.width(LIBRARY_PANEL_WIDTH.dp).fillMaxHeight(),
                )
            }

            CouchGamesShelf(
                rail = shelfRail,
                focusedItem = focus.item,
                // Two rings lit at once is not a highlight, it is a question.
                shelfActive = focus.zone == CouchZone.SHELF,
                platforms = state.platformsById,
                accent = accent,
                onEntryFocused = { item -> onEntryFocused(focus.rail, item) },
                onEntrySelected = onEntrySelected,
                onEntryLongPressed = onEntryLongPressed,
                modifier = Modifier.fillMaxWidth().height(SHELF_BLOCK_HEIGHT.dp),
            )

            CouchDashboardBar(
                actions = actions,
                accent = accent,
                focusedAction = focus.action.takeIf { focus.zone == CouchZone.DASHBOARD },
                onRandomGame = {
                    // Any game, not any entry: "random game" landing on the
                    // calculator is a joke that stops being funny immediately.
                    entries.filterIsInstance<GameEntry>()
                        .randomOrNull()
                        ?.let(onEntrySelected)
                },
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
/**
 * What the dashboard can reach outside its own screen.
 *
 * A holder rather than four more parameters on a signature that already carries
 * a dozen, and `@Immutable` for the reason [ShellStatusActions] is: a bare bag
 * of lambdas is unstable, and would recompose the whole dashboard on every frame
 * the shelf moves.
 */
@androidx.compose.runtime.Immutable
class CouchDashboardActions(
    val onShortcut: (ShortcutAction) -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onOpenDownloads: () -> Unit = {},
    val onPowerMenu: () -> Unit = {},
)

@Composable
private fun CouchSideRail(
    rails: List<CouchRail>,
    selectedRail: Int,
    accent: Color,
    onRailSelected: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val colors = ThorTheme.colors
    val destinations = remember(rails) {
        listOf(
            // The top of the shelf, whatever is there. Home pointed at the
            // Continue rail, which is also what Recent points at — two icons,
            // one destination, and both lit at once whenever it was selected.
            CouchRailShortcut(Icons.Rounded.Home, "Home", if (rails.isEmpty()) -1 else 0),
            CouchRailShortcut(Icons.Rounded.SportsEsports, "Games", rails.indexOfFirst { it.id.startsWith("platform:") }),
            CouchRailShortcut(Icons.Rounded.FavoriteBorder, "Favourites", rails.indexOfFirst { it.id == "favourites" }),
            CouchRailShortcut(Icons.Rounded.History, "Recent", rails.indexOfFirst { it.id == "continue" }),
            // Not a rail: the system's own downloads list, which is what the
            // word means on an Android device. See SystemPanel.DOWNLOADS.
            CouchRailShortcut(Icons.Rounded.Download, "Downloads", DOWNLOADS_DESTINATION),
            CouchRailShortcut(Icons.Rounded.FolderOpen, "Collections", rails.indexOfFirst { it.id == "collections" }),
        )
    }
    /*
     * One icon lit, even when two of them lead to the same rail.
     *
     * Home is rail 0 and Recent is the Continue rail, and on a device that has
     * been played those are the same rail — as they are for Games and Home on
     * one that has not. Marking the first match rather than every match means
     * the rail list can keep offering the shortcuts that are useful without the
     * highlight becoming ambiguous whenever two of them coincide.
     */
    val litDestination = remember(destinations, selectedRail) {
        destinations.indexOfFirst { it.railIndex >= 0 && it.railIndex == selectedRail }
    }

    Column(
        modifier = Modifier
            .width(SIDE_RAIL_WIDTH.dp)
            .fillMaxHeight()
            .padding(vertical = CONTENT_INSET.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SIDE_RAIL_GAP.dp),
    ) {
        destinations.forEachIndexed { position, destination ->
            val isDownloads = destination.railIndex == DOWNLOADS_DESTINATION
            val reachable = isDownloads || destination.railIndex >= 0
            val selected = position == litDestination
            // The pointer lights an icon the same way the rail's own selection
            // does; see [CouchCard] for why hover reuses the existing language
            // rather than inventing a second one. Only where there is somewhere
            // to go — lighting a dead icon promises a jump that will not happen.
            val hover = rememberPointerHover()
            val lit = selected || (reachable && hover.isHovered)
            val shape = ThorTheme.shapes.small
            Box(
                modifier = Modifier
                    .size(SIDE_RAIL_ITEM.dp)
                    .clip(shape)
                    .background(
                        if (lit) accent.copy(alpha = 0.18f) else Color.Transparent,
                    )
                    .then(
                        if (lit) {
                            Modifier.border(1.dp, accent.copy(alpha = 0.7f), shape)
                        } else {
                            Modifier
                        },
                    )
                    .pointerHover(hover)
                    .clickable(enabled = reachable) {
                        if (isDownloads) {
                            onOpenDownloads()
                        } else {
                            onRailSelected(destination.railIndex)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.label,
                    tint = when {
                        lit -> accent
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

/**
 * The game under the cursor, in full.
 *
 * This followed the *last played* entry to begin with, on the reasoning that a
 * panel which moves while you browse is unsettling. That was wrong for this
 * screen: the shelf is the thing being driven, and a detail panel that describes
 * something else makes the largest region on a television answer a question
 * nobody asked. Every other surface in the launcher — the information panel, the
 * handheld's top screen — describes the selection, and this now matches them.
 *
 * Resume rather than Play when there is play time on the clock, because those
 * are different promises and the launcher knows which one it can make.
 */
@Composable
private fun CouchSpotlight(
    entry: GridEntry?,
    platform: Platform?,
    accent: Color,
    /** Which button the controller is on, or null when it is elsewhere. */
    focusedAction: Int?,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val played = (game?.stats?.totalPlayMillis ?: 0L) > 0L

    Column(modifier = modifier) {
        CouchSectionLabel(if (played) "Continue playing" else "Selected")
        Spacer(Modifier.height(10.dp))

        if (entry == null) {
            CouchEmptyPanel(
                message = "Nothing here yet. Add a system and scan for games to begin.",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }
        val panelShape = ThorTheme.shapes.panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(panelShape)
                .background(colors.surface.copy(alpha = 0.55f))
                .border(1.dp, colors.outline.copy(alpha = 0.18f), panelShape)
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
                    .clip(ThorTheme.shapes.small),
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
                Spacer(Modifier.height(PLATFORM_LABEL_GAP.dp))
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

                /*
                 * The description, which is the whole reason a panel this size
                 * is worth the room.
                 *
                 * Weighted rather than given a line count: what fits depends on
                 * the couch UI scale and on how long the title above it ran, and
                 * a fixed height either clips a paragraph on a large television
                 * or leaves a gap on a small one.
                 */
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val description = game?.metadata?.description?.takeIf { it.isNotBlank() }
                    Text(
                        text = description ?: "No description scraped for this one yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (description != null) {
                            colors.onSurfaceVariant
                        } else {
                            colors.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA)
                        },
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CouchActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        label = if (played) "Resume" else "Play",
                        primary = true,
                        accent = accent,
                        focused = focusedAction == 0,
                        onClick = onPlay,
                    )
                    CouchActionButton(
                        icon = Icons.Rounded.Info,
                        label = "More info",
                        primary = false,
                        accent = accent,
                        focused = focusedAction == 1,
                        onClick = onMoreInfo,
                    )
                }

                Spacer(Modifier.height(12.dp))
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
                .clip(ThorTheme.shapes.pill)
                .background(colors.outline.copy(alpha = 0.25f)),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(ThorTheme.shapes.pill)
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

/**
 * The counts, each one a way into the shelf that holds them.
 *
 * Every row but Installed is a rail the deck already builds, so choosing one
 * moves the cursor rather than opening a screen of its own - the shelf below is
 * already the list these rows are counting. Installed is the app drawer, which
 * is where apps live everywhere else in the launcher.
 */
@Composable
private fun CouchLibraryPanel(
    counts: CouchLibraryCounts,
    rails: List<CouchRail>,
    accent: Color,
    focusedRow: Int?,
    onRailSelected: (Int) -> Unit,
    onOpenInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    /*
     * A row is only a control when its shelf exists.
     *
     * Favourites and Collections are built on demand, so before anything is in
     * them these rows point at nothing. Sharing the lookup with the controller's
     * Confirm — see [couchLibraryRailIndex] — is what keeps a tap and a press
     * agreeing about which of them are live.
     */
    fun jumpTo(row: Int): (() -> Unit)? =
        couchLibraryRailIndex(rails, row)?.let { index -> { onRailSelected(index) } }

    Column(modifier = modifier) {
        CouchSectionLabel("Your library")
        Spacer(Modifier.height(10.dp))
        val panelShape = ThorTheme.shapes.panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(panelShape)
                .background(colors.surface.copy(alpha = 0.55f))
                .border(1.dp, colors.outline.copy(alpha = 0.18f), panelShape),
        ) {
            CouchLibraryRow(
                Icons.Rounded.GridView, "All games", counts.allGames,
                focused = focusedRow == CouchNavigation.LIBRARY_ROW_ALL_GAMES,
                onClick = jumpTo(CouchNavigation.LIBRARY_ROW_ALL_GAMES),
            )
            CouchLibraryRow(
                Icons.Rounded.FavoriteBorder, "Favourites", counts.favourites,
                focused = focusedRow == CouchNavigation.LIBRARY_ROW_FAVOURITES,
                onClick = jumpTo(CouchNavigation.LIBRARY_ROW_FAVOURITES),
            )
            CouchLibraryRow(
                Icons.Rounded.Schedule, "Recently played", counts.recentlyPlayed,
                focused = focusedRow == CouchNavigation.LIBRARY_ROW_RECENT,
                onClick = jumpTo(CouchNavigation.LIBRARY_ROW_RECENT),
            )
            CouchLibraryRow(
                Icons.Rounded.Download, "Installed", counts.installed,
                focused = focusedRow == CouchNavigation.LIBRARY_ROW_INSTALLED,
                onClick = onOpenInstalled,
            )
            CouchLibraryRow(
                Icons.Rounded.FolderOpen, "Collections", counts.collections,
                focused = focusedRow == CouchNavigation.LIBRARY_ROW_COLLECTIONS,
                onClick = jumpTo(CouchNavigation.LIBRARY_ROW_COLLECTIONS), last = true,
            )
        }
    }
}

@Composable
private fun CouchLibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    focused: Boolean,
    onClick: (() -> Unit)?,
    last: Boolean = false,
) {
    val colors = ThorTheme.colors
    // Dimmed when there is no shelf behind it, which is the same thing the
    // trophy chip and the unreachable rail icons do with the same alpha.
    val live = onClick != null
    val alpha = if (live) 1f else PLACEHOLDER_ALPHA
    // A dead row does not light under the pointer either, for the same reason it
    // is drawn faint: the highlight is a promise that a press does something.
    val hover = rememberPointerHover()
    val lit = focused || (live && hover.isHovered)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIBRARY_ROW_HEIGHT.dp)
            .background(if (lit) colors.cursor.copy(alpha = 0.16f) else Color.Transparent)
            .pointerHover(hover)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f * alpha),
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

/**
 * The rail of cards.
 *
 * [shelfActive] is whether the controller is actually on it. The cursor stays
 * put while the user is on a button — the spotlight is describing this card, so
 * losing it would be worse — but it is drawn at rest rather than lit, because
 * two full rings on a television is not a highlight, it is a question about
 * which one the next press hits.
 */
@Composable
private fun CouchGamesShelf(
    rail: CouchRail?,
    focusedItem: Int,
    shelfActive: Boolean,
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
        if (rail == null || focusedItem !in rail.entries.indices) return@LaunchedEffect
        /*
         * Scroll only at an edge, and only by the overhang.
         *
         * This animated to the focused item on every change, which on a stick
         * that repeats means re-laying out and re-animating the whole rail for
         * each press — including the presses where the card was already fully on
         * screen and nothing needed to move.
         *
         * The size of the move matters as much as whether there is one.
         * `animateScrollToItem` puts the card at the *start* of the row, so
         * stepping one card to the right threw the whole shelf sideways. Under a
         * pointer that is worse than untidy: the card jumps out from under the
         * cursor, whatever lands there is hovered next, and the shelf chases the
         * cursor across the screen. Nudging by exactly the part hanging off the
         * edge leaves the card where the eye — or the cursor — already is.
         */
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == focusedItem }
        if (visible == null) {
            // Nowhere near the viewport, so there is no overhang to measure.
            listState.animateScrollToItem(focusedItem)
            return@LaunchedEffect
        }
        val start = visible.offset
        val end = visible.offset + visible.size
        val overhang = when {
            start < layout.viewportStartOffset -> start - layout.viewportStartOffset
            end > layout.viewportEndOffset -> end - layout.viewportEndOffset
            else -> 0
        }
        if (overhang != 0) listState.animateScrollBy(overhang.toFloat())
    }

    Column(modifier = modifier.padding(top = SHELF_HEADER_GAP.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            // The title here is the platform's name, and it is allowed to run the
            // width of the shelf — without a gap a long one ellipsised straight
            // into VIEW ALL, which read as one long label rather than as two.
            horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
        ) {
            CouchSectionLabel(rail?.title ?: "Your games", modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
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
                    resting = !shelfActive,
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
private fun CouchDashboardBar(
    actions: CouchDashboardActions,
    accent: Color,
    focusedAction: Int?,
    onRandomGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val panelShape = ThorTheme.shapes.panel
    Row(
        modifier = modifier
            .clip(panelShape)
            .background(colors.surface.copy(alpha = 0.45f))
            .border(1.dp, colors.outline.copy(alpha = 0.14f), panelShape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
    ) {
        CouchDashboardGroup(title = "Quick access") {
            CouchDashboardButton(
                Icons.Rounded.Search, "Search", focusedAction == 0,
            ) { actions.onShortcut(ShortcutAction.SEARCH) }
            CouchDashboardButton(
                Icons.Rounded.FilterList, "Filters", focusedAction == 1, actions.onOpenFilters,
            )
            CouchDashboardButton(
                Icons.Rounded.Shuffle, "Random game", focusedAction == 2, onRandomGame,
            )
        }
        CouchDashboardGroup(title = "System") {
            // Controllers pair over Bluetooth, which is the panel that actually
            // manages them; there is no separate controller screen to open.
            CouchDashboardButton(
                Icons.Rounded.Gamepad, "Controllers", focusedAction == 3,
            ) { actions.onShortcut(ShortcutAction.BLUETOOTH) }
            CouchDashboardButton(
                Icons.Rounded.Download, "Downloads", focusedAction == 4, actions.onOpenDownloads,
            )
            CouchDashboardButton(
                Icons.Rounded.PowerSettingsNew, "Power", focusedAction == 5, actions.onPowerMenu,
            )
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
    /*
     * Read off the main thread.
     *
     * `StatFs` is a `statvfs` syscall, and it was being made inside composition
     * on the frame the dashboard first drew — on internal flash that returns
     * fast, but the first frame of a launcher is the one frame that cannot
     * afford to find out. Producing it instead lets the row lay out at zero and
     * fill in, which is a meter arriving a frame late rather than a screen
     * arriving late.
     */
    val storage by produceState(CouchStorage(0, 0, 0f)) {
        value = withContext(Dispatchers.IO) { readInternalStorage() }
    }

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
                .clip(ThorTheme.shapes.pill)
                .background(colors.outline.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(storage.usedFraction)
                    .fillMaxHeight()
                    .clip(ThorTheme.shapes.pill)
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
            .clip(ThorTheme.shapes.small)
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
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (primary) accent else colors.surfaceHighest.copy(alpha = 0.8f))
            .then(
                when {
                    // The cursor outranks the button's own outline: on a
                    // television the only question is which one a press hits.
                    lit -> Modifier.border(2.dp, colors.cursor, shape)
                    primary -> Modifier
                    else -> Modifier.border(
                        1.dp,
                        colors.outline.copy(alpha = 0.3f),
                        shape,
                    )
                },
            )
            .pointerHover(hover)
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

/** One tile on the bottom row. */
@Composable
private fun CouchDashboardButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (lit) {
                    colors.cursor.copy(alpha = 0.22f)
                } else {
                    colors.surfaceElevated.copy(alpha = 0.55f)
                },
            )
            .border(
                if (lit) 2.dp else 1.dp,
                if (lit) colors.cursor else colors.outline.copy(alpha = 0.18f),
                shape,
            )
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun CouchEmptyPanel(message: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val panelShape = ThorTheme.shapes.panel
    Box(
        modifier = modifier
            .clip(panelShape)
            .background(colors.surface.copy(alpha = 0.35f))
            .border(1.dp, colors.outline.copy(alpha = 0.12f), panelShape),
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

/**
 * Between a platform's name and the thing it is naming.
 *
 * The eyebrow over the spotlight's title had 4dp under it, which at this weight
 * put the system's name in among the headline's ascenders — the two read as one
 * block of text rather than as a label and its subject.
 */
private const val PLATFORM_LABEL_GAP = 8

/**
 * Clearance above the shelf's own title.
 *
 * That title is the platform's name, and the shelf is stacked directly under the
 * spotlight and library panels — so with nothing between them the name of the
 * system was printed hard against the bottom edge of the panel above it.
 *
 * Taken from inside [SHELF_BLOCK_HEIGHT] rather than added to it. The block
 * already carries slack around its cards, whereas the panels above it are
 * weighted: adding to the stack would spend the spotlight's height on a gap, and
 * the spotlight is the one region here that is not a fixed size.
 */
private const val SHELF_HEADER_GAP = 16

/** How faint a control with nothing behind it is drawn. */
private const val PLACEHOLDER_ALPHA = 0.38f

/** Not a rail index: the side rail's Downloads opens the system's list. */
private const val DOWNLOADS_DESTINATION = -2
