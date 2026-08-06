package com.thor.feature.home.couch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.AppEntry
import com.thor.core.model.ClockStyle
import com.thor.core.model.CouchWallpaperStyle
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.completionProgress
import com.thor.core.model.GridEntry
import com.thor.core.model.LauncherTab
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.LauncherStatusBar
import com.thor.core.ui.icon.PlatformIcons
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.core.ui.profile.NotificationShadePanel
import com.thor.core.ui.profile.ProfileClusterHeader
import com.thor.core.ui.profile.ShellStatus
import com.thor.core.ui.profile.ShellStatusActions
import com.thor.feature.home.grid.AppIcon
import com.thor.feature.home.LauncherUiState
import com.thor.feature.home.shell.icon
import kotlinx.coroutines.delay

/**
 * Whether Couch Mode draws the television dashboard.
 *
 * On: the hero, the library counts, one shelf and the system row, with a rail
 * down the side. Off: the earlier single-column shelf, which is kept whole
 * rather than deleted because the shape of this screen is not settled — the same
 * reasoning `LauncherFeatures` records for the dock.
 *
 * A constant rather than a setting for that same reason: an unsettled question
 * presented as a preference is one the user has to answer.
 */
internal const val COUCH_DASHBOARD_LAYOUT = true

/**
 * Controller position in Couch Mode.
 *
 * [rail] and [item] are the shelf cursor and are kept even while the controller
 * is elsewhere: the spotlight describes whatever the shelf is on, so leaving the
 * shelf to press a button must not lose the place it was pressed from.
 *
 * [action] is the position *within* [zone] — which of the two spotlight buttons,
 * which library row, which dashboard tile. It means nothing in
 * [CouchZone.SHELF], where the position is [item].
 */
data class CouchFocus(
    val rail: Int = 0,
    val item: Int = 0,
    val zone: CouchZone = CouchZone.SHELF,
    val action: Int = 0,
)

/** A deliberate TV shelf, independent of the handheld grid and its empty cells. */
data class CouchRail(
    val id: String,
    val title: String,
    val entries: List<GridEntry>,
)

/**
 * Builds the living-room library from actual content, not grid coordinates.
 * Duplicates across rails are intentional: Continue and Favourites are useful
 * shortcuts, while Games and Apps remain complete libraries.
 */
fun LauncherUiState.couchPlatforms(): List<Platform> {
    val platformIds = entriesById.values.asSequence()
        .filterIsInstance<GameEntry>()
        .filterNot(GridEntry::isHidden)
        .map(GameEntry::platformId)
        .toSet()
    return platformIds.mapNotNull(platformsById::get)
        .sortedWith(compareBy<Platform> { it.sortIndex }.thenBy(Platform::name))
}

/**
 * The shelf, one rail at a time.
 *
 * [selectedPlatformId] no longer decides *what* is built — every platform gets a
 * rail — but it is kept because callers use it to work out which rail to land
 * on, and removing it would push that lookup into each of them.
 */
@Suppress("UNUSED_PARAMETER")
fun buildCouchRails(
    state: LauncherUiState,
    selectedPlatformId: String? = state.couchPlatforms().firstOrNull()?.id,
): List<CouchRail> {
    if (state.isFolderOpen) {
        return listOf(
            CouchRail(
                id = "folder:${state.openFolderId}",
                title = state.openFolder?.title ?: "Collection",
                entries = state.openFolderContents.filterNot(GridEntry::isHidden),
            ),
        ).filter { it.entries.isNotEmpty() }
    }

    val entries = state.entriesById.values.filterNot(GridEntry::isHidden)
    val games = entries.filterIsInstance<GameEntry>()
    val apps = entries.filterIsInstance<AppEntry>()
    val folders = entries.filterIsInstance<FolderEntry>()
        .filter { PlatformFolders.platformIdOf(it.id) == null }
    val playable = buildList<GridEntry> {
        addAll(games)
        addAll(apps)
    }
    val recent = playable
        .filter { it.lastPlayedAt() != null }
        .sortedByDescending(GridEntry::lastPlayedAt)
        .take(16)

    return buildList {
        if (recent.isNotEmpty()) add(CouchRail(COUCH_RAIL_CONTINUE, "Continue playing", recent))
        playable.filter(GridEntry::isFavorite)
            .sortedBy(GridEntry::sortTitle)
            .takeIf(List<GridEntry>::isNotEmpty)
            ?.let { add(CouchRail(COUCH_RAIL_FAVOURITES, "Favourites", it)) }
        /*
         * A rail for every system, not just the chosen one.
         *
         * The shelf used to hold a single "Game library" rail whose contents the
         * platform menu swapped, which meant the only way to see what else was
         * installed was to open a menu and change a setting. Every platform is a
         * rail now, so up and down walk the whole library — Continue, Favourites,
         * then each system in turn — and the menu becomes a way of jumping
         * straight to one rather than the only way of reaching it.
         *
         * The deck draws one rail at a time regardless, so this costs a longer
         * list to walk and nothing on screen.
         */
        state.couchPlatforms().forEach { platform ->
            games.filter { it.platformId == platform.id }
                .sortedBy(GameEntry::sortTitle)
                .takeIf(List<GameEntry>::isNotEmpty)
                ?.let { add(CouchRail("$COUCH_RAIL_PLATFORM_PREFIX${platform.id}", platform.name, it)) }
        }
        apps.sortedBy(AppEntry::sortTitle)
            .takeIf(List<AppEntry>::isNotEmpty)
            ?.let { add(CouchRail(COUCH_RAIL_APPS, "Apps", it)) }
        folders.sortedBy(FolderEntry::sortTitle)
            .takeIf(List<FolderEntry>::isNotEmpty)
            ?.let { add(CouchRail(COUCH_RAIL_COLLECTIONS, "Collections", it)) }
    }
}

