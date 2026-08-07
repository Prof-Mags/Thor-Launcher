package com.thor.feature.home.grid

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.CornerStyle
import com.thor.core.model.FolderEntry
import com.thor.core.model.FolderStyle
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.GridSpec
import com.thor.core.model.IconShape
import com.thor.core.model.Platform
import com.thor.core.model.ShortcutEntry
import com.thor.core.model.WidgetEntry
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.icon.PlatformIcons
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.home.couch.platform
import com.thor.feature.home.shell.icon

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

    /*
     * The pointer highlights exactly as the controller cursor does.
     *
     * Deliberately the same treatment rather than a second one. A cell under the
     * cursor and a cell under the pointer are the same fact — "this is what a
     * press will act on" — and the launcher already has a visual language for it.
     * Inventing a hover style would have put two different marks on screen
     * meaning one thing, and left the pointer looking like a guest.
     *
     * An empty cell is skipped: it has nothing to act on, and lighting up the
     * gaps as the cursor crossed them would strobe.
     */
    // Empty cells do not react to a pointer, so giving each of them a hover
    // collector and a layout-bound listener only burns work while the cursor
    // moves. Populated cells retain the identical hover and haptic behaviour.
    val hover = if (entry != null) rememberPointerHover() else null
    val highlighted = focused || hover?.isHovered == true

    // A held icon lifts further than a merely focused one, so the two states
    // are distinguishable at a glance while dragging.
    val targetScale = when {
        isHeld -> 1.14f
        highlighted -> 1.07f
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
    // A grid can have dozens of cells. Keeping an inactive infinite transition
    // in each one leaves the frame clock busy even when arrange mode is closed.
    val wobble = if (jiggling) {
        val wobbleTransition = rememberInfiniteTransition(label = "wobble")
        val value by wobbleTransition.animateFloat(
            initialValue = -JIGGLE_DEGREES,
            targetValue = JIGGLE_DEGREES,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = JIGGLE_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "wobbleAngle",
        )
        value
    } else {
        0f
    }

    val cornerStyle = ThorTheme.shapes.style
    val shape = remember(spec.iconShape, dimens.cornerRadius, cornerStyle) {
        spec.iconShape.toComposeShape(
            radius = dimens.cornerRadius.value,
            cornerStyle = cornerStyle,
        )
    }

    // The user's icon-size preference is expressed as the fraction of the
    // square slot the icon occupies, so growing it can never overflow the cell.
    val iconFraction = (BASE_ICON_FILL * spec.iconScale).coerceIn(0.35f, 1f)

    val contentAlpha = when {
        // Only reachable at all while "show hidden entries" is on, and it has
        // to look like what it is: an entry the user hid, showing temporarily so
        // it can be restored or removed.
        entry?.isHidden == true -> HIDDEN_ALPHA
        isHeld -> 0.85f
        else -> 1f
    }
    val transform = if (focusScale != 1f || wobble != 0f || contentAlpha != 1f) {
        Modifier.graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
            rotationZ = wobble
            alpha = contentAlpha
        }
    } else {
        Modifier
    }

    /*
     * The plate the icon sits on, in the theme's own material.
     *
     * This was a flat `background(surfaceElevated)` — a colour and nothing else —
     * which is how the grid came to be the one surface in the launcher the theme
     * could not reach. [SurfaceLevel.RAISED] names grid cells in its own
     * documentation, and every panel, card and sheet had been moved onto
     * [thorSurface] except the several hundred boxes that make up the thing the
     * user actually looks at. So choosing Glass, Raised, Flat or Tinted changed
     * the settings pages and the info panel and left the home screen identical.
     * Now the grid carries the shadow, the lit edge, the border and the elevation
     * tint the theme asks for, like everything else.
     *
     * An empty cell gets *no* plate. A page of empty plates reads as a form to be
     * filled in rather than as a grid with room on it, and the plate was never
     * saying anything about the cell — it was saying "something could go here",
     * which is true of every empty cell and therefore worth saying nowhere. The
     * exception is arrange mode, where "something could go here" is precisely the
     * question being asked, so the outline comes back while icons are jiggling.
     */
    val plate = when {
        entry != null -> Modifier.thorSurface(
            shape = shape,
            color = theme.surfaceElevated,
            level = SurfaceLevel.RAISED,
        )

        jiggling -> Modifier
            .clip(shape)
            .border(EMPTY_CELL_BORDER.dp, theme.outline.copy(alpha = EMPTY_CELL_ALPHA), shape)

        else -> Modifier
    }

    Column(
        // No inset here: the gap between cells is applied by the page's own
        // arrangement. Padding each cell instead shrank every icon and produced
        // a double-width gutter at the page edges.
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        /*
         * Square slot, sized from whichever edge is shorter.
         *
         * This was an `aspectRatio(1f, matchHeightConstraintsFirst = true)`, on
         * the understanding that it would fall back to the width when a cell was
         * taller than it was wide. There is nothing to fall back into: the cell's
         * width is fixed by its `weight` in the row and its height by
         * `fillMaxHeight`, so both constraints are exact and a square can satisfy
         * them only when the cell happens to be square.
         *
         * Where it was not, the slot took the height and became *wider than its
         * own cell*, so neighbouring icons met in the middle of the gutter and
         * the row looked as though it had no spacing at all. It depended entirely
         * on the matrix — at 16:9 a 4×2 or 6×3 page produces cells taller than
         * they are wide and showed the fault, while 5×3 and 8×5 do not and looked
         * correct — which is why it appeared at some pinch presets and not
         * others.
         *
         * Taking the smaller edge cannot overflow either way.
         */
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val slot = minOf(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    // A share of the square slot rather than of the cell, so the
                    // icon stays square whatever shape the cell is.
                    .size(slot * iconFraction)
                    // Before the scale, so the hover target is the cell's resting
                    // box. Measured inside it, the box grows when the highlight
                    // appears and shrinks when it goes, which makes the element's
                    // own state an input to the test that produced it.
                    .then(if (hover != null) Modifier.pointerHover(hover) else Modifier)
                    // One transient layer carries scale, alpha and arrange-mode
                    // rotation. Ordinary cells need none, avoiding a render node
                    // for every icon on every prefetched page.
                    .then(transform)
                    // The cell's own shape, so a square icon gets a square cursor
                    // and a circular one a ring, rather than a fixed rounded box
                    // that matched only one of the five shapes on offer.
                    .thorCursor(focused = highlighted, shape = shape)
                    .then(plate),
                contentAlignment = Alignment.Center,
            ) {
                when (entry) {
                    null -> Unit

                    is FolderEntry -> FolderShell(
                        folder = entry,
                        shape = shape,
                        style = folderStyle,
                        childImages = folderPreview,
                        platform = platform,
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

                    /*
                     * Nothing, deliberately: a widget is not an icon.
                     *
                     * It draws its own contents through the widget host, across
                     * however many cells it spans, so it is composed above this
                     * layer rather than inside a single cell's artwork slot.
                     * Nothing constructs a [WidgetEntry] yet — the host that
                     * does is the next piece — and this branch exists so the
                     * sealed `when` stays honest in the meantime rather than
                     * being closed with an `else` that would silently swallow
                     * whatever entry type comes after it.
                     */
                    is WidgetEntry -> Unit
                }

                if (entry?.isFavorite == true) {
                    /*
                     * A badge rather than a bare glyph.
                     *
                     * The star was drawn straight onto the artwork in the cursor
                     * colour, which is a colour chosen to read against the
                     * launcher's own surfaces and not against several hundred
                     * pieces of box art. Over a light cover it disappeared
                     * entirely, so whether a game was favourited depended on the
                     * game. A disc behind it costs one small circle and makes the
                     * answer the same everywhere.
                     */
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(FAVOURITE_INSET.dp)
                            .fillMaxSize(FAVOURITE_BADGE_FRACTION)
                            .background(
                                theme.background.copy(alpha = FAVOURITE_DISC_ALPHA),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favourite",
                            tint = theme.cursor,
                            modifier = Modifier.fillMaxSize(FAVOURITE_GLYPH_FRACTION),
                        )
                    }
                }
            }
        }

        if (spec.showLabels && entry != null) {
            Text(
                text = entry.title,
                /*
                 * Carried on a shadow, and brightened under the cursor.
                 *
                 * A label is the one part of a cell drawn straight onto the
                 * wallpaper with nothing behind it, and the wallpaper is a
                 * user-chosen photograph or one of nine animated effects. Against
                 * a light region the text simply went. A soft drop shadow is what
                 * every launcher that draws labels over a wallpaper uses, costs no
                 * layout, and does nothing visible on a dark ground where the text
                 * was already fine.
                 *
                 * The cursor tint is the other half: the focused cell now says so
                 * twice — the plate lifts and its name lights up — which is what
                 * makes the selection readable across the room in couch mode and
                 * at a glance on the handheld.
                 */
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = LABEL_SHADOW_ALPHA),
                        offset = Offset(0f, LABEL_SHADOW_OFFSET),
                        blurRadius = LABEL_SHADOW_BLUR,
                    ),
                ),
                color = if (highlighted) theme.cursor else theme.onBackground,
                maxLines = spec.labelLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The shell drawn for a folder.
 *
 * Custom artwork always wins; otherwise the user's [FolderStyle] decides
 * between a glyph, a 2×2 preview of what is inside, and an offset stack.
 *
 * A *platform* folder is the exception and never shows the preview styles. Those
 * describe a folder by what the user put in it, which is right for a folder they
 * made and wrong for one the scanner made: the cell for "SNES" became a collage
 * of four arbitrary games, which reads as a game rather than as a system and
 * changes every time the library is rescanned. It wears the platform's own name
 * instead, until an icon pack gives it something better.
 */
