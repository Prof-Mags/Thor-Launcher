package com.thor.feature.topscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AchievementSummary
import com.thor.core.model.GameEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import java.util.concurrent.TimeUnit

/**
 * The information panel for a highlighted game.
 *
 * A single translucent column pinned to the left, with the artwork or preview
 * clip filling the rest of the panel behind it. Every block inside shares the
 * same width and gutter so the column reads as one surface rather than as
 * floating fragments — the previous version scattered its sections across the
 * full panel width and lined none of them up.
 *
 * Every field keeps its slot whether or not the game has a value for it. Omitting
 * the empty ones re-flowed the panel for each title — the same fact appeared in a
 * different place for every game, so nothing could be found by position.
 */
@Composable
fun GameDetailPanel(
    game: GameEntry,
    platform: Platform?,
    /** Index of the screenshot currently shown, for the strip's selection. */
    selectedScreenshot: Int,
    onScreenshotSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val artwork = game.metadata.artwork

    Row(modifier = modifier.fillMaxSize()) {
        /*
         * A column of cards, as the settings screen is.
         *
         * This was one bordered box with hairline rules inside it, which made
         * every section look like a paragraph of the same document — the facts,
         * the achievements and the screenshots all read as one undifferentiated
         * block. Settings had already solved the same problem: a stack of raised
         * cards, each with a small caption above it, so a section is a *thing*
         * rather than a region between two lines.
         *
         * Using the same surface means it also inherits the theme's edge and
         * shadow treatment, so this panel changes with a theme rather than
         * keeping its own hardcoded border.
         */
        PanelCard(
            modifier = Modifier
                .weight(PANEL_WEIGHT)
                .fillMaxHeight()
                .padding(dimens.spacing),
        ) {
            Section {
                Header(game = game, artwork = artwork, colors = colors)
                PlatformRow(game = game, platform = platform)
            }

            game.metadata.description?.takeIf(String::isNotBlank)?.let { description ->
                Section(label = "ABOUT") {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = DESCRIPTION_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Section(label = "DETAILS") {
                FactsGrid(game = game)
            }

            game.metadata.achievements?.let {
                Section(label = "ACHIEVEMENTS") {
                    AchievementBlock(summary = it)
                }
            }

            if (artwork.cappedScreenshots.size > 1) {
                Section(label = "SCREENSHOTS") {
                    ScreenshotStrip(
                        urls = artwork.cappedScreenshots,
                        selected = selectedScreenshot,
                        onSelected = onScreenshotSelected,
                    )
                }
            }
        }

        // Deliberately empty: the artwork or preview clip shows through here.
        Spacer(modifier = Modifier.weight(1f - PANEL_WEIGHT))
    }
}

@Composable
private fun Header(
    game: GameEntry,
    artwork: com.thor.core.model.ArtworkSet,
    colors: com.thor.core.designsystem.theme.ThorColors,
) {
    if (artwork.logo != null) {
        ArtworkImage(
            model = artwork.logo,
            contentDescription = game.title,
            // Fit, not crop: logos range from wide banners to tall crests and a
            // wide one was having its ends sliced off.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LOGO_MIN_HEIGHT.dp, max = LOGO_MAX_HEIGHT.dp),
        )
    } else {
        Text(
            text = game.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One card of the panel, with the caption settings puts above a group of rows.
 *
 * The caption is optional because the first card is the game's name and needs no
 * label — a heading over a title would be saying the same thing twice.
 *
 * Shared with the platform panel, so a folder and a game are built from the same
 * pieces and cannot drift apart as either is edited.
 */
@Composable
internal fun Section(
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                // Dimmed, as a settings group's caption is: it names what
                // follows without competing with it.
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        content()
    }
}

/**
 * The card both panels sit inside.
 *
 * One surface holding every section, rather than a card per section. A stack of
 * separate cards gave each group its own edge and shadow, which at five groups
 * read as five unrelated things scattered down the screen — the panel describes
 * a single subject and should look like one object.
 *
 * The gaps between sections do the separating instead, which is enough: a
 * caption and a clear space already say "new group" without an outline round
 * every one of them.
 */
@Composable
internal fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = ThorTheme.dimens

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(dimens.cornerRadius),
        level = SurfaceLevel.RAISED,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacing)
                .verticalScroll(rememberScrollState()),
            // Wider than the gap inside a section, so a new group is visibly a
            // new group even without a rule between them.
            verticalArrangement = Arrangement.spacedBy(dimens.spacingLarge),
            content = content,
        )
    }
}

/** Hairline rule separating blocks within the panel. */
@Composable
internal fun Divider() {
    val colors = ThorTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // height, not size: `size` fixes both axes, so it overrode the
            // fillMaxWidth above it and left the rule zero pixels wide.
            .height(1.dp)
            .background(colors.outline.copy(alpha = 0.30f)),
    )
}

