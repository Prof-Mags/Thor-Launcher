package com.thor.core.designsystem.modifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * Blurs a composable so translucent panels drawn above it read as glass.
 *
 * A no-op when blur is unavailable (pre-API 31) or disabled, so call sites never
 * branch on capability themselves.
 */
@Composable
fun Modifier.thorBackdropBlur(radiusOverride: Dp? = null): Modifier {
    val materials = ThorTheme.materials
    val radius = radiusOverride ?: materials.blurRadius
    return if (materials.isBlurActive && radius > 0.dp) {
        blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
}
