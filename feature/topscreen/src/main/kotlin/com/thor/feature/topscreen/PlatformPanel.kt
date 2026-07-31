package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.thor.core.model.PlatformFlagships
import com.thor.core.ui.component.ArtworkImage

/**
 * The information panel for a platform's folder.
 *
 * A platform folder is not really a folder — it is a *system*, and it happens to
 * be drawn as a folder because that is how the grid holds a hundred games in one
 * cell. Showing it the way any other folder is shown said almost nothing: a name,
 * a count of items, and a blank field where a description would go.
 *
 * Laid out as the same left-hand column a game gets, for the same reason: the two
 * panels alternate as the user moves across the grid, and a shared column means
 * the eye stays where it was instead of hunting for the title again. It also gives
 * the description somewhere to go — the previous full-width version let the
 * paragraph run the whole panel and collide with the artwork behind it.
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

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(PANEL_WEIGHT)
                .fillMaxHeight()
                .padding(dimens.spacing)
                .clip(RoundedCornerShape(dimens.cornerRadius))
                // Translucent, with a hairline edge, so the backdrop still reads
                // through without the text losing its footing on a bright image.
                .background(colors.background.copy(alpha = PANEL_ALPHA))
                .border(
                    width = 1.dp,
                    color = colors.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(dimens.cornerRadius),
                )
                .padding(dimens.spacing)
                // The description is the longest thing here and its length varies
                // by system; scrolling is what keeps a wordy one from pushing the
                // shelf off the bottom of a short panel.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            /*
             * The wordmark decorates the title; it does not replace it.
             *
             * It used to stand in for the name entirely, on the reasoning that a
             * logo *is* the name — which holds right up until the image does not
             * arrive. A pack removed, a URI whose permission lapsed, a slow load:
             * any of those left the panel with no title at all, describing a
             * system it never named. Drawn above the name instead, so the heading
             * is always there and the logo is a bonus when it loads.
             */
            if (logoUri != null) {
                ArtworkImage(
                    model = logoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LOGO_HEIGHT.dp)
                        .widthIn(max = LOGO_MAX_WIDTH.dp),
                )
            }

            Text(
                text = platform.name.ifBlank { folderTitle },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

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
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    // Wrapped to the column rather than clipped to a line count:
                    // the panel scrolls, so there is no reason to cut the last
                    // sentence off mid-word.
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            /*
             * What this collection actually is, rather than only what the system
             * was.
             *
             * The packaged description is the same for everyone; these lines are
             * the only part of the panel that says anything about *this* library —
             * how much of it has been touched, how long it has held someone's
             * attention, and what they keep going back to.
             */
            val played = games.count { it.stats.hasBeenPlayed }
            val totalMillis = games.sumOf { it.stats.totalPlayMillis }
            val favourites = games.count { it.isFavorite }

            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
                PlatformStat("Games", games.size.toString())
                if (played > 0) PlatformStat("Played", "$played")
                if (favourites > 0) PlatformStat("Favourites", "$favourites")
                if (totalMillis > 0L) PlatformStat("Time", totalMillis.asPlaytime())
            }

            val mostPlayed = games
                .filter { it.stats.totalPlayMillis > 0L }
                .maxByOrNull { it.stats.totalPlayMillis }

            if (mostPlayed != null) {
                DetailLine("MOST PLAYED", mostPlayed.title)
            }

            val newest = games.maxByOrNull { it.stats.lastPlayedEpochMs ?: 0L }
                ?.takeIf { it.stats.hasBeenPlayed }
            if (newest != null && newest.id != mostPlayed?.id) {
                DetailLine("LAST PLAYED", newest.title)
            }

            /*
             * The practical half: what the scanner accepts for this system, and
             * what will actually open a file.
             *
             * Both are questions a user asks of a platform folder and nothing
             * else in the launcher answers — "why has nothing appeared here"
             * usually turns out to be one of these two.
             */
            if (platform.romExtensions.isNotEmpty()) {
                DetailLine(
                    "FILE TYPES",
                    platform.romExtensions.sorted().joinToString(" ") { ".$it" },
                )
            }

            DetailLine(
                "EMULATOR",
                platform.defaultEmulatorPackage ?: "None set — games here will not launch",
            )

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

        // Deliberately empty: the backdrop shows through here, as on a game.
        Spacer(modifier = Modifier.weight(1f - PANEL_WEIGHT))
    }
}

/** A labelled fact, in the same shape the game panel uses. */
@Composable
private fun DetailLine(label: String, value: String) {
    val colors = ThorTheme.colors
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "12h 40m", or "45m" under an hour. Zero never reaches here. */
private fun Long.asPlaytime(): String {
    val minutes = this / 60_000L
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
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
 * the default state of a fresh install.
 *
 * Chosen from [PlatformFlagships] rather than from the library's own statistics.
 * Ranking by play count reads well until you remember that a fresh library has no
 * play counts, at which point every game ties and the tiebreak — alphabetical
 * order — decides. That is how the backdrop ended up being whatever game happened
 * to sort first, which is exactly what it looked like.
 *
 * Returns `null` when the user owns none of the platform's flagships, and the
 * caller falls back to the wallpaper. No picture is a better answer than an
 * arbitrary one: the point of the image is to say *which system this is*, and a
 * random screenshot from the library says nothing at all.
 */
fun representativeImageFor(platformId: String, children: List<GridEntry>): String? {
    val games = children.filterIsInstance<GameEntry>()
    if (games.isEmpty()) return null

    /*
     * Flagships first, then whatever the library actually has.
     *
     * The earlier version stopped at the flagship list and returned null when
     * none matched, which for most libraries is most platforms — so after a
     * scrape the folders had artwork available and showed none of it. Falling
     * through is right as long as the fallback is *ordered* rather than
     * arbitrary: play count, then play time, then title. That is a defensible
     * "most representative" answer and, crucially, the same answer every time,
     * which is what the original complaint was really about.
     */
    val ranked = games.sortedWith(
        compareBy<GameEntry> { PlatformFlagships.rankOf(platformId, it.title) ?: FLAGSHIP_MISS }
            .thenByDescending { it.stats.launchCount }
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

/** Sorts every non-flagship below every flagship, without excluding it. */
private const val FLAGSHIP_MISS = Int.MAX_VALUE

private const val PANEL_WEIGHT = 0.40f
private const val PANEL_ALPHA = 0.82f
private const val LOGO_HEIGHT = 56
private const val LOGO_MAX_WIDTH = 320
private const val RECENT_COUNT = 6
private const val SHELF_HEIGHT = 110
