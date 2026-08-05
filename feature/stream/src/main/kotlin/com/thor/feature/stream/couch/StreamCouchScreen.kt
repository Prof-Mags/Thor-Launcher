package com.thor.feature.stream.couch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.stream.ONLINE
import com.thor.feature.stream.StreamAddField
import com.thor.feature.stream.StreamCouchPage
import com.thor.feature.stream.StreamCouchZone
import com.thor.feature.stream.StreamUiState
import com.thor.feature.stream.tint

/**
 * Remote play as a television screen.
 *
 * A rail down the left saying what this section is and how the network looks, a
 * wall of machines in the middle, and the selected one's controls along the foot.
 * It replaced two handheld panels folded side by side, which is a different thing
 * from a screen designed for one display: the list was a column of rows a third
 * of the way across, the machine's own panel was a second column of prose beside
 * it, and the way to add a PC was a text field in the corner. From a sofa that
 * reads as a settings page rather than as a place to choose a computer.
 *
 * The grid is the answer to the only question this screen asks. A PC is a name,
 * an address and whether it is reachable — three short facts that fit in a card
 * — so cards let a household's machines be taken in at a glance instead of read
 * one row at a time.
 *
 * Adding one is a page of its own, [CouchAddHostPage]. It is the one thing here
 * with nothing to show until it is asked for, and a form that is always on screen
 * is a form permanently occupying a corner of a television for the few seconds a
 * year it is used.
 */
