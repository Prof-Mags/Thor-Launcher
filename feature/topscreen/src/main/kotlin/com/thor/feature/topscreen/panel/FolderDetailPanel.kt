package com.thor.feature.topscreen.panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage

/**
 * The wordmark's box on the detail panel.
 *
 * Left-aligned and height-capped because pack logos vary enormously in aspect
 * ratio; fitting to width alone would give a wide PlayStation wordmark and a tall
 * Nintendo seal wildly different visual weight in the same slot.
 */
private const val LOGO_HEIGHT = 56
private const val LOGO_MAX_WIDTH = 320

/**
 * Detail view for a highlighted folder.
 *
 * @param platform the system this folder belongs to, when it is a platform's.
 *   Supplies the wordmark that replaces the typeset title — a logo *is* the name,
 *   so drawing both would be saying it twice.
 */
@Composable
fun FolderDetailPanel(
    folder: FolderEntry,
    children: List<GridEntry>,
    platform: Platform? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val accent = folder.accentArgb?.let(::Color) ?: colors.primary
    val logoUri = platform?.artwork?.logoUri

    /*
     * The header alone — no icon beside it.
     *
     * A large square of the folder's artwork used to sit here, and on a platform
     * folder that put the same image on screen three times over: this panel's own
     * backdrop is already the platform's hero, and the cell the user is looking at
     * on the other screen is already the icon. The third copy said nothing the
     * other two had not, and took most of the header's width to say it.
     */
    Row(
        modifier = modifier.fillMaxSize().padding(dimens.spacingLarge),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            if (logoUri != null) {
                ArtworkImage(
                    model = logoUri,
                    // The title remains the accessible name; the wordmark is only
                    // how it is drawn.
                    contentDescription = folder.title,
                    contentScale = ContentScale.Fit,
                    // Height-bound with a width ceiling, so the logo takes its own
                    // aspect ratio and lands left-aligned with the text beneath it
                    // — the Column already aligns to the start.
                    modifier = Modifier
                        .height(LOGO_HEIGHT.dp)
                        .widthIn(max = LOGO_MAX_WIDTH.dp),
                )
            } else {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onBackground,
                )
            }

            val games = children.filterIsInstance<GameEntry>()
            val platforms = games.map(GameEntry::platformId).distinct()

            Text(
                text = "${children.size} items" +
                    if (platforms.isNotEmpty()) " · ${platforms.size} platforms" else "",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
            )

            if (folder.description.isNotBlank()) {
                Text(
                    text = folder.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (folder.isSmart) {
                Text(
                    text = "Smart folder — contents update automatically",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }

            val recent = games
                .filter { it.stats.hasBeenPlayed }
                .sortedByDescending { it.stats.lastPlayedEpochMs ?: 0L }
                .take(6)

            if (recent.isNotEmpty()) {
                Text(
                    text = "RECENTLY PLAYED",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                    items(recent, key = GameEntry::id) { game ->
                        ArtworkImage(
                            model = game.metadata.artwork.cellImage,
                            contentDescription = game.title,
                            fallbackText = game.title,
                            modifier = Modifier
                                .height(110.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(dimens.cornerRadiusSmall)),
                        )
                    }
                }
            }
        }
    }
}
