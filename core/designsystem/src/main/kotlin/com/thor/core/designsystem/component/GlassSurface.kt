package com.thor.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * A translucent panel with a hairline edge and a soft top-light gradient.
 *
 * The blur itself is applied by the *content behind* this surface via
 * [Modifier.thorBackdropBlur], not here: Compose can only blur a composable's
 * own subtree, so a panel cannot blur what is underneath it. Layering the blur
 * on the background and drawing this translucent sheet on top produces the same
 * result without a render-target round trip per frame.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ThorTheme.dimens.cornerRadius),
    color: Color = ThorTheme.colors.surface,
    alphaOverride: Float? = null,
    borderWidth: Dp = 1.dp,
    /**
     * Forces this surface opaque regardless of the theme, for callers that have
     * their own reason to disable translucency — the dock's blur switch, for
     * instance, which would otherwise leave an unreadable transparent pill.
     */
    translucent: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = ThorTheme.materials
    val colors = ThorTheme.colors
    val alpha = when {
        !translucent -> 1f
        else -> alphaOverride ?: theme.surfaceAlpha
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(color = color.copy(alpha = color.alpha * alpha), shape = shape)
            .background(
                // A faint vertical sheen sells the "pane of glass" read; without
                // it a translucent panel looks like flat reduced opacity.
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (colors.isDark) 0.07f else 0.35f),
                        Color.Transparent,
                    ),
                ),
                shape = shape,
            )
            .border(
                width = borderWidth,
                color = colors.outline.copy(alpha = if (colors.isDark) 0.5f else 0.35f),
                shape = shape,
            ),
        content = content,
    )
}