@Composable
fun StreamCouchScreen(
    state: StreamUiState,
    clientName: String,
    onHostSelected: (Int) -> Unit,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onAddHost: () -> Unit,
    onOpenAddHost: () -> Unit,
    onCloseAddHost: () -> Unit,
    onAddFieldFocused: (StreamAddField) -> Unit,
    onRefreshHost: (StreamHost) -> Unit,
    onRefreshAll: () -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
    onForgetHost: (StreamHost) -> Unit,
    onOpenHelp: () -> Unit,
    onHelpSectionFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    Row(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(colors.surfaceElevated.copy(alpha = FIELD_ALPHA), colors.background),
            ),
        ),
    ) {
        StreamCouchRail(
            state = state,
            onShowComputers = onCloseAddHost,
            onShowAddHost = onOpenAddHost,
            onShowHelp = onOpenHelp,
            modifier = Modifier.width(RAIL_WIDTH.dp).fillMaxHeight(),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (state.page) {
                StreamCouchPage.COMPUTERS -> CouchComputersPage(
                    state = state,
                    clientName = clientName,
                    onHostSelected = onHostSelected,
                    onOpenAddHost = onOpenAddHost,
                    onOpenHelp = onOpenHelp,
                    onRefreshHost = onRefreshHost,
                    onRefreshAll = onRefreshAll,
                    onStartStream = onStartStream,
                    onPairHost = onPairHost,
                    onCancelPairing = onCancelPairing,
                    onStopStream = onStopStream,
                    onForgetHost = onForgetHost,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                StreamCouchPage.HELP -> CouchHelpPage(
                    cursor = state.helpCursor,
                    clientName = clientName,
                    onSectionFocused = onHelpSectionFocused,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                StreamCouchPage.ADD_HOST -> CouchAddHostPage(
                    address = state.newAddress,
                    name = state.newName,
                    field = state.addField,
                    keyboardRequest = state.keyboardRequest,
                    onAddressChanged = onAddressChanged,
                    onNameChanged = onNameChanged,
                    onFieldFocused = onAddFieldFocused,
                    onAddHost = onAddHost,
                    onCancel = onCloseAddHost,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }

            CouchLegend(
                entries = when {
                    state.zone == StreamCouchZone.RAIL -> RAIL_LEGEND
                    state.zone == StreamCouchZone.HEADER -> HEADER_LEGEND
                    state.page == StreamCouchPage.ADD_HOST -> ADD_HOST_LEGEND
                    state.page == StreamCouchPage.HELP -> HELP_LEGEND
                    else -> COMPUTERS_LEGEND
                },
            )
        }
    }
}

// ---- The rail ----------------------------------------------------------------

/**
 * What this section is, where in it you are, and how the network looks.
 *
 * The counts used to be a bar across the top of the screen, above the list they
 * described. That is a header for a page rather than for a television: it spent
 * the widest band on the display on three two-digit figures, and pushed the
 * machines — the reason for the screen — into what was left. Down the side they
 * are read in the same glance as the destination list, and the width they take
 * is width the cards were never going to use.
 */
@Composable
private fun StreamCouchRail(
    state: StreamUiState,
    onShowComputers: () -> Unit,
    onShowAddHost: () -> Unit,
    onShowHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val online = state.hosts.count { state.statusOf(it) is HostStatus.Online }

    Column(
        modifier = modifier
            .background(colors.background.copy(alpha = RAIL_ALPHA))
            .padding(horizontal = RAIL_INSET.dp, vertical = RAIL_TOP_INSET.dp),
        verticalArrangement = Arrangement.spacedBy(RAIL_GAP.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = RAIL_ROW_PADDING.dp, bottom = RAIL_GAP.dp),
            horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(RAIL_MARK.dp),
            )
            Column {
                Text(
                    text = "PC Streaming",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Sunshine",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Lit when the controller is in the rail, marked when the page it names
        // is the one on screen. Two different facts, and on a television both
        // have to be visible at once: the cursor may be resting on Help while
        // the computers are still the page behind it.
        val focus = state.railFocus.takeIf { state.zone == StreamCouchZone.RAIL }

        RailDestination(
            icon = Icons.Rounded.Computer,
            label = "Computers",
            trailing = state.hosts.size.toString(),
            selected = state.page == StreamCouchPage.COMPUTERS,
            focused = focus == StreamCouchPage.COMPUTERS,
            onClick = onShowComputers,
        )
        RailDestination(
            icon = Icons.Rounded.Add,
            label = "Add a PC",
            trailing = null,
            selected = state.page == StreamCouchPage.ADD_HOST,
            focused = focus == StreamCouchPage.ADD_HOST,
            onClick = onShowAddHost,
        )
        RailDestination(
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            label = "Help",
            trailing = null,
            selected = state.page == StreamCouchPage.HELP,
            focused = focus == StreamCouchPage.HELP,
            onClick = onShowHelp,
        )

        Spacer(modifier = Modifier.weight(1f))

        /*
         * The network, reported the way the rest of the rail reports things.
         *
         * A dot and a word, because that is the whole of what is being asked
         * from across a room — is anything out there — with the figure that
         * qualifies it underneath. The counts used to be three metrics along the
         * top of the screen, which is a lot of chrome for two small numbers.
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ThorTheme.shapes.panel)
                .background(colors.surface.copy(alpha = STATS_ALPHA))
                .border(1.dp, colors.outline.copy(alpha = 0.2f), ThorTheme.shapes.panel)
                .padding(STATS_INSET.dp),
            verticalArrangement = Arrangement.spacedBy(STATS_GAP.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(STATUS_DOT.dp)
                        .clip(ThorTheme.shapes.pill)
                        .background(if (online > 0) ONLINE else colors.onSurfaceVariant),
                )
                Text(
                    text = "Network status",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = when {
                    online == 0 -> "Nothing answering"
                    online == 1 -> "1 PC online"
                    else -> "$online PCs online"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatLine(
                icon = Icons.Rounded.Link,
                value = state.readyCount.toString(),
                label = "Paired and ready",
            )
        }
    }
}

@Composable
private fun RailDestination(
    icon: ImageVector,
    label: String,
    trailing: String?,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val lit = selected || focused || hover.isHovered
    val shape = ThorTheme.shapes.small

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = focused || (hover.isHovered && !selected), shape = shape)
            .clip(shape)
            .background(if (lit) colors.surfaceHighest else Color.Transparent)
            .then(
                if (focused) {
                    Modifier.border(1.dp, colors.cursor, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = RAIL_ROW_PADDING.dp, vertical = RAIL_ROW_PADDING_V.dp),
        horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(RAIL_DESTINATION_ICON.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.cursor else colors.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = HINT_ALPHA),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatLine(icon: ImageVector, value: String, label: String) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.cursor,
            modifier = Modifier.size(STAT_ICON.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // Wrapped rather than clipped, for the same reason the catalogue's
            // rail wraps its captions: this column is narrow and fixed, and a
            // figure captioned "Paired and rea" says nothing at all.
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
