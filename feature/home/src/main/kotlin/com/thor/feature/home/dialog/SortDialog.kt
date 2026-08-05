package com.thor.feature.home.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.SortOrder
import com.thor.core.ui.component.ThorMenuRow
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * Reorders the grid.
 *
 * Sorting applies immediately to the layout rather than being stored as a
 * browsing preference — the grid is hand-arranged by default, so "sort" here
 * means "rewrite my arrangement in this order", which is the only reading that
 * does anything visible.
 */
@Composable
fun SortDialog(
    visible: Boolean,
    currentOrder: SortOrder,
    descending: Boolean,
    focusedIndex: Int,
    onPick: (SortOrder) -> Unit,
    onToggleDirection: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    // MANUAL is excluded: it means "however the user arranged it", which is the
    // state sorting replaces, so offering it would be a no-op.
    val orders = SortOrder.entries.filterNot { it == SortOrder.MANUAL }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(dimens.cornerRadiusLarge),
            // Highest surface: these sit over an already-elevated panel, and
            // reusing the base surface made them read as part of it.
            color = ThorTheme.colors.surfaceHighest,
            modifier = Modifier
                .width(CARD_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dimens.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sort grid",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val directionHover = rememberPointerHover()
                    Row(
                        modifier = Modifier
                            .clip(ThorTheme.shapes.pill)
                            .background(
                                if (directionHover.isHovered) {
                                    colors.surfaceHighest
                                } else {
                                    Color.Transparent
                                },
                            )
                            .pointerHover(directionHover)
                            .clickable(onClick = onToggleDirection)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (descending) {
                                Icons.Rounded.ArrowDownward
                            } else {
                                Icons.Rounded.ArrowUpward
                            },
                            contentDescription = null,
                            tint = colors.cursor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (descending) "Descending" else "Ascending",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.cursor,
                        )
                    }
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    orders.forEachIndexed { index, order ->
                        ThorMenuRow(
                            label = order.label,
                            // The order in force is marked the way every other
                            // menu marks its current value, rather than by a
                            // tick in a column that is empty on every other row.
                            selected = order == currentOrder,
                            focused = index == focusedIndex,
                            onClick = { onPick(order) },
                        )
                    }
                }
            }
        }
    }
}

private const val CARD_WIDTH = 300
