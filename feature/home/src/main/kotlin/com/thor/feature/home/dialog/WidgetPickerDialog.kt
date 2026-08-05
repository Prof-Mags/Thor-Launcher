package com.thor.feature.home.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.data.widget.WidgetOption
import com.thor.feature.home.grid.AppIcon

/** State of the widget picker, raised from an empty cell's menu. */
@Immutable
data class WidgetPickerState(
    val visible: Boolean = false,
    /**
     * Null while the device is still being asked what it has.
     *
     * Distinct from an empty list, which means the honest and quite possible
     * answer that nothing installed offers a widget.
     */
    val options: List<WidgetOption>? = null,
    val focusedIndex: Int = 0,
) {
    val rowCount: Int get() = options?.size ?: 0
}

/**
 * Chooses a widget to place.
 *
 * A flat list rather than the platform's own picker. `ACTION_APPWIDGET_PICK`
 * exists and would be less code, but it is a system dialog: it arrives in the
 * system's theme, at the system's text size, on whichever display the system
 * feels like — none of which is what a launcher drawn for a handheld's bottom
 * panel wants, and the last of those is a dialog that lands on the wrong screen.
 *
 * Each row says the size it will take, because that is the one thing about a
 * widget the user cannot see until it is already on the grid.
 */
@Composable
fun WidgetPickerDialog(
    state: WidgetPickerState,
    onPick: (WidgetOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            modifier = Modifier
                .width(CARD_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = "Add a widget",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Placed where you pressed, if it fits there",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                val options = state.options
                when {
                    options == null -> PickerMessage(loading = true, message = "Looking…")

                    options.isEmpty() -> PickerMessage(
                        loading = false,
                        message = "None of your installed apps offer a widget.",
                    )

                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        options.forEachIndexed { index, option ->
                            WidgetRow(
                                option = option,
                                focused = index == state.focusedIndex,
                                onClick = { onPick(option) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(loading: Boolean, message: String) {
    val colors = ThorTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(MESSAGE_HEIGHT.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.cursor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun WidgetRow(
    option: WidgetOption,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .thorCursor(focused = focused, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ROW_ICON.dp)
                .clip(shape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            /*
             * The provider's own preview when it ships one, and the app's icon
             * when it does not.
             *
             * Worth the fallback rather than leaving a blank square: a preview is
             * optional in the manifest and plenty of widgets have none, which on
             * an icon-less list makes half the rows indistinguishable.
             */
            val preview = option.preview
            if (preview != null) {
                val bitmap = remember(preview) { preview.asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            } else {
                AppIcon(
                    packageName = option.packageName,
                    title = option.label,
                    shape = shape,
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = option.appLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Text(
                // The multiplication sign, not the letter: this is a size.
                text = " ${option.spanColumns}×${option.spanRows}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private const val CARD_WIDTH = 380
private const val CONTENT_MAX_HEIGHT = 260
private const val MESSAGE_HEIGHT = 64
private const val ROW_ICON = 40
