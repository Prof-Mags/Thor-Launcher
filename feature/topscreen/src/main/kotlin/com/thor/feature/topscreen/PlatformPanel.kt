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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFlagships
import com.thor.core.ui.component.ArtworkImage
import com.thor.data.scanner.EmulatorRegistry

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
        PanelCard(
            modifier = Modifier
                .weight(PANEL_WEIGHT)
                .fillMaxHeight()
                .padding(dimens.spacing),
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
            /*
             * The masthead: logo, name and subtitle as one centred block.
             *
             * Grouped in a column of its own with a tight internal gutter,
             * because these three are one thing said three ways and were
             * previously spaced as widely from each other as from unrelated
             * facts — which left the heading looking like three separate lines
             * that happened to be near each other.
             */
            Section {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
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

                /*
                 * Smaller when a logo is above it, larger when it stands alone.
                 *
                 * With a wordmark present the name is a caption to it, and
                 * setting both at display size stacked a tall image on a tall
                 * heading — which pushed the title far enough down the panel to
                 * look like it had been left there by mistake. Without a logo the
                 * name *is* the masthead and keeps its full size.
                 */
                Text(
                    text = platform.name.ifBlank { folderTitle },
                    style = if (logoUri != null) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (platform.subtitle.isNotBlank()) {
                    Text(
                        text = platform.subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

                if (platform.description.isNotBlank()) {
                    Text(
                        text = platform.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        // Centred with the masthead above it, so the top of the
                        // card is one symmetrical block rather than a centred
                        // heading sitting on a left-ragged paragraph.
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

            /*
             * Four cells of equal width, always all four.
             *
             * They used to be spaced from the left and appear only when non-zero,
             * so the row was ragged and a different shape on every platform — one
             * system showed one figure, the next showed four, and the block
             * shifted as the cursor moved along the grid. Equal weights with a
             * dash for nothing keeps the row symmetrical and keeps each figure in
             * the same place whichever folder is highlighted.
             */
            Section(label = "THIS LIBRARY") {
            Row(modifier = Modifier.fillMaxWidth()) {
                PlatformStat("Games", games.size.toString(), Modifier.weight(1f))
                PlatformStat("Played", played.orDash(), Modifier.weight(1f))
                PlatformStat("Favourites", favourites.orDash(), Modifier.weight(1f))
                PlatformStat(
                    label = "Time",
                    value = if (totalMillis > 0L) totalMillis.asPlaytime() else UNKNOWN,
                    modifier = Modifier.weight(1f),
                )
            }
            }

            val mostPlayed = games
                .filter { it.stats.totalPlayMillis > 0L }
                .maxByOrNull { it.stats.totalPlayMillis }

            val newest = games.maxByOrNull { it.stats.lastPlayedEpochMs ?: 0L }
                ?.takeIf { it.stats.hasBeenPlayed }

            /*
             * The same two-column table the game panel uses, with the same
             * always-present slots.
             *
             * These were a run of full-width lines whose label and value were
             * spaced apart as far as two unrelated facts, so nothing grouped and
             * the column read as a list of loose sentences. Paired into an even
             * grid, the two panels now have the same shape — which matters
             * because the cursor alternates between them.
             */
            val emulator = platform.defaultEmulatorPackage?.let { rememberEmulatorName(it) }

            val facts = listOf(
                "Most played" to mostPlayed?.title,
                "Last played" to newest?.title,
                "File types" to platform.romExtensions
                    .takeIf(Set<String>::isNotEmpty)
                    ?.sorted()
                    ?.joinToString(" ") { ".$it" },
                // Named rather than left blank: "why has nothing launched here"
                // is nearly always this, and a dash would not say so.
                "Emulator" to (emulator ?: "None set — games will not launch"),
            )

            Section(label = "DETAILS") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                facts.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { (label, value) ->
                            Fact(label = label, value = value, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            }

            val recent = games
                .filter { it.stats.hasBeenPlayed }
                .sortedByDescending { it.stats.lastPlayedEpochMs ?: 0L }
                .take(RECENT_COUNT)

            if (recent.isNotEmpty()) {
                Section(label = "RECENTLY PLAYED") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    ) {
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

        // Deliberately empty: the backdrop shows through here, as on a game.
        Spacer(modifier = Modifier.weight(1f - PANEL_WEIGHT))
    }
}

/**
 * What to call an emulator, rather than what it is installed as.
 *
 * "com.miHoYo.Yuzu.something" is an implementation detail of an app the user
 * knows by a name — the same name it shows in their launcher and its own title
 * bar. THOR's registry knows the ones it ships support for; anything else is
 * asked of the system, which is where the answer for a sideloaded build lives.
 * The package is the last resort, not the first answer.
 */
@Composable
private fun rememberEmulatorName(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        EmulatorRegistry.specFor(packageName)?.displayName
            ?: runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrNull()?.takeIf(String::isNotBlank)
            ?: packageName
    }
}

/*
 * `DetailLine` used to live here.
 *
 * It emitted two `Text`s with no wrapper, so the enclosing column's arrangement
 * put exactly as much space between a label and its own value as between two
 * unrelated facts — nothing grouped, and the panel read as a list of loose lines.
 * The game panel's `Fact` already solved this and is shared now, which also means
 * the two panels cannot drift apart again.
 */

/** "12h 40m", or "45m" under an hour. Zero never reaches here. */
private fun Long.asPlaytime(): String {
    val minutes = this / 60_000L
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

/**
 * One figure in the statistics row.
 *
 * Centred inside its own equal share of the width, which is what makes the row
 * symmetrical however wide the values happen to be — a left-aligned "1" beside a
 * left-aligned "12h 40m" left the row visibly lopsided.
 */
@Composable
private fun PlatformStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            // Value above label, dimmed: the number is what is being looked for
            // and the caption only says which number it is.
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** Nothing shows as a dash, so a cell never collapses and the row stays even. */
private fun Int.orDash(): String = if (this > 0) toString() else UNKNOWN

private const val UNKNOWN = "—"

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
/** Tightened so the name sits close under the wordmark rather than adrift. */
private const val LOGO_HEIGHT = 44
private const val LOGO_MAX_WIDTH = 320
private const val RECENT_COUNT = 6
private const val SHELF_HEIGHT = 110
