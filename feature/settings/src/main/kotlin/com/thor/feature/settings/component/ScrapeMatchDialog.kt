package com.thor.feature.settings.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ArtworkImage
import com.thor.data.metadata.MetadataCandidate
import com.thor.data.sync.PendingMatch

/**
 * Asks which game a file is, mid-scrape, and answers itself if nobody does.
 *
 * Raised only where there is a decision — more than one provider came back with
 * something different — so it is not the interruption per game that "ask every
 * time" sounds like.
 *
 * The countdown is the important part of the design. A dialog that closes on its
 * own without warning reads as a bug; one that visibly runs down reads as an
 * offer, and it is what makes this safe to leave running unattended. Whatever
 * the scrape would have picked on its own is what it picks when the bar empties,
 * so ignoring this entirely costs nothing at all.
 */
@Composable
fun ScrapeMatchDialog(
    pending: PendingMatch?,
    onChoose: (MetadataCandidate) -> Unit,
    onUseAutomatic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending == null) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    /*
     * Driven from the frame clock against the deadline the manager set.
     *
     * Not a countdown this composable owns: the timeout is the scrape's, and a
     * second timer here would drift from it — showing a bar with time left on a
     * dialog that had already been answered, or the reverse.
     */
    var remaining by remember(pending.entryId) { mutableFloatStateOf(1f) }
    LaunchedEffect(pending.entryId) {
        val started = System.currentTimeMillis()
        val span = (pending.deadlineEpochMs - started).coerceAtLeast(1L).toFloat()
        while (true) {
            withFrameMillis { }
            val left = (pending.deadlineEpochMs - System.currentTimeMillis()) / span
            remaining = left.coerceIn(0f, 1f)
            if (remaining <= 0f) break
        }
    }
    val bar by animateFloatAsState(targetValue = remaining, label = "matchCountdown")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Dismissing takes the automatic answer, which is the same thing
            // waiting would have done — so a stray press cannot cost anything.
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
                    text = "Which game is this?",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = pending.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingSmall, bottom = dimens.spacingSmall)
                        .height(COUNTDOWN_HEIGHT.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bar)
                            .fillMaxHeight()
                            .clip(ThorTheme.shapes.pill)
                            .background(colors.cursor),
                    )
                }

                Column(
                    modifier = Modifier
                        .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    pending.candidates.forEachIndexed { index, candidate ->
                        CandidateRow(
                            candidate = candidate,
                            // The first is what the scrape would take anyway, so
                            // it wears the highlight: the countdown and the
                            // marked row then say the same thing.
                            leading = index == 0,
                            onClick = { onChoose(candidate) },
                        )
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
            .thorCursor(focused = leading, shape = shape)
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
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
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

private const val CARD_WIDTH = 400
private const val CONTENT_MAX_HEIGHT = 220
private const val COUNTDOWN_HEIGHT = 4

/** Roughly box-art proportions, so a cover is recognisable at row height. */
private const val COVER_WIDTH = 34
private const val COVER_HEIGHT = 46
