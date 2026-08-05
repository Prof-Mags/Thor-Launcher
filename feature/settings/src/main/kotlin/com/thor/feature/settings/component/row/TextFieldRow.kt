package com.thor.feature.settings.component.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import com.thor.feature.settings.component.SettingsCard
import com.thor.feature.settings.component.SettingsTextButton

/**
 * A free-text setting, used for API keys and account names.
 *
 * Secrets are masked with a reveal toggle — a key is unusable if it cannot be
 * checked for a typo, but leaving it in plain sight on a shared handheld is
 * worse.
 */
@Composable
fun TextFieldRow(
    title: String,
    subtitle: String? = null,
    value: String,
    placeholder: String? = null,
    isSecret: Boolean = false,
    focused: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    var revealed by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    var controllerAction by remember(title) { mutableIntStateOf(TEXT_ACTION_EDIT) }

    // Secret rows contain two real actions: edit the value and reveal it. Keep
    // both reachable from a pad instead of leaving the reveal chip touch-only.
    RegisterForHorizontalSteps(focused && isSecret)
    StepOnHorizontal(focused && isSecret) { direction ->
        controllerAction = (controllerAction + direction)
            .coerceIn(TEXT_ACTION_EDIT, TEXT_ACTION_REVEAL)
    }
    LaunchedEffect(focused) {
        if (!focused) controllerAction = TEXT_ACTION_EDIT
    }

    /*
     * Confirm hands the row over to the launcher's keyboard.
     *
     * A text row cannot be edited by the cursor alone, and it used to ask a platform
     * `FocusRequester` for focus — which summons an IME that does not render on this
     * hardware. Claiming THOR's own text focus raises THOR's own keyboard instead,
     * which is the only one that appears.
     */
    val textInput = LocalThorTextInput.current
    val fieldId = remember(title) { "setting-" + title }
    ActivateOnConfirm(focused) {
        if (isSecret && controllerAction == TEXT_ACTION_REVEAL) {
            revealed = !revealed
        } else {
            textInput.focus(id = fieldId, label = title, initial = draft) { edited ->
                draft = edited
                onValueChange(edited.trim())
            }
        }
    }

    SettingsCard(focused = focused) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_INSET.dp, vertical = 14.dp),
        ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            ThorInputField(
                id = fieldId,
                label = title,
                value = draft,
                onValueChange = {
                    draft = it
                    onValueChange(it.trim())
                },
                placeholder = placeholder,
                secret = isSecret && !revealed,
                modifier = Modifier.weight(1f),
            )
            if (isSecret) {
                SettingsTextButton(
                    label = if (revealed) "HIDE" else "SHOW",
                    containerColor = colors.cursor.copy(alpha = 0.12f),
                    contentColor = colors.cursor,
                    focused = focused && controllerAction == TEXT_ACTION_REVEAL,
                    reactToHover = true,
                    onClick = { revealed = !revealed },
                )
            }
        }
        }
    }
}

private const val TEXT_ACTION_EDIT = 0
private const val TEXT_ACTION_REVEAL = 1
