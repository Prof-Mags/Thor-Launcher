package com.thor.feature.home.dialog

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.component.ArtworkImage
import com.thor.data.metadata.MetadataCandidate

/**
 * State of the "choose the right game" dialog.
 *
 * [candidates] is null while the providers are still being asked, which is a
 * different thing from an empty list — one means "wait", the other means "none
 * of your providers has heard of this", and the two want different sentences.
 */
@Immutable
data class MatchPickerState(
    val visible: Boolean = false,
    val entryId: String? = null,
    val entryTitle: String = "",
    val candidates: List<MetadataCandidate>? = null,
    val focusedIndex: Int = 0,
) {
    val rowCount: Int get() = candidates?.size ?: 0
}

/**
 * Picks which game a scrape should have matched.
 *
 * The automatic path takes the highest-scoring candidate, and for most files it
 * is right. Where it is not, nothing could previously be done about it except
 * editing every field by hand — the scraper had decided, and re-running it made
 * the same decision. This is the escape: the same search, shown rather than
 * resolved, so a person can recognise the answer the score could not.
 *
 * Each row leads with the provider's own cover, because that is what the user is
 * really choosing between. Two candidates called "Resident Evil 2" are told
 * apart by their artwork long before their release year is read.
 */
@Composable
fun MatchPickerDialog(
    state: MatchPickerState,
    onPick: (MetadataCandidate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
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
                    text = "Choose the right game",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = state.entryTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                val candidates = state.candidates
                when {
                    candidates == null -> PickerNote(loading = true, message = "Searching…")

                    candidates.isEmpty() -> PickerNote(
                        loading = false,
                        message = "No provider has a match for this title.",
                    )

                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        candidates.forEachIndexed { index, candidate ->
                            CandidateRow(
                                candidate = candidate,
                                focused = index == state.focusedIndex,
                                onClick = { onPick(candidate) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerNote(loading: Boolean, message: String) {
    val colors = ThorTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(NOTE_HEIGHT.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.cursor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: MetadataCandidate,
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
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
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
            // The score, plainly. It is what the automatic pass would have gone
            // on, and seeing it is how somebody learns whether to trust it next
            // time rather than opening this dialog for every game.
            text = "${(candidate.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

private const val CARD_WIDTH = 400
private const val CONTENT_MAX_HEIGHT = 260
private const val NOTE_HEIGHT = 64

/** Roughly box-art proportions, so a cover is recognisable at row height. */
private const val COVER_WIDTH = 34
private const val COVER_HEIGHT = 46
