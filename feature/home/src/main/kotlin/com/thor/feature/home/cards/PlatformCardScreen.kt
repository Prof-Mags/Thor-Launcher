package com.thor.feature.home.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.icon.PlatformIcons

/**
 * The Home section as a flow of systems, one filling the panel at a time.
 *
 * The alternative to the grid; see [com.thor.core.model.HomeLayout]. It replaces
 * the *top level* and nothing below it — opening a system hands straight over to
 * the grid, showing that platform's folder, so every behaviour inside a system is
 * the one the user already knows.
 *
 * Why one at a time rather than a list: this panel is driven by a d-pad from
 * across a room, and the artwork an icon pack ships is a wide banner meant to be
 * seen. A list of them is a list of thumbnails — the same compromise the grid was
 * already making, at a different size. Showing one whole is the only layout that
 * spends the art, and the cost of it is that reaching a distant system takes
 * presses, which is what the wrap in [stepCard] and the position line below are
 * for.
 *
 * Not scrollable and not lazy. The flow is one card; the ones either side are not
 * composed at all, which is what keeps a twenty-five system library from
 * decoding twenty-five backdrops to show one.
 */
@Composable
fun PlatformCardScreen(
    cards: List<PlatformCard>,
    focusedIndex: Int,
    /** Which way the last step went, so the card slides in from the right side. */
    stepDirection: Int,
    onOpen: (PlatformCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion

    if (cards.isEmpty()) {
        EmptyFlow(modifier = modifier)
        return
    }

    val index = focusedIndex.coerceIn(0, cards.lastIndex)
    val card = cards[index]

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                /*
                 * Enters from the side the press came from.
                 *
                 * Direction is passed in rather than compared from the indices,
                 * because the wrap makes the comparison lie: stepping right from
                 * the last card to the first is a decrease, and animating it as a
                 * leftward move contradicts the button that was pressed.
                 */
                val from = if (stepDirection >= 0) 1 else -1
                (
                    slideInHorizontally(tween(motion.panelMillis)) { width -> from * width / SLIDE_DIVISOR } +
                        fadeIn(tween(motion.panelMillis))
                    ) togetherWith (
                    slideOutHorizontally(tween(motion.panelMillis)) { width -> -from * width / SLIDE_DIVISOR } +
                        fadeOut(tween(motion.panelMillis))
                    )
            },
            label = "platform-card",
            modifier = Modifier.fillMaxSize(),
        ) { animatedIndex ->
            val shown = cards.getOrNull(animatedIndex) ?: card
            CardFace(
                card = shown,
                onOpen = { onOpen(shown) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        PositionStrip(
            index = index,
            count = cards.size,
            accent = Color(card.platform.accentArgb),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dimens.spacing),
        )
    }
}

/**
 * One system, filling the panel.
 *
 * Three layers: the backdrop, a scrim, and the words. The scrim is not optional
 * and not a style choice — the backdrop is a user-supplied image of unknown
 * brightness, and white text over an unknown image is a coin toss. It is drawn
 * from the theme's own background colour rather than from black so a light theme
 * does not acquire a dark band it never asked for.
 */
