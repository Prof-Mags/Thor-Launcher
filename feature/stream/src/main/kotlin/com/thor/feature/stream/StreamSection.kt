package com.thor.feature.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.input.ThorInputField
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.stream.LaunchStage
import com.thor.data.stream.PairingState

/**
 * Remote-play overview on the top display.
 *
 * The panel is deliberately a dashboard rather than a settings list: a host's
 * reachability, pairing and active-session state are the information needed to
 * decide what to do next. Configuration remains on the companion display.
 */
@Composable
fun StreamTopPanel(
    state: StreamUiState,
    onHostSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val listState = rememberLazyListState()

    LaunchedEffect(state.cursor, state.hosts.size) {
        if (state.hosts.isNotEmpty()) {
            listState.animateScrollToItem(state.cursor.coerceIn(0, state.hosts.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfaceElevated.copy(alpha = 0.52f),
                        colors.background,
                    ),
                ),
            )
            .padding(
                start = dimens.spacing,
                top = dimens.spacingSmall,
                end = dimens.spacing,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        StreamHeader(state = state)

        if (state.hosts.isEmpty()) {
            EmptyDiscovery(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 2.dp,
                    bottom = dimens.spacing,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                itemsIndexed(
                    items = state.hosts,
                    key = { _, host -> host.address },
                ) { index, host ->
                    HostCard(
                        host = host,
                        status = state.statusOf(host),
                        selected = index == state.cursor,
                        connecting = state.connecting && index == state.cursor,
                        onClick = { onHostSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamHeader(state: StreamUiState) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val online = state.hosts.count { state.statusOf(it) is HostStatus.Online }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ThorTheme.shapes.panel,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(
                        Brush.linearGradient(colors.accentStops),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = contrastingContentColor(colors.cursor),
                    modifier = Modifier.size(24.dp),
                )
            }

            Column {
                Text(
                    text = "THOR LINK",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "PC Streaming",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HeaderMetric(
                value = state.hosts.size.toString(),
                label = if (state.hosts.size == 1) "HOST" else "HOSTS",
            )
            HeaderMetric(value = online.toString(), label = "ONLINE")
            HeaderMetric(value = state.readyCount.toString(), label = "READY")
        }
    }
}

@Composable
private fun HeaderMetric(value: String, label: String) {
    val colors = ThorTheme.colors
    Column(
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.74f))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDiscovery(modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.spacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(ThorTheme.shapes.panel)
                    .background(colors.cursor.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        colors.cursor.copy(alpha = 0.42f),
                        ThorTheme.shapes.panel,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = colors.cursor,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = "Searching for Sunshine hosts",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = dimens.spacing),
            )
            Text(
                text = "Keep Sunshine running on your PC. Hosts on this network appear " +
                    "automatically; VPN and remote hosts can be added by address below.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(
                modifier = Modifier.padding(top = dimens.spacingLarge),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                DiscoveryStep("01", "START SUNSHINE")
                DiscoveryStep("02", "USE THE SAME NETWORK")
                DiscoveryStep("03", "OR ADD AN ADDRESS")
            }
        }
    }
}

@Composable
private fun DiscoveryStep(number: String, label: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.70f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HostCard(
    host: StreamHost,
    status: HostStatus,
    selected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel
    val hover = rememberPointerHover()
    val highlighted = selected || hover.isHovered
    val displayName = (status as? HostStatus.Online)?.name
        ?.takeIf(String::isNotBlank)
        ?: host.displayName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .clip(shape)
            .background(
                if (highlighted) {
                    Brush.horizontalGradient(
                        listOf(
                            colors.cursor.copy(alpha = 0.18f),
                            colors.surfaceHighest.copy(alpha = 0.96f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(colors.surface.copy(alpha = 0.86f), colors.surfaceElevated),
                    )
                },
            )
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) {
                    colors.cursor.copy(alpha = 0.76f)
                } else {
                    colors.outline.copy(alpha = 0.26f)
                },
                shape = shape,
            )
            .thorCursor(focused = highlighted, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ThorTheme.shapes.small)
                .background(status.tint(colors.error, colors.onSurfaceVariant).copy(alpha = 0.13f))
                .border(
                    1.dp,
                    status.tint(colors.error, colors.onSurfaceVariant).copy(alpha = 0.46f),
                    ThorTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = status.tint(colors.error, colors.onSurfaceVariant),
                modifier = Modifier.size(27.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (host.discovered) "AUTO" else "SAVED",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(ThorTheme.shapes.pill)
                        .background(colors.cursor.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Text(
                text = host.address,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HostStatusBadge(status = status, connecting = connecting)
    }
}

@Composable
private fun HostStatusBadge(status: HostStatus, connecting: Boolean = false) {
    val colors = ThorTheme.colors
    val tint = status.tint(colors.error, colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.36f), ThorTheme.shapes.pill)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(ThorTheme.shapes.pill)
                .background(tint),
        )
        Text(
            text = if (connecting) "CONNECTING" else status.badgeLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Selected-host controls and manual connection on the bottom display. */
@Composable
fun StreamBottomPanel(
    state: StreamUiState,
    clientName: String,
    onAddressChanged: (String) -> Unit,
    onAddHost: () -> Unit,
    onRefreshHost: (StreamHost) -> Unit = {},
    onStartStream: () -> Unit = {},
    onPairHost: () -> Unit = {},
    onCancelPairing: () -> Unit = {},
    onStopStream: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.surfaceElevated.copy(alpha = 0.46f), colors.background),
                ),
            )
            .padding(dimens.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        BottomHeader(state = state)

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            SelectedHostPanel(
                state = state,
                clientName = clientName,
                onRefreshHost = onRefreshHost,
                onStartStream = onStartStream,
                onPairHost = onPairHost,
                onCancelPairing = onCancelPairing,
                onStopStream = onStopStream,
                modifier = Modifier.weight(SELECTED_PANEL_WEIGHT).fillMaxHeight(),
            )
            ManualHostPanel(
                address = state.newAddress,
                onAddressChanged = onAddressChanged,
                onAddHost = onAddHost,
                modifier = Modifier.weight(1f - SELECTED_PANEL_WEIGHT).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun BottomHeader(state: StreamUiState) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val host = state.selected
    val status = host?.let(state::statusOf) ?: HostStatus.Unknown

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ThorTheme.shapes.panel,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 34.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Brush.verticalGradient(colors.accentStops)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REMOTE PLAY CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = host?.displayName ?: "Waiting for a PC",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HostStatusBadge(status = status, connecting = state.connecting)
        }
    }
}

@Composable
private fun SelectedHostPanel(
    state: StreamUiState,
    clientName: String,
    onRefreshHost: (StreamHost) -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val host = state.selected
    val status = host?.let(state::statusOf)

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        if (host == null || status == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(dimens.spacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = "No PC selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Discovered and saved PCs will appear on the top screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            return@GlassSurface
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(ThorTheme.shapes.small)
                        .background(
                            status.tint(colors.error, colors.onSurfaceVariant).copy(alpha = 0.13f),
                        )
                        .border(
                            1.dp,
                            status.tint(colors.error, colors.onSurfaceVariant).copy(alpha = 0.44f),
                            ThorTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = status.tint(colors.error, colors.onSurfaceVariant),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (status as? HostStatus.Online)?.name
                            ?.takeIf(String::isNotBlank)
                            ?: host.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = host.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Text(
                text = selectedHostMessage(state, status, clientName),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    state.error != null || status is HostStatus.Offline -> colors.error
                    else -> colors.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HostMetric(
                    label = "SOURCE",
                    value = if (host.discovered) "Automatic" else "Saved address",
                    modifier = Modifier.weight(1f),
                )
                HostMetric(
                    label = "PAIRING",
                    value = if ((status as? HostStatus.Online)?.paired == true) {
                        "Paired"
                    } else {
                        "Required"
                    },
                    modifier = Modifier.weight(1f),
                )
                HostMetric(
                    label = "SESSION",
                    value = (status as? HostStatus.Online)?.currentGame ?: "Idle",
                    modifier = Modifier.weight(1f),
                )
            }

            when (val pairing = state.pairing) {
                is PairingState.AwaitingPin -> PairingPin(pin = pairing.pin)
                is PairingState.Failed -> ErrorStrip(
                    "Pairing failed while ${pairing.step}: ${pairing.reason}",
                )
                PairingState.Verifying -> StatusStrip("Verifying this PC…")
                PairingState.Paired -> StatusStrip("Pairing complete. Checking the PC again…")
                PairingState.Idle -> Unit
            }

            Spacer(modifier = Modifier.weight(1f))

            HostActions(
                state = state,
                host = host,
                status = status,
                onRefreshHost = onRefreshHost,
                onStartStream = onStartStream,
                onPairHost = onPairHost,
                onCancelPairing = onCancelPairing,
                onStopStream = onStopStream,
            )
        }
    }
}

@Composable
private fun HostMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    Column(
        modifier = modifier
            .clip(ThorTheme.shapes.small)
            .background(colors.surfaceHighest.copy(alpha = 0.72f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PairingPin(pin: String) {
    val colors = ThorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.cursor.copy(alpha = 0.11f))
            .border(1.dp, colors.cursor.copy(alpha = 0.46f), ThorTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PAIRING CODE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Enter this in Sunshine → PIN on your PC.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = pin,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.cursor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusStrip(text: String) {
    val colors = ThorTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.cursor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.cursor.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun ErrorStrip(text: String) {
    val colors = ThorTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThorTheme.shapes.small)
            .background(colors.error.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun HostActions(
    state: StreamUiState,
    host: StreamHost,
    status: HostStatus,
    onRefreshHost: (StreamHost) -> Unit,
    onStartStream: () -> Unit,
    onPairHost: () -> Unit,
    onCancelPairing: () -> Unit,
    onStopStream: () -> Unit,
) {
    val online = status as? HostStatus.Online

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (state.connecting) {
            StreamActionButton(
                label = "CONNECTING",
                icon = Icons.Rounded.Link,
                enabled = false,
                primary = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        } else {
            state.hostActions.forEach { action ->
                val label = when (action) {
                    StreamHostAction.START_STREAM -> if (online?.currentGame != null) {
                        "RESUME STREAM"
                    } else {
                        "START STREAM"
                    }
                    StreamHostAction.STOP_SESSION -> "STOP SESSION"
                    StreamHostAction.REFRESH -> if (online?.paired == true) {
                        "REFRESH"
                    } else {
                        "CHECK AGAIN"
                    }
                    StreamHostAction.PAIR -> "PAIR PC"
                    StreamHostAction.CANCEL_PAIRING -> "CANCEL PAIRING"
                }
                val icon = when (action) {
                    StreamHostAction.START_STREAM -> Icons.Rounded.PlayArrow
                    StreamHostAction.STOP_SESSION -> Icons.Rounded.Stop
                    StreamHostAction.REFRESH -> Icons.Rounded.Refresh
                    StreamHostAction.PAIR -> Icons.Rounded.Link
                    StreamHostAction.CANCEL_PAIRING -> Icons.Rounded.Close
                }
                val onClick = when (action) {
                    StreamHostAction.START_STREAM -> onStartStream
                    StreamHostAction.STOP_SESSION -> onStopStream
                    StreamHostAction.REFRESH -> ({ onRefreshHost(host) })
                    StreamHostAction.PAIR -> onPairHost
                    StreamHostAction.CANCEL_PAIRING -> onCancelPairing
                }
                StreamActionButton(
                    label = label,
                    icon = icon,
                    primary = action == StreamHostAction.START_STREAM ||
                        (action == StreamHostAction.REFRESH && online?.paired != true),
                    destructive = action == StreamHostAction.STOP_SESSION,
                    controllerFocused = state.focusedHostAction == action,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ManualHostPanel(
    address: String,
    onAddressChanged: (String) -> Unit,
    onAddHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val ready = address.isNotBlank()

    GlassSurface(modifier = modifier, shape = ThorTheme.shapes.panel) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimens.spacing),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ThorTheme.shapes.small)
                        .background(colors.cursor.copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = colors.cursor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column {
                    Text(
                        text = "MANUAL CONNECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cursor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Add a PC by address",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = "Use this for VPNs, another subnet, or a network that blocks " +
                    "Sunshine announcements.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "PC ADDRESS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            ThorInputField(
                id = ADDRESS_FIELD_ID,
                label = "PC address",
                value = address,
                onValueChange = onAddressChanged,
                placeholder = "192.168.1.20 or 100.x.y.z",
                modifier = Modifier.fillMaxWidth(),
            )

            StreamActionButton(
                label = "ADD THIS PC",
                icon = Icons.Rounded.Add,
                enabled = ready,
                primary = true,
                onClick = onAddHost,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ThorTheme.shapes.small)
                    .background(colors.surfaceHighest.copy(alpha = 0.68f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "STREAM PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                StreamProfileRow("ENGINE", "Sunshine")
                StreamProfileRow("PROTOCOL", "GameStream")
                StreamProfileRow("DISPLAY", "Dual-screen ready")
            }
        }
    }
}

@Composable
private fun StreamProfileRow(label: String, value: String) {
    val colors = ThorTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = colors.cursor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StreamActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    controllerFocused: Boolean = false,
) {
    val colors = ThorTheme.colors
    val hover = rememberPointerHover()
    val highlighted = enabled && (controllerFocused || hover.isHovered)
    val tint = when {
        destructive -> colors.error
        primary -> colors.cursor
        else -> colors.onSurface
    }
    val background = when {
        !enabled -> colors.surface
        highlighted -> tint
        primary || destructive -> tint.copy(alpha = 0.16f)
        else -> colors.surfaceHighest
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant
        highlighted -> contrastingContentColor(tint)
        primary || destructive -> tint
        else -> colors.onSurface
    }

    Row(
        modifier = modifier
            .pointerHover(hover)
            .clip(ThorTheme.shapes.small)
            .background(background)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = when {
                    !enabled -> colors.outline.copy(alpha = 0.24f)
                    highlighted -> contrastingContentColor(tint).copy(alpha = 0.82f)
                    primary || destructive -> tint.copy(alpha = 0.46f)
                    else -> colors.outline.copy(alpha = 0.34f)
                },
                shape = ThorTheme.shapes.small,
            )
            .thorCursor(focused = highlighted, shape = ThorTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun selectedHostMessage(
    state: StreamUiState,
    status: HostStatus,
    clientName: String,
): String = when {
    state.connecting -> when (state.stage) {
        LaunchStage.ASKING_HOST -> "Checking that the PC is ready for a session…"
        LaunchStage.STARTING_GAME -> "Starting the desktop stream on the PC…"
        null -> "Connecting to the selected PC…"
    }

    state.error != null -> state.error
    status == HostStatus.Unknown -> "This PC has not been checked yet."
    status == HostStatus.Checking -> "Checking reachability, pairing, and session state…"
    status is HostStatus.Offline -> status.reason
    status is HostStatus.Online -> when {
        status.note != null -> status.note.orEmpty()
        status.currentGame != null -> "An active session is ready to resume or stop."
        status.paired -> "Paired and ready to stream."
        else ->
            "Pairing is required. Sunshine will list this device as “$clientName”."
    }
    else -> "The PC's current status is unavailable."
}

private fun HostStatus.badgeLabel(): String = when (this) {
    HostStatus.Unknown -> "WAITING"
    HostStatus.Checking -> "CHECKING"
    is HostStatus.Offline -> "OFFLINE"
    is HostStatus.Online -> when {
        currentGame != null -> "IN SESSION"
        paired -> "READY"
        else -> "PAIRING NEEDED"
    }
}

private fun HostStatus.tint(error: Color, unknown: Color): Color = when (this) {
    is HostStatus.Online -> if (currentGame != null) BUSY else ONLINE
    is HostStatus.Offline -> error
    HostStatus.Checking -> unknown
    HostStatus.Unknown -> unknown
}

private val ONLINE = Color(0xFF4CAF50)
private val BUSY = Color(0xFFFFB300)

private const val SELECTED_PANEL_WEIGHT = 0.61f
private const val ADDRESS_FIELD_ID = "stream-host-address"