@Composable
private fun PlatformRow(game: GameEntry, platform: Platform?) {
    val colors = ThorTheme.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        platform?.let { Badge(text = it.shortName, tint = Color(it.accentArgb)) }
        game.metadata.releaseYear?.let { Badge(text = it.toString(), tint = colors.secondary) }
        game.metadata.genres.firstOrNull()?.let { Badge(text = it, tint = colors.primary) }

        game.metadata.rating?.let { rating ->
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Rating",
                    tint = colors.cursor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "$rating",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, tint: Color) {
    val dimens = ThorTheme.dimens
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The facts table.
 *
 * Every slot is always present, in the same order, whether or not the game has a
 * value for it — an unknown field shows a dash. Omitting empty fields meant the
 * panel re-flowed for every game: "Developer" sat top-left for one title and
 * third-right for the next, so nothing could be found by position and the layout
 * looked unstable while moving the cursor along a shelf.
 *
 * Two even columns with a shared gutter, so labels and values line up down the
 * panel instead of each row finding its own width.
 */
@Composable
private fun FactsGrid(game: GameEntry) {
    val metadata = game.metadata
    val stats = game.stats

    // Fixed order, fixed length. Add fields to the end rather than inserting, so
    // a familiar layout does not shuffle.
    val facts = listOf(
        "Developer" to metadata.developer,
        "Publisher" to metadata.publisher,
        "Released" to (metadata.releaseDate ?: metadata.releaseYear?.toString()),
        "Genre" to metadata.genres.firstOrNull(),
        "Rating" to metadata.rating?.let { "$it / 100" },
        "Play time" to formatDuration(stats.totalPlayMillis),
        "Times played" to stats.launchCount.toString(),
        "Last played" to stats.lastPlayedEpochMs?.let(::formatRelative),
        "First played" to stats.firstPlayedEpochMs?.let(::formatRelative),
        "Platform" to game.platformId.uppercase(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { (label, value) ->
                    Fact(label = label, value = value, modifier = Modifier.weight(1f))
                }
                // Keeps a trailing odd fact in the left column rather than
                // letting it stretch across both.
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * One labelled fact.
 *
 * A null value renders as a dash rather than collapsing the slot, which is what
 * keeps every game's panel identical in shape.
 */
@Composable
internal fun Fact(label: String, value: String?, modifier: Modifier = Modifier) {
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
            text = if (known) value.orEmpty() else UNKNOWN_VALUE,
            style = MaterialTheme.typography.bodySmall,
            // Dimmed when unknown, so the eye skips the gaps instead of reading
            // a wall of equally-weighted dashes.
            color = if (known) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AchievementBlock(summary: AchievementSummary) {
    val colors = ThorTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = if (summary.isMastered) colors.cursor else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "${summary.earned} / ${summary.total} · " +
                    "${summary.earnedPoints} pts",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { summary.completionFraction },
            color = colors.cursor,
            trackColor = colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                // Same trap: `size` here made the progress bar zero-width, so
                // achievement completion rendered as nothing at all.
                .height(5.dp)
                .clip(CircleShape),
        )
    }
}

/**
 * Selectable thumbnails of the game's screenshots.
 *
 * Choosing one changes what fills the panel behind, so this is a picker rather
 * than a decorative carousel — which is why the current item carries a visible
 * selection instead of just scrolling past.
 */
@Composable
private fun ScreenshotStrip(
    urls: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SCREENSHOTS",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(urls, key = { _, url -> url }) { index, url ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .width(THUMB_WIDTH.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                colors.cursor
                            } else {
                                colors.outline.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(dimens.cornerRadiusSmall),
                        )
                        .clickable { onSelected(index) },
                ) {
                    ArtworkImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- formatting

internal fun formatDuration(millis: Long): String {
    if (millis <= 0) return "Never"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours >= 1) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatRelative(epochMs: Long): String {
    val delta = System.currentTimeMillis() - epochMs
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    return when {
        days > 365 -> "${days / 365}y ago"
        days > 30 -> "${days / 30}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

/** Share of the panel width given to the information column. */
private const val PANEL_WEIGHT = 0.40f
private const val PANEL_ALPHA = 0.82f
/** Shown where a provider gave us nothing. */
private const val UNKNOWN_VALUE = "—"

private const val DESCRIPTION_LINES = 5
private const val THUMB_WIDTH = 96

/** Logos vary from wide banners to tall crests; both must fit uncropped. */
private const val LOGO_MIN_HEIGHT = 44
private const val LOGO_MAX_HEIGHT = 88