@Composable
private fun CardFace(
    card: PlatformCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val accent = Color(card.platform.accentArgb)
    val artwork = card.platform.artwork

    Box(modifier = modifier.clickable(onClick = onOpen)) {
        /*
         * The largest image this system has, and a colour if it has none.
         *
         * Hero first because it is the one shaped for this — a wide banner meant
         * to fill a panel. A game's own backdrop second, so a system the pack did
         * not cover still shows something from itself. The accent last, which is
         * never missing and is what every system falls back to.
         */
        val backdrop = artwork.heroUri ?: card.previewUri
        if (backdrop != null) {
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = BARE_TOP_ALPHA),
                                colors.background,
                            ),
                        ),
                    ),
            )
        }

        // Bottom-weighted, because that is where the words are. A scrim spread
        // evenly would dim artwork it does not need to dim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        SCRIM_STOPS to Color.Transparent,
                        1f to colors.background.copy(alpha = SCRIM_ALPHA),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = dimens.spacingLarge,
                    end = dimens.spacingLarge,
                    bottom = dimens.spacingHuge,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Nameplate(card = card)

            /*
             * What the machine is, before what the user has done with it.
             *
             * The only line on the card that says the same thing on an empty
             * library as on a full one, which is why it sits directly under the
             * name rather than among the counts.
             */
            formatPlatformIdentity(
                manufacturer = card.platform.manufacturer,
                releaseYear = card.platform.releaseYear,
                shortName = card.platform.shortName,
                name = card.platform.name,
            ).takeIf(String::isNotBlank)?.let { identity ->
                Text(
                    text = identity,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (card.recentArtwork.isNotEmpty()) {
                CoverStrip(covers = card.recentArtwork, accent = accent)
            }

            /*
             * The counts, in one line, each dropped when it has nothing to say.
             *
             * Order is what the system *has*, then what has been done with it,
             * then what is left — a sentence that reads the same way whichever
             * parts survive. Nothing is printed as a zero: a card must never say
             * "0h played" about a system that is simply new, because that reads
             * as a system the user abandoned rather than one they have not
             * started.
             */
            val stats = listOfNotNull(
                formatGameCount(card.gameCount),
                card.favouriteCount.takeIf { it > 0 }?.let { "$it favourite" + if (it == 1) "" else "s" },
                formatPlayTime(card.totalPlayMillis),
                // Only once something *has* been played. On an untouched system
                // every game is unplayed, so the number would just restate the
                // count beside it in different words.
                card.unplayedCount.takeIf { it > 0 && card.hasBeenPlayed }?.let { "$it unplayed" },
            ).joinToString(SEPARATOR)

            Text(
                text = stats,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            /*
             * Read once per card rather than held in the model.
             *
             * "2 days ago" is a fact about the clock as much as about the game, so
             * folding it into [PlatformCard] would freeze it at the moment the
             * library was loaded and leave a card that had been open for an hour
             * quietly lying. [formatLastPlayed] takes the time so it can be
             * tested at its boundaries; only this call site reads it.
             */
            formatLastPlayed(card.lastPlayedEpochMs, System.currentTimeMillis())?.let { last ->
                Text(
                    text = last,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A few covers from inside the system.
 *
 * The one thing a count cannot say: "142 games" reads identically for a shelf of
 * favourites and a shelf of things never opened. Most recently played first, so
 * the first cover is the same game as the backdrop behind it.
 *
 * Not focusable and not individually pressable. A is "open this system", and a
 * strip whose covers could be pressed would make the card two targets that look
 * like one — the flow has a single action and the covers are illustration.
 */
@Composable
private fun CoverStrip(covers: List<String>, accent: Color) {
    val colors = ThorTheme.colors

    Row(horizontalArrangement = Arrangement.spacedBy(COVER_GAP.dp)) {
        covers.forEach { cover ->
            ArtworkImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(COVER_WIDTH.dp)
                    .height(COVER_HEIGHT.dp)
                    .clip(RoundedCornerShape(COVER_RADIUS.dp))
                    .background(colors.surface)
                    .border(1.dp, accent.copy(alpha = COVER_BORDER_ALPHA), RoundedCornerShape(COVER_RADIUS.dp)),
            )
        }
    }
}

/**
 * The system's name, drawn if the pack drew it and typeset if it did not.
 *
 * A wordmark is the one piece of pack artwork with no equivalent anywhere in the
 * launcher — the grid has never had room for it — and it is also the piece most
 * likely to be missing, because packs ship it least reliably. So both branches
 * have to be good, not just the one with the image: the fallback is the full name
 * at display size rather than the short name a dense cell would use, because there
 * is room here and "Nintendo 64" is what the system is called.
 */
@Composable
private fun Nameplate(card: PlatformCard) {
    val colors = ThorTheme.colors
    val logo = card.platform.artwork.logoUri

    if (logo != null) {
        ArtworkImage(
            model = logo,
            contentDescription = card.platform.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            modifier = Modifier
                .fillMaxWidth(LOGO_WIDTH_FRACTION)
                .height(LOGO_HEIGHT.dp),
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        /*
         * The bundled icon beside the name, under the same rule it follows on the
         * grid: shown unless a pack or a hand-picked image owns this system, and
         * suppressed by the user's own switch. Without it a system with no
         * wordmark and no hero is a line of text on a wash of colour.
         */
        PlatformIcons.preferredOverEnabled(card.platform.artwork, card.platform.id)?.let { icon ->
            ArtworkImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(NAMEPLATE_ICON.dp)
                    .padding(end = 10.dp),
            )
        }
        Text(
            text = card.platform.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Where in the flow this card is.
 *
 * A flow has no visible edges, so without this there is nothing to say how far
 * along it the user is or how much is left — which is the one thing a list gives
 * away for free and a flow does not. Dots up to a point, then a count, because
 * twenty-five dots on this panel are a dotted line rather than a position.
 */
@Composable
private fun PositionStrip(
    index: Int,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    if (count > MAX_DOTS) {
        Text(
            text = "${index + 1} of $count",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { position ->
            val here = position == index
            Box(
                modifier = Modifier
                    .height(DOT_SIZE.dp)
                    // The one you are on is a capsule rather than a larger dot:
                    // width reads as position at a glance, size reads as noise.
                    .width(if (here) DOT_ACTIVE_WIDTH.dp else DOT_SIZE.dp)
                    .clip(CircleShape)
                    .background(if (here) accent else colors.onSurfaceVariant.copy(alpha = DOT_ALPHA)),
            )
        }
    }
}

/**
 * Nothing to flow through yet.
 *
 * Says which of the two reasons it is — no systems rather than a failure — and
 * what fills it, for the same reason `EmptySection` does: a blank panel is
 * indistinguishable from a broken one.
 */
@Composable
private fun EmptyFlow(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier.fillMaxSize().padding(dimens.spacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No systems yet",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Scan a ROM directory and each system found gets a card here. " +
                "Switch Home back to the grid in Settings to arrange apps instead.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = dimens.spacingSmall),
        )
    }
}

/** A card slides a third of the panel, not the whole of it: a full-width slide at
 *  this size reads as a page turn rather than as a step along a row. */
private const val SLIDE_DIVISOR = 3

/** Where the scrim starts, as a fraction down the panel. */
private const val SCRIM_STOPS = 0.35f
private const val SCRIM_ALPHA = 0.92f

/** How strongly the accent shows on a system with no artwork at all. */
private const val BARE_TOP_ALPHA = 0.55f

private const val COVER_WIDTH = 44
private const val COVER_HEIGHT = 62
private const val COVER_GAP = 6
private const val COVER_RADIUS = 5
private const val COVER_BORDER_ALPHA = 0.35f

private const val LOGO_WIDTH_FRACTION = 0.62f
private const val LOGO_HEIGHT = 56
private const val NAMEPLATE_ICON = 44

/** Past this many systems the dots stop being a position and become a texture. */
private const val MAX_DOTS = 12
private const val DOT_SIZE = 6
private const val DOT_ACTIVE_WIDTH = 20
private const val DOT_GAP = 5
private const val DOT_ALPHA = 0.4f
