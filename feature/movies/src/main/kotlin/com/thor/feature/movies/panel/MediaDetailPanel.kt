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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CacheStatus
import com.thor.core.model.Episode
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.Season
import com.thor.core.model.StreamSource
import com.thor.core.model.WatchProgress
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.ThorDropdownItem
import com.thor.core.ui.component.ThorDropdownMenu
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.media.ProviderOutcome
import com.thor.data.media.SourceResult
import com.thor.feature.movies.DetailState
import com.thor.feature.movies.SourceState

/** Selected-title details and playback choices on the companion display. */
@Composable
fun MediaDetailPanel(
    detail: DetailState,
    sources: SourceState,
    focusedSource: Int?,
    onSourcePicked: (Int) -> Unit,
    selectorFocused: Boolean = false,
    showSeriesSelector: Boolean = false,
    onSeasonSelected: (Int) -> Unit = {},
    onEpisodeSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val item = detail.item
    val infoWeight = INFO_WEIGHT

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        item?.backdropUrl?.let { backdrop ->
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Two inexpensive scrims keep both columns legible over bright artwork
        // without flattening the selected title into a solid background.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    *arrayOf(
                            0f to colors.background.copy(alpha = 0.96f),
                            0.56f to colors.background.copy(alpha = 0.74f),
                            1f to colors.background.copy(alpha = 0.48f),
                        ),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, colors.background.copy(alpha = 0.84f)),
                ),
            ),
        )

        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Choose a title to see its story and playback sources.",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Box
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge horizontally: this is the bottom display's main
                // surface, not a floating dialog. Its children retain their own
                // padding so text and source rows still have comfortable insets.
                .padding(top = dimens.spacingSmall),
            shape =                 ThorTheme.shapes.panel,
            alphaOverride = null,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                InformationPanel(
                    detail = detail,
                    modifier = Modifier.weight(infoWeight).fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colors.outline.copy(alpha = 0.28f)),
                )
                PlaybackPanel(
                    detail = detail,
                    sources = sources,
                    focusedSource = focusedSource,
                    selectorFocused = selectorFocused,
                    showSeriesSelector = showSeriesSelector,
                    onSourcePicked = onSourcePicked,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeSelected = onEpisodeSelected,
                    modifier = Modifier.weight(1f - infoWeight).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
internal fun InformationPanel(
    detail: DetailState,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val item = detail.item ?: return
    val scroll = rememberScrollState()

    LaunchedEffect(item.id.key) {
        scroll.scrollTo(0)
    }

    Column(
        modifier = modifier
            .padding(dimens.spacing)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            HeroIdentity(item = item, loading = detail.loading)

            detail.resumeProgress?.takeIf { it.isResumable }?.let { progress ->
                ResumeSummary(progress)
            }

            if (item.overview.isNotBlank()) {
                SectionLabel("STORY")
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = OVERVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.take(GENRES_SHOWN).joinToString("  /  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val director = item.crew.firstOrNull { it.role.equals("Director", ignoreCase = true) }
            if (director != null || item.cast.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                ) {
                    director?.let {
                        CreditBlock(
                            label = "DIRECTOR",
                            value = it.name,
                            modifier = Modifier.weight(0.36f),
                        )
                    }
                    if (item.cast.isNotEmpty()) {
                        CreditBlock(
                            label = "STARRING",
                            value = item.cast.take(CAST_SHOWN).joinToString(", ") { it.name },
                            modifier = Modifier.weight(0.64f),
                        )
                    }
                }
            }

            if (detail.similar.isNotEmpty()) {
                SectionLabel("YOU MAY ALSO LIKE")
                Text(
                    text = detail.similar.take(SIMILAR_SHOWN).joinToString("  /  ") { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

/**
 * The right side of the detail surface.
 *
 * A show uses this column in two deliberate steps. The season/episode browser
 * owns the complete column until an episode is confirmed, then the source list
 * replaces it and gets the same space. Films have no selector and continue to
 * show sources immediately.
 */
@Composable
internal fun PlaybackPanel(
    detail: DetailState,
    sources: SourceState,
    focusedSource: Int?,
    selectorFocused: Boolean,
    showSeriesSelector: Boolean,
    onSourcePicked: (Int) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item ?: return

    if (item.isSeries && showSeriesSelector) {
        SeriesSelector(
            item = item,
            detail = detail,
            selectorFocused = selectorFocused,
            onSeasonSelected = onSeasonSelected,
            onEpisodeSelected = onEpisodeSelected,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        SourceColumn(
            sources = sources,
            focusedIndex = focusedSource,
            onPicked = onSourcePicked,
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** A season dropdown followed by an independently scrollable vertical episode list. */
@Composable
private fun SeriesSelector(
    item: MediaItem,
    detail: DetailState,
    selectorFocused: Boolean,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val seasons = item.orderedSeasons.filter { it.episodes.isNotEmpty() }
    val episodeListState = rememberLazyListState()
    val episodes = detail.season
        ?.takeIf { it.number == detail.selectedSeason }
        ?.episodes
        .orEmpty()
    val selectedSeason = seasons.firstOrNull { it.number == detail.selectedSeason }
    var seasonMenuExpanded by remember(item.id.key) { mutableStateOf(false) }

    LaunchedEffect(item.id.key, detail.selectedSeason, detail.selectedEpisode, episodes) {
        val selectedIndex = episodes.indexOfFirst { it.number == detail.selectedEpisode }
        if (selectedIndex >= 0) episodeListState.animateScrollToItem(selectedIndex)
    }

    Column(
        modifier = modifier.padding(dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectorFocused) {
            Text(
                text = "L/R SEASON  /  U/D EPISODE  /  A SOURCES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.End),
            )
        }

        SeasonDropdown(
            selected = selectedSeason,
            seasons = seasons,
            expanded = seasonMenuExpanded,
            focused = selectorFocused,
            onExpandedChange = { seasonMenuExpanded = it },
            onSelected = { number ->
                seasonMenuExpanded = false
                onSeasonSelected(number)
            },
        )

        SectionLabel("EPISODES")
        if (episodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (detail.loading) {
                        "Loading episodes..."
                    } else {
                        "Episode information unavailable."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = episodeListState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.number }) { _, episode ->
                    EpisodeCard(
                        episode = episode,
                        selected = episode.number == detail.selectedEpisode,
                        selectorFocused = selectorFocused,
                        onClick = { onEpisodeSelected(episode.number) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonDropdown(
    selected: Season?,
    seasons: List<Season>,
    expanded: Boolean,
    focused: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = hover.isHovered || focused

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerHover(hover)
                .thorCursor(focused = lit, cornerRadius = ThorTheme.dimens.cornerRadiusSmall)
                .clip(ThorTheme.shapes.small)
                .background(if (lit) colors.surfaceHighest else colors.surfaceElevated)
                .clickable(enabled = seasons.isNotEmpty()) { onExpandedChange(!expanded) }
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected?.let(::seasonTitle) ?: "No seasons available",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lit) colors.cursor else colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                selected?.let { season ->
                    Text(
                        text = "${season.episodes.size} episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Close seasons" else "Choose season",
                tint = if (lit) colors.cursor else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        ThorDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            seasons.forEach { season ->
                ThorDropdownItem(
                    label = seasonTitle(season),
                    trailing = "${season.episodes.size} EP",
                    selected = season.number == selected?.number,
                    onClick = { onSelected(season.number) },
                )
            }
        }
    }
}

private fun seasonTitle(season: Season): String =
    if (season.isSpecials) "Specials" else "Season ${season.number}"

@Composable
private fun EpisodeCard(
    episode: Episode,
    selected: Boolean,
    selectorFocused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val focused = hover.isHovered || (selectorFocused && selected)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = focused, cornerRadius = ThorTheme.dimens.cornerRadiusSmall)
            .clip(ThorTheme.shapes.small)
            .background(
                when {
                    selected -> colors.cursor.copy(alpha = 0.14f)
                    hover.isHovered -> colors.surfaceHighest
                    else -> colors.surfaceElevated
                },
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(EPISODE_STILL_WIDTH.dp)
                .aspectRatio(16f / 9f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest),
        ) {
            ArtworkImage(
                model = episode.stillUrl,
                contentDescription = episode.title,
                fallbackText = episode.code,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = episode.code,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.cursor else colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = episode.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.cursor else colors.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = listOfNotNull(
                episode.runtimeMinutes?.takeIf { it > 0 }?.let { "$it min" },
                episode.airDate?.takeIf(String::isNotBlank),
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
            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroIdentity(item: MediaItem, loading: Boolean) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(POSTER_WIDTH.dp)
                .aspectRatio(2f / 3f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceElevated),
        ) {
            ArtworkImage(
                model = item.posterUrl,
                contentDescription = item.title,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (item.isSeries) "SERIES" else "FEATURE FILM",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
                if (loading) {
                    Text(
                        text = "UPDATING",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            item.logoUrl?.let { logo ->
                ArtworkImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(LOGO_HEIGHT.dp),
                )
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (item.logoUrl == null) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            FactLine(item)
            Ratings(item.ratings)
        }
    }
}

@Composable
internal fun ResumeSummary(progress: WatchProgress) {
    val colors = ThorTheme.colors
    val episode = if (progress.seasonNumber != null && progress.episodeNumber != null) {
        "S%02dE%02d".format(progress.seasonNumber, progress.episodeNumber)
    } else {
        null
    }
    val minutesLeft = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L)
        .coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = "CONTINUE WATCHING",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = listOfNotNull(episode, "$minutesLeft min remaining").joinToString("  /  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
            }
            Text(
                text = "${(progress.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            color = colors.cursor,
            trackColor = colors.surfaceHighest,
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(ThorTheme.shapes.pill),
        )
    }
}

@Composable
private fun FactLine(item: MediaItem) {
    val facts = listOfNotNull(
        item.releaseYear?.toString(),
        item.runtimeMinutes?.takeIf { it > 0 }?.let { minutes ->
            if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        },
        item.contentRating?.takeIf(String::isNotBlank),
        if (item.isSeries) "${item.seasons.count { !it.isSpecials }} seasons" else null,
    )
    if (facts.isEmpty()) return

    Text(
        text = facts.joinToString("  /  "),
        style = MaterialTheme.typography.labelMedium,
        color = ThorTheme.colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Ratings(ratings: MediaRatings) {
    if (ratings.isEmpty) return

    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ratings.tmdb?.let { RatingBadge("TMDb", "%.1f".format(it)) }
        ratings.imdb?.let { RatingBadge("IMDb", "%.1f".format(it)) }
        ratings.rottenTomatoes?.let { RatingBadge("RT", "$it%") }
    }
}

@Composable
private fun RatingBadge(source: String, value: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(colors.surfaceElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CreditBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ThorTheme.colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

/** Ranked playback options, kept visible while the viewer decides. */
@Composable
private fun SourceColumn(
    sources: SourceState,
    focusedIndex: Int?,
    onPicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val found = sources.result as? SourceResult.Found
    val ranked = found?.ranked.orEmpty()
    val instant = ranked.count { it.cached == CacheStatus.CACHED || it.isPlayableDirectly }
    val episodeCode = if (sources.seasonNumber != null && sources.episodeNumber != null) {
        "S%02dE%02d".format(sources.seasonNumber, sources.episodeNumber)
    } else {
        null
    }
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex != null && ranked.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, ranked.lastIndex))
        }
    }

    Column(
        modifier = modifier.padding(dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = listOfNotNull(episodeCode, when {
                            sources.searching -> "Searching providers..."
                            ranked.isEmpty() -> "No playable source yet"
                            else -> "${ranked.size} found"
                        }).joinToString("  /  "),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (instant > 0) {
                        Text(
                            text = "$instant INSTANT",
                            style = MaterialTheme.typography.labelSmall,
                            color = contrastingContentColor(colors.cursor),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(ThorTheme.shapes.pill)
                                .background(colors.cursor)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            sourceMessage(sources, found)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sources.resolveError != null) colors.error else colors.onSurfaceVariant,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemsIndexed(ranked, key = { _, source -> source.id }) { index, source ->
                    SourceRow(
                        source = source,
                        focused = index == focusedIndex,
                        onClick = { onPicked(index) },
                    )
                }
            }
    }
}

private fun sourceMessage(sources: SourceState, found: SourceResult.Found?): String? {
    if (sources.resolveError != null) return sources.resolveError
    if (sources.resolving) return "Opening the selected source..."
    if (sources.searching && found?.ranked.isNullOrEmpty()) return "Looking for something to play."

    return when (val result = sources.result) {
        null -> "Pause on a title to inspect what can play it."
        is SourceResult.NoProviders ->
            "No source installed. Open Settings > Library > Films and shows."

        is SourceResult.NoImdbId -> "This title has no IMDb id, so it cannot be searched."
        is SourceResult.Empty -> listOfNotNull(
            "Nothing was found for this title.",
            result.outcomes.describe(),
        ).joinToString("\n")

        is SourceResult.Found -> if (result.ranked.isEmpty() && result.all.isNotEmpty()) {
            "Sources were found, but none match the current filters. Disable Cached only to include downloads."
        } else {
            null
        }
    }
}

@Composable
private fun SourceRow(source: StreamSource, focused: Boolean, onClick: () -> Unit) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    val instant = source.cached == CacheStatus.CACHED || source.isPlayableDirectly

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = lit, cornerRadius = ThorTheme.dimens.cornerRadiusSmall)
            .clip(ThorTheme.shapes.small)
            .background(if (lit) colors.surfaceHighest else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.quality.summary.ifBlank { "Unknown quality" },
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) colors.cursor else colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (instant) {
                Text(
                    text = "INSTANT",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = listOfNotNull(
                source.sizeLabel,
                source.seeders?.let { "$it seeders" },
                source.providerName,
            ).joinToString("  /  "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = source.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun List<ProviderOutcome>.describe(): String? = this
    .mapNotNull { outcome -> outcome.note?.let { "${outcome.provider}: $it" } }
    .takeIf { it.isNotEmpty() }
    ?.joinToString("\n")

private const val INFO_WEIGHT = 0.59f
private const val SHELF_INFO_WEIGHT = 0.66f
private const val LIST_INFO_WEIGHT = 0.43f
private const val POSTER_WIDTH = 86
private const val LOGO_HEIGHT = 44
private const val OVERVIEW_LINES = 6
private const val GENRES_SHOWN = 5
private const val CAST_SHOWN = 5
private const val SIMILAR_SHOWN = 5
private const val EPISODE_STILL_WIDTH = 88
