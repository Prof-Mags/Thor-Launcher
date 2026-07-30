package com.thor.core.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.blend
import com.thor.core.model.SurfaceStyle

/**
 * How far up the surface ramp a panel sits.
 *
 * Named rather than numbered because the levels mean something: the grid's
 * backdrop is not "0", it is the page, and a context menu over a card over the
 * page is the reason there are three of them at all.
 */
enum class SurfaceLevel(internal val step: Int) {
    /** The page itself: panels, sheets, the info surface. */
    PAGE(0),

    /** Cards, grid cells, the nav bar — things lying on the page. */
    RAISED(1),

    /** Menus and dialogs, over something already raised. */
    OVERLAY(2),
}

/**
 * Draws a panel in the current theme's surface treatment.
 *
 * This is the one place that turns a [com.thor.core.model.SurfaceTreatment] into
 * pixels, and every panel in the launcher goes through it — which is the point.
 * Before this, "the theme's surface" meant a colour and an alpha, and every
 * component reimplemented the rest: some drew a border, some drew a sheen, none
 * drew a shadow, and no two agreed on how a raised thing should look. A theme
 * could therefore only ever differ from another by hue.
 *
 * The order below is load-bearing. Shadow is cast *before* the clip, because a
 * shadow drawn inside the clip is a shadow you cannot see; tint and sheen are
 * backgrounds so they compose under the content; the border is last so nothing
 * paints over the edge.
 *
 * @param level how far up the ramp this panel sits, which drives both the accent
 *   tint and the shadow depth
 * @param translucent set false to force opacity regardless of the theme, for
 *   callers with their own reason — a panel over a video, say
 */
@Composable
fun Modifier.thorSurface(
    shape: Shape,
    color: Color = ThorTheme.colors.surface,
    level: SurfaceLevel = SurfaceLevel.PAGE,
    translucent: Boolean = true,
    alphaOverride: Float? = null,
    /** False for panels flush against a screen edge; see `GlassSurface`. */
    bordered: Boolean = true,
): Modifier {
    val materials = ThorTheme.materials
    val colors = ThorTheme.colors
    val treatment = materials.surface

    val alpha = when {
        !translucent -> 1f
        else -> alphaOverride ?: materials.surfaceAlpha
    }

    /*
     * Each step up carries a little more of the primary.
     *
     * Material's idea, and it earns its place here: it means a theme needs one
     * good surface colour rather than three, and a stack of panels separates
     * even on the presets whose ramp steps are nearly identical in tone.
     */
    val tinted = if (treatment.elevationTint > 0f && level.step > 0) {
        color.blend(colors.primary, treatment.elevationTint * level.step)
    } else {
        color
    }

    // Shadow deepens with the ramp, so an overlay reads as further off the page
    // than the card it covers rather than as the same card in a different place.
    val shadowDp = (treatment.shadowElevationDp * SHADOW_STEP[level.step]).dp

    return this
        .then(
            if (shadowDp > 0.dp) {
                Modifier.shadow(elevation = shadowDp, shape = shape, clip = false)
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(color = tinted.copy(alpha = tinted.alpha * alpha), shape = shape)
        .then(
            if (treatment.specularAlpha > 0f) {
                Modifier.background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            // Lit from above, and only from above: a gradient that
                            // runs the whole height reads as a colour change,
                            // while one that fades out by a third reads as light
                            // falling on an edge.
                            Color.White.copy(alpha = treatment.specularAlpha),
                            Color.Transparent,
                        ),
                        endY = SPECULAR_FALLOFF,
                    ),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
        .then(
            if (bordered && treatment.borderWidthDp > 0f && treatment.borderAlpha > 0f) {
                Modifier.border(
                    width = treatment.borderWidthDp.dp,
                    // The theme's own outline, never a colour of this modifier's
                    // choosing — that is what keeps a treatment reusable across
                    // twenty palettes without fighting any of them.
                    color = colors.outline.copy(
                        alpha = colors.outline.alpha * treatment.borderAlpha,
                    ),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

/** True when the theme wants a blurred backdrop behind its panels. */
val ThorTheme.wantsGlass: Boolean
    @Composable get() = ThorTheme.materials.surface.style == SurfaceStyle.GLASS

/**
 * Shadow multiplier per ramp step.
 *
 * Not linear: the jump from page to card is what the eye reads as "this is on
 * top", and doubling it again for a menu just produces a dark smear.
 */
private val SHADOW_STEP = floatArrayOf(0f, 1f, 1.6f)

/** Where the top-edge highlight has fully faded, in pixels. */
private const val SPECULAR_FALLOFF = 120f
