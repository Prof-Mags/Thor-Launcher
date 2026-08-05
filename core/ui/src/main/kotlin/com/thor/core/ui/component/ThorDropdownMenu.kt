package com.thor.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The launcher's dropdown, in the same idiom as the side and long-press menus.
 *
 * Material's own menu is a white-ish elevated sheet with dense single-line
 * rows, which read as a different product wherever one opened over the
 * launcher. This is the same panel those two menus use — the highest surface,
 * the panel corner radius, an accent marker down the leading edge of the live
 * row — so a menu looks like part of the launcher regardless of which screen
 * opened it.
 *
 * A wrapper around [DropdownMenu] rather than a replacement: positioning,
 * dismissal and the popup window are all fiddly and correct already. Only the
 * surface and the rows are ours.
 */
@Composable
fun ThorDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThorTheme.colors

    /*
     * The size the menu was opened at, carried into its own window.
     *
     * A dropdown is a popup, and a popup is a separate composition with a Compose
     * owner of its own — which provides [LocalDensity] from that view's resources
     * and discards whatever the tree above it provided. Couch mode composes the
     * whole launcher through a scaled density and the movies section scales its
     * content again on top of that, so a menu raised from either came up at the
     * panel's raw size: arithmetically correct, and visibly a different interface
     * from the screen that opened it.
     *
     * A no-op everywhere the two agree, which is everywhere but couch mode.
     */
    val openedAt = LocalDensity.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = ThorTheme.shapes.panel,
        containerColor = colors.surfaceHighest,
        // Flat. The shadow under Material's default menu is what made it read as
        // a sheet floating above the launcher rather than a panel belonging to
        // it; the surface step does the separating instead.
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.background(colors.surfaceHighest),
    ) {
        val column = this
        CompositionLocalProvider(LocalDensity provides openedAt) {
            column.content()
        }
    }
}

/**
 * One row of a [ThorDropdownMenu].
 *
 * @param selected the option currently in force, marked rather than merely
 *   tinted so the current value is findable in a long list at a glance
 * @param description a second line, for options whose label does not say enough
 *   on its own. This is where the detail that used to be crammed into the row's
 *   button belongs — there is room for it here and there was none there.
 */
@Composable
fun ThorDropdownItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    selected: Boolean = false,
) {
    ThorMenuRow(
        label = label,
        onClick = onClick,
        modifier = modifier,
        description = description,
        icon = icon,
        trailing = trailing,
        selected = selected,
    )
}
