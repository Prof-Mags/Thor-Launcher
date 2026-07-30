package com.thor.feature.topscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage

/**
 * The information panel for a platform's folder.
 *
 * A platform folder is not really a folder — it is a *system*, and it happens to
 * be drawn as a folder because that is how the grid holds a hundred games in one
 * cell. Showing it the way any other folder is shown said almost nothing: a name,
 * a count of items, and a blank field where a description would go.
 *
 * So it gets a panel of its own, laid out like a game's: a title, the maker and
 * year underneath, a paragraph about the system, and a shelf of what has actually
 * been played on it. Nothing here needs a scrape or a network — the text is
 * packaged with the launcher and the artwork comes from games already in the
 * library.
 */
@Composable
fun PlatformDetailPanel(
    platform: Platform,
    folderTitle: String,
    children: List<GridEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val games = children.filterIsInstance<GameEntry>()
    val logoUri = platform.artwork.logoUri

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimens.spacingLarge)
            .widthIn(max = PANEL_MAX_WIDTH.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        // The pack's wordmark stands in for the title when there is one, exactly
        // as it does on the folder banner — a logo is the name.
        if (logoUri != null) {
            ArtworkImage(
                model = logoUri,
                contentDescription = platform.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(LOGO_HEIGHT.dp)
                    .widthIn(max = LOGO_MAX_WIDTH.dp),
            )
        } else {
            Text(
                text = platform.name.ifBlank { folderTitle },
                style = MaterialTheme.typography.displaySmall,
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // "Nintendo · 1990", the line a game's panel gives to its developer.
        if (platform.subtitle.isNotBlank()) {
            Text(
                text = platform.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
            )
        }

        if (platform.description.isNotBlank()) {
            Text(
                text = platform.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
            PlatformStat("Games", games.size.toString())
            val played = games.count { it.stats.hasBeenPlayed }
            if (played > 0) PlatformStat("Played", played.toString())
        }

        val recent = games
            .filter { it.stats.hasBeenPlayed }
            .sortedByDescending { it.stats.lastPlayedEpochMs ?: 0L }
            .take(RECENT_COUNT)

        if (recent.isNotEmpty()) {
            Text(
                text = "RECENTLY PLAYED",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                items(recent, key = GameEntry::id) { game ->
                    ArtworkImage(
                        model = game.metadata.artwork.cellImage,
                        contentDescription = game.title,
                        fallbackText = game.title,
                        modifier = Modifier
                            .height(SHELF_HEIGHT.dp)
                            .aspectRatio(3f / 4f)
                            .clip(ThorTheme.shapes.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformStat(label: String, value: String) {
    val colors = ThorTheme.colors
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
    }
}

/**
 * A game whose artwork can stand for the whole platform.
 *
 * Used as the panel's backdrop when no icon pack has supplied a hero, which is
 * the default state of a fresh install — and a far better default than the same
 * wallpaper behind every system.
 *
 * "Most popular" is answered from what is already known rather than from a
 * popularity list nobody ships: the most-played game with a wide image, falling
 * back to the most-played with any image at all. Ordering is fully determined —
 * play counts, then play time, then title — because a backdrop that picked a
 * different game on each recomposition would flicker between them.
 */
fun representativeImageFor(children: List<GridEntry>): String? {
    val games = children.filterIsInstance<GameEntry>()
    if (games.isEmpty()) return null

    val ranked = games.sortedWith(
        compareByDescending<GameEntry> { it.stats.launchCount }
            .thenByDescending { it.stats.totalPlayMillis }
            .thenBy { it.sortTitle },
    )

    // A screenshot or background fills a panel; box art does not, and stretching
    // a 3:4 cover across a widescreen backdrop looks like a mistake.
    return ranked.firstNotNullOfOrNull { game ->
        val artwork = game.metadata.artwork
        artwork.backgroundImage ?: artwork.cappedScreenshots.firstOrNull()
    }
}

private const val PANEL_MAX_WIDTH = 520
private const val LOGO_HEIGHT = 56
private const val LOGO_MAX_WIDTH = 320
private const val DESCRIPTION_LINES = 5
private const val RECENT_COUNT = 6
private const val SHELF_HEIGHT = 110
