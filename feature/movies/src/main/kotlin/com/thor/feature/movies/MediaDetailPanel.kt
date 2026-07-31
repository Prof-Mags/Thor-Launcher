package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.thor.core.ui.component.ArtworkImage
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

                SourceSummary(sources)
            }

            // Deliberately empty: the backdrop shows through here.
            Spacer(modifier = Modifier.weight(1f - PANEL_WEIGHT))
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
 * What the source search found, in one line.
 *
 * States are distinguished rather than collapsed into "no sources", because the
 * remedies are completely different: add an indexer, add a debrid token, or pick
 * a different title.
 */
@Composable
private fun SourceSummary(sources: SourceState) {
    val colors = ThorTheme.colors
    val text = when {
        sources.searching -> "Searching for sources…"
        sources.resolveError != null -> sources.resolveError
        sources.resolving -> "Opening…"

        else -> when (val result = sources.result) {
            null -> null
            is SourceResult.NoProviders ->
                "No torrent indexers configured. Add one in Settings → Movies."

            is SourceResult.NoImdbId -> "No IMDb id for this title, so it cannot be searched."
            is SourceResult.Empty -> "No sources found."
            is SourceResult.Found -> {
                val cached = result.ranked.count { it.cached == CacheStatus.CACHED }
                "${result.ranked.size} sources · $cached ready to stream"
            }
        }
    } ?: return

    Label("SOURCES")
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (sources.resolveError != null) colors.error else colors.onSurfaceVariant,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ThorTheme.colors.onSurfaceVariant,
    )
}

private const val PANEL_WEIGHT = 0.42f
private const val PANEL_ALPHA = 0.82f
private const val LOGO_HEIGHT = 56
private const val CAST_SHOWN = 6
