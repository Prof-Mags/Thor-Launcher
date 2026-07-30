package com.thor.feature.home.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.FolderStyle
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.GridSpec
import com.thor.core.model.IconShape
import com.thor.core.model.Platform
import com.thor.core.model.ShortcutEntry
import com.thor.core.ui.component.ArtworkImage

/**
 * One cell of the bottom-screen grid.
 *
 * The cell is handed a fixed slot by the page layout and must render inside it,
 * so the icon takes the remaining height via `weight` and is squared off with
 * `aspectRatio`. That ordering matters: sizing the icon from the *width* and
 * letting the aspect ratio decide the height — which is what this did
 * originally — produces a box taller than its slot, and the label underneath
 * gets clipped away.
 *
 * Focus is expressed as scale plus the theme's cursor treatment rather than as
 * a background change, so the effect reads identically over box art, an app
 * icon and an empty folder shell. The scale is a draw-time transform into the
 * inter-cell gap, so it never changes layout or pushes neighbours around.
 */
@Composable
fun GridCell(
    entry: GridEntry?,
    spec: GridSpec,
    focused: Boolean,
    isHeld: Boolean,
    jiggling: Boolean,
    platform: Platform?,
    folderStyle: FolderStyle,
    /** Artwork of the first few children, for the folder preview styles. */
    folderPreview: List<String?>,
    modifier: Modifier = Modifier,
) {
    val theme = ThorTheme.colors
    val motion = ThorTheme.motion
    val dimens = ThorTheme.dimens

    // A held icon lifts further than a merely focused one, so the two states
    // are distinguishable at a glance while dragging.
    val targetScale = when {
        isHeld -> 1.14f
        focused -> 1.07f
        else -> 1f
    }
    val focusScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = motion.tweenSpec(motion.selectionMillis),
        label = "cellScale",
    )

    // An actual oscillation. This used to be a *static* 1.6° tilt, which at that
    // angle is indistinguishable from no change at all — which is why entering
    // edit mode looked like it had done nothing.
    val wobbleTransition = rememberInfiniteTransition(label = "wobble")
    val wobble by wobbleTransition.animateFloat(
        initialValue = -JIGGLE_DEGREES,
        targetValue = if (jiggling) JIGGLE_DEGREES else -JIGGLE_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = JIGGLE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobbleAngle",
    )

    val shape = spec.iconShape.toComposeShape(dimens.cornerRadius.value)

    // The user's icon-size preference is expressed as the fraction of the
    // square slot the icon occupies, so growing it can never overflow the cell.
    val iconFraction = (BASE_ICON_FILL * spec.iconScale).coerceIn(0.35f, 1f)

    Column(
        // No inset here: the gap between cells is applied by the page's own
        // arrangement. Padding each cell instead shrank every icon and produced
        // a double-width gutter at the page edges.
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Square slot: takes the height left over after the label, then matches
        // its width to that height. If the slot is narrower than it is tall,
        // aspectRatio falls back to sizing from the width, so the icon fits
        // either way.
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(ratio = 1f, matchHeightConstraintsFirst = true),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(iconFraction)
                    .scale(focusScale)
                    .graphicsLayer {
                        // The arrange-mode wobble is a rotation on the layer
                        // rather than a re-layout, so it costs nothing per frame.
                        rotationZ = if (jiggling) wobble else 0f
                        alpha = if (isHeld) 0.85f else 1f
                    }
                    // The cell's own shape, so a square icon gets a square cursor
                    // and a circular one a ring, rather than a fixed rounded box
                    // that matched only one of the five shapes on offer.
                    .thorCursor(focused = focused, shape = shape)
                    .clip(shape)
                    .background(theme.surfaceElevated, shape),
                contentAlignment = Alignment.Center,
            ) {
                when (entry) {
                    null -> Unit

                    is FolderEntry -> FolderShell(
                        folder = entry,
                        shape = shape,
                        style = folderStyle,
                        childImages = folderPreview,
                    )

                    is GameEntry -> ArtworkImage(
                        model = entry.metadata.artwork.cellImage,
                        contentDescription = entry.title,
                        fallbackText = entry.title,
                        fallbackTint = platform?.accentArgb?.let(::Color) ?: theme.primary,
                        // Fit, not crop. Cover art is rarely square — tall box
                        // art had its top and bottom sliced off and wide key art
                        // lost its ends. The plate behind fills the rest of the
                        // cell, so the artwork stays whole and every cell is
                        // still the same size.
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ARTWORK_INSET.dp)
                            .clip(shape),
                    )

                    // A user-chosen icon wins over the packaged one.
                    is AppEntry -> if (entry.customIconUri != null) {
                        ArtworkImage(
                            model = entry.customIconUri,
                            contentDescription = entry.title,
                            fallbackText = entry.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ARTWORK_INSET.dp)
                                .clip(shape),
                        )
                    } else {
                        AppIcon(
                            packageName = entry.packageName,
                            title = entry.title,
                            shape = shape,
                        )
                    }

                    is ShortcutEntry -> ArtworkImage(
                        model = entry.customIconUri,
                        contentDescription = entry.title,
                        fallbackText = entry.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ARTWORK_INSET.dp)
                            .clip(shape),
                    )
                }

                if (entry?.isFavorite == true) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Favourite",
                        tint = theme.cursor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .fillMaxSize(FAVOURITE_BADGE_FRACTION),
                    )
                }
            }
        }

        if (spec.showLabels && entry != null) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelSmall,
                color = theme.onBackground,
                maxLines = spec.labelLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * The shell drawn for a folder.
 *
 * Custom artwork always wins; otherwise the user's [FolderStyle] decides
 * between a glyph, a 2×2 preview of what is inside, and an offset stack.
 */
