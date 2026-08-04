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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
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
    alignment: Alignment = Alignment.Center,
    crossfadeMillis: Int = ThorTheme.motion.detailMillis,
) {
    val context = LocalContext.current
    /*
     * `rememberAsyncImagePainter` does not infer its target bounds on its own.
     * Without this resolver a 100px grid cell decoded the original 4K box-art
     * file, then threw almost all of those pixels away during draw.  The same
     * component also fills the top panel, where the resolver naturally asks for
     * the larger size, so image quality stays exactly where it is visible.
     */
    /*
     * The size is observed from layout rather than taken from Coil's own
     * resolver, which is `internal` and cannot be referenced from here. Same
     * effect by the public route: the first layout pass reports the bounds, the
     * request is rebuilt once against them, and every decode afterwards is at
     * the size actually drawn.
    */
    /*
     * Null until the first layout pass, and that is the point.
     *
     * This started at [Size.ORIGINAL], which does not mean "not measured yet" to
     * Coil — it means "decode the file at its full size". So every image was
     * fetched *twice*: once at whatever the source happened to be, which for
     * scraped box art is regularly 2000px or more, and then again at the cell
     * size once the bounds arrived. The first decode is pure waste, and it is
     * waste on the critical path — it holds the decoder, the disk and several
     * megabytes of heap while the image the user is actually waiting for queues
     * behind it. On a page of thirty cells that is thirty full-resolution decodes
     * nobody ever sees, which is exactly the second-long delay and the blank
     * plates that go with it.
     *
     * Waiting costs one frame and no request at all, because the `Image` below is
     * always drawn — see the placeholder note — so its bounds arrive whether or
     * not there is anything to show yet.
     */
    var targetSize by remember(model) { mutableStateOf<Size?>(null) }
    // A Fit/Inside draw must also ask Coil for a fitted decode. Always decoding
    // with FILL could crop the bitmap before Compose received it, so changing
    // only ContentScale still left screenshot and logo edges missing.
    val requestScale = if (
        contentScale == ContentScale.Fit || contentScale == ContentScale.Inside
    ) {
        Scale.FIT
    } else {
        Scale.FILL
    }
    val request = remember(model, crossfadeMillis, targetSize, requestScale) {
        targetSize?.let { size ->
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(crossfadeMillis)
                .scale(requestScale)
                .size(size)
                // Hardware bitmaps are bound to the rendering context that
                // uploaded them. The grid is drawn inside a Presentation on the
                // secondary display, and a hardware bitmap decoded against the
                // primary display's context draws as nothing there — which is
                // why artwork appeared blank only after a scrape had given the
                // cells real images to load.
                .allowHardware(false)
                .build()
        }
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
        when {
            /*
             * Before the bounds arrive there is no request, and a painter with
             * nothing to load reports *failure* rather than loading. Reading that
             * literally would flash the initials plate over every cell for a
             * frame on the way to the artwork — the "placeholder flash" this
             * whole path is meant not to have. Not yet asked is still loading.
             */
            targetSize == null || state is AsyncImagePainter.State.Loading ->
                ShimmerPlaceholder(Modifier.fillMaxSize())

            state is AsyncImagePainter.State.Success -> Unit
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
            alignment = alignment,
            // Where the bounds come from. Reported from the target the image is
            // actually drawn into, so a cell asks for a cell-sized decode and
            // the top panel asks for a panel-sized one.
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        targetSize = Size(size.width, size.height)
                    }
                },
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
