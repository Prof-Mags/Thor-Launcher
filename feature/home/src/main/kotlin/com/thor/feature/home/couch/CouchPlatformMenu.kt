package com.thor.feature.home.couch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.THOR_MENU_ICON_TILE
import com.thor.core.ui.component.ThorMenuRow
import com.thor.feature.home.shell.icon

data class CouchPlatformSummary(
    val gameCount: Int,
    val previewUri: String?,
)

/** Start-button platform drawer used only by Couch Mode's Home section. */
@Composable
fun CouchPlatformMenu(
    visible: Boolean,
    platforms: List<Platform>,
    summaries: Map<String, CouchPlatformSummary>,
    focusedIndex: Int,
    onPlatformSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val motion = ThorTheme.motion
    val listState = rememberLazyListState()
    val safeIndex = focusedIndex.coerceIn(0, (platforms.size - 1).coerceAtLeast(0))

    LaunchedEffect(visible, safeIndex, platforms.size) {
        if (visible && platforms.isNotEmpty()) listState.animateScrollToItem(safeIndex)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.tweenSpec(motion.panelMillis)),
        exit = fadeOut(motion.tweenSpec(motion.panelMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(motion.tweenSpec(motion.panelMillis)) { -it },
                exit = slideOutHorizontally(motion.tweenSpec(motion.panelMillis)) { -it },
            ) {
                GlassSurface(
                    shape = RectangleShape,
                    bordered = false,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(PLATFORM_MENU_WIDTH.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "PLATFORMS",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.cursor,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${summaries.values.sumOf { it.gameCount }} games  ·  Start closes",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            itemsIndexed(
                                items = platforms,
                                key = { _, platform -> platform.id },
                            ) { index, platform ->
                                val focused = index == safeIndex
                                val accent = Color(platform.accentArgb)
                                val summary = summaries[platform.id]
                                ThorMenuRow(
                                    label = platform.name,
                                    description = platform.subtitle
                                        .takeIf(String::isNotBlank),
                                    trailing = "${summary?.gameCount ?: 0} games",
                                    focused = focused,
                                    // The system's own colour, in the slot the
                                    // theme accent takes in the other menus.
                                    accent = accent,
                                    leading = {
                                        Box(
                                            modifier = Modifier
                                                .size(THOR_MENU_ICON_TILE.dp)
                                                .clip(ThorTheme.shapes.small)
                                                .background(colors.surfaceHighest),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val icon = platform.artwork.iconUri
                                            val logo = platform.artwork.logoUri
                                            val image = icon ?: logo ?: summary?.previewUri
                                            if (image != null) {
                                                ArtworkImage(
                                                    model = image,
                                                    contentDescription = platform.name,
                                                    fallbackText = platform.shortName,
                                                    contentScale = if (icon == null && logo != null) {
                                                        ContentScale.Fit
                                                    } else {
                                                        ContentScale.Crop
                                                    },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Text(
                                                    text = platform.shortName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent,
                                                    fontWeight = FontWeight.Black,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onPlatformSelected(index) },
                                )
                            }

                            if (platforms.isEmpty()) {
                                item {
                                    Text(
                                        text = "No game platforms found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PLATFORM_MENU_WIDTH = 310
