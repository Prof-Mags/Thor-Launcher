package com.thor.feature.topscreen.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.thor.core.common.text.splitSentences
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.Achievement
import com.thor.core.model.AchievementSummary
import com.thor.core.model.GameEntry
import com.thor.core.model.completionSeconds
import com.thor.core.model.Platform
import com.thor.core.model.PlatformGlyph
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.topscreen.component.PlatformLineIcon
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Game information in the same translucent, artwork-led language as platforms. */
@Composable
fun GameDetailPanel(
    game: GameEntry,
    platform: Platform?,
    selectedScreenshot: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val accent = platform?.let { Color(it.accentArgb) }
        ?: game.metadata.artwork.dominantArgb?.let(::Color)
        ?: colors.cursor
    val density = LocalDensity.current
    val profileDensity = remember(density.density, density.fontScale) {
        Density(
            density = density.density * GAME_PROFILE_SCALE,
            fontScale = density.fontScale,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDensity provides profileDensity) {
            GameProfileCard(
                game = game,
                platform = platform,
                selectedScreenshot = selectedScreenshot,
                accent = accent,
                modifier = Modifier.infoPanelBounds(),
            )
        }
    }
}

@Composable
private fun GameProfileCard(
    game: GameEntry,
    platform: Platform?,
    selectedScreenshot: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val artwork = game.metadata.artwork
    val screenshots = artwork.cappedScreenshots
    val selectedMedia = screenshots.getOrNull(
        selectedScreenshot.coerceIn(0, (screenshots.size - 1).coerceAtLeast(0)),
    ) ?: artwork.hero

    BoxWithConstraints(
        modifier = modifier
            .infoPanelSurface(platformAccentBrush(accent, alpha = .22f), shape),
    ) {
        // Read out here rather than at the call site below: inside the Column the
        // implicit receiver is a ColumnScope and the card's own constraints are no
        // longer reachable without naming them.
        val cardHeight = maxHeight
        val contentWidth = maxWidth - (GAME_HORIZONTAL_PADDING * 2).dp

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
                // Keeps the text at the width it was written for, so a blended
                // panel's fade happens in space nothing was going to occupy.
                .padding(infoPanelContentInset())
                .padding(
                    start = GAME_HORIZONTAL_PADDING.dp,
                    end = GAME_HORIZONTAL_PADDING.dp,
                    top = GAME_TOP_PADDING.dp,
                    bottom = GAME_BOTTOM_RESERVE.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(GAME_SECTION_GAP.dp),
        ) {
            GameMasthead(game = game, platform = platform, accent = accent)
            GameDivider()
            GameSectionTitle("YOUR ACTIVITY")
            GameActivityStats(game = game, accent = accent)

            /*
             * How far through it you are, where anyone knows how long it is.
             *
             * Under the play time rather than beside it, because it is the same
             * number given a scale: on its own "9h" says nothing about whether
             * that is a third of the way in or twice as long as it should have
             * taken.
             *
             * Absent entirely when the length is unknown, which on a retro
             * library is most games. A bar sitting at zero is a claim that you
             * have not started something, and it should only be made when it is
             * true rather than when the figure is missing.
             */
            GameCompletion(game = game, accent = accent)

            /*
             * The description, below the figures and above the media.
             *
             * It used to sit inside the masthead, immediately under the title,
             * where it pushed the statistics down the panel and competed with the
             * cover for the first thing read. Here it is what it actually is: the
             * paragraph you go on to once the numbers have been taken in.
             */
            val description = game.metadata.description?.takeIf(String::isNotBlank)
            if (description != null) {
                GameDivider()
                GameSectionTitle("DESCRIPTION")
                GameDescription(
                    text = description,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            /*
             * Achievements, between the description and the media.
             *
             * Above the media because it is a fact about this player rather than
             * about the game, and the two things on this card that are about them
             * — the play statistics and this — belong on the same side of the
             * pictures. Absent entirely when the game has no set: a row reading
             * "0 of 0" is worse than silence.
             */
            game.metadata.achievements?.takeIf { it.total > 0 }?.let { summary ->
                GameDivider()
                GameSectionTitle(if (summary.isHardcore) "ACHIEVEMENTS · HARDCORE" else "ACHIEVEMENTS")
                GameAchievements(summary = summary, accent = accent)
            }

            if (selectedMedia != null) {
                GameSectionTitle("MEDIA")
                val mediaHeight = (contentWidth / GAME_MEDIA_ASPECT)
                    .coerceAtMost(cardHeight * GAME_MEDIA_MAX_FRACTION)
                /*
                 * Sixteen by nine, but never more than a quarter of the panel.
                 *
                 * The ratio alone was the reason long synopses were being cut. The
                 * card is wide, so a sixteen-by-nine block of its full width came
                 * out over a third of the panel's height, and the description — the
                 * one thing on here that appears nowhere else — was left with
                 * whatever survived the masthead, the statistics and that. A
                 * ceiling settles it without going back to sharing the leftover,
                 * which starved the strip instead: the shape is kept until it
                 * reaches the cap and the image is fitted rather than cropped, so a
                 * shorter slot letterboxes it and every dp saved goes to the text.
                 */
                GameMedia(
                    model = selectedMedia,
                    selected = selectedScreenshot,
                    count = screenshots.size,
                    accent = accent,
                    /*
                     * Sixteen by nine exactly, whichever dimension is binding.
                     *
                     * Both are given rather than a width and a capped height.
                     * That was the fault: the frame took the card's full width
                     * and a height that the ceiling could cut, so once the
                     * ceiling bound it was no longer sixteen by nine — and the
                     * image inside is cropped to the frame, so what the ceiling
                     * took came off the top and bottom of the picture. It read
                     * as artwork slightly clipped, because it was.
                     *
                     * Deriving the width back from the capped height keeps the
                     * ratio true and spends the ceiling on the frame's width,
                     * where nothing is lost.
                     */
                    modifier = Modifier.size(
                        width = mediaHeight * GAME_MEDIA_ASPECT,
                        height = mediaHeight,
                    ),
                )
            }
        }

        GameControllerHints(
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = GAME_HORIZONTAL_PADDING.dp,
                    bottom = GAME_HINT_BOTTOM_PADDING.dp,
                ),
        )
    }
}

@Composable
private fun GameMasthead(game: GameEntry, platform: Platform?, accent: Color) {
    val colors = ThorTheme.colors
    val artwork = game.metadata.artwork
    val cover = artwork.boxArt ?: artwork.icon
    val coverAspectRatio = if (artwork.boxArt != null) 2f / 3f else 1f
    val creditLine = listOfNotNull(
        game.metadata.developer?.takeIf(String::isNotBlank),
        game.metadata.publisher?.takeIf(String::isNotBlank),
    ).distinct().joinToString("  •  ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GAME_HEADER_GAP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(GAME_COVER_WIDTH.dp)
                .aspectRatio(coverAspectRatio)
                .shadow(7.dp, ThorTheme.shapes.small, clip = false)
                .clip(ThorTheme.shapes.small)
                .background(colors.surfaceHighest.copy(alpha = .42f))
                .border(
                    1.dp,
                    platformAccentBrush(accent, alpha = .34f),
                    ThorTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                ArtworkImage(
                    model = cover,
                    contentDescription = game.title,
                    fallbackText = game.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = game.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (artwork.logo != null) {
                ArtworkImage(
                    model = artwork.logo,
                    contentDescription = game.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth(.96f)
                        .heightIn(min = 48.dp, max = 82.dp),
                )
            }
            Text(
                text = game.title,
                style = if (artwork.logo != null) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (creditLine.isNotEmpty()) {
                Text(
                    text = creditLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                platform?.shortName?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, accent)
                }
                game.metadata.releaseYear?.let {
                    GameBadge(it.toString(), colors.onSurfaceVariant)
                }
                game.metadata.genres.firstOrNull()?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, colors.primary)
                }
                game.metadata.region?.takeIf(String::isNotBlank)?.let {
                    GameBadge(it, colors.secondary)
                }
            }

            if (game.isFavorite || game.metadata.rating != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (game.isFavorite) GameBadge("FAVOURITE", accent)
                    game.metadata.rating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformLineIcon(
                                glyph = PlatformGlyph.FAVOURITE,
                                tint = accent,
                                modifier = Modifier.size(23.dp),
                            )
                            Text(
                                text = "$rating / 100",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun GameBadge(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(tint.copy(alpha = .06f))
            .border(
                1.dp,
                platformAccentBrush(tint, alpha = .17f),
                ThorTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun GameActivityStats(game: GameEntry, accent: Color) {
    val stats = listOf(
        GameStatModel(
            glyph = PlatformGlyph.PLAYTIME,
            value = formatDuration(game.stats.totalPlayMillis),
            label = "Play time",
        ),
        GameStatModel(
            glyph = PlatformGlyph.CLOCK,
            value = game.stats.lastPlayedEpochMs?.let(::formatRelative) ?: "Never",
            label = "Last played",
        ),
        GameStatModel(
            glyph = PlatformGlyph.PLAY,
            value = game.stats.launchCount.toString(),
            label = "Times played",
        ),
        GameStatModel(
            glyph = PlatformGlyph.CLASSICS,
            value = game.stats.firstPlayedEpochMs?.let(::formatRelative) ?: "Never",
            label = "First played",
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(GAME_STAT_GAP.dp)) {
        stats.chunked(2).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GAME_STAT_GAP.dp),
            ) {
                rowStats.forEach { stat ->
                    GameStat(
                        stat = stat,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class GameStatModel(
    val glyph: PlatformGlyph,
    val value: String,
    val label: String,
)

@Composable
private fun GameStat(
    stat: GameStatModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier
            .height(GAME_STAT_CARD_HEIGHT.dp)
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = .32f))
            .border(
                1.dp,
                platformAccentBrush(accent, alpha = .15f),
                ThorTheme.shapes.small,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(GAME_STAT_ICON_SHELL.dp)
                .clip(ThorTheme.shapes.pill)
                .background(accent.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) {
            PlatformLineIcon(
                glyph = stat.glyph,
                tint = accent,
                modifier = Modifier.size(GAME_STAT_ICON_SIZE.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stat.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Play time against how long the game takes.
 *
 * The figure comes from submitted play-throughs rather than from a guess, so it
 * is the ordinary route through — not the rushed one and not the completionist
 * one. Someone past the end of the bar has not broken anything: they took longer
 * than most, or they are still playing, and both are ordinary.
 */
@Composable
private fun GameCompletion(game: GameEntry, accent: Color) {
    val colors = ThorTheme.colors

    /*
     * Shown as soon as the length is known, played or not.
     *
     * The shared helper hides an unplayed game, which is right on a shelf of
     * covers where an empty bar under every one of them says nothing. On this
     * panel it is wrong: the card already says "Never played" two lines above,
     * so an empty bar is not a surprising claim — it is the scale that sentence
     * is missing, and "0m of 25h" is a useful thing to know before starting.
     */
    val totalSeconds = game.metadata.completionSeconds?.takeIf { it > 0 } ?: return
    val progress = (game.stats.totalPlayMillis / 1000f / totalSeconds).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(COMPLETION_GAP.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${formatDuration(game.stats.totalPlayMillis)} of " +
                    formatDuration(totalSeconds * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                // Rounded, because a percentage to the decimal implies the
                // underlying figure is that precise and it is an average of
                // however many people bothered to submit one.
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (progress >= 1f) accent else colors.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(COMPLETION_BAR_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.onSurfaceVariant.copy(alpha = COMPLETION_TRACK_ALPHA)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(ThorTheme.shapes.pill)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun GameFacts(game: GameEntry, accent: Color) {
    val metadata = game.metadata
    val facts = listOf(
        GameFactModel(
            "Developer",
            metadata.developer?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.PERFORMANCE,
        ),
        GameFactModel(
            "Publisher",
            metadata.publisher?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.GAME_LIBRARY,
        ),
        GameFactModel(
            "Players",
            metadata.players?.takeIf(String::isNotBlank) ?: "Not listed",
            PlatformGlyph.MULTIPLAYER,
        ),
        GameFactModel(
            "Rating",
            metadata.rating?.let { "$it / 100" } ?: "Not rated",
            PlatformGlyph.FAVOURITE,
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        facts.chunked(2).forEach { rowFacts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowFacts.forEach { fact ->
                    GameFact(
                        fact = fact,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class GameFactModel(
    val label: String,
    val value: String,
    val glyph: PlatformGlyph,
)

@Composable
private fun GameFact(fact: GameFactModel, accent: Color, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformLineIcon(
            glyph = fact.glyph,
            tint = accent,
            modifier = Modifier.size(GAME_FACT_ICON_SIZE.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fact.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = .72f),
                maxLines = 1,
            )
            Text(
                text = fact.value,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GameMedia(
    model: String,
    selected: Int,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Box(
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = .28f))
            .border(
                1.dp,
                platformAccentBrush(accent, alpha = .16f),
                ThorTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        /*
         * Cropped to the frame, not fitted inside it.
         *
         * Fitting means the frame shows whatever shape arrived — and providers
         * hold artwork in half a dozen ratios, so the strip became a row of
         * mismatched pictures with bars around them. Cropping makes the frame
         * the constant: every image is a true sixteen by nine, whatever it was.
         * Safe because these are landscape media by the time they get here, so
         * the crop takes a little from the sides rather than the subject.
         */
        ArtworkImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (count > 1) {
            Text(
                text = "${selected.coerceIn(0, count - 1) + 1} / $count",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Color.Black.copy(alpha = .48f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * How far through a game's achievements the player is.
 *
 * A bar, a count and the badges of the last few earned. The badges are the part
 * worth the space: a number says how many are left and a row of icons says what
 * the game actually asked of you, which is the thing somebody deciding what to
 * play next is looking at.
 */
@Composable
private fun GameAchievements(summary: AchievementSummary, accent: Color) {
    val colors = ThorTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(ACHIEVEMENT_GAP.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${summary.earned} of ${summary.total}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (summary.isMastered) {
                    "Mastered"
                } else {
                    "${summary.earnedPoints} of ${summary.totalPoints} points"
                },
                style = MaterialTheme.typography.labelSmall,
                // Mastery is the one state worth colouring: it is the end of the
                // set, and it is what the whole progress bar was counting toward.
                color = if (summary.isMastered) accent else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ACHIEVEMENT_BAR_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.onSurfaceVariant.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.completionFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(ThorTheme.shapes.pill)
                    .background(accent),
            )
        }

        /*
         * Earned first, then a few still to come.
         *
         * Both, rather than only what has been earned, because on a game the
         * user has not started the earned list is empty — and a set of forty
         * achievements rendered as a bare zero under an empty strip tells
         * somebody deciding what to play nothing at all. The unearned ones are
         * held back to a silhouette so the row still reads at a glance as how
         * far through this is.
         */
        val earned = summary.recentlyEarned.take(ACHIEVEMENT_BADGES)
        val upcoming = summary.upcoming.take(ACHIEVEMENT_BADGES - earned.size)

        if (earned.isNotEmpty() || upcoming.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ACHIEVEMENT_BADGE_GAP.dp)) {
                earned.forEach { achievement -> AchievementBadge(achievement, accent, true) }
                upcoming.forEach { achievement -> AchievementBadge(achievement, accent, false) }
            }
        }

        /*
         * One line naming what is next, or what was last.
         *
         * The badges are pictures somebody else drew and carry no words, so on
         * their own they say a number of things have been done without saying
         * what any of them were. Naming one is what turns the strip from
         * decoration into the answer to "how much is left in this".
         */
        val highlight = summary.upcoming.firstOrNull() ?: summary.recentlyEarned.firstOrNull()
        if (highlight != null) {
            Text(
                text = if (summary.upcoming.isNotEmpty()) {
                    "Next: ${highlight.title} · ${highlight.points} pts"
                } else {
                    "Latest: ${highlight.title}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One badge, lit or not.
 *
 * An unearned badge is the same picture at a fraction of its opacity rather than
 * a placeholder, because RetroAchievements' art is often the only clue to what
 * an achievement asks for — a silhouette of the real thing says more than a
 * question mark, and keeps the row reading as one set.
 */
@Composable
private fun AchievementBadge(achievement: Achievement, accent: Color, earned: Boolean) {
    ArtworkImage(
        model = achievement.badgeUri,
        contentDescription = achievement.title,
        fallbackText = achievement.title,
        fallbackTint = accent,
        modifier = Modifier
            .size(ACHIEVEMENT_BADGE.dp)
            .clip(ThorTheme.shapes.small)
            .alpha(if (earned) 1f else ACHIEVEMENT_LOCKED_ALPHA),
    )
}

@Composable
private fun GameControllerHints(accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameControllerHint("A", "Launch", accent)
        GameControllerHint("Y", "More Options", accent)
        GameControllerHint("B", "Back", accent)
    }
}

@Composable
private fun GameControllerHint(button: String, label: String, accent: Color) {
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
private fun GameSectionTitle(text: String) {
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
private fun GameDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ThorTheme.colors.outline.copy(alpha = .15f)),
    )
}

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

private const val GAME_PROFILE_SCALE = .70f
private const val GAME_PANEL_WIDTH = .395f
private const val GAME_PANEL_OUTER_PADDING = 14
private const val GAME_HORIZONTAL_PADDING = 20
private const val GAME_TOP_PADDING = 20
private const val GAME_BOTTOM_RESERVE = 46
private const val GAME_HINT_BOTTOM_PADDING = 13
private const val GAME_SECTION_GAP = 5
private const val GAME_HEADER_GAP = 16
/**
 * The cover on the masthead.
 *
 * Down from 128. At two-to-three that block was 192dp tall before anything else
 * was drawn, and it is the largest single claim on a panel whose description
 * kept running out of room — the cover is also on the grid cell the user just
 * came from, so it is the one element here that repeats.
 */
private const val GAME_COVER_WIDTH = 104
/**
 * The synopsis, wrapped to as many lines as it takes.
 *
 * Capping the line count — at three, or at whatever the leftover height
 * divides into — truncates the sentence, and a synopsis that stops mid-clause
 * tells you less than no synopsis at all. So the text wraps freely and the
 * *size* gives way instead: it is measured against the space actually available
 * and stepped down until the whole thing fits, which on a panel this size is
 * one or two points for a long description and nothing at all for a short one.
 */
@Composable
private fun GameDescription(text: String, color: Color, modifier: Modifier = Modifier) {
    val base = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val available = constraints
        val fitted = remember(text, available, base) {
            fitDescription(
                text = text,
                available = available.maxHeight,
                measureHeight = { candidate, body ->
                    measurer.measure(
                        text = AnnotatedString(body),
                        style = base.scaledBy(candidate),
                        constraints = Constraints(maxWidth = available.maxWidth),
                    ).size.height
                },
            )
        }

        Text(
            text = fitted.text,
            style = base.scaledBy(fitted.scale),
            color = color,
            // No line cap: the fitted size and the sentence count are what keep
            // this inside the panel, and a cap on top of them would truncate
            // text that had already been made to fit.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What to draw, and how big: the outcome of [fitDescription]. */
internal data class FittedDescription(val text: String, val scale: Float)

/**
 * Makes a synopsis fit, by shrinking it and then by shortening it.
 *
 * Shrinking alone was the whole mechanism, and it has a floor — below
 * [MIN_DESCRIPTION_SCALE] the text is smaller than anything else on the panel.
 * A description long enough to hit that floor and still overflow was then
 * ellipsised, which is where "…the princess is kidnapped by Bows" came from: the
 * one outcome the fitter exists to prevent, reached by the fitter running out of
 * room.
 *
 * So size gives way first, because a slightly smaller synopsis is a whole
 * synopsis. Only when that is exhausted do whole sentences come off the end, and
 * they come off *whole* — what remains is shorter than what the scraper wrote
 * and reads as though it were written that way, which is the difference between
 * a summary and a truncation.
 */
internal fun fitDescription(
    text: String,
    available: Int,
    /** Height of [String] rendered at a given scale. */
    measureHeight: (Float, String) -> Int,
): FittedDescription {
    if (available <= 0 || available == Constraints.Infinity) return FittedDescription(text, 1f)

    val scale = fittedTextScale(available) { measureHeight(it, text) }
    if (measureHeight(scale, text) <= available) return FittedDescription(text, scale)

    // Still over at the smallest size worth reading, so drop sentences from the
    // end — the last one first, since a synopsis front-loads what it is about.
    val sentences = text.splitSentences()
    for (count in sentences.size - 1 downTo 1) {
        val candidate = sentences.take(count).joinToString(" ")
        if (measureHeight(MIN_DESCRIPTION_SCALE, candidate) <= available) {
            return FittedDescription(candidate, MIN_DESCRIPTION_SCALE)
        }
    }

    // Not even one sentence fits. Show the first and let it ellipsise: there is
    // nothing left to give up, and an empty panel says less than a cut one.
    return FittedDescription(sentences.firstOrNull() ?: text, MIN_DESCRIPTION_SCALE)
}

/**
 * Largest scale at which the text still fits [available].
 *
 * Steps down rather than binary-searching: the range is narrow and the whole
 * search is at most a handful of measurements, done once per game, and a linear
 * walk returns the *largest* fitting size rather than merely a fitting one.
 *
 * An unbounded height — a parent that does not constrain — means nothing needs
 * shrinking, so the text is left at full size.
 */
internal fun fittedTextScale(available: Int, measureHeight: (Float) -> Int): Float {
    if (available <= 0 || available == Constraints.Infinity) return 1f
    var scale = 1f
    while (scale > MIN_DESCRIPTION_SCALE) {
        if (measureHeight(scale) <= available) return scale
        scale -= DESCRIPTION_SCALE_STEP
    }
    return MIN_DESCRIPTION_SCALE
}

/**
 * The floor on shrinking.
 *
 * Below this the text is smaller than anything else on the panel and stops
 * looking like a deliberate choice; a synopsis long enough to need it is better
 * ellipsised than rendered at a size nobody reads.
 *
 * It was 0.95, which gave the fitter a five per cent range — so "shrink until it
 * fits" was in practice "ellipsise", and the mechanism that exists to stop a
 * synopsis being cut was doing nothing at all. Fifteen per cent is enough range
 * to be worth having and still lands well above the smallest type on the panel;
 * the rest of the room comes from capping the media strip, which is the honest
 * place to find it.
 */
internal const val MIN_DESCRIPTION_SCALE = 0.85f
private const val DESCRIPTION_SCALE_STEP = 0.03f

/**
 * How the leftover splits between the synopsis and the screenshot.
 *
 * Weighted toward the text: the screenshot is also drawn full-bleed behind the
 * whole panel, so the strip is a picker showing which of them is back there,
 * while the description appears nowhere else.
 */
/**
 * The strip is a fixed shape, so it cannot be clipped.
 *
 * Sharing the leftover with the description meant the artwork took whatever the
 * text did not want, which on a long synopsis was not enough to draw it in. A
 * fixed ratio settles that: the strip is always the same size, and the
 * description absorbs the slack.
 *
 * Sixteen by nine rather than the twenty-one by nine it was. The images are now
 * artwork rather than banners — covers, key art, the occasional portrait scan —
 * and they are fitted, not cropped, so an ultra-wide frame showed a cover as a
 * sliver with empty panel either side. This is the widest shape that still has
 * somewhere to put an image that is taller than it is wide.
 */
private const val GAME_MEDIA_ASPECT = 16f / 9f

/**
 * The ceiling on it, as a share of the panel's height.
 *
 * Set above what a full-width sixteen-by-nine frame actually needs, so in the
 * ordinary case it does not bind at all and the strip runs the width of the
 * card. That is the point of the number: at a quarter it bound on every card,
 * and since the frame keeps its ratio the width came off instead — a correct
 * little picture with a band of empty card beside it.
 *
 * It still exists for the genuinely short panel, where the strip narrows rather
 * than cropping. Losing width is recoverable by looking at the backdrop, which
 * is the same image full-bleed; losing the top and bottom of the picture is not.
 */
private const val GAME_MEDIA_MAX_FRACTION = 0.42f

/** Scales both the size and its leading, so the text keeps its proportions. */
private fun TextStyle.scaledBy(scale: Float): TextStyle = if (scale == 1f) {
    this
} else {
    copy(
        fontSize = fontSize * scale,
        lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight,
    )
}
private const val GAME_STAT_CARD_HEIGHT = 44
private const val GAME_STAT_GAP = 7
private const val GAME_STAT_ICON_SHELL = 28
private const val GAME_STAT_ICON_SIZE = 19
private const val GAME_FACT_ICON_SIZE = 27

/** The progress bar, its badges, and the space between them. */
private const val ACHIEVEMENT_GAP = 6
private const val ACHIEVEMENT_BAR_HEIGHT = 6
private const val ACHIEVEMENT_BADGE = 30
private const val ACHIEVEMENT_BADGE_GAP = 5

/** How many earned badges fit across the card without wrapping. */
private const val ACHIEVEMENT_BADGES = 5

/** How far an unearned badge is held back; still legible, plainly not done. */
private const val ACHIEVEMENT_LOCKED_ALPHA = 0.3f


/** The completion bar, and how far its track is held back from the fill. */
private const val COMPLETION_GAP = 5
private const val COMPLETION_BAR_HEIGHT = 5
private const val COMPLETION_TRACK_ALPHA = 0.18f
