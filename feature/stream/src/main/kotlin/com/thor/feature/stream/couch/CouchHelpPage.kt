package com.thor.feature.stream.couch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.stream.tint

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
internal fun CouchHelpPage(
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
