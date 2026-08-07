package com.thor.feature.settings.component.row

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorShapes
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeOptions
import com.thor.core.model.ThemeRecipe
import com.thor.core.model.ThemeSpec
import com.thor.feature.settings.component.SettingsCard
import com.thor.feature.settings.component.SettingsTextButton

/**
 * A scrollable gallery of theme swatches.
 *
 * Themes were previously chosen from a dropdown of names, which meant picking
 * blind and backing out of Settings to see the result — twelve names is twelve
 * round trips. Each card here renders its own palette, so the choice is made by
 * looking rather than by guessing what "Sakura" means.
 *
 * Every card is resolved through [options], the same dials the live theme is built
 * from. That matters more than it used to: with light/dark, contrast, hue and
 * colour intensity all belonging to the user rather than to the theme, a gallery
 * that rendered each recipe at its defaults would show twelve dark cards to
 * somebody running the launcher light — twelve accurate previews of a palette none
 * of them would get.
 */
@Composable
fun ThemePreviewRow(
    /** The applied theme's [ThemeRecipe.key] — a [ThemeId] name, or a custom id. */
    selected: String,
    /**
     * Every theme on offer, bundled shelves first and the user's own after.
     *
     * Passed rather than read from [ThemeRecipe.ALL], which is what lets a theme
     * the user built appear here at all: `ALL` is the bundled fourteen and is
     * deliberately closed, so the caller composes the two sets and this row draws
     * whatever it is handed.
     */
    recipes: List<ThemeRecipe>,
    /** The user's own appearance dials, so each card previews what they would get. */
    options: ThemeOptions,
    focused: Boolean,
    onSelected: (ThemeRecipe) -> Unit,
    /** Declares this row as one that navigates sideways while it holds the cursor. */
    onTakesHorizontalInput: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens
    val colors = ThorTheme.colors
    /*
     * Resolved once per change to the dials rather than per card per frame.
     *
     * Twelve palettes is a few hundred gamut searches and a few dozen binary
     * searches for readable text — trivial once, wasteful sixty times a second
     * while the row is being scrolled past.
     */
    val themes = remember(options, recipes) { recipes.map { it.resolve(options) } }
    val listState = rememberLazyListState()

    /*
     * The card the controller is on, which is not the same thing as the theme in use.
     *
     * A gallery navigated by a cursor needs both: you move across the cards to look at
     * them, and press to apply the one you have arrived at. Without the first of those
     * there was nothing on screen saying where the controller was — the row scrolled
     * and themes changed with no indication of what was about to be picked.
     */
    var highlighted by remember {
        mutableIntStateOf(themes.indexOfFirst { it.key == selected }.coerceAtLeast(0))
    }

    /*
     * Re-synced to the applied theme only while the cursor is somewhere else.
     *
     * This used to be `remember(selected)`, which reset the cursor every time a theme
     * was applied — so pressing A moved the highlight to wherever the newly applied
     * theme happened to sit in the row, and the next press of Right carried on from
     * a place the user had not put it.
     */
    LaunchedEffect(selected, focused) {
        if (!focused) {
            highlighted = themes.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        }
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
        recipes.getOrNull(highlighted)?.let(onSelected)
    }

    /*
     * Keeps the cursor's card on screen.
     *
     * Follows the highlight while the controller is here and the applied theme when it
     * is not, so the row is scrolled to what the user is looking at either way.
     *
     * Centred rather than pinned to the leading edge. `animateScrollToItem(index)`
     * alone puts the card flush against the start of the viewport, half under the
     * row's own inset and with nothing visible after it — so the one card the user
     * needs to see is the one drawn in the worst place on the row, and there is no
     * sense of which direction there is left to travel.
     */
    val cardWidthPx = with(LocalDensity.current) { CARD_WIDTH.dp.roundToPx() }
    LaunchedEffect(highlighted, selected, focused) {
        val index = if (focused) {
            highlighted
        } else {
            themes.indexOfFirst { it.key == selected }
        }
        if (index < 0) return@LaunchedEffect

        val layout = listState.layoutInfo
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        val centred = -((viewport - cardWidthPx) / 2).coerceAtLeast(0)

        // Animated while the controller is driving, so movement along the row reads
        // as movement; snapped when it is not, because a row being restored to the
        // applied theme has no journey to show.
        if (focused) {
            listState.animateScrollToItem(index, centred)
        } else {
            listState.scrollToItem(index, centred)
        }
    }

    SettingsCard(focused = focused, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Theme gallery",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) colors.cursor else colors.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                SettingsTextButton(
                    // Named from the resolved list rather than from the key, so a
                    // theme the user renamed says its new name here immediately.
                    label = (themes.firstOrNull { it.key == selected }?.displayName
                        ?: ThemeRecipe.of(ThemeRecipe.DEFAULT).displayName)
                        .uppercase(),
                    containerColor = colors.cursor.copy(alpha = 0.12f),
                    contentColor = colors.cursor,
                    borderColor = colors.cursor.copy(alpha = 0.34f),
                )
            }

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                itemsIndexed(themes, key = { _, spec -> spec.key }) { index, spec ->
                    ThemeCard(
                        spec = spec,
                        selected = spec.key == selected,
                        cursorOn = focused && index == highlighted,
                        onClick = {
                            highlighted = index
                            recipes.getOrNull(index)?.let(onSelected)
                        },
                    )
                }
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
    // Preview the candidate theme through the user's active corner override.
    // Square and Rounded are global choices; Theme Default uses this candidate's
    // own radius instead of the currently applied theme's radius.
    val previewShapes = ThorShapes.build(
        style = ThorTheme.shapes.style,
        themeRadius = spec.cornerRadiusDp.coerceAtMost(MAX_PREVIEW_RADIUS).dp,
    )

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
                .clip(previewShapes.panel)
                .background(background)
                .border(
                    /*
                     * Two *independent* states, not three exclusive ones.
                     *
                     * "The controller is here" and "this theme is applied" are
                     * different facts about a card and are routinely both true — the
                     * cursor starts on the applied theme every time the page opens.
                     * Ranking them in one `when` meant the applied card swallowed the
                     * cursor's own treatment at exactly that moment, so arriving on
                     * the Themes page showed no cursor at all; and once the cursor did
                     * move, the two states were a 2dp border apiece in colours a
                     * glance cannot rank. The ring is now the cursor's alone, and
                     * applied-ness is said separately by the badge below.
                     */
                    width = when {
                        cursorOn -> CURSOR_BORDER.dp
                        selected -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        cursorOn -> activeColors.cursor
                        selected -> activeColors.onSurface.copy(alpha = 0.55f)
                        else -> activeColors.outline
                    },
                    shape = previewShapes.panel,
                )
                // The launcher's own cursor, so a focused theme card is marked the
                // same way a focused grid cell is.
                .thorCursor(
                    focused = cursorOn,
                    shape = previewShapes.panel,
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
                // The information panel, drawn in *this* theme's surface
                // treatment rather than the active one's — the card exists to
                // show what the alternative looks like, and since the treatment
                // is now most of what separates the presets, a card that ignored
                // it would show twenty variations on one rounded box.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PANEL_HEIGHT.dp)
                        .miniature(spec, previewShapes.small, surface),
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
                                .background(onSurface, previewShapes.pill),
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = 2.dp)
                                .background(
                                    Color(spec.onSurfaceVariantArgb),
                                    previewShapes.pill,
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
                                .miniature(spec, previewShapes.small, elevated)
                                .then(
                                    if (index == 0) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = cursor,
                                            shape = previewShapes.small,
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
                        .clip(previewShapes.pill)
                        .background(elevated.copy(alpha = spec.surfaceAlpha)),
                )
            }

            // Applied-ness, given a mark of its own so it never has to compete with
            // the cursor for the same pixels — the two are drawn together on the card
            // the cursor starts on, which is every time this page is opened.
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(BADGE_INSET.dp)
                        .size(BADGE_SIZE.dp)
                        .background(activeColors.cursor, previewShapes.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Applied",
                        tint = activeColors.background,
                        modifier = Modifier.size(BADGE_ICON.dp),
                    )
                }
            }
        }

        Text(
            text = spec.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) activeColors.cursor else activeColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Draws a miniature panel in an *arbitrary* theme's surface treatment.
 *
 * Deliberately not [com.thor.core.designsystem.modifier.thorSurface], which reads
 * the treatment from the active theme — exactly the wrong source here, where the
 * whole point is to render eleven themes that are not the active one. It reads
 * from the passed [spec] instead, and takes the treatment's values raw rather than
 * degraded: a card should show what a theme *is*, not what this device would fall
 * back to if the user picked it.
 */