@Composable
private fun FolderShell(
    folder: FolderEntry,
    shape: Shape,
    style: FolderStyle,
    childImages: List<String?>,
) {
    val colors = ThorTheme.colors
    val accent = folder.accentArgb?.let(::Color) ?: colors.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            folder.artworkUri != null -> ArtworkImage(
                model = folder.artworkUri,
                contentDescription = folder.title,
                fallbackText = folder.title,
                modifier = Modifier.fillMaxSize().clip(shape),
            )

            style == FolderStyle.GLYPH || childImages.isEmpty() -> Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.fillMaxSize(0.45f),
            )

            style == FolderStyle.GRID_PREVIEW -> Column(
                modifier = Modifier.fillMaxSize(0.82f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                childImages.chunked(2).take(2).forEach { pair ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        pair.forEach { image ->
                            ArtworkImage(
                                model = image,
                                contentDescription = null,
                                fallbackText = folder.title,
                                fallbackTint = accent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        // Keeps a lone item in a row at half width rather than
                        // stretching it across the whole preview.
                        if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }

            // STACK: the first child on top, the next peeking out behind it.
            else -> Box(modifier = Modifier.fillMaxSize(0.8f)) {
                childImages.take(2).reversed().forEachIndexed { index, image ->
                    val inset = if (index == 0) 6.dp else 0.dp
                    ArtworkImage(
                        model = image,
                        contentDescription = null,
                        fallbackText = folder.title,
                        fallbackTint = accent,
                        modifier = Modifier
                            .fillMaxSize(0.9f)
                            .padding(start = inset, top = inset)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/** Converts the user's icon-shape preference into a Compose shape. */
fun IconShape.toComposeShape(radius: Float): Shape = when (this) {
    IconShape.SQUARE -> RoundedCornerShape(0.dp)
    IconShape.ROUNDED -> RoundedCornerShape(radius.dp * 0.45f)
    IconShape.SQUIRCLE -> RoundedCornerShape(radius.dp * 0.85f)
    IconShape.CIRCLE -> CircleShape
    IconShape.HEXAGON -> CutCornerShape(radius.dp * 0.9f)
}

/**
 * Fraction of the square slot an icon fills at `iconScale == 1`.
 *
 * Not 1.0, and no longer 0.92. The inter-cell gutter alone was not enough to
 * stop the grid reading as crowded, because at 92% fill two neighbouring icons
 * are separated by the gutter *minus* almost nothing of their own margin. Leaving
 * a little inside each cell as well is what gives every icon room, at every
 * preset, without making the gutter absurd at the dense end.
 */
private const val BASE_ICON_FILL = 0.84f

/**
 * Breathing room between the artwork and the edge of its plate.
 *
 * Constant in dp rather than proportional: it exists so a light image does not
 * bleed into the cursor ring, and that is a fixed optical distance.
 */
private const val ARTWORK_INSET = 2
private const val FAVOURITE_BADGE_FRACTION = 0.24f
private const val JIGGLE_DEGREES = 2.6f
private const val JIGGLE_PERIOD_MS = 140