@Composable
private fun FolderShell(
    folder: FolderEntry,
    shape: Shape,
    style: FolderStyle,
    childImages: List<String?>,
    platform: Platform?,
) {
    val colors = ThorTheme.colors
    // The platform's colour is the folder's when the folder has none of its own,
    // so a system reads as itself rather than as the generic accent.
    val accent = (folder.accentArgb ?: platform?.accentArgb)?.let(::Color) ?: colors.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val bundled = PlatformIcons.preferredOverEnabled(platform?.artwork, platform?.id)
        when {
            /*
             * The shipped artwork outranks whatever a scraper found, but not a
             * pack the user installed or an image they picked — see
             * [PlatformIcons.preferredOver]. Ordering these the other way round
             * showed none of them on a library that had ever been scraped, which
             * is every library.
             */
            bundled != null -> ArtworkImage(
                model = bundled,
                contentDescription = folder.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(BUILT_IN_ICON_FRACTION).clip(shape),
            )

            folder.artworkUri != null -> ArtworkImage(
                model = folder.artworkUri,
                contentDescription = folder.title,
                fallbackText = folder.title,
                modifier = Modifier.fillMaxSize().clip(shape),
            )

            // A system this set has no artwork for: its own short name, which is
            // the one thing that identifies it and never changes under a rescan.
            platform != null -> Text(
                text = platform.shortName.ifBlank { platform.name },
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
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

/** How faint a hidden entry is drawn while hidden entries are being shown. */
private const val HIDDEN_ALPHA = 0.4f

/**
 * Converts the user's icon-shape preference into a Compose shape.
 *
 * [cornerStyle] overrides it outright when the user has asked for one shape
 * across the whole interface. That override is the entire point of the setting:
 * a launcher whose panels, dialogs, tabs and rows are all square, with rounded
 * icons in the middle of it, has not applied the choice — it has applied it
 * everywhere the author remembered.
 *
 * [CornerStyle.THEME] defers to the icon shape, because that is the setting that
 * still means "let each part decide" — and it is the only one of the three that
 * can express a circle or a hexagon at all.
 */
fun IconShape.toComposeShape(
    radius: Float,
    cornerStyle: CornerStyle = CornerStyle.THEME,
): Shape = when (cornerStyle) {
    CornerStyle.SQUARE -> RoundedCornerShape(0.dp)
    CornerStyle.ROUNDED -> RoundedCornerShape(radius.dp * 0.45f)
    CornerStyle.THEME -> when (this) {
        IconShape.SQUARE -> RoundedCornerShape(0.dp)
        IconShape.ROUNDED -> RoundedCornerShape(radius.dp * 0.45f)
        IconShape.SQUIRCLE -> RoundedCornerShape(radius.dp * 0.85f)
        IconShape.CIRCLE -> CircleShape
        IconShape.HEXAGON -> CutCornerShape(radius.dp * 0.9f)
    }
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
/**
 * How much of a folder cell the shipped platform artwork fills.
 *
 * Short of the edge, unlike a pack icon: these are console renders drawn to
 * fill a square, and running one to the cell boundary loses the shape of the
 * hardware against the cell behind it.
 */
private const val BUILT_IN_ICON_FRACTION = 0.88f

/** The disc, and the star inside it. */
private const val FAVOURITE_BADGE_FRACTION = 0.26f
private const val FAVOURITE_GLYPH_FRACTION = 0.72f
private const val FAVOURITE_INSET = 3

/** Enough to separate the star from artwork without becoming a blob of its own. */
private const val FAVOURITE_DISC_ALPHA = 0.55f

/**
 * The outline an empty cell wears while the grid is being arranged.
 *
 * Faint on purpose: it marks where an icon could land, and a page of bright boxes
 * would compete with the icon actually being carried.
 */
private const val EMPTY_CELL_BORDER = 1
private const val EMPTY_CELL_ALPHA = 0.35f

/** A soft drop shadow, so a label survives a light wallpaper. */
private const val LABEL_SHADOW_ALPHA = 0.65f
private const val LABEL_SHADOW_OFFSET = 1f
private const val LABEL_SHADOW_BLUR = 3f

private const val JIGGLE_DEGREES = 2.6f
private const val JIGGLE_PERIOD_MS = 140