/**
 * Which rail holds a platform's games, or null if it has none on the shelf.
 *
 * The platform menu picks a system; with every system already on the shelf, what
 * that has to do is move the cursor to its rail rather than rebuild the list.
 */
fun couchRailIndexForPlatform(rails: List<CouchRail>, platformId: String?): Int? {
    if (platformId == null) return null
    return rails.indexOfFirst { it.id == "$COUCH_RAIL_PLATFORM_PREFIX$platformId" }
        .takeIf { it >= 0 }
}

fun LauncherUiState.couchEntry(focus: CouchFocus, selectedPlatformId: String? = null): GridEntry? {
    val rails = buildCouchRails(this, selectedPlatformId ?: couchPlatforms().firstOrNull()?.id)
    val rail = rails.getOrNull(focus.rail.coerceIn(0, (rails.size - 1).coerceAtLeast(0)))
    return rail?.entries?.getOrNull(
        focus.item.coerceIn(0, (rail.entries.size - 1).coerceAtLeast(0)),
    )
}

/** A genuine one-screen, controller-first living-room launcher. */
@Composable
fun CouchScreen(
    state: LauncherUiState,
    focus: CouchFocus,
    tabs: List<LauncherTab>,
    selectedTab: LauncherTab,
    navCursor: LauncherTab?,
    settingsFocused: Boolean,
    settingsSelected: Boolean,
    platformIndex: Int,
    clockStyle: ClockStyle,
    showStatusBar: Boolean,
    uiScale: Float,
    /** What to draw behind the dashboard. */
    wallpaper: CouchWallpaperStyle = CouchWallpaperStyle.RIDGES,
    /** Deferred to by [CouchWallpaperStyle.THEME], and ignored otherwise. */
    themeWallpaper: AnimatedWallpaper = AnimatedWallpaper.NONE,
    wallpaperImageUri: String? = null,
    onTabSelected: (LauncherTab) -> Unit,
    onSettingsSelected: () -> Unit,
    onPlatformSelected: (Int) -> Unit,
    onEntryFocused: (rail: Int, item: Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    onEntryFavorite: (GridEntry) -> Unit,
    fullscreenSection: Boolean = false,
    sectionContent: (@Composable (LauncherTab) -> Unit)? = null,
    settingsContent: (@Composable () -> Unit)? = null,
    /** Profile and notifications for the corner; null keeps the plain label. */
    status: ShellStatus? = null,
    statusActions: ShellStatusActions = ShellStatusActions(),
    /** What the dashboard's own controls reach outside this screen. */
    dashboardActions: CouchDashboardActions = CouchDashboardActions(),
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    /*
     * No scaling here any more.
     *
     * This used to multiply the panel's density by a constant three quarters,
     * which is a correction to one screen rather than a design size: on a screen
     * worth more dp the whole interface became a smaller share of it while
     * everything laid out as a fraction kept its own, which is what pulled the
     * shelves and the panels apart. The canvas is set once for the surface now —
     * see `BottomScreen`, which wraps this and its overlays in one [DesignScale]
     * — and [uiScale] is applied there as the user's preference on top.
     */
    val platforms = remember(state.entriesById, state.platformsById) {
        state.couchPlatforms()
    }
    val safePlatformIndex = platformIndex.coerceIn(
        0,
        (platforms.size - 1).coerceAtLeast(0),
    )
    val selectedPlatform = platforms.getOrNull(safePlatformIndex)
    val rails = remember(
        state.entriesById,
        state.openFolderId,
        state.openFolderContents,
        selectedPlatform?.id,
    ) { buildCouchRails(state, selectedPlatform?.id) }
    val safeRail = focus.rail.coerceIn(0, (rails.size - 1).coerceAtLeast(0))
    val safeItem = focus.item.coerceIn(
        0,
        ((rails.getOrNull(safeRail)?.entries?.size ?: 0) - 1).coerceAtLeast(0),
    )
    val focusedEntry = rails.getOrNull(safeRail)?.entries?.getOrNull(safeItem)
    val visibleEntries = remember(state.entriesById) {
        state.entriesById.values.filterNot(GridEntry::isHidden)
    }
    val focusedPlatform = focusedEntry?.platform(state.platformsById)
    val libraryStats = remember(visibleEntries, focusedPlatform) {
        couchLibraryStats(visibleEntries, focusedPlatform)
    }

    LaunchedEffect(safeRail, safeItem, focus, rails.size) {
        if (focus.rail != safeRail || focus.item != safeItem) {
            onEntryFocused(safeRail, safeItem)
        }
    }

    LaunchedEffect(platformIndex, safePlatformIndex, platforms.size) {
        if (platforms.isNotEmpty() && platformIndex != safePlatformIndex) {
            onPlatformSelected(safePlatformIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (selectedTab.isHome && !settingsSelected) {
            /*
             * Drawn, not loaded. The selected game's artwork used to fill this
             * and changed every time the cursor moved; see [CouchWallpaper] for
             * why a television wants a fixed field behind its panels.
             */
            CouchWallpaper(
                style = wallpaper,
                accent = focusedEntry?.platform(state.platformsById)
                    ?.let { Color(it.accentArgb) }
                    ?: colors.cursor,
                themeWallpaper = themeWallpaper,
                wallpaperImageUri = wallpaperImageUri,
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (!fullscreenSection) {
                CouchNavigationBar(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    focusedTab = navCursor.takeUnless { settingsSelected },
                    settingsFocused = settingsFocused && !settingsSelected,
                    settingsSelected = settingsSelected,
                    clockStyle = clockStyle,
                    showStatusBar = showStatusBar,
                    onTabSelected = onTabSelected,
                    onSettingsSelected = onSettingsSelected,
                    status = status,
                    statusActions = statusActions,
                )
            }

            if (settingsSelected) {
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    settingsContent?.invoke()
                }
            } else if (selectedTab.isHome) {
                if (focusedEntry == null) {
                    EmptyCouchLibrary(modifier = Modifier.weight(1f))
                } else if (COUCH_DASHBOARD_LAYOUT) {
                    /*
                     * The television dashboard: hero, library counts, one
                     * shelf and a system row, with a rail down the side.
                     *
                     * The rail deck below is kept whole behind the flag
                     * rather than deleted — see [LauncherFeatures] for why
                     * an unsettled shape is decided in code. Everything the
                     * controller does is unchanged: this draws the same
                     * rails from the same focus, so up and down still walk
                     * the library and the shelf still moves with it.
                     */
                    CouchHome(
                        state = state,
                        rails = rails,
                        /*
                         * The whole cursor, not just its shelf half.
                         *
                         * This was `CouchFocus(safeRail, safeItem)`, which
                         * rebuilt the focus from two fields and so handed the
                         * dashboard the defaults for the other two — zone
                         * SHELF, action 0 — no matter where the controller
                         * actually was. Everything still *moved*: the view
                         * model tracked the zone and Confirm did the right
                         * thing. None of it was drawn. Walking down to the
                         * system row and pressing A opened the power dialog
                         * from a screen that showed the cursor sitting on a
                         * game, which is the worst version of this bug —
                         * silent, and only reproducible with a controller.
                         */
                        focus = focus.copy(rail = safeRail, item = safeItem),
                        onEntryFocused = onEntryFocused,
                        onEntrySelected = onEntrySelected,
                        onEntryLongPressed = onEntryLongPressed,
                        onRailSelected = { rail -> onEntryFocused(rail, 0) },
                        actions = dashboardActions,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        /*
                         * The shelf takes what it needs; the panel centres in
                         * everything left over.
                         *
                         * It used to be the other way round — the panel had a
                         * weighted slot at the top and the shelf had the rest
                         * — and the shelf draws its rail hard against its own
                         * bottom edge, so all of that leftover height ended up
                         * *below* the panel. Centring the card inside its own
                         * slot could not help: the slot itself was at the top
                         * of the screen. Giving the shelf a height and the
                         * panel the remainder puts the empty space where it
                         * belongs, around the panel rather than under it.
                         */
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            /*
                             * Which shelf you are on, above the card.
                             *
                             * The rail's own title used to be the only place
                             * this was said, down at the shelf — so on a
                             * screen showing one rail at a time, the answer
                             * to "where am I in the library" was at the far
                             * edge of it. Above the card is where the eye
                             * already is.
                             */
                            rails.getOrNull(safeRail)?.title?.let { railTitle ->
                                Text(
                                    text = railTitle.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = focusedPlatform
                                        ?.let { Color(it.accentArgb) }
                                        ?: colors.cursor,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        start = SCREEN_INSET.dp,
                                        end = SCREEN_INSET.dp,
                                        bottom = 6.dp,
                                    ),
                                )
                            }

                            CouchHero(
                                entry = focusedEntry,
                                platform = focusedEntry.platform(state.platformsById),
                                onPlay = { onEntrySelected(focusedEntry) },
                                onFavorite = { onEntryFavorite(focusedEntry) },
                                onMore = { onEntryLongPressed(focusedEntry) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(HERO_HEIGHT.dp),
                            )
                            CouchLibrarySummary(
                                stats = libraryStats,
                                accent = focusedPlatform
                                    ?.let { Color(it.accentArgb) }
                                    ?: colors.cursor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(LIBRARY_SUMMARY_HEIGHT.dp),
                            )
                        }
                        CouchRailDeck(
                            rails = rails,
                            focus = CouchFocus(safeRail, safeItem),
                            platforms = state.platformsById,
                            onEntryFocused = onEntryFocused,
                            onEntrySelected = onEntrySelected,
                            onEntryLongPressed = onEntryLongPressed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SHELF_HEIGHT.dp),
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    sectionContent?.invoke(selectedTab)
                }
            }
        }

        /*
         * The notification shade, over the screen rather than inside the bar.
         *
         * It used to be stacked under its own header, which works on the
         * information panel because that header floats over a panel with room
         * beneath it. In couch mode the header lives in a navigation bar with a
         * fixed height, so the shade expanded into a container that had no space
         * to give it: it was clipped to the bar, and from the sofa the bell did
         * nothing at all.
         *
         * Drawn here instead, last in the root box so it lies over everything,
         * hung directly under the bar and aligned to the same corner its header
         * is in — which is what makes it read as having come from the bell
         * rather than as a panel that appeared. The scrim behind it closes it,
         * because a shade with no way out but the same small target that opened
         * it is a trap on a television.
         */
        if (status != null) AnimatedVisibility(
            // Kept outside the condition rather than inside it: `status` stays
            // non-null while the shade fades out, so the panel animates away
            // with its contents instead of emptying first and then fading.
            visible = status.shadeOpen && !fullscreenSection,
            enter = fadeIn(tween(SHADE_MS)),
            exit = fadeOut(tween(SHADE_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = SHADE_SCRIM_ALPHA))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = statusActions.onToggleShade,
                        ),
                )
                NotificationShadePanel(
                    profile = status.profile,
                    access = status.notifications,
                    onGrantAccess = statusActions.onGrantAccess,
                    onOpenAppInfo = statusActions.onOpenAppInfo,
                    onNotificationOpened = statusActions.onNotificationOpened,
                    onNotificationDismissed = statusActions.onNotificationDismissed,
                    onDismissAll = statusActions.onDismissAll,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = TOP_BAR_HEIGHT.dp + SHADE_GAP.dp, end = SCREEN_INSET.dp)
                        .width(COUCH_SHADE_WIDTH.dp),
                )
            }
        }
    }
}

