package com.thor.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme

/**
 * A panel drawn in the current theme's surface treatment.
 *
 * Named for the effect it used to have rather than the one it has now: it was a
 * translucent sheet with a hairline edge, hardcoded, and every theme got the same
 * one. It is now a thin wrapper over [Modifier.thorSurface], so the twenty presets
 * differ here — flat and hard-edged on Retro, an opaque card with a real shadow on
 * Switch, a lit sheet on Vision — without any call site knowing which.
 *
 * The blur itself is applied by the *content behind* this surface via
 * [com.thor.core.designsystem.modifier.thorBackdropBlur], not here: Compose can
 * only blur a composable's own subtree, so a panel cannot blur what is underneath
 * it. Layering the blur on the background and drawing this sheet on top produces
 * the same result without a render-target round trip per frame.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ThorTheme.dimens.cornerRadius),
    color: Color = ThorTheme.colors.surface,
    alphaOverride: Float? = null,
    /** Where on the surface ramp this panel sits; drives tint and shadow depth. */
    level: SurfaceLevel = SurfaceLevel.RAISED,
    /**
     * Draws the theme's edge treatment.
     *
     * Set false for panels flush against a screen edge — a drawer, say — where an
     * outline would trace three sides and stop, which reads as a rendering fault
     * rather than as a border.
     */
    bordered: Boolean = true,
    /**
     * Forces this surface opaque regardless of the theme, for callers that have
     * their own reason to disable translucency — the dock's blur switch, for
     * instance, which would otherwise leave an unreadable transparent pill.
     */
    translucent: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.thorSurface(
            shape = shape,
            color = color,
            level = level,
            translucent = translucent,
            alphaOverride = alphaOverride,
            bordered = bordered,
        ),
        content = content,
    )
}
