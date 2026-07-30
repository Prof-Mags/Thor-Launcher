package com.thor.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.blend

/**
 * Loads artwork with a graceful fallback.
 *
 * Library artwork is frequently missing — a freshly scanned ROM set has none at
 * all — so the failure path is a first-class visual, not an error: a tinted
 * plate carrying the entry's initials. That keeps a grid legible before any
 * scraping has happened.
 */
@Composable
fun ArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackText: String? = null,
    fallbackTint: Color = ThorTheme.colors.primary,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeMillis: Int = ThorTheme.motion.detailMillis,
) {
    val context = LocalContext.current
    val request = remember(model, crossfadeMillis) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfadeMillis)
            .scale(Scale.FILL)
            // Hardware bitmaps are bound to the rendering context that uploaded
            // them. The grid is drawn inside a Presentation on the secondary
            // display, and a hardware bitmap decoded against the primary
            // display's context draws as nothing there — which is why artwork
            // appeared blank only after a scrape had given the cells real
            // images to load.
            .allowHardware(false)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val state = painter.state

    Box(modifier = modifier) {
        // Placeholder and fallback sit *behind* the image rather than replacing
        // it, because Coil resolves a request's target size from the bounds the
        // painter is actually drawn into. Swapping the painter out while loading
        // meant it was never drawn, its size never resolved, and the request
        // never completed — so every image stayed in Loading forever and the
        // cell rendered as an empty plate.
        when (state) {
            is AsyncImagePainter.State.Loading -> ShimmerPlaceholder(Modifier.fillMaxSize())
            is AsyncImagePainter.State.Success -> Unit
            else -> ArtworkFallback(
                text = fallbackText,
                tint = fallbackTint,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The plate drawn when artwork is missing or failed to load. */
@Composable
fun ArtworkFallback(
    text: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val initials = remember(text) { text?.toInitials() }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    tint.copy(alpha = 0.55f).blend(colors.surface, 0.35f),
                    colors.surfaceElevated,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.VideogameAsset,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Up to two initials taken from the first two significant words.
 * `"The Legend of Zelda"` -> `"LZ"`.
 */
private fun String.toInitials(): String? {
    val stop = setOf("the", "a", "an", "of", "and")
    val words = split(' ', ':', '-')
        .map(String::trim)
        .filter { it.isNotEmpty() && it.lowercase() !in stop }
    return when {
        words.isEmpty() -> null
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

/** A subtle sweeping highlight used while artwork loads. */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val animationsOn = ThorTheme.materials.animationsEnabled
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsOn) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    colors.surfaceElevated,
                    colors.surfaceElevated.blend(colors.onSurface, 0.08f),
                    colors.surfaceElevated,
                ),
                start = Offset(progress * 600f - 300f, 0f),
                end = Offset(progress * 600f, 300f),
            ),
        ),
    )
}
