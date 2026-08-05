package com.thor.feature.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.HostStatus
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.data.stream.LaunchStage

/**
 * One word per button, wherever one word will do.
 *
 * The buttons share a row and take an equal slice of it, so on the handheld panel
 * each gets about seven characters before the label is ellipsised — and the labels
 * were "STOP SESSION", "CHECK AGAIN", "CANCEL PAIRING" and "START STREAM". Every
 * one of them was cut, and a button reading "CHECK AGA…" is worse than a short
 * label because it looks like a rendering fault rather than a decision.
 *
 * The second word was carrying nothing in any of them. This is the PC-streaming
 * screen with a play icon on the button; "START" is not ambiguous here, and
 * neither is "STOP" beside a stop icon on a machine that is mid-session. "REFRESH"
 * replaces the paired/unpaired split as well — the two said the same thing in
 * different numbers of characters, and only the longer one was ever cut.
 *
 * Shared by both views rather than written out twice. Couch mode has room for the
 * longer strings, but the same action wearing two names across two screens of one
 * feature is how a launcher stops reading as one program.
 */
internal fun streamActionLabel(action: StreamHostAction, online: HostStatus.Online?): String =
    when (action) {
        StreamHostAction.START_STREAM -> if (online?.currentGame != null) "RESUME" else "START"
        StreamHostAction.STOP_SESSION -> "STOP"
        StreamHostAction.REFRESH -> "REFRESH"
        StreamHostAction.PAIR -> "PAIR"
        StreamHostAction.CANCEL_PAIRING -> "CANCEL"
        StreamHostAction.FORGET -> "REMOVE"
    }

@Composable
internal fun StreamActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    controllerFocused: Boolean = false,
    quiet: Boolean = false,
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
        /*
         * Nothing behind it until it is reached.
         *
         * For the controls that sit on the page rather than in a panel — the
         * ones above a screen's own content, where a filled slab reads as a
         * second header competing with the title beside it. The outline still
         * says it is pressable, and the fill comes back the moment the cursor
         * or the pointer arrives, which is when it has something to say.
         */
        quiet && !highlighted -> Color.Transparent
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

internal fun selectedHostMessage(
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

internal fun HostStatus.badgeLabel(): String = when (this) {
    HostStatus.Unknown -> "WAITING"
    HostStatus.Checking -> "CHECKING"
    is HostStatus.Offline -> "OFFLINE"
    is HostStatus.Online -> when {
        currentGame != null -> "IN SESSION"
        paired -> "READY"
        else -> "PAIRING NEEDED"
    }
}

internal fun HostStatus.tint(error: Color, unknown: Color): Color = when (this) {
    is HostStatus.Online -> if (currentGame != null) BUSY else ONLINE
    is HostStatus.Offline -> error
    HostStatus.Checking -> unknown
    HostStatus.Unknown -> unknown
}

internal val ONLINE = Color(0xFF4CAF50)
private val BUSY = Color(0xFFFFB300)

internal const val SELECTED_PANEL_WEIGHT = 0.61f
internal const val ADDRESS_FIELD_ID = "stream-host-address"
