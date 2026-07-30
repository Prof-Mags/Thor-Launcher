package com.thor.feature.settings.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeSpec

/**
 * A scrollable gallery of theme swatches.
 *
 * Themes were previously chosen from a dropdown of names, which meant picking
 * blind and backing out of Settings to see the result — for twenty themes that
 * is twenty round trips. Each card here renders its own palette, so the choice
 * is made by looking rather than by guessing what "Lagoon" means.
 *
 * Each card is drawn from its own [ThemeSpec] rather than from the active theme,
 * which is the whole point: the surrounding UI stays in the current theme while
 * the cards show what the alternatives would look like.
 */
@Composable
fun ThemePreviewRow(
    selected: ThemeId,
    focused: Boolean,
    onSelected: (ThemeId) -> Unit,
    /** Declares this row as one that navigates sideways while it holds the cursor. */
    onTakesHorizontalInput: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens
    val colors = ThorTheme.colors
    val themes = ThemeSpec.ALL
    val listState = rememberLazyListState()

    /*
     * The card the controller is on, which is not the same thing as the theme in use.
     *
     * A gallery navigated by a cursor needs both: you move across the cards to look at
     * them, and press to apply the one you have arrived at. Without the first of those
     * there was nothing on screen saying where the controller was — the row scrolled
     * and themes changed with no indication of what was about to be picked.
     */
    var highlighted by remember(selected) {
        mutableIntStateOf(themes.indexOfFirst { it.id == selected }.coerceAtLeast(0))
    }

    // Left and right belong to this row while it holds the cursor; the page gets them
    // back the moment the cursor leaves.
    DisposableEffect(Unit) {
        onTakesHorizontalInput(true)
        onDispose { onTakesHorizontalInput(false) }
    }

    StepOnHorizontal(focused) { delta ->
        highlighted = (highlighted + delta).coerceIn(0, themes.lastIndex)
    }

    // Confirm applies whatever the cursor has arrived at.
    ActivateOnConfirm(focused) {
        themes.getOrNull(highlighted)?.let { theme -> onSelected(theme.id) }
    }

    /*
     * Keeps the cursor's card on screen.
     *
     * Follows the highlight while the controller is here and the applied theme when it
     * is not, so the row is scrolled to what the user is looking at either way.
     */
    LaunchedEffect(highlighted, selected, focused) {
        val index = if (focused) {
            highlighted
        } else {
            themes.indexOfFirst { it.id == selected }
        }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET.dp, vertical = dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) colors.cursor else colors.onSurface,
        )

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            itemsIndexed(themes, key = { _, spec -> spec.id.name }) { index, spec ->
                ThemeCard(
                    spec = spec,
                    selected = spec.id == selected,
                    cursorOn = focused && index == highlighted,
                    onClick = {
                        highlighted = index
                        onSelected(spec.id)
                    },
                )
            }
        }
    }
}

/**
 * One theme, drawn in its own colours.
 *
 * A miniature of the launcher rather than a row of swatches: a background, a
 * panel, three cells and a cursor ring. Bare swatches tell you the hues but not
 * whether the surface ramp separates or the accent reads against it, which is
 * what actually decides whether a theme is usable.
 */
@Composable
private fun ThemeCard(
    spec: ThemeSpec,
    selected: Boolean,
    /** True when the controller is on this card, which is not the same as applied. */
    cursorOn: Boolean,
    onClick: () -> Unit,
) {
    val activeColors = ThorTheme.colors
    val radius = spec.cornerRadiusDp.coerceAtMost(MAX_PREVIEW_RADIUS).dp

    val background = Color(spec.backgroundArgb)
    val surface = Color(spec.surfaceArgb)
    val elevated = Color(spec.surfaceElevatedArgb)
    val primary = Color(spec.primaryArgb)
    val accentEnd = Color(spec.accentEndArgb)
    val cursor = Color(spec.cursorArgb)
    val onSurface = Color(spec.onSurfaceArgb)

    // Lifts under the cursor, the way a focused tile does on a television. The
    // animation is what makes moving along the row read as movement rather than as
    // the border jumping between cards.
    val lift by animateFloatAsState(
        targetValue = if (cursorOn) CURSOR_SCALE else 1f,
        animationSpec = tween(durationMillis = ThorTheme.motion.cursorMillis),
        label = "themeCardLift",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(CARD_WIDTH.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT.dp)
                .scale(lift)
                .clip(RoundedCornerShape(PREVIEW_CORNER.dp))
                .background(background)
                .border(
                    // Three states, and each has to be tellable from the others: the
                    // theme in use, the card the controller is on, and the rest.
                    width = if (cursorOn || selected) 2.dp else 1.dp,
                    color = when {
                        selected -> activeColors.cursor
                        cursorOn -> activeColors.onSurface
                        else -> activeColors.outline
                    },
                    shape = RoundedCornerShape(PREVIEW_CORNER.dp),
                )
                // The launcher's own cursor, so a focused theme card is marked the
                // same way a focused grid cell is.
                .thorCursor(
                    focused = cursorOn,
                    shape = RoundedCornerShape(PREVIEW_CORNER.dp),
                )
                .clickable(onClick = onClick),
        ) {
            // A hint of the accent gradient, standing in for the wallpaper.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = 0.32f),
                                Color.Transparent,
                                accentEnd.copy(alpha = 0.22f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // The information panel.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PANEL_HEIGHT.dp)
                        .clip(RoundedCornerShape(radius / 2))
                        .background(surface.copy(alpha = spec.surfaceAlpha)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Text stands in as bars, so the card stays legible at
                        // this size instead of rendering unreadable glyphs.
                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 3.dp)
                                .background(onSurface, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = 2.dp)
                                .background(
                                    Color(spec.onSurfaceVariantArgb),
                                    CircleShape,
                                ),
                        )
                    }
                }

                // The grid, with the first cell focused.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(CELL_COUNT) { index ->
                        Box(
                            modifier = Modifier
                                .size(CELL_SIZE.dp)
                                .clip(RoundedCornerShape(radius / 2))
                                .background(elevated)
                                .then(
                                    if (index == 0) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = cursor,
                                            shape = RoundedCornerShape(radius / 2),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                // The dock.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(DOCK_HEIGHT.dp)
                        .clip(CircleShape)
                        .background(elevated.copy(alpha = spec.surfaceAlpha)),
                )
            }
        }

        Text(
            text = spec.id.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) activeColors.cursor else activeColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val CARD_WIDTH = 104
private const val CARD_HEIGHT = 66
private const val PREVIEW_CORNER = 10
private const val PANEL_HEIGHT = 20
private const val CELL_COUNT = 4
private const val CELL_SIZE = 13
private const val DOCK_HEIGHT = 7

/** Preview cards are small, so a 32dp theme radius would round them away. */
private const val MAX_PREVIEW_RADIUS = 14

/** Matches the inset every other settings row uses. */
private const val ROW_INSET = 14

/** How much a card grows under the cursor. */
private const val CURSOR_SCALE = 1.06f
