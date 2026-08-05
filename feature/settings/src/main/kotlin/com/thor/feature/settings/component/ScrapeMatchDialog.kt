package com.thor.feature.settings.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ArtworkImage
import com.thor.data.metadata.MetadataCandidate
import com.thor.data.sync.ArtworkOption
import com.thor.data.sync.PendingMatch

/**
 * Asks which game a file is, and then which of its covers to keep.
 *
 * Two questions in sequence rather than one, because the answers come from
 * different places: the provider that identified the game correctly is routinely
 * not the one with the best art for it. The second is skipped where every
 * provider offered the same picture, which is most games.
 *
 * It waits rather than answering itself. A countdown was tried and taken out —
 * three seconds is not long enough to read four titles and compare their covers,
 * so it answered for the user more often than it let them answer. A scrape left
 * on a prompt is stopped, and stopped is recoverable; a scrape that guessed
 * while somebody was still reading is not. "Keep the best guess" is always on
 * the card, and Back does the same thing.
 */
@Composable
fun ScrapeMatchDialog(
    pending: PendingMatch?,
    focusedIndex: Int,
    onChooseGame: (MetadataCandidate) -> Unit,
    onChooseArtwork: (String) -> Unit,
    onUseAutomatic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending == null) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val choosingArtwork = pending.artwork.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Dismissing takes the automatic answer, which is what the scrape
            // would have done unasked — so a stray press cannot cost anything.
            .clickable(onClick = onUseAutomatic),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            modifier = Modifier
                .width(CARD_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = if (choosingArtwork) "Which cover?" else "Which game is this?",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = pending.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (choosingArtwork) {
                        pending.artwork.forEachIndexed { index, option ->
                            ArtworkRow(
                                option = option,
                                focused = index == focusedIndex,
                                onClick = { onChooseArtwork(option.url) },
                            )
                        }
                    } else {
                        pending.candidates.forEachIndexed { index, candidate ->
                            CandidateRow(
                                candidate = candidate,
                                focused = index == focusedIndex,
                                // The first is what the scrape would take
                                // unasked, so it is named as such rather than
                                // merely being at the top.
                                leading = index == 0,
                                onClick = { onChooseGame(candidate) },
                            )
                        }
                    }
                }

                Text(
                    text = "Keep the best guess",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    modifier = Modifier
                        .padding(top = dimens.spacingSmall)
                        .clip(ThorTheme.shapes.small)
                        .clickable(onClick = onUseAutomatic)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: MetadataCandidate,
    focused: Boolean,
    leading: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .thorCursor(focused = focused, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = COVER_WIDTH.dp, height = COVER_HEIGHT.dp)
                .clip(shape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(
                model = candidate.artwork.boxArt ?: candidate.artwork.cellImage,
                contentDescription = candidate.matchedTitle,
                fallbackText = candidate.matchedTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = candidate.matchedTitle,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                fontWeight = if (leading) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    candidate.providerId.uppercase(),
                    candidate.metadata.releaseYear?.toString(),
                    candidate.metadata.developer,
                    "best guess".takeIf { leading },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (leading) colors.cursor else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "${(candidate.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * One cover on offer.
 *
 * Drawn much larger than the thumbnail in the match list, because this row *is*
 * the decision — the whole question is which of these pictures ends up on the
 * grid, and it cannot be answered from a stamp.
 */
@Composable
private fun ArtworkRow(
    option: ArtworkOption,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .thorCursor(focused = focused, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = ART_WIDTH.dp, height = ART_HEIGHT.dp)
                .clip(shape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(
                model = option.url,
                contentDescription = option.providerId,
                // Fitted, not cropped: cover art comes at wildly different
                // shapes and a crop would hide exactly the difference being
                // chosen between.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = option.providerId.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
    }
}

private const val CARD_WIDTH = 420
private const val CONTENT_MAX_HEIGHT = 260

/** Roughly box-art proportions, so a cover is recognisable at row height. */
private const val COVER_WIDTH = 34
private const val COVER_HEIGHT = 46

/** The artwork step's own, which has to be big enough to actually judge. */
private const val ART_WIDTH = 64
private const val ART_HEIGHT = 88
