package com.thor.feature.topscreen.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlatformFlagships
import com.thor.core.model.PlatformGlyph
import com.thor.core.model.PlatformHighlight
import com.thor.core.model.PlatformProfile
import com.thor.core.model.PlatformProfiles
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.topscreen.component.PlatformLineIcon

/**
 * A platform is presented as hardware with an identity, not as another folder.
 * The right side remains completely available to the pack hero or flagship art;
 * this compact profile owns the left side and uses only real library statistics.
 */
@Composable
fun PlatformDetailPanel(
    platform: Platform,
    folderTitle: String,
    children: List<GridEntry>,
    onGameSelected: (GameEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = Color(platform.accentArgb)
    val density = LocalDensity.current
    val profileDensity = remember(density.density, density.fontScale) {
        Density(
            density = density.density * PLATFORM_PROFILE_SCALE,
            fontScale = density.fontScale,
        )
    }
    val games = remember(children) { children.filterIsInstance<GameEntry>() }
    val profile = remember(platform.id) { PlatformProfiles.forPlatform(platform) }
    val favourites = remember(games) { games.count(GameEntry::isFavorite) }
    val totalMillis = remember(games) { games.sumOf { it.stats.totalPlayMillis } }
    val continuePlaying = remember(games) {
        games.filter { it.stats.hasBeenPlayed }
            .sortedWith(
                compareByDescending<GameEntry> { it.stats.lastPlayedEpochMs ?: 0L }
                    .thenByDescending { it.stats.totalPlayMillis },
            )
            .take(PLATFORM_GAME_ROW_COUNT)
    }
    val highlights = remember(games, platform.id) {
        games.sortedWith(
            compareBy<GameEntry> {
                PlatformFlagships.rankOf(platform.id, it.title) ?: FLAGSHIP_MISS
            }
                .thenByDescending(GameEntry::isFavorite)
                .thenBy(GameEntry::sortTitle),
        ).take(PLATFORM_GAME_ROW_COUNT)
    }
    val featuredGames = continuePlaying.ifEmpty { highlights }
    val showingActivity = continuePlaying.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDensity provides profileDensity) {
            PlatformProfileCard(
                platform = platform,
                fallbackTitle = folderTitle,
                profile = profile,
                games = games.size,
                favourites = favourites,
                totalMillis = totalMillis,
                featuredGames = featuredGames,
                showingActivity = showingActivity,
                onGameSelected = onGameSelected,
                accent = accent,
                modifier = Modifier.infoPanelBounds(),
            )
        }
    }
}

@Composable
private fun PlatformProfileCard(
    platform: Platform,
    fallbackTitle: String,
    profile: PlatformProfile,
    games: Int,
    favourites: Int,
    totalMillis: Long,
    featuredGames: List<GameEntry>,
    showingActivity: Boolean,
    onGameSelected: (GameEntry) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val outlineBrush = platformAccentBrush(accent, alpha = .22f)

    Box(modifier = modifier.infoPanelSurface(outlineBrush, shape)) {
        /*
         * The card's own top edge, and only the card's.
         *
         * A full-width accent rule on a blended panel would run straight into
         * the fade and stop dead in the middle of the artwork — a hard line
         * across the picture, which is the single thing that style exists to
         * remove.
         */
        if (infoPanelHasEdges()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(platformAccentBrush(accent, alpha = .36f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Keeps the text at the width it was written for; see the game
                // panel, which does the same for the same reason.
                .padding(infoPanelContentInset())
                .padding(
                    start = PROFILE_HORIZONTAL_PADDING.dp,
                    end = PROFILE_HORIZONTAL_PADDING.dp,
                    top = PROFILE_TOP_PADDING.dp,
                    bottom = PROFILE_BOTTOM_RESERVE.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(PROFILE_SECTION_GAP.dp),
        ) {
            PlatformMasthead(
                platform = platform,
                fallbackTitle = fallbackTitle,
                profile = profile,
                accent = accent,
            )

            PlatformDivider()

            PlatformStats(
                games = games,
                favourites = favourites,
                totalMillis = totalMillis,
                accent = accent,
            )

            // Divider and heading inside the null check: a platform with no
            // description was otherwise leaving a rule floating directly above
            // the highlights, separating nothing from nothing.
            val description = platform.description.takeIf(String::isNotBlank)
            if (description != null) {
                PlatformDivider()
                PlatformSectionTitle("DESCRIPTION")
                /*
                 * The one part of this card that gives up its space.
                 *
                 * A Column measures its unweighted children first and its
                 * weighted ones with what is left, so making the description the
                 * weighted one inverts the priority that caused the bug: it used
                 * to take four lines whatever the card could afford, and the
                 * sections below it — the ones the user actually came to read —
                 * were measured against nothing and clipped away. Everything
                 * below is now guaranteed its height and the description
                 * ellipsizes into whatever remains.
                 *
                 * It also absorbs the slack, which is why the spacer that used to
                 * push the game rows to the bottom is gone: with the description
                 * filling its slot, the sections under it are already there.
                 */
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = PLATFORM_DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            PlatformSectionTitle("PLATFORM HIGHLIGHTS")
            PlatformHighlights(profile.highlights, accent)

            if (featuredGames.isNotEmpty()) {
                // Only when there is no description to hold the slack; two
                // weighted children would split it and shrink the description for
                // no reason.
                if (description == null) Spacer(modifier = Modifier.weight(1f))
                PlatformSectionTitle(
                    if (showingActivity) "CONTINUE PLAYING" else "LIBRARY HIGHLIGHTS",
                )
                PlatformGameRows(
                    games = featuredGames,
                    accent = accent,
                    showPlayActivity = showingActivity,
                    onGameSelected = onGameSelected,
                )
            }
        }

        PlatformControllerHints(
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = PROFILE_HORIZONTAL_PADDING.dp,
                    bottom = PLATFORM_HINT_BOTTOM_PADDING.dp,
                ),
        )
    }
}

@Composable
private fun PlatformMasthead(
    platform: Platform,
    fallbackTitle: String,
    profile: PlatformProfile,
    accent: Color,
) {
    val colors = ThorTheme.colors
    val outlineBrush = platformAccentBrush(accent, alpha = .42f)
    val icon = platform.artwork.iconUri
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PLATFORM_HEADER_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PLATFORM_ICON_SIZE.dp)
                .shadow(7.dp, ThorTheme.shapes.small, clip = false)
                .clip(ThorTheme.shapes.small)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = .12f),
                            colors.surfaceHighest.copy(alpha = .46f),
                        ),
                    ),
                )
                .border(2.dp, outlineBrush, ThorTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                ArtworkImage(
                    model = icon,
                    contentDescription = platform.name,
                    fallbackText = platform.shortName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PlatformLineIcon(
                    glyph = profile.systemGlyph,
                    tint = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(PLATFORM_ICON_PADDING.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = platform.name.ifBlank { fallbackTitle },
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profile.category,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = .72f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                platform.manufacturer.takeIf(String::isNotBlank)?.let {
                    PlatformMetadataLabel(it, accent, filled = false)
                }
                platform.releaseYear?.let {
                    PlatformMetadataLabel(it.toString(), colors.onSurfaceVariant, filled = false)
                }
                platform.shortName.takeIf(String::isNotBlank)?.let {
                    PlatformMetadataLabel(it, accent, filled = true)
                }
            }
        }
    }
}

