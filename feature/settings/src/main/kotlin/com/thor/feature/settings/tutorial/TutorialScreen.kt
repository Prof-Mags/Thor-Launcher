package com.thor.feature.settings.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.SettingsTextButton

/**
 * The first-run walkthrough.
 *
 * One page at a time rather than a scrollable document, because it is read with
 * a controller: a page is a thing you finish and move on from, while a long
 * scroll is a thing you give up on halfway down.
 *
 * Stateless. The page index lives in the shell that hosts this, so the same
 * command stream that drives every other overlay drives this one too — and so
 * the walkthrough cannot be showing one page while the launcher thinks it is on
 * another.
 */
@Composable
fun TutorialScreen(
    pageIndex: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val pages = ThorTutorial.PAGES
    if (pages.isEmpty()) return

    val index = pageIndex.coerceIn(0, pages.lastIndex)
    val (chapter, page) = pages[index]
    val isLast = index == pages.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.panel,
            color = colors.surface,
            level = SurfaceLevel.RAISED,
            modifier = Modifier
                .fillMaxWidth(CARD_WIDTH_FRACTION)
                .padding(dimens.spacing),
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                ChapterHeading(chapter = chapter, index = index, total = pages.size)

                /*
                 * The page itself, cross-faded.
                 *
                 * Keyed on the index so moving between two pages of the same
                 * chapter still animates — keying on the chapter would leave the
                 * body swapping instantly within one.
                 */
                /*
                 * The diagram sits outside the cross-fade.
                 *
                 * Its highlight moves between pages of the same chapter as often
                 * as it stays put, and fading the whole illustration in and out
                 * each time turns a pointer into a flicker. Redrawing in place
                 * lets the lit region simply be somewhere else.
                 */
                TutorialSpotlight(
                    focus = page.focus.takeIf { it != TutorialFocus.NONE } ?: chapter.focus,
                )

                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tutorial-page",
                ) { current ->
                    val (_, shown) = pages[current.coerceIn(0, pages.lastIndex)]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(BODY_HEIGHT.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    ) {
                        Text(
                            text = shown.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )
                        Text(
                            text = shown.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        shown.hint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.cursor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = dimens.spacingSmall),
                            )
                        }
                    }
                }

                ProgressRail(index = index, total = pages.size)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /*
                     * Back, and nothing that leaves.
                     *
                     * Both of these used to call the same dismiss callback, so
                     * "NEXT" closed the walkthrough on its first page — which,
                     * with the routing fault that sent Confirm to the grid, is
                     * two separate ways the same thing went wrong. There is no
                     * skip: the only way out is the last page.
                     */
                    SettingsTextButton(
                        label = "BACK",
                        enabled = index > 0,
                        reactToHover = index > 0,
                        onClick = onBack.takeIf { index > 0 },
                    )
                    Box(modifier = Modifier.weight(1f))
                    SettingsTextButton(
                        label = if (isLast) "DONE" else "NEXT",
                        containerColor = colors.cursor.copy(alpha = 0.16f),
                        contentColor = colors.cursor,
                        borderColor = colors.cursor.copy(alpha = 0.5f),
                        focused = true,
                        reactToHover = true,
                        onClick = onNext,
                    )
                }

                Text(
                    text = if (isLast) {
                        "A finishes"
                    } else {
                        "A continues  ·  B goes back"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChapterHeading(chapter: TutorialChapter, index: Int, total: Int) {
    val colors = ThorTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = chapter.icon,
            contentDescription = null,
            tint = colors.cursor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = chapter.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.cursor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${index + 1} / $total",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * How far through the whole walkthrough this is.
 *
 * One rail across every page rather than a dot per page: there are more than
 * thirty, and thirty dots is a decoration rather than a measure.
 */
@Composable
private fun ProgressRail(index: Int, total: Int) {
    val colors = ThorTheme.colors
    val fraction = ((index + 1).toFloat() / total).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RAIL_HEIGHT.dp)
            .clip(ThorTheme.shapes.pill)
            .background(colors.surfaceHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(RAIL_HEIGHT.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor),
        )
    }
}

private const val CARD_WIDTH_FRACTION = 0.86f

/** Fixed, so the card does not resize between a short page and a long one. */
private const val BODY_HEIGHT = 208

private const val RAIL_HEIGHT = 4
