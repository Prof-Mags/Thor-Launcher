package com.thor.feature.movies

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaItem
import com.thor.core.ui.component.ArtworkImage

/**
 * One title, filling the television.
 *
 * The page Confirm opens: the film's own artwork behind it, what it is down the
 * left, and the choice to be made down the right — episodes for a series, then
 * the ranked sources for whichever one is picked. It replaces a two-column panel
 * built for a handheld, where an 86dp poster and a column of `labelSmall` credits
 * were the right size for a screen at arm's length and illegible across a room.
 *
 * The right-hand column is the existing [PlaybackPanel] rather than a second copy
 * of it. Season and episode selection and the source list are where the awkward
 * state lives — a dwell before searching, a ranked list that can arrive empty,
 * pairs that must not drift apart — and a television-shaped duplicate of all that
 * would be a second place for it to go wrong.
 */
@Composable
internal fun MoviesCouchTitlePage(
    detail: DetailState,
    sources: SourceState,
    focusedSource: Int?,
    selectorFocused: Boolean,
    showSeriesSelector: Boolean,
    onSourcePicked: (Int) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val item = detail.item

    Box(modifier = modifier.fillMaxSize()) {
        CouchBackdrop(item = item)

        if (item == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Choose a title to see its story and what can play it.",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            return@Box
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PAGE_INSET.dp, vertical = PAGE_TOP_INSET.dp),
            horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP.dp),
        ) {
            TitleIdentity(
                item = item,
                detail = detail,
                onPlay = onPlay,
                modifier = Modifier.weight(IDENTITY_WEIGHT).fillMaxHeight(),
            )

            GlassSurface(
                modifier = Modifier.weight(1f - IDENTITY_WEIGHT).fillMaxHeight(),
                shape = ThorTheme.shapes.panel,
            ) {
                PlaybackPanel(
                    detail = detail,
                    sources = sources,
                    focusedSource = focusedSource,
                    selectorFocused = selectorFocused,
                    showSeriesSelector = showSeriesSelector,
                    onSourcePicked = onSourcePicked,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeSelected = onEpisodeSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * What the title is, at the size of a room.
 *
 * The story scrolls and everything else does not. A synopsis is the one part of
 * this that has no length limit — some run to a paragraph, some to a page — and
 * letting it push the name and the facts off the top of the column would make
 * the page's own subject the first thing to go.
 */
@Composable
private fun TitleIdentity(
    item: MediaItem,
    detail: DetailState,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val story = rememberScrollState()

    LaunchedEffect(item.id.key) { story.scrollTo(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(IDENTITY_GAP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IDENTITY_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (item.isSeries) "SERIES" else "FEATURE FILM",
                style = MaterialTheme.typography.labelMedium,
                color = colors.cursor,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            if (detail.loading) {
                Text(
                    text = "UPDATING",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Ratings(item.ratings)

        detail.resumeProgress?.takeIf { it.isResumable }?.let { progress ->
            ResumeSummary(progress)
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(story),
            verticalArrangement = Arrangement.spacedBy(IDENTITY_GAP.dp),
        ) {
            if (item.overview.isNotBlank()) {
                SectionLabel("STORY")
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            val director = item.crew.firstOrNull { it.role.equals("Director", ignoreCase = true) }
            if (director != null) {
                SectionLabel("DIRECTOR")
                Text(
                    text = director.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (item.cast.isNotEmpty()) {
                SectionLabel("STARRING")
                Text(
                    text = item.cast.take(CAST_SHOWN).joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ACTION_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouchMediaButton(
                label = if (detail.resumeProgress?.isResumable == true) "Resume" else "Play best",
                hint = "HOLD A",
                icon = Icons.Rounded.PlayArrow,
                primary = true,
                onClick = onPlay,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "B  Back",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private const val PAGE_INSET = 26
private const val PAGE_TOP_INSET = 18
private const val COLUMN_GAP = 20
private const val IDENTITY_WEIGHT = 0.46f
private const val IDENTITY_GAP = 10
private const val LOGO_WIDTH_FRACTION = 0.66f
private const val LOGO_HEIGHT = 74
private const val ACTION_GAP = 12
private const val CAST_SHOWN = 6
