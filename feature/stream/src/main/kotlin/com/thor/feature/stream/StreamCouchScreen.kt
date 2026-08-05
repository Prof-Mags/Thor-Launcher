package com.thor.feature.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.stream.PairingState

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

// ---- The computers page ------------------------------------------------------

@Composable
private fun CouchComputersPage(
    state: StreamUiState,
    clientName: String,
    onHostSelected: (Int) -> Unit,
    onOpenAddHost: () -> Unit,
    onOpenHelp: () -> Unit,
    onRefreshHost: (StreamHost) -> Unit,
    onRefreshAll: () -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
    onForgetHost: (StreamHost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    Column(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = SCREEN_TOP_INSET.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SECTION_GAP.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Computers on your network",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Select a computer to start streaming its screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            /*
             * The page's own controls, drawn from the state rather than written
             * out here, so the row the cursor walks and the row on screen cannot
             * disagree about what is on it.
             *
             * Unfilled: they sit on the page beside its title rather than inside
             * a panel, and a slab of surface up here reads as a second heading
             * arguing with the first. The outline is enough to say they are
             * pressable, and the fill arrives with the cursor.
             */
            Row(horizontalArrangement = Arrangement.spacedBy(HEADER_ACTION_GAP.dp)) {
                state.headerActions.forEach { action ->
                    StreamActionButton(
                        label = when (action) {
                            StreamHeaderAction.HELP -> "HELP"
                            StreamHeaderAction.REFRESH -> "REFRESH"
                        },
                        icon = when (action) {
                            StreamHeaderAction.HELP -> Icons.AutoMirrored.Rounded.HelpOutline
                            StreamHeaderAction.REFRESH -> Icons.Rounded.Refresh
                        },
                        quiet = true,
                        controllerFocused = state.zone == StreamCouchZone.HEADER &&
                            state.focusedHeaderAction == action,
                        onClick = when (action) {
                            StreamHeaderAction.HELP -> onOpenHelp
                            StreamHeaderAction.REFRESH -> onRefreshAll
                        },
                        // Sized to their own labels. With no fill behind them, a
                        // shared width is not a tidy pair of boxes any more — it
                        // is "HELP" adrift in the middle of nothing.
                        modifier = Modifier.width(
                            when (action) {
                                StreamHeaderAction.HELP -> HEADER_HELP_WIDTH.dp
                                StreamHeaderAction.REFRESH -> HEADER_ACTION_WIDTH.dp
                            },
                        ),
                    )
                }
            }
        }

        if (state.hosts.isEmpty()) {
            CouchDiscoveryPanel(
                // The panel's button is what Confirm does while the cursor is on
                // an empty page, so it wears the ring that says so.
                focused = state.zone == StreamCouchZone.GRID,
                onAddHost = onOpenAddHost,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            CouchHostGrid(
                state = state,
                onHostSelected = onHostSelected,
                onOpenAddHost = onOpenAddHost,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        CouchHostBand(
            state = state,
            clientName = clientName,
            onOpenAddHost = onOpenAddHost,
            onRefreshHost = onRefreshHost,
            onStartStream = onStartStream,
            onPairHost = onPairHost,
            onCancelPairing = onCancelPairing,
            onStopStream = onStopStream,
            onForgetHost = onForgetHost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The machines, as a wall of cards.
 *
 * Built out of a column of rows rather than a grid component, because the cursor
 * here is a single index into the host list and the row it is on is arithmetic on
 * that index — see [streamGridTarget]. One list to scroll and one number to
 * scroll it by is less to keep in step than a grid with a cursor of its own.
 *
 * The last cell is always "add a PC". It is where the eye ends up after reading
 * the machines already there, which is exactly when somebody notices one missing.
 */
@Composable
private fun CouchHostGrid(
    state: StreamUiState,
    onHostSelected: (Int) -> Unit,
    onOpenAddHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Every host, and then the add tile, laid out in rows of the grid's width.
    val gridRows = remember(state.hosts.size) {
        (0..state.hosts.size).chunked(STREAM_COUCH_COLUMNS)
    }

    LaunchedEffect(state.cursor, gridRows.size) {
        if (gridRows.isNotEmpty()) {
            listState.animateScrollToItem(
                (state.cursor / STREAM_COUCH_COLUMNS).coerceIn(0, gridRows.lastIndex),
            )
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val cardHeight = couchHostCardHeight(maxHeight)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CARD_GROWTH.dp),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
        ) {
            itemsIndexed(gridRows, key = { index, _ -> index }) { _, cells ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp),
                ) {
                    cells.forEach { cell ->
                        val host = state.hosts.getOrNull(cell)
                        if (host == null) {
                            CouchAddCard(
                                selected = state.zone == StreamCouchZone.ADD,
                                onClick = onOpenAddHost,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        } else {
                            CouchHostCard(
                                host = host,
                                status = state.statusOf(host),
                                selected = cell == state.cursor &&
                                    state.zone == StreamCouchZone.GRID,
                                connecting = state.connecting && cell == state.cursor,
                                onClick = { onHostSelected(cell) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                    // Keeps a short final row's cards the width of the others,
                    // rather than letting three stretch into the space of four.
                    repeat(STREAM_COUCH_COLUMNS - cells.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CouchHostCard(
    host: StreamHost,
    status: HostStatus,
    selected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val hover = rememberPointerHover()
    val lit = selected || hover.isHovered
    val tint = status.tint(colors.error, colors.onSurfaceVariant)
    val online = status as? HostStatus.Online
    val name = online?.name?.takeIf(String::isNotBlank) ?: host.displayName

    Column(
        modifier = modifier
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(if (lit) colors.surfaceHighest else colors.surface.copy(alpha = CARD_ALPHA))
            .border(
                width = if (lit) 2.dp else 1.dp,
                color = if (lit) colors.cursor else colors.outline.copy(alpha = 0.24f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(CARD_PADDING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(STATUS_DOT.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(tint),
            )
            // The dot carries the colour and the word carries the meaning. Both
            // tinted, a wall of cards becomes a wall of coloured text, and the
            // one thing that should stand out on a card - the machine's name -
            // stops being the brightest thing on it.
            Text(
                text = if (connecting) "Connecting" else status.couchLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The mark a paired machine carries. Where a media app would put a
            // favourite star, which a PC has no use for: there is one right
            // answer to "which of my computers do I want", and it is whichever
            // is on and already paired.
            if (online?.paired == true) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = "Paired",
                    tint = colors.cursor,
                    modifier = Modifier.size(PAIRED_ICON.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Outlined.DesktopWindows,
            contentDescription = null,
            tint = if (lit) colors.cursor else colors.cursor.copy(alpha = RESTING_ART_ALPHA),
            modifier = Modifier.size(CARD_ICON.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        // As the machine calls itself, or as the user labelled it. Not shouted
        // in capitals: a Sunshine host is usually named in them already, and
        // forcing them turns "Living room PC" into something nobody typed.
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = host.address,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CARD_PILL_GAP.dp))
        Text(
            text = if (host.discovered) "FOUND" else "SAVED",
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor.copy(alpha = 0.12f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CouchAddCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val hover = rememberPointerHover()
    val lit = selected || hover.isHovered

    Column(
        modifier = modifier
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(if (lit) colors.surfaceHighest else colors.surface.copy(alpha = ADD_CARD_ALPHA))
            .border(
                width = if (lit) 2.dp else 1.dp,
                color = colors.cursor.copy(alpha = if (lit) 1f else 0.3f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(CARD_PADDING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ADD_MARK.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(ADD_ICON.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "ADD A PC",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "By address",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The selected machine, and what can be done to it.
 *
 * Along the foot rather than in a column beside the grid: every one of these
 * controls acts on whatever the cursor is on above, and a panel to the side of
 * the thing it describes has to be connected by the reader. Under it, the cursor
 * moves down onto the buttons and they are plainly about the card it left.
 */
@Composable
private fun CouchHostBand(
    state: StreamUiState,
    clientName: String,
    onOpenAddHost: () -> Unit,
    onRefreshHost: (StreamHost) -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
    onForgetHost: (StreamHost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val host = state.selected
    val status = host?.let(state::statusOf)
    val focused = state.zone == StreamCouchZone.ACTIONS

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(BAND_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(BAND_GAP.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BAND_GAP.dp),
            ) {
                val tint = status?.tint(colors.error, colors.onSurfaceVariant)
                    ?: colors.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .size(BAND_MARK.dp)
                        .clip(ThorTheme.shapes.small)
                        .background(tint.copy(alpha = 0.13f))
                        .border(1.dp, tint.copy(alpha = 0.44f), ThorTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (host == null) Icons.Rounded.Wifi else Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(BAND_ICON.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (status as? HostStatus.Online)?.name?.takeIf(String::isNotBlank)
                            ?: host?.displayName
                            ?: "Make sure Sunshine is running on your PC",
                        style = MaterialTheme.typography.titleMedium,
                        // Coloured when it is an instruction rather than a name:
                        // with no PC selected this line is the one thing on the
                        // screen asking to be acted on.
                        color = if (host == null) colors.cursor else colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (host == null || status == null) {
                            "Hosts on this network are found automatically. A PC on a " +
                                "VPN or another subnet can be added by address."
                        } else {
                            selectedHostMessage(state, status, clientName)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            state.error != null || status is HostStatus.Offline -> colors.error
                            else -> colors.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (host == null || status == null) {
                    StreamActionButton(
                        label = "ADD A PC",
                        icon = Icons.Rounded.Add,
                        primary = true,
                        // The band is a stop on the way down even with no PC to
                        // act on, because it still has this one control on it.
                        controllerFocused = focused,
                        onClick = onOpenAddHost,
                        modifier = Modifier.width(BAND_ACTION_WIDTH.dp),
                    )
                } else {
                    CouchHostActions(
                        state = state,
                        host = host,
                        status = status,
                        focused = focused,
                        onRefreshHost = onRefreshHost,
                        onStartStream = onStartStream,
                        onPairHost = onPairHost,
                        onCancelPairing = onCancelPairing,
                        onStopStream = onStopStream,
                        onForgetHost = onForgetHost,
                    )
                }
            }

            when (val pairing = state.pairing) {
                is PairingState.AwaitingPin -> CouchPairingPin(pin = pairing.pin)
                is PairingState.Failed -> CouchStrip(
                    text = "Pairing failed while ${pairing.step}: ${pairing.reason}",
                    error = true,
                )
                PairingState.Verifying -> CouchStrip("Verifying this PC...", error = false)
                PairingState.Paired -> CouchStrip(
                    text = "Pairing complete. Checking the PC again...",
                    error = false,
                )
                PairingState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun CouchHostActions(
    state: StreamUiState,
    host: StreamHost,
    status: HostStatus,
    focused: Boolean,
    onRefreshHost: (StreamHost) -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
    onForgetHost: (StreamHost) -> Unit,
) {
    val online = status as? HostStatus.Online

    Row(horizontalArrangement = Arrangement.spacedBy(BAND_ACTION_GAP.dp)) {
        if (state.connecting) {
            StreamActionButton(
                label = "CONNECTING",
                icon = Icons.Rounded.Link,
                enabled = false,
                primary = true,
                onClick = {},
                modifier = Modifier.width(BAND_ACTION_WIDTH.dp),
            )
            return@Row
        }

        state.hostActions.forEach { action ->
            StreamActionButton(
                label = streamActionLabel(action, online),
                icon = when (action) {
                    StreamHostAction.START_STREAM -> Icons.Rounded.PlayArrow
                    StreamHostAction.STOP_SESSION -> Icons.Rounded.Stop
                    StreamHostAction.REFRESH -> Icons.Rounded.Refresh
                    StreamHostAction.PAIR -> Icons.Rounded.Link
                    StreamHostAction.CANCEL_PAIRING -> Icons.Rounded.Close
                    StreamHostAction.FORGET -> Icons.Rounded.DeleteOutline
                },
                primary = action == StreamHostAction.START_STREAM ||
                    (action == StreamHostAction.REFRESH && online?.paired != true),
                destructive = action == StreamHostAction.STOP_SESSION ||
                    action == StreamHostAction.FORGET,
                // Lit only while the controller is actually on this row. The
                // cursor is on the grid the rest of the time, and a button
                // wearing the focus ring then is a button that looks pressable
                // by a press that would do something else entirely.
                controllerFocused = focused && state.focusedHostAction == action,
                onClick = when (action) {
                    StreamHostAction.START_STREAM -> onStartStream
                    StreamHostAction.STOP_SESSION -> onStopStream
                    StreamHostAction.REFRESH -> ({ onRefreshHost(host) })
                    StreamHostAction.PAIR -> onPairHost
                    StreamHostAction.CANCEL_PAIRING -> onCancelPairing
                    StreamHostAction.FORGET -> ({ onForgetHost(host) })
                },
                modifier = Modifier.width(BAND_ACTION_WIDTH.dp),
            )
        }
    }
}

/**
 * The code the user carries to the PC.
 *
 * Set at a size that can be read while standing up and walking away from the
 * television, because that is literally what is being asked: the PIN is typed
 * into Sunshine on the other machine while this handshake waits.
 */
@Composable
private fun CouchPairingPin(pin: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.cursor.copy(alpha = 0.11f))
            .border(1.dp, colors.cursor.copy(alpha = 0.46f), ThorTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PAIRING CODE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Enter this in Sunshine on the PC, under PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = pin,
            style = MaterialTheme.typography.displaySmall,
            color = colors.cursor,
            fontWeight = FontWeight.Black,
            letterSpacing = PIN_TRACKING.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CouchStrip(text: String, error: Boolean) {
    val colors = ThorTheme.colors
    val tint = if (error) colors.error else colors.cursor
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * What to do when the network has produced nothing.
 *
 * The steps matter more here than anywhere else in the launcher: the user is
 * across a room from the machine that needs fixing, and "no PCs found" would
 * send them to it with nothing to try.
 */
@Composable
private fun CouchDiscoveryPanel(
    focused: Boolean,
    onAddHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EMPTY_PADDING.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(EMPTY_MARK.dp)
                    .clip(ThorTheme.shapes.panel)
                    .background(colors.cursor.copy(alpha = 0.12f))
                    .border(1.dp, colors.cursor.copy(alpha = 0.42f), ThorTheme.shapes.panel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = colors.cursor,
                    modifier = Modifier.size(EMPTY_ICON.dp),
                )
            }
            Text(
                text = "Looking for computers",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "Keep Sunshine running on the PC. Anything on this network " +
                    "appears here on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiscoveryStep("01", "START SUNSHINE")
                DiscoveryStep("02", "SAME NETWORK")
                DiscoveryStep("03", "OR ADD AN ADDRESS")
            }
            StreamActionButton(
                label = "ADD A PC BY ADDRESS",
                icon = Icons.Rounded.Add,
                primary = true,
                controllerFocused = focused,
                onClick = onAddHost,
                modifier = Modifier.padding(top = 18.dp).width(EMPTY_ACTION_WIDTH.dp),
            )
        }
    }
}

@Composable
private fun DiscoveryStep(number: String, label: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---- The help page -----------------------------------------------------------

/**
 * How any of this works, on the screen it is needed on.
 *
 * Every fact here is one the user would otherwise have to already know: that
 * Sunshine is what answers, that pairing is a one-time exchange with the PIN
 * travelling the other way, that a stream is the whole desktop rather than a
 * chosen game, that leaving does not end the session. None of it is discoverable
 * from a list of computers, and the machine that would have explained it is the
 * one across the room.
 *
 * Read a section at a time rather than scrolled freely. A pad has no scroll bar,
 * so a page of continuous prose has no way of saying how much of it is left —
 * whereas a cursor stepping through numbered sections says exactly that, and the
 * page follows it.
 */
@Composable
private fun CouchHelpPage(
    cursor: Int,
    clientName: String,
    onSectionFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val listState = rememberLazyListState()
    val safeCursor = cursor.coerceIn(0, STREAM_HELP_SECTIONS.lastIndex)

    LaunchedEffect(safeCursor) { listState.animateScrollToItem(safeCursor) }

    Row(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = SCREEN_TOP_INSET.dp),
        horizontalArrangement = Arrangement.spacedBy(FORM_GAP.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(FORM_ROW_GAP.dp),
        ) {
            Text(
                text = "Streaming a PC",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "Loki streams from Sunshine, the same host software Moonlight " +
                    "talks to. Everything below is done once.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = CARD_GROWTH.dp),
                verticalArrangement = Arrangement.spacedBy(HELP_GAP.dp),
            ) {
                itemsIndexed(
                    items = STREAM_HELP_SECTIONS,
                    key = { _, section -> section.title },
                ) { index, section ->
                    HelpSection(
                        number = index + 1,
                        section = section,
                        clientName = clientName,
                        focused = index == safeCursor,
                        onClick = { onSectionFocused(index) },
                    )
                }
            }
        }

        CouchPadReference(modifier = Modifier.width(HELP_WIDTH.dp).fillMaxHeight())
    }
}

@Composable
private fun HelpSection(
    number: Int,
    section: StreamHelpSection,
    clientName: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val shape = ThorTheme.shapes.panel
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .thorCursor(focused = lit, shape = shape)
            .clip(shape)
            .background(
                if (lit) colors.surfaceHighest else colors.surface.copy(alpha = CARD_ALPHA),
            )
            .clickable(onClick = onClick),
    ) {
        // The accent down the leading edge, as on every other live row in the
        // launcher. On a page with no buttons it is the only thing saying where
        // the cursor is.
        Box(
            modifier = Modifier
                .width(HELP_MARKER.dp)
                .fillMaxHeight()
                .background(if (lit) colors.cursor else Color.Transparent),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(HELP_PADDING.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(HELP_NUMBER.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(colors.cursor.copy(alpha = if (lit) 0.28f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = section.body.replace(CLIENT_NAME_TOKEN, clientName),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What each button does once a stream is up.
 *
 * Beside the instructions rather than inside them, because it is the part
 * somebody comes back for. The rest of this page is read once; this is looked up.
 */
@Composable
private fun CouchPadReference(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HELP_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(HELP_GAP.dp),
        ) {
            Text(
                text = "While streaming",
                style = MaterialTheme.typography.titleMedium,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            PAD_REFERENCE.forEach { (button, action) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = button,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cursor,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .width(HELP_BUTTON_WIDTH.dp)
                            .clip(ThorTheme.shapes.small)
                            .background(colors.cursor.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outline.copy(alpha = 0.2f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(HELP_NUMBER.dp),
                )
                Text(
                    text = "The trackpad and keyboard appear on the bottom screen, and " +
                        "not in couch mode: docked to a television nobody can reach them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- The add-a-PC page -------------------------------------------------------

/**
 * Adding a machine the network never announced.
 *
 * Two fields, and only two. A name, because a row of addresses is not a list of
 * computers, and the address itself. The port is not offered because nothing
 * below this screen would carry one, and the PIN is not offered because it
 * travels the other way — Loki generates one and Sunshine is told it, which is
 * the opposite of a field to type it into.
 *
 * The panel beside them is not decoration. This is the one screen in the section
 * where the answer is on the other machine, so it says where on that machine to
 * look.
 */
@Composable
private fun CouchAddHostPage(
    address: String,
    name: String,
    field: StreamAddField,
    keyboardRequest: Long,
    onAddressChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onFieldFocused: (StreamAddField) -> Unit,
    onAddHost: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val textInput = LocalThorTextInput.current
    val currentAddress by rememberUpdatedState(address)
    val currentName by rememberUpdatedState(name)
    val currentOnAddressChanged by rememberUpdatedState(onAddressChanged)
    val currentOnNameChanged by rememberUpdatedState(onNameChanged)

    /*
     * The keyboard is raised by the section rather than by a tap on the field.
     *
     * A field can only claim text input when something touches it, and nothing
     * touches anything here — the whole screen is driven from a pad across the
     * room. The counter is what carries "and now" across that gap: the same
     * field being asked for twice is two requests, which a flag could not say.
     */
    LaunchedEffect(keyboardRequest) {
        if (keyboardRequest <= 0L) return@LaunchedEffect
        when (field) {
            StreamAddField.NAME -> textInput.focus(
                id = NAME_FIELD_ID,
                label = "PC name",
                initial = currentName,
            ) { edited -> currentOnNameChanged(edited) }

            StreamAddField.ADDRESS -> textInput.focus(
                id = COUCH_ADDRESS_FIELD_ID,
                label = "PC address",
                initial = currentAddress,
            ) { edited -> currentOnAddressChanged(edited) }

            StreamAddField.SUBMIT -> Unit
        }
    }

    Row(
        modifier = modifier.padding(horizontal = SCREEN_INSET.dp, vertical = SCREEN_TOP_INSET.dp),
        horizontalArrangement = Arrangement.spacedBy(FORM_GAP.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(FORM_ROW_GAP.dp),
        ) {
            Text(
                text = "Add a PC",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "Enter the address of the computer to add it to your list.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(FORM_ROW_GAP.dp))

            FormField(
                label = "PC NAME",
                optional = true,
                id = NAME_FIELD_ID,
                value = name,
                placeholder = "Living room PC",
                focused = field == StreamAddField.NAME,
                onValueChange = onNameChanged,
                onClick = { onFieldFocused(StreamAddField.NAME) },
            )
            FormField(
                label = "PC ADDRESS",
                optional = false,
                id = COUCH_ADDRESS_FIELD_ID,
                value = address,
                placeholder = "192.168.1.20 or 100.x.y.z",
                focused = field == StreamAddField.ADDRESS,
                onValueChange = onAddressChanged,
                onClick = { onFieldFocused(StreamAddField.ADDRESS) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(BAND_ACTION_GAP.dp)) {
                StreamActionButton(
                    label = "CANCEL",
                    icon = Icons.Rounded.Close,
                    onClick = onCancel,
                    modifier = Modifier.width(BAND_ACTION_WIDTH.dp),
                )
                StreamActionButton(
                    label = "SAVE PC",
                    icon = Icons.Rounded.Add,
                    enabled = address.isNotBlank(),
                    primary = true,
                    controllerFocused = field == StreamAddField.SUBMIT,
                    onClick = onAddHost,
                    modifier = Modifier.width(SUBMIT_WIDTH.dp),
                )
            }
        }

        CouchAddHostHelp(modifier = Modifier.width(HELP_WIDTH.dp).fillMaxHeight())
    }
}

@Composable
private fun FormField(
    label: String,
    optional: Boolean,
    id: String,
    value: String,
    placeholder: String,
    focused: Boolean,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Black,
            )
            if (optional) {
                Text(
                    text = "OPTIONAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = HINT_ALPHA),
                )
            }
        }
        // The controller's own ring, over the field's. A field only looks focused
        // once it holds text input, and the cursor arrives on it a press before
        // that — so without this, moving down the form lights nothing at all.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .thorCursor(focused = focused, shape = ThorTheme.shapes.small)
                .clickable(onClick = onClick),
        ) {
            ThorInputField(
                id = id,
                label = label,
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CouchAddHostHelp(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FORM_ROW_GAP.dp)) {
        /*
         * A picture of the thing being added.
         *
         * Drawn rather than shipped: the panel beside it is four lines of
         * instructions and a note, and a column of nothing but words is a column
         * nobody reads from a sofa. A lit glyph on a dark field is enough to say
         * what this page is about without an asset to keep in step with a theme
         * the user chose.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HELP_ART_HEIGHT.dp)
                .clip(ThorTheme.shapes.panel)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.cursor.copy(alpha = 0.16f),
                            colors.surface.copy(alpha = 0.4f),
                        ),
                    ),
                )
                .border(1.dp, colors.cursor.copy(alpha = 0.24f), ThorTheme.shapes.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DesktopWindows,
                contentDescription = null,
                tint = colors.cursor,
                modifier = Modifier.size(HELP_ART_ICON.dp),
            )
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = ThorTheme.shapes.panel,
        ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HELP_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(HELP_GAP.dp),
        ) {
            Text(
                text = "How to find your PC",
                style = MaterialTheme.typography.titleMedium,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            HELP_STEPS.forEachIndexed { index, step ->
                HelpStep(number = index + 1, text = step)
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outline.copy(alpha = 0.2f)),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(HELP_NUMBER.dp),
                )
                Text(
                    text = "The PC has to be awake and on this network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        }
    }
}

@Composable
private fun HelpStep(number: Int, text: String) {
    val colors = ThorTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .size(HELP_NUMBER.dp)
                .clip(ThorTheme.shapes.pill)
                .background(colors.cursor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---- The legend --------------------------------------------------------------

/** What each button does, along the bottom where a television legend belongs. */
@Composable
private fun CouchLegend(entries: List<Pair<String, String>>) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LEGEND_HEIGHT.dp)
            .padding(horizontal = SCREEN_INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_GAP.dp, Alignment.End),
    ) {
        entries.forEach { (button, action) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = button,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---- Words and arithmetic ----------------------------------------------------

/**
 * How a host's state reads on a card.
 *
 * Sentence case and short, where the handheld's badge shouts it in capitals: a
 * card carries its status as a caption beside a coloured dot rather than as a
 * pill, and a wall of capitals is a wall of shouting.
 */
internal fun HostStatus.couchLabel(): String = when (this) {
    HostStatus.Unknown -> "Waiting"
    HostStatus.Checking -> "Checking"
    is HostStatus.Offline -> "Offline"
    is HostStatus.Online -> when {
        currentGame != null -> "In session"
        paired -> "Online"
        else -> "Pair needed"
    }
}

/**
 * How tall one card in the wall is, on a grid [gridHeight] high.
 *
 * Sized so that two rows are on screen, for the same reason the catalogue
 * guarantees two shelves: a wall that shows one row at a time is a list, and the
 * only way to survey a list is to walk it. The clamps keep a card recognisable on
 * a panel too short to honour that, and stop three PCs on a tall screen becoming
 * three billboards.
 */
internal fun couchHostCardHeight(gridHeight: Dp): Dp =
    ((gridHeight - CARD_GAP.dp - CARD_GROWTH.dp * 2) / VISIBLE_CARD_ROWS)
        .coerceIn(MIN_CARD.dp, MAX_CARD.dp)

/** How many PCs stand across the wall. Shared with the cursor's own arithmetic. */
internal const val STREAM_COUCH_COLUMNS = 3

/** The rows that should be reachable without scrolling. */
private const val VISIBLE_CARD_ROWS = 2

private val COMPUTERS_LEGEND = listOf(
    "A" to "Select",
    "Y" to "Pair",
    "LEFT" to "Menu",
    "B" to "Back",
)

private val ADD_HOST_LEGEND = listOf(
    "A" to "Select",
    "Y" to "Keyboard",
    "B" to "Cancel",
)

private val HELP_LEGEND = listOf(
    "UP / DOWN" to "Read",
    "LEFT" to "Menu",
    "B" to "Back",
)

private val RAIL_LEGEND = listOf(
    "A" to "Open",
    "RIGHT" to "Back to the page",
)

private val HEADER_LEGEND = listOf(
    "A" to "Open",
    "DOWN" to "Back to the PCs",
)

/**
 * What the pad does once a stream is up.
 *
 * Read from the streaming window's own handling rather than invented for this
 * page: Back leaves on the press, every other button belongs to the PC, and the
 * combination is the way out when a game has taken the pad whole.
 */
private val PAD_REFERENCE = listOf(
    "B / BACK" to "Leave the stream. The session keeps running on the PC.",
    "START" to "Show the trackpad's own settings on the bottom screen.",
    "EVERY OTHER" to "Goes to the PC, exactly as a pad plugged into it would.",
)

/** Stands in for whatever the user has named this device to Sunshine. */
private const val CLIENT_NAME_TOKEN = "%CLIENT%"

/** One numbered part of the help page. */
internal data class StreamHelpSection(val title: String, val body: String)

/**
 * What somebody has to know, in the order they have to know it.
 *
 * Held here rather than in strings because the view model counts them: the help
 * page's cursor is clamped to this list, and a count kept in two places is a
 * cursor that eventually points past the end of the page.
 */
internal val STREAM_HELP_SECTIONS = listOf(
    StreamHelpSection(
        title = "Install Sunshine on the PC",
        body = "Sunshine is what answers when Loki asks — the same host software " +
            "Moonlight talks to. Install it on the computer, start it, and leave " +
            "it running. Windows asks whether to allow it through the firewall the " +
            "first time, and it has to be allowed on the private network or nothing " +
            "on this device will ever reach it.",
    ),
    StreamHelpSection(
        title = "Let it be found",
        body = "A PC running Sunshine on this network announces itself and appears " +
            "on the Computers page on its own. One on a VPN, on another subnet, or " +
            "on a network that blocks those announcements will not — add it by " +
            "address instead, and it is remembered from then on.",
    ),
    StreamHelpSection(
        title = "Pair, once",
        body = "Pairing is a one-time exchange, and it is per device rather than per " +
            "network. Press Y on the PC here; Loki shows a PIN and waits. Type that " +
            "PIN into Sunshine's web interface on the PC, under PIN. It will list " +
            "this device as \"$CLIENT_NAME_TOKEN\". Unpairing happens on the PC, and " +
            "the first sign of it here is a machine asking to be paired again.",
    ),
    StreamHelpSection(
        title = "Stream the desktop",
        body = "Press A on a paired machine and it shares its whole screen rather " +
            "than one chosen game — so whatever is then started on the PC appears " +
            "here, and nothing has to be picked beforehand. A PC already streaming " +
            "can only be resumed or stopped, which is why it says so on its card.",
    ),
    StreamHelpSection(
        title = "Leaving is not stopping",
        body = "Back leaves the stream and the session keeps running on the PC, " +
            "which is what makes going straight back into it instant. Use Stop " +
            "session to actually end it — otherwise the only other way is to walk " +
            "to the machine.",
    ),
    StreamHelpSection(
        title = "If a PC will not answer",
        body = "The card says which failure it was rather than only \"offline\": " +
            "connection refused means the machine is there and Sunshine is not, a " +
            "timeout means it is not answering at all, and an address that cannot " +
            "be resolved is the wrong address. A machine that has answered once is " +
            "kept in the list while it sleeps, so a PC being listed is not a claim " +
            "that it is awake.",
    ),
)

private val HELP_STEPS = listOf(
    "Install Sunshine on the PC and leave it running.",
    "Open Sunshine's web interface on that machine.",
    "Note the address it is listening on.",
    "Type it here, then pair from the list.",
)

private const val FIELD_ALPHA = 0.34f
private const val HINT_ALPHA = 0.66f

private const val SCREEN_INSET = 22
private const val SCREEN_TOP_INSET = 14
private const val SECTION_GAP = 12
private const val LEGEND_HEIGHT = 24
private const val LEGEND_GAP = 18

private const val RAIL_WIDTH = 186
private const val RAIL_ALPHA = 0.55f
private const val RAIL_INSET = 12
private const val RAIL_TOP_INSET = 16
private const val RAIL_GAP = 6
private const val RAIL_ROW_PADDING = 12
private const val RAIL_ROW_PADDING_V = 8
private const val RAIL_ICON_GAP = 10
private const val RAIL_MARK = 34
private const val RAIL_DESTINATION_ICON = 17
private const val STATS_ALPHA = 0.7f
private const val STATS_INSET = 12
private const val STATS_GAP = 10
private const val STAT_ICON = 18

private const val MIN_CARD = 132
private const val MAX_CARD = 218
private const val CARD_GAP = 12
private const val CARD_PADDING = 14
private const val CARD_ALPHA = 0.86f
private const val CARD_ICON = 46
private const val CARD_PILL_GAP = 8
private const val STATUS_DOT = 8
private const val PAIRED_ICON = 15
private const val RESTING_ART_ALPHA = 0.78f
/** Room around a card for the focus ring to sit in without being clipped. */
private const val CARD_GROWTH = 3
private const val ADD_CARD_ALPHA = 0.4f
private const val ADD_MARK = 46
private const val ADD_ICON = 26

private const val HEADER_ACTION_WIDTH = 132
private const val HEADER_HELP_WIDTH = 100
private const val HEADER_ACTION_GAP = 6

private const val BAND_PADDING = 13
private const val BAND_GAP = 12
private const val BAND_MARK = 44
private const val BAND_ICON = 24
private const val BAND_ACTION_WIDTH = 138
private const val BAND_ACTION_GAP = 8
private const val PIN_TRACKING = 6

private const val EMPTY_PADDING = 24
private const val EMPTY_MARK = 70
private const val EMPTY_ICON = 36
private const val EMPTY_ACTION_WIDTH = 232

private const val FORM_GAP = 18
private const val FORM_ROW_GAP = 10
private const val SUBMIT_WIDTH = 168
private const val HELP_WIDTH = 274
private const val HELP_PADDING = 16
private const val HELP_GAP = 11
private const val HELP_NUMBER = 22
private const val HELP_ART_HEIGHT = 116
private const val HELP_ART_ICON = 56
private const val HELP_MARKER = 3
private const val HELP_BUTTON_WIDTH = 86

private const val NAME_FIELD_ID = "stream-couch-host-name"
private const val COUCH_ADDRESS_FIELD_ID = "stream-couch-host-address"
