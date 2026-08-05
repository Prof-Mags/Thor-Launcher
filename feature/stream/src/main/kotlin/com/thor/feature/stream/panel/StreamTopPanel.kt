package com.thor.feature.stream.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.model.StreamHost
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.stream.badgeLabel
import com.thor.feature.stream.ONLINE
import com.thor.feature.stream.StreamUiState
import com.thor.feature.stream.tint

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
            .then(
                Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                colors.surfaceElevated.copy(alpha = 0.52f),
                                colors.background,
                            ),
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
        color = colors.surface,
        bordered = true,
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
                    text = "Loki LINK",
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
            .background(
                colors.surfaceHighest.copy(alpha = 0.74f),
            )
            .padding(
                horizontal = 13.dp,
                vertical = 7.dp,
            ),
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
internal fun DiscoveryStep(number: String, label: String) {
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
            .padding(
                horizontal = dimens.spacing,
                vertical = 12.dp,
            ),
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
internal fun HostStatusBadge(status: HostStatus, connecting: Boolean = false) {
    val colors = ThorTheme.colors
    val tint = status.tint(colors.error, colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(tint.copy(alpha = 0.12f))
            .then(
                Modifier.border(
                    1.dp,
                    tint.copy(alpha = 0.36f),
                    ThorTheme.shapes.pill,
                ),
            )
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
