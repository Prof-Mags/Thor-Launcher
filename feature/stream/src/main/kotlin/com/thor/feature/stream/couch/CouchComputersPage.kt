package com.thor.feature.stream.couch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.stream.PairingState
import com.thor.feature.stream.selectedHostMessage
import com.thor.feature.stream.StreamActionButton
import com.thor.feature.stream.streamActionLabel
import com.thor.feature.stream.StreamCouchZone
import com.thor.feature.stream.streamGridTarget
import com.thor.feature.stream.StreamHeaderAction
import com.thor.feature.stream.StreamHostAction
import com.thor.feature.stream.StreamUiState
import com.thor.feature.stream.tint

// ---- The computers page ------------------------------------------------------

@Composable
internal fun CouchComputersPage(
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
internal fun DiscoveryStep(number: String, label: String) {
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