@Composable
fun CouchNavigationBar(
    tabs: List<LauncherTab>,
    selectedTab: LauncherTab,
    focusedTab: LauncherTab?,
    settingsFocused: Boolean,
    settingsSelected: Boolean,
    clockStyle: ClockStyle,
    showStatusBar: Boolean,
    onTabSelected: (LauncherTab) -> Unit,
    onSettingsSelected: () -> Unit,
    /** Who is signed in, drawn in the corner. Null draws the plain mode label. */
    status: ShellStatus? = null,
    statusActions: ShellStatusActions = ShellStatusActions(),
) {
    val colors = ThorTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT.dp)
            .background(colors.background.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = SCREEN_INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            /*
             * The destinations take what is left, not what they want.
             *
             * They used to be laid out first at their natural width with a
             * weighted spacer after them, which meant a long tab list pushed the
             * corner off the end of the bar — and the corner is where the clock
             * and the profile are. Weighting this group instead makes the
             * trailing group the fixed part: the time is always drawn, and it is
             * a destination label that ellipsises when the bar runs out of room.
             */
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "LOKI",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 14.dp),
                )
                tabs.forEach { tab ->
                    CouchNavItem(
                        label = tab.label,
                        icon = tab.couchIcon(),
                        selected = !settingsSelected && tab == selectedTab,
                        focused = !settingsFocused && tab == focusedTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
                CouchNavItem(
                    label = "Settings",
                    icon = Icons.Rounded.Settings,
                    selected = settingsSelected,
                    focused = settingsFocused,
                    onClick = onSettingsSelected,
                )
            }
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                /*
                 * Who is signed in, where the mode label used to be.
                 *
                 * "COUCH" told the user something they could see for themselves —
                 * the whole interface had changed shape — and took the one piece
                 * of corner a television interface has for the things a
                 * television interface actually needs: whose profile this is, and
                 * whether anything is waiting.
                 *
                 * The header only. The shade it opens is drawn by [CouchScreen]
                 * over the screen below this bar — see [ProfileClusterHeader].
                 */
                if (status != null) {
                    ProfileClusterHeader(
                        profile = status.profile,
                        avatarPath = status.avatarPath,
                        access = status.notifications,
                        expanded = status.shadeOpen,
                        onToggleExpanded = statusActions.onToggleShade,
                        // The bar already has a background; a pill on top of it
                        // would box something that is already in a box.
                        surfaced = false,
                        modifier = Modifier.width(COUCH_PROFILE_WIDTH.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(ThorTheme.shapes.pill)
                            .background(colors.cursor),
                    )
                    Text(
                        text = "COUCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (showStatusBar) {
                    LauncherStatusBar(
                        clockStyle = clockStyle,
                        visible = true,
                        // Packed against the right edge rather than centred in a
                        // fixed 126dp box, which on a bar this wide left the time
                        // floating in the middle of its own empty column.
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.width(COUCH_STATUS_WIDTH.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun CouchNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    // Lit by the pointer exactly as by the controller cursor, but without moving
    // it: the nav cursor also decides what the *controller* does next — up from
    // the dashboard lands on it — and having the pointer drag that around while
    // crossing the bar on the way to something else would leave the stick
    // starting somewhere the user never chose.
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                if (lit) colors.surfaceHighest.copy(alpha = 0.74f)
                else Color.Transparent,
            )
            .then(
                if (lit) Modifier.border(
                    1.dp,
                    colors.cursor.copy(alpha = 0.56f),
                    shape,
                ) else Modifier,
            )
            .pointerHover(hover)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected || lit) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected || lit) colors.onSurface else colors.onSurfaceVariant,
                fontWeight = if (selected || lit) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(if (selected) 46.dp else 0.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor),
        )
    }
}