@Composable
private fun PlatformMetadataLabel(text: String, tint: Color, filled: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) contrastingOnAccent(tint) else tint,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(if (filled) tint.copy(alpha = .62f) else tint.copy(alpha = .06f))
            .border(
                1.dp,
                platformAccentBrush(tint, alpha = .18f),
                ThorTheme.shapes.small,
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlatformStats(
    games: Int,
    favourites: Int,
    totalMillis: Long,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformStat(
            glyph = PlatformGlyph.GAME_LIBRARY,
            value = games.toString(),
            label = "Games",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        PlatformStatDivider()
        PlatformStat(
            glyph = PlatformGlyph.FAVOURITE,
            value = favourites.takeIf { it > 0 }?.toString() ?: "None",
            label = "Favourite",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        PlatformStatDivider()
        PlatformStat(
            glyph = PlatformGlyph.PLAYTIME,
            value = totalMillis.takeIf { it > 0L }?.let(::formatDuration) ?: "0m",
            label = "Play time",
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlatformStat(
    glyph: PlatformGlyph,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier.padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformLineIcon(
            glyph = glyph,
            tint = accent,
            modifier = Modifier.size(STAT_ICON_SIZE.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlatformStatDivider() {
    Box(
        modifier = Modifier
            .height(STAT_DIVIDER_HEIGHT.dp)
            .width(1.dp)
            .background(ThorTheme.colors.outline.copy(alpha = .14f)),
    )
}

@Composable
private fun PlatformHighlights(highlights: List<PlatformHighlight>, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HIGHLIGHT_ROW_GAP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HIGHLIGHT_COLUMN_GAP.dp),
        ) {
            highlights.take(2).forEach { highlight ->
                PlatformHighlightItem(
                    highlight = highlight,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
            }
            if (highlights.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
        highlights.getOrNull(2)?.let { highlight ->
            Row(modifier = Modifier.fillMaxWidth()) {
                PlatformHighlightItem(
                    highlight = highlight,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlatformHighlightItem(
    highlight: PlatformHighlight,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PlatformLineIcon(
            glyph = highlight.glyph,
            tint = accent,
            modifier = Modifier.size(HIGHLIGHT_ICON_SIZE.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlight.description,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlatformGameRows(
    games: List<GameEntry>,
    accent: Color,
    showPlayActivity: Boolean,
    onGameSelected: (GameEntry) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        games.forEach { game ->
            PlatformGameRow(
                game = game,
                accent = accent,
                showPlayActivity = showPlayActivity,
                onSelected = { onGameSelected(game) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlatformGameRow(
    game: GameEntry,
    accent: Color,
    showPlayActivity: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val outlineBrush = platformAccentBrush(accent, alpha = .16f)
    val artwork = game.metadata.artwork
    val thumbnail = artwork.cappedScreenshots.firstOrNull()
        ?: artwork.hero
        ?: artwork.boxArt
    val supportingText = if (showPlayActivity) {
        listOfNotNull(
            game.stats.lastPlayedEpochMs?.let(::formatRelative),
            game.stats.totalPlayMillis.takeIf { it > 0L }?.let(::formatDuration),
        ).joinToString("  ·  ")
    } else {
        listOfNotNull(
            game.metadata.releaseYear?.toString(),
            game.metadata.genres.firstOrNull()?.takeIf(String::isNotBlank),
        ).joinToString("  ·  ")
    }

    Row(
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = .32f))
            .border(1.dp, outlineBrush, ThorTheme.shapes.small)
            .clickable(onClick = onSelected)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            model = thumbnail,
            contentDescription = game.title,
            fallbackText = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(GAME_THUMBNAIL_WIDTH.dp)
                .aspectRatio(16f / 9f)
                .clip(ThorTheme.shapes.small)
                .background(colors.surface),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText.isNotEmpty()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(GAME_ACTION_SIZE.dp)
                .padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlatformLineIcon(
                glyph = PlatformGlyph.PLAY,
                tint = accent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Mirrors the controller commands routed to a focused top-screen platform. */
@Composable
private fun PlatformControllerHints(accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformControllerHint("A", "Open", accent)
        PlatformControllerHint("Y", "More Options", accent)
        PlatformControllerHint("B", "Back", accent)
    }
}

@Composable
private fun PlatformControllerHint(button: String, label: String, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(23.dp)
                .clip(ThorTheme.shapes.pill)
                .background(ThorTheme.colors.surface.copy(alpha = .58f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = button,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ThorTheme.colors.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlatformSectionTitle(text: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = .76f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outline.copy(alpha = .15f)),
        )
    }
}

@Composable
private fun PlatformDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ThorTheme.colors.outline.copy(alpha = .15f)),
    )
}

private fun contrastingOnAccent(accent: Color): Color {
    val luminance = .299f * accent.red + .587f * accent.green + .114f * accent.blue
    return if (luminance > .58f) Color.Black.copy(alpha = .86f) else Color.White
}

/** A shared accent spectrum for the frame, cards, actions and focus treatment. */
@Composable
internal fun platformAccentBrush(accent: Color, alpha: Float = 1f): Brush {
    val colors = ThorTheme.colors
    return remember(accent, colors.primary, colors.secondary, alpha) {
        val softSecondary = lerp(accent, colors.secondary, .28f)
        val softPrimary = lerp(accent, colors.primary, .22f)
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = alpha),
                softSecondary.copy(alpha = alpha * .92f),
                softPrimary.copy(alpha = alpha * .88f),
                accent.copy(alpha = alpha),
            ),
        )
    }
}

/** Chooses stable platform backdrop art from flagship and play-history ranking. */
fun representativeImageFor(platformId: String, children: List<GridEntry>): String? {
    val games = children.filterIsInstance<GameEntry>()
    if (games.isEmpty()) return null

    val ranked = games.sortedWith(
        compareBy<GameEntry> { PlatformFlagships.rankOf(platformId, it.title) ?: FLAGSHIP_MISS }
            .thenByDescending { it.stats.launchCount }
            .thenByDescending { it.stats.totalPlayMillis }
            .thenBy { it.sortTitle },
    )
    return ranked.firstNotNullOfOrNull { game ->
        val artwork = game.metadata.artwork
        artwork.backgroundImage ?: artwork.cappedScreenshots.firstOrNull()
    }
}

private const val FLAGSHIP_MISS = Int.MAX_VALUE
private const val PLATFORM_PROFILE_SCALE = .82f
private const val PLATFORM_PANEL_WIDTH = .395f
private const val PLATFORM_PANEL_OUTER_PADDING = 14
private const val PROFILE_HORIZONTAL_PADDING = 20
private const val PROFILE_TOP_PADDING = 22
private const val PROFILE_BOTTOM_RESERVE = 46
private const val PLATFORM_HINT_BOTTOM_PADDING = 13
private const val PROFILE_SECTION_GAP = 10
private const val PLATFORM_HEADER_GAP = 16
private const val PLATFORM_ICON_SIZE = 80
private const val PLATFORM_ICON_PADDING = 17
private const val PLATFORM_DESCRIPTION_LINES = 4
private const val STAT_ICON_SIZE = 29
private const val STAT_DIVIDER_HEIGHT = 45
private const val HIGHLIGHT_ICON_SIZE = 34
private const val HIGHLIGHT_ROW_GAP = 8
private const val HIGHLIGHT_COLUMN_GAP = 14
private const val PLATFORM_GAME_ROW_COUNT = 2
private const val GAME_THUMBNAIL_WIDTH = 86
private const val GAME_ACTION_SIZE = 42