private fun Modifier.miniature(
    spec: ThemeSpec,
    shape: Shape,
    color: Color,
): Modifier {
    val treatment = spec.surface
    // Scaled down hard: a 10dp shadow under a 13dp cell is a black smudge.
    val shadow = (treatment.shadowElevationDp * MINIATURE_SHADOW_SCALE).dp

    return this
        .then(
            if (shadow > 0.dp) {
                Modifier.shadow(elevation = shadow, shape = shape, clip = false)
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(color.copy(alpha = color.alpha * spec.surfaceAlpha))
        .then(
            if (treatment.specularAlpha > 0f) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = treatment.specularAlpha),
                            Color.Transparent,
                        ),
                    ),
                )
            } else {
                Modifier
            },
        )
        .then(
            if (treatment.borderWidthDp > 0f && treatment.borderAlpha > 0f) {
                Modifier.border(
                    width = treatment.borderWidthDp.dp,
                    color = Color(spec.outlineArgb).copy(alpha = treatment.borderAlpha),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

/** A card is roughly a sixth of a panel, and its shadows have to scale with it. */
private const val MINIATURE_SHADOW_SCALE = 0.35f

private const val CARD_WIDTH = 104
private const val CARD_HEIGHT = 66
private const val PANEL_HEIGHT = 20
private const val CELL_COUNT = 4
private const val CELL_SIZE = 13
private const val DOCK_HEIGHT = 7

/** Preview cards are small, so a 32dp theme radius would round them away. */
private const val MAX_PREVIEW_RADIUS = 14

/** How much a card grows under the cursor. */
private const val CURSOR_SCALE = 1.06f

/**
 * The cursor's ring, deliberately heavier than any other border on the card.
 *
 * The point of the row is choosing by looking, so where the controller is has to be
 * readable at a glance across a row of small cards.
 */
private const val CURSOR_BORDER = 3

/** The "applied" badge: small enough not to obscure the miniature it sits on. */
private const val BADGE_SIZE = 12
private const val BADGE_ICON = 9
private const val BADGE_INSET = 3
