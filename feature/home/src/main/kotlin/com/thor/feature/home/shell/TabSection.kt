package com.thor.feature.home.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.LauncherTab

/**
 * A section with nothing connected to it yet.
 *
 * Deliberately explicit rather than an empty grid. A section that rendered a
 * blank page would be indistinguishable from one whose content failed to load,
 * and the user would have no way to tell "this is not built" from "this is
 * broken" — so it says which, and says what would fill it.
 *
 * This is the honest half of a decision taken up front: the launcher's shape is
 * being settled before its content. Everything around the section is real — it is
 * themed, it takes controller focus, it transitions like every other surface, and
 * the nav bar treats it exactly as it treats Home. Only the source is missing, and
 * this says so out loud rather than implying otherwise.
 */
@Composable
fun EmptySection(
    tab: LauncherTab,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier.fillMaxSize().padding(dimens.spacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CARD_MAX_WIDTH.dp)
                .thorSurface(
                    shape = RoundedCornerShape(dimens.cornerRadius),
                    color = colors.surface,
                    level = SurfaceLevel.RAISED,
                )
                .padding(dimens.spacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Icon(
                imageVector = tab.emptyIcon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(ICON_SIZE.dp),
            )
            Text(
                text = tab.emptyTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = tab.emptyBody,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val LauncherTab.emptyIcon: ImageVector
    get() = when (this) {
        LauncherTab.STREAM -> Icons.Rounded.Sensors
        LauncherTab.MOVIES, LauncherTab.SHOWS -> Icons.Rounded.Movie
        // Home is never empty in this sense: it always has a grid, even if the
        // grid has nothing on it yet.
        LauncherTab.HOME -> Icons.Rounded.Sensors
    }

private val LauncherTab.emptyTitle: String
    get() = when (this) {
        LauncherTab.STREAM -> "No streaming sources"
        LauncherTab.MOVIES -> "No video library"
        LauncherTab.SHOWS -> "No shows library"
        LauncherTab.HOME -> "Nothing here"
    }

private val LauncherTab.emptyBody: String
    get() = when (this) {
        LauncherTab.STREAM ->
            "This section has no source connected yet. It is built and navigable — " +
                "what it lists has not been decided."

        LauncherTab.MOVIES, LauncherTab.SHOWS ->
            "This section has no source connected yet. It is built and navigable — " +
                "what it lists has not been decided."

        LauncherTab.HOME -> "Add apps or scan a ROM directory to fill the grid."
    }

private const val CARD_MAX_WIDTH = 320
private const val ICON_SIZE = 40