private fun LauncherTab.couchIcon(): ImageVector = when (this) {
    LauncherTab.STREAM -> Icons.Rounded.Cast
    LauncherTab.HOME -> Icons.Rounded.Home
    LauncherTab.MOVIES -> Icons.Rounded.Movie
    LauncherTab.SHOWS -> Icons.Rounded.Tv
}

@Composable
private fun CouchHero(
    entry: GridEntry,
    platform: Platform?,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val game = entry as? GameEntry
    val metadata = game?.metadata
    val description = when (entry) {
        is GameEntry -> entry.metadata.description
        is FolderEntry -> entry.description
        is AppEntry -> entry.packageName
        else -> null
    }
    val facts = buildList {
        platform?.shortName?.ifBlank { platform.name }?.let(::add)
        metadata?.releaseYear?.toString()?.let(::add)
        metadata?.rating?.let { add("$it / 100") }
    }

    Box(
        /*
         * Centred in the space it is given, rather than filling it.
         *
         * The card used to stretch from the tab bar down to the shelf, which
         * left it hard against both and made the panel read as a wall between
         * them rather than as a card sitting in the gap. Holding it short of
         * full height is what allows the alignment to do anything at all —
         * something told to fill its parent has nowhere to be centred.
         */
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(HERO_CARD_WIDTH)
                .fillMaxHeight()
                .clip(ThorTheme.shapes.panel)
                .background(colors.surface.copy(alpha = 0.48f))
                .border(1.dp, colors.outline.copy(alpha = 0.16f), ThorTheme.shapes.panel)
                .padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CouchHeroArtwork(
                entry = entry,
                platform = platform,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when (entry) {
                        is GameEntry -> "SELECTED GAME"
                        is AppEntry -> "SELECTED APP"
                        is FolderEntry -> "SELECTED COLLECTION"
                        else -> "SELECTED ITEM"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString("  /  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    CouchHeroAction(
                        label = if (entry is FolderEntry) "Open" else "Play",
                        icon = if (entry is FolderEntry) Icons.Rounded.FolderOpen
                        else Icons.Rounded.PlayArrow,
                        primary = true,
                        onClick = onPlay,
                        modifier = Modifier.weight(1f),
                    )
                    CouchHeroAction(
                        label = if (entry.isFavorite) "Favorited" else "Favorite",
                        icon = if (entry.isFavorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        onClick = onFavorite,
                        modifier = Modifier.weight(1f),
                    )
                    CouchHeroAction(
                        label = "More",
                        icon = Icons.Rounded.MoreHoriz,
                        onClick = onMore,
                        modifier = Modifier.weight(0.78f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CouchHeroArtwork(
    entry: GridEntry,
    platform: Platform?,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceHighest)
            .border(1.dp, colors.outline.copy(alpha = 0.2f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            is GameEntry -> ArtworkImage(
                model = entry.metadata.artwork.cellImage,
                contentDescription = entry.title,
                fallbackText = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            is AppEntry -> if (entry.customIconUri != null) {
                ArtworkImage(
                    model = entry.customIconUri,
                    contentDescription = entry.title,
                    fallbackText = entry.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(0.64f),
                )
            } else {
                AppIcon(
                    packageName = entry.packageName,
                    title = entry.title,
                    shape = shape,
                    modifier = Modifier.fillMaxSize(0.64f),
                )
            }
            is FolderEntry -> FolderCard(entry, platform)
            else -> Unit
        }
    }
}

@Composable
private fun CouchHeroAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val colors = ThorTheme.colors
    val background = if (primary) colors.cursor else colors.surfaceHighest.copy(alpha = 0.72f)
    val foreground = if (primary) contrastingContentColor(colors.cursor) else colors.onSurface
    Row(
        modifier = modifier
            .height(HERO_ACTION_HEIGHT.dp)
            .clip(ThorTheme.shapes.small)
            .background(background)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, colors.outline.copy(alpha = 0.18f), ThorTheme.shapes.small),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CouchLibrarySummary(
    stats: CouchLibraryStats,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Box(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(HERO_CARD_WIDTH)
                .fillMaxHeight()
                .clip(ThorTheme.shapes.panel)
                .background(colors.surface.copy(alpha = 0.44f))
                .border(1.dp, colors.outline.copy(alpha = 0.14f), ThorTheme.shapes.panel)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stats.title,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CouchLibraryStat(stats.games, "Games", Modifier.weight(1f))
                CouchLibraryDivider()
                CouchLibraryStat(stats.favorites, "Favorites", Modifier.weight(1f))
                CouchLibraryDivider()
                CouchLibraryStat(stats.played, "Played", Modifier.weight(1f))
                CouchLibraryDivider()
                CouchLibraryStat(stats.trailing, stats.trailingLabel, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The four figures under the hero, and what they are counting.
 *
 * [trailing] is the slot that changes meaning: scoped to a platform it is time
 * on that platform, because a platform count of one tells you nothing.
 */
internal data class CouchLibraryStats(
    val title: String,
    val games: Int,
    val favorites: Int,
    val played: Int,
    val trailing: Int,
    val trailingLabel: String,
)

/**
 * Narrows the library figures to the platform under the cursor.
 *
 * Whole-library totals sit still while you move around, which makes them
 * wallpaper; the same four numbers scoped to whatever you are looking at
 * actually answer something — how far into this platform you are. With nothing
 * under the cursor that belongs to a platform (an app, a folder), it falls back
 * to the library totals rather than showing zeroes.
 */
internal fun couchLibraryStats(
    entries: List<GridEntry>,
    platform: Platform?,
): CouchLibraryStats {
    val games = entries.filterIsInstance<GameEntry>()
    if (platform == null) {
        return CouchLibraryStats(
            title = "YOUR LIBRARY",
            games = games.size,
            favorites = entries.count(GridEntry::isFavorite),
            played = games.count { it.stats.hasBeenPlayed },
            trailing = games.map(GameEntry::platformId).distinct().size,
            trailingLabel = "Platforms",
        )
    }
    val owned = games.filter { it.platformId == platform.id }
    val playMillis = owned.sumOf { it.stats.totalPlayMillis }
    return CouchLibraryStats(
        title = platform.shortName.ifBlank { platform.name }.uppercase(),
        games = owned.size,
        favorites = owned.count(GridEntry::isFavorite),
        played = owned.count { it.stats.hasBeenPlayed },
        // Rounded down: an hour you have not finished is not one you have put in.
        trailing = (playMillis / MILLIS_PER_HOUR).toInt(),
        trailingLabel = "Hours",
    )
}

private const val MILLIS_PER_HOUR = 3_600_000L

@Composable
private fun CouchLibraryStat(value: Int, label: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CouchLibraryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(ThorTheme.colors.outline.copy(alpha = 0.2f)),
    )
}

@Composable
private fun CouchGameInfoPanel(
    entry: GridEntry,
    platform: Platform?,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val scroll = rememberScrollState()
    val game = entry as? GameEntry

    LaunchedEffect(entry.id) { scroll.scrollTo(0) }

    GlassSurface(
        modifier = modifier,
        shape = ThorTheme.shapes.panel,
        alphaOverride = INFO_PANEL_ALPHA,
    ) {
        if (game == null) {
            CouchGenericInfo(
                entry = entry,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(INFO_PANEL_PADDING.dp),
            )
        } else {
            val metadata = game.metadata
            val artwork = metadata.artwork
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(INFO_PANEL_PADDING.dp),
                horizontalArrangement = Arrangement.spacedBy(INFO_COLUMN_GAP.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(INFO_COVER_WIDTH.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val cover = artwork.boxArt ?: artwork.backgroundImage
                    if (cover != null) {
                        ArtworkImage(
                            model = cover,
                            contentDescription = game.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(ThorTheme.shapes.small),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(ThorTheme.shapes.small)
                                .background(colors.surfaceHighest.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = game.title.take(1).uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

        Text(
                        text = platform?.name ?: game.platformId.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = platform?.let { Color(it.accentArgb) } ?: colors.cursor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (game.stats.totalPlayMillis > 0L) {
                        CouchInfoFact(
                            label = "Play time",
                            value = game.stats.totalPlayMillis.asCouchPlaytime(),
                        )
                    }
                    if (game.isFavorite) {
                        Text(
                            text = "FAVOURITE",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cursor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    if (artwork.logo != null) {
                        ArtworkImage(
                            model = artwork.logo,
                            contentDescription = game.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp, max = 76.dp),
                        )
                    } else {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        platform?.let {
                            CouchInfoBadge(it.shortName, Color(it.accentArgb))
                        }
                        metadata.releaseYear?.let {
                            CouchInfoBadge(it.toString(), colors.secondary)
                        }
                        metadata.genres.firstOrNull()?.let {
                            CouchInfoBadge(it, colors.primary)
                        }
                        metadata.rating?.let { rating ->
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                tint = colors.cursor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = rating.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface,
                            )
                        }
                    }

                    metadata.description?.takeIf(String::isNotBlank)?.let { description ->
                        CouchInfoSectionTitle("ABOUT")
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = INFO_DESCRIPTION_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    CouchInfoSectionTitle("DETAILS")
                    val facts = listOf(
                        "Developer" to metadata.developer,
                        "Publisher" to metadata.publisher,
                        "Released" to (metadata.releaseDate ?: metadata.releaseYear?.toString()),
                        "Genre" to metadata.genres.firstOrNull(),
                        "Players" to metadata.players,
                        "Times played" to game.stats.launchCount.toString(),
                        "Last played" to game.stats.lastPlayedEpochMs?.asCouchRelativeTime(),
                        "Platform" to (platform?.name ?: game.platformId.uppercase()),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        facts.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                pair.forEach { (label, value) ->
                                    CouchInfoFact(
                                        label = label,
                                        value = value,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    artwork.cappedScreenshots.takeIf(List<String>::isNotEmpty)?.let { screenshots ->
                        CouchInfoSectionTitle("SCREENSHOTS")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            screenshots.forEach { screenshot ->
                                ArtworkImage(
                                    model = screenshot,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(16f / 9f)
                                        .clip(ThorTheme.shapes.small),
                                )
                            }
                            repeat(3 - screenshots.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouchGenericInfo(entry: GridEntry, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "ITEM INFO",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        when (entry) {
            is AppEntry -> {
                CouchInfoFact("Type", "Android app")
                entry.versionName?.takeIf(String::isNotBlank)
                    ?.let { CouchInfoFact("Version", it) }
                entry.launchCount.takeIf { it > 0 }
                    ?.let { CouchInfoFact("Times played", it.toString()) }
            }

            is FolderEntry -> {
                CouchInfoFact("Type", "Collection")
                CouchInfoFact("Items", entry.childIds.size.toString())
            }

            else -> Unit
        }
    }
}

@Composable
private fun CouchInfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant.copy(alpha = 0.72f),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CouchInfoBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun CouchInfoFact(label: String, value: String?, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val known = !value.isNullOrBlank()
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
        Text(
            text = if (known) value.orEmpty() else "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (known) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CouchRailDeck(
    rails: List<CouchRail>,
    focus: CouchFocus,
    platforms: Map<String, Platform>,
    onEntryFocused: (Int, Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rail = rails.getOrNull(focus.rail) ?: return
    val animationsEnabled = ThorTheme.materials.animationsEnabled
    val target = remember(focus.rail, rail) { CouchRailTarget(focus.rail, rail) }
    val duration = if (animationsEnabled) RAIL_TRANSITION_MS else 0

    // The deck is the only thing here that knows how tall the shelf slot really
    // is, so the card size is decided once at this level and passed down.
    BoxWithConstraints(modifier = modifier.padding(top = 2.dp)) {
        val cardSize = couchCardSize(maxHeight)

        AnimatedContent(
            targetState = target,
            transitionSpec = {
                val direction = if (targetState.index >= initialState.index) 1 else -1
                (slideInVertically(tween(duration)) { height -> direction * (height / 5) } +
                    fadeIn(tween(duration))) togetherWith
                    (slideOutVertically(tween(duration)) { height -> -direction * (height / 5) } +
                        fadeOut(tween(duration)))
            },
            contentKey = { it.rail.id },
            label = "couch-rail-change",
        ) { displayed ->
            CouchRailContent(
                rails = rails,
                railIndex = displayed.index,
                rail = displayed.rail,
                focusedItem = focus.item,
                platforms = platforms,
                cardSize = cardSize,
                onEntryFocused = onEntryFocused,
                onEntrySelected = onEntrySelected,
                onEntryLongPressed = onEntryLongPressed,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Largest square card the shelf slot can actually hold.
 *
 * The rail is laid out at a fixed height, so a constant card size overflows the
 * top of the deck as soon as the slot is short — and the slot is not fixed: it
 * is a fraction of the screen, measured through a density the user can scale.
 * Capping against the space on offer lets the preferred size be generous
 * without the shelf running off the top of a display that reports a high
 * density or a scale turned up.
 */
internal fun couchCardSize(slotHeight: Dp): Dp {
    val available = slotHeight - RAIL_HEADER_HEIGHT.dp - CARD_RAIL_EXTRA_HEIGHT.dp
    return available.coerceIn(MIN_CARD_SIZE.dp, SQUARE_CARD_SIZE.dp)
}

private data class CouchRailTarget(
    val index: Int,
    val rail: CouchRail,
)

@Composable
private fun CouchRailContent(
    rails: List<CouchRail>,
    railIndex: Int,
    rail: CouchRail,
    focusedItem: Int,
    platforms: Map<String, Platform>,
    cardSize: Dp,
    onEntryFocused: (Int, Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val listState = rememberLazyListState()
    val safeItem = focusedItem.coerceIn(0, (rail.entries.size - 1).coerceAtLeast(0))

    LaunchedEffect(rail.id, safeItem) {
        if (rail.entries.isNotEmpty()) {
            val layout = listState.layoutInfo
            val visibleItem = layout.visibleItemsInfo.firstOrNull { it.index == safeItem }
            val fullyVisible = visibleItem != null &&
                visibleItem.offset >= layout.viewportStartOffset &&
                visibleItem.offset + visibleItem.size <= layout.viewportEndOffset
            // Keeping a visible card in place avoids re-laying out and animating
            // the whole shelf on every D-pad repeat. Scroll only at an edge.
            if (!fullyVisible) listState.animateScrollToItem(safeItem)
        }
    }

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SCREEN_INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rail.title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.cursor,
                fontWeight = FontWeight.Black,
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${rail.entries.size} ITEMS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            rails.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .height(3.dp)
                        .width(if (index == railIndex) 19.dp else 7.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(
                            if (index == railIndex) colors.cursor
                            else colors.onSurfaceVariant.copy(alpha = 0.36f),
                        ),
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardSize + CARD_RAIL_EXTRA_HEIGHT.dp)
                .padding(top = 3.dp),
            contentPadding = PaddingValues(horizontal = SCREEN_INSET.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            itemsIndexed(rail.entries, key = { _, entry -> entry.id }) { index, entry ->
                CouchCard(
                    entry = entry,
                    platform = entry.platform(platforms),
                    size = cardSize,
                    focused = index == safeItem,
                    onFocus = { onEntryFocused(railIndex, index) },
                    onSelected = { onEntrySelected(entry) },
                    onLongPressed = { onEntryLongPressed(entry) },
                )
            }
        }
    }
}

/**
 * One card on a shelf.
 *
 * [resting] marks a cursor that is still on this card but not being driven —
 * the dashboard keeps the shelf position while the controller is on a button, so
 * the card has to say "this is where you were" without competing with the ring
 * that says "this is what a press hits". It keeps the outline and gives up the
 * lift, which is the difference between the two at ten feet.
 */
@Composable
internal fun CouchCard(
    entry: GridEntry,
    platform: Platform?,
    size: Dp,
    focused: Boolean,
    onFocus: () -> Unit,
    onSelected: () -> Unit,
    onLongPressed: () -> Unit,
    resting: Boolean = false,
) {
    val colors = ThorTheme.colors
    /*
     * The pointer moves the shelf cursor rather than merely lighting a card.
     *
     * The spotlight above describes whatever the shelf is on, so a pointer that
     * only highlighted would leave the largest panel on the television describing
     * a different game from the one being pointed at. Reporting the hover as a
     * focus keeps one answer to "what is selected" whichever input is driving.
     *
     * It is also how a pointer browses at all. Clicking a card launches it, so
     * without this the only reachable games were the ones already on screen —
     * moving onto a card at the edge now focuses it, and the shelf's own
     * scroll-into-view brings the next ones along.
     */
    val hover = rememberPointerHover()
    val hovered = hover.isHovered
    LaunchedEffect(hovered, focused) {
        if (hovered && !focused) onFocus()
    }
    val lit = (focused && !resting) || hovered
    val focusColor = platform?.let { Color(it.accentArgb) } ?: colors.cursor
    val scale by animateFloatAsState(
        targetValue = if (lit) 1.035f else 1f,
        animationSpec = tween(160),
        label = "couch-card-focus",
    )
    val imageAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.88f,
        animationSpec = tween(160),
        label = "couch-card-depth",
    )
    val elevation by animateDpAsState(
        targetValue = if (lit) 10.dp else 0.dp,
        animationSpec = tween(160),
        label = "couch-card-elevation",
    )
    val shape = ThorTheme.shapes.small
    val game = entry as? GameEntry
    // One definition, shared with the information panel; see completionProgress.
    val progress = game?.completionProgress()

    Box(
        modifier = Modifier
            .width(size)
            .aspectRatio(1f)
            .zIndex(if (focused) 1f else 0f)
            // Before the scale, so the hover target is the card's resting box.
            // Measured inside it, the box grows when the highlight appears and
            // the element's own state becomes an input to the test that made it.
            .pointerHover(hover)
            .scale(scale)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(colors.surfaceHighest)
            .then(
                when {
                    lit -> Modifier.border(2.dp, focusColor, shape)
                    // Held, not driven: the same ring at half strength.
                    focused -> Modifier.border(2.dp, focusColor.copy(alpha = 0.45f), shape)
                    else -> Modifier.border(1.dp, colors.outline.copy(alpha = 0.16f), shape)
                },
            )
            .pointerInput(entry.id) {
                detectTapGestures(
                    onPress = { onFocus() },
                    onTap = { onSelected() },
                    onLongPress = { onLongPressed() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            is GameEntry -> ArtworkImage(
                    model = entry.metadata.artwork.cellImage,
                    contentDescription = entry.title,
                    fallbackText = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(imageAlpha),
                )
            is AppEntry -> if (entry.customIconUri != null) {
                    ArtworkImage(
                        model = entry.customIconUri,
                        contentDescription = entry.title,
                        fallbackText = entry.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight(0.54f)
                            .aspectRatio(1f)
                            .alpha(imageAlpha),
                    )
                } else {
                    AppIcon(
                        packageName = entry.packageName,
                        title = entry.title,
                        shape = shape,
                        modifier = Modifier.fillMaxHeight(0.54f).aspectRatio(1f),
                    )
                }
            is FolderEntry -> FolderCard(entry, platform)
            else -> Unit
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                    ),
                ),
        )
        /*
         * The platform badge and the favourite mark share a row.
         *
         * They used to be aligned into opposite corners of the card and measured
         * independently, so nothing stopped the badge from running the width of
         * the card and printing underneath the heart — the label had no ellipsis
         * either, so a platform whose short name ran long simply overlapped it.
         * Laid out against each other, the badge takes what it needs up to the
         * space the heart leaves and cuts itself short after that.
         */
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(CARD_BADGE_INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(ThorTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = platform?.shortName?.ifBlank { platform.name } ?: entry.typeLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.isFavorite) {
                Spacer(modifier = Modifier.width(CARD_BADGE_GAP.dp))
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Favorite",
                    tint = focusColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (progress != null) {
                Text(
                    text = "${(progress * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(focusColor),
                    )
                }
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (focused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(36.dp)
                    .height(3.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(focusColor),
            )
        }
    }
}

@Composable
internal fun FolderCard(folder: FolderEntry, platform: Platform?) {
    val colors = ThorTheme.colors
    val art = folder.artworkUri ?: platform?.artwork?.heroUri
    val bundled = PlatformIcons.preferredOverEnabled(platform?.artwork, platform?.id)
    if (bundled != null) {
        // Fitted rather than cropped, unlike scraped artwork: these are console
        // renders on transparency, and cropping one to fill a card cuts the
        // hardware off at the edges. Ahead of `art` for the same reason as on the
        // grid — a scraped platform image is not a chosen one.
        ArtworkImage(
            model = bundled,
            contentDescription = folder.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(BUILT_IN_ICON_FRACTION),
        )
    } else if (art != null) {
        ArtworkImage(
            model = art,
            contentDescription = folder.title,
            fallbackText = folder.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // A folder with no artwork still has to read as a folder rather than as
        // an empty card, which at this size is the glyph and nothing else.
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = folder.title,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(FOLDER_GLYPH_FRACTION),
        )
    }
}

@Composable
private fun EmptyCouchLibrary(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Your couch library is empty",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Scan games or install an app to begin.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

internal fun GridEntry.lastPlayedAt(): Long? = when (this) {
    is GameEntry -> stats.lastPlayedEpochMs
    is AppEntry -> lastPlayedEpochMs
    else -> null
}

/** Wide artwork for the selected game's room-scale couch backdrop. */
private fun GridEntry.couchBackdropArtwork(): String? = when (this) {
    is GameEntry -> metadata.artwork.backgroundImage ?: metadata.artwork.cellImage
    is FolderEntry -> artworkUri
    else -> null
}

internal fun GridEntry.platform(platforms: Map<String, Platform>): Platform? = when (this) {
    is GameEntry -> platforms[platformId]
    is FolderEntry -> PlatformFolders.platformIdOf(id)?.let(platforms::get)
    else -> null
}

internal fun GridEntry.typeLabel(): String = when (this) {
    is GameEntry -> "${platformId.uppercase()} GAME"
    is AppEntry -> "ANDROID APP"
    is FolderEntry -> "COLLECTION"
    else -> "LIBRARY"
}

internal fun Long.asCouchPlaytime(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun Long.asCouchRelativeTime(): String {
    val elapsedMinutes = ((System.currentTimeMillis() - this).coerceAtLeast(0L)) / 60_000L
    val elapsedHours = elapsedMinutes / 60L
    val elapsedDays = elapsedHours / 24L
    return when {
        elapsedDays > 365L -> "${elapsedDays / 365L}y ago"
        elapsedDays > 30L -> "${elapsedDays / 30L}mo ago"
        elapsedDays > 0L -> "${elapsedDays}d ago"
        elapsedHours > 0L -> "${elapsedHours}h ago"
        elapsedMinutes > 0L -> "${elapsedMinutes}m ago"
        else -> "Just now"
    }
}

private const val TOP_BAR_HEIGHT = 64
private const val COUCH_STATUS_WIDTH = 126
private const val SCREEN_INSET = 32

/** Name, face and bell in the bar's corner. */
private const val COUCH_PROFILE_WIDTH = 176

/**
 * The opened shade.
 *
 * Wider than the 196dp the information panel gives it: that width is set by the
 * panel it shares a screen with, and a notification read from a sofa needs the
 * line length a television can afford rather than the one a handheld can.
 */
private const val COUCH_SHADE_WIDTH = 380

/** Clear of the bar, so the shade reads as hanging from it rather than joined. */
private const val SHADE_GAP = 8

/**
 * How far the screen behind the shade is dimmed.
 *
 * Lighter than the theme's own scrim, which is set for a dialog that takes the
 * whole screen. This is a corner panel, and dimming the dashboard to dialog
 * strength for it would say the launcher had been interrupted.
 */
private const val SHADE_SCRIM_ALPHA = 0.45f

private const val SHADE_MS = 200
private const val INFO_COVER_WIDTH = 104
private const val INFO_COLUMN_GAP = 14
private const val INFO_PANEL_PADDING = 15
private const val INFO_PANEL_ALPHA = 0.82f
private const val INFO_DESCRIPTION_LINES = 5
private const val CARD_GAP = 14

/** How far a card's platform badge and favourite mark sit inside its corner. */
private const val CARD_BADGE_INSET = 7

/** Kept between the two, so a long badge stops short rather than meeting it. */
private const val CARD_BADGE_GAP = 6

/** Preferred card edge, subject to what the shelf slot can hold. */
private const val SQUARE_CARD_SIZE = 173
private const val MIN_CARD_SIZE = 104
private const val CARD_RAIL_EXTRA_HEIGHT = 22
/** Room the rail's title row takes above the cards. */
private const val RAIL_HEADER_HEIGHT = 26
private const val HERO_CARD_WIDTH = 0.46f

/** The information card.s own height, now that it no longer shares a weight. */
private const val HERO_HEIGHT = 196

/**
 * What the shelf reserves at the bottom of the screen.
 *
 * The rail plus its header and inset — see `couchCardSize`, which divides this
 * back down into a card. Fixed so the space left over goes to the panel above,
 * which is what puts the panel in the middle of the screen rather than at the
 * top of it.
 */
private const val SHELF_HEIGHT = 232
private const val HERO_ACTION_HEIGHT = 40
private const val LIBRARY_SUMMARY_HEIGHT = 94
private const val BACKDROP_SETTLE_MS = 105L
private const val BACKDROP_CROSSFADE_MS = 300
private const val RAIL_TRANSITION_MS = 220

/** How much of a card the shipped platform artwork fills. */
private const val BUILT_IN_ICON_FRACTION = 0.86f

/** How much of an artless folder card its glyph fills. */
private const val FOLDER_GLYPH_FRACTION = 0.42f
