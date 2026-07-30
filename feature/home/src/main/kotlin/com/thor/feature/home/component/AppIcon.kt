package com.thor.feature.home.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.thor.core.ui.component.ArtworkFallback
import com.thor.core.designsystem.theme.ThorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders an installed app's launcher icon.
 *
 * The icon is rasterised off the main thread and cached in composition state:
 * `PackageManager.getApplicationIcon` does disk I/O and adaptive-icon
 * compositing, and calling it during layout for every visible cell is enough to
 * drop frames on a dense grid.
 */
@Composable
fun AppIcon(
    packageName: String,
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                drawable.toBitmap(
                    width = ICON_PX,
                    height = ICON_PX,
                ).asImageBitmap()
            }.getOrNull()
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxSize()
                .clip(shape),
        )
    } else {
        ArtworkFallback(
            text = title,
            tint = ThorTheme.colors.secondary,
            modifier = modifier.fillMaxSize().clip(shape),
        )
    }
}

/**
 * Rasterisation size.
 *
 * 192px covers the largest cell the grid can produce at minimum column count on
 * the Thor's panel without wasting memory on a full-resolution adaptive icon.
 */
private const val ICON_PX = 192
