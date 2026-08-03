package com.thor.feature.home.couch

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.AppEntry
import com.thor.core.model.ClockStyle
import com.thor.core.model.DisplaySettings
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.LauncherTab
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFolders
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.LauncherStatusBar
import com.thor.feature.home.LauncherUiState
import com.thor.feature.home.component.AppIcon
import kotlinx.coroutines.delay

/** Controller position in Couch Mode's rail-based library. */
data class CouchFocus(val rail: Int = 0, val item: Int = 0)

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
    val selectedPlatform = selectedPlatformId?.let(state.platformsById::get)
    val platformGames = if (selectedPlatform == null) {
        games
    } else {
        games.filter { it.platformId == selectedPlatform.id }
    }
    val playable = buildList<GridEntry> {
        addAll(games)
        addAll(apps)
    }
    val recent = playable
        .filter { it.lastPlayedAt() != null }
        .sortedByDescending(GridEntry::lastPlayedAt)
        .take(16)

    return buildList {
        if (recent.isNotEmpty()) add(CouchRail("continue", "Continue playing", recent))
        playable.filter(GridEntry::isFavorite)
            .sortedBy(GridEntry::sortTitle)
            .takeIf(List<GridEntry>::isNotEmpty)
            ?.let { add(CouchRail("favourites", "Favourites", it)) }
        platformGames.sortedBy(GameEntry::sortTitle)
            .takeIf(List<GameEntry>::isNotEmpty)
            ?.let {
                add(
                    CouchRail(
                        id = selectedPlatform?.let { platform -> "platform:${platform.id}" }
                            ?: "games",
                        title = selectedPlatform?.name ?: "Game library",
                        entries = it,
                    ),
                )
            }
        apps.sortedBy(AppEntry::sortTitle)
            .takeIf(List<AppEntry>::isNotEmpty)
            ?.let { add(CouchRail("apps", "Apps", it)) }
        folders.sortedBy(FolderEntry::sortTitle)
            .takeIf(List<FolderEntry>::isNotEmpty)
            ?.let { add(CouchRail("collections", "Collections", it)) }
    }
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
    onTabSelected: (LauncherTab) -> Unit,
    onSettingsSelected: () -> Unit,
    onPlatformSelected: (Int) -> Unit,
    onEntryFocused: (rail: Int, item: Int) -> Unit,
    onEntrySelected: (GridEntry) -> Unit,
    onEntryLongPressed: (GridEntry) -> Unit,
    fullscreenSection: Boolean = false,
    sectionContent: (@Composable (LauncherTab) -> Unit)? = null,
    settingsContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val baseDensity = LocalDensity.current
    val safeUiScale = uiScale.coerceIn(
        DisplaySettings.MIN_COUCH_UI_SCALE,
        DisplaySettings.MAX_COUCH_UI_SCALE,
    )
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, safeUiScale) {
        Density(
            density = baseDensity.density * safeUiScale,
            fontScale = baseDensity.fontScale,
        )
    }
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
    var backdropEntry by remember { mutableStateOf(focusedEntry) }

    LaunchedEffect(focusedEntry?.id, focusedEntry?.heroArtwork()) {
        if (focusedEntry == null || backdropEntry == null) {
            backdropEntry = focusedEntry
        } else {
            // A held stick can cross ten games in a moment. Waiting for a brief
            // settle means all cancelled intermediate selections avoid starting
            // an image request and a full-screen crossfade.
            delay(BACKDROP_SETTLE_MS)
            backdropEntry = focusedEntry
        }
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
            CouchBackdrop(
                entry = backdropEntry,
                platform = backdropEntry?.platform(state.platformsById),
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
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
                    )
                }

                if (settingsSelected) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        settingsContent?.invoke()
                    }
                } else if (selectedTab.isHome) {
                    if (focusedEntry == null) {
                        EmptyCouchLibrary(modifier = Modifier.weight(1f))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                CouchHero(
                                    entry = focusedEntry,
                                    onPlay = { onEntrySelected(focusedEntry) },
                                    modifier = Modifier.fillMaxWidth().weight(HERO_WEIGHT),
                                )
                                CouchRailDeck(
                                    rails = rails,
                                    focus = CouchFocus(safeRail, safeItem),
                                    platforms = state.platformsById,
                                    onEntryFocused = onEntryFocused,
                                    onEntrySelected = onEntrySelected,
                                    onEntryLongPressed = onEntryLongPressed,
                                    modifier = Modifier.fillMaxWidth().weight(1f - HERO_WEIGHT),
                                )
                            }

                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        sectionContent?.invoke(selectedTab)
                    }
                }
            }
        }
    }
}

private data class CouchBackdropModel(
    val entryId: String,
    val artwork: String,
    val accentArgb: Long?,
)

