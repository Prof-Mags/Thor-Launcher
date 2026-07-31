package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaType
import com.thor.core.model.StreamSource
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.media.SourceResult

/** Which of the section's three states the panels are showing. */
enum class MoviesMode { BROWSE, SOURCES, PLAYING }

/**
 * The Movies section's top panel.
 *
 * Three states, and the bottom panel is always showing the matching half of the
 * same one: browsing shows shelves here and a description below; choosing a
 * source shows the title here and the list below; playing shows the film here
 * and the controls below. The panels never disagree about which state they are
 * in because they are handed the same value.
 */
@Composable
fun MoviesTopPanel(
    mode: MoviesMode,
    state: MoviesUiState,
    playback: Playback?,
    onStatus: (PlayerStatus) -> Unit,
    onCommands: (PlayerCommands) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (mode == MoviesMode.PLAYING && playback != null) {
            PlayerSurface(
                url = playback.url,
                resumeFromMs = playback.resumeFromMs,
                onStatus = onStatus,
                onCommands = onCommands,
            )
        } else {
            MoviesBrowseScreen(state = state)
        }
    }
}

/** The Movies section's bottom panel: describe, choose, or control. */
@Composable
fun MoviesBottomPanel(
    mode: MoviesMode,
    detail: DetailState,
    sources: SourceState,
    playback: Playback?,
    status: PlayerStatus,
    focusedSource: Int,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    onPlayerAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    /** A source chosen by touch or pointer, by position in the ranked list. */
    onSourcePicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        mode == MoviesMode.PLAYING && playback != null -> PlayerControls(
            playback = playback,
            status = status,
            focusedAction = focusedAction,
            hasNextEpisode = hasNextEpisode,
            onAction = onPlayerAction,
            onSeek = onSeek,
            modifier = modifier,
        )

        /*
         * One panel for browsing and for choosing.
         *
         * The sources sit beside the description whether or not the cursor is in
         * them, so what would play is visible while deciding *whether* to play.
         * Swapping to a separate list on choosing would take the title away at
         * the moment its details matter most.
         */
        else -> MediaDetailPanel(
            detail = detail,
            sources = sources,
            focusedSource = focusedSource.takeIf { mode == MoviesMode.SOURCES },
            onSourcePicked = onSourcePicked,
            modifier = modifier,
        )
    }
}

/**
 * The sources found for a title.
 *
 * Ordered by [com.thor.core.model.SourceRanking] and shown in that order, so the
 * list the user reads is the same one the automatic choice used. Cache status is
 * the first thing on each row because it is the first thing that matters: it is
 * the difference between playing now and waiting.
 */
@Composable
private fun SourceList(
    sources: SourceState,
    focusedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    val ranked = (sources.result as? SourceResult.Found)?.ranked.orEmpty()

    LaunchedEffect(focusedIndex) {
        if (ranked.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, ranked.lastIndex))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (ranked.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        sources.searching -> "Searching…"
                        sources.result is SourceResult.NoProviders ->
                            "No torrent indexers configured.\nSettings → Library → Films and shows."

                        sources.result is SourceResult.NoImdbId ->
                            "This title has no IMDb id, so it cannot be searched."

                        else -> "No sources found."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(dimens.spacingLarge),
                )
            }
            return@Box
        }

        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            itemsIndexed(ranked, key = { _, source -> source.id }) { index, source ->
                SourceRow(source = source, focused = index == focusedIndex)
            }
        }
    }
}

@Composable
private fun SourceRow(source: StreamSource, focused: Boolean) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = lit, cornerRadius = ThorTheme.dimens.cornerRadiusSmall)
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = source.quality.summary.ifBlank { "Unknown quality" },
                style = MaterialTheme.typography.labelLarge,
                color = if (lit) colors.cursor else colors.onSurface,
            )
            Text(
                text = when (source.cached) {
                    CacheStatus.CACHED -> "Instant"
                    CacheStatus.NOT_CACHED -> "Not cached"
                    CacheStatus.UNKNOWN -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (source.cached == CacheStatus.CACHED) {
                    colors.cursor
                } else {
                    colors.onSurfaceVariant
                },
            )
        }

        Text(
            text = listOfNotNull(
                source.sizeLabel,
                source.seeders?.let { "$it seeders" },
                source.providerName,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )

        Text(
            text = source.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Films or shows, for the section's own type switch. */
val MediaTypes: List<MediaType> = MediaType.entries
