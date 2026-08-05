package com.thor.feature.stream.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.input.ThorInputField
import com.thor.data.stream.PairingState
import com.thor.feature.stream.ADDRESS_FIELD_ID
import com.thor.feature.stream.SELECTED_PANEL_WEIGHT
import com.thor.feature.stream.selectedHostMessage
import com.thor.feature.stream.StreamActionButton
import com.thor.feature.stream.streamActionLabel
import com.thor.feature.stream.StreamHostAction
import com.thor.feature.stream.StreamUiState
import com.thor.feature.stream.tint

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
    onForgetHost: (StreamHost) -> Unit = {},
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
                onForgetHost = onForgetHost,
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
    onForgetHost: (StreamHost) -> Unit,
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
                onForgetHost = onForgetHost,
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
    onForgetHost: (StreamHost) -> Unit,
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
                val label = streamActionLabel(action, online)
                val icon = when (action) {
                    StreamHostAction.START_STREAM -> Icons.Rounded.PlayArrow
                    StreamHostAction.STOP_SESSION -> Icons.Rounded.Stop
                    StreamHostAction.REFRESH -> Icons.Rounded.Refresh
                    StreamHostAction.PAIR -> Icons.Rounded.Link
                    StreamHostAction.CANCEL_PAIRING -> Icons.Rounded.Close
                    StreamHostAction.FORGET -> Icons.Rounded.DeleteOutline
                }
                val onClick = when (action) {
                    StreamHostAction.START_STREAM -> onStartStream
                    StreamHostAction.STOP_SESSION -> onStopStream
                    StreamHostAction.REFRESH -> ({ onRefreshHost(host) })
                    StreamHostAction.PAIR -> onPairHost
                    StreamHostAction.CANCEL_PAIRING -> onCancelPairing
                    StreamHostAction.FORGET -> ({ onForgetHost(host) })
                }
                StreamActionButton(
                    label = label,
                    icon = icon,
                    primary = action == StreamHostAction.START_STREAM ||
                        (action == StreamHostAction.REFRESH && online?.paired != true),
                    destructive = action == StreamHostAction.STOP_SESSION ||
                        action == StreamHostAction.FORGET,
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
