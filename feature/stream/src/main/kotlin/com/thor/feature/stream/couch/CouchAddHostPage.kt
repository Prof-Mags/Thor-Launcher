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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import com.thor.feature.stream.StreamActionButton
import com.thor.feature.stream.StreamAddField
import com.thor.feature.stream.tint

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
internal fun CouchAddHostPage(
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