@Composable
private fun CouchBackdrop(entry: GridEntry?, platform: Platform?) {
    val art = entry?.heroArtwork() ?: return
    val animationsEnabled = ThorTheme.materials.animationsEnabled
    val model = remember(entry.id, art, platform?.accentArgb) {
        CouchBackdropModel(entry.id, art, platform?.accentArgb)
    }
    val drift = remember { Animatable(0f) }

    LaunchedEffect(model.entryId, animationsEnabled) {
        drift.snapTo(0f)
        if (animationsEnabled) {
            drift.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = HERO_DRIFT_MS, easing = LinearEasing),
            )
        }
    }

    Crossfade(
        targetState = model,
        animationSpec = tween(if (animationsEnabled) HERO_CROSSFADE_MS else 0),
        label = "couch-hero-art",
        modifier = Modifier.fillMaxSize(),
    ) { backdrop ->
        val accent = backdrop.accentArgb?.let(::Color) ?: ThorTheme.colors.cursor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = HERO_START_SCALE + drift.value * HERO_SCALE_DELTA
                    scaleX = zoom
                    scaleY = zoom
                    translationX = -drift.value * HERO_DRIFT_PX
                },
        ) {
            ArtworkImage(
                model = backdrop.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                crossfadeMillis = 0,
                modifier = Modifier.fillMaxSize().alpha(0.86f),
            )
            // Platform identity reads as light in the room, not another panel.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to accent.copy(alpha = 0.28f),
                        0.34f to accent.copy(alpha = 0.13f),
                        0.72f to Color.Transparent,
                    ),
                ),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.52f to Color.Black.copy(alpha = 0.28f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
            )
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
) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT.dp)
            .background(colors.background.copy(alpha = 0.72f))
            .padding(horizontal = SCREEN_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "LOKI",
            style = MaterialTheme.typography.titleLarge,
            color = colors.cursor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(end = 12.dp),
        )
        tabs.forEach { tab ->
            val selected = !settingsSelected && tab == selectedTab
            CouchNavItem(
                label = tab.label,
                icon = tab.couchIcon(),
                selected = selected,
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
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = if (showStatusBar) 2.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.cursor),
            )
            Text(
                text = "COUCH",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            if (showStatusBar) {
                Box(
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .width(COUCH_STATUS_WIDTH.dp),
                ) {
                    LauncherStatusBar(
                        clockStyle = clockStyle,
                        visible = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
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
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(
                when {
                    focused -> colors.cursor
                    selected -> colors.surfaceHighest.copy(alpha = 0.92f)
                    else -> Color.Transparent
                },
            )
            .then(
                if (focused) Modifier.border(
                    2.dp,
                    contrastingContentColor(colors.cursor).copy(alpha = 0.64f),
                    ThorTheme.shapes.pill,
                ) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                focused -> contrastingContentColor(colors.cursor)
                selected -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = when {
                focused -> contrastingContentColor(colors.cursor)
                selected -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun LauncherTab.couchIcon(): ImageVector = when (this) {
    LauncherTab.STREAM -> Icons.Rounded.Cast
    LauncherTab.HOME -> Icons.Rounded.Home
    LauncherTab.MOVIES -> Icons.Rounded.Movie
}

@Composable
private fun CouchHero(
    entry: GridEntry,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(HERO_TEXT_WIDTH),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val logo = (entry as? GameEntry)?.metadata?.artwork?.logo
            if (logo != null) {
                ArtworkImage(
                    model = logo,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .fillMaxWidth(HERO_LOGO_WIDTH)
                        .heightIn(min = HERO_LOGO_MIN_HEIGHT.dp, max = HERO_LOGO_MAX_HEIGHT.dp),
                )
            } else {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(HERO_ACTION_GAP.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(ThorTheme.shapes.small)
                        .background(colors.cursor)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (entry is FolderEntry) {
                            Icons.Rounded.FolderOpen
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = null,
                        tint = contrastingContentColor(colors.cursor),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = if (entry is FolderEntry) "OPEN" else "PLAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = contrastingContentColor(colors.cursor),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (entry.isFavorite) {
                    Text(
                        text = "FAVOURITE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cursor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(ThorTheme.shapes.pill)
                            .background(colors.cursor.copy(alpha = 0.14f))
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
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
        modifier = modifier.padding(top = 2.dp),
    ) { displayed ->
        CouchRailContent(
            rails = rails,
            railIndex = displayed.index,
            rail = displayed.rail,
            focusedItem = focus.item,
            platforms = platforms,
            onEntryFocused = onEntryFocused,
            onEntrySelected = onEntrySelected,
            onEntryLongPressed = onEntryLongPressed,
            modifier = Modifier.fillMaxSize(),
        )
    }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SCREEN_INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = rail.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${railIndex + 1} OF ${rails.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            rails.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .height(5.dp)
                        .width(if (index == railIndex) 25.dp else 9.dp)
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
            modifier = Modifier.fillMaxSize().padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = SCREEN_INSET.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
        ) {
            itemsIndexed(rail.entries, key = { _, entry -> entry.id }) { index, entry ->
                CouchCard(
                    entry = entry,
                    platform = entry.platform(platforms),
                    focused = index == safeItem,
                    onFocus = { onEntryFocused(railIndex, index) },
                    onSelected = { onEntrySelected(entry) },
                    onLongPressed = { onEntryLongPressed(entry) },
                )
            }
        }
    }
}

@Composable
private fun CouchCard(
    entry: GridEntry,
    platform: Platform?,
    focused: Boolean,
    onFocus: () -> Unit,
    onSelected: () -> Unit,
    onLongPressed: () -> Unit,
) {
    val colors = ThorTheme.colors
    val focusColor = platform?.let { Color(it.accentArgb) } ?: colors.cursor
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.075f else 1f,
        animationSpec = tween(160),
        label = "couch-card-focus",
    )
    val imageAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.84f,
        animationSpec = tween(160),
        label = "couch-card-depth",
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 16.dp else 0.dp,
        animationSpec = tween(160),
        label = "couch-card-elevation",
    )
    val shape = ThorTheme.shapes.small
    val isGame = entry is GameEntry
    val cardWidth = if (isGame) GAME_CARD_WIDTH else CARD_WIDTH
    val cardAspect = if (isGame) GAME_CARD_ASPECT else CARD_ASPECT

    Column(
        modifier = Modifier
            .width(cardWidth.dp)
            .zIndex(if (focused) 1f else 0f)
            .scale(scale),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspect)
                .shadow(elevation = elevation, shape = shape, clip = false)
                .clip(shape)
                .background(
                    if (focused) focusColor.copy(alpha = 0.22f)
                    else colors.surfaceHighest,
                )
                .then(
                    if (focused) Modifier.border(3.dp, focusColor, shape)
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape),
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
                    model = entry.metadata.artwork.boxArt
                        ?: entry.metadata.artwork.backgroundImage,
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
                            .fillMaxHeight(0.58f)
                            .aspectRatio(1f)
                            .alpha(imageAlpha),
                    )
                } else {
                    AppIcon(
                        packageName = entry.packageName,
                        title = entry.title,
                        shape = shape,
                        modifier = Modifier.fillMaxHeight(0.58f).aspectRatio(1f),
                    )
                }
                is FolderEntry -> FolderCard(entry, platform)
                else -> Unit
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                        ),
                    ),
            )
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(Color.Black.copy(alpha = 0.66f))
                        .border(1.dp, focusColor.copy(alpha = 0.72f), ThorTheme.shapes.pill)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = platform?.shortName?.ifBlank { platform.name }
                            ?: entry.typeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(focusColor),
                )
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.66f),
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FolderCard(folder: FolderEntry, platform: Platform?) {
    val colors = ThorTheme.colors
    val art = folder.artworkUri ?: platform?.artwork?.heroUri
    if (art != null) {
        ArtworkImage(
            model = art,
            contentDescription = folder.title,
            fallbackText = folder.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(50.dp),
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

private fun GridEntry.lastPlayedAt(): Long? = when (this) {
    is GameEntry -> stats.lastPlayedEpochMs
    is AppEntry -> lastPlayedEpochMs
    else -> null
}

private fun GridEntry.heroArtwork(): String? = when (this) {
    is GameEntry -> metadata.artwork.backgroundImage
    is FolderEntry -> artworkUri
    else -> null
}

private fun GridEntry.platform(platforms: Map<String, Platform>): Platform? = when (this) {
    is GameEntry -> platforms[platformId]
    is FolderEntry -> PlatformFolders.platformIdOf(id)?.let(platforms::get)
    else -> null
}

private fun GridEntry.typeLabel(): String = when (this) {
    is GameEntry -> "${platformId.uppercase()} GAME"
    is AppEntry -> "ANDROID APP"
    is FolderEntry -> "COLLECTION"
    else -> "LIBRARY"
}

private fun Long.asCouchPlaytime(): String {
    val totalMinutes = this / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun Long.asCouchRelativeTime(): String {
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
private const val INFO_COVER_WIDTH = 104
private const val INFO_COLUMN_GAP = 14
private const val INFO_PANEL_PADDING = 15
private const val INFO_PANEL_ALPHA = 0.82f
private const val INFO_DESCRIPTION_LINES = 5
private const val CARD_WIDTH = 210
private const val GAME_CARD_WIDTH = 148
private const val CARD_GAP = 15
private const val CARD_ASPECT = 16f / 9f
private const val GAME_CARD_ASPECT = 2f / 3f
private const val HERO_WEIGHT = 0.43f
private const val HERO_TEXT_WIDTH = 0.66f
private const val HERO_LOGO_WIDTH = 0.72f
private const val HERO_LOGO_MIN_HEIGHT = 58
private const val HERO_LOGO_MAX_HEIGHT = 94
private const val HERO_ACTION_GAP = 12
private const val HERO_CROSSFADE_MS = 420
private const val BACKDROP_SETTLE_MS = 110L
private const val HERO_DRIFT_MS = 12_000
private const val HERO_START_SCALE = 1.025f
private const val HERO_SCALE_DELTA = 0.035f
private const val HERO_DRIFT_PX = 18f
private const val RAIL_TRANSITION_MS = 220
