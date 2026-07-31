package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.CacheStatus
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRatings
import com.thor.core.model.StreamSource
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.media.SourceResult

/**
 * What is highlighted, on the bottom panel.
 *
 * The same column shape a game gets on the information screen, for the same
 * reason: the launcher's two panels always mean the same two things, and a
 * media section that rearranged that relationship would make the device feel
 * like two devices.
 *
 * Every field keeps its slot whether or not the title has a value for it, so
 * the panel does not reflow as the cursor moves along a shelf.
 */
@Composable
fun MediaDetailPanel(
    detail: DetailState,
    sources: SourceState,
    /** Which source the cursor is on, or null while the cursor is in the shelves. */
    focusedSource: Int?,
    onSourcePicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val item = detail.item

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        // The backdrop fills the panel; the column sits over its left half.
        item?.backdropUrl?.let { backdrop ->
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Box
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(PANEL_WEIGHT)
                    .fillMaxHeight()
                    .padding(dimens.spacing)
                    .clip(RoundedCornerShape(dimens.cornerRadius))
                    .background(colors.background.copy(alpha = PANEL_ALPHA))
                    .border(
                        width = 1.dp,
                        color = colors.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(dimens.cornerRadius),
                    )
                    .padding(dimens.spacing)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                // A wordmark is the title where the catalogue has one.
                if (item.logoUrl != null) {
                    ArtworkImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(LOGO_HEIGHT.dp),
                    )
                } else {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FactLine(item)
                Ratings(item.ratings)

                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (item.cast.isNotEmpty()) {
                    Label("CAST")
                    Text(
                        text = item.cast.take(CAST_SHOWN).joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                val director = item.crew.firstOrNull { it.role == "Director" }
                if (director != null) {
                    Label("DIRECTOR")
                    Text(
                        text = director.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (item.isSeries) {
                    Label("SEASONS")
                    Text(
                        text = item.orderedSeasons
                            .filterNot { it.isSpecials }
                            .joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (detail.similar.isNotEmpty()) {
                    Label("SIMILAR")
                    Text(
                        text = detail.similar.take(SIMILAR_SHOWN).joinToString(", ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            /*
             * The sources, beside the description rather than behind a press.
             *
             * Not being able to see what was available until after committing to
             * play made choosing feel like a lottery — and the automatic pick,
             * however well ranked, is an opinion the viewer could not inspect or
             * overrule. Both columns describe the same title: what it is on the
             * left, what it would actually play on the right.
             */
            SourceColumn(
                sources = sources,
                focusedIndex = focusedSource,
                onPicked = onSourcePicked,
                modifier = Modifier
                    .weight(1f - PANEL_WEIGHT)
                    .fillMaxHeight(),
            )
        }
    }
}

/** "2021 · 2h 35m · PG-13", the line under the title. */
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
        text = facts.joinToString(" · "),
        style = MaterialTheme.typography.titleSmall,
        color = ThorTheme.colors.onSurfaceVariant,
    )
}

/**
 * Scores, each shown only where there is one.
 *
 * Reserving space for three services and filling one reads as two failures
 * rather than as one rating.
 */
@Composable
private fun Ratings(ratings: MediaRatings) {
    if (ratings.isEmpty) return
    val colors = ThorTheme.colors

    Row(horizontalArrangement = Arrangement.spacedBy(ThorTheme.dimens.spacing)) {
        ratings.tmdb?.let { Rating("TMDb", "%.1f".format(it)) }
        ratings.imdb?.let { Rating("IMDb", "%.1f".format(it)) }
        ratings.rottenTomatoes?.let { Rating("RT", "$it%") }
    }
}

@Composable
private fun Rating(source: String, value: String) {
    Column {
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall,
            color = ThorTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = ThorTheme.colors.onSurface,
        )
    }
}

/**
 * Everything that could play this title, ranked, as a column of its own.
 *
 * States are distinguished rather than collapsed into "no sources", because the
 * remedies are completely different: install an addon, add a debrid token, or
 * pick a different title.
 */
@Composable
private fun SourceColumn(
    sources: SourceState,
    focusedIndex: Int?,
    onPicked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val ranked = (sources.result as? SourceResult.Found)?.ranked.orEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex != null && ranked.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, ranked.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .padding(vertical = dimens.spacing, horizontal = dimens.spacingSmall)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(colors.background.copy(alpha = PANEL_ALPHA))
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(dimens.cornerRadius),
            )
            .padding(dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        val cached = ranked.count { it.cached == CacheStatus.CACHED }
        Text(
            text = when {
                sources.searching -> "SEARCHING…"
                ranked.isEmpty() -> "SOURCES"
                else -> "SOURCES · $cached READY"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )

        val message = when {
            sources.resolveError != null -> sources.resolveError
            sources.resolving -> "Opening…"
            sources.searching && ranked.isEmpty() -> "Looking for something to play."

            else -> when (sources.result) {
                null -> "Rest on a title to see what can play it."
                is SourceResult.NoProviders ->
                    "No source installed. Settings → Library → Films and shows."

                is SourceResult.NoImdbId ->
                    "This title has no IMDb id, so it cannot be searched."

                is SourceResult.Empty -> "Nothing found for this one."
                is SourceResult.Found -> null
            }
        }

        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (sources.resolveError != null) {
                    colors.error
                } else {
                    colors.onSurfaceVariant
                },
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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

/**
 * One candidate.
 *
 * Cache status leads because it is the difference between playing now and
 * waiting, and the release name is kept verbatim underneath because people read
 * these — a group, a repack tag or an audio track listed there is often exactly
 * why one source is chosen over a better-ranked one.
 */
@Composable
private fun SourceRow(source: StreamSource, focused: Boolean, onClick: () -> Unit) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered
    val instant = source.cached == CacheStatus.CACHED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = lit, cornerRadius = ThorTheme.dimens.cornerRadiusSmall)
            .clip(ThorTheme.shapes.small)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = source.quality.summary.ifBlank { "Unknown quality" },
                style = MaterialTheme.typography.labelMedium,
                color = if (lit) colors.cursor else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (instant) "INSTANT" else "",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = source.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant,
    )
}

private const val PANEL_WEIGHT = 0.52f
private const val PANEL_ALPHA = 0.82f
private const val LOGO_HEIGHT = 56
private const val CAST_SHOWN = 6
private const val SIMILAR_SHOWN = 5
